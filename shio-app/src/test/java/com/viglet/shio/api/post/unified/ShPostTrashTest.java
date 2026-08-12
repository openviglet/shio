/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.unified;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.CreateRequest;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.history.ShHistoryRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * SH55 trash / soft-delete: a deleted post is hidden from normal reads (the
 * {@code @SQLRestriction}) but listed in the trash and restorable; purge removes
 * it permanently.
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShPostTrashTest {

    @Autowired
    private ShPostUnifiedWriteService writeService;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private ShHistoryRepository shHistoryRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String folderId;
    private String postTypeId;

    @BeforeAll
    void setup() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(s -> {
            ShSite site = new ShSite();
            site.setName("trash-site-" + UUID.randomUUID());
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
            folderId = root.getId();

            ShPostType pt = new ShPostType();
            pt.setName("PT-TRASH-" + UUID.randomUUID());
            pt.setTitle("Trash Test Post Type");
            pt.setSystem((byte) 0);
            pt.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            pt.setOwner("admin");
            entityManager.persist(pt);
            postTypeId = pt.getId();
        });
    }

    private String createPost(String title) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("title", title);
        return writeService.create(new CreateRequest(
                folderId, postTypeId, data, ShPostState.DRAFT, "admin", null, null)).getId();
    }

    private boolean inTrash(String postId) {
        return writeService.listTrash().stream().anyMatch(p -> p.getId().equals(postId));
    }

    /** The stored friendly URL of a live post — SH243 asserts a restore returns it to this one. */
    private String furlOf(String postId) {
        return shPostUnifiedRepository.findByIdAndState(postId, ShPostState.DRAFT)
                .orElseThrow().getFurl();
    }

    /**
     * Titles a post directly, because this class's post-type declares no title field and
     * {@code create} therefore derives none — and SH243's refusal has to name the occupant, which
     * cannot be asserted against a fixture whose posts are all called "".
     */
    private void titleAs(String postId, String title) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                shPostUnifiedRepository.findById(postId).forEach(row -> {
                    row.setTitle(title);
                    shPostUnifiedRepository.save(row);
                }));
    }

    @Test
    void softDeleteHidesFromReadsButKeepsInTrash() {
        String postId = createPost("Doomed");
        assertThat(shPostUnifiedRepository.findByIdAndState(postId, ShPostState.DRAFT)).isPresent();

        writeService.delete(postId, "admin");

        // @SQLRestriction hides it from every normal read...
        assertThat(shPostUnifiedRepository.findByIdAndState(postId, ShPostState.DRAFT)).isEmpty();
        assertThat(shPostUnifiedRepository.findByState(ShPostState.DRAFT))
                .noneMatch(p -> p.getId().equals(postId));
        // ...but it is recoverable from the trash.
        assertThat(inTrash(postId)).isTrue();
    }

    @Test
    void restoreBringsItBack() {
        String postId = createPost("Reprieve");
        writeService.delete(postId, "admin");
        assertThat(inTrash(postId)).isTrue();

        assertThat(writeService.restoreFromTrash(postId, "admin")).isTrue();

        assertThat(shPostUnifiedRepository.findByIdAndState(postId, ShPostState.DRAFT)).isPresent();
        assertThat(inTrash(postId)).isFalse();
    }

    @Test
    void purgeRemovesPermanently() {
        String postId = createPost("Gone");
        writeService.delete(postId, "admin");
        assertThat(inTrash(postId)).isTrue();

        assertThat(writeService.purge(postId, "admin")).isTrue();

        assertThat(inTrash(postId)).isFalse();
        assertThat(shPostUnifiedRepository.findById(postId)).isEmpty();
        // A second purge is a no-op (already gone).
        assertThat(writeService.purge(postId, "admin")).isFalse();
    }

    /**
     * SH243 — a restore asks for <em>the thing that was there</em>, so an occupied URL is refused.
     *
     * <p>{@code ShFurlNaming} now sees the trash, so a <em>copy</em> can no longer wander onto a
     * sleeping URL. This is the other way in, and it stays reachable however good the naming loop
     * gets: the URL can be taken by an explicit {@code furl}, a rename or a cross-site move while
     * the post sleeps. Restoring anyway is what produced the {@code duplicate-url} error out of two
     * individually correct operations.</p>
     *
     * <p>The alternative — hand it back at {@code contact-2}, the way a copy is numbered — is what
     * P7 calls a surprise: the curator's link, the sitemap entry and the search result all name the
     * URL they are asking to get back. So the refusal names the occupant and leaves the choice to
     * the human, and the post stays in the trash where they left it.</p>
     */
    @Test
    @DisplayName("SH243: a restore onto an occupied URL is refused, naming the occupant")
    void restoreIsRefusedWhenALivePostTookItsUrl() {
        // `setFurl` rather than a derived slug, twice over: this class's post-type declares no
        // title field, so the writer has no title to derive an address from — and an explicit furl
        // is also the path that stays reachable now that the naming loop itself sees the trash.
        String sleepingId = createPost("Contact");
        writeService.setFurl(sleepingId, "contact");
        writeService.delete(sleepingId, "curator");

        String occupantId = createPost("Front Desk");
        titleAs(occupantId, "Front Desk");
        writeService.setFurl(occupantId, "contact");

        assertThatThrownBy(() -> writeService.restoreFromTrash(sleepingId, "curator"))
                .as("""
                        refused rather than renamed, and the refusal teaches: P6 wants the \
                        occupant named, because "some post holds this" is not something a curator \
                        can act on (SH243).""")
                .isInstanceOf(ShRestoreUrlConflictException.class)
                .satisfies(refusal -> {
                    ProblemDetail problem =
                            ((ShRestoreUrlConflictException) refusal).toProblemDetail();
                    assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
                    assertThat(problem.getDetail()).contains("Front Desk").contains("/contact");
                    assertThat(problem.getProperties()).containsKey("fix");
                });

        assertThat(inTrash(sleepingId))
                .as("a refused restore changes nothing — the post is still where the curator left it")
                .isTrue();
    }

    /**
     * SH243's other half of the same rule: with the URL free again, the same restore goes through
     * and returns the post to <b>its own</b> address.
     *
     * <p>Here because a refusal nobody can clear is a dead end rather than an instruction, and the
     * {@code fix} the 409 carries says to clear it. This asserts the fix works.</p>
     */
    @Test
    @DisplayName("SH243: clearing the occupant makes the same restore succeed at the same URL")
    void restoreSucceedsOnceTheUrlIsFreedAgain() {
        String sleepingId = createPost("Pricing");
        writeService.setFurl(sleepingId, "pricing");
        String storedFurl = furlOf(sleepingId);
        writeService.delete(sleepingId, "curator");

        String occupantId = createPost("Rates");
        writeService.setFurl(occupantId, storedFurl);
        assertThatThrownBy(() -> writeService.restoreFromTrash(sleepingId, "curator"))
                .isInstanceOf(ShRestoreUrlConflictException.class);

        // The fix the 409 names: move the occupant off the address.
        writeService.setFurl(occupantId, "rates");

        assertThat(writeService.restoreFromTrash(sleepingId, "curator")).isTrue();
        assertThat(furlOf(sleepingId))
                .as("its own URL, not a numbered one — that is the whole reason it refused")
                .isEqualTo(storedFurl);
    }

    @Test
    @DisplayName("SH224: a purge writes the row that says who destroyed it")
    void aPurgeIsAudited() {
        // The one write nobody recorded. `purge` fired the DELETE lifecycle event — so the webhook
        // went and the stored bytes were removed — and wrote no audit row at all. The REVERSIBLE
        // half of the trash was fully recorded and the irreversible half recorded nothing, which is
        // backwards: "who destroyed this, and when" is a question about the deletion that cannot be
        // undone.
        String postId = createPost("Doomed");
        writeService.delete(postId, "curator");

        assertThat(writeService.purge(postId, "curator")).isTrue();

        assertThat(shHistoryRepository.findAll())
                .as("""
                        the row names the post that no longer exists, which SH88 already knows how \
                        to carry: an entry whose object is gone travels with its title and a null \
                        address. That is the shape, and the reason the row is worth having despite \
                        naming something unreachable (SH224).""")
                .anySatisfy(row -> {
                    assertThat(row.getShObject()).isEqualTo(postId);
                    assertThat(row.getAction()).isEqualTo("PURGE");
                    assertThat(row.getOwner()).isEqualTo("curator");
                    // The verb, not the title. Whether this fixture's post carries an entity
                    // title is the fixture's business — what SH224 claims is that the row says a
                    // DESTRUCTION happened, in words a reader of the feed can act on.
                    assertThat(row.getDescription()).startsWith("Destroyed");
                });
    }

    @Test
    @DisplayName("SH224: a purge is its own action, not a second DELETE")
    void aPurgeIsNotADelete() {
        // The two states a reader most needs to tell apart. A caller walking /agent/changes saw a
        // post enter the trash and never learned it was gone for good; folding the two would make
        // the feed say a post left twice and never say which time was final.
        String postId = createPost("Also doomed");
        writeService.delete(postId, "curator");
        writeService.purge(postId, "curator");

        assertThat(shHistoryRepository.findAll())
                .filteredOn(row -> postId.equals(row.getShObject()))
                .as("one DELETE for the trash and one PURGE for the destruction, in that order")
                .extracting(row -> row.getAction())
                .containsSubsequence("DELETE", "PURGE");
    }

}
