/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
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

import com.viglet.shio.api.agent.safety.ShAgentGrant;
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

        // Only process if no authentication is already set
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
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

        String tokenValue = request.getHeader(TOKEN_HEADER);
        if (tokenValue == null || tokenValue.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Look up the token tenant-agnostically (SH22 Part D): this runs before the
        // request tenant is known, so a normal (tenant-filtered) lookup would miss
        // tokens of other tenants. The token's own tenant is then propagated below.
        ShApiToken apiToken = shApiTokenRepository.findByTokenAcrossTenants(tokenValue).orElse(null);
        if (apiToken == null || !apiToken.isEnabled()) {
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

        // READ/PREVIEW tokens are read-only: only allow GET requests. WRITE and AGENT
        // tokens may use any method (the CDA write surface itself lands in SH10; the
        // agent write surface is SH81/SH82). GraphQL (SH4) is exempt — it reads via POST
        // and the schema exposes no mutation.
        if (!scope.canWrite() && !graphQl && !"GET".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"CDA tokens only allow read (GET) requests\"}");
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
        // Carry scope + per-site allow-list (SH2) + environment (SH5) + the agent grant
        // (SH106) to the service.
        authentication.setDetails(new ShApiTokenContext(
                apiToken.getId(), scope, apiToken.getSiteIds(), env, grantFor(apiToken, scope)));

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
        var authorities = new ArrayList<GrantedAuthority>(List.of(
                new SimpleGrantedAuthority("ROLE_CDA"),
                new SimpleGrantedAuthority("ROLE_USER")));
        if (scope.canPreview() || env == ShApiTokenEnv.PREVIEW) {
            authorities.add(new SimpleGrantedAuthority("ROLE_CDA_PREVIEW"));
        }
        if (scope.canWrite()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_CDA_WRITE"));
        }
        if (scope == ShApiTokenScope.AGENT) {
            // Named separately from ROLE_CDA_WRITE so a surface can require "an agent
            // credential" rather than "something that can write" — the review queue's
            // approve/revert endpoints are for the human and must refuse the agent's own
            // token, which is not a distinction ROLE_CDA_WRITE can express.
            authorities.add(new SimpleGrantedAuthority("ROLE_AGENT"));
        }
        return authorities;
    }

    /**
     * The credential's agent grant (SH106): the publish gate and the per-session ceilings.
     *
     * <p>Only {@code AGENT} tokens are bounded. Every other scope predates Block L, so
     * handing them a gate now would break integrations to enforce a rule they were never
     * issued under — the honest place for that decision is the credential a user creates
     * <em>as</em> an agent credential.</p>
     */
    private static ShAgentGrant grantFor(ShApiToken token, ShApiTokenScope scope) {
        if (scope != ShApiTokenScope.AGENT) {
            return ShAgentGrant.CONSOLE;
        }
        return new ShAgentGrant(
                token.agentMayPublish(),
                token.getMaxCalls() == null ? 0 : token.getMaxCalls(),
                token.getMaxWrites() == null ? 0 : token.getMaxWrites());
    }

    private static boolean isCdaPath(HttpServletRequest request) {
        return relativePath(request).startsWith(CDA_PATH_PREFIX);
    }

    private static boolean isGraphQlPath(HttpServletRequest request) {
        String path = relativePath(request);
        return path.equals(GRAPHQL_PATH) || path.startsWith(GRAPHQL_PATH + "/");
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
