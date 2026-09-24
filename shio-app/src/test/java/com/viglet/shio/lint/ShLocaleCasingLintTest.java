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
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.persistence.model.webhook.ShWebhookEventType;
import com.viglet.shio.url.ShURLFormatter;

/**
 * SH1085 — a casing call with no {@code Locale} depends on where the JVM runs.
 *
 * <p>{@code String.toLowerCase()} uses {@code Locale.getDefault()}, and Turkish maps {@code I} to the
 * dotless {@code ı}: {@code "PUBLISH"} lower-cases to {@code "publısh"}. That was the {@code event}
 * field of every signed webhook, the friendly URL a title mints, and the slug an export and an import
 * each derive. None of them is text for a person, so every one takes {@code Locale.ROOT}.</p>
 *
 * <p>The lint lands before the fixes (SH338). The task counted nine, and the scan found eleven.</p>
 */
@ShSourceScan(ShSourceScan.Reads.CODE)
class ShLocaleCasingLintTest {

    private static final Path MAIN = Path.of("src/main/java/com/viglet/shio");

    /** A casing call with nothing between the parentheses. */
    private static final Pattern BARE = Pattern.compile("\\.to(?:Lower|Upper)Case\\(\\)");

    @Test
    @DisplayName("SH1085: no casing call in shio-app leaves the locale to the JVM")
    void everyCasingNamesItsLocale() throws IOException {
        List<String> bare = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                String code = ShSourceText.code(Files.readString(file));
                Matcher call = BARE.matcher(code);
                while (call.find()) {
                    int line = 1 + (int) code.substring(0, call.start()).chars()
                            .filter(c -> c == '\n').count();
                    bare.add(MAIN.relativize(file).toString().replace('\\', '/') + ":" + line);
                }
            }
        }

        assertThat(bare)
                .as("""
                        SH1085: a casing call with no Locale reads Locale.getDefault(), so on a \
                        Turkish JVM `I` becomes the dotless `ı` and a wire value, a URL or a slug \
                        changes with where the instance runs. Pass Locale.ROOT; a string for a \
                        person to read would take the reader's locale, and none of these is one.""")
                .isEmpty();
    }

    @Test
    @DisplayName("SH1085: the reader is not vacuous — it sees a bare call and passes a rooted one")
    void theReaderActuallyReads() {
        assertThat(BARE.matcher("name().toLowerCase()").find()).isTrue();
        assertThat(BARE.matcher("name().toLowerCase(Locale.ROOT)").find()).isFalse();
    }

    @Test
    @DisplayName("SH1085: on a Turkish JVM the webhook event and a minted URL are what they are anywhere")
    void aTurkishDefaultLocaleChangesNothingOnTheWire() {
        Locale held = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            assertThat(ShWebhookEventType.PUBLISH.wire())
                    .as("SH1085: the create-shio-app receiver matches `publish` byte for byte, and a "
                            + "Turkish default made every publish webhook `publısh`, silently ignored")
                    .isEqualTo("publish");
            assertThat(ShURLFormatter.format("INDIGO ISLAND")).doesNotContain("ı");
        } finally {
            Locale.setDefault(held);
        }
    }
}
