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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.lint.ShSourceScan;
import com.viglet.shio.lint.ShSourceText;

/**
 * SH529 — three removals, held by an assertion instead of by memory (SH20, SH21, SH60).
 *
 * <h2>Why an absence needs a test at all</h2>
 *
 * <p>SH527's rule is that a defect closes with the assertion that would have caught it, and SH529
 * is the debt already standing: the closed work no assertion holds. Removals were the cheapest
 * group on that list and the least defensible — <em>"an absence is assertable, the way
 * {@code ShIdeCompiledClassLintTest} asserts a state rather than a behaviour, and nothing here does
 * it, so a re-introduction would be silent"</em>.</p>
 *
 * <h2>It was not a re-introduction. The removal was never finished (SH620).</h2>
 *
 * <p>Writing this found that {@code src/main/resources/ui/} still held <b>four tracked files</b> —
 * {@code shio.js}, {@code shio.min.js}, {@code shFeature.js} and {@code shSystem.js}, about 456 KB
 * of the legacy AngularJS bundle — under a commit whose own subject is <i>"delete legacy AngularJS
 * resources/ui tree"</i>. Nothing referenced them and nothing served them: Spring Boot serves
 * {@code resources/public/**} and {@code resources/static/**}, never {@code resources/ui/**}. They
 * were dead weight that shipped in every jar, for two blocks, because a deletion of 330 files is
 * reviewed by its diff and nobody counts what is left.</p>
 *
 * <p>That is the argument for the whole SH529 exercise in one case: the debt list said this entry
 * was <em>unasserted</em>, and the assertion turned out to be red. A rule kept by memory is a rule
 * nobody can be wrong about out loud.</p>
 *
 * <h2>What is checked, and what deliberately is not</h2>
 *
 * <p>The <b>file names</b>, not the directory. The four above are unmistakably the legacy bundle,
 * so a re-introduction would carry them; a working copy with untracked leftovers elsewhere under
 * {@code resources/ui/} is a local state and not a repository one, and failing somebody's build for
 * it would teach them to distrust this test.</p>
 *
 * @author Alexandre Oliveira
 */
@ShSourceScan(value = ShSourceScan.Reads.BOTH,
        reason = "SH827 added a content scan beside the file-name ones. It reads CODE for the Java "
                + "sources — `ShSourceText.code`, because this repository explains its removals by "
                + "naming them and a raw read reports the explanation as the defect — and raw text "
                + "for the Liquibase YAML, where a re-added column is data rather than a symbol. "
                + "The vacuity check then asserts a PROSE phrase is *not* found, which is the "
                + "distinction itself and only says anything if both halves are read.")
class ShRemovalsStayedRemovedTest {

    private static final Path MAIN_RESOURCES = Path.of("src", "main", "resources");
    private static final Path MAIN_JAVA = Path.of("src", "main", "java");

    /** SH21 — the legacy console's bundles, by the names only it ever used. */
    private static final List<String> ANGULAR_BUNDLES =
            List.of("shio.js", "shio.min.js", "shFeature.js", "shSystem.js");

    @Test
    @DisplayName("SH21: no file of the legacy AngularJS console is in the tree")
    void theAngularConsoleIsGone() {
        List<String> found = matching(MAIN_RESOURCES, ANGULAR_BUNDLES::contains);

        assertThat(found)
                .as("""
                        SH21 deleted 330 files and left four, about 456 KB of AngularJS that nothing \
                        references and nothing serves — `resources/ui/**` is not a served root — and \
                        that shipped in every jar for two blocks. A deletion is reviewed by its diff, \
                        and nobody counts what is left, which is why this is a test and not a habit.""")
                .isEmpty();
    }

    @Test
    @DisplayName("SH20: no Thymeleaf template engine came back with a page renderer")
    void thymeleafIsGone() {
        // The dependency, not a template file: SH20's finding was that the *engine* had no server-side
        // site rendering left to do. `ShTemplateEngine` (Handlebars) is the renderer this product has,
        // and a second one arriving would be a fork of the render path rather than a new file.
        List<String> poms = new ArrayList<>();
        for (Path pom : List.of(Path.of("pom.xml"), Path.of("..", "pom.xml"))) {
            if (Files.exists(pom) && read(pom).contains("spring-boot-starter-thymeleaf")) {
                poms.add(pom.toString());
            }
        }

        assertThat(poms)
                .as("""
                        SH20 found no server-side Thymeleaf site rendering left — the nuclear refactor \
                        had already deleted the split-model page renderer. Re-adding the starter would \
                        put a second template engine beside SH183's Handlebars one, which is a fork of \
                        the render path and not a new feature.""")
                .isEmpty();
    }

    @Test
    @DisplayName("SH60: the deprecated commerce and recaptcha widgets were not ported")
    void theLegacyWidgetsAreGone() {
        // SH60 was a port-or-deprecate decision, and "deprecate" is only a decision if something
        // notices it being reversed. Names rather than a directory: these are what the AngularJS
        // widgets were called, and a port would arrive carrying them.
        List<String> found = new ArrayList<>();
        for (Path root : List.of(MAIN_JAVA, MAIN_RESOURCES)) {
            found.addAll(matching(root, name -> {
                String lower = name.toLowerCase(java.util.Locale.ROOT);
                return lower.contains("recaptcha") || lower.contains("shopline")
                        || lower.contains("redecard") || lower.contains("paymentslip");
            }));
        }

        assertThat(found)
                .as("""
                        SH60 decided these are not ported: payment-slip (boleto), rede-card, Cielo \
                        shopline and recaptcha. A decision nothing checks is a preference, and the \
                        console migration is exactly the kind of work that re-adds one by accident.""")
                .isEmpty();
    }

    @Test
    @DisplayName("the guard is not vacuous: it finds a name that is there")
    void theScanWorks() {
        // Guards the guard (SH275's habit). A scan that matched nothing would pass forever, which is
        // the failure every lint in this repository is written against — and it is the *only* failure
        // mode a test asserting an absence has.
        assertThat(matching(MAIN_JAVA, name -> name.equals("ShTemplateEngine.java")))
                .as("the walk reaches src/main/java and matches by file name")
                .isNotEmpty();
        assertThat(matching(MAIN_RESOURCES, name -> name.equals("application.properties")))
                .as("and reaches src/main/resources too, or the three assertions above are theatre")
                .isNotEmpty();

        // SH827 — the content scan needs its own proof, and for a stronger reason than the two
        // above: `declaring` strips comments, so a strip that took the whole file would report a
        // clean tree for ever and look exactly like a removal that held.
        assertThat(declaring(MAIN_JAVA, "ShPostTypeAttr"))
                .as("SH827: `declaring` finds a symbol that IS there. An absence assertion has one "
                        + "failure mode — matching nothing — and this one strips comments first, "
                        + "which is a second way to match nothing that reads identically")
                .isNotEmpty();
        assertThat(declaring(MAIN_JAVA, "SH177 dropped the column"))
                .as("SH827: and does NOT find a phrase that exists only in prose, which is the "
                        + "distinction it was built for — this repository explains its removals by "
                        + "naming them, so a raw scan reports the explanation as the defect")
                .isEmpty();
    }

    private static List<String> matching(Path root, java.util.function.Predicate<String> name) {
        if (!Files.exists(root)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> name.test(path.getFileName().toString()))
                    .map(Path::toString)
                    .sorted()
                    .toList();
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

    /**
     * SH827 — the denormalization that was correct exactly once.
     *
     * <h2>What SH177 removed, and why an absence is the right shape for it</h2>
     *
     * <p>{@code schemaJson} was a denormalized copy of a post-type's field list, and its only writer
     * was the one-shot {@code POST /api/v2/migration/unified/run}. So it was right the day the
     * migration ran, <b>empty</b> for every type a blueprint or {@code shio push} created after, and
     * <b>stale</b> for every type edited after — a denormalization with no invalidation. The column
     * went, and with it the migration service, its endpoint and the console's own
     * {@code ShPostUnifiedMigrationService}, because the model already carries {@code fields} and
     * one shape is cheaper to keep right than two.</p>
     *
     * <p>Nothing would notice any of it coming back, which is what this class is for: a removal is
     * the one regression with no human in its path. The likely reversal is not a revert either — it
     * is somebody adding a cached field list for a perfectly good reason and rediscovering, at
     * whatever cost SH177 already paid, that there is no invalidation to hang it on.</p>
     *
     * <h2>By content, not by file name</h2>
     *
     * <p>The other assertions here match file names, which is right for a bundle or a widget. A
     * dropped column and a retired mapping live <em>inside</em> files that legitimately remain, so
     * this reads them. {@code ShSourceText.code} strips the comments first — {@code post-unified.ts}
     * and this very javadoc explain the removal by naming it, and a scan that could not tell a
     * mention from a use would report the explanation as the defect (SH825's family).</p>
     */
    @Test
    @DisplayName("SH177: schemaJson, its migration endpoint and both migration services are gone")
    void theDenormalizedSchemaStayedRemoved() {
        List<String> found = new ArrayList<>();

        for (String name : List.of("schemaJson", "migration/unified", "ShPostUnifiedMigrationService")) {
            found.addAll(declaring(MAIN_JAVA, name));
        }
        // The column itself: a Liquibase changeset adding it back would not name a Java symbol.
        found.addAll(declaring(MAIN_RESOURCES, "schemaJson"));
        found.addAll(matching(MAIN_JAVA, file -> file.contains("MigrationService")));

        assertThat(found)
                .as("""
                        SH177 dropped `schemaJson` and everything that wrote it. A copy of the \
                        field list with no invalidation is right on the day it is built and wrong \
                        from the next write onward — empty for every post-type created after, \
                        stale for every one edited after — and the model already carries `fields`.
                        If this is a deliberate return, it needs the invalidation SH177 could not \
                        find, and this assertion is where to say so.""")
                .isEmpty();
    }

    /**
     * Files under {@code root} whose <b>code</b> names {@code needle} — comments stripped (SH827).
     *
     * <p>The strip is not tidiness. This repository explains its removals in prose, so the sentence
     * *"there is no `schemaJson` — SH177 dropped the column"* is exactly the text a raw scan would
     * report as the column coming back. Same distinction {@code @ShSourceScan} exists to make, one
     * assertion along.</p>
     */
    private static List<String> declaring(Path root, String needle) {
        if (!Files.exists(root)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> {
                        String file = path.getFileName().toString();
                        return file.endsWith(".java") || file.endsWith(".yaml") || file.endsWith(".xml");
                    })
                    .filter(path -> readCode(path).contains(needle))
                    .map(path -> path + " names " + needle)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String readCode(Path path) {
        try {
            String body = Files.readString(path);
            return path.getFileName().toString().endsWith(".java")
                    ? ShSourceText.code(body)
                    : body;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
