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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * <h2>The rule: every navigable path is this site's, except the instance's own routes (SH466)</h2>
 *
 * <p>SH336 shipped the opposite rule — rewrite a link <b>only when its path matches a URL in this
 * site's index</b>, leave everything else exactly as the author wrote it — on the reasoning that
 * {@code {{file_url}}} emits a real server path, SH141's declared {@code frontend-routes} are real,
 * and an external site's own {@code /api/…} is not ours to touch. Two of those three are about
 * <em>the instance's</em> routes, and the third is a claim about a front end that is not what is
 * serving this page.</p>
 *
 * <p>What the index-match rule actually produced is a page carrying two kinds of {@code href} where
 * only one works. A replicated page held {@code href="/turing/"} — the source's path, verbatim — on
 * a page served from {@code /sites/vigletorg/default/en-us/}; the other five links on it were
 * rewritten. {@code turing} was unpublished at that moment, so it was not in the index and fell
 * through to verbatim, which is <b>the source's base, not this one</b>. The same page under
 * {@code /preview} had all six rewritten, because the preview sees DRAFT. So the output depended on
 * publication state, and the failure appeared exactly when a site is half published.</p>
 *
 * <p>A verbatim root-relative {@code href} is a hardcoded base, and both address spaces this pass
 * writes into — {@code /preview/<site>/} and {@code /sites/<furl>/<format>/<locale>/} — are
 * different from {@code /}. Leaving one alone is not a conservative fallback; it is the one output
 * guaranteed to point somewhere else. So an unresolved path is <b>rebased onto the site anyway</b>:
 * it then 404s <em>inside</em> the replica, where {@code verify}'s {@code routes} check already
 * reports it. A missing page is a finding; a link out of the site is a silent exit.</p>
 *
 * <p>The exception that survives is the one the old rule was really protecting, named directly
 * instead of inferred from an index: a path whose first segment is a route <b>the instance itself</b>
 * serves ({@link #INSTANCE_ROUTES}). {@code /file_source/…} is where {@code {{file_url}}} points and
 * must not move; {@code /sites/…} and {@code /preview/…} are the two prefixes this pass writes, which
 * is also what keeps it idempotent.</p>
 *
 * <p>The href pass therefore no longer consults the URL index at all — which is why it no longer
 * depends on {@code state}. {@link #pathsForBridge} still builds it, for the browser half that
 * decides about links this pass never saw.</p>
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
     * The first path segments the <b>instance</b> serves, which are never a site's own (SH466).
     *
     * <p>The whole exception list, and deliberately short: {@code file_source} is where
     * {@code {{file_url}}} already points, {@code preview} and {@code sites} are the two prefixes
     * this pass writes (so a second pass changes nothing), {@code api} is the REST surface and
     * {@code mcp} the tool one. A source that really publishes a page under one of these cannot be
     * replicated at that address anyway, so leaving it verbatim is the honest answer rather than a
     * carve-out.</p>
     */
    private static final Set<String> INSTANCE_ROUTES =
            Set.of("api", "mcp", "file_source", "preview", "sites");

    /**
     * A CSS {@code url()} — quoted either way, or bare (SH461).
     *
     * <p>Deliberately the same three alternatives the CLI's own {@code URL_CALL} matches, and for the
     * same reason its comment gives: an alternative that can match <b>empty</b> between two
     * {@code \s*} makes {@code url(} with no closing paren quadratic, so the bare branch is {@code +}.
     * Two copies of one pattern is what SH245 warns about — they are in different languages on
     * different sides of the wire, and the shared contract is CSS's grammar rather than either
     * file's.</p>
     */
    private static final Pattern CSS_URL =
            Pattern.compile("url\\(\\s*(?:\"([^\"]*)\"|'([^']*)'|([^)'\"\\s]+))\\s*\\)",
                    Pattern.CASE_INSENSITIVE);

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
        return rewriteInto(html,
                ShSiteFrontEnd.PREVIEW_BASE + URLEncoder.encode(siteKey, StandardCharsets.UTF_8),
                page);
    }

    /**
     * The same pass, into whatever address space the caller serves at (SH451).
     *
     * <p>SH336's rule is not about the preview — it is that a link is rewritten <b>only when its path
     * is a URL this site publishes</b>, so a `{{file_url}}`, an SH141 front-end route and an external
     * site's own `/api/…` are all left as the author wrote them. That rule is identical for a public
     * delivery URL; what differs is four characters of prefix. Parameterising it here rather than
     * copying the pass is SH245's rule — the second copy is the one that stops matching.</p>
     *
     * @param prefix what an internal path is prefixed with, with no trailing slash
     */
    public String rewriteInto(String html, String prefix, ShPageRenderService.ShRenderedPage page) {
        if (html == null || html.isBlank() || page == null || page.siteId() == null) {
            return html;
        }
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
        rewriteStylesheets(document, prefix, page, assets);
        for (String attribute : NAVIGABLE) {
            for (Element element : document.select("[" + attribute + "]")) {
                String value = element.attr(attribute);
                String rewritten = rewriteOne(value, prefix);
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
     *
     * <p>Everything else is rebased, resolvable or not (SH466). {@link #INSTANCE_ROUTES} is the one
     * remaining exception, and it is also what makes a second pass a no-op: the prefix this method
     * writes starts with {@code /preview/} or {@code /sites/}.</p>
     */
    private String rewriteOne(String value, String prefix) {
        if (value == null || !value.startsWith("/") || value.startsWith("//")
                || isInstanceRoute(value)) {
            return null;
        }
        int cut = firstOf(value, '?', '#');
        String path = cut < 0 ? value : value.substring(0, cut);
        String tail = cut < 0 ? "" : value.substring(cut);
        return prefix + path + tail;
    }

    /** Whether the path's first segment names a route the instance serves rather than a site. */
    private static boolean isInstanceRoute(String value) {
        int end = value.indexOf('/', 1);
        int query = firstOf(value, '?', '#');
        if (query >= 0 && (end < 0 || query < end)) {
            end = query;
        }
        return INSTANCE_ROUTES.contains(end < 0 ? value.substring(1) : value.substring(1, end));
    }

    /**
     * The same rule inside {@code <style>}, on every {@code url()} (SH461).
     *
     * <h2>Why a page carried two rewriting rules for one job</h2>
     *
     * <p>A fidelity replica's markup is kept verbatim, so its {@code <img src="/img/hero.png">} is the
     * source's own path and SH437's pass above points it at this site's copy. Its stylesheet was not:
     * the capture rewrote every {@code url()} to the asset <em>tree</em>'s path, which is addressable
     * in {@code shio/assets/} and nowhere a browser can ask for. So one page carried two rules for one
     * job and the working one was not the one the stylesheet used — the logo loaded and the webfonts
     * 404'd, on a capture that had fetched them correctly and a route that serves them when asked
     * properly. SH461 deleted the second rule in the CLI (the theme now carries the source's path,
     * like the markup) and this is the other half: the same lookup, the same prefix, the same
     * leave-it-alone default, applied where CSS references live.</p>
     *
     * <h2>Inline only, and that is the whole surface</h2>
     *
     * <p>A {@code <link rel="stylesheet">} is an {@code href} the {@code NAVIGABLE} pass already sees,
     * and its bytes are served by whoever owns them. Shio's own theme is <em>inlined</em> by the
     * layout through {@code {{theme.css}}} (SH183), which is exactly why there is a {@code <style>} to
     * walk — and why walking it is enough.</p>
     */
    private void rewriteStylesheets(Document document, String prefix,
                                    ShPageRenderService.ShRenderedPage page, Map<String, Boolean> seen) {
        for (Element style : document.select("style")) {
            String css = style.data();
            // `(` and not `url(`: the pattern is case-insensitive, and a guard that is not would
            // skip a stylesheet written with `URL(` — legal CSS, and the kind of asymmetry that
            // makes one instance's replica render and another's not.
            if (css.indexOf('(') >= 0) {
                String rewritten = rewriteCssUrls(css, prefix, page, seen);
                if (!css.equals(rewritten)) {
                    // `Element#text` on a <style> writes a DataNode, which Jsoup emits unescaped —
                    // the one thing this pass must not undo, since an escaped url() is half the
                    // defect it exists for.
                    style.text(rewritten);
                }
            }
        }
    }

    private String rewriteCssUrls(String css, String prefix,
                                  ShPageRenderService.ShRenderedPage page, Map<String, Boolean> seen) {
        Matcher urls = CSS_URL.matcher(css);
        StringBuilder out = new StringBuilder(css.length());
        while (urls.find()) {
            String quote = quoteOf(urls);
            String raw = valueOf(urls).trim();
            String served = rewriteAsset(raw, prefix, page, seen);
            urls.appendReplacement(out, Matcher.quoteReplacement(
                    "url(" + quote + (served == null ? raw : served) + quote + ")"));
        }
        return urls.appendTail(out).toString();
    }

    /** The quoting the author used, kept: a bare `url()` must not gain quotes it never had. */
    private static String quoteOf(Matcher urls) {
        if (urls.group(1) != null) {
            return "\"";
        }
        return urls.group(2) != null ? "'" : "";
    }

    private static String valueOf(Matcher urls) {
        for (int group = 1; group <= 3; group++) {
            if (urls.group(group) != null) {
                return urls.group(group);
            }
        }
        return "";
    }

    /**
     * One {@code src}, or {@code null} to leave it alone (SH437).
     *
     * <p>The same exclusions as {@link #rewriteOne} — absolute, protocol-relative and
     * document-relative are all somebody else's or already correct, and {@link #INSTANCE_ROUTES}
     * covers {@code /file_source/…}, where {@code {{file_url}}} already points, so a template that
     * resolved an asset properly is not re-pointed at a path that would have to resolve back to the
     * same bytes.</p>
     *
     * <p>What it does <b>not</b> share is SH466's inverted default. An unresolvable {@code href} is
     * still a navigation into this site and 404s where {@code verify} reports it; an unresolvable
     * {@code src} is a request for bytes that are not here, and rebasing it would turn a reference
     * the capture can still be measured against into one pointing at nothing.</p>
     */
    private String rewriteAsset(String value, String prefix,
                                ShPageRenderService.ShRenderedPage page, Map<String, Boolean> seen) {
        if (value == null || !value.startsWith("/") || value.startsWith("//")
                || isInstanceRoute(value)) {
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
