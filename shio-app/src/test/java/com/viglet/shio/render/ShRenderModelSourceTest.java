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

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.viglet.shio.api.post.type.dto.ShPostTypeAttrDef;
import com.viglet.shio.api.post.type.dto.ShPostTypeDef;
import com.viglet.shio.post.type.ShSystemPostType;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SH290 — the render model is compared with the 2018 model it was transcribed from.
 *
 * <h2>Why this exists</h2>
 *
 * <p>{@link ShRenderPostTypes} says, in a comment: <em>"The field names below are the 2018
 * package's, so a real {@code openviglet/bootstrap-site} export imports and renders without a
 * migration."</em> SH277 found that claim false in the way that mattered — the 2018 model declares
 * {@code TITLE} first, {@code Text}, required, {@code isTitle} on all three types, and the
 * transcription dropped it from all three, which is what made a template written through the
 * documented flow unaddressable. The premise was load-bearing, wrong for four blocks, and nothing
 * could notice.</p>
 *
 * <p>It is assertable in SH274's sense, because the artefact is <b>in this repository</b>:
 * {@code import/post-types.json} holds the whole 2018 content model. Nothing loads that file at
 * runtime — this test is what gives it a job, which is the better outcome than deleting it, since a
 * reference model with no reader is exactly how the transcription drifted in the first place.</p>
 *
 * <h2>Why it does not demand equality</h2>
 *
 * <p>The 2018 model carries fields this renderer deliberately does not implement, so a strict
 * comparison would fail on purpose-built differences and be switched off. Every divergence is
 * therefore <b>declared with its reason</b> — and a declared divergence that no longer exists
 * <b>fails the build</b>, which is SH142's rule for the verify baseline and SH255's for the
 * round-trip table: a suppression list that cannot rot is the only kind worth keeping.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:shio-sh290;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true")
@TestInstance(Lifecycle.PER_CLASS)
class ShRenderModelSourceTest {

    private static final String REFERENCE = "/import/post-types.json";

    /**
     * A field the 2018 model declares and this one deliberately omits, with the reason.
     *
     * <p>Keyed {@code Type.FIELD}. Read once as a checklist of everything the renderer decided not
     * to carry — which is a more useful artefact than the omissions being invisible.</p>
     */
    private static final Map<String, String> OMITTED = new TreeMap<>(Map.of(
            "PageLayout.DESCRIPTION", "the renderer never reads a template's description, and a "
                    + "required field nothing consumes is one every write has to satisfy",
            "Region.DESCRIPTION", "as PageLayout.DESCRIPTION",
            "Theme.DESCRIPTION", "as PageLayout.DESCRIPTION",
            "Region.CACHED", "there is no page-cache subsystem — /object/clear-cache is a no-op, "
                    + "so a per-region cache flag would be a setting with nothing behind it",
            "Region.CACHE_TAB", "the Tab that groups CACHED, and empty without it"));

    /**
     * A field this renderer declares that the 2018 model does not, with why that is right.
     *
     * <p>The default is that inventing one is a defect — an imported 2018 package arrives without
     * it, which is the migration {@code ShRenderPostTypes}' comment promises there is none of. It
     * is allowed when the field is <b>optional</b> and its absence changes nothing, which is the
     * condition that keeps the promise true.</p>
     */
    /**
     * A whole post-type this renderer invents, with the reason (SH385).
     *
     * <p>Kept apart from {@link #INVENTED}, which is about a <em>field</em>, because the two say
     * different things: an invented field means an imported 2018 template arrives without it and
     * renders as it did, while an invented <b>type</b> means the 2018 package cannot carry the concept
     * at all. That is a larger claim and deserves its own list rather than one entry per field of it,
     * which would also have to be extended every time the type grew a field.</p>
     */
    private static final Map<String, String> INVENTED_TYPES = new TreeMap<>(Map.of(
            ShSystemPostType.REDIRECT,
            "SH385 — a replication that renames a path breaks every link into it, and the 2018 model "
                    + "has no redirect of any kind: FSP resolved a URL to a post and 404'd. It is a "
                    + "SYSTEM type rather than a vocabulary one because the *router* consults it "
                    + "before a page is chosen (SH249's test), and it carries no 2018 field at all, "
                    + "so a per-field entry in INVENTED would be three entries saying one thing."));

    private static final Map<String, String> INVENTED = new TreeMap<>(Map.of(
            "Theme.TOKENS", "SH195 — the 2018 model has no token vocabulary at all, which is the "
                    + "defect that task exists to fix rather than a difference to reconcile. The "
                    + "field is optional and empty means 'render on the CSS alone', so an imported "
                    + "2018 theme arrives without it and renders exactly as it did."));

    /**
     * A field both models declare with a different widget, with the reason.
     *
     * <p>These are the ones worth watching: a differing widget is a decision, while a differing
     * <em>name</em> would be a transcription error of exactly the kind SH277 found.</p>
     */
    private static final Map<String, String> WIDGETS = new TreeMap<>(Map.of(
            "PageLayout.THEME", "SH183 resolves a theme by title, not by id: the 2018 model's "
                    + "Content Select stores a post id, which no `sh-region`-style name can be "
                    + "written by hand and which does not survive an export/import",
            "Theme.CSS", "SH252 — a stylesheet is not an HTML document, and the console should "
                    + "give it the CSS editor; the 2018 model used Ace Editor - HTML for both",
            "Theme.JAVASCRIPT", "as Theme.CSS — the 2018 model used the HTML editor for a script"));

    @Autowired
    private ShRenderPostTypes shRenderPostTypes;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("every field the render model declares is the 2018 model's, spelled the same way")
    void fieldNamesMatchTheReference() {
        Map<String, Map<String, Field>> reference = reference();

        for (ShPostTypeDef def : shRenderPostTypes.ensure()) {
            if (INVENTED_TYPES.containsKey(def.name())) {
                continue;
            }
            Map<String, Field> declared = reference.get(def.name());
            assertThat(declared)
                    .as("the 2018 model has no %s at all, which makes the whole comparison "
                            + "meaningless rather than merely failing", def.name())
                    .isNotNull();
            for (ShPostTypeAttrDef field : def.fields()) {
                if (INVENTED.containsKey(def.name() + "." + field.name())) {
                    continue;
                }
                assertThat(declared)
                        .as("""
                                %s.%s is not in the 2018 model. A field this renderer invents is \
                                a field the 2018 package cannot carry, so an imported template \
                                arrives without it — which is the migration the class comment \
                                promises there is none of.""", def.name(), field.name())
                        .containsKey(field.name());
            }
        }
    }

    @Test
    @DisplayName("the title flag matches — the SH277 defect, now asserted rather than remembered")
    void theTitleFlagMatchesTheReference() {
        Map<String, Map<String, Field>> reference = reference();

        for (ShPostTypeDef def : shRenderPostTypes.ensure()) {
            if (INVENTED_TYPES.containsKey(def.name())) {
                continue;
            }
            for (ShPostTypeAttrDef field : def.fields()) {
                if (INVENTED.containsKey(def.name() + "." + field.name())) {
                    continue;
                }
                Field source = reference.get(def.name()).get(field.name());
                assertThat(field.isTitle())
                        .as("""
                                %s.%s: the 2018 model says isTitle=%s. This is the flag SH277 was \
                                filed for — dropped from all three types, so every template \
                                written by post.upsert landed with an empty title and no \
                                sh-region or postTypeLayout binding could resolve to it.""",
                                def.name(), field.name(), source.title())
                        .isEqualTo(source.title());
            }
        }
    }

    @Test
    @DisplayName("every divergence from the 2018 model is declared, and every declaration is real")
    void divergencesAreDeclaredAndStillApply() {
        Map<String, Map<String, Field>> reference = reference();
        List<String> undeclared = new java.util.ArrayList<>();
        java.util.Set<String> unusedOmissions = new TreeSet<>(OMITTED.keySet());
        java.util.Set<String> unusedWidgets = new TreeSet<>(WIDGETS.keySet());
        java.util.Set<String> unusedInventions = new TreeSet<>(INVENTED.keySet());
        // Emptied the same way as the others, so a declaration for a type this renderer stopped
        // inventing fails here rather than sitting in the list looking like a reason.
        java.util.Set<String> unusedInventedTypes = new TreeSet<>(INVENTED_TYPES.keySet());

        for (ShPostTypeDef def : shRenderPostTypes.ensure()) {
            if (unusedInventedTypes.remove(def.name())) {
                continue;
            }
            Map<String, Field> source = reference.get(def.name());
            Map<String, ShPostTypeAttrDef> ours = new LinkedHashMap<>();
            def.fields().forEach(field -> ours.put(field.name(), field));

            for (String name : source.keySet()) {
                String key = def.name() + "." + name;
                if (ours.containsKey(name)) {
                    continue;
                }
                if (!unusedOmissions.remove(key)) {
                    undeclared.add(key + " — the 2018 model declares it and this one does not");
                }
            }
            for (ShPostTypeAttrDef field : ours.values()) {
                Field their = source.get(field.name());
                String key = def.name() + "." + field.name();
                if (their == null) {
                    // Invented here: reported unless declared, and the declaration is spent so an
                    // entry that stops being true fails below.
                    if (!unusedInventions.remove(key)) {
                        undeclared.add(key + " — this model declares it and the 2018 one does not");
                    }
                    continue;
                }
                if (their.widget().equals(field.widget())) {
                    continue;
                }
                if (!unusedWidgets.remove(key)) {
                    undeclared.add(key + " — widget " + field.widget() + " here, "
                            + their.widget() + " in the 2018 model");
                }
            }
        }

        assertThat(undeclared)
                .as("""
                        an undeclared divergence is the shape SH277 had: a difference nobody \
                        decided, carried in a comment that claimed there was none. Add it to \
                        OMITTED or WIDGETS with the reason, or make the model match.""")
                .isEmpty();
        assertThat(unusedOmissions)
                .as("""
                        a declared omission the model no longer has — SH142's rule, so the list \
                        cannot rot into a blanket suppression that hides the next real drift.""")
                .isEmpty();
        assertThat(unusedWidgets)
                .as("a declared widget difference that no longer exists — same rule")
                .isEmpty();
        assertThat(unusedInventedTypes)
                .as("""
                        a TYPE declared as invented that this renderer no longer provisions — same                         rule, and this entry is permission to skip an entire type's comparison,                         which makes a stale one the most expensive of the three.""")
                .isEmpty();
        assertThat(unusedInventions)
                .as("""
                        a field declared as invented that the 2018 model turns out to have, or                         that this model no longer declares — same rule, and the more important                         direction here: an INVENTED entry is permission to skip two other                         assertions.""")
                .isEmpty();
    }

    // --- the reference model ---

    /** {@code type name -> field name -> field}, read from the 2018 package in this repository. */
    private Map<String, Map<String, Field>> reference() {
        try (InputStream in = getClass().getResourceAsStream(REFERENCE)) {
            assertThat(in).as("%s is missing — it is the reference this test compares against, so "
                    + "its absence is a failure rather than a skip", REFERENCE).isNotNull();
            JsonNode root = objectMapper.readTree(in);
            Map<String, Map<String, Field>> byType = new TreeMap<>();
            for (JsonNode type : root.get("postTypes")) {
                Map<String, Field> fields = new TreeMap<>();
                JsonNode declared = type.get("fields");
                if (declared != null) {
                    for (String name : declared.propertyNames()) {
                        JsonNode field = declared.get(name);
                        fields.put(name, new Field(
                                field.path("widget").asString(""),
                                field.path("title").asBoolean(false)));
                    }
                }
                byType.put(type.path("name").asString(""), fields);
            }
            return byType;
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cannot read " + REFERENCE, e);
        }
    }

    /** One field of the reference model — only what this comparison is about. */
    private record Field(String widget, boolean title) {
    }
}
