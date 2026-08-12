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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
        return String.join(System.lineSeparator(), styleSheetsIn(html));
    }

    /**
     * The page's own stylesheets, kept apart (SH694).
     *
     * <p>Concatenating them answers "is this class defined" and destroys "by which sheet", which is
     * the question a page presenting its chrome in one visual language and its sections in another
     * needs answered. Kept in document order, so sheet <i>n</i> is stable across two runs (P5).</p>
     *
     * <p>Measured before it was written, because the concern that had this filed as undesigned was
     * memory: on 1.02 MB of CSS across two sheets the per-sheet form holds <b>1,322 KB against the
     * single set's 1,377 KB</b> and scans in the same 3.5 ms. The names do not multiply, they
     * <em>partition</em> — a class costs twice only where two sheets both define it.</p>
     */
    private static List<String> styleSheetsIn(String html) {
        if (html == null) {
            return List.of();
        }
        List<String> sheets = new ArrayList<>();
        Matcher matcher = STYLE_BLOCK.matcher(html);
        while (matcher.find()) {
            // Separated, so a selector at the end of one block and one at the start of the next
            // cannot be read as a single name.
            sheets.add(matcher.group(2));
        }
        return sheets;
    }

    /**
     * Which of the page's own stylesheets defines each class (SH694).
     *
     * <p>Keyed {@code style[1]}, {@code style[2]} — the sheet's position in the document, which is
     * what the markup actually carries. A friendlier name would have to be invented: a rendered page
     * does not say which block is the captured theme and which the Shio one, and a label the reader
     * cannot check is the thing SH689 spent a task removing.</p>
     */
    public static Map<String, Set<String>> definedBySheet(String html) {
        Map<String, Set<String>> bySheet = new LinkedHashMap<>();
        List<String> sheets = styleSheetsIn(html);
        for (int at = 0; at < sheets.size(); at++) {
            Set<String> names = definedIn(sheets.get(at));
            if (!names.isEmpty()) {
                bySheet.put("style[" + (at + 1) + "]", names);
            }
        }
        return bySheet;
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

    /**
     * Class selectors a stylesheet defines, as the <b>markup</b> spells them (SH686).
     *
     * <p>Deliberately crude about what a selector is: every {@code .name}, which over-collects (a
     * {@code .5em} in a shorthand is not a selector) and never under-collects. Over-collecting is
     * the safe direction — it can only make the undefined-class list <em>shorter</em>, and a list
     * that claims a class is unstyled when it is not is the reading that wastes an agent's turn.</p>
     *
     * <h3>It under-collected anyway, on most of the web</h3>
     *
     * <p>This was a regex whose identifier stopped at the first character outside
     * {@code [_a-zA-Z0-9-]} — and a <b>backslash</b> is outside it. CSS requires {@code /} and
     * {@code :} to be escaped in an identifier, so Tailwind writes {@code .bg-background\/80} and
     * {@code .dark\:text-slate-400}, from which it collected {@code bg-background} and {@code dark}.
     * The markup carries the unescaped names. The two halves could never match.</p>
     *
     * <p>Measured on the {@code www.viglet.org} replica: <b>205 used, 1002 defined, 42 unstyled</b>,
     * and every one of the 42 is defined in the stored {@code theme/captured} CSS (1,061,479 chars).
     * All 42 carry a {@code /} or a {@code :} — the trait is the whole population, not a sample. So
     * {@code page-unstyled} was a false positive on any site built with a modern utility framework,
     * which is most of what a replication will ever capture, and worse than noise: it accused the
     * <em>verbatim</em> half of the page, which renders correctly. A warning pointing at the wrong
     * half is what sent a whole investigation into hand-parsing 1.1 MB of HTML.</p>
     *
     * <h3>A scan and not a regex, and that is a measurement rather than a taste</h3>
     *
     * <p>The obvious repair is to let the pattern's identifier accept escapes:
     * {@code (?:[_a-zA-Z0-9-]|\\…)*}. It works, and it introduces a <b>{@code StackOverflowError}
     * reachable from captured CSS</b> — which is content from somebody else's website. Measured on
     * this machine, over {@code .a} followed by 40,000 backslashes:</p>
     *
     * <pre>
     *   old regex   OK,  1.5 ms      (never enters a loop — it stops at the backslash)
     *   escape regex STACK OVERFLOW
     *   this scan   OK,  3.9 ms
     * </pre>
     *
     * <p>{@code java.util.regex} compiles {@code [class]*} to a non-recursive loop but
     * {@code (?:A|B)*} to a recursive one, so the fix would have added the crash while removing the
     * false positive. On the real 1 MB workload the scan is also the cheaper of the three — 23–32 ms
     * against 31.7 ms for the old regex and 58.4 ms for the escape-aware one — and it needs no
     * arbitrary length bound, which would truncate a long name back into a class the markup cannot
     * match. Same defect, one indirection along.</p>
     *
     * <p>Both escape forms, per the grammar. A hex escape is up to six digits and consumes one
     * optional whitespace terminator — that terminator exists so {@code \31 23} means "1" then "23"
     * rather than a six-digit code point, and keeping it would leave a space inside a class name
     * that has none. Any other escaped character stands for itself, which is the form Tailwind emits
     * for {@code /} and {@code :}. A null code point becomes U+FFFD as CSS requires, rather than a
     * NUL that no {@code class} attribute can hold.</p>
     */
    private static Set<String> definedIn(String css) {
        Set<String> names = new LinkedHashSet<>();
        if (css == null || css.isBlank()) {
            return names;
        }
        int at = 0;
        while (at < css.length()) {
            if (css.charAt(at) != '.') {
                at++;
                continue;
            }
            StringBuilder name = new StringBuilder();
            int end = readIdent(css, at + 1, name);
            if (end > at + 1 && !name.isEmpty()) {
                names.add(name.toString());
                at = end;
            } else {
                at++;
            }
        }
        return names;
    }

    /**
     * Reads one CSS identifier into {@code out}, unescaped, and returns where it ended.
     *
     * @return the index after the identifier, or {@code from} when there is no identifier here
     */
    private static int readIdent(String css, int from, StringBuilder out) {
        int at = from;
        if (at < css.length() && css.charAt(at) == '-') {
            out.append('-');
            at++;
        }
        boolean first = true;
        while (at < css.length()) {
            char c = css.charAt(at);
            if (c == '\\') {
                int next = readEscape(css, at, out);
                if (next == at) {
                    break;
                }
                at = next;
                first = false;
                continue;
            }
            if (first ? isIdentStart(c) : isIdentPart(c)) {
                out.append(c);
                at++;
                first = false;
                continue;
            }
            break;
        }
        // `first` still true means nothing but an optional hyphen was read, which names nothing.
        return first ? from : at;
    }

    /** One escape at {@code at}, appended to {@code out}; returns {@code at} when it is not one. */
    private static int readEscape(String css, int at, StringBuilder out) {
        int hexEnd = at + 1;
        while (hexEnd < css.length() && hexEnd - at <= 6 && isHex(css.charAt(hexEnd))) {
            hexEnd++;
        }
        if (hexEnd > at + 1) {
            int codePoint = Integer.parseInt(css.substring(at + 1, hexEnd), 16);
            out.appendCodePoint(codePoint == 0 ? 0xFFFD : codePoint);
            return hexEnd < css.length() && isEscapeTerminator(css.charAt(hexEnd)) ? hexEnd + 1 : hexEnd;
        }
        if (at + 1 < css.length() && css.charAt(at + 1) != '\n') {
            out.append(css.charAt(at + 1));
            return at + 2;
        }
        return at;
    }

    private static boolean isEscapeTerminator(char c) {
        return c == ' ' || c == '\t' || c == '\n';
    }

    private static boolean isIdentStart(char c) {
        return c == '_' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static boolean isIdentPart(char c) {
        return isIdentStart(c) || (c >= '0' && c <= '9') || c == '-';
    }

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
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
