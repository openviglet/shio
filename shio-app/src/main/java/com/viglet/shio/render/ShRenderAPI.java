/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.render.ShPageRenderService.ShRenderedPage;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

/**
 * SH183 — the route where a page becomes something a person can look at.
 *
 * <h2>What it is for</h2>
 *
 * <p>Applying the {@code marketing-site} blueprint produces thirteen correct pages and,
 * until this route existed, no way to see one: the preview minted a URL against
 * {@code site.url} and 400'd without one, and the Universal Editor is an
 * {@code <iframe src={site.url}>}. Both human-facing visual surfaces pointed outward at a
 * front end only {@code shio init} can scaffold and only a developer's machine can run.
 * This renders the page from content the instance already holds, with no client setup at
 * all — which is also the only path an MCP-only agent can reach (SH186).</p>
 *
 * <h2>What it is deliberately not</h2>
 *
 * <p>Production delivery is Next.js over the CDA and stays there (P10). A preview URL that
 * renders production content <em>becomes</em> a delivery path the moment somebody points
 * DNS at it, and then Shio owns HTML rendering, caching and SEO — a position this project
 * declined on purpose. Three things keep that from happening by accident rather than by
 * decision:</p>
 *
 * <ul>
 *   <li>a <b>distinct route</b> — {@code /preview/**}, not the site's own paths;</li>
 *   <li>a <b>banner</b> on every page, which is also the P7 courtesy: nobody should have
 *       to work out whether what they are looking at is live;</li>
 *   <li><b>no freshness headers</b>. {@code no-store} plus {@code X-Robots-Tag: noindex}
 *       means a CDN in front of this caches nothing and a crawler indexes nothing. A
 *       {@code max-age} here is how a preview route quietly becomes infrastructure.</li>
 * </ul>
 *
 * <p>It sits under {@code anyRequest().authenticated()} like the rest of the console, so a
 * draft is never public. That is the floor, not the ceiling: SH184 is where the site's own
 * URL and the SH5 preview-token path get reconciled with it.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@RestController
@RequestMapping("/preview")
@Tag(name = "Preview", description = "Server-rendered pages (page → layout → region)")
public class ShRenderAPI {

    private final ShPageRenderService shPageRenderService;
    private final ShPreviewBanner banner;

    public ShRenderAPI(ShPageRenderService shPageRenderService, ShPreviewBanner banner) {
        this.shPageRenderService = shPageRenderService;
        this.banner = banner;
    }

    /**
     * {@code GET /preview/{site}/{path…}} — one rendered page.
     *
     * @param siteKey the site's name, friendly URL or id (SH181's order — a human types this)
     * @param state   {@code DRAFT} (the default: previewing means seeing unpublished edits)
     *                or {@code PUBLISHED} to see what a visitor would get
     */
    @GetMapping(value = "/{siteKey}/**", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> page(@PathVariable String siteKey,
                                       @RequestParam(defaultValue = "DRAFT") String state,
                                       HttpServletRequest request) {
        ShRenderedPage rendered = shPageRenderService.render(siteKey, pathWithin(siteKey, request),
                ShPostState.PUBLISHED.name().equalsIgnoreCase(state)
                        ? ShPostState.PUBLISHED
                        : ShPostState.DRAFT);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                // Store nothing, anywhere. See the class note: a freshness lifetime is how
                // this stops being a preview.
                .cacheControl(CacheControl.noStore().mustRevalidate())
                .header("X-Robots-Tag", "noindex, nofollow")
                .header("X-Shio-Render-Layout", rendered.layout())
                .body(banner.wrap(rendered));
    }

    /**
     * The page's friendly URL: everything after {@code /preview/<site>}.
     *
     * <p>Taken from the matched pattern rather than from a {@code @PathVariable}, because a
     * friendly URL contains slashes and {@code {path}} would capture one segment. An empty
     * remainder is the site's root page, {@code "/"} — which is the URL a curator means by
     * "show me the site".</p>
     */
    private String pathWithin(String siteKey, HttpServletRequest request) {
        Object attribute = request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String full = attribute == null ? "" : attribute.toString();
        String prefix = "/preview/" + siteKey;
        String rest = full.startsWith(prefix) ? full.substring(prefix.length()) : "";
        return rest.isBlank() ? "/" : rest;
    }
}
