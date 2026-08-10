/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.type;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * The 404 that said nothing, on the surface a typo is likeliest (SH621).
 *
 * <h2>The finding, and why this endpoint</h2>
 *
 * <p>SH499's execution instrument issued the requests {@code docs-shio} publishes and this was its
 * first: {@code GET /api/v2/post-type/Article} on a bare instance answered <b>404 with
 * {@code Content-Length: 0}</b>. Not a thin problem document — no body at all. The same miss one
 * surface over answered a full RFC 9457 document with {@code fix} and {@code allowed}, so P6 was
 * implemented on {@code /api/v2/agent/**} and nowhere else.</p>
 *
 * <p>That is the wrong half to have covered. Post-types are <b>the one name-keyed surface</b> —
 * SH117 retired the id-keyed twin — so a name is the only thing a caller can get wrong here, and a
 * typo is the likeliest way to arrive at this 404 at all.</p>
 *
 * <h2>What the zero bytes cost, which is not cosmetic</h2>
 *
 * <p>A renamed type and a type that never existed were the same answer. The caller's next move —
 * retry, create, or ask a human — depends on which, so an agent that cannot tell them apart
 * guesses; and guessing on a model change is how a second post-type gets created beside the one
 * that was meant.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShPostTypeUnknownNameTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    private MockMvc mockMvc;
    private String declared;

    @BeforeAll
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        declared = "Sh621Article" + java.util.UUID.randomUUID().toString().substring(0, 8);

        // Its own types rather than whatever the shared context happens to hold: `allowed` and
        // `didYouMean` are assertions about a POPULATION, so borrowing another test's would make
        // them statements about suite order (SH315's rule, and the first draft of this file was
        // exactly that mistake — it assumed `Text` was seeded and every assertion read 404).
        new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> {
                    com.viglet.shio.persistence.model.post.type.ShPostType type =
                            new com.viglet.shio.persistence.model.post.type.ShPostType();
                    type.setName(declared);
                    type.setTitle(declared);
                    type.setSystem((byte) 0);
                    type.setDate(java.time.Instant.parse("2026-01-01T00:00:00Z"));
                    type.setOwner("admin");
                    entityManager.persist(type);
                });
    }

    private MvcResult miss(String name) throws Exception {
        return mockMvc.perform(get("/api/v2/post-type/{name}", name)).andReturn();
    }

    @Test
    @DisplayName("SH621: an unknown name answers 404 with a body, where it used to send zero bytes")
    void theRefusalHasABody() throws Exception {
        MvcResult result = miss("NoSuchTypeSh621");

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        String body = result.getResponse().getContentAsString();
        assertThat(body)
                .as("""
                        the defect's own signature was Content-Length: 0 — asserted on the bytes \\
                        rather than on a parsed field, because "no body at all" is exactly what a \\
                        test reading a DTO would have missed (SH621)""")
                .isNotBlank();
        assertThat(body).contains("unknown-post-type");
    }

    @Test
    @DisplayName("SH621: it teaches — what is allowed, and what to do next")
    void theRefusalTeaches() throws Exception {
        String body = miss("NoSuchTypeSh621").getResponse().getContentAsString();

        // P6's shape, on the surface it had been implemented everywhere except.
        assertThat(body)
                .as("`allowed` is what turns 'no' into 'here is what there is' (P6)")
                .contains("allowed");
        assertThat(body)
                .as("""
                        and `fix` names both next moves, because a miss here is a spelling, a \\
                        rename, or a type nobody created — and those need different ones""")
                .contains("PUT /api/v2/post-type/")
                .contains("GET /api/v2/post-type");
    }

    @Test
    @DisplayName("SH621: a near miss suggests the name it is near, which is why the mechanism exists")
    void aNearMissIsSuggested() throws Exception {
        // One character short of a name this test created, so the near miss is a fact about its own
        // fixture. `didYouMean` was a solved mechanism on this codebase and this is the endpoint it
        // was invented for — a name-keyed surface where a typo is the likeliest arrival.
        String body = miss(declared.substring(0, declared.length() - 1)).getResponse()
                .getContentAsString();

        assertThat(body)
                .as("a renamed type and a type that never existed stop being the same answer (SH621)")
                .contains("didYouMean");
    }

    @Test
    @DisplayName("SH621: a name that exists is unaffected")
    void aRealNameStillAnswers() throws Exception {
        // The half that must not break: this endpoint is what every `shio push --check` and every
        // console editor load fetches, so a refusal path that changed the hit path would be worse
        // than the defect it fixes.
        assertThat(mockMvc.perform(get("/api/v2/post-type/{name}", declared)).andReturn()
                .getResponse().getStatus())
                .isEqualTo(200);
    }
}
