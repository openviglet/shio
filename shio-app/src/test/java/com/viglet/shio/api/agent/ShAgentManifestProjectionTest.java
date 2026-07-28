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
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * SH174 — the manifest can be asked for in pieces, and it stops ignoring {@code format=}.
 *
 * <h2>The number this exists to move</h2>
 *
 * <p>The manifest is the first read of every session and the most expensive document on the
 * surface: 2169 estimated tokens, against 410 for a terse context pack. SH116's ETag already
 * makes the <em>repeat</em> read free, so what is left is the first call — which no {@code 304}
 * can help — and the very common case of an agent re-reading the whole thing to confirm one
 * boolean in {@code features}. Both are answered by letting the caller say what it wants.</p>
 *
 * <p>So the assertions here are about cost, not just shape: a projected read must actually
 * measure smaller on {@code X-Shio-Est-Tokens}, the header the SH124 budget tests read. A
 * projection that returned the same bytes with different keys would pass a structural test and
 * deliver nothing.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentManifestProjectionTest {

    private static final String MANIFEST = "/api/v2/agent/manifest";
    private static final String EST_TOKENS = "X-Shio-Est-Tokens";

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;
    private Principal principal;

    @BeforeAll
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(new ShEstTokensHeaderFilter())
                .build();
        principal = Mockito.mock(Principal.class);
        Mockito.when(principal.getName()).thenReturn("admin");
    }

    @Test
    @DisplayName("SH174: a capability check costs a fraction of the full manifest")
    void capabilityCheckDropsTheEndpointIndex() throws Exception {
        MvcResult full = manifest("");
        MvcResult check = manifest("?include=features,limits");

        String body = check.getResponse().getContentAsString();
        assertThat(body)
                .as("the identity scalars survive every projection — a response a client cannot "
                        + "branch on turns a saving into a second call")
                .contains("\"protocol\"", "\"product\"", "\"apiBase\"")
                .contains("\"features\"", "\"limits\"");
        assertThat(body)
                .as("the endpoint index is the whole weight of this document and was not asked for")
                .doesNotContain("\"endpoints\"", "\"startHere\"");

        assertThat(tokens(check))
                .as("SH174 is a token claim; if the projection does not measure smaller it did nothing")
                .isLessThan(tokens(full) / 2);
    }

    @Test
    @DisplayName("SH174: format=terse compacts the whole document")
    void terseIsCheaperThanJson() throws Exception {
        MvcResult json = manifest("");
        MvcResult terse = manifest("?format=terse");

        String body = terse.getResponse().getContentAsString();
        assertThat(body).startsWith("shio-manifest ");
        assertThat(body)
                .as("both feature lines are always present — an agent must be able to tell "
                        + "'reported false' from 'not reported', which is the guess the manifest closes")
                .contains("\nfeatures ")
                .contains("\nfeatures-off ");
        assertThat(body).contains("\nendpoint GET /agent/context");

        assertThat(tokens(terse)).isLessThan(tokens(json));
    }

    /**
     * The failure SH174 names second, and the reason it is worth a test: before this, the
     * endpoint accepted {@code ?format=terse} and returned full JSON without comment, while its
     * immediate neighbour taught a 400 for the identical mistake.
     */
    @Test
    @DisplayName("SH174: an unknown format teaches, as /agent/context always has")
    void unknownFormatIsATeaching400() throws Exception {
        MvcResult result = manifest("?format=agents-md");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("unknown-format");
        assertThat(body)
                .as("the allowed set is this document's, not the context pack's")
                .contains("terse")
                .contains("include");
    }

    @Test
    @DisplayName("SH174: an unknown include section teaches the vocabulary")
    void unknownSectionIsATeaching400() throws Exception {
        MvcResult result = manifest("?include=featurs");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("unknown-manifest-section");
        assertThat(body)
                .as("SH80: a typo should cost one call, so the nearest match is named")
                .contains("features");
    }

    /**
     * SH116's rule, applied to the parameters SH174 adds. A tag names a representation — two
     * different projections of one unchanged manifest are two representations, and handing them
     * the same tag would serve a client its cached two-line answer when it asked for everything.
     */
    @Test
    @DisplayName("SH174: the entity tag distinguishes projections and encodings")
    void etagCoversIncludeAndFormat() throws Exception {
        String full = etag(manifest(""));
        String projected = etag(manifest("?include=features"));
        String terse = etag(manifest("?format=terse"));

        assertThat(full).isNotEqualTo(projected).isNotEqualTo(terse);
        assertThat(projected).isNotEqualTo(terse);

        // And the tag a caller actually holds still works.
        MvcResult revalidated = mockMvc.perform(get(MANIFEST + "?include=features")
                .principal(principal)
                .header(HttpHeaders.IF_NONE_MATCH, projected))
                .andReturn();
        assertThat(revalidated.getResponse().getStatus()).isEqualTo(304);
        assertThat(tokens(revalidated)).isZero();
    }

    /**
     * Declaration order, not request order. A caller alternating between two equivalent
     * spellings of the same projection would otherwise revalidate forever and never see a 304 —
     * a caching bug that looks exactly like a working cache from the server's side.
     */
    @Test
    @DisplayName("SH174: section order in the query does not change the tag")
    void etagIsOrderIndependent() throws Exception {
        assertThat(etag(manifest("?include=features,limits")))
                .isEqualTo(etag(manifest("?include=limits,features")));
    }

    private MvcResult manifest(String query) throws Exception {
        return mockMvc.perform(get(MANIFEST + query).principal(principal)).andReturn();
    }

    private static String etag(MvcResult result) {
        return result.getResponse().getHeader(HttpHeaders.ETAG);
    }

    private static int tokens(MvcResult result) {
        String header = result.getResponse().getHeader(EST_TOKENS);
        return header == null ? -1 : Integer.parseInt(header);
    }
}
