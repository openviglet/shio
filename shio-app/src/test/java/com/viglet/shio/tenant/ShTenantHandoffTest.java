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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The boundary SH612's defect crossed, asserted (SH527).
 *
 * <p>A <b>single-threaded</b> executor throughout, and not for speed: it is the shape
 * {@code ShTuringConfig} builds, and thread <em>reuse</em> is what makes the restore half of the
 * fix necessary rather than tidy. Two tasks on one thread is the whole hazard.</p>
 *
 * @author Alexandre Oliveira
 */
class ShTenantHandoffTest {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        ShTenantContext.clear();
    }

    @Test
    @DisplayName("SH612: a task runs under the tenant of the thread that submitted it")
    void theSubmittingThreadsTenantCrosses() throws Exception {
        // The defect exactly: the publish committed under `acme`, the incident was written on a
        // pool thread that had never heard of it, and the row went to `shio` — visible to the one
        // operator it does not concern and invisible to the one whose site failed.
        ShTenantContext.setTenantId("acme");
        AtomicReference<String> seen = new AtomicReference<>("unset");

        executor.submit(ShTenantHandoff.capture(() -> seen.set(ShTenantContext.getTenantId())))
                .get(5, TimeUnit.SECONDS);

        assertThat(seen.get())
                .as("""
                        @TenantId is resolved from this thread-local when the session opens, so \
                        the value here is the discriminator the incident row is stamped with \
                        (SH612)""")
                .isEqualTo("acme");
    }

    @Test
    @DisplayName("SH612: the pool thread is left as it was found, so the next task is not poisoned")
    void theFarThreadIsRestored() throws Exception {
        // The half that is not housekeeping. Setting and walking away would bind the tenant for
        // whatever the pool runs next on that thread — a foreign row CREATED rather than one
        // misfiled, which is strictly worse than the defect being repaired.
        executor.submit(() -> ShTenantContext.setTenantId("resident")).get(5, TimeUnit.SECONDS);

        ShTenantContext.setTenantId("visitor");
        executor.submit(ShTenantHandoff.capture(() -> {
        })).get(5, TimeUnit.SECONDS);

        AtomicReference<String> after = new AtomicReference<>("unset");
        executor.submit(() -> after.set(ShTenantContext.getTenantId())).get(5, TimeUnit.SECONDS);

        assertThat(after.get()).isEqualTo("resident");
    }

    @Test
    @DisplayName("SH612: an absent tenant clears the far thread rather than leaving the last one")
    void anAbsentCaptureClears() throws Exception {
        // A single-tenant install binds nothing, and ShTenantResolver falls back to the default
        // when nothing is bound — so "no tenant" has a meaning, and it is not "keep whatever the
        // previous task left behind". Without this branch the fix would work only for instances
        // that never run an untenanted publish, which is the shape hardest to notice in a test.
        executor.submit(() -> ShTenantContext.setTenantId("resident")).get(5, TimeUnit.SECONDS);

        ShTenantContext.clear();
        AtomicReference<String> seen = new AtomicReference<>("unset");
        executor.submit(ShTenantHandoff.capture(() -> seen.set(ShTenantContext.getTenantId())))
                .get(5, TimeUnit.SECONDS);

        assertThat(seen.get()).isNull();
    }

    @Test
    @DisplayName("SH612: the tenant is read when the task is wrapped, not when it runs")
    void theCaptureHappensAtWrapTime() throws Exception {
        // The one way to write this fix and still have the bug: reading the context inside the
        // lambda captures exactly the nothing the far thread already had. The submitting thread
        // moves on to another tenant here, which is what a pooled request thread does.
        ShTenantContext.setTenantId("acme");
        AtomicReference<String> seen = new AtomicReference<>("unset");
        Runnable probe = ShTenantHandoff.capture(() -> seen.set(ShTenantContext.getTenantId()));

        ShTenantContext.setTenantId("other");
        executor.submit(probe).get(5, TimeUnit.SECONDS);

        assertThat(seen.get())
                .as("the tenant that committed is the one that owns the incident, not whichever "
                        + "one the request thread went on to serve")
                .isEqualTo("acme");
    }

    @Test
    @DisplayName("SH612: runAs restores even when the body throws")
    void aThrowingBodyStillRestores() throws Exception {
        // Every caller of this is on a failure path — that is the only reason an incident is being
        // recorded at all — so "the body threw" is the expected case, not the edge one.
        executor.submit(() -> ShTenantContext.setTenantId("resident")).get(5, TimeUnit.SECONDS);

        AtomicReference<String> after = new AtomicReference<>("unset");
        executor.submit(() -> {
            try {
                ShTenantHandoff.runAs("visitor", () -> {
                    throw new IllegalStateException("the subscriber refused");
                });
            } catch (IllegalStateException expected) {
                // The point is what the thread looks like afterwards.
            }
            after.set(ShTenantContext.getTenantId());
        }).get(5, TimeUnit.SECONDS);

        assertThat(after.get()).isEqualTo("resident");
    }
}
