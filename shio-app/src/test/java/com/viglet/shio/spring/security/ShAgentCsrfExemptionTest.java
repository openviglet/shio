/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.spring.security;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import jakarta.servlet.http.Cookie;

/**
 * SH167 — the narrowed CSRF exemption, proved through the real filter chain.
 *
 * <p>{@link ShCsrfExemptionLintTest} guards the <em>shape</em> of the configuration; this
 * guards that the shape has the effect it is supposed to. The two failure modes are opposite
 * and both silent: exempt too widely and {@code POST /api/v2/agent/batch} — the endpoint that
 * deletes content — is reachable from a cross-site form riding a logged-in curator's session;
 * exempt too narrowly and every agent, every MCP client and the {@code shio} CLI get a 403 for
 * a CSRF token they have no session to obtain.</p>
 *
 * <p>The distinguishing assertion is the pair of status codes. A cookie-less caller is <b>401</b>
 * — it passed CSRF and failed authentication, which is the agent path working. A cookie-bearing
 * caller with no {@code X-XSRF-TOKEN} is <b>403</b>, rejected by {@code CsrfFilter} before
 * authentication runs at all. Asserting only one of the two would pass on a chain that rejects
 * everything.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentCsrfExemptionTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc secured;

    @BeforeAll
    void setUp() {
        secured = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("SH167: a session-less agent write passes CSRF and stops at authentication")
    void aSessionLessAgentWriteIsStillExempt() throws Exception {
        secured.perform(post("/api/v2/agent/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ops\":[]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("SH167: a session-bearing agent write with no XSRF token is refused")
    void aSessionBearingAgentWriteIsCsrfChecked() throws Exception {
        secured.perform(post("/api/v2/agent/batch")
                        .cookie(new Cookie("JSESSIONID", "forged-by-a-cross-site-form"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ops\":[]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SH167: MCP is narrowed the same way")
    void mcpIsNarrowedTheSameWay() throws Exception {
        secured.perform(post("/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"))
                .andExpect(status().isUnauthorized());
        secured.perform(post("/mcp")
                        .cookie(new Cookie("JSESSIONID", "forged-by-a-cross-site-form"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"))
                .andExpect(status().isForbidden());
    }

    /**
     * The CDA write surface was exempt wholesale on the same premise. Narrowing it changes
     * nothing that works today — a session-bearing CDA write already failed for want of
     * {@code ROLE_CDA_WRITE}, which a console login does not carry — and it means the day
     * something teaches the browser to reach the CDA with a cookie, the check is already there.
     */
    @Test
    @DisplayName("SH167: the CDA write surface is narrowed too")
    void theCdaWriteSurfaceIsNarrowedToo() throws Exception {
        secured.perform(post("/api/v2/cda/post/any-id/publish")
                        .cookie(new Cookie("JSESSIONID", "forged-by-a-cross-site-form"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }
}
