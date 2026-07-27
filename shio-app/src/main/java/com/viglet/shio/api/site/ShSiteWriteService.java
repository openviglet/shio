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
