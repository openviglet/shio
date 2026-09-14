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
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import org.jetbrains.annotations.NotNull;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriUtils;

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

    /**
     * A segment whose every {@code %} is followed by two hex digits. Anything else is what
     * {@link UriUtils#decode} refuses with an exception.
     */
    private static final Pattern WELL_FORMED = Pattern.compile("(?:[^%]|%\\p{XDigit}{2})*");

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

    /**
     * The last path segment of {@code /file_source/<id>/<name>}, decoded, or {@code null} if there
     * is none.
     *
     * <p>SH1071: decoded because {@code getRequestURI()} is not, by the servlet spec, and the resource
     * handler behind this decodes it to find the file. Read raw, {@code relatório.pdf} downloaded as
     * {@code relat%C3%B3rio.pdf} — and the one character a raw URI cannot carry, {@code "}, arrived
     * as {@code %22}, which is why the quoting defect was never visible on this route. It is now, and
     * {@link ShAssetResponseHeaders#disposition} is what makes that safe.</p>
     */
    private static String fileNameOf(String uri) {
        if (uri == null) {
            return null;
        }
        int slash = uri.lastIndexOf('/');
        if (slash < 0 || slash == uri.length() - 1) {
            return null;
        }
        String segment = uri.substring(slash + 1);
        // A broken escape is the connector's to refuse, and Tomcat does before this runs. Checked
        // rather than caught, so a request that gets here anyway keeps its headers and its raw
        // segment instead of a 500 — and adds no silent catch to SH270's inventory.
        return WELL_FORMED.matcher(segment).matches()
                ? UriUtils.decode(segment, StandardCharsets.UTF_8)
                : segment;
    }
}
