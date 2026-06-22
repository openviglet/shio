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

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

        assertThat(writeService.purge(postId)).isTrue();

        assertThat(inTrash(postId)).isFalse();
        assertThat(shPostUnifiedRepository.findById(postId)).isEmpty();
        // A second purge is a no-op (already gone).
        assertThat(writeService.purge(postId)).isFalse();
    }
}
