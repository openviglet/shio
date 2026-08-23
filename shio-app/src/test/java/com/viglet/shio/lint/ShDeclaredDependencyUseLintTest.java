/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.lint;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SH916 — a library declared in the POM that no source imports.
 *
 * <h2>The one that got here</h2>
 *
 * <p>Six {@code org.apache.tika} artifacts sat in {@code shio-app/pom.xml} and nothing in
 * {@code src/main} ever imported one. They were a reasonable bet — parsing an attachment's text
 * locally is the obvious design — placed before anybody checked whether Turing could already do it.
 * SH913 found that it can, and the bet stayed on the classpath: <b>23 compile-scope artifacts</b>
 * once pdfbox and BouncyCastle came with it, in every build, every image and every dependency scan
 * this project answers for. A CVE in any of them is a CVE somebody triages against code that never
 * runs.</p>
 *
 * <h2>Why a lint and not just a deletion</h2>
 *
 * <p>SH322's rule: when a task says "and whatever a grep turns up", the grep is the deliverable,
 * and it lands <em>before</em> the fixes because it is what finds the next one. Deleting six lines
 * fixes today; this makes the seventh line fail on the day it is added, while the person adding it
 * is still holding the reason.</p>
 *
 * <h2>What it cannot see, and why the list is short</h2>
 *
 * <p>A dependency can be load-bearing without any source importing it, and this reader would call
 * every one of those unused. Three kinds, all present here: an {@code ImageIO} SPI whose readers
 * are found by service lookup ({@code jai-imageio}, {@code webp-imageio} — a JPEG2000 upload cannot
 * be read without them), a JDBC driver the URL names as a string, and a Spring starter that exists
 * to be auto-configured. So the walk is <b>opt-in</b>: only the groups named below are judged, and
 * adding a group here is a deliberate claim that its use would be visible as an import. That is the
 * opposite of the usual maven-dependency-plugin sweep, which reports the SPI cases forever until
 * somebody stops reading it.</p>
 *
 * @author Alexandre Oliveira
 */
@ShSourceScan(ShSourceScan.Reads.CODE)
class ShDeclaredDependencyUseLintTest {

    private static final Path POM = Path.of("pom.xml");
    private static final Path MAIN = Path.of("src", "main", "java");

    /**
     * Group id → the package prefix an import would use.
     *
     * <p>Only groups whose use is genuinely visible as an {@code import}. A group is added here
     * when somebody decides that claim is true of it, which is why this is a short list and not a
     * scan of the whole POM.</p>
     */
    private static final Map<String, String> JUDGED = Map.of(
            "org.apache.tika", "org.apache.tika",
            "org.apache.pdfbox", "org.apache.pdfbox",
            "org.jsoup", "org.jsoup",
            "com.github.jknack", "com.github.jknack");

    private static final Pattern DEPENDENCY = Pattern.compile(
            "<dependency>\\s*<groupId>\\s*([^<\\s]+)\\s*</groupId>\\s*<artifactId>\\s*([^<\\s]+)\\s*</artifactId>",
            Pattern.DOTALL);

    /** Declared group:artifact pairs, comments excluded — a group named in prose is not a declaration. */
    private static List<String[]> declared() throws IOException {
        String pom = Files.readString(POM).replaceAll("(?s)<!--.*?-->", " ");
        List<String[]> found = new ArrayList<>();
        Matcher matcher = DEPENDENCY.matcher(pom);
        while (matcher.find()) {
            found.add(new String[] { matcher.group(1), matcher.group(2) });
        }
        return found;
    }

    private static boolean importedAnywhere(String packagePrefix) throws IOException {
        try (Stream<Path> sources = Files.walk(MAIN)) {
            return sources.filter(path -> path.toString().endsWith(".java")).anyMatch(path -> {
                try {
                    return ShSourceText.code(Files.readString(path)).contains("import " + packagePrefix + ".");
                } catch (IOException e) {
                    return false;
                }
            });
        }
    }

    @Test
    @DisplayName("SH916: every judged dependency is imported by something in src/main")
    void everyJudgedDependencyIsUsed() throws IOException {
        List<String> unused = new ArrayList<>();
        for (String[] dependency : declared()) {
            String prefix = JUDGED.get(dependency[0]);
            if (prefix != null && !importedAnywhere(prefix)) {
                unused.add(dependency[0] + ":" + dependency[1]);
            }
        }

        assertThat(unused)
                .as("SH916: a declared library that no source imports. Six tika artifacts sat here "
                        + "unused and brought 23 compile-scope jars with them, into every build, "
                        + "image and dependency scan — a CVE in one is a CVE triaged against code "
                        + "that never runs.\n"
                        + "Remove the declaration, or — if it is load-bearing without an import "
                        + "(an ImageIO SPI, a JDBC driver named in a URL, a Spring starter) — take "
                        + "its group OUT of JUDGED with the reason, because then the premise that "
                        + "put it there is what is wrong.")
                .isEmpty();
    }

    @Test
    @DisplayName("SH916: the reader is not vacuous — it parses a real POM and finds real imports")
    void theReaderActuallyReads() throws IOException {
        List<String[]> declared = declared();
        assertThat(declared)
                .as("SH916: the POM yielded no dependencies — the regex or the path moved, and this "
                        + "lint now passes over everything")
                .hasSizeGreaterThan(20);

        // One judged group that IS used, so a reader that answered "imported" for everything would
        // fail the next assertion instead of passing this one by luck.
        assertThat(importedAnywhere("org.jsoup"))
                .as("SH916: jsoup is imported by the mapper and the renderer; a reader that cannot "
                        + "see it cannot see anything")
                .isTrue();
        assertThat(importedAnywhere("org.apache.tika"))
                .as("SH916: tika is gone from src/main, which is the state this task left — if this "
                        + "fails somebody started using it and the declaration must come back")
                .isFalse();

        // And the comment stripper has to work, or SH916's own explanation of why tika was removed
        // would read as a declaration of tika and this lint would report it for ever.
        assertThat(Files.readString(POM))
                .as("the removal note names tika in prose, which is the case the stripper exists for")
                .contains("org.apache.tika");
        assertThat(declared.stream().map(pair -> pair[0]).toList())
                .as("SH916: a group named in a comment is not a declaration")
                .doesNotContain("org.apache.tika");
    }
}
