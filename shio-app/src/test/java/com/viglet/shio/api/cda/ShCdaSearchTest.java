/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.cda;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
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

import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Functional coverage for the SH52 full-text search endpoint
 * (`GET /api/v2/cda/search`). No-token/console access (PROD) sees published
 * content only; the term matches title, summary and the raw attribute data.
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShCdaSearchTest {

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
    private String alphaId;
    private String bodyMatchId;
    private String draftId;

    @BeforeAll
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        mockPrincipal = Mockito.mock(Principal.class);
        Mockito.when(mockPrincipal.getName()).thenReturn("admin");

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            ShSite site = new ShSite();
            site.setName("search-site-" + UUID.randomUUID());
            site.setDescription("Search Test Site");
            site.setUrl("http://search.example.test");
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

            ShPostType postType = new ShPostType();
            postType.setName("PT-SEARCH-" + UUID.randomUUID());
            postType.setTitle("Search Test Post Type");
            postType.setSystem((byte) 0);
            postType.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            postType.setOwner("admin");
            entityManager.persist(postType);

            // A unique term in the title — "zephyrium" — only on this post.
            alphaId = persistPost(site, root, postType, ShPostState.PUBLISHED,
                    "Zephyrium Quarterly Report", "/zephyrium", "{\"title\":\"Zephyrium Quarterly Report\"}");
            // Term only in the raw data body, not the title.
            bodyMatchId = persistPost(site, root, postType, ShPostState.PUBLISHED,
                    "Unrelated Heading", "/body", "{\"title\":\"Unrelated Heading\",\"body\":\"mentions zephyrium inside\"}");
            // A DRAFT post that also contains the term — must NOT surface in PROD search.
            draftId = persistPost(site, root, postType, ShPostState.DRAFT,
                    "Zephyrium Draft", "/zephyrium-draft", "{\"title\":\"Zephyrium Draft\"}");
        });
    }

    private String persistPost(ShSite site, ShFolder folder, ShPostType postType,
                               ShPostState state, String title, String furl, String data) {
        ShPostUnified post = new ShPostUnified();
        post.setId(UUID.randomUUID().toString());
        post.setState(state);
        post.setShSite(site);
        post.setShFolder(folder);
        post.setShPostType(postType);
        post.setTitle(title);
        post.setSummary(title);
        post.setFurl(furl);
        post.setPublished(state == ShPostState.PUBLISHED);
        post.setOwner("admin");
        post.setDate(Instant.parse("2026-01-01T00:00:00Z"));
        post.setData(data);
        entityManager.persist(post);
        return post.getId();
    }

    @AfterAll
    void tearDown() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            for (String id : new String[]{alphaId, bodyMatchId, draftId}) {
                if (id != null) {
                    shPostUnifiedRepository.findById(id).forEach(shPostUnifiedRepository::delete);
                }
            }
        });
    }

    @Test
    void searchMatchesTitleAndBodyButNotDrafts() throws Exception {
        // Case-insensitive, matches both the title hit and the body-only hit;
        // the DRAFT post with the same term is excluded (PROD = published-only).
        mockMvc.perform(get("/api/v2/cda/search")
                        .param("q", "ZEPHYRIUM")
                        .param("siteId", siteId)
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts.length()").value(2))
                .andExpect(jsonPath("$.totalPosts").value(2));
    }

    @Test
    void searchUnscopedFindsTheTerm() throws Exception {
        mockMvc.perform(get("/api/v2/cda/search")
                        .param("q", "zephyrium")
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts.length()").value(2));
    }

    @Test
    void searchUnknownTermReturnsEmpty() throws Exception {
        mockMvc.perform(get("/api/v2/cda/search")
                        .param("q", "term-that-matches-nothing-" + UUID.randomUUID())
                        .param("siteId", siteId)
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts.length()").value(0))
                .andExpect(jsonPath("$.totalPosts").value(0));
    }

    @Test
    void searchBlankQueryReturnsEmpty() throws Exception {
        mockMvc.perform(get("/api/v2/cda/search")
                        .param("q", "   ")
                        .param("siteId", siteId)
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts.length()").value(0));
    }
}
