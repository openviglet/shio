/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SH357 — within one class, either <b>every</b> overload of a name carries {@code @Transactional}
 * or none does.
 *
 * <h2>What went wrong, and why it could not be seen</h2>
 *
 * <p>{@link ShAgentIncidentStore} declared two {@code record} overloads. The five-argument one
 * carried {@code @Transactional(propagation = REQUIRES_NEW)} and delegated to the six-argument one,
 * which carried nothing. Nothing ever called the five-argument one. So the annotation the class
 * note calls <em>"not a detail; it is the feature"</em> sat on dead code, and every incident this
 * product has ever recorded was written with no demarcation of its own — which is precisely the
 * failure the class exists to prevent, because a {@code save} with no transaction joins the
 * caller's, and several callers are on a path that is about to roll back.</p>
 *
 * <p>Reading the class does not reveal it. The annotation is present, it is spelled correctly, it
 * is on a method named {@code record}, and the javadoc explaining it is three lines above. Only
 * asking <em>which overload the caller resolves to</em> — a question no reviewer asks — finds it.
 * The compiler is content, every test passed, and the defect had been shipped for two blocks.</p>
 *
 * <h2>Why the pair is the defect, not the missing annotation</h2>
 *
 * <p>Moving the annotation to the other overload would have fixed this instance and left the trap
 * armed: the delegation between two overloads is a <b>self-invocation</b>, so it never crosses
 * Spring's proxy. Annotate the inner one and the outer one silently loses it; annotate the outer
 * one and a caller that picks the inner one silently loses it. Whichever half a reader checks, the
 * other half is wrong. The only shape with no invisible case is the one this refuses to break:
 * all-or-nothing per method name.</p>
 *
 * <p>Textual, for {@code ShFurlReaderLintTest}'s reason — no behavioural test can watch for an
 * overload nobody has written yet, and the failure arrives as a lost row on a path that was
 * already failing, which is the last place anybody looks.</p>
 *
 * @author Alexandre Oliveira
 */
class ShAgentTransactionalOverloadLintTest {

    private static final Path MAIN = Path.of("src", "main", "java");

    /**
     * A method declaration: modifiers, an optional generic return, the name, the open paren.
     *
     * <p>Deliberately loose on the return type and strict on what precedes it — a declaration is
     * recognised by starting with a visibility modifier at method indentation, which is what keeps
     * a call site inside a body (never indented four spaces and never preceded by {@code public})
     * out of the count.</p>
     */
    /**
     * Only what a proxy can enter.
     *
     * <p>A {@code private} overload is never reached through the proxy, so it can only ever run
     * inside a caller that is already demarcated — a mixed pair involving one is not the trap, and
     * two of the four this lint found on its first run were exactly that. Narrowing here rather
     * than exempting them one by one, because the narrowing is the actual rule.</p>
     */
    private static final Pattern MODIFIER = Pattern.compile("^ {4}(?:public|protected)\\b");

    /**
     * The escape hatch, spelled the way {@code ShFolderSite-exempt} is.
     *
     * <p>There is one honest reason to leave the pair mixed: the bare overload delegates through
     * an <em>injected self-reference</em> rather than through {@code this}, which does cross the
     * proxy. That is safe, it is invisible to a textual scan, and it is rare enough that the
     * comment is cheaper than teaching the scan to recognise it — and the comment does the more
     * useful thing anyway, which is making the next reader see the workaround on purpose.</p>
     */
    private static final String EXEMPT = "@Transactional-overload-exempt:";

    /**
     * The declared name on a method line, or {@code null} when the line declares nothing.
     *
     * <p>Read backwards from the open paren rather than forwards through the return type: a
     * generic return with nested type arguments is what makes the forward regex backtrack, and the
     * identifier immediately before {@code (} is unambiguous however complicated the type is.</p>
     */
    private static String declaredName(String line) {
        if (!MODIFIER.matcher(line).find()) {
            return null;
        }
        int paren = line.indexOf('(');
        if (paren < 0) {
            return null;
        }
        int end = paren;
        while (end > 0 && Character.isWhitespace(line.charAt(end - 1))) {
            end--;
        }
        int start = end;
        while (start > 0 && Character.isJavaIdentifierPart(line.charAt(start - 1))) {
            start--;
        }
        if (start == end) {
            return null;
        }
        // A constructor has no return type, so nothing but modifiers precedes the name. It is not
        // an overload a caller could resolve differently, and `new` never crosses a proxy anyway.
        boolean hasReturnType = Stream.of(line.substring(0, start).trim().split("\\s+"))
                .anyMatch(token -> !token.isEmpty() && !MODIFIERS.contains(token));
        return hasReturnType ? line.substring(start, end) : null;
    }

    private static final Set<String> MODIFIERS = Set.of("public", "protected", "private", "static",
            "final", "synchronized", "abstract", "default", "native", "strictfp");

    /**
     * The offending pairs, as {@code File.method}: annotated on some overloads, bare on others.
     */
    private static List<String> mixedOverloads(String body) {
        if (body.contains(EXEMPT)) {
            return List.of();
        }
        Map<String, Set<Boolean>> byName = new LinkedHashMap<>();
        boolean annotated = false;
        for (String raw : body.split("\r?\n")) {
            String line = raw.stripTrailing();
            String trimmed = line.trim();
            if (trimmed.startsWith("*") || trimmed.startsWith("/*") || trimmed.startsWith("//")) {
                continue;
            }
            if (trimmed.startsWith("@Transactional")) {
                annotated = true;
                continue;
            }
            String name = declaredName(line);
            if (name == null) {
                // Any other annotation between @Transactional and the declaration is fine and
                // common (@Override, @Cacheable); anything else resets, so a stray annotation
                // earlier in the file cannot be attributed to a later method.
                if (!trimmed.startsWith("@") && !trimmed.isEmpty()) {
                    annotated = false;
                }
                continue;
            }
            byName.computeIfAbsent(name, key -> new LinkedHashSet<>()).add(annotated);
            annotated = false;
        }
        return byName.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(Map.Entry::getKey)
                .toList();
    }

    @Test
    @DisplayName("no method name is @Transactional on one overload and bare on another")
    void transactionalIsAllOrNothingPerMethodName() {
        List<String> offenders = new ArrayList<>();
        sources().forEach((file, body) -> mixedOverloads(body)
                .forEach(name -> offenders.add(file + "." + name + "(...)")));

        assertThat(offenders)
                .as("""
                        one overload carries @Transactional and another does not, so which \
                        semantics apply depends on which overload the caller resolved — invisible \
                        at the call site, and invisible in review because the annotation *is* \
                        there. Adding it to the other overload does not help: the delegation \
                        between them is a self-invocation and never crosses the proxy. Collapse \
                        the pair into one method, or give every overload the same annotation and \
                        no delegation between them.""")
                .isEmpty();
    }

    @Test
    @DisplayName("the lint matches the pair that lost the rows, and not a healthy class")
    void theLintDetectsTheShapeThatShipped() {
        // Guards the guard (SH275's habit): a lint that silently matches nothing is worse than
        // none. This is the declaration pair as it stood before SH357, reduced to its shape.
        String broken = """
                @Component
                public class ShAgentIncidentStore {

                    @Transactional(propagation = Propagation.REQUIRES_NEW)
                    public boolean record(Kind kind, String subject, Instant now) {
                        return record(kind, subject, null, now);
                    }

                    public boolean record(Kind kind, String subject, String frames, Instant now) {
                        return true;
                    }
                }
                """;
        assertThat(mixedOverloads(broken)).containsExactly("record");

        String fixed = """
                @Component
                public class ShAgentIncidentStore {

                    @Transactional(propagation = Propagation.REQUIRES_NEW)
                    public boolean record(Kind kind, String subject, String frames, Instant now) {
                        return true;
                    }

                    @Transactional(readOnly = true)
                    public Snapshot snapshot(int limit, Instant ceiling) {
                        return null;
                    }

                    public void helper() {
                    }
                }
                """;
        assertThat(mixedOverloads(fixed))
                .as("a class where each name is consistent — including a bare method beside "
                        + "annotated ones, which is the normal and correct case")
                .isEmpty();

        String withOtherAnnotations = """
                public class Example {

                    @Transactional
                    @Override
                    public void run(String one) {
                    }

                    @Transactional
                    public void run(String one, String two) {
                    }
                }
                """;
        assertThat(mixedOverloads(withOtherAnnotations))
                .as("@Override between @Transactional and the declaration must not break the link")
                .isEmpty();

        String privateHelper = """
                public class ShRenderPostTypes {

                    @Transactional
                    public List<ShPostTypeDef> ensure() {
                        return List.of();
                    }

                    private ShPostType ensure(String name, String plural, String title) {
                        return null;
                    }
                }
                """;
        assertThat(mixedOverloads(privateHelper))
                .as("""
                        a private overload cannot be entered through the proxy, so it always runs \
                        inside a caller that is already demarcated. Two of this lint's first four \
                        findings were this, and flagging them would have taught the next reader \
                        that the rule is noise.""")
                .isEmpty();
    }

    @Test
    @DisplayName("the store keeps the one overload SH357 collapsed it to")
    void theStoreHasOneRecord() {
        String store = sources().get("ShAgentIncidentStore.java");

        assertThat(store).isNotNull();
        assertThat(store.split("public boolean record\\(", -1).length - 1)
                .as("""
                        the pair is the defect. A second `record` overload brings back the \
                        question "which one did the caller pick", and the answer decides whether \
                        an incident on a rolling-back request survives it.""")
                .isEqualTo(1);
        assertThat(store)
                .as("and the one that is left is the demarcated one")
                .contains("@Transactional(propagation = Propagation.REQUIRES_NEW)\n"
                        + "    public boolean record(");
    }

    private static Map<String, String> sources() {
        try (Stream<Path> files = Files.walk(MAIN)) {
            Map<String, String> byName = new LinkedHashMap<>();
            files.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> byName.put(path.getFileName().toString(), read(path)));
            return byName;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path path) {
        try {
            // Normalized, because the `contains` assertion below spans a line break and this
            // repository is checked out with CRLF on Windows and LF on CI.
            return Files.readString(path).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + path.toAbsolutePath(), e);
        }
    }
}
