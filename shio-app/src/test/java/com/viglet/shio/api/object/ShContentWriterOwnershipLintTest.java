/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.object;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
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

import com.viglet.shio.lint.ShSourceScan;
import com.viglet.shio.lint.ShSourceText;

/**
 * SH245 — one writer per content verb, checked instead of expected.
 *
 * <h2>The four this is the instrument for</h2>
 *
 * <p>The same defect was found the same way four times: fixing the agent's version of a verb meant
 * reading the console's, and the console's was the careless one.</p>
 *
 * <table>
 *   <tr><td>move a post</td><td>re-slugged {@code furl} from the title, changing live URLs</td>
 *       <td>SH139</td></tr>
 *   <tr><td>move a folder</td><td>no cycle guard — re-parenting into its own subtree detached it</td>
 *       <td>SH137</td></tr>
 *   <tr><td>delete a folder</td><td>{@code deleteAll}: no trash, no audit, no lifecycle event</td>
 *       <td>SH200</td></tr>
 *   <tr><td>copy a folder</td><td>duplicate URLs, and non-termination on its own subtree</td>
 *       <td>SH202/SH203</td></tr>
 * </table>
 *
 * <p>Every one was caught by somebody happening to read the neighbouring file, which does not scale
 * and had already missed three of them for a release each. The rule that would have prevented all
 * four is written down — a content mutation goes through the writer service — and was enforced
 * nowhere.</p>
 *
 * <h2>What it keys on, and the population that decided it</h2>
 *
 * <p>A write mapping on an API class whose body reaches a <b>content</b> repository directly. The
 * "content" is load-bearing: the first version asked about any {@code *Repository} and found
 * <b>23</b> writes across nine classes — every one of them an admin surface (tokens, roles, users,
 * widgets, webhooks) with no writer service to go through and no verb anybody has duplicated. A
 * lint whose first act is 23 exemptions teaches nothing about the rule it is named for.</p>
 *
 * <p>Against the content repositories it is <b>0</b>, because all four defects were fixed. That is
 * the honest state and it is why {@link #theCheckWouldHaveCaughtTheFourItIsNamedFor} exists: a
 * guard reporting zero forever is indistinguishable from one that stopped matching, and this
 * repository has been bitten by exactly that (SH738, SH742).</p>
 *
 * <h2>What it does not watch, printed rather than hidden</h2>
 *
 * <p>Six content writes live outside the writer services in ordinary services rather than
 * controllers — the exchange import, the static-file upload, the draft merge. They are not this
 * lint's population, because SH245 scoped it to the console/API surface where the four defects
 * were. They are printed on every run so the number is a fact somebody can act on rather than one
 * this file quietly knows.</p>
 *
 * @author Alexandre Oliveira
 */
@ShSourceScan(value = ShSourceScan.Reads.BOTH,
        reason = "The breach is a content-repository write and the waiver is the"
                + " `ShContentWriter-exempt` comment on the same line, so the detection reads"
                + " code and the exemption reads prose — the ShFolderSiteInvariantTest form."
                + " Stripping both would make the hatch undetectable, which is how the first"
                + " version of this file reported a waived line as an offender.")
class ShContentWriterOwnershipLintTest {

    private static final Path API = Path.of("src", "main", "java", "com", "viglet", "shio", "api");
    private static final Path MAIN = Path.of("src", "main", "java", "com", "viglet", "shio");

    /** A mapping that changes something. {@code @GetMapping} is not this lint's business. */
    private static final Pattern WRITE_MAPPING =
            Pattern.compile("@(Post|Put|Delete|Patch)Mapping\\b");

    /** Any mapping — where one method's window ends and the next begins. */
    private static final Pattern ANY_MAPPING =
            Pattern.compile("@(Get|Post|Put|Delete|Patch|Request)Mapping\\b");

    /**
     * The repositories the writer services own.
     *
     * <p>Named rather than matched as {@code *Repository}: the difference between this list and
     * "anything ending in Repository" is 23 findings that are not the defect, and a list somebody
     * has to extend deliberately when a new content table appears is the honest form of that.</p>
     */
    private static final Pattern CONTENT_WRITE = Pattern.compile(
            "\\bsh(PostUnified|Folder|Site|Object|Post)Repository"
                    + "\\s*\\.\\s*(save|saveAll|delete|deleteAll|deleteById)\\s*\\(",
            Pattern.CASE_INSENSITIVE);

    /** The waiver, in the {@code ShFolderSite-exempt} form agents.md already documents. */
    private static final String EXEMPT = "ShContentWriter-exempt:";

    @Test
    @DisplayName("SH245: no write mapping reaches a content repository without its writer service")
    void everyContentVerbGoesThroughItsWriter() {
        List<String> offenders = new ArrayList<>();
        int mappings = 0;

        for (Map.Entry<String, String> file : sources(API).entrySet()) {
            String[] lines = file.getValue().split("\n");
            for (int at = 0; at < lines.length; at++) {
                if (!WRITE_MAPPING.matcher(lines[at]).find()) {
                    continue;
                }
                mappings++;
                int end = lines.length;
                for (int scan = at + 1; scan < lines.length; scan++) {
                    if (ANY_MAPPING.matcher(lines[scan]).find()) {
                        end = scan;
                        break;
                    }
                }
                for (int scan = at; scan < end; scan++) {
                    Matcher write = CONTENT_WRITE.matcher(ShSourceText.codeLine(lines[scan]));
                    if (write.find() && !lines[scan].contains(EXEMPT)) {
                        offenders.add(file.getKey() + ": " + write.group().trim());
                    }
                }
            }
        }

        System.out.printf("%n[SH245] %d write mapping(s) across the API surface; %d reach a content "
                + "repository directly%n", mappings, offenders.size());

        assertThat(offenders)
                .as("""
                        A content mutation goes through the writer that owns the verb. Four times \
                        the console's own copy was the careless one — a move that re-slugged live \
                        URLs, a folder move with no cycle guard, a delete with no trash or audit, \
                        a copy that duplicated URLs and did not terminate — and each was found by \
                        somebody reading the neighbouring file while fixing something else. Call \
                        ShObjectWriteService / ShFolderWriteService / ShPostUnifiedWriteService, or \
                        put a `ShContentWriter-exempt: <reason>` comment on the line saying why \
                        this one is different (SH245).""")
                .isEmpty();
    }

    @Test
    @DisplayName("SH245: the check would have caught the four it is named for")
    void theCheckWouldHaveCaughtTheFourItIsNamedFor() {
        // The population is 0 because all four were fixed, so this is the only thing standing
        // between "nothing is wrong" and "the regex stopped matching". SH200's shape, verbatim in
        // form: a delete mapping whose body reaches the repository instead of the writer.
        String sh200 = String.join("\n",
                "    @DeleteMapping(\"/{id}\")",
                "    public boolean delete(@PathVariable String id) {",
                "        shFolderRepository.deleteAll(shFolderRepository.findByParentFolder(id));",
                "        return true;",
                "    }");
        assertThat(offendersIn(sh200))
                .as("a delete mapping reaching the folder repository is exactly SH200")
                .hasSize(1);

        // And the exemption is honoured, so the hatch is real rather than documented.
        String waived = sh200.replace("shFolderRepository.deleteAll(",
                "/* ShContentWriter-exempt: the migration path predates the writer */ "
                        + "shFolderRepository.deleteAll(");
        assertThat(offendersIn(waived))
                .as("a waiver carrying its reason is permitted, which is what makes the rare "
                        + "legitimate case stated rather than silently allowed")
                .isEmpty();

        // A read mapping doing the same thing is not this lint's business, and a lint that could
        // not tell them apart would report every listing endpoint.
        assertThat(offendersIn(sh200.replace("@DeleteMapping", "@GetMapping")))
                .as("@GetMapping is not a write mapping")
                .isEmpty();
    }

    @Test
    @DisplayName("SH245: the walk reaches the API surface, and the content writes outside it are printed")
    void theWalkIsNotVacuousAndTheRestIsReported() {
        Map<String, String> api = sources(API);
        assertThat(api)
                .as("the API tree moved and this lint is now watching nothing")
                .hasSizeGreaterThan(100)
                .containsKey("ShObjectAPI.java");

        // SH245 scoped this to the API surface. Six content writes live in ordinary services —
        // the exchange import, the static-file upload, the draft merge — and each is a second
        // writer for some verb. Printed rather than asserted, because deciding they are wrong is
        // a task rather than a lint's opinion, and printing is how the next reader learns they
        // exist without this file pretending they do not.
        List<String> elsewhere = new ArrayList<>();
        sources(MAIN).forEach((name, body) -> {
            if (name.endsWith("WriteService.java") || name.endsWith("Repository.java")) {
                return;
            }
            for (String line : body.split("\n")) {
                if (CONTENT_WRITE.matcher(ShSourceText.codeLine(line)).find()) {
                    elsewhere.add(name);
                    return;
                }
            }
        });
        System.out.printf("[SH245] %d file(s) outside the writer services also write content: %s%n%n",
                elsewhere.size(), elsewhere);
    }

    /** The offenders one snippet would produce, so the detector can be tested without the tree. */
    private static List<String> offendersIn(String source) {
        List<String> found = new ArrayList<>();
        // Raw, like the walk: the waiver is a comment, and stripping it here would make the hatch
        // untestable — which is precisely what the first version of this method did.
        String[] lines = source.split("\n");
        for (int at = 0; at < lines.length; at++) {
            if (!WRITE_MAPPING.matcher(lines[at]).find()) {
                continue;
            }
            int end = lines.length;
            for (int scan = at + 1; scan < lines.length; scan++) {
                if (ANY_MAPPING.matcher(lines[scan]).find()) {
                    end = scan;
                    break;
                }
            }
            for (int scan = at; scan < end; scan++) {
                if (CONTENT_WRITE.matcher(ShSourceText.codeLine(lines[scan])).find()
                        && !lines[scan].contains(EXEMPT)) {
                    found.add(lines[scan].trim());
                }
            }
        }
        return found;
    }

    private static Map<String, String> sources(Path tree) {
        try (Stream<Path> files = Files.walk(tree)) {
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
            // Raw: the waiver is a comment, so a file stripped here could not carry one. Every
            // MATCH below runs through ShSourceText.codeLine, which is what the CODE half of the
            // declaration promises and what keeps a javadoc naming a repository call from
            // reporting itself (SH206's defect, SH613's rule).
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + path.toAbsolutePath(), e);
        }
    }
}
