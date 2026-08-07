/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SH574 — this repository is on Jackson 3, and the rule is narrower than a grep for the old
 * group id.
 *
 * <h2>Why the obvious rule is the wrong one</h2>
 *
 * <p>Jackson 3 renamed databind and streaming to {@code tools.jackson.*} and <b>left the
 * annotations where they were</b>: {@code com.fasterxml.jackson.annotation} is still the home of
 * {@code @JsonInclude} and {@code @JsonProperty}, and {@code tools.jackson.core:jackson-databind}
 * depends on {@code jackson-annotations} to supply them. So "no {@code com.fasterxml.jackson}"
 * is false — it was 92 occurrences across 56 files when this was written and 87 of them were
 * correct — and a rule stated that way cannot be enforced, which is how the repository ended up
 * with the rule written nowhere and checked by nothing.</p>
 *
 * <p>The rule that is true is the one below: none of the four packages Jackson 3
 * <em>replaced</em>, and the annotations package allowed by name.</p>
 *
 * <h2>Why it needs a lint rather than a review</h2>
 *
 * <p>{@code ShConvertedLayoutRenderTest} imported {@code JsonNode}, {@code ObjectMapper},
 * {@code ArrayNode} and {@code ObjectNode} from Jackson 2, and it <b>compiled and passed</b>:
 * springdoc-openapi drags {@code jackson-databind:2.21.4} onto the classpath through
 * swagger-core-jakarta, so the wrong import resolves against a transitive dependency nobody
 * chose. That is the whole failure mode — the mistake is invisible for exactly as long as a
 * third-party dependency happens to supply it, and becomes a compile error on the day that
 * dependency moves, in a file whose author has long since left the change behind.</p>
 *
 * <p>It is not only tidiness. Jackson 3 makes {@code asString()} the abstract accessor and keeps
 * {@code asText()} as a legacy alias, so a file on the Jackson 2 spelling quietly drifts from the
 * idiom of every other file — and drift is what review is worst at seeing.</p>
 *
 * <h2>Declared limits</h2>
 *
 * <p>This reads <b>import statements</b>, so it does not see Jackson 2 reached through a
 * fully-qualified name in the body of a method, and it deliberately does not see the package
 * named in prose — {@code ShMcpServer} discusses a Maven coordinate in a comment and is not an
 * offender. It also checks only what <em>this repository declares</em>: Jackson 2 arriving
 * transitively is a fact about springdoc, not a thing our sources may use, and banning it in the
 * build would break the dependency that needs it. A lower bound, stated rather than left to be
 * found (SH273, SH322).</p>
 *
 * @author Alexandre Oliveira
 */
class ShJacksonVersionLintTest {

    private static final List<Path> SOURCE_ROOTS =
            List.of(Path.of("src", "main", "java"), Path.of("src", "test", "java"));

    /** The module's own pom and the parent reactor's — the two this repository writes. */
    private static final List<Path> POMS = List.of(Path.of("pom.xml"), Path.of("..", "pom.xml"));

    /**
     * The four Jackson 2 packages Jackson 3 replaced.
     *
     * <p>{@code annotation} is deliberately absent: Jackson 3 has no replacement for it and
     * depends on it, so an import of it is correct and adding it here would fail 87 files that
     * are right.</p>
     */
    private static final String REPLACED = "databind|core|dataformat|datatype";

    private static final Pattern JACKSON_2_IMPORT = Pattern.compile(
            "^\\s*import\\s+(?:static\\s+)?com\\.fasterxml\\.jackson\\.(?:" + REPLACED + ")\\.");

    private static final Pattern ANNOTATION_IMPORT = Pattern.compile(
            "^\\s*import\\s+(?:static\\s+)?com\\.fasterxml\\.jackson\\.annotation\\.");

    @Test
    @DisplayName("SH574: no Java source imports a Jackson 2 package that Jackson 3 replaced")
    void everyJavaSourceIsOnJackson3() throws IOException {
        List<String> offenders = new ArrayList<>();
        int scanned = 0;

        for (Path root : SOURCE_ROOTS) {
            try (Stream<Path> sources = Files.walk(root)) {
                for (Path source : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
                    scanned++;
                    List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
                    for (int line = 0; line < lines.size(); line++) {
                        if (JACKSON_2_IMPORT.matcher(lines.get(line)).find()) {
                            offenders.add("%s:%d: %s"
                                    .formatted(source, line + 1, lines.get(line).trim()));
                        }
                    }
                }
            }
        }

        assertThat(scanned)
                .as("a walk that found no Java source would make the assertion below pass over "
                        + "nothing, which is the one way this lint can be green and useless")
                .isGreaterThan(500);
        assertThat(offenders)
                .as("""
                        These import a Jackson 2 package Jackson 3 replaced. They compile only \
                        because springdoc-openapi drags jackson-databind 2.x onto the classpath \
                        through swagger-core-jakarta, so the mistake is invisible until that \
                        dependency moves (SH574). Fix: `com.fasterxml.jackson.databind` -> \
                        `tools.jackson.databind`, same for `.core`, and note that Jackson 3 makes \
                        `asString()` the accessor — `asText()` survives only as a legacy alias. \
                        `com.fasterxml.jackson.annotation` is NOT one of these: Jackson 3 keeps \
                        the annotations there and depends on them.""")
                .isEmpty();
    }

    /**
     * The exemption has to cover something, or the rule above degenerates into "no Jackson at
     * all" — which is a different rule, one this codebase would fail 87 times over, and the
     * shape SH142 warns about: an allow-list nobody can see the effect of stops being read.
     */
    @Test
    @DisplayName("SH574: the annotations package is exempt because Jackson 3 still requires it")
    void theAnnotationsPackageIsGenuinelyInUse() throws IOException {
        int annotationImports = 0;

        for (Path root : SOURCE_ROOTS) {
            try (Stream<Path> sources = Files.walk(root)) {
                for (Path source : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
                    for (String line : Files.readAllLines(source, StandardCharsets.UTF_8)) {
                        if (ANNOTATION_IMPORT.matcher(line).find()) {
                            annotationImports++;
                        }
                    }
                }
            }
        }

        assertThat(annotationImports)
                .as("`com.fasterxml.jackson.annotation` is where Jackson 3 keeps @JsonInclude and "
                        + "@JsonProperty, so these imports are correct and the lint above must "
                        + "never fire on them. If this reaches zero the exemption is dead code "
                        + "and should go, not be kept as a comment")
                .isPositive();
    }

    /**
     * The other half of "no Jackson 2": not using it and not <em>asking</em> for it.
     *
     * <p>Transitive arrival is out of our hands — springdoc needs it — but a declared dependency
     * is a choice, and it is the one that would make an import of it legitimate again. Checking
     * the declaration rather than the resolved tree is what keeps this a statement about our own
     * build instead of a hostage to somebody else's.</p>
     */
    @Test
    @DisplayName("SH574: no pom declares a Jackson 2 artifact of its own")
    void noPomDeclaresJackson2() throws IOException {
        List<String> declared = new ArrayList<>();

        for (Path pom : POMS) {
            List<String> lines = Files.readAllLines(pom, StandardCharsets.UTF_8);
            for (int line = 0; line < lines.size(); line++) {
                String text = lines.get(line).trim();
                if (text.startsWith("<groupId>com.fasterxml.jackson")) {
                    declared.add("%s:%d: %s".formatted(pom, line + 1, text));
                }
            }
        }

        assertThat(declared)
                .as("""
                        A declared Jackson 2 artifact is this repository choosing Jackson 2, which \
                        is what the import lint above exists to prevent one layer up. Jackson 3 is \
                        `tools.jackson.*`; jackson-annotations arrives underneath it and needs no \
                        declaration of ours (SH574).""")
                .isEmpty();
    }
}
