/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */
package com.viglet.shio.spring.security.auth;

import java.util.Optional;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.viglet.shio.persistence.model.token.ShApiTokenScope;

/**
 * Per-request access context of an authenticated CDA token (SH2).
 *
 * <p>Set as the {@link Authentication#getDetails() details} of the token
 * authentication by {@link ShApiTokenFilter} and read at the CDA service
 * boundary to enforce the per-site allow-list. When no token is present
 * (console session, or a test using a raw {@code Principal}),
 * {@link #current()} returns empty and callers treat access as unrestricted —
 * preserving the SH1 behaviour.</p>
 *
 * @param tokenId the id of the authenticating {@code ShApiToken}
 * @param scope   the token's access scope
 * @param siteIds the per-site allow-list (empty = every site)
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
public record ShApiTokenContext(String tokenId, ShApiTokenScope scope, Set<String> siteIds) {

    /**
     * @return {@code true} when this token may read the given site — i.e. it has
     *         no allow-list, or the site id is in the allow-list.
     */
    public boolean allowsSite(String siteId) {
        return siteIds == null || siteIds.isEmpty()
                || (siteId != null && siteIds.contains(siteId));
    }

    /** @return the token context bound to the current request, if any. */
    public static Optional<ShApiTokenContext> current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getDetails() instanceof ShApiTokenContext context) {
            return Optional.of(context);
        }
        return Optional.empty();
    }
}
