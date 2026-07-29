/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * SH331 — the vocabulary rules, unit-tested, because the boundary is the whole design.
 *
 * <p>No Spring context: {@link ShMcpVocabulary} takes a schema and a node and returns a node, and the
 * one thing that must never be true of it — that it touches a {@code data} key — is cheapest to assert
 * against a schema written here rather than against whichever tool happens to have one today.</p>
 *
 * @author Alexandre Oliveira
 */
class ShMcpVocabularyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** A tool-shaped schema: a top-level name, an array of ops, and an opaque `data` inside. */
    private static Map<String, Object> schema() {
        return ShMcpTool.schema(Map.of(
                "site", ShMcpTool.string("Site name."),
                "ops", Map.of("type", "array", "description", "The operations.",
                        "items", Map.of("type", "object", "properties", Map.of(
                                "op", ShMcpTool.string("What to do."),
                                "address", ShMcpTool.string("The subject."),
                                "type", ShMcpTool.string("Post-type name."),
                                // Declared with no properties of its own — which is what makes it
                                // opaque, and the guard is structural rather than a name check.
                                "data", ShMcpTool.object("The post's fields."))))),
                List.of());
    }

    private ObjectNode json(String text) {
        return (ObjectNode) objectMapper.readTree(text);
    }

    @Test
    @DisplayName("SH331: a synonym is rewritten and reported, not silently applied")
    void aSynonymIsRewrittenAndReported() {
        ShMcpVocabulary.Normalized normalized = ShMcpVocabulary.normalize(
                "shio_test", json("{\"siteName\":\"Blog\"}"), schema());

        assertThat(normalized.arguments().get("site").asString()).isEqualTo("Blog");
        assertThat(normalized.arguments().has("siteName")).isFalse();
        assertThat(normalized.notes())
                .as("""
                        reported, because a synonym accepted in silence teaches nothing — the caller \
                        keeps sending it, which is the state SH340 measured as six silent drops in \
                        twelve calls.""")
                .singleElement().asString().contains("`siteName`").contains("`site`");
    }

    @Test
    @DisplayName("SH331: an op's argument name is rewritten too — that is where the mistake was")
    void anOpsArgumentIsRewritten() {
        // shio_write declares only `ops` at the top level, so `postType` for `type` was written
        // inside the op object. A validator that stopped at the top level would miss the very call
        // the task was filed from.
        ShMcpVocabulary.Normalized normalized = ShMcpVocabulary.normalize("shio_test",
                json("{\"ops\":[{\"op\":\"post.upsert\",\"address\":\"post:Blog/x\","
                        + "\"postType\":\"Article\"}]}"),
                schema());

        ObjectNode op = (ObjectNode) normalized.arguments().get("ops").get(0);
        assertThat(op.get("type").asString()).isEqualTo("Article");
        assertThat(op.has("postType")).isFalse();
        assertThat(normalized.notes()).singleElement().asString().contains("`type`");
    }

    @Test
    @DisplayName("SH331: a `data` key is never renamed and never refused")
    void aDataKeyIsUntouched() {
        // The load-bearing boundary. SH168's whole point is that an upsert declares whole state, so
        // a near-miss on a field name silently *replaces* the field it meant — accepting a synonym
        // there would import exactly the failure this repository spent a task removing. The three
        // conditions for a safe synonym (closed set, server-owned, unambiguous) hold for an op's
        // argument names and hold for none of a post's fields.
        ShMcpVocabulary.Normalized normalized = ShMcpVocabulary.normalize("shio_test",
                json("{\"ops\":[{\"op\":\"post.upsert\",\"data\":{\"postType\":\"x\","
                        + "\"scope\":\"y\",\"anythingAtAll\":\"z\"}}]}"),
                schema());

        ObjectNode data = (ObjectNode) normalized.arguments().get("ops").get(0).get("data");
        assertThat(data.propertyNames())
                .as("""
                        every key survives verbatim: `postType` and `scope` are aliases at the op \
                        level and must not be at the content level, and `anythingAtAll` must not be \
                        refused — a post-type declares its own fields and SH168 is where an \
                        undeclared one is answered.""")
                .containsExactlyInAnyOrder("postType", "scope", "anythingAtAll");
        assertThat(normalized.notes()).isEmpty();
    }

    @Test
    @DisplayName("SH331: an argument nothing explains is refused, with the allowed set")
    void anUnknownArgumentIsRefused() {
        assertThatThrownBy(() -> ShMcpVocabulary.normalize(
                "shio_test", json("{\"stie\":\"Blog\"}"), schema()))
                .isInstanceOf(ShMcpVocabulary.UnknownArgumentException.class)
                .satisfies(failure -> {
                    var problem = ((ShMcpVocabulary.UnknownArgumentException) failure)
                            .problem().toProblemDetail();
                    assertThat(problem.getStatus()).isEqualTo(400);
                    assertThat(problem.getProperties().get("allowed").toString())
                            .contains("site").contains("ops");
                    assertThat(problem.getProperties().get("didYouMean"))
                            .as("a typo one letter away gets the name, from the shared "
                                    + "ShDidYouMean rather than a second distance function")
                            .isEqualTo("site");
                });
    }

    @Test
    @DisplayName("SH331: an alias never displaces a value the caller also sent canonically")
    void anAliasNeverDisplacesTheCanonicalValue() {
        // Both spellings present is not a synonym to resolve — it is two values, and picking one
        // would be guessing which the caller meant. The canonical wins and the alias is refused,
        // so the caller is told rather than silently losing one of them.
        assertThatThrownBy(() -> ShMcpVocabulary.normalize(
                "shio_test", json("{\"site\":\"Blog\",\"siteName\":\"Docs\"}"), schema()))
                .isInstanceOf(ShMcpVocabulary.UnknownArgumentException.class);
    }

    @Test
    @DisplayName("SH331: every alias points at a name some Shio surface actually uses")
    void everyAliasIsARealSpelling() {
        // Guards the table against becoming a guess at what a model might type. Each entry exists
        // because *this product* gave the same concept another name somewhere, which is why an agent
        // that learned one surface used it on another.
        assertThat(ShMcpVocabulary.aliases())
                .containsEntry("postType", "type")
                .containsEntry("addresses", "address")
                .containsEntry("scope", "site")
                .containsEntry("cursor", "since")
                .containsEntry("check", "checks");
        assertThat(ShMcpVocabulary.aliases().keySet())
                .as("an alias that is also a canonical name would make the table ambiguous")
                .doesNotContainAnyElementsOf(ShMcpVocabulary.aliases().values());
    }
}
