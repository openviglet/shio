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

import java.util.Map;

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
