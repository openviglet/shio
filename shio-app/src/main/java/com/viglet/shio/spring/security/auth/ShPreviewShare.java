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
