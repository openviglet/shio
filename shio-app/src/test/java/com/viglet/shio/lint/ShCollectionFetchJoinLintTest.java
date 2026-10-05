/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.lint;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * SH1236 — no entity collection carries {@code @Fetch(FetchMode.JOIN)}.
 *
 * <p>On a collection that fetch mode makes the association eager, and a JPQL query cannot join it
 * implicitly, so Hibernate fills it with one more select per row the query loaded.
 * {@code ShObject.shWorkflowTasks} carried it, and every listing of sites, folders and post types
 * paid it: 202 statements for a folder's 200 children, read for a field nothing used. Nothing
 * compiles differently and nothing fails; the cost only shows when somebody counts statements, which
 * is why the rule is a lint. A single-valued association is not in scope: it is eager by default,
 * and a join there is one row, not one select per row.</p>
 */
@ShSourceScan(ShSourceScan.Reads.CODE)
class ShCollectionFetchJoinLintTest {

    private static final Path MAIN = Path.of("src", "main", "java");

    /** {@code @Fetch(...JOIN)}, then the next field declaration, whose type is captured. */
    private static final Pattern FETCH_JOIN_FIELD = Pattern.compile(
            "@Fetch\\s*\\(\\s*(?:org\\.hibernate\\.annotations\\.)?FetchMode\\.JOIN\\s*\\)"
                    + "[^;]*?private\\s+([\\w.<>?, ]+?)\\s+\\w+\\s*(?:=|;)");

    private static final Pattern COLLECTION = Pattern.compile(
            "^(?:java\\.util\\.)?(Set|List|Collection|Map|SortedSet|SortedMap)\\b");

    static List<String> offenders(String name, String source) {
        List<String> found = new ArrayList<>();
        Matcher matcher = FETCH_JOIN_FIELD.matcher(source);
        while (matcher.find()) {
            if (COLLECTION.matcher(matcher.group(1).trim()).find()) {
                found.add(name + " :: " + matcher.group(1).trim());
            }
        }
        return found;
    }

    @Test
    void sh1236NoCollectionIsFetchJoined() throws IOException {
        assertThat(Files.isDirectory(MAIN)).as("SH1236: expected to run from the shio-app module").isTrue();
        List<String> found = new ArrayList<>();
        int scanned = 0;
        try (Stream<Path> tree = Files.walk(MAIN)) {
            for (Path java : tree.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = ShSourceText.code(Files.readString(java));
                if (source.contains("@Fetch")) {
                    scanned += 1;
                }
                found.addAll(offenders(MAIN.relativize(java).toString().replace('\\', '/'), source));
            }
        }
        assertThat(scanned)
                .as("SH1236: no @Fetch anywhere, so this walk asserted nothing")
                .isPositive();
        assertThat(found)
                .as("SH1236: @Fetch(JOIN) on a collection makes it eager, and every query over the "
                        + "entity then pays one select per row to fill it. Leave the collection lazy and "
                        + "join-fetch it in the one query that reads it.")
                .isEmpty();
    }

    @Test
    void sh1236ThePlantedCaseIsCaughtAndAManyToOneIsNot() {
        String planted = """
                @OneToMany(mappedBy = "owner")
                @Fetch(org.hibernate.annotations.FetchMode.JOIN)
                private Set<Task> tasks = new HashSet<>();
                """;
        String single = """
                @ManyToOne
                @Fetch(FetchMode.JOIN)
                private Vendor vendor;
                """;
        assertThat(offenders("Planted.java", planted))
                .as("SH1236: the shape that cost 202 statements has to be refused")
                .containsExactly("Planted.java :: Set<Task>");
        assertThat(offenders("Single.java", single))
                .as("SH1236: a single-valued association is one row, not one select per row")
                .isEmpty();
    }
}
