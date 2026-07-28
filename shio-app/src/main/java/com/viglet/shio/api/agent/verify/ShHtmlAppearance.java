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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SH187 — what can honestly be said about a page's <em>appearance</em> without a browser.
 *
 * <h2>The gap, and the part of it this closes</h2>
 *
 * <p>SH101's digest reads structure, so an agent can prove the outline, the landmarks and the alt
 * text are right, and has no instrument for whether the page <em>looks</em> right. SH102 shipped
 * the correct instrument for a human — {@code shio snapshot}: a PNG, a baseline, a diff — scoped
 * deliberately "for the human handoff rather than for the agent". Visual autonomy needs the agent
 * to be the reader, which means a payload rather than an image.</p>
 *
 * <p><b>This is not a renderer and does not pretend to be.</b> Computed style, box geometry and
 * "does the hero overlap the nav" need a layout engine, and no amount of parsing substitutes for
 * one. What this extracts is the part of appearance that <em>is</em> in the document: which
 * stylistic hooks the markup uses, whether the stylesheet the page loads defines them, where style
 * is inlined instead of themed, and which images will reflow the page because they declare no
 * size. That is a small honest subset, and it happens to cover the failure an <em>agent</em>
 * actually creates: markup written against class names the theme does not have.</p>
 *
 * <h2>Why the undefined-class list is advisory and not a lint finding</h2>
 *
 * <p>Because a page may legitimately load a stylesheet this instance has never seen. The 2018
 * {@code openviglet/bootstrap-site} package is exactly that — its markup is full of Bootstrap
 * classes served from a CDN — so a rule that reported every class the {@code Theme} does not define
 * would fire dozens of times on the one real example in the repository. A rule with false positives
 * is one authors learn to ignore (SH192's finding, third application), so this reports the set and
 * says what it does not know. An agent comparing two runs of its own page gets the useful half
 * regardless: a class that was defined before its edit and is not now.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShHtmlAppearance {

    private static final Pattern CLASS_ATTR =
            Pattern.compile("(?is)\\bclass\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))");
    private static final Pattern STYLE_ATTR = Pattern.compile("(?is)\\bstyle\\s*=\\s*[\"']");
    private static final Pattern IMG = Pattern.compile("(?is)<img\\b([^>]*?)/?>");
    private static final Pattern WIDTH = Pattern.compile("(?is)\\bwidth\\s*=");
    private static final Pattern HEIGHT = Pattern.compile("(?is)\\bheight\\s*=");
    private static final Pattern LINK_STYLESHEET =
            Pattern.compile("(?is)<link\\b[^>]*\\brel\\s*=\\s*[\"']?stylesheet[\"']?[^>]*>");
    private static final Pattern HREF = Pattern.compile("(?is)\\bhref\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))");

    /**
     * Class selectors a stylesheet defines.
     *
     * <p>Deliberately crude: every {@code .name} outside a string, which over-collects (a
     * {@code .5em} in a shorthand is not a selector) and never under-collects. Over-collecting is
     * the safe direction here — it can only make the undefined-class list <em>shorter</em>, and a
     * list that claims a class is unstyled when it is not is the reading that wastes an agent's
     * turn.</p>
     */
    private static final Pattern CSS_CLASS = Pattern.compile("\\.(-?[_a-zA-Z][_a-zA-Z0-9-]*)");

    /**
     * A {@code <style>} element and its contents.
     *
     * <p>{@code <style(\s…)?>} rather than {@code <style\b…>} on purpose: in a Java string literal
     * {@code "\b"} is the <b>backspace character</b>, not the regex word boundary, so the pattern
     * silently matched nothing and every class read as unstyled — or rather as nothing at all,
     * since the comparison is skipped when no stylesheet is found. Caught by this class's own test.
     * The whitespace form says what was meant without an escape that changes meaning between the
     * language and the regex.</p>
     */
    private static final Pattern STYLE_BLOCK =
            Pattern.compile("(?is)<style(\\s[^>]*)?>(.*?)</style>");

    private ShHtmlAppearance() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * The page's appearance, reading the stylesheets the page itself carries.
     *
     * <p>SH183 inlines a {@code Theme}'s CSS into the layout ({@code <style>{{theme.css}}</style>}),
     * so the rendered document already holds the stylesheet it is served with. Reading it back out
     * of the HTML rather than fetching the {@code Theme} post is the more faithful answer: it is
     * whatever the page actually carries, including a second {@code <style>} block a layout added
     * by hand, and it needs no second read.</p>
     */
    public static Appearance of(String html) {
        return of(html, styleBlocksIn(html));
    }

    private static String styleBlocksIn(String html) {
        if (html == null) {
            return "";
        }
        StringBuilder css = new StringBuilder();
        Matcher matcher = STYLE_BLOCK.matcher(html);
        while (matcher.find()) {
            // Separated, so a selector at the end of one block and one at the start of the next
            // cannot be read as a single name.
            css.append(matcher.group(2)).append(System.lineSeparator());
        }
        return css.toString();
    }

    /**
     * @param html the rendered page (SH249 — the artefact a visitor gets)
     * @param css  the stylesheets the page is served with, concatenated; may be {@code null}
     */
    public static Appearance of(String html, String css) {
        String page = html == null ? "" : html;
        Set<String> used = classesIn(page);
        Set<String> defined = definedIn(css);
        // Only when there is a stylesheet to compare against: with none, *every* class would be
        // "undefined", which is a true statement that tells the reader nothing.
        Set<String> unstyled = new TreeSet<>();
        if (!defined.isEmpty()) {
            for (String name : used) {
                if (!defined.contains(name)) {
                    unstyled.add(name);
                }
            }
        }
        return new Appearance(
                List.copyOf(used),
                List.copyOf(unstyled),
                count(STYLE_ATTR, page),
                imagesWithoutDimensions(page),
                externalStylesheets(page),
                defined.size());
    }

    /** Every class name the markup uses, sorted — the order is observable, so it is total. */
    private static Set<String> classesIn(String html) {
        Set<String> names = new TreeSet<>();
        Matcher matcher = CLASS_ATTR.matcher(html);
        while (matcher.find()) {
            String value = firstNonNull(matcher.group(2), matcher.group(3), matcher.group(4));
            for (String name : value.trim().split("\\s+")) {
                if (!name.isBlank()) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    private static Set<String> definedIn(String css) {
        Set<String> names = new LinkedHashSet<>();
        if (css == null || css.isBlank()) {
            return names;
        }
        Matcher matcher = CSS_CLASS.matcher(css);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    /**
     * Images with no intrinsic size declared.
     *
     * <p>One of the few appearance defects a parser can be certain about, and the one whose
     * symptom a human describes as "the page jumps" — the image loads, the layout reflows, and
     * nothing about the markup looks wrong.</p>
     */
    private static List<String> imagesWithoutDimensions(String html) {
        List<String> out = new ArrayList<>();
        Matcher matcher = IMG.matcher(html);
        while (matcher.find()) {
            String attrs = matcher.group(1);
            if (!WIDTH.matcher(attrs).find() || !HEIGHT.matcher(attrs).find()) {
                Matcher src = Pattern.compile("(?is)\\bsrc\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))")
                        .matcher(attrs);
                out.add(src.find()
                        ? firstNonNull(src.group(2), src.group(3), src.group(4))
                        : "(no src)");
            }
        }
        return List.copyOf(out);
    }

    /**
     * Stylesheets the page loads from somewhere this instance does not hold.
     *
     * <p>Present so the undefined-class list can be <em>read correctly</em> rather than as a defect
     * list: a page pulling Bootstrap from a CDN legitimately uses hundreds of classes no
     * {@code Theme} defines. Naming the sheets is what turns a confusing number into an
     * explanation.</p>
     */
    private static List<String> externalStylesheets(String html) {
        Set<String> out = new TreeSet<>();
        Matcher matcher = LINK_STYLESHEET.matcher(html);
        while (matcher.find()) {
            Matcher href = HREF.matcher(matcher.group());
            if (href.find()) {
                out.add(firstNonNull(href.group(2), href.group(3), href.group(4)));
            }
        }
        return List.copyOf(out);
    }

    private static int count(Pattern pattern, String text) {
        int total = 0;
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            total++;
        }
        return total;
    }

    private static String firstNonNull(String... values) {
        for (String value : values) {
            if (value != null) {
                return value;
            }
        }
        return "";
    }

    /**
     * A page's appearance surface.
     *
     * @param classes            every class the markup uses, sorted
     * @param unstyledClasses    those no stylesheet this instance holds defines — <b>advisory</b>,
     *                           empty when there is no stylesheet to compare against
     * @param inlineStyles       {@code style="…"} attributes, which are styling the theme cannot
     *                           reach and a curator cannot review
     * @param imagesWithoutSize  images declaring no width/height, so the page reflows on load
     * @param externalStylesheets sheets loaded from elsewhere — why `unstyledClasses` may be long
     * @param definedClasses     how many class selectors the page's own stylesheets define
     */
    public record Appearance(List<String> classes, List<String> unstyledClasses, int inlineStyles,
                             List<String> imagesWithoutSize, List<String> externalStylesheets,
                             int definedClasses) {
    }
}
