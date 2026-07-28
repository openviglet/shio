/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * SH183 — how a layout and its regions become one page, and why the seams stay visible.
 *
 * <h2>The mechanism is the one the 2018 templates already used</h2>
 *
 * <p>A layout's {@code HTML} field is a whole document with named holes:</p>
 *
 * <pre>{@code <div sh-region="BOOTSTRAP_NAV_REGION"> </div>}</pre>
 *
 * <p>That is from {@code openviglet/bootstrap-site}, an SH68 exchange package last
 * touched in 2018, and it is the composition mechanism this class implements. Nothing
 * about it had to be invented — the attribute is in the content, and content imported
 * from that package still carries it byte-identically.</p>
 *
 * <h2>The attribute survives the render, on purpose</h2>
 *
 * <p>It would be tidier to strip {@code sh-region} once the hole is filled. It is left in,
 * and each filled slot additionally carries {@code data-shio-region} and
 * {@code data-shio-region-post}, because <b>the rendered DOM is the only place the
 * Universal Editor can learn which node is which region</b>. SH185's whole problem is
 * that {@code visual-editor.page.tsx} posts {@code {type, data}} into an iframe and
 * nothing on the other side knows what a given element maps to; an annotated render is
 * the answer, and it is free here. A region is the edit unit precisely because it is the
 * unit the markup can name.</p>
 *
 * <h2>An unfilled slot is not an empty slot</h2>
 *
 * <p>A layout naming a region that does not exist, and a region that rendered to nothing,
 * are different mistakes and must not produce the same page — the same rule SH169 applied
 * to the digest, where a missing section became {@code [missing section /url]} rather than
 * contributing nothing so that two failures could not hash alike. A missing region leaves
 * an HTML comment naming it and sets {@code data-shio-region-missing}, so the defect is
 * visible in the page, in the SH101 digest and to anyone reading source.</p>
 *
 * <h2>Determinism</h2>
 *
 * <p>{@link #slotsOf} returns names in <b>document order</b> with duplicates collapsed,
 * and output is written with pretty-print off. Both are P5: the same layout and the same
 * regions must produce the same bytes, or SH101's digest and SH102's snapshot compare
 * noise. A layout may slot the same region twice (a nav in the header and the footer),
 * which is why the resolve is keyed by name and applied to every match.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShRegionComposer {

    /** The 2018 attribute. Not renamed: existing content carries it. */
    public static final String SLOT_ATTR = "sh-region";

    private static final String ANNOTATION_NAME = "data-shio-region";
    private static final String ANNOTATION_POST = "data-shio-region-post";
    private static final String ANNOTATION_MISSING = "data-shio-region-missing";

    /**
     * Fixed, and it does <b>not</b> interpolate the region's name.
     *
     * <p>The first version wrote {@code <!-- no Region post named "NAME" -->}, which reads
     * better and is not provably safe: a region name is content, HTML comments decode no
     * entities, and a name containing {@code -->} closes the comment and puts whatever
     * follows into the document as markup. Escaping it is possible and is the kind of
     * hand-rolled rule that is right until somebody finds the sequence it missed. The name
     * is already on the element as {@code data-shio-region}, where jsoup escapes it as an
     * attribute value — so the comment can be a constant and lose nothing: anyone reading
     * source sees the marker and the name on the same line.</p>
     */
    private static final String MISSING_MARKER =
            "<!-- shio: no Region post for this slot; see data-shio-region -->";

    /**
     * @param layoutHtml a layout's {@code HTML} field
     * @return the region names it slots, in document order, each once
     */
    public List<String> slotsOf(String layoutHtml) {
        if (layoutHtml == null || layoutHtml.isBlank()) {
            return List.of();
        }
        // LinkedHashSet: document order is the stable order, and a layout that slots the
        // same region twice asks for it once.
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (Element slot : parse(layoutHtml).select("[" + SLOT_ATTR + "]")) {
            String name = slot.attr(SLOT_ATTR).trim();
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return new ArrayList<>(names);
    }

    /**
     * Fills every slot in {@code layoutHtml} from {@code regions}, keyed by region name.
     *
     * @param layoutHtml the layout's markup, already rendered as a template
     * @param regions    rendered regions by name; a name absent here is a missing region
     * @return the composed page
     */
    public String compose(String layoutHtml, Map<String, ShRenderedRegion> regions) {
        if (layoutHtml == null || layoutHtml.isBlank()) {
            return "";
        }
        Document document = parse(layoutHtml);
        for (Element slot : document.select("[" + SLOT_ATTR + "]")) {
            String name = slot.attr(SLOT_ATTR).trim();
            ShRenderedRegion region = regions.get(name);
            slot.attr(ANNOTATION_NAME, name);
            if (region == null) {
                slot.attr(ANNOTATION_MISSING, "true");
                slot.html(MISSING_MARKER);
                continue;
            }
            if (region.postId() != null) {
                slot.attr(ANNOTATION_POST, region.postId());
            }
            slot.html(region.html() == null ? "" : region.html());
        }
        return document.html();
    }

    private Document parse(String html) {
        Document document = Jsoup.parse(html);
        // Byte stability over readability: re-indenting moves SH101's digest and SH102's
        // snapshot for a change nobody made.
        document.outputSettings().prettyPrint(false);
        return document;
    }

    /**
     * One region's contribution to a page.
     *
     * @param name   the {@code sh-region} name it answers to
     * @param postId the Region post's id, so the rendered DOM can be mapped back to the
     *               thing to save — SH185's contract
     * @param html   the rendered markup
     */
    public record ShRenderedRegion(String name, String postId, String html) {
    }
}
