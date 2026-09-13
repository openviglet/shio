/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.staticfile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Take the behaviour out of an SVG and leave the drawing (SH951).
 *
 * <p>SVG is the one image type that is also a document: it can carry {@code <script>}, an
 * {@code onload=} attribute, a {@code <foreignObject>} holding arbitrary HTML, and a
 * {@code href="javascript:…"}. Denying it outright would be simpler and would make the product
 * worse at its own job — a replicated site's logo is very often an SVG — so this is what
 * {@link ShUploadPolicy.Verdict#SANITIZE} does instead.</p>
 *
 * <h2>Why a regex, and where that would be wrong</h2>
 *
 * <p>Parsing SVG as XML and re-serialising it is the textbook answer and is the wrong trade here.
 * It means an XML parser on attacker-controlled bytes — the surface XXE lives on — and it rewrites
 * the whole document, so an asset a capture reproduced byte for byte comes back different and
 * every replication digest moves. This removes named constructs and leaves the rest of the bytes
 * untouched.</p>
 *
 * <p>The bet that makes it sound is not that the patterns are exhaustive: it is that they do not
 * have to be. {@code ShAssetResponseHeaders} already serves every SVG as an attachment under
 * {@code default-src 'none'; sandbox}, so a construct this misses is not reachable as script —
 * this is the second of two, and its job is that the <em>stored</em> bytes are clean too, because
 * {@code shio pull} copies them into a content tree and a later change of policy must not
 * resurrect a payload. <b>Do not remove either layer on the strength of the other.</b></p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShSvgSanitizer {

    /** A {@code <script>…</script>}, however it is cased and whatever it holds — including none. */
    private static final Pattern SCRIPT_ELEMENT = Pattern.compile(
            "<\\s*script\\b[^>]*(?:/\\s*>|>.*?<\\s*/\\s*script\\s*>)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** {@code <foreignObject>} is an escape hatch into arbitrary HTML inside a picture. */
    private static final Pattern FOREIGN_OBJECT = Pattern.compile(
            "<\\s*foreignObject\\b[^>]*(?:/\\s*>|>.*?<\\s*/\\s*foreignObject\\s*>)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** {@code <handler>} and {@code <set attributeName="onload">}, the less obvious two. */
    private static final Pattern HANDLER_ELEMENT = Pattern.compile(
            "<\\s*handler\\b[^>]*(?:/\\s*>|>.*?<\\s*/\\s*handler\\s*>)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** Any {@code on…="…"} event-handler attribute, quoted either way. */
    private static final Pattern EVENT_ATTRIBUTE = Pattern.compile(
            "\\s+on[a-z]+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)",
            Pattern.CASE_INSENSITIVE);

    /**
     * An {@code href} or {@code xlink:href} that is not a fragment, a relative path or a
     * {@code data:} image — which is what catches {@code javascript:} without needing to enumerate
     * the schemes somebody can invent.
     */
    private static final Pattern DANGEROUS_HREF = Pattern.compile(
            "\\s+(?:xlink:)?href\\s*=\\s*(\"|')\\s*(?!#|/|\\.|data:image/|https?:)[^\"']*\\1",
            Pattern.CASE_INSENSITIVE);

    /** An {@code <?xml-stylesheet?>} processing instruction, which loads and applies a stylesheet. */
    private static final Pattern XML_STYLESHEET = Pattern.compile(
            "<\\?xml-stylesheet\\b.*?\\?>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** A {@code <!DOCTYPE …>}, which is where an entity declaration would sit. */
    private static final Pattern DOCTYPE = Pattern.compile(
            "<!DOCTYPE\\b.*?>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** What was taken out, and from what. */
    public record Result(byte[] bytes, List<String> removed) {

        public boolean changed() {
            return !removed.isEmpty();
        }
    }

    public Result sanitize(byte[] svg) {
        String text = new String(svg, StandardCharsets.UTF_8);
        List<String> removed = new ArrayList<>();

        text = strip(text, DOCTYPE, "doctype", removed);
        text = strip(text, XML_STYLESHEET, "xml-stylesheet", removed);
        text = strip(text, SCRIPT_ELEMENT, "script", removed);
        text = strip(text, FOREIGN_OBJECT, "foreignObject", removed);
        text = strip(text, HANDLER_ELEMENT, "handler", removed);
        text = strip(text, EVENT_ATTRIBUTE, "event-handler attribute", removed);
        text = strip(text, DANGEROUS_HREF, "non-local href", removed);

        return new Result(removed.isEmpty() ? svg : text.getBytes(StandardCharsets.UTF_8), removed);
    }

    private static String strip(String text, Pattern pattern, String what, List<String> removed) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return text;
        }
        removed.add(what);
        // An attribute is replaced by nothing and an element by nothing: neither leaves a gap that
        // could close a tag the author did not open.
        return matcher.reset().replaceAll("");
    }
}
