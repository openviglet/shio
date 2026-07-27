/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.type;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.viglet.shio.api.post.type.dto.ShPostTypeAttrDef;
import com.viglet.shio.api.post.type.dto.ShPostTypeDef;

/**
 * SH152 — additive post-type reconciliation, on its own.
 *
 * <p>The blueprint-level test proves the flag is wired; these prove the operation is the one the
 * task promised, which is a set of <em>refusals</em>: never change a field, never remove one,
 * never reorder, never touch a system type. Each of those is something somebody else may own, and
 * a merge that broke one would be an upsert wearing a different name.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShPostTypeMergeTest {

    @Autowired
    private ShPostTypeAdminService service;

    private ShPostTypeAttrDef field(String name, int ordinal, String widget) {
        return new ShPostTypeAttrDef(name, name, null, widget, false, false, false, ordinal,
                null, null);
    }

    private String seed(List<ShPostTypeAttrDef> fields) {
        String name = "MergeTarget" + UUID.randomUUID().toString().substring(0, 8);
        service.upsert(name,
                new ShPostTypeDef(name, name, name + "s", null, null, false, false, fields));
        return name;
    }

    @Test
    @DisplayName("the missing fields are added and the existing ones are untouched")
    void addsWhatIsMissing() {
        String name = seed(List.of(field("title", 1, "Text")));

        List<String> added = service.mergeMissingFields(new ShPostTypeDef(name, name, null, null,
                null, false, false,
                List.of(field("title", 1, "Text"), field("sections", 2, "Multi Select"),
                        field("summary", 3, "Text Area"))));

        assertThat(added).containsExactly("sections", "summary");
        var fields = service.get(name).orElseThrow().fields();
        assertThat(fields).extracting(ShPostTypeAttrDef::name)
                .containsExactly("title", "sections", "summary");
        assertThat(fields).filteredOn(f -> "title".equals(f.name())).singleElement()
                .satisfies(f -> assertThat(f.widget())
                        .as("the field that was already there keeps its widget")
                        .isEqualTo("Text"));
    }

    @Test
    @DisplayName("an added field's ordinal is appended, so nobody's form is reordered")
    void ordinalsAreAppended() {
        // The package declares its own field first (ordinal 1). Honouring that would push the
        // human's field down the form — a form that rearranged itself because a blueprint was
        // applied is a surprise with no upside.
        String name = seed(List.of(field("title", 7, "Text")));

        service.mergeMissingFields(new ShPostTypeDef(name, name, null, null, null, false, false,
                List.of(field("sections", 1, "Multi Select"))));

        var fields = service.get(name).orElseThrow().fields();
        assertThat(fields).extracting(ShPostTypeAttrDef::name).containsExactly("title", "sections");
        assertThat(fields).filteredOn(f -> "sections".equals(f.name())).singleElement()
                .satisfies(f -> assertThat(f.ordinal()).isGreaterThan(7));
    }

    @Test
    @DisplayName("a field the type already has is never changed, even when the package differs")
    void neverChangesAnExistingField() {
        String name = seed(List.of(field("body", 1, "Text")));

        List<String> added = service.mergeMissingFields(new ShPostTypeDef(name, name, null, null,
                null, false, false, List.of(field("body", 1, "HTML Editor"))));

        assertThat(added).isEmpty();
        assertThat(service.get(name).orElseThrow().fields())
                .singleElement()
                .satisfies(f -> assertThat(f.widget())
                        .as("changing a widget is a change, and this operation only adds")
                        .isEqualTo("Text"));
    }

    @Test
    @DisplayName("nothing the package omits is removed")
    void neverRemoves() {
        String name = seed(List.of(field("title", 1, "Text"), field("kept", 2, "Text")));

        service.mergeMissingFields(new ShPostTypeDef(name, name, null, null, null, false, false,
                List.of(field("title", 1, "Text"))));

        assertThat(service.get(name).orElseThrow().fields()).extracting(ShPostTypeAttrDef::name)
                .contains("kept");
    }

    @Test
    @DisplayName("an unknown post-type is a no-op, not a create")
    void anUnknownTypeIsANoop() {
        assertThat(service.mergeMissingFields(new ShPostTypeDef("NoSuchType" + UUID.randomUUID(),
                null, null, null, null, false, false, List.of(field("x", 1, "Text")))))
                .isEmpty();
    }
}
