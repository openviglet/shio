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
import java.util.ResourceBundle;

import org.springframework.web.util.HtmlUtils;

/**
 * SH1411 — the page the renderer draws for a browser when the site has none of its own.
 *
 * <p>SH1391's fallback was bare HTML in the browser's default serif, always English, so a visitor
 * turned away from a page under {@code /sites/<site>/default/pt-br/} read "Page not found" on what
 * looked like a broken site. It is still deliberately plain (no theme, no script, never cached,
 * {@code noindex}), since it is the answer when the site's own page cannot be drawn, but it carries
 * a small inline stylesheet that follows the reader's light or dark setting, names the site, and
 * speaks the reader's language: the address's locale, else the site's declared default.</p>
 *
 * <p>The strings are in {@code i18n/error-page*.properties}, read with no fallback to the JVM's own
 * locale, so a server running in Portuguese still answers an English reader in English.</p>
 */
final class ShErrorPage {

    static final String BUNDLE = "i18n/error-page";

    /** Inlined, never linked: the page must not ask the server for anything else. */
    static final String STYLE = """
            :root{color-scheme:light dark;--bg:#fafafa;--fg:#1f2328;--muted:#59636e;--accent:#5b3fd6;--on-accent:#fff}
            @media (prefers-color-scheme:dark){:root{--bg:#16181d;--fg:#e8e6e3;--muted:#a3a8b0;--accent:#a99bf2;--on-accent:#16181d}}
            *{box-sizing:border-box}
            body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;\
            background:var(--bg);color:var(--fg);font:16px/1.5 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif}
            main{max-width:32rem;padding:2rem 1.5rem;text-align:center}
            .site{margin:0 0 1rem;color:var(--muted);font-size:.875rem;letter-spacing:.04em;text-transform:uppercase}
            h1{margin:0 0 .5rem;font-size:1.75rem;line-height:1.2}
            p{margin:0 0 1.5rem;color:var(--muted)}
            a.home{display:inline-block;padding:.625rem 1.25rem;border-radius:.5rem;background:var(--accent);\
            color:var(--on-accent);text-decoration:none;font-weight:600}
            a.home:focus-visible{outline:2px solid var(--accent);outline-offset:3px}
            """;

    private ShErrorPage() {
    }

    /**
     * @param home       the site's home, as the reader's address space spells it
     * @param siteName   the site's display name, or null when the key resolved to no site
     * @param localeTag  the language to answer in (a BCP 47 tag such as {@code pt-br}), or null
     */
    static String notFound(String home, String siteName, String localeTag) {
        Locale wanted = localeTag == null || localeTag.isBlank() ? Locale.ENGLISH
                : Locale.forLanguageTag(localeTag.replace('_', '-'));
        ResourceBundle messages = bundle(wanted);
        // The page says the language it is written in: the requested tag when its language has a
        // bundle, and English when it fell back to the base one.
        String lang = messages.getLocale().getLanguage().isEmpty() ? "en" : wanted.toLanguageTag();
        String title = HtmlUtils.htmlEscape(messages.getString("notFound.title"));
        String site = siteName == null || siteName.isBlank() ? ""
                : "<p class=\"site\">" + HtmlUtils.htmlEscape(siteName) + "</p>\n";
        return "<!doctype html>\n<html lang=\"" + HtmlUtils.htmlEscape(lang) + "\">\n<head>\n"
                + "<meta charset=\"utf-8\">\n"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
                + "<meta name=\"robots\" content=\"noindex\">\n<title>" + title + "</title>\n"
                + "<style>\n" + STYLE + "</style>\n</head>\n<body>\n<main>\n" + site
                + "<h1>" + title + "</h1>\n"
                + "<p>" + HtmlUtils.htmlEscape(messages.getString("notFound.body")) + "</p>\n"
                + "<a class=\"home\" href=\"" + HtmlUtils.htmlEscape(home) + "\">"
                + HtmlUtils.htmlEscape(messages.getString("notFound.home")) + "</a>\n"
                + "</main>\n</body>\n</html>\n";
    }

    /**
     * A language with no strings of its own resolves to the base bundle, the English one: the base
     * is always among the candidates, and only the JVM's default locale is kept out of them.
     */
    private static ResourceBundle bundle(Locale locale) {
        return ResourceBundle.getBundle(BUNDLE, locale, ShErrorPage.class.getClassLoader(),
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
    }
}
