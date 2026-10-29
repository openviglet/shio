/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.staticfile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.agent.ShAddress;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;
import com.viglet.shio.render.ShPageRenderService;
import com.viglet.shio.render.ShPreviewLinks;
import com.viglet.shio.render.ShSiteAssets;

/**
 * SH1280 — which assets a site's rendered pages load, and how many pages load each.
 *
 * <h2>Why a second count beside {@code usageCount}</h2>
 *
 * <p>{@code usageCount} (SH1034) is the reference index, which File and Content Select fields feed.
 * A theme's {@code @font-face} or a layout's CSS background is in no field, so a font every page
 * fetches counted zero, and {@code shio assets prune} listed Quilhara's three fonts as reclaimable
 * with the instruction to delete them. {@code verify} had stopped saying so in SH1268, by reading
 * what rendered pages request; this is that reading as a count, so the listing, prune and verify
 * mean one thing by "used".</p>
 *
 * <h2>The same walk as verify's page check</h2>
 *
 * <p>The same subjects (published posts whose type the site binds a layout for), the same render
 * ({@link ShPageRenderService}, which {@code /sites/**} serves), the same reference list
 * ({@link ShPreviewLinks#referencesIn}) and the same resolution ({@link ShSiteAssets#findInSite}).
 * It costs one render per page, which is why it is computed only when a caller asks
 * ({@code loads=true}) and never on a plain listing. Bounded at {@link #MAX_PAGES} per site, and
 * the answer says how many it rendered: an asset loaded only by a page past the cap would
 * otherwise read as unused, and that is the reading a deletion acts on.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
public class ShAssetLoadService {

    /** Per site. A prune is a deliberate request, so the cap is generous; the count says if it cut. */
    static final int MAX_PAGES = 500;

    private final ShPageRenderService pageRenderService;
    private final ShSiteAssets siteAssets;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShSiteRepository shSiteRepository;

    public ShAssetLoadService(ShPageRenderService pageRenderService, ShSiteAssets siteAssets,
                              ShPostUnifiedRepository shPostUnifiedRepository,
                              ShSiteRepository shSiteRepository) {
        this.pageRenderService = pageRenderService;
        this.siteAssets = siteAssets;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shSiteRepository = shSiteRepository;
    }

    /**
     * What these sites' pages load.
     *
     * @param byAsset  asset id to the number of rendered pages that request it
     * @param rendered how many pages were rendered to know
     * @param pages    how many pages the sites publish; above {@code rendered} when the cap cut
     */
    public record Loads(Map<String, Integer> byAsset, int rendered, int pages) {

        public Loads {
            byAsset = byAsset == null ? Map.of() : java.util.Collections.unmodifiableMap(new TreeMap<>(byAsset));
        }

        /** Whether every page was rendered, so a zero is a fact rather than a gap. */
        public boolean complete() {
            return rendered == pages;
        }
    }

    /**
     * The same count, for the sites these assets belong to: for a caller holding asset ids and not
     * sites, such as {@code shio_assets usage=true}.
     */
    @Transactional(readOnly = true)
    public Loads loadedByAssets(Collection<String> assetIds) {
        Set<String> siteIds = new TreeSet<>();
        for (ShPostUnified asset : shPostUnifiedRepository.findByIdIn(assetIds)) {
            if (asset.getShSite() != null) {
                siteIds.add(asset.getShSite().getId());
            }
        }
        return loadedBy(siteIds);
    }

    @Transactional(readOnly = true)
    public Loads loadedBy(Collection<String> siteIds) {
        Map<String, Integer> byAsset = new TreeMap<>();
        int rendered = 0;
        int pages = 0;
        for (String siteId : new TreeSet<>(siteIds)) {
            ShSite site = siteId == null ? null : shSiteRepository.findById(siteId).orElse(null);
            if (site == null) {
                continue;
            }
            List<ShPostUnified> subjects = pagesOf(site);
            pages += subjects.size();
            List<ShPostUnified> read = subjects.size() > MAX_PAGES ? subjects.subList(0, MAX_PAGES) : subjects;
            for (ShPostUnified page : read) {
                rendered += 1;
                for (String assetId : loadedByPage(site, page)) {
                    byAsset.merge(assetId, 1, Integer::sum);
                }
            }
        }
        return new Loads(byAsset, rendered, pages);
    }

    /** The published posts of this site that are pages, in a stable order. */
    private List<ShPostUnified> pagesOf(ShSite site) {
        Set<String> pageTypes = pageRenderService.layoutBindings(site).keySet();
        List<ShPostUnified> pages = new ArrayList<>();
        for (ShPostUnified post : shPostUnifiedRepository.findByShSite_IdAndStateIn(site.getId(),
                List.of(ShPostState.PUBLISHED))) {
            String type = post.getShPostType() == null ? null : post.getShPostType().getName();
            if (type != null && pageTypes.contains(type) && post.getFurl() != null
                    && !post.getFurl().isBlank() && post.getDeletedDate() == null) {
                pages.add(post);
            }
        }
        pages.sort(Comparator.comparing(ShPostUnified::getFurl).thenComparing(ShPostUnified::getId));
        return pages;
    }

    /** The distinct assets one rendered page requests; none when it does not render. */
    private Set<String> loadedByPage(ShSite site, ShPostUnified page) {
        String url = ShAddress.normalizePath(page.getFurl());
        String html;
        try {
            html = pageRenderService.render(site.getId(), url, ShPostState.PUBLISHED).html();
        } catch (ShPageRenderService.ShPageRedirectException | ShTeachingException cannotRender) {
            // A redirect has no page to read, and a page that does not compose is verify's
            // `page-unrendered`: neither loads anything a visitor receives.
            return Set.of();
        }
        Set<String> paths = new HashSet<>();
        Set<String> assets = new TreeSet<>();
        for (ShPreviewLinks.Reference reference : ShPreviewLinks.referencesIn(html, url)) {
            String path = pathOnly(reference.path());
            if (paths.add(path)) {
                siteAssets.findInSite(site.getId(), path, ShPostState.PUBLISHED)
                        .ifPresent(asset -> assets.add(asset.getId()));
            }
        }
        return assets;
    }

    private static String pathOnly(String path) {
        int query = path.indexOf('?');
        int fragment = path.indexOf('#');
        int cut = query < 0 ? fragment : (fragment < 0 ? query : Math.min(query, fragment));
        return cut < 0 ? path : path.substring(0, cut);
    }
}
