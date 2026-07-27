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
 * SH81 — batch transactional mutation.
 *
 * <p>The properties under test are the ones the design rests on rather than the
 * current field list: <b>idempotency</b> (an address-keyed upsert applied twice is
 * applied once — without it the whole plan/apply story of SH82 collapses),
 * <b>atomicity</b> (a failing op leaves nothing behind, because a half-applied
 * structure costs more than a clean failure), <b>a dry run that writes nothing yet
 * still plans multi-step work</b>, and <b>errors that say which op failed</b>.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentBatchTest {

    @Autowired
    private ShAgentBatchService shAgentBatchService;
    @Autowired
    private ShAddressResolver shAddressResolver;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;
    private String postTypeName;

    @BeforeAll
    void setup() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = new ShSite();
            siteName = "batch-site-" + UUID.randomUUID();
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
            postTypeName = "BatchArticle" + UUID.randomUUID().toString().substring(0, 8);
            postType.setName(postTypeName);
            postType.setTitle("Batch Article");
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

    /**
     * Runs a batch, doing SH108's two-step when it could delete — the same thing the CLI does,
     * so each test below still reads as one gesture. A batch that destroys nothing is one call.
     */
    private ShAgentChangeSet run(boolean dryRun, Op... ops) {
        List<Op> list = List.of(ops);
        return new TransactionTemplate(transactionManager).execute(status -> {
            String confirm = !dryRun && ShAgentConfirm.mayDestroy(list)
                    ? shAgentBatchService.run(new ShAgentBatch(true, list), true, "tester").confirm()
                    : null;
            return shAgentBatchService.run(new ShAgentBatch(dryRun, list, confirm), true, "tester");
        });
    }

    /** The last result row — the one the assertion under test is about. */
    private static Change only(ShAgentChangeSet result) {
        return result.results().get(result.results().size() - 1);
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

    private static List<String> actions(ShAgentChangeSet result) {
        return result.results().stream().map(Change::action).toList();
    }

    // --- the core promise ---

    @Test
    void oneCallBuildsAStructureThatWouldCostADozen() {
        String base = "/news-" + UUID.randomUUID().toString().substring(0, 8);
        ShAgentChangeSet result = run(false,
                folder(base),
                folder(base + "/2026"),
                post(base + "/hello", base + "/2026", "Hello"),
                post(base + "/world", base + "/2026", "World"));

        assertThat(result.dryRun()).isFalse();
        assertThat(result.ops()).isEqualTo(4);
        assertThat(result.changed()).isEqualTo(4);
        assertThat(actions(result)).containsOnly(Change.CREATE);
        // Addressable afterwards without anyone having carried an id (SH79 + SH121).
        assertThat(shAddressResolver.resolve("post:" + siteName + base + "/hello").title())
                .isEqualTo("Hello");
        assertThat(shAddressResolver.resolve("folder:" + siteName + base + "/2026").kind())
                .isEqualTo("folder");
    }

    @Test
    void reRunningTheSameBatchChangesNothing() {
        // The load-bearing property: an address-keyed upsert is a statement about the
        // end state, not an instruction. Without this, SH82's plan/apply is impossible
        // and every re-run duplicates content.
        String base = "/idem-" + UUID.randomUUID().toString().substring(0, 8);
        Op[] ops = {folder(base), post(base + "/a", base, "A")};

        run(false, ops);
        ShAgentChangeSet second = run(false, ops);

        assertThat(second.changed()).isZero();
        assertThat(actions(second)).containsOnly(Change.NOOP);
    }

    @Test
    void changingDataUpdatesInPlaceRatherThanCreatingASecondPost() {
        String base = "/upd-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, folder(base), post(base + "/a", base, "First"));

        ShAgentChangeSet result = run(false, post(base + "/a", base, "Second"));

        assertThat(actions(result)).containsExactly(Change.UPDATE);
        assertThat(shAddressResolver.resolve("post:" + siteName + base + "/a").title())
                .isEqualTo("Second");
    }

    @Test
    void intermediateFoldersAreCreatedImplicitly() {
        String base = "/deep-" + UUID.randomUUID().toString().substring(0, 8);
        ShAgentChangeSet result = run(false, folder(base + "/a/b/c"));

        assertThat(actions(result)).containsExactly(Change.CREATE);
        assertThat(result.results().get(0).detail()).contains(base + "/a", base + "/a/b");
        assertThat(shAddressResolver.resolve("folder:" + siteName + base + "/a/b/c").kind())
                .isEqualTo("folder");
    }

    // --- dry run ---

    @Test
    void dryRunWritesNothing() {
        String base = "/dry-" + UUID.randomUUID().toString().substring(0, 8);
        ShAgentChangeSet planned = run(true, folder(base), post(base + "/a", base, "A"));

        assertThat(planned.dryRun()).isTrue();
        assertThat(planned.changed()).isEqualTo(2);
        assertThatThrownBy(() -> shAddressResolver.resolve("folder:" + siteName + base))
                .isInstanceOf(ShAddressResolver.UnresolvedAddressException.class);
    }

    @Test
    void dryRunPlansAgainstWhatEarlierOpsWouldHaveCreated() {
        // The reason a dry run simulates instead of writing-and-rolling-back: without
        // a pending set, "create /x then put a post in /x" reports a spurious 404 on
        // its second line and the plan is useless for exactly the structures the
        // endpoint exists for.
        String base = "/sim-" + UUID.randomUUID().toString().substring(0, 8);
        ShAgentChangeSet planned = run(true, folder(base), post(base + "/a", base, "A"));

        assertThat(actions(planned)).containsExactly(Change.CREATE, Change.CREATE);
    }

    @Test
    void dryRunIsAccurateAboutAnExistingInstance() {
        String base = "/acc-" + UUID.randomUUID().toString().substring(0, 8);
        Op[] ops = {folder(base), post(base + "/a", base, "A")};
        run(false, ops);

        assertThat(actions(run(true, ops))).containsOnly(Change.NOOP);
    }

    // --- atomicity ---

    @Test
    void aFailingOpRollsBackEveryOpBeforeIt() {
        String base = "/atomic-" + UUID.randomUUID().toString().substring(0, 8);

        assertThatThrownBy(() -> run(false,
                folder(base),
                post(base + "/a", base, "A"),
                new Op(ShAgentBatchService.POST_UPSERT, "post:" + siteName + base + "/b",
                        "NoSuchPostType", base, data("B"))))
                .isInstanceOf(ShAgentBatchService.BatchOpException.class);

        // Nothing survives — a half-applied structure would leave the caller unable to
        // tell which half landed without re-reading the whole site.
        assertThatThrownBy(() -> shAddressResolver.resolve("folder:" + siteName + base))
                .isInstanceOf(ShAddressResolver.UnresolvedAddressException.class);
        assertThatThrownBy(() -> shAddressResolver.resolve("post:" + siteName + base + "/a"))
                .isInstanceOf(ShAddressResolver.UnresolvedAddressException.class);
    }

    // --- publish / unpublish / delete ---

    @Test
    void publishIsAChangeOnceAndANoopAfterwards() {
        String base = "/pub-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, folder(base), post(base + "/a", base, "A"));
        String address = "post:" + siteName + base + "/a";
        Op publish = new Op(ShAgentBatchService.POST_PUBLISH, address, null, null, null);

        assertThat(actions(run(false, publish))).containsExactly(Change.PUBLISH);
        // Re-publishing identical content is not a change — a re-run of the same
        // document must not claim to have published again.
        assertThat(actions(run(false, publish))).containsExactly(Change.NOOP);
    }

    @Test
    void deleteSaysItWentToTheTrashSoTheUndoIsStillOffered() {
        String base = "/del-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, folder(base), post(base + "/a", base, "A"));

        Change change = run(false, new Op(ShAgentBatchService.POST_DELETE,
                "post:" + siteName + base + "/a", null, null, null)).results().get(0);

        assertThat(change.action()).isEqualTo(Change.DELETE);
        assertThat(change.detail()).contains("trash");
    }

    @Test
    void deletingWhatIsNotThereIsANoopNotAnError() {
        ShAgentChangeSet result = run(false, new Op(ShAgentBatchService.POST_DELETE,
                "post:" + siteName + "/never-existed", null, null, null));

        assertThat(actions(result)).containsExactly(Change.NOOP);
    }

    // --- teaching errors (SH80) ---

    @Test
    void aFailingOpSaysWhichOpFailed() {
        String base = "/at-" + UUID.randomUUID().toString().substring(0, 8);

        assertThatThrownBy(() -> run(true, folder(base),
                new Op("post.upzert", "post:" + siteName + base + "/a", null, null, null)))
                .isInstanceOf(ShAgentBatchService.BatchOpException.class)
                .satisfies(thrown -> {
                    var properties = ((ShAgentBatchService.BatchOpException) thrown)
                            .toProblemDetail().getProperties();
                    // Without `at`, a 40-op batch makes the caller guess which line broke.
                    assertThat(properties).containsEntry("at", "ops[1]");
                    assertThat(properties).containsEntry("didYouMean", "post.upsert");
                    assertThat(properties.get("allowed")).isEqualTo(ShAgentBatchService.OPS);
                });
    }

    @Test
    void anUnknownPostTypeListsTheOnesThatExist() {
        assertThatThrownBy(() -> run(true, new Op(ShAgentBatchService.POST_UPSERT,
                "post:" + siteName + "/x", "NoSuchType", "/", data("X"))))
                .isInstanceOf(ShAgentBatchService.BatchOpException.class)
                .satisfies(thrown -> assertThat(((ShAgentBatchService.BatchOpException) thrown)
                        .toProblemDetail().getProperties().get("allowed"))
                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories
                                .list(String.class))
                        .contains(postTypeName));
    }

    @Test
    void writesRefuseTheLenientSiteAddress() {
        // SH79's standing rule: a read may guess which namespace was meant because a
        // wrong guess costs a retry; a write may not, because a wrong guess creates
        // real content in the wrong place and nothing reports it.
        assertThatThrownBy(() -> run(true, new Op(ShAgentBatchService.POST_UPSERT,
                "site:" + siteName + "/x", postTypeName, "/", data("X"))))
                .isInstanceOf(ShAgentBatchService.BatchOpException.class)
                .satisfies(thrown -> assertThat(((ShAgentBatchService.BatchOpException) thrown)
                        .toProblemDetail().getDetail()).contains("namespaces"));
    }

    @Test
    void creatingAPostWithoutAFolderExplainsWhyAUrlIsNotEnough() {
        assertThatThrownBy(() -> run(true, new Op(ShAgentBatchService.POST_UPSERT,
                "post:" + siteName + "/orphan", postTypeName, null, data("X"))))
                .isInstanceOf(ShAgentBatchService.BatchOpException.class)
                .satisfies(thrown -> assertThat(((ShAgentBatchService.BatchOpException) thrown)
                        .toProblemDetail().getProperties()).containsEntry("example", "/blog"));
    }

    @Test
    void anEmptyBatchIsRejectedWithSomethingToDoInstead() {
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).execute(status ->
                shAgentBatchService.run(new ShAgentBatch(false, List.of()), false, "tester")))
                .isInstanceOf(ShAgentBatchService.EmptyBatchException.class);
    }

    // --- token economy (P3) ---

    @Test
    void resultRowsCarryNoIdUnlessAsked() {
        String base = "/lean-" + UUID.randomUUID().toString().substring(0, 8);
        ShAgentChangeSet lean = new TransactionTemplate(transactionManager).execute(status ->
                shAgentBatchService.run(
                        new ShAgentBatch(true, List.of(folder(base))), false, "tester"));

        assertThat(lean.results()).allSatisfy(change -> assertThat(change.id()).isNull());
        assertThat(objectMapper.writeValueAsString(lean)).doesNotContain("null");
    }

    // --- SH168: a write must refuse a field the post-type does not declare ---

    @Test
    @DisplayName("a mistyped field name is refused, and the refusal names the field that was meant")
    void anUndeclaredFieldIsRefused() {
        String base = "/uf-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, folder(base));

        Op typo = new Op(ShAgentBatchService.POST_UPSERT, "post:" + siteName + base + "/typo",
                postTypeName, base, objectMapper.valueToTree(Map.of("headlien", "Oops")));

        assertThatThrownBy(() -> run(false, typo))
                .isInstanceOf(ShAgentBatchService.BatchOpException.class)
                .satisfies(failure -> {
                    var detail = ((ShAgentBatchService.BatchOpException) failure).problem()
                            .toProblemDetail();
                    assertThat(detail.getStatus()).isEqualTo(400);
                    assertThat(detail.getProperties().get("allowed").toString()).contains("headline");
                    assertThat(detail.getProperties().get("didYouMean"))
                            .as("the nearest declared name is the field a typo meant")
                            .isEqualTo("headline");
                    assertThat(detail.getProperties().get("at"))
                            .as("which op failed, so the fix is local")
                            .isEqualTo("ops[0]");
                });
    }

    @Test
    @DisplayName("nothing is written when a field is refused — the post does not half-exist")
    void anUndeclaredFieldWritesNothing() {
        String base = "/uf-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, folder(base));
        Op typo = new Op(ShAgentBatchService.POST_UPSERT, "post:" + siteName + base + "/ghost",
                postTypeName, base, objectMapper.valueToTree(Map.of("nosuch", "value")));

        assertThatThrownBy(() -> run(false, typo)).isInstanceOf(ShAgentBatchService.BatchOpException.class);

        // The upsert that *is* correct reports create, which means the refused one left nothing.
        assertThat(only(run(true, post(base + "/ghost", base, "Ghost"))).action())
                .isEqualTo(Change.CREATE);
    }

    @Test
    @DisplayName("the refusal also applies to an update, before the value is replaced")
    void anUndeclaredFieldIsRefusedOnUpdateToo() {
        String base = "/uf-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, folder(base), post(base + "/page", base, "Before"));

        Op typo = new Op(ShAgentBatchService.POST_UPSERT, "post:" + siteName + base + "/page",
                postTypeName, base, objectMapper.valueToTree(Map.of("headlien", "After")));

        assertThatThrownBy(() -> run(false, typo)).isInstanceOf(ShAgentBatchService.BatchOpException.class);
        // An upsert declares whole state, so accepting the typo would have *replaced* the
        // headline with a key nothing reads. The stored value is untouched.
        assertThat(only(run(true, post(base + "/page", base, "Before"))).action())
                .isEqualTo(Change.NOOP);
    }

    @Test
    @DisplayName("a key the post already holds stays writable — an unwritable post is worse")
    void alreadyStoredKeysAreGrandfathered() {
        // How a post gets one in the first place: a post-type that declared the field when the
        // content was written, and no longer does (SH152's collisions, older File posts).
        String base = "/uf-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, folder(base), post(base + "/legacy", base, "Legacy"));
        String id = only(run(true, post(base + "/legacy", base, "Legacy"))).id();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShPostUnified draft = shPostUnifiedRepository.findByIdAndState(id, ShPostState.DRAFT)
                    .orElseThrow();
            draft.setData("{\"headline\":\"Legacy\",\"legacyField\":\"kept\"}");
            shPostUnifiedRepository.save(draft);
        });

        Op keepIt = new Op(ShAgentBatchService.POST_UPSERT, "post:" + siteName + base + "/legacy",
                postTypeName, base,
                objectMapper.valueToTree(Map.of("headline", "Edited", "legacyField", "kept")));

        assertThat(only(run(false, keepIt)).action())
                .as("refusing this would mean the agent could not fix the post at all")
                .isEqualTo(Change.UPDATE);
    }

    // --- SH170: the batch surface can make a site ---

    @Test
    @DisplayName("site.upsert creates a blank site — step one is on the surface now")
    void siteUpsertCreatesABlankSite() {
        String name = "sh170-" + UUID.randomUUID().toString().substring(0, 8);
        Op create = new Op(ShAgentBatchService.SITE_UPSERT, "site:" + name, null, null, null);

        Change change = only(run(false, create));

        assertThat(change.action()).isEqualTo(Change.CREATE);
        assertThat(change.id()).as("the id comes back so a caller that wants one has it").isNotNull();
        // Blank, not a clone of the SH71 bootstrap: a package applied over somebody else's
        // Welcome page is not what was asked for (SH151).
        assertThat(run(true, new Op(ShAgentBatchService.FOLDER_UPSERT, "folder:" + name + "/anything",
                null, null, null)).results().get(0).action())
                .isEqualTo(Change.CREATE);
    }

    @Test
    @DisplayName("a second site.upsert is a noop, so a document stays re-runnable")
    void siteUpsertIsIdempotent() {
        String name = "sh170-" + UUID.randomUUID().toString().substring(0, 8);
        Op create = new Op(ShAgentBatchService.SITE_UPSERT, "site:" + name, null, null, null);
        run(false, create);

        assertThat(only(run(false, create)).action()).isEqualTo(Change.NOOP);
    }

    @Test
    @DisplayName("a plan names the site before it exists — creating one is never a surprise")
    void siteUpsertPlansBeforeItCreates() {
        String name = "sh170-" + UUID.randomUUID().toString().substring(0, 8);
        Change planned = only(run(true, new Op(ShAgentBatchService.SITE_UPSERT, "site:" + name,
                null, null, null)));

        assertThat(planned.action()).isEqualTo(Change.CREATE);
        assertThat(planned.detail()).contains("blank");
        // And it wrote nothing: the same plan, run again, still reports a create.
        assertThat(only(run(true, new Op(ShAgentBatchService.SITE_UPSERT, "site:" + name,
                null, null, null))).action()).isEqualTo(Change.CREATE);
    }

    @Test
    @DisplayName("site.upsert takes a site: address, and refuses a post: one")
    void siteUpsertNeedsASiteAddress() {
        assertThatThrownBy(() -> run(false, new Op(ShAgentBatchService.SITE_UPSERT,
                "post:" + siteName + "/not-a-site", null, null, null)))
                .isInstanceOf(ShAgentBatchService.BatchOpException.class);
    }
}
