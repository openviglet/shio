/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.spring.security.auth;

import java.util.Optional;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.viglet.shio.persistence.model.token.ShApiTokenEnv;
import com.viglet.shio.persistence.model.token.ShApiTokenScope;

/**
 * Per-request access context of an authenticated CDA token (SH2/SH5).
 *
 * <p>Set as the {@link Authentication#getDetails() details} of the token
 * authentication by {@link ShApiTokenFilter} and read at the CDA service
 * boundary to enforce the per-site allow-list (SH2) and resolve the content
 * environment (SH5). When no token is present (console session, or a test using
 * a raw {@code Principal}), {@link #current()} returns empty and callers treat
 * access as unrestricted + {@code PROD} — preserving the SH1 behaviour.</p>
 *
 * @param tokenId the id of the authenticating {@code ShApiToken}
 * @param scope   the token's access scope
 * @param siteIds the per-site allow-list (empty = every site)
 * @param env     the target content environment ({@code PROD}/{@code PREVIEW})
 * @param mayPublish whether this credential may publish on the agent surface (SH107)
 * @param pathPrefix SH1110 — the page path a share may render; {@code null} for the whole site
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
public record ShApiTokenContext(String tokenId, ShApiTokenScope scope, Set<String> siteIds,
                                ShApiTokenEnv env, boolean mayPublish, String pathPrefix) {

    /** The pre-SH1110 form: no path narrowing, which is every token but a page's share link. */
    public ShApiTokenContext(String tokenId, ShApiTokenScope scope, Set<String> siteIds,
                             ShApiTokenEnv env, boolean mayPublish) {
        this(tokenId, scope, siteIds, env, mayPublish, null);
    }

    /**
     * Back-compat constructor defaulting the environment to {@code PROD}, so
     * pre-SH5 call sites (and SH2 tests) keep compiling with published-only reads.
     */
    public ShApiTokenContext(String tokenId, ShApiTokenScope scope, Set<String> siteIds) {
        this(tokenId, scope, siteIds, ShApiTokenEnv.PROD);
    }

    /**
     * Back-compat constructor for the pre-SH107 four-field form. Publishing defaults to
     * <b>not</b> permitted, which is the direction a default on this field has to fail in.
     */
    public ShApiTokenContext(String tokenId, ShApiTokenScope scope, Set<String> siteIds, ShApiTokenEnv env) {
        this(tokenId, scope, siteIds, env, false);
    }

    /**
     * @return {@code true} when this token may read the given site — i.e. it has
     *         no allow-list, or the site id is in the allow-list.
     */
    public boolean allowsSite(String siteId) {
        return siteIds == null || siteIds.isEmpty()
                || (siteId != null && siteIds.contains(siteId));
    }

    /**
     * @return {@code true} when this token targets the preview environment and so
     *         reads draft-preferred content (SH5).
     */
    public boolean preview() {
        return env == ShApiTokenEnv.PREVIEW;
    }

    /**
     * Whether the current request may publish through the agent surface (SH107).
     *
     * <p>No token means a console-authenticated caller — a human or the CLI running as
     * one — and they publish as they always have; the SH54 ACL is what bounds them. A
     * token publishes only if it was issued the capability.</p>
     */
    public static boolean currentMayPublish() {
        return current().map(ShApiTokenContext::mayPublish).orElse(true);
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
