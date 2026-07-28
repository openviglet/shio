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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;
import com.viglet.shio.render.ShRegionComposer.ShRenderedRegion;
import com.viglet.shio.render.ShRenderModel.ShThemeAssets;
import com.viglet.shio.utils.ShStableMap;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SH183 — page → layout → region, composed on the server with no script engine.
 *
 * <h2>The three levels, and why they are not one</h2>
 *
 * <p>This is the model the product already had (OpenText WEM / Vignette V7's page →
 * template → component, held as managed content rather than as files in a build), and the
 * granularity is the point: each level has a different owner and a different clock.</p>
 *
 * <table>
 *   <caption>The levels</caption>
 *   <tr><th>Level</th><th>Is</th><th>Changes</th></tr>
 *   <tr><td>Page</td><td>content plus a layout binding</td><td>constantly</td></tr>
 *   <tr><td>Layout</td><td>an HTML skeleton with named region slots</td><td>per template</td></tr>
 *   <tr><td>Region</td><td>HTML that renders one kind of content</td><td>per component</td></tr>
 * </table>
 *
 * <p>A region is therefore the unit of <b>reuse</b> (a header authored once appears on
 * every layout that slots it), of <b>editing</b> (which is what makes the Universal Editor
 * mean something — a curator edits a region, not a whole page), and of <b>caching</b>.</p>
 *
 * <h2>Everything it reads is content</h2>
 *
 * <p>Layouts, regions and themes are posts of system post-types, so an agent creates them
 * with {@code post.upsert}, they travel in the SH68 exchange package, they project to
 * files under {@code shio/content/**} and {@code shio verify} can lint them. A renderer
 * built instead as a closed Java projection of the SH95 section types would be none of
 * those: unextendable without a Java release, and invisible to every tool the agent
 * already has.</p>
 *
 * <h2>The binding is the map the one shipped example actually used</h2>
 *
 * <p>{@code site.postTypeLayout} is read as a flat {@code {"<post type>": "<layout>"}}
 * map. The entity can also express {@code ShSitePostTypeLayouts} — an array of
 * {@code {format, layout, mimeType, cacheTTL}} — and the format axis is a real idea, but
 * {@code openviglet/bootstrap-site} used the simple map and nothing has ever used the
 * array. It is treated as the extension it evidently was and is not paid for up front; a
 * value that is not a string says so rather than being guessed at.</p>
 *
 * <h2>Preview-grade, deliberately</h2>
 *
 * <p>Production delivery stays Next.js over the CDA (P10). A preview URL that renders
 * production content <em>is</em> a delivery path the moment somebody points DNS at it, and
 * then Shio owns HTML rendering, caching and SEO — the position §0.3 and §0.4 declined. If
 * that changes it has to be a decision taken in the open, not the consequence of a preview
 * route getting popular. Until then: a distinct route, a banner nobody can miss, and no
 * freshness headers.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
@Transactional(readOnly = true)
public class ShPageRenderService {

    private final ShSiteRepository shSiteRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShPostTypeRepository shPostTypeRepository;
    private final ShTemplateEngine engine;
    private final ShRegionComposer composer;
    private final ShRenderModel renderModel;
    private final ObjectMapper objectMapper;

    public ShPageRenderService(ShSiteRepository shSiteRepository,
                               ShPostUnifiedRepository shPostUnifiedRepository,
                               ShPostTypeRepository shPostTypeRepository,
                               ShTemplateEngine engine,
                               ShRegionComposer composer,
                               ShRenderModel renderModel,
                               ObjectMapper objectMapper) {
        this.shSiteRepository = shSiteRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shPostTypeRepository = shPostTypeRepository;
        this.engine = engine;
        this.composer = composer;
        this.renderModel = renderModel;
        this.objectMapper = objectMapper;
    }

    /**
     * Renders one page.
     *
     * @param siteKey the site's name, furl or id — SH181's rule, since a human types this
     * @param url     the page's friendly URL
     * @param state   {@code DRAFT} for preview, {@code PUBLISHED} to see what a visitor gets
     * @return the composed HTML
     */
    // SH249 — `noRollbackFor` because a caller may legitimately *catch* these. Every exception
    // this throws is a teaching 4xx over a read that wrote nothing, and `/agent/render` now calls
    // it speculatively: a post whose type has no layout binding is not a renderable page, and the
    // digest falls back rather than failing. Without this, that catch blows up at commit with
    // `UnexpectedRollbackException`, because the inner proxy joins the caller's transaction and
    // marks it rollback-only whether or not anybody handles the exception. Exactly the hazard SH171
    // documented for the agent read path, one service along — and the reason it is worth naming
    // twice is that the failure surfaces at commit, nowhere near the catch that caused it.
    @Transactional(readOnly = true, noRollbackFor = ShTeachingException.class)
    public ShRenderedPage render(String siteKey, String url, ShPostState state) {
        ShSite site = siteByKey(siteKey);
        ShPostUnified page = pageAt(site, url, state);
        // SH278 — the render's own state reaches every level: a preview shows the draft layout,
        // the draft region and the draft theme, which is what makes it a preview of the page the
        // curator is editing rather than a mix of what is live and what is not.
        ShPostUnified layout = layoutFor(site, page, state);

        ShThemeAssets theme = themeOf(site, layout, state);
        // SH297 — the layout names a theme and the site has not got one. Computed here rather than
        // inside themeOf because "no theme named" and "a theme named that is not there" are
        // different facts and only the second is a defect.
        String themeTitle = themeTitleOf(layout);
        String missingTheme = theme == null && themeTitle != null && !themeTitle.isBlank()
                ? themeTitle : null;
        Map<String, Object> model = new LinkedHashMap<>(renderModel.of(page, theme));
        // Reserved, like `post`/`site`/`theme`: it lets the helpers pick DRAFT vs PUBLISHED
        // and lets a layout render its own "you are looking at a draft" marker.
        model.put("render", ShStableMap.copyOf(Map.of("state", state.name())));
        Map<String, Object> stable = ShStableMap.copyOf(model);

        String layoutHtml = engine.render(fieldOf(layout, ShSystemPostTypeAttr.HTML), stable);
        List<String> slots = composer.slotsOf(layoutHtml);
        Map<String, ShRenderedRegion> regions = renderRegions(site, slots, stable, state);
        // SH281 — of the slots that did not resolve, which exist as an unpublished draft. The marker
        // needs to tell "create this region" from "publish the one you are looking at", because
        // reporting the second as the first is a wrong diagnosis rather than a missing one.
        Set<String> unpublishedSlots = unresolvedButDrafted(site, slots, regions, state);

        return new ShRenderedPage(
                // SH185: the page's own identity goes onto <body>, so a `sh-field` in a layout
                // needs no `sh-post` to say what it belongs to.
                composer.compose(layoutHtml, regions, page.getId(),
                        page.getShPostType() == null ? null : page.getShPostType().getName(),
                        unpublishedSlots, missingTheme),
                site.getName(),
                page.getFurl(),
                layout.getTitle(),
                slots,
                state,
                page.getId());
    }

    /**
     * The templates a post-type's pages are rendered through, without rendering one (SH251).
     *
     * <h3>Why the post-type and not a page</h3>
     *
     * <p>§XV.17 described SH251 as "a lint over resolved pages", and building it found that the
     * resolution does not depend on the page at all: a page reaches its layout through
     * {@code site.postTypeLayout}, which is keyed by <b>post-type</b>, and the layout's slots are
     * static attributes in its HTML. So every page of a type resolves to exactly the same layout
     * and the same regions. Iterating the bindings instead of the pages checks the identical
     * ground at a fraction of the cost — a thousand articles resolve once — and reports the
     * finding against the artefact somebody edits.</p>
     *
     * <p>Returns empty when the type has no binding, which is the normal case rather than an
     * error: {@code postTypeLayout} names the types that are <em>pages</em>, and a type that only
     * ever appears as a row in someone else's listing has none. The renderer throws for that
     * (a request named a page), a lint must not.</p>
     *
     * @param site     the site whose bindings resolve the layout
     * @param typeName the post-type the page has
     */
    public Optional<ShPageTemplates> templatesFor(ShSite site, String typeName) {
        String layoutName = layoutBindings(site).get(typeName);
        if (layoutName == null) {
            return Optional.empty();
        }
        // DRAFT-preferred: the SH251 lint and SH193's graph are about the template as it now stands,
        // which is the row an author is editing rather than the one a visitor currently gets.
        Optional<ShPostUnified> layout =
                postOfSystemType(site, ShSystemPostType.PAGE_LAYOUT, layoutName, ShPostState.DRAFT);
        if (layout.isEmpty()) {
            return Optional.empty();
        }
        String layoutHtml = fieldOf(layout.get(), ShSystemPostTypeAttr.HTML);
        List<ShNamedTemplate> regions = new ArrayList<>();
        // Read off the *unrendered* HTML, which is sound because `sh-region="NAME"` is a static
        // attribute — the one shape it cannot see is a name that is itself interpolated, and a
        // region slot whose name is content would be unresolvable to a reader too.
        for (String slot : composer.slotsOf(layoutHtml)) {
            postOfSystemType(site, ShSystemPostType.REGION, slot, ShPostState.DRAFT)
                    .ifPresent(region -> regions.add(new ShNamedTemplate(slot, region,
                            fieldOf(region, ShSystemPostTypeAttr.HTML))));
        }
        return Optional.of(new ShPageTemplates(
                new ShNamedTemplate(layout.get().getTitle(), layout.get(), layoutHtml),
                List.copyOf(regions)));
    }

    /**
     * {@code site.postTypeLayout} as the flat post-type → layout-title map, for a caller that
     * needs to know <em>which</em> types are pages (SH251) rather than to render one.
     */
    public Map<String, String> layoutBindings(ShSite site) {
        return bindingsOf(site);
    }

    /** A template with the post that holds it and the name it is reached by. */
    public record ShNamedTemplate(String name, ShPostUnified post, String html) {
    }

    /**
     * A post-type's resolved presentation: the layout, plus the Regions its slots resolve to, in
     * document order. Carries the HTML so a caller that is not rendering needs no second read.
     */
    public record ShPageTemplates(ShNamedTemplate layout, List<ShNamedTemplate> regions) {

        /** The layout and its regions, in the order a reader meets them. */
        public List<ShNamedTemplate> all() {
            List<ShNamedTemplate> out = new ArrayList<>();
            out.add(layout);
            out.addAll(regions);
            return List.copyOf(out);
        }
    }

    // --- resolution -------------------------------------------------------------

    private ShSite siteByKey(String siteKey) {
        // SH181's order: the declared key wins a collision.
        Optional<ShSite> byId = shSiteRepository.findById(siteKey);
        if (byId.isPresent()) {
            return byId.get();
        }
        ShSite byFurl = shSiteRepository.findByFurl(siteKey);
        if (byFurl != null) {
            return byFurl;
        }
        ShSite byName = shSiteRepository.findByName(siteKey);
        if (byName == null) {
            throw new UnknownSiteException(siteKey);
        }
        return byName;
    }

    private ShPostUnified pageAt(ShSite site, String url, ShPostState state) {
        String furl = url == null || url.isBlank() ? "/" : url;
        // DRAFT-preferred when previewing: a preview that showed the published row would
        // be showing the page the curator is trying to change.
        Optional<ShPostUnified> page = state == ShPostState.DRAFT
                ? first(shPostUnifiedRepository.findByShSite_IdAndFurlAndState(site.getId(), furl, ShPostState.DRAFT),
                        () -> shPostUnifiedRepository.findByShSite_IdAndFurlAndState(site.getId(), furl, ShPostState.PUBLISHED))
                : shPostUnifiedRepository.findByShSite_IdAndFurlAndState(site.getId(), furl, ShPostState.PUBLISHED);
        return page.orElseThrow(() -> new UnknownPageException(site.getName(), furl, state));
    }

    private Optional<ShPostUnified> first(Optional<ShPostUnified> preferred,
                                          java.util.function.Supplier<Optional<ShPostUnified>> fallback) {
        return preferred.isPresent() ? preferred : fallback.get();
    }

    /**
     * The page's layout, via {@code site.postTypeLayout}. Every refusal here names the
     * post-type and what to add, because the alternative — a blank page — is the failure
     * mode that made the original engine expensive to author against.
     */
    private ShPostUnified layoutFor(ShSite site, ShPostUnified page, ShPostState state) {
        String typeName = page.getShPostType() == null ? null : page.getShPostType().getName();
        Map<String, String> bindings = bindingsOf(site);
        String layoutName = bindings.get(typeName);
        if (layoutName == null) {
            throw new NoLayoutBindingException(site.getName(), typeName,
                    bindings.keySet().stream().sorted().toList());
        }
        return postOfSystemType(site, ShSystemPostType.PAGE_LAYOUT, layoutName, state)
                .orElseThrow(() -> missingLayout(site, typeName, layoutName, state));
    }

    /**
     * The right refusal for a layout that did not resolve (SH280).
     *
     * <p>Two situations that look identical from here and need different sentences. SH278 made a
     * PUBLISHED render read only published rows, which is correct — a visitor must not get an
     * unpublished edit — and it turned "the layout exists but nobody has published it" into a
     * failure where it used to silently render the draft. That is <b>the normal state of a site
     * built through the SH183 flow</b>, since {@code post.upsert} writes a DRAFT and publishing is a
     * separate act (P7). Telling that operator "no such layout" sends them to create a second one.</p>
     */
    private ShTeachingException missingLayout(ShSite site, String typeName, String layoutName,
                                              ShPostState state) {
        boolean existsAsDraft = state == ShPostState.PUBLISHED
                && postOfSystemType(site, ShSystemPostType.PAGE_LAYOUT, layoutName,
                        ShPostState.DRAFT).isPresent();
        return existsAsDraft
                ? new UnpublishedLayoutException(site.getName(), typeName, layoutName)
                : new MissingLayoutException(site.getName(), typeName, layoutName,
                        titlesOfSystemType(site, ShSystemPostType.PAGE_LAYOUT));
    }

    private Map<String, String> bindingsOf(ShSite site) {
        String raw = site.getPostTypeLayout();
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        Map<String, String> bindings = new LinkedHashMap<>();
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (node == null || !node.isObject()) {
                return Map.of();
            }
            for (String key : node.propertyNames()) {
                JsonNode value = node.get(key);
                if (value != null && value.isString()) {
                    bindings.put(key, value.stringValue());
                }
                // A non-string value is the ShSitePostTypeLayouts array form — the format
                // axis that was built and, in the one shipped example, never used. Skipped
                // rather than half-read, so the refusal above names the post-type instead
                // of a page rendering with a layout nobody chose.
            }
        } catch (JacksonException e) {
            return Map.of();
        }
        return bindings;
    }

    /**
     * Slots that produced no region here and do have a draft (SH281).
     *
     * <p>Only asked on a PUBLISHED render: on a draft render an unresolved slot really has no post,
     * because {@code statesFor(DRAFT)} already read both states. So this costs nothing in the case
     * that runs most, which is the preview.</p>
     */
    private Set<String> unresolvedButDrafted(ShSite site, List<String> slots,
                                             Map<String, ShRenderedRegion> rendered,
                                             ShPostState state) {
        if (state != ShPostState.PUBLISHED) {
            return Set.of();
        }
        Set<String> unpublished = new java.util.TreeSet<>();
        for (String name : slots) {
            if (!rendered.containsKey(name)
                    && postOfSystemType(site, ShSystemPostType.REGION, name, ShPostState.DRAFT)
                            .isPresent()) {
                unpublished.add(name);
            }
        }
        return unpublished;
    }

    private Map<String, ShRenderedRegion> renderRegions(ShSite site, List<String> slots,
                                                        Map<String, Object> model,
                                                        ShPostState state) {
        Map<String, ShRenderedRegion> rendered = new LinkedHashMap<>();
        for (String name : slots) {
            postOfSystemType(site, ShSystemPostType.REGION, name, state).ifPresent(region ->
                    rendered.put(name, new ShRenderedRegion(name, region.getId(),
                            engine.render(fieldOf(region, ShSystemPostTypeAttr.HTML), model),
                            // SH248 — passed through, never evaluated. The composer emits it
                            // as a <script> in the page; the JVM never sees it as code, which
                            // is the half of the Block D split that makes the other half safe.
                            fieldOf(region, ShSystemPostTypeAttr.JAVASCRIPT))));
        }
        return rendered;
    }

    private ShThemeAssets themeOf(ShSite site, ShPostUnified layout, ShPostState state) {
        String themeName = fieldOf(layout, ShSystemPostTypeAttr.THEME);
        if (themeName == null || themeName.isBlank()) {
            return null;
        }
        return postOfSystemType(site, ShSystemPostType.THEME, themeName, state)
                .map(theme -> new ShThemeAssets(
                        fieldOf(theme, ShSystemPostTypeAttr.CSS),
                        fieldOf(theme, ShSystemPostTypeAttr.JAVASCRIPT)))
                .orElse(null);
    }

    /**
     * A layout, region or theme by title within a site. Keyed on title because that is
     * what {@code sh-region="NAME"} and a {@code postTypeLayout} binding both name — the
     * 2018 package addresses them that way and its content imports byte-identically.
     */
    /**
     * One template by title, DRAFT-preferred on a draft render and deterministic always (SH278).
     *
     * <p>This used to read both states and take whichever row the database returned first, which was
     * undefined in two ways: between a template's own DRAFT and PUBLISHED rows — so a preview could
     * render the <em>published</em> layout while the curator was editing the draft, which is exactly
     * the failure SH247 extracted `preferDraft` to prevent for listings and nobody applied here — and
     * between two templates sharing a title (SH263), where the choice cannot be right but can at
     * least be the same on every engine and every run.</p>
     */
    private Optional<ShPostUnified> postOfSystemType(ShSite site, String typeName, String title,
                                                     ShPostState state) {
        return ShRenderState.preferDraft(postsOfSystemType(site, typeName, state), state).stream()
                .filter(post -> title.equals(post.getTitle()))
                .findFirst();
    }

    private List<String> titlesOfSystemType(ShSite site, String typeName) {
        // Every title, whatever the render state: this feeds a refusal that lists the choices, and a
        // layout that exists only as a draft is still a choice the author meant to have.
        return postsOfSystemType(site, typeName, ShPostState.DRAFT).stream()
                .map(ShPostUnified::getTitle)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    private List<ShPostUnified> postsOfSystemType(ShSite site, String typeName, ShPostState state) {
        ShPostType type = shPostTypeRepository.findByName(typeName);
        if (type == null) {
            return List.of();
        }
        // Keyed on the post-type's *id*, never `p.shPostType.name` — SH22's join hazard:
        // @TenantId lives on the JOINED ShObject root, so navigating past the id makes
        // Hibernate emit `tenant_id = ?` against the subclass table alias, which has no
        // such column, and the query dies at prepare time.
        return shPostUnifiedRepository.findByShSite_IdAndShPostType_IdAndStateIn(
                site.getId(), type.getId(), ShRenderState.statesFor(state));
    }

    /**
     * The title a layout names its theme by, or {@code null} (SH281).
     *
     * <p>Exposed so the lint reads the binding from the same place the renderer does: a theme is
     * reached through a layout's {@code THEME} field, and a second reader parsing that field would be
     * a second thing that can disagree about which theme a page uses.</p>
     */
    public String themeTitleOf(ShPostUnified layout) {
        return fieldOf(layout, ShSystemPostTypeAttr.THEME);
    }

    private String fieldOf(ShPostUnified post, String field) {
        Object value = renderModel.fieldsOf(post).get(field);
        return value == null ? null : value.toString();
    }

    /**
     * A rendered page and what it was built from — the slots are reported because "which
     * regions did this page actually use" is the question SH193 makes answerable and the
     * one a curator asks the moment a page looks wrong.
     */
    public record ShRenderedPage(String html, String site, String url, String layout,
                                 List<String> regions, ShPostState state, String postId) {
    }

    // --- teaching errors (SH80) ---------------------------------------------
    //
    // Named subclasses rather than inline construction — the convention on
    // ShAgentRenderService, and the reason ShTeachingException's constructor is
    // protected. An error a caller can be expected to hit is part of the surface, and
    // giving it a type is what stops one failure being worded three ways by three call
    // sites. Every one of these names what exists, because the alternative for a
    // renderer is a blank page, which is the failure mode that made the original engine
    // expensive to author against.

    /** A 404: the first path segment is not a site. */
    public static class UnknownSiteException extends ShTeachingException {

        public UnknownSiteException(String siteKey) {
            super(ShProblem.of("render-site-unknown", HttpStatus.NOT_FOUND,
                            "No site named '" + siteKey + "'")
                    .withDetail("A page renders inside one site, and this key matched no site "
                            + "by id, friendly URL or name.")
                    .withFix("GET /api/v2/site lists the sites this instance holds.")
                    .withExample("/preview/Blog/blog/hello-world"));
        }
    }

    /** A 404: the site is there, the page is not. */
    public static class UnknownPageException extends ShTeachingException {

        public UnknownPageException(String siteName, String furl, ShPostState state) {
            super(ShProblem.of("render-page-unknown", HttpStatus.NOT_FOUND,
                            "No page at '" + furl + "' in site '" + siteName + "'")
                    .withDetail("Looked for a " + state.name()
                            + " post whose friendly URL is '" + furl + "'.")
                    .withFix("GET /api/v2/agent/find?site=" + siteName
                            + " lists the addresses this site holds.")
                    .withExample("/preview/" + siteName + "/"));
        }
    }

    /** A 400 naming the post-types that <em>are</em> bound. */
    public static class NoLayoutBindingException extends ShTeachingException {

        public NoLayoutBindingException(String siteName, String typeName, List<String> bound) {
            super(ShProblem.badRequest("render-no-layout-binding",
                            "Site '" + siteName + "' binds no layout for post-type '"
                                    + typeName + "'")
                    .withDetail("A page is rendered by the " + ShSystemPostType.PAGE_LAYOUT
                            + " its post-type is bound to, and this site binds none for that type.")
                    .withAllowed(bound)
                    .withFix("Set postTypeLayout on the site to a map of post-type name to "
                            + ShSystemPostType.PAGE_LAYOUT + " title.")
                    .withExample("{\"" + typeName + "\": \"MY_LAYOUT\"}"));
        }
    }

    /** A 404: the binding names a layout nobody created — listing the ones that exist. */
    /**
     * The layout is there and is not published (SH280).
     *
     * <p>Its own type rather than a reworded {@code MissingLayoutException}, because the two carry
     * different {@code fix}es and a caller that branches on the problem type should be able to tell
     * "you have not written this yet" from "you have not published it". A {@code 409} rather than a
     * {@code 404}: the thing exists, the request conflicts with its state.</p>
     */
    public static class UnpublishedLayoutException extends ShTeachingException {

        public UnpublishedLayoutException(String siteName, String typeName, String layoutName) {
            super(ShProblem.conflict("render-layout-unpublished",
                            "Layout '" + layoutName + "' exists in site '" + siteName
                                    + "' but has never been published")
                    .withDetail("This is a PUBLISHED render, which reads only published rows at "
                            + "every level, so an unpublished layout cannot be used — what a visitor "
                            + "gets must not depend on an edit nobody has published. Site '" + siteName
                            + "' binds post-type '" + typeName + "' to that layout.")
                    .withFix("Publish the layout (a post.publish op on POST /api/v2/agent/batch), or "
                            + "render the preview instead, which reads drafts. A site built with "
                            + "post.upsert has draft templates until somebody publishes them."));
        }
    }

    public static class MissingLayoutException extends ShTeachingException {

        public MissingLayoutException(String siteName, String typeName, String layoutName,
                                      List<String> available) {
            super(ShProblem.of("render-layout-missing", HttpStatus.NOT_FOUND,
                            "Layout '" + layoutName + "' does not exist in site '" + siteName + "'")
                    .withDetail("Site '" + siteName + "' binds post-type '" + typeName
                            + "' to that layout.")
                    .withAllowed(available)
                    .withFix("Create a " + ShSystemPostType.PAGE_LAYOUT + " post titled '"
                            + layoutName + "', or point the binding at one that exists."));
        }
    }
}
