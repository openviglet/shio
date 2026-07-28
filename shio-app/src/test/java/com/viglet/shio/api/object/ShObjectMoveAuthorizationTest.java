/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.object;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.acl.ShContentAclService;
import com.viglet.shio.api.acl.ShContentAclService.AclEntry;
import com.viglet.shio.persistence.model.acl.ShAclPermission;
import com.viglet.shio.persistence.model.acl.ShAclPrincipalType;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH201 — the console's move and copy authorize <b>both ends</b>.
 *
 * <p>{@code ShObjectAPI} used to check {@code requireWrite(destId)} and nothing else, so
 * write on any single folder was enough to relocate content out of a subtree the caller could
 * not write, or to copy content they had never been granted read on. The agent path has
 * checked both ends since SH134/SH137, which made this the console being the looser of the
 * two surfaces — Block L's invariant read from the other end.</p>
 *
 * <p>The API is exercised as a bean rather than through MockMvc because the ACL reads the
 * {@code SecurityContextHolder}, and the point of the test is <em>which</em> principal is
 * being resolved.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShObjectMoveAuthorizationTest {

    @Autowired
    private ShObjectAPI shObjectAPI;
    @Autowired
    private ShContentAclService aclService;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    /** Where the caller may write. */
    private String mineId;
    /** Where they may not — somebody else's section. */
    private String theirsId;
    private String theirPostId;

    @BeforeAll
    void setup() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            ShSite site = new ShSite();
            site.setName("ut-acl-move-site-" + UUID.randomUUID());
            site.setUrl("http://acl-move.example.test");
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

            ShFolder mine = child(root, "mine");
            entityManager.persist(mine);
            mineId = mine.getId();

            ShFolder theirs = child(root, "theirs");
            entityManager.persist(theirs);
            theirsId = theirs.getId();

            ShPostType postType = new ShPostType();
            postType.setName("PT-ACLMOVE-" + UUID.randomUUID());
            postType.setTitle("ACL Move Post Type");
            postType.setSystem((byte) 0);
            postType.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            postType.setOwner("admin");
            entityManager.persist(postType);

            ShPostUnified post = new ShPostUnified();
            post.setId(UUID.randomUUID().toString());
            post.setState(ShPostState.DRAFT);
            post.setShSite(site);
            post.setShFolder(theirs);
            post.setShPostType(postType);
            post.setTitle("Their Page");
            post.setOwner("admin");
            post.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            post.setData("{}");
            entityManager.persist(post);
            theirPostId = post.getId();
        });

        // Grants: the caller owns `mine` outright and has no entry at all on `theirs`, whose
        // own grant names somebody else. Without an ACL anywhere SH54 is default-allow, so
        // both folders have to carry one for the test to say anything.
        aclService.replaceAcl(mineId, List.of(
                new AclEntry(ShAclPrincipalType.USER, "alice", ShAclPermission.WRITE)));
        aclService.replaceAcl(theirsId, List.of(
                new AclEntry(ShAclPrincipalType.USER, "bob", ShAclPermission.WRITE)));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    /** The removal half: moving a page out of a subtree the caller cannot write. */
    @Test
    void moveRefusesASourceTheCallerCannotWrite() {
        authAs("alice");

        assertThatThrownBy(() -> inSession(
                () -> shObjectAPI.moveTo(mineId, List.of(theirPostId), null)))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(folderIdOf(theirPostId)).isEqualTo(theirsId);
    }

    /** The disclosure half: a copy is a read of the source, so it needs READ on it. */
    @Test
    void copyRefusesASourceTheCallerCannotRead() {
        authAs("alice");

        assertThatThrownBy(() -> inSession(() -> shObjectAPI.copyTo(mineId, List.of(theirPostId))))
                .isInstanceOf(AccessDeniedException.class);
    }

    /**
     * A batch is refused whole when any one source is forbidden — the checks run over the
     * entire body before the write service starts, rather than relocating the ids that
     * happened to be listed first and failing on the rest.
     */
    @Test
    void aBatchWithOneForbiddenSourceMovesNothing() {
        authAs("alice");
        String mineOwn = createPostIn(mineId, "My Page");

        assertThatThrownBy(() -> inSession(
                () -> shObjectAPI.moveTo(mineId, List.of(mineOwn, theirPostId), null)))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(folderIdOf(theirPostId)).isEqualTo(theirsId);
    }

    /** The grant still works: a caller who may write both ends moves content as before. */
    @Test
    void moveAllowedWhenBothEndsAreWritable() {
        authAs("bob");
        String theirOwn = createPostIn(theirsId, "Bob's Page");

        // bob has WRITE on `theirs`; give him the destination too.
        aclService.replaceAcl(mineId, List.of(
                new AclEntry(ShAclPrincipalType.USER, "alice", ShAclPermission.WRITE),
                new AclEntry(ShAclPrincipalType.USER, "bob", ShAclPermission.WRITE)));
        try {
            inSession(() -> shObjectAPI.moveTo(mineId, List.of(theirOwn), null));
            assertThat(folderIdOf(theirOwn)).isEqualTo(mineId);
        } finally {
            aclService.replaceAcl(mineId, List.of(
                    new AclEntry(ShAclPrincipalType.USER, "alice", ShAclPermission.WRITE)));
        }
    }

    // --- fixtures ---

    private static ShFolder child(ShFolder parent, String name) {
        ShFolder folder = new ShFolder();
        folder.setName(name);
        folder.setRootFolder((byte) 0);
        folder.setParentFolder(parent);
        folder.setDate(Instant.parse("2026-01-01T00:00:00Z"));
        folder.setOwner("admin");
        return folder;
    }

    /**
     * Runs the call with a persistence session open, which is what a real request has:
     * {@code ShContentAclService} walks a folder's lazy parent chain, and in the app that
     * happens inside {@code spring.jpa.open-in-view}'s session. Calling the controller bean
     * straight from a test has none, so without this the ACL walk dies on a proxy before it
     * gets as far as deciding anything.
     */
    private void inSession(Runnable call) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> call.run());
    }

    private void authAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "x", List.of()));
    }

    private String folderIdOf(String postId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return tx.execute(status -> shPostUnifiedRepository.findById(postId).stream()
                .findFirst().orElseThrow().getShFolder().getId());
    }

    private String createPostIn(String folderId, String title) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return tx.execute(status -> {
            ShFolder folder = entityManager.find(ShFolder.class, folderId);
            ShPostUnified sample = shPostUnifiedRepository.findById(theirPostId).stream()
                    .findFirst().orElseThrow();
            ShPostUnified post = new ShPostUnified();
            post.setId(UUID.randomUUID().toString());
            post.setState(ShPostState.DRAFT);
            post.setShSite(sample.getShSite());
            post.setShFolder(folder);
            post.setShPostType(sample.getShPostType());
            post.setTitle(title);
            post.setOwner("admin");
            post.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            post.setData("{}");
            entityManager.persist(post);
            return post.getId();
        });
    }
}
