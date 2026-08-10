/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.lint;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every test that reads Java source says what it reads (SH613).
 *
 * <h2>The finding this is the instrument for</h2>
 *
 * <p>A textual lint reads prose, so <b>writing about a rule can fail the build for breaking it</b>.
 * SH206 hit exactly that — a javadoc explaining why a class no longer carries {@code @Transactional}
 * was read as a declaration of it — and fixed the one lint in front of it. The other scans were
 * never counted.</p>
 *
 * <p>The population is printed on every run rather than written down here, because a number in a
 * comment is a measurement that has already started rotting. What is not printed is the shape of
 * the finding: <b>some of these read comments on purpose and some by accident, and from outside
 * they are the same scan.</b> That is why this asks for a declaration instead of enforcing a rule.
 * Three lints would break under "strip comments everywhere".</p>
 *
 * <h2>What a declaration has to survive</h2>
 *
 * <p>A claim nothing checks is a comment. {@code CODE} is therefore held to using
 * {@link ShSourceText}: a scan that says it reads code and then greps raw text is precisely SH206's
 * defect wearing a label. {@code PROSE} and {@code BOTH} are held to a written reason, because an
 * accident cannot explain itself.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
// Inside its own population, and it has to be: this file's javadoc discusses `Files.walk`,
// `".java"` and the declaration itself, so a version of this lint that read raw text would find
// itself by its own prose. Eating its own dog food is not a flourish here — it is the one case
// where the defect and the guard are the same file.
@ShSourceScan(ShSourceScan.Reads.CODE)
class ShSourceScanLintTest {

    private static final Path TESTS = Path.of("src", "test", "java", "com", "viglet", "shio");

    /** Reading a file at all. Deliberately the whole family, not the one spelling in use today. */
    private static final Pattern READS_A_FILE =
            Pattern.compile("Files\\.(walk|readString|readAllLines|lines)\\b");

    /** ...and that file being Java source, which is the population this is about. */
    private static final String JAVA_SOURCE = "\".java\"";

    private static final Pattern DECLARATION =
            Pattern.compile("@ShSourceScan\\s*\\(\\s*(?:value\\s*=\\s*)?"
                    + "ShSourceScan\\.Reads\\.(CODE|PROSE|BOTH)");

    private static final Pattern REASON = Pattern.compile("reason\\s*=\\s*\"");

    @Test
    @DisplayName("SH613: a test that scans Java source declares whether it reads code or prose")
    void everyScanDeclaresWhatItReads() {
        Map<String, String> scans = scanners();
        List<String> undeclared = new ArrayList<>();
        Map<String, String> modes = new TreeMap<>();

        scans.forEach((name, source) -> {
            Matcher declared = DECLARATION.matcher(source);
            if (!declared.find()) {
                undeclared.add(name);
                return;
            }
            modes.put(name, declared.group(1));
        });

        System.out.printf("%n[SH613] %d tests read Java source: %s%n%n", scans.size(), modes);

        assertThat(undeclared)
                .as("""
                        A scan over raw file text cannot tell a rule from a sentence about the rule, \
                        and neither can a reader: SH206's build went red for explaining why a class \
                        no longer carried an annotation. Add @ShSourceScan saying which half this \
                        one means to read (SH613).""")
                .isEmpty();
    }

    @Test
    @DisplayName("SH613: a scan that claims to read code strips comments with the shared stripper")
    void aCodeScanUsesTheSharedStripper() {
        List<String> unbacked = new ArrayList<>();
        scanners().forEach((name, source) -> {
            Matcher declared = DECLARATION.matcher(source);
            if (declared.find() && "CODE".equals(declared.group(1))
                    && !source.contains("ShSourceText.")) {
                unbacked.add(name);
            }
        });

        assertThat(unbacked)
                .as("""
                        A declaration nothing checks is a comment. `CODE` means a javadoc mention \
                        cannot be read as a declaration, and the only thing that makes it true is \
                        running the scan over ShSourceText.code/codeLine — five private strippers \
                        with five ideas of what a comment is were the state SH613 found (SH613).""")
                .isEmpty();
    }

    @Test
    @DisplayName("SH613: reading prose on purpose is stated, not left to be inferred")
    void readingProseCarriesAReason() {
        List<String> unexplained = new ArrayList<>();
        scanners().forEach((name, source) -> {
            Matcher declared = DECLARATION.matcher(source);
            if (declared.find() && !"CODE".equals(declared.group(1))
                    && !REASON.matcher(source).find()) {
                unexplained.add(name);
            }
        });

        assertThat(unexplained)
                .as("""
                        An accident cannot explain itself, which is the whole reason `reason` is \
                        required here and pointless on CODE. Say in one sentence what is being read \
                        out of the comments and why the scan needs it (SH613).""")
                .isEmpty();
    }

    @Test
    @DisplayName("SH613: the walk still finds the scans, so a green run means something")
    void theWalkIsNotVacuous() {
        // The failure mode of every source lint: a moved directory or a tightened pattern turns
        // the instrument off and reports the same green. Named files rather than a count, because
        // a count is a number somebody will relax rather than investigate.
        assertThat(scanners().keySet())
                .contains("ShRenderCacheReachLintTest.java", "ShSilentCatchInventoryTest.java",
                        "ShFolderSiteInvariantTest.java");
    }

    @Test
    @DisplayName("SH613: the stripper knows a comment from a literal that looks like one")
    void theStripperIsLiteralAware() {
        // Guards the guard. The `//` in a URL is the case that makes this a state machine rather
        // than a regex, and this repository is full of both URLs and address grammars.
        assertThat(ShSourceText.code("String url = \"https://example.com/a\"; // trailing"))
                .contains("https://example.com/a")
                .doesNotContain("trailing");
        assertThat(ShSourceText.code("""
                /** Mentions @Transactional in prose. */
                class Example { }
                """))
                .doesNotContain("@Transactional");
        assertThat(ShSourceText.codeLine("     * a javadoc continuation naming @TenantId"))
                .as("a javadoc continuation is not code, and the per-line form cannot see the /**")
                .isEmpty();
        assertThat(ShSourceText.codeLine("private String tenantId; // @TenantId lives here"))
                .contains("tenantId")
                .doesNotContain("@TenantId");
    }

    /**
     * Test sources that read Java source, by file name, <b>with their comments already stripped</b>.
     *
     * <p>Every match this class makes is therefore against code — the detection, the declaration
     * and the {@code ShSourceText} usage alike. Reading raw here would have been the exact defect
     * being guarded: this file's own javadoc names {@code Files.walk} and the annotation, so a raw
     * scan would find at least one file by its prose, and quite possibly this one.</p>
     */
    private static Map<String, String> scanners() {
        try (Stream<Path> files = Files.walk(TESTS)) {
            Map<String, String> byName = new TreeMap<>();
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                String source = ShSourceText.code(read(path));
                if (READS_A_FILE.matcher(source).find() && source.contains(JAVA_SOURCE)) {
                    byName.put(path.getFileName().toString(), source);
                }
            });
            return byName;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
