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

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.viglet.shio.api.staticfile.ShFileStorageService;

/**
 * SH746 — a tenant's bytes are metered, and the meter can be put right (see SH45).
 *
 * <p>SH22 named three resources and built two. Sites and posts are a {@code count()} on an indexed,
 * {@code @TenantId}-filtered repository; storage has no such column, because SH399 stamps a file's
 * size as {@code "SIZE"} inside {@code ShPostUnified.data}, a VARCHAR of JSON. Both obvious
 * readings are O(files) on a path the quota check runs before every upload, so the figure is kept
 * on {@code sh_tenant} instead — and a kept figure needs a way back to the truth, which is what
 * {@code reconcile} is and what most of this asserts.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShTenantStorageMeterTest {

    private static final String TENANT = "shio";

    @Autowired
    private ShTenantStorageMeter meter;
    @Autowired
    private ShFileStorageService storage;

    @BeforeAll
    void reset() {
        meter.reconcile(TENANT);
    }

    @Test
    @DisplayName("SH746: a write and a removal move the kept figure, and it is read in O(1)")
    void theFigureFollowsTheBytes() {
        long before = meter.bytesFor(TENANT);

        meter.record(TENANT, 4096);
        assertThat(meter.bytesFor(TENANT))
                .as("SH746: nothing metered storage at all — the resource SH22 named and the one "
                        + "the half see SH110 waits on (see SH45)")
                .isEqualTo(before + 4096);

        meter.record(TENANT, -4096);
        assertThat(meter.bytesFor(TENANT)).isEqualTo(before);
    }

    @Test
    @DisplayName("SH746: the figure never reads below empty, whatever the drift")
    void itClampsAtZero() {
        // Arithmetically reachable after drift — two deletes of one file, or a delete of bytes
        // counted before the column existed. A meter reading below empty refuses nothing and
        // confuses everything, so the floor is that a tenant storing nothing stores zero.
        meter.reconcile(TENANT);
        meter.record(TENANT, -Long.MAX_VALUE / 2);

        assertThat(meter.bytesFor(TENANT))
                .as("SH746: a negative total is the one reading that cannot be true")
                .isZero();
    }

    @Test
    @DisplayName("SH746: reconcile replaces the kept figure with what is actually on disk")
    void reconcilePutsItRight() throws Exception {
        // The whole cost of choosing a maintained counter, and the reason it is affordable: SH47
        // gave every tenant its own directory, so the truth is one subtree away.
        meter.record(TENANT, 999_999);
        assertThat(meter.bytesFor(TENANT)).isNotEqualTo(storage.bytesOnDiskFor(TENANT));

        long reconciled = meter.reconcile(TENANT);

        assertThat(reconciled)
                .as("SH746: a kept number drifts — a crash between write and commit, a file removed "
                        + "by hand, an install that had files before this column existed. Without "
                        + "this the meter would have no way back to the truth")
                .isEqualTo(storage.bytesOnDiskFor(TENANT));
        assertThat(meter.bytesFor(TENANT)).isEqualTo(reconciled);
    }

    @Test
    @DisplayName("SH746: the on-disk recount sees a file's bytes, and an absent tree is zero")
    void theRecountIsRealAndSafeWhenEmpty() throws Exception {
        assertThat(storage.bytesOnDiskFor("ut-sh746-no-such-tenant"))
                .as("a tenant that has stored nothing has no directory, and that is zero rather "
                        + "than an error — reconcile must work on a fresh install")
                .isZero();

        long before = storage.bytesOnDiskFor(TENANT);
        Path dir = storage.rootDir().resolve(TENANT).resolve("ut-sh746-probe");
        Files.createDirectories(dir);
        Path file = dir.resolve("probe.bin");
        Files.write(file, new byte[1234]);
        try {
            assertThat(storage.bytesOnDiskFor(TENANT))
                    .as("SH746: the recount walks the tenant's own subtree, so a file written into "
                            + "it is counted — a walk that saw nothing would make reconcile a "
                            + "reliable way to zero every meter")
                    .isEqualTo(before + 1234);
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(dir);
            meter.reconcile(TENANT);
        }
    }
}
