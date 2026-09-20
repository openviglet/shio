/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.api.agent.verify;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * SH809 — which fields on a rendered page a curator could edit, read off the page itself.
 *
 * <h2>The gap this closes</h2>
 *
 * <p>{@code ShAgentP1ConformanceTest} holds the honest inventory of capabilities the manifest
 * advertises that the product's own primary operator cannot reach. {@code universalEditor} was on
 * it, classified {@code CONSOLE_ONLY}, with the note that SH185's bridge is a browser protocol and
 * the console is its only client — <em>`true` is honest to a human and not to an agent</em>.</p>
 *
 * <p>So an agent could read a post, render it, and diff draft against live (SH619), and still could
 * not answer the question a curator's whole job turns on: <b>which fields on this page can somebody
 * change</b>. It is the replication round's subject one layer up — a replica is *authorable* or it
 * is a screenshot — and P7's <i>never surprise the curator</i> read from the other side.</p>
 *
 * <h2>Why it reads the page and invents nothing</h2>
 *
 * <p>The editable surface is already written down, in the rendered HTML, by the protocol SH185
 * specified for the browser: {@code data-shio-prop} names a field and {@code data-shio-post} the
 * post that owns it, with {@code data-shio-region} / {@code data-shio-region-post} one level up.
 * The console's editor reads exactly those and nothing else, which is what lets it work against any
 * front end that emits them rather than only against this renderer.</p>
 *
 * <p>Reading the same attributes is therefore P10 — compose, do not fork. A second implementation
 * deriving editability from post-types and layout bindings would answer a *different* question
 * (what the model permits) and the two would agree only by accident, which is SH583's rule. What a
 * curator can actually click is what the page says it is.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShHtmlEditable {

    /** The field-level half of the bridge protocol (SH185). */
    private static final String PROP = "data-shio-prop";
    private static final String PROP_POST = "data-shio-post";

    /** The region-level half (SH183), which is what a field is nested inside when it is in one. */
    private static final String REGION = "data-shio-region";
    private static final String REGION_POST = "data-shio-region-post";

    private ShHtmlEditable() {
    }

    /**
     * One editable field, as the page annotates it.
     *
     * @param address the post that owns the field, spelled as every write on this surface spells
     *                one (SH79). Named `address` and not `post` after the attribute it is read from:
     *                SH810 — the wire format inherited `data-shio-post` from the markup, and an
     *                agent should not have to learn that two names are one idea (P2)
     * @param field   the field name a write would set
     * @param region  the region it renders through, or {@code null} where it is the page's own
     */
    public record Field(String address, String field, String region) {
    }

    /**
     * Every editable field a page declares, in document order and without duplicates.
     *
     * <p>Document order rather than sorted: it is the order a curator sees, and P5's byte-stability
     * is satisfied by the render being deterministic. Sorting would make the answer stable and
     * wrong about the one thing it is for.</p>
     */
    public static List<Field> of(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        Document document = Jsoup.parse(html);
        Set<Field> seen = new LinkedHashSet<>();
        for (Element element : document.select("[" + PROP + "]")) {
            String field = element.attr(PROP);
            if (field.isBlank()) {
                continue;
            }
            // The nearest annotated post wins: a field inside a region belongs to the region's post
            // unless it names its own, which is exactly how the browser bridge resolves it.
            Element owner = element.closest("[" + PROP_POST + "]");
            Element region = element.closest("[" + REGION + "]");
            String post = owner != null ? owner.attr(PROP_POST) : null;
            if ((post == null || post.isBlank()) && region != null) {
                post = region.attr(REGION_POST);
            }
            seen.add(new Field(blankToNull(post), field,
                    region == null ? null : blankToNull(region.attr(REGION))));
        }
        return List.copyOf(seen);
    }

    /** SH1038/SH1039 — the container half: an ordered list of sections, and what it accepts. */
    private static final String SECTIONS = "data-shio-sections";
    private static final String SECTION = "data-shio-section";
    private static final String ACCEPTS = "data-shio-accepts";

    /**
     * One place on the page that composes an ordered list of sections (SH1039).
     *
     * @param index    its position among this page's containers, in document order — the one handle
     *                 both halves can compute, since the field name is gone by the time markup
     *                 exists ({@code getRelation} takes the field's value, not its name)
     * @param region   the region it renders through, or {@code null} where the layout composes it
     * @param holds    how many sections are in it now; {@code 0} is a real answer and the case
     *                 insertion exists for
     * @param sections the types in it, in order, so a caller sees the composition without parsing
     * @param accepts  the post-type names the template says fit here — <b>empty when the container
     *                 declares none</b>, which is "nothing is known to fit" and not "anything"
     */
    public record Container(int index, String region, int holds, List<String> sections,
            List<String> accepts) {
    }

    /**
     * Every container on a rendered page, in document order (SH1039).
     *
     * <p>Selected by <em>either</em> annotation, which is the whole reach of this: a container with
     * sections in it is found by the count the composer derived, and an <b>empty</b> one only by
     * its own declaration — and the empty one is the case an editor most needs, since a page with
     * no sections yet is where nothing can be dragged from what is already there.</p>
     */
    public static List<Container> containers(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        List<Container> found = new ArrayList<>();
        for (Element element : Jsoup.parse(html).select("[" + SECTIONS + "],[" + ACCEPTS + "]")) {
            Element region = element.closest("[" + REGION + "]");
            List<String> sections = new ArrayList<>();
            for (Element child : element.children()) {
                String type = child.attr(SECTION);
                if (!type.isBlank()) {
                    sections.add(type);
                }
            }
            found.add(new Container(found.size(),
                    region == null ? null : blankToNull(region.attr(REGION)),
                    sections.size(), List.copyOf(sections), words(element.attr(ACCEPTS))));
        }
        return List.copyOf(found);
    }

    /** A space-separated declaration, split and de-duplicated, in the order it was written. */
    private static List<String> words(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        Set<String> names = new LinkedHashSet<>();
        for (String word : raw.trim().split("\\s+")) {
            if (!word.isBlank()) {
                names.add(word);
            }
        }
        return List.copyOf(names);
    }

    /** Every region the page declares, whether or not a field inside it is editable. */
    public static List<String> regions(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        Set<String> names = new LinkedHashSet<>();
        for (Element element : Jsoup.parse(html).select("[" + REGION + "]")) {
            String name = element.attr(REGION);
            if (!name.isBlank()) {
                names.add(name);
            }
        }
        return new ArrayList<>(names);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
