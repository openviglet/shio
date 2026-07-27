/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.object;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.api.cda.dto.ShCdaFolder;
import com.viglet.shio.api.cda.dto.ShCdaListing;
import com.viglet.shio.api.cda.dto.ShCdaPostSummary;
import com.viglet.shio.api.object.dto.ShConsoleFolderRow;
import com.viglet.shio.api.object.dto.ShConsoleListing;
import com.viglet.shio.api.object.dto.ShConsolePostRow;

/**
 * SH120 — where a console read and a CDA read describe the same thing, they describe it
 * with the same words.
 *
 * <p>The console's navigation shapes are supersets of the CDA's: the same components, in
 * the same order, with the same types, plus the fields a console has that a delivery API
 * does not (draft state, manual ordering). Anything else is a second vocabulary for one
 * concept, which is the tax P5 and P10 exist to refuse and the reason this task existed at
 * all — the listing used to say {@code shPosts}/{@code shPostType.name} for what the CDA
 * calls {@code posts}/{@code postType}.</p>
 *
 * <p>Like the SH162 mirror test, this guards a <em>class</em> of drift rather than an
 * instance of it. The failure mode it prevents is nobody's mistake in particular:
 * somebody adds {@code modified} to one row shape and {@code modifiedDate} already exists
 * on the other, both are locally reasonable, and the alignment is gone with no test to
 * notice. It also checks the prefix ordering, because a superset whose shared components
 * are shuffled is still two shapes to a reader comparing them side by side.</p>
 */
class ShConsoleShapeOverlapTest {

    /**
     * Console shape -> the CDA shape it is a superset of. The <em>envelope</em> pairing is
     * deliberately partial in one direction and that is documented on
     * {@link ShConsoleListing}: the console listing carries no paging, because it returns a
     * folder's children whole and three constant fields would be a contract it does not
     * keep. So the envelope is checked by name-overlap, and the rows are checked as strict
     * prefixes.
     */
    private static final Map<Class<?>, Class<?>> ROW_SUPERSETS = Map.of(
            ShConsoleFolderRow.class, ShCdaFolder.class,
            ShConsolePostRow.class, ShCdaPostSummary.class);

    @Test
    @DisplayName("every console row starts with its CDA counterpart, name and type for name and type")
    void consoleRowsAreStrictSupersetsOfTheirCdaCounterparts() {
        List<String> violations = new ArrayList<>();

        for (Map.Entry<Class<?>, Class<?>> pair : ROW_SUPERSETS.entrySet()) {
            RecordComponent[] console = components(pair.getKey());
            RecordComponent[] cda = components(pair.getValue());

            if (console.length < cda.length) {
                violations.add(pair.getKey().getSimpleName() + " has fewer components than "
                        + pair.getValue().getSimpleName() + " — it cannot be a superset");
                continue;
            }
            for (int i = 0; i < cda.length; i++) {
                if (!cda[i].getName().equals(console[i].getName())) {
                    violations.add(pair.getKey().getSimpleName() + " component " + i + " is '"
                            + console[i].getName() + "' where " + pair.getValue().getSimpleName()
                            + " has '" + cda[i].getName()
                            + "' — the shared prefix must match in order and in name");
                } else if (!cda[i].getType().equals(console[i].getType())) {
                    violations.add(pair.getKey().getSimpleName() + "." + console[i].getName()
                            + " is a " + console[i].getType().getSimpleName() + " where "
                            + pair.getValue().getSimpleName() + " delivers a "
                            + cda[i].getType().getSimpleName()
                            + " — a shared name with a different type is worse than a new name");
                }
            }
        }

        assertThat(violations)
                .as("""
                        A console read shape has drifted from the CDA shape it mirrors (SH120). Where \
                        the two describe the same thing they use the same component names, in the same \
                        order, with the same types; console-only fields go after the shared prefix.""")
                .isEmpty();
    }

    @Test
    @DisplayName("the listing envelope uses the CDA's names for the parts it shares")
    void listingEnvelopeSharesTheCdaVocabulary() {
        Map<String, Class<?>> console = componentsByName(ShConsoleListing.class);
        Map<String, Class<?>> cda = componentsByName(ShCdaListing.class);

        // These three are the same concept in both and must not be renamed apart again.
        assertThat(console.keySet()).contains("folderId", "folders", "posts");
        assertThat(cda.keySet()).contains("folderId", "folders", "posts");
        assertThat(console.get("folderId")).isEqualTo(cda.get("folderId"));

        // ...and the console envelope adds nothing beyond them. A field here would be a
        // per-listing cost on the console's hottest read, and the two things it used to
        // carry — the site and the breadcrumb — are what /path answers.
        assertThat(console.keySet()).containsExactly("folderId", "folders", "posts");
    }

    /**
     * The guard's own guard: reflection over a non-record silently yields nothing, which
     * would make every assertion above vacuously true.
     */
    @Test
    @DisplayName("the comparison is actually reading components from both sides")
    void bothSidesHaveComponentsToCompare() {
        for (Map.Entry<Class<?>, Class<?>> pair : ROW_SUPERSETS.entrySet()) {
            assertThat(components(pair.getKey())).as("%s", pair.getKey()).isNotEmpty();
            assertThat(components(pair.getValue())).as("%s", pair.getValue()).isNotEmpty();
        }
        // The properties SH120 was opened for, pinned by name so a rename has to be
        // deliberate: these are the three the old entity-shaped listing got wrong.
        Map<String, Class<?>> post = componentsByName(ShConsolePostRow.class);
        assertThat(post).containsKeys("postType", "date", "modifiedDate");
        // ...and the draft axis, which is why this is a superset and not the CDA record.
        assertThat(post).containsKeys("published", "state", "publishStatus");
    }

    private static RecordComponent[] components(Class<?> type) {
        assertThat(type.isRecord()).as("%s must be a record", type.getSimpleName()).isTrue();
        return type.getRecordComponents();
    }

    private static Map<String, Class<?>> componentsByName(Class<?> type) {
        Map<String, Class<?>> byName = new LinkedHashMap<>();
        for (RecordComponent component : components(type)) {
            byName.put(component.getName(), component.getType());
        }
        return byName;
    }
}
