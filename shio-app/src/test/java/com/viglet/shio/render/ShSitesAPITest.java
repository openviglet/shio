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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SH451 — what a 0.3.x public URL means, asserted without a servlet.
 *
 * <h2>Why the split is the subject</h2>
 *
 * <p>The route is <b>recovered</b> rather than designed: branch {@code 0.3.8}'s
 * {@code ShSitesContextURLProcess} splits {@code /sites/<site>/<format>/<locale>/<path…>} by
 * position — {@code contexts[2]} the site, {@code [3]} the format, {@code [4]} the locale — and the
 * whole claim of this task is that the URLs of anything migrating from 0.3.x keep working. A claim
 * about what a URL means has to be a test of what a URL means, or it is a claim about a comment.</p>
 *
 * <p>The rendering half lives in {@link ShPageRenderService}, which has its own tests and is reached
 * here unchanged; the two variants and the defaults are what is new.</p>
 */
class ShSitesAPITest {

    @Test
    @DisplayName("SH451: the positional split is 0.3.8's, defaults included")
    void thePositionalSplitIsRecovered() {
        ShSitesAPI.Address home = ShSitesAPI.Address.of("/acme/default/en-us/", null, null);

        assertThat(home.site()).isEqualTo("acme");
        assertThat(home.path())
                .as("SH433: a site's home page is the post whose friendly URL is `/`, which is the "
                        + "one address form the SH79 grammar does not spell")
                .isEqualTo("/");
        assertThat(home.prefix()).isEqualTo("/sites/acme/default/en-us");

        ShSitesAPI.Address deep = ShSitesAPI.Address.of("/acme/default/en-us/blog/hello-world", null, null);
        assertThat(deep.path()).isEqualTo("/blog/hello-world");
    }

    @Test
    @DisplayName("SH451: the format and locale segments default when the URL omits them")
    void theSegmentsDefault() {
        // 0.3.8 defaulted them to `default` and `en-us`, so a URL that names neither is still a URL
        // and names the same page — which is what makes `/sites/acme/` a usable hand-typed address.
        ShSitesAPI.Address bare = ShSitesAPI.Address.of("/acme", null, null);

        assertThat(bare.site()).isEqualTo("acme");
        assertThat(bare.path()).isEqualTo("/");
        assertThat(bare.prefix()).isEqualTo("/sites/acme/default/en-us");
    }

    @Test
    @DisplayName("SH451: the prefix keeps the segments the caller used, not the defaults")
    void theCallersOwnSegmentsSurviveIntoEveryLink() {
        // The one thing a link rewrite must not do here: a reader who asked for `print`/`pt-br` and
        // gets links back to `default`/`en-us` has had both of their choices silently dropped on the
        // first click. So the prefix is built from what arrived.
        ShSitesAPI.Address chosen = ShSitesAPI.Address.of("/acme/print/pt-br/sobre", null, null);

        assertThat(chosen.prefix()).isEqualTo("/sites/acme/print/pt-br");
        assertThat(chosen.path()).isEqualTo("/sobre");
    }

    @Test
    @DisplayName("SH451: `sh-format` replaces the format segment, as it did in 0.3.8")
    void theFormatParameterOverridesTheSegment() {
        // Kept because it is part of the recovered shape, and inert because the format axis carries
        // no behaviour: a layout comes from `postTypeLayout` (SH328), and making this segment a
        // second binding table would be a feature rather than a recovery.
        ShSitesAPI.Address overridden = ShSitesAPI.Address.of("/acme/default/en-us/sobre", null, "print");

        assertThat(overridden.prefix())
                .as("it reaches the URL space, so a link keeps the reader in the format they asked for")
                .isEqualTo("/sites/acme/print/en-us");
        assertThat(overridden.path()).isEqualTo("/sobre");
    }

    @Test
    @DisplayName("SH451: `x-sh-site` makes the whole path content, which is how one host serves one site")
    void theHeaderVariantTakesTheWholePathAsContent() {
        ShSitesAPI.Address headed = ShSitesAPI.Address.of("/blog/hello-world", "acme", null);

        assertThat(headed.site()).isEqualTo("acme");
        assertThat(headed.path())
                .as("no site, format or locale is taken out of the path — the header said the site, "
                        + "so every segment is the content's")
                .isEqualTo("/blog/hello-world");
        assertThat(headed.prefix())
                .as("and a link needs only `/sites` in front of it, because the header travels with "
                        + "the request rather than with the URL")
                .isEqualTo("/sites");

        assertThat(ShSitesAPI.Address.of("/", "acme", null).path()).isEqualTo("/");
    }

    @Test
    @DisplayName("SH451: a URL with no site teaches the two ways to name one")
    void aUrlWithNoSiteIsATeaching400() {
        assertThatThrownBy(() -> ShSitesAPI.Address.of("/", null, null))
                .isInstanceOf(ShSitesAPI.NoSiteInPathException.class)
                .satisfies(thrown -> {
                    var problem = ((ShSitesAPI.NoSiteInPathException) thrown).problem();
                    assertThat(problem.slug()).isEqualTo("sites-no-site");
                    assertThat(problem.toProblemDetail().getProperties())
                            .extractingByKey("fix").asString()
                            // P6: both remedies, because which one is right depends on whether this
                            // host serves one site or many, and the server cannot know that.
                            .contains("x-sh-site").contains("content path");
                });
    }

    @Test
    @DisplayName("SH451: a trailing slash deeper in the path is kept, because it is a different URL")
    void aTrailingSlashSurvivesTheSplit() {
        // `"/acme/default/en-us/blog/".split("/")` drops the last empty segment, so `/blog/` and
        // `/blog` would collapse into one path — and they are two friendly URLs. Restored rather
        // than normalised away: normalising is a routing decision, and this class's job is to say
        // what the URL said.
        assertThat(ShSitesAPI.Address.of("/acme/default/en-us/blog/", null, null).path())
                .isEqualTo("/blog/");
        assertThat(ShSitesAPI.Address.of("/acme/default/en-us/blog", null, null).path())
                .isEqualTo("/blog");
    }
}
