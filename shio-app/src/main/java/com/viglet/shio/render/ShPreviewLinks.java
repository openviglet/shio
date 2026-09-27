/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
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
 * <h2>And a relative reference is worse than an absolute one (SH512)</h2>
 *
 * <p>The rule above says a document-relative reference "already resolves correctly under the
 * prefix — which is why it must <em>not</em> be touched". That premise is false whenever the
 * replica's serving depth differs from the source's, and it always does. Replicating
 * {@code vilt-group.com/en/} produced a page whose logo, hero background and fourteen images were
 * missing on a run where every asset uploaded and {@code verify} was clean:
 * {@code /sites/vilt/default/en-us/img/vilt-logo.svg} answers <b>200</b> and the markup asked for
 * {@code ./../../img/vilt-logo.svg}. The source serves at {@code /en/} — one segment deep, so that
 * string means {@code /img/x} — and the replica at {@code /sites/vilt/default/en-us/en}, four deep,
 * where the same string means {@code /sites/vilt/img/x}. Forty-four references on that page were
 * relative.</p>
 *
 * <p>So it is the same defect SH466 named, and strictly worse: an absolute reference is wrong in a
 * fixed way, a relative one is wrong by however much the depth differs — which changes with the
 * site's furl and the locale segment. The repair is to resolve it <b>against the page's own URL in
 * the source's space</b> and rebase the result, which is what a browser would have computed reading
 * the source. That base is the requested content path — the segments after the prefix — passed in by
 * the two controllers that know it, because it is the one form that keeps the reader's own trailing
 * slash: {@code /en/} and {@code /en} are different bases and a furl cannot tell them apart
 * ({@code ShAddress.normalizePath} drops the slash, correctly, for addressing).</p>
 *
 * <p>Widened at the same time from {@code href} and {@code src} to <b>every reference the page
 * carries</b>: {@code srcset}, {@code poster}, and {@code url()} inside a {@code style}
 * <em>attribute</em> as well as inside a {@code <style>} element. Each one breaks identically, and
 * a list that grows one entry per incident is the shape SH515 is about.</p>
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
     *
     * <p>{@code poster} joined it with SH512: a {@code <video poster>} is an image reference with no
     * other way onto the page, and it broke exactly the way {@code src} did.</p>
     */
    private static final List<String> ASSET = List.of("src", "poster");

    /**
     * A scheme, which means the reference belongs to whoever it names (SH512).
     *
     * <p>{@code https:}, and also {@code mailto:}, {@code tel:}, {@code data:} and
     * {@code javascript:} — the ones a relative-resolution pass would otherwise mangle into a path.
     * RFC 3986's production, so a reference that merely contains a colon later on ({@code a/b:c}) is
     * still relative.</p>
     */
    private static final Pattern SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.\\-]*:");

    /**
     * The {@code <link rel>} tokens whose {@code href} is an asset — the collector's own list.
     *
     * <p>See {@link #isAssetLink}. Kept as the same five the CLI's {@code scanPage} reads, because a
     * sixth on one side is the disagreement SH515's fixture exists to fail on.</p>
     */
    private static final Set<String> ASSET_RELS =
            Set.of("stylesheet", "icon", "apple-touch-icon", "manifest", "preload");

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
     * A {@code fetch()} whose argument is a literal path (SH515).
     *
     * <p>The reference that is neither markup nor CSS, and the only one here whose breakage is a
     * <b>crash</b>: a replica of {@code vilt-group.com} threw {@code insightsData.find is not a
     * function} because {@code content/home-insights.json} answered 404 and the page parsed the
     * error body. A page that draws a section from a data file references that file as surely as an
     * {@code <img>} references its bytes, and leaving the string alone is the same hardcoded base
     * SH466 removed from {@code href}.</p>
     *
     * <p>Three quotings, and a template literal only when it holds <b>no interpolation</b>
     * ({@code [^`$]*}): {@code ${…}} means the path is computed at runtime and rewriting a fragment
     * of it would corrupt a string this pass does not understand. Everything else a script can pass
     * to {@code fetch} — a variable, a concatenation, an {@code import()} — is left exactly as
     * written, which is the honest boundary for a pass that does not run the code.</p>
     *
     * <p>The CLI's {@code FETCH_CALL} is the same grammar, a deliberate second copy for the reason
     * {@link #CSS_URL} is one: the two are on opposite sides of the wire and the shared contract is
     * JavaScript's, not either file's. {@code cli/test/fixtures/references-page.html} is the one
     * fixture both suites read, so a disagreement is a red build rather than a missing image.</p>
     */
    private static final Pattern FETCH_CALL =
            Pattern.compile("\\bfetch\\(\\s*(?:\"([^\"]*)\"|'([^']*)'|`([^`$]*)`)");

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
        return rewrite(html, siteKey, page, page == null ? null : page.url());
    }

    /**
     * The same pass, told which URL the reader asked for (SH512).
     *
     * @param basePath the requested content path — everything after the preview prefix — which is
     *                 what a document-relative reference resolves against
     */
    public String rewrite(String html, String siteKey, ShPageRenderService.ShRenderedPage page,
                          String basePath) {
        return rewriteInto(html, ShSiteFrontEnd.previewPrefix(siteKey), page, basePath);
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
        return rewriteInto(html, prefix, page, page == null ? null : page.url());
    }

    /**
     * The same pass, told which URL the reader asked for (SH512).
     *
     * @param basePath the requested content path — everything after {@code prefix} — which is what a
     *                 document-relative reference resolves against. The <em>request's</em> path and
     *                 not the post's furl, because the two differ by a trailing slash and that slash
     *                 is one level of {@code ../}
     */
    public String rewriteInto(String html, String prefix, ShPageRenderService.ShRenderedPage page,
                              String basePath) {
        return rewriteInto(html, prefix, page, basePath, null);
    }

    /**
     * The same pass, carrying the renderer's own parameters onto every navigation (SH1036).
     *
     * @param carry a query fragment such as {@code editor=true&state=rev:3}, or {@code null} for a
     *              route that has none — the public {@code /sites/**} delivery, which has no modes
     */
    public String rewriteInto(String html, String prefix, ShPageRenderService.ShRenderedPage page,
                              String basePath, String carry) {
        if (html == null || html.isBlank() || page == null || page.siteId() == null) {
            return html;
        }
        Document document = Jsoup.parse(html);
        document.outputSettings().prettyPrint(false);
        String base = basePath == null || basePath.isBlank() ? "/" : basePath;
        // SH437 — one lookup per *distinct* path, not per element. A fidelity page is the whole
        // source document, so forty `<img>` tags sharing one sprite is the ordinary case and a query
        // per attribute would make the rewrite cost the page rather than the assets.
        Map<String, Boolean> assets = new HashMap<>();
        walk(document, (reference, asset) -> asset
                ? rewriteAsset(reference, prefix, page, assets, base)
                : rewriteOne(reference, prefix, base), carry);
        return document.html();
    }

    /**
     * What this pass does to one reference: a replacement, or {@code null} to leave it alone.
     *
     * <p>{@code asset} says which of the two rules applies — bytes this site serves, or a
     * navigation into it — because that is the only thing the two callers below need to tell
     * apart.</p>
     */
    @FunctionalInterface
    interface Rule {
        String apply(String reference, boolean asset);
    }

    /**
     * Every reference the page carries, visited once — the <b>one list</b> (SH516).
     *
     * <p>{@link #rewriteInto} and {@link #referencesIn} are two shapes over this method rather than
     * two walks, and that is the whole of what makes the gate's answer trustworthy: a check that
     * looked for references in its own list would go green on the one attribute the rewriter reads
     * and it does not. Whatever this rebases is exactly what that reports.</p>
     */
    private static void walk(Document document, Rule rule, String carry) {
        for (String attribute : ASSET) {
            eachAttribute(document, attribute, value -> rule.apply(value, true));
        }
        eachAttribute(document, "srcset", value -> eachSrcsetCandidate(value, rule));
        eachAttribute(document, "style", value -> eachCssUrl(value, rule));
        for (Element style : document.select("style")) {
            String rewritten = eachCssUrl(style.data(), rule);
            if (rewritten != null) {
                // `Element#text` on a <style> writes a DataNode, which Jsoup emits unescaped — the
                // one thing this pass must not undo, since an escaped url() is half the defect it
                // exists for.
                style.text(rewritten);
            }
        }
        // SH515 — inline scripts, for their `fetch()` literals. `[src]` is excluded: that is a
        // separate file this pass never has in hand, and its own references are a reader of the
        // bundle rather than of the page.
        for (Element script : document.select("script:not([src])")) {
            String rewritten = eachFetchCall(script.data(), rule);
            if (rewritten != null) {
                script.text(rewritten);
            }
        }
        for (String attribute : NAVIGABLE) {
            for (Element element : document.select("[" + attribute + "]")) {
                boolean asset = isAssetLink(element);
                String rewritten = rule.apply(element.attr(attribute), asset);
                if (rewritten != null) {
                    // SH1036 — the renderer's own parameters ride every NAVIGATION and no asset.
                    // A `?editor=true` on a stylesheet means nothing and busts its cache; on a link
                    // it is the difference between the next page carrying the editor bridge and the
                    // curator's edit mode ending on their first click.
                    element.attr(attribute, asset ? rewritten : withCarried(rewritten, carry));
                }
            }
        }
    }

    /**
     * Threads the current request's renderer parameters onto one rewritten link (SH1036).
     *
     * <h2>The defect this closes</h2>
     *
     * <p>A preview's mode lives in its query — {@code ?editor=true}, {@code ?state=rev:3} — and this
     * pass returned a prefix plus the site path, so the link's own query survived and the reader's
     * did not. The consequence was worse than losing a mode, because it was <b>silent</b>: the next
     * page shipped no bridge script, the console keyed its handshake on an iframe {@code src} that
     * in-frame navigation cannot change, and so the badge stayed green over a page with no bridge at
     * all while Save still offered to write the previous page's pending edits.</p>
     *
     * <h2>The link's own query wins</h2>
     *
     * <p>A carried parameter is only added when the link does not already name it. An author who
     * wrote {@code href="/page?state=published"} means that, and a preview parameter overriding a
     * link's own would make the rewrite change what the page says rather than where it goes.</p>
     *
     * <p>The fragment is split off first and re-appended, because {@code /a#b?c} is a fragment
     * containing a question mark and not a query at all.</p>
     */
    static String withCarried(String href, String carry) {
        if (carry == null || carry.isBlank() || href == null || href.isBlank()) {
            return href;
        }
        int hash = href.indexOf('#');
        String fragment = hash < 0 ? "" : href.substring(hash);
        String withoutFragment = hash < 0 ? href : href.substring(0, hash);
        int question = withoutFragment.indexOf('?');
        String existing = question < 0 ? "" : withoutFragment.substring(question + 1);
        StringBuilder added = new StringBuilder();
        for (String pair : carry.split("&")) {
            if (pair.isBlank() || declares(existing, name(pair))) {
                continue;
            }
            added.append(added.isEmpty() ? "" : "&").append(pair);
        }
        if (added.isEmpty()) {
            return href;
        }
        String separator = existing.isBlank() ? "?" : "&";
        String base = question < 0 || !existing.isBlank()
                ? withoutFragment
                // `/a?` — a question mark with nothing after it. Appending with `&` would produce
                // `/a?&editor=true`, which parses but reads as a defect to anyone looking at it.
                : withoutFragment.substring(0, question);
        return base + separator + added + fragment;
    }

    /** Whether {@code query} already names {@code parameter}, so the author's value stands. */
    private static boolean declares(String query, String parameter) {
        if (query.isBlank()) {
            return false;
        }
        for (String pair : query.split("&")) {
            if (name(pair).equals(parameter)) {
                return true;
            }
        }
        return false;
    }

    private static String name(String pair) {
        int equals = pair.indexOf('=');
        return equals < 0 ? pair : pair.substring(0, equals);
    }

    /**
     * Whether a {@code <link>}'s {@code href} asks for <b>bytes</b> rather than naming a page.
     *
     * <p>Found by SH515's parity fixture on its first run, and it is exactly the class of
     * disagreement that fixture exists for: the capture's collector records a
     * {@code <link rel="stylesheet">} as an <em>asset</em> to download, and this pass was treating
     * it as a navigation — so the two halves of one contract disagreed about what a stylesheet is.
     * The consequence was not cosmetic. The navigable rule rebases <b>unconditionally</b> (SH466,
     * correctly: a missing page is a finding, a link out of the site is a silent exit), and that
     * argument is about navigations. For bytes the rule is the opposite one {@link #rewriteAsset}
     * states — a path this site does not serve is left as written, or a reference the capture can
     * still be measured against becomes one pointing at nothing.</p>
     *
     * <p>The rel set is the collector's own, token by token, which is what makes
     * {@code rel="shortcut icon"} an icon on both sides. {@code preconnect} and {@code dns-prefetch}
     * are deliberately absent: those name a <em>host</em>, and there is no path in them to rebase.</p>
     */
    private static boolean isAssetLink(Element element) {
        if (!"link".equals(element.tagName())) {
            return false;
        }
        for (String token : element.attr("rel").toLowerCase(Locale.ROOT).trim().split("\\s+")) {
            if (ASSET_RELS.contains(token)) {
                return true;
            }
        }
        return false;
    }

    /** One attribute across the document, left exactly as it was wherever the rule answers null. */
    private static void eachAttribute(Document document, String attribute, UnaryOperator<String> rule) {
        for (Element element : document.select("[" + attribute + "]")) {
            String rewritten = rule.apply(element.attr(attribute));
            if (rewritten != null) {
                element.attr(attribute, rewritten);
            }
        }
    }

    /**
     * Every reference the page carries, as paths in this site's space (SH516).
     *
     * <p>The read half of {@link #walk}, and the answer to the question {@code verify} could not
     * ask: its checks resolve against the content graph — does a furl exist, is a {@code File} post
     * addressable — and all of that can be true of a page whose every string still misses.
     * Measured: {@code verify --site VILT} answered <b>ok, 0 errors, 17 warnings</b> over a page with
     * 38 broken links of 39 and 43 broken references.</p>
     *
     * <p>{@link #INSTANCE_ROUTES} is filtered out here as it is there: those paths belong to the
     * instance and are nobody's to judge as a site's content. Duplicates are kept — the caller knows
     * whether it wants one finding per element or per distinct path.</p>
     *
     * @param basePath the URL the page is served at, in the site's own space
     */
    public static List<Reference> referencesIn(String html, String basePath) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        Document document = Jsoup.parse(html);
        document.outputSettings().prettyPrint(false);
        String base = basePath == null || basePath.isBlank() ? "/" : basePath;
        List<Reference> found = new ArrayList<>();
        walk(document, (reference, asset) -> {
            String path = toSitePath(reference, base);
            if (path != null && !isInstanceRoute(path)) {
                found.add(new Reference(reference.trim(), path, asset));
            }
            // Never a replacement: this shape reads, and a `referencesIn` that edited its own copy
            // of the document would be a second rewriter nobody asked for.
            return null;
        // No carry: this shape reports what the page references, and a parameter the reader's
        // request happened to hold is not part of any reference (SH1036).
        }, null);
        return List.copyOf(found);
    }

    /**
     * One reference on a page: as the author wrote it, and as it resolves.
     *
     * @param raw   the attribute's own value, for a finding to quote
     * @param path  where it lands in this site's URL space
     * @param asset whether it asks for bytes ({@code src}, {@code srcset}, {@code poster},
     *              {@code url()}) rather than a navigation
     */
    public record Reference(String raw, String path, boolean asset) {
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
     *
     * <p>SH512 moved the "document-relative already resolves" clause out of the exclusions and into
     * {@link #toSitePath}, which turns it into the path the source meant before this decides.</p>
     */
    private String rewriteOne(String value, String prefix, String basePath) {
        String site = toSitePath(value, basePath);
        if (site == null) {
            return null;
        }
        // SH1173 — a link already inside this address space, such as the home link a template writes
        // from `ShSiteFrontEnd.of`, is kept as it is rather than skipped as an instance route: skipped,
        // it never carried `editor=true`, and a click on it ended edit mode.
        if (site.equals(prefix) || site.startsWith(prefix + "/") || site.startsWith(prefix + "?")) {
            return site;
        }
        if (isInstanceRoute(site)) {
            return null;
        }
        return prefix + site;
    }

    /**
     * One reference as a path in <b>this site's</b> URL space, or {@code null} to leave it alone
     * (SH512).
     *
     * <p>Root-relative comes back as it was, which is every caller's behaviour before this existed.
     * A document-relative one is resolved against {@code basePath} exactly as a browser reading the
     * <em>source</em> would have: the base's directory, then {@code .} and {@code ..} collapsed,
     * clamping at the root the way every URL parser does rather than escaping above it.</p>
     *
     * <p>What is left alone is what is not ours to move: an absolute URL (any scheme — including
     * {@code mailto:}, {@code tel:}, {@code data:} and {@code javascript:}, each of which a naive
     * path resolution would mangle), a protocol-relative {@code //host/x}, a same-page {@code #top},
     * and an empty value.</p>
     */
    private static String toSitePath(String value, String basePath) {
        if (value == null) {
            return null;
        }
        String reference = value.trim();
        if (reference.isEmpty() || reference.startsWith("//") || reference.startsWith("#")
                || SCHEME.matcher(reference).find()) {
            return null;
        }
        if (reference.startsWith("/")) {
            return reference;
        }
        int cut = firstOf(reference, '?', '#');
        String path = cut < 0 ? reference : reference.substring(0, cut);
        String tail = cut < 0 ? "" : reference.substring(cut);
        String base = basePath.startsWith("/") ? basePath : "/" + basePath;
        // A reference that is only a tail (`?page=2`) addresses this page, not its directory.
        String against = path.isEmpty() ? base : base.substring(0, base.lastIndexOf('/') + 1) + path;
        return collapseDots(against) + tail;
    }

    /** {@code .} and {@code ..} resolved away, the trailing slash kept because it is a base. */
    private static String collapseDots(String path) {
        Deque<String> segments = new ArrayDeque<>();
        for (String segment : path.split("/")) {
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                segments.pollLast();
            } else {
                segments.addLast(segment);
            }
        }
        String joined = String.join("/", segments);
        return "/" + joined + (path.endsWith("/") && !joined.isEmpty() ? "/" : "");
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
     *
     * <h2>The same method for the {@code style} attribute (SH512)</h2>
     *
     * <p>{@code style="background-image:url('img/hero.png')"} is a reference on the page and breaks
     * exactly the way the stylesheet's did — measured as a missing hero background on a replica
     * whose every asset had uploaded. One method, so the two cannot come to differ.</p>
     *
     * @return the rewritten CSS, or {@code null} when no {@code url()} in it moved
     */
    private static String eachCssUrl(String css, Rule rule) {
        // `(` and not `url(`: the pattern is case-insensitive, and a guard that is not would
        // skip a stylesheet written with `URL(` — legal CSS, and the kind of asymmetry that
        // makes one instance's replica render and another's not.
        if (css == null || css.indexOf('(') < 0) {
            return null;
        }
        Matcher urls = CSS_URL.matcher(css);
        StringBuilder out = new StringBuilder(css.length());
        while (urls.find()) {
            String quote = quoteOf(urls);
            String raw = valueOf(urls).trim();
            String served = rule.apply(raw, true);
            urls.appendReplacement(out, Matcher.quoteReplacement(
                    "url(" + quote + (served == null ? raw : served) + quote + ")"));
        }
        String rewritten = urls.appendTail(out).toString();
        return css.equals(rewritten) ? null : rewritten;
    }

    /**
     * Every literal {@code fetch()} argument in a script, or {@code null} when none of them moved.
     *
     * <p>Treated as an <b>asset</b> reference, which is the conservative half of the rule and the
     * right one here: a path this site does not serve is left exactly as written, so a script that
     * calls its own API on a host this instance knows nothing about is untouched, and only a
     * reference whose bytes are demonstrably here is repointed at them.</p>
     */
    private static String eachFetchCall(String script, Rule rule) {
        if (script == null || !script.contains("fetch(")) {
            return null;
        }
        Matcher calls = FETCH_CALL.matcher(script);
        StringBuilder out = new StringBuilder(script.length());
        while (calls.find()) {
            String quote = fetchQuoteOf(calls);
            String raw = valueOf(calls).trim();
            String served = rule.apply(raw, true);
            calls.appendReplacement(out, Matcher.quoteReplacement(
                    "fetch(" + quote + (served == null ? raw : served) + quote));
        }
        String rewritten = calls.appendTail(out).toString();
        return script.equals(rewritten) ? null : rewritten;
    }

    /** The quoting the author used — and a template literal keeps its backticks. */
    private static String fetchQuoteOf(Matcher calls) {
        if (calls.group(1) != null) {
            return "\"";
        }
        return calls.group(2) != null ? "'" : "`";
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
                                ShPageRenderService.ShRenderedPage page, Map<String, Boolean> seen,
                                String basePath) {
        String site = toSitePath(value, basePath);
        if (site == null || isInstanceRoute(site)) {
            return null;
        }
        int cut = firstOf(site, '?', '#');
        String path = cut < 0 ? site : site.substring(0, cut);
        String tail = cut < 0 ? "" : site.substring(cut);
        boolean served = seen.computeIfAbsent(path, candidate ->
                shSiteAssets.findInSite(page.siteId(), candidate, page.state()).isPresent());
        return served ? prefix + path + tail : null;
    }

    /**
     * A {@code srcset}, which is a list and therefore one rewrite per candidate (SH512).
     *
     * <p>Split on commas, each candidate being a reference and an optional descriptor. A
     * {@code data:} URI legitimately contains a comma and would be torn in half by that split, so an
     * attribute holding one is left entirely alone — the honest answer, since a {@code data:} URI is
     * not a reference this pass has anything to do to.</p>
     */
    private static String eachSrcsetCandidate(String value, Rule rule) {
        if (value == null || value.isBlank() || value.contains("data:")) {
            return null;
        }
        StringBuilder out = new StringBuilder(value.length());
        boolean moved = false;
        for (String candidate : value.split(",")) {
            String entry = candidate.trim();
            if (entry.isEmpty()) {
                continue;
            }
            int space = firstWhitespace(entry);
            String reference = space < 0 ? entry : entry.substring(0, space);
            String descriptor = space < 0 ? "" : entry.substring(space);
            String rewritten = rule.apply(reference, true);
            moved |= rewritten != null;
            out.append(out.isEmpty() ? "" : ", ")
                    .append(rewritten == null ? reference : rewritten).append(descriptor);
        }
        return moved ? out.toString() : null;
    }

    private static int firstWhitespace(String value) {
        for (int at = 0; at < value.length(); at++) {
            if (Character.isWhitespace(value.charAt(at))) {
                return at;
            }
        }
        return -1;
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
