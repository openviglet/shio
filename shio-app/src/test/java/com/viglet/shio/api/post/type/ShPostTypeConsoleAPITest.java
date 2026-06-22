/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.type;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

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

import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.CreateRequest;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.persistence.repository.widget.ShWidgetRepository;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;
import com.viglet.shio.widget.ShSystemWidget;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Covers the console post-type API ({@code /api/v2/post/type}, id-keyed,
 * entity-shaped) and the XP resolution endpoint ({@code /api/v2/post/xp/{id}}),
 * both rebuilt on the unified post model.
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestMethodOrder(MethodOrderer.MethodName.class)
@TestInstance(Lifecycle.PER_CLASS)
class ShPostTypeConsoleAPITest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private ShWidgetRepository shWidgetRepository;
    @Autowired
    private ShPostUnifiedWriteService shPostUnifiedWriteService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private MockMvc mockMvc;
    private Principal mockPrincipal;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String rootFolderId;
    private String textWidgetId;
    private String postTypeId;
    private String newPostId;

    @BeforeAll
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        mockPrincipal = Mockito.mock(Principal.class);
        Mockito.when(mockPrincipal.getName()).thenReturn("admin");

        ShWidget textWidget = shWidgetRepository.findByName(ShSystemWidget.TEXT);
        textWidgetId = textWidget.getId();

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            ShSite site = new ShSite();
            site.setName("ut-pt-site-" + UUID.randomUUID());
            site.setUrl("http://example.test");
            site.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            site.setOwner("admin");
            entityManager.persist(site);

            ShFolder rootFolder = new ShFolder();
            rootFolder.setName("root");
            rootFolder.setRootFolder((byte) 1);
            rootFolder.setShSite(site);
            rootFolder.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            rootFolder.setOwner("admin");
            entityManager.persist(rootFolder);
            rootFolderId = rootFolder.getId();
        });
    }

    private ObjectNode postTypePayload(String name, String title) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("name", name);
        payload.put("namePlural", name + "s");
        payload.put("title", title);
        payload.put("description", "console test");
        ArrayNode attrs = payload.putArray("shPostTypeAttrs");
        ObjectNode titleAttr = attrs.addObject();
        titleAttr.put("name", ShSystemPostTypeAttr.TITLE);
        titleAttr.put("label", "Title");
        titleAttr.put("ordinal", 1);
        titleAttr.put("required", 1);
        titleAttr.put("isTitle", 1);
        titleAttr.put("isSummary", 0);
        titleAttr.put("widgetSettings", "");
        ObjectNode widget = titleAttr.putObject("shWidget");
        widget.put("id", textWidgetId);
        widget.put("name", ShSystemWidget.TEXT);
        return payload;
    }

    @Test
    void stage01CreatePostType() throws Exception {
        String response = mockMvc.perform(post("/api/v2/post/type")
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                postTypePayload("PT-CONSOLE-" + UUID.randomUUID(), "Console Type"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Console Type"))
                .andExpect(jsonPath("$.shPostTypeAttrs.length()").value(1))
                .andExpect(jsonPath("$.shPostTypeAttrs[0].shWidget.name").value(ShSystemWidget.TEXT))
                .andReturn().getResponse().getContentAsString();
        postTypeId = objectMapper.readTree(response).get("id").asString();
    }

    @Test
    void stage02GetServesAttrsTree() throws Exception {
        mockMvc.perform(get("/api/v2/post/type/" + postTypeId).principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(postTypeId))
                .andExpect(jsonPath("$.shPostTypeAttrs[0].name").value(ShSystemPostTypeAttr.TITLE));
    }

    @Test
    void stage03ListContainsIt() throws Exception {
        mockMvc.perform(get("/api/v2/post/type").principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + postTypeId + "')]").exists());
    }

    @Test
    void stage04UpdateReplacesAttrsSafely() throws Exception {
        // Re-send the post type renamed; the attribute replace must NOT cascade-delete
        // the shared Text widget catalog row.
        ObjectNode payload = postTypePayload("PT-CONSOLE-renamed-" + UUID.randomUUID(), "Console Type v2");

        mockMvc.perform(put("/api/v2/post/type/" + postTypeId)
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Console Type v2"))
                .andExpect(jsonPath("$.shPostTypeAttrs.length()").value(1));

        org.junit.jupiter.api.Assertions.assertNotNull(
                shWidgetRepository.findByName(ShSystemWidget.TEXT),
                "The shared Text widget must survive a post-type attribute replace");
    }

    @Test
    void stage05PostXpResolvesTitleAndFolder() throws Exception {
        newPostId = createPost(rootFolderId, postTypeId, "Referenced Post");

        mockMvc.perform(get("/api/v2/post/xp/" + newPostId).principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowPublish").value(true))
                .andExpect(jsonPath("$.shPost.title").value("Referenced Post"))
                .andExpect(jsonPath("$.shPost.shFolder.id").value(rootFolderId));
    }

    @Test
    void stage06PostXpUnknownReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v2/post/xp/" + UUID.randomUUID()).principal(mockPrincipal))
                .andExpect(status().isNotFound());
    }

    @Test
    void stage07DeleteEmptyPostType() throws Exception {
        String response = mockMvc.perform(post("/api/v2/post/type")
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                postTypePayload("PT-CONSOLE-del-" + UUID.randomUUID(), "Disposable"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String disposableId = objectMapper.readTree(response).get("id").asString();

        mockMvc.perform(delete("/api/v2/post/type/" + disposableId).principal(mockPrincipal))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v2/post/type/" + disposableId).principal(mockPrincipal))
                .andExpect(status().isNotFound());
    }

    @Test
    void stage08ManagedTypeIsReadOnlyInConsole() throws Exception {
        // SH66: a code-owned (managed) type is locked in the console — GET exposes
        // the flag (so the editor can render read-only) and PUT is rejected (409).
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        String managedId = tx.execute(status -> {
            ShPostType pt = new ShPostType();
            pt.setName("PT-MANAGED-" + UUID.randomUUID());
            pt.setTitle("Code Owned");
            pt.setManaged(true);
            pt.setSystem((byte) 0);
            pt.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            pt.setOwner("admin");
            entityManager.persist(pt);
            return pt.getId();
        });

        mockMvc.perform(get("/api/v2/post/type/" + managedId).principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.managed").value(true));

        mockMvc.perform(put("/api/v2/post/type/" + managedId)
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(postTypePayload("hacked", "Hacked"))))
                .andExpect(status().isConflict());
    }

    private String createPost(String folderId, String typeId, String title) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put(ShSystemPostTypeAttr.TITLE, title);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return tx.execute(status -> shPostUnifiedWriteService.create(
                new CreateRequest(folderId, typeId, data, ShPostState.DRAFT, "admin")).getId());
    }
}
