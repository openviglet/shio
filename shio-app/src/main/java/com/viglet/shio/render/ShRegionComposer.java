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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

import org.jsoup.Jsoup;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
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

    /**
     * SH503 — a second field on the same element, and the attribute it is drawn into.
     *
     * <p>{@code sh-field} declares the element's <em>content</em>, so <b>one element declares one
     * field</b> — and a link is two of them, its text and its target. The measured case is the call
     * to action: {@code <a sh-field="ctaLabel" href="{{ctaHref}}">} annotates 9 of the vocabulary's
     * 10 fields, and the one it cannot reach is the destination, so a curator could rename the
     * button and not repoint it. {@code image} escapes the limit only by accident — its element is
     * the {@code <img>} and the field it declares <em>is</em> the attribute.</p>
     *
     * <p>The form is {@code sh-field-attr="FIELD:ATTRIBUTE"}, space-separated for more than one
     * pair, and the name says the order the value uses. The <b>alternative was to wrap the
     * anchor</b> so the text and the href each own an element; that is the tail wagging the dog. A
     * link is one thing to a curator, and splitting it into two boxes to satisfy the markup would
     * put the protocol's limit into every template that has a link in it.</p>
     *
     * <p>Like {@link #FIELD_ATTR} the value is <b>copied verbatim</b> — the renderer has never
     * parsed a prop and still does not. What splits the pair is the browser bridge, which is the
     * side that has to read the attribute off the element anyway.</p>
     */
    public static final String ATTR_FIELD_ATTR = "sh-field-attr";

    private static final String ANNOTATION_NAME = "data-shio-region";
    private static final String ANNOTATION_POST = "data-shio-region-post";
    public static final String ANNOTATION_MISSING = "data-shio-region-missing";
    /**
     * SH281 — the slot's region exists and has never been published.
     *
     * <p>A separate attribute rather than a value on {@code data-shio-region-missing}, because SH169's
     * rule is that different mistakes must not look alike, and this is a <b>third</b> state beside
     * missing and empty: the author is looking at the region in the console while the page says it is
     * absent. Reporting "no Region post" for it is not a missing diagnosis, it is a wrong one.</p>
     */
    public static final String ANNOTATION_UNPUBLISHED = "data-shio-region-unpublished";

    /** SH297 — the theme title a layout names, on {@code <body>}, escaped as an attribute value. */
    private static final String ANNOTATION_THEME = "data-shio-theme";

    /** SH297 — set when that theme does not exist, so the page says what the lint says. */
    public static final String ANNOTATION_THEME_MISSING = "data-shio-theme-missing";

    /**
     * SH299 — the three attributes that mean "this page is carrying a defect", in one list.
     *
     * <p>Public, and read by {@code /agent/render}: the digest counts them so a proof cannot come
     * back green on a page that says it is broken. A second hand-kept list of these names in the
     * verify package would be the drift SH260 removed for helpers, on the rendered side.</p>
     */
    public static final List<String> DEFECT_MARKERS =
            List.of(ANNOTATION_MISSING, ANNOTATION_UNPUBLISHED, ANNOTATION_THEME_MISSING);

    /** Constant for the same reason the region markers are: a theme title is content. */
    private static final String MISSING_THEME_MARKER =
            " shio: this layout names a Theme that does not exist; see data-shio-theme ";
    private static final String ANNOTATION_FIELD_POST = "data-shio-post";
    private static final String ANNOTATION_PROP = "data-shio-prop";
    /** SH503 — the rendered form of {@link #ATTR_FIELD_ATTR}, same pairs, same order. */
    private static final String ANNOTATION_PROP_ATTR = "data-shio-prop-attr";

    /**
     * SH1037 — the WIDGET the model declares for this field, so the browser knows its kind.
     *
     * <p>Edit mode was granted by the presence of {@code data-shio-prop} alone: any annotated
     * element became {@code contenteditable} and reported {@code innerHTML}, with no reference to
     * what the field is. So a plain {@code Text} field absorbed the markup of a paste, stored it,
     * and then displayed the tags as visible text — because the template escapes a text field, which
     * is correct. The defect is not the escaping; it is an editor that offered rich editing over a
     * field that cannot hold it.</p>
     *
     * <p>The widget name verbatim, not a kind this class invents: the model's vocabulary is the one
     * the console's widget map, {@code shio pull} and the CDA schema already speak, and a second
     * classification here would be a third opinion about what a field is. The browser maps the name
     * to an affordance, which is where that decision belongs — it is the half that knows what a
     * {@code contenteditable} can do.</p>
     */
    public static final String ANNOTATION_PROP_TYPE = "data-shio-prop-type";

    /**
     * The section handle a conversion's generated layout writes, READ here and never written.
     *
     * <p>SH556 established it: a class is a role, a per-type handle is a {@code data-*} attribute, so
     * {@code data-shio-section="SectionHero"} is what addresses one type. {@code ShHtmlRegions}
     * already reads it to tell authored content from markup a conversion kept. This class does not
     * emit it — the template does, because only the template knows which element IS the section —
     * and adds the two facts below, which the template cannot know.</p>
     */
    public static final String ANNOTATION_SECTION = "data-shio-section";

    /**
     * SH1038 — a section's position among its siblings, so the DOM records ORDER and not only
     * identity.
     *
     * <p>Derived from document order rather than taken from the template, and that is what makes it
     * trustworthy: {@code {{#getRelation SECTIONS}}} renders the Page's reference field in the order
     * the field names, so document order <em>is</em> that order. A template could write
     * {@code {{@index}}} — the data frame has published it since SH772 — and then every template
     * would have to remember to, and one that forgot would leave a page whose sections cannot be
     * moved with nothing to say so.</p>
     *
     * <p>It is also why the index is <b>per container</b> rather than per page: a layout may compose
     * two ordered lists, and a single running count would make the second list's first section read
     * as position three of one array that does not exist.</p>
     */
    public static final String ANNOTATION_SECTION_INDEX = "data-shio-section-index";

    /**
     * SH1038 — on the element that composes an ordered list of sections: how many it holds.
     *
     * <p>The container is what a reorder acts on, and it has to be addressable for the editor to
     * know where a section may move TO — the position after the last one is a real destination and
     * there is no element there to select. The count says where that is.</p>
     */
    public static final String ANNOTATION_SECTIONS = "data-shio-sections";

    /**
     * SH1039 — what a container <b>accepts</b>, declared by the template that composes it.
     *
     * <p>An editor that can select and move a section still cannot add one, because nothing says
     * which types fit where: the shared section types reach the console as post-type names in a
     * picker, never as something to place. The catalogue has to be the template's, since
     * <em>only the template knows what a container accepts</em> — the same argument that makes
     * {@link #FIELD_ATTR} the author's job rather than the engine's.</p>
     *
     * <p>Space-separated post-type names, copied verbatim like every other declaration here. An
     * <b>absent</b> attribute is not "everything": it is "nothing is known to fit", and the
     * catalogue says so with the declaration to add. Reading it as "every section type this
     * instance has" would need a server-side list of which types are sections, and
     * {@code ShVocabularyPostTypes} is explicit that a third hand-written copy of the vocabulary is
     * the drift SH245 bans.</p>
     */
    public static final String ACCEPTS_ATTR = "sh-accepts";
    public static final String ANNOTATION_ACCEPTS = "data-shio-accepts";
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

    /** Constant for the same reason {@code MISSING_MARKER} is: a region name is content. */
    private static final String UNPUBLISHED_MARKER =
            "<!-- shio: this slot's Region post is not published; see data-shio-region -->";

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
     * SH1106 — the section types a template's containers declare, read off the unrendered HTML.
     *
     * <p>Static for the reason {@link #slotsOf} is: {@code sh-accepts} is an attribute an author
     * writes, not content. A blank declaration contributes nothing, as it does in the render pass.</p>
     *
     * @return the declared type names in document order, each once; empty when nothing declares
     */
    public List<String> acceptsOf(String templateHtml) {
        if (templateHtml == null || templateHtml.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> types = new LinkedHashSet<>();
        for (Element container : parse(templateHtml).select("[" + ACCEPTS_ATTR + "]")) {
            for (String type : container.attr(ACCEPTS_ATTR).trim().split("\\s+")) {
                if (!type.isEmpty()) {
                    types.add(type);
                }
            }
        }
        return new ArrayList<>(types);
    }

    /**
     * Fills every slot in {@code layoutHtml} from {@code regions}, keyed by region name.
     *
     * @param layoutHtml the layout's markup, already rendered as a template
     * @param regions    rendered regions by name; a name absent here is a missing region
     * @return the composed page
     */
    public String compose(String layoutHtml, Map<String, ShRenderedRegion> regions) {
        return compose(layoutHtml, regions, null, null, Set.of());
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
        return compose(layoutHtml, regions, pagePostId, pageType, Set.of());
    }

    /**
     * @param unpublishedSlots SH281 — slot names whose Region post exists and is not published, so
     *                         the marker can say which of the two mistakes this is
     */
    public String compose(String layoutHtml, Map<String, ShRenderedRegion> regions,
                          String pagePostId, String pageType, Set<String> unpublishedSlots) {
        return compose(layoutHtml, regions, pagePostId, pageType, unpublishedSlots, null);
    }

    /**
     * @param missingTheme SH297 — the theme title this layout names and the site has not got, so
     *                     the page carries the same defect the lint reports. Marked rather than
     *                     refused, because a theme <em>degrades</em> the page (SH281's rule for a
     *                     region): an unstyled page still serves, and for a preview that is right
     */
    public String compose(String layoutHtml, Map<String, ShRenderedRegion> regions,
                          String pagePostId, String pageType, Set<String> unpublishedSlots,
                          String missingTheme) {
        return compose(layoutHtml, regions, pagePostId, pageType, unpublishedSlots, missingTheme,
                Map.of());
    }

    /**
     * @param propTypes SH1037 — the page type's field name to declared widget, so an annotated
     *                  element can say what KIND of field it renders. The page's own type only: a
     *                  listing row declares its post and not its type, so a row's widget is not
     *                  knowable here and the attribute is simply absent, which the browser reads as
     *                  "edit this in the panel" rather than guessing
     */
    public String compose(String layoutHtml, Map<String, ShRenderedRegion> regions,
                          String pagePostId, String pageType, Set<String> unpublishedSlots,
                          String missingTheme, Map<String, String> propTypes) {
        return compose(layoutHtml, regions, pagePostId, pageType, unpublishedSlots, missingTheme,
                propTypes, ids -> Map.of());
    }

    /**
     * @param ownerWidgets SH1175 — asked once, with every post id that owns a field and is not the
     *                     page, for each one's field-to-widget map. A section is a post of its own
     *                     type, so without this every field inside one was undeclared, the bridge
     *                     edited it as rich HTML, and a typed trailing space was stored as an
     *                     {@code &nbsp;} that the next save escaped again. One call for the whole
     *                     page rather than one per section, so a page of ten sections is one query
     */
    public String compose(String layoutHtml, Map<String, ShRenderedRegion> regions,
                          String pagePostId, String pageType, Set<String> unpublishedSlots,
                          String missingTheme, Map<String, String> propTypes,
                          Function<Set<String>, Map<String, Map<String, String>>> ownerWidgets) {
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
                // SH281 — which of the two it is, because they need different actions: create the
                // region, or publish the one you are looking at.
                boolean unpublished = unpublishedSlots != null && unpublishedSlots.contains(name);
                slot.attr(unpublished ? ANNOTATION_UNPUBLISHED : ANNOTATION_MISSING, "true");
                slot.html(unpublished ? UNPUBLISHED_MARKER : MISSING_MARKER);
                continue;
            }
            if (region.postId() != null) {
                slot.attr(ANNOTATION_POST, region.postId());
            }
            slot.html(region.html() == null ? "" : region.html());
            filled.add(name);
        }
        annotateEditable(document, pagePostId, pageType, propTypes, ownerWidgets);
        annotateSections(document);
        markMissingTheme(document, missingTheme);
        appendRegionScripts(document, regions, filled);
        return document.html();
    }

    /**
     * SH297 — says on the page that its layout names a theme that is not there.
     *
     * <p>On {@code <body>} and not on a slot, because a theme is not slotted: a layout names it in
     * a field and decides itself where {@code {{theme.css}}} goes, so there is no element that
     * stands for it. The name travels as an <b>attribute</b>, where jsoup escapes it, and the
     * comment is a constant — SH183's rule, because a title is content and {@code -->} inside one
     * would close the comment and put the rest of the page into the document as markup.</p>
     *
     * <p>A distinct attribute from the region markers for SH281's reason: three states that need
     * three different actions must not share one flag, or the marker diagnoses the wrong thing.</p>
     */
    private void markMissingTheme(Document document, String missingTheme) {
        if (missingTheme == null || missingTheme.isBlank()) {
            return;
        }
        document.body().attr(ANNOTATION_THEME, missingTheme);
        document.body().attr(ANNOTATION_THEME_MISSING, "true");
        document.body().prependChild(new org.jsoup.nodes.Comment(MISSING_THEME_MARKER));
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
     *
     * <h3>And one element may declare more than one field (SH503)</h3>
     *
     * <p>{@code sh-field} says what the element's <em>content</em> is, which makes one element one
     * field — so a link's destination had nowhere to go, and was captured, stored and rendered with
     * no declaration. {@link #ATTR_FIELD_ATTR} adds the pairs that live in the element's own
     * attributes. Both are copied verbatim into their {@code data-} form and neither is parsed
     * here.</p>
     */
    private void annotateEditable(Document document, String pagePostId, String pageType,
            Map<String, String> propTypes,
            Function<Set<String>, Map<String, Map<String, String>>> ownerWidgets) {
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
        Elements fields = document.select("[" + FIELD_ATTR + "]");
        Map<String, Map<String, String>> ownerTypes = ownerWidgetsOf(fields, pagePostId, ownerWidgets);
        for (Element field : fields) {
            String prop = field.attr(FIELD_ATTR).trim();
            if (prop.isEmpty()) {
                continue;
            }
            field.attr(ANNOTATION_PROP, prop);
            String widget = widgetOf(field, prop, propTypes, ownerTypes);
            if (widget != null) {
                field.attr(ANNOTATION_PROP_TYPE, widget);
            }
        }
        // SH503 — the same translation for the fields an element draws into its own attributes.
        // Separate loop rather than a branch inside the one above, because the two declarations are
        // independent: an <a> carries both (its text and its href), and an element may carry only
        // this one — a row's `icon`, which is a `data-` attribute and no text at all.
        for (Element field : document.select("[" + ATTR_FIELD_ATTR + "]")) {
            String pairs = field.attr(ATTR_FIELD_ATTR).trim();
            if (pairs.isEmpty()) {
                continue;
            }
            field.attr(ANNOTATION_PROP_ATTR, pairs);
        }
    }

    /**
     * The widget this field's model declares, or {@code null} when it is not knowable here.
     *
     * <p>Only for a field the <b>page</b> owns. Ownership is nearest-ancestor and a listing row
     * declares its post id without its type ({@code sh-post="{{id}}"} is all an author can write,
     * because the row's type is the query's and not the element's), so a row's widget cannot be
     * resolved at compose time. Absent rather than guessed: a wrong kind is worse than no kind,
     * since the browser acts on it — it would make a {@code Date} field contenteditable, or refuse
     * inline editing of a {@code Text} one.</p>
     *
     * <p>A collection leaf ({@code COLL.N.LEAF}, SH475) is not in the map either and falls through
     * the same way.</p>
     */
    private static String widgetOf(Element field, String prop, Map<String, String> propTypes,
                                   Map<String, Map<String, String>> ownerTypes) {
        Element owner = field.closest("[" + ANNOTATION_FIELD_POST + "]");
        if (owner == null) {
            return null;
        }
        if ("body".equals(owner.tagName())) {
            return propTypes == null ? null : propTypes.get(prop);
        }
        // SH1175 — a section, or any other post named by `sh-post`: its own type's widgets, when the
        // resolver knew the post. A listing row the resolver could not type stays undeclared.
        Map<String, String> widgets = ownerTypes.get(owner.attr(ANNOTATION_FIELD_POST));
        return widgets == null ? null : widgets.get(prop);
    }

    /**
     * SH1175 — every post other than the page that owns a field, asked about in one call, sorted so
     * the call is the same for the same page (P5). No call at all when the page owns every field.
     */
    private static Map<String, Map<String, String>> ownerWidgetsOf(Elements fields, String pagePostId,
            Function<Set<String>, Map<String, Map<String, String>>> ownerWidgets) {
        Set<String> owners = new TreeSet<>();
        for (Element field : fields) {
            String owner = ownerIdOf(field);
            if (owner != null && !owner.isBlank() && !owner.equals(pagePostId)) {
                owners.add(owner);
            }
        }
        if (owners.isEmpty() || ownerWidgets == null) {
            return Map.of();
        }
        Map<String, Map<String, String>> answer = ownerWidgets.apply(owners);
        return answer == null ? Map.of() : answer;
    }

    /** The post a field belongs to: its nearest declared owner, or {@code null} for none. */
    private static String ownerIdOf(Element field) {
        Element owner = field.closest("[" + ANNOTATION_FIELD_POST + "]");
        return owner == null ? null : owner.attr(ANNOTATION_FIELD_POST);
    }

    /**
     * SH1038 — records each section's POSITION beside the identity it already carried.
     *
     * <h3>The gap this closes</h3>
     *
     * <p>A Page names its sections by address in order, every section's fields are annotated, and the
     * per-section handle is emitted by the generated layout and read by the server's region walk. So
     * the handle existed, the server read it, and the browser bridge did not — which made a section
     * something an editor could change the headline of and never the section: not selectable, not
     * movable, not removable, though {@code post.upsert} can do all four by rewriting the array.</p>
     *
     * <h3>Derived from the DOM, which is the only place both facts are true at once</h3>
     *
     * <p>Position is not knowable to the element itself and identity is not knowable to the
     * container, so neither the template nor the field can state the pair. Document order is the
     * field's order — {@code getRelation} renders the references in the order they are named — so a
     * pass over the composed page is where the two meet. It writes nothing when a page has no
     * sections, so an ordinary layout is byte-identical to what it was.</p>
     */
    private void annotateSections(Document document) {
        Map<Element, Integer> held = new LinkedHashMap<>();
        for (Element section : document.select("[" + ANNOTATION_SECTION + "]")) {
            Element container = section.parent();
            if (container == null) {
                continue;
            }
            // Position among the sections of THIS container, not among the page's. A layout may
            // compose two ordered lists, and one running count would make the second list's first
            // section read as position three of an array that does not exist.
            int index = held.merge(container, 1, Integer::sum) - 1;
            section.attr(ANNOTATION_SECTION_INDEX, String.valueOf(index));
        }
        held.forEach((container, count) ->
                container.attr(ANNOTATION_SECTIONS, String.valueOf(count)));
        // SH1039 — a container that declares what it accepts is a container even when it holds
        // nothing, and the EMPTY one is the case insertion exists for: a page with no sections yet
        // is where an editor most needs to be told what fits. Without this it would be invisible to
        // every reader, since the pass above can only see a container by the sections in it.
        for (Element container : document.select("[" + ACCEPTS_ATTR + "]")) {
            String accepts = container.attr(ACCEPTS_ATTR).trim();
            // Blank is skipped like an empty `sh-field`: a declaration naming nothing is an author
            // mid-edit, and annotating it would say "this container accepts nothing" as a fact.
            if (accepts.isEmpty()) {
                continue;
            }
            container.attr(ANNOTATION_ACCEPTS, accepts);
            if (!container.hasAttr(ANNOTATION_SECTIONS)) {
                container.attr(ANNOTATION_SECTIONS, "0");
            }
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
