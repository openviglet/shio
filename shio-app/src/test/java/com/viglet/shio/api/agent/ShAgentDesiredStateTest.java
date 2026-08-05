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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.agent.dto.ShAgentChangeSet;
import com.viglet.shio.api.agent.dto.ShAgentChangeSet.Change;
import com.viglet.shio.api.agent.dto.ShAgentDesiredState;
import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SH82 — desired-state plan/apply for content.
 *
 * <p>What is actually being tested is the property that makes the document worth
 * having over a batch: <b>convergence</b>. Apply it, and the plan that follows is
 * empty; change one field, and the plan names exactly that one change. That empty
 * plan is the content drift guard, the same signal {@code shio push --check} gives
 * for post-types (SH65). The other load-bearing rule is that {@code plan} writes
 * nothing — a plan that perturbs the instance is not a plan.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentDesiredStateTest {

    @Autowired
    private ShAgentDesiredStateService shAgentDesiredStateService;
    @Autowired
    private ShAddressResolver shAddressResolver;
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
            siteName = "state-site-" + UUID.randomUUID();
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
            postTypeName = "StateArticle" + UUID.randomUUID().toString().substring(0, 8);
            postType.setName(postTypeName);
            postType.setTitle("State Article");
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

    private JsonNode data(String headline) {
        return objectMapper.valueToTree(Map.of("headline", headline));
    }

    private ShAgentDesiredState document(String base, boolean prune, String... headlines) {
        List<ShAgentDesiredState.Post> posts = new java.util.ArrayList<>();
        for (int i = 0; i < headlines.length; i++) {
            posts.add(new ShAgentDesiredState.Post(base + "/p" + i, postTypeName, base,
                    data(headlines[i]), null));
        }
        return new ShAgentDesiredState(siteName,
                List.of(new ShAgentDesiredState.Folder(base)), posts, prune);
    }

    private ShAgentChangeSet plan(ShAgentDesiredState desired) {
        return new TransactionTemplate(transactionManager).execute(status ->
                shAgentDesiredStateService.plan(desired, false, "tester"));
    }

    /**
     * Applies a document, confirming its deletions first when it prunes (SH108) — the two-step
     * the CLI does for the human. A document that cannot delete is still one call.
     */
    private ShAgentChangeSet apply(ShAgentDesiredState desired) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            String confirm = desired.prune()
                    ? shAgentDesiredStateService.plan(desired, false, "tester").confirm()
                    : null;
            // Every field, not the five the confirm case used to copy: a rebuild that drops one is a
            // helper that quietly tests a different document than the one it was handed (SH439).
            ShAgentDesiredState confirmed = confirm == null ? desired
                    : new ShAgentDesiredState(desired.site(), desired.folders(), desired.posts(),
                            desired.moves(), desired.prune(), confirm, desired.createSite());
            return shAgentDesiredStateService.apply(confirmed, false, "tester");
        });
    }

    private static List<String> actions(ShAgentChangeSet result) {
        return result.results().stream().map(Change::action).toList();
    }

    private String base() {
        return "/sec-" + UUID.randomUUID().toString().substring(0, 8);
    }

    // --- convergence ---

    @Test
    void applyThenPlanIsEmpty() {
        // The whole reason a document beats a script: the agent does not have to
        // remember what it already did, because the document is a fact about the world.
        ShAgentDesiredState desired = document(base(), false, "One", "Two");

        assertThat(apply(desired).changed()).isEqualTo(3);
        assertThat(plan(desired).changed()).isZero();
    }

    @Test
    void planNamesOnlyWhatActuallyDrifted() {
        String base = base();
        apply(document(base, false, "One", "Two"));

        ShAgentChangeSet drift = plan(document(base, false, "One", "Changed"));

        assertThat(drift.changed()).isEqualTo(1);
        assertThat(drift.results()).filteredOn(Change::isChange).singleElement()
                .satisfies(change -> {
                    assertThat(change.action()).isEqualTo(Change.UPDATE);
                    assertThat(change.address()).endsWith("/p1");
                });
    }

    @Test
    void planWritesNothing() {
        String base = base();
        ShAgentChangeSet planned = plan(document(base, false, "One"));

        assertThat(planned.dryRun()).isTrue();
        assertThat(planned.changed()).isEqualTo(2);
        assertThatThrownBy(() -> shAddressResolver.resolve("folder:" + siteName + base))
                .isInstanceOf(ShAddressResolver.UnresolvedAddressException.class);
    }

    @Test
    void applyIsAtomicAcrossTheWholeDocument() {
        String base = base();
        ShAgentDesiredState broken = new ShAgentDesiredState(siteName,
                List.of(new ShAgentDesiredState.Folder(base)),
                List.of(new ShAgentDesiredState.Post(base + "/ok", postTypeName, base,
                                data("Fine"), null),
                        new ShAgentDesiredState.Post(base + "/bad", "NoSuchType", base,
                                data("Broken"), null)),
                false);

        assertThatThrownBy(() -> apply(broken))
                .isInstanceOf(ShAgentBatchService.BatchOpException.class);
        assertThatThrownBy(() -> shAddressResolver.resolve("post:" + siteName + base + "/ok"))
                .isInstanceOf(ShAddressResolver.UnresolvedAddressException.class);
    }

    // --- publish semantics ---

    @Test
    void publishTrueGoesLiveAndStaysConvergent() {
        String base = base();
        ShAgentDesiredState desired = new ShAgentDesiredState(siteName,
                List.of(new ShAgentDesiredState.Folder(base)),
                List.of(new ShAgentDesiredState.Post(base + "/live", postTypeName, base,
                        data("Live"), true)),
                false);

        assertThat(actions(apply(desired)))
                .containsExactly(Change.CREATE, Change.CREATE, Change.PUBLISH);
        assertThat(plan(desired).changed()).isZero();
    }

    @Test
    void anAbsentPublishFlagLeavesTheLiveStateAlone() {
        // Treating absence as `false` would unpublish live pages the moment someone
        // trimmed a field out of the document — exactly the surprise P7 forbids.
        String base = base();
        apply(new ShAgentDesiredState(siteName,
                List.of(new ShAgentDesiredState.Folder(base)),
                List.of(new ShAgentDesiredState.Post(base + "/live", postTypeName, base,
                        data("Live"), true)),
                false));

        ShAgentChangeSet silent = plan(new ShAgentDesiredState(siteName,
                List.of(new ShAgentDesiredState.Folder(base)),
                List.of(new ShAgentDesiredState.Post(base + "/live", postTypeName, base,
                        data("Live"), null)),
                false));

        assertThat(silent.changed()).isZero();
        assertThat(actions(silent)).doesNotContain(Change.UNPUBLISH);
    }

    // --- prune ---

    @Test
    void prunePlansTheDeletionOfUndeclaredPostsBeforeAnythingIsDeleted() {
        String base = base();
        apply(document(base, false, "One", "Two"));

        ShAgentChangeSet planned = plan(document(base, true, "One"));

        // The deletion is visible in the plan first — that is what makes a destructive
        // apply reviewable by a human rather than a surprise (P7).
        assertThat(planned.results()).filteredOn(Change::isChange).singleElement()
                .satisfies(change -> {
                    assertThat(change.action()).isEqualTo(Change.DELETE);
                    assertThat(change.address()).endsWith("/p1");
                });
        // …and nothing happened yet.
        assertThat(shAddressResolver.resolve("post:" + siteName + base + "/p1").kind())
                .isEqualTo("post");
    }

    @Test
    void applyWithPruneRemovesTheUndeclaredPost() {
        String base = base();
        apply(document(base, false, "One", "Two"));

        assertThat(apply(document(base, true, "One")).changed()).isEqualTo(1);

        assertThatThrownBy(() -> shAddressResolver.resolve("post:" + siteName + base + "/p1"))
                .isInstanceOf(ShAddressResolver.UnresolvedAddressException.class);
        assertThat(shAddressResolver.resolve("post:" + siteName + base + "/p0").kind())
                .isEqualTo("post");
    }

    @Test
    void pruneWithoutDeclaredFoldersIsRefused() {
        // With no folders there is no boundary, and a document about one section would
        // quietly delete another.
        ShAgentDesiredState unscoped =
                new ShAgentDesiredState(siteName, List.of(), List.of(), true);

        assertThatThrownBy(() -> plan(unscoped))
                .isInstanceOf(ShAgentDesiredStateService.UnscopedPruneException.class);
    }

    @Test
    void pruneNeverReachesOutsideTheDeclaredFolders() {
        String kept = base();
        String owned = base();
        apply(document(kept, false, "Untouched"));
        apply(document(owned, false, "One", "Two"));

        apply(document(owned, true, "One"));

        assertThat(shAddressResolver.resolve("post:" + siteName + kept + "/p0").kind())
                .isEqualTo("post");
    }

    // --- a renamed folder keeps its posts (SH211) ---

    /**
     * The defect SH211 closes, end to end. Declared as state alone, a renamed section is an
     * undeclared folder at the old path plus a declared one at the new — and a {@code prune}
     * deletes the first, which for a folder takes <em>every post beneath it</em>. This is
     * SH150's failure one level up, where the loss is a section rather than one post.
     */
    @Test
    void aDeclaredFolderMoveRenamesTheSectionAndKeepsItsPosts() {
        String from = base();
        apply(document(from, false, "Kept"));
        String to = from + "-renamed";

        ShAgentChangeSet applied = apply(new ShAgentDesiredState(siteName,
                List.of(new ShAgentDesiredState.Folder(to, from)),
                List.of(new ShAgentDesiredState.Post(from + "/p0", postTypeName, to,
                        data("Kept"), null)),
                true));

        assertThat(applied.results()).anySatisfy(change ->
                assertThat(change.action()).isEqualTo(Change.MOVE));
        assertThat(shAddressResolver.resolve("folder:" + siteName + to).kind()).isEqualTo("folder");
        // SH137's whole point, and what makes the rename safe to infer from a file tree: the
        // pages inside keep their URLs, so a folder move has no delivery consequence.
        assertThat(shAddressResolver.resolve("post:" + siteName + from + "/p0").kind())
                .isEqualTo("post");
    }

    /**
     * The ordering the compiler exists to get right. Upserting the destination first would
     * <em>create</em> it, and {@code folder.move} then refuses an occupied target — so the
     * rename would fail on the one path it was asked to produce.
     */
    @Test
    void theDestinationIsNotCreatedBeforeTheMove() {
        String from = base();
        apply(document(from, false, "Kept"));
        String to = from + "-moved";

        List<ShAgentBatch.Op> ops = shAgentDesiredStateService.compile(new ShAgentDesiredState(
                siteName,
                List.of(new ShAgentDesiredState.Folder("/"),
                        new ShAgentDesiredState.Folder(to, from)),
                List.of(), false));

        List<String> order = ops.stream().map(ShAgentBatch.Op::op).toList();
        assertThat(order).containsExactly(ShAgentBatchService.FOLDER_UPSERT,
                ShAgentBatchService.FOLDER_MOVE, ShAgentBatchService.FOLDER_UPSERT);
        // The third phase is not redundant: after a *noop* move — the source is gone because a
        // previous run already did it — it is what makes the folder exist at all.
        assertThat(ops.get(2).address()).isEqualTo("folder:" + siteName + to);
    }

    /** Applying the same rename twice is applying it once — the property the shape exists for. */
    @Test
    void aRenameIsRerunnable() {
        String from = base();
        apply(document(from, false, "Kept"));
        String to = from + "-twice";
        ShAgentDesiredState renamed = new ShAgentDesiredState(siteName,
                List.of(new ShAgentDesiredState.Folder(to, from)),
                List.of(new ShAgentDesiredState.Post(from + "/p0", postTypeName, to,
                        data("Kept"), null)),
                false);
        apply(renamed);

        ShAgentChangeSet again = apply(renamed);

        assertThat(again.results()).allSatisfy(change ->
                assertThat(change.action()).isEqualTo(Change.NOOP));
        assertThat(shAddressResolver.resolve("folder:" + siteName + to).kind()).isEqualTo("folder");
    }

    /**
     * The source of a move is protected from the sweep, for SH150's reason with a worse
     * outcome. The sweep reads the database before any op has run, so the renamed section is
     * still at its old path and nothing declares it: it would be swept, and SH108's confirm
     * digest is taken over exactly that list — a rename would ask the operator to confirm
     * destroying the section it is preserving.
     */
    @Test
    void aMoveSourceIsNotPlannedForDeletion() {
        String from = base();
        apply(document(from, false, "Kept"));
        String to = from + "-safe";

        ShAgentChangeSet planned = plan(new ShAgentDesiredState(siteName,
                List.of(new ShAgentDesiredState.Folder(to, from)),
                List.of(new ShAgentDesiredState.Post(from + "/p0", postTypeName, to,
                        data("Kept"), null)),
                true));

        assertThat(planned.results()).noneSatisfy(change -> {
            assertThat(change.action()).isEqualTo(Change.DELETE);
            assertThat(change.address()).isEqualTo("folder:" + siteName + from);
        });
    }

    @Test
    void aFolderThatMovedFromItselfIsATeachingError() {
        ShAgentDesiredState circular = new ShAgentDesiredState(siteName,
                List.of(new ShAgentDesiredState.Folder("/loop", "/loop")), List.of(), false);

        assertThatThrownBy(() -> plan(circular))
                .isInstanceOf(ShAgentDesiredStateService.CircularFolderMoveException.class)
                .satisfies(thrown -> assertThat(
                        ((ShAgentDesiredStateService.CircularFolderMoveException) thrown)
                                .toProblemDetail().getProperties()).containsKey("fix"));
    }

    // --- teaching errors ---

    @Test
    void aDocumentWithoutASiteSaysWhatToAdd() {
        assertThatThrownBy(() -> plan(new ShAgentDesiredState(null, List.of(), List.of(), false)))
                .isInstanceOf(ShAgentDesiredStateService.MissingSiteException.class)
                .satisfies(thrown -> assertThat(((ShAgentDesiredStateService.MissingSiteException) thrown)
                        .toProblemDetail().getProperties()).containsKey("example"));
    }

    @Test
    void aPostWithoutAUrlExplainsWhyIdentityMatters() {
        ShAgentDesiredState anonymous = new ShAgentDesiredState(siteName, List.of(),
                List.of(new ShAgentDesiredState.Post(null, postTypeName, "/", data("X"), null)),
                false);

        assertThatThrownBy(() -> plan(anonymous))
                .isInstanceOf(ShAgentDesiredStateService.MissingPostUrlException.class);
    }

    // --- createSite (SH439) ---

    /** A document into a site that is not there, which is the normal shape of a replication. */
    private ShAgentDesiredState intoNewSite(String site, boolean createSite) {
        return new ShAgentDesiredState(site, List.of(new ShAgentDesiredState.Folder("/pages")),
                List.of(new ShAgentDesiredState.Post("/pages/home", postTypeName, "/pages",
                        data("Home"), null)),
                null, false, null, createSite);
    }

    /**
     * The flag is one op, and it is the first one.
     *
     * <p>Which is the whole design: the site has to exist before the folder a post needs, and
     * compiling to {@code site.upsert} rather than calling a write service means one op decides
     * what creating a site means — blank, never an update, refused for an SH2-scoped credential
     * (P10).</p>
     */
    @Test
    void createSiteCompilesToOneSiteUpsertBeforeEverythingElse() {
        List<ShAgentBatch.Op> ops = shAgentDesiredStateService
                .compile(intoNewSite("new-site-" + UUID.randomUUID(), true));

        assertThat(ops.stream().map(ShAgentBatch.Op::op).toList()).containsExactly(
                ShAgentBatchService.SITE_UPSERT, ShAgentBatchService.FOLDER_UPSERT,
                ShAgentBatchService.POST_UPSERT);
        assertThat(shAgentDesiredStateService.compile(intoNewSite(siteName, false)))
                .noneMatch(op -> ShAgentBatchService.SITE_UPSERT.equals(op.op()));
    }

    /**
     * SH439 — the defect, at the surface it was reported on.
     *
     * <p>{@code shio convert --site Viglet2} printed its plan and then refused with the write
     * resolver's fix: add a {@code site.upsert} op earlier in the same batch. Correct advice on the
     * batch surface and un-followable on a document, which takes no ops — P6 makes an instruction
     * naming something that does not exist worse than none. The refusal itself is right, so it
     * stays: what changes is that the caller can now express the remedy.</p>
     */
    @Test
    void aDocumentIntoAnAbsentSiteIsRefusedUntilItAsksForTheSite() {
        String absent = "absent-site-" + UUID.randomUUID();

        // The batch re-raises the resolver's problem with `at` added, so the slug is what identifies
        // it — and the fix it carries is the `site.upsert` sentence this task is about.
        assertThatThrownBy(() -> plan(intoNewSite(absent, false)))
                .isInstanceOf(ShAgentBatchService.BatchOpException.class)
                .satisfies(thrown -> {
                    ShProblem problem = ((ShAgentBatchService.BatchOpException) thrown).problem();
                    assertThat(problem.slug()).isEqualTo("unknown-site");
                    assertThat(problem.toProblemDetail().getProperties())
                            .extractingByKey("fix").asString().contains("site.upsert");
                });

        // And with the flag it resolves — on a *plan*, which is the half that could not work by
        // asking the database, because nothing has been created yet. SH329's `withSitesFrom` reads
        // the site off this run's own first op, so the plan describes its own first step.
        ShAgentChangeSet planned = plan(intoNewSite(absent, true));

        assertThat(planned.dryRun()).isTrue();
        assertThat(planned.results()).extracting(Change::op).containsExactly(
                ShAgentBatchService.SITE_UPSERT, ShAgentBatchService.FOLDER_UPSERT,
                ShAgentBatchService.POST_UPSERT);
        assertThat(planned.changed()).isEqualTo(3);
        // And a plan that describes creating a site still creates none — the endpoint's one promise.
        assertThatThrownBy(() -> shAddressResolver.resolve("site:" + absent))
                .isInstanceOf(ShAddressResolver.UnresolvedAddressException.class);
    }

    /** And the apply makes it so — then the same document is a noop, which is the shape's promise. */
    @Test
    void createSiteAppliesAndThenConverges() {
        String fresh = "fresh-site-" + UUID.randomUUID();

        ShAgentChangeSet applied = apply(intoNewSite(fresh, true));

        assertThat(actions(applied)).containsExactly(Change.CREATE, Change.CREATE, Change.CREATE);
        assertThat(shAddressResolver.resolve("post:" + fresh + "/pages/home").kind())
                .isEqualTo("post");
        // Re-running is the property the whole document shape exists for, and `createSite` must not
        // cost it: a second run's `site.upsert` is a noop, not a second site and not an error.
        assertThat(plan(intoNewSite(fresh, true)).changed()).isZero();
    }
}
