/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.cda;

import java.io.IOException;

import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.CacheControl;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Stamps a {@code Cache-Control} header on Content Delivery API responses (SH3)
 * so a CDN/browser can absorb repeat reads. The header is set before the body
 * is written, so it survives a {@code 304} produced by the paired
 * {@code ShallowEtagHeaderFilter}.
 *
 * <p>Scoped to {@code /api/v2/cda/*} by its registration; Spring Security has
 * already disabled the global no-store header, so this value is authoritative.</p>
 *
 * <p><b>Tenant-keyed caching (SH22 Part F).</b> A CDA response depends on the
 * authenticating {@code Key} token — which determines the tenant (SH22), the
 * per-site allow-list (SH2) and the content environment (SH5). Emitting
 * {@code Vary: Key} makes a shared proxy/CDN key its cache by token, so tenant A's
 * body is never served to tenant B (different token → different cache entry). A
 * body-hash {@code ETag} collision between tenants is harmless on its own: a
 * {@code 304} makes a client re-use <em>its own</em> cached (tenant-correct) body,
 * and {@code Vary} keeps shared-cache entries separate. (No server-side cache is
 * involved, and since SH1261 none is even on the classpath; were one added, its keys would
 * have to be {@code {tenantId}:{id}}.)</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
public class ShCdaCacheControlFilter extends OncePerRequestFilter {

    /** The CDA auth header (see {@code ShApiTokenFilter}); the cache-key dimension. */
    private static final String TOKEN_HEADER = "Key";

    private final ShCdaProperties properties;

    public ShCdaCacheControlFilter(ShCdaProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request,
                                    @NotNull HttpServletResponse response,
                                    @NotNull FilterChain filterChain) throws ServletException, IOException {
        long maxAge = properties.getCache().getMaxAgeSeconds();
        CacheControl cacheControl = maxAge > 0
                ? CacheControl.maxAge(java.time.Duration.ofSeconds(maxAge)).cachePublic()
                : CacheControl.noCache();
        response.setHeader(HttpHeaders.CACHE_CONTROL, cacheControl.getHeaderValue());
        // Tenant/site/env all derive from the token, so shared caches must key by it.
        // addHeader (not set) so a CORS-added Vary: Origin is preserved alongside.
        response.addHeader(HttpHeaders.VARY, TOKEN_HEADER);
        filterChain.doFilter(request, response);
    }
}
