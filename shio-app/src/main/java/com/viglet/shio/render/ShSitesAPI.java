/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.render.ShPageRenderService.ShRenderedPage;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

/**
 * SH451 — the public URL a site did not have.
 *
 * <h2>The defect</h2>
 *
 * <p>A site built or replicated in Shio had <b>no public URL by any path</b>. {@code /preview/**} is
 * the only HTML mapping {@link ShRenderAPI} declares and it answers 401 anonymously — authenticated,
 * {@code no-store}, {@code noindex}, all deliberate. The path {@code renderer.md} names as production,
 * Next.js over the CDA, cannot install (SH446). So the renderer composed a correct page nobody outside
 * the console could read.</p>
 *
 * <h2>The route is recovered, not designed from memory</h2>
 *
 * <p>Branch {@code 0.3.8} carries it in {@code com.viglet.shio.website}: {@code ShSitesContext} maps
 * {@code GET /sites/**} and {@code ShSitesContextURLProcess} splits the path <em>by position</em> —
 *
 * <pre>/sites/&lt;site-furl&gt;/&lt;format&gt;/&lt;locale&gt;/&lt;content-path…&gt;</pre>
 *
 * with {@code contexts[2]} the site (resolved by {@code findByFurl}), {@code [3]} the format and
 * {@code [4]} the locale, defaulting to {@code default} and {@code en-us}. Two variants ship with it
 * and both are here: <b>{@code x-sh-site}</b> names the site as a header so the URL carries only the
 * content path — which is how one host serves one site — and <b>{@code sh-format}</b> overrides the
 * format segment.</p>
 *
 * <h2>Two decisions taken</h2>
 *
 * <p><b>The path stays plural.</b> {@code /sites}, because that preserves the URLs of anything
 * migrating from 0.3.x and singular buys nothing.</p>
 *
 * <p><b>{@code <format>} is vestigial.</b> The segment is kept so the shape is the old one, is fixed
 * at {@code default}, and carries no behaviour: today's layout comes from {@code postTypeLayout}
 * (SH328), and making the segment a second binding table is a feature rather than a recovery. It is
 * <em>read</em> — {@code sh-format} still replaces it, and a URL that names one is still a URL — and
 * then ignored, which is the honest form of "kept for compatibility".</p>
 *
 * <p><b>And vestigial means fixed, which is not what it first shipped as (SH457).</b> "Carries no
 * behaviour" was implemented as <em>accepts anything</em>, and the two differ exactly where it
 * costs: with any value in that slot and no segments after it the content path is empty, so the
 * resolver returns the home page and the response is a <b>200</b>. Measured:
 * {@code GET /sites/publico/viglet-logo.png} answered 200 {@code text/html} with the home page's
 * 10265 bytes, and so did {@code /sites/publico/qualquer}. Four probes place it — {@code /sites/<site>/}
 * is the home and right, {@code /sites/<site>/default/en-us/nao-existe} and {@code /sites/<site>/a/b/c/d/e}
 * are teaching 404s and right, and the <b>three-segment</b> form is the one that lies. That shape is
 * what every root-relative asset becomes: a page served at {@code /sites/publico/} carrying
 * {@code <img src="/viglet-logo.png">} requests {@code /sites/publico/viglet-logo.png}, and the
 * browser is handed HTML where it expected an image. A missing asset stopped being a 404 an author
 * can see and became a silent soft-200 — the failure {@code verify}'s route checks exist to prevent,
 * on the surface they cannot reach. So a format that is not {@code default} is now
 * {@link UnknownFormatException}, and the segment finally means what this note always said.</p>
 *
 * <p><b>The locale segment was left, then checked, then read.</b> SH459 made a locale the site has no
 * content in a 404 instead of the home page with a 200, and it still only reached the prefix. Since
 * SH1057 it selects the content: {@link Address#selectedLocale} goes to the renderer, which serves the
 * page at the path when it is in that locale and otherwise sends the reader, with a 302, to the
 * member of its translation group the site's fallback chain names — at that translation's own URL.
 * The route's own default ({@link #DEFAULT_LOCALE}) asks for nothing, so every link it has built
 * resolves as it did.</p>
 *
 * <h2>Six guards, inverted</h2>
 *
 * <p>Everything {@link ShRenderAPI}'s class note lists as keeping a preview a preview is deliberately
 * the other way round here, and that is the whole difference between the two routes: <b>anonymous</b>
 * (the path is already {@code permitAll} — it has been since before the renderer existed, which was a
 * latent hazard while nothing served there and is now the point), <b>PUBLISHED and never DRAFT</b>
 * (a visitor must not see an unpublished edit, and this route takes no {@code state}),
 * <b>no banner</b>, <b>no editor bridge</b>, <b>cacheable</b>, <b>indexable</b>, and links
 * <b>rewritten into the public space</b> rather than the preview's — SH336's pass, pointed the other
 * way.</p>
 *
 * <p>It makes {@code renderer.md}'s "production delivery is Next.js" a choice between two rather than
 * a statement of the only option. No non-goal forbids it: P10 says MCP must not become a third
 * contract, and this is the same renderer over the same content at a second address.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@RestController
@RequestMapping(ShSitesAPI.BASE)
@Tag(name = "Sites", description = "Public delivery — published pages, anonymous and cacheable")
public class ShSitesAPI {

    public static final String BASE = "/sites";

    /** 0.3.8's defaults, and the only values this route writes when it builds a URL of its own. */
    public static final String DEFAULT_FORMAT = "default";
    public static final String DEFAULT_LOCALE = "en-us";

    /** The site, as a header, so one host can serve one site from bare content paths. */
    private static final String SITE_HEADER = "x-sh-site";

    /** The format segment, as a query parameter — 0.3.8's own override, kept and still inert. */
    private static final String FORMAT_PARAM = "sh-format";

    /**
     * How long a published page may be held.
     *
     * <p>Five minutes rather than an hour: a curator publishing a correction expects to see it, and
     * a delivery route whose invalidation story is "wait" is one people put a CDN in front of and
     * then purge by hand. {@code public}, because the response is anonymous by construction — there
     * is no per-reader state in it to leak into a shared cache.</p>
     */
    private static final Duration MAX_AGE = Duration.ofMinutes(5);

    private final ShPageRenderService shPageRenderService;
    private final ShPreviewLinks links;
    private final ShSiteAssetDelivery assetDelivery;

    public ShSitesAPI(ShPageRenderService shPageRenderService, ShPreviewLinks links,
                      ShSiteAssetDelivery assetDelivery) {
        this.shPageRenderService = shPageRenderService;
        this.links = links;
        this.assetDelivery = assetDelivery;
    }

    /**
     * {@code GET /sites/…} — one published page, or the asset at that path.
     *
     * <p>One mapping for both shapes, because which one a request is depends on a <em>header</em> and
     * Spring cannot route on that without making the two forms two endpoints that must be kept in
     * step. {@link Address#of} does the split, which is also what makes it testable without a
     * servlet.</p>
     */
    @GetMapping("/**")
    public ResponseEntity<?> serve(@RequestHeader(name = SITE_HEADER, required = false) String siteHeader,
                                   @RequestParam(name = FORMAT_PARAM, required = false) String format,
                                   HttpServletRequest request) {
        Address address = Address.of(withinBase(request), siteHeader, format);
        requireKnownLocale(address);
        ShRenderedPage rendered;
        try {
            // SH1057 — the segment reaches the resolution. The route's own default is every link it
            // has ever built, so it asks for nothing and the page at the path is served as before.
            rendered = shPageRenderService.render(address.site(), address.path(),
                    ShPostState.PUBLISHED, readerParams(request), address.selectedLocale());
        } catch (ShPageRenderService.UnknownPageException noPage) {
            // SH437's fallback, on this route too: a site's own images are served at the source's
            // paths, and a public page whose images 404 is not a published site.
            return asset(noPage, address);
        } catch (ShPageRenderService.ShPageRedirectException redirect) {
            return redirectTo(address, redirect, request);
        }
        ResponseEntity<?> declared = toDeclaredLocale(address, rendered, request);
        if (declared != null) {
            return declared;
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                // Cacheable and indexable — the two guards that are inverted rather than dropped.
                // A preview is `no-store` + `noindex` so it cannot quietly become infrastructure;
                // this route *is* the infrastructure, so saying so is the correct answer.
                .cacheControl(CacheControl.maxAge(MAX_AGE).cachePublic())
                // SH512 — `address.path()` is the base a document-relative reference resolves
                // against: the segments after the prefix, which is the source's own URL space and
                // the one form that keeps the reader's trailing slash.
                .body(links.rewriteInto(rendered.html(), address.prefix(), rendered, address.path()));
    }

    /**
     * SH459 — the locale segment is the format segment's twin, and it lied the same way.
     *
     * <h2>The same 200 as SH457's, one slot along</h2>
     *
     * <p>{@code Address.of} reads the locale into the prefix and never passes it to the renderer, so
     * with any value in that slot and no segments after it the content path is empty and
     * {@code /sites/acme/default/xx-yy/} answers the <b>home page</b> with a 200. SH457's format check
     * cannot reach it: a root-relative reference puts its first segment in the <em>format</em> slot,
     * which is why that task closed every case it measured and not this one.</p>
     *
     * <h2>Why the site's own content and not a fixed `en-us`</h2>
     *
     * <p>"Vestigial means fixed" was the right answer for the format, whose slot names a binding
     * table this product does not have. It is the wrong one here: pinning the segment to
     * {@code en-us} would make a Portuguese site's public URLs say {@code en-us}, which is a lie of
     * a different kind and a statement about a multi-language CMS that a route-lenience fix is not
     * entitled to make. So the rule is a <b>fact about this site</b> — the locale is accepted when
     * the site has content in it, or when it is the default this route writes — and it stays true
     * when locale selection eventually ships, because that is the same set.</p>
     *
     * <h2>It costs a query only when a URL uses it</h2>
     *
     * <p>The default short-circuits, so {@code /sites/acme/} and every link this route builds pay
     * nothing. A URL that names some other locale pays one site read and one projection, which is
     * the request that was about to be answered wrongly.</p>
     */
    private void requireKnownLocale(Address address) {
        String locale = address.locale();
        if (locale == null || locale.isBlank() || DEFAULT_LOCALE.equalsIgnoreCase(locale)) {
            return;
        }
        List<String> known = shPageRenderService.localesOf(address.site(), ShPostState.PUBLISHED);
        if (known.stream().noneMatch(locale::equalsIgnoreCase)) {
            throw new UnknownLocaleException(locale, known);
        }
    }

    /**
     * SH1057 — a site that declared its default locale is addressed in it, not in {@code en-us}.
     *
     * <p>{@link #DEFAULT_LOCALE} is 0.3.8's label for "the reader named no locale", and every URL
     * this route built wrote it — so a Portuguese site's public links said {@code en-us} while its
     * content was stored as {@code pt-br}. Once a site <em>declares</em> a default (SH1056), that tag
     * is the one its URLs name: a request in the legacy label, or in none, is answered with a
     * <b>302</b> to the same path in the declared locale's space, where the links it then follows
     * stay. 302 because a curator can change the default tomorrow, and a cached 301 would keep
     * sending readers to the old one.</p>
     *
     * <p><b>Only a declared site moves.</b> An undeclared site keeps {@code en-us}, exactly as it
     * was: moving every existing site's URLs would serve each page at two addresses (the SH467
     * split) to fix a label nobody chose, while declaring a policy is the act of choosing. The header
     * form names no locale in its URLs at all, so it is left alone; and a site whose declared
     * default <em>is</em> {@code en-us} would redirect to itself, so it is left alone too.</p>
     *
     * <p>The tag comes back on the rendered page, which already holds the site, so this costs no
     * query. What it costs is the render before the 302 — paid once per reader on a legacy URL of a
     * declared site, which is the request that was about to be answered in the wrong space.</p>
     *
     * @return the redirect, or null when the page is to be served as rendered
     */
    private ResponseEntity<?> toDeclaredLocale(Address address, ShRenderedPage rendered,
                                               HttpServletRequest request) {
        String declared = rendered.siteLocale();
        if (declared == null || declared.isBlank() || address.isHeaderForm()
                || address.selectedLocale() != null || DEFAULT_LOCALE.equalsIgnoreCase(declared)) {
            return null;
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(java.net.URI.create(
                        withQueryOf(request, address.inLocale(declared).prefix() + address.path())))
                .cacheControl(CacheControl.maxAge(MAX_AGE).cachePublic())
                .build();
    }

    private ResponseEntity<?> asset(RuntimeException pageProblem, Address address) {
        try {
            return assetDelivery.serve(address.site(), address.path(), ShPostState.PUBLISHED);
        } catch (ShSiteAssetDelivery.UnknownAssetException ignored) {
            throw pageProblem;
        }
    }

    /**
     * A redirect, in this route's own address space (SH385).
     *
     * <p>301 or 302 exactly as the preview decides it, and for the same reason — a permanent redirect
     * is cached by browsers effectively for ever, so it is opt-in. What differs is the {@code
     * Location}: a relative target is a friendly URL in this site, so it becomes a public URL rather
     * than a preview one, and an absolute one is left alone.</p>
     *
     * <p>The reader's query survives it (SH467). Dropping it was invisible while every redirect came
     * from a {@code Redirect} post a human wrote, and stops being so the moment the route
     * canonicalises a trailing slash by itself: {@code /search/?q=shio} would have arrived at
     * {@code /search} with no term, which is a worse answer than the 404 it replaced. Only when the
     * target declares none of its own — a {@code TO} carrying {@code ?} chose its query, and
     * appending a second one would produce a URL neither side wrote.</p>
     */
    private ResponseEntity<Void> redirectTo(Address address,
                                            ShPageRenderService.ShPageRedirectException redirect,
                                            HttpServletRequest request) {
        String to = redirect.to();
        boolean absolute = to.startsWith("http://") || to.startsWith("https://");
        String within = to.startsWith("/") ? to : "/" + to;
        return ResponseEntity
                .status(redirect.permanent() ? HttpStatus.MOVED_PERMANENTLY : HttpStatus.FOUND)
                .location(java.net.URI.create(
                        withQueryOf(request, absolute ? to : address.prefix() + within)))
                .cacheControl(CacheControl.maxAge(MAX_AGE).cachePublic())
                .build();
    }

    /** The reader's query string, kept when the redirect target declares none (SH467). */
    static String withQueryOf(HttpServletRequest request, String location) {
        String query = request == null ? null : request.getQueryString();
        return query == null || query.isBlank() || location.indexOf('?') >= 0
                ? location
                : location + "?" + query;
    }

    /** Everything after {@code /sites}, which is what both forms are split out of. */
    private static String withinBase(HttpServletRequest request) {
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String full = path == null ? request.getRequestURI() : path;
        return full.startsWith(BASE) ? full.substring(BASE.length()) : full;
    }

    /** The reader's query, minus this route's own two parameters (SH259's rule about the digest). */
    private static Map<String, String> readerParams(HttpServletRequest request) {
        Map<String, String> params = new java.util.LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (!FORMAT_PARAM.equals(key) && values != null && values.length > 0) {
                params.put(key, values[0]);
            }
        });
        return params;
    }

    /**
     * The two URL shapes, split by position exactly as 0.3.8 split them.
     *
     * <p>A record and a static factory rather than logic in the controller, because the split <em>is
     * </em> the recovered artefact: it is what a 0.3.x URL means, and it has to be assertable without
     * a servlet or the recovery is a claim rather than a fact.</p>
     *
     * @param site   the site's furl, name or id — SH181's order, since a human types this
     * @param path   the content path, always leading-slashed; {@code /} is the home page (SH433)
     * @param prefix what a link in this page has to be prefixed with to stay in this address space
     */
    public record Address(String site, String path, String prefix, String locale) {

        /**
         * SH1057 — the locale this URL asks the content to be in, or null when it asks for none.
         *
         * <p>{@link #DEFAULT_LOCALE} is the value this route writes whenever the reader named nothing,
         * and every link it has built carries it, so it is not a request for English: it is the
         * absence of a request, and reading it as one would redirect a Portuguese site's own home
         * page to its English translation.</p>
         */
        public String selectedLocale() {
            return locale == null || locale.isBlank() || DEFAULT_LOCALE.equalsIgnoreCase(locale)
                    ? null : locale;
        }

        /** Whether this is the {@code x-sh-site} form, whose URLs carry no format or locale. */
        public boolean isHeaderForm() {
            return BASE.equals(prefix);
        }

        /**
         * SH1057 — the same address in another locale's space: the prefix a reader's links then
         * carry. The format stays {@link #DEFAULT_FORMAT}, the only one the route serves.
         */
        public Address inLocale(String tag) {
            if (isHeaderForm()) {
                return this;
            }
            return new Address(site, path,
                    BASE + "/" + encode(site) + "/" + encode(DEFAULT_FORMAT) + "/" + encode(tag), tag);
        }

        /**
         * @param within everything after {@code /sites}
         * @param header the {@code x-sh-site} value, or null
         * @param format the {@code sh-format} value, or null — read, then ignored
         */
        public static Address of(String within, String header, String format) {
            String rest = within == null || within.isBlank() ? "/" : within;
            if (header != null && !header.isBlank()) {
                // The header form: the whole path is content, so one host serves one site and the
                // URLs are the site's own. The prefix is `/sites` alone, because that is what the
                // reader's browser already has in front of every path. It names no locale segment,
                // so it names the default and there is nothing for SH459 to check.
                return new Address(header.trim(), leading(rest), BASE, DEFAULT_LOCALE);
            }
            String[] segments = rest.split("/");
            // segments[0] is the empty string before the first slash — 0.3.8 indexed from 1 on the
            // whole URI, and this is the same positions with `/sites` already taken off.
            if (segments.length < 2 || segments[1].isBlank()) {
                throw new NoSiteInPathException(rest);
            }
            String site = segments[1];
            String usedFormat = format != null && !format.isBlank() ? format
                    : segments.length > 2 && !segments[2].isBlank() ? segments[2] : DEFAULT_FORMAT;
            // SH457 — and vestigial means *fixed*, which is what "carrying no behaviour" was meant
            // to say. It was implemented as accepts-anything, and the two are not the same thing:
            // with any value in this slot and no segments after it the content path is empty, so
            // `/sites/publico/viglet-logo.png` answered 200 with `text/html` and the home page's
            // 10265 bytes. That shape is not hypothetical — it is what every root-relative asset
            // becomes, so a missing image stopped being a 404 an author can see and became a silent
            // soft-200, on the one surface `verify`'s route checks cannot reach.
            if (!DEFAULT_FORMAT.equalsIgnoreCase(usedFormat)) {
                throw new UnknownFormatException(usedFormat);
            }
            String locale = segments.length > 3 && !segments[3].isBlank() ? segments[3] : DEFAULT_LOCALE;
            String content = segments.length > 4
                    ? "/" + String.join("/", java.util.Arrays.copyOfRange(segments, 4, segments.length))
                    : "/";
            // The prefix carries the segments the caller used, not the defaults: a reader who asked
            // for `/sites/acme/print/pt-br/x` must get links that keep them there, or every click
            // silently changes the two things they chose.
            return new Address(site, content + trailingSlashOf(rest),
                    BASE + "/" + encode(site) + "/" + encode(usedFormat) + "/" + encode(locale),
                    locale);
        }

        private static String leading(String path) {
            return path.startsWith("/") ? path : "/" + path;
        }

        /**
         * A trailing slash the split loses.
         *
         * <p>{@code "/acme/default/en-us/blog/".split("/")} drops the last empty segment, so
         * {@code /blog/} and {@code /blog} would become one path — and they are two friendly URLs
         * (SH433's home page is literally {@code /}). Restored rather than normalised away, because
         * normalising is a routing decision and this class's job is to say what the URL said.</p>
         */
        private static String trailingSlashOf(String rest) {
            return rest.endsWith("/") && rest.length() > 1 && rest.chars().filter(c -> c == '/').count() > 4
                    ? "/" : "";
        }

        /** SH1173 — a path segment, where a space is {@code %20}: form encoding's {@code +} is a literal plus here. */
        private static String encode(String value) {
            return ShSiteFrontEnd.pathSegment(value);
        }
    }

    /**
     * A 404: the format segment is fixed at {@code default}, and this URL named something else.
     *
     * <p><b>404 and not 400</b>, because of what actually sends these. The URL shape is fine — the
     * caller is almost always a browser resolving {@code <img src="/logo.png">} against a page served
     * at {@code /sites/acme/default/en-us/}, which lands on {@code /sites/acme/logo.png} with the
     * file name sitting in the format slot. "That is not here" is the true answer and the one an
     * author can act on; "your format segment is malformed" describes a URL nobody typed. The
     * teaching body still carries the rule, so a caller who *did* type it is told the same thing
     * (P6).</p>
     */
    /**
     * A 404: this site has no content in the locale the URL named (SH459).
     *
     * <p>404 and not 400 for {@link UnknownFormatException}'s reason, one slot along: the URL shape
     * is fine and "that is not here" is the true answer. The body lists what the site does have,
     * which is the one thing a caller cannot derive — and on a site with no locale on any post the
     * list is empty, so the message says the default is the only address this route answers rather
     * than printing an empty {@code allowed} and leaving the reader to infer it.</p>
     */
    public static class UnknownLocaleException extends ShTeachingException {

        public UnknownLocaleException(String sent, List<String> known) {
            super(ShProblem.of("sites-unknown-locale", HttpStatus.NOT_FOUND,
                            "Nothing is served in locale '" + sent + "'")
                    .withDetail("The locale segment of a public URL names the locale the content is "
                            + "written in. This site has " + (known.isEmpty()
                                    ? "no post declaring a locale, so `" + DEFAULT_LOCALE
                                            + "` is the only value this route answers."
                                    : "content in " + String.join(", ", known) + ".")
                            + " Any other value used to serve the home page with a 200, which is "
                            + "what this refusal replaces.")
                    .withAllowed(allowedLocales(known))
                    .withDidYouMeanFrom(sent)
                    .withFix("Use the locale this site's content is in, or omit the segment - "
                            + "`/sites/<site>/` is the home page.")
                    .withExample("/sites/<site>/" + DEFAULT_FORMAT + "/"
                            + (known.isEmpty() ? DEFAULT_LOCALE : known.getFirst()) + "/"));
        }

        private static List<String> allowedLocales(List<String> known) {
            List<String> allowed = new ArrayList<>(known);
            if (allowed.stream().noneMatch(DEFAULT_LOCALE::equalsIgnoreCase)) {
                allowed.add(DEFAULT_LOCALE);
            }
            return allowed;
        }
    }

    public static class UnknownFormatException extends ShTeachingException {

        public UnknownFormatException(String sent) {
            super(ShProblem.of("sites-unknown-format", HttpStatus.NOT_FOUND,
                            "Nothing is served at format '" + sent + "'")
                    .withDetail("The format segment of " + BASE + "/<site>/<format>/<locale>/<path> "
                            + "is vestigial: it is kept so the URL keeps 0.3.x's shape and it carries "
                            + "no behaviour, so `" + DEFAULT_FORMAT + "` is the only value it takes.")
                    .withAllowed(java.util.List.of(DEFAULT_FORMAT))
                    .withFix("If this was meant to be an asset or a page, put it after the locale: "
                            + BASE + "/<site>/" + DEFAULT_FORMAT + "/" + DEFAULT_LOCALE + "/" + sent
                            + " — a page's own root-relative references are rewritten into that space "
                            + "for you, so a reference that arrived here was never rewritten.")
                    .withExample(BASE + "/acme/" + DEFAULT_FORMAT + "/" + DEFAULT_LOCALE + "/"));
        }
    }

    /** A 400: {@code /sites} on its own names no site, and the header form is the way to omit it. */
    public static class NoSiteInPathException extends ShTeachingException {

        public NoSiteInPathException(String path) {
            super(ShProblem.of("sites-no-site", HttpStatus.BAD_REQUEST,
                            "No site in '" + BASE + path + "'")
                    .withDetail("A public URL names its site in the second segment: "
                            + BASE + "/<site>/<format>/<locale>/<path>.")
                    .withFix("Add the site, or send the `x-sh-site` header and keep the URL to the "
                            + "content path — which is how one host serves one site.")
                    .withExample(BASE + "/acme/" + DEFAULT_FORMAT + "/" + DEFAULT_LOCALE + "/"));
        }
    }
}
