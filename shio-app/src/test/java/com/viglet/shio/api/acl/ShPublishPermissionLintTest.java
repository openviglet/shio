/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.acl;

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
 * SH995 — every method that changes what visitors see asks for PUBLISH, or is a declared exemption.
 *
 * <h2>The sweep before the fix (SH322)</h2>
 *
 * <p>SH995's finding is that publishing was never a permission of its own: {@code ShAclPermission} had
 * READ and WRITE, and {@code mayPublish} lived on an API token and returned true for every console
 * session — so the <em>agent</em>, whose work the review queue exists to gate, was the only actor the
 * product could stop from publishing, and a team had no separation between author and approver.</p>
 *
 * <p>The instrument lands before the fixes because the question "where does this product publish" has
 * no answer a reader can produce: it is thirteen call sites across six classes, and the one that is
 * missed is invisible — it does not fail, it silently lets somebody publish. So the calls are found
 * here, and each one either asks or is on a list with a reason.</p>
 *
 * <h2>Why a method scan and not a class one</h2>
 *
 * <p>{@code ShPostUnifiedAPI} publishes in three of its twenty methods, and a class-level check would
 * be satisfied by the {@code requireWrite} its <em>save</em> does — which is exactly the hole this
 * closes. The scan reads the enclosing method by brace counting, the way
 * {@code ShAgentOpAclLintTest} does one file over.</p>
 */
@ShSourceScan(ShSourceScan.Reads.CODE)
class ShPublishPermissionLintTest {

    private static final Path MAIN = Path.of("src/main/java/com/viglet/shio");

    /** What changing the live site looks like in this codebase. */
    private static final Pattern PUBLISHES = Pattern.compile(
            "shPostUnifiedWriteService\\.(publish|unpublish|publishScheduled|unpublishScheduled|schedule)\\(");

    /** What asking looks like. {@code requirePublish} is SH995's; the token gate is a second lock. */
    private static final String ASKS = "requirePublish";

    /**
     * Methods that publish without asking the content ACL, each with the reason it is not a defect.
     *
     * <p>An asserted list rather than a ceiling (SH527's rule): adding an entry is a decision somebody
     * writes down, and a new publish path that forgets the guard is a red build.</p>
     */
    private static final Map<String, String> EXEMPT = new LinkedHashMap<>();

    static {
        EXEMPT.put("ShPostUnifiedWriteService", "it IS the writer — the permission is asked by the "
                + "callers that decide to publish, and asking it here would also gate the SH15 sweep "
                + "and the rollback, which are not acting for a user at all");
        EXEMPT.put("ShPostScheduler", "the SH15 sweep runs on a timer as nobody; what it fires was "
                + "authorised when somebody scheduled it, and that call asks (SH995)");
        EXEMPT.put("ShReleaseRunner", "SH993's runner is called by the release ops, which ask, and by "
                + "the sweep, which is the entry above; asking here would gate a timer");
        EXEMPT.put("ShPostDraftMergeService", "the merge publishes only what its caller already "
                + "authorised — the console publish and the agent's op both ask before calling in");
    }

    @Test
    @DisplayName("SH995: a method that publishes asks for PUBLISH, or is on the list with a reason")
    void everyPublishAsksForThePermission() throws IOException {
        List<String> unguarded = new ArrayList<>();
        int publishing = 0;

        for (Map.Entry<Path, String> source : sources().entrySet()) {
            String file = source.getKey().getFileName().toString().replace(".java", "");
            String code = ShSourceText.code(source.getValue());
            for (String method : methodsThatPublish(code)) {
                publishing += 1;
                if (EXEMPT.containsKey(file) || method.contains(ASKS)) {
                    continue;
                }
                unguarded.add(file + "#" + signatureOf(method));
            }
        }

        // A walk that stops finding call sites passes for the wrong reason (SH322). Thirteen when
        // this shipped, across six classes; the floor is a floor, not the number.
        assertThat(publishing)
                .as("SH995: no publishing methods were found at all, so this asserted nothing")
                .isGreaterThan(8);

        assertThat(unguarded)
                .as("""
                        SH995: a method changes what visitors see without asking for PUBLISH. That \
                        is invisible from every side: nothing fails, the caller is allowed, and the \
                        separation between who writes and who approves is quietly not there. Ask \
                        `shContentAclService.requirePublish(<id>)`, or add the class to EXEMPT with \
                        the reason it publishes for somebody who already asked.""")
                .isEmpty();
    }

    @Test
    @DisplayName("SH995: the reader is not vacuous — it sees a guard and its absence")
    void theReaderActuallyReads() throws IOException {
        Map<Path, String> sources = sources();
        String api = code(sources, "ShPostUnifiedAPI.java");
        String runner = code(sources, "ShReleaseRunner.java");

        assertThat(methodsThatPublish(api))
                .as("SH995: the console's publish routes are the case this lint was written for")
                .isNotEmpty()
                .allSatisfy(method -> assertThat(method).contains(ASKS));
        assertThat(methodsThatPublish(runner))
                .as("""
                        SH995: and the release runner genuinely asks nothing — it is called by the                         ops, which ask, and by a timer, which is nobody. Without a witness for the                         other direction the reader could be returning whole files and still look                         right. ShPostUnifiedWriteService is NOT that witness: it publishes by                         calling itself, so the accessor this scans for never appears in it.""")
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
            int open = code.lastIndexOf('{', call.start());
            int start = enclosingMethod(code, call.start());
            if (start < 0 || open < 0) {
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
                "(?m)^ {4}(?:@\\w+\\s+)*(?:public|private|protected)[\\w<>,\\[\\] .]+ \\w+\\(")
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
                .orElseThrow(() -> new AssertionError("SH995: " + name + " is not where it was"));
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
