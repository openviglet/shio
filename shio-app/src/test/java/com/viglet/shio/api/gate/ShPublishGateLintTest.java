/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.gate;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.lint.ShSourceScan;
import com.viglet.shio.lint.ShSourceText;

/**
 * SH1084 — every method that publishes evaluates the site's publish gates, or is a declared
 * exemption.
 *
 * <h2>The instrument before the fixes (SH338)</h2>
 *
 * <p>SH997 put the gate "where the publish happens" and named three surfaces. The review queue's
 * Approve and the editor's save-and-publish were two more, and nothing found them, because nothing
 * looked: a gate a surface skips does not fail, it reads as enforced. So the calls are found here,
 * and each one either evaluates the gate or is on the list with the reason it does not.</p>
 *
 * <p>Method-level, the way {@code ShPublishPermissionLintTest} is: {@code ShAgentBatchService} gates
 * its publish op and not its redirect write, and a class-level check could not tell them apart.</p>
 */
@ShSourceScan(ShSourceScan.Reads.CODE)
class ShPublishGateLintTest {

    private static final Path MAIN = Path.of("src/main/java/com/viglet/shio");

    /** What a publish looks like: the writer's promotion of a draft. */
    private static final Pattern PUBLISHES = Pattern.compile("shPostUnifiedWriteService\\.publish\\(");

    /** What evaluating the gate looks like: {@code requirePost} or {@code require} on the service. */
    private static final String ASKS = "shPublishGateService.require";

    /**
     * Methods that publish without evaluating a gate, keyed {@code Class#method}, each with its reason.
     *
     * <p>A named list, never a count (SH527): an entry is a decision somebody wrote down, and a new
     * publish path that forgets the gate is a red build.</p>
     */
    private static final Map<String, String> EXEMPT = new LinkedHashMap<>();

    static {
        EXEMPT.put("ShAgentBatchService#postRedirect", "a redirect post carries no content a gate "
                + "judges; SH1064 writes it beside a publish that was itself gated");
        EXEMPT.put("ShReleaseRunner#run", "the release op evaluates the gate for every publish in the "
                + "plan before calling in; the scheduled sweep calling it ungated is SH1138");
        EXEMPT.put("ShReleaseRunner#rollback", "puts back what was live before the release, which is "
                + "content that already went through a publish");
        EXEMPT.put("ShReviewService#revertOne", "a revert restores the version that was live before "
                + "the run; gating it could leave the run's own change standing");
    }

    @Test
    @DisplayName("SH1084: a method that publishes evaluates the publish gate, or is on the list")
    void everyPublishEvaluatesTheGate() throws IOException {
        List<String> ungated = new ArrayList<>();
        List<String> found = new ArrayList<>();

        for (Map.Entry<Path, String> source : sources().entrySet()) {
            String file = source.getKey().getFileName().toString().replace(".java", "");
            String code = ShSourceText.code(source.getValue());
            for (String method : methodsThatPublish(code)) {
                String key = file + "#" + signatureOf(method);
                found.add(key);
                if (EXEMPT.containsKey(key) || method.contains(ASKS)) {
                    continue;
                }
                ungated.add(key);
            }
        }

        System.out.printf("%n[SH1084] %d methods publish: %s%n  exempt: %s%n%n", found.size(), found,
                EXEMPT.keySet());

        assertThat(found)
                .as("SH1084: no publishing methods were found, so this asserted nothing")
                .hasSizeGreaterThan(6);
        assertThat(found)
                .as("SH1084: an exemption naming a method that no longer publishes is a list nobody "
                        + "reads; delete it")
                .containsAll(EXEMPT.keySet());
        assertThat(ungated)
                .as("""
                        SH1084: a method publishes without evaluating the site's publish gates. \
                        Nothing fails and nothing warns: the gate simply does not run on this \
                        path, which reads as enforced from every other. Call \
                        `shPublishGateService.requirePost(<id>, <waiver>)` before the publish, or add \
                        the method to EXEMPT with the reason it needs none.""")
                .isEmpty();
    }

    @Test
    @DisplayName("SH1084: the reader is not vacuous — it sees a gate and its absence")
    void theReaderActuallyReads() throws IOException {
        Map<Path, String> sources = sources();
        assertThat(methodsThatPublish(code(sources, "ShPostUnifiedAPI.java")))
                .as("SH1084: the console's publish button is SH997's own gated surface")
                .anySatisfy(method -> assertThat(method).contains(ASKS));
        assertThat(methodsThatPublish(code(sources, "ShReleaseRunner.java")))
                .as("SH1084: and the release runner asks nothing, which is why it is listed")
                .isNotEmpty()
                .allSatisfy(method -> assertThat(method).doesNotContain(ASKS));
    }

    /** Every method body in this source that publishes, by brace counting from its signature. */
    private static List<String> methodsThatPublish(String code) {
        List<String> bodies = new ArrayList<>();
        Matcher call = PUBLISHES.matcher(code);
        int lastEnd = -1;
        while (call.find()) {
            if (call.start() < lastEnd) {
                continue;
            }
            int start = enclosingMethod(code, call.start());
            if (start < 0) {
                continue;
            }
            String body = bodyFrom(code, start);
            lastEnd = start + body.length();
            bodies.add(body);
        }
        return bodies;
    }

    /** The start of the method a position sits in: the last signature line above it. */
    private static int enclosingMethod(String code, int at) {
        Matcher signature = Pattern.compile(
                "(?m)^ {4}(?:@\\w+\\s+)*(?:public|private|protected)[\\w<>,?&\\[\\] .]+ \\w+\\(")
                .matcher(code);
        int start = -1;
        while (signature.find() && signature.start() < at) {
            start = signature.start();
        }
        return start;
    }

    private static String bodyFrom(String code, int start) {
        int open = code.indexOf('{', start);
        if (open < 0) {
            return "";
        }
        int depth = 0;
        for (int at = open; at < code.length(); at += 1) {
            if (code.charAt(at) == '{') {
                depth += 1;
            } else if (code.charAt(at) == '}') {
                depth -= 1;
                if (depth == 0) {
                    return code.substring(start, at + 1);
                }
            }
        }
        return code.substring(start);
    }

    private static String signatureOf(String method) {
        int paren = method.indexOf('(');
        int space = method.lastIndexOf(' ', paren);
        return paren < 0 || space < 0 ? method.substring(0, Math.min(40, method.length()))
                : method.substring(space + 1, paren);
    }

    private static String code(Map<Path, String> sources, String name) {
        return sources.entrySet().stream()
                .filter(entry -> entry.getKey().getFileName().toString().equals(name))
                .map(entry -> ShSourceText.code(entry.getValue()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("SH1084: " + name + " is not where it was"));
    }

    private static Map<Path, String> sources() throws IOException {
        try (Stream<Path> files = Files.walk(MAIN)) {
            Map<Path, String> byPath = new LinkedHashMap<>();
            files.filter(path -> path.toString().endsWith(".java")).sorted().forEach(path -> {
                try {
                    byPath.put(path, Files.readString(path));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            return byPath;
        }
    }
}
