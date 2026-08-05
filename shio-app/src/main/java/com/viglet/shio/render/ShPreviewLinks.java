/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import com.viglet.shio.api.agent.ShAddress;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;

/**
 * SH336 — makes a previewed site walkable.
 *
 * <h2>The defect</h2>
 *
 * <p>{@code {{link}}} is a post's friendly URL — {@code /contato} — and the page is served at
 * {@code /preview/Demo/contato}. So every internal link in a previewed page pointed <em>outside</em>
 * the preview and answered 404. It compounded SH334: the Universal Editor frames the preview, so
 * with no picker <em>and</em> no working links exactly one page of a site was reachable by any
 * means.</p>
 *
 * <p><b>And the lint could not see it.</b> {@code verify}'s {@code routes} check resolves a link
 * against content friendly URLs, where {@code /contato} is correct — from the content's point of
 * view nothing is wrong. The breakage lives in the preview's own address space, which no check
 * models. Worse, the inversion is active rather than merely absent: a hand-written
 * {@code /preview/Demo/home} link, one that actually <em>works</em> in the preview, is reported
 * {@code link-unresolved}, while every link that is really broken passes.</p>
 *
 * <h2>Why this rewrite and not a preview-aware {@code {{link}}}</h2>
 *
 * <p>Making the helper preview-aware is the one option that is <b>not</b> available. It is
 * content-facing, so the same field would mean two different things in two renders — precisely the
 * coupling SH187 spent a task splitting apart.</p>
 *
 * <p>Applied beside {@link ShPreviewBanner}, in {@code ShRenderAPI.page} only. {@code /agent/render}
 * calls {@link ShPageRenderService#render} directly, so nothing injected at this layer is ever
 * hashed and the SH101 digest does not move — checked, because the objection raised against this
 * design was exactly that it would, and it was wrong on the facts.</p>
 *
 * <h2>The rule: only a URL this site actually publishes</h2>
 *
 * <p>Rewriting every root-relative {@code href} would break the page. {@code {{file_url}}} emits
 * {@code /file_source/<id>/<name>}, which is a real server path; SH141's declared
 * {@code frontend-routes} are real too; and an external site's own {@code /api/...} is not ours to
 * touch. So the pass rewrites a link <b>only when its path matches a URL in this site's index</b> —
 * reusing the index {@code routes} already builds rather than inventing a rule. Anything else is
 * left exactly as the author wrote it, which is also what makes this safe to run on 2018 content
 * with no author change.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShPreviewLinks {

    /** Attributes that carry a navigable URL. Deliberately not `src`: see the class note. */
    private static final List<String> NAVIGABLE = List.of("href");

    /**
     * And the one that carries an asset (SH437).
     *
     * <p>The class note above says `src` is deliberately excluded, and it was right about the index
     * it had: `{{file_url}}` emits `/file_source/<id>/<name>`, a real server path that must not be
     * touched, and page URLs are not what an `<img>` points at. What changed is that a site-relative
     * asset path now <em>resolves</em> — {@link ShSiteAssets} answers "is there a `File` post at
     * `/img/hero.png` in this site" — so `src` gets the same rule the class already had, against a
     * different index: rewrite only what this site actually serves, leave everything else exactly as
     * the author wrote it. That is what makes a fidelity replica's images load without a single byte
     * of its captured markup being edited (SH429's option A).</p>
     */
    private static final List<String> ASSET = List.of("src");

    /**
     * How many of this site's URLs the editor bridge is handed (SH336's second mechanism).
     *
     * <p>The browser half needs the same index the server pass used, because it cannot otherwise
     * tell a page from {@code /file_source/…}. Bounded because it rides in the page: above this
     * the attribute is omitted and click interception simply does not run, which is the honest
     * degradation — the server pass still rewrote every link that was in the HTML, so what is lost
     * is only the JS-built ones on a site large enough for the list to be the wrong mechanism.</p>
     */
    static final int BRIDGE_URL_CAP = 500;

    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShSiteAssets shSiteAssets;

    public ShPreviewLinks(ShPostUnifiedRepository shPostUnifiedRepository, ShSiteAssets shSiteAssets) {
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shSiteAssets = shSiteAssets;
    }

    /**
     * Rewrites this page's internal links into the preview's address space.
     *
     * @param html    the rendered page
     * @param siteKey the key the preview is being served under, so a rewritten link keeps the
     *                caller's own spelling of the site rather than swapping it for the name
     * @param page    what was rendered — its site and the state it was rendered in
     * @return the same HTML with resolvable internal links prefixed, and everything else untouched
     */
    public String rewrite(String html, String siteKey, ShPageRenderService.ShRenderedPage page) {
        if (html == null || html.isBlank() || page == null || page.siteId() == null) {
            return html;
        }
        Set<String> known = publishedUrls(page.siteId(), page.state());
        String prefix = ShSiteFrontEnd.PREVIEW_BASE
                + URLEncoder.encode(siteKey, StandardCharsets.UTF_8);
        Document document = Jsoup.parse(html);
        document.outputSettings().prettyPrint(false);
        // SH437 — one lookup per *distinct* path, not per element. A fidelity page is the whole
        // source document, so forty `<img>` tags sharing one sprite is the ordinary case and a query
        // per attribute would make the rewrite cost the page rather than the assets.
        Map<String, Boolean> assets = new HashMap<>();
        for (String attribute : ASSET) {
            for (Element element : document.select("[" + attribute + "]")) {
                String value = element.attr(attribute);
                String rewritten = rewriteAsset(value, prefix, page, assets);
                if (rewritten != null) {
                    element.attr(attribute, rewritten);
                }
            }
        }
        // The empty index used to short-circuit the whole method. It cannot any more: a site with no
        // page URLs can still hold assets, and this loop already does nothing when `known` is empty.
        for (String attribute : NAVIGABLE) {
            for (Element element : document.select("[" + attribute + "]")) {
                String value = element.attr(attribute);
                String rewritten = rewriteOne(value, known, prefix);
                if (rewritten != null) {
                    element.attr(attribute, rewritten);
                }
            }
        }
        return document.html();
    }

    /**
     * This site's URLs for the browser half, or empty when there are too many to ship.
     *
     * <p>The same index, so the two mechanisms cannot come to disagree about what a page is —
     * SH260's rule applied to a fact that now has two readers.</p>
     */
    public Set<String> pathsForBridge(ShPageRenderService.ShRenderedPage page) {
        if (page == null || page.siteId() == null) {
            return Set.of();
        }
        Set<String> urls = publishedUrls(page.siteId(), page.state());
        return urls.size() > BRIDGE_URL_CAP ? Set.of() : urls;
    }

    /**
     * One URL, or {@code null} to leave it alone.
     *
     * <p>Only root-relative: an absolute URL belongs to whoever it names (SH337 is how a template
     * builds one deliberately), a protocol-relative {@code //host/x} is absolute too, and a
     * document-relative {@code contato} already resolves correctly under the preview prefix — which
     * is why it must <em>not</em> be touched.</p>
     */
    private String rewriteOne(String value, Set<String> known, String prefix) {
        if (value == null || !value.startsWith("/") || value.startsWith("//")) {
            return null;
        }
        // Already in the preview's space: a hand-written /preview/... link, or a second pass.
        if (value.startsWith(ShSiteFrontEnd.PREVIEW_BASE)) {
            return null;
        }
        int cut = firstOf(value, '?', '#');
        String path = cut < 0 ? value : value.substring(0, cut);
        String tail = cut < 0 ? "" : value.substring(cut);
        return known.contains(ShAddress.normalizePath(path)) ? prefix + path + tail : null;
    }

    /**
     * One {@code src}, or {@code null} to leave it alone (SH437).
     *
     * <p>Same three exclusions as {@link #rewriteOne} — absolute, protocol-relative and
     * document-relative are all somebody else's or already correct — plus one this rule needs and
     * that one does not: {@code /file_source/…} is where {@code {{file_url}}} already points, so a
     * template that resolved an asset properly must not have its work re-pointed at a path that
     * would have to resolve back to the same bytes.</p>
     */
    private String rewriteAsset(String value, String prefix,
                                ShPageRenderService.ShRenderedPage page, Map<String, Boolean> seen) {
        if (value == null || !value.startsWith("/") || value.startsWith("//")
                || value.startsWith(ShSiteFrontEnd.PREVIEW_BASE)
                || value.startsWith("/file_source/")) {
            return null;
        }
        int cut = firstOf(value, '?', '#');
        String path = cut < 0 ? value : value.substring(0, cut);
        String tail = cut < 0 ? "" : value.substring(cut);
        boolean served = seen.computeIfAbsent(path, candidate ->
                shSiteAssets.findInSite(page.siteId(), candidate, page.state()).isPresent());
        return served ? prefix + path + tail : null;
    }

    private static int firstOf(String value, char... any) {
        int found = -1;
        for (char candidate : any) {
            int at = value.indexOf(candidate);
            if (at >= 0 && (found < 0 || at < found)) {
                found = at;
            }
        }
        return found;
    }

    /**
     * The site's URLs, in the state this page was rendered in.
     *
     * <p>State-matched on purpose: a DRAFT preview is for walking work in progress, and linking to
     * a page that only exists as a draft is correct there and would be a broken link in a PUBLISHED
     * preview. One projection query, no entities — the same shape {@code verify} uses.</p>
     */
    private Set<String> publishedUrls(String siteId, ShPostState state) {
        Collection<ShPostState> states = state == ShPostState.PUBLISHED
                ? List.of(ShPostState.PUBLISHED)
                : List.of(ShPostState.DRAFT, ShPostState.PUBLISHED);
        Set<String> urls = new LinkedHashSet<>();
        for (Object[] row : shPostUnifiedRepository.findUrlIndexBySiteId(siteId, states)) {
            String furl = (String) row[1];
            if (furl != null && !furl.isBlank()) {
                urls.add(ShAddress.normalizePath(furl));
            }
        }
        return urls;
    }
}
