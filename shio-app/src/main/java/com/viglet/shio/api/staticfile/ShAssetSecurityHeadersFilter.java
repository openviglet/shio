/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.staticfile;

import java.io.IOException;

import org.jetbrains.annotations.NotNull;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * {@code /file_source/**} is served by a Spring resource handler, so it is the one asset route with
 * no {@link org.springframework.http.ResponseEntity} to hang headers on (SH950).
 *
 * <p>It is also the one that matters most: {@code ShSecurityConfig} marks it {@code permitAll}, so
 * an uploaded {@code payload.html} was a public, same-origin document on the host the console runs
 * on. The other two routes call {@link ShAssetResponseHeaders#harden} where they build their
 * response; this does the same thing for the route that cannot.</p>
 *
 * <p><b>The type comes from the request path, not from the response.</b> Headers have to be set
 * before the handler writes the body, and at that point nothing has decided a content type — so
 * this reads it from the file name in the URL, which is the same first move
 * {@link ShFileStorageService#probeContentType} makes. A transform query ({@code ?format=webp})
 * can change the type that is actually sent, and cannot change the answer: every format the
 * pipeline emits is a raster image, and so was the source it read.</p>
 *
 * <p>Scoped to the one prefix by its registration in {@link ShAssetSecurityConfig}, so nothing here
 * can reach a page render — a delivered page is {@code text/html} on purpose, and an
 * {@code attachment} disposition on it would take the site down.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public class ShAssetSecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request,
                                    @NotNull HttpServletResponse response,
                                    @NotNull FilterChain filterChain) throws ServletException, IOException {
        String fileName = fileNameOf(request.getRequestURI());
        String contentType = ShAssetResponseHeaders.contentTypeByName(fileName);
        ShAssetResponseHeaders.harden(response,
                contentType == null ? ShAssetResponseHeaders.FALLBACK_CONTENT_TYPE : contentType,
                fileName);
        filterChain.doFilter(request, response);
    }

    /** The last path segment of {@code /file_source/<id>/<name>}, or {@code null} if there is none. */
    private static String fileNameOf(String uri) {
        if (uri == null) {
            return null;
        }
        int slash = uri.lastIndexOf('/');
        if (slash < 0 || slash == uri.length() - 1) {
            return null;
        }
        return uri.substring(slash + 1);
    }
}
