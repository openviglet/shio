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
import org.jsoup.nodes.DataNode;
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

    /**
     * SH185 — the two attributes a <b>template author</b> writes to make something editable.
     *
     * <p>{@code sh-field="TEXT"} says "this element renders that field"; {@code sh-post="{{id}}"}
     * says "the fields under here belong to that post". They are the author's job and not the
     * engine's for a reason that only shows up in a listing: a region is rendered against the
     * <em>page's</em> model, so {@code {{TEXT}}} inside it is the page's TEXT — but inside a
     * {@code {{#query}}} block {@code {{title}}} is the listed post's. Nothing in the rendered
     * HTML records which interpolation came from where, and inferring it would mean the
     * template engine tracking provenance through every helper. The template is the only thing
     * that knows, so the template says.</p>
     */
    public static final String FIELD_ATTR = "sh-field";
    public static final String POST_ATTR = "sh-post";

    private static final String ANNOTATION_NAME = "data-shio-region";
    private static final String ANNOTATION_POST = "data-shio-region-post";
    private static final String ANNOTATION_MISSING = "data-shio-region-missing";
    private static final String ANNOTATION_FIELD_POST = "data-shio-post";
    private static final String ANNOTATION_PROP = "data-shio-prop";
    private static final String ANNOTATION_POST_TYPE = "data-shio-post-type";

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
        return compose(layoutHtml, regions, null, null);
    }

    /**
     * @param pagePostId the page's own post id, stamped on {@code <body>} so a
     *                   {@code sh-field} with no nearer {@code sh-post} belongs to the page —
     *                   which is the common case and the one an author should not have to
     *                   write out (SH185)
     * @param pageType   the page's post-type name, so the editor can fetch a field schema
     *                   without a second resolve
     */
    public String compose(String layoutHtml, Map<String, ShRenderedRegion> regions,
                          String pagePostId, String pageType) {
        if (layoutHtml == null || layoutHtml.isBlank()) {
            return "";
        }
        Document document = parse(layoutHtml);
        // Filled in slot order, so the scripts below come out in the order the page uses
        // the regions rather than in whatever order the map happens to hold them (P5).
        LinkedHashSet<String> filled = new LinkedHashSet<>();
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
            filled.add(name);
        }
        annotateEditable(document, pagePostId, pageType);
        appendRegionScripts(document, regions, filled);
        return document.html();
    }

    /**
     * SH185 — turns the author's {@code sh-field}/{@code sh-post} into the {@code data-shio-*}
     * pair the Universal Editor reads.
     *
     * <h3>Why the translation exists at all</h3>
     *
     * <p>It would be simpler to have authors write {@code data-shio-prop} directly. Keeping a
     * short authoring form and a rendered form is the same choice {@code sh-region} already
     * makes, and it buys the same thing: the {@code sh-*} attributes are a <b>contract the
     * content declares</b>, so SH193 can extract them into the reference graph and SH192 can
     * lint them, while the {@code data-*} pair is what a browser and the editor consume. A
     * single attribute doing both jobs could not be changed on either side without breaking
     * the other.</p>
     *
     * <h3>Ownership is nearest-ancestor, and the page is the default</h3>
     *
     * <p>{@code <body>} carries the page's own post, so the common case — a layout
     * interpolating the page's fields — needs no {@code sh-post} at all. A listing declares
     * ownership per row ({@code sh-post="{{id}}"}), which is the only place it can be known.
     * Resolution walks up from the field, so a row's declaration wins over the page's without
     * either having to know about the other.</p>
     *
     * <p>A {@code sh-field} that resolves to <b>no</b> post is annotated as a field and left
     * without an owner rather than dropped: the editor renders it read-only and says why,
     * which is SH169's rule — a mistake the author can see beats markup that quietly is not
     * editable.</p>
     */
    private void annotateEditable(Document document, String pagePostId, String pageType) {
        if (pagePostId != null && !pagePostId.isBlank()) {
            document.body().attr(ANNOTATION_FIELD_POST, pagePostId);
            if (pageType != null && !pageType.isBlank()) {
                document.body().attr(ANNOTATION_POST_TYPE, pageType);
            }
        }
        for (Element owner : document.select("[" + POST_ATTR + "]")) {
            String postId = owner.attr(POST_ATTR).trim();
            if (!postId.isEmpty()) {
                owner.attr(ANNOTATION_FIELD_POST, postId);
            }
        }
        for (Element field : document.select("[" + FIELD_ATTR + "]")) {
            String prop = field.attr(FIELD_ATTR).trim();
            if (prop.isEmpty()) {
                continue;
            }
            field.attr(ANNOTATION_PROP, prop);
        }
    }

    /**
     * SH248 — a region's browser JavaScript, shipped to the browser.
     *
     * <h3>Why the renderer has to do this</h3>
     *
     * <p>The whole resolution of the Block D non-goal is a split: HTML composes on the server,
     * declaratively; <b>JavaScript runs in the browser as a region's static asset</b>. SH183
     * shipped the first half and gave {@code Region.JAVASCRIPT} a code editor, and nothing
     * served it — so the field accepted working code and silently discarded its effect, which
     * is worse than not having the field, because it teaches the author that they wrote the
     * code wrong. Same shape as SH178's two schema entry points.</p>
     *
     * <h3>Inline, and once per region</h3>
     *
     * <p><b>Inline</b> rather than a cacheable route, because the page it is part of is served
     * {@code no-store} (SH183's guard against a preview becoming infrastructure) — caching a
     * fragment of an uncacheable page buys nothing, and a route is a second thing that can
     * 404. It also keeps the render self-contained, which is what SH102's snapshot and SH187
     * need. If preview ever becomes delivery, this is one of the decisions that gets revisited
     * along with the headers.</p>
     *
     * <p><b>Once</b>, keyed by region name, because a layout may slot the same region twice —
     * a nav in the header and the footer — and running its initialisation twice is a bug that
     * only appears on the layouts that do it. {@code filled} is a {@code LinkedHashSet} for
     * exactly that.</p>
     *
     * <p>Each tag carries {@code data-shio-region}, so a script is traceable to the post it
     * came from in the same way its markup is.</p>
     */
    private void appendRegionScripts(Document document, Map<String, ShRenderedRegion> regions,
                                     LinkedHashSet<String> filled) {
        for (String name : filled) {
            ShRenderedRegion region = regions.get(name);
            if (region == null || region.javascript() == null || region.javascript().isBlank()) {
                continue;
            }
            Element script = document.createElement("script");
            script.attr(ANNOTATION_NAME, name);
            if (region.postId() != null) {
                script.attr(ANNOTATION_POST, region.postId());
            }
            // appendChild(DataNode) rather than .text(): a script body is not text content,
            // and jsoup would escape `<` and `&` in it — which turns `a && b` into `a &amp;&amp; b`
            // and the script into a syntax error.
            script.appendChild(new DataNode(region.javascript()));
            document.body().appendChild(script);
        }
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
     * @param name       the {@code sh-region} name it answers to
     * @param postId     the Region post's id, so the rendered DOM can be mapped back to the
     *                   thing to save — SH185's contract
     * @param html       the rendered markup
     * @param javascript the region's browser script (SH248), emitted once per page even when
     *                   the layout slots the region twice. Never evaluated on the server.
     */
    public record ShRenderedRegion(String name, String postId, String html, String javascript) {

        /** For the many callers and tests that have no script to attach. */
        public ShRenderedRegion(String name, String postId, String html) {
            this(name, postId, html, null);
        }
    }
}
