/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import com.viglet.shio.api.agent.dto.ShAgentConsoleCost;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SH1112 — the instance reads its console's eager load the way the build gate's script does.
 *
 * <p>{@code src/test/resources/console-cost/} is a small built console, and {@code expected.json}
 * beside it is what {@code shio-react/scripts/console-size.mjs} measured on it.
 * {@code shio-react/test/console-cost-parity.test.mjs} holds the script to the same file, so the
 * two readers agree through one committed answer rather than through a promise.</p>
 */
class ShConsoleCostTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ShConsoleCost over(String root) {
        return new ShConsoleCost(new DefaultResourceLoader(), objectMapper, root);
    }

    @Test
    @DisplayName("SH1112: an agent reading the eager total gets the bytes the script prints for the same build")
    void theInstanceMeasuresWhatTheScriptMeasures() throws IOException {
        JsonNode expected;
        try (InputStream in = getClass().getResourceAsStream("/console-cost/expected.json")) {
            expected = objectMapper.readTree(in);
        }

        ShAgentConsoleCost cost = over("classpath:console-cost/").cost();

        assertThat(cost).isNotNull();
        assertThat(cost.raw()).as("SH1112: raw bytes, as the script prints them").isEqualTo(expected.path("raw").longValue());
        // Gzip is the same reading through a different deflater: Node ships Chromium's zlib and the
        // JVM its own, and neither promises the same stream at level 9. Measured on this fixture the
        // two differ by 0.6%, so the claim is agreement within 1% rather than equal bytes, and the
        // raw total, which is exact, is what a comparison against the build gate should use.
        assertThat(cost.gzip()).as("SH1112: gzipped at level 9, within 1% of the script's deflater")
                .isCloseTo(expected.path("gzip").longValue(), org.assertj.core.data.Percentage.withPercentage(1));
        List<String> rows = new ArrayList<>();
        expected.path("assets").forEach(row -> rows.add(row.path("asset").stringValue("") + " "
                + row.path("raw").longValue()));
        assertThat(cost.assets()).extracting(asset -> asset.asset() + " " + asset.raw())
                .as("the same assets, each the same raw size, and the lazy chunk the HTML never names left out")
                .containsExactlyElementsOf(rows);
        assertThat(cost.ceiling())
                .as("and the ceiling the build shipped beside itself, so the number arrives with its line")
                .isEqualTo(new ShAgentConsoleCost.Ceiling(60000, 12000));
    }

    @Test
    @DisplayName("SH1112: the console's cost is an opt-in manifest section, so no ordinary read pays for it")
    void theSectionIsOptIn() {
        assertThat(ShAgentManifestSection.byDefault()).doesNotContain(ShAgentManifestSection.CONSOLE);
        assertThat(ShAgentManifestSection.parse("console")).containsExactly(ShAgentManifestSection.CONSOLE);
        assertThat(ShAgentManifestSection.parse("all"))
                .as("`all` is every section a caller would have got anyway, SH979's rule")
                .doesNotContain(ShAgentManifestSection.CONSOLE);
    }

    @Test
    @DisplayName("SH1112: an instance serving no console answers no section, not a total of zero")
    void noConsoleIsNoSection() {
        assertThat(over("classpath:no-such-console/").cost()).isNull();
    }
}
