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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.viglet.core.tenancy.VigletPlanLimits;
import com.viglet.core.tenancy.VigletQuotaService;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;
import com.viglet.shio.persistence.repository.tenant.ShTenantApiCallRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SH1352 — a tenant at its plan's daily agent ceiling is refused with a 402 that says when it resets,
 * and the call after the reset is admitted.
 *
 * @author Alexandre Oliveira
 */
class ShAgentCallCapTest {

    /** A clock the test can move across midnight. */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-11-05T22:00:00Z");

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MovableClock clock = new MovableClock();
    private final ShTenantApiCallMeter meter = new ShTenantApiCallMeter(mock(ShTenantApiCallRepository.class),
            new ShMeterProperties(), null, clock);
    private final ShVigletResourceCounter counter = new ShVigletResourceCounter(mock(ShSiteRepository.class),
            mock(ShPostUnifiedRepository.class), mock(ShTenantStorageMeter.class), meter);

    private ShAgentCallCapFilter filter(VigletPlanLimits limits) {
        VigletQuotaService quota = mock(VigletQuotaService.class);
        when(quota.currentLimits()).thenReturn(limits);
        return new ShAgentCallCapFilter(quota, counter, meter, tenant -> "FREE");
    }

    private MockHttpServletResponse call(ShAgentCallCapFilter filter, MockFilterChain chain) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v2/agent/batch");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    @DisplayName("SH1352: at the ceiling the next agent call is refused, and after the reset it is admitted")
    void theCeilingRefusesUntilTheReset() throws Exception {
        ShAgentCallCapFilter filter = filter(VigletPlanLimits.of(ShVigletResourceCounter.AGENT_CALLS, 3L));
        String tenant = ShTenantContext.currentOrDefault();
        for (int i = 0; i < 3; i++) {
            MockFilterChain admitted = new MockFilterChain();
            assertThat(call(filter, admitted).getStatus()).isEqualTo(200);
            assertThat(admitted.getRequest()).as("under the ceiling the call runs").isNotNull();
            meter.record(tenant, ShTenantApiCallMeter.AGENT);
        }

        MockFilterChain refusedChain = new MockFilterChain();
        MockHttpServletResponse refused = call(filter, refusedChain);
        assertThat(refused.getStatus()).isEqualTo(402);
        assertThat(refusedChain.getRequest()).as("refused before the call runs").isNull();
        JsonNode body = new ObjectMapper().readTree(refused.getContentAsString());
        assertThat(body.get("limit").asLong()).isEqualTo(3);
        assertThat(body.get("used").asLong()).isEqualTo(3);
        assertThat(body.get("plan").asString()).isEqualTo("FREE");
        assertThat(body.get("resetsAt").asString()).isEqualTo("2026-11-06T00:00:00Z");
        assertThat(body.get("fix").asString()).isNotBlank();
        assertThat(refused.getHeader("Retry-After")).isNotBlank();

        clock.advance(Duration.ofHours(3));
        MockFilterChain afterReset = new MockFilterChain();
        assertThat(call(filter, afterReset).getStatus()).isEqualTo(200);
        assertThat(afterReset.getRequest()).as("a new UTC day is a new window").isNotNull();
    }

    @Test
    @DisplayName("SH1352: a plan with no agent ceiling is never refused")
    void anUnlimitedPlanIsNotCapped() throws Exception {
        ShAgentCallCapFilter filter = filter(VigletPlanLimits.UNLIMITED);
        for (int i = 0; i < 10; i++) {
            meter.record(ShTenantContext.currentOrDefault(), ShTenantApiCallMeter.AGENT);
        }
        MockFilterChain chain = new MockFilterChain();
        assertThat(call(filter, chain).getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("SH1352: the FREE plan carries the agent ceiling, and a paid plan does not")
    void theFreePlanCarriesTheCeiling() {
        assertThat(counter.limitsFor("FREE").limit(ShVigletResourceCounter.AGENT_CALLS))
                .isEqualTo(ShTenantPlanLimits.FREE_AGENT_CALLS_PER_DAY);
        assertThat(counter.limitsFor("PRO").isUnlimited(ShVigletResourceCounter.AGENT_CALLS)).isTrue();
    }

    @Test
    @DisplayName("SH1352: a 402 is a refusal the meter does not bill")
    void aRefusalIsNotMetered() throws Exception {
        String tenant = ShTenantContext.currentOrDefault();
        ShTenantApiCallFilter metering = new ShTenantApiCallFilter(meter);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v2/agent/batch");
        MockHttpServletResponse response = new MockHttpServletResponse();
        metering.doFilter(request, response, (req, res) -> ((MockHttpServletResponse) res).setStatus(402));
        assertThat(meter.pendingCalls(tenant, ShTenantApiCallMeter.AGENT)).isZero();
    }
}
