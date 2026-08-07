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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SH547 — <b>a refusal that teaches the other endpoint's grammar.</b>
 *
 * <h2>The round trip that was spent twice</h2>
 *
 * <p>{@code POST /api/v2/agent/apply} takes a desired-state document. Sent an ops batch, it answered
 * 400 {@code empty-batch} — <i>The batch has no operations</i> — with {@code allowed} listing the
 * eleven op names and {@code example} showing an {@code ops} array. <b>Every teaching field in that
 * document described {@code /agent/batch}</b>, and following it exactly reproduced the same 400.</p>
 *
 * <p>The mechanism was ordinary reuse: {@code apply} compiles the document into ops and hands them to
 * {@link ShAgentBatchService}, which raised the batch's own error when the list came back empty.
 * Correct for {@code batch}, wrong here, and the error had no way to know which door it came through —
 * except that it did: {@code Source} has been threaded into {@code validate} since SH138 gave the
 * size ceiling exactly this treatment.</p>
 *
 * <h2>Why the last test is the one that matters</h2>
 *
 * <p><b>P6 is not that a 4xx carries fields; it is that following them works.</b> A problem document
 * naming the wrong grammar is worse than a bare status, because a bare status sends the reader to the
 * docs and this one sends them in a circle. So the third test takes the {@code example} out of each
 * refusal, posts it back, and requires the answer to be <em>something else</em> — which is the
 * assertion the old message could not have passed, and the cheapest possible statement of the law.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentEmptyRefusalTest {

    private static final String BATCH = "/api/v2/agent/batch";
    private static final String APPLY = "/api/v2/agent/apply";

    /** A batch with nothing in it — the shape {@code /agent/batch} refuses. */
    private static final String EMPTY_BATCH = "{\"ops\":[]}";

    /**
     * A document with nothing in it, and the shape a misdirected ops batch arrives as: {@code ops} is
     * not a key here, so it is dropped and what the handler sees is a site with no folders and no
     * posts. Which is why one refusal has to answer both readers.
     */
    private static final String EMPTY_DOCUMENT =
            "{\"site\":\"sh547-no-such-site\",\"folders\":[],\"posts\":[]}";

    private static final ObjectMapper MAPPER = new ObjectMapper();

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
    @DisplayName("SH547: the batch's empty refusal teaches the batch grammar")
    void batchRefusesWithOpsInEveryField() throws Exception {
        JsonNode problem = refusal(BATCH, EMPTY_BATCH);

        assertThat(problem.path("type").asString()).endsWith("/empty-batch");
        assertThat(problem.path("example").asString())
                .as("an ops array, because that is what this endpoint takes")
                .contains("\"ops\"");
        assertThat(problem.path("allowed").toString())
                .as("and the op vocabulary, which is the closed set this caller is choosing from")
                .contains("post.upsert");
    }

    @Test
    @DisplayName("SH547: the document's empty refusal teaches the document grammar, and the two differ")
    void applyRefusesWithTheDocumentsOwnKeys() throws Exception {
        JsonNode batch = refusal(BATCH, EMPTY_BATCH);
        JsonNode document = refusal(APPLY, EMPTY_DOCUMENT);

        assertThat(document.path("type").asString()).endsWith("/empty-document");
        assertThat(document.path("type").asString())
                .as("one request per endpoint, and the two refusals differ — the whole of SH547, and "
                        + "an assertion that costs two calls and would have caught this before it "
                        + "was written")
                .isNotEqualTo(batch.path("type").asString());
        assertThat(document.path("allowed").toString())
                .as("the document's keys, not the op names")
                .contains("folders", "posts")
                .doesNotContain("post.upsert");
        assertThat(document.path("example").asString())
                .as("and a document-shaped example")
                .contains("\"folders\"", "\"posts\"")
                .doesNotContain("\"ops\"");
        assertThat(document.path("fix").asString())
                .as("naming the endpoint an ops array does belong to, because an empty document is "
                        + "either a caller who declared nothing or one who came to the wrong door, "
                        + "and both are answered in one round trip")
                .contains("/api/v2/agent/batch");
    }

    @Test
    @DisplayName("SH547: following either refusal's own example does not reproduce it")
    void theExampleEachRefusalCarriesIsNotACircle() throws Exception {
        for (String endpoint : new String[] { BATCH, APPLY }) {
            JsonNode first = refusal(endpoint, endpoint.equals(BATCH) ? EMPTY_BATCH : EMPTY_DOCUMENT);
            String example = first.path("example").asString();

            // The status is deliberately not asserted: both examples name `Blog`, which no fixture
            // seeds (the bootstrap import brings `Bootstrap Site`), so the second call refuses on the
            // site and writes nothing. A teaching 400 or 404 about *that* is the correct answer, and
            // pinning one would make this break on an unrelated rule — SH158's reasoning. What is
            // asserted is that the second answer is a *different* problem: following the fix moved
            // the caller on, which is the only thing P6 actually claims.
            JsonNode second = body(endpoint, example);

            assertThat(second.path("type").asString())
                    .as("P6 is that following a 4xx works. %s answered %s and its own example "
                            + "reproduced it, which is worse than a bare status: a bare status sends "
                            + "the reader to the docs and this one sent them in a circle",
                            endpoint, first.path("type").asString())
                    .isNotEqualTo(first.path("type").asString());
        }
    }

    /** The problem document a refused request answers with, parsed. */
    private JsonNode refusal(String endpoint, String request) throws Exception {
        JsonNode problem = body(endpoint, request);
        assertThat(problem.path("type").asString())
                .as("%s was expected to refuse %s and answered a document with no `type`: %s",
                        endpoint, request, problem)
                .isNotEmpty();
        return problem;
    }

    private JsonNode body(String endpoint, String request) throws Exception {
        String raw = mockMvc.perform(post(endpoint)
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(raw).as("%s answered with an empty body for %s", endpoint, request).isNotBlank();
        return MAPPER.readTree(raw);
    }
}
