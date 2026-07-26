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

import com.viglet.shio.api.agent.safety.ShAgentGrant;
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
 * @param agent   what this credential may do on the agent surface (SH106) — the publish
 *                gate and the per-session ceilings. Never {@code null}: a token that
 *                predates Block L carries {@link ShAgentGrant#CONSOLE}, because applying
 *                a gate retroactively to credentials issued without one would break
 *                working integrations to enforce a rule they never agreed to
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
public record ShApiTokenContext(String tokenId, ShApiTokenScope scope, Set<String> siteIds,
                                ShApiTokenEnv env, ShAgentGrant agent) {

    public ShApiTokenContext {
        agent = agent == null ? ShAgentGrant.CONSOLE : agent;
    }

    /**
     * Back-compat constructor defaulting the environment to {@code PROD}, so
     * pre-SH5 call sites (and SH2 tests) keep compiling with published-only reads.
     */
    public ShApiTokenContext(String tokenId, ShApiTokenScope scope, Set<String> siteIds) {
        this(tokenId, scope, siteIds, ShApiTokenEnv.PROD);
    }

    /** Back-compat constructor for the pre-SH106 four-field form. */
    public ShApiTokenContext(String tokenId, ShApiTokenScope scope, Set<String> siteIds,
                             ShApiTokenEnv env) {
        this(tokenId, scope, siteIds, env, ShAgentGrant.CONSOLE);
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

    /** @return the token context bound to the current request, if any. */
    public static Optional<ShApiTokenContext> current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getDetails() instanceof ShApiTokenContext context) {
            return Optional.of(context);
        }
        return Optional.empty();
    }
}
