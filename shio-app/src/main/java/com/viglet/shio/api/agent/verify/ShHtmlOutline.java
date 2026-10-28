/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.verify;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.viglet.shio.utils.ShStableMap;

/**
 * The structural scan behind SH101's render digest and SH100's link check.
 *
 * <h2>Why a scanner and not an HTML parser</h2>
 *
 * <p>A digest is not a DOM. What SH101 promises is a <em>structural</em> summary —
 * heading outline, landmark inventory, links and images with their alt text, word
 * counts — and every one of those is a flat extraction from the byte stream. Adding a
 * parser dependency (and a second HTML dialect to keep in step with the console's
 * contentEditable output) buys tree fidelity that nothing here consumes.</p>
 *
 * <p>The trade is real and bounded: badly nested tags produce a slightly wrong
 * outline, and a {@code <script>} body would be counted as words. Both are acceptable
 * for a summary whose job is to answer "is the hero missing?" — and neither can be
 * wrong in a way that <em>invents</em> structure, which is the failure mode that would
 * matter. What it must never do is be non-deterministic: everything below is emitted
 * in document order (landmarks in name order), because the digest hash is what lets an
 * agent compare two runs.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShHtmlOutline {

    /** Elements that count as landmarks — the page's skeleton, not its decoration. */
    private static final List<String> LANDMARKS = List.of(
            "header", "nav", "main", "section", "article", "aside", "footer", "form", "table");

    private static final Pattern HEADING =
            Pattern.compile("(?is)<h([1-6])\\b[^>]*>(.*?)</h\\1\\s*>");
    private static final Pattern ANCHOR =
            Pattern.compile("(?is)<a\\b([^>]*)>(.*?)</a\\s*>");
    private static final Pattern IMAGE = Pattern.compile("(?is)<img\\b([^>]*?)/?>");
    private static final Pattern TAG = Pattern.compile("(?s)<[^>]*>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern STRIPPED_BLOCK =
            Pattern.compile("(?is)<(script|style)\\b[^>]*>.*?</\\1\\s*>");

    private ShHtmlOutline() {
        // utility
    }

    /**
     * Scans one HTML fragment or document.
     *
     * @param html raw markup; {@code null} or blank yields an empty outline
     */
    public static Outline of(String html) {
        if (html == null || html.isBlank()) {
            return new Outline(List.of(), List.of(), List.of(), Map.of(), 0);
        }
        String source = STRIPPED_BLOCK.matcher(html).replaceAll(" ");
        return new Outline(headings(source), links(source), images(source),
                landmarks(source), words(source));
    }

    /** Value of an attribute on a tag's attribute string, or {@code null}. */
    static String attribute(String attributes, String name) {
        Matcher matcher = Pattern.compile("(?is)\\b" + Pattern.quote(name)
                + "\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))").matcher(attributes);
        if (!matcher.find()) {
            return null;
        }
        for (int group = 1; group <= 3; group++) {
            if (matcher.group(group) != null) {
                return decode(matcher.group(group).trim());
            }
        }
        return null;
    }

    /** Tags out, entities decoded, whitespace collapsed — the text a reader would see. */
    public static String text(String html) {
        if (html == null) {
            return "";
        }
        return WHITESPACE.matcher(decode(TAG.matcher(html).replaceAll(" "))).replaceAll(" ").trim();
    }

    private static List<Heading> headings(String html) {
        List<Heading> headings = new ArrayList<>();
        Matcher matcher = HEADING.matcher(html);
        while (matcher.find()) {
            headings.add(new Heading(Integer.parseInt(matcher.group(1)), text(matcher.group(2))));
        }
        return List.copyOf(headings);
    }

    private static List<Link> links(String html) {
        List<Link> links = new ArrayList<>();
        Matcher matcher = ANCHOR.matcher(html);
        while (matcher.find()) {
            String href = attribute(matcher.group(1), "href");
            if (href != null && !href.isBlank()) {
                links.add(new Link(href, text(matcher.group(2))));
            }
        }
        return List.copyOf(links);
    }

    private static List<Image> images(String html) {
        List<Image> images = new ArrayList<>();
        Matcher matcher = IMAGE.matcher(html);
        while (matcher.find()) {
            String src = attribute(matcher.group(1), "src");
            if (src != null && !src.isBlank()) {
                // A *missing* alt and an explicitly empty one are different facts: the
                // second is how a decorative image is declared, and reporting it as
                // missing would train the agent to "fix" correct markup.
                images.add(new Image(src, attribute(matcher.group(1), "alt")));
            }
        }
        return List.copyOf(images);
    }

    private static final Pattern MEDIA = Pattern.compile("(?is)<(img|source|video)\\b([^>]*?)/?>");

    /**
     * SH1266 — the media elements whose source attribute is present and empty, one description each.
     *
     * <p>An {@code <img src="">} is never what an author meant: the browser requests the page itself
     * again and draws a broken image. It is the signature of a template field that resolved to
     * nothing, and {@link #images} drops it (a blank src is not a reference), so nothing downstream
     * saw it. Present-and-empty only: an {@code <img>} with no {@code src} at all is how lazy loaders
     * ({@code data-src}) start, and an empty {@code src} beside a usable {@code srcset} still loads.</p>
     *
     * @return {@code img "<alt>"}, {@code source} or {@code video poster}, in document order
     */
    public static List<String> emptySources(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        List<String> empty = new ArrayList<>();
        Matcher matcher = MEDIA.matcher(STRIPPED_BLOCK.matcher(html).replaceAll(" "));
        while (matcher.find()) {
            String element = matcher.group(1).toLowerCase(java.util.Locale.ROOT);
            String attributes = matcher.group(2);
            String src = ownAttribute(attributes, "src");
            String srcset = ownAttribute(attributes, "srcset");
            boolean usableSrcset = srcset != null && !srcset.isBlank();
            switch (element) {
                case "img" -> {
                    if (src != null && src.isBlank() && !usableSrcset) {
                        String alt = ownAttribute(attributes, "alt");
                        empty.add(alt == null || alt.isBlank() ? "img" : "img \"" + alt + "\"");
                    }
                }
                case "source" -> {
                    if ((src != null || srcset != null) && (src == null || src.isBlank()) && !usableSrcset) {
                        empty.add("source");
                    }
                }
                default -> {
                    String poster = ownAttribute(attributes, "poster");
                    if (poster != null && poster.isBlank()) {
                        empty.add("video poster");
                    }
                }
            }
        }
        return List.copyOf(empty);
    }

    /**
     * As {@link #attribute}, but only the attribute itself: {@code \b} matches after a hyphen, so
     * {@code attribute(.., "src")} reads a lazy loader's {@code data-src}, which here would report a
     * working image as empty.
     */
    private static String ownAttribute(String attributes, String name) {
        Matcher matcher = Pattern.compile("(?is)(?:^|\\s)" + Pattern.quote(name)
                + "\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))").matcher(attributes);
        if (!matcher.find()) {
            return null;
        }
        for (int group = 1; group <= 3; group++) {
            if (matcher.group(group) != null) {
                return decode(matcher.group(group).trim());
            }
        }
        return null;
    }

    /** Landmark element → occurrences, name-sorted so the digest is stable. */
    private static Map<String, Integer> landmarks(String html) {
        Map<String, Integer> counts = new TreeMap<>();
        for (String element : LANDMARKS) {
            int found = 0;
            Matcher matcher = Pattern.compile("(?is)<" + element + "\\b[^>]*>").matcher(html);
            while (matcher.find()) {
                found++;
            }
            if (found > 0) {
                counts.put(element, found);
            }
        }
        // SH136: the sort above is load-bearing — digestOf() hashes this map in iteration
        // order, so Map.copyOf here made the same page hash differently after a restart.
        // Only for a *single*-field post, since merge() re-sorts, which is the kind of
        // half-broken determinism no same-JVM test can see.
        return ShStableMap.copyOf(counts);
    }

    private static int words(String html) {
        String text = text(html);
        return text.isEmpty() ? 0 : text.split("\\s+").length;
    }

    /**
     * The handful of entities that actually appear in CMS rich text. Deliberately not a
     * full entity table: an unrecognised entity is left verbatim, which is visible and
     * harmless in a digest, whereas a half-applied table would silently corrupt text.
     */
    private static String decode(String text) {
        return text.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'");
    }

    /**
     * One scanned fragment.
     *
     * @param headings  in document order, so a missing {@code h1} or a skipped level is
     *                  visible without a second call
     * @param links     every {@code href}, in document order
     * @param images    every {@code src} with its alt text ({@code null} = no alt
     *                  attribute at all)
     * @param landmarks landmark element → count, name-sorted
     * @param words     words of visible text
     */
    public record Outline(List<Heading> headings, List<Link> links, List<Image> images,
                          Map<String, Integer> landmarks, int words) {

        /** Merges two outlines, preserving order — one per rich-text field of a post. */
        public Outline merge(Outline other) {
            Map<String, Integer> merged = new LinkedHashMap<>(landmarks);
            other.landmarks.forEach((name, count) -> merged.merge(name, count, Integer::sum));
            return new Outline(concat(headings, other.headings), concat(links, other.links),
                    concat(images, other.images), new TreeMap<>(merged), words + other.words);
        }

        private static <T> List<T> concat(List<T> first, List<T> second) {
            List<T> all = new ArrayList<>(first.size() + second.size());
            all.addAll(first);
            all.addAll(second);
            return List.copyOf(all);
        }
    }

    /** @param level 1–6 */
    public record Heading(int level, String text) {
    }

    public record Link(String href, String text) {
    }

    /** @param alt {@code null} when the attribute is absent; {@code ""} when decorative */
    public record Image(String src, String alt) {
    }
}
