/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.staticfile;

import java.net.URLConnection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import jakarta.servlet.http.HttpServletResponse;

/**
 * The one rule for how uploaded bytes are served (SH950).
 *
 * <p><b>What was wrong.</b> All three asset routes sent {@code Content-Disposition: inline} and no
 * response anywhere in the backend carried {@code X-Content-Type-Options} or a
 * {@code Content-Security-Policy}. The content type is probed from the file name first, so
 * {@code evil.svg} was served as {@code image/svg+xml} and {@code payload.html} as
 * {@code text/html} — as a document, from the same origin the console runs on, by a route the
 * upload endpoint accepts an agent token for. That is stored XSS against the console session, and
 * it needed no defect anywhere else to exploit.</p>
 *
 * <p>Three headers, applied together because each covers what the others do not:</p>
 *
 * <ul>
 *   <li>{@code X-Content-Type-Options: nosniff} — a browser may not upgrade a byte stream to a
 *       document type the server did not declare.</li>
 *   <li>{@code Content-Disposition: attachment} for anything outside {@link #RENDER_SAFE}, a short
 *       list of raster image types. A raster image is not a document and cannot carry script; an
 *       SVG, an HTML file and a PDF all can. <b>This does not affect the delivered site</b>: a
 *       browser honours the disposition on a navigation and ignores it for a subresource, so
 *       {@code <img>}, {@code <link rel=stylesheet>} and {@code <script src>} load exactly as
 *       before.</li>
 *   <li>{@code Content-Security-Policy: default-src 'none'; sandbox} — the backstop for the case
 *       the other two do not reach, an asset opened directly as a top-level document. The sandbox
 *       puts it in an opaque origin with scripting off, so the SVG runs nothing even if a future
 *       route forgets the disposition. On a subresource the header is inert, because CSP is
 *       enforced on the document that embeds a response and never on the response itself.</li>
 * </ul>
 *
 * <p><b>{@link #BY_EXTENSION} is part of the fix, not housekeeping.</b> {@code nosniff} is what
 * makes a wrong content type fatal rather than survivable: a browser refuses a stylesheet that is
 * not {@code text/css} and a module script that is not a JavaScript type, where before it would
 * sniff and carry on. Measured on this JDK, {@code guessContentTypeFromName} answers correctly for
 * {@code .css} and {@code .js} — and returns {@code null} for {@code .mjs} (probed as
 * {@code text/plain}), {@code .woff2} and {@code .avif}. So turning on {@code nosniff} without this
 * table would take a replicated site's ES modules off the air, which is the same class of
 * site-wide breakage SH555 and SH556 cost a round each.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShAssetResponseHeaders {

    public static final String X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    public static final String NOSNIFF = "nosniff";
    public static final String CONTENT_SECURITY_POLICY = "Content-Security-Policy";

    /** Opaque origin, no scripting, no loads of its own. The value an asset is worth serving with. */
    public static final String ASSET_CSP = "default-src 'none'; sandbox";

    /** What a type nobody could name is served as, and never a document type. */
    public static final String FALLBACK_CONTENT_TYPE = "application/octet-stream";

    /**
     * The types that may still be rendered inline: raster images, which are not documents.
     *
     * <p>{@code image/svg+xml} is deliberately absent and is the whole point — an SVG is a document
     * that can carry {@code <script>}, and it is the one image type a reader assumes is safe
     * because the rest of its family is.</p>
     */
    private static final Set<String> RENDER_SAFE = Set.of(
            "image/png",
            "image/jpeg",
            "image/gif",
            "image/webp",
            "image/avif",
            "image/bmp",
            "image/x-icon",
            "image/vnd.microsoft.icon");

    /** The extensions this JDK's own table does not know. See the class javadoc for why it matters. */
    private static final Map<String, String> BY_EXTENSION = Map.of(
            "mjs", "text/javascript",
            "avif", "image/avif",
            "woff", "font/woff",
            "woff2", "font/woff2",
            "ico", "image/x-icon");

    private ShAssetResponseHeaders() {
    }

    /**
     * The content type a file name implies, or {@code null} where nothing here knows.
     *
     * <p>The JDK's table first, so this only ever adds. Callers fall back to probing the bytes.</p>
     */
    public static String contentTypeByName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        String byName = URLConnection.guessContentTypeFromName(fileName);
        if (byName != null) {
            return byName;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return null;
        }
        return BY_EXTENSION.get(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    /** Whether these bytes may be handed to the browser as something to display. */
    public static boolean rendersInline(String contentType) {
        if (contentType == null) {
            return false;
        }
        // Drop any `; charset=…` and compare on the bare type.
        int semicolon = contentType.indexOf(';');
        String bare = (semicolon < 0 ? contentType : contentType.substring(0, semicolon)).trim();
        return RENDER_SAFE.contains(bare.toLowerCase(Locale.ROOT));
    }

    /** The disposition these bytes get: {@code inline} only for a render-safe raster image. */
    public static String disposition(String contentType, String fileName) {
        String how = rendersInline(contentType) ? "inline" : "attachment";
        return fileName == null || fileName.isBlank()
                ? how
                : how + "; filename=\"" + fileName + "\"";
    }

    /**
     * Put the three headers on a response built by a controller.
     *
     * @param contentType the type the body is being sent as, which decides the disposition
     * @param fileName    the download name, or {@code null} for none
     */
    public static ResponseEntity.BodyBuilder harden(ResponseEntity.BodyBuilder builder,
                                                    String contentType, String fileName) {
        return builder
                .header(X_CONTENT_TYPE_OPTIONS, NOSNIFF)
                .header(CONTENT_SECURITY_POLICY, ASSET_CSP)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition(contentType, fileName));
    }

    /**
     * The same three headers on a raw servlet response, for the route that builds no
     * {@link ResponseEntity} — {@code /file_source/**}, which a Spring resource handler serves.
     */
    public static void harden(HttpServletResponse response, String contentType, String fileName) {
        response.setHeader(X_CONTENT_TYPE_OPTIONS, NOSNIFF);
        response.setHeader(CONTENT_SECURITY_POLICY, ASSET_CSP);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, disposition(contentType, fileName));
    }
}
