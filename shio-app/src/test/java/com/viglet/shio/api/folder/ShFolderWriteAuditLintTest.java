/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.folder;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SH196 — every folder write records an audit row, and this is what says so.
 *
 * <h2>Why a lint and not four assertions</h2>
 *
 * <p>The defect was not that a particular write forgot to record; it was that <em>nothing
 * asked</em>. SH200 added the audit call to {@code delete} and {@code restore} and left
 * {@code create}, {@code update} and {@code move} silent for four more tasks, because a
 * missing audit row is invisible: the write succeeds, the tests pass, the console shows the
 * folder, and the only symptom is a change feed that describes a site's contents and never its
 * shape. So the instrument is the deliverable (SH338's rule) — the next mutating method added
 * here fails the build unless it records, which is a question no behavioural test can ask about
 * code that does not exist yet.</p>
 *
 * <h2>Why it reads the source</h2>
 *
 * <p>The property is "every mutating method reaches the audit", and reflection can enumerate the
 * methods but cannot see what they call. The check is therefore textual and deliberately blunt:
 * for each {@code @Transactional} method that is not {@code readOnly}, the body must contain a
 * call to {@link ShFolderWriteService}'s single audit helper. Being blunt is the point — a
 * cleverer check that resolved call graphs would be a second implementation to be wrong.</p>
 *
 * @author Alexandre Oliveira
 */
class ShFolderWriteAuditLintTest {

    private static final Path SOURCE = Paths.get("src/main/java/com/viglet/shio/api/folder",
            "ShFolderWriteService.java");

    /** The one call site every mutating method has to reach. */
    private static final String AUDIT_CALL = "recordFolder(";

    /**
     * Mutating methods that record nothing, each with the reason it is right that they do not.
     *
     * <p>Exemptions are named here rather than inferred, so adding one is an edit somebody has
     * to justify in a diff. An empty-ish list is the healthy state; a growing one is the signal
     * that the rule has stopped meaning anything.</p>
     */
    private static final Map<String, String> EXEMPT = Map.of(
            "purge", "mirrors ShPostUnifiedWriteService.purge: the DELETE that had to precede it "
                    + "is the audited row, and a purge of an already-audited trash entry would "
                    + "record the same section leaving twice");

    @Test
    void everyMutatingFolderWriteRecordsAnAuditRow() throws IOException {
        String source = Files.readString(SOURCE, StandardCharsets.UTF_8);
        List<String> methods = mutatingMethods(source);

        // The lint is worthless if it found nothing to check — a rename of the annotation, or of
        // the file, would otherwise turn it green for the wrong reason.
        assertThat(methods)
                .as("mutating methods found in %s", SOURCE)
                .contains("create", "update", "move", "delete", "restore");

        List<String> silent = new ArrayList<>();
        for (String method : methods) {
            if (EXEMPT.containsKey(method)) {
                continue;
            }
            if (!bodyOf(source, method).contains(AUDIT_CALL)) {
                silent.add(method);
            }
        }

        assertThat(silent)
                .as("a folder write that records nothing is invisible to the SH88 change feed and "
                        + "has nothing for the SH109 queue to revert. Call %s, or add the method "
                        + "to EXEMPT with the reason it is right to stay silent", AUDIT_CALL)
                .isEmpty();
    }

    @Test
    @DisplayName("SH243: an annotation named in prose is not a method that forgot to record")
    void javadocMentioningTheAnnotationIsNotAMutatingMethod() {
        // Guards the guard, in the direction that actually broke it. The real service's five
        // methods are asserted above; what nothing asked was whether a *sentence* could invent a
        // sixth, and one did — the lint failed naming "@Transactional", which is not a method.
        String prose = """
                public class Example {

                    /**
                     * A throw out of an inner {@code @Transactional} call leaves the transaction
                     * rollback-only, so catching it loses the row.
                     */
                    @Transactional
                    public boolean restore(String id) {
                        return recordFolder(id);
                    }
                }
                """;

        assertThat(mutatingMethods(prose))
                .as("the annotation on line 6 is the only one; the one on line 4 is prose about it")
                .containsExactly("restore");
    }

    /**
     * Method names carrying {@code @Transactional} without {@code readOnly = true}.
     *
     * <p><b>Prose is skipped, since SH243.</b> This scanned every occurrence of the string, so the
     * first javadoc in this service to write <code>{@literal {@code @Transactional}}</code> — in a
     * sentence about transaction propagation, which is exactly the subject a method here has reason
     * to explain — was read as an annotation, and the token before the next {@code (} was reported
     * as a mutating method that records nothing. The lint failed naming {@code "@Transactional"},
     * about a method that does not exist. {@code ShAgentTransactionalOverloadLintTest} skips comment
     * lines for the same reason and had already paid for the lesson; this is that fix, here.</p>
     */
    private static List<String> mutatingMethods(String source) {
        List<String> names = new ArrayList<>();
        int at = source.indexOf("@Transactional");
        while (at >= 0) {
            int lineEnd = source.indexOf('\n', at);
            String annotation = source.substring(at, lineEnd < 0 ? source.length() : lineEnd);
            if (!annotation.contains("readOnly") && !inComment(source, at)) {
                String name = methodNameAfter(source, lineEnd);
                if (name != null) {
                    names.add(name);
                }
            }
            at = source.indexOf("@Transactional", at + 1);
        }
        return names;
    }

    /** Whether the occurrence at {@code at} sits on a comment or javadoc line. */
    private static boolean inComment(String source, int at) {
        int lineStart = source.lastIndexOf('\n', at) + 1;
        String before = source.substring(lineStart, at).stripLeading();
        return before.startsWith("*") || before.startsWith("//") || before.startsWith("/*");
    }

    /** The name in the first method signature after {@code from}. */
    private static String methodNameAfter(String source, int from) {
        int paren = source.indexOf('(', from);
        if (paren < 0) {
            return null;
        }
        int start = paren;
        while (start > 0 && !Character.isWhitespace(source.charAt(start - 1))) {
            start--;
        }
        String name = source.substring(start, paren).trim();
        return name.isEmpty() ? null : name;
    }

    /** The brace-matched body of the named method. */
    private static String bodyOf(String source, String method) {
        int signature = source.indexOf(" " + method + "(");
        int open = source.indexOf('{', signature);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return source.substring(open, i + 1);
            }
        }
        return "";
    }
}
