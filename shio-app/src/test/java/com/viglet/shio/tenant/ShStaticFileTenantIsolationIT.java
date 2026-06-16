/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.viglet.shio.api.staticfile.ShFileStorageService;

/**
 * SH47 — proves the per-tenant {@code file_source} layout actually isolates bytes
 * on disk: the same file id stored under tenant "alpha" and tenant "beta" lands in
 * distinct {@code <root>/<tenant>/<fileId>} directories, and neither tenant can
 * read the other's bytes through {@link ShFileStorageService}.
 *
 * <p>Filesystem-only (no DB session), so the tenant is bound directly on the
 * thread via {@link ShTenantContext} rather than per-transaction. Points the
 * storage root at a throwaway temp directory.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
class ShStaticFileTenantIsolationIT {

    private static final Path ROOT = createTempRoot();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("shio.file-source.path", ROOT::toString);
        registry.add("shio.multi-tenant", () -> "true");
    }

    private static final String ALPHA = "alpha";
    private static final String BETA = "beta";
    private static final String FILE_ID = "post-123";
    private static final String FILE_NAME = "doc.txt";

    @Autowired
    private ShFileStorageService storage;

    @Test
    void sameFileIdIsIsolatedPerTenant() {
        asTenant(ALPHA, () -> storage.store(FILE_ID, FILE_NAME, bytes("ALPHA")));
        asTenant(BETA, () -> storage.store(FILE_ID, FILE_NAME, bytes("BETA")));

        // Each tenant reads back exactly its own content for the identical id + name.
        assertThat(asTenant(ALPHA, () -> read(storage.resolve(FILE_ID, FILE_NAME)))).isEqualTo("ALPHA");
        assertThat(asTenant(BETA, () -> read(storage.resolve(FILE_ID, FILE_NAME)))).isEqualTo("BETA");

        // The bytes live under distinct <root>/<tenant>/<fileId> directories.
        Path alphaPath = asTenant(ALPHA, () -> storage.resolve(FILE_ID, FILE_NAME));
        Path betaPath = asTenant(BETA, () -> storage.resolve(FILE_ID, FILE_NAME));
        assertThat(alphaPath).isNotEqualTo(betaPath);
        assertThat(alphaPath.startsWith(ROOT.resolve(ALPHA))).isTrue();
        assertThat(betaPath.startsWith(ROOT.resolve(BETA))).isTrue();

        // Deleting alpha's file leaves beta's bytes intact.
        asTenant(ALPHA, () -> {
            storage.delete(FILE_ID);
            return null;
        });
        assertThat(asTenant(ALPHA, () -> storage.exists(FILE_ID, FILE_NAME))).isFalse();
        assertThat(asTenant(BETA, () -> storage.exists(FILE_ID, FILE_NAME))).isTrue();
    }

    private <T> T asTenant(String tenant, Supplier<T> work) {
        ShTenantContext.setTenantId(tenant);
        try {
            return work.get();
        } finally {
            ShTenantContext.clear();
        }
    }

    private static ByteArrayInputStream bytes(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Path createTempRoot() {
        try {
            return Files.createTempDirectory("shio-sh47-it");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
