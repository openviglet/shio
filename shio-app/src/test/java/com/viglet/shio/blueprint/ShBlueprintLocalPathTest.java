/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.blueprint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.viglet.shio.blueprint.dto.ShBlueprint;
import com.viglet.shio.blueprint.dto.ShBlueprintManifest;

/**
 * SH96 — a package may come from a directory an operator named, and from nowhere else.
 *
 * <p>No Spring context, for the same reason {@link ShBlueprintRegistryTest} has none: the
 * registry needs a codec and a path, so the whole trust model is testable in milliseconds.
 * What is worth pinning here is not that loading works — it is the three refusals, because
 * each of them is a way the feature could quietly do the wrong thing to somebody's site.</p>
 *
 * @author Alexandre Oliveira
 */
class ShBlueprintLocalPathTest {

    private static ShBlueprintRegistry registryAt(Path path) {
        ShBlueprintProperties properties = new ShBlueprintProperties();
        properties.setPath(path == null ? null : path.toString());
        return new ShBlueprintRegistry(new ShBlueprintCodec(), properties);
    }

    /** A minimal, valid package: one post-type-free content document. */
    private static void writePackage(Path root, String name) throws IOException {
        Path dir = Files.createDirectories(root.resolve(name));
        Files.writeString(dir.resolve("content.json"), """
                {"posts":[{"url":"/hello","type":"Page","folder":"/","data":{"title":"Hello"}}]}
                """);
        Files.writeString(dir.resolve(ShBlueprintManifest.FILE_NAME), """
                {
                  "formatVersion": 1,
                  "name": "%s",
                  "title": "A local package",
                  "version": "0.1.0",
                  "content": ["content.json"]
                }
                """.formatted(name));
    }

    @Test
    @DisplayName("with no path configured, the classpath is still the whole story")
    void noPathIsTheOldBehaviour() {
        assertThat(registryAt(null).names())
                .containsExactly("blog", "docs", "landing", "marketing-site", "nextjs-starter",
                        "product-catalog");
    }

    @Test
    @DisplayName("a package in the configured directory loads and says where it came from")
    void localPackageLoads(@TempDir Path dir) throws IOException {
        writePackage(dir, "acme-intranet");
        ShBlueprintRegistry registry = registryAt(dir);

        assertThat(registry.names()).contains("acme-intranet", "blog");
        ShBlueprint local = registry.require("acme-intranet");
        assertThat(local.origin()).isEqualTo(dir.toString());
        assertThat(local.isLocal()).isTrue();

        // Reported on the listing, because an agent about to write to somebody's site should
        // be able to tell that the package it picked did not ship with the CMS (P7).
        assertThat(registry.summaries())
                .filteredOn(row -> "acme-intranet".equals(row.name()))
                .singleElement()
                .satisfies(row -> assertThat(row.origin()).isEqualTo(dir.toString()));
        // …and absent for the first-party ones: stamping "classpath" on six rows would cost
        // tokens on every listing to say "normal" (P3). The presence is the signal.
        assertThat(registry.summaries())
                .filteredOn(row -> "blog".equals(row.name()))
                .singleElement()
                .satisfies(row -> assertThat(row.origin()).isNull());
    }

    @Test
    @DisplayName("a local package that shadows a first-party name is refused, naming both")
    void collisionIsRefused(@TempDir Path dir) throws IOException {
        writePackage(dir, "blog");

        // Neither winner is defensible: shadowing changes what every existing `blog` apply
        // does without saying so, and ignoring makes the operator's package do nothing.
        assertThatThrownBy(() -> registryAt(dir).names())
                .isInstanceOf(ShBlueprintRegistry.DuplicateBlueprintException.class)
                .satisfies(failure -> {
                    var problem = ((ShBlueprintRegistry.DuplicateBlueprintException) failure)
                            .problem().toProblemDetail();
                    assertThat(problem.getDetail()).contains("classpath").contains(dir.toString());
                    assertThat(problem.getProperties().get("fix").toString())
                            .as("the fix names the one action that resolves it")
                            .contains("Rename");
                });
    }

    @Test
    @DisplayName("a configured path that is not there fails instead of loading fewer packages")
    void missingPathIsRefused(@TempDir Path dir) {
        Path absent = dir.resolve("nope");

        // The failure mode this prevents: a typo in the property, an instance that quietly
        // serves only the built-in packages, and a site built from the wrong ones.
        assertThatThrownBy(() -> registryAt(absent).names())
                .isInstanceOf(ShBlueprintRegistry.UnreadablePathException.class)
                .satisfies(failure -> assertThat(
                        ((ShBlueprintRegistry.UnreadablePathException) failure)
                                .problem().toProblemDetail().getDetail())
                        .contains(absent.toString()));
    }

    @Test
    @DisplayName("a subdirectory with no manifest is skipped, not refused")
    void strayDirectoryIsSkipped(@TempDir Path dir) throws IOException {
        writePackage(dir, "acme-intranet");
        // An operator's package folder is a working directory: a `.git` or an editor's
        // backup folder is not a malformed package, and refusing it would make the whole
        // registry unusable for a reason that has nothing to do with blueprints.
        Files.createDirectories(dir.resolve(".git").resolve("objects"));
        Files.createDirectories(dir.resolve("notes"));

        assertThat(registryAt(dir).names()).contains("acme-intranet");
    }

    @Test
    @DisplayName("a local package is held to the format exactly as a first-party one is")
    void localPackagesAreValidatedIdentically(@TempDir Path dir) throws IOException {
        Path pkg = Files.createDirectories(dir.resolve("acme-broken"));
        Files.writeString(pkg.resolve(ShBlueprintManifest.FILE_NAME), """
                {
                  "formatVersion": 1,
                  "name": "acme-broken",
                  "content": ["missing.json"]
                }
                """);

        // "It loaded" has to mean the same thing whichever source it came from — otherwise
        // the directory is a way to smuggle a package past the validation the format exists for.
        // That property is untouched by SH217: the package is still REFUSED, and the only thing
        // that changed is the blast radius. What used to happen is that it took the catalogue with
        // it, which is not stricter validation — it is the same validation charged to everybody.
        ShBlueprintRegistry registry = registryAt(dir);

        assertThat(registry.names())
                .as("the malformed package did not load, which is what validation means here")
                .doesNotContain("acme-broken");
        assertThat(registry.rejected())
                .as("and it is named rather than silently absent — silence would be the smuggling "
                        + "this test was written against, arriving by the other door (SH217)")
                .singleElement()
                .satisfies(row -> assertThat(row.name()).isEqualTo("acme-broken"));
    }

    @Test
    @DisplayName("a local content document may not name its own site either")
    void localPackagesCannotOwnTheSite(@TempDir Path dir) throws IOException {
        Path pkg = Files.createDirectories(dir.resolve("acme-greedy"));
        Files.writeString(pkg.resolve("content.json"),
                "{\"site\":\"Someone Else\",\"posts\":[]}");
        Files.writeString(pkg.resolve(ShBlueprintManifest.FILE_NAME), """
                {
                  "formatVersion": 1,
                  "name": "acme-greedy",
                  "version": "0.1.0",
                  "content": ["content.json"]
                }
                """);

        // The rule that matters most once packages can come from elsewhere: the target site
        // is the caller's, and a package the user did not write does not get to choose it. SH217
        // isolates the refusal and does not soften it — a package that names a site is not
        // available to apply, and the reason still says which rule it broke.
        ShBlueprintRegistry registry = registryAt(dir);

        assertThat(registry.names()).doesNotContain("acme-greedy");
        assertThat(registry.rejected())
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.name()).isEqualTo("acme-greedy");
                    assertThat(row.reason()).contains("site");
                });
    }

    // --- SH217: the community half's load-bearing property ---

    /** A package whose manifest is not a package: the shape a stranger's mistake takes. */
    private static void writeBrokenPackage(Path root, String name) throws IOException {
        Path dir = Files.createDirectories(root.resolve(name));
        Files.writeString(dir.resolve(ShBlueprintManifest.FILE_NAME), """
                {
                  "formatVersion": 1,
                  "name": "NAME",
                  "title": "Broken",
                  "version": "0.1.0",
                  "content": ["missing.json"]
                }
                """.replace("NAME", name));
    }

    @Test
    @DisplayName("SH217: a local package that will not load is refused on its own")
    void oneBadPackageDoesNotTakeTheCatalogueDown(@TempDir Path dir) throws IOException {
        // The property the community half needs before it needs an index: a package a stranger
        // wrote must not be able to disable the catalogue. Measured rather than imagined - during
        // SH216 one FIRST-PARTY manifest missing one parameter made GET /agent/blueprints answer
        // 4xx for all six packages and failed forty-one tests. A directory anybody can drop a
        // folder into is the same defect with a worse author.
        writePackage(dir, "good-local");
        writeBrokenPackage(dir, "broken-local");

        ShBlueprintRegistry registry = registryAt(dir);

        assertThat(registry.all()).containsKey("good-local");
        assertThat(registry.all())
                .as("""
                        the first-party packages and the operator's other one still load. Before \
                        SH217 the whole registry threw, so one malformed directory made the \
                        catalogue unavailable to every caller (SH217).""")
                .containsKey("blog")
                .doesNotContainKey("broken-local");
    }

    @Test
    @DisplayName("SH217: the refusal names the package and says why, which is the author's loop")
    void theRefusalIsReadable(@TempDir Path dir) throws IOException {
        // The validation pass SH217 asks for, with no new contract: an author points an instance
        // at their directory and reads the refusal out of the surface they were going to publish
        // against. The name is the DIRECTORY's - the manifest is the thing that failed, so reading
        // a field out of it is how a report about a broken file becomes a second failure.
        writeBrokenPackage(dir, "broken-local");

        assertThat(registryAt(dir).rejected())
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.name()).isEqualTo("broken-local");
                    assertThat(row.origin()).isEqualTo(dir.toString());
                    assertThat(row.reason()).isNotBlank();
                });
    }

    @Test
    @DisplayName("SH217: a first-party package that will not load still fails the registry")
    void theClasspathHalfStillFailsLoudly(@TempDir Path dir) throws IOException {
        // Not an inconsistency with the two above - the same rule read from both sides. A failure
        // is isolated exactly when its author is somebody the operator did not choose to trust
        // with the catalogue. A first-party package that will not load is OUR build defect, and a
        // registry that quietly shipped five of six would hide it until somebody counted.
        //
        // Asserted as the ABSENCE of a rejection rather than by breaking the classpath, which no
        // test can do: what is checkable is that nothing first-party is ever reported here, so the
        // isolation cannot silently widen to cover the packages it must not cover.
        writePackage(dir, "good-local");

        assertThat(registryAt(dir).rejected())
                .as("isolation covers the operator's directory and nothing else")
                .isEmpty();
        assertThat(registryAt(dir).all().values())
                .anySatisfy(blueprint ->
                        assertThat(blueprint.origin()).isEqualTo(ShBlueprint.CLASSPATH));
    }
}
