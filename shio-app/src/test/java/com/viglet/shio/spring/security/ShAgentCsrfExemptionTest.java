/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.spring.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

import java.util.UUID;

import com.viglet.shio.persistence.model.token.ShApiToken;
import com.viglet.shio.persistence.model.token.ShApiTokenEnv;
import com.viglet.shio.persistence.model.token.ShApiTokenScope;
import com.viglet.shio.persistence.repository.token.ShApiTokenRepository;

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
    @Autowired
    private ShApiTokenRepository shApiTokenRepository;

    private MockMvc secured;
    private String writeKey;

    @BeforeAll
    void setUp() {
        secured = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        ShApiToken token = new ShApiToken();
        token.setTitle("ut-sh233");
        writeKey = "sh233-" + UUID.randomUUID().toString().replace("-", "");
        token.setToken(writeKey);
        token.setOwner("editor");
        token.setEnabled(true);
        token.setScope(ShApiTokenScope.WRITE);
        token.setEnv(ShApiTokenEnv.PROD);
        shApiTokenRepository.saveAndFlush(token);
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

    /**
     * SH233 — <b>which credential wins when a caller holds both, through the real chain.</b>
     *
     * <p>{@code ShApiTokenFilterTest} asserts the filter's own decision; this asserts the chain
     * *order* that made the defect possible: {@code SecurityContextHolderFilter} restores the
     * session before {@code ShApiTokenFilter} runs, so a filter that returned early on an existing
     * authentication never read the {@code Key} header. That is the Universal Editor's save-back,
     * and it is the arrangement no unit test can see.</p>
     *
     * <p>The assertion is "<b>not 403</b>" rather than a specific success code, on purpose: 403 is
     * the symptom — a curator's session has no {@code ROLE_CDA_WRITE} — and any other answer means
     * authorization passed and the request got as far as the content, which is the fact under
     * test. Pinning 404 here would couple this to how the CDA reports an unknown post id.</p>
     */
    @Test
    @DisplayName("SH233: a Key header beats a restored session on the CDA write surface")
    void aKeyHeaderBeatsARestoredSession() throws Exception {
        secured.perform(patch("/api/v2/cda/post/no-such-post-id")
                        .with(user("curator").roles("USER"))
                        .with(csrf())
                        .header("Key", writeKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attrs\":{}}"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 403) {
                        throw new AssertionError("403 — the request authenticated as the console "
                                + "session rather than the Key token, which is SH233 exactly: a "
                                + "curator has no ROLE_CDA_WRITE, so the editor's save-back dies "
                                + "here whatever the client sets withCredentials to.");
                    }
                });
    }

    /** ...and the same call without the key is still refused, so the assertion above means something. */
    @Test
    @DisplayName("SH233: the same call with no Key is still the curator, and still refused")
    void withoutAKeyItIsStillTheCurator() throws Exception {
        secured.perform(patch("/api/v2/cda/post/no-such-post-id")
                        .with(user("curator").roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attrs\":{}}"))
                .andExpect(status().isForbidden());
    }
}
