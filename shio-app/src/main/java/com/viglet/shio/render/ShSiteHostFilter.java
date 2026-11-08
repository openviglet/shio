/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import java.io.IOException;
import java.util.Optional;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * SH1354 — a read on {@code <site>.<service-domain>} is a read of {@code /sites/**}.
 *
 * <p>The request is <b>re-addressed, not forwarded</b>: a wrapper whose URI carries the
 * {@code /sites} prefix goes down the same chain, so security, encoding and every other filter see
 * exactly the request a {@code /sites/…} URL would have been, and {@link ShSitesAPI} stays the one
 * delivery route. A forward would skip the filters registered for {@code REQUEST} only, Spring
 * Security among them.</p>
 *
 * <p>Three kinds of request pass untouched: anything that is not {@code GET} or {@code HEAD}
 * (delivery is read-only, and a form post goes to {@code /api/…}); the instance's own routes
 * ({@code /api}, {@code /mcp}, {@code /file_source}, {@code /preview}, {@code /sites}), which is the
 * same set the link rewrite leaves alone, so {@code {{file_url}}} and a form action keep working on a
 * site host; and every request on a host that is not a site host.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public class ShSiteHostFilter extends OncePerRequestFilter {

    /** The site label a site host named; {@link ShSitesAPI} reads it in place of a path segment. */
    public static final String SITE_LABEL = ShSiteHostFilter.class.getName() + ".label";

    private final ShSiteHost siteHost;

    public ShSiteHostFilter(ShSiteHost siteHost) {
        this.siteHost = siteHost;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<String> label = siteHost.enabled() ? siteHost.labelOf(request.getServerName()) : Optional.empty();
        if (label.isEmpty() || !isRead(request)) {
            chain.doFilter(request, response);
            return;
        }
        String context = request.getContextPath() == null ? "" : request.getContextPath();
        String within = request.getRequestURI().substring(context.length());
        if (within.isEmpty()) {
            within = "/";
        }
        if (ShPreviewLinks.isInstanceRoute(within)) {
            chain.doFilter(request, response);
            return;
        }
        request.setAttribute(SITE_LABEL, label.get());
        chain.doFilter(new Readdressed(request, context, ShSitesAPI.BASE + within), response);
    }

    private static boolean isRead(HttpServletRequest request) {
        return "GET".equalsIgnoreCase(request.getMethod()) || "HEAD".equalsIgnoreCase(request.getMethod());
    }

    /** The same request at {@code /sites/<path>}. */
    static final class Readdressed extends HttpServletRequestWrapper {

        private final String context;
        private final String path;

        Readdressed(HttpServletRequest request, String context, String path) {
            super(request);
            this.context = context;
            this.path = path;
        }

        @Override
        public String getRequestURI() {
            return context + path;
        }

        @Override
        public StringBuffer getRequestURL() {
            HttpServletRequest request = (HttpServletRequest) getRequest();
            StringBuffer original = request.getRequestURL();
            String uri = request.getRequestURI();
            int at = original.lastIndexOf(uri);
            StringBuffer url = new StringBuffer(at < 0 ? "" : original.substring(0, at));
            return url.append(getRequestURI());
        }

        @Override
        public String getServletPath() {
            // The servlet path is decoded; the URI is not.
            return org.springframework.web.util.UriUtils.decode(path, java.nio.charset.StandardCharsets.UTF_8);
        }

        @Override
        public String getPathInfo() {
            return null;
        }
    }
}
