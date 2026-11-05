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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import org.springframework.web.filter.OncePerRequestFilter;

import com.viglet.core.tenancy.VigletPlanLimits;
import com.viglet.core.tenancy.VigletQuotaService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * SH1352 — a plan's daily ceiling on agent calls, checked before the call runs.
 *
 * <h2>Why a filter, and why here</h2>
 *
 * <p>The agent surface is {@code /api/v2/agent/**} and {@code /mcp}, and every call to it is a model
 * turn somebody pays for, which is why it is the one flow a plan caps. SH45's meter already counts it
 * apart from CDA and console; this reads that count through the quota SPI
 * ({@link ShVigletResourceCounter#AGENT_CALLS}) and refuses when the plan's ceiling is reached.
 * Mounted beside the meter, inside the security chain, so the tenant is resolved and an
 * unauthenticated caller has already met its 401 and cannot spend a tenant's budget.</p>
 *
 * <p>Not {@code VigletQuotaService.check}: that throws a bare 402 with a sentence, and this refusal has
 * to say what a caller can act on — the plan, the ceiling, the count, and when the window resets.
 * The limit and the no-op cases (tenancy off, the default tenant, a paid plan) are still the
 * kernel's, through {@code currentLimits()}. The console and CDA surfaces are never capped here.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public class ShAgentCallCapFilter extends OncePerRequestFilter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final VigletQuotaService quotaService;
    private final ShVigletResourceCounter counter;
    private final ShTenantApiCallMeter meter;
    private final Function<String, String> planOf;

    public ShAgentCallCapFilter(VigletQuotaService quotaService, ShVigletResourceCounter counter,
            ShTenantApiCallMeter meter, Function<String, String> planOf) {
        this.quotaService = quotaService;
        this.counter = counter;
        this.meter = meter;
        this.planOf = planOf;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        VigletPlanLimits limits = quotaService.currentLimits();
        long max = limits.limit(ShVigletResourceCounter.AGENT_CALLS);
        if (limits.isUnlimited(max)) {
            chain.doFilter(request, response);
            return;
        }
        String tenant = ShTenantContext.currentOrDefault();
        long used = counter.count(tenant, ShVigletResourceCounter.AGENT_CALLS);
        if (used < max) {
            chain.doFilter(request, response);
            return;
        }
        refuse(response, tenant, max, used);
    }

    private void refuse(HttpServletResponse response, String tenant, long max, long used) throws IOException {
        String resetsAt = meter.nextPeriodStart().toString();
        String plan = planOf.apply(tenant);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "Agent call limit reached");
        body.put("status", HttpServletResponse.SC_PAYMENT_REQUIRED);
        body.put("detail", "The " + plan + " plan allows " + max + " agent calls a day and this tenant has made "
                + used + ". The count resets at " + resetsAt + ".");
        body.put("plan", plan);
        body.put("limit", max);
        body.put("used", used);
        body.put("resetsAt", resetsAt);
        body.put("fix", "Wait until resetsAt, or move the tenant to a plan without a daily agent ceiling. "
                + "The console and the delivery API are not capped.");
        response.setStatus(HttpServletResponse.SC_PAYMENT_REQUIRED);
        response.setHeader("Retry-After", Long.toString(Math.max(1,
                (meter.nextPeriodStart().toEpochMilli() - System.currentTimeMillis()) / 1000)));
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(MAPPER.writeValueAsString(body));
    }
}
