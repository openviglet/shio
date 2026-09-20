/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.console;

import java.io.IOException;

import org.jetbrains.annotations.NotNull;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * SH1048 — a validator on the console's own reads, so an unchanged one costs a 304.
 *
 * <h2>What was missing</h2>
 *
 * <p>SH3 gave the delivery API a body-hash {@code ETag} and a {@code Cache-Control}, and SH116 gave
 * the agent surface conditional reads of its own. Everything the <em>console</em> calls —
 * {@code /api/v2/object/…/list}, {@code /api/v2/post-unified}, {@code /api/v2/post-type},
 * {@code /api/v2/site}, {@code /api/v2/staticfile}, {@code /api/v2/widget} — answered a full
 * {@code 200} with no validator and no freshness hint. A curator walking a tree and coming back
 * paid for every byte again, and so did the CLI and MCP, which call the same REST.</p>
 *
 * <h2>One filter, both headers, and why it is not two</h2>
 *
 * <p>{@link ShallowEtagHeaderFilter} decides what to buffer, and the {@code Cache-Control} has to be
 * set <em>before</em> the body is written so it survives the {@code 304} the same filter produces.
 * Putting both in one class keeps the exclusion list — see {@link #shouldNotFilter} — in one place
 * too: two registrations with two patterns is two chances to exclude the streaming endpoint from
 * one of them.</p>
 *
 * <h2>{@code no-cache}, which is not {@code no-store}</h2>
 *
 * <p>{@code no-cache} means <em>revalidate</em>: the browser may keep the body and must ask before
 * reusing it. That is the whole saving here — the second read is a conditional request that comes
 * back empty, not a request that is never made. A console read depends on the caller's rights and
 * their tenant, so it is {@code cachePrivate}: a shared proxy must not hold it at all.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public class ShConsoleCacheFilter extends ShallowEtagHeaderFilter {

    /**
     * The two endpoints under {@code /api/v2} whose body must never be buffered.
     *
     * <p>Not an optimisation, a correctness rule. {@code ShallowEtagHeaderFilter} works by holding
     * the whole response in memory to hash it, which turns the change feed's Server-Sent Events
     * into a stream that never reaches the browser, and a site export into however many megabytes
     * the zip weighs held on the heap for a hash nobody will ask about twice.</p>
     */
    private static final String EVENTS = "/api/v2/events";
    private static final String EXPORT_SUFFIX = "/export";

    /**
     * The two surfaces that already have their own.
     *
     * <p>SH3 registers {@link ShallowEtagHeaderFilter} for the CDA and SH116 answers conditional
     * agent reads from the controller. Filtering them again would hash every delivery body twice
     * and — worse — compute a shallow body ETag over an agent response that has already returned a
     * semantic one, so the weaker validator would win.</p>
     */
    private static final String CDA_PREFIX = "/api/v2/cda/";
    private static final String AGENT_PREFIX = "/api/v2/agent/";

    /**
     * Reads only, and nothing that streams.
     *
     * <p>{@code ShallowEtagHeaderFilter} would skip a POST when it comes to stamping the header,
     * but it buffers the response first either way — so a write's body is copied through memory for
     * a validator it can never be given. Answering the question here is cheaper and says what the
     * rule is.</p>
     */
    @Override
    protected boolean shouldNotFilter(@NotNull HttpServletRequest request) {
        if (!HttpMethod.GET.matches(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return path == null
                || path.startsWith(CDA_PREFIX)
                || path.startsWith(AGENT_PREFIX)
                || path.startsWith(EVENTS)
                || path.endsWith(EXPORT_SUFFIX);
    }

    /**
     * The freshness hint, stamped before the chain so it survives a {@code 304}.
     *
     * <p>A {@code 304} carries no body and, in a servlet, nothing written after the chain returns
     * would reach the client anyway. Setting it on the way in is the same reason
     * {@code ShCdaCacheControlFilter} does.</p>
     */
    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request,
                                    @NotNull HttpServletResponse response,
                                    @NotNull FilterChain filterChain) throws ServletException, IOException {
        response.setHeader(HttpHeaders.CACHE_CONTROL,
                CacheControl.noCache().cachePrivate().getHeaderValue());
        super.doFilterInternal(request, response, filterChain);
    }
}
