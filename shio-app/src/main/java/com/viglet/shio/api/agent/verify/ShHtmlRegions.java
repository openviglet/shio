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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.viglet.shio.render.ShRegionComposer;

/**
 * The page's regions in document order, and where each one's markup came from (SH687).
 *
 * <h2>Counting a page is not describing it</h2>
 *
 * <p>Two defects were visible in one glance at the {@code www.viglet.org} replica's home page: the
 * captured footer renders <b>above</b> the first section, and the accepted sections are presented by
 * the Shio theme while the chrome keeps the captured one — two visual languages in one document.
 * Neither was expressible in anything Shio returned.</p>
 *
 * <p>{@code landmarks} is a histogram: {@code footer×1 header×1 nav×1 section×9}, which cannot say
 * the footer is second. {@code digest} promises an outline and gives the heading tree, which
 * excludes a footer because footers carry no headings. {@code classes} (205), {@code definedClasses}
 * (1002), {@code unstyledClasses} (42), {@code diff}'s link and character counts, {@code
 * annotations}' {@code 67 of 4} — every one is a count or a set. {@code audit} alone holds geometry
 * and it returned <b>0 errors</b> there. So the appearance surface could <em>measure</em> a page and
 * could not <em>describe</em> it, and both defects are positional: one is order, one is
 * attribution.</p>
 *
 * <p>This answers the first. Fifteen lines against 1.1 MB of hand-parsed HTML and a human round
 * trip, which is P9 stated exactly backwards.</p>
 *
 * <h2>Beside {@code landmarks}, not instead of it</h2>
 *
 * <p>The histogram stays. It is in the structural hash that lets two runs be compared by string
 * (P5), {@code snapshot} diffs a source against a replica with it, and {@code audit} reads it as
 * roles — replacing it would have changed what a digest <em>means</em> to three readers in order to
 * add a fourth. The cost is two fields describing one thing, and it is the cheaper mistake.</p>
 *
 * <h2>Jsoup here and regex in {@link ShHtmlOutline}, which is not an inconsistency</h2>
 *
 * <p>The outline's own note explains why it parses by regex: nothing it produces needs tree
 * fidelity, and badly nested tags cost it a slightly wrong outline. This needs exactly what that
 * trade gives up — <b>containment</b>. "Is this landmark inside an authored section" is a question
 * about ancestry, and answering it by counting tags is how a scanner comes to attribute a region to
 * the wrong owner. {@code ShAgentRenderService.markersOf} already parses the same rendered page with
 * Jsoup for the same reason, so this adds no dependency and no second dialect.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShHtmlRegions {

    /** The same skeleton {@link ShHtmlOutline} counts, so the two fields cannot describe different pages. */
    private static final String LANDMARKS =
            "header, nav, main, section, article, aside, footer, form, table";

    /**
     * What marks a region as content somebody authored rather than markup that was kept.
     *
     * <p>Both, because they answer at different levels: the generated layout writes {@code sh-post}
     * on the element that owns a section's fields and {@code data-shio-section} naming its type, and
     * a replica in fidelity mode has neither. A landmark with an ancestor carrying either is a
     * section; everything else is markup this conversion kept verbatim.</p>
     */
    private static final String AUTHORED = "[" + ShRegionComposer.POST_ATTR + "], [data-shio-section]";

    /** A region whose markup an editor owns. */
    public static final String SECTIONS = "sections";

    /** A region whose markup came from the source and was kept as it was. */
    public static final String VERBATIM = "verbatim";

    private ShHtmlRegions() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Every landmark the page draws, in document order.
     *
     * @param html     the rendered page — the artefact a visitor gets
     * @param unstyled the class names no stylesheet defines ({@link ShHtmlAppearance}), so the count
     *                 is attributed to the region that draws them instead of to the document. An
     *                 empty set yields zeroes rather than nulls: "none" is an answer.
     */
    public static List<Region> of(String html, Collection<String> unstyled) {
        return of(html, unstyled, Map.of());
    }

    /**
     * @param bySheet SH694 — sheet name → the classes it defines ({@link ShHtmlAppearance}). A
     *                region's {@code styledBy} is which of those sheets styled anything it draws,
     *                which is what makes "the chrome and the sections share no stylesheet" sayable.
     *                Empty leaves every {@code styledBy} empty rather than guessing one.
     */
    public static List<Region> of(String html, Collection<String> unstyled,
                                  Map<String, Set<String>> bySheet) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        // A sorted set rather than `Set.copyOf`, which `ShByteStabilityLintTest` refuses and was
        // right to. Nothing here iterates it today — `undefinedIn` only asks `contains` and returns
        // an int — so an exemption would have been defensible and would have been the wrong answer:
        // SH687 deliberately stops one column short of naming the theme per region, and the task
        // that adds it is the one that starts iterating this. An exemption is a claim about today.
        Set<String> undefined =
                unstyled == null ? new TreeSet<>() : new TreeSet<>(unstyled);
        Document document = Jsoup.parse(html);
        List<Region> regions = new ArrayList<>();
        int order = 0;
        // Jsoup returns a selection in document order, which is the whole point of this field: the
        // footer being second is the defect, and any re-sorting here would erase it.
        for (Element element : document.select(LANDMARKS)) {
            order++;
            regions.add(new Region(
                    element.tagName(),
                    order,
                    element.closest(AUTHORED) != null ? SECTIONS : VERBATIM,
                    undefinedIn(element, undefined),
                    styledBy(element, bySheet)));
        }
        return List.copyOf(regions);
    }

    /**
     * How many undefined class names this region and its descendants draw with.
     *
     * <p>Descendants included, and nested landmarks therefore double-count: a {@code section} inside
     * a {@code main} is counted by both. That is the honest reading of "what does this region draw
     * with" and the alternative — attributing each class to its nearest landmark only — answers a
     * question nobody asked, since a reader scanning the table wants the region they are looking at
     * to account for everything under it.</p>
     */
    private static int undefinedIn(Element region, Set<String> undefined) {
        if (undefined.isEmpty()) {
            return 0;
        }
        Set<String> seen = new TreeSet<>();
        for (Element node : region.select("*")) {
            for (String name : node.classNames()) {
                if (undefined.contains(name)) {
                    seen.add(name);
                }
            }
        }
        for (String name : region.classNames()) {
            if (undefined.contains(name)) {
                seen.add(name);
            }
        }
        return seen.size();
    }

    /**
     * Which of the page's own stylesheets styles anything this region draws (SH694).
     *
     * <p>The second positional defect, and the one {@code landmarks} and {@code unstyledClasses}
     * between them could not state: the replica presents its accepted sections through the Shio
     * theme and keeps the captured one for its chrome — two visual languages in one document. A set
     * of classes says <em>whether</em> they are defined; this says <b>by which sheet</b>, and two
     * regions whose sets are disjoint is the fact the warning is made of.</p>
     *
     * <p>Sheet order, so two runs agree (P5), and the region's own descendants included for the
     * reason {@link #undefinedIn} gives: a reader looking at a region wants it to account for
     * everything under it.</p>
     */
    private static List<String> styledBy(Element region, Map<String, Set<String>> bySheet) {
        if (bySheet.isEmpty()) {
            return List.of();
        }
        Set<String> drawn = new TreeSet<>();
        for (Element node : region.select("*")) {
            drawn.addAll(node.classNames());
        }
        drawn.addAll(region.classNames());
        List<String> sheets = new ArrayList<>();
        for (Map.Entry<String, Set<String>> sheet : bySheet.entrySet()) {
            for (String name : drawn) {
                if (sheet.getValue().contains(name)) {
                    sheets.add(sheet.getKey());
                    break;
                }
            }
        }
        return List.copyOf(sheets);
    }

    /**
     * One region of the page.
     *
     * @param tag      the landmark element
     * @param order    its 1-based position among the landmarks, in document order — the field the
     *                 histogram could not carry, and the one that says a footer renders second
     * @param markup   {@link #SECTIONS} or {@link #VERBATIM}: who owns what this region draws
     * @param unstyled distinct class names it draws with that no stylesheet defines
     * @param styledBy the page's own stylesheets that style something it draws, in document order
     *                 (SH694) — empty when the caller supplied no sheet map, which is an absence
     *                 rather than a claim that nothing styles it
     */
    public record Region(String tag, int order, String markup, int unstyled, List<String> styledBy) {
    }
}
