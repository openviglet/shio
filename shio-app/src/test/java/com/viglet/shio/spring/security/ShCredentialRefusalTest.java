/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.spring.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * The refusal a caller meets first (SH622).
 *
 * <h2>Why nobody had noticed</h2>
 *
 * <p>The task named it, and it is the reason this file exists at all: <b>no test asked an
 * unauthenticated question and read the body.</b> {@code ShAnonymousRouteAccessTest} asserts the
 * <em>status</em> of every route — which is the property it is for — and a 401 whose body is
 * {@code {"error":"Unauthorized"}} passes it exactly as a teaching one does. So the assertions here
 * are all about bytes.</p>
 *
 * <h2>What was wrong, and why it was structural</h2>
 *
 * <p>SH499's execution run found {@code /api/v2/cda/site} answering 403 to console HTTP Basic with
 * Spring's own {@code {timestamp, status, error, path}}. P6 is implemented in
 * {@code @ControllerAdvice} and a rejected credential never reaches a controller — the filter chain
 * answers first — so <b>every</b> endpoint had one 4xx carrying no {@code fix}, and it is the one a
 * caller hits before any other.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShCredentialRefusalTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    private MockMvc mockMvc;

    @BeforeAll
    void setup() {
        // The real chain: these refusals are written by filters, so a MockMvc without it would
        // exercise the controllers this defect is defined by never reaching.
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    private String bodyOf(String path) throws Exception {
        return mockMvc.perform(get(path)).andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("SH622: an anonymous read is refused with a problem document, not a word")
    void theRefusalHasAProblemDocument() throws Exception {
        String body = bodyOf("/api/v2/agent/manifest");

        assertThat(body)
                .as("""
                        it used to be `{"error":"Unauthorized"}` — the status restated as a word, \\
                        with nothing about which credential this path wants or how to get one \\
                        (SH622)""")
                .contains("\"title\"")
                .contains("\"status\"")
                .contains("\"fix\"");
    }

    @Test
    @DisplayName("SH622: the fix names the credential this particular path wants")
    void theFixIsPathSpecific() throws Exception {
        // The field that was missing is not prose, it is *which* credential — and the answer
        // differs per surface, which is exactly why a generic body could not carry it.
        assertThat(bodyOf("/api/v2/agent/manifest"))
                .as("the agent surface wants an AGENT-scope token, sent as `Key` (SH622)")
                .contains("AGENT")
                .contains("Key");

        assertThat(bodyOf("/api/v2/cda/site"))
                .as("""
                        and the CDA wants a CDA token with the scope its verb needs — the endpoint \\
                        SH499's run actually found (SH622)""")
                .contains("CDA")
                .contains("/api/v2/api-token");
    }

    @Test
    @DisplayName("SH622: a console path is told it is not a token surface at all")
    void aConsolePathSaysSo() throws Exception {
        // The distinction a caller could not make: "your credential is too narrow" and "this
        // endpoint is not for this kind of credential" need opposite next moves, and with
        // `{timestamp, status, error, path}` they were the same answer.
        assertThat(bodyOf("/api/v2/site-webhook"))
                .as("an API token authenticates only the CDA, GraphQL, the agent surface and MCP")
                .contains("console session")
                .contains("not a token surface");
    }

    @Test
    @DisplayName("SH622: the document is problem+json, so a caller can parse one shape everywhere")
    void theContentTypeIsProblemJson() throws Exception {
        assertThat(mockMvc.perform(get("/api/v2/agent/manifest")).andReturn()
                .getResponse().getContentType())
                .as("""
                        the same media type the controller advice sends, because a caller that \\
                        learned the shape from one refusal must read the next one the same way""")
                .startsWith("application/problem+json");
    }
}
