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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH1132 — a rename a curator can do, and the two things it must say before doing it.
 *
 * <p>A post's friendly URL is its identity (SH81) and the console had no writer for one. The only
 * one was {@code post.move} on the agent surface, so a curator who wanted {@code /pricing} to
 * become {@code /plans} had to ask an agent — or create a second post and delete the first, which
 * takes the original's id, its audit trail, its history and every inbound reference with it.</p>
 *
 * <p>Driven rather than read off the source, because the part that matters is behavioural: which
 * rows move, and what the plan says before they do.</p>
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShConsoleRenameTest {

    private static final String RUN = UUID.randomUUID().toString().substring(0, 8);
    private static final String SITE = "sh1132-" + RUN;

    @Autowired
    private ShPostUnifiedMoveService move;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    /** SH1064 — the Redirect system type has to exist before anything can write one. */
    @Autowired
    private com.viglet.shio.render.ShRenderPostTypes shRenderPostTypes;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private final Set<String> posts = new HashSet<>();

    @BeforeAll
    void seed() {
        shRenderPostTypes.ensure();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant then = Instant.parse("2026-01-01T00:00:00Z");
            ShSite site = new ShSite();
            site.setName(SITE);
            site.setDate(then);
            site.setOwner("admin");
            entityManager.persist(site);

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(then);
            root.setOwner("admin");
            entityManager.persist(root);

            ShPostType type = new ShPostType();
            type.setName("PT-SH1132-" + RUN);
            type.setTitle("SH1132 Page");
            type.setSystem((byte) 0);
            type.setDate(then);
            type.setOwner("admin");
            entityManager.persist(type);

            // `/pricing` exists in BOTH states, which is the case the whole task turns on: a rename
            // there moves the live URL at once. `/draft-only` has no published row.
            for (String url : List.of("/pricing", "/draft-only")) {
                for (ShPostState state : url.equals("/pricing")
                        ? List.of(ShPostState.DRAFT, ShPostState.PUBLISHED)
                        : List.of(ShPostState.DRAFT)) {
                    ShPostUnified row = new ShPostUnified();
                    row.setId(SITE + url.replace('/', '-'));
                    row.setState(state);
                    row.setShSite(site);
                    row.setShFolder(root);
                    row.setShPostType(type);
                    row.setTitle(url);
                    row.setFurl(url);
                    row.setLocale("en");
                    row.setOwner("admin");
                    row.setDate(then);
                    row.setData("{\"title\":\"" + url + "\"}");
                    entityManager.persist(row);
                    posts.add(row.getId());
                }
            }
        });
    }

    @AfterAll
    void tearDown() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                posts.forEach(id -> shPostUnifiedRepository.findById(id)
                        .forEach(shPostUnifiedRepository::delete)));
    }

    private String urlOf(String id, ShPostState state) {
        return shPostUnifiedRepository.findByIdAndState(id, state)
                .map(ShPostUnified::getFurl).orElse(null);
    }

    @Test
    @DisplayName("SH1132: a plan says the live URL moves at once, and writes nothing")
    void aPlanSaysWhatItWouldDo() {
        String id = SITE + "-pricing";

        ShPostUnifiedMoveService.MovePlan plan = move.run(id, "/plans", false, true, "ana");

        assertThat(plan.from()).isEqualTo("/pricing");
        assertThat(plan.to()).isEqualTo("/plans");
        assertThat(plan.live())
                .as("""
                        SH1132: both state rows move together, so a rename of a PUBLISHED page \
                        changes the live URL with no publish step. That is what a move means — \
                        moving only the draft would make the post's identity depend on who was \
                        asking — so the plan has to say it, or it is the surprise P7 forbids.""")
                .isTrue();
        assertThat(plan.vacated())
                .as("SH1132: and which address stops answering, because that is what a redirect "
                        + "would be for")
                .isEqualTo("/pricing");
        assertThat(plan.changes().dryRun()).isTrue();
        assertThat(urlOf(id, ShPostState.PUBLISHED))
                .as("SH1132: a plan writes nothing — dryRun defaults to true for this reason and "
                        + "SH1021's, since the request sent by accident must be the harmless one")
                .isEqualTo("/pricing");
    }

    @Test
    @DisplayName("SH1132: the rename moves both rows and keeps the post's id")
    void bothRowsMove() {
        String id = SITE + "-draft-only";

        move.run(id, "/started-here", false, false, "ana");

        assertThat(urlOf(id, ShPostState.DRAFT)).isEqualTo("/started-here");
        assertThat(shPostUnifiedRepository.findById(id))
                .as("""
                        SH1132: keeping the id is the entire content of this operation. The \
                        workaround it replaces — create at the new address, delete the old — \
                        orphans the original and takes its audit trail, its version history and \
                        every inbound reference with it.""")
                .isNotEmpty();
    }

    @Test
    @DisplayName("SH1132: a rename to nothing is refused rather than normalized to the root")
    void anEmptyUrlIsRefused() {
        assertThatThrownBy(() -> move.run(SITE + "-pricing", "  ", false, true, "ana"))
                .as("SH1132: an empty URL normalizes to the site root, which is a different page "
                        + "and almost certainly not what was meant")
                .isInstanceOf(ShPostUnifiedMoveService.NoUrlException.class);
    }
}
