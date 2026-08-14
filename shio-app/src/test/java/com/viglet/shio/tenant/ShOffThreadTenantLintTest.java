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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.lint.ShSourceScan;
import com.viglet.shio.lint.ShSourceText;

/**
 * Every thread boundary in the application, classified (SH612).
 *
 * <h2>Why this is the deliverable and not the fix</h2>
 *
 * <p>SH612 was found by reading two recorders. What decides whether reading two was enough is a
 * question a reading cannot answer — <b>does any other {@code @TenantId} write happen off-thread?</b>
 * — and SH338's rule is that when a review finds a law being broken, the instrument that finds
 * every <em>other</em> break lands before the fixes. {@link ShTenantContext} is a thread-local and
 * {@code @TenantId} is resolved from it when a Hibernate session opens, so <b>every</b> hand-off to
 * another thread is a place a write can be filed under the wrong tenant, silently, with no test
 * that would notice.</p>
 *
 * <h2>An asserted list, not a refusal</h2>
 *
 * <p>Same shape as {@code ShAgentP1ConformanceTest} and {@code ShTenantJoinLintTest}: a boundary
 * that does not carry the tenant is sometimes correct, so the answer is a list somebody signed
 * rather than a rule that says no. What the list cannot do is grow without a reviewable diff, and
 * that is the whole property — the two defects SH612 fixed were each invisible until somebody
 * happened to read the file.</p>
 *
 * <h2>Comments are stripped first</h2>
 *
 * <p>Via {@link ShSourceText} (SH613), because they are where this project explains its thread
 * boundaries, and a lint that fires on
 * the sentence describing a hazard rather than on the hazard is one nobody keeps. {@code
 * ShScheduleProperties} is the live example: it names {@code @Scheduled} in a javadoc and hands
 * nothing to any thread.</p>
 *
 * @author Alexandre Oliveira
 */
@ShSourceScan(ShSourceScan.Reads.CODE)
class ShOffThreadTenantLintTest {

    private static final Path SOURCE = Path.of("src/main/java/com/viglet/shio");

    /**
     * What "another thread will run this" looks like in source.
     *
     * <p>Deliberately broader than what the codebase uses today — {@code @Async} and
     * {@code CompletableFuture.runAsync} appear nowhere yet, and a lint that only knows the
     * constructs already present would pass on the day somebody introduces the next one.</p>
     */
    private static final List<String> BOUNDARIES = List.of(
            "whenComplete(", "thenRun(", "thenApply(", "thenAccept(", "Async(", "@Async",
            "ExecutorService", "java.util.concurrent.Executor", "new Thread(", "@Scheduled");

    /**
     * Boundaries that deliberately do not carry a tenant, and why.
     *
     * <p>Each is a statement somebody has to disagree with in a diff, which is the point. Two of
     * the three are structural; the third is a defect this task did not cause and does not fix.</p>
     */
    private static final Map<String, String> DECLARED = Map.of(
            "turing/ShTuringConfig.java",
            """
            Builds the executor and names its thread. It creates the pool; it submits no work, so \
            there is no tenant in scope to carry and nothing that could write one. The submit site \
            is ShTuringIndexListener, which does carry it.""",

            "schedule/ShPostScheduler.java",
            """
            The sweep has no tenant by construction — it is a timer, not a request — so there \
            is nothing for ShTenantHandoff.capture to capture. SH639 fixed the consequence \
            the other way round: the sweep now ITERATES the tenants and runs one pass under \
            each, through runAs rather than by binding the context itself, because the \
            timer's thread is reused. Still declared rather than detected, because what makes \
            it correct is the iteration and this lint can only see the boundary.""",

            "tenant/ShTenantApiCallMeter.java",
            """
            SH45's flush names its tenant in the row it writes, so there is nothing for the \
            context to carry. The accumulator's key IS (tenant, period, surface), captured on \
            the request thread where the tenant was still bound, and sh_tenant_api_call is \
            deliberately not @TenantId-scoped — it is the global usage catalog, like sh_tenant \
            itself. Binding the context on the timer's thread would change nothing about which \
            row is written and would add the reuse hazard ShTenantHandoff exists to close.""");

    @Test
    @DisplayName("SH612: every thread boundary either carries the tenant or is declared")
    void everyBoundaryIsClassified() throws IOException {
        List<String> boundaries = new ArrayList<>();
        List<String> uncarried = new ArrayList<>();
        for (Path file : sources()) {
            String body = ShSourceText.code(Files.readString(file, StandardCharsets.UTF_8));
            if (BOUNDARIES.stream().noneMatch(body::contains)) {
                continue;
            }
            String name = relative(file);
            boundaries.add(name);
            if (body.contains("ShTenantHandoff") || DECLARED.containsKey(name)) {
                continue;
            }
            uncarried.add(name);
        }

        // A walk that found nothing passes this lint perfectly, and that is the failure mode of
        // every source lint: a moved directory or a tightened pattern turns the instrument off
        // and reports the same green. So it names the two files SH612 actually fixed.
        assertThat(boundaries)
                .as("the scan reaches the boundaries this task repaired; an empty walk is a lint "
                        + "that has stopped looking rather than a codebase that has stopped doing it")
                .contains("webhook/ShWebhookDispatcher.java", "turing/ShTuringIndexListener.java");

        assertThat(uncarried)
                .as("""
                        A hand-off to another thread is a place ShTenantContext is empty, and \
                        @TenantId is read from it when the session opens — so a write on the far \
                        side is stamped `shio` whoever published. That is invisible: the row \
                        exists, nothing throws, and the only reader who would notice is the tenant \
                        who cannot see it. Carry it with ShTenantHandoff, or declare it here with \
                        the reason it does not need one (SH612).""")
                .isEmpty();
    }

    @Test
    @DisplayName("SH612: a declared exemption names a file that still exists")
    void exemptionsDoNotOutliveTheirFiles() {
        // The failure mode of every signed list: the file moves, the entry stays, and the next
        // reader trusts a line that classifies nothing. Same guard NOT_DRAWN_HERE carries in
        // shio-sections' field-reachability lint, for the same reason.
        for (String declared : DECLARED.keySet()) {
            assertThat(SOURCE.resolve(declared))
                    .as("%s is exempted here and no longer exists — an exemption for a file that "
                            + "is gone is a line nobody removes", declared)
                    .exists();
        }
    }

    private static List<Path> sources() throws IOException {
        try (Stream<Path> walk = Files.walk(SOURCE)) {
            return walk.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }

    private static String relative(Path file) {
        return SOURCE.relativize(file).toString().replace('\\', '/');
    }
}
