/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.site;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.history.ShContentAuditService;
import com.viglet.shio.url.ShSiteFurlNaming;
import com.viglet.shio.api.history.ShContentAction;
import com.viglet.shio.api.history.ShContentSubject;
import com.viglet.shio.api.folder.ShFolderWriteService;
import com.viglet.shio.exchange.site.ShBootstrapSiteProvider;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Write-side logic for console site management. Creating a site also
 * provisions its root folder ({@code rootFolder = 1}) so the content tree and
 * the navigation endpoints ({@code /api/v2/object/{id}/path}) have an anchor.
 *
 * @author Alexandre Oliveira
 */
@Service
public class ShSiteWriteService {

    private final ShSiteRepository shSiteRepository;
    private final ShFolderRepository shFolderRepository;
    private final ShBootstrapSiteProvider bootstrapSiteProvider;
    private final ObjectMapper objectMapper;
    /** SH454 — deleting a subtree is the folder writer's verb, and there is one of it (SH245). */
    private final ShFolderWriteService shFolderWriteService;
    private final ShContentAuditService shContentAuditService;
    /** SH744 — the one writer of a site's address, shared with the exchange import. */
    private final ShSiteFurlNaming shSiteFurlNaming;
    /** SH1218 — a purged site's home picture goes with it. */
    private final ShSiteThumbnailStore shSiteThumbnailStore;

    public ShSiteWriteService(ShSiteRepository shSiteRepository, ShFolderRepository shFolderRepository,
                              ShBootstrapSiteProvider bootstrapSiteProvider,
                              ObjectMapper objectMapper,
                              ShFolderWriteService shFolderWriteService,
                              ShContentAuditService shContentAuditService,
                              ShSiteFurlNaming shSiteFurlNaming,
                              ShSiteThumbnailStore shSiteThumbnailStore) {
        this.shSiteThumbnailStore = shSiteThumbnailStore;
        this.shSiteRepository = shSiteRepository;
        this.shFolderRepository = shFolderRepository;
        this.bootstrapSiteProvider = bootstrapSiteProvider;
        this.objectMapper = objectMapper;
        this.shFolderWriteService = shFolderWriteService;
        this.shContentAuditService = shContentAuditService;
        this.shSiteFurlNaming = shSiteFurlNaming;
    }

    @Transactional
    public ShSite create(ShSite payload, String owner) {
        // SH744 — before the branch, because there are two ways out of this method and the rule
        // belongs to the verb rather than to one arm of it. The bootstrap path hands the payload to
        // ShSiteImportService, which DERIVES a free address (right for a clone, where the export's
        // furl is a starting point) — so a declared furl put through it came back silently numbered
        // and the caller's own link pointed at nothing. Asked here, both paths obey one rule:
        // a declared address is identity and is refused when taken (SH81/SH651); a derived one is
        // numbered by whichever path builds it.
        if (hasText(payload.getFurl())) {
            shSiteFurlNaming.requireFree(payload.getFurl());
        }
        // SH71: clone the bundled bootstrap-site starter (post-types, folders, sample
        // pages) so a new site isn't blank. Best-effort — falls through to a plain
        // site + empty root folder if the template is absent or fails to clone.
        ShSite fromTemplate = bootstrapSiteProvider.provision(payload, owner);
        if (fromTemplate != null) {
            return fromTemplate;
        }
        return createBlank(payload, owner);
    }

    /**
     * A blank site that carries the layout bindings it was created with (SH453).
     *
     * <h2>The defect this closes</h2>
     *
     * <p>{@code site.upsert} has two branches, and only the second read {@code postTypeLayout}: an
     * existing site went through {@link #fillEmpty}, which takes the requested fields <em>and</em>
     * the SH328 bindings, while a site that did not exist was built from {@code description},
     * {@code url} and {@code furl} alone and reported {@code CREATE — a blank site}. So a caller
     * that created a site and bound its layout in one op was told the op succeeded and got no
     * binding. <b>Both halves are individually correct</b>, which is why no unit test saw it and
     * why SH448's conformance suite found it on its first run.</p>
     *
     * <p>The failure surfaces nowhere near the cause. The next thing to read the binding is
     * {@code ShPageRenderService}, so the caller gets a 400 from {@code /preview/<site>/} —
     * <em>"Site '…' binds no layout for post-type '…'"</em> — after four more successful writes.
     * That message is a good one, and it answers a question about the wrong step.</p>
     *
     * <h2>Applied rather than refused</h2>
     *
     * <p>§VIII.21 left the choice open between applying the bindings and refusing a create that
     * carries data it will not honour (SH320's rule). <b>Applied</b>, because P2 is the whole
     * argument for this surface: one op should mean one intention, and refusing would make the
     * first step of every new-site build two ops with the second existing only to work around the
     * first. The refusal would also have been the more surprising of the two — a caller who sends a
     * binding wants the binding.</p>
     *
     * <p>Serialized here rather than by the protocol layer, for SH245's reason: the merge that
     * {@link #applyFill} performs is the same write, and a second copy of it is where the careless
     * version grows. On a create the merge has nothing to merge with, so it is the requested map
     * minus blanks — but it goes through the same {@code objectMapper}, so both branches produce
     * one shape.</p>
     *
     * @param bindings post-type name → {@code PageLayout} title; blank values are dropped, and an
     *                 empty map leaves the column null exactly as before
     */
    @Transactional
    public ShSite createBlank(ShSite payload, Map<String, String> bindings, String owner) {
        Map<String, String> merged = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : bindings == null ? Map.<String, String>of().entrySet()
                : bindings.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isBlank()) {
                merged.put(entry.getKey(), entry.getValue());
            }
        }
        if (!merged.isEmpty()) {
            payload.setPostTypeLayout(objectMapper.writeValueAsString(merged));
        }
        return createBlank(payload, owner);
    }

    /**
     * A site and its root folder, and nothing else (SH151/SH170).
     *
     * <h2>Why blank is a first-class option</h2>
     *
     * <p>{@link #create} clones the SH71 bootstrap template because an empty site used to be
     * useless. A blueprint is exactly the thing that makes it useful — and against a cloned
     * template, "create a site for this package" means applying a package on top of a Welcome
     * page and an About page nobody asked for, then pruning them. That is why a blueprint apply
     * into a fresh site used to report {@code about} as an <em>update</em>.</p>
     *
     * <p>So the agent path creates blank sites and the console keeps its starter: the difference
     * is not a preference, it is whether something else is about to fill the site in.</p>
     */
    @Transactional
    public ShSite createBlank(ShSite payload, String owner) {
        ShSite site = new ShSite();
        site.setName(payload.getName());
        site.setDescription(payload.getDescription());
        site.setUrl(payload.getUrl());
        site.setPostTypeLayout(payload.getPostTypeLayout());
        site.setSearchablePostTypes(payload.getSearchablePostTypes());
        site.setFormSuccess(payload.getFormSuccess());
        // SH1056 — validated by whoever built the payload, because this method takes an entity and
        // an entity cannot refuse. `ShSiteLocaleService.declare` is the one door.
        site.setLocales(payload.getLocales());
        site.setPageType(payload.getPageType());
        // SH591 — carried from the create and settable nowhere else. A flag anybody could raise
        // later would make `shio site rm --scratch` a way to bulk-delete a site somebody chose to
        // keep; the guarantee it rests on is that the declaration preceded the content.
        site.setScratch(payload.isScratch() ? Boolean.TRUE : null);
        // SH744 — two writers used to decide this and only one checked. The rule is SH81/SH651's,
        // one level up: a DECLARED address is identity and is refused when taken rather than
        // renumbered, and a DERIVED one has no claim and is numbered until free.
        if (hasText(payload.getFurl())) {
            shSiteFurlNaming.requireFree(payload.getFurl());
            site.setFurl(payload.getFurl());
        } else {
            site.setFurl(shSiteFurlNaming.freeFurl(null, payload.getName()));
        }
        site.setOwner(owner);
        site.setDate(Instant.now());
        // tenant_id is populated by Hibernate's @TenantId from ShTenantContext on insert.
        ShSite saved = shSiteRepository.save(site);

        ShFolder rootFolder = new ShFolder();
        rootFolder.setName(saved.getName());
        rootFolder.setRootFolder((byte) 1);
        rootFolder.setShSite(saved);
        rootFolder.setFurl("/");
        rootFolder.setOwner(owner);
        rootFolder.setDate(Instant.now());
        shFolderRepository.save(rootFolder);

        return saved;
    }

    /**
     * SH184 — fills a site's empty descriptive fields and <b>never replaces a value that is
     * already there</b>.
     *
     * <h2>Why this is a verb of its own</h2>
     *
     * <p>{@link #update} is the console's full replace: it writes every field from the
     * payload, including the ones a partial caller never mentioned, so using it to set a URL
     * would null out {@code postTypeLayout} and {@code searchablePostTypes} on the way past.
     * That is correct for a form with every field on screen and wrong for anything else.</p>
     *
     * <p>It lives here rather than in {@code ShAgentBatchService} because the rule it encodes
     * is write semantics, not protocol shaping — and because SH245 is the standing lesson that
     * a second copy of a verb is where the careless version grows. If the console ever wants
     * "fill in what is blank", it calls this.
     *
     * <h2>The rule</h2>
     *
     * <p><b>Empty is filled; non-empty is kept.</b> {@code site.url} gates both visual
     * surfaces, and an agent that just deployed a front end is the thing that knows the URL —
     * so refusing to write it at all (SH170's original position) left a gap only a human
     * opening the console could close. But repointing a live site because a document mentioned
     * a URL is precisely the surprise P7 forbids. The line runs between filling in a blank and
     * redirecting a choice.</p>
     *
     * <p>The result names both halves so the caller can report the difference between "done"
     * and "left alone on purpose"; a silent no-op reads as the request having been ignored.</p>
     *
     * @param site     the site to fill, already resolved
     * @param values   requested values by field name ({@code url}, {@code description},
     *                 {@code furl}); blank and unknown entries are skipped
     * @param modifier who is asking, for the audit stamp
     * @return which fields were filled and which were kept
     */
    @Transactional
    public ShSiteFill fillEmpty(ShSite site, Map<String, String> values, String modifier) {
        return applyFill(site, values, Map.of(), modifier);
    }

    /**
     * As {@link #fillEmpty(ShSite, Map, String)}, also binding post-types to layouts (SH328).
     *
     * @param bindings post-type name → {@code PageLayout} title; entries only, never a
     *                 replacement of the whole map
     */
    @Transactional
    public ShSiteFill fillEmpty(ShSite site, Map<String, String> values,
                                Map<String, String> bindings, String modifier) {
        return applyFill(site, values, bindings, modifier);
    }

    // Not annotated, and both entry points above call it: a self-invocation would bypass the
    // proxy, so an annotation here would be decoration rather than a transaction.
    private ShSiteFill applyFill(ShSite site, Map<String, String> values,
                                 Map<String, String> bindings, String modifier) {
        ShSiteFill fill = plan(site, values, bindings);
        if (!fill.changed()) {
            return fill;
        }
        Map<String, String> merged = layoutBindings(site);
        boolean bound = false;
        for (String field : fill.filled()) {
            String key = layoutKeyOf(field);
            if (key == null) {
                write(site, field, values.get(field));
            } else {
                merged.put(key, bindings.get(key));
                bound = true;
            }
        }
        if (bound) {
            // Serialized from the merged map rather than by editing the raw string: the column
            // may hold the SH193 array form for another key, and a textual splice would make
            // that unreadable while looking like it worked.
            site.setPostTypeLayout(objectMapper.writeValueAsString(merged));
        }
        site.setModifier(modifier);
        site.setModifiedDate(Instant.now());
        shSiteRepository.save(site);
        return fill;
    }

    /**
     * What {@link #fillEmpty} <em>would</em> do, writing nothing.
     *
     * <p>Split out so a {@code dryRun} answers from the same code that performs the write —
     * a plan derived from a second reading of the rule is a plan that can disagree with the
     * run it predicts, which is the whole failure SH153 argued about for blueprints. It also
     * means the honest and useful case is reported in advance: "your URL will not be applied,
     * because that site already has one."</p>
     */
    @Transactional(readOnly = true)
    public ShSiteFill planFill(ShSite site, Map<String, String> values) {
        return plan(site, values, Map.of());
    }

    /** What {@link #fillEmpty(ShSite, Map, Map, String)} would do, writing nothing (SH328). */
    @Transactional(readOnly = true)
    public ShSiteFill planFill(ShSite site, Map<String, String> values,
                               Map<String, String> bindings) {
        return plan(site, values, bindings);
    }

    private ShSiteFill plan(ShSite site, Map<String, String> values,
                            Map<String, String> bindings) {
        List<String> filled = new ArrayList<>();
        List<String> kept = new ArrayList<>();
        for (String field : FILLABLE) {
            String requested = values.get(field);
            if (requested == null || requested.isBlank()) {
                continue;
            }
            String current = read(site, field);
            if (current == null || current.isBlank()) {
                filled.add(field);
            } else if (!current.equals(requested)) {
                kept.add(field);
            }
        }
        planBindings(site, bindings, filled, kept);
        return new ShSiteFill(List.copyOf(filled), List.copyOf(kept));
    }

    /**
     * SH328 — the same rule one field-type wider: a post-type with no binding is filled, one that
     * already names a layout is kept and reported.
     *
     * <p>Per key, so binding a second post-type never disturbs the first. The console's
     * {@code PUT /api/v2/site/{id}} is a whole-object replace that nulls this map when the field
     * is omitted, and that is exactly the hazard this op exists to avoid.</p>
     */
    private void planBindings(ShSite site, Map<String, String> bindings,
                              List<String> filled, List<String> kept) {
        Map<String, String> current = layoutBindings(site);
        for (Map.Entry<String, String> entry : bindings.entrySet()) {
            String requested = entry.getValue();
            if (requested == null || requested.isBlank()) {
                continue;
            }
            String existing = current.get(entry.getKey());
            if (existing == null || existing.isBlank()) {
                filled.add(LAYOUT_FIELD + "." + entry.getKey());
            } else if (!existing.equals(requested)) {
                kept.add(LAYOUT_FIELD + "." + entry.getKey());
            }
        }
    }

    /** The reported name of a binding, e.g. {@code postTypeLayout.Article}. */
    public static final String LAYOUT_FIELD = "postTypeLayout";

    /** The post-type behind a reported {@code postTypeLayout.<name>}, or {@code null}. */
    private static String layoutKeyOf(String field) {
        String prefix = LAYOUT_FIELD + ".";
        return field.startsWith(prefix) ? field.substring(prefix.length()) : null;
    }

    /**
     * The site's post-type → layout-title map, mutable and in file order.
     *
     * <p>Only string values, which is {@code ShPageRenderService.bindingsOf}'s rule and for its
     * reason: a non-string value is the SH193 array form, built once and never used, and reading
     * it half-way would bind a page to a layout nobody chose. A key skipped here is a key this op
     * reports as fillable — honest, since the renderer does not see it either.</p>
     */
    private Map<String, String> layoutBindings(ShSite site) {
        Map<String, String> bindings = new LinkedHashMap<>();
        String raw = site.getPostTypeLayout();
        if (raw == null || raw.isBlank()) {
            return bindings;
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (node == null || !node.isObject()) {
                return bindings;
            }
            for (String key : node.propertyNames()) {
                JsonNode value = node.get(key);
                if (value != null && value.isString()) {
                    bindings.put(key, value.stringValue());
                }
            }
        } catch (JacksonException unreadable) {
            // An unreadable column reads as "no bindings", so every key plans as a fill. That is
            // the renderer's own behaviour (SH270 records the incident there, on the path that
            // actually renders); repairing it from the write side would replace a value somebody
            // set, which is the one thing this method exists not to do.
            return new LinkedHashMap<>();
        }
        return bindings;
    }

    /**
     * Which fields {@link #fillEmpty} may write, in the order a report lists them — URL
     * first because it is the one that makes a site visible.
     */
    public static final List<String> FILLABLE =
            List.of("url", "description", "furl", ShSiteLocales.FIELD, ShSiteWriteService.PAGE_TYPE_FIELD);

    /** SH1283 — the op's key for the type a new page in this site starts as. */
    public static final String PAGE_TYPE_FIELD = "pageType";

    /**
     * SH1283 — a site's page type must be one it binds to a layout, or it names a type that cannot
     * render as a page. Checked against the bindings the site has plus those the same op adds, so a
     * package that binds and names in one {@code site.upsert} is not refused for its own order.
     *
     * @throws PageTypeNotBoundException naming the bound types (P6)
     */
    public static void requirePageTypeBound(String pageType, java.util.Collection<String> bound) {
        if (pageType != null && !pageType.isBlank() && !bound.contains(pageType.trim())) {
            throw new PageTypeNotBoundException(pageType.trim(), bound);
        }
    }

    /** SH1283 — the post types this site binds to a layout, which is what a page type may name. */
    public java.util.Set<String> boundTypes(ShSite site) {
        return new java.util.TreeSet<>(layoutBindings(site).keySet());
    }

    /** A 400 for a page type the site does not bind to a layout (SH1283). */
    public static class PageTypeNotBoundException extends com.viglet.shio.api.error.ShTeachingException {

        public PageTypeNotBoundException(String sent, java.util.Collection<String> bound) {
            super(com.viglet.shio.api.error.ShProblem.badRequest("page-type-not-bound",
                            "The page type '" + sent + "' is not bound to a layout on this site")
                    .withDetail("A site's page type is what a new page starts as, and a type with no "
                            + "layout binding has nothing to render through, so it cannot be a page.")
                    .withAllowed(new ArrayList<>(new java.util.TreeSet<>(bound)))
                    .withDidYouMeanFrom(sent)
                    .withFix("Name one of the bound types, or bind '" + sent + "' with "
                            + "`postTypeLayout` in the same site.upsert.")
                    .withExample("{\"pageType\":\"InfoPage\",\"postTypeLayout\":{\"InfoPage\":\"Page\"}}"));
        }
    }

    /**
     * @param filled fields that were empty and now hold the requested value
     * @param kept   fields whose existing value differs from the request and was left alone
     */
    public record ShSiteFill(List<String> filled, List<String> kept) {

        public boolean changed() {
            return !filled.isEmpty();
        }
    }

    private static String read(ShSite site, String field) {
        return switch (field) {
            case "url" -> site.getUrl();
            case "description" -> site.getDescription();
            case "furl" -> site.getFurl();
            // SH1056 — the already-validated JSON, so the fill rule is the one every other field
            // gets: a site that has no policy takes this one, and a site that has one keeps it and
            // is told so. Changing a policy somebody set is the console's job, not an upsert's.
            case ShSiteLocales.FIELD -> site.getLocales();
            case PAGE_TYPE_FIELD -> site.getPageType();
            default -> null;
        };
    }

    private static void write(ShSite site, String field, String value) {
        switch (field) {
            case "url" -> site.setUrl(value);
            case "description" -> site.setDescription(value);
            case "furl" -> site.setFurl(value);
            case ShSiteLocales.FIELD -> site.setLocales(value);
            case PAGE_TYPE_FIELD -> site.setPageType(value);
            default -> throw new IllegalStateException("Unfillable site field " + field);
        }
    }

    /**
     * SH1056 — sets a site's locale policy, or clears it back to the one its content implies.
     *
     * <p>The console's door, and the one that <em>replaces</em>. {@code site.upsert} fills a policy
     * that is missing and keeps one that is set (P7, the rule {@link #FILLABLE} gives every field),
     * and says "change it in the console" when it keeps one — so this is where that sentence
     * leads. It is its own verb rather than a field of {@link #update}, because that one is a
     * whole-object replace and a form that did not render the policy would null it on every
     * save.</p>
     *
     * @param json the policy as {@code ShSiteLocaleService.declare} validated it, or null to stop
     *             declaring one — which is not "no languages": the site then reports the locales its
     *             content is written in, as every site did before there was a setting
     * @return the site, or empty when no live site has that id
     */
    @Transactional
    public Optional<ShSite> declareLocales(String id, String json, String modifier) {
        return shSiteRepository.findById(id).map(site -> {
            site.setLocales(json);
            site.setModifier(modifier);
            site.setModifiedDate(Instant.now());
            return shSiteRepository.save(site);
        });
    }

    @Transactional
    public Optional<ShSite> update(String id, ShSite payload, String modifier) {
        return shSiteRepository.findById(id).map(site -> {
            site.setName(payload.getName());
            site.setDescription(payload.getDescription());
            site.setUrl(payload.getUrl());
            if (hasText(payload.getFurl())) {
                site.setFurl(payload.getFurl());
            }
            site.setPostTypeLayout(payload.getPostTypeLayout());
            site.setSearchablePostTypes(payload.getSearchablePostTypes());
            site.setFormSuccess(payload.getFormSuccess());
            site.setModifier(modifier);
            site.setModifiedDate(Instant.now());
            return shSiteRepository.save(site);
        });
    }

    /**
     * Puts a site and everything in it in the trash — <b>reversible</b> (SH648).
     *
     * <h2>The level the trash stopped one short of</h2>
     *
     * <p>SH55 gave posts a trash, SH200 gave folders one, and SH454 made a site delete go down
     * through both writers — so every page of a deleted site could be restored and the site itself
     * could not, because its row was removed on the way out. The most destructive gesture in the
     * product was the only one with no undo, which is the inversion SH200 fixed one level down and
     * for the same reason: a curator who deletes forty pages one at a time can get them back; the
     * same curator using the site's delete button could not.</p>
     *
     * <p>So this stamps {@link ShSite#getDeletedDate()} and trashes each root folder through the
     * folder writer — the composition SH454 established, minus the purge. Nothing is removed, which
     * is what {@link #restore} depends on: the posts keep their {@code folder_id} and the root
     * folders their {@code site_id}, so the site row has to outlive the gesture that deleted it.
     * The rows go at {@link #purge} time instead, which is also where the FK ordering SH454
     * documents is satisfied.</p>
     *
     * <p><b>The site is stamped before the folders are trashed</b>, deliberately. {@link #restore}
     * brings back only what this deletion trashed, comparing the folder's instant against the
     * site's; stamping the site last would put it after every folder's instant and the comparison
     * would exclude the whole tree.</p>
     *
     * <p>No lifecycle event fires here — SH55's rule, not an omission: a reversible trash has no
     * side effects, and the webhook plus the byte cleanup run on the irreversible purge.</p>
     *
     * @param actor who deleted it, for the audit entries the post writer and the folder writer
     *              record on the way down (SH51/SH196)
     * @return {@code false} when no live site has that id
     */
    @Transactional
    public boolean delete(String id, String actor) {
        Optional<ShSite> found = shSiteRepository.findById(id);
        if (found.isEmpty()) {
            return false;
        }
        ShSite site = found.get();
        site.setDeletedDate(Instant.now());
        shSiteRepository.save(site);
        for (String rootFolderId : rootFolderIds(site)) {
            shFolderWriteService.delete(rootFolderId, actor);
        }
        shContentAuditService.record(ShContentAction.DELETE, ShContentSubject.SITE, id, id,
                site.getName(), actor, "the site and everything in it, to the trash");
        return true;
    }

    /**
     * Restores a trashed site and the content that went into the trash with it (SH648).
     *
     * <p>Only what this deletion trashed, which is SH200's rule and its class of comparison: a root
     * folder marked <em>before</em> the site's own {@code deletedDate} was somebody else's decision
     * and stays where they put it. The folder writer applies the same test one level down, so a
     * post that was already in the trash when the site was deleted does not come back either.</p>
     *
     * <p>A site's name cannot be taken while it sleeps — {@code ShSite.name} is unique and
     * {@link ShSiteNameHeldByTrashException} refuses the create that would have taken it — but its
     * <b>furl is not unique</b>, so a live site can and does end up answering at the address this
     * one is asking to get back. That is refused rather than silently renamed, for SH243's reason:
     * a restore asks for the thing that was there, and returning it at a different address is the
     * surprise P7 exists to prevent.</p>
     *
     * @return {@code false} when the id is not a trashed site
     * @throws ShSiteRestoreConflictException when a live site took this one's furl
     */
    @Transactional
    public boolean restore(String id, String actor) {
        Optional<ShSite> found = shSiteRepository.findAnyById(id).filter(ShSite::isTrashed);
        if (found.isEmpty()) {
            return false;
        }
        ShSite site = found.get();
        requireRestorableFurl(site);
        Instant trashedAt = site.getDeletedDate();
        site.setDeletedDate(null);
        shSiteRepository.save(site);
        for (ShFolder folder : restorableRoots(site, trashedAt)) {
            shFolderWriteService.restore(folder.getId(), actor);
        }
        shContentAuditService.record(ShContentAction.RESTORE, ShContentSubject.SITE, id, id,
                site.getName(), actor, "the site and the content that went into the trash with it");
        return true;
    }

    /**
     * The pre-flight, run before the first row is written: a refusal here leaves the trash exactly
     * as the curator left it rather than relying on the rollback to put it back (SH243's shape).
     */
    @Transactional(readOnly = true)
    public void requireRestorableFurl(ShSite site) {
        if (!hasText(site.getFurl())) {
            return;
        }
        ShSite occupant = shSiteRepository.findByFurl(site.getFurl());
        if (occupant != null && !occupant.getId().equals(site.getId())) {
            throw new ShSiteRestoreConflictException(site.getFurl(), occupant.getName());
        }
    }

    /**
     * The root folders this deletion trashed — on the loaded instants rather than in SQL, because
     * an equality test against a timestamp column is a promise about fractional-second precision
     * that three database engines do not make the same way (SH200).
     */
    private static List<ShFolder> restorableRoots(ShSite site, Instant trashedAt) {
        List<ShFolder> roots = new ArrayList<>();
        for (ShFolder folder : site.getShFolders()) {
            if (folder.isTrashed() && !folder.getDeletedDate().isBefore(trashedAt)) {
                roots.add(folder);
            }
        }
        return roots;
    }

    /** SH648 — sites currently in the trash, newest deletion first. */
    @Transactional(readOnly = true)
    public List<ShSite> listTrash() {
        return shSiteRepository.findTrashedSites();
    }

    /**
     * Why a create naming {@code name} would be refused, without attempting it (SH648).
     *
     * <p>Non-throwing, for the callers that <em>report</em> a refusal rather than raising it — a
     * dry run has to reach the answer the write reaches, which is SH690's rule for the same split
     * one level down. {@link ShSiteAPI#create} raises; a plan prints.</p>
     *
     * @return the trashed site holding that name, or empty when the name is free
     */
    @Transactional(readOnly = true)
    public Optional<ShSite> nameHeldByTrash(String name) {
        if (!hasText(name)) {
            return Optional.empty();
        }
        return Optional.ofNullable(shSiteRepository.findAnyByName(name)).filter(ShSite::isTrashed);
    }

    /**
     * Permanently removes a site and everything in it (SH454, SH648) — <b>irreversible</b>.
     *
     * <h2>What this was, and why only an empty site deleted</h2>
     *
     * <p>It called {@code shSiteRepository.delete(site)} alone, with a comment explaining that an
     * entity delete is used so the cascade reaches the folders through {@code ShSite#shFolders}.
     * It does. <b>Nothing reached the posts</b>, which hold {@code sh_post_unified.folder_id}, so
     * Hibernate ordered a folder delete against rows that still referenced it and the database
     * answered {@code Referential integrity constraint violation}. The controller catches nothing,
     * so the caller got a <b>500 with a Spring default body</b> — SH189's defect, on the console's
     * most destructive button, for every site that holds a single post. Only an empty site
     * deleted, which is the one nobody deletes. Trashing the posts first is the obvious workaround
     * and does not help: trash is a flag on the row, the FK is still there, and the 500 is
     * identical.</p>
     *
     * <h2>The verb that already gets this right</h2>
     *
     * <p>{@link ShFolderWriteService#purge} solved this ordering once and records the reasoning:
     * trash what is live, purge what is trashed, {@code flush()} and {@code clear()} so Hibernate
     * does not order a folder delete ahead of a post it believes still references it, then let the
     * folder cascade run. So this is SH245's shape — <b>one writer per verb</b> — rather than new
     * logic: the root folder goes through that path, and only then is there a site whose cascade
     * has nothing left to trip on. It is the same three steps SH448's conformance harness had to
     * make by hand in its own teardown, which is the workaround that stood in for this fix and the
     * reason the deletion was checkable at all.</p>
     *
     * <p>The site itself is re-read after the purge rather than held across it: {@code purge}
     * clears the persistence context, so the instance loaded above is detached by the time it
     * returns and deleting it would be a no-op or a merge nobody asked for.</p>
     *
     * @param actor who deleted it, for the audit entries the post writer and the folder writer
     *              record on the way down (SH51/SH196)
     */
    @Transactional
    public boolean purge(String id, String actor) {
        Optional<ShSite> found = shSiteRepository.findAnyById(id);
        if (found.isEmpty()) {
            return false;
        }
        for (String rootFolderId : rootFolderIds(found.get())) {
            // Trash first because `purge` operates *on* the trash and refuses a live folder — the
            // two halves are one gesture here, and a folder this site's own delete already trashed
            // answers the first call with a harmless false.
            shFolderWriteService.delete(rootFolderId, actor);
            shFolderWriteService.purge(rootFolderId, actor);
        }
        // SH648 — findAnyById, not findById: a purge of a site that is already in the trash is the
        // ordinary path now, and the live-only read would report it absent one line before removing
        // it. The re-read itself is SH454's: `purge` clears the persistence context, so the instance
        // loaded above is detached by the time it returns.
        return shSiteRepository.findAnyById(id).map(site -> {
            String name = site.getName();
            shSiteRepository.delete(site);
            // SH223 — the row the largest deletion in the product never wrote. Its posts and
            // folders were audited one and two levels down, so a reader of the SH88 feed saw every
            // page of a site leave and nothing saying the site had. Written AFTER the delete
            // succeeded, because an audit row for something that did not happen is worse than none.
            // PURGE rather than DELETE since SH648: DELETE is now the reversible half, and an entry
            // the SH109 queue offers to revert had better be one that can be.
            shContentAuditService.record(ShContentAction.PURGE, ShContentSubject.SITE, id, id,
                    name, actor, "the site and everything in it, permanently");
            // SH1218 — the picture of its home is part of "everything in it".
            shSiteThumbnailStore.forget(id);
            return true;
        }).orElse(false);
    }

    /** The site's root folders, read before anything is purged out from under the collection. */
    private static List<String> rootFolderIds(ShSite site) {
        List<String> ids = new ArrayList<>();
        for (ShFolder folder : site.getShFolders()) {
            ids.add(folder.getId());
        }
        return ids;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

}
