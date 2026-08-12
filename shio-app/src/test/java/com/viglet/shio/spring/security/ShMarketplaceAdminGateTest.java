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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * SH682 — the marketplace writes want the role the blueprint reload already has.
 *
 * <h2>The argument the assertion encodes</h2>
 *
 * <p>{@code POST /api/v2/marketplace/{id}/install} and {@code DELETE /api/v2/marketplace/{id}} were
 * {@code authenticated()} — the blanket rule, and therefore no line in the config at all. Merely
 * <em>re-reading</em> the operator's package directory is admin-gated (SH219), because it changes
 * what every later apply can do; installing is that act <b>plus a download</b>, so it cannot
 * honestly be gated more weakly than the thing it contains.</p>
 *
 * <h2>Why this test is the deliverable and the config change is one line</h2>
 *
 * <p>SH677 wrote the {@code hasRole("ADMIN")} first and took it out again: the role was unreachable
 * for a locally authenticated user, so the reload answered <b>403 to the seeded admin</b>. A gate on
 * a role nothing grants is not safety, it is a dead capability that teaches the next reader the gate
 * is decorative — and nothing failed, because no test asked whether an administrator gets
 * <em>through</em>. {@link ShLocalAdminAuthorityTest} now asks that of the reload; this asks it of
 * the writes it justifies, so the pair cannot go dead again without a red run.</p>
 *
 * <p>Both halves are asserted on purpose. A test that only proves the refusal passes perfectly well
 * against a gate nobody can pass, which is the exact state SH681 found and the reason "it is gated"
 * was believed for four tasks.</p>
 *
 * <h2>What it does not cover</h2>
 *
 * <p>The Keycloak branch of {@code ShSecurityConfig#filterChain}, which carries the same two
 * matchers: reaching it needs an OIDC provider this suite does not stand up. The rule there is a
 * copy of the one exercised here and is asserted only by reading.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShMarketplaceAdminGateTest {

    /** Any id: authorization answers before the controller resolves one, which is the point. */
    private static final String INSTALL = "/api/v2/marketplace/sh682-nothing/install";
    private static final String UNINSTALL = "/api/v2/marketplace/sh682-nothing";
    private static final String CATALOG = "/api/v2/marketplace";

    @Autowired
    private ShCustomUserDetailsService userDetailsService;
    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeAll
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    @DisplayName("SH682: an authenticated caller who is not an administrator cannot install")
    void aNonAdminIsRefusedTheInstall() throws Exception {
        assertStatus(post(INSTALL).with(asEditor()).with(csrf()), 403,
                "installing a stranger's package is the operator's act, and 'holds any account' "
                        + "is not that");
    }

    @Test
    @DisplayName("SH682: nor uninstall")
    void aNonAdminIsRefusedTheUninstall() throws Exception {
        assertStatus(delete(UNINSTALL).with(asEditor()).with(csrf()), 403,
                "removing a package changes what every later apply can do, exactly as the reload "
                        + "does");
    }

    @Test
    @DisplayName("SH682: and the seeded administrator is not refused — the half SH681 was missing")
    void theSeededAdminReachesBothWrites() throws Exception {
        // Anything but 403 is the claim. Whether the install then succeeds is SH646's business, and
        // asserting its body here would couple this test to a catalog it does not control.
        assertNotForbidden(post(INSTALL).with(asAdmin()).with(csrf()),
                "the real seeded admin, resolved through ShCustomUserDetailsService — not a "
                        + "fabricated ROLE_ADMIN, which would pass against the dead gate too");
        assertNotForbidden(delete(UNINSTALL).with(asAdmin()).with(csrf()),
                "the same, for the write on the other verb");
    }

    @Test
    @DisplayName("SH682: the catalog read stays open to anyone authenticated")
    void theReadsDidNotMoveWithTheWrites() throws Exception {
        // The gate is on POST and DELETE, not on the path: narrowing the reads too would make
        // browsing the marketplace an admin act and hide from a curator what their operator
        // installed.
        assertNotForbidden(get(CATALOG).with(asEditor()),
                "reads are `authenticated()` and SH682 moved only the writes");
    }

    /** An authenticated principal carrying no ROLE_ADMIN — the caller the gate exists to stop. */
    private static org.springframework.test.web.servlet.request.RequestPostProcessor asEditor() {
        return user("sh682-editor").roles("USER");
    }

    /** The seeded administrator, loaded the way a password login loads it (SH681). */
    private org.springframework.test.web.servlet.request.RequestPostProcessor asAdmin() {
        return user(userDetailsService.loadUserByUsername("admin"));
    }

    private void assertStatus(RequestBuilder request, int expected, String because) throws Exception {
        mvc.perform(request).andExpect(result -> assertThat(result.getResponse().getStatus())
                .as(because)
                .isEqualTo(expected));
    }

    private void assertNotForbidden(RequestBuilder request, String because) throws Exception {
        mvc.perform(request).andExpect(result -> assertThat(result.getResponse().getStatus())
                .as(because)
                .isNotEqualTo(403));
    }
}
