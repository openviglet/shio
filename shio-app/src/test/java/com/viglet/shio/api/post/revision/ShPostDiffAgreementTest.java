/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.revision;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.shio.api.post.revision.ShPostDiffService.Field;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SH988 — the console's diff and {@code shio diff} agree on what changed.
 *
 * <p>{@code shio diff} composes its comparison over the agent read surface (SH619 decided that,
 * and P10 keeps it), so the two are separate code. What must not be separate is the answer. Both
 * read {@code src/test/resources/diff/agreement.json}; this holds the server's rule to it, and
 * {@code cli/test/diff-agreement.test.mjs} holds the CLI's.</p>
 */
class ShPostDiffAgreementTest {

    @Test
    void sh988TheServerRuleMatchesTheSharedCases() throws IOException {
        JsonNode cases = new ObjectMapper()
                .readTree(Files.readString(Path.of("src", "test", "resources", "diff", "agreement.json")))
                .get("cases");
        assertThat(cases.size()).as("SH988: the agreement fixture is empty").isGreaterThan(4);

        for (JsonNode each : cases) {
            JsonNode before = each.get("before").isNull() ? null : each.get("before");
            List<String> changed = ShPostDiffService.fields(before, each.get("after")).stream()
                    .filter(Field::changed).map(Field::name).toList();
            List<String> expected = new ArrayList<>();
            each.get("changed").forEach(name -> expected.add(name.asString()));
            assertThat(changed)
                    .as("SH988: the server disagrees with the shared case '%s'", each.get("name").asString())
                    .containsExactlyElementsOf(expected);
        }
    }
}
