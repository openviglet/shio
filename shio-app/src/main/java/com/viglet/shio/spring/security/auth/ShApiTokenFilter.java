/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */
package com.viglet.shio.spring.security.auth;

import java.io.IOException;
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

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * CDA (Content Delivery API) Token authentication filter.
 * <p>
 * Authenticates requests with a {@code Key} header containing a valid API token,
 * confined to the {@code /api/v2/cda/**} namespace. Grants authorities by token
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

    private static final String RATE_LIMIT_REMAINING_HEADER = "X-Rate-Limit-Remaining";

    private final ShApiTokenRepository shApiTokenRepository;
    private final ShCdaRateLimiter rateLimiter;

    public ShApiTokenFilter(ShApiTokenRepository shApiTokenRepository, ShCdaRateLimiter rateLimiter) {
        this.shApiTokenRepository = shApiTokenRepository;
        this.rateLimiter = rateLimiter;
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
        // The GraphQL delivery endpoint (SH4) is the second such surface.
        boolean graphQl = isGraphQlPath(request);
        if (!isCdaPath(request) && !graphQl) {
            filterChain.doFilter(request, response);
            return;
        }

        String tokenValue = request.getHeader(TOKEN_HEADER);
        if (tokenValue == null || tokenValue.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Look up the token
        ShApiToken apiToken = shApiTokenRepository.findByToken(tokenValue).orElse(null);
        if (apiToken == null || !apiToken.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        ShApiTokenScope scope = apiToken.getScope() != null ? apiToken.getScope() : ShApiTokenScope.READ;
        ShApiTokenEnv env = apiToken.getEnv() != null ? apiToken.getEnv() : ShApiTokenEnv.PROD;

        // READ/PREVIEW tokens are read-only: only allow GET requests. WRITE tokens
        // may use any method (the CDA write surface itself lands in SH10). GraphQL
        // (SH4) is exempt — it reads via POST and the schema exposes no mutation.
        if (scope != ShApiTokenScope.WRITE && !graphQl && !"GET".equalsIgnoreCase(request.getMethod())) {
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

        // Grant authorities by scope. A PREVIEW environment (SH5) is preview-capable
        // by definition, so it also carries ROLE_CDA_PREVIEW regardless of scope.
        var authorities = new ArrayList<GrantedAuthority>(List.of(
                new SimpleGrantedAuthority("ROLE_CDA"),
                new SimpleGrantedAuthority("ROLE_USER")));
        if (scope == ShApiTokenScope.PREVIEW || env == ShApiTokenEnv.PREVIEW) {
            authorities.add(new SimpleGrantedAuthority("ROLE_CDA_PREVIEW"));
        }
        if (scope == ShApiTokenScope.WRITE) {
            authorities.add(new SimpleGrantedAuthority("ROLE_CDA_WRITE"));
        }

        var authentication = new UsernamePasswordAuthenticationToken(
                apiToken.getOwner(), null, authorities);
        // Carry scope + per-site allow-list (SH2) + environment (SH5) to the service.
        authentication.setDetails(new ShApiTokenContext(
                apiToken.getId(), scope, apiToken.getSiteIds(), env));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.debug("CDA token auth: '{}' ({}/{}) by {}", apiToken.getTitle(), scope, env, apiToken.getOwner());

        filterChain.doFilter(request, response);
    }

    private static boolean isCdaPath(HttpServletRequest request) {
        return relativePath(request).startsWith(CDA_PATH_PREFIX);
    }

    private static boolean isGraphQlPath(HttpServletRequest request) {
        String path = relativePath(request);
        return path.equals(GRAPHQL_PATH) || path.startsWith(GRAPHQL_PATH + "/");
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
