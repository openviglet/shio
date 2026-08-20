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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

/**
 * SH872 — the rule two readers of the content model share.
 *
 * <p>The storage SH868 proposed is the markup, and what shipped stored the values in fields on
 * {@code Page} as well — a field per captured block on the type every page in the instance uses.
 * Giving the storage back leaves one question: a curator's edit through the Universal Editor
 * <em>does</em> store a key, and {@code ShAgentVerifyService} and {@code ShAgentBatchService} would
 * both call it undeclared. This is what they ask instead.</p>
 *
 * <p>No Spring context, for {@link ShRenderModelAnnotatedTest}'s reason: SH317 counts them, and
 * this needs an {@code ObjectMapper} and nothing else.</p>
 *
 * @author Alexandre Oliveira
 */
class ShAnnotatedFieldsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("SH872: a name an sh-field attribute addresses is declared by the markup holding it")
    void anAnnotatedNameIsDeclared() {
        assertThat(of("{\"content\":\"<h1 sh-field=\\\"b1a2c3dHeading\\\">Source</h1>"
                + "<p sh-field='e5f6a7bIntro'>Copy</p>\"}"))
                .as("both quotings, because the attribute is copied verbatim from wherever it was "
                        + "stamped and nothing normalises it on the way in")
                .containsExactly("b1a2c3dHeading", "e5f6a7bIntro");
    }

    @Test
    @DisplayName("a post storing no markup declares nothing, which is almost every post")
    void plainDataDeclaresNothing() {
        assertThat(of("{\"title\":\"Home\",\"description\":\"A page\"}")).isEmpty();
    }

    @Test
    @DisplayName("a collection path is the section's own data, so it is not a key on this post")
    void aCollectionPathIsNotDeclared() {
        // The same boundary `ShRenderModel.substituteInto` draws one step along: `features.0.title`
        // addresses a row inside a value this post already holds, and its template renders it.
        assertThat(of("{\"content\":\"<li sh-field=\\\"features.0.title\\\">Fast</li>\"}")).isEmpty();
    }

    @Test
    @DisplayName("only a string value is markup — a number or an array declares nothing")
    void onlyStringsAreRead() {
        assertThat(of("{\"sections\":[\"/sections/hero\"],\"weight\":3}")).isEmpty();
    }

    private java.util.Set<String> of(String data) {
        return ShAnnotatedFields.of(objectMapper.readTree(data));
    }
}
