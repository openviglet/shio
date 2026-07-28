/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.type;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
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
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import com.viglet.shio.widget.ShSystemWidget;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH163 — renaming a post-type is a move, not an upsert.
 *
 * <p>The workaround this replaces is what the tests are really written against: a
 * {@code PUT /api/v2/post-type/NewName} answers {@code 200} with a perfectly good-looking
 * definition and leaves every post on a second post-type nobody meant to create. So the
 * assertions are about <em>what came along</em> — the posts still resolve, the id is the same
 * one, the old name is gone — and about the four refusals, because a rename that merged two
 * models or renamed a system type would be worse than the workaround.</p>
 *
 * @author Alexandre Oliveira
 */
// Plain @SpringBootTest on purpose: a distinct property set means a distinct Spring context,
// and a second context on the same in-memory H2 re-runs `ddl-auto=create-drop` — dropping the
// schema under whatever context is cached. Isolation is worth a separate *database* (the
// benchmark does that) and never worth a shared one.
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShPostTypeRenameTest {

    private static final String PATH = "/api/v2/post-type";
    private static final Instant EPOCH = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private ShPostTypeRepository shPostTypeRepository;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private MockMvc mockMvc;
    private Principal principal;
    private ShSite site;
    private ShFolder root;

    @BeforeAll
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        principal = Mockito.mock(Principal.class);
        Mockito.when(principal.getName()).thenReturn("admin");

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            site = new ShSite();
            site.setName("ut-rename-site-" + UUID.randomUUID());
            site.setDate(EPOCH);
            site.setOwner("admin");
            entityManager.persist(site);

            root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(EPOCH);
            root.setOwner("admin");
            entityManager.persist(root);
        });
    }

    @Test
    @DisplayName("a rename carries the content, the id and the fields")
    void renameCarriesEverything() throws Exception {
        String from = type("RenameMe", false, false);
        String typeId = idOf(from);
        createPost(from, "A page");
        createPost(from, "Another page");
        String to = from + "Renamed";

        mockMvc.perform(rename(from, "{\"to\":\"" + to + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value(from))
                .andExpect(jsonPath("$.to").value(to))
                // Two logical posts, one DRAFT row each. The count is the point of the response:
                // the workaround reported success and moved none of them.
                .andExpect(jsonPath("$.posts").value(2))
                .andExpect(jsonPath("$.notes").isArray());

        // The same row, renamed — not a copy. If this were an upsert-and-orphan, the id would
        // differ and the old name would still answer.
        assertThat(idOf(to)).isEqualTo(typeId);
        mockMvc.perform(get(PATH + "/" + from).principal(principal))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(PATH + "/" + to).principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields[0].name").value("headline"));
        assertThat(shPostUnifiedRepository.countByShPostType_Id(typeId)).isEqualTo(2);
    }

    @Test
    @DisplayName("a Multi Select that narrowed to the old name is retargeted")
    void referencesAreRetargeted() throws Exception {
        String from = type("Narrowed", false, false);
        String referrer = typeReferencing("Referrer", from);
        String to = from + "Renamed";

        mockMvc.perform(rename(from, "{\"to\":\"" + to + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referencesUpdated[0]").value(referrer + ".sections"));

        // The settings string is the only place the model stores a post-type by *name*, so an
        // un-retargeted one is a field silently narrowing to a type that no longer exists.
        assertThat(settingsOf(referrer, "sections")).contains("\"postType\":\"" + to + "\"");
    }

    @Test
    @DisplayName("an occupied target is a conflict, never a merge")
    void occupiedTargetConflicts() throws Exception {
        String from = type("Occupied", false, false);
        String taken = type("Taker", false, false);

        mockMvc.perform(rename(from, "{\"to\":\"" + taken + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fix").exists());

        assertThat(shPostTypeRepository.findByName(from)).isNotNull();
    }

    @Test
    @DisplayName("a system post-type is not renamable")
    void systemTypeRefuses() throws Exception {
        String from = type("SysType", true, false);

        mockMvc.perform(rename(from, "{\"to\":\"" + from + "X\"}"))
                .andExpect(status().isConflict());
        assertThat(shPostTypeRepository.findByName(from)).isNotNull();
    }

    @Test
    @DisplayName("a hand edit cannot rename a code-managed type; the owner can")
    void managedTypeNeedsTheOwnersClaim() throws Exception {
        String from = type("Owned", false, true);

        // managed:false is the console — a hand edit, and a rename is a bigger change than the
        // edits that rule already refuses.
        mockMvc.perform(rename(from, "{\"to\":\"" + from + "X\",\"managed\":false}"))
                .andExpect(status().isConflict());
        assertThat(shPostTypeRepository.findByName(from)).isNotNull();

        // Absent means the code-owned writer, exactly as it does on an upsert.
        mockMvc.perform(rename(from, "{\"to\":\"" + from + "X\"}"))
                .andExpect(status().isOk());
        assertThat(shPostTypeRepository.findByName(from + "X").isManaged())
                .as("a rename is not how a type changes hands")
                .isTrue();
    }

    @Test
    @DisplayName("an unknown source teaches with the installed names")
    void unknownSourceTeaches() throws Exception {
        // A type has to exist for `allowed` to say anything: the installed set is what makes
        // this teaching error worth its bytes, and an empty one is honest rather than helpful.
        String installed = type("Installed", false, false);

        mockMvc.perform(rename("NoSuchTypeHere", "{\"to\":\"Whatever\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.allowed").isArray())
                .andExpect(jsonPath("$.allowed", org.hamcrest.Matchers.hasItem(installed)))
                .andExpect(jsonPath("$.fix").exists());
    }

    @Test
    @DisplayName("a blank target is a 400 — renaming to nothing is a delete nobody asked for")
    void blankTargetTeaches() throws Exception {
        String from = type("NeedsATarget", false, false);

        mockMvc.perform(rename(from, "{\"to\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fix").exists());
        assertThat(shPostTypeRepository.findByName(from)).isNotNull();
    }

    @Test
    @DisplayName("renaming to the current name is a no-op, so a re-run is safe")
    void sameNameIsANoop() throws Exception {
        String from = type("Idempotent", false, false);

        mockMvc.perform(rename(from, "{\"to\":\"" + from + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.to").value(from))
                .andExpect(jsonPath("$.referencesUpdated").isEmpty());
        assertThat(shPostTypeRepository.findByName(from)).isNotNull();
    }

    // ── SH236: the delete this rename is the alternative to ────────────────────────────

    @Test
    @DisplayName("deleting a post-type that content points at teaches with the count")
    void deleteRefusesATypeInUse() throws Exception {
        String name = type("StillUsed", false, false);
        createPost(name, "A page");
        createPost(name, "Another page");

        // It used to be a 500: the delete checked the `system` flag and nothing else, and the
        // post FK is onDelete NO ACTION — so the database refused with a constraint violation the
        // caller reads as a server fault. The count is what separates "rename it" from "retire
        // it", which is why it is in the body rather than just the status.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete(PATH + "/" + name).principal(principal))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("2 post(s)")))
                .andExpect(jsonPath("$.fix").value(org.hamcrest.Matchers.containsString("rename")));

        assertThat(shPostTypeRepository.findByName(name)).isNotNull();
    }

    @Test
    @DisplayName("an unused post-type still deletes")
    void deleteStillWorksWithoutContent() throws Exception {
        String name = type("Unused", false, false);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete(PATH + "/" + name).principal(principal))
                .andExpect(status().isNoContent());

        assertThat(shPostTypeRepository.findByName(name)).isNull();
    }

    // --- fixtures ---

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder rename(
            String from, String body) {
        return post(PATH + "/" + from + "/rename").principal(principal)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    /** A post-type with one {@code Text} field, created straight through the repository. */
    private String type(String prefix, boolean system, boolean managed) {
        String name = prefix + UUID.randomUUID().toString().substring(0, 8);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShPostType postType = new ShPostType();
            postType.setName(name);
            postType.setTitle(prefix);
            postType.setSystem((byte) (system ? 1 : 0));
            postType.setManaged(managed);
            postType.setDate(EPOCH);
            postType.setOwner("admin");
            entityManager.persist(postType);

            ShPostTypeAttr attr = new ShPostTypeAttr();
            attr.setId(UUID.randomUUID().toString());
            attr.setName("headline");
            attr.setLabel("Headline");
            attr.setOrdinal(1);
            attr.setIsTitle((byte) 1);
            attr.setShWidget(widget(ShSystemWidget.TEXT));
            attr.setShPostType(postType);
            entityManager.persist(attr);
        });
        return name;
    }

    /** A post-type whose {@code sections} field narrows to {@code target} by name (SH154). */
    private String typeReferencing(String prefix, String target) {
        String name = prefix + UUID.randomUUID().toString().substring(0, 8);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShPostType postType = new ShPostType();
            postType.setName(name);
            postType.setTitle(prefix);
            postType.setSystem((byte) 0);
            postType.setDate(EPOCH);
            postType.setOwner("admin");
            entityManager.persist(postType);

            ShPostTypeAttr attr = new ShPostTypeAttr();
            attr.setId(UUID.randomUUID().toString());
            attr.setName("sections");
            attr.setLabel("Sections");
            attr.setOrdinal(1);
            attr.setShWidget(widget(ShSystemWidget.MULTI_SELECT));
            attr.setWidgetSettings("{\"multiselect\":{\"references\":\"post\",\"postType\":\""
                    + target + "\"}}");
            attr.setShPostType(postType);
            entityManager.persist(attr);
        });
        return name;
    }

    private ShWidget widget(String name) {
        return entityManager
                .createQuery("select w from ShWidget w where w.name = :name", ShWidget.class)
                .setParameter("name", name)
                .getResultStream().findFirst().orElseGet(() -> {
                    ShWidget created = new ShWidget();
                    created.setName(name);
                    entityManager.persist(created);
                    return created;
                });
    }

    private void createPost(String typeName, String title) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShPostType postType = shPostTypeRepository.findByName(typeName);
            ShPostUnified post = new ShPostUnified();
            post.setId(UUID.randomUUID().toString());
            post.setState(ShPostState.DRAFT);
            post.setShSite(entityManager.find(ShSite.class, site.getId()));
            post.setShFolder(entityManager.find(ShFolder.class, root.getId()));
            post.setShPostType(postType);
            post.setTitle(title);
            post.setOwner("admin");
            post.setDate(EPOCH);
            post.setData("{}");
            entityManager.persist(post);
        });
    }

    private String idOf(String typeName) {
        return new TransactionTemplate(transactionManager)
                .execute(status -> shPostTypeRepository.findByName(typeName).getId());
    }

    private String settingsOf(String typeName, String field) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            List<ShPostTypeAttr> attrs = shPostTypeRepository.findByName(typeName)
                    .getShPostTypeAttrs().stream()
                    .filter(attr -> field.equals(attr.getName()))
                    .toList();
            return attrs.isEmpty() ? null : attrs.get(0).getWidgetSettings();
        });
    }
}
