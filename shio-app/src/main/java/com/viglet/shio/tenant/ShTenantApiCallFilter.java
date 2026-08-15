/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.tenant;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Classifies each API request by surface and counts it against the current tenant (SH45).
 *
 * <h2>Where it sits, and why that is the whole design</h2>
 *
 * <p>Registered at {@code LOWEST_PRECEDENCE - 30}, the precedence {@code ShAgentWebConfig} already
 * uses for the cost header and the incident recorder — <b>inside</b> the Spring Security chain.
 * That is not a preference: the tenant is resolved by {@link ShTenantFilter}, whose shared base
 * <em>always clears</em> the context on the way out, so a meter mounted outside the chain would
 * read an empty tenant on every request and file nothing. Inside it, the context is still bound
 * when {@code chain.doFilter} returns.</p>
 *
 * <h2>What is not counted</h2>
 *
 * <p>{@code 401}, {@code 403} and {@code 429} — the same three the SH3 precedent excludes from the
 * cost header, for a reason that is sharper here than there: those are the responses anyone on the
 * internet can generate without a credential, so counting them would let a stranger run up a
 * tenant's bill. Every other status is a real call, a {@code 404} included: the instance did the
 * work of deciding.</p>
 *
 * <p>A request with no resolved tenant is dropped rather than filed under the default, which
 * {@link ShTenantApiCallMeter#record} enforces — a call nobody can attribute is not a call somebody
 * should be billed for.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public class ShTenantApiCallFilter extends OncePerRequestFilter {

    private static final String CDA_PREFIX = "/api/v2/cda/";
    private static final String AGENT_PREFIX = "/api/v2/agent";
    private static final String GRAPHQL_PATH = "/graphql";
    private static final String MCP_PATH = "/mcp";
    private static final String API_PREFIX = "/api/";

    private final ShTenantApiCallMeter meter;

    public ShTenantApiCallFilter(ShTenantApiCallMeter meter) {
        this.meter = meter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } finally {
            meterCall(request, response);
        }
    }

    /**
     * Metering never fails a request. A meter is an accounting side-effect of a call that has
     * already been served, and letting it turn a {@code 200} into a {@code 500} would trade the
     * product for the invoice.
     */
    private void meterCall(HttpServletRequest request, HttpServletResponse response) {
        try {
            if (isRefusal(response.getStatus())) {
                return;
            }
            String surface = surfaceOf(path(request));
            if (surface != null) {
                // currentOrDefault, not getTenantId (SH761). On a single-tenant install — the
                // DEFAULT — ShTenantFilter is a no-op and nothing is ever bound, so the raw read
                // answered null on every request and the meter dropped all of them. Measured on a
                // live instance: five agent calls, every count zero, and the document naming the
                // very tenant it had counted nothing for.
                meter.record(ShTenantContext.currentOrDefault(), surface);
            }
        } catch (RuntimeException e) {
            logger.warn("SH45: could not meter a call: " + e.getMessage());
        }
    }

    /** Refused before the instance did any work for the caller — see the class javadoc. */
    private static boolean isRefusal(int status) {
        return status == HttpServletResponse.SC_UNAUTHORIZED
                || status == HttpServletResponse.SC_FORBIDDEN
                || status == 429;
    }

    /**
     * Which surface a path belongs to, or {@code null} for a path this meter has no opinion about.
     *
     * <p>Prefix matching in declaration order, narrowest first: {@code /api/v2/cda/...} and
     * {@code /api/v2/agent/...} are both under {@code /api/}, so the general case has to be last.</p>
     */
    static String surfaceOf(String path) {
        if (path == null) {
            return null;
        }
        if (path.startsWith(CDA_PREFIX) || path.equals(GRAPHQL_PATH)) {
            return ShTenantApiCallMeter.CDA;
        }
        if (path.startsWith(AGENT_PREFIX) || path.equals(MCP_PATH) || path.startsWith(MCP_PATH + "/")) {
            return ShTenantApiCallMeter.AGENT;
        }
        if (path.startsWith(API_PREFIX)) {
            return ShTenantApiCallMeter.CONSOLE;
        }
        return null;
    }

    private static String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && uri.startsWith(context)) {
            return uri.substring(context.length());
        }
        return uri;
    }
}
