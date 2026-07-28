/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.post.type.ShPostTypeAdminMapper;
import com.viglet.shio.api.post.type.dto.ShPostTypeDef;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import com.viglet.shio.persistence.repository.widget.ShWidgetRepository;
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;
import com.viglet.shio.widget.ShSystemWidget;

/**
 * SH183 — the three post-types the render model is made of, created on first use.
 *
 * <h2>Why they are created rather than seeded</h2>
 *
 * <p>System post-types are not in {@code import/post-types.json}, and that is deliberate:
 * a fresh instance should not carry a vocabulary it may never use. SH38 set the precedent
 * for the render side of that — {@code ShStaticFileService.resolveOrCreateFilePostType()}
 * brings the {@code File} type into being the first time somebody uploads — and this is
 * the same rule for {@code PageLayout}, {@code Region} and {@code Theme}. Nothing appears
 * in an instance nobody renders in.</p>
 *
 * <h2>Why they are post-types at all</h2>
 *
 * <p>This is the decision the whole block turns on. A layout could have been a Java class,
 * a file on disk, or a row in a table of its own. Making it a <b>post</b> of a system
 * post-type means an agent writes it with {@code post.upsert}, it travels in the SH68
 * exchange package, it projects to {@code shio/content/**} (P4), {@code shio verify} lints
 * it and {@code shio push --content} converges it — all of it for free, because those
 * tools work on content and this is content. A renderer built as a closed Java projection
 * would be none of those things and would need a Java release to extend.</p>
 *
 * <p>{@code PT-PAGE-LAYOUT} and {@code PT-REGION} were both already declared in FSP's
 * {@code ShSystemPostType} in 2019; the region level was never finished, because nothing
 * in Java ever read it. The field names below are the 2018 package's, so a real
 * {@code openviglet/bootstrap-site} export imports and renders without a migration.</p>
 *
 * <h2>The widgets say what each field is</h2>
 *
 * <p>{@code HTML} is {@code Ace Editor - HTML}, {@code JAVASCRIPT} is
 * {@code Ace Editor - Javascript} and — since SH252 — a {@code Theme}'s {@code CSS} is
 * {@code Ace Editor - CSS}: a template is code, and the console should give it the right code
 * editor. SH191 projects each of them to a sidecar file keyed on the widget, so the CSS field now
 * lands in a {@code .css} rather than a {@code .html}.</p>
 *
 * <p>A region's {@code JAVASCRIPT} exists and is <b>never evaluated on the server</b>. It
 * is the browser's, shipped as a static asset — which is the whole resolution of the
 * Block D non-goal: an author still writes "HTML and JavaScript", and the JavaScript runs
 * where JavaScript has run in every front end written this decade.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
public class ShRenderPostTypes {

    /** The three, in the order a reader meets them: page, then its parts, then its skin. */
    public static final List<String> NAMES = List.of(
            ShSystemPostType.PAGE_LAYOUT, ShSystemPostType.REGION, ShSystemPostType.THEME);

    private final ShPostTypeRepository shPostTypeRepository;
    private final ShWidgetRepository shWidgetRepository;
    private final ShPostTypeAdminMapper mapper;

    public ShRenderPostTypes(ShPostTypeRepository shPostTypeRepository,
                             ShWidgetRepository shWidgetRepository,
                             ShPostTypeAdminMapper mapper) {
        this.shPostTypeRepository = shPostTypeRepository;
        this.shWidgetRepository = shWidgetRepository;
        this.mapper = mapper;
    }

    /**
     * Creates whichever of the three are absent and returns all three as SH16 defs.
     *
     * <p>Idempotent, and it never touches an existing type: a curator who added a field to
     * their {@code Region} keeps it. That is the same rule a blueprint follows when it
     * meets a post-type it did not create (SH152) — provision, never upsert.</p>
     *
     * <p><b>It returns defs, not entities, and the mapping happens in here.</b>
     * {@code shPostTypeAttrs} is LAZY, so mapping an <em>existing</em> type after this
     * method's transaction has closed throws {@code LazyInitializationException} — and
     * only for an existing one, because a type this call just built still holds its
     * fields in memory. That is a defect that passes on a fresh instance and fails on
     * every one after, which is exactly the shape SH176 found on the console's post read
     * and answered the same way: map inside the transaction, hand out a projection.</p>
     */
    @Transactional
    public List<ShPostTypeDef> ensure() {
        return List.of(
                mapper.toDef(ensurePageLayout()),
                mapper.toDef(ensureRegion()),
                mapper.toDef(ensureTheme()));
    }

    /** Which of the three exist, without creating any — same transaction rule. */
    @Transactional(readOnly = true)
    public List<ShPostTypeDef> existing() {
        return NAMES.stream()
                .map(shPostTypeRepository::findByName)
                .filter(Objects::nonNull)
                .map(mapper::toDef)
                .toList();
    }

    private ShPostType ensurePageLayout() {
        return ensure(ShSystemPostType.PAGE_LAYOUT, "Page Layouts", "Page Layout",
                "An HTML skeleton with named sh-region slots",
                titleField(),
                field(ShSystemPostTypeAttr.HTML, "HTML", ShSystemWidget.ACE_HTML, 2, true),
                field(ShSystemPostTypeAttr.THEME, "Theme", ShSystemWidget.TEXT, 3, false),
                field(ShSystemPostTypeAttr.JAVASCRIPT, "JavaScript",
                        ShSystemWidget.ACE_JS, 4, false));
    }

    private ShPostType ensureRegion() {
        return ensure(ShSystemPostType.REGION, "Regions", "Region",
                "HTML that renders one kind of content, slotted into a layout by name",
                titleField(),
                field(ShSystemPostTypeAttr.HTML, "HTML", ShSystemWidget.ACE_HTML, 2, true),
                field(ShSystemPostTypeAttr.JAVASCRIPT, "JavaScript (browser)",
                        ShSystemWidget.ACE_JS, 3, false));
    }

    private ShPostType ensureTheme() {
        return ensure(ShSystemPostType.THEME, "Themes", "Theme",
                "The stylesheet and script a whole site shares",
                // SH252 — a stylesheet, not an HTML document. Note the limit of this fix: `ensure`
                // never touches an existing post-type (SH152's provision-never-upsert), so an
                // instance that already created its Theme keeps `Ace Editor - HTML` on this field.
                // That is the correct trade — the alternative is a startup that rewrites somebody's
                // model — and it means the defect persists on existing installs until somebody
                // changes the field by hand.
                titleField(),
                field(ShSystemPostTypeAttr.CSS, "CSS", ShSystemWidget.ACE_CSS, 2, false),
                field(ShSystemPostTypeAttr.JAVASCRIPT, "JavaScript",
                        ShSystemWidget.ACE_JS, 3, false));
    }

    private ShPostType ensure(String name, String plural, String title, String description,
                              FieldSpec... fields) {
        ShPostType existing = shPostTypeRepository.findByName(name);
        if (existing != null) {
            return existing;
        }
        ShPostType postType = new ShPostType();
        postType.setName(name);
        postType.setNamePlural(plural);
        postType.setTitle(title);
        postType.setDescription(description);
        postType.setSystem((byte) 1);
        postType.setDate(Instant.now());
        postType.setOwner("admin");

        Set<ShPostTypeAttr> attrs = new HashSet<>();
        for (FieldSpec spec : fields) {
            attrs.add(attr(spec, postType));
        }
        postType.setShPostTypeAttrs(attrs);
        return shPostTypeRepository.save(postType);
    }

    private ShPostTypeAttr attr(FieldSpec spec, ShPostType postType) {
        ShPostTypeAttr attr = new ShPostTypeAttr();
        attr.setName(spec.name());
        attr.setLabel(spec.label());
        attr.setDescription(spec.label());
        attr.setOrdinal(spec.ordinal());
        attr.setRequired(spec.required() ? (byte) 1 : (byte) 0);
        // SH277 — the title *is* a field, and this is where SH183 got it wrong. The comment that
        // stood here said the title is "the post's own column, not a field ... the thing the
        // curator types into the title box", which rests on a title box that does not exist: a
        // unified post's title column is derived by the writer from whichever field carries
        // isTitle, and `post.upsert` sends nothing but `data`. With no such field the column came
        // out empty, so no `sh-region` slot and no `postTypeLayout` binding could ever resolve to
        // a template written through the documented flow — and SH168 refused a `TITLE` key as
        // undeclared, so there was no way to set it either. The 2018 package this model is copied
        // from declares TITLE first, required and isTitle on all three; that part was dropped.
        attr.setIsTitle(spec.isTitle() ? (byte) 1 : (byte) 0);
        attr.setIsSummary((byte) 0);
        attr.setShWidget(widget(spec.widget()));
        attr.setShPostType(postType);
        return attr;
    }

    private ShWidget widget(String name) {
        ShWidget widget = shWidgetRepository.findByName(name);
        if (widget == null) {
            throw new IllegalStateException(
                    "Widget '" + name + "' is not seeded; cannot create the render post types");
        }
        return widget;
    }

    private FieldSpec field(String name, String label, String widget, int ordinal, boolean required) {
        return new FieldSpec(name, label, widget, ordinal, required);
    }

    /**
     * The title field every template needs (SH277).
     *
     * <p>Identical on all three, because the thing it names is the same on all three: the title is
     * how a slot ({@code sh-region="NAV"}), a binding ({@code postTypeLayout}) and a layout's
     * {@code THEME} field address a template. Required, because a template with no title is
     * content nothing can reach — the strongest form of SH193's {@code unused-region}.</p>
     */
    private FieldSpec titleField() {
        return new FieldSpec(ShSystemPostTypeAttr.TITLE, "Title", ShSystemWidget.TEXT, 1,
                true, true);
    }

    private record FieldSpec(String name, String label, String widget, int ordinal,
                            boolean required, boolean isTitle) {

        /** The ordinary form: a field that is not the post's title. */
        FieldSpec(String name, String label, String widget, int ordinal, boolean required) {
            this(name, label, widget, ordinal, required, false);
        }
    }
}
