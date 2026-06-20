/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.cda;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.time.Instant;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Functional coverage for the CDA read contract (SH1). Mirrors the
 * {@code ShPostUnifiedAPITest} harness (raw {@code webAppContextSetup} +
 * {@code Principal}) for the data/404/shape criteria, plus one
 * security-enabled MockMvc to assert the {@code Key}-gated access.
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.MethodName.class)
@TestInstance(Lifecycle.PER_CLASS)
class ShCdaAPITest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private MockMvc mockMvc;
    private Principal mockPrincipal;

    private String siteId;
    private String rootFolderId;
    private String publishedId;
    private String draftOnlyId;
    private String malformedId;
    private static final String FURL = "/hello-cda";

    @BeforeAll
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        mockPrincipal = Mockito.mock(Principal.class);
        Mockito.when(mockPrincipal.getName()).thenReturn("admin");

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            ShSite site = new ShSite();
            site.setName("cda-site-" + UUID.randomUUID());
            site.setDescription("CDA Test Site");
            site.setUrl("http://cda.example.test");
            site.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            site.setOwner("admin");
            entityManager.persist(site);
            siteId = site.getId();

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            root.setOwner("admin");
            entityManager.persist(root);
            rootFolderId = root.getId();

            ShPostType postType = new ShPostType();
            postType.setName("PT-CDA-" + UUID.randomUUID());
            postType.setTitle("CDA Test Post Type");
            postType.setSystem((byte) 0);
            postType.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            postType.setOwner("admin");
            entityManager.persist(postType);

            publishedId = persistPost(site, root, postType, ShPostState.PUBLISHED, true,
                    "Hello", FURL, "{\"title\":\"Hello\"}");
            draftOnlyId = persistPost(site, root, postType, ShPostState.DRAFT, false,
                    "Draft", "/draft-cda", "{\"title\":\"Draft\"}");
            malformedId = persistPost(site, root, postType, ShPostState.PUBLISHED, true,
                    "Broken", "/broken-cda", "{not valid json");
        });
    }

    private String persistPost(ShSite site, ShFolder folder, ShPostType postType,
                               ShPostState state, boolean published,
                               String title, String furl, String data) {
        ShPostUnified post = new ShPostUnified();
        post.setId(UUID.randomUUID().toString());
        post.setState(state);
        post.setShSite(site);
        post.setShFolder(folder);
        post.setShPostType(postType);
        post.setTitle(title);
        post.setFurl(furl);
        post.setPublished(published);
        post.setOwner("admin");
        post.setPublisher("admin");
        post.setPublishStatus("PUBLISHED");
        post.setDate(Instant.parse("2026-01-01T00:00:00Z"));
        post.setData(data);
        entityManager.persist(post);
        return post.getId();
    }

    @AfterAll
    void tearDown() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            for (String id : new String[]{publishedId, draftOnlyId, malformedId}) {
                if (id != null) {
                    shPostUnifiedRepository.findById(id).forEach(shPostUnifiedRepository::delete);
                }
            }
        });
    }

    @Test
    void stage01ListSites() throws Exception {
        mockMvc.perform(get("/api/v2/cda/site").principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void stage02GetSiteHasRootFolderId() throws Exception {
        mockMvc.perform(get("/api/v2/cda/site/" + siteId).principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(siteId))
                .andExpect(jsonPath("$.rootFolderId").value(rootFolderId));
    }

    @Test
    void stage03ListChildrenShowsPublishedNotDraft() throws Exception {
        mockMvc.perform(get("/api/v2/cda/object/" + rootFolderId + "/list").principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.folderId").value(rootFolderId))
                .andExpect(jsonPath("$.posts[?(@.id=='" + publishedId + "')]").exists())
                .andExpect(jsonPath("$.posts[?(@.id=='" + draftOnlyId + "')]").doesNotExist());
    }

    @Test
    void stage04PathBreadcrumb() throws Exception {
        mockMvc.perform(get("/api/v2/cda/object/" + rootFolderId + "/path").principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.site.id").value(siteId))
                .andExpect(jsonPath("$.currentFolder.id").value(rootFolderId))
                .andExpect(jsonPath("$.currentFolder.root").value(true));
    }

    @Test
    void stage05GetPostExpandsAttrsAndHidesAuthoringFields() throws Exception {
        mockMvc.perform(get("/api/v2/cda/post/" + publishedId).principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(publishedId))
                .andExpect(jsonPath("$.attrs.title").value("Hello"))
                .andExpect(jsonPath("$.owner").doesNotExist())
                .andExpect(jsonPath("$.publisher").doesNotExist())
                .andExpect(jsonPath("$.publishStatus").doesNotExist());
    }

    @Test
    void stage06GetDraftOnlyPostReturns404() throws Exception {
        mockMvc.perform(get("/api/v2/cda/post/" + draftOnlyId).principal(mockPrincipal))
                .andExpect(status().isNotFound());
    }

    @Test
    void stage07PostByUrlResolves() throws Exception {
        mockMvc.perform(get("/api/v2/cda/post/by-url")
                        .param("siteId", siteId)
                        .param("url", FURL)
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(publishedId));
    }

    @Test
    void stage08PostByUrlUnresolvableReturns404() throws Exception {
        mockMvc.perform(get("/api/v2/cda/post/by-url")
                        .param("siteId", siteId)
                        .param("url", "/does-not-exist")
                        .principal(mockPrincipal))
                .andExpect(status().isNotFound());
    }

    @Test
    void stage09MalformedDataYieldsEmptyAttrs() throws Exception {
        mockMvc.perform(get("/api/v2/cda/post/" + malformedId).principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attrs").isMap())
                .andExpect(jsonPath("$.attrs.title").doesNotExist());
    }

    @Test
    void stage10QueryBySite() throws Exception {
        mockMvc.perform(get("/api/v2/cda/query")
                        .param("siteId", siteId)
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts").isArray());
    }

    @Test
    void stage11RequiresTokenWhenSecurityApplied() throws Exception {
        MockMvc secured = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        // No Key header / no authentication → CDA is gated (hasRole CDA) → 4xx, never 200.
        secured.perform(get("/api/v2/cda/site"))
                .andExpect(status().is4xxClientError());
        // Non-GET to the CDA namespace is rejected as well.
        secured.perform(post("/api/v2/cda/site"))
                .andExpect(status().is4xxClientError());
    }
}
