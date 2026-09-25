/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.ShAgentReadService;
import com.viglet.shio.api.agent.dto.ShAgentFound;
import com.viglet.shio.api.agent.mcp.tools.ShFindTool;
import com.viglet.shio.api.form.ShFormSubmitter;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.CreateRequest;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.site.ShSite;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.ObjectMapper;

/**
 * SH1066 — {@code submission}, the word that finds what arrived through a site's form.
 *
 * <p>A submission is a post (SH633), so it was already findable by every word a post is found by,
 * and indistinguishable from a curator's draft by all of them. What tells it apart is the owner the
 * form endpoint records ({@link ShFormSubmitter}), so that is what the word reads, in both
 * directions and on every surface that parses the vocabulary.</p>
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShSubmissionQueryTest {

    private static final Instant EPOCH = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private ShAgentReadService shAgentReadService;
    @Autowired
    private ShFindTool findTool;
    @Autowired
    private ShPostUnifiedWriteService write;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;

    @BeforeAll
    void seed() {
        siteName = "sh1066sub-" + UUID.randomUUID().toString().substring(0, 8);
        String[] ids = new String[2];
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = new ShSite();
            site.setName(siteName);
            site.setDate(EPOCH);
            site.setOwner("admin");
            entityManager.persist(site);

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(EPOCH);
            root.setOwner("admin");
            entityManager.persist(root);
            ids[0] = root.getId();

            ShPostType type = new ShPostType();
            type.setName("Sh1066Sub" + UUID.randomUUID().toString().substring(0, 6));
            type.setTitle("SH1066 lead");
            type.setSystem((byte) 0);
            type.setDate(EPOCH);
            type.setOwner("admin");
            entityManager.persist(type);
            ids[1] = type.getId();
        });
        // A curator's draft, and two submissions: one through a FORM token, one without.
        create(ids, "/about", "admin");
        create(ids, "/lead-1", ShFormSubmitter.TOKEN_PREFIX + "tok-1");
        create(ids, "/lead-2", ShFormSubmitter.UNTOKENED);
    }

    @Test
    @DisplayName("SH1066: submission=true finds what a form wrote, and false everything else")
    void submissionNarrowsBothWays() {
        assertThat(addresses(ShContentQuery.ofSite(siteName).withSubmission(true)))
                .as("SH1066: a submission is a draft like any other, and without this word a curator "
                        + "has to guess the folder a developer configured to find one")
                .containsExactlyInAnyOrder("post:" + siteName + "/lead-1", "post:" + siteName + "/lead-2");
        assertThat(addresses(ShContentQuery.ofSite(siteName).withSubmission(false)))
                .containsExactly("post:" + siteName + "/about");
        assertThat(addresses(ShContentQuery.ofSite(siteName)))
                .as("SH1066: absent is both, so no existing question changed its answer")
                .hasSize(3);
    }

    @Test
    @DisplayName("SH1066: the word is on MCP too, and a value that is neither yes nor no is refused")
    void theAgentAsksTheSameQuestion() {
        String listed = findTool.call(objectMapper.valueToTree(
                Map.of("site", siteName, "submission", "true")));
        assertThat(listed)
                .as("SH1066: REST answers submission and the agent's own tool dropped it (P1)")
                .contains("/lead-1").contains("/lead-2").doesNotContain("/about");

        assertThatThrownBy(() -> ShContentQuery.of(Map.of("submission", "maybe"),
                ShContentQuery.FILTERS, java.util.List.of(), ""))
                .as("SH1066: guessing a direction would answer with the wrong half of a site")
                .isInstanceOf(ShInvalidQueryValueException.class);
    }

    private java.util.List<String> addresses(ShContentQuery query) {
        ShAgentFound found = shAgentReadService.find(query, 200, false, null, false, null, null);
        return found.results().stream().map(ShAgentFound.Row::address).toList();
    }

    private void create(String[] ids, String furl, String owner) {
        write.create(new CreateRequest(ids[0], ids[1], objectMapper.readTree("{\"body\":\"x\"}"),
                ShPostState.DRAFT, owner, "en", null, furl));
    }
}
