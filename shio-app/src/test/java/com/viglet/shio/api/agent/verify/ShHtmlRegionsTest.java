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
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SH687 — counting a page is not describing it.
 *
 * <h2>The two defects nothing could state</h2>
 *
 * <p>One glance at the {@code www.viglet.org} replica's home page showed both: the captured footer
 * renders <b>above</b> the first section, and the accepted sections are presented by the Shio theme
 * while the chrome keeps the captured one. Neither was expressible in anything Shio returned —
 * {@code landmarks} answered {@code footer×1 header×1 nav×1 section×9}, {@code digest}'s outline is
 * the heading tree and excludes a footer because footers carry no headings, and every other number
 * on the appearance surface is a count or a set. {@code audit}, which alone holds geometry, returned
 * <b>0 errors</b>. It took a screenshot from a human.</p>
 *
 * <p>This class carries the first defect's answer: order, and who owns each region's markup.</p>
 *
 * @author Alexandre Oliveira
 */
class ShHtmlRegionsTest {

    /** The replica's shape, reduced: chrome kept verbatim, sections authored, footer misplaced. */
    private static final String REPLICA = """
            <html><body>
              <header class="site-head"><nav><a href="/">Home</a></nav></header>
              <footer class="site-foot">© Viglet</footer>
              <main>
                <section class="shio-section" sh-post="a1" data-shio-section="SectionHero">
                  <h2>Ship faster</h2>
                </section>
                <section class="shio-section" sh-post="b2" data-shio-section="SectionFaq">
                  <h2>Questions</h2>
                </section>
              </main>
            </body></html>
            """;

    @Test
    @DisplayName("SH687: the footer being second is a fact the histogram could not carry")
    void regionsAreInDocumentOrder() {
        List<ShHtmlRegions.Region> regions = ShHtmlRegions.of(REPLICA, Set.of());

        assertThat(regions)
                .as("""
                        `footer×1 header×1 nav×1 section×2` is true of this page and of one where \
                        the footer is last, and only one of those two is the defect. Order is the \
                        whole field (SH687).""")
                .extracting(ShHtmlRegions.Region::tag, ShHtmlRegions.Region::order)
                .containsExactly(
                        tuple("header", 1),
                        tuple("nav", 2),
                        tuple("footer", 3),
                        tuple("main", 4),
                        tuple("section", 5),
                        tuple("section", 6));

        assertThat(regions)
                .filteredOn(region -> "footer".equals(region.tag()))
                .allSatisfy(footer -> assertThat(footer.order())
                        .as("the footer renders before the sections, which is the finding")
                        .isLessThan(regions.stream()
                                .filter(r -> "section".equals(r.tag()))
                                .mapToInt(ShHtmlRegions.Region::order).min().orElseThrow()));
    }

    @Test
    @DisplayName("SH687: a region says whether an editor owns its markup or it was kept verbatim")
    void provenanceSeparatesSectionsFromChrome() {
        List<ShHtmlRegions.Region> regions = ShHtmlRegions.of(REPLICA, Set.of());

        assertThat(regions)
                .extracting(ShHtmlRegions.Region::tag, ShHtmlRegions.Region::markup)
                .containsExactly(
                        tuple("header", ShHtmlRegions.VERBATIM),
                        tuple("nav", ShHtmlRegions.VERBATIM),
                        tuple("footer", ShHtmlRegions.VERBATIM),
                        tuple("main", ShHtmlRegions.VERBATIM),
                        tuple("section", ShHtmlRegions.SECTIONS),
                        tuple("section", ShHtmlRegions.SECTIONS));
    }

    @Test
    @DisplayName("SH687: containment, not position — a landmark nested in a section is the section's")
    void aLandmarkInsideASectionIsAuthored() {
        // The reason this parses rather than scans. "Is this landmark inside an authored section" is
        // a question about ancestry, and a tag counter answers it by proximity — which attributes a
        // region to whichever marker it happened to follow.
        String html = """
                <body>
                  <section sh-post="a1"><table><tr><td>rows</td></tr></table></section>
                  <table><tr><td>chrome</td></tr></table>
                </body>
                """;

        assertThat(ShHtmlRegions.of(html, Set.of()))
                .extracting(ShHtmlRegions.Region::tag, ShHtmlRegions.Region::markup)
                .containsExactly(
                        tuple("section", ShHtmlRegions.SECTIONS),
                        tuple("table", ShHtmlRegions.SECTIONS),
                        tuple("table", ShHtmlRegions.VERBATIM));
    }

    @Test
    @DisplayName("SH687: an undefined class is attributed to the region that draws it")
    void unstyledIsCountedPerRegion() {
        // The document-wide list said 42 and could not say WHERE, so the one warning that speaks
        // about styling pointed at the whole page. Handed the same set the appearance computed, so
        // a region's count and the document's list cannot disagree about what is undefined.
        String html = """
                <body>
                  <header class="site-head missing-a">x</header>
                  <section sh-post="a1" class="shio-section missing-b">
                    <p class="missing-c">y</p>
                  </section>
                </body>
                """;

        assertThat(ShHtmlRegions.of(html, Set.of("missing-a", "missing-b", "missing-c")))
                .extracting(ShHtmlRegions.Region::tag, ShHtmlRegions.Region::unstyled)
                .containsExactly(tuple("header", 1), tuple("section", 2));
    }

    /**
     * SH694 — the second positional defect: which theme is in force, per region.
     *
     * <p>One glance at the replica showed two things and SH687 could state only the first. This is
     * the other: the accepted sections are presented by the Shio theme while the chrome keeps the
     * captured one, two visual languages in one document. {@code unstyledClasses} says <em>whether</em>
     * a class is defined; nothing said <b>by which sheet</b>, because {@code definedIn} concatenated
     * every stylesheet into one set.</p>
     */
    @Test
    @DisplayName("SH694: a region names the stylesheets that style what it draws")
    void styledByNamesTheSheet() {
        String html = """
                <html><head>
                  <style>.site-head{color:#111}.site-foot{color:#222}</style>
                  <style>.shio-section{padding:2rem}.shio-heading{font-size:2rem}</style>
                </head><body>
                  <header class="site-head">chrome</header>
                  <section sh-post="a1" class="shio-section"><h2 class="shio-heading">x</h2></section>
                  <footer class="site-foot">chrome</footer>
                </body></html>
                """;

        var regions = ShHtmlRegions.of(html, List.of(), ShHtmlAppearance.definedBySheet(html));

        assertThat(regions)
                .as("""
                        the captured chrome draws from one sheet and the authored sections from the \
                        other, and no earlier field could say so: a set of classes answers whether \
                        they are defined, never by which sheet (SH694).""")
                .extracting(ShHtmlRegions.Region::tag, ShHtmlRegions.Region::styledBy)
                .containsExactly(
                        tuple("header", List.of("style[1]")),
                        tuple("section", List.of("style[2]")),
                        tuple("footer", List.of("style[1]")));
    }

    /**
     * SH829 — the column SH694 added to end a lookup was ending in one.
     *
     * <p>{@code style[1]} and {@code style[2]} are ordinal positions, and nothing the digest returned
     * mapped them to anything; both sheets are ordinary {@code Theme} posts with titles, so the
     * reader had to make a second call to learn which ordinal was the captured theme. The
     * {@code <style>} now carries that title, and this asserts the whole rule in one page: a named
     * sheet prints its name, and an inline sheet a layout wrote by hand has no post and keeps the
     * index — the distinction between a sheet somebody can edit and one nobody can.</p>
     */
    @Test
    @DisplayName("SH829: a region names its stylesheet's theme, and an unnamed sheet keeps its index")
    void styledByNamesTheTheme() {
        String html = """
                <html><head>
                  <style data-shio-theme="VigR3 (Shio)">.shio-section{padding:2rem}</style>
                  <style data-shio-theme="VigR3 &amp; co">.site-head{color:#111}</style>
                  <style>.hand-written{color:#333}</style>
                </head><body>
                  <section sh-post="a1" class="shio-section">x</section>
                  <header class="site-head">chrome</header>
                  <footer class="hand-written">chrome</footer>
                </body></html>
                """;

        var regions = ShHtmlRegions.of(html, List.of(), ShHtmlAppearance.definedBySheet(html));

        assertThat(regions)
                .as("""
                        the title travels as an attribute, so it survives being content — `&amp;` \
                        decodes back to the post's own name rather than reaching the reader as \
                        markup (SH829).""")
                .extracting(ShHtmlRegions.Region::tag, ShHtmlRegions.Region::styledBy)
                .containsExactly(
                        tuple("section", List.of("VigR3 (Shio)")),
                        tuple("header", List.of("VigR3 & co")),
                        tuple("footer", List.of("style[3]")));
    }

    /**
     * SH829 — a name two sheets share is not a name, and half-naming them is worse than the index.
     *
     * <p>Printing {@code VigR3} twice would give the reader two keys mapping to one post and one
     * mapping to neither, which is the second lookup this column removes, restored and disguised.</p>
     */
    @Test
    @DisplayName("SH829: two sheets naming one theme both fall back to their positions")
    void ambiguousThemeNamesFallBackToTheIndex() {
        String html = """
                <html><head>
                  <style data-shio-theme="VigR3">.a{color:#111}</style>
                  <style data-shio-theme="VigR3">.b{color:#222}</style>
                </head><body>
                  <header class="a">x</header><footer class="b">y</footer>
                </body></html>
                """;

        assertThat(ShHtmlAppearance.definedBySheet(html))
                .containsOnlyKeys("style[1]", "style[2]");
    }

    @Test
    @DisplayName("SH694: with no sheet map the list is empty, which is an absence not a claim")
    void styledByIsEmptyWithoutSheets() {
        // The one-argument form still exists and still answers order and provenance. An empty
        // `styledBy` there must not read as "nothing styles this region" — it is "nobody asked".
        assertThat(ShHtmlRegions.of(REPLICA, Set.of()))
                .allSatisfy(region -> assertThat(region.styledBy()).isEmpty());
    }

    @Test
    @DisplayName("with nothing undefined, and with no page at all, the answer is not a null")
    void emptyInputsAnswerEmptily() {
        assertThat(ShHtmlRegions.of(REPLICA, Set.of()))
                .as("an empty undefined-set yields zeroes rather than nulls — none is an answer")
                .allSatisfy(region -> assertThat(region.unstyled()).isZero());
        assertThat(ShHtmlRegions.of(null, Set.of())).isEmpty();
        assertThat(ShHtmlRegions.of("   ", Set.of())).isEmpty();
    }
}
