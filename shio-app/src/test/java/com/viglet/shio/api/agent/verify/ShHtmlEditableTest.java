/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.api.agent.verify;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SH809 — which fields a curator could edit, read off the page an agent can already render.
 *
 * <h2>The gap</h2>
 *
 * <p>{@code universalEditor} was the one entry in {@code ShAgentP1ConformanceTest}'s inventory that
 * named a route and did not have it. It was advertised {@code true} and classified
 * {@code CONSOLE_ONLY}, because SH185's bridge is a browser protocol the console alone speaks —
 * <em>honest to a human and not to an agent</em>. So an agent could read a post, render it, and diff
 * draft against live (SH619), and still not answer the question a curator's job turns on.</p>
 *
 * <h2>Why the page and not the model</h2>
 *
 * <p>Deriving editability from post-types and layout bindings would answer what the <b>model</b>
 * permits, which is a different question, and the two would agree only by accident (SH583). What a
 * curator can click is what the page says it is — and the page already says it, in the attributes
 * the browser bridge reads. Reading the same ones is P10.</p>
 */
class ShHtmlEditableTest {

    /** A page as the renderer composes it: a region, a post inside it, and two annotated fields. */
    private static final String PAGE = """
            <html><body>
              <main data-shio-region="main" data-shio-region-post="post:Blog/">
                <article data-shio-post="post:Blog/blog/hello">
                  <h1 data-shio-prop="title">Hello</h1>
                  <div data-shio-prop="html"><p>Body</p></div>
                </article>
              </main>
              <aside data-shio-region="sidebar" data-shio-region-post="post:Blog/nav"></aside>
            </body></html>
            """;

    @Test
    @DisplayName("SH809: every annotated field is reported with the post that owns it")
    void everyAnnotatedFieldIsReported() {
        assertThat(ShHtmlEditable.of(PAGE))
                .as("SH809: an agent asking which fields a curator could change gets them from the "
                        + "page's own annotations — the ones SH185 put there for the browser")
                .containsExactly(
                        new ShHtmlEditable.Field("post:Blog/blog/hello", "title", "main"),
                        new ShHtmlEditable.Field("post:Blog/blog/hello", "html", "main"));
    }

    @Test
    @DisplayName("SH809: a field with no post of its own belongs to its region's")
    void aFieldFallsBackToItsRegionsPost() {
        String page = """
                <main data-shio-region="main" data-shio-region-post="post:Blog/">
                  <h1 data-shio-prop="title">Hello</h1>
                </main>
                """;

        assertThat(ShHtmlEditable.of(page))
                .as("SH809: the nearest annotated post wins and a region's is the fallback, which is "
                        + "exactly how the browser bridge resolves it — a second rule here would be "
                        + "two answers to one question (SH583)")
                .containsExactly(new ShHtmlEditable.Field("post:Blog/", "title", "main"));
    }

    @Test
    @DisplayName("SH809: regions are reported whether or not anything in them is editable")
    void regionsAreReportedEvenWhenEmpty() {
        assertThat(ShHtmlEditable.regions(PAGE))
                .as("SH809: a region with nothing editable in it is a fact a curator needs — it is "
                        + "where content could go, and an agent that only saw filled regions would "
                        + "report a finished page as having no room")
                .containsExactly("main", "sidebar");
    }

    @Test
    @DisplayName("SH809: a page with no annotations is empty rather than an error")
    void anUnannotatedPageIsEmpty() {
        // A front end that emits none of the protocol is the normal state of somebody else's site,
        // and SH185's whole argument is that the annotations are what makes a page editable at all.
        assertThat(ShHtmlEditable.of("<html><body><h1>Hello</h1></body></html>")).isEmpty();
        assertThat(ShHtmlEditable.of(null)).isEmpty();
        assertThat(ShHtmlEditable.of("  ")).isEmpty();
        assertThat(ShHtmlEditable.regions(null)).isEmpty();
    }

    @Test
    @DisplayName("SH809: the same field annotated twice is reported once")
    void duplicatesCollapse() {
        // A layout that renders one field in two places — a title in a header and in a card — is one
        // editable field, not two. An agent counting them would report work that does not exist.
        String page = """
                <div data-shio-post="post:Blog/x">
                  <h1 data-shio-prop="title">A</h1>
                  <span data-shio-prop="title">A</span>
                </div>
                """;

        assertThat(ShHtmlEditable.of(page))
                .containsExactly(new ShHtmlEditable.Field("post:Blog/x", "title", null));
    }

    // --- SH1039: what may be inserted, and where ---

    /** A page composing two ordered lists: one declaring what fits, one holding sections. */
    private static final String COMPOSED = """
            <html><body>
              <main data-shio-region="main" data-shio-region-post="post:Site/" data-shio-sections="2">
                <section data-shio-section="SectionHero" data-shio-post="post:Site/sections/a"></section>
                <section data-shio-section="SectionCta" data-shio-post="post:Site/sections/b"></section>
              </main>
              <aside data-shio-accepts="SectionRichText  SectionRichText SectionFaq"
                     data-shio-sections="0"></aside>
            </body></html>
            """;

    @Test
    @DisplayName("SH1039: each container reports where it is, what it holds and what fits")
    void containersAreReported() {
        assertThat(ShHtmlEditable.containers(COMPOSED))
                .as("""
                        SH1038 made a section selectable, movable and removable, and an editor that \
                        can do all three still cannot ADD one: nothing said which types fit where. \
                        The index is the handle both halves can compute — `getRelation` takes the \
                        field's VALUE, so the field's name is gone by the time markup exists.""")
                .containsExactly(
                        new ShHtmlEditable.Container(0, "main", 2,
                                java.util.List.of("SectionHero", "SectionCta"), java.util.List.of()),
                        // De-duplicated in the order written: a declaration naming a type twice is
                        // one offer, and two would be two identical rows in a catalogue.
                        new ShHtmlEditable.Container(1, null, 0, java.util.List.of(),
                                java.util.List.of("SectionRichText", "SectionFaq")));
    }

    @Test
    @DisplayName("SH1039: an empty container is found by its own declaration alone")
    void anEmptyDeclaredContainerIsFound() {
        // The case insertion exists for. A page with no sections yet has nothing to copy from what
        // is already there, and a reader that could only see a container by the sections in it
        // would report the emptiest page as having nowhere to put anything.
        assertThat(ShHtmlEditable.containers(
                "<body><main data-shio-accepts=\"SectionHero\"></main></body>"))
                .containsExactly(new ShHtmlEditable.Container(0, null, 0, java.util.List.of(),
                        java.util.List.of("SectionHero")));
    }

    @Test
    @DisplayName("SH1039: a container that declares nothing accepts nothing, not everything")
    void anUndeclaredContainerAcceptsNothing() {
        // Never read as "anything": that would need a server-side list of which post-types are
        // sections, and ShVocabularyPostTypes is explicit that a third copy of the vocabulary is
        // the drift SH245 bans. The catalogue carries the declaration to add instead.
        assertThat(ShHtmlEditable.containers(COMPOSED).getFirst().accepts()).isEmpty();
        assertThat(ShHtmlEditable.containers(null)).isEmpty();
        assertThat(ShHtmlEditable.containers(PAGE)).isEmpty();
    }
}
