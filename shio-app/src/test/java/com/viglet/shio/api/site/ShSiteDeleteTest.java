/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.site;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;
import com.viglet.shio.persistence.repository.history.ShHistoryRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH454 — deleting a site that holds content.
 *
 * <h2>What this asserts that nothing did</h2>
 *
 * <p>{@code delete} called {@code shSiteRepository.delete(site)} alone, with a comment explaining
 * that an entity delete is used so the cascade reaches the folders through {@code ShSite#shFolders}.
 * It does. Nothing reached the <b>posts</b>, which hold {@code sh_post_unified.folder_id}, so
 * Hibernate ordered a folder delete against rows that still referenced it and the database answered
 * {@code Referential integrity constraint violation}. The controller catches nothing, so the caller
 * got a <b>500 with a Spring default body</b> — on the console's most destructive button.</p>
 *
 * <p><b>Only an empty site deleted, which is the one nobody deletes.</b> That is the whole reason
 * this went unnoticed: it is trivially easy to write a test that creates a bare site and removes it,
 * and such a test passes against the defect. So the fixture here is a site built by
 * {@link ShSiteWriteService#create}, which clones the SH71 bootstrap template and therefore holds
 * pages, a child folder and posts in both — the shape a curator actually has.</p>
 *
 * <p>It was found by SH448's conformance suite in its own teardown: the run needed to remove the
 * site it had seeded and could not, which is the argument for a suite that drives a live instance.
 * Trashing the posts first is the obvious workaround and does not work — trash is a flag on the
 * row, the FK is still there, and the 500 is identical.</p>
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShSiteDeleteTest {

    private static final List<ShPostState> ALL = List.of(ShPostState.DRAFT, ShPostState.PUBLISHED);

    @Autowired
    private ShSiteWriteService shSiteWriteService;
    @Autowired
    private ShSiteRepository shSiteRepository;
    @Autowired
    private ShHistoryRepository shHistoryRepository;
    @Autowired
    private ShFolderRepository shFolderRepository;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("SH454: a site that holds a post deletes, and takes its folders and posts with it")
    void aSiteWithContentDeletes() {
        ShSite created = shSiteWriteService.create(payload("ut-sh454-"), "admin");
        String siteId = created.getId();
        Content content = contentOf(siteId);
        assertThat(content.postIds())
                .as("the fixture is the shape that broke: a site with posts under it")
                .isNotEmpty();

        assertThatCode(() -> shSiteWriteService.delete(siteId, "admin"))
                .as("the folder delete used to be ordered against posts that still referenced it, "
                        + "so this threw a referential-integrity violation the controller turned "
                        + "into a body-less 500")
                .doesNotThrowAnyException();

        assertThat(shSiteRepository.findById(siteId)).isEmpty();
        // Read back, not inferred: a delete that removed the site row and orphaned its subtree
        // would satisfy the assertion above and leave the content behind.
        assertThat(rowsRemaining("ShFolder", "object_id", content.folderIds()))
                .as("every folder in the subtree went, not only the root")
                .isZero();
        assertThat(rowsRemaining("sh_post_unified", "id", content.postIds()))
                .as("and the posts, through the post writer — a purge, so the rows are gone rather "
                        + "than trashed, which is what the FK needed")
                .isZero();
    }

    @Test
    @DisplayName("SH454: an empty site still deletes, and a site that is not there is not an error")
    void theCasesThatAlreadyWorkedStillWork() {
        ShSite blank = shSiteWriteService.createBlank(payload("ut-sh454-blank-"), "admin");

        assertThat(shSiteWriteService.delete(blank.getId(), "admin")).isTrue();
        assertThat(shSiteRepository.findById(blank.getId())).isEmpty();
        // Absent is `false`, which is what the controller turns into a 404 — never an exception.
        assertThat(shSiteWriteService.delete(UUID.randomUUID().toString(), "admin")).isFalse();
    }

    private static ShSite payload(String prefix) {
        ShSite payload = new ShSite();
        payload.setName(prefix + UUID.randomUUID());
        payload.setDescription("Seeded by ShSiteDeleteTest");
        return payload;
    }

    /** Every folder and post id under a site, read before the delete removes them. */
    private Content contentOf(String siteId) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            ShSite site = shSiteRepository.findById(siteId).orElseThrow();
            List<String> folderIds = new java.util.ArrayList<>();
            List<String> postIds = new java.util.ArrayList<>();
            java.util.Deque<ShFolder> pending = new java.util.ArrayDeque<>(site.getShFolders());
            while (!pending.isEmpty()) {
                ShFolder folder = pending.removeFirst();
                folderIds.add(folder.getId());
                for (ShPostUnified post : shPostUnifiedRepository.findByShFolderAndStateIn(folder, ALL)) {
                    postIds.add(post.getId());
                }
                pending.addAll(shFolderRepository.findByParentFolder(folder));
            }
            return new Content(List.copyOf(folderIds), List.copyOf(postIds));
        });
    }

    /**
     * How many of those ids the table still holds.
     *
     * <p>Native, and deliberately: {@code @SQLRestriction} hides trashed rows from every repository
     * read, so "gone" and "in the trash" look identical through JPA — and the workaround this
     * replaces was precisely trashing the posts and finding the FK still there.</p>
     */
    private long rowsRemaining(String table, String idColumn, List<String> ids) {
        if (ids.isEmpty()) {
            return 0;
        }
        return new TransactionTemplate(transactionManager).execute(status -> ((Number) entityManager
                .createNativeQuery("select count(*) from " + table + " where " + idColumn + " in (:ids)")
                .setParameter("ids", ids)
                .getSingleResult()).longValue());
    }

    private record Content(List<String> folderIds, List<String> postIds) {
    }

    @Test
    @DisplayName("SH223: deleting a site writes the row that says the site went")
    void theSiteItselfIsAudited() {
        // The largest deletion in the product was the only one that left nothing behind saying it
        // happened. Its POSTS were audited (SH51) and its FOLDERS were (SH200) — one and two
        // levels down — so a reader of the SH88 feed saw every page of a site leave and nothing
        // saying the site had. That is an inference the feed exists to remove.
        String name = "sh223-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        ShSite site = new ShSite();
        site.setName(name);
        site.setDate(java.time.Instant.parse("2026-01-01T00:00:00Z"));
        site.setOwner("admin");
        shSiteRepository.save(site);
        String siteId = site.getId();

        shSiteWriteService.delete(siteId, "curator");

        assertThat(shHistoryRepository.findAll())
                .as("""
                        one row, naming the site, written after the delete succeeded — an audit \
                        row for something that did not happen is worse than none (SH223).""")
                .anySatisfy(row -> {
                    assertThat(row.getShObject()).isEqualTo(siteId);
                    assertThat(row.getAction()).isEqualTo("DELETE");
                    assertThat(row.getOwner()).isEqualTo("curator");
                });
    }
}
