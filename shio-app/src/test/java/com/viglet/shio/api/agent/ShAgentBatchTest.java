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
import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.agent.dto.ShAgentBatch.Op;
import com.viglet.shio.api.agent.dto.ShAgentChangeSet;
import com.viglet.shio.api.agent.dto.ShAgentChangeSet.Change;
import com.viglet.shio.api.agent.dto.ShAgentDesiredState;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.history.ShHistory;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.persistence.repository.history.ShHistoryRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.post.type.ShSystemPostType;

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
    /** SH1283 — to read back the page type a site.upsert stored. */
    @Autowired
    private com.viglet.shio.persistence.repository.site.ShSiteRepository siteRepository;
    @Autowired
    private ShAddressResolver shAddressResolver;
    /** SH1277 — the grants a folder.acl op wrote, and the compiler that emits the op. */
    @Autowired
    private com.viglet.shio.api.acl.ShContentAclService shContentAclService;
    @Autowired
    private ShAgentDesiredStateService shAgentDesiredStateService;
    /** SH1301 — the sitemap row the files projection reads a folder's gate from. */
    @Autowired
    private ShAgentContextService shAgentContextService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private ShAgentReadService shAgentReadService;
    /** SH1032 — the trail a metadata write now lands in, read back rather than mocked. */
    @Autowired
    private ShHistoryRepository shHistoryRepository;

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
    @DisplayName("SH1277: folder.acl replaces a folder's grants, a re-run is a noop, and a grant to nobody is refused")
    void aFolderSaysWhoMayEditIt() {
        String base = "/acl-" + UUID.randomUUID().toString().substring(0, 8);
        String group = "Sh1277Editors-" + UUID.randomUUID().toString().substring(0, 8);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var editors = new com.viglet.shio.persistence.model.auth.ShGroup();
            editors.setName(group);
            entityManager.persist(editors);
        });
        run(false, folder(base));
        Op grant = new Op(ShAgentBatchService.FOLDER_ACL, "folder:" + siteName + base, null, null,
                objectMapper.valueToTree(Map.of("acl",
                        List.of(Map.of("group", group, "permission", "write")))));

        assertThat(only(run(true, grant)).action())
                .as("SH1277: a dry run says the grants would change and writes nothing")
                .isEqualTo(Change.UPDATE);
        assertThat(only(run(false, grant)).action()).isEqualTo(Change.UPDATE);
        String folderId = shAddressResolver.resolve("folder:" + siteName + base).id();
        assertThat(shContentAclService.getAcl(folderId))
                .as("SH1277: the folder now carries the declared grant, and only it")
                .singleElement()
                .satisfies(acl -> {
                    assertThat(acl.getPrincipalName()).isEqualTo(group);
                    assertThat(acl.getPermission().name()).isEqualTo("WRITE");
                });
        assertThat(only(run(false, grant)).action())
                .as("SH1277: the same document re-applied changes nothing")
                .isEqualTo(Change.NOOP);

        Op nobody = new Op(ShAgentBatchService.FOLDER_ACL, "folder:" + siteName + base, null, null,
                objectMapper.valueToTree(Map.of("acl",
                        List.of(Map.of("group", "no-such-group-sh1277", "permission", "WRITE")))));
        assertThatThrownBy(() -> run(true, nobody))
                .as("SH1277: a grant to nobody would lock the folder against everyone but an admin")
                .isInstanceOf(ShAgentBatchService.BatchOpException.class)
                .satisfies(failure -> assertThat(((ShAgentBatchService.BatchOpException) failure)
                        .problem().toProblemDetail().getTitle()).contains("no-such-group-sh1277"));

        Op twoPrincipals = new Op(ShAgentBatchService.FOLDER_ACL, "folder:" + siteName + base, null,
                null, objectMapper.valueToTree(Map.of("acl",
                        List.of(Map.of("group", group, "user", "admin", "permission", "WRITE")))));
        assertThatThrownBy(() -> run(true, twoPrincipals))
                .as("SH1277: a grant names exactly one principal")
                .isInstanceOf(ShAgentBatchService.BatchOpException.class);

        assertThat(shAgentDesiredStateService.compile(new ShAgentDesiredState(siteName,
                        List.of(new ShAgentDesiredState.Folder(base, null, objectMapper.valueToTree(
                                List.of(Map.of("group", group, "permission", "WRITE"))))),
                        List.of(), false)).stream().map(Op::op).toList())
                .as("SH1277: a desired-state folder with an acl compiles to its grant after the folder")
                .containsSubsequence(ShAgentBatchService.FOLDER_UPSERT, ShAgentBatchService.FOLDER_ACL);
    }

    @Test
    @DisplayName("SH1285: folder.visibility gates a section's readers, a re-run is a noop, and public opens it")
    void aFolderSaysWhoMayReadIt() {
        String base = "/members-" + UUID.randomUUID().toString().substring(0, 8);
        String group = "Sh1285Members-" + UUID.randomUUID().toString().substring(0, 8);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var members = new com.viglet.shio.persistence.model.auth.ShGroup();
            members.setName(group);
            entityManager.persist(members);
        });
        run(false, folder(base));
        String address = "folder:" + siteName + base;
        Op gate = new Op(ShAgentBatchService.FOLDER_VISIBILITY, address, null, null,
                objectMapper.valueToTree(Map.of("visibility", "members", "groups", List.of(group))));

        assertThat(only(run(true, gate)).action()).isEqualTo(Change.UPDATE);
        String folderId = shAddressResolver.resolve(address).id();
        assertThat(shContentAclService.ownGate(folderId))
                .as("SH1285: a dry run writes nothing")
                .isEmpty();
        assertThat(only(run(false, gate)).action()).isEqualTo(Change.UPDATE);
        assertThat(shContentAclService.ownGate(folderId))
                .as("SH1285: the folder is now for that group's members")
                .contains(List.of(group));
        assertThat(only(run(false, gate)).action())
                .as("SH1285: the same declaration re-applied changes nothing")
                .isEqualTo(Change.NOOP);

        Op nobody = new Op(ShAgentBatchService.FOLDER_VISIBILITY, address, null, null,
                objectMapper.valueToTree(Map.of("visibility", "members", "groups", List.of("no-such-group-sh1285"))));
        assertThatThrownBy(() -> run(true, nobody))
                .as("SH1285: a gate for nobody would hide the section from everyone but an admin")
                .isInstanceOf(ShAgentBatchService.BatchOpException.class);
        Op misspelled = new Op(ShAgentBatchService.FOLDER_VISIBILITY, address, null, null,
                objectMapper.valueToTree(Map.of("visibility", "private")));
        assertThatThrownBy(() -> run(true, misspelled))
                .as("SH1285: only public and members are visibilities")
                .isInstanceOf(ShAgentBatchService.BatchOpException.class);

        // SH1301 — the files projection reads the gate off the sitemap and writes it back through a
        // desired-state folder, so both ends are asserted here, beside the op they both reach.
        assertThat(shAgentContextService.context(siteName, null, 0, "sitemap", null).sitemap())
                .as("SH1301: a gated folder's sitemap row carries its gate, which `shio pull` projects")
                .anySatisfy(node -> {
                    assertThat(node.path()).isEqualTo(base);
                    assertThat(node.visibility()).isEqualTo("members");
                    assertThat(node.groups()).containsExactly(group);
                });
        assertThat(shAgentDesiredStateService.compile(new ShAgentDesiredState(siteName,
                        List.of(new ShAgentDesiredState.Folder(base, null, null, "public", null)),
                        List.of(), false)))
                .as("SH1301: a desired-state folder with a visibility compiles to folder.visibility after it")
                .anySatisfy(compiled -> {
                    assertThat(compiled.op()).isEqualTo(ShAgentBatchService.FOLDER_VISIBILITY);
                    assertThat(compiled.data().path("visibility").asString()).isEqualTo("public");
                });

        run(false, new Op(ShAgentBatchService.FOLDER_VISIBILITY, address, null, null,
                objectMapper.valueToTree(Map.of("visibility", "public"))));
        assertThat(shContentAclService.ownGate(folderId))
                .as("SH1285: public removes the gate")
                .isEmpty();
    }

    @Test
    @DisplayName("SH1300: post.visibility gates one post, a re-run is a noop, and public opens it")
    void aPostSaysWhoMayReadIt() {
        String base = "/pv-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, folder(base), post(base + "/vip", base, "Vip"));
        String address = "post:" + siteName + base + "/vip";
        Op gate = new Op(ShAgentBatchService.POST_VISIBILITY, address, null, null,
                objectMapper.valueToTree(Map.of("visibility", "members")));

        assertThat(only(run(false, gate)).action()).isEqualTo(Change.UPDATE);
        String postId = shAddressResolver.resolve(address).id();
        assertThat(shContentAclService.ownGate(postId))
                .as("SH1300: the post itself carries the gate, not its folder")
                .contains(List.of());
        assertThat(shContentAclService.ownGate(shAddressResolver.resolve("folder:" + siteName + base).id()))
                .isEmpty();
        assertThat(only(run(false, gate)).action()).isEqualTo(Change.NOOP);

        assertThat(only(run(true, new Op(ShAgentBatchService.POST_VISIBILITY, "post:" + siteName + base + "/planned",
                null, null, objectMapper.valueToTree(Map.of("visibility", "members"))))).action())
                .as("SH1300: a dry run plans a post the batch has not written yet, as a planned folder")
                .isEqualTo(Change.UPDATE);
        assertThatThrownBy(() -> run(false, new Op(ShAgentBatchService.POST_VISIBILITY,
                "post:" + siteName + base + "/missing", null, null,
                objectMapper.valueToTree(Map.of("visibility", "members")))))
                .as("SH1300: and a real run refuses a post that does not exist")
                .isInstanceOf(RuntimeException.class);

        run(false, new Op(ShAgentBatchService.POST_VISIBILITY, address, null, null,
                objectMapper.valueToTree(Map.of("visibility", "public"))));
        assertThat(shContentAclService.ownGate(postId)).isEmpty();
    }

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

    @Test
    @DisplayName("SH872: a key this write's own markup annotates is declared by that markup")
    void aMarkupAnnotatedKeyIsDeclared() {
        // The assertion that would have caught it, at the boundary it crossed. SH868 kept the
        // annotated values off the post-type by writing a field per captured block onto `Page`,
        // which put 44 fields on the type every page in the instance shares. SH872 gives the
        // storage back to the markup — and a curator's edit through the Universal Editor then
        // stores a key the type does not declare, so a `shio push` of the content file that edit
        // produced would meet SH168 and be refused. It is the markup that declares it.
        String base = "/an-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, folder(base));

        Op edited = new Op(ShAgentBatchService.POST_UPSERT, "post:" + siteName + base + "/page",
                postTypeName, base, objectMapper.valueToTree(Map.of(
                        "headline", "<h1 sh-field=\"b1a2c3dHeading\">Source text</h1>",
                        "b1a2c3dHeading", "What the curator typed")));

        assertThat(only(run(false, edited)).action()).isEqualTo(Change.CREATE);
    }

    @Test
    @DisplayName("SH872: and a key no markup annotates is still the SH168 refusal it always was")
    void anUnannotatedKeyIsStillRefused() {
        // The bound, stated: the rule is not "a post carrying markup may store anything". The key
        // has to be addressed by an sh-field attribute in a string this same write carries.
        String base = "/an-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, folder(base));

        Op smuggled = new Op(ShAgentBatchService.POST_UPSERT, "post:" + siteName + base + "/page",
                postTypeName, base, objectMapper.valueToTree(Map.of(
                        "headline", "<h1 sh-field=\"b1a2c3dHeading\">Source text</h1>",
                        "somethingElse", "not annotated anywhere")));

        assertThatThrownBy(() -> run(false, smuggled))
                .isInstanceOf(ShAgentBatchService.BatchOpException.class);
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

    // --- SH184: fill what is empty, never replace what somebody chose ---

    @Test
    @DisplayName("SH1283: a site names its page type with site.upsert, and only a bound type")
    void siteUpsertNamesABoundPageType() {
        String name = "sh1283-" + UUID.randomUUID().toString().substring(0, 8);
        JsonNode bound = objectMapper.valueToTree(Map.of(
                "postTypeLayout", Map.of("InfoPage", "Page", "Trail", "Detail"), "pageType", "InfoPage"));

        assertThat(only(run(false, siteOp(name, bound))).action())
                .as("SH1283: binding and naming in one op is not refused for its own order")
                .isEqualTo(Change.CREATE);
        assertThat(siteRepository.findByName(name).getPageType())
                .as("SH1283: the type a new page in this site starts as")
                .isEqualTo("InfoPage");

        String other = "sh1283-" + UUID.randomUUID().toString().substring(0, 8);
        assertThatThrownBy(() -> run(true, siteOp(other, objectMapper.valueToTree(Map.of(
                "postTypeLayout", Map.of("Trail", "Detail"), "pageType", "Guide")))))
                .as("SH1283: a type with no layout cannot render as a page, refused at plan time too")
                .isInstanceOf(ShAgentBatchService.BatchOpException.class).hasMessageContaining("no layout binding");
        assertThatThrownBy(() -> run(false, siteOp(name, objectMapper.valueToTree(Map.of("pageType", "Guide")))))
                .as("SH1283: an existing site is checked against the bindings it already has")
                .isInstanceOf(ShAgentBatchService.BatchOpException.class).hasMessageContaining("no layout binding");
    }

    @Test
    @DisplayName("SH1299: site.upsert sets the sign-in switch on a site that has none, and keeps one already set")
    void siteUpsertFillsTheMembersLoginSwitch() {
        String name = "sh1299-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, siteOp(name, null));
        assertThat(siteRepository.findByName(name).getMembersLogin())
                .as("SH1299: a site created without it keeps the 404")
                .isNull();

        assertThat(only(run(false, siteOp(name, objectMapper.valueToTree(Map.of("membersLogin", true))))).action())
                .isEqualTo(Change.UPDATE);
        assertThat(siteRepository.findByName(name).sendsMembersToLogin()).isTrue();

        ShAgentChangeSet.Change kept = only(run(false, siteOp(name, objectMapper.valueToTree(Map.of("membersLogin", false)))));
        assertThat(kept.action())
                .as("SH1299: an upsert fills what is empty and never replaces what somebody set")
                .isEqualTo(Change.NOOP);
        assertThat(siteRepository.findByName(name).sendsMembersToLogin()).isTrue();
    }

    @Test
    @DisplayName("SH184: an empty url is filled — the agent is what knows the URL")
    void siteUpsertFillsAnEmptyUrl() {
        String name = "sh184-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, siteOp(name, null));

        // SH170 refused to update at all, so an agent could build a site, deploy a front end
        // and still not connect the two without a human opening the console. Nothing about
        // that is a judgement only a person can make.
        Change change = only(run(false, siteOp(name, siteData("https://built.example.com", null))));

        assertThat(change.action()).isEqualTo(Change.UPDATE);
        assertThat(change.detail()).contains("filled url");
    }

    @Test
    @DisplayName("SH184: a url somebody set is kept, and the report says so")
    void siteUpsertNeverReplacesAChosenUrl() {
        String name = "sh184-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, siteOp(name, siteData("https://chosen.example.com", null)));

        Change change = only(run(false, siteOp(name, siteData("https://hijack.example.com", null))));

        assertThat(change.action())
                .as("""
                        the P7 line runs between filling in what was empty and redirecting what \
                        somebody chose, and only the second is a surprise""")
                .isEqualTo(Change.NOOP);
        assertThat(change.detail())
                .as("""
                        naming what was kept is what stops this reading as the op having ignored \
                        the request; a bare noop tells the caller nothing about why""")
                .contains("kept the existing url")
                .contains("console");
    }

    @Test
    @DisplayName("SH184: filling is idempotent, so a document stays re-runnable")
    void fillingIsIdempotent() {
        String name = "sh184-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, siteOp(name, null));
        Op withUrl = siteOp(name, siteData("https://built.example.com", "A site"));

        assertThat(only(run(false, withUrl)).action()).isEqualTo(Change.UPDATE);
        assertThat(only(run(false, withUrl)).action())
                .as("the same document applied twice reports no change the second time (P5)")
                .isEqualTo(Change.NOOP);
    }

    @Test
    @DisplayName("SH184: a plan says in advance that a url will not be applied")
    void aPlanReportsWhatWillBeKept() {
        String name = "sh184-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, siteOp(name, siteData("https://chosen.example.com", null)));

        Change planned = only(run(true, siteOp(name, siteData("https://hijack.example.com", null))));

        assertThat(planned.action()).isEqualTo(Change.NOOP);
        assertThat(planned.detail()).contains("kept the existing url");
        // And the plan wrote nothing, so the real run still reports the same thing.
        assertThat(only(run(false, siteOp(name, siteData("https://hijack.example.com", null))))
                .action()).isEqualTo(Change.NOOP);
    }

    @Test
    @DisplayName("SH184: a plan reports the fill it would do, and does not do it")
    void aPlanReportsAFillWithoutWriting() {
        String name = "sh184-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, siteOp(name, null));
        Op withUrl = siteOp(name, siteData("https://built.example.com", null));

        assertThat(only(run(true, withUrl)).action()).isEqualTo(Change.UPDATE);
        assertThat(only(run(true, withUrl)).action())
                .as("a dry run that had written would report noop the second time")
                .isEqualTo(Change.UPDATE);
    }

    // --- SH328: the one edge that makes a site render ---

    /**
     * The gap this closes: an agent could build the theme, the regions, the layout and the pages,
     * and then not bind them, so nothing rendered. Both surfaces that instruct the write
     * (SH272's conventions line, SH298's `unused-layout` fix) named an op that did not exist.
     */
    @Test
    @DisplayName("SH328: site.upsert binds a post-type to a layout")
    void siteUpsertBindsAPostTypeToALayout() {
        String name = "sh328-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, siteOp(name, null));

        Change change = only(run(false, siteOp(name, bindings(Map.of("Article", "Article Layout")))));

        assertThat(change.action()).isEqualTo(Change.UPDATE);
        assertThat(change.detail()).contains("postTypeLayout.Article");
    }

    @Test
    @DisplayName("SH328: a second binding does not disturb the first")
    void bindingASecondPostTypeKeepsTheFirst() {
        String name = "sh328-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, siteOp(name, null));
        run(false, siteOp(name, bindings(Map.of("Article", "Article Layout"))));

        run(false, siteOp(name, bindings(Map.of("Page", "Page Layout"))));

        // Per key, which is the whole point: the console's PUT is a whole-object replace that
        // nulls this map when the field is omitted, and that hazard is what this op avoids.
        Change again = only(run(false, siteOp(name, bindings(Map.of("Article", "Article Layout")))));
        assertThat(again.action())
                .as("the first binding survived writing the second, so re-asserting it is a noop")
                .isEqualTo(Change.NOOP);
    }

    @Test
    @DisplayName("SH328: a binding somebody set is kept and reported, never replaced")
    void anExistingBindingIsKept() {
        String name = "sh328-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, siteOp(name, null));
        run(false, siteOp(name, bindings(Map.of("Article", "Chosen Layout"))));

        Change change = only(run(false, siteOp(name, bindings(Map.of("Article", "Other Layout")))));

        assertThat(change.action()).isEqualTo(Change.NOOP);
        assertThat(change.detail())
                .as("SH184's rule, one field-type wider — and the caller is told why, or a noop "
                        + "reads as the op having ignored the request")
                .contains("postTypeLayout.Article");
    }

    @Test
    @DisplayName("SH328: a dry run reports the binding and writes nothing")
    void aDryRunReportsTheBindingWithoutWriting() {
        String name = "sh328-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, siteOp(name, null));
        Op bind = siteOp(name, bindings(Map.of("Article", "Article Layout")));

        assertThat(only(run(true, bind)).action()).isEqualTo(Change.UPDATE);
        assertThat(only(run(true, bind)).action())
                .as("a dry run that had written would report noop the second time")
                .isEqualTo(Change.UPDATE);
    }

    // --- SH453: the create branch used to drop the bindings it was handed ---

    /**
     * Why every SH328 test above missed this: each one creates the site first and binds second, so
     * the op under test is always the <em>fill</em> branch. A caller doing both in one op went down
     * the create branch, which read {@code description}, {@code url} and {@code furl} and nothing
     * else — and was told {@code CREATE — a blank site}, which was true and was the reason nobody
     * noticed. Both halves are individually correct, which is why SH448's conformance suite found
     * it on a live instance and no unit test had.
     */
    @Test
    @DisplayName("SH453: creating a site and binding its layout in one op applies both")
    void aCreateAppliesTheBindingsItWasHanded() {
        String name = "sh453-" + UUID.randomUUID().toString().substring(0, 8);

        Change change = only(run(false, siteOp(name, bindings(Map.of("Article", "Article Layout")))));

        assertThat(change.action()).isEqualTo(Change.CREATE);
        assertThat(change.detail())
                .as("and it says so: reporting `a blank site` either way is what kept this invisible "
                        + "to the one caller who could have noticed — the one who sent the binding")
                .contains("postTypeLayout.Article");
        // Read back rather than inferred from the report, because the op that dropped the binding
        // still answered CREATE. The failure surfaced four writes later as a 400 from
        // /preview/<site>/ — "Site '…' binds no layout for post-type '…'" — a good message
        // answering a question about the wrong step.
        assertThat(bindingsOn(name))
                .containsEntry("Article", "Article Layout");
    }

    @Test
    @DisplayName("SH453: a plan says the same thing the run does, bindings included")
    void aPlannedCreateNamesTheBindings() {
        String name = "sh453-" + UUID.randomUUID().toString().substring(0, 8);
        Op create = siteOp(name, bindings(Map.of("Article", "Article Layout")));

        Change planned = only(run(true, create));

        assertThat(planned.action()).isEqualTo(Change.CREATE);
        assertThat(planned.detail()).contains("blank").contains("postTypeLayout.Article");
        // A plan derived from a second reading of the rule is one that can disagree with the run it
        // predicts, so the detail comes from the same call in both branches.
        assertThat(only(run(false, create)).detail()).isEqualTo(planned.detail());
    }

    @Test
    @DisplayName("SH453: a create with no bindings still reports a blank site and writes no map")
    void aCreateWithoutBindingsIsUnchanged() {
        String name = "sh453-" + UUID.randomUUID().toString().substring(0, 8);

        Change change = only(run(false, siteOp(name, null)));

        assertThat(change.detail()).isEqualTo("a blank site");
        assertThat(bindingsOn(name))
                .as("an empty map leaves the column null, exactly as before — the fix adds a "
                        + "binding when one was sent and changes nothing when none was")
                .isEmpty();
    }

    // --- SH1056: a site declares the locales it is in --------------------------------------

    @Test
    @DisplayName("SH1056: a create declares the site's locale policy, and stores it stably")
    void aCreateDeclaresALocalePolicy() {
        String name = "sh1056-" + UUID.randomUUID().toString().substring(0, 8);

        Change change = only(run(false, siteOp(name, localeData("en", List.of("en", "pt-br"),
                Map.of("pt-br", "en")))));

        assertThat(change.detail()).contains("declared locale policy");
        assertThat(localesOn(name))
                .as("SH1056: P5 — the fixed key order and the sorted map, so the same policy is "
                        + "the same bytes wherever it is projected")
                .isEqualTo("{\"default\":\"en\",\"enabled\":[\"en\",\"pt-br\"],"
                        + "\"fallback\":{\"pt-br\":\"en\"}}");
    }

    @Test
    @DisplayName("SH1056: a locale this instance does not know is refused at plan time too")
    void anUnknownLocaleIsRefusedBeforeAnythingIsWritten() {
        String name = "sh1056-" + UUID.randomUUID().toString().substring(0, 8);
        JsonNode policy = localeData("pt-BR", List.of("pt-BR"), Map.of());

        assertThatThrownBy(() -> run(true, siteOp(name, policy)))
                .as("SH1056: this class's own rule — a plan derived from a second reading of the "
                        + "rule is a plan that can disagree with the run it predicts, so the "
                        + "casing an agent guesses is a 400 on the dry run as well")
                .isInstanceOf(ShTeachingException.class);
        assertThat(localesOn(name)).as("and nothing was written").isNull();
    }

    @Test
    @DisplayName("SH1056: an upsert fills a policy that is missing and keeps one that is set")
    void aPolicyIsFilledOnceAndThenKept() {
        String name = "sh1056-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, siteOp(name, null));

        Change filled = only(run(false, siteOp(name, localeData("en", List.of("en"), Map.of()))));
        Change kept = only(run(false, siteOp(name, localeData("pt-br", List.of("pt-br"),
                Map.of()))));

        assertThat(filled.detail()).contains("filled").contains("locales");
        assertThat(kept.detail())
                .as("SH1056: P7 — an upsert fills what is empty and never replaces a decision "
                        + "somebody made, and it says which value it protected")
                .contains("kept the existing").contains("locales");
        assertThat(localesOn(name)).contains("\"default\":\"en\"");
    }

    /** The site's declared locale policy as stored, read straight out of the column. */
    private String localesOn(String name) {
        return new TransactionTemplate(transactionManager).execute(status -> entityManager
                .createQuery("select s from ShSite s where s.name = :name", ShSite.class)
                .setParameter("name", name)
                .getResultStream()
                .findFirst()
                .map(ShSite::getLocales)
                .orElse(null));
    }

    private JsonNode localeData(String defaultLocale, List<String> enabled,
                                Map<String, String> fallback) {
        Map<String, Object> policy = new java.util.LinkedHashMap<>();
        policy.put("default", defaultLocale);
        policy.put("enabled", enabled);
        policy.put("fallback", fallback);
        return objectMapper.valueToTree(Map.of("locales", policy));
    }

    /** The site's post-type → layout map as stored, read straight out of the column. */
    private Map<String, String> bindingsOn(String name) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            ShSite site = entityManager
                    .createQuery("select s from ShSite s where s.name = :name", ShSite.class)
                    .setParameter("name", name)
                    .getSingleResult();
            String raw = site.getPostTypeLayout();
            if (raw == null || raw.isBlank()) {
                return Map.<String, String>of();
            }
            return objectMapper.readValue(raw, new tools.jackson.core.type.TypeReference<Map<String, String>>() {});
        });
    }

    private Op siteOp(String name, JsonNode data) {
        return new Op(ShAgentBatchService.SITE_UPSERT, "site:" + name, null, null, data);
    }

    private JsonNode bindings(Map<String, String> postTypeToLayout) {
        return objectMapper.valueToTree(Map.of("postTypeLayout", postTypeToLayout));
    }

    private JsonNode siteData(String url, String description) {
        Map<String, String> fields = new java.util.LinkedHashMap<>();
        if (url != null) {
            fields.put("url", url);
        }
        if (description != null) {
            fields.put("description", description);
        }
        return objectMapper.valueToTree(fields);
    }

    // --- SH721: a write that can be made conditional ---

    /**
     * SH721 — the lost update the surface could not express a defence against.
     *
     * <p>Measured by SH713: a curator patching one field while an agent runs {@code post.upsert} on
     * the same post gets {@code 200}, the agent gets {@code changed=1}, and the curator's field is
     * <b>gone</b> — with no signal to either party. SH335 added the console {@code PATCH} for
     * precisely this reason and the reasoning stopped at the console door.</p>
     *
     * <p>The curator here is the second write: what matters is not who ran it but that it landed
     * between the read that planned the agent's write and the write itself, which is the whole
     * window.</p>
     */
    @Test
    void anExpectedDigestRefusesAWriteThatWouldOverwriteSomebodyElse() {
        String url = "/expect-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, post(url, "/", "First"));
        String read = digestOf(url);
        assertThat(read)
                .as("a read asked for `include=digest` hands back the value `expect` takes")
                .isNotBlank();

        // The edit that lands inside the agent's window.
        run(false, post(url, "/", "Curator merged a correction"));

        assertThatThrownBy(() -> run(false, expecting(post(url, "/", "Agent plan"), read)))
                .as("""
                        before SH721 this SUCCEEDED and the correction was gone, with both sides                         told they had won. Nothing on the surface said `only if unchanged`.""")
                // The batch layer wraps a per-op teaching error so the refusal names WHICH op of
                // twenty failed, which is the form a caller actually receives.
                .isInstanceOf(ShAgentBatchService.BatchOpException.class)
                .hasMessageContaining("to be unchanged")
                .hasMessageContaining("Somebody wrote to it");

        assertThat(shAddressResolver.resolve("post:" + siteName + url).title())
                .as("and the refusal wrote nothing, which is what makes it safe to act on")
                .isEqualTo("Curator merged a correction");
    }

    @Test
    void anExpectedDigestThatStillHoldsWritesNormally() {
        String url = "/expect-ok-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, post(url, "/", "First"));

        ShAgentChangeSet result = run(false, expecting(post(url, "/", "Second"), digestOf(url)));

        assertThat(only(result).action()).isEqualTo(Change.UPDATE);
        assertThat(shAddressResolver.resolve("post:" + siteName + url).title()).isEqualTo("Second");
    }

    @Test
    void aDryRunRefusesTheStaleExpectationTheWriteWouldRefuse() {
        String url = "/expect-plan-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, post(url, "/", "First"));
        String read = digestOf(url);
        run(false, post(url, "/", "Changed underneath"));

        assertThatThrownBy(() -> run(true, expecting(post(url, "/", "Agent plan"), read)))
                .as("""
                        P9: a plan that says `update` about a post somebody has since rewritten is                         a plan whose only use is to be trusted, and it would be wrong.""")
                .isInstanceOf(ShAgentBatchService.BatchOpException.class)
                .hasMessageContaining("to be unchanged");
    }

    @Test
    void expectingAPostThatIsGoneIsAlsoRefused() {
        String url = "/expect-gone-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, post(url, "/", "First"));
        String read = digestOf(url);

        assertThatThrownBy(() -> run(false,
                expecting(post(url + "-elsewhere", "/", "Agent plan"), read)))
                .as("""
                        absence is not `unchanged` — it is a different state, and re-creating what                         somebody deleted is the surprise `expect` exists to catch, not one it                         should wave through.""")
                .isInstanceOf(ShAgentBatchService.BatchOpException.class)
                .hasMessageContaining("there is no post there now");
    }

    @Test
    void withoutExpectTheSurfaceIsStillLastWriteWins() {
        String url = "/expect-off-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, post(url, "/", "First"));
        run(false, post(url, "/", "Second"));

        // The default is deliberate and is what keeps the write surface cheap: the guarantee is
        // opt-in because most writes are the only writer, and an agent that CARES can now say so.
        assertThat(shAddressResolver.resolve("post:" + siteName + url).title()).isEqualTo("Second");
    }

    /**
     * SH730 — the ops that take the whole post out from under the reader.
     *
     * <p>SH721 wired `expect` into `post.upsert` because that is the op SH713 measured. The window
     * is not specific to it, and the other three are the more destructive half: an upsert overwrites
     * a field, while a move changes the address a curator just corrected and a delete removes the
     * post they are editing.</p>
     */
    @Test
    void aStaleExpectationRefusesAMoveADeleteAndAPublish() {
        for (String verb : java.util.List.of(ShAgentBatchService.POST_MOVE,
                ShAgentBatchService.POST_DELETE, ShAgentBatchService.POST_PUBLISH)) {
            String url = "/expect-" + verb.replace('.', '-') + "-"
                    + UUID.randomUUID().toString().substring(0, 8);
            run(false, post(url, "/", "First"));
            String read = digestOf(url);
            run(false, post(url, "/", "Changed underneath"));

            Op op = switch (verb) {
                case ShAgentBatchService.POST_MOVE -> new Op(verb, "post:" + siteName + url,
                        null, null, "post:" + siteName + url + "-moved", null, null, null, null,
                        null, read);
                default -> new Op(verb, "post:" + siteName + url, null, null, null, null, null,
                        null, null, null, read);
            };

            assertThatThrownBy(() -> run(false, op))
                    .as("""
                            %s reaches the same rows post.upsert does, and until SH730 there was                             no way to say `only if unchanged` about any of them.""", verb)
                    .isInstanceOf(ShAgentBatchService.BatchOpException.class)
                    .hasMessageContaining("to be unchanged");
        }
    }

    /**
     * SH730 — publish compares the DRAFT, and the choice is the whole design of this op.
     *
     * <p>Publishing is not a content change: matching `expect` against the PUBLISHED row would
     * refuse precisely the case publishing exists for — a draft that differs from what is live. The
     * draft is also what an ordinary read returns, so the digest a caller already holds works here
     * without a second, state-scoped read.</p>
     */
    @Test
    void publishComparesTheDraftAndNotThePublishedRow() {
        String url = "/expect-publish-ok-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, post(url, "/", "First"));
        run(false, new Op(ShAgentBatchService.POST_PUBLISH, "post:" + siteName + url,
                null, null, null, null, null, null, null, null, null));

        // The draft now differs from the published row, which is the ordinary pre-publish state.
        run(false, post(url, "/", "Second"));
        String draftDigest = digestOf(url);

        ShAgentChangeSet result = run(false, new Op(ShAgentBatchService.POST_PUBLISH,
                "post:" + siteName + url, null, null, null, null, null, null, null, null,
                draftDigest));

        assertThat(only(result).action())
                .as("""
                        a digest taken from the draft publishes it. Compared against the published                         row this would refuse every publish that changes anything, which is all of                         them.""")
                .isEqualTo(Change.PUBLISH);
    }

    /** The digest a read hands back, which is the only place `expect` may come from. */
    private String digestOf(String url) {
        return new TransactionTemplate(transactionManager).execute(status ->
                shAgentReadService.read(List.of("post:" + siteName + url), List.of(), false,
                                false, null, true)
                        .get(0).digest());
    }

    /**
     * SH733 — an agent could fill the trash and never empty it.
     *
     * <h2>The half that is not about row growth</h2>
     *
     * <p>`post.delete` and `folder.delete` trash, and <b>purge is what fires the {@code DELETE}
     * lifecycle event</b>: `ShStaticFileCleanupListener` releases an uploaded file's bytes on it and
     * the SH12 webhook is delivered on it. `site.delete` gained `data.purge` in SH648 and the two
     * ops an agent actually uses did not, so on an agent-driven instance every deleted asset kept
     * its bytes for ever and no downstream system was ever told the post went — while the delete
     * looked complete from every surface an agent can read.</p>
     */
    @Test
    void aPurgingDeleteRemovesTheRowAndAPlainOneKeepsIt() {
        String trashed = "/purge-off-" + UUID.randomUUID().toString().substring(0, 8);
        String purged = "/purge-on-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, post(trashed, "/", "Kept"), post(purged, "/", "Gone"));
        String trashedId = idOf(trashed);
        String purgedId = idOf(purged);

        run(false, new Op(ShAgentBatchService.POST_DELETE, "post:" + siteName + trashed,
                null, null, null, null, null));
        run(false, new Op(ShAgentBatchService.POST_DELETE, "post:" + siteName + purged,
                null, null, null, objectMapper.valueToTree(Map.of("purge", true)), null));

        assertThat(rowsFor(trashedId))
                .as("""
                        the default is unchanged: SH55's trash keeps the row so the delete can be                         undone, and that is the behaviour P7 wants.""")
                .isNotZero();
        assertThat(rowsFor(purgedId))
                .as("""
                        and `data.purge` removes it, which is the only path on this surface that                         fires the DELETE event — without it an agent's deleted asset keeps its                         bytes for ever and no webhook says it went (SH733).""")
                .isZero();
    }

    /** Native, because @SQLRestriction hides a trashed row and "gone" must not look like "trashed". */
    private long rowsFor(String postId) {
        return new TransactionTemplate(transactionManager).execute(status -> ((Number) entityManager
                .createNativeQuery("select count(*) from sh_post_unified where id = :id")
                .setParameter("id", postId)
                .getSingleResult()).longValue());
    }

    private String idOf(String url) {
        return new TransactionTemplate(transactionManager).execute(status ->
                shAgentReadService.read(List.of("post:" + siteName + url), List.of(), true,
                        false, null, false).get(0).id());
    }

    private static Op expecting(Op op, String digest) {
        return new Op(op.op(), op.address(), op.type(), op.folder(), op.to(), op.data(),
                op.when(), op.locale(), op.translationOf(), op.url(), digest);
    }

    // --- SH1032: an asset's own metadata, reachable and merged ------------------------------

    /**
     * An uploaded asset, as the upload path leaves one: a PUBLISHED {@code File} post whose
     * {@code data} carries the stamps no caller writes and none may drop.
     */
    private String anAsset() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            ShPostType fileType = entityManager
                    .createQuery("select t from ShPostType t where t.name = :name", ShPostType.class)
                    .setParameter("name", ShSystemPostType.FILE)
                    .getResultStream().findFirst().orElseGet(() -> {
                        ShPostType created = new ShPostType();
                        created.setName(ShSystemPostType.FILE);
                        created.setTitle("File");
                        created.setSystem((byte) 1);
                        created.setDate(Instant.parse("2026-01-01T00:00:00Z"));
                        created.setOwner("admin");
                        entityManager.persist(created);
                        return created;
                    });
            // Two statements, not `f.shSite.name`: navigating past `.id` of an association whose
            // target is an ShObject subclass emits the @TenantId predicate against a subclass alias
            // with no such column — valid JPQL, `Column "ss1_1.tenant_id" not found` at prepare
            // time. This fixture wrote it the short way first and that is exactly what it did.
            ShSite site = entityManager
                    .createQuery("select s from ShSite s where s.name = :name", ShSite.class)
                    .setParameter("name", siteName)
                    .getSingleResult();
            ShFolder root = entityManager
                    .createQuery("select f from ShFolder f where f.shSite = :site", ShFolder.class)
                    .setParameter("site", site)
                    .getResultStream().findFirst().orElseThrow();
            ShPostUnified asset = new ShPostUnified();
            asset.setId(UUID.randomUUID().toString());
            asset.setState(ShPostState.PUBLISHED);
            asset.setShFolder(root);
            // What the upload path writes — ShPostUnifiedWriteService#create sets it from
            // ShFolderSite.of(folder). Without it the resolver refuses the id ("No id is
            // addressable there, for this credential"), because the SH2 allow-list is asked about
            // a site the row does not name. A fixture that omitted it tested a row no upload makes.
            asset.setShSite(site);
            asset.setShPostType(fileType);
            asset.setTitle("hero.jpg");
            asset.setData(STAMPED_ASSET_DATA);
            asset.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            asset.setOwner("admin");
            entityManager.persist(asset);
            return asset.getId();
        });
    }

    /** What an upload stamps and no metadata write may drop. */
    private static final String STAMPED_ASSET_DATA = """
            {"FILE":"hero.jpg","WIDTH":1600,"HEIGHT":900,\
            "SHA256":"deadbeef","SIZE":1234,"CONTENT_TYPE":"image/jpeg"}""";

    private JsonNode assetDataOf(String postId) {
        return new TransactionTemplate(transactionManager).execute(status -> objectMapper.readTree(
                shPostUnifiedRepository.findByIdAndState(postId, ShPostState.PUBLISHED)
                        .orElseThrow().getData()));
    }

    private Op assetMeta(String assetId, Map<String, Object> data) {
        return new Op(ShAgentBatchService.ASSET_META, "id:" + assetId, null, null,
                objectMapper.valueToTree(data));
    }

    @Test
    @DisplayName("SH1032: asset.meta merges, and the stamps the caller never sent survive it")
    void assetMetaMergesRatherThanDeclaringWholeState() {
        String assetId = anAsset();

        Change change = only(run(false, assetMeta(assetId,
                Map.of("alt", "A wide hero", "caption", "Above the fold"))));
        JsonNode after = assetDataOf(assetId);

        assertThat(change.action()).isEqualTo(Change.UPDATE);
        assertThat(after.get("ALT").asString()).isEqualTo("A wide hero");
        assertThat(after.get("CAPTION").asString()).isEqualTo("Above the fold");
        // THE ASSERTION SH1032 EXISTS FOR, and the reason this is an op rather than an upsert.
        // `post.upsert` declares whole state — ShPostUnifiedWriteService#update calls setData with
        // exactly what it was sent — so the shape an agent would reach for first, sending only the
        // key it wants to change, drops five stamps the server wrote: the file name the bytes are
        // served under, the intrinsic size {{#image}} needs to stop the layout shifting, the digest
        // `pull --content` compares against, and the content type the media library filters on.
        // The asset would still be listed, still be referenced, and point at nothing.
        assertThat(after.propertyNames())
                .as("""
                        SH1032: every stamp the caller did not send is still there. An op that \
                        merged two keys and lost FILE would leave a library of assets that look \
                        present and serve 404s — and nothing on this surface would say so, \
                        because a post with no FILE is a valid post.""")
                .contains("FILE", "WIDTH", "HEIGHT", "SHA256", "SIZE", "CONTENT_TYPE");
        assertThat(after.get("FILE").asString()).isEqualTo("hero.jpg");
    }

    @Test
    @DisplayName("SH1032: a blank value clears its key, and clears only that one")
    void assetMetaClearsWithABlank() {
        String assetId = anAsset();
        run(false, assetMeta(assetId, Map.of("alt", "A wide hero", "caption", "Above the fold")));

        run(false, assetMeta(assetId, Map.of("alt", "", "caption", "Above the fold")));
        JsonNode after = assetDataOf(assetId);

        // updateMeta's rule, which is therefore the console's: one writer, one meaning. An op that
        // merged on the console's rules and cleared on its own would be the two surfaces
        // disagreeing about what an empty string means, on the same row, silently.
        assertThat(after.has("ALT")).isFalse();
        assertThat(after.get("CAPTION").asString()).isEqualTo("Above the fold");
        assertThat(after.get("FILE").asString()).isEqualTo("hero.jpg");
    }

    @Test
    @DisplayName("SH1032: against a page it refuses with the op to use instead, on the dry run too")
    void assetMetaRefusesWhatIsNotAnAsset() {
        String base = "/not-an-asset-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, folder(base), post(base + "/page", base.substring(1), "A page"));
        String pageId = idOf(base + "/page");
        Op op = assetMeta(pageId, Map.of("alt", "nope"));

        // Both branches, and the dry run is the one that matters: a plan that reported "would
        // describe this" about a page would be believed, and the refusal would arrive at apply
        // time with the rest of the batch already written (P9).
        for (boolean dryRun : new boolean[] { true, false }) {
            boolean planning = dryRun;
            assertThatThrownBy(() -> run(planning, op))
                    .as("SH1032: dryRun=%s", planning)
                    .isInstanceOf(ShAgentBatchService.BatchOpException.class)
                    .satisfies(thrown -> {
                        var problem = ((ShAgentBatchService.BatchOpException) thrown)
                                .toProblemDetail();
                        assertThat(problem.getType().toString()).endsWith("not-an-asset");
                        // P6: a 4xx carries the fix. Without it the refusal is updateMeta's
                        // NoSuchElementException — "File post not found: <uuid>" about an id the
                        // caller just read back — which reads as an instance fault, not wrong op.
                        assertThat(problem.getProperties())
                                .extractingByKey("fix", as(InstanceOfAssertFactories.STRING))
                                .contains("post.upsert");
                    });
        }
    }

    @Test
    @DisplayName("SH1032: describing an image writes an audit row, naming which keys moved")
    void assetMetaReachesTheChangeFeed() {
        String assetId = anAsset();
        run(false, assetMeta(assetId, Map.of("alt", "A wide hero")));

        List<ShHistory> trail = new TransactionTemplate(transactionManager).execute(status ->
                shHistoryRepository.findByShObjectOrderByDateDesc(assetId, PageRequest.of(0, 5)));

        // `replace` has recorded one since SH210 and this did not, so describing an image was the
        // one content write reaching neither the change feed nor the SH109 review queue. That was
        // invisible while the console was the only caller — a curator who typed the alt text knew
        // they had. An agent is a caller now, and P7 says the curator is never surprised by what
        // one did: an image that acquired a description overnight with nothing in the feed is
        // exactly that surprise.
        assertThat(trail)
                .as("SH1032: the agent's metadata write is in the trail")
                .isNotEmpty();
        assertThat(trail.get(0).getChangedFields())
                .as("""
                        SH1032: and it names ALT rather than saying "metadata" — SH989's overload, \
                        because a reviewer deciding whether to look needs to know which of the \
                        three keys moved.""")
                .contains("ALT");
    }
}
