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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.MediaType;
import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import com.viglet.shio.api.post.ShContentPerspective;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.spring.security.auth.ShPreviewShare;
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
    private final ShSiteAssetDelivery assetDelivery;

    /** Never indexed, whether it renders a page or answers with a redirect (SH385). */
    private static final String ROBOTS = "X-Robots-Tag";
    private static final String NO_INDEX = "noindex, nofollow";

    public ShRenderAPI(ShPageRenderService shPageRenderService, ShPreviewBanner banner,
                       ShEditorBridge editorBridge, ShPreviewLinks previewLinks,
                       ShSiteAssetDelivery assetDelivery) {
        this.shPageRenderService = shPageRenderService;
        this.banner = banner;
        this.editorBridge = editorBridge;
        this.previewLinks = previewLinks;
        this.assetDelivery = assetDelivery;
    }

    /**
     * The same path, when no page answers to it: an <b>asset</b> (SH437).
     *
     * <h2>Why one mapping with a fallback and not two mappings</h2>
     *
     * <p>Two {@code @GetMapping("/{siteKey}/**")} differing only by {@code produces} was the
     * obvious shape and it is wrong: a caller sending {@code Accept: *}/{@code *} — which is every
     * programmatic client and most {@code <img>} requests — matches both, Spring picks one, and
     * which one is not a decision this class made. Measured: the home page started answering
     * {@code render-asset-unknown} for {@code /}. So the route has one entry, pages are its primary
     * meaning, and an asset is what it tries when the page lookup says there is nothing there. The
     * cost is one failed post lookup per asset request; the alternative is a content-negotiation
     * outcome that changes with the caller's headers.</p>
     *
     * <h2>Which 404 a caller gets when neither answers</h2>
     *
     * <p>The <b>page's</b>, because a path with no page and no asset is far more often a page
     * somebody meant to write than an image somebody meant to upload, and {@code render-page-unknown}
     * names the call that lists what the site does hold. The asset's own teaching 404 is reserved
     * for the case that reached the resolver and missed there.</p>
     *
     * <h2>What it is not</h2>
     *
     * <p>Not a second file API. {@code /file_source/<id>/<name>} stays exactly as it is and is
     * still where {@code file_url} points; this resolves the <em>site's own</em> path to the same
     * post and hands off to the same storage. The whole feature is the addressing.</p>
     */
    /**
     * SH1042 — whether this request is a share link reaching for a site it was not minted for.
     *
     * <p>{@code false} for every caller that is not a share, and the site is resolved only for one
     * that is — so a curator's preview costs no extra query. An unknown key is <b>not</b> refused
     * here: it is left to the render, which answers the same 404 it always did rather than letting
     * this path tell a prober which keys exist.</p>
     */
    private boolean shareRefused(String siteKey) {
        if (!ShPreviewShare.isShare()) {
            return false;
        }
        return shPageRenderService.siteIdOf(siteKey)
                .map(siteId -> !ShPreviewShare.allows(siteId))
                // An unknown key is not refused here: the render answers the same 404 it always
                // did, and deciding earlier would let this path tell a prober which keys exist.
                .orElse(false);
    }

    private ResponseEntity<?> assetOr(RuntimeException pageProblem, String siteKey, String path,
                                      ShPostState state) {
        try {
            return assetDelivery.serve(siteKey, path, state);
        } catch (ShSiteAssetDelivery.UnknownAssetException ignored) {
            throw pageProblem;
        }
    }

    /**
     * {@code GET /preview/{site}/{path…}} — one rendered page.
     *
     * @param siteKey the site's name, friendly URL or id (SH181's order — a human types this)
     * @param state   {@code draft} (the default: previewing means seeing unpublished edits),
     *                {@code published} to see what a visitor would get, or {@code rev:<n>} to see
     *                what the page said at a captured revision. SH990 — parsed by
     *                {@link ShContentPerspective}, so a value outside the grammar is a teaching 400;
     *                before, anything but {@code PUBLISHED} previewed the draft without saying so
     */
    @GetMapping("/{siteKey}/**")
    public ResponseEntity<?> page(@PathVariable String siteKey,
                                  @RequestParam(required = false) String state,
                                  @RequestParam(defaultValue = "false") boolean editor,
                                  HttpServletRequest request) {
        ShContentPerspective perspective = ShContentPerspective.parse(state)
                .requireVersion("/preview", "GET /api/v2/agent/read?address=<address>&state=release:<name>");
        // SH1042 — a share link is bounded to one site, and this is where the site is known. The
        // filter that authenticated the cookie saw only a path, whose first segment may be an id, a
        // friendly URL or a name; resolving it there would be a second opinion about SH181's order
        // in front of every preview request. 404 rather than 403, for the reason
        // ShAuthenticationEntryPoint gives about an absent path: 403 tells the holder of one site's
        // link that another site exists.
        if (shareRefused(siteKey)) {
            return ResponseEntity.notFound().build();
        }
        // An asset has no revisions, so a revision preview that lands on one serves the draft's bytes.
        ShPostState wanted = perspective.rowOr(ShPostState.DRAFT);
        // SH1110 — and to the page it was minted for. Outside that path a share still reaches the
        // site's own assets, because that is how the shared page's stylesheet and pictures arrive
        // (its images are /file_source, public and outside the cookie's path; its theme is inlined),
        // and an asset is a file the site serves rather than a draft somebody is writing. A page is
        // refused with the site bound's 404, for the site bound's reason.
        String path = pathWithin(siteKey, request);
        if (!ShPreviewShare.allowsPath(path)) {
            return assetOr(new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND), siteKey, path, wanted);
        }
        ShRenderedPage rendered;
        try {
            rendered = shPageRenderService.render(siteKey, pathWithin(siteKey, request), perspective,
                    readerParams(request));
        } catch (ShPageRenderService.UnknownPageException noPage) {
            // SH437 — the same path, as an asset. See `assetOr`: one mapping with a fallback,
            // because two mappings differing only by `produces` let the caller's Accept header
            // decide which of them a request reaches.
            return assetOr(noPage, siteKey, pathWithin(siteKey, request), wanted);
        } catch (ShPageRenderService.ShPageRedirectException redirect) {
            // SH385 — the *proof* has to cover the URL space, which is why the redirect is honoured
            // here and not only by whatever serves the site in production. A replication's whole claim
            // is "it renders the same", and a preview that 404'd on every old path would leave the one
            // thing a replica most often gets wrong untested.
            return redirectTo(siteKey, redirect, request);
        }
        // SH185 — the editor bridge is opt-in per request. An ordinary preview must ship no
        // editor code at all: a script that makes elements contenteditable is not something to
        // leave lying in a page somebody might screenshot, and "off unless asked" is cheaper
        // to reason about than "on unless the parent says no".
        // SH336 — internal links into the preview's own address space, before the banner so the
        // banner's own markup is never a subject of the rewrite. Preview only: `/agent/render`
        // does not come through here, so the SH101 digest cannot move.
        // SH512 — and the path the reader asked for, which is what a document-relative reference
        // resolves against. The *request's* path rather than the post's furl: they differ by a
        // trailing slash, and that slash is one level of `../`.
        // SH1036 — and the renderer's own parameters onto every link, so a click does not end edit
        // mode. `?editor=true` is what injects the bridge, so a link that dropped it landed the
        // frame on a page with no bridge at all — and the console, which keys its handshake on an
        // iframe `src` that in-frame navigation cannot change, went on reporting `connected`.
        String walkable = previewLinks.rewriteInto(rendered.html(),
                ShSiteFrontEnd.previewPrefix(siteKey),
                rendered, pathWithin(siteKey, request), rendererParams(request));
        ShRenderedPage page = new ShRenderedPage(walkable, rendered.site(), rendered.url(),
                rendered.layout(), rendered.regions(), rendered.state(), rendered.postId(),
                rendered.siteId(), rendered.composition(), rendered.revision());
        // SH1042 — and never for a share reader, whatever the link says. The bridge makes elements
        // contenteditable and opens a postMessage channel to whatever framed the page; a recipient
        // outside the console is exactly who must not have it, and `?editor=true` is a query string
        // anybody can type. The banner stays: a shared draft that did not say it was a draft is the
        // screenshot that ends up in a meeting as if it were the site.
        String body = editor && !ShPreviewShare.isShare()
                ? editorBridge.inject(banner.wrap(page), originOf(request),
                        previewLinks.pathsForBridge(rendered))
                : banner.wrap(page);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                // Store nothing, anywhere. See the class note: a freshness lifetime is how
                // this stops being a preview.
                .cacheControl(CacheControl.noStore().mustRevalidate())
                .header(ROBOTS, NO_INDEX)
                .header("X-Shio-Render-Layout", rendered.layout())
                .body(body);
    }

    /**
     * A redirect, as HTTP (SH385).
     *
     * <h2>301 or 302, and nothing else</h2>
     *
     * <p>Permanent is what a replication almost always means — the old path is gone and a search engine
     * should move its index — but it is opt-in rather than the default, because a 301 is <b>cached by
     * browsers effectively for ever</b> and a wrong one is the hardest routing mistake to take back.
     * SH121's shape applied to a status code: the safe answer is the default and the expensive one is
     * asked for.</p>
     *
     * <h2>The target is rewritten into the preview's own address space</h2>
     *
     * <p>A relative {@code TO} names a friendly URL in this site, so the {@code Location} is that page's
     * preview path — {@code ShPreviewLinks}' rule, which exists so a reader clicking through a preview
     * stays inside it (SH336). An absolute {@code TO} is left exactly as written: a path that now lives
     * on another host is a legitimate redirect, and rewriting it into {@code /preview/<site>/https://…}
     * would be a broken link built out of a working one.</p>
     *
     * <h2>Cache nothing, and say so</h2>
     *
     * <p>The same headers the page carries, for the same reason: a preview that told a browser to
     * remember a 301 would leave the reader unable to see the page even after the redirect was deleted.
     * {@code X-Robots-Tag} too — a preview redirect must not teach a crawler anything.</p>
     *
     * <h2>And the reader's query survives it (SH467)</h2>
     *
     * <p>{@code ShSitesAPI.withQueryOf}, shared rather than copied. It matters here for one reason the
     * public route does not have: a preview's own {@code ?state=} and {@code ?editor=} are in that
     * query, so a redirect that dropped it would answer the next page in a different mode than the one
     * the reader was in.</p>
     */
    private ResponseEntity<String> redirectTo(String siteKey,
                                              ShPageRenderService.ShPageRedirectException redirect,
                                              HttpServletRequest request) {
        String to = redirect.to();
        boolean absolute = to.startsWith("http://") || to.startsWith("https://");
        String within = to.startsWith("/") ? to : "/" + to;
        String location = absolute ? to : "/preview/" + siteKey + within;
        return ResponseEntity
                .status(redirect.permanent() ? HttpStatus.MOVED_PERMANENTLY : HttpStatus.FOUND)
                .location(URI.create(ShSitesAPI.withQueryOf(request, location)))
                .cacheControl(CacheControl.noStore().mustRevalidate())
                .header(ROBOTS, NO_INDEX)
                .build();
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
                .header(ROBOTS, NO_INDEX)
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

    /**
     * The inverse of {@link #readerParams}: the reader's mode, as a query fragment (SH1036).
     *
     * <p>Derived from {@link #RENDERER_PARAMS} rather than listed again, which is the point of
     * putting it beside that set: the two methods split one request's query in two, and a third
     * spelling of "which parameters are the renderer's" is how they come to disagree. Ordered by
     * that set's iteration, then sorted, so the rewritten HTML is byte-stable across requests (P5)
     * — a preview is uncacheable but its digest is compared.</p>
     */
    static String rendererParams(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        List<String> pairs = new ArrayList<>();
        for (String name : new java.util.TreeSet<>(RENDERER_PARAMS)) {
            String value = request.getParameter(name);
            if (value != null && !value.isBlank()) {
                pairs.add(name + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8));
            }
        }
        return pairs.isEmpty() ? null : String.join("&", pairs);
    }

    /**
     * The page path after the site segment.
     *
     * <p>SH1322 — cut at the first segment, never matched against {@code siteKey}'s spelling. The
     * handler path keeps the segment as the URL encoded it ({@code Console%20Check}) while the
     * variable arrives decoded ({@code Console Check}), so the prefix never matched for a site whose
     * name needs encoding: every page of it previewed as the home, and a share link, bounded to its
     * one page, answered 404 on every page including its own.</p>
     */
    private String pathWithin(String siteKey, HttpServletRequest request) {
        Object attribute = request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String full = attribute == null ? "" : attribute.toString();
        String base = "/preview/";
        if (!full.startsWith(base)) {
            return "/";
        }
        int slash = full.indexOf('/', base.length());
        String rest = slash < 0 ? "" : full.substring(slash);
        return rest.isBlank() ? "/" : rest;
    }
}
