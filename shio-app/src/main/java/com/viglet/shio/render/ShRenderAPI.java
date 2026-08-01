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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

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
    private final ShEditorBridge editorBridge;
    private final ShPreviewLinks previewLinks;

    public ShRenderAPI(ShPageRenderService shPageRenderService, ShPreviewBanner banner,
                       ShEditorBridge editorBridge, ShPreviewLinks previewLinks) {
        this.shPageRenderService = shPageRenderService;
        this.banner = banner;
        this.editorBridge = editorBridge;
        this.previewLinks = previewLinks;
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
                                       @RequestParam(defaultValue = "false") boolean editor,
                                       HttpServletRequest request) {
        ShRenderedPage rendered = shPageRenderService.render(siteKey, pathWithin(siteKey, request),
                ShPostState.PUBLISHED.name().equalsIgnoreCase(state)
                        ? ShPostState.PUBLISHED
                        : ShPostState.DRAFT,
                readerParams(request));
        // SH185 — the editor bridge is opt-in per request. An ordinary preview must ship no
        // editor code at all: a script that makes elements contenteditable is not something to
        // leave lying in a page somebody might screenshot, and "off unless asked" is cheaper
        // to reason about than "on unless the parent says no".
        // SH336 — internal links into the preview's own address space, before the banner so the
        // banner's own markup is never a subject of the rewrite. Preview only: `/agent/render`
        // does not come through here, so the SH101 digest cannot move.
        String walkable = previewLinks.rewrite(rendered.html(), siteKey, rendered);
        ShRenderedPage page = new ShRenderedPage(walkable, rendered.site(), rendered.url(),
                rendered.layout(), rendered.regions(), rendered.state(), rendered.postId(),
                rendered.siteId());
        String body = editor
                ? editorBridge.inject(banner.wrap(page), originOf(request),
                        previewLinks.pathsForBridge(rendered))
                : banner.wrap(page);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                // Store nothing, anywhere. See the class note: a freshness lifetime is how
                // this stops being a preview.
                .cacheControl(CacheControl.noStore().mustRevalidate())
                .header("X-Robots-Tag", "noindex, nofollow")
                .header("X-Shio-Render-Layout", rendered.layout())
                .body(body);
    }

    /**
     * {@code GET /preview/_shio/editor.js} — the page half of the bridge (SH185).
     *
     * <p>Served here rather than through {@code ShStaticResourceConfiguration} because it
     * belongs to the renderer that injects it: one place decides both that the script exists
     * and when a page gets it. It is {@code no-store} like the pages that reference it —
     * caching an editor script against a page that is deliberately uncacheable buys a version
     * skew and nothing else.</p>
     */
    @GetMapping(value = "/_shio/editor.js", produces = "application/javascript")
    public ResponseEntity<String> editorScript() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Robots-Tag", "noindex, nofollow")
                .body(editorBridge.script());
    }

    /**
     * The origin that will be told about, and checked against, on both sides of the bridge.
     *
     * <p>The console frames this page, so the parent's origin is the one that requested it.
     * Deriving it from the request rather than from configuration means it is right behind a
     * proxy, on a developer's {@code localhost:5173} and in production without anybody
     * setting a property — and the alternative, {@code "*"}, is what SH185 exists partly to
     * remove.</p>
     */
    private String originOf(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin != null && !origin.isBlank()) {
            return origin;
        }
        String scheme = request.getScheme();
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(scheme) && port == 80)
                || ("https".equals(scheme) && port == 443);
        return scheme + "://" + request.getServerName() + (defaultPort ? "" : ":" + port);
    }

    /**
     * The page's friendly URL: everything after {@code /preview/<site>}.
     *
     * <p>Taken from the matched pattern rather than from a {@code @PathVariable}, because a
     * friendly URL contains slashes and {@code {path}} would capture one segment. An empty
     * remainder is the site's root page, {@code "/"} — which is the URL a curator means by
     * "show me the site".</p>
     */
    /**
     * The reader's query string, as the SH259 {@code param} frame sees it.
     *
     * <p>Two exclusions, both deliberate. {@code state} and {@code editor} are the <b>renderer's
     * own</b> parameters, so passing them through would let a page read the flags that control how
     * it is being rendered — a template that branches on {@code param.editor} is a page that looks
     * different in the editor from the way it looks to a reader, which is the whole thing the
     * Universal Editor is trying not to be. And a repeated parameter is flattened to its
     * <b>first</b> value rather than a list: the frame is strings, `?q=a&amp;q=b` is a caller
     * mistake rather than a shape templates should have to handle, and picking the first is the
     * only choice that is stable (P5).</p>
     */
    // Package-private: the two exclusions above are rules worth a test, and the alternative
    // was a MockMvc harness for a method that reads a map.
    static Map<String, String> readerParams(HttpServletRequest request) {
        Map<String, String> params = new LinkedHashMap<>();
        request.getParameterMap().forEach((name, values) -> {
            if (RENDERER_PARAMS.contains(name) || values == null || values.length == 0) {
                return;
            }
            params.put(name, values[0]);
        });
        return params;
    }

    /** The two the renderer reads itself — see {@link #readerParams}. */
    private static final Set<String> RENDERER_PARAMS = Set.of("state", "editor");

    private String pathWithin(String siteKey, HttpServletRequest request) {
        Object attribute = request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String full = attribute == null ? "" : attribute.toString();
        String prefix = "/preview/" + siteKey;
        String rest = full.startsWith(prefix) ? full.substring(prefix.length()) : "";
        return rest.isBlank() ? "/" : rest;
    }
}
