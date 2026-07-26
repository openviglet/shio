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
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SH134 ({@code post.move}) and SH135 (folder prune) — the two verbs the write
 * vocabulary was missing for <em>changing</em> a site rather than building one.
 *
 * <p>Both gaps were invisible while building: an agent creating twelve pages never
 * needs either. They appear the moment a section is renamed or moved, which is the
 * harder half of the north star and the half a demo does not exercise.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentMoveAndPruneTest {

    @Autowired
    private ShAgentBatchService shAgentBatchService;
    @Autowired
    private ShAgentDesiredStateService shAgentDesiredStateService;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
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
            siteName = "move-site-" + UUID.randomUUID();
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

            ShWidget widget = entityManager
                    .createQuery("select w from ShWidget w where w.name = 'Text'", ShWidget.class)
                    .getResultStream().findFirst().orElseGet(() -> {
                        ShWidget created = new ShWidget();
                        created.setName("Text");
                        created.setDescription("Text");
                        entityManager.persist(created);
                        return created;
                    });

            ShPostType postType = new ShPostType();
            postTypeName = "MoveArticle" + UUID.randomUUID().toString().substring(0, 8);
            postType.setName(postTypeName);
            postType.setTitle("Move Article");
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

    // --- helpers ---

    private ShAgentChangeSet run(boolean dryRun, Op... ops) {
        return new TransactionTemplate(transactionManager).execute(status ->
                shAgentBatchService.run(new ShAgentBatch(dryRun, List.of(ops)), true, "tester"));
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

    private Op move(String from, String to, String folder) {
        return new Op(ShAgentBatchService.POST_MOVE, "post:" + siteName + from,
                null, folder, to == null ? null : "post:" + siteName + to, null);
    }

    private ShPostUnified reload(String id, ShPostState state) {
        return new TransactionTemplate(transactionManager).execute(status ->
                shPostUnifiedRepository.findByIdAndState(id, state).orElse(null));
    }

    private static Change only(ShAgentChangeSet result) {
        return result.results().get(result.results().size() - 1);
    }

    // --- SH134: post.move ---

    @Test
    @DisplayName("a move keeps the post's id — which is the whole point")
    void aMoveKeepsThePostId() {
        String base = "/mv-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, folder(base), post(base + "/old", base, "Old"));
        String before = only(run(true, post(base + "/old", base, "Old"))).id();

        Change change = only(run(false, move(base + "/old", base + "/new", null)));

        assertThat(change.action()).isEqualTo(Change.MOVE);
        assertThat(change.id())
                .as("a move that changed the id would have lost the audit trail and the "
                        + "version history — the exact failure re-addressing an upsert causes")
                .isEqualTo(before);
        assertThat(change.address()).endsWith(base + "/new");
        assertThat(reload(before, ShPostState.DRAFT).getFurl()).isEqualTo(base + "/new");
    }

    @Test
    @DisplayName("a move relocates the folder and re-resolves the owning site")
    void aMoveRelocatesTheFolder() {
        String base = "/mv-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, folder(base + "/from"), folder(base + "/to"),
                post(base + "/page", base + "/from", "Page"));

        Change change = only(run(false, move(base + "/page", null, base + "/to")));

        assertThat(change.action()).isEqualTo(Change.MOVE);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShPostUnified moved = shPostUnifiedRepository
                    .findByIdAndState(change.id(), ShPostState.DRAFT).orElseThrow();
            assertThat(moved.getShFolder().getName()).isEqualTo("to");
            // SH128: the destination is nested, so its own shSite is null. A move that
            // read it directly would blank the post's site and make it unreachable by
            // address.
            assertThat(moved.getShSite()).isNotNull();
            assertThat(moved.getShSite().getName()).isEqualTo(siteName);
        });
    }

    @Test
    @DisplayName("moving twice is moving once — the source is gone, the target is there")
    void aMoveIsIdempotent() {
        String base = "/mv-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, folder(base), post(base + "/a", base, "A"));
        run(false, move(base + "/a", base + "/b", null));

        // The precondition of a move is destroyed by its own success, so a re-run has to
        // recognise the finished state rather than 404 on the missing source — otherwise
        // no document containing a move could be applied twice.
        Change again = only(run(false, move(base + "/a", base + "/b", null)));

        assertThat(again.action()).isEqualTo(Change.NOOP);
        assertThat(again.detail()).isEqualTo("already at this address");
    }

    @Test
    @DisplayName("a move onto an occupied address is refused, not silently merged")
    void aMoveNeverOverwrites() {
        String base = "/mv-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, folder(base), post(base + "/one", base, "One"), post(base + "/two", base, "Two"));

        assertThatThrownBy(() -> run(false, move(base + "/one", base + "/two", null)))
                .isInstanceOf(ShAgentBatchService.BatchOpException.class)
                .hasMessageContaining("A move never overwrites");
    }

    @Test
    @DisplayName("a move with no destination says what a destination looks like")
    void aMoveNeedsADestination() {
        String base = "/mv-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, folder(base), post(base + "/x", base, "X"));

        assertThatThrownBy(() -> run(true, move(base + "/x", null, null)))
                .isInstanceOf(ShAgentBatchService.BatchOpException.class)
                .hasMessageContaining("A move needs a destination");
    }

    @Test
    @DisplayName("a dry-run move writes nothing")
    void aDryRunMoveWritesNothing() {
        String base = "/mv-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, folder(base), post(base + "/keep", base, "Keep"));

        Change planned = only(run(true, move(base + "/keep", base + "/moved", null)));

        assertThat(planned.action()).isEqualTo(Change.MOVE);
        assertThat(reload(planned.id(), ShPostState.DRAFT).getFurl()).isEqualTo(base + "/keep");
    }

    @Test
    @DisplayName("moving a published post says the live URL changes now")
    void movingAPublishedPostSaysSo() {
        String base = "/mv-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, folder(base), post(base + "/live", base, "Live"),
                new Op(ShAgentBatchService.POST_PUBLISH, "post:" + siteName + base + "/live",
                        null, null, null));

        Change change = only(run(false, move(base + "/live", base + "/relocated", null)));

        // A published post's URL changes without a publish step. That is what a move
        // means; P7 says the plan has to say it rather than let the human find out.
        assertThat(change.detail()).isEqualTo("the live URL changes immediately");
        assertThat(reload(change.id(), ShPostState.PUBLISHED).getFurl())
                .as("the draft and the published row must not disagree about the address")
                .isEqualTo(base + "/relocated");
    }

    @Test
    @DisplayName("post.move is in the advertised vocabulary")
    void theVocabularyAdvertisesMove() {
        assertThat(ShAgentBatchService.OPS).contains(ShAgentBatchService.POST_MOVE);
    }

    @Test
    @DisplayName("the batch op record still round-trips through JSON with the new field")
    void theOpRecordStillDeserializes() {
        // The `to` field made Op a six-component record with a five-arg convenience
        // constructor; Jackson must still bind the canonical one.
        Op parsed = objectMapper.readValue(
                "{\"op\":\"post.move\",\"address\":\"post:S/a\",\"to\":\"post:S/b\"}", Op.class);
        assertThat(parsed.op()).isEqualTo("post.move");
        assertThat(parsed.to()).isEqualTo("post:S/b");
        assertThat(parsed.data()).isNull();
    }

    // --- SH135: folder prune ---

    @Test
    @DisplayName("prune removes an undeclared folder, deepest first")
    void pruneRemovesUndeclaredFolders() {
        String base = "/pr-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, folder(base), folder(base + "/stale/deeper"), folder(base + "/kept"));

        ShAgentChangeSet plan = plan(base, List.of(base, base + "/kept"), List.of(), true);

        List<String> deleted = plan.results().stream()
                .filter(change -> Change.DELETE.equals(change.action()))
                .map(Change::address)
                .toList();
        assertThat(deleted)
                .as("the child must precede its parent, or the parent's silent cascade "
                        + "would decide the blast radius instead of the plan")
                .containsExactly("folder:" + siteName + base + "/stale/deeper",
                        "folder:" + siteName + base + "/stale");
    }

    @Test
    @DisplayName("prune never touches a declared folder or an ancestor of one")
    void pruneProtectsDeclaredFoldersAndTheirAncestors() {
        String base = "/pr-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, folder(base + "/keep/inner"));

        ShAgentChangeSet plan = plan(base, List.of(base, base + "/keep/inner"), List.of(), true);

        // /keep is not declared, but deleting it to reach nothing would take the declared
        // /keep/inner with it.
        assertThat(plan.results()).noneMatch(change -> Change.DELETE.equals(change.action()));
    }

    @Test
    @DisplayName("prune protects a folder a declared post lives in, even if unlisted")
    void pruneProtectsAPostsHomeFolder() {
        String base = "/pr-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, folder(base + "/unlisted"), post(base + "/here", base + "/unlisted", "Here"));

        ShAgentChangeSet plan = plan(base, List.of(base),
                List.of(new ShAgentDesiredState.Post(base + "/here", postTypeName,
                        base + "/unlisted", data("Here"), null)), true);

        // Placing a post somewhere is a declaration about that folder too — pruning it
        // would delete the post the same document just declared.
        assertThat(plan.results()).noneMatch(change -> Change.DELETE.equals(change.action()));
    }

    @Test
    @DisplayName("posts go before the folder that holds them")
    void postsArePrunedBeforeTheirFolder() {
        String base = "/pr-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, folder(base + "/gone"), post(base + "/orphan", base + "/gone", "Orphan"));

        ShAgentChangeSet plan = plan(base, List.of(base), List.of(), true);

        List<String> deleted = plan.results().stream()
                .filter(change -> Change.DELETE.equals(change.action()))
                .map(Change::address)
                .toList();
        assertThat(deleted).containsExactly(
                "post:" + siteName + base + "/orphan",
                "folder:" + siteName + base + "/gone");
    }

    @Test
    @DisplayName("a second apply of the same document plans nothing — prune converges")
    void pruneConverges() {
        String base = "/pr-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, folder(base), folder(base + "/shell"));

        apply(document(base, List.of(base), List.of(), true));
        ShAgentChangeSet second = plan(base, List.of(base), List.of(), true);

        // The whole justification for declarative apply: the document keeps describing
        // the site. Before SH135 the empty shell survived and this stayed at 1 forever.
        assertThat(second.changed()).isZero();
    }

    @Test
    @DisplayName("prune still refuses to run without a declared scope")
    void pruneStillNeedsAScope() {
        assertThatThrownBy(() -> plan("/x", List.of(), List.of(), true))
                .isInstanceOf(ShAgentDesiredStateService.UnscopedPruneException.class);
    }

    private ShAgentDesiredState document(String base, List<String> folders,
                                         List<ShAgentDesiredState.Post> posts, boolean prune) {
        return new ShAgentDesiredState(siteName,
                folders.stream().map(ShAgentDesiredState.Folder::new).toList(),
                posts, prune);
    }

    private ShAgentChangeSet plan(String base, List<String> folders,
                                  List<ShAgentDesiredState.Post> posts, boolean prune) {
        return new TransactionTemplate(transactionManager).execute(status ->
                shAgentDesiredStateService.plan(document(base, folders, posts, prune),
                        true, "tester"));
    }

    /**
     * Plan, then apply carrying the plan's {@code confirm} token (SH108) — the two-step a
     * pruning document has to take. This is the verb the gate exists for: a prune's
     * deletions are derived from server state rather than written down, so the plan is the
     * only place "what exactly will this remove?" is answered.
     */
    private void apply(ShAgentDesiredState desired) {
        String confirm = new TransactionTemplate(transactionManager).execute(status ->
                shAgentDesiredStateService.plan(desired, true, "tester")).confirm();
        ShAgentDesiredState confirmed = confirm == null ? desired : new ShAgentDesiredState(
                desired.site(), desired.folders(), desired.posts(), desired.prune(), confirm);
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                shAgentDesiredStateService.apply(confirmed, false, "tester"));
    }
}
