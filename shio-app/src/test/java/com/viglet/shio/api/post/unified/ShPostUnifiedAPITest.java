/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.unified;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.widget.ShWidgetRepository;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;
import com.viglet.shio.widget.ShSystemWidget;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest
@TestMethodOrder(MethodOrderer.MethodName.class)
@TestInstance(Lifecycle.PER_CLASS)
class ShPostUnifiedAPITest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private ShWidgetRepository shWidgetRepository;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private MockMvc mockMvc;
    private Principal mockPrincipal;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String folderId;
    private String postTypeId;
    private String newPostId;

    @BeforeAll
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        mockPrincipal = Mockito.mock(Principal.class);
        Mockito.when(mockPrincipal.getName()).thenReturn("admin");

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            ShSite site = new ShSite();
            site.setName("ut-site-" + UUID.randomUUID());
            site.setDescription("Unified API Test Site");
            site.setUrl("http://example.test");
            site.setDate(Instant.now());
            site.setOwner("admin");
            entityManager.persist(site);

            ShFolder rootFolder = new ShFolder();
            rootFolder.setName("root");
            rootFolder.setRootFolder((byte) 1);
            rootFolder.setShSite(site);
            rootFolder.setDate(Instant.now());
            rootFolder.setOwner("admin");
            entityManager.persist(rootFolder);
            folderId = rootFolder.getId();

            ShWidget textWidget = entityManager.find(ShWidget.class,
                    shWidgetRepository.findByName(ShSystemWidget.TEXT).getId());

            ShPostType postType = new ShPostType();
            postType.setName("PT-UT-" + UUID.randomUUID());
            postType.setTitle("Unified Test Post Type");
            postType.setDescription("Post type for unified API tests");
            postType.setSystem((byte) 0);
            postType.setDate(Instant.now());
            postType.setOwner("admin");

            ShPostTypeAttr titleAttr = new ShPostTypeAttr();
            titleAttr.setName(ShSystemPostTypeAttr.TITLE);
            titleAttr.setLabel("Title");
            titleAttr.setDescription("Title field");
            titleAttr.setOrdinal(1);
            titleAttr.setRequired((byte) 1);
            titleAttr.setIsTitle((byte) 1);
            titleAttr.setIsSummary((byte) 0);
            titleAttr.setShWidget(textWidget);
            titleAttr.setShPostType(postType);

            Set<ShPostTypeAttr> attrs = new HashSet<>();
            attrs.add(titleAttr);
            postType.setShPostTypeAttrs(attrs);

            entityManager.persist(postType);
            postTypeId = postType.getId();
        });
    }

    @AfterAll
    void tearDown() {
        if (newPostId == null) return;
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status ->
                shPostUnifiedRepository.findById(newPostId).forEach(shPostUnifiedRepository::delete));
    }

    private ObjectNode createPayload(String title, String state) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("folderId", folderId);
        payload.put("postTypeId", postTypeId);
        payload.put("state", state);
        ObjectNode data = payload.putObject("data");
        data.put(ShSystemPostTypeAttr.TITLE, title);
        return payload;
    }

    @Test
    void stage01PostUnifiedListByFolderEmpty() throws Exception {
        mockMvc.perform(get("/api/v2/post-unified/folder/" + folderId)
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void stage02PostUnifiedCreateDraft() throws Exception {
        String body = objectMapper.writeValueAsString(createPayload("Hello", "DRAFT"));

        mockMvc.perform(post("/api/v2/post-unified")
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        newPostId = shPostUnifiedRepository.findByState(ShPostState.DRAFT).stream()
                .filter(p -> p.getShFolder() != null && folderId.equals(p.getShFolder().getId()))
                .findFirst()
                .map(ShPostUnified::getId)
                .orElseThrow(() -> new AssertionError("Created post not found in repository"));
    }

    @Test
    void stage03PostUnifiedGetDraft() throws Exception {
        mockMvc.perform(get("/api/v2/post-unified/" + newPostId)
                        .param("state", "DRAFT")
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.id").value(newPostId))
                .andExpect(jsonPath("$.state").value("DRAFT"));
    }

    @Test
    void stage04PostUnifiedListByFolder() throws Exception {
        mockMvc.perform(get("/api/v2/post-unified/folder/" + folderId)
                        .param("state", "DRAFT")
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(newPostId));
    }

    @Test
    void stage05PostUnifiedUpdate() throws Exception {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("state", "DRAFT");
        ObjectNode data = payload.putObject("data");
        data.put(ShSystemPostTypeAttr.TITLE, "Hello Updated");

        mockMvc.perform(put("/api/v2/post-unified/" + newPostId)
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Hello Updated"));
    }

    @Test
    void stage06PostUnifiedPublish() throws Exception {
        mockMvc.perform(post("/api/v2/post-unified/" + newPostId + "/publish")
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PUBLISHED"))
                .andExpect(jsonPath("$.published").value(true));
    }

    @Test
    void stage07PostUnifiedVersions() throws Exception {
        mockMvc.perform(get("/api/v2/post-unified/" + newPostId + "/versions")
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void stage07bPostUnifiedRestore() throws Exception {
        // Diverge the draft from the published snapshot...
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("state", "DRAFT");
        ObjectNode data = payload.putObject("data");
        data.put(ShSystemPostTypeAttr.TITLE, "Draft Pending Change");

        mockMvc.perform(put("/api/v2/post-unified/" + newPostId)
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Draft Pending Change"));

        // ...then restore the draft to the last published version.
        mockMvc.perform(post("/api/v2/post-unified/" + newPostId + "/restore")
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("DRAFT"))
                .andExpect(jsonPath("$.published").value(false))
                .andExpect(jsonPath("$.title").value("Hello Updated"));

        mockMvc.perform(get("/api/v2/post-unified/" + newPostId)
                        .param("state", "DRAFT")
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Hello Updated"));
    }

    @Test
    void stage07cPostUnifiedRestoreUnknownReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/v2/post-unified/" + UUID.randomUUID() + "/restore")
                        .principal(mockPrincipal))
                .andExpect(status().isNotFound());
    }

    @Test
    void stage08PostUnifiedUnpublish() throws Exception {
        mockMvc.perform(post("/api/v2/post-unified/" + newPostId + "/unpublish")
                        .principal(mockPrincipal))
                .andExpect(status().isNoContent());
    }

    @Test
    void stage09PostUnifiedDelete() throws Exception {
        mockMvc.perform(delete("/api/v2/post-unified/" + newPostId)
                        .principal(mockPrincipal))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v2/post-unified/" + newPostId)
                        .param("state", "DRAFT")
                        .principal(mockPrincipal))
                .andExpect(status().isNotFound());
    }
}
