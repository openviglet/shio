/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.object;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.acl.ShContentAclService;
import com.viglet.shio.api.acl.ShContentAclService.AclEntry;
import com.viglet.shio.api.object.dto.ShConsoleSearch;
import com.viglet.shio.api.query.ShContentQuery;
import com.viglet.shio.persistence.model.acl.ShAclPermission;
import com.viglet.shio.persistence.model.acl.ShAclPrincipalType;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH1027 — the console's search finds a draft, and does not show what the reader may not open.
 *
 * <p>The design names both assertions in one sentence: "a draft is found, and an unreadable
 * folder's post is withheld and counted." They are the two halves of the same defect — the search
 * page read the <b>delivery</b> API, which is published-only and enforces a token's site
 * allow-list rather than a person's content ACL — and neither half fails loudly. A curator who
 * cannot find the draft an agent just wrote concludes the agent did not write it; a curator shown
 * a title from a folder they cannot open finds that out by clicking it.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shio-console-search;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true",
        "shio.schedule.enabled=false" })
@TestInstance(Lifecycle.PER_CLASS)
class ShConsoleSearchTest {

    private static final Instant EPOCH = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private ShConsoleSearchService shConsoleSearchService;
    @Autowired
    private ShContentAclService shContentAclService;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;
    private String openFolderId;
    private String closedFolderId;

    /** Two folders, one post each, and only one of them readable by {@code curator}. */
    @BeforeAll
    void setup() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = new ShSite();
            site.setName("search-" + UUID.randomUUID());
            site.setDate(EPOCH);
            site.setOwner("admin");
            entityManager.persist(site);
            siteName = site.getName();

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(EPOCH);
            root.setOwner("admin");
            entityManager.persist(root);

            ShPostType type = new ShPostType();
            type.setName("SearchArticle");
            type.setTitle("Search Article");
            type.setDescription("SH1027 fixture");
            type.setDate(EPOCH);
            entityManager.persist(type);

            openFolderId = folder(root, "open").getId();
            closedFolderId = folder(root, "closed").getId();

            // A DRAFT that was never published, which is what an agent run leaves behind and what
            // the delivery search could not see at all.
            post(site, entityManager.find(ShFolder.class, openFolderId), type,
                    "Peculiar draft heading", "open-draft", ShPostState.DRAFT);
            post(site, entityManager.find(ShFolder.class, closedFolderId), type,
                    "Peculiar hidden heading", "closed-draft", ShPostState.DRAFT);
        });

        // Read granted on the open folder only. An ACL row anywhere makes the instance
        // ACL-governed, which is the state the CDA's site allow-list says nothing about.
        shContentAclService.replaceAcl(openFolderId, List.of(
                new AclEntry(ShAclPrincipalType.USER, "curator", ShAclPermission.READ)));
        shContentAclService.replaceAcl(closedFolderId, List.of(
                new AclEntry(ShAclPrincipalType.USER, "someone-else", ShAclPermission.READ)));
    }

    private ShFolder folder(ShFolder parent, String name) {
        ShFolder folder = new ShFolder();
        folder.setName(name);
        folder.setRootFolder((byte) 0);
        folder.setParentFolder(parent);
        folder.setDate(EPOCH);
        folder.setOwner("admin");
        entityManager.persist(folder);
        return folder;
    }

    private void post(ShSite site, ShFolder folder, ShPostType type, String title, String furl,
                      ShPostState state) {
        ShPostUnified post = new ShPostUnified();
        post.setState(state);
        post.setShSite(site);
        post.setShFolder(folder);
        post.setShPostType(type);
        post.setTitle(title);
        post.setFurl(furl);
        post.setData("{}");
        post.setDate(EPOCH);
        post.setModifiedDate(EPOCH);
        post.setOwner("agent");
        shPostUnifiedRepository.save(post);
    }

    @Test
    @DisplayName("SH1027: a draft is found — the delivery search could not see one at all")
    @WithMockUser(username = "admin", roles = { "ADMIN" })
    void aDraftIsFound() {
        ShConsoleSearch found = search(ShContentQuery.ofSite(siteName).withQ("Peculiar"));

        assertThat(found.results())
                .as("""
                        SH1027: the search page called /api/v2/cda/search, whose state is \
                        PUBLISHED unless the caller holds a preview token — and the console has \
                        never sent one. Searching for what a run just made is the first thing a \
                        curator does after it, and it answered nothing.""")
                .hasSize(2)
                .allSatisfy(row -> assertThat(row.state()).isEqualTo("DRAFT"));
        assertThat(found.withheld())
                .as("SH1027: an admin is withheld nothing")
                .isZero();
    }

    @Test
    @DisplayName("SH1027: an unreadable folder's post is withheld, and counted")
    @WithMockUser(username = "curator")
    void whatCannotBeOpenedIsWithheldAndCounted() {
        ShConsoleSearch found = search(ShContentQuery.ofSite(siteName).withQ("Peculiar"));

        assertThat(found.results())
                .as("""
                        SH1027: the delivery API enforces a TOKEN's site allow-list and knows \
                        nothing about the content ACL, so the console listed titles from folders \
                        the reader cannot open — and they found that out by clicking one.""")
                .singleElement()
                .satisfies(row -> assertThat(row.title()).isEqualTo("Peculiar draft heading"));
        assertThat(found.withheld())
                .as("""
                        SH1027: the number, not silence. A filtered listing that says nothing is \
                        indistinguishable from a search that found less, and the curator's next \
                        move is to conclude the content is not there — which sends them to write \
                        the page again rather than to ask somebody for access.""")
                .isEqualTo(1);
        assertThat(found.count()).isEqualTo(found.results().size());
    }

    @Test
    @DisplayName("SH1027: the folder scope is the subtree, the same word the agent's find spells")
    @WithMockUser(username = "admin", roles = { "ADMIN" })
    void theFolderScopeIsRecursive() {
        ShConsoleSearch beneath = search(ShContentQuery.ofSite(siteName).withQ("Peculiar")
                .withFolder("/"));
        assertThat(beneath.results())
                .as("""
                        SH1027: the CDA's folder filter matched direct children while the agent's \
                        is recursive, so one word asked two questions. The console search takes \
                        the agent's, which is the one a curator means by "in this section".""")
                .hasSize(2);

        ShConsoleSearch narrowed = search(ShContentQuery.ofSite(siteName).withQ("Peculiar")
                .withFolder("/open"));
        assertThat(narrowed.results()).hasSize(1);
    }

    @Test
    @DisplayName("SH1027: state narrows it, in the vocabulary every other surface uses")
    @WithMockUser(username = "admin", roles = { "ADMIN" })
    void stateNarrowsIt() {
        assertThat(search(ShContentQuery.ofSite(siteName).withQ("Peculiar").withState("published"))
                .results())
                .as("SH1027: nothing here has been published, and the published listing says so "
                        + "rather than falling back to the draft")
                .isEmpty();
        assertThat(search(ShContentQuery.ofSite(siteName).withQ("Peculiar").withState("draft"))
                .results())
                .hasSize(2);
    }

    private ShConsoleSearch search(ShContentQuery query) {
        return new TransactionTemplate(transactionManager)
                .execute(status -> shConsoleSearchService.search(query, 50, null, false));
    }
}
