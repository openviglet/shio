/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;

import com.viglet.shio.api.post.type.ShManagedPostTypeException;
import com.viglet.shio.api.post.type.ShSystemPostTypeException;
import com.viglet.shio.api.post.type.ShUnknownWidgetException;

/**
 * SH80 — teaching errors. Network-free unit tests: {@link ShProblem} rendering,
 * {@link ShDidYouMean} matching, the retrofitted domain exceptions, and the
 * framework-exception enrichment in {@link ShApiExceptionHandler}.
 *
 * <p>The property under test throughout is that a 4xx <em>teaches</em>: the body
 * must carry enough to succeed on the next attempt without another request.</p>
 *
 * @author Alexandre Oliveira
 */
class ShTeachingErrorTest {

    /** The installed catalog, sorted — as {@code widgetCatalogNames()} produces it. */
    private static final List<String> WIDGETS = List.of(
            "Ace Editor - HTML", "Ace Editor - Javascript", "Check Box", "Combo Box",
            "Content Select", "Date", "File", "HTML Editor", "Hidden", "Multi Select",
            "Relator", "Tab", "Text", "Text Area");

    private final ShApiExceptionHandler handler = new ShApiExceptionHandler();

    @Test
    void problemOmitsEmptyExtensionsAndSetsTypeUri() {
        ProblemDetail problem = ShProblem.badRequest("bare", "Bare problem").toProblemDetail();

        assertThat(problem.getType()).hasToString(ShProblem.TYPE_BASE + "bare");
        assertThat(problem.getTitle()).isEqualTo("Bare problem");
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        // Nothing to teach → nothing on the wire: the payload never pays for silence.
        assertThat(problem.getProperties()).isNull();
    }

    @Test
    void problemCarriesTheFourTeachingFields() {
        ProblemDetail problem = ShProblem.badRequest("invalid-state", "Invalid state 'LIVE'")
                .withDetail("'LIVE' is not a post state.")
                .withFix("Use DRAFT while authoring and publish explicitly.")
                .withAllowed(List.of("DRAFT", "PUBLISHED"))
                .withDidYouMeanFrom("live")
                .withExample("?state=DRAFT")
                .toProblemDetail();

        assertThat(problem.getProperties())
                .containsEntry("fix", "Use DRAFT while authoring and publish explicitly.")
                .containsEntry("allowed", List.of("DRAFT", "PUBLISHED"))
                .containsEntry("example", "?state=DRAFT");
        assertThat(problem.getDetail()).isEqualTo("'LIVE' is not a post state.");
    }

    @Test
    void didYouMeanIgnoresCaseAndSeparators() {
        assertThat(ShDidYouMean.nearest("html editor", WIDGETS)).contains("HTML Editor");
        assertThat(ShDidYouMean.nearest("HTMLEditor", WIDGETS)).contains("HTML Editor");
        assertThat(ShDidYouMean.nearest("text_area", WIDGETS)).contains("Text Area");
        assertThat(ShDidYouMean.nearest("combobox", WIDGETS)).contains("Combo Box");
    }

    @Test
    void didYouMeanStaysSilentWhenNothingIsClose() {
        // A wrong guess is worse than no guess: it sends the caller down a dead end.
        assertThat(ShDidYouMean.nearest("Paragraph", WIDGETS)).isEmpty();
        assertThat(ShDidYouMean.nearest("Text", List.of())).isEmpty();
        assertThat(ShDidYouMean.nearest(null, WIDGETS)).isEmpty();
        assertThat(ShDidYouMean.nearest("  ", WIDGETS)).isEmpty();
    }

    @Test
    void didYouMeanIsDeterministicOnATie() {
        // Equal distance → first candidate in the given order, both directions.
        List<String> ordered = List.of("Text", "Date");
        assertThat(ShDidYouMean.nearest("Tex", ordered)).contains("Text");
        assertThat(ShDidYouMean.nearest("Tex", List.of("Date", "Text"))).contains("Text");
    }

    @Test
    void unknownWidgetTeachesTheCatalogAndTheNearestMatch() {
        ShUnknownWidgetException exception = new ShUnknownWidgetException("RichText", WIDGETS);
        ProblemDetail problem = exception.toProblemDetail();

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getType()).hasToString(ShProblem.TYPE_BASE + "unknown-widget");
        assertThat(problem.getProperties())
                .containsEntry("allowed", WIDGETS)
                // Vocabulary, not spelling: "RichText" is 4 edits from "Text" and
                // further from everything else, so only the alias map can answer it.
                .containsEntry("didYouMean", "HTML Editor")
                .hasEntrySatisfying("example", example -> assertThat(example)
                        .isEqualTo(Map.of("name", "body", "label", "Body", "widget", "HTML Editor")));
        assertThat(problem.getProperties().get("fix")).asString().contains("catalog widgets");
    }

    @Test
    void unknownWidgetTranslatesOtherPlatformsVocabularies() {
        // The guesses an agent actually makes, by the CMS it learned from.
        assertThat(didYouMean("reference")).isEqualTo("Content Select");   // Sanity/Strapi
        assertThat(didYouMean("boolean")).isEqualTo("Check Box");          // JSON-schema/Strapi
        assertThat(didYouMean("enumeration")).isEqualTo("Combo Box");      // Strapi
        assertThat(didYouMean("media")).isEqualTo("File");                 // Strapi/Payload
        assertThat(didYouMean("array")).isEqualTo("Relator");              // Sanity
        assertThat(didYouMean("Symbol")).isEqualTo("Text");                // Contentful
        assertThat(didYouMean("markdown")).isEqualTo("HTML Editor");
    }

    @Test
    void unknownWidgetFallsBackToSpellingWhenTheNameIsNotForeign() {
        // A genuine typo is still a typo — the distance path must stay live.
        assertThat(didYouMean("Txt Area")).isEqualTo("Text Area");
        assertThat(didYouMean("Combo Bx")).isEqualTo("Combo Box");
    }

    @Test
    void unknownWidgetNeverSuggestsAWidgetThatIsNotInstalled() {
        // An alias must not promise a widget this instance does not have.
        List<String> stripped = List.of("Text", "Date");
        ProblemDetail problem = new ShUnknownWidgetException("richtext", stripped).toProblemDetail();

        assertThat(problem.getProperties()).containsEntry("allowed", stripped);
        Object suggestion = problem.getProperties().get("didYouMean");
        assertThat(suggestion == null || stripped.contains(String.valueOf(suggestion))).isTrue();
    }

    private String didYouMean(String sent) {
        Map<String, Object> properties = new ShUnknownWidgetException(sent, WIDGETS)
                .toProblemDetail().getProperties();
        return properties == null ? null : String.valueOf(properties.get("didYouMean"));
    }

    @Test
    void unknownWidgetDegradesWithoutACatalog() {
        // The single-arg form is still legal; it must not claim an empty allow-list.
        ProblemDetail problem = new ShUnknownWidgetException("RichText").toProblemDetail();

        assertThat(problem.getProperties()).doesNotContainKey("allowed").doesNotContainKey("didYouMean");
        assertThat(problem.getProperties().get("fix")).asString().contains("GET /api/v2/widget");
    }

    @Test
    void systemAndManagedPostTypesExplainTheirBoundary() {
        ProblemDetail system = new ShSystemPostTypeException("File").toProblemDetail();
        assertThat(system.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(system.getProperties().get("fix")).asString().contains("Create your own post-type");

        ProblemDetail managed = new ShManagedPostTypeException("Article").toProblemDetail();
        assertThat(managed.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(managed.getProperties().get("fix")).asString().contains("shio push");
    }

    @Test
    void teachingExceptionMessageMirrorsTheDetail() {
        // Logs and the wire must agree, or a support conversation costs a repro.
        ShUnknownWidgetException exception = new ShUnknownWidgetException("RichText", WIDGETS);
        assertThat(exception).hasMessage(exception.toProblemDetail().getDetail());
    }

    @Test
    void handlerRendersADomainProblemWithItsOwnStatus() {
        ResponseEntity<ProblemDetail> response =
                handler.teaching(new ShSystemPostTypeException("File"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getHeaders().getContentType()).hasToString("application/problem+json");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getType()).hasToString(ShProblem.TYPE_BASE + "system-post-type");
    }

    @Test
    void handlerTeachesAMissingParameter() {
        ResponseEntity<ProblemDetail> response = handler.missingParameter(
                new MissingServletRequestParameterException("siteId", "String"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties())
                .containsEntry("example", "?siteId=<value>")
                .hasEntrySatisfying("fix", fix -> assertThat(fix).asString().contains("siteId=<value>"));
    }

    @Test
    void handlerTurnsAWrongVerbIntoTheAllowedSet() {
        ResponseEntity<ProblemDetail> response = handler.methodNotSupported(
                new HttpRequestMethodNotSupportedException("DELETE", List.of("GET", "PUT")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties())
                .containsEntry("allowed", List.of("GET", "PUT"))
                .hasEntrySatisfying("fix", fix -> assertThat(fix).asString().contains("GET, PUT"));
    }
}
