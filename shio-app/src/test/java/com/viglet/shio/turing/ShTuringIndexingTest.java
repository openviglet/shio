/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.turing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.turing.commons.sn.field.TurSNFieldName;

import tools.jackson.databind.ObjectMapper;

/**
 * SH84 — what Shio sends to Turing, and when it sends nothing at all.
 *
 * <p>No Spring context: every property here is about the shape of one document or the reading of
 * one config value, and the seam that needs a context — the {@code AFTER_COMMIT} listener — is
 * exercised by the rest of the suite, which publishes hundreds of posts with indexing off and must
 * keep behaving exactly as it did before this existed.</p>
 *
 * <p>The invariant that earns most of these assertions is Turing's own grounding contract (T381,
 * Dumont's D03): <b>an absent field is a missing key</b>, never a key mapped to {@code null} or
 * {@code ""}. Nothing refuses a document that breaks it — not Shio, not Turing, not Solr — and the
 * symptom is a facet that counts documents having no such value. It is the exact shape of defect
 * that has to be asserted rather than reviewed.</p>
 *
 * @author Alexandre Oliveira
 */
class ShTuringIndexingTest {

    private final ShTuringDocumentMapper mapper = new ShTuringDocumentMapper(new ObjectMapper());

    // --- the grounding contract ---

    /**
     * A post with nothing filled in produces a document with <em>no empty values</em>, not a
     * document full of blanks.
     */
    @Test
    void aFieldWithNoValueIsAMissingKeyAndNeverAnEmptyOne() {
        ShPostUnified bare = new ShPostUnified();
        bare.setId("post-1");
        bare.setTitle("   ");
        bare.setSummary("");
        bare.setFurl(null);
        bare.setData(null);

        Map<String, Object> attributes = mapper.attributes(bare, "shio");

        assertThat(attributes)
                .containsOnlyKeys(TurSNFieldName.ID, TurSNFieldName.SOURCE_APPS);
        assertThat(attributes.values())
                .as("an empty value materialized in the index makes facet counts wrong")
                .allSatisfy(value -> assertThat(value.toString()).isNotBlank());
    }

    /**
     * `"null"` is a value the backend really stores, and {@code readTree} turns it into a node that
     * passes every non-null check — the same trap the console's {@code widgetSettings} guard exists
     * for, one layer down.
     */
    @Test
    void theLiteralStringNullIsNotIndexedAsText() {
        ShPostUnified post = new ShPostUnified();
        post.setId("post-2");
        post.setData("null");

        assertThat(mapper.attributes(post, "shio")).doesNotContainKey(TurSNFieldName.TEXT);
    }

    /** Malformed data is not indexed and does not throw — the render path already reports it. */
    @Test
    void dataThatIsNotJsonYieldsNoTextRatherThanAnError() {
        ShPostUnified post = new ShPostUnified();
        post.setId("post-3");
        post.setData("{not json");

        assertThat(mapper.attributes(post, "shio")).doesNotContainKey(TurSNFieldName.TEXT);
    }

    // --- the mapping ---

    @Test
    void aPublishedPostMapsOntoTuringsStandardFields() {
        Map<String, Object> attributes = mapper.attributes(fullPost(), "shio");

        assertThat(attributes)
                .containsEntry(TurSNFieldName.ID, "post-9")
                .containsEntry(TurSNFieldName.TITLE, "Hello")
                .containsEntry(TurSNFieldName.ABSTRACT, "A greeting")
                .containsEntry(TurSNFieldName.TYPE, "Article")
                .containsEntry(TurSNFieldName.SITE, "Cafeteria")
                .containsEntry(TurSNFieldName.AUTHOR, "curator")
                .containsEntry(TurSNFieldName.SOURCE_APPS, "shio")
                .containsEntry(TurSNFieldName.URL, "http://cafe.example.test/hello");
        assertThat(attributes.get(TurSNFieldName.PUBLICATION_DATE)).isNotNull();
    }

    /**
     * Markup is stripped, and every user-defined field is walked.
     *
     * <p>Walked rather than named: post-types are user-defined (SH16), so a post-type's fifth field
     * has to become searchable the day somebody adds it, with no mapping edit. Markup is stripped
     * because indexing a rich-text field verbatim makes a search for "div" match the whole site.</p>
     */
    @Test
    void everyUserDefinedStringIsIndexedAsTextWithMarkupStripped() {
        String text = mapper.attributes(fullPost(), "shio").get(TurSNFieldName.TEXT).toString();

        assertThat(text)
                .contains("Hello")
                .contains("The body of the page")
                .contains("a nested value")
                .doesNotContain("<p>")
                .doesNotContain("strong");
    }

    /** A number is a value, not prose: indexing it as text matches nothing anybody searches for. */
    @Test
    void numbersAreNotIndexedAsText() {
        ShPostUnified post = new ShPostUnified();
        post.setId("post-4");
        post.setData("{\"ordinal\": 42, \"headline\": \"Only me\"}");

        assertThat(mapper.attributes(post, "shio").get(TurSNFieldName.TEXT))
                .isEqualTo("Only me");
    }

    /** No site URL configured still yields a usable relative link rather than no link at all. */
    @Test
    void aSiteWithNoUrlStillProducesAPath() {
        ShPostUnified post = new ShPostUnified();
        post.setId("post-5");
        post.setFurl("news/today");

        assertThat(mapper.attributes(post, "shio")).containsEntry(TurSNFieldName.URL, "/news/today");
    }

    // --- optional, and parameterized ---

    /**
     * The P7 assertion: an instance that has never heard of Turing contacts nothing.
     *
     * <p>Pinned as a test because it is one word to change and the change is invisible — an
     * instance that suddenly starts pushing every publish to a URL nobody configured is exactly
     * the surprise the law forbids, and no other test would fail.</p>
     */
    @Test
    void indexingIsOffUntilSomebodyTurnsItOn() {
        ShTuringProperties defaults = new ShTuringProperties();

        assertThat(defaults.isEnabled()).isFalse();
        assertThat(service(defaults).active()).isFalse();
    }

    /** Enabled but pointed at nothing is still inactive — not an error, and not a half-attempt. */
    @Test
    void enabledWithoutAUrlIndexesNothing() {
        ShTuringProperties properties = new ShTuringProperties();
        properties.setEnabled(true);
        properties.setDefaultSite("cafeteria");

        assertThat(service(properties).active()).isFalse();
    }

    @Test
    void anExplicitSiteMappingWinsOverTheDefaultAndIgnoresCase() {
        ShTuringProperties properties = enabled();
        properties.setDefaultSite("catch-all");
        properties.setSites(Map.of("Cafeteria", "cafeteria-sn"));

        ShTuringIndexService service = service(properties);

        // Case-insensitive: the two names are typed by people into two different systems, and a
        // mismatch would present as indexing that silently does nothing.
        assertThat(service.snSiteFor("cafeteria")).contains("cafeteria-sn");
        assertThat(service.snSiteFor("CAFETERIA")).contains("cafeteria-sn");
        assertThat(service.snSiteFor("Bootstrap")).contains("catch-all");
    }

    /** No mapping and no default routes nowhere, rather than guessing an index name. */
    @Test
    void aSiteWithNoMappingAndNoDefaultIsNotIndexed() {
        ShTuringProperties properties = enabled();
        properties.setSites(Map.of("Other", "other-sn"));

        assertThat(service(properties).snSiteFor("Cafeteria")).isEmpty();
    }

    /** A post whose site routes nowhere is not indexed even when the instance is active. */
    @Test
    void anUnroutedPostIsNotIndexed() {
        ShTuringProperties properties = enabled();
        properties.setSites(Map.of("Elsewhere", "elsewhere-sn"));

        assertThat(service(properties).routed(fullPost())).isEmpty();
    }

    // --- fixtures ---

    private ShTuringIndexService service(ShTuringProperties properties) {
        return new ShTuringIndexService(properties, mapper);
    }

    private ShTuringProperties enabled() {
        ShTuringProperties properties = new ShTuringProperties();
        properties.setEnabled(true);
        properties.setUrl("http://turing.example.test");
        return properties;
    }

    private ShPostUnified fullPost() {
        ShSite site = new ShSite();
        site.setName("Cafeteria");
        site.setUrl("http://cafe.example.test/");

        ShPostType postType = new ShPostType();
        postType.setName("Article");

        ShPostUnified post = new ShPostUnified();
        post.setId("post-9");
        post.setTitle("Hello");
        post.setSummary("A greeting");
        post.setFurl("/hello");
        post.setShSite(site);
        post.setShPostType(postType);
        post.setOwner("curator");
        post.setLocale("en");
        post.setPublicationDate(Instant.parse("2026-01-01T00:00:00Z"));
        post.setModifiedDate(Instant.parse("2026-02-01T00:00:00Z"));
        post.setData("""
                {"headline": "Hello",
                 "body": "<p>The body of the <strong>page</strong></p>",
                 "nested": {"deep": "a nested value"}}""");
        return post;
    }
}
