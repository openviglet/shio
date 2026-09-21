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

import org.junit.jupiter.api.Test;

/**
 * SH1055 — the accessibility family, driven against a page holding one of each defect.
 *
 * <h2>Both directions, for every rule</h2>
 *
 * <p>A rule is asserted twice here: once on markup that breaks it, and once on the markup that is
 * the <b>correct</b> form of the same thing. The second half is what this file is really for —
 * every one of these rules has a shape that looks wrong and is right, and a rule that fires on it
 * is a rule a team turns off, taking the four that were correct with it. An icon link with a
 * described image, a control wrapped in its own label, a `role="main"` instead of a `<main>`: all
 * real, all common, all silent here.</p>
 *
 * @author Alexandre Oliveira
 */
class ShHtmlAccessibilityTest {

    /** One page, one violation of each rule — the fixture the design asks for. */
    private static final String BROKEN = """
            <html>
            <head><title>Broken</title></head>
            <body>
              <h1>A title</h1>
              <h3>A section two levels down</h3>
              <a href="/somewhere"><span></span></a>
              <form>
                <input type="text" id="query" name="q">
                <input type="submit" value="Go">
              </form>
            </body>
            </html>
            """;

    /** The same page, correct — and correct in the ways that most look like the defects above. */
    private static final String SOUND = """
            <html lang="pt-br">
            <head><title>Sound</title></head>
            <body>
              <main>
                <h1>A title</h1>
                <h2>A section one level down</h2>
                <a href="/icon-link"><img src="/i.png" alt="Our newsletter"></a>
                <a href="/aria-link" aria-label="Subscribe"><i class="icon"></i></a>
                <form>
                  <label for="query">Search</label>
                  <input type="text" id="query" name="q">
                  <label>Also fine<input type="text" name="wrapped"></label>
                  <input type="hidden" name="csrf" value="x">
                  <input type="submit" value="Go">
                </form>
              </main>
            </body>
            </html>
            """;

    private static List<String> rules(String html) {
        return ShHtmlAccessibility.of(html).stream().map(ShHtmlAccessibility.Violation::rule).toList();
    }

    @Test
    void thePageWithOneOfEachReportsOneOfEach() {
        assertThat(rules(BROKEN))
                .as("SH1055: the family is the deliverable, so the fixture holds one violation of "
                        + "each rule and the report has to name all five. A subset means a rule "
                        + "that is declared and does not run.")
                .containsExactlyInAnyOrder("heading-skip", "lang-missing", "link-no-text",
                        "landmark-missing", "form-label-missing");
    }

    @Test
    void everyFindingSaysWhereAndWhatToDo() {
        // P6 is about errors and a finding is one: a report that says a page is wrong and not what
        // to do with it is a report somebody has to become an expert to act on.
        for (ShHtmlAccessibility.Violation violation : ShHtmlAccessibility.of(BROKEN)) {
            assertThat(violation.detail())
                    .as("SH1055: %s has no detail", violation.rule())
                    .isNotBlank();
            assertThat(violation.fix())
                    .as("SH1055: %s says what is wrong and not what to do about it", violation.rule())
                    .isNotBlank();
        }
        assertThat(ShHtmlAccessibility.of(BROKEN))
                .filteredOn(violation -> "heading-skip".equals(violation.rule()))
                .singleElement()
                .satisfies(violation -> assertThat(violation.detail())
                        .as("SH1055: the heading finding has to name the heading, or it sends a "
                                + "reader to a page with forty of them and no place to start")
                        .contains("A section two levels down")
                        .contains("h1")
                        .contains("h3"));
    }

    @Test
    void theCorrectFormsOfEachDefectAreSilent() {
        assertThat(rules(SOUND))
                .as("SH1055: a rule that fires on a page that is fine is a rule somebody turns "
                        + "off, and the four that were right go with it. Every shape here is one "
                        + "the naive version of these rules reports: a link named by the alt of "
                        + "the image inside it, a control wrapped in its own label, a hidden "
                        + "field, a submit button.")
                .isEmpty();
    }

    @Test
    void aMainRegionCountsWhicheverWayItIsSaid() {
        assertThat(rules("<html lang=\"en\"><body><div role=\"main\"><h1>T</h1></div></body></html>"))
                .as("SH1055: `role=\"main\"` and `<main>` are the same statement, and a rule that "
                        + "accepted only the element would fire on every page built before it")
                .doesNotContain("landmark-missing");
    }

    @Test
    void aFirstHeadingBelowH1IsTheSameDefectFromTheTop() {
        assertThat(rules("<html lang=\"en\"><main><h2>Only heading</h2></main></html>"))
                .as("SH1055: the walk starts at zero, so a page whose first heading is an h2 is "
                        + "skipping h1 — the same defect, met at the top of the page")
                .contains("heading-skip");
    }

    @Test
    void onlyTheFirstJumpIsReported() {
        // A page whose sections are all one level too deep is one repair. Forty copies of the same
        // sentence is a report nobody reads, which is how a rule stops being looked at.
        String deep = "<html lang=\"en\"><main><h1>T</h1><h3>a</h3><h3>b</h3><h5>c</h5></main></html>";

        assertThat(rules(deep)).filteredOn("heading-skip"::equals).hasSize(1);
    }

    @Test
    void anEmptyLangIsWorseThanNoneAndSaysSo() {
        // `lang=""` asserts the language is unknown, which is a stronger and worse claim than
        // leaving it unstated — SH383's own note makes the same point about the render model.
        List<ShHtmlAccessibility.Violation> found =
                ShHtmlAccessibility.of("<html lang=\"\"><main><h1>T</h1></main></html>");

        assertThat(found).singleElement()
                .satisfies(violation -> {
                    assertThat(violation.rule()).isEqualTo("lang-missing");
                    assertThat(violation.detail()).contains("unknown");
                });
    }

    @Test
    void aFragmentIsNotADocumentWithAMissingLanguage() {
        // Every rule but this one applies to a fragment. A partial with no <html> element is
        // `page-empty`'s subject if it is anybody's, and reporting a missing attribute on an
        // element that is not there would be a finding about the check.
        assertThat(rules("<main><h1>T</h1><p>Words.</p></main>"))
                .doesNotContain("lang-missing");
    }

    @Test
    void oneUnnamedLinkPerDestination() {
        // The same unnamed icon in a header, repeated down a page, is one repair.
        String repeated = "<html lang=\"en\"><main><h1>T</h1>"
                + "<a href=\"/x\"><i></i></a><a href=\"/x\"><i></i></a><a href=\"/y\"><i></i></a>"
                + "</main></html>";

        assertThat(rules(repeated)).filteredOn("link-no-text"::equals).hasSize(2);
    }

    @Test
    void anAnchorWithNoHrefIsATargetAndNotALink() {
        assertThat(rules("<html lang=\"en\"><main><h1>T</h1><a name=\"top\"></a></main></html>"))
                .as("SH1055: `<a name>` with no href is a target, and a rule that called it an "
                        + "unnamed link would fire on every in-page anchor ever written")
                .isEmpty();
    }

    @Test
    void nothingIsFoundInNothing() {
        // The report-zero guard, from the other side: an empty answer has to come from an empty
        // input rather than from a reader that stopped matching, and the fixture above is what
        // proves the second half.
        assertThat(ShHtmlAccessibility.of(null)).isEmpty();
        assertThat(ShHtmlAccessibility.of("   ")).isEmpty();
    }
}
