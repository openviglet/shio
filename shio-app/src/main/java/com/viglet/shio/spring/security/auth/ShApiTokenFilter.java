/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.spring.security.auth;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.viglet.shio.api.cda.ShCdaRateLimiter;
import com.viglet.shio.persistence.model.token.ShApiToken;
import com.viglet.shio.persistence.model.token.ShApiTokenEnv;
import com.viglet.shio.persistence.model.token.ShApiTokenScope;
import com.viglet.shio.persistence.repository.token.ShApiTokenRepository;
import com.viglet.shio.tenant.ShTenantFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * CDA (Content Delivery API) Token authentication filter.
 * <p>
 * Authenticates requests with a {@code Key} header containing a valid API token,
 * confined to the delivery + agent namespaces ({@code /api/v2/cda/**},
 * {@code /graphql}, {@code /api/v2/agent/**}). Grants authorities by token
 * scope (SH2 — {@code READ}/{@code PREVIEW}/{@code WRITE}) and enforces a
 * per-token rate limit (SH3), returning {@code 429} when the budget is spent.
 * </p>
 *
 * <p><b>A presented {@code Key} wins over an existing session (SH233).</b> The filter used to
 * return immediately whenever anything had already authenticated, which read as conservative
 * and meant a caller holding both a session cookie and a token got the session — the case that
 * broke the Universal Editor's save-back, because a browser sends a same-origin cookie whatever
 * the client asks for. Sending a key is a request to be that principal; nothing about the
 * token's own bounds changes, and a key that cannot authenticate leaves the context untouched.
 * </p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Component
@Slf4j
public class ShApiTokenFilter extends OncePerRequestFilter {

    private static final String TOKEN_HEADER = "Key";

    /** Token auth only applies to the CDA namespace (SH2 — confinement). */
    private static final String CDA_PATH_PREFIX = "/api/v2/cda";

    /**
     * The GraphQL delivery endpoint (SH4) is a second CDA surface: it carries the
     * same {@code Key} auth and rate limit. Unlike REST, GraphQL reads via
     * {@code POST}, so the SH2 GET-only gate is skipped for it — the generated
     * schema has no {@code Mutation}, so a write is impossible by construction.
     */
    private static final String GRAPHQL_PATH = "/graphql";

    /**
     * The agent surface (SH76, Block H) is a third token-authenticated surface: it
     * is a shape over the CDA, so an agent holding a delivery key must be able to
     * read it without also holding a console credential. The SH2 method gate still
     * applies unchanged — a non-{@code WRITE} scope stays GET-only here, so adding
     * this prefix widens *reads* only.
     */
    private static final String AGENT_PATH_PREFIX = "/api/v2/agent";

    /**
     * Where a rendered form may POST (SH631), and the only path a {@code FORM} token may use a
     * method other than GET on.
     *
     * <p>Under the CDA namespace on purpose: a submission is delivery-side traffic from a public
     * page, and putting it there means it inherits the confinement, the rate limiter and the
     * {@code Key} header the other delivery surfaces already have, rather than becoming a fourth
     * write contract (P10). The endpoint itself is SH633; naming the prefix here is what lets the
     * scope be bounded without it.</p>
     */
    private static final String FORM_PATH_PREFIX = "/api/v2/cda/form";

    /**
     * The MCP endpoint (SH75) is the same surface again, reached over a different
     * transport. Like GraphQL it reads via {@code POST}, so the SH2 GET-only gate is
     * skipped for it — but unlike GraphQL, MCP <em>can</em> write, so the per-tool
     * checks inside the tools (SH54 ACL, SH22 quota, and the SH2 site allow-list every
     * agent read already honours) are what bound a token here. Widening this prefix
     * without those would have been the privilege escalation §XII forbids.
     */
    private static final String MCP_PATH = "/mcp";

    private static final String RATE_LIMIT_REMAINING_HEADER = "X-Rate-Limit-Remaining";

    private final ShApiTokenRepository shApiTokenRepository;
    private final ShCdaRateLimiter rateLimiter;
    private final Clock clock;

    public ShApiTokenFilter(ShApiTokenRepository shApiTokenRepository, ShCdaRateLimiter rateLimiter, Clock clock) {
        this.shApiTokenRepository = shApiTokenRepository;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request,
                                    @NotNull HttpServletResponse response,
                                    @NotNull FilterChain filterChain) throws ServletException, IOException {

        String tokenValue = request.getHeader(TOKEN_HEADER);
        boolean presented = tokenValue != null && !tokenValue.isBlank();

        // An explicitly presented `Key` wins over an authentication already in the context
        // (SH233). This used to return here whenever anything had authenticated, which sounds
        // conservative and was in fact the reason the Universal Editor could not save: the
        // console is same-origin with the API, so its session cookie rides along whatever the
        // client sets `withCredentials` to (that flag governs cross-origin requests only),
        // `SecurityContextHolderFilter` restores the session before this filter runs, and the
        // `Key` header was never read — leaving the request authenticated as a curator who has
        // no ROLE_CDA_WRITE, and a 403.
        //
        // It is not an escalation: presenting a valid token *is* the authorization, and the
        // token's own scope, allow-list, expiry and rate limit still bound the request exactly
        // as they do for a cookie-less caller. Nor does it widen CSRF: a forged cross-site
        // request cannot set a custom header without the target's CORS consent, and since SH167
        // a session-bearing caller on these paths is CSRF-checked anyway.
        if (!presented && SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        // Confine token auth to the CDA surfaces (SH2). A Key header on any other
        // path is ignored, so a token can never authenticate a console request.
        // The GraphQL delivery endpoint (SH4) and the agent surface (SH76) are the
        // second and third such surfaces.
        boolean graphQl = isGraphQlPath(request) || isMcpPath(request);
        if (!isCdaPath(request) && !graphQl && !isAgentPath(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!presented) {
            filterChain.doFilter(request, response);
            return;
        }

        // Look up the token tenant-agnostically (SH22 Part D): this runs before the
        // request tenant is known, so a normal (tenant-filtered) lookup would miss
        // tokens of other tenants. The token's own tenant is then propagated below.
        ShApiToken apiToken = shApiTokenRepository.findByTokenAcrossTenants(tokenValue).orElse(null);
        if (apiToken == null || !apiToken.isEnabled()) {
            // A token that cannot authenticate leaves whatever is in the context alone rather
            // than clearing it (SH233): a presented key is a request to be *this* principal, not
            // a request to stop being the other one. The request then succeeds or fails on
            // authorization exactly as it did before, which is what keeps this change additive.
            filterChain.doFilter(request, response);
            return;
        }

        // Short-lived preview tokens (SH13) carry an expiry; once it passes the token
        // stops authenticating — no separate revocation step. Treated like disabled.
        if (apiToken.isExpired(Instant.now(clock))) {
            filterChain.doFilter(request, response);
            return;
        }

        ShApiTokenScope scope = apiToken.getScope() != null ? apiToken.getScope() : ShApiTokenScope.READ;
        ShApiTokenEnv env = apiToken.getEnv() != null ? apiToken.getEnv() : ShApiTokenEnv.PROD;

        // READ/PREVIEW tokens are read-only: only allow GET requests. WRITE tokens
        // may use any method (the CDA write surface itself lands in SH10). GraphQL
        // (SH4) is exempt — it reads via POST and the schema exposes no mutation.
        //
        // An AGENT token (SH106) writes with POST, but only where its protocol lives:
        // /api/v2/agent/** and /mcp. On the CDA it stays read-only, which is the whole
        // reason the scope exists — an agent's credential must not also be a key to the
        // path that patches published content outside the agent surface's guards.
        if (!mayUseAnyMethod(scope, request, graphQl) && !"GET".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write(scope == ShApiTokenScope.AGENT
                    ? "{\"error\":\"An AGENT token may only write on /api/v2/agent/** and /mcp\"}"
                    : "{\"error\":\"CDA tokens only allow read (GET) requests\"}");
            return;
        }

        // Per-token rate limit (SH3): keyed by token id, so clients sharing a key
        // share a budget. Done after the token is known but before granting access.
        ShCdaRateLimiter.Decision decision = rateLimiter.tryConsume(apiToken.getId());
        response.setHeader(RATE_LIMIT_REMAINING_HEADER, Long.toString(Math.max(decision.remaining(), 0)));
        if (!decision.allowed()) {
            response.setStatus(429); // 429 Too Many Requests
            response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Rate limit exceeded\"}");
            return;
        }

        var authentication = new UsernamePasswordAuthenticationToken(
                apiToken.getOwner(), null, authoritiesFor(scope, env));
        // Carry scope + per-site allow-list (SH2) + environment (SH5) + the publish
        // capability (SH107) to the service. `mayPublish` travels on the context rather
        // than being re-read from the database at the publish site: the decision must be
        // the one this request authenticated with, not whatever the row says a second later.
        authentication.setDetails(new ShApiTokenContext(
                apiToken.getId(), scope, apiToken.getSiteIds(), env, apiToken.isMayPublish()));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Propagate the token's tenant so the downstream ShTenantFilter scopes the
        // request to the content owner's tenant (SH22 Part D), not the token owner's
        // username. No-op in single-tenant mode (ShTenantFilter ignores it).
        if (apiToken.getTenantId() != null) {
            request.setAttribute(ShTenantFilter.TENANT_REQUEST_ATTR, apiToken.getTenantId());
        }

        log.debug("CDA token auth: '{}' ({}/{}) by {}", apiToken.getTitle(), scope, env, apiToken.getOwner());

        filterChain.doFilter(request, response);
    }

    /**
     * Authorities by scope. A PREVIEW environment (SH5) is preview-capable by
     * definition, so it also carries ROLE_CDA_PREVIEW regardless of scope.
     */
    private static List<GrantedAuthority> authoritiesFor(ShApiTokenScope scope, ShApiTokenEnv env) {
        // SH631 — FORM returns before the common grant, and that is the whole point of the scope
        // rather than an optimisation. Every scope below starts from ROLE_CDA + ROLE_USER and adds
        // capability, which is right while the weakest of them may still read the delivery API. A
        // form token may not: it rides in a page a stranger can read, so ROLE_CDA would hand the
        // whole site to anyone who viewed the source — the privilege escalation §XII forbids, and
        // the same reasoning that keeps AGENT away from ROLE_CDA_WRITE below.
        if (scope == ShApiTokenScope.FORM) {
            return List.of(new SimpleGrantedAuthority("ROLE_FORM"));
        }
        var authorities = new ArrayList<GrantedAuthority>(List.of(
                new SimpleGrantedAuthority("ROLE_CDA"),
                new SimpleGrantedAuthority("ROLE_USER")));
        if (scope == ShApiTokenScope.PREVIEW || env == ShApiTokenEnv.PREVIEW) {
            authorities.add(new SimpleGrantedAuthority("ROLE_CDA_PREVIEW"));
        }
        if (scope == ShApiTokenScope.WRITE) {
            authorities.add(new SimpleGrantedAuthority("ROLE_CDA_WRITE"));
        }
        // SH106: an agent reads drafts by definition (every /agent/read is DRAFT-preferred),
        // so AGENT is preview-capable — and it carries ROLE_AGENT instead of ROLE_CDA_WRITE.
        if (scope == ShApiTokenScope.AGENT) {
            authorities.add(new SimpleGrantedAuthority("ROLE_CDA_PREVIEW"));
            authorities.add(new SimpleGrantedAuthority("ROLE_AGENT"));
        }
        return authorities;
    }

    /**
     * Whether this token may use a method other than GET on this request.
     *
     * <p>{@code WRITE} may anywhere it is accepted; {@code AGENT} only on the agent
     * surface; GraphQL and MCP read via POST and are exempt by transport.</p>
     */
    private static boolean mayUseAnyMethod(ShApiTokenScope scope, HttpServletRequest request, boolean graphQl) {
        if (scope == ShApiTokenScope.WRITE || graphQl) {
            return true;
        }
        // SH631 — a submission is a POST and a FORM token may make exactly that one, on exactly this
        // path. Bounding the method here rather than leaving it to the authorization rule means a
        // form token cannot POST anywhere else even if a future mapping under /api/v2/cda/** were
        // to grant ROLE_FORM by accident.
        if (scope == ShApiTokenScope.FORM) {
            return isFormPath(request);
        }
        return scope == ShApiTokenScope.AGENT && (isAgentPath(request) || isMcpPath(request));
    }

    private static boolean isCdaPath(HttpServletRequest request) {
        return relativePath(request).startsWith(CDA_PATH_PREFIX);
    }

    private static boolean isGraphQlPath(HttpServletRequest request) {
        String path = relativePath(request);
        return path.equals(GRAPHQL_PATH) || path.startsWith(GRAPHQL_PATH + "/");
    }

    private static boolean isFormPath(HttpServletRequest request) {
        return relativePath(request).startsWith(FORM_PATH_PREFIX);
    }

    private static boolean isAgentPath(HttpServletRequest request) {
        return relativePath(request).startsWith(AGENT_PATH_PREFIX);
    }

    private static boolean isMcpPath(HttpServletRequest request) {
        String path = relativePath(request);
        return path.equals(MCP_PATH) || path.startsWith(MCP_PATH + "/");
    }

    /** Request URI with any servlet context path stripped. */
    private static String relativePath(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return "";
        }
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return path;
    }
}
