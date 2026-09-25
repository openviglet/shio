/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.lint.ShSourceScan;
import com.viglet.shio.lint.ShSourceText;

/**
 * SH1099 — no {@code @Lob} in the model, because on PostgreSQL it is a different column.
 *
 * <p>pgjdbc writes a {@code @Lob String} as a <em>large object</em>: the {@code text} column holds an
 * OID, the document lives in {@code pg_largeobject}, and Hibernate resolves it on read. The entity is
 * right and every SQL predicate over the column matches nothing, so search found no body text on
 * PostgreSQL and each post leaked an object its delete never reclaimed. The unit suite is H2, where
 * {@code @Lob} is an ordinary text column, so nothing here could have seen it; this lint is the one
 * place a new {@code @Lob} is visible before a PostgreSQL instance holds the data. Long text is
 * {@code @JdbcTypeCode(SqlTypes.LONGVARCHAR)}, bound with {@code setString} on every engine.</p>
 */
@ShSourceScan(ShSourceScan.Reads.CODE)
class ShLobStringLintTest {

    private static final Pattern LOB = Pattern.compile("^\\s*(@Lob\\b|import\\s+jakarta\\.persistence\\.Lob\\s*;)");

    @Test
    @DisplayName("SH1099: no entity declares @Lob, which PostgreSQL stores as an OID")
    void noEntityDeclaresALob() throws IOException {
        List<String> found = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(Path.of("src/main/java"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String[] lines = ShSourceText.code(Files.readString(path, StandardCharsets.UTF_8)).split("\n", -1);
                for (int i = 0; i < lines.length; i++) {
                    if (LOB.matcher(lines[i]).find()) {
                        found.add(path.toString().replace('\\', '/') + ":" + (i + 1));
                    }
                }
            }
        }
        assertThat(found)
                .as("""
                        SH1099: @Lob on a String is a large object on PostgreSQL, so the column holds \
                        an OID and no SQL predicate over it matches. Use \
                        @JdbcTypeCode(SqlTypes.LONGVARCHAR) for long text.""")
                .isEmpty();
    }
}
