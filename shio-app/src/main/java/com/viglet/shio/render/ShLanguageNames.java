/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import java.util.Locale;
import java.util.Map;

/**
 * SH1270 — a language's name in itself, for the label a language switcher shows.
 *
 * <p>SH383 labelled a {@code {{#translations}}} row with the sibling's title, which is in the
 * target language by construction and reads as a headline where a visitor scans for one word:
 * Quilhara's switcher said "Trilhas de costão e de serra". A visitor looking for their language
 * looks for "Português".</p>
 *
 * <h2>A table the product owns, not the JDK's display names</h2>
 *
 * <p>{@code Locale#getDisplayLanguage(Locale)} would cover every language and change with the JDK:
 * CLDR revises names and capitalisation between releases, and a row value that moves on an upgrade
 * moves SH101's render digest on every page that carries a switcher, for a change nobody made. So
 * the names are written here, once, keyed by the primary language subtag, and a tag this table does
 * not know yields its own subtag upper-cased, which is visibly a code rather than a wrong name.</p>
 */
public final class ShLanguageNames {

    /** Primary subtag → the language's own name, written the way its speakers write it. */
    private static final Map<String, String> AUTONYMS = Map.ofEntries(
            Map.entry("ar", "العربية"),
            Map.entry("ca", "Català"),
            Map.entry("cs", "Čeština"),
            Map.entry("da", "Dansk"),
            Map.entry("de", "Deutsch"),
            Map.entry("el", "Ελληνικά"),
            Map.entry("en", "English"),
            Map.entry("es", "Español"),
            Map.entry("fi", "Suomi"),
            Map.entry("fr", "Français"),
            Map.entry("he", "עברית"),
            Map.entry("hi", "हिन्दी"),
            Map.entry("hu", "Magyar"),
            Map.entry("id", "Bahasa Indonesia"),
            Map.entry("it", "Italiano"),
            Map.entry("ja", "日本語"),
            Map.entry("ko", "한국어"),
            Map.entry("nb", "Norsk bokmål"),
            Map.entry("nl", "Nederlands"),
            Map.entry("pl", "Polski"),
            Map.entry("pt", "Português"),
            Map.entry("ro", "Română"),
            Map.entry("ru", "Русский"),
            Map.entry("sv", "Svenska"),
            Map.entry("th", "ไทย"),
            Map.entry("tr", "Türkçe"),
            Map.entry("uk", "Українська"),
            Map.entry("vi", "Tiếng Việt"),
            Map.entry("zh", "中文"));

    private ShLanguageNames() {
        // utility
    }

    /**
     * The name for a locale tag in any of the spellings this product stores ({@code pt-br},
     * {@code pt_BR}, {@code pt}).
     *
     * @return the autonym, the primary subtag upper-cased when the table does not know it, or
     *         {@code null} for a blank tag
     */
    public static String of(String locale) {
        if (locale == null || locale.isBlank()) {
            return null;
        }
        String primary = locale.trim().split("[-_]", 2)[0].toLowerCase(Locale.ROOT);
        return AUTONYMS.getOrDefault(primary, primary.toUpperCase(Locale.ROOT));
    }
}
