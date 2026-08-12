/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.spring.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * SH681 — a locally authenticated administrator can reach an admin-gated path.
 *
 * <h2>What was measured</h2>
 *
 * <p>{@code POST /api/v2/agent/blueprints/reload} answered <b>403</b> to the seeded {@code admin} —
 * over a console session <em>and</em> over Basic auth — while {@code /api/v2/user/current} reported
 * {@code "admin": true}. The endpoint exists to be called by an administrator and had not been
 * callable by one since SH219.</p>
 *
 * <p>{@link ShAuthorityResolver} grants {@code ROLE_ADMIN} from the {@code Administrator} group and
 * was referenced by exactly two classes, both OAuth. {@link ShCustomUserDetailsService} walked the
 * same tables and built authorities from {@code ShRole} names alone, and a fresh instance has none:
 * {@code GET /api/v2/role} answers {@code []}. So a password login carried no role authorities at
 * all, and every {@code hasRole("ADMIN")} gate was unreachable from it.</p>
 *
 * <h2>Why the assertion is at the gate</h2>
 *
 * <p>SH527's rule: the assertion that would have caught it, at the boundary it crossed. A unit test
 * of the resolver would have passed throughout — the resolver was never wrong. What nothing asked is
 * whether the authority it grants <em>arrives</em> at a request, which is one composition away and is
 * where the two years of 403 lived.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
class ShLocalAdminAuthorityTest {

    /** The one admin-gated path on the agent surface (SH219). */
    private static final String BLUEPRINT_RELOAD = "/api/v2/agent/blueprints/reload";

    @Autowired
    private ShCustomUserDetailsService userDetailsService;
    @Autowired
    private WebApplicationContext context;

    @Test
    @DisplayName("SH681: the seeded admin's local login carries ROLE_ADMIN")
    void aLocalAdminCarriesTheAuthority() {
        UserDetails details = userDetailsService.loadUserByUsername("admin");

        assertThat(details.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .as("""
                        `admin` is in the Administrator group, which is what makes \
                        /api/v2/user/current report `"admin": true`. Before SH681 this login \
                        carried NO role authority at all — the group rule lived in \
                        ShAuthorityResolver and only OAuth2/OIDC asked it.""")
                .contains("ROLE_ADMIN");
    }

    @Test
    @DisplayName("SH681: and reaches the admin-gated path instead of a 403")
    void aLocalAdminReachesTheGatedPath() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                        .springSecurity())
                .build();

        // The measurement, as an assertion. Anything but 403 is the claim: whether the reload then
        // succeeds is SH219's business, and asserting its body here would couple this to it.
        mvc.perform(post(BLUEPRINT_RELOAD)
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.user(
                                        userDetailsService.loadUserByUsername("admin")))
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("""
                                403 is what a locally authenticated administrator got from the one \
                                admin-gated path on the agent surface, for as long as that path has \
                                existed (SH681). The authority now arrives at the request.""")
                        .isNotEqualTo(403));
    }
}
