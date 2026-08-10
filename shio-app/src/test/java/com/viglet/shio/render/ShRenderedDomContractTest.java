/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.lint.ShSourceScan;
import com.viglet.shio.lint.ShSourceText;

/**
 * SH300 — the rendered DOM is a contract, and the document that states it is checked.
 *
 * <h2>Why a test and not just a table</h2>
 *
 * <p>Nine {@code data-shio-*} attributes accumulated across four blocks, defined as constants in
 * three classes and explained in four changelog entries, with no single statement of what they mean
 * — while being read by the Universal Editor bridge, by anyone debugging a preview, and (SH299) by
 * the digest. The gap SH272 closed for helpers, on the other side of the renderer.</p>
 *
 * <p>A table alone would drift, and <b>silently</b>: the failure direction is an attribute that
 * gains a meaning nobody wrote down, which no functional test can see because the page still
 * renders. So this is SH273's instrument — the grammar-coverage table — applied to the emitted DOM:
 * every attribute the code emits must have a row, and every row must name something the code
 * emits.</p>
 *
 * <p>It reads <b>source</b> rather than reflecting over constants, for the same reason
 * {@code ShCsrfExemptionLintTest} and {@code ShAgentIncidentFramesTest} do: a literal written
 * inline, in Java or in the injected JavaScript, is exactly the case a constant-based scan would
 * miss, and it is the likeliest way a tenth attribute arrives.</p>
 *
 * @author Alexandre Oliveira
 */
@ShSourceScan(ShSourceScan.Reads.CODE)
class ShRenderedDomContractTest {

    private static final Path DOC =
            Path.of("..", "docs", "specs", "SH185-universal-editor-protocol.md");

    private static final Path RENDER_JAVA =
            Path.of("src", "main", "java", "com", "viglet", "shio", "render");

    private static final Path EDITOR_JS =
            Path.of("src", "main", "resources", "render", "shio-editor.js");

    private static final Pattern ATTRIBUTE = Pattern.compile("data-shio-[a-z-]+");

    /**
     * Names the bridge <em>reads</em> and nothing emits, each with the row that must say so.
     *
     * <p>Declared rather than excluded: a name the consumer looks for and the renderer never writes
     * is a capability that silently does nothing, which is worth a row precisely because it looks
     * like a working feature from the reading side.</p>
     *
     * <p><b>Empty since SH303</b>, which found the one entry — {@code data-shio-label} — and removed
     * the <em>read</em> rather than adding the write, because a field's label is declared by the
     * post-type and a copy in the markup is a second place for it to be wrong. The mechanism stays
     * for the next one.</p>
     */
    private static final Set<String> READ_BUT_NEVER_EMITTED = Set.of();

    @Test
    @DisplayName("every attribute the renderer emits has a row in the protocol document")
    void everyEmittedAttributeIsDocumented() {
        Set<String> emitted = produced();
        Set<String> documented = documented();

        assertThat(documented)
                .as("""
                        an attribute the rendered DOM carries and the document does not mention is \
                        a contract term nobody agreed to. Add a row to the appendix of \
                        docs/specs/SH185-universal-editor-protocol.md saying where it appears, \
                        what emits it and what it means.""")
                .containsAll(emitted);
    }

    @Test
    @DisplayName("every documented attribute is emitted, or declared as read-only")
    void everyDocumentedAttributeIsReal() {
        Set<String> read = attributesIn(Stream.of(read(EDITOR_JS)));
        Set<String> documented = new TreeSet<>(documented());
        documented.removeAll(produced());
        documented.removeAll(READ_BUT_NEVER_EMITTED);

        assertThat(documented)
                .as("""
                        a row naming an attribute nothing emits sends a reader looking for \
                        something that is not there — the same failure as a helper list naming \
                        one that is not registered (SH272). If it is read but never written, add \
                        it to READ_BUT_NEVER_EMITTED, which is a defect being tracked rather than \
                        a name being excused.""")
                .isEmpty();
        assertThat(read)
                .as("and the declared read-only names must still be read by the bridge, or the "
                        + "declaration has outlived the defect — SH142's rule")
                .containsAll(READ_BUT_NEVER_EMITTED);
    }

    @Test
    @DisplayName("the defect markers are the three the digest counts, and no more")
    void theDefectMarkersAreTheDocumentedThree() {
        // SH299 reads this list to count markers, so a fourth marker added without joining it
        // would be a defect the digest cannot see — a proof coming back green on a broken page,
        // which is exactly what SH299 exists to prevent.
        assertThat(ShRegionComposer.DEFECT_MARKERS)
                .containsExactly("data-shio-region-missing", "data-shio-region-unpublished",
                        "data-shio-theme-missing");
        String doc = read(DOC);
        assertThat(ShRegionComposer.DEFECT_MARKERS)
                .allSatisfy(marker -> assertThat(doc)
                        .as("%s must be in the appendix table", marker)
                        .contains(marker));
    }

    // --- reading ---

    /**
     * The attributes the appendix's <b>tables</b> name — rows only, not prose.
     *
     * <p>The distinction is load-bearing and was found by this test failing: SH303 removed
     * {@code data-shio-label} and the document still discusses it, correctly, in a paragraph
     * explaining why the read went away. A scan of the whole file read that paragraph as a claim
     * that the attribute exists. <b>The contract is the table</b>; prose is free to talk about
     * names that no longer exist, which is most of what makes a document worth reading.</p>
     */
    private static Set<String> documented() {
        return attributesIn(read(DOC).lines()
                .filter(line -> line.startsWith("|")));
    }


    /**
     * Every attribute something writes: the renderer's, plus the three the bridge sets at runtime.
     *
     * <p>Two producers, deliberately one set. A reader of the DOM cannot tell which side wrote an
     * attribute, and both are part of what a page carries — so a table that covered only the
     * server's half would be a contract with a hole exactly where a front end styling edit mode
     * looks. The document keeps them in separate tables because the <em>lifetime</em> differs: the
     * bridge's exist only while {@code ?editor=true} is on.</p>
     */
    private static Set<String> produced() {
        Set<String> produced = new TreeSet<>(attributesIn(javaSources()));
        Matcher matcher = Pattern.compile("setAttribute\\(\"(data-shio-[a-z-]+)\"")
                .matcher(read(EDITOR_JS));
        while (matcher.find()) {
            produced.add(matcher.group(1));
        }
        return produced;
    }


    private static Set<String> attributesIn(Stream<String> sources) {
        Set<String> found = new TreeSet<>();
        sources.forEach(source -> {
            Matcher matcher = ATTRIBUTE.matcher(source);
            while (matcher.find()) {
                found.add(matcher.group());
            }
        });
        return found;
    }

    /** Every Java source in the render package — where the emitting happens. */
    private static Stream<String> javaSources() {
        try (Stream<Path> files = Files.walk(RENDER_JAVA)) {
            Map<String, String> byName = new TreeMap<>();
            files.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> byName.put(path.getFileName().toString(),
                            // SH613 — code only, and only here: `read` also serves a markdown
                            // doc and a JS bundle, where a `//` is not always a comment.
                            ShSourceText.code(read(path))));
            return List.copyOf(byName.values()).stream();
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
