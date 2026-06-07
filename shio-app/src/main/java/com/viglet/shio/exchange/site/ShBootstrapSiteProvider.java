/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.exchange.site;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import com.viglet.shio.exchange.ShExchangePackageCodec;
import com.viglet.shio.exchange.model.ShExchangePackage;
import com.viglet.shio.exchange.model.ShExchangeSite;
import com.viglet.shio.persistence.model.site.ShSite;

/**
 * Provisions a newly-created site with the bundled <strong>bootstrap-site</strong>
 * starter (SH71) — restoring the pre-nuclear-refactor behaviour where creating a
 * site cloned a full starter structure (post-types, folders, sample pages) instead
 * of leaving it blank.
 *
 * <p>The template is a versioned, in-repo exchange package at
 * {@code classpath:/import/bootstrap-site/export.json} (SH68 format). On site
 * creation, {@link ShSiteWriteService} asks this provider to clone it via the SH70
 * import path, after rewriting the template site's metadata (name/description/url/
 * furl/owner) with the user's input so the cloned site carries the user's identity
 * but the template's content tree.</p>
 *
 * <p><strong>Best-effort, never fatal:</strong> a missing or unreadable template,
 * or any clone failure, returns {@code null} so the caller falls back to the plain
 * site + empty root folder. Site creation is never blocked on the template.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Service
public class ShBootstrapSiteProvider {

    private static final Logger log = LoggerFactory.getLogger(ShBootstrapSiteProvider.class);
    static final String TEMPLATE_RESOURCE = "classpath:/import/bootstrap-site/export.json";

    private final ShExchangePackageCodec codec;
    private final ShSiteImportService shSiteImportService;
    private final ResourceLoader resourceLoader;

    public ShBootstrapSiteProvider(ShExchangePackageCodec codec,
                                   ShSiteImportService shSiteImportService,
                                   ResourceLoader resourceLoader) {
        this.codec = codec;
        this.shSiteImportService = shSiteImportService;
        this.resourceLoader = resourceLoader;
    }

    /**
     * Clone the bootstrap template into a new site carrying {@code payload}'s
     * metadata, or {@code null} if the template is absent / unreadable / fails to
     * clone (the caller then provisions a plain empty site).
     */
    public ShSite provision(ShSite payload, String owner) {
        ShExchangePackage template = loadTemplate();
        if (template == null || template.sites().isEmpty()) {
            return null;
        }
        try {
            return shSiteImportService.importPackage(withSiteMetadata(template, payload, owner), owner);
        } catch (RuntimeException e) {
            log.warn("Bootstrap site template clone failed; falling back to an empty site", e);
            return null;
        }
    }

    private ShExchangePackage loadTemplate() {
        Resource resource = resourceLoader.getResource(TEMPLATE_RESOURCE);
        if (!resource.exists()) {
            return null;
        }
        try (InputStream in = resource.getInputStream()) {
            return codec.readManifest(in);
        } catch (Exception e) {
            log.warn("Could not read bootstrap site template {}", TEMPLATE_RESOURCE, e);
            return null;
        }
    }

    /** Replace the template's first site's display metadata with the user's input. */
    private ShExchangePackage withSiteMetadata(ShExchangePackage template, ShSite payload, String owner) {
        ShExchangeSite tpl = template.sites().get(0);
        ShExchangeSite overridden = new ShExchangeSite(
                tpl.id(),
                payload.getName(),
                payload.getDescription(),
                payload.getUrl(),
                payload.getFurl(), // blank → import slugs the name
                tpl.postTypeLayout(),
                tpl.searchablePostTypes(),
                tpl.formSuccess(),
                owner,
                tpl.rootFolderIds());

        List<ShExchangeSite> sites = new ArrayList<>(template.sites());
        sites.set(0, overridden);
        return new ShExchangePackage(template.version(), template.exportedAt(), template.generator(),
                sites, template.postTypes(), template.folders(), template.posts(), template.files());
    }
}
