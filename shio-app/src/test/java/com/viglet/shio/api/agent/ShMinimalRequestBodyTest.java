/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.security.Principal;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * SH158 — <b>the minimal documented body of every write surface must bind.</b>
 *
 * <h2>Why a request record constructed in Java proves nothing</h2>
 *
 * <p>{@link ShRequestReadingConfigLintTest} closes the configuration half. This is the other
 * half, and it is the one that would have caught the original defect on the day it landed: the
 * write surfaces were covered by tests that call the service with a {@code new ShAgentBatch(…)},
 * which exercises the canonical constructor and <em>none</em> of the binding. So
 * {@code POST /api/v2/agent/batch {"ops":[…]}} — the exact body the manifest advertises — was a
 * bodiless 400 for four blocks with a green suite the whole time.</p>
 *
 * <h2>What is asserted, and why it is not the status code</h2>
 *
 * <p>The assertion is that Spring did not raise {@link HttpMessageNotReadableException}, not
 * that the call succeeded. A minimal body naturally lands on a business outcome — a teaching
 * 400 naming an unknown address, a 404, a 409 — and pinning any of those here would make this
 * class break every time an unrelated rule changed, which is how a guard becomes something
 * people delete. The distinction it does draw is the one that matters: <b>"I could not read
 * what you sent" is a different failure from "I read it and it is wrong"</b>, and only the
 * first one is invisible to every other test in the repository.</p>
 *
 * <p>Each body below is the shortest form the documentation shows — every optional flag
 * omitted, because an omitted primitive is precisely the thing that used to fail.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShMinimalRequestBodyTest {

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

    @Test
    @DisplayName("SH158: POST /agent/batch binds a body with dryRun omitted")
    void batchBindsWithoutDryRun() {
        assertBinds(post("/api/v2/agent/batch"), """
                {"ops":[{"op":"post.publish","address":"post:no-such-site/no-such-page"}]}""");
    }

    @Test
    @DisplayName("SH158: POST /agent/plan binds a document with prune omitted")
    void planBindsWithoutPrune() {
        assertBinds(post("/api/v2/agent/plan"), """
                {"site":"no-such-site","folders":[],"posts":[]}""");
    }

    @Test
    @DisplayName("SH158: POST /agent/apply binds a document with prune omitted")
    void applyBindsWithoutPrune() {
        assertBinds(post("/api/v2/agent/apply"), """
                {"site":"no-such-site","folders":[],"posts":[]}""");
    }

    @Test
    @DisplayName("SH158: POST /agent/blueprints/{name}/apply binds a body with all three flags omitted")
    void blueprintApplyBindsWithoutFlags() {
        assertBinds(post("/api/v2/agent/blueprints/blog/apply"), """
                {"site":"no-such-site"}""");
    }

    @Test
    @DisplayName("SH158: PUT /agent/memory binds a note with scope omitted")
    void memoryBindsWithoutScope() {
        assertBinds(put("/api/v2/agent/memory"), """
                {"key":"sh158-binding-probe","note":"written by ShMinimalRequestBodyTest"}""");
    }

    /**
     * The SH16 post-type surface has the densest primitive set on any request record —
     * {@code system}, {@code required}, {@code isTitle}, {@code isSummary}, {@code ordinal} —
     * and the CLI's canonical serialization omits most of them.
     */
    @Test
    @DisplayName("SH158: PUT /post-type/{name} binds a def with system and every field flag omitted")
    void postTypeBindsWithoutFlags() {
        assertBinds(put("/api/v2/post-type/Sh158BindingProbe"), """
                {"name":"Sh158BindingProbe","title":"Probe",\
                "fields":[{"name":"headline","widget":"Text"}]}""");
    }

    @Test
    @DisplayName("SH158: POST /post-unified binds a create with state omitted")
    void postUnifiedBindsWithoutState() {
        assertBinds(post("/api/v2/post-unified"), """
                {"folderId":"no-such-folder","postTypeId":"no-such-type","data":{}}""");
    }

    /**
     * Binding happens before the handler runs, so <em>any</em> outcome other than
     * {@link HttpMessageNotReadableException} is a pass — including an exception that escapes
     * {@code perform()} entirely, which is what a handler blowing up on a deliberately
     * nonexistent address looks like. Only the read failure is interesting here, and only
     * because it is the one no other test in the repository can see.
     */
    private void assertBinds(MockHttpServletRequestBuilder builder, String body) {
        Throwable failure = null;
        try {
            failure = mockMvc.perform(builder
                    .principal(principal)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andReturn()
                    .getResolvedException();
        } catch (Exception thrownFromHandler) {
            failure = thrownFromHandler;
        }

        assertThat(unreadable(failure))
                .as("""
                        SH158: the body below is the minimal documented form and Spring could not read it, \
                        which means an omitted optional property is being rejected before any of this \
                        endpoint's own rules run. Check that the request-reading keys in \
                        src/test/resources/application.properties still mirror production \
                        (ShRequestReadingConfigLintTest), then check the record's components.

                        Body: %s""".formatted(body))
                .isFalse();
    }

    private static boolean unreadable(Throwable failure) {
        for (Throwable t = failure; t != null && t != t.getCause(); t = t.getCause()) {
            if (t instanceof HttpMessageNotReadableException) {
                return true;
            }
        }
        return false;
    }
}
