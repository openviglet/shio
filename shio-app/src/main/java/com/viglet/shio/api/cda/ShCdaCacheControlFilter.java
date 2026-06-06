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
 * @author Alexandre Oliveira
 * @since 2026.2
 */
public class ShCdaCacheControlFilter extends OncePerRequestFilter {

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
        filterChain.doFilter(request, response);
    }
}
