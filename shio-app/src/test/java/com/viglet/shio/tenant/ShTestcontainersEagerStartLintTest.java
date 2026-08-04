/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SH416 — a {@code @Container} field read from {@code @DynamicPropertySource} must be started
 * eagerly, because the extension does not start it in time.
 *
 * <p>{@code @Container} starts the container in {@code TestcontainersExtension.beforeAll}, and Spring
 * makes its first context-load attempt <b>before</b> that runs. A
 * {@code registry.add("spring.datasource.url", POSTGRES::getJdbcUrl)} supplier is therefore invoked
 * against a container with no mapped port, the attempt fails, and with Spring's default
 * {@code spring.test.context.failure.threshold} of 1 every test in the class then reports
 * {@code ApplicationContext failure threshold (1) exceeded} — a message about Spring declining a
 * <em>second</em> attempt, which names neither Testcontainers nor a container nor a port.</p>
 *
 * <p><b>Why a lint and not just the two fixes.</b> The symptom is unreadable and the diagnosis cost
 * three runs to separate from a cold image pull: with {@code @Container} alone the class fails in
 * 3.9 s, with the threshold raised to 5 it <b>passes</b> in 18.7 s with zero errors, and with an
 * eager start it passes in 17.1 s at the default threshold. Nobody rediscovers that from the stack
 * trace. And it had already gone unnoticed for a release: these two classes carry
 * {@code @Testcontainers(disabledWithoutDocker = true)}, so on a machine with no Docker daemon they
 * <em>skip</em> and read as a pass, while failsafe's {@code testFailureIgnore} meant the error state
 * was not red either — the defect was invisible in both states of the machine. That is SH322's shape,
 * and SH338's rule applies: the instrument that finds the next one lands before the fixes.</p>
 *
 * <p><b>The rule:</b> a test source that declares a {@code @Container} field and references that
 * field's name inside a {@code @DynamicPropertySource} method must also start it outside the
 * extension's lifecycle — a {@code static} initializer calling {@code <FIELD>.start()}. A class
 * initializer is the deterministic form: the JVM completes it before any member is read, so it does
 * not depend on extension ordering (swapping {@code @Testcontainers} ahead of {@code @SpringBootTest}
 * was measured and changed nothing).</p>
 *
 * <p><b>Declared limit.</b> This is textual and matches the field by name, so it sees the two spellings
 * this repository uses — {@code FIELD::getJdbcUrl} and {@code FIELD.getX()} — and would miss a
 * container reached through an alias or a helper method. It is a lower bound, stated here rather than
 * left to be found, which is the failure mode SH273 and SH322 both name. It does not fire on a class
 * that resolves nothing from its container (no {@code @DynamicPropertySource} reference), because
 * there is no premature read to be wrong about.</p>
 *
 * @author Alexandre Oliveira
 */
class ShTestcontainersEagerStartLintTest {

    private static final Path TEST_SOURCES = Path.of("src", "test", "java");

    /** {@code @Container} on a field declaration, capturing the field's name. */
    private static final Pattern CONTAINER_FIELD = Pattern.compile(
            "@Container\\s+(?:static\\s+|final\\s+|public\\s+|private\\s+)*"
                    + "[A-Za-z0-9_.<>?, \\[\\]]+?\\s+([A-Z][A-Z0-9_]*)\\s*=");

    @Test
    @DisplayName("SH416: a container resolved from @DynamicPropertySource is started eagerly")
    void everyLazilyResolvedContainerIsStartedEagerly() throws IOException {
        List<String> offenders = new ArrayList<>();

        try (Stream<Path> sources = Files.walk(TEST_SOURCES)) {
            for (Path source : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
                String body = Files.readString(source, StandardCharsets.UTF_8);
                if (!body.contains("@Container") || !body.contains("@DynamicPropertySource")) {
                    continue;
                }
                Matcher fields = CONTAINER_FIELD.matcher(body);
                while (fields.find()) {
                    String field = fields.group(1);
                    if (!resolvedLazily(body, field)) {
                        continue;
                    }
                    if (!startedEagerly(body, field)) {
                        offenders.add("%s: %s".formatted(source.getFileName(), field));
                    }
                }
            }
        }

        assertThat(offenders)
                .as("""
                        These containers are read by a @DynamicPropertySource supplier but started \
                        only by @Container, which runs after Spring's first context-load attempt. \
                        The supplier is then invoked against a container with no mapped port, and \
                        the class fails with "ApplicationContext failure threshold (1) exceeded" — \
                        a message that names neither the container nor the port, which is why this \
                        lint exists rather than a comment (SH416). Fix: add \
                        `static { <FIELD>.start(); }` beside the field. Keep @Container for \
                        teardown; start() on a running container is a no-op.""")
                .isEmpty();
    }

    /** Does a {@code @DynamicPropertySource} method read this field? */
    private static boolean resolvedLazily(String body, String field) {
        int at = body.indexOf("@DynamicPropertySource");
        return at >= 0 && body.substring(at).contains(field + "::")
                || at >= 0 && body.substring(at).contains(field + ".get");
    }

    /** Is there a static initializer that starts it? */
    private static boolean startedEagerly(String body, String field) {
        return Pattern.compile("static\\s*\\{[^}]*" + Pattern.quote(field) + "\\s*\\.start\\s*\\(")
                .matcher(body).find();
    }
}
