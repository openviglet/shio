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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import com.viglet.shio.api.agent.ShAgentSessionContext;
import com.viglet.shio.api.review.ShReviewService;
import com.viglet.shio.api.review.dto.ShReviewOutcome;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.UpdateRequest;
import com.viglet.shio.persistence.model.review.ShReviewDecision;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.agent.mcp.ShMcpResource;
import com.viglet.shio.persistence.model.agent.ShAgentRun;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.note.ShNote;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.persistence.repository.widget.ShWidgetRepository;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;
import com.viglet.shio.widget.ShSystemWidget;
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
    private ShWidgetRepository widgetRepository;
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
    private ShPostUnifiedWriteService writeService;
    @Autowired
    private ShReviewService reviewService;
    @Autowired
    private ShPostUnifiedRepository unifiedRepository;
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
            // Its own type, with a title and nothing required beside it: borrowing "the first type in
            // the database" picked up another test's required field in the full suite, and the
            // approval that publishes was refused for a field this test never heard of.
            ShWidget text = entityManager.find(ShWidget.class,
                    widgetRepository.findFirstByName(ShSystemWidget.TEXT).getId());
            ShPostType postType = new ShPostType();
            postType.setName("PT-SH1006-" + UUID.randomUUID());
            postType.setTitle("Relay Type");
            postType.setDescription("Post type for the relay test");
            postType.setSystem((byte) 0);
            postType.setDate(then);
            postType.setOwner("admin");
            ShPostTypeAttr title = new ShPostTypeAttr();
            title.setName(ShSystemPostTypeAttr.TITLE);
            title.setLabel("Title");
            title.setDescription("Title field");
            title.setOrdinal(1);
            title.setRequired((byte) 0);
            title.setIsTitle((byte) 1);
            title.setIsSummary((byte) 0);
            title.setShWidget(text);
            title.setShPostType(postType);
            Set<ShPostTypeAttr> attrs = new HashSet<>();
            attrs.add(title);
            postType.setShPostTypeAttrs(attrs);
            entityManager.persist(postType);
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
            entityManager.createQuery("delete from ShHistory h where h.shObject = :id")
                    .setParameter("id", postId).executeUpdate();
            entityManager.createQuery("delete from ShReviewDecision d where d.postId = :id")
                    .setParameter("id", postId).executeUpdate();
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

    /**
     * SH1007 — an answer that arrives with its change attached. The agent drafts the change and
     * replies in the same session, so the reply names that run (SH106's column, which note.add
     * already fills); approving the anchored page in that run, the call the dock's action makes,
     * publishes it and leaves a decision.
     */
    @Test
    @DisplayName("SH1007: a reply names the run that drafted the change, and approving it publishes")
    void aReplyCarriesItsRunAndApprovingItPublishes() {
        String run = "sh1007-run-" + UUID.randomUUID().toString().substring(0, 8);
        ShNote request = noteService.add(postId, null, null, null, "request", "New hero, please", "curator");
        ShNote reply;
        ShAgentSessionContext.set(run);
        try {
            writeService.update(postId, new UpdateRequest(
                    objectMapper.readTree("{\"title\":\"Pricing, simply\"}"), ShPostState.DRAFT, "agent"));
            reply = noteService.add(postId, null, null, request.getId(), "reply",
                    "Drafted a new hero; approve it from the dock.", "agent");
        } finally {
            ShAgentSessionContext.clear();
        }
        assertThat(reply.getAgentSessionId())
                .as("SH1007: the reply does not name the run whose drafts answer it, so the dock has no change to attach")
                .isEqualTo(run);

        ShReviewOutcome outcome = reviewService.decide(run,
                List.of(new ShReviewService.Decision(postId, ShReviewDecision.Kind.APPROVE, null)), "curator");
        assertThat(outcome.changed()).as("SH1007: approving the run's change to the page changed nothing").isEqualTo(1);
        assertThat(unifiedRepository.findByIdAndState(postId, ShPostState.PUBLISHED))
                .as("SH1007: an approved change from the dock is not live")
                .isPresent();
    }

    /**
     * SH1215 — the console's Open notes page lists every open note across sites, and until this an
     * agent had no one read for the same list: it reached notes a page or a site at a time.
     */
    @Test
    @DisplayName("SH1215: shio://notes lists every open note with its kind and page, twin of GET /note/open")
    void everyOpenNoteIsOneRead() {
        ShMcpResource notes = resources.stream().filter(each -> "shio://notes".equals(each.uri()))
                .findFirst().orElseThrow(() -> new AssertionError(
                        "SH1215: shio://notes is gone, so the Open notes surface has no agent equivalent"));
        assertThat(notes.httpTwin())
                .as("SH1215: the resource's twin is the read the console's Open notes page makes")
                .isEqualTo("/api/v2/note/open");

        ShNote comment = noteService.add(postId, null, null, null, "comment", "Hero image is blurry", "curator");
        String listed = notes.read("shio://notes");
        assertThat(listed.lines().filter(line -> line.contains("note=" + comment.getId())))
                .as("SH1215: a comment is open work the console lists, and the agent cannot see it")
                .singleElement().asString()
                .startsWith("- comment ")
                .contains("on post:" + SITE + "/")
                .contains("Hero image is blurry");
        assertThat(requests().read("shio://requests"))
                .as("SH1215: shio://requests stays the dock's channel and does not widen to comments")
                .doesNotContain("note=" + comment.getId());
        assertThat(listed).contains("note.resolve");
    }

    /** SH1019 — a request about a field says which, so the agent knows the reply is its value. */
    @Test
    @DisplayName("SH1019: a field request is listed with its field, and a plain one without")
    void aFieldRequestNamesItsField() {
        ShNote field = noteService.add(postId, "title", null, null, "request", "Suggest a better title", "curator");
        String listed = requests().read("shio://requests");
        assertThat(listed.lines().filter(line -> line.contains("note=" + field.getId())))
                .as("SH1019: the agent cannot tell this request wants a value for the title field")
                .singleElement().asString().contains("field=title");
    }
}
