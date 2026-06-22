/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.folder;

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
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
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

/**
 * Covers the folder CRUD and object-navigation surface rebuilt on the unified
 * post model: {@code /api/v2/folder} CRUD + {@code /path}, and
 * {@code /api/v2/object/{id}/list}, {@code /copyto}, {@code /moveto}.
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestMethodOrder(MethodOrderer.MethodName.class)
@TestInstance(Lifecycle.PER_CLASS)
class ShFolderAPITest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private ShWidgetRepository shWidgetRepository;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private ShPostUnifiedWriteService shPostUnifiedWriteService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private MockMvc mockMvc;
    private Principal mockPrincipal;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String siteId;
    private String rootFolderId;
    private String postTypeId;
    private String childFolderId;

    @BeforeAll
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        mockPrincipal = Mockito.mock(Principal.class);
        Mockito.when(mockPrincipal.getName()).thenReturn("admin");

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            ShSite site = new ShSite();
            site.setName("ut-folder-site-" + UUID.randomUUID());
            site.setDescription("Folder API Test Site");
            site.setUrl("http://example.test");
            site.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            site.setOwner("admin");
            entityManager.persist(site);
            siteId = site.getId();

            ShFolder rootFolder = new ShFolder();
            rootFolder.setName("root");
            rootFolder.setRootFolder((byte) 1);
            rootFolder.setShSite(site);
            rootFolder.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            rootFolder.setOwner("admin");
            entityManager.persist(rootFolder);
            rootFolderId = rootFolder.getId();

            ShWidget textWidget = entityManager.find(ShWidget.class,
                    shWidgetRepository.findFirstByName(ShSystemWidget.TEXT).getId());

            ShPostType postType = new ShPostType();
            postType.setName("PT-FUT-" + UUID.randomUUID());
            postType.setTitle("Folder Test Post Type");
            postType.setSystem((byte) 0);
            postType.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            postType.setOwner("admin");

            ShPostTypeAttr titleAttr = new ShPostTypeAttr();
            titleAttr.setName(ShSystemPostTypeAttr.TITLE);
            titleAttr.setLabel("Title");
            titleAttr.setOrdinal(1);
            titleAttr.setRequired((byte) 1);
            titleAttr.setIsTitle((byte) 1);
            titleAttr.setIsSummary((byte) 0);
            titleAttr.setShWidget(textWidget);
            titleAttr.setShPostType(postType);
            postType.setShPostTypeAttrs(java.util.Set.of(titleAttr));

            entityManager.persist(postType);
            postTypeId = postType.getId();
        });
    }

    @Test
    void stage01ListSiteShowsRootFolder() throws Exception {
        mockMvc.perform(get("/api/v2/object/" + siteId + "/list").principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shSite.id").value(siteId))
                .andExpect(jsonPath("$.shFolders[?(@.id == '" + rootFolderId + "')]").exists());
    }

    @Test
    void stage02CreateFolderUnderRoot() throws Exception {
        ShFolder payload = new ShFolder();
        payload.setName("Docs");

        String response = mockMvc.perform(post("/api/v2/folder/" + rootFolderId)
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Docs"))
                .andExpect(jsonPath("$.rootFolder").value(0))
                .andReturn().getResponse().getContentAsString();

        childFolderId = objectMapper.readTree(response).get("id").asString();
    }

    @Test
    void stage03CreateFolderUnknownParentReturnsNotFound() throws Exception {
        ShFolder payload = new ShFolder();
        payload.setName("Orphan");
        mockMvc.perform(post("/api/v2/folder/" + UUID.randomUUID())
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isNotFound());
    }

    @Test
    void stage04GetFolder() throws Exception {
        mockMvc.perform(get("/api/v2/folder/" + childFolderId).principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(childFolderId))
                .andExpect(jsonPath("$.name").value("Docs"));
    }

    @Test
    void stage05FolderPathHasBreadcrumbAndSite() throws Exception {
        mockMvc.perform(get("/api/v2/folder/" + childFolderId + "/path").principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shSite.id").value(siteId))
                .andExpect(jsonPath("$.currentFolder.id").value(childFolderId))
                // breadcrumb is root-first and includes the current folder.
                .andExpect(jsonPath("$.breadcrumb[0].id").value(rootFolderId))
                .andExpect(jsonPath("$.breadcrumb[1].id").value(childFolderId));
    }

    @Test
    void stage06UpdateFolderRename() throws Exception {
        ShFolder payload = new ShFolder();
        payload.setName("Documents");
        mockMvc.perform(put("/api/v2/folder/" + childFolderId)
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Documents"))
                .andExpect(jsonPath("$.furl").value("documents"));
    }

    @Test
    void stage07ListFolderShowsPostsAndClone() throws Exception {
        String postId = createPost(childFolderId, "First Post");

        // List shows the post.
        mockMvc.perform(get("/api/v2/object/" + childFolderId + "/list").principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shPosts[?(@.id == '" + postId + "')]").exists());

        // Clone (copy to same folder) yields a second post in the folder.
        mockMvc.perform(put("/api/v2/object/copyto/" + childFolderId)
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"" + postId + "\"]"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v2/object/" + childFolderId + "/list").principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shPosts.length()").value(2));
    }

    @Test
    void stage08MovePostToRoot() throws Exception {
        String postId = createPost(childFolderId, "Movable Post");

        mockMvc.perform(put("/api/v2/object/moveto/" + rootFolderId)
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"" + postId + "\"]"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v2/object/" + rootFolderId + "/list").principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shPosts[?(@.id == '" + postId + "')]").exists());
    }

    @Test
    void stage09ClearCacheIsOk() throws Exception {
        mockMvc.perform(get("/api/v2/object/" + childFolderId + "/clear-cache").principal(mockPrincipal))
                .andExpect(status().isOk());
    }

    @Test
    void stage10DeleteFolderCascadesPosts() throws Exception {
        // childFolder still has the original post + its clone; delete must remove them too.
        mockMvc.perform(delete("/api/v2/folder/" + childFolderId).principal(mockPrincipal))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v2/folder/" + childFolderId).principal(mockPrincipal))
                .andExpect(status().isNotFound());
    }

    private String createPost(String folderId, String title) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put(ShSystemPostTypeAttr.TITLE, title);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return tx.execute(status -> shPostUnifiedWriteService.create(
                new CreateRequest(folderId, postTypeId, data, ShPostState.DRAFT, "admin", null, null)).getId());
    }
}
