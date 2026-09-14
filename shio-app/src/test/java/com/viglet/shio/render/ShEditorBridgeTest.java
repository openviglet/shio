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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.render.ShRegionComposer.ShRenderedRegion;

/**
 * SH185 — the Universal Editor's counterpart, and the annotations it reads.
 *
 * <p>The console has posted {@code sh:*} messages into an iframe since SH10 and there was no
 * {@code shio-editor.js} anywhere in this repository to receive them — not one
 * {@code postMessage} in {@code create-shio-app}, {@code shio-react-sdk},
 * {@code shio-sections} or {@code shio-client}. These assert the two halves that make the
 * conversation possible: a page that says which node is which field of which post, and a
 * bridge that is injected only when it was asked for.</p>
 *
 * @author Alexandre Oliveira
 */
class ShEditorBridgeTest {

    private final ShRegionComposer composer = new ShRegionComposer();
    private final ShEditorBridge bridge = new ShEditorBridge();

    // --- the annotations -------------------------------------------------------------

    @Test
    @DisplayName("the page's own post is on <body>, so a plain sh-field needs no sh-post")
    void thePageOwnsItsFieldsByDefault() {
        String html = composer.compose(
                "<body><h1 sh-field=\"TITLE\">Hi</h1></body>", Map.of(), "page-1", "Article");

        assertThat(html)
                .contains("data-shio-post=\"page-1\"")
                .contains("data-shio-post-type=\"Article\"")
                .contains("data-shio-prop=\"TITLE\"");
    }

    @Test
    @DisplayName("a listing row declares its own owner, and the nearest one wins")
    void aRowCanOwnItsFields() {
        // This is why ownership is the template's job. A region renders against the *page's*
        // model, so `{{TEXT}}` inside it is the page's — but inside a {{#query}} block
        // `{{title}}` is the listed post's, and nothing in the rendered HTML records which
        // interpolation came from where.
        String html = composer.compose("""
                <body>
                  <h1 sh-field="TITLE">Page</h1>
                  <li sh-post="post-9"><span sh-field="title">Row</span></li>
                </body>""", Map.of(), "page-1", "Article");

        assertThat(html).contains("data-shio-post=\"post-9\"");
        // Ownership is resolved in the browser by walking up, so what the markup has to get
        // right is that both declarations survive and the inner one is nearer.
        int rowOwner = html.indexOf("data-shio-post=\"post-9\"");
        int rowField = html.indexOf("data-shio-prop=\"title\"");
        assertThat(rowOwner).isLessThan(rowField);
    }

    @Test
    @DisplayName("the authoring attribute survives beside the rendered one")
    void bothFormsSurvive() {
        String html = composer.compose(
                "<body><h1 sh-field=\"TITLE\">Hi</h1></body>", Map.of(), "page-1", "Article");

        assertThat(html)
                .as("""
                        sh-field is a contract the *content* declares, so SH193 can extract it \
                        into the reference graph and SH192 can lint it; data-shio-prop is what \
                        the browser and the editor consume. One attribute doing both jobs could \
                        not change on either side without breaking the other.""")
                .contains("sh-field=\"TITLE\"")
                .contains("data-shio-prop=\"TITLE\"");
    }

    @Test
    @DisplayName("a field inside a region is annotated too — the pass runs after slots fill")
    void regionsCarryFieldsThrough() {
        String html = composer.compose(
                "<body><div sh-region=\"NAV\"></div></body>",
                Map.of("NAV", new ShRenderedRegion("NAV", "region-1",
                        "<a sh-field=\"LINK\">Home</a>")),
                "page-1", "Article");

        assertThat(html).contains("data-shio-prop=\"LINK\"");
    }

    @Test
    @DisplayName("an empty sh-field is ignored rather than annotated as a nameless field")
    void anEmptyFieldNameIsSkipped() {
        String html = composer.compose(
                "<body><h1 sh-field=\"  \">Hi</h1></body>", Map.of(), "page-1", "Article");

        assertThat(html).doesNotContain("data-shio-prop");
    }

    @Test
    @DisplayName("SH503: one element may declare a second field, and the attribute it is drawn into")
    void anElementCanDeclareAFieldItDrawsIntoAnAttribute() {
        String html = composer.compose(
                "<body><a sh-field=\"ctaLabel\" sh-field-attr=\"ctaHref:href\" href=\"/signup\">"
                        + "Start</a></body>",
                Map.of(), "page-1", "Page");

        assertThat(html)
                .as("""
                        sh-field declares what the element's CONTENT is, so one element declared \
                        one field — and a link is two, its text and its target. The destination was \
                        captured, stored and rendered into the href with no declaration, so a \
                        curator could rename the button and not repoint it.""")
                .contains("data-shio-prop=\"ctaLabel\"")
                .contains("data-shio-prop-attr=\"ctaHref:href\"")
                .contains("sh-field-attr=\"ctaHref:href\"");
    }

    @Test
    @DisplayName("SH503: the pairs are copied verbatim — the renderer still parses no prop")
    void attributeFieldPairsAreNotParsed() {
        String html = composer.compose(
                "<body><li sh-field-attr=\"features.0.icon:data-shio-icon rows.0.href:href\" "
                        + "data-shio-icon=\"bolt\"></li></body>",
                Map.of(), "page-1", "Page");

        assertThat(html)
                .as("""
                        an indexed path (SH475) and a list of more than one pair both pass through \
                        untouched: what splits them is the browser bridge, which is the side that \
                        has to read the value off the element anyway.""")
                .contains("data-shio-prop-attr=\"features.0.icon:data-shio-icon rows.0.href:href\"");
        assertThat(html)
                .as("and an element may declare only attribute fields — a row's icon is a data- "
                        + "attribute the theme reads and has no text node at all")
                .doesNotContain("data-shio-prop=");
    }

    @Test
    @DisplayName("SH503: an empty sh-field-attr is ignored, like an empty sh-field")
    void anEmptyAttributeFieldDeclarationIsSkipped() {
        String html = composer.compose(
                "<body><a sh-field-attr=\"  \" href=\"/x\">Start</a></body>", Map.of(), "page-1",
                "Page");

        assertThat(html).doesNotContain("data-shio-prop-attr");
    }

    @Test
    @DisplayName("a page rendered without an id is not annotated — no owner is invented")
    void noPageIdMeansNoOwner() {
        assertThat(composer.compose("<body><h1 sh-field=\"TITLE\">Hi</h1></body>", Map.of()))
                .doesNotContain("data-shio-post=");
    }

    // --- the bridge ------------------------------------------------------------------

    @Test
    @DisplayName("injection adds the script, pinned to the origin that asked")
    void injectionPinsTheOrigin() {
        String html = bridge.inject("<html><body><p>Hi</p></body></html>",
                "https://console.example.com");

        assertThat(html)
                .contains("src=\"" + ShEditorBridge.SCRIPT_PATH + "\"")
                .as("""
                        the origin is passed in rather than guessed in the browser: \
                        document.referrer is empty on some navigations and \
                        window.parent.location throws cross-origin, so the server that \
                        rendered the page is the only party that knows who asked""")
                .contains("data-shio-editor-origin=\"https://console.example.com\"");
    }

    @Test
    @DisplayName("the script refuses to do anything without a parent origin")
    void theScriptIsInertWithoutAnOrigin() {
        assertThat(bridge.script())
                .as("a page that starts making things contenteditable outside the editor is "
                        + "a page that corrupts content")
                .contains("if (!PARENT_ORIGIN || window.parent === window)");
    }

    @Test
    @DisplayName("the script checks the sender's origin on the way in")
    void theScriptChecksIncomingOrigin() {
        assertThat(bridge.script()).contains("event.origin !== PARENT_ORIGIN");
    }

    /**
     * SH304 — one implementation of the protocol, and neither side posts to {@code "*"}.
     *
     * <p>Both halves of this were stated as load-bearing and neither was asserted, which is how the
     * repository ended up serving <b>two</b> bridges: this one (specified, 182 lines,
     * {@code data-shio-*}) and an unspecified 550-line script at {@code /universal-editor/}
     * ({@code data-sh-*}, written by hand by whatever front end included it). That is P10's own
     * warning — two contracts for one feature — and the unspecified one was the one exposed to
     * external front ends. It also broke the single security rule the protocol states, in both
     * directions: it posted to {@code "*"} and it never looked at {@code event.origin}, so any page
     * that framed a front end including it could ask for the content map and get it, and patch the
     * DOM. Deleted, with external editing declared unsupported until the SDK emits the annotations
     * (SH350) — a capability regression stated out loud beats one nobody can see.</p>
     */
    @Test
    @DisplayName("SH304: exactly one bridge is served, and no served script posts to \"*\"")
    void oneBridgeAndNoWildcardOrigin() throws IOException {
        Path resources = Path.of("src", "main", "resources");
        List<Path> bridges;
        try (Stream<Path> files = Files.walk(resources)) {
            bridges = files.filter(file -> file.toString().endsWith(".js"))
                    // The built console bundle is Vite's output, not a script this repository writes.
                    .filter(file -> !file.toString().replace('\\', '/').contains("/public/"))
                    // A bridge is defined by what it does, not by where it sits: the legacy AngularJS
                    // UI ships four scripts and a Handlebars build that talk to nobody's parent.
                    .filter(ShEditorBridgeTest::postsToAParent)
                    .sorted()
                    .toList();
        }

        assertThat(bridges.stream().map(file -> file.getFileName().toString()).toList())
                .as("""
                        A second implementation of the SH185 protocol must be a decision, not a file \
                        somebody adds. If this list grows, either the new script is part of the \
                        protocol (specify it in docs/specs/SH185-universal-editor-protocol.md and \
                        add it here) or it is not (do not serve it).""")
                .containsExactly("shio-editor.js");

        for (Path script : bridges) {
            assertThat(Files.readString(script, StandardCharsets.UTF_8))
                    .as("""
                            %s posts to "*", which hands every message - including page content - to \
                            whatever origin framed the page. renderer.md has claimed "neither side of \
                            the bridge uses \\"*\\"" since SH185 and nothing asserted it.""", script)
                    .doesNotContain("postMessage(\"*\"")
                    .doesNotContain(", \"*\")");
        }
    }

    /** True for a script that talks to whatever framed it — the defining act of a bridge. */
    private static boolean postsToAParent(Path script) {
        try {
            return Files.readString(script, StandardCharsets.UTF_8).contains("postMessage");
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @Test
    @DisplayName("SH304: the console's half does not post to \"*\" either")
    void theConsoleHalfPinsItsOriginToo() throws IOException {
        // SH961 moved the console's pages into trees by domain; the Universal Editor is editor/.
        Path editor = Path.of("..", "shio-react", "src", "app", "editor", "visual-editor.page.tsx");

        assertThat(Files.readString(editor, StandardCharsets.UTF_8))
                .as("""
                        The rule is about the bridge, not about one file of it, and the console is the \
                        half that posts *into* an iframe whose origin it chose - so a "*" here would \
                        hand the message to whatever the site's url happens to resolve to today.""")
                .doesNotContain(", \"*\")")
                .contains("frameOrigin");
    }

    /**
     * SH350 — the published external bridge obeys the same rule, asserted from the suite that runs.
     *
     * <p>{@code @viglet/shio-editor-cors} is the home for external editing (SH304 established that,
     * and it is why deleting the server's copy cost no capability). It posted to {@code "*"} and read
     * {@code event.data.type} without ever looking at {@code event.origin}, so any page framing a
     * front end that included it could ask for the content map and rewrite the DOM.</p>
     *
     * <p>The guard lives here rather than in the JS workspace <em>on purpose</em>: this is the suite
     * the build gates on, and the sibling test above already reads the console's TSX for the same
     * rule — one instrument covering all three implementations beats three that run at different
     * times. What is still missing is a <b>behavioural</b> test of the bridge in a DOM, which needs a
     * harness this workspace does not have; that decision is SH306's and is not pretended here.</p>
     */
    @Test
    @DisplayName("SH350: the published external bridge pins its origin in both directions")
    void thePublishedBridgePinsItsOrigin() throws IOException {
        String source = Files.readString(
                Path.of("..", "shio-js-sdk", "src", "shio-editor-cors.ts"), StandardCharsets.UTF_8);

        assertThat(source)
                .as("outbound: the message carries page content, so \"*\" publishes it")
                .doesNotContain(", \"*\")");
        assertThat(source)
                .as("inbound: sh:request-content answers with the whole content map and "
                        + "sh:content-patch rewrites the DOM, so the sender has to be the shell")
                .contains("event.origin !== state.parentOrigin");
        assertThat(source)
                .as("""
                        and it has to be *told* which origin: a framed page cannot work it out \
                        (document.referrer is empty on some navigations, window.parent.location \
                        throws cross-origin). Inert without it, which is the rule SH185 set for the \
                        injected bridge - stated by the meta name it reads.""")
                .contains("urn:shio:system:editor-origin")
                .contains("if (!state.parentOrigin)");
    }

    @Test
    @DisplayName("the script speaks exactly the four messages the console implements")
    void theProtocolMatchesTheConsole() {
        // The console switches on these three inbound types and sends the one outbound type.
        // If either side grows a fifth, this is where the two stop agreeing silently.
        assertThat(bridge.script())
                .contains("sh:ready")
                .contains("sh:element-selected")
                .contains("sh:content-changed")
                .contains("sh:mode-change");
    }
}
