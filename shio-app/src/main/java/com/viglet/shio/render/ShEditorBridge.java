/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * SH185 — the Universal Editor's missing other half.
 *
 * <h2>What was wrong</h2>
 *
 * <p>{@code visual-editor.page.tsx} has loaded a site in an iframe, posted
 * {@code {type, data}} into it and listened for replies since SH10. Its own comment said the
 * replies came from {@code shio-editor.js}. There was <b>no such file anywhere in this
 * repository</b> — not in {@code create-shio-app}, {@code shio-react-sdk},
 * {@code shio-sections} or {@code shio-client}, not one {@code postMessage} among them — so
 * the editor could not work against anything, including the first-party starter correctly
 * deployed. The console was one end of a conversation nobody was on the other side of.</p>
 *
 * <p>SH183 is what made fixing it the right thing to do now rather than earlier: specifying a
 * wire format with nothing on the other end is how the shell got here, and the renderer is a
 * first implementation to target.</p>
 *
 * <h2>Where the annotations come from</h2>
 *
 * <p>Nothing had to be invented. {@code sh-region="NAME"} has been in the content since 2018
 * and SH183 keeps it through the render as {@code data-shio-region} +
 * {@code data-shio-region-post}; SH185 adds the field level with {@code sh-field} /
 * {@code sh-post} → {@code data-shio-prop} / {@code data-shio-post}. The script reads only
 * those, so it works against <b>any</b> page that carries them — Shio's renderer today, a
 * Next.js front end that emits the same attributes tomorrow. That is the point of writing the
 * protocol down rather than coupling the editor to this renderer.</p>
 *
 * <h2>Injection, not bundling</h2>
 *
 * <p>The script is a static asset served by {@link ShRenderAPI} and injected into a page only
 * when the request asks for it. It is not part of the rendered page's own markup, so
 * {@code ShPageRenderService} keeps returning exactly what a front end would serve — which is
 * what lets SH101's digest and SH102's snapshot compare the page rather than the page plus an
 * editor, the same separation {@link ShPreviewBanner} keeps.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShEditorBridge {

    /** Where {@link ShRenderAPI} serves it, and therefore what the injected tag points at. */
    public static final String SCRIPT_PATH = "/preview/_shio/editor.js";

    private static final String ORIGIN_ATTR = "data-shio-editor-origin";

    private final String script;

    public ShEditorBridge() {
        // Read once at startup rather than per request: it is a build artifact, and a
        // failure to find it should be a boot failure rather than a page that silently
        // renders without the bridge it was asked for.
        try (var in = new ClassPathResource("render/shio-editor.js").getInputStream()) {
            this.script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("render/shio-editor.js is missing from the classpath", e);
        }
    }

    /** The script itself, for the route that serves it. */
    public String script() {
        return script;
    }

    /**
     * Adds the bridge's {@code <script>} to a rendered page.
     *
     * @param html         the page, banner and all
     * @param parentOrigin the origin allowed to talk to the bridge — passed <b>in</b>, rather
     *                     than guessed in the browser, because {@code document.referrer} is
     *                     empty on some navigations and {@code window.parent.location} throws
     *                     cross-origin. The server is the only party that knows who asked.
     */
    public String inject(String html, String parentOrigin) {
        Document document = Jsoup.parse(html);
        document.outputSettings().prettyPrint(false);
        Element tag = document.createElement("script");
        tag.attr("src", SCRIPT_PATH);
        tag.attr("defer", "defer");
        tag.attr(ORIGIN_ATTR, parentOrigin == null ? "" : parentOrigin);
        document.body().appendChild(tag);
        return document.html();
    }
}
