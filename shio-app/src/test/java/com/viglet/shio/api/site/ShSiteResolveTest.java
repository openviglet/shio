/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.site;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.time.Instant;
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

import com.viglet.shio.persistence.model.site.ShSite;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH181 — the site read answers the key it is given.
 *
 * <p>The Universal Editor asked this id-keyed endpoint for a site's <em>furl</em> and got a
 * pair of 404s behind a spinner. That is the third instance of one confusion (SH117 settled
 * it for post-types), so the guard is a test rather than a patched call site: id, furl and
 * name all resolve, and a key that names nothing is still a 404 — a read that resolved
 * anything would show the curator the wrong site.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShSiteResolveTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private MockMvc mockMvc;
    private Principal mockPrincipal;

    private String siteId;
    private String siteName;
    private String siteFurl;

    @BeforeAll
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        mockPrincipal = Mockito.mock(Principal.class);
        Mockito.when(mockPrincipal.getName()).thenReturn("admin");

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            ShSite site = new ShSite();
            site.setName("ut-resolve-" + UUID.randomUUID());
            site.setDescription("Site resolution test");
            site.setUrl("http://example.test");
            site.setFurl("ut-resolve-furl-" + UUID.randomUUID());
            site.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            site.setOwner("admin");
            entityManager.persist(site);
            siteId = site.getId();
            siteName = site.getName();
            siteFurl = site.getFurl();
        });
    }

    @Test
    void resolvesById() throws Exception {
        mockMvc.perform(get("/api/v2/site/" + siteId).principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(siteId));
    }

    @Test
    void resolvesByFurl() throws Exception {
        mockMvc.perform(get("/api/v2/site/" + siteFurl).principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(siteId));
    }

    @Test
    void resolvesByName() throws Exception {
        mockMvc.perform(get("/api/v2/site/" + siteName).principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(siteId));
    }

    @Test
    void unknownKeyIsStillNotFound() throws Exception {
        mockMvc.perform(get("/api/v2/site/no-such-site-" + UUID.randomUUID()).principal(mockPrincipal))
                .andExpect(status().isNotFound());
    }
}
