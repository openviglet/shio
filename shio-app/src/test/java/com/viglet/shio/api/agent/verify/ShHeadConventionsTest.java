/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.verify;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SH388 — the head scanner, which is where every decision this task makes actually lives.
 *
 * <h2>Why a pure test and not a Spring one</h2>
 *
 * <p>The whole point of the design SH388 arrived at is that <b>no render is needed</b>: the defect is a
 * property of the layout's stored markup, so the check is a function from a string to a list. That
 * makes the instrument a plain unit test with no context, no database and no fixture — which is also
 * the evidence for the design claim. A test that needed a running instance to check a head would mean
 * the cheap version had not been found.
 *
 * <h2>What is asserted is mostly the silence</h2>
 *
 * <p>A lint over templates fires on somebody's correct work if it is wrong, and then they stop reading
 * it. So most of this file is layouts that must produce <b>nothing</b>: a head that interpolates, an
 * absolute {@code og:image}, one built out of {@code site.baseUrl}, a fragment with no head at all.
 *
 * @author Alexandre Oliveira
 */
class ShHeadConventionsTest {

    private static List<String> rulesOf(String html) {
        return ShHeadConventions.of(html).stream().map(ShHeadConventions.Finding::rule).sorted().toList();
    }

    /** A head that gets everything right, so every assertion below is about a difference from it. */
    private static final String GOOD = """
            <html><head>
            <title>{{post.title}} — {{site.name}}</title>
            <meta name="description" content="{{post.summary}}">
            <link rel="canonical" href="{{post.canonical}}">
            <meta property="og:image" content="{{site.baseUrl}}/img/card.png">
            </head><body><h1>{{post.title}}</h1></body></html>""";

    @Test
    @DisplayName("a head that interpolates everything and builds og:image absolutely is silent")
    void aCorrectHeadIsSilent() {
        assertThat(ShHeadConventions.of(GOOD)).isEmpty();
    }

    @Test
    @DisplayName("a layout with no <head> at all yields nothing, rather than five findings")
    void aHeadlessFragmentIsSilent() {
        // A fragment or a deliberately headless template. Reporting the absence of every convention
        // would bury the layouts that HAVE a head and get it wrong, which are the ones worth finding.
        assertThat(ShHeadConventions.of("<div><h1>{{post.title}}</h1></div>")).isEmpty();
        assertThat(ShHeadConventions.of(null)).isEmpty();
        assertThat(ShHeadConventions.of("  ")).isEmpty();
    }

    @Test
    @DisplayName("a static <title> is the whole point: it has the same value on every page, by construction")
    void aStaticTitleIsReported() {
        List<ShHeadConventions.Finding> found = ShHeadConventions.of(
                GOOD.replace("<title>{{post.title}} — {{site.name}}</title>", "<title>Acme Cloud</title>"));

        assertThat(found).singleElement().satisfies(finding -> {
            assertThat(finding.rule()).isEqualTo("head-static-title");
            assertThat(finding.error())
                    .as("a warning: a one-page site legitimately has one title, and a layout bound to "
                            + "a single post is not wrong")
                    .isFalse();
            assertThat(finding.detail()).contains("Acme Cloud");
        });
    }

    @Test
    @DisplayName("any Handlebars expression counts as interpolation, including a block")
    void interpolationIsGenerous() {
        // Deliberately generous, because the claim being made is narrow: this value CANNOT differ
        // between two pages. A conditional title can, however oddly it is written.
        for (String title : List.of("{{TITLE}}", "{{post.title}}", "{{#if SEO}}{{SEO}}{{else}}x{{/if}}",
                "Acme — {{post.title}}")) {
            assertThat(rulesOf(GOOD.replace("{{post.title}} — {{site.name}}", title)))
                    .as(title)
                    .doesNotContain("head-static-title");
        }
    }

    @Test
    @DisplayName("a missing title, description and canonical are each their own warning")
    void theAbsencesAreReported() {
        assertThat(rulesOf("<html><head><meta charset=\"utf-8\"></head><body></body></html>"))
                .containsExactly("head-no-canonical", "head-no-description", "head-no-title");
    }

    @Test
    @DisplayName("a static description is reported, which is the sixty-pages-one-description defect")
    void aStaticDescriptionIsReported() {
        assertThat(rulesOf(GOOD.replace("content=\"{{post.summary}}\"", "content=\"Acme does cloud\"")))
                .containsExactly("head-static-description");
    }

    @Test
    @DisplayName("a relative og:image is an ERROR, because the tag renders perfectly and the card is blank")
    void aRelativeOgImageIsAnError() {
        List<ShHeadConventions.Finding> found = ShHeadConventions.of(
                GOOD.replace("{{site.baseUrl}}/img/card.png", "/img/card.png"));

        assertThat(found).singleElement().satisfies(finding -> {
            assertThat(finding.rule()).isEqualTo("head-relative-og-image");
            // A crawler resolves it against ITS own origin, so nothing about the page looks wrong.
            // `site.baseUrl` (SH337) exists for exactly this.
            assertThat(finding.error()).isTrue();
        });
    }

    @Test
    @DisplayName("an og:image this scanner cannot judge is left alone, not guessed at")
    void anUnjudgeableOgImageIsSilent() {
        // `{{OG_IMAGE}}` may hold anything, and reporting it would fire on the layout that is doing
        // it right — which is how a lint over templates gets ignored.
        for (String value : List.of("{{OG_IMAGE}}", "{{site.baseUrl}}/a.png", "https://x.test/a.png",
                "//cdn.x.test/a.png", "data:image/png;base64,AAA")) {
            assertThat(rulesOf(GOOD.replace("{{site.baseUrl}}/img/card.png", value)))
                    .as(value)
                    .doesNotContain("head-relative-og-image");
        }
    }

    @Test
    @DisplayName("og:image is matched on `name` too, because both spellings are in the wild")
    void bothOgSpellingsAreMatched() {
        assertThat(rulesOf(GOOD.replace("property=\"og:image\" content=\"{{site.baseUrl}}/img/card.png\"",
                "name=\"og:image\" content=\"/img/card.png\"")))
                .contains("head-relative-og-image");
    }

    @Test
    @DisplayName("a robots noindex is an ERROR: it is what a preview-shaped conversion carries over")
    void noindexIsAnError() {
        List<ShHeadConventions.Finding> found = ShHeadConventions.of(
                GOOD.replace("</head>", "<meta name=\"robots\" content=\"noindex, nofollow\"></head>"));

        assertThat(found).singleElement().satisfies(finding -> {
            assertThat(finding.rule()).isEqualTo("head-noindex");
            assertThat(finding.error()).isTrue();
        });
        // An ordinary robots value is not a defect.
        assertThat(rulesOf(GOOD.replace("</head>",
                "<meta name=\"robots\" content=\"index, follow\"></head>"))).isEmpty();
    }

    @Test
    @DisplayName("a <title> inside a comment or a script is not a title")
    void ignoredBlocksAreIgnored() {
        // The failure this prevents is the interesting direction: a commented-out good title would
        // make a layout with a *static* one look correct.
        assertThat(rulesOf("<html><head><!-- <title>{{post.title}}</title> -->"
                + "<title>Acme</title><meta name=\"description\" content=\"{{x}}\">"
                + "<link rel=canonical href=\"{{post.canonical}}\"></head><body></body></html>"))
                .containsExactly("head-static-title");
    }

    @Test
    @DisplayName("an unquoted rel=canonical still counts, because real markup writes it that way")
    void attributesNeedNoQuotes() {
        assertThat(rulesOf("<html><head><title>{{t}}</title>"
                + "<meta name=description content=\"{{d}}\">"
                + "<link rel=canonical href=\"{{post.canonical}}\"></head><body></body></html>"))
                .isEmpty();
    }

    @Test
    @DisplayName("scanning the same layout twice yields the same findings in the same order")
    void scanningIsStable() {
        String messy = "<html><head><title>A</title><meta name=\"robots\" content=\"noindex\">"
                + "<meta property=\"og:image\" content=\"/a.png\"></head><body></body></html>";

        assertThat(ShHeadConventions.of(messy)).isEqualTo(ShHeadConventions.of(messy));
        assertThat(rulesOf(messy)).containsExactly(
                "head-no-canonical", "head-no-description", "head-noindex",
                "head-relative-og-image", "head-static-title");
    }
}
