/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SH1411 — the renderer's fallback not-found page reads as a page of the site, in the reader's
 * language.
 *
 * <p>It was bare HTML in the browser's default serif, always {@code lang="en"} and "Page not found",
 * even under a {@code pt-br} address. These hold the three things the fix promised: the reader's
 * language, a stylesheet inlined in the page, and the site's name above the message.</p>
 */
class ShErrorPageTest {

    @Test
    @DisplayName("SH1411: a pt-br reader is answered in Portuguese, with lang=\"pt-BR\"")
    void portuguese() {
        String html = ShErrorPage.notFound("/sites/quilhara/default/pt-br/", "Quilhara", "pt-br");
        assertThat(html).contains("<html lang=\"pt-BR\">")
                .contains("<h1>P&aacute;gina n&atilde;o encontrada</h1>")
                .contains("Ir para a p&aacute;gina inicial")
                .doesNotContain("Page not found");
    }

    @Test
    @DisplayName("SH1411: a language with no strings falls back to English, and says so in lang")
    void unknownLanguageIsEnglish() {
        String html = ShErrorPage.notFound("/sites/acme/default/de-de/", "Acme", "de-de");
        assertThat(html).contains("<html lang=\"en\">").contains("<h1>Page not found</h1>");
    }

    @Test
    @DisplayName("SH1411: the JVM's own locale never chooses the language for the reader")
    void jvmLocaleDoesNotLeak() {
        Locale before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("pt-BR"));
            assertThat(ShErrorPage.notFound("/sites/acme/", "Acme", null))
                    .contains("<html lang=\"en\">").contains("<h1>Page not found</h1>");
        } finally {
            Locale.setDefault(before);
        }
    }

    @Test
    @DisplayName("SH1411: the page carries its own stylesheet, light and dark, and the site's name")
    void styledAndNamed() {
        String html = ShErrorPage.notFound("/sites/acme/", "Acme & Co", "en-us");
        assertThat(html).contains("<style>").contains("prefers-color-scheme:dark")
                .doesNotContain("<link").doesNotContain("<script")
                .contains("<meta name=\"robots\" content=\"noindex\">")
                .contains("<p class=\"site\">Acme &amp; Co</p>")
                .contains("<a class=\"home\" href=\"/sites/acme/\">");
    }
}
