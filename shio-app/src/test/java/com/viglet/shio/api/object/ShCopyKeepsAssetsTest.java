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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import com.viglet.shio.api.agent.ShAgentBatchService;
import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.agent.dto.ShAgentBatch.Op;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.site.ShSite;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH749 — a copy that leaves its assets behind is a copy that looks complete and is not.
 *
 * <p>{@code copyPost} gives every copy a fresh UUID and SH38 keys the bytes on exactly that id, so
 * a copied {@code File} post pointed at a directory nobody had written. Every surface reported
 * success: the row is there, the reference resolves, {@code verify} passes, the page renders — and
 * the image 404s at delivery, which is a replica's own worst case and the family SH470 and SH501
 * each spent a task on.</p>
 *
 * <p>The assertion is the one the filing asked for and it is deliberately an <b>HTTP</b> one: a
 * copied {@code File} answers 200 with its bytes. Checking the disk instead would be a test of the
 * fix's internals (SH527), and the defect was never that a file was missing from a directory — it
 * was that a reader asking for the asset did not get it.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShCopyKeepsAssetsTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private ShObjectWriteService shObjectWriteService;
    @Autowired
    private ShAgentBatchService shAgentBatchService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private MockMvc mockMvc;
    private Principal principal;
    private String siteName;
    private String rootFolderId;

    @BeforeAll
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        principal = Mockito.mock(Principal.class);
        Mockito.when(principal.getName()).thenReturn("admin");

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = new ShSite();
            siteName = "assets-copy-" + UUID.randomUUID();
            site.setName(siteName);
            site.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            site.setOwner("admin");
            entityManager.persist(site);

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            root.setOwner("admin");
            entityManager.persist(root);
            rootFolderId = root.getId();
        });
    }

    // --- helpers ---

    /** A folder under the site root, created the way the agent surface creates one. */
    private String folder(String name) {
        run(new Op(ShAgentBatchService.FOLDER_UPSERT, "folder:" + siteName + "/" + name,
                null, null, null));
        return folderId(name);
    }

    private void run(Op... ops) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                shAgentBatchService.run(new ShAgentBatch(false, List.of(ops)), true, "tester"));
    }

    /**
     * A child folder of the site root, by name.
     *
     * <p>{@code f.parentFolder.id} and not {@code f.parentFolder.name}: a folder is an
     * {@code ShObject} subclass, and navigating past {@code .id} makes Hibernate emit the
     * {@code @TenantId} predicate against a subclass alias that has no such column.</p>
     */
    private String folderId(String name) {
        return new TransactionTemplate(transactionManager).execute(status ->
                entityManager.createQuery(
                                "select f.id from ShFolder f where f.name = :n "
                                        + "and f.parentFolder.id = :p", String.class)
                        .setParameter("n", name)
                        .setParameter("p", rootFolderId)
                        .getResultStream().findFirst().orElse(null));
    }

    /** Uploads {@code name} into {@code folderId} and returns the File post's id. */
    private String upload(String folderId, String name, String content) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", name, "text/plain", content.getBytes(StandardCharsets.UTF_8));
        String response = mockMvc.perform(multipart("/api/v2/staticfile/upload")
                        .file(file).param("folderId", folderId).principal(principal))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(response, "$.id");
    }

    /**
     * The id of the one post in the folder named {@code folderName}.
     *
     * <p>Keyed on the folder id, never on {@code p.shFolder.name}: {@code ShFolder} is an
     * {@code ShObject} subclass, so navigating the association emits the {@code tenant_id}
     * predicate against an alias that has no such column (the SH22 hazard).</p>
     */
    private String onlyPostIn(String folderName, ShPostState state) {
        String id = folderId(folderName);
        return new TransactionTemplate(transactionManager).execute(status ->
                entityManager.createQuery(
                                "select p.id from ShPostUnified p where p.shFolder.id = :id "
                                        + "and p.state = :s", String.class)
                        .setParameter("id", id)
                        .setParameter("s", state)
                        .getResultStream().findFirst().orElse(null));
    }

    /** What `GET /api/v2/staticfile/{id}` answers — the reader's own question. */
    private String served(String postId) throws Exception {
        return mockMvc.perform(get("/api/v2/staticfile/" + postId).principal(principal))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    // --- the assertions ---

    @Test
    @DisplayName("SH749: a folder.copy'd asset answers 200 with its bytes")
    void anAgentCopiedAssetIsServed() throws Exception {
        String name = "ag-" + UUID.randomUUID().toString().substring(0, 8);
        String source = folder(name);
        upload(source, "logo.txt", "the bytes that must come along");

        run(new Op(ShAgentBatchService.FOLDER_COPY, "folder:" + siteName + "/" + name,
                null, null, "folder:" + siteName + "/" + name + "-copy", null));

        // The copy is a DRAFT (SH244), which is why this asks for the draft row rather than the
        // PUBLISHED one an upload writes.
        String copied = onlyPostIn(name + "-copy", ShPostState.DRAFT);
        assertThat(copied).as("the copy has no File post at all").isNotNull();
        assertThat(served(copied))
                .as("""
                        the row was copied and the bytes were not: storage is keyed on the post id \
                        and the copy has a fresh one, so every surface reported success and the \
                        asset 404'd at delivery""")
                .contains("the bytes that must come along");
    }

    @Test
    @DisplayName("SH749: the console's clone carries the bytes too — one writer, one fix")
    void aConsoleClonedAssetIsServed() throws Exception {
        String name = "cn-" + UUID.randomUUID().toString().substring(0, 8);
        String source = folder(name);
        upload(source, "logo.txt", "the console half of the same defect");

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                shObjectWriteService.copyTo(rootFolderId, List.of(source)));

        // The console's copy keeps the state rows it found, so the clone of a published upload is
        // published — the difference SH244's `asDrafts` flag exists for, asserted here so the two
        // surfaces staying different stays deliberate.
        String copied = onlyPostIn(name + " (2)", ShPostState.PUBLISHED);
        assertThat(copied).as("the clone has no File post at all").isNotNull();
        assertThat(served(copied)).contains("the console half of the same defect");
    }

    @Test
    @DisplayName("SH749: the copy owns its bytes — purging the source does not blank it")
    void theCopyOwnsItsBytes() throws Exception {
        String name = "ow-" + UUID.randomUUID().toString().substring(0, 8);
        String source = folder(name);
        String uploaded = upload(source, "logo.txt", "bytes that outlive their original");

        run(new Op(ShAgentBatchService.FOLDER_COPY, "folder:" + siteName + "/" + name,
                null, null, "folder:" + siteName + "/" + name + "-copy", null));
        String copied = onlyPostIn(name + "-copy", ShPostState.DRAFT);

        mockMvc.perform(delete("/api/v2/post-unified/" + uploaded).principal(principal))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v2/post-unified/" + uploaded + "/purge").principal(principal))
                .andExpect(status().isNoContent());

        assertThat(served(copied))
                .as("""
                        the cheap fix is to point the copy at the source's directory, and this is \
                        what it costs: ShStaticFileCleanupListener frees a directory on DELETE, so \
                        purging one asset would blank every copy of it""")
                .contains("bytes that outlive their original");
        mockMvc.perform(get("/api/v2/staticfile/" + uploaded).principal(principal))
                .andExpect(status().isNotFound());
    }
}
