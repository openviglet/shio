/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.acl;

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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.acl.ShContentAclService.AclEntry;
import com.viglet.shio.persistence.model.acl.ShAclPermission;
import com.viglet.shio.persistence.model.acl.ShAclPrincipalType;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH54 ACL resolution: default-allow, explicit grant (deny non-matching),
 * folder-tree inheritance, READ vs WRITE, and admin bypass.
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShContentAclServiceTest {

    @Autowired
    private ShContentAclService aclService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String folderId;
    private String subFolderId;
    private String postId;

    @BeforeAll
    void setup() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(s -> {
            ShSite site = new ShSite();
            site.setName("acl-site-" + UUID.randomUUID());
            site.setUrl("http://acl.example.test");
            site.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            site.setOwner("admin");
            entityManager.persist(site);

            ShFolder folder = new ShFolder();
            folder.setName("parent");
            folder.setRootFolder((byte) 1);
            folder.setShSite(site);
            folder.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            folder.setOwner("admin");
            entityManager.persist(folder);
            folderId = folder.getId();

            ShFolder sub = new ShFolder();
            sub.setName("child");
            sub.setRootFolder((byte) 0);
            sub.setParentFolder(folder);
            sub.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            sub.setOwner("admin");
            entityManager.persist(sub);
            subFolderId = sub.getId();

            ShPostType pt = new ShPostType();
            pt.setName("PT-ACL-" + UUID.randomUUID());
            pt.setTitle("ACL Test Post Type");
            pt.setSystem((byte) 0);
            pt.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            pt.setOwner("admin");
            entityManager.persist(pt);

            ShPostUnified post = new ShPostUnified();
            post.setId(UUID.randomUUID().toString());
            post.setState(ShPostState.DRAFT);
            post.setShSite(site);
            post.setShFolder(sub);
            post.setShPostType(pt);
            post.setTitle("ACL Post");
            post.setOwner("admin");
            post.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            post.setData("{}");
            entityManager.persist(post);
            postId = post.getId();
        });
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        // Drop any ACL set during the test so cases stay independent.
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(s -> aclService.replaceAcl(folderId, List.of()));
    }

    private void authAs(String username, String... roles) {
        var authorities = java.util.Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "x", authorities));
    }

    @Test
    void defaultAllowWhenNoAcl() {
        authAs("alice");
        assertThat(aclService.canRead(folderId)).isTrue();
        assertThat(aclService.canWrite(folderId)).isTrue();
    }

    @Test
    void explicitGrantDeniesNonMatchingUser() {
        aclService.replaceAcl(folderId, List.of(
                new AclEntry(ShAclPrincipalType.USER, "bob", ShAclPermission.WRITE)));
        authAs("alice");
        assertThat(aclService.canRead(folderId)).isFalse();
        assertThatThrownBy(() -> aclService.requireWrite(folderId))
                .isInstanceOf(AccessDeniedException.class);

        authAs("bob");
        assertThat(aclService.canWrite(folderId)).isTrue();
    }

    @Test
    void readGrantDoesNotConferWrite() {
        aclService.replaceAcl(folderId, List.of(
                new AclEntry(ShAclPrincipalType.USER, "alice", ShAclPermission.READ)));
        authAs("alice");
        assertThat(aclService.canRead(folderId)).isTrue();
        assertThat(aclService.canWrite(folderId)).isFalse();
    }

    @Test
    void aclInheritsDownTheFolderTreeToSubfolderAndPost() {
        aclService.replaceAcl(folderId, List.of(
                new AclEntry(ShAclPrincipalType.USER, "bob", ShAclPermission.READ)));
        authAs("alice");
        // The subfolder and the post in it have no ACL of their own → inherit the parent's.
        assertThat(aclService.canRead(subFolderId)).isFalse();
        assertThat(aclService.canRead(postId)).isFalse();

        authAs("bob");
        assertThat(aclService.canRead(subFolderId)).isTrue();
        assertThat(aclService.canRead(postId)).isTrue();
    }

    @Test
    void sh995AWriteGrantSavesAndDoesNotPublish() {
        // SH995's assertion, and the separation the product had no way to express: a curator who may
        // edit this branch of the tree is not automatically the one who decides it goes live.
        aclService.replaceAcl(folderId, List.of(
                new AclEntry(ShAclPrincipalType.USER, "alice", ShAclPermission.WRITE),
                new AclEntry(ShAclPrincipalType.USER, "bob", ShAclPermission.PUBLISH)));

        authAs("alice");
        assertThat(aclService.canWrite(postId))
                .as("SH995: the draft is still hers to save — this is a level above write, not a "
                        + "narrower write")
                .isTrue();
        assertThat(aclService.canPublish(postId)).isFalse();
        assertThatThrownBy(() -> aclService.requirePublish(postId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("the draft is saved");

        authAs("bob");
        assertThat(aclService.canPublish(postId)).isTrue();
        assertThat(aclService.canWrite(postId))
                .as("SH995: PUBLISH implies WRITE, so an approver is not locked out of the page "
                        + "they are approving")
                .isTrue();
    }

    @Test
    void sh995TheConsoleIsToldWhatItMayDoAndWhyNot() {
        // What GET /v2/object/{id}/allowed answers, which is how the console disables Publish rather
        // than offering it and producing a 403 after the click.
        aclService.replaceAcl(folderId, List.of(
                new AclEntry(ShAclPrincipalType.USER, "alice", ShAclPermission.WRITE)));

        authAs("alice");
        ShContentAclService.Allowed allowed = aclService.allowed(postId);

        assertThat(allowed.write()).isTrue();
        assertThat(allowed.publish()).isFalse();
        assertThat(allowed.publishHint())
                .as("SH995: the console shows the server's own sentence — a console that wrote its "
                        + "own would be a second authorisation story, and only one is enforced")
                .isEqualTo("the draft is saved, and somebody granted PUBLISH here can put it live");
        assertThatThrownBy(() -> aclService.requirePublish(postId))
                .hasMessageContaining(allowed.publishHint());

        aclService.replaceAcl(folderId, List.of(
                new AclEntry(ShAclPrincipalType.USER, "alice", ShAclPermission.PUBLISH)));
        assertThat(aclService.allowed(postId).publishHint())
                .as("SH995: a hint beside a permitted action is something a reader has to work out "
                        + "is not about them")
                .isNull();
    }

    @Test
    void adminBypassesAnyAcl() {
        aclService.replaceAcl(folderId, List.of(
                new AclEntry(ShAclPrincipalType.USER, "bob", ShAclPermission.READ)));
        authAs("root", "ROLE_ADMIN");
        assertThat(aclService.canWrite(folderId)).isTrue();
        assertThat(aclService.canWrite(postId)).isTrue();
    }
}
