/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import java.io.IOException;

import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Stamps {@code X-Shio-Est-Tokens} on agent (SH78) and delivery (SH124) responses.
 *
 * <p>Making the cost visible is what makes design law <b>P3</b> enforceable: a
 * caller can log it, the SH112 benchmark can read it off real traffic instead of
 * re-tokenizing bodies, and the SH113 CI guard has a number to regress against.
 * Measured on the <em>serialized</em> body, so it covers every encoding and cannot
 * drift from what was actually sent.</p>
 *
 * <p>Applied to the CDA as well as the agent surface, because the reads an agent
 * makes most often are CDA reads — instrumenting only the agent namespace would
 * measure the cheap half of the traffic and miss the expensive one.</p>
 *
 * <p>The body is buffered because a header cannot be set after the response is
 * committed — the same reason Spring's own ETag filter buffers. On the CDA this
 * filter is registered <em>outside</em> the SH3 ETag filter, so a {@code 304} is
 * measured as the zero-byte response it actually is.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public class ShEstTokensHeaderFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Shio-Est-Tokens";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(request, wrapper);
            int bytes = wrapper.getContentSize();
            // UTF-8 byte length is within a rounding error of char length for the
            // ASCII-dominant payloads here, and the value is an estimate by contract.
            wrapper.setHeader(HEADER, String.valueOf(ShTokenBudget.estimateChars(bytes)));
        } finally {
            wrapper.copyBodyToResponse();
        }
    }
}
