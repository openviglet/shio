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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.exchange.site.ShBootstrapSiteProvider;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;

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

    public ShSiteWriteService(ShSiteRepository shSiteRepository, ShFolderRepository shFolderRepository,
                              ShBootstrapSiteProvider bootstrapSiteProvider) {
        this.shSiteRepository = shSiteRepository;
        this.shFolderRepository = shFolderRepository;
        this.bootstrapSiteProvider = bootstrapSiteProvider;
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
        ShSiteFill fill = planFill(site, values);
        if (!fill.changed()) {
            return fill;
        }
        for (String field : fill.filled()) {
            write(site, field, values.get(field));
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
        return new ShSiteFill(List.copyOf(filled), List.copyOf(kept));
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

    @Transactional
    public boolean delete(String id) {
        return shSiteRepository.findById(id).map(site -> {
            // Entity delete (not the JPQL bulk delete) so the cascade reaches the
            // site's folders via ShSite#shFolders (CascadeType.ALL).
            shSiteRepository.delete(site);
            return true;
        }).orElse(false);
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
