/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.url;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.lint.ShSourceScan;
import com.viglet.shio.lint.ShSourceText;

/**
 * SH583 — one generator decides which spellings of a path name the same post, and nothing may
 * write a second one.
 *
 * <h2>Why a lint, and why before SH467</h2>
 *
 * <p>The tolerance that bridges {@code /shio} and {@code /shio/} was four functions under three
 * names, three of them private: {@code ShAddressResolver.furlCandidates},
 * {@code ShAgentBatchService.furlCandidates} (a copy of it), {@code ShCdaService.urlCandidates},
 * and {@code ShPageRenderService}'s inline redirect rule. They agreed by <b>accident</b> — nothing
 * was ever stored with a trailing slash, so all four were asked the same question. SH467 stores
 * one, and from that commit they have to agree <b>on purpose</b>.</p>
 *
 * <p>The failure mode is why this is a lint and not a review note: a page that renders in
 * {@code /preview}, answers <b>404</b> on the CDA and <b>verifies clean</b>, because three readers
 * disagreed about which spellings match one stored value. No behavioural test watches for a
 * generator that does not exist yet, and the fifth copy will be written by somebody who has read
 * neither SH467 nor this.</p>
 *
 * <p>SH322's order, which SH338 restated as a law: when a task's body is "and whatever a grep turns
 * up", <b>the grep is the deliverable and it lands before the fixes</b>. This shipped in the same
 * commit as {@link ShFurlSpellings} and ahead of SH467.</p>
 *
 * <h2>The exclusions, named rather than assumed</h2>
 *
 * <p>A folder {@code furl} and a site {@code furl} are a <b>different namespace</b> (SH79 keeps a
 * folder's chain of names apart from a post's arbitrary friendly URL) and neither is touched by
 * SH467. {@link #EXCLUDED_FINDERS} names them, and {@link #theExclusionsAreRealFinders} asserts
 * they still exist — an exclusion list that has gone stale silently widens the rule it documents.
 * </p>
 *
 * @author Alexandre Oliveira
 */
@ShSourceScan(ShSourceScan.Reads.CODE)
class ShFurlSpellingLintTest {

    private static final Path MAIN = Path.of("src", "main", "java", "com", "viglet", "shio");

    /** The shared generator's own file — the one place allowed to decide what a spelling is. */
    private static final String GENERATOR = "ShFurlSpellings.java";

    /** Every post-{@code furl} finder on {@code ShPostUnifiedRepository} shares this prefix. */
    private static final String POST_FURL_FINDER = "findByShSite_IdAndFurl";

    /**
     * Declares the finders, so it names {@link #POST_FURL_FINDER} without calling it.
     *
     * <p>{@code ShAddress} is exempt for the inverse reason: it <em>is</em> the address grammar and
     * delegates its {@code normalizePath} to the generator, so it references the class without
     * resolving anything.</p>
     */
    private static final Set<String> NOT_CALLERS =
            Set.of("ShPostUnifiedRepository.java", "ShAddress.java", GENERATOR);

    /** A different namespace, and out of SH467's scope — see the class note. */
    private static final Map<String, String> EXCLUDED_FINDERS = Map.of(
            "findByShSiteAndFurl", "ShFolderRepository.java",
            "findByParentFolderAndFurl", "ShFolderRepository.java",
            "findByFurl", "ShSiteRepository.java");

    /**
     * A method that hands back a list of path spellings.
     *
     * <p>Matches the three declarations this task deleted, by their shape rather than their names —
     * {@code furlCandidates} and {@code urlCandidates} differed only in the noun, which is exactly
     * how the third one got written.</p>
     */
    private static final Pattern CANDIDATE_GENERATOR = Pattern.compile(
            "List<String>\\s+\\w*[Cc]andidates\\s*\\(");

    @Test
    @DisplayName("only ShFurlSpellings decides which spellings of a path mean the same post")
    void noSecondCandidateGenerator() {
        List<String> offenders = new ArrayList<>();
        sources().forEach((file, body) -> {
            if (GENERATOR.equals(file)) {
                return;
            }
            int lineNumber = 0;
            for (String line : body.split("\r?\n")) {
                lineNumber++;
                String code = ShSourceText.codeLine(line);
                if (CANDIDATE_GENERATOR.matcher(code).find()) {
                    offenders.add(file + ":" + lineNumber + " —" + code.stripTrailing());
                }
            }
        });

        assertThat(offenders)
                .as("""
                        a second candidate generator agrees with ShFurlSpellings until the day a \
                        trailing slash is stored (SH467), and then the symptom is a page that \
                        renders in /preview, 404s on the CDA and verifies clean. Call \
                        ShFurlSpellings.candidates for "which stored furls mean this path" and \
                        ShFurlSpellings.otherSlashForm for "the other spelling of this one".""")
                .isEmpty();
    }

    @Test
    @DisplayName("every post-furl reader asks the shared generator")
    void everyPostFurlReaderRoutesThroughTheGenerator() {
        List<String> callers = new ArrayList<>();
        List<String> offenders = new ArrayList<>();
        sources().forEach((file, body) -> {
            if (NOT_CALLERS.contains(file) || !body.contains(POST_FURL_FINDER)) {
                return;
            }
            callers.add(file);
            if (!body.contains("ShFurlSpellings")) {
                offenders.add(file);
            }
        });

        // A rule that has stopped finding its own subject passes for the wrong reason. Six classes
        // called a post-furl finder at 077b115; the count is asserted as a floor rather than a
        // number so adding a reader is not a docs edit, but losing all of them is a failure.
        assertThat(callers)
                .as("nothing matched '%s' — either the finders were renamed and this rule now "
                        + "guards nothing, or NOT_CALLERS has grown to cover the whole surface",
                        POST_FURL_FINDER)
                .hasSizeGreaterThanOrEqualTo(6)
                .contains("ShAddressResolver.java", "ShAgentBatchService.java", "ShCdaService.java",
                        "ShAgentVerifyService.java", "ShAgentRenderService.java",
                        "ShObjectWriteService.java");

        assertThat(offenders)
                .as("""
                        these resolve a requested path to a stored post by exact equality and \
                        never ask what else that path could be spelled as. Route the lookup \
                        through ShFurlSpellings.candidates. The one deliberate non-tolerant \
                        reader is ShPageRenderService.pageWithFurl — it serves the stored \
                        spelling and answers the other with a 301, which is SH467's canonical \
                        rule and is itself written in terms of ShFurlSpellings.otherSlashForm, \
                        so it satisfies this without an exemption.""")
                .isEmpty();
    }

    @Test
    @DisplayName("the lint matches the three generators SH583 deleted, and not the shared one")
    void theLintDetectsTheFormsItReplaced() {
        // Guards the guard: a lint that silently matches nothing is worse than none. All three are
        // the real declarations, as they stood at 077b115.
        assertThat(matches("    private static List<String> furlCandidates(String path) {")).isTrue();
        assertThat(matches("    private List<String> urlCandidates(String url) {")).isTrue();
        assertThat(matches("    static List<String> pathCandidates(String p) {"))
                .as("the third name nobody has invented yet is the one this is for")
                .isTrue();

        assertThat(matches("    public static List<String> candidates(String path) {"))
                .as("the shared generator's own declaration — matched, which is why its file is "
                        + "excluded by name rather than by the pattern")
                .isTrue();
        assertThat(matches("    static List<String> segments(String path) {"))
                .as("splitting a path into segments is a different job and stays where it is")
                .isFalse();
    }

    @Test
    @DisplayName("the excluded finders are real, and are not post-furl finders")
    void theExclusionsAreRealFinders() {
        Map<String, String> repositories = sources();
        EXCLUDED_FINDERS.forEach((finder, file) -> {
            assertThat(repositories.get(file))
                    .as("%s is excluded as a folder/site furl finder; if it has been renamed the "
                            + "exclusion is silently widening the rule", finder)
                    .isNotNull()
                    .contains(finder + "(");
            assertThat(finder)
                    .as("an excluded finder that starts with the post prefix would be excluded "
                            + "from its own rule")
                    .doesNotStartWith(POST_FURL_FINDER);
        });
    }

    private static boolean matches(String code) {
        return CANDIDATE_GENERATOR.matcher(code).find();
    }

    private static Map<String, String> sources() {
        try (Stream<Path> files = Files.walk(MAIN)) {
            Map<String, String> byName = new TreeMap<>();
            files.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> byName.put(path.getFileName().toString(), read(path)));
            return byName;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + path.toAbsolutePath(), e);
        }
    }
}
