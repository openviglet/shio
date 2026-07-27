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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
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

import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.agent.dto.ShAgentBatch.Op;
import com.viglet.shio.api.agent.dto.ShAgentChangeSet;
import com.viglet.shio.api.agent.dto.ShAgentChangeSet.Change;
import com.viglet.shio.api.agent.dto.ShAgentDesiredState;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.persistence.repository.widget.ShWidgetRepository;
import com.viglet.shio.widget.ShSystemWidget;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SH138 — <b>a deletion plan is never silently shortened.</b>
 *
 * <p>SH135 widened {@code prune} to whole subtrees, which made a plan able to list
 * thousands of deletions for the first time. Every other unbounded section on this
 * surface is capped and reports the cut, and for a <em>report</em> that is right; for a
 * <em>plan</em> it is the most dangerous truncation in the system, because a shortened
 * sitemap reads as incomplete while a shortened list of deletions reads as a complete
 * review of what is about to be destroyed. So the only two options are to exempt the
 * rows from the budget or to refuse the run — and this class pins both, plus the
 * structural guarantee that the change-set type cannot express a cut at all.</p>
 *
 * <p>The ceilings are lowered to single digits here so the behaviour is provable
 * without seeding thousands of posts. What is under test is the rule, not the number.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = {
        "shio.agent.batch.max-ops=4",
        "shio.agent.batch.max-document-ops=8"
})
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentDeletionPlanTest {

    @Autowired
    private ShAgentBatchService shAgentBatchService;
    @Autowired
    private ShAgentDesiredStateService shAgentDesiredStateService;
    @Autowired
    private ShWidgetRepository shWidgetRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;
    private String postTypeName;

    @BeforeAll
    void setup() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = new ShSite();
            siteName = "del-site-" + UUID.randomUUID();
            site.setName(siteName);
            site.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            site.setOwner("admin");
            entityManager.persist(site);

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            root.setOwner("admin");
            entityManager.persist(root);

            ShWidget widget = entityManager.find(ShWidget.class,
                    shWidgetRepository.findFirstByName(ShSystemWidget.TEXT).getId());

            ShPostType postType = new ShPostType();
            postTypeName = "DelArticle" + UUID.randomUUID().toString().substring(0, 8);
            postType.setName(postTypeName);
            postType.setTitle("Deletion Article");
            postType.setSystem((byte) 0);
            postType.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            postType.setOwner("admin");
            entityManager.persist(postType);

            ShPostTypeAttr attr = new ShPostTypeAttr();
            attr.setName("headline");
            attr.setLabel("Headline");
            attr.setOrdinal(1);
            attr.setIsTitle((byte) 1);
            attr.setRequired((byte) 1);
            attr.setShWidget(widget);
            attr.setShPostType(postType);
            entityManager.persist(attr);
        });
    }

    /**
     * The invariant made structural: one row per op, so the type cannot hold a short
     * list. This is the guard that survives a future {@code limit} parameter — a
     * behavioural test only proves the paths that exist today (§VIII.10).
     */
    @Test
    @DisplayName("a change set cannot be constructed with fewer rows than ops")
    void aChangeSetCannotBeTruncated() {
        List<Change> onlyOne = List.of(
                new Change(ShAgentBatchService.POST_DELETE, "post:x/a", Change.DELETE, null, null));

        assertThatThrownBy(() -> new ShAgentChangeSet(true, 3, 1, onlyOne))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("never")
                .hasMessageContaining("truncated");

        assertThatThrownBy(() -> new ShAgentChangeSet(true, 1, 0, null))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * The case that was impossible before SH138: a document whose prune compiles to more
     * ops than a hand-written batch may hold. It used to fail with "split the work into
     * batches of at most N ops" — advice the caller cannot follow, because the ops were
     * compiled here and a converge cannot be hand-split.
     */
    @Test
    @DisplayName("a compiled document may exceed the hand-written batch ceiling")
    void aDocumentIsNotHeldToTheBatchCeiling() {
        String base = "/dl-" + UUID.randomUUID().toString().substring(0, 8);
        seed(base, 5);

        // 5 deletes + the declared folder = 6 ops: over max-ops (4), under max-document-ops (8).
        ShAgentChangeSet plan = plan(base, true);

        assertThat(plan.ops()).isGreaterThan(4);
        assertThat(deletions(plan)).hasSize(5);
    }

    /** Every deletion is listed. The count of rows is the count of ops, with nothing dropped. */
    @Test
    @DisplayName("the plan lists every deletion, one row per op")
    void thePlanIsComplete() {
        String base = "/dc-" + UUID.randomUUID().toString().substring(0, 8);
        seed(base, 5);

        ShAgentChangeSet plan = plan(base, true);

        assertThat(plan.results()).hasSize(plan.ops());
        for (int i = 1; i <= 5; i++) {
            assertThat(deletions(plan)).contains("post:" + siteName + base + "/doomed-" + i);
        }
    }

    /**
     * Past its own ceiling the run is <b>refused</b>, and the refusal teaches the thing
     * that is actually actionable: how many of the ops are deletions, and that the way
     * out is a narrower scope — never "split into batches", which does not exist here.
     */
    @Test
    @DisplayName("a document over the document ceiling is refused, not shortened")
    void anOversizedDocumentIsRefused() {
        String base = "/dx-" + UUID.randomUUID().toString().substring(0, 8);
        seed(base, 9);

        assertThatThrownBy(() -> plan(base, true))
                .isInstanceOf(ShAgentBatchService.DocumentTooLargeException.class)
                .satisfies(thrown -> {
                    var problem = ((ShAgentBatchService.DocumentTooLargeException) thrown)
                            .toProblemDetail();
                    assertThat(problem.getStatus()).isEqualTo(400);
                    assertThat(problem.getDetail()).contains("9").contains("deletions");
                    String fix = String.valueOf(problem.getProperties().get("fix"));
                    assertThat(fix).contains("fewer folders").contains("max-document-ops");
                    // The old advice was the unusable one, and it must not come back.
                    assertThat(fix).doesNotContain("batches of at most");
                });
    }

    /** The two ceilings stay distinct: a hand-written batch is still bounded by its own. */
    @Test
    @DisplayName("a hand-written batch still gets the batch ceiling and its own error")
    void aRequestBatchKeepsItsOwnCeiling() {
        String base = "/db-" + UUID.randomUUID().toString().substring(0, 8);
        List<Op> ops = new ArrayList<>();
        ops.add(folder(base));
        for (int i = 1; i <= 5; i++) {
            ops.add(post(base + "/typed-" + i, base, "Typed " + i));
        }

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).execute(status ->
                shAgentBatchService.run(new ShAgentBatch(true, ops), false, "tester")))
                .isInstanceOf(ShAgentBatchService.BatchTooLargeException.class);
    }

    // --- helpers ---

    /** A folder holding {@code count} posts the document will not declare. */
    private void seed(String base, int count) {
        List<Op> ops = new ArrayList<>();
        ops.add(folder(base));
        for (int i = 1; i <= count; i++) {
            ops.add(post(base + "/doomed-" + i, base, "Doomed " + i));
        }
        // Seeded through the batch surface so the content is what an agent could have
        // written — but one op at a time, since max-ops is 4 in this class.
        for (Op op : ops) {
            new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    shAgentBatchService.run(new ShAgentBatch(false, List.of(op)), false, "tester"));
        }
    }

    private ShAgentChangeSet plan(String base, boolean prune) {
        ShAgentDesiredState desired = new ShAgentDesiredState(siteName,
                List.of(new ShAgentDesiredState.Folder(base)), List.of(), prune);
        return new TransactionTemplate(transactionManager).execute(status ->
                shAgentDesiredStateService.plan(desired, false, "tester"));
    }

    private static List<String> deletions(ShAgentChangeSet plan) {
        return plan.results().stream()
                .filter(change -> Change.DELETE.equals(change.action()))
                .map(Change::address)
                .toList();
    }

    private JsonNode data(String headline) {
        return objectMapper.valueToTree(Map.of("headline", headline));
    }

    private Op folder(String path) {
        return new Op(ShAgentBatchService.FOLDER_UPSERT, "folder:" + siteName + path,
                null, null, null);
    }

    private Op post(String url, String folder, String headline) {
        return new Op(ShAgentBatchService.POST_UPSERT, "post:" + siteName + url,
                postTypeName, folder, data(headline));
    }
}
