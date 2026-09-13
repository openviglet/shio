/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.staticfile;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.viglet.shio.lint.ShSourceScan;
import com.viglet.shio.lint.ShSourceText;

/**
 * SH950 — an uploaded SVG or HTML file is not a document this origin renders.
 *
 * <p>Every asset route sent {@code Content-Disposition: inline} and nothing in the backend sent
 * {@code X-Content-Type-Options} or a {@code Content-Security-Policy}. The content type is probed
 * from the file name, so {@code evil.svg} came back as {@code image/svg+xml} — a scriptable
 * document, on the console's own origin, uploaded through an endpoint that accepts an agent token.
 * Stored XSS against the curator's session, needing no other defect to exploit.</p>
 *
 * <p><b>The HTTP boundary is asserted in {@code ShStaticFileAPITest}</b> (its {@code stage02d/e/f}
 * methods): a real upload, a real GET, and the headers that come back. It goes there rather than
 * here because that class already owns an isolated context with a temp file-source root, and a new
 * {@code @SpringBootTest} would have spent the last slot SH423's headroom budget has.</p>
 *
 * <p>What is here is everything that needs no instance: the filter covering the one route with no
 * {@code ResponseEntity}, the URL pattern that keeps it off a page render, the content types
 * {@code nosniff} would otherwise break, and a grep so the <b>next</b> asset route cannot be
 * written without any of it.</p>
 */
@ShSourceScan(ShSourceScan.Reads.CODE)
class ShAssetSecurityHeadersTest {

    /**
     * The {@code permitAll} route, which a MockMvc GET cannot reach.
     *
     * <p>{@code /file_source/**} is served by a Spring resource handler, so it builds no
     * {@code ResponseEntity} and is covered by a filter instead — and {@code webAppContextSetup}
     * does not run registered servlet filters, so an HTTP-level test would pass over it in
     * silence. Driven directly for that reason.</p>
     */
    @Test
    void sh950TheFileSourceFilterHardensThePublicRoute() throws Exception {
        ShAssetSecurityHeadersFilter filter = new ShAssetSecurityHeadersFilter();

        MockHttpServletResponse svg = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/file_source/" + UUID.randomUUID() + "/evil.svg"),
                svg, new MockFilterChain());

        assertThat(svg.getHeader(ShAssetResponseHeaders.X_CONTENT_TYPE_OPTIONS)).isEqualTo("nosniff");
        assertThat(svg.getHeader(ShAssetResponseHeaders.CONTENT_SECURITY_POLICY))
                .isEqualTo(ShAssetResponseHeaders.ASSET_CSP);
        assertThat(svg.getHeader("Content-Disposition"))
                .as("SH950: /file_source/** is the permitAll route — an SVG served inline there is a "
                        + "public, same-origin, scriptable document")
                .startsWith("attachment");

        MockHttpServletResponse png = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/file_source/" + UUID.randomUUID() + "/ok.png"),
                png, new MockFilterChain());
        assertThat(png.getHeader("Content-Disposition"))
                .as("SH950: an image on the public route still renders — a delivered site's <img> "
                        + "points here, and a fix that breaks that gets reverted")
                .startsWith("inline");
    }

    /**
     * The URL pattern is the whole safety of the filter: a delivered page is {@code text/html} by
     * design, and an {@code attachment} disposition on {@code /sites/**} would take every
     * replicated site down.
     */
    @Test
    void sh950TheFilterCannotReachAPageRender() {
        assertThat(new ShAssetSecurityConfig().shAssetSecurityHeadersFilter().getUrlPatterns())
                .as("SH950: widening this past the byte route would put `attachment` on rendered pages")
                .isEqualTo(Set.of("/file_source/*"));
    }

    /**
     * {@code nosniff} is what makes a wrong content type fatal instead of survivable, so the types
     * the JDK's own table does not know are part of this fix rather than housekeeping. A
     * {@code .mjs} answered as {@code text/plain} is a module the browser refuses to execute —
     * which is a replicated site off the air, the class of breakage SH555 and SH556 each cost a
     * round.
     */
    @Test
    void sh950TheTypesNosniffWouldOtherwiseBreakAreNamed() {
        assertThat(ShAssetResponseHeaders.contentTypeByName("app.mjs"))
                .as("SH950: an ES module served as text/plain is refused outright under nosniff")
                .isEqualTo("text/javascript");
        assertThat(ShAssetResponseHeaders.contentTypeByName("Inter.woff2")).isEqualTo("font/woff2");
        assertThat(ShAssetResponseHeaders.contentTypeByName("hero.avif")).isEqualTo("image/avif");

        // The control: the JDK already answers for these, and this table must only ever add.
        assertThat(ShAssetResponseHeaders.contentTypeByName("site.css")).isEqualTo("text/css");
        assertThat(ShAssetResponseHeaders.contentTypeByName("logo.svg")).isEqualTo("image/svg+xml");
        assertThat(ShAssetResponseHeaders.contentTypeByName("no-extension")).isNull();
    }

    /** The rule itself, stated once: an SVG is a document and a PNG is not. */
    @Test
    void sh950OnlyRasterImagesAreHandedToTheBrowserToRender() {
        assertThat(ShAssetResponseHeaders.disposition("image/png", "ok.png"))
                .isEqualTo("inline; filename=\"ok.png\"");
        assertThat(ShAssetResponseHeaders.disposition("image/jpeg", "photo.jpg")).startsWith("inline");

        for (String scriptable : List.of("image/svg+xml", "text/html", "application/xhtml+xml",
                "application/pdf", "text/xml", "application/octet-stream")) {
            assertThat(ShAssetResponseHeaders.disposition(scriptable, "x"))
                    .as("SH950: %s is a document, not a picture", scriptable)
                    .startsWith("attachment");
        }

        // A charset parameter must not defeat the comparison — `text/html; charset=utf-8` is the
        // shape a probe actually returns, and a naive equals() on the raw value would let it through.
        assertThat(ShAssetResponseHeaders.rendersInline("image/png; charset=binary")).isTrue();
        assertThat(ShAssetResponseHeaders.rendersInline("text/html; charset=utf-8")).isFalse();
        assertThat(ShAssetResponseHeaders.rendersInline(null)).isFalse();
    }

    /**
     * SH322 — the grep is the deliverable. The design named two routes carrying this defect and
     * there were three: {@code /file_source/**} is a Spring resource handler with no
     * {@code ResponseEntity} at all, and it is the {@code permitAll} one. So the rule is asserted
     * over the tree rather than over the three call sites that happen to exist today.
     */
    @Test
    void sh950NoRouteWritesItsOwnContentDisposition() throws Exception {
        Path main = Path.of("src", "main", "java");
        assertThat(Files.isDirectory(main)).as("SH950: expected to run from the shio-app module").isTrue();

        List<String> offenders = new ArrayList<>();
        int scanned = 0;
        try (Stream<Path> tree = Files.walk(main)) {
            for (Path java : tree.filter(p -> p.toString().endsWith(".java")).toList()) {
                scanned++;
                // The one file allowed to name the header is the one that owns the rule.
                if (java.getFileName().toString().equals("ShAssetResponseHeaders.java")) {
                    continue;
                }
                // Comments stripped: a javadoc explaining this rule must not read as a breach of it,
                // which is SH206's defect and the reason ShSourceText exists.
                String source = ShSourceText.code(Files.readString(java));
                if (source.contains("\"inline; filename=") || source.contains("\"attachment; filename=")) {
                    offenders.add(main.relativize(java).toString());
                }
            }
        }

        // 499 when this shipped. A floor rather than the number, so ordinary growth does not fail
        // the build — but a walk that stopped finding the tree would pass this test silently, and
        // that is the failure mode of every lint of this shape (SH322).
        assertThat(scanned)
                .as("SH950: a walk that finds nothing passes for the wrong reason")
                .isGreaterThan(400);
        assertThat(offenders)
                .as("SH950: a route builds its own Content-Disposition instead of calling "
                        + "ShAssetResponseHeaders.harden(...), so it decides on its own whether "
                        + "uploaded bytes are a renderable document")
                .isEmpty();
    }
}
