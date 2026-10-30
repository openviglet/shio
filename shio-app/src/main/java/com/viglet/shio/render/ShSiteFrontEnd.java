/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import java.nio.charset.StandardCharsets;

import org.springframework.web.util.UriUtils;

import com.viglet.shio.persistence.model.site.ShSite;

/**
 * SH184 — the one place that answers "where can a person look at this site?".
 *
 * <h2>Why a site with no URL stopped being a dead end</h2>
 *
 * <p>{@code site.url} is the field that turns content into something a human can open, and
 * both visual surfaces read it directly: {@code ShPreviewTokenAPI} answered
 * {@code 400 site has no url}, and the Universal Editor rendered
 * {@code universalEditor.siteNoUrl}. Empty is the normal state of a site an agent just
 * built — SH170's {@code site.upsert} creates a blank one — so the product's two ways of
 * seeing a page were both unavailable at exactly the moment there was something new to
 * see.</p>
 *
 * <p>SH183 removed the reason. Shio can render the page itself, so a site with no external
 * front end is not unviewable; it is viewable <em>here</em>. This class is the fallback,
 * and it is deliberately a resolver rather than a default written into the column.</p>
 *
 * <h2>Why the fallback is resolved and not stored</h2>
 *
 * <p>Writing {@code /preview/<site>} into {@code site.url} at creation would be simpler and
 * is wrong twice over. The field means <b>the customer's front end</b> — the thing DNS
 * points at — so storing an internal route in it makes every later reader unable to tell a
 * real deployment from a placeholder, and makes "clear this" a thing somebody has to know
 * to do. Worse, it destroys the SH184 rule that matters: an upsert may <b>fill an empty
 * URL and must never replace one a human chose</b>, and once a placeholder is stored there
 * is no empty left to fill and no way to tell the placeholder from the choice.</p>
 *
 * <p>So the column stays honest — empty means "no front end deployed" — and absence is
 * answered at the point of use.</p>
 *
 * <h2>The two destinations are different kinds of thing</h2>
 *
 * <p>They are not interchangeable strings, which is why this returns a record rather than a
 * URL. An {@link Kind#EXTERNAL} front end previews a draft by being handed a
 * <em>token</em> and enabling its own draft mode at {@code /api/preview} — that is SH13's
 * flow and it depends on code the front end has to implement. {@link Kind#SHIO_PREVIEW}
 * needs no token at all: {@code /preview/**} is console-authenticated and renders
 * DRAFT-preferred by itself. Collapsing the two would mean minting a credential nobody
 * uses, and building a {@code /api/preview} URL against a route Shio does not serve.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShSiteFrontEnd {

    /** The SH183 route. Kept here so nothing else has to spell it. */
    public static final String PREVIEW_BASE = "/preview/";

    private ShSiteFrontEnd() {
        throw new IllegalStateException("Utility class");
    }

    /** Which of the two places a page can be looked at. */
    public enum Kind {
        /** A front end the customer deployed; previewing a draft needs SH13's token. */
        EXTERNAL,
        /** Shio's own renderer; already draft-preferred and already authenticated. */
        SHIO_PREVIEW
    }

    /**
     * @param kind    which destination this is
     * @param baseUrl the site's base — an absolute origin for {@code EXTERNAL}, the
     *                same-origin {@code /preview/<site>} for {@code SHIO_PREVIEW}
     */
    public record Destination(Kind kind, String baseUrl) {

        public boolean isExternal() {
            return kind == Kind.EXTERNAL;
        }
    }

    /**
     * @param site any site
     * @return where a human can look at it — never {@code null}, which is the whole point
     */
    public static Destination of(ShSite site) {
        String url = site == null ? null : site.getUrl();
        if (url != null && !url.isBlank()) {
            return new Destination(Kind.EXTERNAL, trimTrailingSlash(url.trim()));
        }
        return new Destination(Kind.SHIO_PREVIEW, previewPrefix(previewKey(site)));
    }

    /**
     * SH1173 — {@code /preview/<site>}, spelled once, as a path.
     *
     * <p>Three call sites built this with {@code URLEncoder.encode}, which is <em>form</em>
     * encoding: a space becomes {@code +}, and in a path a {@code +} is a literal plus. Every link on
     * a site named "Console Check" pointed at {@code /preview/Console+Check/}, a 404, and the
     * Universal Editor's click-through landed there with no bridge.</p>
     */
    public static String previewPrefix(String siteKey) {
        return PREVIEW_BASE + pathSegment(siteKey);
    }

    /** One URL path segment: a space is {@code %20}, and {@code /} is escaped rather than a separator. */
    public static String pathSegment(String value) {
        return UriUtils.encodePathSegment(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    /**
     * How to address the site on the {@code /preview/**} route.
     *
     * <p>The name, because a preview URL is a thing a human reads and pastes. But the route
     * resolves <b>id, furl or name</b> (SH181's order), so falling back to the id when there is
     * no name costs nothing and is the difference between an ugly URL and a broken one. A site
     * really can lack a name here: this runs on whatever the caller resolved, and
     * {@code ShPreviewTokenAPI} reaches it through {@code ShFolderSite}, which walks a parent
     * chain and returns whatever it finds.</p>
     */
    private static String previewKey(ShSite site) {
        if (site == null) {
            return "";
        }
        String name = site.getName();
        if (name != null && !name.isBlank()) {
            return name;
        }
        return site.getId() == null ? "" : site.getId();
    }

    /**
     * A page's URL at this destination.
     *
     * @param destination from {@link #of(ShSite)}
     * @param furl        the post's friendly URL, which already starts with {@code /}
     */
    public static String pageUrl(Destination destination, String furl) {
        String path = furl == null || furl.isBlank() ? "/" : furl;
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return destination.baseUrl() + path;
    }

    /**
     * SH1304 — where a <b>visitor</b> sees this page, which {@link #of(ShSite)} does not answer.
     *
     * <p>{@code of} falls back to {@code /preview/<site>}: authenticated, banner-wrapped and
     * draft-preferred, the right place for a curator checking work and the wrong one for checking a
     * publish. This is the other question. A deployed front end is where the published site lives, so
     * it answers the same as {@code of}; without one it is {@code /sites/**}, the only route that
     * serves PUBLISHED alone and enforces the members-only gate. The console's
     * {@code publishedDestination} mirrors this, so the two cannot disagree.</p>
     *
     * <p>A page other than the home is addressed in the {@code en-us} space: {@code /sites/**}
     * redirects a page written in another locale to its own (SH1279), so one spelling reaches every
     * page and this does not have to know the site's languages.</p>
     *
     * @param site any site
     * @param furl the page's friendly URL, or {@code null} for the home
     */
    public static String publishedUrl(ShSite site, String furl) {
        Destination destination = of(site);
        if (destination.isExternal()) {
            return pageUrl(destination, furl);
        }
        String base = ShSitesAPI.BASE + "/" + pathSegment(sitesKey(site));
        if (furl == null || furl.isBlank() || "/".equals(furl)) {
            return base + "/";
        }
        return base + "/" + ShSitesAPI.DEFAULT_FORMAT + "/" + ShSitesAPI.DEFAULT_LOCALE
                + (furl.startsWith("/") ? furl : "/" + furl);
    }

    /** {@code /sites/**} resolves a site by its furl first, which is the spelling a visitor reads. */
    private static String sitesKey(ShSite site) {
        String furl = site == null ? null : site.getFurl();
        return furl != null && !furl.isBlank() ? furl : previewKey(site);
    }

    /**
     * Trailing slashes are stripped so composing a base with a path cannot produce
     * {@code //page} — which some front ends serve, some redirect, and one or two 404.
     */
    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
