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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

/**
 * SH450 — the preview's 401 carries a challenge, and the API's does not.
 *
 * <h2>Why this test exists at all</h2>
 *
 * <p>The defect it pins was invisible to every existing test and cost a whole visual gate. `shio
 * snapshot --against` reported {@code 100.00% of pixels} on a replica whose markup is the source's,
 * verbatim; the PNG it wrote was a white page reading {@code {"error":"Unauthorized"}}. Playwright's
 * {@code httpCredentials} answers a <b>challenge</b> and does not send {@code Authorization}
 * unprompted, and {@link ShAuthenticationEntryPoint} sent no {@code WWW-Authenticate}. The unit test
 * that was supposed to cover it asserted the credentials were handed to an <em>injected capture</em>
 * — true, and about the CLI's own plumbing rather than about whether a browser could get in.</p>
 *
 * <p>So this asserts the HTTP fact, at the layer that produces it, in both directions. The second
 * direction is not decoration: the reason the entry point was replaced in the first place is that a
 * challenge on the console's XHR opens the browser's native credential dialog in front of a React
 * login page, which a user cannot usefully answer and cannot get rid of.</p>
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShPreviewChallengeTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    private MockMvc mockMvc;

    @BeforeAll
    void setup() {
        // The real filter chain: the entry point is reached by Spring Security and not by a
        // controller, so a standalone setup would assert nothing about what a caller receives.
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @Test
    @DisplayName("SH450: an anonymous /preview request is challenged, so a browser can answer it")
    void thePreviewChallenges() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/preview/any-site/"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(result -> assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE))
                        .as("without this, Playwright's httpCredentials has nothing to answer and "
                                + "the browser never retries — which is a photograph of a 401 scored "
                                + "against a real site at 100%")
                        .isEqualTo(ShAuthenticationEntryPoint.CHALLENGE));
    }

    @Test
    @DisplayName("SH450: and the API's 401 still does not, because the console has a login page")
    void theApiDoesNotChallenge() throws Exception {
        // The reason the default entry point was replaced. A native credential dialog in front of
        // the React login page is both unanswerable and unremovable — dismiss it and the console is
        // blank. Keyed on the path rather than on `Accept`, because a console fetch can ask for HTML.
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v2/agent/manifest"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(result -> assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE))
                        .isNull());
    }

    @Test
    @DisplayName("SH459: a path no controller claims is absent, not behind a door")
    void anUnclaimedPathIsNotFound() throws Exception {
        // Measured before the fix: /fonts/x.woff2 and /nope answered 401, while /assets/missing.js
        // answered 404 — the only difference being that /assets/** is on the permitAll list, so it
        // reached the dispatcher and got the truth. A visitor asking for a missing font was told to
        // authenticate for a file that does not exist anywhere, which is a fact about the security
        // chain rather than about any route (SH450's family).
        for (String absent : new String[] { "/fonts/x.woff2", "/model-catalog/fonts/x.woff2", "/nope" }) {
            mockMvc.perform(MockMvcRequestBuilders.get(absent))
                    .andExpect(MockMvcResultMatchers.status().isNotFound());
        }
    }

    @Test
    @DisplayName("SH459: and a route that exists still answers 401, which the console depends on")
    void aClaimedPathStillRefuses() throws Exception {
        // The whole safety of the rule above. SH167's `anyRequest().authenticated()` default is
        // untouched: the condition is "no @RequestMapping claims this URI", so a new controller is
        // claimed the moment it exists. If this ever inverted, the React app would read a 404 where
        // it looks for a 401 and stop sending anybody to its login page.
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v2/agent/manifest"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
        mockMvc.perform(MockMvcRequestBuilders.get("/preview/any-site/"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
        // A path a controller claims with another method: the route exists and only the verb is
        // wrong, so 401 is honest and 404 would be a lie about a route that is there.
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v2/agent/batch"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test
    @DisplayName("SH450: the body is the same shape either way, so a program reads one contract")
    void theBodyIsUnchanged() throws Exception {
        // SH450's property, unchanged; SH622 changed the literal it used to be asserted against.
        // This pinned `{"error":"Unauthorized"}`, which was the whole defect SH622 fixed — a status
        // restated as a word. What SH450 actually claims is that a browser-challenged refusal and a
        // programmatic one differ in the HEADER and in nothing else, because a browser never renders
        // this body. So the assertion is now that comparison rather than a copy of the bytes.
        String challenged = mockMvc.perform(MockMvcRequestBuilders.get("/preview/any-site/"))
                .andReturn().getResponse().getContentAsString();
        String plain = mockMvc.perform(MockMvcRequestBuilders.get("/api/v2/agent/manifest"))
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(challenged)
                .as("both are problem documents, and both say what to do (SH622)")
                .contains("\"title\"").contains("\"fix\"");
        org.assertj.core.api.Assertions.assertThat(plain).contains("\"title\"").contains("\"fix\"");
        org.assertj.core.api.Assertions.assertThat(fieldsOf(challenged))
                .as("""
                        the same field set: making the two differ in more than the header would be                         a second contract for no reader (SH450)""")
                .isEqualTo(fieldsOf(plain));
    }

    /** The JSON keys, which is the shape SH450 is about — the values legitimately differ per path. */
    private static java.util.Set<String> fieldsOf(String json) {
        java.util.Set<String> keys = new java.util.TreeSet<>();
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("\"([a-zA-Z]+)\"\s*:").matcher(json);
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }
}
