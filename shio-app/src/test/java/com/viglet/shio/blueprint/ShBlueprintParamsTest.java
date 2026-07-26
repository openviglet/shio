/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.blueprint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.blueprint.dto.ShBlueprintManifest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SH92 — blueprint parameters: validation and substitution.
 *
 * <p>The property under test is that a parameter mistake is <b>never silent</b>. A typo in
 * a parameter name, a value outside a declared set, a required parameter nobody supplied,
 * a placeholder the package forgot to declare: every one of them has to be a teaching
 * error rather than a gap in somebody's home page, because the failure mode this
 * validation exists to prevent is a site that applied "successfully" and says
 * {@code {{brand}}} in its hero.</p>
 *
 * @author Alexandre Oliveira
 */
class ShBlueprintParamsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static ShBlueprintManifest manifest(String paramsJson) {
        return new ShBlueprintManifest(1, "demo", "Demo", "A demo", "1.0.0",
                paramsJson == null ? null : MAPPER.readTree(paramsJson),
                List.of(), List.of(), List.of(), null);
    }

    private static JsonNode json(String raw) {
        return MAPPER.readTree(raw);
    }

    private static final String SCHEMA = """
            {"type":"object","required":["brand"],"properties":{
              "brand":{"type":"string","description":"The company name."},
              "tagline":{"type":"string","default":"Ships on Fridays"},
              "columns":{"type":"integer","default":3},
              "plan":{"type":"string","enum":["free","team","enterprise"],"default":"team"},
              "slug":{"type":"string","pattern":"[a-z-]+","default":"acme"}
            }}""";

    @Test
    @DisplayName("defaults fill in, the target site is always available, and both are echoed")
    void defaultsFillIn() {
        Map<String, String> values = ShBlueprintParams.resolve(manifest(SCHEMA),
                json("{\"brand\":\"Acme\"}"), "Acme Site");

        assertThat(values)
                .containsEntry("site", "Acme Site")
                .containsEntry("brand", "Acme")
                .containsEntry("tagline", "Ships on Fridays")
                .containsEntry("columns", "3")
                .containsEntry("plan", "team");
        // `site` first, then declaration order: the echo of two identical applies has to be
        // byte-identical, so the map's iteration order is part of the contract (P5).
        assertThat(values.keySet())
                .containsExactly("site", "brand", "tagline", "columns", "plan", "slug");
    }

    @Test
    @DisplayName("a required parameter with no default and no value is a teaching 400")
    void requiredParameterIsEnforced() {
        assertThatThrownBy(() -> ShBlueprintParams.resolve(manifest(SCHEMA), json("{}"), "Site"))
                .isInstanceOf(ShTeachingException.class)
                .satisfies(failure -> {
                    var problem = ((ShTeachingException) failure).problem().toProblemDetail();
                    assertThat(problem.getTitle()).contains("brand");
                    // The description the package wrote is the detail: it is the only place
                    // that knows why the value cannot be guessed.
                    assertThat(problem.getDetail()).isEqualTo("The company name.");
                    assertThat(problem.getProperties()).containsKey("fix");
                });
    }

    @Test
    @DisplayName("an unknown parameter is refused, not ignored, and the nearest name is offered")
    void unknownParameterIsRefused() {
        assertThatThrownBy(() -> ShBlueprintParams.resolve(manifest(SCHEMA),
                json("{\"brand\":\"Acme\",\"tagline2\":\"x\"}"), "Site"))
                .isInstanceOf(ShBlueprintParams.UnknownParamException.class)
                .satisfies(failure -> {
                    var problem = ((ShTeachingException) failure).problem().toProblemDetail();
                    assertThat(problem.getProperties()).containsEntry("didYouMean", "tagline");
                    assertThat(problem.getProperties().get("allowed").toString())
                            .contains("brand", "tagline");
                });
    }

    @Test
    @DisplayName("`site` is reserved — it travels in the request, not in the parameters")
    void siteIsReserved() {
        assertThatThrownBy(() -> ShBlueprintParams.resolve(manifest(SCHEMA),
                json("{\"brand\":\"Acme\",\"site\":\"Other\"}"), "Site"))
                .isInstanceOf(ShBlueprintParams.ReservedParamException.class);
    }

    @Test
    @DisplayName("declared type, enum and pattern are all enforced")
    void valuesAreValidated() {
        assertThatThrownBy(() -> ShBlueprintParams.resolve(manifest(SCHEMA),
                json("{\"brand\":\"Acme\",\"columns\":\"three\"}"), "Site"))
                .isInstanceOf(ShBlueprintParams.BadParamTypeException.class);

        assertThatThrownBy(() -> ShBlueprintParams.resolve(manifest(SCHEMA),
                json("{\"brand\":\"Acme\",\"plan\":\"startup\"}"), "Site"))
                .isInstanceOf(ShBlueprintParams.BadParamValueException.class)
                .satisfies(failure -> assertThat(((ShTeachingException) failure).problem()
                        .toProblemDetail().getProperties().get("allowed").toString())
                        .contains("free", "team", "enterprise"));

        assertThatThrownBy(() -> ShBlueprintParams.resolve(manifest(SCHEMA),
                json("{\"brand\":\"Acme\",\"plan\":\"tea\"}"), "Site"))
                .isInstanceOf(ShBlueprintParams.BadParamValueException.class)
                .satisfies(failure -> assertThat(((ShTeachingException) failure).problem()
                        .toProblemDetail().getProperties())
                        .containsEntry("didYouMean", "team"));

        assertThatThrownBy(() -> ShBlueprintParams.resolve(manifest(SCHEMA),
                json("{\"brand\":\"Acme\",\"slug\":\"Not A Slug\"}"), "Site"))
                .isInstanceOf(ShBlueprintParams.BadParamPatternException.class);
    }

    @Test
    @DisplayName("substitution rewrites string values, leaves keys and non-strings alone")
    void substitutionTouchesOnlyStringValues() {
        Map<String, String> values = Map.of("site", "Acme Site", "brand", "Acme", "columns", "3");
        JsonNode result = ShBlueprintParams.substitute(json("""
                {"heading":"Welcome to {{brand}}","columns":3,"live":true,
                 "{{brand}}":"key stays literal",
                 "features":[{"title":"{{brand}} is fast"}],"empty":null}"""),
                values, "demo");

        assertThat(result.get("heading").asString()).isEqualTo("Welcome to Acme");
        assertThat(result.get("columns").asInt()).isEqualTo(3);
        assertThat(result.get("live").asBoolean()).isTrue();
        // A field name is part of the content model, not of the copy: a parameter that
        // could rename a field would let parameters change the schema.
        assertThat(result.propertyNames()).contains("{{brand}}");
        assertThat(result.get("features").get(0).get("title").asString())
                .isEqualTo("Acme is fast");
        assertThat(result.get("empty").isNull()).isTrue();
    }

    @Test
    @DisplayName("a placeholder with no parameter behind it is the package's bug, reported as one")
    void undeclaredPlaceholderFails() {
        assertThatThrownBy(() -> ShBlueprintParams.text("Hello {{brnd}}",
                Map.of("site", "S", "brand", "Acme"), "demo"))
                .isInstanceOf(ShBlueprintParams.UnknownPlaceholderException.class)
                .satisfies(failure -> assertThat(((ShTeachingException) failure).problem()
                        .toProblemDetail().getProperties())
                        .containsEntry("didYouMean", "brand"));
    }

    @Test
    @DisplayName("a package with no params schema still resolves, with `site` alone")
    void noSchemaIsFine() {
        assertThat(ShBlueprintParams.resolve(manifest(null), null, "Only Site"))
                .containsExactly(Map.entry("site", "Only Site"));
    }
}
