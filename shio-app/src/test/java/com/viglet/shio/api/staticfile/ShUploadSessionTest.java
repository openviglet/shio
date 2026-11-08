/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.staticfile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.Principal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.property.ShUploadSessionProperties;
import com.viglet.shio.tenant.ShTenantQuotaService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SH1355 — an upload that survives the link dropping.
 *
 * <p>The assertion this id is closed by: a session killed midway resumes from the offset the server
 * reports, and finishes as one asset whose digest is the declared one. Around it, the refusals that
 * keep a half upload from ever becoming an asset.</p>
 *
 * @author Alexandre Oliveira
 */
// The context several suites already share, so this spends no slot ShContextCacheHeadroomTest counts.
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShUploadSessionTest {

    private static final String BASE = "/api/v2/staticfile/upload-session";
    private static final Principal ALICE = () -> "alice-sh1355";
    private static final Principal BOB = () -> "bob-sh1355";

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ShStaticFileService shStaticFileService;
    @Autowired
    private ShUploadSessionProperties properties;
    @Autowired
    private ShTenantQuotaService shTenantQuotaService;
    @Autowired
    private ShUploadPolicy shUploadPolicy;

    @PersistenceContext
    private EntityManager entityManager;

    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mockMvc;
    private String folderId;
    private byte[] file;
    private String sha256;

    @BeforeAll
    void seed() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        file = new byte[300_000];
        new Random(1355).nextBytes(file);
        sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file));
        String name = "sh1355-" + UUID.randomUUID().toString().substring(0, 8);
        folderId = new TransactionTemplate(transactionManager).execute(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShSite site = new ShSite();
            site.setName(name);
            site.setFurl(name);
            site.setDate(when);
            site.setOwner("admin");
            entityManager.persist(site);
            ShFolder root = new ShFolder();
            root.setName(name);
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(when);
            root.setOwner("admin");
            entityManager.persist(root);
            return root.getId();
        });
    }

    private String begin(Principal who, String digest) throws Exception {
        String body = json.writeValueAsString(java.util.Map.of("name", "clip.bin", "size", file.length,
                "folderId", folderId, "sha256", digest, "contentType", "application/octet-stream"));
        MvcResult result = mockMvc.perform(post(BASE).principal(who).contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.offset").value(0))
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asString();
    }

    private MvcResult chunk(String id, Principal who, int from, int to) throws Exception {
        return mockMvc.perform(put(BASE + "/" + id).param("offset", Integer.toString(from)).principal(who)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(Arrays.copyOfRange(file, from, to)))
                .andReturn();
    }

    @Test
    @DisplayName("SH1355: a session killed midway resumes from the reported offset and finishes as one asset")
    void aKilledSessionResumesAndFinishes() throws Exception {
        String id = begin(ALICE, sha256);
        assertThat(chunk(id, ALICE, 0, 100_000).getResponse().getStatus()).isEqualTo(200);

        // The link drops mid-chunk: the client lost count and sends from the wrong place.
        MvcResult lost = chunk(id, ALICE, 200_000, 300_000);
        assertThat(lost.getResponse().getStatus()).as("SH1355: a gap is a 409, never appended").isEqualTo(409);
        assertThat(lost.getResponse().getContentAsString()).contains("upload-session-offset").contains("100000");

        // It asks where to resume, and a resent chunk that already landed is a no-op.
        JsonNode where = json.readTree(mockMvc.perform(get(BASE + "/" + id).principal(ALICE))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(where.get("offset").asLong()).isEqualTo(100_000);
        assertThat(where.get("complete").asBoolean()).isFalse();
        assertThat(chunk(id, ALICE, 0, 100_000).getResponse().getContentAsString()).contains("\"offset\":100000");

        // Finishing early is refused, so a half upload never becomes an asset.
        mockMvc.perform(post(BASE + "/" + id + "/finish").principal(ALICE)).andExpect(status().isConflict());

        assertThat(chunk(id, ALICE, 100_000, 300_000).getResponse().getContentAsString())
                .contains("\"complete\":true");
        JsonNode created = json.readTree(mockMvc.perform(post(BASE + "/" + id + "/finish").principal(ALICE))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(created.has("fileName") && !created.has("shPostType") && !created.has("data"))
                .as("SH1364: finish answers with the media library's row, as /upload does, not the entity")
                .isTrue();
        assertThat(created.get("size").asLong()).isEqualTo(file.length);

        ShStaticFileService.ShStaticFileDigest digest = shStaticFileService.digestOf(created.get("id").asString());
        assertThat(digest.sha256()).as("SH1355: the asset is the declared file").isEqualTo(sha256);
        assertThat(digest.size()).isEqualTo(file.length);
        mockMvc.perform(get(BASE + "/" + id).principal(ALICE))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("SH1355: bytes that do not hash to the declared digest never become an asset")
    void aWrongDigestIsRefused() throws Exception {
        String id = begin(ALICE, "0".repeat(64));
        chunk(id, ALICE, 0, file.length);
        mockMvc.perform(post(BASE + "/" + id + "/finish").principal(ALICE))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.fix").exists());
        mockMvc.perform(get(BASE + "/" + id).principal(ALICE)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("SH1355: a session is its owner's, more bytes than declared are refused, abort drops it")
    void ownershipOverflowAndAbort() throws Exception {
        String id = begin(ALICE, sha256);
        mockMvc.perform(get(BASE + "/" + id).principal(BOB)).andExpect(status().isNotFound());
        assertThat(chunk(id, BOB, 0, 10).getResponse().getStatus()).isEqualTo(404);

        byte[] tooMuch = new byte[file.length + 1];
        mockMvc.perform(put(BASE + "/" + id).param("offset", "0").principal(ALICE)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM).content(tooMuch))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(BASE + "/" + id).principal(ALICE))
                .andExpect(jsonPath("$.offset").value(0));

        mockMvc.perform(delete(BASE + "/" + id).principal(ALICE)).andExpect(status().isNoContent());
        mockMvc.perform(get(BASE + "/" + id).principal(ALICE)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("SH1355: a begin missing the digest is a teaching 400 before any byte is sent")
    void aBeginNeedsTheDigest() throws Exception {
        mockMvc.perform(post(BASE).principal(ALICE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"a.bin\",\"size\":3,\"folderId\":\"" + folderId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fix").exists());
    }

    @Test
    @DisplayName("SH1355: an abandoned session expires and is swept")
    void anAbandonedSessionIsSwept() {
        ShUploadSessionService now = new ShUploadSessionService(properties, shStaticFileService,
                shTenantQuotaService, shUploadPolicy, Clock.systemUTC());
        ShUploadSessionService.Status opened = now.begin(
                new ShUploadSessionService.Begin("old.bin", 3L, folderId, sha256, null), "carol-sh1355");
        now.append(opened.id(), 0, new ByteArrayInputStream(new byte[] {1}), "carol-sh1355");

        ShUploadSessionService later = new ShUploadSessionService(properties, shStaticFileService,
                shTenantQuotaService, shUploadPolicy,
                Clock.offset(Clock.systemUTC(), properties.getTtl().plus(Duration.ofMinutes(1))));
        assertThat(later.sweepExpired()).isPositive();
        assertThatThrownBy(() -> now.status(opened.id(), "carol-sh1355"))
                .isInstanceOf(ShUploadSessionService.UnknownSessionException.class);
    }
}
