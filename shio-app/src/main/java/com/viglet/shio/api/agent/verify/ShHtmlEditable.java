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
     * @param post   the address of the post that owns the field, as the page states it
     * @param field  the field name a write would set
     * @param region the region it renders through, or {@code null} where it is the page's own
     */
    public record Field(String post, String field, String region) {
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
