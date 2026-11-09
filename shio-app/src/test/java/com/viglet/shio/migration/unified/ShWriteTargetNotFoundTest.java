/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.migration.unified;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.CreateRequest;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.site.ShSite;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.ObjectMapper;

/**
 * SH1380 — a write naming something that does not exist answers 404 with a problem detail.
 *
 * <p>Through the whole chain, because the defect was what a client sees: Spring's default 500 body,
 * which a phone's retry cannot tell from the instance being down. Three requests, one seam
 * ({@link ShPostUnifiedWriteService}), and each must now answer 404 with the id it could not find.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShWriteTargetNotFoundTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private ShPostUnifiedWriteService writeService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private MockMvc mvc;
    private String folderId;
    private String postId;

    @BeforeAll
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String[] typeId = new String[1];
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShSite site = new ShSite();
            site.setName("sh1380-" + suffix);
            site.setDate(when);
            site.setOwner("admin");
            entityManager.persist(site);
            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(when);
            root.setOwner("admin");
            entityManager.persist(root);
            folderId = root.getId();
            ShPostType type = new ShPostType();
            type.setName("Sh1380Type" + suffix);
            type.setTitle("Sh1380Type" + suffix);
            type.setSystem((byte) 0);
            type.setDate(when);
            type.setOwner("admin");
            entityManager.persist(type);
            typeId[0] = type.getId();
        });
        postId = writeService.create(new CreateRequest(folderId, typeId[0],
                new ObjectMapper().createObjectNode().put("TITLE", "Hello"), ShPostState.DRAFT,
                "admin", null, null)).getId();
    }

    private static void assertTeaching404(MvcResult result, String type, String id) throws Exception {
        String body = result.getResponse().getContentAsString();
        assertThat(result.getResponse().getStatus())
                .as("SH1380: a 500 reads as the instance being down, so a client retries what cannot "
                        + "succeed; a missing target is the caller's to fix: " + body)
                .isEqualTo(404);
        assertThat(body).as("SH1380: the problem names what it could not find, and how to look")
                .contains(type).contains(id).contains("fix");
    }

    @Test
    @DisplayName("SH1380: an upload into a folder that does not exist answers 404, not 500")
    void anUploadIntoAnUnknownFolderIs404() throws Exception {
        String missing = UUID.randomUUID().toString();
        MvcResult result = mvc.perform(multipart("/api/v2/staticfile/upload")
                        .file(new MockMultipartFile("file", "note.txt", "text/plain",
                                "hello".getBytes(StandardCharsets.UTF_8)))
                        .param("folderId", missing)
                        .with(user("admin").roles("ADMIN")))
                .andReturn();
        assertTeaching404(result, "no-such-folder", missing);
    }

    @Test
    @DisplayName("SH1380: a create naming a post type that does not exist answers 404, not 500")
    void aCreateWithAnUnknownPostTypeIs404() throws Exception {
        String missing = UUID.randomUUID().toString();
        MvcResult result = mvc.perform(post("/api/v2/post-unified")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"folderId\":\"" + folderId + "\",\"postTypeId\":\"" + missing
                                + "\",\"data\":{\"TITLE\":\"x\"}}")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andReturn();
        assertTeaching404(result, "no-such-post-type", missing);
    }

    @Test
    @DisplayName("SH1380: restoring a revision that was never captured answers 404, not 500")
    void restoringAnUncapturedRevisionIs404() throws Exception {
        MvcResult result = mvc.perform(post("/api/v2/post-unified/" + postId + "/revisions/42/restore")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andReturn();
        assertTeaching404(result, "no-such-revision", postId);
    }
}
