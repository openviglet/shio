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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * A draft is never public — the claim {@code ShRenderAPI} makes, asserted.
 *
 * <h2>The gap this closes</h2>
 *
 * <p>{@code ShRenderAPI}'s class note says the preview <em>"sits under
 * {@code anyRequest().authenticated()} like the rest of the console, so a draft is never
 * public"</em>. It did not. {@code ShSecurityConfig} carried {@code mvc.matcher("/preview/**")}
 * inside {@code permitAll()} — in <b>both</b> filter chains — and the first matching rule wins, so
 * the entry the comment relied on was never reached.</p>
 *
 * <p>The origin is worth recording because it is the shape rather than the slip: that
 * {@code permitAll()} entry predates the renderer by years, from when {@code /preview} meant
 * something else on the legacy console. SH183 then built {@code GET /preview/{site}/{url}} on the
 * path, defaulting to {@code DRAFT} — so <b>every unpublished draft on the instance was readable
 * by anyone who could reach the port</b>, and nothing anywhere disagreed out loud. A route
 * inheriting an allow-list entry written for a different route is invisible to review, because
 * both halves read correctly on their own.</p>
 *
 * <p>Asserted from the outside, with no credentials, which is the only view that answers the
 * question. A unit test over the config would restate it.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShPreviewIsNotPublicTest {

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
    @DisplayName("an anonymous caller cannot read a preview, whatever the site")
    void anAnonymousPreviewIsRefused() throws Exception {
        // Deliberately a site that does not exist. A 404 would be a *leak* of the same kind the
        // SH2 rule names — it would mean the request reached the renderer, and a real site name
        // would then have rendered its drafts. What must come back is a refusal that happens
        // before anything is resolved.
        secured.perform(get("/preview/any-site/any-page"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("and the editor bridge is behind the same door")
    void theEditorScriptIsRefusedToo() throws Exception {
        // `/preview/_shio/editor.js` is served from the same route. It carries no content, so this
        // is not a leak on its own — it is here because the whole subtree has to move together: an
        // exemption carved for the script would be a second entry somebody later widens.
        secured.perform(get("/preview/_shio/editor.js"))
                .andExpect(status().isUnauthorized());
    }
}
