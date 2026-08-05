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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SH388 — what a {@code PageLayout}'s {@code <head>} gets wrong, read off the stored HTML.
 *
 * <h2>The question SH388 was filed to answer, and the answer</h2>
 *
 * <p>It was filed as an <b>idea</b> with one open question: are these {@code verify} findings at all,
 * or do they belong to the delivery layer that owns the head in production (P10)? The answer is that
 * both readings were about the wrong subject. The head in production is composed by the front end over
 * the CDA and Shio does not own it — so enforcing what a <em>visitor</em> receives is not this
 * product's business. What Shio does own is the <b>layout</b>, because a layout is content it stores,
 * and every defect the task named is a property of that stored markup rather than of a page.</p>
 *
 * <h2>Which turns the expensive design into a cheap one</h2>
 *
 * <p>The idea proposed rules over the <em>rendered</em> page: a duplicate {@code <title>} within a
 * site, a duplicate description, an absent canonical. Rendering every page of a site to compare heads
 * is the cost SH312 and SH384 measured, and it reports the <b>symptom</b> — sixty pages sharing one
 * description — sixty times.</p>
 *
 * <p>The <b>cause</b> is one line of one layout, and it is provable with no render at all: a
 * {@code <title>} that interpolates nothing has the same value on every page bound to that layout, by
 * construction. So the scan is a text scan of a field the instance already holds, the finding is
 * reported once against the post an author would edit, and the page count goes in the message rather
 * than into the count — {@code unpublishedTemplates}' shape (SH280), for its reason.</p>
 *
 * <h2>The five rules, and why two are errors</h2>
 *
 * <p>A <b>static title</b> and a <b>static description</b> are warnings: a one-page site legitimately
 * has one of each, and a layout bound to a single post-type with a single post is not wrong. A
 * <b>missing canonical</b> is a warning too — SH337 supplied {@code post.canonical} precisely so a
 * layout can emit one, but a site behind a front end that composes its own head does not need it here.
 *
 * <p>Two are errors because they are provably wrong rather than a matter of taste. A <b>relative
 * {@code og:image}</b> is the one the task singled out and it deserves the severity: a crawler
 * resolving it against its own origin gets nothing, so the tag renders perfectly and the card is
 * blank — and {@code site.baseUrl} (SH337) exists for exactly this. A <b>{@code noindex}</b> in a
 * layout is the mistake a preview-shaped conversion carries over, and its consequence is a site that
 * is invisible; nobody writes it into a production layout on purpose.
 *
 * <h2>A scanner, not a parser — and the interpolation test is the whole trick</h2>
 *
 * <p>Everything here is a bounded regex over a field, in the idiom {@link ShHtmlOutline} established.
 * The one non-obvious part is what counts as interpolated: a Handlebars expression {@code {{...}}}
 * anywhere inside the element's own text or attribute. That is deliberately generous — {@code {{TITLE}}},
 * {@code {{post.title}}} and {@code {{#if x}}a{{else}}b{{/if}}} all count — because the claim being
 * made is narrow: this value <em>cannot</em> differ between two pages. A layout that varies its title
 * some way this scanner cannot see is a false positive, and the severity is a warning for that reason.
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShHeadConventions {

    /** A Handlebars expression of any kind. Its presence is the whole "this can vary" test. */
    private static final Pattern INTERPOLATION = Pattern.compile("\\{\\{.*?}}", Pattern.DOTALL);

    private static final Pattern TITLE =
            Pattern.compile("(?is)<title\\b[^>]*>(.*?)</title\\s*>");
    private static final Pattern META = Pattern.compile("(?is)<meta\\b([^>]*?)/?>");
    private static final Pattern LINK = Pattern.compile("(?is)<link\\b([^>]*?)/?>");
    /** Raw-text and comment blocks, removed first: a `<title>` inside a comment is not a title. */
    private static final Pattern IGNORED = Pattern.compile(
            "(?is)<(script|style)\\b[^>]*>.*?</\\1\\s*>|<!--.*?-->", Pattern.DOTALL);

    private ShHeadConventions() {
        // utility
    }

    /** One defect in a layout's head: the rule, whether it is an error, and what was found. */
    public record Finding(String rule, boolean error, String detail) {
    }

    /**
     * Scans one layout's HTML.
     *
     * <p>A layout with <b>no {@code <head>} at all</b> yields nothing rather than five findings: it is
     * either a fragment or a deliberately headless template, and reporting the absence of every
     * convention would bury the layouts that have a head and get it wrong. That is the one shape of
     * silence here, and it is a choice rather than an oversight.
     */
    public static List<Finding> of(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        String source = IGNORED.matcher(html).replaceAll(" ");
        if (!source.toLowerCase(java.util.Locale.ROOT).contains("<head")) {
            return List.of();
        }
        List<Finding> found = new ArrayList<>();
        titleFindings(source, found);
        metaFindings(source, found);
        canonicalFindings(source, found);
        return List.copyOf(found);
    }

    private static void titleFindings(String source, List<Finding> found) {
        Matcher matcher = TITLE.matcher(source);
        if (!matcher.find()) {
            found.add(new Finding("head-no-title", false,
                    "the layout emits no <title> at all"));
            return;
        }
        String inner = matcher.group(1);
        if (!INTERPOLATION.matcher(inner).find()) {
            found.add(new Finding("head-static-title", false,
                    "<title> is the literal \"" + ShHtmlOutline.text(inner) + "\""));
        }
    }

    private static void metaFindings(String source, List<Finding> found) {
        boolean description = false;
        Matcher matcher = META.matcher(source);
        while (matcher.find()) {
            String attributes = matcher.group(1);
            String name = lower(ShHtmlOutline.attribute(attributes, "name"));
            String property = lower(ShHtmlOutline.attribute(attributes, "property"));
            String content = ShHtmlOutline.attribute(attributes, "content");

            if ("description".equals(name)) {
                description = true;
                if (!hasInterpolation(attributes)) {
                    found.add(new Finding("head-static-description", false,
                            "the description is the literal \"" + trim(content) + "\""));
                }
            }
            if ("robots".equals(name) && content != null
                    && content.toLowerCase(java.util.Locale.ROOT).contains("noindex")) {
                found.add(new Finding("head-noindex", true,
                        "<meta name=\"robots\" content=\"" + trim(content) + "\">"));
            }
            // `og:image` is matched on `property` and on `name`, because both spellings are in the
            // wild and a scanner that accepted only the specified one would pass the file that is
            // wrong in the commoner way.
            if (("og:image".equals(property) || "og:image".equals(name)) && isRelative(content)) {
                found.add(new Finding("head-relative-og-image", true,
                        "og:image is \"" + trim(content) + "\""));
            }
        }
        if (!description) {
            found.add(new Finding("head-no-description", false,
                    "the layout emits no <meta name=\"description\">"));
        }
    }

    private static void canonicalFindings(String source, List<Finding> found) {
        Matcher matcher = LINK.matcher(source);
        while (matcher.find()) {
            if ("canonical".equals(lower(ShHtmlOutline.attribute(matcher.group(1), "rel")))) {
                return;
            }
        }
        found.add(new Finding("head-no-canonical", false,
                "no <link rel=\"canonical\"> — SH337 put post.canonical on the model for it"));
    }

    /**
     * Whether an {@code og:image} value is a path rather than an absolute URL.
     *
     * <p>A value that is <b>entirely</b> a Handlebars expression is not judged: {@code {{OG_IMAGE}}}
     * may hold anything, and guessing would report the layout that is doing it right. A value that
     * *starts* with an expression is left alone for the same reason. What is caught is the literal
     * {@code /img/card.png} — and {@code {{site.baseUrl}}/img/card.png}, which is absolute by
     * construction, correctly is not.
     */
    private static boolean isRelative(String content) {
        String value = trim(content);
        if (value.isEmpty() || value.startsWith("{{")) {
            return false;
        }
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        return !lower.startsWith("http://") && !lower.startsWith("https://") && !lower.startsWith("//")
                && !lower.startsWith("data:");
    }

    private static boolean hasInterpolation(String text) {
        return text != null && INTERPOLATION.matcher(text).find();
    }

    private static String lower(String value) {
        return value == null ? null : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String trim(String value) {
        String text = value == null ? "" : value.trim();
        return text.length() <= 80 ? text : text.substring(0, 79) + "…";
    }
}
