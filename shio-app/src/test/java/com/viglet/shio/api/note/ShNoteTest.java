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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.viglet.shio.api.agent.ShAgentReadService;
import com.viglet.shio.api.agent.ShAgentReadTerse;
import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.agent.dto.ShAgentDoc;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.note.ShNote;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.note.ShNoteRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.ObjectMapper;

/**
 * SH1003 — a curator's correction has somewhere to live.
 *
 * <p>Searching this backend for a comment, mention or annotation class returned nothing. The review
 * queue reviews <em>runs</em>, not documents, so a curator who wanted one paragraph fixed had no
 * channel and an agent had nothing to read: the correction lived in a conversation somewhere else,
 * and the next session started without it.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShNoteTest {

    private static final String RUN = UUID.randomUUID().toString().substring(0, 8);
    private static final String SITE = "sh1003-" + RUN;

    @Autowired
    private ShNoteService shNoteService;
    @Autowired
    private ShNoteRepository shNoteRepository;
    @Autowired
    private ShAgentBatchService shAgentBatchService;
    @Autowired
    private ShAgentReadService shAgentReadService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private final Set<String> posts = new HashSet<>();
    private String postId;

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

            ShPostType postType = entityManager
                    .createQuery("select t from ShPostType t", ShPostType.class)
                    .setMaxResults(1).getResultStream().findFirst().orElse(null);

            ShPostUnified draft = new ShPostUnified();
            draft.setId(SITE + "-about");
            draft.setState(ShPostState.DRAFT);
            draft.setShSite(site);
            draft.setShFolder(root);
            draft.setShPostType(postType);
            draft.setTitle("About");
            draft.setFurl("/about");
            draft.setLocale("en");
            draft.setOwner("admin");
            draft.setDate(then);
            draft.setData("{}");
            entityManager.persist(draft);
            postId = draft.getId();
            posts.add(draft.getId());
        });
    }

    @AfterAll
    void tearDown() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            shNoteRepository.findByObjectIdOrderByCreatedAtAsc(postId)
                    .forEach(shNoteRepository::delete);
            entityManager.createQuery("delete from ShPostUnified p where p.id = :id")
                    .setParameter("id", postId).executeUpdate();
        });
    }

    private void batch(String json) {
        shAgentBatchService.run(objectMapper.readValue(json, ShAgentBatch.class), false, "ut-sh1003");
    }

    @Test
    @DisplayName("SH1003: a correction anchors to the page, the field and the version it is about")
    void aNoteAnchorsWhereItIsAbout() {
        ShNote note = shNoteService.add(postId, "data.subtitle", 7, null, "request",
                "the price table is last quarter's", "ana");

        assertThat(note.getObjectId()).isEqualTo(postId);
        assertThat(note.getFieldPath())
                .as("SH1003: 'this page is wrong', 'this field is wrong' and 'this field was wrong "
                        + "at version 7' are one statement at three resolutions — three tables for "
                        + "them would be three read paths for one question")
                .isEqualTo("data.subtitle");
        assertThat(note.getRevisionNo()).isEqualTo(7);
        assertThat(note.getKind()).isEqualTo("REQUEST");
        assertThat(note.isResolved()).isFalse();
    }

    @Test
    @DisplayName("SH1003: an agent writes and closes one through the op vocabulary, not a new tool")
    void theAgentUsesTheWriteVocabulary() {
        batch("""
                {"ops":[{"op":"note.add","address":"post:%s/about",
                  "data":{"body":"rewritten as asked","kind":"reply"}}]}
                """.formatted(SITE));

        List<ShNote> thread = shNoteService.of(postId);
        assertThat(thread).extracting(ShNote::getBody).contains("rewritten as asked");
        ShNote reply = thread.stream()
                .filter(note -> "rewritten as asked".equals(note.getBody())).findFirst().orElseThrow();
        assertThat(reply.getKind())
                .as("SH1003: a request and a reply are one mechanism a column apart — an assignee "
                        + "column and a request table would be three shapes meaning 'somebody said "
                        + "something about this'")
                .isEqualTo("REPLY");

        batch("""
                {"ops":[{"op":"note.resolve","address":"post:%s/about","data":{"note":"%s"}}]}
                """.formatted(SITE, reply.getId()));

        assertThat(shNoteRepository.findById(reply.getId()).orElseThrow().isResolved())
                .as("SH1003: closing a request is the gesture the channel exists to enable, so an "
                        + "agent can make it")
                .isTrue();
    }

    @Test
    @DisplayName("SH1003: a note with nothing written in it is refused, because the note IS the correction")
    void anEmptyNoteIsRefused() {
        assertThatThrownBy(() -> shNoteService.add(postId, null, null, null, null, "  ", "ana"))
                .isInstanceOf(ShNoteService.EmptyNoteException.class)
                .satisfies(failure -> assertThat(((ShNoteService.EmptyNoteException) failure)
                        .problem().toProblemDetail().getDetail())
                        .contains("the correction itself"));
    }

    @Test
    @DisplayName("SH1003: a kind this vocabulary does not have is refused with the three that exist")
    void anUnknownKindIsRefused() {
        assertThatThrownBy(() -> shNoteService.add(postId, null, null, null, "assignment", "x", "ana"))
                .isInstanceOf(ShNoteService.UnknownKindException.class)
                .satisfies(failure -> assertThat(((ShNoteService.UnknownKindException) failure)
                        .problem().toProblemDetail().getProperties().get("allowed").toString())
                        .as("SH1003: assignment IS a request, which is the point of the kind column")
                        .contains("request"));
    }

    @Test
    @DisplayName("SH1003: a dry run plans the note and writes none, like every other op")
    void aDryRunWritesNothing() {
        int before = shNoteService.of(postId).size();

        batch("""
                {"dryRun":true,"ops":[{"op":"note.add","address":"post:%s/about",
                  "data":{"body":"would say this"}}]}
                """.formatted(SITE));

        assertThat(shNoteService.of(postId))
                .as("SH1003: a plan that silently posted comments would be the one surprise this "
                        + "surface must not have (P7)")
                .hasSize(before);
    }

    @Test
    @DisplayName("SH1003: include=notes attaches what is still being asked, and nothing else")
    void theReadCarriesTheOpenOnes() {
        ShNote open = shNoteService.add(postId, "data.subtitle", null, null, "request",
                "the price table is last quarter's", "ana");
        ShNote done = shNoteService.add(postId, null, null, null, "comment", "looks fine now", "ana");
        shNoteService.resolve(done.getId(), true, "ana");

        List<ShAgentDoc> without = shAgentReadService.read(List.of("post:" + SITE + "/about"),
                List.of(), false, false, null, false, false, false, false);
        assertThat(without.get(0).notes())
                .as("SH1003: opt-in like every other rider on this read — a page nobody has asked "
                        + "anything of reads exactly as it always did (P3)")
                .isNull();

        List<ShAgentDoc> with = shAgentReadService.read(List.of("post:" + SITE + "/about"),
                List.of(), false, false, null, false, false, false, false, true);
        assertThat(with.get(0).notes()).extracting(ShAgentDoc.Note::body)
                .as("SH1003: a read an agent makes before rewriting a page wants what is still "
                        + "being asked of it, not the history of everything anybody ever said")
                .contains("the price table is last quarter's")
                .doesNotContain("looks fine now");
        assertThat(with.get(0).notes()).extracting(ShAgentDoc.Note::id)
                .as("SH1003: with the id, so the run that fixes the page can close the request")
                .contains(open.getId());

        assertThat(ShAgentReadTerse.docs(with))
                .as("SH1003: a note only the JSON carried would be a note this surface does not "
                        + "have — a session reading terse gets the correction here or not at all")
                .contains("note REQUEST")
                .contains("the price table is last quarter's");
    }

    @Test
    @DisplayName("SH1003: the work list is what is still open, and a resolved note leaves it")
    void theWorkListIsWhatIsOpen() {
        ShNote request = shNoteService.add(postId, null, null, null, "request",
                "the hero image has no alt text", "ana");

        assertThat(shNoteService.open("request", null)).extracting(ShNote::getId)
                .contains(request.getId());

        shNoteService.resolve(request.getId(), true, "agent");

        assertThat(shNoteService.open("request", null)).extracting(ShNote::getId)
                .as("SH1003: a work list that kept a finished item is one nobody reads twice")
                .doesNotContain(request.getId());
    }

    @Test
    @DisplayName("SH1061: a reviewed mark is a note, and it is not work")
    void aReviewedMarkIsNotWork() {
        ShNote mark = shNoteService.add(postId, "data.subtitle", null, null, "reviewed",
                "Reviewed against the source: data.subtitle", "ana");

        assertThat(mark.getKind()).isEqualTo("REVIEWED");
        assertThat(shNoteService.open(null, null)).extracting(ShNote::getId)
                .as("SH1061: a work list is what is still being asked of somebody, and 'this field "
                        + "has been checked' is the opposite — counted in, the page translated most "
                        + "carefully would report the most outstanding work")
                .doesNotContain(mark.getId());
        assertThat(shNoteService.openOn(Set.of(postId)).getOrDefault(postId, List.of()))
                .extracting(ShNote::getId)
                .as("SH1061: nor does it ride the read an agent makes before rewriting the page")
                .doesNotContain(mark.getId());

        assertThat(shNoteService.open("reviewed", null)).extracting(ShNote::getId)
                .as("SH1061: asked for by name it is there, which is how the workspace reads the "
                        + "marks back")
                .contains(mark.getId());
        assertThat(shNoteService.of(postId)).extracting(ShNote::getId)
                .as("SH1061: and it is in the page's own thread, where the console reads it")
                .contains(mark.getId());
    }
}
