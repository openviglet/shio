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
import java.util.TreeMap;
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

    /**
     * SH829 — the {@code Theme} post whose CSS a {@code <style>} carries, on the element itself.
     *
     * <p>{@code data-shio-theme} rather than HTML's own {@code title}: a titled {@code <style>} is
     * an <b>alternate stylesheet set</b>, and two of them with different titles means the browser
     * applies one and drops the other. Naming the sheet must not change which sheets paint the
     * page.</p>
     *
     * <p>{@code data-shio-theme-missing} cannot match it — {@code -} is not {@code \s*=} — which is
     * what lets the two attributes share a prefix without sharing a reading.</p>
     */
    private static final Pattern SHEET_THEME =
            Pattern.compile("(?is)\\bdata-shio-theme\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))");

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
        return sheetsIn(html).stream().map(Sheet::css).toList();
    }

    /**
     * One {@code <style>} element: the {@code Theme} post it names, and what it contains.
     *
     * @param theme the post's title from {@code data-shio-theme}, or {@code null} — an inline
     *              {@code <style>} a layout writes is nobody's post and has no name to print
     */
    private record Sheet(String theme, String css) {
    }

    private static List<Sheet> sheetsIn(String html) {
        if (html == null) {
            return List.of();
        }
        List<Sheet> sheets = new ArrayList<>();
        Matcher matcher = STYLE_BLOCK.matcher(html);
        while (matcher.find()) {
            // Separated, so a selector at the end of one block and one at the start of the next
            // cannot be read as a single name.
            sheets.add(new Sheet(themeOf(matcher.group(1)), matcher.group(2)));
        }
        return sheets;
    }

    /** The attribute's value, decoded — it travelled as an attribute so a title could be content. */
    private static String themeOf(String attributes) {
        if (attributes == null) {
            return null;
        }
        Matcher matcher = SHEET_THEME.matcher(attributes);
        if (!matcher.find()) {
            return null;
        }
        String value = firstNonNull(matcher.group(2), matcher.group(3), matcher.group(4));
        String title = org.jsoup.parser.Parser.unescapeEntities(value, true).trim();
        return title.isEmpty() ? null : title;
    }

    /**
     * Which of the page's own stylesheets defines each class (SH694), by the name each carries
     * (SH829).
     *
     * <h3>An index is not a name</h3>
     *
     * <p>SH694 keyed these {@code style[1]}, {@code style[2]} — the sheet's position in the document
     * — and said a friendlier name would have to be invented, because a rendered page does not say
     * which block is the captured theme and which the Shio one. It does now: the {@code <style>}
     * carries {@code data-shio-theme} with the title of the {@code Theme} post whose CSS it holds,
     * so the name is read off the page rather than invented, which is the property SH689 was
     * protecting. A region table that says {@code VigR3 (captured)} answers <em>which sheet</em>;
     * one that says {@code style[2]} answers only <em>there are two</em> and costs a second read.</p>
     *
     * <p>The index stays where there is nothing to read: an inline {@code <style>} a layout writes
     * by hand is not a post and honestly is {@code style[N]}. Keeping the distinction is worth
     * something on its own — it is exactly the difference between a sheet somebody can edit and one
     * nobody can.</p>
     *
     * <h3>A name that cannot be told apart is not one either</h3>
     *
     * <p>Two sheets naming the same title, or a title that collides with the {@code style[N]} of
     * another sheet, both fall back to the index for <b>every</b> sheet involved. Half-naming them
     * would print two keys the reader cannot map back to two posts, which is the second lookup this
     * removes, restored.</p>
     */
    public static Map<String, Set<String>> definedBySheet(String html) {
        Map<String, Set<String>> bySheet = new LinkedHashMap<>();
        List<Sheet> sheets = sheetsIn(html);
        List<String> names = keysFor(sheets);
        for (int at = 0; at < sheets.size(); at++) {
            Set<String> defined = definedIn(sheets.get(at).css());
            if (!defined.isEmpty()) {
                bySheet.put(names.get(at), defined);
            }
        }
        return bySheet;
    }

    /**
     * One key per sheet, in document order: its theme's title where that is unambiguous, else its
     * position.
     */
    private static List<String> keysFor(List<Sheet> sheets) {
        List<String> indexes = new ArrayList<>();
        for (int at = 0; at < sheets.size(); at++) {
            indexes.add("style[" + (at + 1) + "]");
        }
        Set<String> ambiguous = new LinkedHashSet<>(indexes);
        Set<String> seen = new LinkedHashSet<>();
        for (Sheet sheet : sheets) {
            if (sheet.theme() != null && !seen.add(sheet.theme())) {
                ambiguous.add(sheet.theme());
            }
        }
        List<String> keys = new ArrayList<>();
        for (int at = 0; at < sheets.size(); at++) {
            String theme = sheets.get(at).theme();
            keys.add(theme == null || ambiguous.contains(theme) ? indexes.get(at) : theme);
        }
        return keys;
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
        Tokens tokens = tokensIn(css);
        return new Appearance(
                List.copyOf(used),
                List.copyOf(unstyled),
                count(STYLE_ATTR, page),
                imagesWithoutDimensions(page),
                externalStylesheets(page),
                defined.size(),
                tokens.dangling(),
                tokens.danglingRefs(),
                tokens.declared());
    }

    /**
     * The custom properties a stylesheet declares and the ones it reads without declaring (SH851).
     *
     * <h2>The half of the vocabulary nothing was counting</h2>
     *
     * <p>SH850 was a page-wide visual regression that <b>every</b> instrument passed: {@code verify}
     * answered {@code 0 error(s), 2 warning(s)} on both rounds, {@code diff} reported the same 18
     * losses, {@code judge} found nothing new, {@code audit} rendered the page and returned no error.
     * The only thing that noticed was a person looking at it and saying it had got uglier.</p>
     *
     * <p>The gap is exact. This class counts <b>class names</b> — {@code classes},
     * {@code definedClasses}, {@code unstyledClasses} — and a stylesheet built on design tokens
     * carries most of its meaning somewhere else: on the replica that found this, <b>3,005</b>
     * {@code var()} calls against <b>205</b> class names. A dangling {@code var()} is the same defect
     * shape as an unstyled class and it is cheaper to detect, because both sides are in the CSS the
     * page already carries — declared set minus referenced set, no rendering and no browser:</p>
     *
     * <pre>
     *   round 4    62 undefined,  231 occurrences
     *   round 5   194 undefined,  958 occurrences
     * </pre>
     *
     * <h2>Three decisions, each in the direction that under-reports</h2>
     *
     * <p><b>A reference carrying a fallback is not dangling.</b> {@code var(--x, 1rem)} is a
     * declaration the author made at the call site; counting it would report every defensive
     * stylesheet ever written as broken. <b>Comments are skipped</b>, which {@link #definedIn}
     * does not bother to do and is right not to: over-collecting a *declaration* can only shorten a
     * finding list, while over-collecting a *reference* invents one, and a commented-out
     * {@code var()} would be exactly that. <b>And only what the page carries</b> — a {@code var()}
     * in a {@code style=} attribute is a real reference this does not see, because the answer would
     * then depend on markup the declared half was never read from.</p>
     *
     * <p>The scan is a scan for {@link #definedIn}'s reason, measured there: {@code java.util.regex}
     * compiles {@code (?:A|B)*} to a recursive loop, so an escape-aware pattern over captured CSS —
     * content from somebody else's website — is a {@code StackOverflowError} waiting for a source
     * with forty thousand backslashes in it. {@link #readIdent} is reused whole, entered one
     * character in: it takes a single leading hyphen, and a custom property has two.</p>
     */
    static Tokens tokensIn(String css) {
        Set<String> declared = new TreeSet<>();
        Map<String, Integer> read = new TreeMap<>();
        if (css == null || css.isBlank()) {
            return new Tokens(List.of(), 0, 0);
        }
        int at = 0;
        while (at < css.length() - 1) {
            if (css.charAt(at) == '/' && css.charAt(at + 1) == '*') {
                int close = css.indexOf("*/", at + 2);
                at = close < 0 ? css.length() : close + 2;
                continue;
            }
            if (css.charAt(at) != '-' || css.charAt(at + 1) != '-') {
                at++;
                continue;
            }
            StringBuilder name = new StringBuilder("-");
            int end = readIdent(css, at + 1, name);
            if (end == at + 1 || name.length() < 3) {
                at++;
                continue;
            }
            if (declares(css, end)) {
                declared.add(name.toString());
            } else if (insideVar(css, at) && !hasFallback(css, end)) {
                read.merge(name.toString(), 1, Integer::sum);
            }
            at = end;
        }
        List<String> dangling = read.keySet().stream().filter(name -> !declared.contains(name))
                .toList();
        int occurrences = dangling.stream().mapToInt(read::get).sum();
        return new Tokens(dangling, occurrences, declared.size());
    }

    /** What a stylesheet says about its own custom properties. */
    record Tokens(List<String> dangling, int danglingRefs, int declared) {
    }

    /** A name followed by a colon is a declaration, whatever else it might have been. */
    private static boolean declares(String css, int end) {
        int at = skipSpace(css, end);
        return at < css.length() && css.charAt(at) == ':';
    }

    /** A name preceded by `var(` is a reference. Case-insensitive, because CSS keywords are. */
    private static boolean insideVar(String css, int start) {
        int at = start - 1;
        while (at >= 0 && Character.isWhitespace(css.charAt(at))) {
            at--;
        }
        return at >= 3 && css.charAt(at) == '('
                && "var".equalsIgnoreCase(css.substring(at - 3, at));
    }

    /** `var(--x, 1rem)` — the author declared a value here, so nothing is missing. */
    private static boolean hasFallback(String css, int end) {
        int at = skipSpace(css, end);
        return at < css.length() && css.charAt(at) == ',';
    }

    private static int skipSpace(String css, int from) {
        int at = from;
        while (at < css.length() && Character.isWhitespace(css.charAt(at))) {
            at++;
        }
        return at;
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
                             int definedClasses, List<String> danglingTokens, int danglingTokenRefs,
                             int definedTokens) {
    }
}
