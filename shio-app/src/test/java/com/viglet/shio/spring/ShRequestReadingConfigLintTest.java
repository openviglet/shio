/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.TreeMap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SH158 — <b>the test configuration must not silently disagree with production about how a
 * request is read.</b>
 *
 * <h2>The class of defect, not the instance</h2>
 *
 * <p>{@code src/test/resources/application.properties} <b>shadows</b> the main file on the
 * test classpath rather than merging with it. That is ordinary Spring Boot behaviour — the
 * first {@code classpath:/application.properties} wins, and the test classes directory comes
 * first — and it is also how an entire category of defect hides: a setting that governs how a
 * request is <em>read</em> can be correct in production and simply absent from every test, and
 * no test can notice, <em>because the tests are the thing that is misconfigured</em>.</p>
 *
 * <p>It is not hypothetical. {@code spring.jackson.deserialization.fail-on-null-for-primitives=false}
 * is what lets a write surface accept a body that omits an optional flag. Its absence here made
 * {@code POST /api/v2/agent/batch {"ops":[…]}} — the most natural call the protocol has — a
 * bodiless 400 through four blocks. Every test built the request record in Java, so none of
 * them exercised the binding at all; the SH112 benchmark, the first thing to send real JSON,
 * is what found it.</p>
 *
 * <p>So this is a lint over the two files rather than another setting. It closes the class:
 * the next request-reading key added to production is either mirrored here or the build
 * fails, and nobody has to remember the rule.</p>
 *
 * <h2>What counts as "request-reading"</h2>
 *
 * <p>{@link #REQUEST_READING_PREFIXES} is the whole definition, and it is deliberately narrow:
 * a key that changes how an inbound body or an inbound part is <em>parsed and bound</em>.
 * Serialization settings are out — a test that asserts on a response object never went through
 * the writer, so drift there fails visibly in the test itself. Widening the list is cheap and
 * safe; the cost of a key that belongs on it and is not is another four blocks.</p>
 *
 * @author Alexandre Oliveira
 */
class ShRequestReadingConfigLintTest {

    /**
     * The prefixes whose values must agree between the two files.
     *
     * <p>{@code spring.jackson.mapper.} and {@code spring.jackson.parser.} are here alongside
     * the obvious {@code deserialization.} group because all three reach the read path:
     * {@code MapperFeature} governs which properties are even considered for binding, and
     * {@code JsonParser} features govern what the tokenizer accepts. {@code spring.servlet.multipart.}
     * is here because a size limit is the difference between an upload test proving SH38 works
     * and an upload test proving Spring's 1 MB default works.</p>
     */
    private static final List<String> REQUEST_READING_PREFIXES = List.of(
            "spring.jackson.deserialization.",
            "spring.jackson.mapper.",
            "spring.jackson.parser.",
            "spring.servlet.multipart.");

    private static final Path MAIN = Path.of("src", "main", "resources", "application.properties");
    private static final Path TEST = Path.of("src", "test", "resources", "application.properties");

    /**
     * The whole guard. Every request-reading key production sets must be set to the same value
     * in the test file.
     *
     * <p>The direction is one-way on purpose. A test file that sets a request-reading key
     * production does not is not automatically wrong — it may be exercising a stricter
     * configuration on purpose — but a production key the tests never see means the tests are
     * validating a parser configuration nobody runs.</p>
     */
    @Test
    @DisplayName("SH158: every request-reading key in application.properties is mirrored in the test override")
    void testConfigurationMirrorsProductionRequestReading() throws IOException {
        final Properties main = read(MAIN);
        final Properties test = read(TEST);

        final var drift = new TreeMap<String, String>();
        for (final String key : main.stringPropertyNames()) {
            if (!isRequestReading(key)) {
                continue;
            }
            final String expected = main.getProperty(key).trim();
            final String actual = test.getProperty(key);
            if (actual == null) {
                drift.put(key, "absent from the test file (production sets it to '%s')".formatted(expected));
            } else if (!expected.equals(actual.trim())) {
                drift.put(key, "test says '%s', production says '%s'".formatted(actual.trim(), expected));
            }
        }

        assertThat(drift)
                .as("""
                        SH158: these keys change how an inbound request is parsed and bound, and the test \
                        application.properties SHADOWS the main one rather than merging with it — so a key \
                        missing below means every test in this repository is validating a configuration \
                        production does not run. Mirror them into %s (with a comment saying why), or, if a \
                        key genuinely does not belong to the read path, narrow REQUEST_READING_PREFIXES and \
                        say so.

                        Drift:
                        %s"""
                        .formatted(TEST, render(drift)))
                .isEmpty();
    }

    /**
     * The mirror is only load-bearing if somebody can tell it is a mirror. A key copied without
     * a reason reads as an ordinary test override and gets "cleaned up" by the next person to
     * tidy the file — which restores exactly the state SH158 exists to prevent.
     */
    @Test
    @DisplayName("SH158: the test override explains that it is mirroring production, not overriding it")
    void testOverrideDocumentsTheMirror() throws IOException {
        final String body = Files.readString(TEST);
        assertThat(body)
                .as("%s must carry a comment naming SH158 and the shadowing behaviour, so the mirrored "
                        + "keys are not mistaken for deliberate test-only overrides", TEST)
                .containsIgnoringCase("shadow");
    }

    private static boolean isRequestReading(String key) {
        return REQUEST_READING_PREFIXES.stream().anyMatch(key::startsWith);
    }

    private static Properties read(Path path) throws IOException {
        final Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            properties.load(in);
        }
        return properties;
    }

    private static String render(TreeMap<String, String> drift) {
        final List<String> lines = new ArrayList<>();
        drift.forEach((key, why) -> lines.add("  - %s: %s".formatted(key, why)));
        return String.join("\n", lines);
    }
}
