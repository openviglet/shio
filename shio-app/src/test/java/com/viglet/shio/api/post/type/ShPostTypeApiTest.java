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

import org.junit.jupiter.api.Assertions;
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
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.widget.ShWidgetRepository;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;
import com.viglet.shio.widget.ShSystemWidget;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * SH117 — the one post-type surface, over HTTP.
 *
 * <p>Replaces {@code ShPostTypeConsoleAPITest}: the id-keyed, entity-shaped
 * {@code /api/v2/post/type} it covered is retired, and everything the console did
 * through it now goes through the name-keyed {@code /api/v2/post-type} the CLI and
 * the agent already used. The XP resolution endpoint ({@code /api/v2/post/xp/{id}})
 * is <em>not</em> part of that retirement and keeps its coverage here — it resolves
 * a referenced post for display, which is a different concept that merely shared a
 * path prefix.</p>
 *
 * <p>The interesting assertions are the ones about which writer a write claims to
 * be. {@code managed} absent means "code-owned" (the CLI's contract, which predates
 * the console sharing this path); {@code managed:false} means "hand edit", and a
 * hand edit against a code-owned type is a 409 rather than a silent overwrite. Get
 * that wrong in either direction and unifying the APIs either locks the console out
 * of every type it creates or lets it quietly win a race against the repository.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestMethodOrder(MethodOrderer.MethodName.class)
@TestInstance(Lifecycle.PER_CLASS)
class ShPostTypeApiTest {

    private static final String PATH = "/api/v2/post-type";
    /** The surface SH117 retired — nothing must answer here any more. */
    private static final String RETIRED_PATH = "/api/v2/post/type";

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private ShWidgetRepository shWidgetRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private MockMvc mockMvc;
    private Principal mockPrincipal;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String rootFolderId;
    private String postTypeName;

    @BeforeAll
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        mockPrincipal = Mockito.mock(Principal.class);
        Mockito.when(mockPrincipal.getName()).thenReturn("admin");

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            ShSite site = new ShSite();
            site.setName("ut-pt-api-site-" + UUID.randomUUID());
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

    /**
     * A post-type as the console sends one: the lossless def shape, with
     * {@code managed} explicitly false because a console save is a hand edit.
     */
    private ObjectNode consoleDef(String name, String title) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("name", name);
        payload.put("namePlural", name + "s");
        payload.put("title", title);
        payload.put("description", "post-type api test");
        payload.put("managed", false);
        ArrayNode fields = payload.putArray("fields");
        ObjectNode titleField = fields.addObject();
        titleField.put("name", ShSystemPostTypeAttr.TITLE);
        titleField.put("label", "Title");
        titleField.put("ordinal", 1);
        titleField.put("required", true);
        titleField.put("isTitle", true);
        titleField.put("isSummary", false);
        titleField.put("widget", ShSystemWidget.TEXT);
        return payload;
    }

    private String json(ObjectNode node) {
        return objectMapper.writeValueAsString(node);
    }

    @Test
    void stage01CreateConflictsOnDuplicateName() throws Exception {
        postTypeName = "PT-API-" + UUID.randomUUID();

        mockMvc.perform(post(PATH)
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(consoleDef(postTypeName, "Api Type"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(postTypeName))
                .andExpect(jsonPath("$.title").value("Api Type"))
                // A console write is a hand edit, so the type stays editable in the console.
                .andExpect(jsonPath("$.managed").value(false))
                .andExpect(jsonPath("$.fields.length()").value(1))
                .andExpect(jsonPath("$.fields[0].widget").value(ShSystemWidget.TEXT));

        // POST creates; it must not become an accidental overwrite of an existing model.
        mockMvc.perform(post(PATH)
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(consoleDef(postTypeName, "Second Attempt"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fix").exists());
    }

    @Test
    void stage02CreateWithoutANameTeaches() throws Exception {
        ObjectNode nameless = objectMapper.createObjectNode();
        nameless.put("title", "No API name");

        mockMvc.perform(post(PATH)
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(nameless)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fix").exists())
                .andExpect(jsonPath("$.example.name").exists());
    }

    @Test
    void stage03GetAndListServeTheFieldTree() throws Exception {
        mockMvc.perform(get(PATH + "/" + postTypeName).principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(postTypeName))
                .andExpect(jsonPath("$.fields[0].name").value(ShSystemPostTypeAttr.TITLE));

        mockMvc.perform(get(PATH).principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == '" + postTypeName + "')]").exists());
    }

    @Test
    void stage04UpdateReplacesFieldsWithoutEatingTheWidgetCatalog() throws Exception {
        ObjectNode payload = consoleDef(postTypeName, "Api Type v2");

        mockMvc.perform(put(PATH + "/" + postTypeName)
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Api Type v2"))
                .andExpect(jsonPath("$.fields.length()").value(1));

        Assertions.assertNotNull(shWidgetRepository.findFirstByName(ShSystemWidget.TEXT),
                "The shared Text widget must survive a post-type field replace");
    }

    @Test
    void stage05AnOmittedManagedFlagClaimsCodeOwnership() throws Exception {
        // The CLI's canonical serialization omits `managed`, and that has to keep
        // meaning "this model is owned by the repository" now that the console shares
        // the path — otherwise `shio push` would silently unlock every type it writes.
        String cliName = "PT-API-CLI-" + UUID.randomUUID();
        ObjectNode cliDef = consoleDef(cliName, "Pushed By Code");
        cliDef.remove("managed");

        mockMvc.perform(put(PATH + "/" + cliName)
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(cliDef)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.managed").value(true));

        // ...and a hand edit against it is refused rather than winning the race.
        mockMvc.perform(put(PATH + "/" + cliName)
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(consoleDef(cliName, "Hand Edited"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fix").exists());

        // The push itself still converges — the owner may keep writing.
        mockMvc.perform(put(PATH + "/" + cliName)
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(cliDef)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.managed").value(true));

        mockMvc.perform(delete(PATH + "/" + cliName).principal(mockPrincipal))
                .andExpect(status().isNoContent());
    }

    @Test
    void stage06APostIsCreatedByPostTypeName() throws Exception {
        // SH117: the console reads a name-keyed post-type and has no id to send, so
        // the unified create resolves `postType` server-side.
        String body = "{\"folderId\":\"" + rootFolderId + "\",\"postType\":\"" + postTypeName
                + "\",\"state\":\"DRAFT\",\"data\":{\"" + ShSystemPostTypeAttr.TITLE + "\":\"Named Type Post\"}}";
        String response = mockMvc.perform(post("/api/v2/post-unified")
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Named Type Post"))
                .andReturn().getResponse().getContentAsString();
        String postId = objectMapper.readTree(response).get("id").asString();

        // The XP endpoint is not part of SH117's retirement — it resolves a referenced
        // post for display and only ever shared a path prefix with the id-keyed twin.
        mockMvc.perform(get("/api/v2/post/xp/" + postId).principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shPost.title").value("Named Type Post"))
                .andExpect(jsonPath("$.shPost.shFolder.id").value(rootFolderId));

        mockMvc.perform(get("/api/v2/post/xp/" + UUID.randomUUID()).principal(mockPrincipal))
                .andExpect(status().isNotFound());
    }

    @Test
    void stage07AnUnresolvablePostTypeTeachesRatherThanFailingLater() throws Exception {
        String unknown = "{\"folderId\":\"" + rootFolderId
                + "\",\"postType\":\"NoSuchType\",\"state\":\"DRAFT\",\"data\":{}}";
        mockMvc.perform(post("/api/v2/post-unified")
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unknown))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.allowed").isArray())
                .andExpect(jsonPath("$.fix").exists());

        String neither = "{\"folderId\":\"" + rootFolderId + "\",\"state\":\"DRAFT\",\"data\":{}}";
        mockMvc.perform(post("/api/v2/post-unified")
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(neither))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fix").exists());
    }

    @Test
    void stage08SystemAndManagedFlagsAreDelivered() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        String managedName = "PT-MANAGED-" + UUID.randomUUID();
        tx.executeWithoutResult(status -> {
            ShPostType pt = new ShPostType();
            pt.setName(managedName);
            pt.setTitle("Code Owned");
            pt.setManaged(true);
            pt.setSystem((byte) 0);
            pt.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            pt.setOwner("admin");
            entityManager.persist(pt);
        });

        // The console renders read-only off this flag, so it must be on every read.
        mockMvc.perform(get(PATH + "/" + managedName).principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.managed").value(true))
                .andExpect(jsonPath("$.system").value(false));
    }

    @Test
    void stage09DeleteThenGetIsNotFound() throws Exception {
        String disposable = "PT-API-del-" + UUID.randomUUID();
        mockMvc.perform(post(PATH)
                        .principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(consoleDef(disposable, "Disposable"))))
                .andExpect(status().isCreated());

        mockMvc.perform(delete(PATH + "/" + disposable).principal(mockPrincipal))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(PATH + "/" + disposable).principal(mockPrincipal))
                .andExpect(status().isNotFound());
    }

    /**
     * The retirement itself. Leaving the id-keyed twin mapped while the console no
     * longer calls it is the failure mode SH117 exists to prevent — a second contract
     * that still answers, still has to be maintained, and is still a question an agent
     * has to resolve ("which one, and by id or by name?").
     */
    @Test
    void stage10TheIdKeyedTwinIsGone() throws Exception {
        mockMvc.perform(get(RETIRED_PATH).principal(mockPrincipal))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(RETIRED_PATH)
                        .principal(mockPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nope\"}"))
                .andExpect(status().isNotFound());
    }
}
