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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.property.ShAssetRenditionProperties;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH1035 — a replace can be undone, and the copies it keeps are not public.
 *
 * <h2>The three assertions §LXX.7 asks for, and one it does not</h2>
 *
 * <p>The design names three: replace an image, read the new bytes behind the old id, and revert.
 * They are here, and so is a fourth that matters more than any of them — <b>the superseded bytes
 * must not be reachable from {@code /file_source/**}</b>. That path is mapped at the root of the
 * asset tree, so the obvious place to keep a rendition (a sub-directory of the asset's own) would
 * publish it: a document replaced because it said the wrong thing would stay downloadable by anyone
 * who guessed the path. Nothing about that looks wrong in the code that writes it, which is exactly
 * why it is asserted rather than remembered.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShAssetRenditionTest {

    private static final Path FILE_SOURCE = createTempRoot("sh1035-file-source");
    private static final Path RENDITIONS = createTempRoot("sh1035-renditions");

    private static Path createTempRoot(String prefix) {
        try {
            return Files.createTempDirectory(prefix);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void paths(DynamicPropertyRegistry registry) {
        registry.add("shio.file-source.path", () -> FILE_SOURCE.toAbsolutePath().toString());
        registry.add("shio.asset-renditions.path", () -> RENDITIONS.toAbsolutePath().toString());
        // Two, so the prune is observable at all: with the shipped three, a test would have to
        // replace four times to see the oldest go, and the assertion would be about the loop.
        registry.add("shio.asset-renditions.keep", () -> "2");
    }

    @Autowired
    private ShStaticFileService shStaticFileService;
    @Autowired
    private ShAssetRenditionStore renditions;
    @Autowired
    private ShAssetRenditionProperties properties;
    @Autowired
    private ShFileStorageService storage;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String folderId;

    @BeforeAll
    void setup() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = new ShSite();
            site.setName("rendition-" + UUID.randomUUID());
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
            folderId = root.getId();
        });
    }

    /** An asset holding {@code body}, through the real upload path. */
    private String upload(String name, String body) {
        MockMultipartFile file = new MockMultipartFile("file", name, "text/plain",
                body.getBytes(StandardCharsets.UTF_8));
        return new TransactionTemplate(transactionManager)
                .execute(status -> shStaticFileService.upload(file, folderId, "admin").getId());
    }

    private void replace(String id, String name, String body) {
        MockMultipartFile file = new MockMultipartFile("file", name, "text/plain",
                body.getBytes(StandardCharsets.UTF_8));
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> shStaticFileService.replace(file, id, "admin"));
    }

    private String liveBytes(String id, String name) {
        try {
            return Files.readString(storage.resolve(id, name), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("SH1035: a replace puts the new bytes behind the same id and keeps the old ones")
    void replaceKeepsTheSupersededBytes() {
        String id = upload("notice.txt", "the first version");
        replace(id, "notice.txt", "the second version");

        // The whole point of replace over delete-and-re-upload: the id did not change, so every
        // field pointing at this asset still resolves.
        assertThat(liveBytes(id, "notice.txt")).isEqualTo("the second version");

        List<ShAssetRendition> kept = shStaticFileService.listRenditions(id);
        assertThat(kept).singleElement().satisfies(rendition -> {
            assertThat(rendition.number()).isEqualTo(1);
            assertThat(rendition.fileName()).isEqualTo("notice.txt");
            assertThat(rendition.size()).isEqualTo("the first version".length());
        });
    }

    @Test
    @DisplayName("SH1035: a revert restores the old bytes, and is itself undoable")
    void revertRestoresAndIsUndoable() {
        String id = upload("policy.txt", "version one");
        replace(id, "policy.txt", "version two");

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                shStaticFileService.revertToRendition(id, 1, "admin"));
        assertThat(liveBytes(id, "policy.txt")).isEqualTo("version one");

        // The property that makes a revert safe to offer: reverting captured the bytes it replaced,
        // so a curator who went back to the wrong version can come forward again. Without it the
        // undo destroys the thing it was meant to protect.
        assertThat(shStaticFileService.listRenditions(id))
                .extracting(ShAssetRendition::number)
                .contains(2);
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                shStaticFileService.revertToRendition(id, 2, "admin"));
        assertThat(liveBytes(id, "policy.txt")).isEqualTo("version two");
    }

    @Test
    @DisplayName("SH1035: a revert re-stamps the digest, so no reader trusts a stale hash")
    void revertRestampsTheDigest() {
        String id = upload("stamped.txt", "aaaa");
        String first = shStaticFileService.digestOf(id).sha256();
        replace(id, "stamped.txt", "bbbbbbbb");
        String second = shStaticFileService.digestOf(id).sha256();
        assertThat(second).isNotEqualTo(first);

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                shStaticFileService.revertToRendition(id, 1, "admin"));

        // SH398's silent no-op, reached from the other direction: a digest that outlived its bytes
        // makes every cache-aware reader — `shio pull --content` most of all — believe an asset it
        // just changed is untouched.
        assertThat(shStaticFileService.digestOf(id).sha256()).isEqualTo(first);
    }

    @Test
    @DisplayName("SH1035: superseded bytes are NOT under the publicly served asset tree")
    void renditionsAreNotPubliclyReachable() {
        String id = upload("confidential.txt", "the wrong text");
        replace(id, "confidential.txt", "the right text");

        Path assetDir = storage.dirOf(id);
        Path renditionDir = renditions.dirFor(id);

        assertThat(shStaticFileService.listRenditions(id)).isNotEmpty();
        // `/file_source/**` is mapped at the root of the asset tree (ShStaticResourceConfiguration),
        // so anything under it is downloadable by anyone who guesses the path. This is the assertion
        // that keeps the safety net from becoming a disclosure.
        assertThat(renditionDir.startsWith(properties.getRootDir()))
                .as("renditions must live under their own root")
                .isTrue();
        assertThat(renditionDir.startsWith(storage.rootDir()))
                .as("""
                        a rendition under the file_source root is PUBLIC: /file_source/** is mapped \
                        at that root, so a superseded document stays downloadable to anyone who \
                        guesses the path. Keep them in shio.asset-renditions.path, which has no \
                        resource handler over it.""")
                .isFalse();
        assertThat(assetDir.startsWith(storage.rootDir())).isTrue();
    }

    @Test
    @DisplayName("SH1035: the kept count is bounded, because these bytes are outside the meter")
    void thePruneIsBounded() {
        String id = upload("rolling.txt", "v1");
        replace(id, "rolling.txt", "v2");
        replace(id, "rolling.txt", "v3");
        replace(id, "rolling.txt", "v4");

        // keep = 2 here. The bound is what licenses excluding these bytes from the tenant meter
        // (SH110's difference rule would otherwise make a replace GROW a tenant's usage), so the
        // cost is a function of the asset's size and not of how often somebody replaces it.
        List<ShAssetRendition> kept = shStaticFileService.listRenditions(id);
        assertThat(kept).hasSize(2);
        // Newest first, and the numbers keep climbing after a prune: the oldest going away must
        // not renumber what is left, or a revert a dialog offered would restore something else.
        assertThat(kept).extracting(ShAssetRendition::number).containsExactly(3, 2);
    }

    @Test
    @DisplayName("SH1035: an unknown rendition is a teaching refusal, not a silent no-op")
    void anUnknownRenditionTeaches() {
        String id = upload("lonely.txt", "only ever this");

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> shStaticFileService.revertToRendition(id, 9, "admin")))
                .hasMessageContaining("No rendition 9")
                .hasMessageContaining("/renditions")
                // P6: the refusal carries what the caller needs to get it right, which here is the
                // instance's own ceiling — "keeps 2 per asset" explains an absence a list does not.
                .hasMessageContaining("keeps 2 per asset");
    }

    @Test
    @DisplayName("SH1035: a replace on an asset with no bytes on disk supersedes nothing")
    void nothingToKeepKeepsNothing() {
        String id = upload("vanishing.txt", "here for now");
        storage.delete(id);

        replace(id, "vanishing.txt", "back again");

        // An empty rendition would offer a revert that restores zero bytes, which is worse than
        // offering no revert: the curator would take it.
        assertThat(shStaticFileService.listRenditions(id)).isEmpty();
        assertThat(liveBytes(id, "vanishing.txt")).isEqualTo("back again");
    }
}
