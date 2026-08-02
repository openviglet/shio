/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.widget;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.api.widget.dto.ShWidgetRequest;
import com.viglet.shio.persistence.model.widget.ShWidget;

/**
 * SH285 — what a widget row is allowed to carry, pinned.
 *
 * <h2>The audit this comes from</h2>
 *
 * <p>{@code ShWidget} carried six columns. Three of them named artefacts this repository does not
 * contain: {@code class_name} pointed at {@code com.viglet.shio.widget.Sh*Widget} (that package
 * holds one file today, a constants class), and {@code implementation_code} and
 * {@code setting_path} pointed into {@code template/widget/**}, Thymeleaf paths that left with the
 * AngularJS console. Nothing read any of them, and two were <b>writable through the API</b> — which
 * is the part that makes a dead column worse than clutter: a caller can set a template path, get
 * 200, and conclude that a widget is extensible by pointing it at a file. It is not. A widget is a
 * <b>name</b>, and the behaviour behind that name is the console's code.</p>
 *
 * <h2>Why the shape is pinned rather than the deletion asserted</h2>
 *
 * <p>Asserting "these three are gone" would pass forever the moment they are gone and say nothing
 * about the fourth. Pinning the whole set makes the next addition <em>state itself</em>: a new
 * column fails here, and the fix is one line plus the sentence saying what reads it. That is SH322's
 * habit — the grep that found the three lands as the guard against the fourth — and the same shape
 * as {@code ShSystemPostTypeInventoryTest} (SH293), which asked the same question of the post-type
 * name constants one class along.</p>
 *
 * <p>No Spring context: reflection over two classes answers this, and SH317 measured what a context
 * costs (~1.5 s) — a guard that needs none should take none.</p>
 *
 * @author Alexandre Oliveira
 */
class ShWidgetShapeLintTest {

    /**
     * Every persisted field, and what reads it. A column with nothing in the right-hand column is
     * the defect SH285 removed.
     */
    private static final Map<String, String> PERSISTED = new TreeMap<>(Map.of(
            "id", "the row's identity — what a post-type attribute joins to",
            "name", "the catalogue key: WIDGET_MAP in the console, ShSystemWidget here, and what "
                    + "ShRenderPostTypes resolves a field's widget by",
            "description", "a human label the API returns; dormant rather than false (SH293's line)",
            "type", "the field types this widget can back, e.g. DATE,TEXT,TEXTAREA — dormant, and "
                    + "true of the widget it describes",
            "shPostTypeAttrs", "the inverse association, not a column",
            "serialVersionUID", "Serializable's, not a column"));

    @Test
    @DisplayName("SH285: a widget row carries only fields something reads")
    void theEntityCarriesOnlyWhatIsRead() {
        List<String> fields = fieldsOf(ShWidget.class);

        assertThat(fields)
                .as("""
                        A new column here needs a line in PERSISTED naming what reads it. Three \
                        were removed by SH285 — class_name, implementation_code, setting_path — \
                        because nothing did, and two of them were writable through the API, which \
                        made them look like extension points. If the answer to "what reads it" is \
                        "nothing yet", that is the answer this test exists to make somebody say \
                        out loud.""")
                .containsExactlyInAnyOrderElementsOf(new TreeSet<>(PERSISTED.keySet()));
    }

    @Test
    @DisplayName("SH285: and the write DTO cannot set more than the entity keeps")
    void theRequestCannotSetWhatIsGone() {
        List<String> payload = fieldsOf(ShWidgetRequest.class);

        assertThat(payload)
                .as("""
                        ShWidgetRequest is what POST/PUT /api/v2/widget binds. A component here \
                        that the entity no longer has is a field a caller can send, that is \
                        accepted, and that cannot affect anything — exactly the surface SH285 \
                        removed rather than a compile error, since a record component binds \
                        whether or not anything stores it.""")
                .isSubsetOf(PERSISTED.keySet());
        assertThat(payload)
                .as("the vacuity check: the DTO must still carry the fields the console edits")
                .contains("name", "description", "type");
    }

    private static List<String> fieldsOf(Class<?> type) {
        return java.util.Arrays.stream(type.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .filter(field -> !(Modifier.isStatic(field.getModifiers())
                        && Modifier.isFinal(field.getModifiers())
                        && !"serialVersionUID".equals(field.getName())))
                .map(Field::getName)
                .sorted()
                .toList();
    }
}
