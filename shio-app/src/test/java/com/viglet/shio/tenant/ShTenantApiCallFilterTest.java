/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * SH45 — which request counts as which tenant's call, and which does not count at all.
 *
 * <h2>The two decisions this pins</h2>
 *
 * <p><b>The surface vocabulary</b>, because the whole reason the meter records three keys instead
 * of one total is that "what counts as a call" is a pricing question: a classifier that quietly
 * folded {@code /api/v2/agent/**} into {@code console} would throw an answer away that no later
 * migration could recover.</p>
 *
 * <p><b>The three statuses that are not billed.</b> {@code 401}/{@code 403}/{@code 429} are the
 * responses anyone on the internet can produce without a credential, so counting them would let a
 * stranger run up a tenant's bill. A {@code 404} is counted, because the instance did the work of
 * deciding.</p>
 *
 * @author Alexandre Oliveira
 */
class ShTenantApiCallFilterTest {

    private final RecordingMeter meter = new RecordingMeter();
    private final ShTenantApiCallFilter filter = new ShTenantApiCallFilter(meter);

    @AfterEach
    void clearTenant() {
        ShTenantContext.clear();
    }

    @Test
    @DisplayName("SH45: each API surface is classified, and nothing else is metered")
    void surfacesAreToldApart() {
        assertThat(ShTenantApiCallFilter.surfaceOf("/api/v2/cda/post/1"))
                .isEqualTo(ShTenantApiCallMeter.CDA);
        assertThat(ShTenantApiCallFilter.surfaceOf("/graphql")).isEqualTo(ShTenantApiCallMeter.CDA);
        assertThat(ShTenantApiCallFilter.surfaceOf("/api/v2/agent/context"))
                .isEqualTo(ShTenantApiCallMeter.AGENT);
        assertThat(ShTenantApiCallFilter.surfaceOf("/mcp"))
                .as("SH45: the MCP endpoint is the agent surface, and it does not live under /api")
                .isEqualTo(ShTenantApiCallMeter.AGENT);
        assertThat(ShTenantApiCallFilter.surfaceOf("/api/v2/post-unified/1"))
                .as("SH45: the general case is last — CDA and agent are both under /api, so a "
                        + "broadest-first classifier would fold all three into one and lose the "
                        + "distinction a plan prices by")
                .isEqualTo(ShTenantApiCallMeter.CONSOLE);
        assertThat(ShTenantApiCallFilter.surfaceOf("/preview/site/page"))
                .as("a rendered preview is not an API call")
                .isNull();
        assertThat(ShTenantApiCallFilter.surfaceOf(null)).isNull();
    }

    @Test
    @DisplayName("SH45: a served call is counted against the request's tenant")
    void aServedCallIsCounted() throws Exception {
        ShTenantContext.setTenantId("ut-sh45-filter");

        filter.doFilter(get("/api/v2/cda/post/1"), response(200), new MockFilterChain());

        assertThat(meter.tenant)
                .as("SH45: the meter runs INSIDE the security chain on purpose — ShTenantFilter "
                        + "always clears the context on the way out, so a meter mounted outside it "
                        + "would read an empty tenant on every request and file nothing")
                .isEqualTo("ut-sh45-filter");
        assertThat(meter.surface).isEqualTo(ShTenantApiCallMeter.CDA);
    }

    @Test
    @DisplayName("SH45: a refusal is not billed, and a 404 is")
    void refusalsAreNotBilled() throws Exception {
        ShTenantContext.setTenantId("ut-sh45-filter");

        for (int status : new int[] {401, 403, 429}) {
            meter.reset();
            filter.doFilter(get("/api/v2/cda/post/1"), response(status), new MockFilterChain());
            assertThat(meter.calls)
                    .as("SH45: %s costs no credential to produce, so counting it would let a "
                            + "stranger run up a tenant's bill", status)
                    .isZero();
        }

        meter.reset();
        filter.doFilter(get("/api/v2/cda/post/1"), response(404), new MockFilterChain());
        assertThat(meter.calls)
                .as("SH45: the instance did the work of deciding — a 404 is a real call")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("SH45: metering never turns a served request into a failure")
    void meteringNeverFailsTheRequest() throws Exception {
        ShTenantContext.setTenantId("ut-sh45-filter");
        ShTenantApiCallFilter throwing = new ShTenantApiCallFilter(new RecordingMeter() {
            @Override
            public void record(String tenant, String surface) {
                throw new IllegalStateException("meter is down");
            }
        });

        MockHttpServletResponse response = response(200);
        throwing.doFilter(get("/api/v2/cda/post/1"), response, new MockFilterChain());

        assertThat(response.getStatus())
                .as("SH45: a meter is an accounting side-effect of a call already served; letting "
                        + "it turn a 200 into a 500 would trade the product for the invoice")
                .isEqualTo(200);
    }

    private static MockHttpServletRequest get(String path) {
        return new MockHttpServletRequest("GET", path);
    }

    private static MockHttpServletResponse response(int status) {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(status);
        return response;
    }

    /** A meter that only remembers, so the filter's decisions are what is under test. */
    private static class RecordingMeter extends ShTenantApiCallMeter {

        private String tenant;
        private String surface;
        private int calls;

        RecordingMeter() {
            super(null, new ShMeterProperties(), null, java.time.Clock.systemUTC());
        }

        @Override
        public void record(String tenant, String surface) {
            this.tenant = tenant;
            this.surface = surface;
            this.calls++;
        }

        void reset() {
            tenant = null;
            surface = null;
            calls = 0;
        }
    }
}
