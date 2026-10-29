/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import com.viglet.shio.api.acl.ShContentAclService;
import com.viglet.shio.persistence.model.auth.ShGroup;
import com.viglet.shio.persistence.model.auth.ShUser;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.post.type.ShSystemPostType;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH1285 — a members-only section answers its members and hides from everyone else, on the
 * public delivery route.
 *
 * <p>Asserted at {@code /sites/**} with the security filter chain applied, because that is where
 * a console session meets an anonymous route: a check that only worked with a hand-set security
 * context would pass while every real member got the 404. The fixture is a site with a public
 * home and a gated {@code members} folder holding one page, and three readers: nobody, a
 * signed-in user outside the group, and a member.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShSitesMembersOnlyTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ShContentAclService shContentAclService;

    @PersistenceContext
    private EntityManager entityManager;

    private MockMvc mockMvc;
    private String siteName;
    private String member;
    private String stranger;
    private String admin;
    private String siteId;
    private String rootId;
    private String membersId;

    @BeforeAll
    void seedAGatedSection() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity()).build();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        siteName = "sh1285-" + suffix;
        member = "sh1285-member-" + suffix;
        stranger = "sh1285-stranger-" + suffix;
        admin = "sh1285-admin-" + suffix;
        String group = "Sh1285Members-" + suffix;
        String[] gated = new String[1];

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShSite site = new ShSite();
            site.setName(siteName);
            site.setFurl(siteName);
            site.setDate(when);
            site.setOwner("admin");
            site.setPostTypeLayout("{\"Sh1285Page\":\"SH1285_LAYOUT\"}");
            entityManager.persist(site);
            siteId = site.getId();

            ShFolder root = folder(site, null, siteName, when);
            ShFolder members = folder(null, root, "members", when);
            gated[0] = members.getId();
            rootId = root.getId();
            membersId = members.getId();

            ShPostType layoutType = postType(ShSystemPostType.PAGE_LAYOUT, (byte) 1, when);
            ShPostType pageType = postType("Sh1285Page", (byte) 0, when);
            post(site, root, layoutType, "SH1285_LAYOUT", "/l", "{\"HTML\":\"<html><body>{{TEXT}}</body></html>\"}", when);
            post(site, root, pageType, "Home", "/", "{\"TEXT\":\"welcome\"}", when);
            post(site, members, pageType, "Lounge", "/lounge", "{\"TEXT\":\"members lounge\"}", when);

            ShGroup memberGroup = new ShGroup();
            memberGroup.setName(group);
            entityManager.persist(memberGroup);
            account(member, memberGroup);
            account(stranger, null);
            ShGroup administrators = entityManager
                    .createQuery("select g from ShGroup g where g.name = 'Administrator'", ShGroup.class)
                    .getResultStream().findFirst().orElseGet(() -> {
                        ShGroup created = new ShGroup();
                        created.setName("Administrator");
                        entityManager.persist(created);
                        return created;
                    });
            account(admin, administrators);
        });
        shContentAclService.replaceGate(gated[0], true, List.of(group));
    }

    @Test
    @DisplayName("SH1285: a visitor nobody signed in gets the 404 a missing page gets")
    void aVisitorIsTurnedAway() throws Exception {
        mockMvc.perform(get(page("lounge")))
                .andExpect(status().isNotFound())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .as("SH1285: and nothing of the page reaches the body")
                        .doesNotContain("members lounge"));
    }

    @Test
    @DisplayName("SH1285: a signed-in user outside the group is turned away the same way")
    void aStrangerIsTurnedAway() throws Exception {
        mockMvc.perform(get(page("lounge")).with(user(stranger)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("SH1285: a member reads it, and the response is never shared-cacheable")
    void aMemberReadsIt() throws Exception {
        mockMvc.perform(get(page("lounge")).with(user(member)))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    assertThat(result.getResponse().getContentAsString()).contains("members lounge");
                    assertThat(result.getResponse().getHeader("Cache-Control"))
                            .as("SH1285: a shared cache holding a member's page would hand it to the next visitor")
                            .contains("no-store").contains("private").doesNotContain("public");
                });
    }

    @Test
    @DisplayName("SH1285: a public sibling keeps its public cache header")
    void aPublicPageIsUnchanged() throws Exception {
        mockMvc.perform(get(page("")))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getHeader("Cache-Control"))
                        .contains("public").contains("max-age=300"));
    }

    // --- the CDA: a member is the owner of the token ---

    @Test
    @DisplayName("SH1295: on the CDA a token whose owner is outside the group gets the missing-post 404")
    void aStrangersTokenIsTurnedAway() throws Exception {
        mockMvc.perform(cda("/lounge").with(token(stranger)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("SH1295: a member's token reads the post, privately")
    void aMembersTokenReadsIt() throws Exception {
        mockMvc.perform(cda("/lounge").with(token(member)))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    assertThat(result.getResponse().getContentAsString()).contains("Lounge");
                    assertThat(result.getResponse().getHeader("Cache-Control"))
                            .contains("no-store").contains("private").doesNotContain("public");
                });
    }

    @Test
    @DisplayName("SH1295: an administrator's token reads it, as the same administrator does on /sites")
    void anAdministratorsTokenReadsIt() throws Exception {
        mockMvc.perform(cda("/lounge").with(token(admin)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("SH1295: a public post on the CDA keeps the shared cache header")
    void aPublicPostIsUnchangedOnTheCda() throws Exception {
        mockMvc.perform(cda("/").with(token(stranger)))
                .andExpect(status().isOk())
                // MockMvc does not run the CDA's servlet filters, so a public post carries no header
                // of its own here; what matters is that the controller did not mark it private.
                .andExpect(result -> assertThat(String.valueOf(result.getResponse().getHeader("Cache-Control")))
                        .doesNotContain("no-store"));
    }

    // --- the CDA's listings ---

    @Test
    @DisplayName("SH1296: a folder listing hides a gated subfolder, and the gated folder lists as absent")
    void aListingHidesTheGatedSection() throws Exception {
        mockMvc.perform(get("/api/v2/cda/object/" + rootId + "/list").with(token(stranger)))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .as("SH1296: the members folder is not a child a stranger is shown")
                        .doesNotContain(membersId));
        mockMvc.perform(get("/api/v2/cda/object/" + membersId + "/list").with(token(stranger)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v2/cda/object/" + membersId + "/list").with(token(member)))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains("Lounge"));
    }

    @Test
    @DisplayName("SH1296: a search finds a gated post for its member and never for a stranger")
    void aSearchHidesTheGatedPost() throws Exception {
        mockMvc.perform(get("/api/v2/cda/search").param("q", "Lounge").param("siteId", siteId)
                        .with(token(stranger)))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .as("SH1296: a search is a listing, and a listing is where a gate leaks first")
                        .doesNotContain("\"Lounge\""));
        mockMvc.perform(get("/api/v2/cda/search").param("q", "Lounge").param("siteId", siteId)
                        .with(token(member)))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains("\"Lounge\""));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder cda(String url) {
        return get("/api/v2/cda/post/by-url").param("siteId", siteId).param("url", url);
    }

    /** What ShApiTokenFilter builds for a PROD READ token: the owner, with the CDA's authorities. */
    private static org.springframework.test.web.servlet.request.RequestPostProcessor token(String owner) {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                .authentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        owner, null, List.of(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CDA"),
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))));
    }

    private String page(String path) {
        return "/sites/" + siteName + "/default/en-us/" + path;
    }

    private ShFolder folder(ShSite site, ShFolder parent, String name, Instant when) {
        ShFolder folder = new ShFolder();
        folder.setName(name);
        folder.setRootFolder(parent == null ? (byte) 1 : (byte) 0);
        folder.setShSite(site);
        folder.setParentFolder(parent);
        folder.setDate(when);
        folder.setOwner("admin");
        entityManager.persist(folder);
        return folder;
    }

    private void account(String username, ShGroup group) {
        ShUser user = new ShUser();
        user.setUsername(username);
        user.setEmail(username + "@example.invalid");
        user.setPassword("not-a-real-hash");
        user.setEnabled(1);
        if (group != null) {
            user.getShGroups().add(group);
        }
        entityManager.persist(user);
    }

    private ShPostType postType(String name, byte system, Instant when) {
        ShPostType existing = entityManager
                .createQuery("select t from ShPostType t where t.name = :name", ShPostType.class)
                .setParameter("name", name).getResultStream().findFirst().orElse(null);
        if (existing != null) {
            return existing;
        }
        ShPostType type = new ShPostType();
        type.setName(name);
        type.setTitle(name);
        type.setSystem(system);
        type.setDate(when);
        type.setOwner("admin");
        entityManager.persist(type);
        return type;
    }

    private void post(ShSite site, ShFolder folder, ShPostType type, String title, String furl,
                      String data, Instant when) {
        ShPostUnified row = new ShPostUnified();
        row.setId(UUID.randomUUID().toString());
        row.setState(ShPostState.PUBLISHED);
        row.setShSite(site);
        row.setShFolder(folder);
        row.setShPostType(type);
        row.setTitle(title);
        row.setFurl(furl);
        row.setOwner("admin");
        row.setDate(when);
        row.setData(data);
        entityManager.persist(row);
    }
}
