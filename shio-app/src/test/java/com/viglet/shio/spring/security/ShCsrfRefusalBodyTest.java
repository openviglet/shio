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
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import jakarta.servlet.http.Cookie;

/**
 * SH339's second half — <b>a CSRF refusal carries a body, and it is the right body.</b>
 *
 * <h2>The measurement that came first</h2>
 *
 * <p>Before this, a POST to {@code /api/v2/render/post-types}, {@code /api/v2/site},
 * {@code /api/v2/site-webhook} or {@code /api/v2/preview-token} answered <b>403, no content type,
 * empty body</b> — measured, not assumed. That is SH189's bodiless refusal on the surface whose
 * caller is most often a program, and it is what made SH327 cost an experiment to diagnose instead of
 * a read.</p>
 *
 * <h2>The two callers, and why the message branches</h2>
 *
 * <p>A browser has a session and forgot the token, so it can fetch one. A stateless caller has no
 * session, so it <em>cannot</em> — for it the 403 means "this route is not exempt", a fact about the
 * route. Telling that caller to fetch a token would be advice it cannot follow, which is why the
 * assertions below check the two bodies say different things. Asserting only one would pass on a
 * handler that gives everybody the browser's answer, which is the failure this is written against.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShCsrfRefusalBodyTest {

    /**
     * Session-required by design (SH170): the console's own site-creation endpoint, whose agent form
     * is `site.upsert` on the batch surface.
     *
     * <p>It used to be {@code /api/v2/render/post-types}, which <b>SH327 has since narrowed</b> — so
     * this constant moved rather than the assertions changing, and that is the point: the messages
     * below are about what a caller is told, not about which path happens to be closed this week.</p>
     */
    private static final String SESSION_REQUIRED = "/api/v2/site";

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
    @DisplayName("SH339: a stateless caller is told the route is not exempt, and where to write instead")
    void aStatelessCallerIsToldWhereToWriteInstead() throws Exception {
        MockHttpServletResponse response = secured.perform(post(SESSION_REQUIRED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn().getResponse();

        assertThat(response.getStatus())
                .as("the status is unchanged on purpose — the console's axios interceptor "
                        + "re-fetches the token and retries on exactly 403")
                .isEqualTo(403);
        assertThat(response.getContentType())
                .as("measured as null before SH339: no content type, no body, nothing to read")
                .startsWith("application/problem+json");

        String body = response.getContentAsString();
        assertThat(body)
                .as("""
                        the caller holds no session, so it cannot obtain a CSRF token at all — this \
                        403 is a fact about the route. Telling it to fetch a token would be advice \
                        it cannot follow.""")
                .contains("csrf-token-unobtainable")
                .contains(SESSION_REQUIRED)
                .contains("/api/v2/agent/batch");
        assertThat(body)
                .as("P6: a 4xx without a fix is incomplete")
                .contains("\"fix\"");
    }

    @Test
    @DisplayName("SH339: a session-bearing caller is told to fetch the token")
    void aSessionBearingCallerIsToldToFetchTheToken() throws Exception {
        MockHttpServletResponse response = secured.perform(post(SESSION_REQUIRED)
                        .cookie(new Cookie("JSESSIONID", "a-console-session"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(403);
        String body = response.getContentAsString();
        assertThat(body)
                .as("the browser's remedy, which is the one it can act on")
                .contains("csrf-token-required")
                .contains("/api/v2/csrf")
                .contains("X-XSRF-TOKEN");
        assertThat(body)
                .as("""
                        and NOT the stateless answer — a handler that sent one message to both \
                        would pass a test that only checked the other, which is why both are here.""")
                .doesNotContain("csrf-token-unobtainable");
    }

    @Test
    @DisplayName("SH327: the render model is reachable without a cookie")
    void theRenderModelIsReachableStatelessly() throws Exception {
        // The whole of Block P is "the visible site", and this was the one endpoint that makes a
        // site visible and that an agent could not call — SH338 named it as `serverRender` and SH339
        // named the CSRF half. A session-less caller now passes CSRF and stops at authentication
        // (401), which is the same signal SH167's own test uses for the agent namespace working.
        assertThat(secured.perform(post("/api/v2/render/post-types")
                        .contentType(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getStatus())
                .as("""
                        401, not 403: it passed CSRF and failed authentication. A 403 here means the \
                        narrowing was reverted and an agent is back to a refusal it cannot act on — \
                        it holds no session, so it cannot obtain the token being demanded.""")
                .isEqualTo(401);
    }

    @Test
    @DisplayName("SH344: the preview token is mintable without a cookie")
    void thePreviewTokenIsMintableStatelessly() throws Exception {
        // The last step of SH184's answer: an agent that deployed a front end needs the token that
        // front end uses to show a draft, and could not mint one. No new gate — the token is PREVIEW
        // scope for one post of one site, so it grants draft read the caller already has (SH106).
        assertThat(secured.perform(post("/api/v2/preview-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"postId\":\"whatever\"}"))
                .andReturn().getResponse().getStatus())
                .as("401, not 403: it passed CSRF and failed authentication")
                .isEqualTo(401);
    }

    @Test
    @DisplayName("SH339: the narrowed exemptions still work, so nothing was widened")
    void theNarrowedExemptionsStillWork() throws Exception {
        // A session-less agent write passes CSRF and stops at authentication (SH167's 401), which is
        // the assertion that says this handler changed the *message* and not the chain.
        secured.perform(post("/api/v2/agent/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ops\":[]}"))
                .andReturn();
        assertThat(secured.perform(post("/api/v2/agent/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ops\":[]}"))
                .andReturn().getResponse().getStatus())
                .as("401, not 403: it passed CSRF and failed authentication — the agent path working")
                .isEqualTo(401);
    }
}
