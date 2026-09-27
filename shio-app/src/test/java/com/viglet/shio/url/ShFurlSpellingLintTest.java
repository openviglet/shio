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

    /**
     * An address scheme opened as a literal, added to, with a stored URL on the same line (SH666).
     *
     * <p>Both halves are load-bearing, and the first draft had only the first. A scheme literal
     * alone matched eight lines and one of them was a comment: {@code "site:" + site.getName()} is
     * <b>fine</b> — a site address carries no path, so there is no spelling to get wrong — and the
     * teaching errors spell {@code "post:" + site + "/blog/new-url"} as an <em>example</em> of the
     * grammar rather than an address to a row. Measured with the URL accessor required: exactly
     * one line matches, and it is the defect. A gate that trips on the other seven is bypassed.</p>
     */
    private static final Pattern ADDRESS_SCHEME = Pattern.compile("\"(post|folder):\"\\s*\\+");

    /** …and a stored URL going into it, which is the half that has two spellings. */
    private static final Pattern STORED_URL = Pattern.compile("[Ff]url\\s*\\(");

    /** A line that only talks about the shape — this file's subject appears in prose a lot. */
    private static final Pattern COMMENT = Pattern.compile("^\\s*(//|\\*|/\\*)");

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
            // SH1137 — the code, not the file. This rule's own subject is written about in javadoc
            // far more often than it is called, and reading the raw body made a class that
            // *explains* the finder indistinguishable from one that calls it. Its sibling above has
            // read code only since SH613; this half had not, and the first paragraph that named the
            // finder while explaining a cross-site move turned it red.
            String code = ShSourceText.code(body);
            if (NOT_CALLERS.contains(file) || !code.contains(POST_FURL_FINDER)) {
                return;
            }
            callers.add(file);
            if (!code.contains("ShFurlSpellings")) {
                offenders.add(file);
            }
        });

        // A rule that has stopped finding its own subject passes for the wrong reason. Six classes
        // called a post-furl finder at 077b115; the count is asserted as a floor rather than a
        // number so adding a reader is not a docs edit, but losing all of them is a failure.
        //
        // ShObjectWriteService was an anchor here and is deliberately no longer one: SH243 moved its
        // occupied-URL walk into ShFurlNaming, which the restore refusal needed too, so it reaches
        // the generator one call further away. An anchor leaving because its lookup was *shared*
        // rather than dropped is the healthy direction, and ShFurlNaming takes its place — losing
        // the anchor entirely is what would let the next such move go unnoticed.
        assertThat(callers)
                .as("nothing matched '%s' — either the finders were renamed and this rule now "
                        + "guards nothing, or NOT_CALLERS has grown to cover the whole surface",
                        POST_FURL_FINDER)
                .hasSizeGreaterThanOrEqualTo(6)
                .contains("ShAddressResolver.java", "ShAgentBatchService.java", "ShCdaService.java",
                        "ShAgentVerifyService.java", "ShAgentRenderService.java",
                        "ShFurlNaming.java");

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
    @DisplayName("SH1137: a class that writes about the finder is not a class that calls it")
    void theReaderRuleReadsCodeRatherThanProse() {
        String prose = """
                /**
                 * The posts kept resolving through findByShSite_IdAndFurlAndState under the old
                 * site, so a folder moved across sites left them answering somewhere else.
                 */
                class Explains { }
                """;
        assertThat(ShSourceText.code(prose))
                .as("SH1137: explaining the defect must not read as committing it — this is the "
                        + "SH613 shape, and the sibling rule above has been immune to it since. "
                        + "A guard that goes red when somebody documents the rule is one people "
                        + "route around by not documenting it.")
                .doesNotContain(POST_FURL_FINDER);

        assertThat(ShSourceText.code("    var row = repository.findByShSite_IdAndFurl(id, furl);"))
                .as("SH1137: and the call itself is still seen, or the fix blinded the rule")
                .contains(POST_FURL_FINDER);
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

    /**
     * SH666 — the other direction: composing an address, not resolving one.
     *
     * <p>The rule above keeps every <b>reader</b> tolerant of both spellings. Nothing kept a
     * <b>writer</b> honest, and the two stored forms are real: {@code setFurl} and {@code move}
     * guarantee a leading slash through {@code normalizeUrl}, while {@code create} (SH651),
     * {@code copyPost} and the folder and site writes store it bare. That coexistence is
     * deliberate — SH467 stores whichever spelling the source declared, and this class exists so
     * reading tolerates it.</p>
     *
     * <p>What is not deliberate is string addition. {@code ShSiteExportService} built
     * {@code "post:" + site.getName() + "/" + row.getFurl()}, which assumes the stored value has
     * no slash of its own — so a post whose address the agent path declared exported as
     * {@code post:site//page}, and the same post created through the console exported as
     * {@code post:site/page}. One of those is not an SH79 address, and which one you got depended
     * on which surface wrote the row. It was found by a red run in another task's suite, not here.
     *
     * <p>{@code ShAddress.post} is the composer and already normalises through this class, so the
     * rule is simply that nobody hand-rolls the concatenation instead.</p>
     */
    @Test
    @DisplayName("SH666: nobody concatenates a furl into an address instead of asking ShAddress")
    void noAddressIsBuiltByStringAddition() {
        List<String> offenders = new ArrayList<>();
        sources().forEach((file, body) -> {
            if (NOT_CALLERS.contains(file)) {
                return;
            }
            for (String line : body.split("\n")) {
                if (composesByAddition(line)) {
                    offenders.add(file + ": " + line.strip());
                }
            }
        });

        assertThat(offenders)
                .as("""
                        an address built by concatenation assumes a stored spelling, and there \
                        are two. `ShAddress.post(site, furl)` normalises through \
                        ShFurlSpellings.canonical and is the composer; `ShAddress.id(...)` is the \
                        fallback for a row with no furl. A `+` here produces `post:site//page` \
                        for every row a slash-guaranteeing writer created.""")
                .isEmpty();
    }

    @Test
    @DisplayName("SH666: the composition rule fires on the form it replaced")
    void theCompositionRuleDetectsTheFormItReplaced() {
        // The literal that was in ShSiteExportService, and two shapes that must NOT fire: the
        // composer itself, and a message that merely prints a furl. A rule that matched those
        // would be reverted the first week, which is how a lint stops guarding anything.
        assertThat(composesByAddition(
                "                : \"post:\" + site.getName() + \"/\" + row.getFurl();"))
                .as("the exact line SH666 was filed for has to match, or this guards nothing")
                .isTrue();
        assertThat(composesByAddition(
                "        return ShAddress.post(site.getName(), row.getFurl()).toString();"))
                .as("the composer is the fix, so it must not be reported as the defect")
                .isFalse();
        assertThat(composesByAddition(
                "                    \"Published URL '\" + post.getFurl() + \"' is claimed by \""))
                .as("a furl quoted inside a sentence is prose, not an address being composed")
                .isFalse();
        assertThat(composesByAddition("                    \"site:\" + site.getName(), null,"))
                .as("a site address carries no path, so there is no spelling for it to get wrong")
                .isFalse();
        assertThat(composesByAddition(
                "                            : \"post:\" + from.site() + \"/blog/new-url\"));"))
                .as("a teaching error spelling the grammar is an example, not a row's address")
                .isFalse();
        assertThat(composesByAddition(
                "        // projections concatenate — `ShSiteExportService` builds `\"post:\" + site "
                        + "+ \"/\" + furl`,"))
                .as("and a comment describing the defect is not the defect — this file's own "
                        + "subject is written about in prose more often than it is committed")
                .isFalse();
    }

    /** A scheme literal being added to, a stored URL on the same line, and not a comment. */
    private static boolean composesByAddition(String line) {
        return !COMMENT.matcher(line).find()
                && ADDRESS_SCHEME.matcher(line).find()
                && STORED_URL.matcher(line).find();
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
