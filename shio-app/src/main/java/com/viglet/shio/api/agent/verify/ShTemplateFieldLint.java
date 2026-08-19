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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.stereotype.Component;

import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import com.viglet.shio.render.ShPageRenderService;
import com.viglet.shio.render.ShRenderHelpers;
import com.viglet.shio.render.ShRenderModel;
import com.viglet.shio.render.ShPageRenderService.ShNamedTemplate;
import com.viglet.shio.render.ShPageRenderService.ShPageTemplates;
import com.viglet.shio.render.ShTemplateEngine;
import com.viglet.shio.render.ShTemplateFieldScanner;
import com.viglet.shio.render.ShTemplateFieldScanner.ShHelperShapes;
import com.viglet.shio.render.ShTemplateFieldScanner.ShTemplateReference;

/**
 * SH251 — a template that interpolates a field nothing declares.
 *
 * <h2>The defect, and why SH192 could not catch it</h2>
 *
 * <p>SH192 refuses a template that will not <em>compile</em>, and §XV.10 wanted more: an unknown
 * field reference as an error "when the post-type is known". At write time it is not known. A
 * {@code Region}'s post-type declares {@code HTML} and {@code JAVASCRIPT}; the fields its
 * template interpolates belong to whatever page is later bound to the layout that slots it. So
 * {@code {{headline}}} against a post-type that renamed the field to {@code title} compiles,
 * renders empty, and looks exactly like missing content — SH168's defect wearing a template.</p>
 *
 * <h2>Two severities, because there are two kinds of interpolation</h2>
 *
 * <p><b>An error where the owner is known.</b> A top-level {@code {{TEXT}}} in a layout or region
 * resolves against the page's model, and the page's post-type is reachable through
 * {@code site.postTypeLayout}. If it declares no {@code TEXT}, that interpolation renders empty
 * on every page of that type, always. Nothing else in the system reports it.</p>
 *
 * <p><b>A warning inside a helper block</b>, and only when the bar is met. Inside
 * {@code {{#query}}} the rows are whatever posts the helper found, so the CMS cannot know their
 * shape — but it can know that <em>no post-type in the instance</em> declares the name, and that
 * a reference like that cannot resolve for any post whatsoever. Warning rather than error because
 * the population is not enumerable the way a bound post-type is, which is exactly the line SH99
 * draws: {@code ok} is false only for something the CMS can prove from inside itself.</p>
 *
 * <p>A name the row shape supplies ({@code title}, {@code link}, …) is never reported, or every
 * legitimate listing body would be a finding.</p>
 *
 * <h2>It iterates bindings, not pages</h2>
 *
 * <p>§XV.17 called for "a lint over resolved pages" and building it found the resolution does not
 * depend on the page: {@code postTypeLayout} is keyed by post-type and a layout's slots are static
 * attributes, so every page of a type resolves to the same layout and regions. So this walks the
 * bindings — a thousand articles resolve once — and addresses each finding at the <b>layout or
 * region post</b>, which is the artefact somebody edits to fix it. Being subject-addressed is also
 * what makes SH143's scoping work for free: a finding is emitted only when its template's post is
 * in the scanned set.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShTemplateFieldLint {

    /**
     * The reserved model frames (SH183): a path rooted at one of these is not a field and is never
     * reported.
     *
     * <p>SH292 — read from {@link ShRenderModel#FRAMES} rather than restated. This was a private copy
     * of a set three other places write, which is exactly the shape SH260 removed for the row keys
     * two lines below, and the pack now teaches the list — so a drift here would reach an author.</p>
     */
    private static final Set<String> FRAMES = ShRenderModel.FRAMES;

    // SH260 — the row's keys are `ShRenderHelpers.ROW_KEYS`, read rather than restated. This used
    // to be a copy, and its drift showed up as a warning on a correct listing body.

    private final ShPageRenderService renderService;
    private final ShPostTypeRepository shPostTypeRepository;
    private final ShTemplateEngine engine;

    public ShTemplateFieldLint(ShPageRenderService renderService,
                               ShPostTypeRepository shPostTypeRepository,
                               ShTemplateEngine engine) {
        this.renderService = renderService;
        this.shPostTypeRepository = shPostTypeRepository;
        this.engine = engine;
    }

    /**
     * @param sites      the readable sites
     * @param inScopeIds the ids of the posts this run is scanning (SH143); a finding whose
     *                   template is outside that set is not this run's to report
     */
    public List<ShTemplateFinding> scan(Collection<ShSite> sites, Set<String> inScopeIds) {
        Set<String> anyDeclared = allDeclaredNames();
        List<ShTemplateFinding> findings = new ArrayList<>();
        for (ShSite site : sites) {
            // Sorted, so a report is stable whatever order the bindings were written in (P5).
            for (String typeName : new TreeSet<>(renderService.layoutBindings(site).keySet())) {
                Optional<ShPageTemplates> resolved = renderService.templatesFor(site, typeName);
                if (resolved.isEmpty()) {
                    // A binding naming a layout that is not there. Already
                    // `ShPageRenderService.MissingLayoutException` when somebody asks for the
                    // page, and a second report for one defect is two reports that can disagree.
                    continue;
                }
                check(typeName, resolved.get(), inScopeIds, anyDeclared, findings);
            }
        }
        return List.copyOf(findings);
    }

    private void check(String typeName, ShPageTemplates templates, Set<String> inScopeIds,
                       Set<String> anyDeclared, List<ShTemplateFinding> findings) {
        Set<String> declared = declaredNames(typeName);
        if (declared.isEmpty()) {
            // A bound type with no fields at all is a modelling mistake of its own, and treating
            // every interpolation in its layout as undeclared would bury that in noise.
            return;
        }
        for (ShNamedTemplate template : templates.all()) {
            checkTemplate(template.post(), template.html(), typeName, declared, anyDeclared,
                    inScopeIds, findings);
        }
    }

    private void checkTemplate(ShPostUnified subject, String template, String typeName,
                               Set<String> declared, Set<String> anyDeclared,
                               Set<String> inScopeIds, List<ShTemplateFinding> findings) {
        if (subject == null || subject.getId() == null || !inScopeIds.contains(subject.getId())) {
            return;
        }
        // Deduplicated per (name, scope): a layout that writes {{headline}} in the header and the
        // footer has one defect, and reporting it twice makes a report the reader has to
        // de-duplicate — the same rule SH141 applies to routes and delivery.
        Set<String> reported = new LinkedHashSet<>();
        // SH260 — the scanner is told what the engine registered, so a helper's own name is never
        // read as an undeclared field and the two cannot drift.
        ShHelperShapes helpers = ShHelperShapes.of(engine.registeredHelpers());
        for (ShTemplateReference reference : ShTemplateFieldScanner.referencesIn(template, helpers)) {
            String root = reference.root();
            if (FRAMES.contains(root)) {
                continue;
            }
            if (reference.isPageScoped()) {
                if (!declared.contains(root) && reported.add("page:" + root)) {
                    findings.add(undeclaredForType(subject, typeName, root, declared));
                }
                continue;
            }
            // SH247 — the keys of the block this reference sits in, not "a listing's keys". A
            // `{{#form}}` yields a post-type's *declaration*, so `{{label}}` and `{{input}}` are
            // legitimate there and nowhere else; asking the helper is what keeps this from
            // warning on a correct template, which is the one failure mode a field lint cannot
            // afford — an author who sees a false finding stops reading the true ones.
            if (!ShRenderHelpers.rowKeysFor(reference.helperScope()).contains(root)
                    && !anyDeclared.contains(root)
                    && reported.add("row:" + root)) {
                findings.add(declaredNowhere(subject, reference, root));
            }
        }
    }

    private ShTemplateFinding undeclaredForType(ShPostUnified subject, String typeName,
                                                String field, Set<String> declared) {
        return new ShTemplateFinding(subject, "template-unknown-field", true, field,
                "This template interpolates `{{" + field + "}}`, which the post-type '" + typeName
                        + "' does not declare — so it renders empty on every page of that type. "
                        + "'" + typeName + "' declares: " + String.join(", ", declared) + ".",
                "Rename the reference to the field it means, or add `" + field + "` to the "
                        + "post-type (PUT /api/v2/post-type/" + typeName + "). A renamed field is "
                        + "the usual cause: the post-type moved and the template did not.");
    }

    private ShTemplateFinding declaredNowhere(ShPostUnified subject, ShTemplateReference reference,
                                              String field) {
        return new ShTemplateFinding(subject, "template-unknown-field", false, field,
                "Inside `{{#" + reference.helperScope() + "}}` this template interpolates `{{" + field
                        + "}}`, and no post-type in this instance declares a field of that name — "
                        + "so it cannot resolve for any post the block lists.",
                "Rename it to a field the listed posts have, or to one of the values `{{#"
                        + reference.helperScope() + "}}` provides ("
                        + String.join(", ",
                                new TreeSet<>(ShRenderHelpers.rowKeysFor(reference.helperScope()))) + "). "
                        + "This is a warning rather than an error because the rows come from a "
                        + "helper, and which posts it finds is not something the CMS can settle.");
    }

    private Set<String> declaredNames(String typeName) {
        ShPostType type = shPostTypeRepository.findByName(typeName);
        return type == null ? Set.of() : namesOf(type);
    }

    /**
     * Every field name any post-type declares — the bar for the in-block warning.
     *
     * <p>Instance-wide rather than site-wide on purpose: a helper block can list posts from the
     * folder the page is in, and narrowing this to one site would report a name that a type in
     * another site legitimately declares. The claim being made is deliberately weak — "nothing
     * anywhere has this field" — because a weak claim that is always true beats a strong one that
     * is sometimes wrong (SH192's false-positive rule).</p>
     */
    private Set<String> allDeclaredNames() {
        Set<String> names = new LinkedHashSet<>();
        for (ShPostType type : shPostTypeRepository.findAll()) {
            names.addAll(namesOf(type));
        }
        return names;
    }

    /** Top-level and nested field names — a Relator's children count, or they read as unknown. */
    private Set<String> namesOf(ShPostType type) {
        Set<String> names = new LinkedHashSet<>();
        if (type.getShPostTypeAttrs() == null) {
            return names;
        }
        for (ShPostTypeAttr attr : type.getShPostTypeAttrs()) {
            if (attr.getName() != null && !attr.getName().isBlank()) {
                names.add(attr.getName());
            }
        }
        return names;
    }

    /**
     * One finding, in this lint's own terms.
     *
     * <p>Deliberately not an {@code ShAgentReport.Finding}: the addressing is the verify
     * service's, which owns {@code address(post)} and the SH172 {@code id:} fallback, and a
     * second place that built an address would be a second place that could disagree about
     * one.</p>
     */
    public record ShTemplateFinding(ShPostUnified subject, String rule, boolean error,
                                    String field, String message, String fix) {
    }
}
