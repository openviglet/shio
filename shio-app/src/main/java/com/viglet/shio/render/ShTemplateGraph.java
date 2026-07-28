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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.render.ShPageRenderService.ShNamedTemplate;
import com.viglet.shio.render.ShPageRenderService.ShPageTemplates;

/**
 * SH193 — who depends on a layout or a region, so a shared-presentation edit has a blast radius.
 *
 * <h2>The question and why nothing could answer it</h2>
 *
 * <p>Composition is an attribute inside an HTML string — {@code <div sh-region="NAV">} — so
 * "which layouts slot this region, which pages use those layouts, how many are published" was
 * unanswerable. Editing a shared region was therefore an unbounded change: nobody could say what
 * it touched beforehand, and {@code verify} could not prove afterwards that nothing broke, because
 * it did not know what depended on it. That is the difference between a CMS region and a component
 * in one application, where the blast radius is the file.</p>
 *
 * <h2>Computed, not extracted — and that is a correction to the design</h2>
 *
 * <p>§XV.11 proposed extracting slot references at write time into {@code ShReference}, "which
 * already exists and already survives a move", following SH154's treatment of {@code Multi Select}
 * post URLs. <b>That is not available:</b> {@code ShReference} joins {@code object_from} and
 * {@code object_to} to {@code ShObject}, and a post is <em>not</em> an {@code ShObject} — the
 * unified model is its own table. A post-to-post edge would need a new table.</p>
 *
 * <p>It is not needed, and SH251 is the evidence. That task established that a page's presentation
 * resolves from its <b>post-type</b> rather than from the page: {@code site.postTypeLayout} is
 * keyed by type and a layout's slots are static attributes, so the whole forward index for a site
 * is (number of bindings) resolutions regardless of how many pages exist. Inverting it is a map
 * built in memory. Two properties come free that a persisted index would have had to earn:</p>
 *
 * <ul>
 *   <li><b>It cannot go stale.</b> An extracted edge has to be maintained when a layout is edited
 *       to drop a slot, when a region is renamed, and when {@code site.postTypeLayout} changes —
 *       the third being a {@code ShSite} write that no post-level extraction would ever see.</li>
 *   <li><b>It needs no migration and no backfill</b>, so it answers correctly for the 2018
 *       {@code bootstrap-site} package the moment it is imported.</li>
 * </ul>
 *
 * <p>What a persisted graph would buy is a cheap reverse query at scale. That is a real trade and
 * it is not this one: the cost here is bounded by bindings, and the page counts are one aggregate
 * query per type.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
@Transactional(readOnly = true)
public class ShTemplateGraph {

    private final ShPageRenderService renderService;
    private final ShPostUnifiedRepository shPostUnifiedRepository;

    public ShTemplateGraph(ShPageRenderService renderService,
                           ShPostUnifiedRepository shPostUnifiedRepository) {
        this.renderService = renderService;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
    }

    /**
     * One site's presentation graph, both directions.
     *
     * @param site the site whose {@code postTypeLayout} bindings define it
     */
    public ShSiteTemplateGraph of(ShSite site) {
        Map<String, String> bindings = renderService.layoutBindings(site);
        // type -> layout title, and the two inversions a caller actually asks for.
        Map<String, Set<String>> typesByLayout = new TreeMap<>();
        Map<String, Set<String>> layoutsByRegion = new TreeMap<>();
        Set<String> resolvedLayouts = new TreeSet<>();

        for (Map.Entry<String, String> binding : new TreeMap<>(bindings).entrySet()) {
            String typeName = binding.getKey();
            Optional<ShPageTemplates> templates = renderService.templatesFor(site, typeName);
            if (templates.isEmpty()) {
                // The binding names a layout that is not there. Already
                // `MissingLayoutException` when somebody asks for the page; the graph simply has
                // no edge for it, which is honest — an absent layout slots nothing.
                continue;
            }
            String layoutTitle = binding.getValue();
            resolvedLayouts.add(layoutTitle);
            typesByLayout.computeIfAbsent(layoutTitle, key -> new TreeSet<>()).add(typeName);
            for (ShNamedTemplate region : templates.get().regions()) {
                layoutsByRegion.computeIfAbsent(region.name(), key -> new TreeSet<>())
                        .add(layoutTitle);
            }
        }
        return new ShSiteTemplateGraph(site.getName(), site.getId(), typesByLayout,
                layoutsByRegion, resolvedLayouts);
    }

    /**
     * What a change to one template would affect, or empty when the post is not a template.
     *
     * <p>Takes the post rather than a title because the two system types share a namespace with
     * everything else: a {@code Region} called {@code NAV} and an {@code Article} called
     * {@code NAV} are different posts, and answering for the wrong one would be worse than
     * answering nothing.</p>
     */
    public Optional<ShTemplateImpact> impactOf(ShPostUnified post) {
        String typeName = post == null || post.getShPostType() == null
                ? null : post.getShPostType().getName();
        ShSite site = post == null ? null : post.getShSite();
        if (site == null || post.getTitle() == null
                || !(ShSystemPostType.REGION.equals(typeName)
                        || ShSystemPostType.PAGE_LAYOUT.equals(typeName))) {
            return Optional.empty();
        }
        ShSiteTemplateGraph graph = of(site);
        Set<String> layouts = ShSystemPostType.REGION.equals(typeName)
                ? graph.layoutsSlotting(post.getTitle())
                : Set.of(post.getTitle());
        Set<String> types = new TreeSet<>();
        for (String layout : layouts) {
            types.addAll(graph.typesUsing(layout));
        }
        return Optional.of(new ShTemplateImpact(
                ShSystemPostType.REGION.equals(typeName) ? "region" : "layout",
                post.getTitle(),
                List.copyOf(layouts),
                List.copyOf(types),
                pageCount(site, types, ShPostState.DRAFT),
                pageCount(site, types, ShPostState.PUBLISHED)));
    }

    /**
     * How many pages of these types the site holds.
     *
     * <p>Counted per state rather than deduplicated in memory: "how many are published" is the
     * number that decides whether an edit is safe to make now, and a DRAFT-preferred count would
     * quietly answer a different question.</p>
     */
    private int pageCount(ShSite site, Collection<String> typeNames, ShPostState state) {
        if (typeNames.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (ShPostUnified post : shPostUnifiedRepository
                .findByShSite_IdAndStateIn(site.getId(), List.of(state))) {
            String name = post.getShPostType() == null ? null : post.getShPostType().getName();
            if (name != null && typeNames.contains(name)) {
                total++;
            }
        }
        return total;
    }

    /** One site's graph: layout → the types that use it, region → the layouts that slot it. */
    public record ShSiteTemplateGraph(String siteName, String siteId,
                                      Map<String, Set<String>> typesByLayout,
                                      Map<String, Set<String>> layoutsByRegion,
                                      Set<String> boundLayouts) {

        public Set<String> typesUsing(String layoutTitle) {
            return typesByLayout.getOrDefault(layoutTitle, Set.of());
        }

        public Set<String> layoutsSlotting(String regionTitle) {
            return layoutsByRegion.getOrDefault(regionTitle, Set.of());
        }

        /**
         * Every region name any bound layout slots — the set a Region has to be in to be used.
         *
         * <p>A {@code TreeSet} and not {@code Set.copyOf}, which SH136's lint caught here: that
         * copy's iteration order comes from a per-JVM salt, so the same graph would iterate
         * differently after a restart. It is only a membership test today, which is exactly the
         * reasoning that would have justified an exemption — and the reason not to take one is
         * that the next caller to iterate or serialize this would inherit an unstable order
         * silently, with nothing to fail. The source is a {@code TreeMap}, so sorted is free.</p>
         */
        public Set<String> slottedRegions() {
            return new TreeSet<>(layoutsByRegion.keySet());
        }
    }

    /**
     * What one template change affects.
     *
     * @param kind      {@code region} or {@code layout}
     * @param title     the template's title, which is how a slot and a binding name it
     * @param layouts   the layouts involved — the region's slotters, or the layout itself
     * @param postTypes the post-types whose pages render through those layouts
     * @param pages     draft pages of those types in this site
     * @param published published pages of those types — the number that decides whether an edit
     *                  is safe to make right now
     */
    public record ShTemplateImpact(String kind, String title, List<String> layouts,
                                   List<String> postTypes, int pages, int published) {

        /** Whether anything at all renders through this template. */
        public boolean isUsed() {
            return !layouts.isEmpty() && !postTypes.isEmpty();
        }
    }

    /**
     * The templates a site holds that nothing renders through, by kind (SH193).
     *
     * <p>The inverse query, and the one that is a <em>defect</em> rather than information: a
     * Region no layout slots and a PageLayout no post-type binds are content nobody can reach,
     * and until now nothing could tell them from content that is merely not visited.</p>
     *
     * @return region titles and layout titles, each sorted
     */
    public ShUnusedTemplates unusedIn(ShSite site, Collection<ShPostUnified> templates) {
        ShSiteTemplateGraph graph = of(site);
        Set<String> slotted = graph.slottedRegions();
        List<ShPostUnified> regions = new ArrayList<>();
        List<ShPostUnified> layouts = new ArrayList<>();
        // LinkedHashMap keyed by title: a site with two Region posts of the same title is a
        // separate defect (the renderer resolves the first), and reporting it twice here would
        // describe it as two.
        Map<String, ShPostUnified> seenRegions = new LinkedHashMap<>();
        Map<String, ShPostUnified> seenLayouts = new LinkedHashMap<>();
        for (ShPostUnified post : templates) {
            String typeName = post.getShPostType() == null ? null : post.getShPostType().getName();
            String title = post.getTitle();
            if (title == null || title.isBlank()) {
                continue;
            }
            if (ShSystemPostType.REGION.equals(typeName) && !slotted.contains(title)) {
                seenRegions.putIfAbsent(title, post);
            } else if (ShSystemPostType.PAGE_LAYOUT.equals(typeName)
                    && graph.typesUsing(title).isEmpty()) {
                seenLayouts.putIfAbsent(title, post);
            }
        }
        regions.addAll(seenRegions.values());
        layouts.addAll(seenLayouts.values());
        return new ShUnusedTemplates(List.copyOf(regions), List.copyOf(layouts),
                new LinkedHashSet<>(graph.boundLayouts()));
    }

    /**
     * Titles that more than one template in this site claims (SH263).
     *
     * <p><b>Why a title is an identity here.</b> A layout reaches a region with
     * {@code sh-region="NAME"} and a site binds a post-type to a layout by title, so
     * {@code ShPageRenderService.postOfSystemType} resolves by title and takes the <em>first</em>
     * match. Two {@code Region} posts both called {@code NAV} therefore render whichever row the
     * query happens to return — intermittently, with no error anywhere, and SH193's graph counts the
     * slot once. That is SH202's duplicate-URL defect one namespace over: the identity is a title
     * rather than a friendly URL, and nothing enforced uniqueness because nothing had noticed a
     * title was an identity.</p>
     *
     * <p><b>Instance-wide within the site, never scoped.</b> "How many templates claim this name" is
     * a cross-cutting question, and SH143's rule is that a scope narrows the <em>subjects</em> and
     * not the indexes — a scoped run that only saw one of the two would answer that the name is
     * unique, which is the worst kind of false green. So this reads every render-type post in the
     * site and the caller decides which findings are its to report.</p>
     *
     * @return title → the ids claiming it, only for titles claimed more than once; both maps sorted
     */
    public Map<String, List<String>> duplicateTitlesIn(ShSite site) {
        Map<String, Map<String, String>> byTitle = new TreeMap<>();
        for (ShPostUnified post : shPostUnifiedRepository.findByShSite_IdAndStateIn(
                site.getId(), List.of(ShPostState.DRAFT, ShPostState.PUBLISHED))) {
            String typeName = post.getShPostType() == null ? null : post.getShPostType().getName();
            if (typeName == null || !ShRenderPostTypes.NAMES.contains(typeName)
                    || post.getTitle() == null || post.getTitle().isBlank()) {
                continue;
            }
            // Keyed by id inside, because a post has a DRAFT row and a PUBLISHED row that share
            // both title and id — counting rows would report every published template as its own
            // duplicate, which is the false positive that would make this rule useless.
            byTitle.computeIfAbsent(typeName + " '" + post.getTitle() + "'",
                            key -> new TreeMap<>())
                    .put(post.getId(), post.getId());
        }
        Map<String, List<String>> duplicates = new TreeMap<>();
        for (Map.Entry<String, Map<String, String>> entry : byTitle.entrySet()) {
            if (entry.getValue().size() > 1) {
                duplicates.put(entry.getKey(), List.copyOf(entry.getValue().keySet()));
            }
        }
        return duplicates;
    }

    /**
     * Bound layouts with no published row, where the site has published pages of that type (SH280).
     *
     * <p>SH278 made a PUBLISHED render read only published rows at every level, so an unpublished
     * layout stops a published page rendering — and that is the normal state of a site built through
     * the SH183 flow, since {@code post.upsert} writes a DRAFT and publishing is a separate act (P7).
     * The refusal now says so, but a refusal only reaches whoever asked for the page: the point of
     * reporting it is to find it before a visitor does.</p>
     *
     * <p><b>The published-pages condition is what keeps this from firing on every site mid-build.</b>
     * A draft layout bound to a type with no published pages is somebody working, not a defect; the
     * same layout with published pages behind it is a page that 404s in production. Same shape as
     * SH193's unused-template warnings — the difference between work in progress and something wrong
     * is almost always a second condition, not a stricter first one.</p>
     *
     * @return layout title → the post-types whose published pages cannot render, both sorted
     */
    public Map<String, Set<String>> unpublishedBoundLayouts(ShSite site) {
        Map<String, Set<String>> stranded = new TreeMap<>();
        for (Map.Entry<String, String> binding
                : new TreeMap<>(renderService.layoutBindings(site)).entrySet()) {
            String typeName = binding.getKey();
            String layoutTitle = binding.getValue();
            if (pageCount(site, Set.of(typeName), ShPostState.PUBLISHED) == 0) {
                continue;
            }
            boolean publishedLayout = ShRenderState
                    .preferDraft(templatesOfTitle(site, layoutTitle), ShPostState.PUBLISHED)
                    .stream()
                    .anyMatch(post -> post.getState() == ShPostState.PUBLISHED);
            if (!publishedLayout) {
                stranded.computeIfAbsent(layoutTitle, key -> new TreeSet<>()).add(typeName);
            }
        }
        return stranded;
    }

    /**
     * Regions and themes a published page needs and nobody has published (SH281).
     *
     * <p>The layout level refuses (SH280). These two <b>degrade</b>: an unresolved slot is skipped and
     * an unresolved theme yields no CSS, which is right for a preview and means the only way anybody
     * learns is a report. So the same published-pages condition applies — a draft region behind no
     * published page is somebody working — and the finding names what to publish.</p>
     *
     * @return {@code "Region 'NAV'"} → the layouts needing it, sorted; themes likewise
     */
    public Map<String, Set<String>> unpublishedDependencies(ShSite site) {
        Map<String, Set<String>> stranded = new TreeMap<>();
        for (Map.Entry<String, String> binding
                : new TreeMap<>(renderService.layoutBindings(site)).entrySet()) {
            if (pageCount(site, Set.of(binding.getKey()), ShPostState.PUBLISHED) == 0) {
                continue;
            }
            Optional<ShPageTemplates> templates =
                    renderService.templatesFor(site, binding.getKey());
            if (templates.isEmpty()) {
                continue;
            }
            String layoutTitle = binding.getValue();
            for (ShNamedTemplate region : templates.get().regions()) {
                if (!isPublished(site, ShSystemPostType.REGION, region.name())) {
                    stranded.computeIfAbsent("Region '" + region.name() + "'",
                            key -> new TreeSet<>()).add(layoutTitle);
                }
            }
            String themeTitle = renderService.themeTitleOf(templates.get().layout().post());
            if (themeTitle != null && !themeTitle.isBlank()
                    && !isPublished(site, ShSystemPostType.THEME, themeTitle)) {
                stranded.computeIfAbsent("Theme '" + themeTitle + "'", key -> new TreeSet<>())
                        .add(layoutTitle);
            }
        }
        return stranded;
    }

    /** Whether a render-type post with this title has a published row. */
    private boolean isPublished(ShSite site, String typeName, String title) {
        for (ShPostUnified post : shPostUnifiedRepository.findByShSite_IdAndStateIn(
                site.getId(), List.of(ShPostState.PUBLISHED))) {
            String name = post.getShPostType() == null ? null : post.getShPostType().getName();
            if (typeName.equals(name) && title.equals(post.getTitle())) {
                return true;
            }
        }
        return false;
    }

    /** Every render-type row in the site carrying this title, both states. */
    private List<ShPostUnified> templatesOfTitle(ShSite site, String title) {
        List<ShPostUnified> out = new ArrayList<>();
        for (ShPostUnified post : shPostUnifiedRepository.findByShSite_IdAndStateIn(
                site.getId(), List.of(ShPostState.DRAFT, ShPostState.PUBLISHED))) {
            String typeName = post.getShPostType() == null ? null : post.getShPostType().getName();
            if (ShSystemPostType.PAGE_LAYOUT.equals(typeName) && title.equals(post.getTitle())) {
                out.add(post);
            }
        }
        return out;
    }

    /** Templates nothing renders through, plus the layouts that are bound (for the message). */
    public record ShUnusedTemplates(List<ShPostUnified> regions, List<ShPostUnified> layouts,
                                    Set<String> boundLayouts) {
    }
}
