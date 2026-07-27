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

    /** Runs a batch, doing SH108's two-step when it could delete (as the CLI does). */
    private ShAgentChangeSet run(boolean dryRun, Op... ops) {
        List<Op> list = List.of(ops);
        return new TransactionTemplate(transactionManager).execute(status -> {
            String confirm = !dryRun && ShAgentConfirm.mayDestroy(list)
                    ? shAgentBatchService.run(new ShAgentBatch(true, list), true, "tester").confirm()
                    : null;
            return shAgentBatchService.run(new ShAgentBatch(dryRun, list, confirm), true, "tester");
        });
    }

    /** Applies a document, confirming its deletions first when it prunes (SH108). */
    private ShAgentChangeSet applyDocument(ShAgentDesiredState desired) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            String confirm = desired.prune()
                    ? shAgentDesiredStateService.plan(desired, false, "tester").confirm()
                    : null;
            ShAgentDesiredState confirmed = confirm == null ? desired
                    : new ShAgentDesiredState(desired.site(), desired.folders(), desired.posts(),
                            desired.moves(), desired.prune(), confirm);
            return shAgentDesiredStateService.apply(confirmed, false, "tester");
        });
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

    private Op folderMove(String from, String to) {
        return new Op(ShAgentBatchService.FOLDER_MOVE, "folder:" + siteName + from,
                null, null, to == null ? null : "folder:" + siteName + to, null);
    }

    /** A folder's id by its name chain, or {@code null} — the SH79 walk, from a test. */
    private String folderId(String path) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            ShSite site = entityManager.createQuery(
                            "select s from ShSite s where s.name = :n", ShSite.class)
                    .setParameter("n", siteName).getSingleResult();
            return shAgentBatchService.findFolder(site, path).map(ShFolder::getId).orElse(null);
        });
    }

    /**
     * Post titles in the folder at {@code path} — proof the content came along.
     *
     * <p>Keyed on the folder <b>id</b>, resolved first. Filtering on {@code p.shFolder.name}
     * is the SH22 join hazard: {@code ShFolder} is an {@code ShObject} subclass, so an
     * implicit association join makes Hibernate emit the {@code tenant_id} predicate against
     * the subclass table's alias, which has no such column, and the query dies at prepare
     * time. Written the wrong way first, exactly as documented.</p>
     */
    private List<String> postsInFolderAt(String path) {
        String id = folderId(path);
        return new TransactionTemplate(transactionManager).execute(status ->
                entityManager.createQuery(
                                "select p.title from ShPostUnified p where p.shFolder.id = :id "
                                        + "and p.state = :s order by p.title", String.class)
                        .setParameter("id", id)
                        .setParameter("s", ShPostState.DRAFT)
                        .getResultList());
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

    // --- SH137: folder.move ---

    /**
     * The verb's whole reason for existing: renaming a section used to mean
     * {@code folder.upsert} at the new path plus {@code folder.delete} of the old, and that
     * delete cascades. Here the folder keeps its id and its content.
     */
    @Test
    @DisplayName("folder.move renames a section in place, keeping its id and its posts")
    void folderMoveRenamesInPlace() {
        String base = "/fm-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, folder(base), folder(base + "/old"),
                post(base + "/page-a", base + "/old", "Page A"));
        String idBefore = folderId(base + "/old");

        ShAgentChangeSet result = run(false, folderMove(base + "/old", base + "/new"));

        assertThat(result.results().get(0).action()).isEqualTo(Change.MOVE);
        assertThat(folderId(base + "/new")).isEqualTo(idBefore);
        assertThat(folderId(base + "/old")).isNull();
        // The post is still there — the destructive workaround is what this op replaces.
        assertThat(postsInFolderAt(base + "/new")).containsExactly("Page A");
    }

    /**
     * SH79 keeps folder paths and friendly URLs in separate namespaces, so moving a section
     * does not change a single page's URL. That is the opposite of a path-shaped CMS, and it
     * is why this op needs no SH108 confirm token — nothing goes dark.
     */
    @Test
    @DisplayName("folder.move leaves every URL beneath it untouched")
    void folderMoveKeepsPostUrls() {
        String base = "/fu-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, folder(base), folder(base + "/sec"),
                post(base + "/keeps-its-url", base + "/sec", "Kept"));

        ShAgentChangeSet result = run(false, folderMove(base + "/sec", base + "/moved"));

        assertThat(result.results().get(0).detail()).contains("keep their URLs");
        // Addressed by the URL it always had, not by one derived from the new folder chain.
        assertThat(run(true, post(base + "/keeps-its-url", base + "/moved", "Kept"))
                .results().get(0).action()).isEqualTo(Change.NOOP);
    }

    /** A move is not destructive: it must not demand a confirm token the way a delete does. */
    @Test
    @DisplayName("folder.move needs no confirm token")
    void folderMoveIsNotDestructive() {
        String base = "/fn-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, folder(base), folder(base + "/plain"));

        ShAgentChangeSet plan = new TransactionTemplate(transactionManager).execute(status ->
                shAgentBatchService.run(new ShAgentBatch(true,
                        List.of(folderMove(base + "/plain", base + "/renamed"))), true, "tester"));

        assertThat(plan.confirm()).isNull();
    }

    /**
     * The guard that had to exist before the op could ship. Re-parenting a folder into its
     * own subtree does not fail — it succeeds and produces a cycle, detaching the subtree
     * from the site root and sending the recursive delete round the loop until the stack
     * runs out.
     */
    @Test
    @DisplayName("folder.move refuses a destination inside the folder's own subtree")
    void folderMoveRefusesACycle() {
        String base = "/fc-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, folder(base), folder(base + "/outer"), folder(base + "/outer/inner"));

        assertThatThrownBy(() -> run(false,
                folderMove(base + "/outer", base + "/outer/inner/outer")))
                .isInstanceOf(ShAgentBatchService.BatchOpException.class)
                .hasMessageContaining("would detach the subtree");

        // Still reachable from the root, which is the property a cycle destroys.
        assertThat(folderId(base + "/outer")).isNotNull();
    }

    /** And a plan reports it too, or the plan lied (SH82's rule). */
    @Test
    @DisplayName("a dry run reports the refusals the real run would make")
    void folderMovePlanReportsRefusals() {
        String base = "/fp-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, folder(base), folder(base + "/a"), folder(base + "/a/b"));

        assertThatThrownBy(() -> run(true, folderMove(base + "/a", base + "/a/b/a")))
                .isInstanceOf(ShAgentBatchService.BatchOpException.class)
                .hasMessageContaining("would detach the subtree");
    }

    @Test
    @DisplayName("folder.move refuses an occupied name chain, and says why for a folder")
    void folderMoveRefusesAnOccupiedTarget() {
        String base = "/fo-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, folder(base), folder(base + "/one"), folder(base + "/two"));

        assertThatThrownBy(() -> run(false, folderMove(base + "/one", base + "/two")))
                .isInstanceOf(ShAgentBatchService.BatchOpException.class)
                // Kind-aware wording: a folder collision is not a delivery ambiguity, and an
                // error describing the wrong consequence teaches the caller something false.
                .hasMessageContaining("two folders")
                .hasMessageNotContaining("delivery layer");
    }

    /** Idempotence, as {@code post.move}: a document holding a move survives a second apply. */
    @Test
    @DisplayName("re-running a folder.move is a noop, not a 404")
    void folderMoveIsIdempotent() {
        String base = "/fi-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, folder(base), folder(base + "/before"));
        run(false, folderMove(base + "/before", base + "/after"));

        ShAgentChangeSet again = run(false, folderMove(base + "/before", base + "/after"));

        assertThat(again.results().get(0).action()).isEqualTo(Change.NOOP);
        assertThat(again.changed()).isZero();
    }

    /** A site root carries the site and is what every address resolves from (SH128). */
    @Test
    @DisplayName("folder.move refuses to move a site root")
    void folderMoveRefusesARoot() {
        String base = "/fr-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, folder(base));

        // The root is addressed `folder:<site>/`, and its own name is the destination's parent.
        assertThatThrownBy(() -> run(false, folderMove("", base + "/root-copy")))
                .isInstanceOf(RuntimeException.class);
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

        applyDocument(document(base, List.of(base), List.of(), true));
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

    // --- SH150: a document declares moves ---

    @Test
    @DisplayName("a document's move keeps the post's id, and its upsert lands at the new url")
    void aDocumentMoveKeepsThePostId() {
        String base = "/doc-mv-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, folder(base), post(base + "/old", base, "Old"));
        String before = only(run(true, post(base + "/old", base, "Old"))).id();

        applyDocument(withMoves(
                document(base, List.of(base), List.of(declared(base + "/new", base, "Renamed")), false),
                new ShAgentDesiredState.Move(base + "/old", base + "/new", null)));

        // The move runs before the upsert, so the upsert writes the new title onto the post
        // that was already there instead of creating a second one beside it.
        ShPostUnified moved = reload(before, ShPostState.DRAFT);
        assertThat(moved).as("the post kept its id across the rename").isNotNull();
        assertThat(moved.getFurl()).isEqualTo(base + "/new");
        assertThat(moved.getTitle()).isEqualTo("Renamed");
        assertThat(postsInFolderAt(base)).containsExactly("Renamed");
    }

    @Test
    @DisplayName("a pruning document does not offer to delete the post it is moving")
    void aPruningDocumentProtectsAMovesSource() {
        String base = "/doc-mvp-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, folder(base), post(base + "/old", base, "Old"));

        ShAgentChangeSet planned = new TransactionTemplate(transactionManager).execute(status ->
                shAgentDesiredStateService.plan(withMoves(
                        document(base, List.of(base),
                                List.of(declared(base + "/new", base, "Old")), true),
                        new ShAgentDesiredState.Move(base + "/old", base + "/new", null)),
                        true, "tester"));

        // The sweep reads the database before any op has run, so the post is still at its old
        // URL and nothing in `posts` names that URL. Unprotected it would be swept — and the
        // real damage is not the delete (the move runs first, so it would find nothing) but the
        // *plan*: SH108's confirm digest is taken over this list, so a rename would ask the
        // operator to confirm destroying the post it is preserving.
        assertThat(planned.results())
                .noneMatch(change -> Change.DELETE.equals(change.action()));
    }

    @Test
    @DisplayName("re-applying a document whose move already happened changes nothing")
    void aDocumentMoveIsIdempotent() {
        String base = "/doc-mvi-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, folder(base), post(base + "/old", base, "Old"));
        ShAgentDesiredState desired = withMoves(
                document(base, List.of(base), List.of(declared(base + "/new", base, "Old")), false),
                new ShAgentDesiredState.Move(base + "/old", base + "/new", null));

        applyDocument(desired);
        ShAgentChangeSet second = new TransactionTemplate(transactionManager).execute(status ->
                shAgentDesiredStateService.plan(desired, true, "tester"));

        // A move whose source is gone is a noop, not an error — which is what lets a document
        // carrying an intent stay re-runnable (SH82). Without this the second apply of a pushed
        // rename would fail on "no post at /old".
        assertThat(second.changed()).isZero();
    }

    @Test
    @DisplayName("a document's move relocates without renaming")
    void aDocumentMoveRelocatesOnly() {
        String base = "/doc-mvr-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, folder(base + "/from"), folder(base + "/to"),
                post(base + "/page", base + "/from", "Page"));

        applyDocument(withMoves(
                document(base, List.of(base), List.of(), false),
                new ShAgentDesiredState.Move(base + "/page", null, base + "/to")));

        // The axis that exists because `post.upsert` deliberately does not relocate an existing
        // post: without it, changing a file's `folder:` was a write that reported success and
        // moved nothing at all.
        assertThat(postsInFolderAt(base + "/to")).containsExactly("Page");
        assertThat(postsInFolderAt(base + "/from")).isEmpty();
    }

    @Test
    @DisplayName("a move with no destination is refused, not compiled to a no-op")
    void aDocumentMoveNeedsSomewhereToGo() {
        String base = "/doc-mvn-" + UUID.randomUUID().toString().substring(0, 8);

        assertThatThrownBy(() -> plan(base, List.of(base), List.of(), false,
                new ShAgentDesiredState.Move(base + "/page", null, null)))
                .isInstanceOf(ShAgentDesiredStateService.EmptyMoveException.class)
                // A no-op would report `changed:0`, which reads as "already in the right place".
                .hasMessageContaining("silent no-op");
        assertThatThrownBy(() -> plan(base, List.of(base), List.of(), false,
                new ShAgentDesiredState.Move(null, base + "/page", null)))
                .isInstanceOf(ShAgentDesiredStateService.MissingMoveFromException.class);
    }

    private ShAgentDesiredState.Post declared(String url, String folder, String headline) {
        return new ShAgentDesiredState.Post(url, postTypeName, folder, data(headline), null);
    }

    private static ShAgentDesiredState withMoves(ShAgentDesiredState desired,
                                                 ShAgentDesiredState.Move... moves) {
        return new ShAgentDesiredState(desired.site(), desired.folders(), desired.posts(),
                List.of(moves), desired.prune(), desired.confirm());
    }

    private ShAgentDesiredState document(String base, List<String> folders,
                                         List<ShAgentDesiredState.Post> posts, boolean prune) {
        return new ShAgentDesiredState(siteName,
                folders.stream().map(ShAgentDesiredState.Folder::new).toList(),
                posts, prune);
    }

    private ShAgentChangeSet plan(String base, List<String> folders,
                                  List<ShAgentDesiredState.Post> posts, boolean prune,
                                  ShAgentDesiredState.Move... moves) {
        return new TransactionTemplate(transactionManager).execute(status ->
                shAgentDesiredStateService.plan(
                        withMoves(document(base, folders, posts, prune), moves),
                        true, "tester"));
    }
}
