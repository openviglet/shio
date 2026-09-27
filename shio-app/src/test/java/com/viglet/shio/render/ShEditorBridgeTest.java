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
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Set;
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

    // --- SH1038: a section records its position beside its identity ---

    @Test
    @DisplayName("SH1038: each section carries its index, and its container the count")
    void sectionsCarryTheirPosition() {
        String html = composer.compose("""
                <body><main>
                  <section data-shio-section="SectionHero" sh-post="a1"></section>
                  <section data-shio-section="SectionFeatures" sh-post="b2"></section>
                  <section data-shio-section="SectionFaq" sh-post="c3"></section>
                </main></body>""", Map.of(), "page-1", "Page");

        assertThat(html)
                .as("""
                        The handle existed, the server's region walk read it, and the browser bridge                         did not — so a section was something the editor could change the headline of                         and never the section. Position is what a MOVE needs and neither the element                         nor the container can state alone, so it is derived from document order:                         `getRelation` renders the Page's references in the order the field names                         them, which makes document order that order.""")
                .contains("data-shio-section=\"SectionHero\" sh-post=\"a1\" "
                        + "data-shio-post=\"a1\" data-shio-section-index=\"0\"")
                .contains("data-shio-section-index=\"1\"")
                .contains("data-shio-section-index=\"2\"")
                // On the container, because the position AFTER the last section is a real
                // destination and there is no element there to read a length off.
                .contains("data-shio-sections=\"3\"");
    }

    @Test
    @DisplayName("SH1038: two composed lists are two arrays, counted separately")
    void eachContainerCountsItsOwn() {
        String html = composer.compose("""
                <body>
                  <main><section data-shio-section="A"></section>
                        <section data-shio-section="B"></section></main>
                  <aside><section data-shio-section="C"></section></aside>
                </body>""", Map.of(), "page-1", "Page");

        // One running count would make the aside's only section read as position three of an array
        // that does not exist, and a move against it would rewrite the wrong field.
        assertThat(html)
                .contains("<aside data-shio-sections=\"1\"")
                .contains("data-shio-section=\"C\" data-shio-section-index=\"0\"");
    }

    @Test
    @DisplayName("SH1038: a page with no sections is untouched")
    void aPageWithNoSectionsIsUnchanged() {
        String layout = "<body><main><h1 sh-field=\"TITLE\">Hi</h1></main></body>";

        // The pass writes nothing where there is nothing to describe, so an ordinary layout renders
        // byte-identically to what it did before this shipped.
        assertThat(composer.compose(layout, Map.of(), "page-1", "Page"))
                .doesNotContain("data-shio-sections")
                .doesNotContain("data-shio-section-index");
    }

    // --- SH1039: what fits where, declared by the template ---

    @Test
    @DisplayName("SH1039: a container declares what it accepts, and an empty one is still a container")
    void anEmptyContainerThatDeclaresIsStillOne() {
        String html = composer.compose(
                "<body><main sh-accepts=\"SectionHero SectionCta\"></main></body>",
                Map.of(), "page-1", "Page");

        assertThat(html)
                .as("""
                        An editor that can select and move a section still cannot add one, because                         nothing says which types fit where. The EMPTY container is the case                         insertion exists for — a page with no sections yet is where nothing can be                         copied from what is already there — and the section pass can only find a                         container by the sections in it, so this one would be invisible.""")
                .contains("data-shio-accepts=\"SectionHero SectionCta\"")
                .contains("data-shio-sections=\"0\"");
    }

    @Test
    @DisplayName("SH1039: a container that holds sections keeps its own count")
    void aDeclaringContainerKeepsItsCount() {
        String html = composer.compose("""
                <body><main sh-accepts="SectionHero">
                  <section data-shio-section="SectionHero" sh-post="a1"></section>
                  <section data-shio-section="SectionCta" sh-post="b2"></section>
                </main></body>""", Map.of(), "page-1", "Page");

        // The declaration never overwrites the derived count: a container holding two sections and
        // accepting one type is an ordinary page whose layout was narrowed after it was composed.
        assertThat(html).contains("data-shio-sections=\"2\"").contains("data-shio-accepts=\"SectionHero\"");
    }

    @Test
    @DisplayName("SH1039: a blank declaration is skipped, like an empty sh-field")
    void aBlankAcceptsDeclarationIsSkipped() {
        String html = composer.compose("<body><main sh-accepts=\"   \"></main></body>",
                Map.of(), "page-1", "Page");

        // An author mid-edit, not a statement that nothing fits. Annotating it would turn a typo
        // into a fact the console acts on, and the fact it would state is the most restrictive one.
        assertThat(html).doesNotContain("data-shio-accepts").doesNotContain("data-shio-sections");
    }

    // --- SH1037: a field says what KIND it is ---

    @Test
    @DisplayName("SH1037: a page field carries the widget its model declares")
    void theDeclaredWidgetTravels() {
        String html = composer.compose(
                "<body><h1 sh-field=\"TITLE\">Hi</h1><div sh-field=\"BODY\">b</div>"
                        + "<time sh-field=\"WHEN\">t</time></body>",
                Map.of(), "page-1", "Article", java.util.Set.of(), null,
                Map.of("TITLE", "Text", "BODY", "HTML Editor", "WHEN", "Date"));

        assertThat(html)
                .as("""
                        Edit mode used to be granted by the presence of `data-shio-prop` alone, so                         every annotated element became contenteditable HTML — and a plain Text field                         absorbed the markup of a paste, stored it, and then showed the tags as                         visible text, because the template escapes a text field. The defect is the                         editor offering rich editing over a field that cannot hold it, so the kind                         has to reach the browser.""")
                .contains("data-shio-prop-type=\"Text\"")
                .contains("data-shio-prop-type=\"HTML Editor\"")
                .contains("data-shio-prop-type=\"Date\"");
    }

    @Test
    @DisplayName("SH1037: a listing row's field carries no kind, because none is knowable")
    void aRowsWidgetIsNotGuessed() {
        String html = composer.compose(
                "<body><ul><li sh-post=\"row-9\"><span sh-field=\"TITLE\">r</span></li></ul></body>",
                Map.of(), "page-1", "Article", java.util.Set.of(), null,
                Map.of("TITLE", "Text"));

        assertThat(html)
                .as("""
                        A row is a post of some OTHER type — `sh-post="{{id}}"` is all an author can                         write, because the row's type is the query's — so the page type's map does                         not describe it. Absent rather than guessed: the browser ACTS on this, so a                         wrong kind would make a Date contenteditable or refuse a Text field, and                         both are worse than the compatible default.""")
                .contains("data-shio-post=\"row-9\"")
                .doesNotContain("data-shio-prop-type");
    }

    @Test
    @DisplayName("SH1175: a section's field carries the widget of the section's own type")
    void aSectionsFieldIsDeclaredFromItsOwnType() {
        java.util.List<java.util.Set<String>> asked = new java.util.ArrayList<>();
        String html = composer.compose(
                "<body><h1 sh-field=\"TITLE\">Page</h1>"
                        + "<section sh-post=\"hero-1\"><h1 sh-field=\"heading\">Acme</h1>"
                        + "<div sh-field=\"body\">b</div></section>"
                        + "<section sh-post=\"cta-2\"><a sh-field=\"ctaLabel\">Go</a></section></body>",
                Map.of(), "page-1", "Page", java.util.Set.of(), null, Map.of("TITLE", "Text"),
                ids -> {
                    asked.add(ids);
                    return Map.of("hero-1", Map.of("heading", "Text", "body", "HTML Editor"));
                });

        // Undeclared, the bridge edited a section's Text heading as rich HTML, and a typed trailing
        // space was stored as `&nbsp;` and escaped again on every save — measured on marketing-site.
        assertThat(html)
                .contains("sh-field=\"heading\" data-shio-prop=\"heading\" data-shio-prop-type=\"Text\"")
                .contains("data-shio-prop=\"body\" data-shio-prop-type=\"HTML Editor\"")
                .contains("data-shio-prop=\"TITLE\" data-shio-prop-type=\"Text\"");
        assertThat(html).as("a post the resolver did not type stays undeclared, as a row always did")
                .doesNotContain("data-shio-prop=\"ctaLabel\" data-shio-prop-type");
        assertThat(asked).as("one call for the whole page, with every non-page owner, sorted")
                .containsExactly(new java.util.TreeSet<>(java.util.Set.of("cta-2", "hero-1")));
    }

    @Test
    @DisplayName("SH1175: a page that owns every field asks about no other post")
    void aPageAloneAsksNothing() {
        java.util.List<java.util.Set<String>> asked = new java.util.ArrayList<>();
        composer.compose("<body><h1 sh-field=\"TITLE\">Hi</h1></body>", Map.of(), "page-1", "Page",
                java.util.Set.of(), null, Map.of("TITLE", "Text"), ids -> {
                    asked.add(ids);
                    return Map.of();
                });
        assertThat(asked).isEmpty();
    }

    @Test
    @DisplayName("SH1037: a field the type does not declare carries no kind")
    void anUndeclaredFieldCarriesNoKind() {
        String html = composer.compose("<body><h1 sh-field=\"NOPE\">Hi</h1></body>",
                Map.of(), "page-1", "Article", java.util.Set.of(), null, Map.of("TITLE", "Text"));

        // SH475's collection leaves (`COLL.N.LEAF`) fall through the same way, which is why the
        // lookup is a plain map read and not a parse.
        assertThat(html).contains("data-shio-prop=\"NOPE\"").doesNotContain("data-shio-prop-type");
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

    /**
     * The two halves speak the same vocabulary — <b>derived, not listed</b> (SH1036).
     *
     * <h2>What this used to assert, and why it was not that</h2>
     *
     * <p>It was four {@code contains} calls over the script, under a name promising "exactly the
     * four messages the console implements". Neither half of that was true: it never read the
     * console at all, and {@code contains} cannot say <em>exactly</em> — a fifth type on either side
     * passed it. Its own comment said "if either side grows a fifth, this is where the two stop
     * agreeing silently", which is precisely what would have happened, and SH1036 grew one.</p>
     *
     * <p>So the sets are now scraped from both sources and compared. The script's are the strings it
     * passes to {@code send(...)} and switches on; the console's are the {@code case "sh:…"} labels
     * it handles and the types it posts. A type either side speaks alone is named in the failure,
     * which is the whole point — a message nobody listens to and a listener for a message nobody
     * sends are the same defect wearing two faces, and both are silent.</p>
     */
    @Test
    @DisplayName("SH1036: the script and the console speak the same message vocabulary")
    void theProtocolMatchesTheConsole() throws IOException {
        Set<String> inScript = messageTypesIn(bridge.script());
        Set<String> inConsole = messageTypesIn(Files.readString(
                Path.of("..", "shio-react", "src", "app", "editor", "visual-editor.page.tsx"),
                StandardCharsets.UTF_8));

        assertThat(inScript)
                .as("""
                        The bridge is a PROTOCOL, so a type one half speaks and the other does not is                         a message into the void or a listener for nothing — and both are silent. This                         used to be four `contains` calls over the script alone, under a name claiming                         it compared the two; SH1036 added a fifth type and the old form passed                         without reading the console. Script: %s  Console: %s. Update both halves,                         the table in docs/specs/SH185-universal-editor-protocol.md, and this test                         will follow on its own.""".formatted(inScript, inConsole))
                .isEqualTo(inConsole);

        // The floor, so the derivation cannot pass by finding nothing on both sides — a regex that
        // stopped matching would otherwise make two empty sets equal and the guard vacuous.
        assertThat(inScript)
                .as("the vocabulary is not empty, or the scrape above has stopped working")
                .contains("sh:ready", "sh:element-selected", "sh:content-changed", "sh:mode-change",
                        "sh:navigating", "sh:field-set");
    }

    /**
     * SH1038 — and the <b>document</b> is the third speaker, held to the same set.
     *
     * <h2>Why the pair above was not enough</h2>
     *
     * <p>{@code theProtocolMatchesTheConsole} makes the two implementations agree with each other,
     * which is what stops a message going into the void. It says nothing about whether the protocol
     * that both halves now speak is the one anybody wrote down — and the spec is the artefact an
     * external producer implements from ({@code @viglet/shio-editor-cors} is the second producer
     * SH304 established, and it is written against this document, not against this repository). So
     * two consenting halves could grow a verb the only other implementer could not know existed.</p>
     *
     * <p><b>The contract is the table</b>, which is {@link ShRenderedDomContractTest}'s rule one
     * layer along, and for its reason: prose is free to discuss a type that no longer exists, and
     * most of what makes a document worth reading is exactly that. SH1038 added two types and this
     * is the check that the tables gained both rows.</p>
     */
    @Test
    @DisplayName("SH1038: the spec's message tables name every type the bridge speaks")
    void theSpecNamesEveryMessage() throws IOException {
        Set<String> spelled = new java.util.TreeSet<>();
        Matcher matcher = Pattern.compile("`(sh:[a-z-]+)`").matcher(
                Files.readString(Path.of("..", "docs", "specs",
                        "SH185-universal-editor-protocol.md"), StandardCharsets.UTF_8).lines()
                        .filter(line -> line.startsWith("|"))
                        .reduce("", (all, line) -> all + "\n" + line));
        while (matcher.find()) {
            spelled.add(matcher.group(1));
        }

        assertThat(spelled)
                .as("""
                        A type both halves speak and the document does not is a protocol term the \
                        one implementation written from the document - @viglet/shio-editor-cors, \
                        the external producer SH304 established - cannot know exists. Add a row to \
                        the page-to-console or console-to-page table in \
                        docs/specs/SH185-universal-editor-protocol.md. Prose about a type is not \
                        enough: the contract is the table.""")
                .isEqualTo(messageTypesIn(bridge.script()));
    }

    /** Every {@code sh:*} message type a source mentions, however it mentions it. */
    private static Set<String> messageTypesIn(String source) {
        Set<String> types = new java.util.TreeSet<>();
        Matcher matcher = Pattern.compile("\"(sh:[a-z-]+)\"").matcher(source);
        while (matcher.find()) {
            types.add(matcher.group(1));
        }
        return types;
    }
}
