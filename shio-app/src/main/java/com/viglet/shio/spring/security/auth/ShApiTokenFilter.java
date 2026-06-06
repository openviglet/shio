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

        // Confine token auth to the CDA namespace (SH2). A Key header on any other
        // path is ignored, so a token can never authenticate a console request.
        if (!isCdaPath(request)) {
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

        // READ/PREVIEW tokens are read-only: only allow GET requests. WRITE tokens
        // may use any method (the CDA write surface itself lands in SH10).
        if (scope != ShApiTokenScope.WRITE && !"GET".equalsIgnoreCase(request.getMethod())) {
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

        // Grant authorities by scope
        var authorities = new ArrayList<GrantedAuthority>(List.of(
                new SimpleGrantedAuthority("ROLE_CDA"),
                new SimpleGrantedAuthority("ROLE_USER")));
        if (scope == ShApiTokenScope.PREVIEW) {
            authorities.add(new SimpleGrantedAuthority("ROLE_CDA_PREVIEW"));
        } else if (scope == ShApiTokenScope.WRITE) {
            authorities.add(new SimpleGrantedAuthority("ROLE_CDA_WRITE"));
        }

        var authentication = new UsernamePasswordAuthenticationToken(
                apiToken.getOwner(), null, authorities);
        // Carry scope + per-site allow-list to the service boundary (SH2).
        authentication.setDetails(new ShApiTokenContext(
                apiToken.getId(), scope, apiToken.getSiteIds()));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.debug("CDA token auth: '{}' ({}) by {}", apiToken.getTitle(), scope, apiToken.getOwner());

        filterChain.doFilter(request, response);
    }

    private static boolean isCdaPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return false;
        }
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return path.startsWith(CDA_PATH_PREFIX);
    }
}
