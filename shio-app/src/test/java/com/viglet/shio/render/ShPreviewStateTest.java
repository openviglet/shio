/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.security.Principal;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * SH990 — {@code /preview} reads {@code state} with the grammar every other read uses.
 *
 * <p>Before, the route compared the value to {@code PUBLISHED} and previewed the draft for anything
 * else, so {@code state=live} — or a revision number — showed the draft under a 200 and a banner
 * that said nothing about the substitution. The refusals come before any site is looked up, which is
 * why these requests name a site that does not exist.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShPreviewStateTest {

    private static final String NO_SITE = "/preview/sh990-no-such-site/about";

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;
    private Principal principal;

    @BeforeAll
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        principal = Mockito.mock(Principal.class);
        Mockito.when(principal.getName()).thenReturn("admin");
    }

    private MvcResult preview(String state) throws Exception {
        var request = get(NO_SITE).principal(principal);
        return mockMvc.perform(state == null ? request : request.param("state", state)).andReturn();
    }

    @Test
    @DisplayName("SH990: a state outside the grammar is a teaching 400 on /preview, not the draft")
    void anUnknownStateIsRefused() throws Exception {
        MvcResult result = preview("live");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString())
                .contains("unknown-state")
                .contains("rev:<n>");
    }

    @Test
    @DisplayName("SH990: a revision asked of /preview is refused with the read that serves one")
    void aRevisionIsRefusedWithTheCallThatServesIt() throws Exception {
        MvcResult result = preview("rev:2");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString())
                .contains("state-revision-not-served")
                .contains("/api/v2/agent/read");
    }

    @Test
    @DisplayName("SH990: draft, PUBLISHED and no state at all get past the grammar to the page lookup")
    void theGrammarsWordsReachTheRenderer() throws Exception {
        for (String state : new String[] { null, "draft", "PUBLISHED" }) {
            assertThat(preview(state).getResponse().getStatus())
                    .as("state=%s reaches the renderer, which answers for the missing site", state)
                    .isNotEqualTo(400);
        }
    }
}
