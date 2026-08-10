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

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.blueprint.dto.ShBlueprint;

/**
 * SH92/SH94 — the registry loads and validates the first-party set.
 *
 * <h2>Why this test is the one that matters most in the block</h2>
 *
 * <p>Everything the format promises is enforced at load: that a referenced file exists,
 * that a content document does not name its own site, that every {@code {{placeholder}}}
 * has a declared parameter, and that a package cannot promise a URL it never creates. Each
 * of those is an <em>author's</em> mistake, and each would otherwise surface as a stranger's
 * half-applied site. So the assertion "all five first-party packages load" is not a
 * smoke test — it is the whole validation suite running against real packages, and it is
 * what stops a shipped blueprint from being broken in a way nobody notices until someone
 * applies it.</p>
 *
 * <p>No Spring context: the registry reads the classpath and needs only the codec, so this
 * runs in milliseconds and can therefore be run on every change to a package.</p>
 *
 * @author Alexandre Oliveira
 */
class ShBlueprintRegistryTest {

    private final ShBlueprintRegistry registry = new ShBlueprintRegistry(new ShBlueprintCodec());

    @Test
    @DisplayName("the first-party set loads, validated, in a stable order")
    void firstPartySetLoads() {
        assertThat(registry.names())
                .containsExactly("blog", "docs", "landing", "marketing-site", "nextjs-starter",
                        "product-catalog");
    }

    /**
     * SH119 — the starter is a blueprint, and the only first-party package that carries
     * front-end files. Two properties matter and neither is obvious from the manifest:
     * the tree really is attached (a package whose `files` list resolved to nothing would
     * load clean and deliver half a starter), and a Next.js catch-all route survives the
     * path-escape guard, which is where `contains("..")` used to reject `[...slug]`.
     */
    @Test
    @DisplayName("the Next.js starter carries its front-end half, catch-all route included")
    void starterCarriesItsFrontEnd() {
        ShBlueprint starter = registry.require("nextjs-starter");
        assertThat(starter.files()).isNotEmpty();
        assertThat(starter.files()).allSatisfy(file -> {
            assertThat(file.path()).as("a carried file needs a project-relative path").isNotBlank();
            assertThat(file.text()).as("%s has no body", file.path()).isNotBlank();
        });
        assertThat(starter.files().stream().map(ShBlueprint.File::path))
                .contains("app/[...slug]/page.tsx", "package.json", ".gitignore");
        assertThat(starter.postTypeNames()).containsExactly("Page");
    }

    /**
     * The path-escape guard, at the boundary SH119 had to move. Asserted directly and in
     * both directions because a security check that has been loosened is exactly the kind
     * that needs to be shown still catching what it was written for (§XIV.4: characterize
     * by execution, and self-test the guard).
     */
    @Test
    @DisplayName("a `..` segment escapes the package; a dot run inside a filename does not")
    void pathEscapeGuardChecksSegmentsNotSubstrings() {
        assertThat(ShBlueprintRegistry.traverses("../../etc/passwd")).isTrue();
        assertThat(ShBlueprintRegistry.traverses("files/../../secret")).isTrue();
        assertThat(ShBlueprintRegistry.traverses("files/..")).isTrue();
        assertThat(ShBlueprintRegistry.traverses("files\\..\\secret")).isTrue();

        assertThat(ShBlueprintRegistry.traverses("files/app/[...slug]/page.tsx")).isFalse();
        assertThat(ShBlueprintRegistry.traverses("content/pages.json")).isFalse();
        assertThat(ShBlueprintRegistry.traverses("files/next-env.d.ts")).isFalse();
        // A filename may legitimately be dots-and-more; only a whole segment of `..` walks up.
        assertThat(ShBlueprintRegistry.traverses("files/...rc")).isFalse();
    }

    @Test
    @DisplayName("every package describes itself well enough to be chosen without reading it")
    void everyPackageIsSelfDescribing() {
        for (ShBlueprint.Summary summary : registry.summaries()) {
            assertThat(summary.title()).as("%s title", summary.name()).isNotBlank();
            assertThat(summary.version()).as("%s version", summary.name()).isNotBlank();
            assertThat(summary.description()).as("%s description", summary.name())
                    .isNotBlank()
                    // The description is what an agent reads to choose. One clause is not a
                    // description; it is a name repeated.
                    .hasSizeGreaterThan(60);
            assertThat(summary.postTypes() + summary.posts())
                    .as("%s applies something", summary.name()).isPositive();
        }
    }

    @Test
    @DisplayName("SH395: blog carries its own design, not just its content")
    void blogIsWhatItSaysItIs() {
        ShBlueprint blog = registry.require("blog");

        assertThat(blog.postTypeNames()).containsExactly("Article");
        // Two folders and five posts since SH395: `/theme` holding a Theme and a PageLayout,
        // beside `/blog` and its three samples. Before it, applying this package left every page
        // on SH169's labelled fallback — a safety net, not a design — because the format could
        // not express a layout at all.
        assertThat(blog.declaredFolders()).isEqualTo(2);
        assertThat(blog.declaredPosts()).isEqualTo(5);
        assertThat(blog.manifest().verify().posts()).contains("/blog/hello-world");
        assertThat(blog.manifest().verify().checks()).isEqualTo("content");
    }

    @Test
    @DisplayName("the section-based packages carry the whole vocabulary")
    void sectionPackagesCarryTheVocabulary() {
        for (String name : List.of("marketing-site", "landing")) {
            assertThat(registry.require(name).postTypeNames())
                    .as(name)
                    .containsExactly("Page", "SectionCta", "SectionFaq", "SectionFeatureGrid",
                            "SectionHero", "SectionLogoWall", "SectionRichText",
                            "SectionTestimonial");
        }
    }

    @Test
    @DisplayName("a package that needs a value it cannot invent declares it required")
    void requiredParametersAreDeclared() {
        assertThat(ShBlueprintParams.required(registry.require("marketing-site")
                .manifest().params())).containsExactly("brand");
        assertThat(ShBlueprintParams.required(registry.require("docs")
                .manifest().params())).containsExactly("productName");
        // A default is a decision the package is willing to make on the caller's behalf;
        // a brand name is not one of those.
        assertThat(ShBlueprintParams.properties(registry.require("blog").manifest().params())
                .get("blogTitle").get("default").asString()).isEqualTo("Blog");
    }

    @Test
    @DisplayName("names are case-insensitive; an unknown one teaches the set and the near match")
    void unknownNameTeaches() {
        assertThat(registry.require("BLOG").name()).isEqualTo("blog");

        assertThatThrownBy(() -> registry.require("blogs"))
                .isInstanceOf(ShBlueprintRegistry.UnknownBlueprintException.class)
                .satisfies(failure -> {
                    var problem = ((ShTeachingException) failure).problem().toProblemDetail();
                    assertThat(problem.getStatus()).isEqualTo(404);
                    assertThat(problem.getProperties()).containsEntry("didYouMean", "blog");
                });
    }

    @Test
    @DisplayName("no package declares `site` or `prune` — the two things a package may not decide")
    void packagesDoNotOwnSiteOrPrune() {
        for (ShBlueprint blueprint : registry.all().values()) {
            assertThat(ShBlueprintParams.properties(blueprint.manifest().params()))
                    .as("%s params", blueprint.name())
                    .doesNotContainKey("site");
            // SH395 added `postTypes` and `postTypeLayout` — the two a package CAN answer for
            // itself. `site` and `prune` stay out for the reasons this test is named for: the
            // first comes from the request, and the second is never a package's decision.
            blueprint.content().forEach(document ->
                    assertThat(document.propertyNames())
                            .as("%s content keys", blueprint.name())
                            .allMatch(ShBlueprintRegistry.CONTENT_KEYS::contains));
        }
    }

    @Test
    @DisplayName("every promised URL is one the package's own content declares")
    void promisesAreKeepable() {
        for (ShBlueprint blueprint : registry.all().values()) {
            if (blueprint.manifest().verify() == null) {
                continue;
            }
            List<String> declared = blueprint.content().stream()
                    .flatMap(document -> document.get("posts") == null
                            ? java.util.stream.Stream.<String>of()
                            : java.util.stream.StreamSupport.stream(
                                    document.get("posts").spliterator(), false)
                            .map(post -> post.get("url").asString()))
                    .toList();
            assertThat(declared)
                    .as("%s promises", blueprint.name())
                    .containsAll(blueprint.manifest().verify().posts());
        }
    }
}
