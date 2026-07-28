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

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.render.ShRegionComposer.ShRenderedRegion;

/**
 * SH183 — layout + regions → one page, over the markup a real 2018 package contains.
 *
 * <p>The layout fragment below is not invented: it is the shape of
 * {@code openviglet/bootstrap-site}'s {@code PT-PAGE-LAYOUT} {@code HTML} field, which
 * imports into a current instance byte-identically. That is the evidence the model needs
 * no schema work — a renderer and three system post-types, not a migration.</p>
 *
 * @author Alexandre Oliveira
 */
class ShRegionComposerTest {

    private static final String LAYOUT = """
            <html><body>
              <div sh-region="BOOTSTRAP_NAV_REGION"> </div>
              <div sh-region="BOOTSTRAP_CONTENT_LIST_REGION" style="margin:20px"></div>
            </body></html>""";

    private final ShRegionComposer composer = new ShRegionComposer();

    @Test
    @DisplayName("the slots are found in document order, each once")
    void slotsAreFoundInOrder() {
        assertThat(composer.slotsOf(LAYOUT))
                .containsExactly("BOOTSTRAP_NAV_REGION", "BOOTSTRAP_CONTENT_LIST_REGION");
    }

    @Test
    @DisplayName("a layout slotting one region twice asks for it once")
    void repeatedSlotsCollapse() {
        String twice = "<body><nav sh-region=\"NAV\"></nav><footer sh-region=\"NAV\"></footer></body>";

        assertThat(composer.slotsOf(twice)).containsExactly("NAV");
        // ...and both are filled, because a nav in the header and the footer is the
        // reason a layout does this.
        String page = composer.compose(twice, Map.of("NAV", region("NAV", "p1", "<a>Home</a>")));
        assertThat(page.split("<a>Home</a>", -1)).hasSize(3);
    }

    @Test
    @DisplayName("a filled slot keeps sh-region and gains the editor's annotation")
    void filledSlotsAreAnnotated() {
        String page = composer.compose(LAYOUT, regions());

        assertThat(page)
                .as("""
                        sh-region survives the render on purpose: the rendered DOM is the only \
                        place the Universal Editor can learn which node is which region, which \
                        is SH185's whole problem. Stripping it would be tidier and would take \
                        the annotation contract with it.""")
                .contains("sh-region=\"BOOTSTRAP_NAV_REGION\"")
                .contains("data-shio-region=\"BOOTSTRAP_NAV_REGION\"")
                .contains("data-shio-region-post=\"nav-post-id\"");
        assertThat(page).contains("<a href=\"/\">Home</a>", "<h2>Latest</h2>");
    }

    @Test
    @DisplayName("the layout's own attributes are left alone")
    void layoutAttributesSurvive() {
        assertThat(composer.compose(LAYOUT, regions()))
                .as("a region fills the hole; it does not own the element around it")
                .contains("style=\"margin:20px\"");
    }

    @Test
    @DisplayName("a region that does not exist is named, not silently empty")
    void aMissingRegionIsVisible() {
        String page = composer.compose(LAYOUT,
                Map.of("BOOTSTRAP_NAV_REGION", region("BOOTSTRAP_NAV_REGION", "nav-post-id", "<a>Home</a>")));

        assertThat(page)
                .as("""
                        a layout naming a region nobody created, and a region that rendered to \
                        nothing, are different mistakes — the same rule SH169 applied to the \
                        digest, where a missing section became [missing section /url] so that \
                        two failures could not hash alike.""")
                .contains("data-shio-region-missing=\"true\"")
                .contains("no Region post for this slot")
                .contains("data-shio-region=\"BOOTSTRAP_CONTENT_LIST_REGION\"");
    }

    @Test
    @DisplayName("composing is deterministic — the same inputs give the same bytes")
    void compositionIsStable() {
        // P5, and it is what lets SH101's digest and SH102's snapshot mean anything: a
        // renderer that re-indents or reorders reports a change nobody made.
        assertThat(composer.compose(LAYOUT, regions()))
                .isEqualTo(composer.compose(LAYOUT, regions()));
    }

    // --- SH248: the browser half of the Block D split ---

    @Test
    @DisplayName("a region's JavaScript reaches the browser, annotated like its markup")
    void regionScriptsAreEmitted() {
        Map<String, ShRenderedRegion> regions = new LinkedHashMap<>(regions());
        regions.put("BOOTSTRAP_NAV_REGION", new ShRenderedRegion("BOOTSTRAP_NAV_REGION",
                "nav-post-id", "<a>Home</a>", "console.log('nav')"));

        String page = composer.compose(LAYOUT, regions);

        assertThat(page)
                .as("""
                        SH183 gave Region.JAVASCRIPT a code editor and served it nowhere, so the \
                        field accepted working code and discarded its effect — worse than not \
                        having the field, because it teaches the author they wrote it wrong.""")
                .contains("<script data-shio-region=\"BOOTSTRAP_NAV_REGION\"")
                .contains("console.log('nav')")
                .contains("data-shio-region-post=\"nav-post-id\"");
    }

    @Test
    @DisplayName("a script body is not escaped — `&&` must survive as `&&`")
    void scriptBodiesAreNotHtmlEscaped() {
        String page = composer.compose("<body><div sh-region=\"NAV\"></div></body>",
                Map.of("NAV", new ShRenderedRegion("NAV", "p1", "<a>x</a>",
                        "if (a && b) { document.write('<b>hi</b>') }")));

        assertThat(page)
                .as("a script is a DataNode, not text — jsoup would turn && into &amp;&amp; "
                        + "and the script into a syntax error")
                .contains("if (a && b)")
                .doesNotContain("&amp;&amp;");
    }

    @Test
    @DisplayName("a region slotted twice runs its script once")
    void aRepeatedRegionScriptIsEmittedOnce() {
        String twice = "<body><nav sh-region=\"NAV\"></nav><footer sh-region=\"NAV\"></footer></body>";

        String page = composer.compose(twice, Map.of("NAV",
                new ShRenderedRegion("NAV", "p1", "<a>Home</a>", "init()")));

        assertThat(page.split("init\\(\\)", -1))
                .as("""
                        a nav in the header and the footer is why a layout does this, and running \
                        its initialisation twice is a bug that only shows up on the layouts that \
                        do — so the emit is keyed by region name, not by slot.""")
                .hasSize(2);
        assertThat(page.split("<a>Home</a>", -1))
                .as("the markup, by contrast, is genuinely wanted in both places")
                .hasSize(3);
    }

    @Test
    @DisplayName("a region with no script adds no empty tag")
    void noScriptMeansNoTag() {
        assertThat(composer.compose(LAYOUT, regions())).doesNotContain("<script");
    }

    @Test
    @DisplayName("a missing region contributes no script, because there is no post to run")
    void aMissingRegionHasNoScript() {
        String page = composer.compose(LAYOUT,
                Map.of("BOOTSTRAP_NAV_REGION",
                        new ShRenderedRegion("BOOTSTRAP_NAV_REGION", "nav-post-id", "<a>Home</a>",
                                "console.log('nav')")));

        assertThat(page.split("<script", -1))
                .as("only the slot that was actually filled contributes one")
                .hasSize(2);
    }

    @Test
    @DisplayName("a region name cannot inject markup — it is only ever an attribute value")
    void aMissingRegionNameCannotEscapeIntoTheDocument() {
        // A region name is content, so it is attacker-controlled in exactly the way a
        // post's title is. The first implementation wrote it into the HTML comment that
        // marks an unfilled slot, where `-->` closes the comment and everything after it
        // becomes markup. There is nothing to escape now: the marker is a constant and the
        // name goes only where jsoup escapes it.
        String layout = "<body><div sh-region=\"A--><script>alert(1)</script><!--B\"></div></body>";

        assertThat(composer.compose(layout, Map.of()))
                .doesNotContain("<script>alert(1)</script>")
                .contains("&lt;script&gt;");
    }

    private Map<String, ShRenderedRegion> regions() {
        Map<String, ShRenderedRegion> regions = new LinkedHashMap<>();
        regions.put("BOOTSTRAP_NAV_REGION",
                region("BOOTSTRAP_NAV_REGION", "nav-post-id", "<a href=\"/\">Home</a>"));
        regions.put("BOOTSTRAP_CONTENT_LIST_REGION",
                region("BOOTSTRAP_CONTENT_LIST_REGION", "list-post-id", "<h2>Latest</h2>"));
        return regions;
    }

    private ShRenderedRegion region(String name, String postId, String html) {
        return new ShRenderedRegion(name, postId, html);
    }
}
