/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.folder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.history.ShContentAction;
import com.viglet.shio.api.post.unified.ShRestoreUrlConflictException;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.CreateRequest;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.history.ShHistory;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.history.ShHistoryRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;
import com.viglet.shio.tenant.ShTenantContext;
import com.viglet.shio.persistence.model.webhook.ShWebhookEventType;
import com.viglet.shio.webhook.ShPostLifecycleEvent;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * SH200 — a folder delete leaves a trace, and can be undone.
 *
 * <p>The cascade used to clear the subtree's posts with
 * {@code shPostUnifiedRepository.deleteAll(...)}, which skipped the SH55 trash, the SH51
 * audit and the {@code DELETE} lifecycle event in one line. The inversion these tests pin:
 * the most destructive operation on the surface must not be the least recoverable and the
 * least observable one.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShFolderTrashTest {

    @Autowired
    private ShFolderWriteService shFolderWriteService;
    @Autowired
    private ShFolderRepository shFolderRepository;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private ShPostUnifiedWriteService shPostUnifiedWriteService;
    @Autowired
    private ShHistoryRepository shHistoryRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ConfigurableApplicationContext applicationContext;

    @PersistenceContext
    private EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<ShPostLifecycleEvent> events = new ArrayList<>();

    private String rootFolderId;
    private String postTypeId;

    @BeforeAll
    void setup() {
        // A record payload is published wrapped in a PayloadApplicationEvent, and the wrapper's
        // generics do not survive a lambda's inferred type — so the listener takes every event
        // and unwraps by hand rather than relying on Spring to resolve one.
        applicationContext.addApplicationListener((ApplicationListener<ApplicationEvent>) event -> {
            if (event instanceof PayloadApplicationEvent<?> payload
                    && payload.getPayload() instanceof ShPostLifecycleEvent lifecycle) {
                events.add(lifecycle);
            }
        });
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            ShSite site = new ShSite();
            site.setName("ut-trash-site-" + UUID.randomUUID());
            site.setUrl("http://trash.example.test");
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
            rootFolderId = root.getId();

            ShPostType postType = new ShPostType();
            postType.setName("PT-TRASH-" + UUID.randomUUID());
            postType.setTitle("Trash Test Post Type");
            postType.setSystem((byte) 0);
            postType.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            postType.setOwner("admin");
            entityManager.persist(postType);
            postTypeId = postType.getId();
        });
    }

    /**
     * The headline: deleting a section trashes it instead of destroying it, and the whole
     * subtree — sub-folders included — goes with it.
     */
    @Test
    void deleteTrashesTheSubtreeInsteadOfRemovingIt() {
        String section = createFolder(rootFolderId, "Blog");
        String nested = createFolder(section, "Archive");
        String top = createPost(section, "Index");
        String deep = createPost(nested, "Old Post");

        assertThat(shFolderWriteService.delete(section, "curator")).isTrue();

        // Gone from every read: the folder no longer resolves and it left the listing.
        assertThat(shFolderRepository.findById(section)).isEmpty();
        assertThat(shFolderRepository.findById(nested)).isEmpty();
        assertThat(childNamesOf(rootFolderId)).doesNotContain("Blog");

        // But the rows are all still there, and the posts are in SH55's trash rather than
        // deleted — which is what makes the gesture undoable at all.
        assertThat(shFolderRepository.findAnyById(section)).isPresent();
        assertThat(trashed(top)).isTrue();
        assertThat(trashed(deep)).isTrue();
    }

    /** SH51: the deletion is recorded — for the section and for every post beneath it. */
    @Test
    void deleteIsAudited() {
        String section = createFolder(rootFolderId, "Recorded");
        String postId = createPost(section, "Recorded Page");

        shFolderWriteService.delete(section, "curator");

        assertThat(auditActions(section)).contains(ShContentAction.DELETE.name());
        assertThat(auditActions(postId)).contains(ShContentAction.DELETE.name());
    }

    /**
     * SH55's rule, kept: a reversible trash fires no lifecycle event, so no webhook is
     * delivered and SH38's byte cleanup does not run until the deletion is final.
     */
    @Test
    void deleteFiresNoLifecycleEventButPurgeDoes() {
        String section = createFolder(rootFolderId, "Eventful");
        String postId = createPost(section, "Eventful Page");

        events.clear();
        shFolderWriteService.delete(section, "curator");
        assertThat(deleteEventsFor(postId)).isEmpty();

        assertThat(shFolderWriteService.purge(section, "admin")).isTrue();
        // This is the event SH38's cleanup listener and the SH12 webhook hang off — the old
        // cascade fired it for nobody, so the stored bytes leaked forever.
        assertThat(deleteEventsFor(postId)).hasSize(1);
        assertThat(shPostUnifiedRepository.findTrashedById(postId, tenantId())).isEmpty();
        assertThat(shFolderRepository.findAnyById(section)).isEmpty();
    }

    /** Restoring the section brings back the folders and the content that went in with it. */
    @Test
    void restoreBringsBackTheWholeSection() {
        String section = createFolder(rootFolderId, "Restorable");
        String nested = createFolder(section, "Nested");
        String postId = createPost(nested, "Nested Page");

        shFolderWriteService.delete(section, "curator");
        assertThat(shFolderWriteService.restore(section, "curator")).isTrue();

        assertThat(shFolderRepository.findById(section)).isPresent();
        assertThat(shFolderRepository.findById(nested)).isPresent();
        assertThat(childNamesOf(rootFolderId)).contains("Restorable");
        assertThat(trashed(postId)).isFalse();
    }

    /**
     * A restore returns what <em>this</em> deletion trashed. A post the curator had already
     * thrown away stays thrown away — the deletion stamped one instant down the subtree, and
     * anything older than that was somebody else's decision.
     */
    @Test
    void restoreLeavesContentThatWasAlreadyInTheTrash() {
        String section = createFolder(rootFolderId, "Mixed");
        String kept = createPost(section, "Kept Page");
        String alreadyGone = createPost(section, "Already Deleted");

        inTx(() -> shPostUnifiedWriteService.delete(alreadyGone, "curator"));
        shFolderWriteService.delete(section, "curator");
        shFolderWriteService.restore(section, "curator");

        assertThat(trashed(kept)).isFalse();
        assertThat(trashed(alreadyGone)).isTrue();
    }

    /**
     * SH243 — one post whose URL was taken refuses the <b>whole</b> section, and restores nothing.
     *
     * <p>A post's restore is refused when a live post holds the friendly URL it would come back to,
     * and this cascade inherits that. The alternative — bring back 49 of 50 and report the 50th — is
     * a partial success the caller has to read carefully to notice, and a curator who asked for
     * their section and got most of it is the reader P7 is written for.</p>
     *
     * <p>The bystander is the point of the test: it has no conflict of its own, and it must still be
     * in the trash afterwards. That is what makes this an all-or-nothing refusal rather than a
     * cascade that stopped where it happened to fail.</p>
     */
    @Test
    @DisplayName("SH243: one occupied URL refuses the whole section, leaving the trash untouched")
    void restoreRefusesTheSubtreeWhenOnePostsUrlWasTaken() {
        String section = createFolder(rootFolderId, "Occupied");
        String nested = createFolder(section, "Deep");
        String collider = createPost(nested, "Careers");
        String bystander = createPost(section, "Team");
        // Explicitly: this class's post-type declares no title field, so the writer has no title
        // to derive an address from and every fixture post would otherwise hold a blank furl —
        // which is legitimately free (an uploaded file never gets one, SH38) and would make the
        // collision this test is about impossible to build.
        shPostUnifiedWriteService.setFurl(collider, "careers");
        String colliderFurl = furlOf(collider);

        shFolderWriteService.delete(section, "curator");

        // Taken while the section slept — by an explicit furl, which is the path that stays
        // reachable now that the naming loop itself sees the trash.
        String occupantId = createPost(rootFolderId, "Jobs");
        titleAs(occupantId, "Jobs");
        shPostUnifiedWriteService.setFurl(occupantId, colliderFurl);

        assertThatThrownBy(() -> shFolderWriteService.restore(section, "curator"))
                .isInstanceOf(ShRestoreUrlConflictException.class)
                .hasMessageContaining("Jobs");

        assertThat(shFolderRepository.findById(section)).isEmpty();
        assertThat(shFolderRepository.findById(nested)).isEmpty();
        assertThat(trashed(collider)).isTrue();
        assertThat(trashed(bystander))
                .as("""
                        the post with no conflict of its own is still in the trash: the refusal is \
                        all-or-nothing, checked before the first row is written rather than left to \
                        the rollback (SH243).""")
                .isTrue();
    }

    /**
     * And the same question asked without throwing — what {@code ShReviewService} needs.
     *
     * <p>It collects a reason per entry it could not undo, and it must not <em>catch</em> this
     * refusal: a throw out of an inner {@code @Transactional} call leaves the shared transaction
     * rollback-only, so the skipped row the queue exists to show would be lost to an
     * {@code UnexpectedRollbackException} at commit — a 500 in place of one honest line.</p>
     */
    @Test
    @DisplayName("SH243: the folder refusal can be asked for as a reason, not only thrown")
    void restoreUrlConflictReportsTheOccupantWithoutThrowing() {
        String section = createFolder(rootFolderId, "Askable");
        String collider = createPost(section, "Support");
        shPostUnifiedWriteService.setFurl(collider, "support");
        String colliderFurl = furlOf(collider);

        shFolderWriteService.delete(section, "curator");
        assertThat(shFolderWriteService.restoreUrlConflict(section))
                .as("nothing holds the URL yet, so there is nothing to report")
                .isEmpty();

        String occupantId = createPost(rootFolderId, "Help Desk");
        titleAs(occupantId, "Help Desk");
        shPostUnifiedWriteService.setFurl(occupantId, colliderFurl);

        assertThat(shFolderWriteService.restoreUrlConflict(section))
                .hasValueSatisfying(reason -> assertThat(reason).contains("Help Desk"));
        assertThat(trashed(collider))
                .as("asking does not restore")
                .isTrue();
    }

    /**
     * Restoring a sub-folder whose parent is still trashed is refused: it would attach live
     * content to a deleted section, which no listing shows — a restore that reads as a no-op.
     */
    @Test
    void restoreRefusesWhenTheParentIsStillTrashed() {
        String section = createFolder(rootFolderId, "Outer");
        String nested = createFolder(section, "Inner");

        shFolderWriteService.delete(section, "curator");

        assertThat(shFolderWriteService.restore(nested, "curator")).isFalse();
        assertThat(shFolderRepository.findById(nested)).isEmpty();
    }

    /** A live folder is not in the trash, so neither restore nor purge will touch it. */
    @Test
    void restoreAndPurgeIgnoreALiveFolder() {
        String section = createFolder(rootFolderId, "Live");

        assertThat(shFolderWriteService.restore(section, "curator")).isFalse();
        assertThat(shFolderWriteService.purge(section, "admin")).isFalse();
        assertThat(shFolderRepository.findById(section)).isPresent();
    }

    /** The trash lists one row per deleted section, not one per folder inside it. */
    @Test
    void trashListsSectionsNotEveryFolderInThem() {
        String section = createFolder(rootFolderId, "Listed");
        String nested = createFolder(section, "Not Listed");

        shFolderWriteService.delete(section, "curator");

        List<String> ids = shFolderWriteService.listTrash().stream().map(ShFolder::getId).toList();
        assertThat(ids).contains(section).doesNotContain(nested);
    }

    // --- fixtures ---

    private List<String> childNamesOf(String parentId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return tx.execute(status -> shFolderRepository
                .findByParentFolderOrderByPositionAsc(entityManager.find(ShFolder.class, parentId))
                .stream().map(ShFolder::getName).toList());
    }

    private boolean trashed(String postId) {
        return !shPostUnifiedRepository.findTrashedById(postId, tenantId()).isEmpty();
    }

    /** The stored friendly URL of a live post — what SH243's refusal is about. */
    private String furlOf(String postId) {
        return shPostUnifiedRepository.findByIdAndState(postId, ShPostState.DRAFT)
                .orElseThrow().getFurl();
    }

    /**
     * Titles a post directly: this class's post-type declares no title field, so {@code create}
     * derives none, and SH243's refusal has to name the occupant — which cannot be asserted against
     * a fixture whose posts are all called "".
     */
    private void titleAs(String postId, String title) {
        inTx(() -> shPostUnifiedRepository.findById(postId).forEach(row -> {
            row.setTitle(title);
            shPostUnifiedRepository.save(row);
        }));
    }

    private List<String> auditActions(String objectId) {
        return shHistoryRepository.findAll().stream()
                .filter(row -> objectId.equals(row.getShObject()))
                .map(ShHistory::getAction)
                .toList();
    }

    private List<ShPostLifecycleEvent> deleteEventsFor(String postId) {
        return events.stream()
                .filter(event -> event.type() == ShWebhookEventType.DELETE)
                .filter(event -> postId.equals(event.postId()))
                .toList();
    }

    private String createFolder(String parentId, String name) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return tx.execute(status -> shFolderWriteService.create(parentId, named(name), "admin")
                .orElseThrow().getId());
    }

    private static ShFolder named(String name) {
        ShFolder folder = new ShFolder();
        folder.setName(name);
        return folder;
    }

    private String createPost(String folderId, String title) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put(ShSystemPostTypeAttr.TITLE, title);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return tx.execute(status -> shPostUnifiedWriteService.create(
                new CreateRequest(folderId, postTypeId, data, ShPostState.DRAFT, "admin", null, null)).getId());
    }

    private void inTx(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
    }

    private static String tenantId() {
        String tenant = ShTenantContext.getTenantId();
        return tenant != null ? tenant : ShTenantContext.DEFAULT_TENANT;
    }
}
