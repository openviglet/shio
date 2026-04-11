/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */
package com.viglet.shio.spring.security.auth;

import java.io.IOException;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.viglet.shio.persistence.model.token.ShApiToken;
import com.viglet.shio.persistence.repository.token.ShApiTokenRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * CDA (Content Delivery API) Token authentication filter.
 * <p>
 * Authenticates requests with a {@code Key} header containing a valid API token.
 * Grants read-only access (GET only) to content delivery endpoints.
 * </p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Component
@Slf4j
public class ShApiTokenFilter extends OncePerRequestFilter {

    private static final String TOKEN_HEADER = "Key";

    private final ShApiTokenRepository shApiTokenRepository;

    public ShApiTokenFilter(ShApiTokenRepository shApiTokenRepository) {
        this.shApiTokenRepository = shApiTokenRepository;
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

        // CDA tokens are read-only: only allow GET requests
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"CDA tokens only allow read (GET) requests\"}");
            return;
        }

        // Grant read-only authority
        var authorities = List.of(
                new SimpleGrantedAuthority("ROLE_CDA"),
                new SimpleGrantedAuthority("ROLE_USER")
        );

        var authentication = new UsernamePasswordAuthenticationToken(
                apiToken.getOwner(), null, authorities);

        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.debug("CDA token auth: '{}' by {}", apiToken.getTitle(), apiToken.getOwner());

        filterChain.doFilter(request, response);
    }
}
