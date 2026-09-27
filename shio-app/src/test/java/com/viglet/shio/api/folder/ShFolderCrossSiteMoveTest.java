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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
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

import com.viglet.shio.api.object.ShObjectWriteService;
import com.viglet.shio.object.ShFolderSite;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH1137 — a folder cannot cross sites, and the console is the caller that could.
 *
 * <h2>What was wrong, found by a run and not by reading</h2>
 *
 * <p>A folder holding a published post was moved with {@code PUT /api/v2/object/moveto/{root}} from
 * one site's root into another's. <b>The move succeeded.</b> The folder's parent became the new
 * site's root and every post beneath it kept the old site in {@code shSite} — so delivery, which
 * resolves a page by {@code findByShSite_IdAndFurlAndState}, went on answering it under the old
 * site while the tree showed it under the new one. That is the split the {@code ShFolderSite}
 * invariant exists to prevent, reached from the other direction.</p>
 *
 * <h2>The agent already refused it, which is the whole finding</h2>
 *
 * <p>§LXXII.25 supposed the agent's {@code folder.move} "probably has the same hole" and said to
 * check before assuming. It does not: it throws {@code CrossSiteMoveException} on the address, and
 * its own docstring says the other refusals live on the shared writer "because the console can
 * reach them too". This one did not. A refusal that lives on one caller is a refusal the other
 * caller does not have — so it moved to the writer, where the other two already were.</p>
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShFolderCrossSiteMoveTest {

    private static final String RUN = UUID.randomUUID().toString().substring(0, 8);
    private static final String HOME = "sh1137-home-" + RUN;
    private static final String AWAY = "sh1137-away-" + RUN;

    @Autowired
    private ShFolderWriteService shFolderWriteService;
    @Autowired
    private ShObjectWriteService shObjectWriteService;
    @Autowired
    private ShFolderRepository shFolderRepository;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String homeRootId;
    private String awayRootId;
    private String sectionId;
    private String siblingId;
    private String postId;

    @BeforeAll
    void seed() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant then = Instant.parse("2026-01-01T00:00:00Z");
            ShSite home = site(HOME, then);
            ShSite away = site(AWAY, then);
            ShFolder homeRoot = root(home, then);
            ShFolder awayRoot = root(away, then);
            homeRootId = homeRoot.getId();
            awayRootId = awayRoot.getId();

            // A section under HOME, with a published post inside it, and a sibling to move into.
            ShFolder section = child("section", homeRoot, then);
            ShFolder sibling = child("sibling", homeRoot, then);
            sectionId = section.getId();
            siblingId = sibling.getId();

            ShPostType type = new ShPostType();
            type.setName("PT-SH1137-" + RUN);
            type.setTitle("SH1137 Page");
            type.setSystem((byte) 0);
            type.setDate(then);
            type.setOwner("admin");
            entityManager.persist(type);

            ShPostUnified post = new ShPostUnified();
            post.setId("sh1137-post-" + RUN);
            post.setState(ShPostState.PUBLISHED);
            post.setShSite(home);
            post.setShFolder(section);
            post.setShPostType(type);
            post.setTitle("Inside");
            post.setFurl("/inside");
            post.setLocale("en");
            post.setOwner("admin");
            post.setDate(then);
            post.setData("{\"title\":\"Inside\"}");
            entityManager.persist(post);
            postId = post.getId();
        });
    }

    private ShSite site(String name, Instant then) {
        ShSite site = new ShSite();
        site.setName(name);
        site.setDate(then);
        site.setOwner("admin");
        entityManager.persist(site);
        return site;
    }

    private ShFolder root(ShSite site, Instant then) {
        ShFolder folder = new ShFolder();
        folder.setName("root");
        folder.setRootFolder((byte) 1);
        folder.setShSite(site);
        folder.setDate(then);
        folder.setOwner("admin");
        entityManager.persist(folder);
        return folder;
    }

    private ShFolder child(String name, ShFolder parent, Instant then) {
        ShFolder folder = new ShFolder();
        folder.setName(name);
        folder.setRootFolder((byte) 0);
        folder.setParentFolder(parent);
        folder.setDate(then);
        folder.setOwner("admin");
        entityManager.persist(folder);
        return folder;
    }

    @Test
    @DisplayName("SH1137: the console's move refuses a folder crossing into another site")
    void theConsolePathRefuses() {
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> shObjectWriteService.moveTo(awayRootId, List.of(sectionId), "ana")))
                .as("""
                        SH1137: this move used to SUCCEED. The folder's parent became the other \
                        site's root and every post beneath it kept the old site in shSite, so \
                        delivery went on answering them under the old site while the tree showed \
                        them under the new one — and requireUrlFree never runs for posts beneath a \
                        folder, so the destination could end up with two posts at one URL.""")
                .isInstanceOf(ShFolderMoveException.CrossSite.class);
    }

    @Test
    @DisplayName("SH1137: the refusal names both sites and the verbs that do carry content across")
    void theRefusalTeaches() {
        ShFolderMoveException.CrossSite refusal =
                new ShFolderMoveException.CrossSite("section", HOME, AWAY);

        assertThat(refusal.getMessage())
                .as("SH1137: a refusal that does not name where the folder is and where it was "
                        + "going leaves the curator to guess which of the two is wrong")
                .contains(HOME).contains(AWAY);
        assertThat(String.valueOf(refusal.toProblemDetail().getProperties()))
                .as("SH1137: refusing is only half an answer — carrying content between sites is "
                        + "what export and import are for, and the refusal has to say so (P6)")
                .contains("shio export").contains("shio import");
    }

    @Test
    @DisplayName("SH1137: a move within the site still works, and the post keeps its site")
    void aMoveInsideTheSiteStillWorks() {
        // The guard must refuse the cross-site case and nothing else. A `requireSameSite` that read
        // the FIELD rather than walking (SH128) would call this cross-site too: only a root folder
        // carries shSite, so a nested folder's own is null.
        assertThatCode(() -> new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> shFolderWriteService.move(sectionId, parent(siblingId), null, "ana")))
                .doesNotThrowAnyException();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShFolder moved = shFolderRepository.findById(sectionId).orElseThrow();
            assertThat(moved.getParentFolder().getId()).isEqualTo(siblingId);
            assertThat(ShFolderSite.of(moved).getName())
                    .as("SH1137: the walk still finds the site, which is what makes the guard able "
                            + "to tell a move within one from a move between two")
                    .isEqualTo(HOME);
            assertThat(shPostUnifiedRepository.findById(postId))
                    .allSatisfy(row -> assertThat(row.getShSite().getName())
                            .as("SH1137: and the post's own site is untouched by a move inside it")
                            .isEqualTo(HOME));
        });
    }

    private ShFolder parent(String id) {
        return shFolderRepository.findById(id).orElseThrow();
    }
}
