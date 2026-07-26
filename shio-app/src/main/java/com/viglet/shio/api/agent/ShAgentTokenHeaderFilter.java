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
 * Stamps every agent response with {@code X-Shio-Est-Tokens} (SH78).
 *
 * <p>Making the cost visible is what makes design law <b>P3</b> enforceable: a
 * caller can log it, the SH112 benchmark can read it off real traffic instead of
 * re-tokenizing bodies, and the SH113 CI guard has a number to regress against.
 * Measured on the <em>serialized</em> body, so it covers the JSON and terse
 * renderings alike and cannot drift from what was actually sent.</p>
 *
 * <p>The body is buffered because a header cannot be set after the response is
 * committed — the same reason Spring's own ETag filter buffers. Agent responses are
 * bounded by their token budget, so the buffer is small by construction.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public class ShAgentTokenHeaderFilter extends OncePerRequestFilter {

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
