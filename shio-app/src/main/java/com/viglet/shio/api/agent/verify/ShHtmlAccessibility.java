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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SH1055 — what is wrong with a delivered page for somebody who is not looking at it.
 *
 * <h2>The liability this closes</h2>
 *
 * <p>A CMS's real accessibility exposure is the pages it <em>publishes</em>, and verify had one
 * rule about them: {@code image-no-alt}. Nothing asked whether the headings formed an outline,
 * whether the document declared a language, whether a link had a name, whether the page had a main
 * region, or whether a form control was labelled — although the render path already builds the
 * heading list, the link list and the landmark counts every one of those needs.</p>
 *
 * <h2>Read the same way the rest of this package reads</h2>
 *
 * <p>Regex over the markup, through {@link ShHtmlOutline}'s own helpers, rather than a second HTML
 * parser beside the one every other reader here uses. jsoup is on the classpath and would be
 * tidier for the form rule; two parsing models in one package is the cost, and the day they
 * disagree about a malformed page is the day a finding is about the parser.</p>
 *
 * <h2>Every rule is conservative, on purpose</h2>
 *
 * <p>A rule that fires on a page that is fine is a rule somebody turns off, and then the four that
 * were right go with it. So: a control wrapped in its own {@code <label>} is labelled and this
 * knows it; a link whose only content is an image with {@code alt} has a name; a page with no
 * {@code <html>} element at all is a fragment and is not missing a language. Each of those is a
 * real pattern, and each would otherwise be a false finding on the first site anybody ran this on.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShHtmlAccessibility {

    /** Controls that are not for a person to fill in, so nothing announces them. */
    private static final Set<String> UNLABELLED_TYPES =
            Set.of("hidden", "submit", "button", "image", "reset");

    private static final Pattern HTML_TAG = Pattern.compile("(?is)<html\\b([^>]*)>");
    private static final Pattern LABEL_BLOCK = Pattern.compile("(?is)<label\\b[^>]*>.*?</label\\s*>");
    private static final Pattern LABEL_FOR = Pattern.compile("(?is)<label\\b([^>]*)>");
    private static final Pattern CONTROL = Pattern.compile(
            "(?is)<(input|select|textarea)\\b([^>]*)>");
    private static final Pattern ANCHOR = Pattern.compile("(?is)<a\\b([^>]*)>(.*?)</a\\s*>");
    private static final Pattern IMG = Pattern.compile("(?is)<img\\b([^>]*)>");
    private static final Pattern MAIN = Pattern.compile("(?is)<main\\b|role\\s*=\\s*[\"']main[\"']");

    /**
     * One thing wrong, in the shape a finding takes.
     *
     * @param rule   the rule name, as the report and the publish gate address it
     * @param detail what is wrong, with enough of the page in it to find the place
     * @param fix    what to do — P6 applies to a finding as much as to a 4xx
     */
    public record Violation(String rule, String detail, String fix) {
    }

    private ShHtmlAccessibility() {
        // utility
    }

    /**
     * Every violation on one rendered page.
     *
     * @param html the page as a visitor receives it; {@code null} or blank yields nothing
     */
    public static List<Violation> of(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        List<Violation> found = new ArrayList<>();
        headingSkip(html, found);
        langMissing(html, found);
        linkNoText(html, found);
        landmarkMissing(html, found);
        formLabelMissing(html, found);
        return List.copyOf(found);
    }

    /**
     * A heading level that jumps.
     *
     * <p>An outline is how a screen-reader user navigates a long page, and a jump from
     * {@code h1} to {@code h3} says a level exists that is not there. The walk starts at zero, so
     * a page whose first heading is an {@code h2} is reported too — that is the same defect from
     * the top rather than a different rule.</p>
     *
     * <p>Only the <b>first</b> jump is reported. A page whose section headings are all one level
     * too deep produces one finding and one repair, not forty of the same sentence.</p>
     */
    private static void headingSkip(String html, List<Violation> found) {
        int previous = 0;
        for (ShHtmlOutline.Heading heading : ShHtmlOutline.of(html).headings()) {
            if (heading.level() > previous + 1) {
                found.add(new Violation("heading-skip",
                        "The heading outline jumps from h" + previous + " to h" + heading.level()
                                + " at \"" + shorten(heading.text()) + "\".",
                        "Headings are how a screen reader navigates a page, so a level that is "
                                + "skipped is a section that cannot be reached. Use h"
                                + (previous + 1) + " here, or add the level above it."));
                return;
            }
            previous = heading.level();
        }
    }

    /**
     * A document that does not say what language it is in.
     *
     * <p>Without it a screen reader reads the page in whatever voice it was left in, which turns
     * Portuguese content into noise for a reader whose synthesizer is set to English. The render
     * model has carried `locale` since SH383 — this asks whether the template used it.</p>
     *
     * <p>A page with no {@code <html>} element is a fragment, not a document with a missing
     * attribute, and is silently skipped: verify renders whole pages, but a template that emits a
     * partial is a different defect and `page-empty` is the rule about it.</p>
     */
    private static void langMissing(String html, List<Violation> found) {
        Matcher root = HTML_TAG.matcher(html);
        if (!root.find()) {
            return;
        }
        String lang = ShHtmlOutline.attribute(root.group(1), "lang");
        if (lang == null || lang.isBlank()) {
            found.add(new Violation("lang-missing",
                    lang == null
                            ? "The <html> element has no lang attribute."
                            : "The <html> element has lang=\"\", which asserts the language is "
                                    + "unknown rather than leaving it unstated.",
                    "Emit lang=\"{{locale}}\" on <html> in the theme's layout — the render model "
                            + "has carried the post's locale since SH383, defaulted so it is "
                            + "never blank."));
        }
    }

    /**
     * A link a screen reader announces as "link", and nothing else.
     *
     * <p>Three ways to have a name and all three count: visible text, an {@code aria-label} or
     * {@code title} on the anchor, or an image inside carrying {@code alt}. An icon link with a
     * described image is correct and common, and a rule that missed that would fire on every
     * social-media row ever built.</p>
     *
     * <p>Anchors with no {@code href} are skipped: those are named anchors and targets, not links.</p>
     */
    private static void linkNoText(String html, List<Violation> found) {
        Set<String> reported = new LinkedHashSet<>();
        Matcher anchors = ANCHOR.matcher(html);
        while (anchors.find()) {
            String attributes = anchors.group(1);
            String href = ShHtmlOutline.attribute(attributes, "href");
            if (href == null || href.isBlank()) {
                continue;
            }
            if (!named(attributes) && ShHtmlOutline.text(anchors.group(2)).isEmpty()
                    && !describedImage(anchors.group(2))) {
                // Per href: the same unnamed icon in a header repeated on a page is one repair.
                if (reported.add(href)) {
                    found.add(new Violation("link-no-text",
                            "The link to '" + href + "' has no text and no accessible name.",
                            "Give it visible text, or aria-label, or — if it wraps an icon — an "
                                    + "alt on the image inside it. A link announced as \"link\" "
                                    + "with nothing after it cannot be chosen from a list of them."));
                }
            }
        }
    }

    /**
     * A page with nothing marked as its main content.
     *
     * <p>The landmark that lets a reader skip the header and the navigation, which on a site
     * with a long menu is the difference between reaching the article in one keystroke and forty.
     * A {@code role="main"} counts: the ARIA form and the element are the same statement.</p>
     */
    private static void landmarkMissing(String html, List<Violation> found) {
        if (!MAIN.matcher(html).find()) {
            found.add(new Violation("landmark-missing",
                    "This page marks no main region: no <main> element and no role=\"main\".",
                    "Wrap the page's own content in <main> in the layout. It is what lets a "
                            + "screen reader skip the header and the navigation, which is a "
                            + "keystroke instead of the whole menu on every page."));
        }
    }

    /**
     * A form control nothing names.
     *
     * <p>Four ways to be labelled, and the fourth is why this strips before it scans: a control
     * <b>wrapped</b> in its own {@code <label>} is named by it, which is a correct and common
     * pattern, so every label's content is removed first and what is left is genuinely
     * unaccompanied. The other three are a {@code <label for>} pointing at the control's id, an
     * {@code aria-label} or {@code aria-labelledby}, and a {@code title}.</p>
     *
     * <p>Submit buttons, hidden fields and the rest are skipped: their name is their value or they
     * are not announced at all.</p>
     */
    private static void formLabelMissing(String html, List<Violation> found) {
        Set<String> labelled = new LinkedHashSet<>();
        Matcher labels = LABEL_FOR.matcher(html);
        while (labels.find()) {
            String target = ShHtmlOutline.attribute(labels.group(1), "for");
            if (target != null && !target.isBlank()) {
                labelled.add(target);
            }
        }
        // A control inside a label is named by it, so those are taken out of the question entirely.
        String outside = LABEL_BLOCK.matcher(html).replaceAll(" ");

        Set<String> reported = new LinkedHashSet<>();
        Matcher controls = CONTROL.matcher(outside);
        while (controls.find()) {
            String tag = controls.group(1).toLowerCase(java.util.Locale.ROOT);
            String attributes = controls.group(2);
            String type = ShHtmlOutline.attribute(attributes, "type");
            if ("input".equals(tag) && type != null
                    && UNLABELLED_TYPES.contains(type.toLowerCase(java.util.Locale.ROOT))) {
                continue;
            }
            String id = ShHtmlOutline.attribute(attributes, "id");
            if (named(attributes) || (id != null && labelled.contains(id))) {
                continue;
            }
            String name = ShHtmlOutline.attribute(attributes, "name");
            String which = id != null ? "#" + id : (name != null ? "name=" + name : "<" + tag + ">");
            if (reported.add(which)) {
                found.add(new Violation("form-label-missing",
                        "The form control " + which + " has no label.",
                        "Add a <label for=\"…\"> pointing at its id, wrap it in a <label>, or "
                                + "give it aria-label. A placeholder is not a label: it "
                                + "disappears as soon as somebody types."));
            }
        }
    }

    /** Whether an element carries an accessible name of its own. */
    private static boolean named(String attributes) {
        return notBlank(ShHtmlOutline.attribute(attributes, "aria-label"))
                || notBlank(ShHtmlOutline.attribute(attributes, "aria-labelledby"))
                || notBlank(ShHtmlOutline.attribute(attributes, "title"));
    }

    /** Whether this markup contains an image with alt text, which names the link around it. */
    private static boolean describedImage(String inner) {
        Matcher images = IMG.matcher(inner);
        while (images.find()) {
            if (notBlank(ShHtmlOutline.attribute(images.group(1), "alt"))) {
                return true;
            }
        }
        return false;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    /** Enough of a heading to find it on the page, and not a paragraph in a report. */
    private static String shorten(String text) {
        String trimmed = text == null ? "" : text.trim();
        return trimmed.length() <= 60 ? trimmed : trimmed.substring(0, 57) + "...";
    }
}
