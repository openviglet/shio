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

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SH155 — the sentence a site remembers about the package it came from.
 *
 * <p>No Spring context: the key and the text are pure, which is what makes them cheap enough
 * to pin. The rules worth pinning are the ones a later change could break silently — the
 * order (a note whose text churned would rewrite its own row on every apply), the reserved
 * parameter, and the admission that a long parameter list was cut.</p>
 *
 * @author Alexandre Oliveira
 */
class ShBlueprintRecordTest {

    @Test
    @DisplayName("the key is the package name under one prefix, so the records are one grep apart")
    void keyIsPrefixed() {
        assertThat(ShBlueprintRecord.keyFor("marketing-site")).isEqualTo("blueprint.marketing-site");
        assertThat(ShBlueprintRecord.keyFor("  Blog  ")).isEqualTo("blueprint.blog");
    }

    @Test
    @DisplayName("a name that cannot be a slug files no record rather than a sanitised guess")
    void unslugabbleNameRecordsNothing() {
        // A third-party package (SH96) with a name the memory's key grammar rejects has a
        // naming problem, and filing it under a mangled key would hide that behind a record
        // nobody can search for.
        assertThat(ShBlueprintRecord.keyFor("My Package")).isNull();
        assertThat(ShBlueprintRecord.keyFor("-leading-dash")).isNull();
        assertThat(ShBlueprintRecord.keyFor("")).isNull();
        assertThat(ShBlueprintRecord.keyFor(null)).isNull();
    }

    @Test
    @DisplayName("the note names the package, its version and its parameters, sorted")
    void noteNamesTheVersionAndParams() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("tagline", "Ship faster");
        params.put("brand", "Acme");
        params.put("site", "Acme Marketing");

        String note = ShBlueprintRecord.noteFor("marketing-site", "1.0.0", params);

        assertThat(note).isEqualTo(
                "Built from blueprint 'marketing-site' 1.0.0 — params: brand=Acme; tagline=Ship faster");
        // Sorted, not insertion-ordered: the same apply has to write the same bytes, or the
        // note rewrites itself and its date stops meaning "when this was last confirmed" (P5).
        assertThat(note.indexOf("brand")).isLessThan(note.indexOf("tagline"));
        // `site` is the reserved parameter and always equals the note's own scope — repeating
        // it would cost tokens in every context pack that carries the note.
        assertThat(note).doesNotContain("Acme Marketing");
    }

    @Test
    @DisplayName("a package with no parameters still records what it is")
    void noParamsIsStillARecord() {
        assertThat(ShBlueprintRecord.noteFor("docs", "2.1.0", Map.of()))
                .isEqualTo("Built from blueprint 'docs' 2.1.0.");
        assertThat(ShBlueprintRecord.noteFor("docs", null, null))
                .isEqualTo("Built from blueprint 'docs'.");
    }

    @Test
    @DisplayName("a parameter list too long to fit says how many it dropped")
    void aCutIsAdmitted() {
        Map<String, String> params = new LinkedHashMap<>();
        for (int i = 0; i < 60; i++) {
            params.put(String.format("param%02d", i), "a value long enough to matter " + i);
        }

        String note = ShBlueprintRecord.noteFor("big", "1.0.0", params);

        assertThat(note.length()).isLessThanOrEqualTo(400);
        // An unreported cut reads as "these were all the parameters", which is the one way
        // this record could mislead the session that reads it back (SH138's rule).
        assertThat(note).contains(" more)");
        assertThat(note).contains("param00=");
    }
}
