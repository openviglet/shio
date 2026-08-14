/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.staticfile;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.viglet.shio.property.ShFileSourceProperties;

class ShFileStorageServiceTest {

    @TempDir
    Path tempDir;

    private ShFileStorageService storage;

    @BeforeEach
    void setUp() {
        ShFileSourceProperties props = new ShFileSourceProperties();
        props.setPath(tempDir.toAbsolutePath().toString());
        // No meter: this exercises the storage layer, and storage has to work when nobody is
        // counting. That the bytes DO move SH45's meter is asserted in ShTenantStorageMeterTest,
        // where there is a database for it to move (SH751).
        storage = new ShFileStorageService(props, null);
    }

    // With no tenant bound on the thread (plain unit test), the storage roots at the
    // default "shio" tenant sub-directory: <root>/shio/<fileId>/<fileName> (SH47).
    private static final String DEFAULT_TENANT = "shio";

    @Test
    void storeThenResolveAndRead() throws Exception {
        byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);
        Path stored = storage.store("post-1", "doc.txt", new ByteArrayInputStream(bytes));

        assertTrue(Files.isRegularFile(stored));
        assertTrue(stored.startsWith(tempDir.resolve(DEFAULT_TENANT)));
        assertTrue(storage.exists("post-1", "doc.txt"));
        assertEquals("hello", Files.readString(storage.resolve("post-1", "doc.txt")));
    }

    @Test
    void storeStripsDirectoryComponentsFromName() {
        Path stored = storage.store("post-2", "/etc/evil.txt",
                new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)));
        assertEquals("evil.txt", stored.getFileName().toString());
        assertTrue(stored.startsWith(tempDir.resolve(DEFAULT_TENANT).resolve("post-2")));
    }

    @Test
    void resolveNeutralizesTraversalInFileName() {
        // A traversal file name is reduced to its last segment and stays under root.
        Path resolved = storage.resolve("post-3", "../../escape");
        assertTrue(resolved.startsWith(tempDir.resolve(DEFAULT_TENANT).resolve("post-3")));
        assertEquals("escape", resolved.getFileName().toString());
    }

    @Test
    void resolveRejectsTraversalInFileId() {
        assertThrows(IllegalArgumentException.class, () -> storage.resolve("../outside", "x.txt"));
        assertThrows(IllegalArgumentException.class, () -> storage.dirOf("../outside"));
    }

    @Test
    void sanitizeRejectsBlankAndDotNames() {
        assertThrows(IllegalArgumentException.class, () -> ShFileStorageService.sanitizeFileName(""));
        assertThrows(IllegalArgumentException.class, () -> ShFileStorageService.sanitizeFileName(".."));
        assertEquals("a.png", ShFileStorageService.sanitizeFileName("dir/sub/a.png"));
    }

    @Test
    void deleteRemovesTheIdDirectory() {
        storage.store("post-4", "a.txt", new ByteArrayInputStream("a".getBytes(StandardCharsets.UTF_8)));
        assertTrue(storage.exists("post-4", "a.txt"));

        storage.delete("post-4");

        assertFalse(storage.exists("post-4", "a.txt"));
        assertFalse(Files.exists(tempDir.resolve("post-4")));
    }

    @Test
    void deleteIsNoOpForUnknownId() {
        // Must not throw for a post that never had bytes (every delete fires this).
        assertDoesNotThrow(() -> storage.delete("never-stored"));
    }

    @Test
    void probeContentTypeUsesFileExtension() {
        Path png = storage.store("post-5", "image.png",
                new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)));
        assertEquals("image/png", storage.probeContentType(png));
    }
}
