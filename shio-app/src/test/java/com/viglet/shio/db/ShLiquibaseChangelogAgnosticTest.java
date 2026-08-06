/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.viglet.shio.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Ensures Liquibase changelog files avoid non-agnostic types so the same
 * changelogs apply cleanly on H2, MariaDB and PostgreSQL. Ported from Turing's
 * {@code TurLiquibaseChangelogAgnosticTest}.
 */
class ShLiquibaseChangelogAgnosticTest {
    private static final List<String> FORBIDDEN_TOKENS = List.of(
            "TIMESTAMP WITH TIME ZONE",
            "CHARACTER LARGE OBJECT",
            "BINARY LARGE OBJECT",
            "BOOLEAN",
            // SH26: inline ENUM('A','B') is accepted by H2/MySQL/MariaDB but
            // PostgreSQL rejects it ("type \"enum\" does not exist"), so the
            // changelog never applied there. Map enums as VARCHAR (the entity
            // uses @Enumerated(STRING)); the cross-DB ITs guard the real apply.
            "ENUM(");

    @Test
    void testChangelogFilesAvoidDatabaseSpecificTypes() throws IOException {
        Path changelogDir = Paths.get("src/main/resources/db/changelog");
        assertThat(Files.isDirectory(changelogDir))
                .as("Expected changelog directory at %s", changelogDir)
                .isTrue();

        List<String> violations = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(changelogDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(ShLiquibaseChangelogAgnosticTest::isYamlFile)
                    .forEach(path -> scanFile(path, changelogDir, violations));
        }

        assertThat(violations)
                .as("Changelog files must be database agnostic. Remove forbidden types.")
                .isEmpty();
    }

    private static boolean isYamlFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yaml") || name.endsWith(".yml");
    }

    private static void scanFile(Path path, Path baseDir, List<String> violations) {
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                for (String token : FORBIDDEN_TOKENS) {
                    if (line.contains(token)) {
                        String relative = baseDir.relativize(path).toString()
                                .replace('\\', '/');
                        violations.add(relative + ":" + (i + 1) + " contains '" + token
                                + "' -> " + line.trim());
                    }
                }
            }
        } catch (IOException ex) {
            violations.add("Failed to read " + path + ": " + ex.getMessage());
        }
    }
}
