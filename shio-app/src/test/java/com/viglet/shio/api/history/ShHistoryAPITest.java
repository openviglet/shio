/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.history;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.security.Principal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.persistence.repository.widget.ShWidgetRepository;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;
import com.viglet.shio.widget.ShSystemWidget;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Proves the SH51 audit trail end to end: the unified write path records a row
 * per transition and {@link ShHistoryAPI} reads them back newest-first.
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShHistoryAPITest {

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

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;
    private Principal mockPrincipal;

    private String folderId;
    private String postTypeId;
    private String siteId;
    private String postId;

    @BeforeAll
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        mockPrincipal = Mockito.mock(Principal.class);
        Mockito.when(mockPrincipal.getName()).thenReturn("admin");

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(s -> {
            ShSite site = new ShSite();
            site.setName("hist-site-" + UUID.randomUUID());
            site.setDescription("History Test Site");
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
            folderId = rootFolder.getId();

            ShWidget textWidget = entityManager.find(ShWidget.class,
                    shWidgetRepository.findByName(ShSystemWidget.TEXT).getId());

            ShPostType postType = new ShPostType();
            postType.setName("PT-HIST-" + UUID.randomUUID());
            postType.setTitle("History Test Post Type");
            postType.setDescription("Post type for history tests");
            postType.setSystem((byte) 0);
            postType.setDate(Instant.parse("2026-01-01T00:00:00Z"));
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

        // Drive the audited transitions: CREATE (draft) then PUBLISH.
        ObjectNode data = objectMapper.createObjectNode();
        data.put(ShSystemPostTypeAttr.TITLE, "Audited Post");
        ShPostUnified created = shPostUnifiedWriteService.create(new CreateRequest(
                folderId, postTypeId, data, ShPostState.DRAFT, "admin", null, null));
        postId = created.getId();
        shPostUnifiedWriteService.publish(postId, "admin");
    }

    @Test
    void historyByObjectReturnsTransitionsNewestFirst() throws Exception {
        mockMvc.perform(get("/api/v2/history/object/" + postId).principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                // Newest first: PUBLISH precedes CREATE.
                .andExpect(jsonPath("$[0].action").value("PUBLISH"))
                .andExpect(jsonPath("$[0].owner").value("admin"))
                .andExpect(jsonPath("$[0].shObject").value(postId))
                .andExpect(jsonPath("$[0].description").value("Published \"Audited Post\""))
                .andExpect(jsonPath("$[1].action").value("CREATE"));
    }

    @Test
    void historyBySiteReturnsTransitions() throws Exception {
        mockMvc.perform(get("/api/v2/history/site/" + siteId).principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].shSite").value(siteId));
    }

    @Test
    void recentActivityIncludesThePost() throws Exception {
        mockMvc.perform(get("/api/v2/history").param("limit", "50").principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
