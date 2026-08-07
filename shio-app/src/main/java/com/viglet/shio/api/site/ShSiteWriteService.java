/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.site;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public ShSiteWriteService(ShSiteRepository shSiteRepository, ShFolderRepository shFolderRepository,
                              ShBootstrapSiteProvider bootstrapSiteProvider,
                              ObjectMapper objectMapper,
                              ShFolderWriteService shFolderWriteService) {
        this.shSiteRepository = shSiteRepository;
        this.shFolderRepository = shFolderRepository;
        this.bootstrapSiteProvider = bootstrapSiteProvider;
        this.objectMapper = objectMapper;
        this.shFolderWriteService = shFolderWriteService;
    }

    @Transactional
    public ShSite create(ShSite payload, String owner) {
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
        // SH591 — carried from the create and settable nowhere else. A flag anybody could raise
        // later would make `shio site rm --scratch` a way to bulk-delete a site somebody chose to
        // keep; the guarantee it rests on is that the declaration preceded the content.
        site.setScratch(payload.isScratch() ? Boolean.TRUE : null);
        site.setFurl(hasText(payload.getFurl()) ? payload.getFurl() : slugify(payload.getName()));
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
    public static final List<String> FILLABLE = List.of("url", "description", "furl");

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
            default -> null;
        };
    }

    private static void write(ShSite site, String field, String value) {
        switch (field) {
            case "url" -> site.setUrl(value);
            case "description" -> site.setDescription(value);
            case "furl" -> site.setFurl(value);
            default -> throw new IllegalStateException("Unfillable site field " + field);
        }
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
     * Deletes a site and everything in it (SH454).
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
    public boolean delete(String id, String actor) {
        Optional<ShSite> found = shSiteRepository.findById(id);
        if (found.isEmpty()) {
            return false;
        }
        for (String rootFolderId : rootFolderIds(found.get())) {
            // Trash first because `purge` operates *on* the trash and refuses a live folder — the
            // two halves are one gesture here, since a site delete has no undo to preserve.
            shFolderWriteService.delete(rootFolderId, actor);
            shFolderWriteService.purge(rootFolderId);
        }
        return shSiteRepository.findById(id).map(site -> {
            shSiteRepository.delete(site);
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

    private static String slugify(String value) {
        if (value == null) {
            return null;
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return normalized.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-)|(-$)", "");
    }
}
