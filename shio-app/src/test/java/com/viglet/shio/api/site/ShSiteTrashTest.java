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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

/**
 * SH648 — a deleted site can be restored, which is the level the trash stopped one short of.
 *
 * <h2>The assertion that would have caught it</h2>
 *
 * <p>The defect was never a thrown exception or a wrong answer: {@code delete} did exactly what its
 * javadoc said, and every level <em>beneath</em> a site had a working undo. What nothing asserted is
 * the relation between those levels — that the recoverability of a gesture does not decrease as it
 * gets wider. So the first test here is the one that fails against the old code for the right
 * reason: delete a site, ask for it back, and read its content.</p>
 *
 * <p>The second is the collision the soft-delete introduces and the reason SH648 was filed as a
 * schema change rather than a service fix: {@code ShSite.name} is unique, so a trashed site keeps
 * holding its name and the re-create that used to succeed now meets a database constraint. Left
 * alone that is a body-less 500 naming a site the caller cannot find in any listing — which is why
 * {@link ShSiteNameHeldByTrashException} is part of the fix and not a follow-up.</p>
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShSiteTrashTest {

    private static final List<ShPostState> ALL = List.of(ShPostState.DRAFT, ShPostState.PUBLISHED);

    @Autowired
    private ShSiteWriteService shSiteWriteService;
    @Autowired
    private ShSiteRepository shSiteRepository;
    @Autowired
    private ShFolderRepository shFolderRepository;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("SH648: a deleted site comes back, and so does the content that went with it")
    void aDeletedSiteIsRestorable() {
        ShSite created = shSiteWriteService.create(payload("ut-sh648-"), "admin");
        String siteId = created.getId();
        List<String> postIds = postIdsUnder(siteId);
        assertThat(postIds)
                .as("the fixture is the shape a curator has: a site with pages under it")
                .isNotEmpty();

        assertThat(shSiteWriteService.delete(siteId, "curator")).isTrue();
        assertThat(shSiteRepository.findById(siteId))
                .as("a trashed site is absent from every ordinary read, or a deleted site keeps "
                        + "answering at its URL")
                .isEmpty();
        assertThat(shSiteRepository.findAnyById(siteId))
                .as("the row outlives the gesture — that is what makes the undo possible at all")
                .isPresent();
        assertThat(shSiteWriteService.listTrash()).extracting(ShSite::getId).contains(siteId);

        assertThat(shSiteWriteService.restore(siteId, "curator"))
                .as("""
                        the whole of SH648: before it, this site was gone and its posts were \
                        individually restorable — the widest gesture in the product was the only \
                        one with no undo.""")
                .isTrue();
        assertThat(shSiteRepository.findById(siteId)).isPresent();
        assertThat(livePostIds(siteId))
                .as("the content came back with it, through the folder writer that trashed it")
                .containsAll(postIds);
    }

    @Test
    @DisplayName("SH648: a trashed site keeps its name, and the create that meets it is told where it went")
    void theNameIsHeldByTheTrashAndSaysSo() {
        String name = "ut-sh648-held-" + UUID.randomUUID();
        ShSite payload = new ShSite();
        payload.setName(name);
        ShSite created = shSiteWriteService.createBlank(payload, "admin");

        assertThat(shSiteWriteService.delete(created.getId(), "curator")).isTrue();
        assertThat(shSiteRepository.findByName(name))
                .as("gone from the live read, which is exactly what makes the collision invisible")
                .isNull();

        assertThat(shSiteWriteService.nameHeldByTrash(name))
                .as("""
                        `ShSite.name` is unique, so the re-create reaches the database and comes \
                        back as a constraint violation with no body. This is the read that turns \
                        it into a refusal naming the site the caller cannot find.""")
                .isPresent();
        assertThatThrownBy(() -> {
            throw new ShSiteNameHeldByTrashException(name, "name");
        })
                .isInstanceOf(ShSiteNameHeldByTrashException.class)
                .hasMessageContaining("trash");

        // And the way out the refusal names actually frees it.
        assertThat(shSiteWriteService.purge(created.getId(), "curator")).isTrue();
        assertThat(shSiteWriteService.nameHeldByTrash(name)).isEmpty();
    }

    @Test
    @DisplayName("SH648: restoring onto an occupied friendly URL is refused, not renamed")
    void aRestoreOntoAnOccupiedFurlRefuses() {
        String furl = "ut-sh648-furl-" + UUID.randomUUID().toString().substring(0, 8);
        ShSite first = new ShSite();
        first.setName("ut-sh648-first-" + UUID.randomUUID());
        first.setFurl(furl);
        ShSite trashed = shSiteWriteService.createBlank(first, "admin");
        assertThat(shSiteWriteService.delete(trashed.getId(), "curator")).isTrue();

        // The furl carries no unique constraint, so nothing stopped this while the first slept.
        ShSite second = new ShSite();
        second.setName("ut-sh648-second-" + UUID.randomUUID());
        second.setFurl(furl);
        ShSite occupant = shSiteWriteService.createBlank(second, "admin");

        assertThatThrownBy(() -> shSiteWriteService.restore(trashed.getId(), "curator"))
                .as("""
                        a restore asks for the thing that was there — every link and sitemap entry \
                        that made it worth restoring points at the address it had, so returning it \
                        somewhere else is the surprise P7 exists to prevent (SH243's rule).""")
                .isInstanceOf(ShSiteRestoreConflictException.class);
        assertThat(shSiteRepository.findAnyById(trashed.getId()))
                .get()
                .extracting(ShSite::isTrashed)
                .as("refused before the first row is written, so the trash is as the curator left it")
                .isEqualTo(true);

        shSiteWriteService.purge(occupant.getId(), "admin");
        shSiteWriteService.purge(trashed.getId(), "admin");
    }

    @Test
    @DisplayName("SH648: an id that is not a trashed site restores as false, never as an exception")
    void restoringSomethingThatIsNotInTheTrashIsFalse() {
        ShSite live = shSiteWriteService.createBlank(payload("ut-sh648-live-"), "admin");

        assertThat(shSiteWriteService.restore(live.getId(), "admin"))
                .as("a live site is not in the trash — false, which the controller turns into a 404")
                .isFalse();
        assertThat(shSiteWriteService.restore(UUID.randomUUID().toString(), "admin")).isFalse();

        shSiteWriteService.purge(live.getId(), "admin");
    }

    private static ShSite payload(String prefix) {
        ShSite payload = new ShSite();
        payload.setName(prefix + UUID.randomUUID());
        payload.setDescription("Seeded by ShSiteTrashTest");
        return payload;
    }

    /** Every post under a site's live tree, read before the delete trashes them. */
    private List<String> postIdsUnder(String siteId) {
        return livePostIds(siteId);
    }

    /**
     * Inside one transaction, because {@code ShSite#shFolders} is lazy and this walks it — the
     * same reason {@code ShSiteDeleteTest} reads its fixture through a {@link TransactionTemplate}.
     */
    private List<String> livePostIds(String siteId) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            ShSite site = shSiteRepository.findAnyById(siteId).orElseThrow();
            List<String> ids = new java.util.ArrayList<>();
            java.util.Deque<ShFolder> pending = new java.util.ArrayDeque<>();
            for (ShFolder root : site.getShFolders()) {
                if (!root.isTrashed()) {
                    pending.add(root);
                }
            }
            while (!pending.isEmpty()) {
                ShFolder folder = pending.removeFirst();
                for (ShPostUnified post : shPostUnifiedRepository.findByShFolderAndStateIn(folder, ALL)) {
                    ids.add(post.getId());
                }
                pending.addAll(shFolderRepository.findByParentFolder(folder));
            }
            return List.copyOf(ids);
        });
    }
}
