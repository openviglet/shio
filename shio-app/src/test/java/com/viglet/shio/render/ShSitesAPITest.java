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
import org.springframework.mock.web.MockHttpServletRequest;

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
    @DisplayName("SH451: the prefix keeps the locale segment the caller used, not the default")
    void theCallersOwnSegmentsSurviveIntoEveryLink() {
        // The one thing a link rewrite must not do here: a reader who asked for `pt-br` and gets
        // links back to `en-us` has had their choice silently dropped on the first click. So the
        // prefix is built from what arrived.
        //
        // SH457 narrowed this to the locale: the format segment was `print` here, and a format that
        // is not `default` is now a 404 rather than a value carried into the prefix.
        ShSitesAPI.Address chosen = ShSitesAPI.Address.of("/acme/default/pt-br/sobre", null, null);

        assertThat(chosen.prefix()).isEqualTo("/sites/acme/default/pt-br");
        assertThat(chosen.path()).isEqualTo("/sobre");
    }

    @Test
    @DisplayName("SH451: `sh-format` still replaces the format segment, and SH457 still fixes its value")
    void theFormatParameterOverridesTheSegment() {
        // Kept because it is part of the recovered shape, and inert because the format axis carries
        // no behaviour: a layout comes from `postTypeLayout` (SH328), and making this segment a
        // second binding table would be a feature rather than a recovery.
        ShSitesAPI.Address overridden = ShSitesAPI.Address.of("/acme/whatever/en-us/sobre", null, "default");

        assertThat(overridden.prefix())
                .as("the parameter replaces the segment, so a URL whose segment would be refused is "
                        + "served when the parameter names the one value there is")
                .isEqualTo("/sites/acme/default/en-us");
        assertThat(overridden.path()).isEqualTo("/sobre");

        // And it is the *same* rule, applied to the value that won: an override is not an escape
        // from it. Vestigial means fixed wherever the value came from, or the two sources disagree
        // about one axis and a reader has to know which one is checked.
        assertThatThrownBy(() -> ShSitesAPI.Address.of("/acme/default/en-us/sobre", null, "print"))
                .isInstanceOf(ShSitesAPI.UnknownFormatException.class);
    }

    @Test
    @DisplayName("SH457: a format that is not `default` is a 404, because that is what a missing asset is")
    void aVestigialSegmentAcceptsOneValue() {
        // The measured defect: `GET /sites/publico/viglet-logo.png` answered **200 text/html** with
        // the home page's 10265 bytes, and so did `/sites/publico/qualquer`. With any value in the
        // format slot and no segments after it the content path is empty, so the resolver returns
        // the home page — and that shape is what every root-relative asset becomes. A page served at
        // `/sites/publico/` carrying `<img src="/viglet-logo.png">` requests exactly this URL, so a
        // missing image stopped being a 404 an author can see and became a silent soft-200.
        assertThatThrownBy(() -> ShSitesAPI.Address.of("/publico/viglet-logo.png", null, null))
                .isInstanceOf(ShSitesAPI.UnknownFormatException.class)
                .satisfies(thrown -> {
                    var problem = ((ShSitesAPI.UnknownFormatException) thrown).problem();
                    assertThat(problem.slug()).isEqualTo("sites-unknown-format");
                    assertThat(problem.status().value())
                            .as("404 and not 400: the sender is a browser resolving an <img>, not a "
                                    + "person who typed a malformed URL, and `it is not here` is the "
                                    + "answer an author can act on")
                            .isEqualTo(404);
                    assertThat(problem.toProblemDetail().getProperties())
                            .extractingByKey("fix").asString()
                            // P6: the remedy is where the path should have gone, and it names the
                            // rewrite whose absence is why the request arrived here at all.
                            .contains("/sites/<site>/default/en-us/viglet-logo.png");
                });

        // The four-segment form lies the same way and for the same reason — `img` lands in the
        // format slot, `missing.png` in the locale slot, and the content path is empty.
        assertThatThrownBy(() -> ShSitesAPI.Address.of("/publico/img/missing.png", null, null))
                .isInstanceOf(ShSitesAPI.UnknownFormatException.class);

        // And the three URLs the probe found correct stay correct: the home page with the segments
        // spelled, the home page with them omitted, and a real content path underneath them.
        assertThat(ShSitesAPI.Address.of("/publico/default/en-us/", null, null).path()).isEqualTo("/");
        assertThat(ShSitesAPI.Address.of("/publico", null, null).path()).isEqualTo("/");
        assertThat(ShSitesAPI.Address.of("/publico/default/en-us/sobre", null, null).path())
                .isEqualTo("/sobre");
    }

    @Test
    @DisplayName("SH457: the header form takes the whole path as content, so no segment is a format")
    void theHeaderFormIsUnaffectedByTheFixedSegment() {
        // `x-sh-site` is the one-host-one-site variant, and it has no format slot at all — every
        // segment is the content's. So the narrowing above must not reach it, or the variant that
        // makes root-relative paths work natively is the one it breaks.
        assertThat(ShSitesAPI.Address.of("/viglet-logo.png", "publico", null).path())
                .isEqualTo("/viglet-logo.png");
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
        // SH467: and for two blocks that was the whole story — the split said `/blog/` faithfully and
        // the renderer answered 404, because the stored furl is the grammar's slash-free form. The
        // fidelity of this class was real and reached nothing; `ShRenderRedirectTest` holds the half
        // that makes it answer.
        assertThat(ShSitesAPI.Address.of("/acme/default/en-us/blog/", null, null).path())
                .isEqualTo("/blog/");
        assertThat(ShSitesAPI.Address.of("/acme/default/en-us/blog", null, null).path())
                .isEqualTo("/blog");
    }

    @Test
    @DisplayName("SH467: a redirect keeps the reader's query, unless the target chose one")
    void aRedirectKeepsTheReadersQuery() {
        // Invisible while every redirect came from a `Redirect` post a human wrote. It stops being so
        // the moment the route canonicalises a trailing slash by itself: `/search/?q=shio` arriving at
        // `/search` with no term is a worse answer than the 404 it replaces.
        MockHttpServletRequest reader = new MockHttpServletRequest();
        reader.setQueryString("q=shio&page=2");

        assertThat(ShSitesAPI.withQueryOf(reader, "/sites/acme/default/en-us/search"))
                .isEqualTo("/sites/acme/default/en-us/search?q=shio&page=2");
        assertThat(ShSitesAPI.withQueryOf(reader, "https://elsewhere.test/x?ref=1"))
                .as("a target that carries its own query chose it, and appending a second would "
                        + "produce a URL neither side wrote")
                .isEqualTo("https://elsewhere.test/x?ref=1");
        assertThat(ShSitesAPI.withQueryOf(new MockHttpServletRequest(), "/sites/acme/x"))
                .isEqualTo("/sites/acme/x");
    }
}
