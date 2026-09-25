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
import java.util.List;
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
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.CreateRequest;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.history.ShHistory;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.site.ShSite;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.ObjectMapper;

/**
 * SH1024 — {@code agent}, the word that finds what an agent run wrote.
 *
 * <p>The console's author picker offers "an agent" beside the people, and a console-only filter is
 * what P1 forbids, so the option is a vocabulary word first. It reads the audit trail's
 * {@code agent_session_id} (SH106) with the spelling the activity feed's attribution filter uses,
 * which is the part worth asserting: a {@code console:} session is a curator publishing their own
 * work (SH996), and a filter that read "has a session" would file it under the machines.</p>
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentWrittenQueryTest {

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
        siteName = "sh1024agent-" + UUID.randomUUID().toString().substring(0, 8);
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
            type.setName("Sh1024Agent" + UUID.randomUUID().toString().substring(0, 6));
            type.setTitle("SH1024 page");
            type.setSystem((byte) 0);
            type.setDate(EPOCH);
            type.setOwner("admin");
            entityManager.persist(type);
            ids[1] = type.getId();
        });
        // A page an agent run wrote, one a curator published from the console, and one neither did.
        wrote(create(ids, "/by-agent"), "run-" + UUID.randomUUID());
        wrote(create(ids, "/by-console"), "console:" + UUID.randomUUID());
        create(ids, "/untouched");
    }

    @Test
    @DisplayName("SH1024: agent=true finds what an agent run wrote, and a console session is not one")
    void agentNarrowsBothWays() {
        assertThat(addresses(ShContentQuery.ofSite(siteName).withAgent(true)))
                .as("SH1024: a console session is a curator publishing their own work (SH996); a "
                        + "filter reading 'has a session' would file it under the machines")
                .containsExactly("post:" + siteName + "/by-agent");
        assertThat(addresses(ShContentQuery.ofSite(siteName).withAgent(false)))
                .containsExactlyInAnyOrder("post:" + siteName + "/by-console", "post:" + siteName + "/untouched");
        assertThat(addresses(ShContentQuery.ofSite(siteName)))
                .as("SH1024: absent is both, so no existing question changed its answer")
                .hasSize(3);
    }

    @Test
    @DisplayName("SH1024: the word is on MCP too, and a value that is neither yes nor no is refused")
    void theAgentAsksTheSameQuestion() {
        String listed = findTool.call(objectMapper.valueToTree(Map.of("site", siteName, "agent", "true")));
        assertThat(listed)
                .as("SH1024: the console's author picker has the option, so the agent's own tool must (P1)")
                .contains("/by-agent").doesNotContain("/by-console").doesNotContain("/untouched");

        assertThatThrownBy(() -> ShContentQuery.of(Map.of("agent", "maybe"),
                ShContentQuery.FILTERS, List.of(), ""))
                .isInstanceOf(ShInvalidQueryValueException.class);
    }

    private List<String> addresses(ShContentQuery query) {
        ShAgentFound found = shAgentReadService.find(query, 200, false, null, false, null, null);
        return found.results().stream().map(ShAgentFound.Row::address).toList();
    }

    private String create(String[] ids, String furl) {
        return write.create(new CreateRequest(ids[0], ids[1], objectMapper.readTree("{\"body\":\"x\"}"),
                ShPostState.DRAFT, "admin", "en", null, furl)).getId();
    }

    /** The audit row a write through a session leaves, which is all the word reads. */
    private void wrote(String postId, String session) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShHistory row = new ShHistory();
            row.setShObject(postId);
            row.setAgentSessionId(session);
            row.setAction("UPDATE");
            row.setOwner("admin");
            row.setDate(EPOCH);
            entityManager.persist(row);
        });
    }
}
