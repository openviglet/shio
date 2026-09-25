/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.note;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.ShAgentBatchService;
import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.agent.mcp.ShMcpResource;
import com.viglet.shio.persistence.model.agent.ShAgentRun;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.note.ShNote;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.agent.ShAgentRunRepository;
import com.viglet.shio.persistence.repository.note.ShNoteRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.ObjectMapper;

/**
 * SH1006 — a question from the dock reaches the agent, and the agent's answer reaches the thread.
 *
 * <p>The round trip the console depends on, with no browser: a curator's {@code request} note is
 * listed by {@code shio://requests}, the agent answers it with {@code note.add} as a reply through
 * the same batch {@code shio_write} runs, and the thread then holds the reply under the request.
 * And the gate in front of it: the dock offers the chat only while an agent run is recent.</p>
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShNoteRelayTest {

    private static final String SITE = "sh1006-" + UUID.randomUUID().toString().substring(0, 8);

    @Autowired
    private ShNoteService noteService;
    @Autowired
    private ShNoteRelay relay;
    @Autowired
    private List<ShMcpResource> resources;
    @Autowired
    private ShNoteRepository noteRepository;
    @Autowired
    private ShAgentRunRepository runRepository;
    @Autowired
    private ShAgentBatchService batchService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @PersistenceContext
    private EntityManager entityManager;

    private String postId;
    private String runId;

    @BeforeAll
    void seed() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant then = Instant.parse("2026-01-01T00:00:00Z");
            ShSite site = new ShSite();
            site.setName(SITE);
            site.setDate(then);
            site.setOwner("admin");
            entityManager.persist(site);
            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(then);
            root.setOwner("admin");
            entityManager.persist(root);
            ShPostType postType = entityManager.createQuery("select t from ShPostType t", ShPostType.class)
                    .setMaxResults(1).getResultStream().findFirst().orElse(null);
            ShPostUnified draft = new ShPostUnified();
            draft.setId(SITE + "-pricing");
            draft.setState(ShPostState.DRAFT);
            draft.setShSite(site);
            draft.setShFolder(root);
            draft.setShPostType(postType);
            draft.setTitle("Pricing");
            draft.setFurl("/pricing");
            draft.setLocale("en");
            draft.setOwner("admin");
            draft.setDate(then);
            draft.setData("{}");
            entityManager.persist(draft);
            postId = draft.getId();
        });
    }

    @AfterAll
    void tearDown() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            noteRepository.findByObjectIdOrderByCreatedAtAsc(postId).forEach(noteRepository::delete);
            entityManager.createQuery("delete from ShPostUnified p where p.id = :id")
                    .setParameter("id", postId).executeUpdate();
            if (runId != null) runRepository.deleteById(runId);
        });
    }

    private ShMcpResource requests() {
        return resources.stream().filter(each -> "shio://requests".equals(each.uri())).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("SH1006: a dock request is listed for the agent, and its reply lands on the thread")
    void aRequestIsAnsweredOnItsThread() {
        ShNote request = noteService.add(postId, null, null, null, "request",
                "Can you shorten the pricing intro?", "curator");

        String listed = requests().read("shio://requests");
        assertThat(listed)
                .as("SH1006: the agent cannot see what the curator asked from the dock")
                .contains("note=" + request.getId())
                .contains("Can you shorten the pricing intro?");

        batchService.run(objectMapper.readValue("""
                {"ops":[{"op":"note.add","address":"id:%s",
                 "data":{"kind":"reply","parent":"%s","body":"Done: two sentences now."}}]}
                """.formatted(postId, request.getId()), ShAgentBatch.class), false, "ut-sh1006-agent");

        List<ShNote> thread = noteService.of(postId);
        assertThat(thread).filteredOn(note -> request.getId().equals(note.getParentId()))
                .as("SH1006: the agent's answer did not land under the request, so the dock has nothing to show")
                .extracting(ShNote::getBody).containsExactly("Done: two sentences now.");
    }

    @Test
    @DisplayName("SH1006: the dock offers the chat only while an agent run is recent")
    void theChatWaitsForAnAgent() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShAgentRun run = new ShAgentRun();
            run.setId("sh1006-run-" + UUID.randomUUID());
            run.setStartedAt(Instant.now());
            runRepository.save(run);
            runId = run.getId();
        });
        assertThat(relay.status().agentActive())
                .as("SH1006: a run that just started does not open the chat")
                .isTrue();
        assertThat(ShNoteRelay.WINDOW.toHours()).isEqualTo(relay.status().windowHours());
    }
}
