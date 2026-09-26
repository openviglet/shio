/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.spring.security.auth;

import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.viglet.shio.persistence.model.token.ShApiTokenScope;

/**
 * SH1042 — what the renderer needs to know about the caller of a share link.
 *
 * <h2>Why the site check is not in the filter</h2>
 *
 * <p>A share credential is bounded to one site, and the filter that authenticates it sees only a
 * path — {@code /preview/<key>/…}, where the key may be an id, a friendly URL or a name (SH181's
 * order). Resolving it there would put a second site lookup, with its own idea of that order, in
 * front of every preview request. The renderer resolves the site because rendering needs it, so the
 * check belongs where the answer already is.</p>
 *
 * <h2>Why an empty allow-list means nothing rather than everything</h2>
 *
 * <p>{@link ShApiTokenContext#allowsSite} reads an empty list as <em>every site</em>, which is right
 * for a delivery key an operator minted deliberately and wrong here by exactly one bit: a share link
 * is minted for one page of one site and handed to somebody outside the console, so the failure
 * direction is not symmetrical. A credential that arrived without its allow-list must read nothing,
 * not everything.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShPreviewShare {

    private ShPreviewShare() {
        throw new IllegalStateException("Utility class");
    }

    /** Whether this request is being made with a share link rather than by a curator. */
    public static boolean isShare() {
        return context() != null;
    }

    /**
     * Whether this request may read that site.
     *
     * <p>{@code true} for everybody who is not a share — a curator's session, an agent, the CLI —
     * so the caller can ask unconditionally and the answer costs nothing when it is not a share.</p>
     */
    public static boolean allows(String siteId) {
        ShApiTokenContext share = context();
        if (share == null) {
            return true;
        }
        Set<String> sites = share.siteIds();
        return siteId != null && sites != null && sites.contains(siteId);
    }

    /**
     * SH1110 — whether this request may render the page at {@code path}, within a site it may read.
     *
     * <p>{@code true} for everybody who is not a share, and for a share minted before SH1110 or for
     * a whole site ({@code null} prefix). Otherwise the prefix is a page — {@code /pricing} matches
     * {@code /pricing} and {@code /pricing/} and nothing else — or, ending in {@code /}, a folder and
     * everything under it. A string comparison and never a content lookup: this is a security
     * decision, and resolving content to reach it would be a second path into the content.</p>
     */
    public static boolean allowsPath(String path) {
        ShApiTokenContext share = context();
        if (share == null || share.pathPrefix() == null || share.pathPrefix().isBlank()) {
            return true;
        }
        return matches(share.pathPrefix(), path);
    }

    /** The rule {@link #allowsPath} applies, exposed for the test that holds it. */
    static boolean matches(String prefix, String path) {
        String asked = normalize(path);
        if (prefix.length() > 1 && prefix.endsWith("/")) {
            return asked.equals(prefix.substring(0, prefix.length() - 1)) || asked.startsWith(prefix);
        }
        return asked.equals(normalize(prefix));
    }

    /** One spelling of a page path: leading slash, no trailing one, {@code /} for the home page. */
    public static String normalize(String path) {
        String trimmed = path == null ? "" : path.trim();
        if (!trimmed.startsWith("/")) {
            trimmed = "/" + trimmed;
        }
        while (trimmed.length() > 1 && trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    /** The share's own context, or {@code null} when this request is not one. */
    private static ShApiTokenContext context() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getDetails() instanceof ShApiTokenContext token)) {
            return null;
        }
        return token.scope() == ShApiTokenScope.SHARE ? token : null;
    }
}
