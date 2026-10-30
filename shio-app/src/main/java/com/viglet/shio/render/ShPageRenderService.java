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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.agent.ShAddress;
import com.viglet.shio.api.agent.diagnostics.ShAgentIncidentFrames;
import com.viglet.shio.api.agent.diagnostics.ShAgentIncidentLog;
import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.api.post.ShContentPerspective;
import com.viglet.shio.api.post.revision.ShPostRevisionService;
import com.viglet.shio.api.post.revision.ShRevisionRow;
import com.viglet.shio.api.post.unified.ShPostStateNotFoundException;
import com.viglet.shio.api.site.ShSiteLocaleService;
import com.viglet.shio.api.site.ShSiteLocales;
import com.viglet.shio.persistence.model.post.revision.ShPostRevision;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;
import com.viglet.shio.post.type.ShPostFieldFlags;
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;
import com.viglet.shio.render.ShRegionComposer.ShRenderedRegion;
import com.viglet.shio.render.ShRenderModel.ShThemeAssets;
import com.viglet.shio.url.ShFurlSpellings;
import com.viglet.shio.utils.ShStableMap;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
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
// SH345's rule, on the class rather than on one method: this renderer throws teaching exceptions
// (no such site, no layout bound) from a read-only transaction, and marking that rollback-only can
// only destroy the caller's ability to report the refusal. It was on the three-argument `render`
// alone, which SH259's four-argument overload then bypassed — `/preview` calls the overload, so a
// teaching 404 became an UnexpectedRollbackException at commit. The lint that exists to catch this
// missed it because its escape searched the whole file rather than the annotation.
@Transactional(readOnly = true,
        noRollbackFor = { ShTeachingException.class, ShPageRenderService.ShPageRedirectException.class })
public class ShPageRenderService {

    private final ShSiteRepository shSiteRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShPostTypeRepository shPostTypeRepository;
    private final ShTemplateEngine engine;
    private final ShRegionComposer composer;
    private final ShRenderModel renderModel;
    private final ObjectMapper objectMapper;
    private final ShAgentIncidentLog incidentLog;
    /** SH990 — what a page said at a revision, for {@code state=rev:<n>}. */
    private final ShPostRevisionService shPostRevisionService;
    /** SH1057 — the site's fallback chain, which decides what a reader asking for a locale gets. */
    private final ShSiteLocaleService shSiteLocaleService;

    public ShPageRenderService(ShSiteRepository shSiteRepository,
                               ShPostUnifiedRepository shPostUnifiedRepository,
                               ShPostTypeRepository shPostTypeRepository,
                               ShTemplateEngine engine,
                               ShRegionComposer composer,
                               ShRenderModel renderModel,
                               ObjectMapper objectMapper,
                               ShAgentIncidentLog incidentLog,
                               ShPostRevisionService shPostRevisionService,
                               ShSiteLocaleService shSiteLocaleService) {
        this.shPostRevisionService = shPostRevisionService;
        this.shSiteLocaleService = shSiteLocaleService;
        this.shSiteRepository = shSiteRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shPostTypeRepository = shPostTypeRepository;
        this.engine = engine;
        this.composer = composer;
        this.renderModel = renderModel;
        this.objectMapper = objectMapper;
        this.incidentLog = incidentLog;
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
    @Transactional(readOnly = true,
        noRollbackFor = { ShTeachingException.class, ShPageRedirectException.class })
    public ShRenderedPage render(String siteKey, String url, ShPostState state) {
        return render(siteKey, url, state, Map.of());
    }

    /**
     * As {@link #render(String, String, ShPostState)}, with the reader's query string (SH259).
     *
     * <h2>What a template is allowed to know about the request</h2>
     *
     * <p>SH247's {@code {{#search q="…"}}} takes a term the <em>template</em> supplies, which
     * builds a tag page or a curated collection and is not what an author means by "search". A
     * results page needs {@code ?q=}, and the model deliberately had no request in it: SH183's
     * non-goal resolution was that a layout is content and must never have the raw
     * {@code HttpServletRequest} in scope, which is exactly what made FSP's engine a sandboxing
     * liability.</p>
     *
     * <p>So the question was never how to pass a parameter. It is what a template may know, and the
     * answer is deliberately the smallest thing that serves search, pagination and a filter: one
     * {@code param} frame of <b>query-string values as strings</b>. Read-only, never headers, never
     * cookies, never the request object. That is nothing like handing a template the request, and
     * it is the only frame whose content comes from the reader rather than from the content.</p>
     *
     * @param params query-string values, already flattened to one string per name
     */
    // SH357 — the same annotation, deliberately repeated rather than inherited from the overload
    // above. That overload's delegation to this one is a self-invocation, so it never crosses the
    // proxy: a caller entering *here* — which is every `/preview/**` request carrying a query
    // string — would otherwise get neither the read-only transaction nor the `noRollbackFor` the
    // comment above explains at length. Which of the two a caller picked decided whether a caught
    // teaching 4xx blew up at commit. `ShAgentTransactionalOverloadLintTest` refuses the next pair.
    @Transactional(readOnly = true,
        noRollbackFor = { ShTeachingException.class, ShPageRedirectException.class })
    public ShRenderedPage render(String siteKey, String url, ShPostState state,
                                 Map<String, String> params) {
        ShSite site = siteByKey(siteKey);
        return renderPage(site, pageAt(site, url, state), state, params, null);
    }

    /**
     * SH1057 — as {@link #render(String, String, ShPostState, Map)}, for a reader who asked for a
     * locale.
     *
     * <p>The public route parsed its locale segment into the link prefix and nothing read it, so on
     * a site with two languages every locale URL rendered the same page. This is where the segment
     * reaches the resolution: see {@link #pageInLocale} for what "the page in pt-br" means when a
     * translation lives at its own URL.</p>
     *
     * @param locale the locale the URL named, or null/blank for the page at the path, whatever its
     *               language — which is what every call made before this one got
     * @throws ShPageRedirectException 302 to a translation at its own URL, when that is the answer
     */
    @Transactional(readOnly = true,
        noRollbackFor = { ShTeachingException.class, ShPageRedirectException.class })
    public ShRenderedPage render(String siteKey, String url, ShPostState state,
                                 Map<String, String> params, String locale) {
        ShSite site = siteByKey(siteKey);
        ShPostUnified page = locale == null || locale.isBlank()
                ? pageAt(site, url, state)
                : pageInLocale(site, url, state, locale.trim());
        return renderPage(site, page, state, params, null);
    }

    /**
     * SH1014 — the page as it <em>would</em> read, from values nobody has saved.
     *
     * <h2>Why this is not a new renderer</h2>
     *
     * <p>A curator could not see a change without saving it, and an agent could not either: a dry
     * run proves a write is legal and says nothing about how the page will look. What was missing
     * was never a renderer — it was a way to hand this one a document that is not in the database.
     * SH990's revision path already does exactly that, and says so: {@code asOf} builds a
     * <b>transient</b> copy carrying the present page's site, folder, type, address and locale, with
     * a different document in place of the stored one. This is that split again, with the document
     * coming from the caller instead of from a revision.</p>
     *
     * <h2>It merges, and it writes nothing</h2>
     *
     * <p>A field the override does not name keeps its stored value — the same rule as the merge
     * verb (SH335), which is what lets an agent ask "how would the page look with <em>this</em>
     * headline" without restating the document. And the copy is never passed to a repository: the
     * transaction is {@code readOnly}, nothing here calls a writer, so no revision is captured, no
     * webhook fires, nothing is indexed and nothing reaches the trash.
     * {@code ShProposedRenderWritesNothingTest} is the assertion, because "I did not call a writer"
     * is a claim about code that will be edited by somebody who has not read this paragraph.</p>
     *
     * @param proposed field values to lay over the stored document; {@code null} renders the row
     */
    @Transactional(readOnly = true,
        noRollbackFor = { ShTeachingException.class, ShPageRedirectException.class })
    public ShRenderedPage renderProposed(String siteKey, String url, ShPostState state,
                                         JsonNode proposed, Map<String, String> params) {
        ShSite site = siteByKey(siteKey);
        ShPostUnified stored = pageAt(site, url, state);
        return renderPage(site, proposedCopy(stored, proposed), state, params, null);
    }

    /**
     * SH1323 — the page at {@code url}, with {@code section} carrying {@code proposed} values.
     *
     * <p>A section has no layout of its own, so {@link #renderProposed} of one has nothing to draw,
     * and a curator edited a page's headline without seeing the page. This renders the page that
     * uses the section, as stored, with a transient copy of the section in its place for this one
     * render: answered by its id and by its friendly URL, the two spellings a Sections field holds.
     * Same merge and the same read-only transaction as {@link #renderProposed}, so nothing is
     * written either.</p>
     */
    @Transactional(readOnly = true,
        noRollbackFor = { ShTeachingException.class, ShPageRedirectException.class })
    public ShRenderedPage renderProposedOn(String siteKey, String url, ShPostState state,
                                           ShPostUnified section, JsonNode proposed) {
        ShSite site = siteByKey(siteKey);
        ShPostUnified page = pageAt(site, url, state);
        ShPostUnified copy = proposedCopy(section, proposed);
        Map<String, ShPostUnified> swapped = new java.util.HashMap<>();
        swapped.put(section.getId(), copy);
        if (section.getFurl() != null && !section.getFurl().isBlank()) {
            swapped.put(section.getFurl(), copy);
        }
        return ShRenderHelpers.withTransient(swapped, () -> renderPage(site, page, state, Map.of(), null));
    }

    /**
     * SH1105 — what one section of {@code typeName} looks like on this page, as HTML.
     *
     * <p>A curator choosing between a hero and a rich-text block is choosing a shape, and a sentence
     * is the worst way to show one. So the page renders through its own layout (the preview path,
     * never a second one) with {@code field} proposed as one section: a transient post of that type
     * whose fields carry their own labels as placeholder text. Nothing is stored, and the section
     * exists only for this render ({@link ShRenderHelpers#withTransient}).</p>
     *
     * <p>What comes back is the page's {@code <head>} (its theme, so the section is styled as the
     * site styles it) and the one element marked {@code data-shio-section} that holds the transient
     * post, scripts removed: a thumbnail runs nothing. HTML, not a raster, which keeps it inside the
     * non-goal that the instance does not run a browser.</p>
     *
     * @return empty when the page or the type is unknown, or the template marks no section element
     */
    @Transactional(readOnly = true,
        noRollbackFor = { ShTeachingException.class, ShPageRedirectException.class })
    public Optional<String> sectionPreview(String pageId, String field, String typeName) {
        List<ShPostUnified> rows = shPostUnifiedRepository.findById(pageId);
        ShPostType type = typeName == null ? null : shPostTypeRepository.findByName(typeName);
        if (rows.isEmpty() || type == null || field == null || field.isBlank()) {
            return Optional.empty();
        }
        ShPostUnified stored = rows.stream().filter(row -> row.getState() == ShPostState.DRAFT)
                .findFirst().orElse(rows.getFirst());
        ShSite site = stored.getShSite();
        if (site == null) {
            return Optional.empty();
        }
        ObjectNode placeholder = objectMapper.createObjectNode();
        type.getShPostTypeAttrs().stream()
                .sorted(java.util.Comparator.comparing(
                        (com.viglet.shio.persistence.model.post.type.ShPostTypeAttr attr) -> attr.getOrdinal())
                        .thenComparing(com.viglet.shio.persistence.model.post.type.ShPostTypeAttr::getName))
                // Only text reads as a label. A label in a File field is a broken image and in a
                // combo box a choice that does not exist, so those render as the template's empty case.
                .filter(attr -> attr.getShWidget() != null
                        && PLACEHOLDER_WIDGETS.contains(attr.getShWidget().getName()))
                .forEach(attr -> placeholder.put(attr.getName(),
                        attr.getLabel() == null || attr.getLabel().isBlank() ? attr.getName() : attr.getLabel()));
        ShPostUnified section = new ShPostUnified();
        section.setId("sh-preview-" + java.util.UUID.randomUUID());
        section.setState(stored.getState());
        section.setShSite(site);
        section.setShFolder(stored.getShFolder());
        section.setShPostType(type);
        section.setTitle(type.getTitle() == null ? type.getName() : type.getTitle());
        section.setFurl("/" + section.getId());
        section.setData(placeholder.toString());
        ObjectNode proposed = objectMapper.createObjectNode();
        proposed.putArray(field).add(section.getId());
        String html = ShRenderHelpers.withTransient(Map.of(section.getId(), section),
                () -> renderPage(site, proposedCopy(stored, proposed), stored.getState(), Map.of(), null).html());
        return Optional.ofNullable(cropToSection(html, section.getId()));
    }

    /** The widgets whose placeholder is the field's own label. */
    private static final java.util.Set<String> PLACEHOLDER_WIDGETS =
            java.util.Set.of("Text", "Text Area", "HTML Editor");

    /** The page's head and the one section element holding {@code postId}, or null if none is marked. */
    static String cropToSection(String html, String postId) {
        org.jsoup.nodes.Document page = org.jsoup.Jsoup.parse(html);
        org.jsoup.select.Elements marked = page.select("[" + ShRegionComposer.ANNOTATION_SECTION + "]");
        org.jsoup.nodes.Element section = marked.stream()
                .filter(element -> !element.select("[data-shio-post=\"" + postId + "\"]").isEmpty()
                        || postId.equals(element.attr("data-shio-post")))
                .findFirst()
                // A template that marks its section element but annotates no field inside it: the
                // proposed page holds this one section, so a lone marked element is it.
                .orElse(marked.size() == 1 ? marked.first() : null);
        if (section == null) {
            return null;
        }
        page.select("script").remove();
        section.select("script").remove();
        return "<!doctype html><html><head>" + page.head().html() + "</head><body>"
                + section.outerHtml() + "</body></html>";
    }

    /**
     * A transient page carrying the caller's values over the stored ones.
     *
     * <p>Everything that is a fact about <em>where</em> the page lives comes from the stored row, so
     * the override cannot move a post, change its type or rename its URL by way of a preview. Only
     * the document differs, which is the whole of what a preview is asking about.</p>
     */
    private ShPostUnified proposedCopy(ShPostUnified stored, JsonNode proposed) {
        if (proposed == null || !proposed.isObject()) {
            return stored;
        }
        ObjectNode merged = objectMapper.createObjectNode();
        JsonNode current = readData(stored);
        if (current != null && current.isObject()) {
            merged.setAll((ObjectNode) current);
        }
        merged.setAll((ObjectNode) proposed);

        ShPostUnified as = new ShPostUnified();
        as.setId(stored.getId());
        as.setState(stored.getState());
        as.setShSite(stored.getShSite());
        as.setShFolder(stored.getShFolder());
        as.setShPostType(stored.getShPostType());
        as.setFurl(stored.getFurl());
        as.setLocale(stored.getLocale());
        as.setTranslationGroupId(stored.getTranslationGroupId());
        as.setOwner(stored.getOwner());
        as.setDate(stored.getDate());
        as.setModifiedDate(stored.getModifiedDate());
        // The title follows the document where the override names it, so a preview's <title> and
        // its <h1> cannot disagree about the headline the curator just typed.
        JsonNode title = merged.get("title");
        as.setTitle(title != null && title.isString() ? title.asString() : stored.getTitle());
        as.setData(merged.toString());
        return as;
    }

    /**
     * The stored document, or nothing — and it says so when it is nothing.
     *
     * <p>SH270's rule, which this path meets for the same reason the two it named do: a preview
     * whose base document would not parse renders with <em>only</em> the overridden fields, and the
     * symptom is a page that looks emptier than the curator expects. Silence is the one shape
     * nobody investigates, so the degradation is recorded rather than swallowed.</p>
     */
    private JsonNode readData(ShPostUnified post) {
        if (post.getData() == null) {
            return null;
        }
        try {
            return objectMapper.readTree(post.getData());
        } catch (JacksonException e) {
            incidentLog.record(ShAgentIncidentLog.Kind.CONTENT,
                    post.getShSite() == null ? null
                            : ShAddress.post(post.getShSite().getName(), post.getFurl()).toString(),
                    "This post's stored document is not valid JSON, so a preview of unsaved values "
                            + "rendered with only the fields that were overridden: "
                            + e.getOriginalMessage(),
                    "Read the post and rewrite its data with post.upsert. Until then every preview "
                            + "of it shows less than the page holds, which reads as content having "
                            + "gone missing rather than as a document that will not parse.",
                    ShAgentIncidentFrames.of(e),
                    post.getShSite() == null ? null : post.getShSite().getId());
            return null;
        }
    }

    /**
     * A page from whichever perspective {@code state=} named: a row, or a captured revision (SH990).
     *
     * <h2>A revision renders at the page's present address, through its present templates</h2>
     *
     * <p>The page is found draft-preferred, exactly as a preview finds it, and the revision's document
     * is put in its place with {@link ShPostRevisionService#asOf}. Layout, regions and theme are read at
     * the state the revision was captured from, so a revision of what went live renders through the
     * published layout. That is the question a curator asks of a revision — what did this page say —
     * and not "what did the whole site look like then", which the revision store does not hold.</p>
     *
     * @param perspective the parsed {@code state=}; the default reads the draft, as a preview does
     */
    @Transactional(readOnly = true,
        noRollbackFor = { ShTeachingException.class, ShPageRedirectException.class })
    public ShRenderedPage render(String siteKey, String url, ShContentPerspective perspective,
                                 Map<String, String> params) {
        if (!perspective.isRevision()) {
            return render(siteKey, url, perspective.rowOr(ShPostState.DRAFT), params);
        }
        ShSite site = siteByKey(siteKey);
        ShPostUnified present = pageAt(site, url, ShPostState.DRAFT);
        int number = perspective.revision();
        ShPostRevision revision = shPostRevisionService.revision(present.getId(), number)
                .orElseThrow(() -> ShPostStateNotFoundException.noSuchRevision(present.getId(), number,
                        shPostRevisionService.rows(present.getId()).stream()
                                .map(ShRevisionRow::version).toList()));
        ShPostUnified asOf = shPostRevisionService.asOf(revision, present);
        return renderPage(site, asOf, asOf.getState(), params, number);
    }

    private ShRenderedPage renderPage(ShSite site, ShPostUnified page, ShPostState state,
                                      Map<String, String> params, Integer revision) {
        // SH385 — a replication that renames a path breaks every link into it: search results, other
        // people's pages, a decade of email. A `Redirect` answers the old path, and it is found by the
        // read that just ran rather than by a lookup of its own, because its friendly URL *is* the
        // path it answers. So this costs one type-name comparison and no query at all.
        redirectFor(page).ifPresent(redirect -> {
            throw redirect;
        });
        // SH278 — the render's own state reaches every level: a preview shows the draft layout,
        // the draft region and the draft theme, which is what makes it a preview of the page the
        // curator is editing rather than a mix of what is live and what is not.
        ShPostUnified layout = layoutFor(site, page, state);

        ShThemeAssets theme = themeOf(site, layout, state);
        // SH389 — resolved beside the theme because it is the same kind of fact: a property of the
        // site rather than of the page, read through the SH311 per-type cache that is already warm.
        Map<String, Object> scripts = siteScriptsOf(site, state);
        // SH297 — the layout names a theme and the site has not got one. Computed here rather than
        // inside themeOf because "no theme named" and "a theme named that is not there" are
        // different facts and only the second is a defect.
        String themeTitle = themeTitleOf(layout);
        String missingTheme = theme == null && themeTitle != null && !themeTitle.isBlank()
                ? themeTitle : null;
        // SH829 — the title reaches the model as well as the missing-theme marker, so a layout can
        // name the sheet it emits and the digest's region table can print `VigR3 (captured)` where
        // it printed `style[2]`. Same value, one resolution: `themeTitle` is what the layout names
        // and, when `theme` is non-null, what the post resolving it is called.
        Map<String, Object> model =
                new LinkedHashMap<>(renderModel.of(page, theme, scripts, themeTitle));
        // Reserved, like `post`/`site`/`theme`: it lets the helpers pick DRAFT vs PUBLISHED
        // and lets a layout render its own "you are looking at a draft" marker.
        //
        // SH383 added `defaultLocale`, and it is here rather than on `site` — which is what the
        // design asked for — because there **is** no per-site default locale in this model. The
        // value is `ShPostUnifiedWriteService.DEFAULT_LOCALE`, instance-wide, the same one the
        // context pack publishes; calling it `site.defaultLocale` would promise a setting nobody can
        // set, and a template built on that promise would break the day one arrives. When a per-site
        // default lands it can appear on `site` beside this without moving it.
        //
        // It exists because `{{#if}}` on a locale needs something to compare against: emitting
        // `hreflang` on the default-language page, or picking the `x-default` of a set, is a
        // comparison a template cannot make from `post.locale` alone.
        model.put("render", ShStableMap.copyOf(new LinkedHashMap<>(Map.of(
                "state", state.name(),
                "defaultLocale", ShPostUnifiedWriteService.DEFAULT_LOCALE))));
        // SH259 — the reader's query, and nothing else of the request. Sorted rather than in
        // arrival order: the model feeds markup SH101 hashes, and two readers sending the same
        // parameters in a different order must not produce two digests.
        model.put("param", ShStableMap.copyOf(new java.util.TreeMap<>(
                params == null ? Map.<String, String>of() : params)));
        Map<String, Object> stable = ShStableMap.copyOf(model);

        String layoutHtml = engine.render(fieldOf(layout, ShSystemPostTypeAttr.HTML), stable);
        List<String> slots = composer.slotsOf(layoutHtml);
        Map<String, ShRenderedRegion> regions = renderRegions(site, slots, stable, state);
        // SH281 — of the slots that did not resolve, which exist as an unpublished draft. The marker
        // needs to tell "create this region" from "publish the one you are looking at", because
        // reporting the second as the first is a wrong diagnosis rather than a missing one.
        Set<String> unpublishedSlots = unresolvedButDrafted(site, slots, regions, state);

        // SH774 — composed once and read twice: the banner asks what this render produced, and
        // producing it twice would be a second answer that can differ from the one served.
        String composed = composer.compose(layoutHtml, regions, page.getId(),
                page.getShPostType() == null ? null : page.getShPostType().getName(),
                unpublishedSlots, missingTheme,
                // SH1037 — what KIND each of the page's fields is, so an annotated element can say
                // so and the browser can offer the right affordance instead of contenteditable HTML
                // over everything.
                declaredWidgets(page.getShPostType()),
                // SH1175 — and each section's, which is a post of its own type.
                this::widgetsOfOwners);
        return new ShRenderedPage(
                // SH185: the page's own identity goes onto <body>, so a `sh-field` in a layout
                // needs no `sh-post` to say what it belongs to.
                composed,
                site.getName(),
                page.getFurl(),
                layout.getTitle(),
                slots,
                state,
                page.getId(),
                site.getId(),
                // SH559 — from the page's own fields, which is the only place the answer is. The
                // layout's title used to be the banner's only statement about how a page was made,
                // and since SH464 one layout serves all three modes, so it named the wrong one.
                compositionOf(stable, composed),
                revision,
                // SH1057 — the column alone, no content read: the same rule the render model uses.
                shSiteLocaleService.declared(site).map(ShSiteLocales::defaultLocale).orElse(null));
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
     * SH1106 — the section types a page of {@code typeName} may compose, per its own templates.
     *
     * <p>The union of every {@code sh-accepts} in the layout and its regions, read statically: no
     * render, since a declaration is an attribute and not content. Empty means nobody declared,
     * which is "nothing is known", never "nothing fits" — the same reading SH1039 gives it.</p>
     */
    public List<String> acceptedSections(ShSite site, String typeName) {
        return templatesFor(site, typeName)
                .map(templates -> templates.all().stream()
                        .flatMap(template -> composer.acceptsOf(template.html()).stream())
                        .distinct().toList())
                .orElse(List.of());
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

    /**
     * The locales this site's content is actually written in, in this state (SH459).
     *
     * <p>Here rather than in the route because site resolution is here: {@code siteByKey} is SH181's
     * order (id, then furl, then name) and a second copy of it on the delivery controller would be
     * the second copy that stops matching. Sorted by the query, so a refusal that lists them reads
     * the same on every engine (P5).</p>
     */
    @Transactional(readOnly = true)
    public List<String> localesOf(String siteKey, ShPostState state) {
        ShSite site = siteByKey(siteKey);
        List<String> inContent = shPostUnifiedRepository.findDistinctLocalesBySiteId(site.getId(), state);
        // SH1057 — and the ones the site declares, which a URL may now name before any page is
        // written in them: the fallback chain answers it, so refusing it would 404 a URL the site
        // promises. Sorted and distinct, as the query already was.
        java.util.TreeSet<String> offered = new java.util.TreeSet<>(inContent);
        shSiteLocaleService.declared(site).ifPresent(policy -> offered.addAll(policy.enabled()));
        return List.copyOf(offered);
    }

    /**
     * SH1042 — the id behind a preview's site key, for a caller that has to check it before reading.
     *
     * <p>Here rather than in the route for {@code localesOf}'s reason one method up: site resolution
     * is SH181's order and it lives in this class, so a second resolver in the security layer would
     * be a second opinion about which of an id, a friendly URL and a name wins a collision.</p>
     *
     * @throws UnknownSiteException exactly as a render of the same key would
     */
    @Transactional(readOnly = true)
    public Optional<String> siteIdOf(String siteKey) {
        return siteIfAny(siteKey).map(ShSite::getId);
    }

    private ShSite siteByKey(String siteKey) {
        return siteIfAny(siteKey).orElseThrow(() -> new UnknownSiteException(siteKey));
    }

    /**
     * The site a key names, or empty — SH181's order, in one place.
     *
     * <p>Split out of {@link #siteByKey} rather than copied beside it (SH1042): a caller that has
     * to ask <em>whether</em> a key resolves would otherwise either carry its own three lookups, and
     * so its own opinion about which of an id, a furl and a name wins a collision, or catch the
     * refusal — and a caught exception standing in for "no" is the silent degradation
     * {@code ShSilentCatchInventoryTest} exists to count.</p>
     */
    private Optional<ShSite> siteIfAny(String siteKey) {
        // SH181's order: the declared key wins a collision.
        Optional<ShSite> byId = shSiteRepository.findById(siteKey);
        if (byId.isPresent()) {
            return byId;
        }
        ShSite byFurl = shSiteRepository.findByFurl(siteKey);
        if (byFurl != null) {
            return Optional.of(byFurl);
        }
        return Optional.ofNullable(shSiteRepository.findByName(siteKey));
    }

    private ShPostUnified pageAt(ShSite site, String url, ShPostState state) {
        String furl = url == null || url.isBlank() ? "/" : url;
        Optional<ShPostUnified> page = pageWithFurl(site, furl, state);
        if (page.isEmpty()) {
            canonicalSlashRedirect(site, furl, state).ifPresent(redirect -> {
                throw redirect;
            });
        }
        return page.orElseThrow(() -> new UnknownPageException(site.getName(), furl, state));
    }

    /**
     * SH1057 — the page a reader asking for {@code wanted} gets at this path.
     *
     * <h2>A translation has its own URL, so the locale selects through the group</h2>
     *
     * <p>{@code translate} creates the sibling through {@code create}, which gives it a free friendly
     * URL — an English {@code /about} and its Portuguese translation at {@code /about-2}, linked by
     * {@code translationGroupId}. So "{@code /pt-br/about}" cannot mean a second row at
     * {@code /about}; it means the Portuguese member of {@code /about}'s group. The answer is a
     * <b>302</b> to that member's own URL, in the reader's locale space: serving it here would put
     * one page at two addresses, which is the split SH467 exists to close, and a 302 rather than a
     * 301 because the translation that is missing today may be written tomorrow.</p>
     *
     * <h2>In the site's own order</h2>
     *
     * <p>The locales are tried along the site's declared fallback chain (SH1056) — the one asked
     * for, then its fallback, ending at the default — and at each one: the page at the path when it
     * is already in that locale, a row at the same path in that locale (the unique key allows two
     * translations to share a URL, and a locale-blind read there would otherwise redirect to itself
     * for ever), then a group member elsewhere. A site with no declared policy tries the locale asked
     * for alone. When nothing answers, the page at the path is served as it always was, which is
     * what keeps a single-language site's URLs exactly as they were.</p>
     */
    private ShPostUnified pageInLocale(ShSite site, String url, ShPostState state, String wanted) {
        String furl = url == null || url.isBlank() ? "/" : url;
        Optional<ShPostUnified> exact = rowInLocale(site, furl, state, wanted);
        if (exact.isPresent()) {
            return exact.get();
        }
        ShPostUnified page = pageAt(site, url, state);
        List<String> chain = shSiteLocaleService.declared(site)
                .map(policy -> policy.chain(wanted))
                .orElse(List.of(wanted));
        List<ShPostUnified> siblings = null;
        for (String locale : chain) {
            if (locale.equalsIgnoreCase(page.getLocale())) {
                return page;
            }
            if (!locale.equalsIgnoreCase(wanted)) {
                Optional<ShPostUnified> here = rowInLocale(site, furl, state, locale);
                if (here.isPresent()) {
                    return here.get();
                }
            }
            if (siblings == null) {
                siblings = translationsOf(page, state);
            }
            inLocale(siblings, locale).ifPresent(sibling -> {
                throw new ShPageRedirectException(sibling.getFurl(), false);
            });
        }
        return page;
    }

    private static Optional<ShPostUnified> inLocale(List<ShPostUnified> rows, String locale) {
        return rows.stream().filter(row -> locale.equalsIgnoreCase(row.getLocale())).findFirst();
    }

    /** The row at this path in this locale, draft-preferred when previewing as {@link #pageWithFurl} is. */
    private Optional<ShPostUnified> rowInLocale(ShSite site, String furl, ShPostState state, String locale) {
        return state == ShPostState.DRAFT
                ? first(shPostUnifiedRepository.findByShSite_IdAndFurlAndStateAndLocale(
                                site.getId(), furl, ShPostState.DRAFT, locale),
                        () -> shPostUnifiedRepository.findByShSite_IdAndFurlAndStateAndLocale(
                                site.getId(), furl, ShPostState.PUBLISHED, locale))
                : shPostUnifiedRepository.findByShSite_IdAndFurlAndStateAndLocale(
                        site.getId(), furl, ShPostState.PUBLISHED, locale);
    }

    /**
     * The page's translations a reader of this state may be sent to, by id.
     *
     * <p>Its own site only, never itself, never one in the trash, and never one at the page's own
     * URL — that one is {@link #rowInLocale}'s to serve, and redirecting to it would redirect to the
     * address the reader is already at. Ordered by id so a group holding two members in one locale
     * sends every reader to the same one.</p>
     */
    private List<ShPostUnified> translationsOf(ShPostUnified page, ShPostState state) {
        String group = page.getTranslationGroupId() != null ? page.getTranslationGroupId() : page.getId();
        String siteId = page.getShSite() == null ? null : page.getShSite().getId();
        Map<String, ShPostUnified> byId = new java.util.TreeMap<>();
        for (ShPostUnified row : shPostUnifiedRepository.findByTranslationGroupId(group)) {
            boolean readable = row.getState() == state
                    || (state == ShPostState.DRAFT && row.getState() == ShPostState.PUBLISHED);
            if (!readable || row.getId().equals(page.getId()) || row.getDeletedDate() != null
                    || row.getShSite() == null || !row.getShSite().getId().equals(siteId)
                    || row.getFurl() == null || row.getFurl().isBlank()
                    || row.getFurl().equals(page.getFurl())) {
                continue;
            }
            byId.merge(row.getId(), row, (kept, other) ->
                    kept.getState() == ShPostState.DRAFT ? kept : other);
        }
        return List.copyOf(byId.values());
    }

    private Optional<ShPostUnified> pageWithFurl(ShSite site, String furl, ShPostState state) {
        // DRAFT-preferred when previewing: a preview that showed the published row would
        // be showing the page the curator is trying to change.
        return state == ShPostState.DRAFT
                ? first(storedAt(site, furl, ShPostState.DRAFT), () -> storedAt(site, furl, ShPostState.PUBLISHED))
                : storedAt(site, furl, ShPostState.PUBLISHED);
    }

    /**
     * SH1346 — the row at {@code furl} as requested, or stored without its leading slash.
     *
     * <p>A route always hands this a leading slash, and every write since SH651 stores the bare
     * form, so a page made in the console or by a blueprint answered 404 here while its address
     * resolved and {@code /agent/render} drew it. The leading slash is not a spelling of the URL
     * anybody sees, so both are the same page. The trailing slash is: SH467 serves only the stored
     * form and redirects the other, and this leaves that rule exactly as it was.</p>
     */
    private Optional<ShPostUnified> storedAt(ShSite site, String furl, ShPostState state) {
        Optional<ShPostUnified> exact = shPostUnifiedRepository.findByShSite_IdAndFurlAndState(site.getId(), furl, state);
        if (exact.isPresent() || furl == null || furl.length() <= 1 || !furl.startsWith("/")) {
            return exact;
        }
        return shPostUnifiedRepository.findByShSite_IdAndFurlAndState(site.getId(), furl.substring(1), state);
    }

    /**
     * The other trailing-slash form of a path this site does serve (SH467).
     *
     * <h2>A URL space with one character removed</h2>
     *
     * <p>A source serves {@code /shio/} with a 200 and answers {@code /shio} with a 301 to it. Its
     * replica did the exact opposite — {@code /shio} was 200 and {@code /shio/} was <b>404</b> — on
     * the public route and the preview alike, because the address grammar collapses a trailing slash
     * ({@code ShAddress.normalizePath}) and the stored {@code furl} is therefore the source's path
     * with one character removed. SH385's rule is that a replica keeps the original's URL space, and
     * this broke it in the direction hardest to notice: every page still resolves, so {@code verify}
     * is clean and a crawl of the replica succeeds. What fails is everything carrying the
     * <em>source's</em> URLs — a sitemap, an inbound link, a bookmark, and the captured markup a
     * fidelity replica serves, which links to {@code /shio/}.</p>
     *
     * <h2>Toward the stored form, not toward a fixed one</h2>
     *
     * <p>The canonical URL is whichever one the content declares, so this reads the direction off
     * the {@code furl} rather than deciding it. Today the grammar always yields the slash-free form,
     * so in practice {@code /shio/} redirects to {@code /shio}; if a stored {@code furl} ever carries
     * the slash the source declared, the same code redirects the other way with no edit. That is
     * also why it is here and not a rule in either controller: one behaviour, and the two routes
     * already know how to turn a {@link ShPageRedirectException} into their own address space.</p>
     *
     * <p>Permanent, unlike SH385's opt-in: the two spellings of one path will not stop meaning the
     * same page, which is the property that makes a cached 301 safe, and it is what the source and
     * every ordinary web server answer here.</p>
     *
     * <p>{@code /} has no other form — {@code ""} is not a path — so the home page is left to the
     * {@code UnknownPageException} that follows, and a request matching neither form still reaches
     * the asset fallback both routes apply to it.</p>
     */
    private Optional<ShPageRedirectException> canonicalSlashRedirect(ShSite site, String furl,
                                                                     ShPostState state) {
        // SH583 — the other spelling comes from the shared generator, not from a fourth private
        // rule. This route is the one deliberate *non*-tolerant reader of the trailing slash:
        // `pageWithFurl` matches the stored slash form exactly, because tolerating both here would
        // serve one page at two URLs and that is the URL-space split SH467 exists to close. The
        // tolerance is the 301. (The leading slash is not a URL anybody sees; SH1346 reads both.)
        return ShFurlSpellings.otherSlashForm(furl)
                .flatMap(other -> pageWithFurl(site, other, state))
                .map(found -> new ShPageRedirectException(found.getFurl(), true));
    }

    private Optional<ShPostUnified> first(Optional<ShPostUnified> preferred,
                                          java.util.function.Supplier<Optional<ShPostUnified>> fallback) {
        return preferred.isPresent() ? preferred : fallback.get();
    }

    /**
     * Whether the post at this path is a redirect, and where to (SH385).
     *
     * <h2>Why a redirect's source is its friendly URL and not a {@code from} field</h2>
     *
     * <p>The design offered a {@code from}, and this is strictly less machinery for the same
     * capability. The router already resolves a path to a post, so a redirect is <b>found by the read
     * that just ran</b> — no lookup, and in particular no site-wide scan on every 404. SH323 makes a
     * friendly URL site-unique, so "two redirects claim one path" is impossible rather than reported.
     * And a redirect shadowing a real page is already SH99's {@code duplicate-url} <em>error</em>,
     * which is one of the two findings SH385 asked for, arriving with no rule written.</p>
     *
     * <h2>Not a rewrite engine, and that is the point</h2>
     *
     * <p>One exact path in, one address out. No patterns, no regular expressions, no ordering to
     * reason about — which is what makes a replica's URL space verifiable from the tree alone rather
     * than by running requests through it. A pattern language would also be a second addressing scheme
     * beside SH79, for a capability nobody has asked for yet.</p>
     *
     * <h2>A redirect with no target renders as a page, deliberately</h2>
     *
     * <p>An empty {@code TO} yields no redirect, so the request falls through to the ordinary render
     * path and fails there — naming the missing layout binding, which is a true statement about a post
     * that is not finished. Redirecting to {@code /} instead would send a reader to the homepage and
     * tell nobody, and {@code verify}'s {@code redirect-target-missing} is where the defect belongs.</p>
     */
    private Optional<ShPageRedirectException> redirectFor(ShPostUnified page) {
        String typeName = page.getShPostType() == null ? null : page.getShPostType().getName();
        if (!ShSystemPostType.REDIRECT.equals(typeName)) {
            return Optional.empty();
        }
        Map<String, Object> fields = renderModel.fieldsOf(page);
        String to = fields.get(ShSystemPostTypeAttr.TO) == null
                ? null
                : fields.get(ShSystemPostTypeAttr.TO).toString().trim();
        if (to == null || to.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ShPageRedirectException(to, isTrue(fields.get(ShSystemPostTypeAttr.PERMANENT))));
    }

    /**
     * A Check Box's value, which is not a boolean on the wire.
     *
     * <p>The widget stores {@code "1"}/{@code "0"} through the console and {@code true}/{@code false}
     * through {@code post.upsert}, and a redirect written by either has to mean the same thing — a 301
     * that silently became a 302 because the value arrived as a string is the kind of defect nobody
     * finds by looking at the content.</p>
     *
     * <p>Moved to {@link ShPostFieldFlags} by SH421, which gave {@code PageLayout} a flag of its own
     * and therefore gave this coercion a second caller in a different package. Delegating rather
     * than copying is SH245's rule, and here the disagreement it prevents is concrete: a layout
     * whose opt-out the lint failed to recognise is warned about anyway.</p>
     */
    private static boolean isTrue(Object value) {
        return ShPostFieldFlags.isTrue(value);
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
            // SH270 — degrade *and* report. With no bindings, every page of this site refuses with
            // `render-no-layout-binding`, which names the post-type and reads like a missing binding
            // rather than what it is: the site's own map is not JSON. Nothing said so anywhere.
            incidentLog.record(ShAgentIncidentLog.Kind.CONTENT,
                    ShAddress.site(site == null ? null : site.getName()).toString(),
                    "The site's postTypeLayout is not valid JSON, so no post-type has a layout: "
                            + e.getOriginalMessage(),
                    "Rewrite it with site.upsert (postTypeLayout is a flat "
                            + "{\"<post type>\": \"<layout title>\"} map). Until then every page of "
                            + "this site refuses with render-no-layout-binding.",
                    // SH204: free — the very next line up already dereferences `site` for its
                    // name, so the entity is provably initialised and `getId()` reads a field.
                    ShAgentIncidentFrames.of(e), site == null ? null : site.getId());
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
                .map(this::themeAssets)
                .orElse(null);
    }

    /**
     * SH389 — the site's ordered script declarations, or three empty lists.
     *
     * <h2>Two of them is nothing, not the first one</h2>
     *
     * <p>The post is found by <b>type</b> rather than by title, because there is one per site and
     * asking an author to remember a magic title would be a second convention. Two posts of the type
     * therefore have to mean something, and it is SH381's rule: a fabricated answer wearing a
     * deterministic hat is worse than none, so the render carries no scripts and
     * {@code shio verify}'s {@code site-scripts-ambiguous} says which two.</p>
     *
     * <h2>Malformed JSON degrades and is reported</h2>
     *
     * <p>{@code Theme.TOKENS}' shape exactly (SH195/SH270): the page must not fail over a tag manager,
     * and it must not fail <em>silently</em> either. The catch is here rather than in
     * {@link ShSiteScripts} because reporting needs the incident log and that class is a pure
     * function — {@code ShSilentCatchInventoryTest} is what made that placement a decision.</p>
     */
    private Map<String, Object> siteScriptsOf(ShSite site, ShPostState state) {
        List<ShPostUnified> found = ShRenderState.preferDraft(
                postsOfSystemType(site, ShSystemPostType.SITE_SCRIPTS, state), state);
        if (found.size() != 1) {
            return ShSiteScripts.of(null);
        }
        ShPostUnified post = found.getFirst();
        try {
            return ShSiteScripts.of(fieldOf(post, ShSystemPostTypeAttr.SCRIPTS));
        } catch (JacksonException e) {
            incidentLog.record(ShAgentIncidentLog.Kind.CONTENT,
                    ShAddress.post(site.getName(), post.getFurl()).toString(),
                    "This site's SCRIPTS field is not valid JSON, so the page rendered with none of "
                            + "the site's scripts at all: " + e.getOriginalMessage(),
                    "Rewrite SCRIPTS as a JSON array of "
                            + "{\"src\"|\"inline\", \"position\", \"defer\", \"category\"} "
                            + "with post.upsert, or clear the field. A consent banner that silently "
                            + "stopped loading is the worst shape this defect takes.",
                    // SH204: `site` is the method's own parameter and is dereferenced for its name
                    // three lines up, so this is a field read.
                    ShAgentIncidentFrames.of(e), site.getId());
            return ShSiteScripts.of(null);
        }
    }

    /**
     * SH195 — a theme's tokens compiled into its stylesheet, degrading if they are not JSON.
     *
     * <p>The page renders either way; without this the operator would see an unstyled page and
     * nothing anywhere would say why, which is exactly the silent degradation SH270 exists to
     * refuse. The catch lives outside {@code ShThemeTokens} because reporting needs the incident log
     * and that class is a pure function — {@code ShSilentCatchInventoryTest} is what made the
     * placement a decision instead of an accident.</p>
     *
     * <p>SH850 — and it lives on {@link ShRenderModel} rather than here, which is a different
     * decision from that one and was made for a different reason: this is not the only place a
     * theme's stylesheet is built, and being written where each was needed is how the two diverged
     * twice. This method stays as the name the render path reads.</p>
     */
    private ShThemeAssets themeAssets(ShPostUnified theme) {
        // SH850 — the composition moved to `ShRenderModel`, which is where the *other* emission of
        // a theme's stylesheet is built. It was written here because this is where it was needed,
        // and the second one (`{{#getRelation "/theme/captured"}}{{CSS}}`, SH549) was written as a
        // plain field read for the same reason — so the two diverged twice, in escaping (SH555) and
        // then in composition, and a page lost 132 declarations its own rules were still reading.
        // One implementation, asked by both, is what makes a third divergence not a thing that can
        // happen quietly.
        return renderModel.themeAssetsOf(theme);
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

    /**
     * SH311 — the templates of one system type in one site, resolved once per read-only sweep.
     *
     * <h2>Why there is a cache at all, and why it is this small</h2>
     *
     * <p>SH264 measured the thing that justifies it: a page's rendered digest costs 21 queries, and
     * the next page of the same site costs the same 21, because this method re-read the layout,
     * every region and the theme for each page. A binding resolves to the same templates for every
     * page of its type — SH251's finding, and what made the graph affordable — so that was per-page
     * work carrying per-type information.</p>
     *
     * <h2>Why the scope is a read-only transaction</h2>
     *
     * <p>The lifetime was the whole design question, and it is now
     * {@link ShReadOnlyTransactionCache}'s to state: a value lives exactly as long as one read-only
     * transaction, and a writing transaction gets no cache at all. SH316 moved the mechanism there
     * when {@link ShTemplateGraph} turned out to need the same lifetime for a different population
     * — one implementation of a staleness rule rather than two that can drift.</p>
     */
    private List<ShPostUnified> postsOfSystemType(ShSite site, String typeName, ShPostState state) {
        // SH316 — the mechanism moved to `ShReadOnlyTransactionCache` when a second population
        // needed exactly this lifetime. The behaviour is unchanged: cached inside a read-only
        // transaction, and not cached at all anywhere else.
        return SWEEP_CACHE.get(site.getId() + "|" + typeName + "|" + state,
                () -> loadPostsOfSystemType(site, typeName, state));
    }

    /**
     * Field name to declared widget, for one post-type (SH1037).
     *
     * <p>Read from the type already in hand rather than re-resolved: {@code page.getShPostType()} is
     * the association the row was loaded with, so this costs the attribute collection and no query
     * for the type itself. Read-only — the {@code agents.md} invariant about detaching
     * {@code ShPostTypeAttr.shWidget} is about <em>mutating</em> a post-type, and a cascade cannot
     * fire on a walk.</p>
     *
     * <p>Empty for a type with no attributes and for no type at all, which the composer reads as
     * "no kind is known" and leaves the attribute off.</p>
     */
    private Map<String, String> declaredWidgets(ShPostType type) {
        if (type == null || type.getShPostTypeAttrs() == null) {
            return Map.of();
        }
        Map<String, String> widgets = new java.util.LinkedHashMap<>();
        for (ShPostTypeAttr attr : type.getShPostTypeAttrs()) {
            if (attr.getName() != null && attr.getShWidget() != null
                    && attr.getShWidget().getName() != null) {
                widgets.put(attr.getName(), attr.getShWidget().getName());
            }
        }
        return widgets;
    }

    /**
     * SH1175 — field-to-widget maps for the posts that own a page's fields other than the page.
     *
     * <p>Two queries whatever the number of sections: the posts' type ids, then those types. A
     * section's fields used to carry no declared widget, so the editor bridge treated a Text heading
     * as rich HTML and a typed trailing space was stored as {@code &nbsp;}, escaped again on every
     * save. A post the query does not find (a listing row from another site, a stale id) is simply
     * absent, and its fields stay undeclared as before.</p>
     */
    private Map<String, Map<String, String>> widgetsOfOwners(Set<String> postIds) {
        Map<String, String> typeOfPost = new java.util.HashMap<>();
        for (Object[] row : shPostUnifiedRepository.findPostTypeIdsByIdIn(postIds)) {
            if (row[0] != null && row[1] != null) {
                typeOfPost.put(row[0].toString(), row[1].toString());
            }
        }
        if (typeOfPost.isEmpty()) {
            return Map.of();
        }
        Map<String, Map<String, String>> widgetsOfType = new java.util.HashMap<>();
        for (ShPostType type : shPostTypeRepository.findAllById(new java.util.HashSet<>(typeOfPost.values()))) {
            widgetsOfType.put(type.getId(), declaredWidgets(type));
        }
        Map<String, Map<String, String>> answer = new java.util.HashMap<>();
        typeOfPost.forEach((post, type) -> {
            Map<String, String> widgets = widgetsOfType.get(type);
            if (widgets != null && !widgets.isEmpty()) {
                answer.put(post, widgets);
            }
        });
        return answer;
    }

    private List<ShPostUnified> loadPostsOfSystemType(ShSite site, String typeName,
                                                      ShPostState state) {
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

    /**
     * A template's source, read the way the page reads it (SH572).
     *
     * <p>Exposed for {@code ShTemplateGraph}, and for the same reason {@code themeTitleOf} above is:
     * SH281's rule that the graph and the page must not come to disagree about what a template
     * <em>says</em>. A lint that read the {@code HTML} field through its own accessor would be a
     * second answer to that question, and the whole value of a static rule over template source is
     * that it is the source the renderer will use.</p>
     */
    public String htmlOf(ShPostUnified template) {
        return fieldOf(template, ShSystemPostTypeAttr.HTML);
    }

    /** SH311's population: one site's templates of one system type, per read-only transaction. */
    private static final ShReadOnlyTransactionCache SWEEP_CACHE = new ShReadOnlyTransactionCache();

    private String fieldOf(ShPostUnified post, String field) {
        Object value = renderModel.fieldsOf(post).get(field);
        return value == null ? null : value.toString();
    }

    /**
     * A rendered page and what it was built from — the slots are reported because "which
     * regions did this page actually use" is the question SH193 makes answerable and the
     * one a curator asks the moment a page looks wrong.
     */
    /**
     * @param siteId SH336 — the preview controller needs it to look up this site's own URLs, and
     *               asking the render service for the site again would be a second resolution of
     *               something this call already holds
     */
    /**
     * @param composition how this page is made — {@code verbatim}, {@code authorable},
     *                    {@code mixed}, or {@code null} where the page holds neither field
     *                    (SH559). Read from the page, never from the layout's name.
     */
    /**
     * @param siteLocale SH1057 — the default locale the site <em>declared</em>, or null when it
     *                   declared none. Carried out of the render because the site is already loaded
     *                   here and the public route needs it to name its links; asking again would be a
     *                   second site resolution on every public request.
     */
    public record ShRenderedPage(String html, String site, String url, String layout,
                                 List<String> regions, ShPostState state, String postId,
                                 String siteId, String composition, Integer revision,
                                 String siteLocale) {

        /** A render of a row, which is every render before SH990 could read a revision. */
        public ShRenderedPage(String html, String site, String url, String layout,
                              List<String> regions, ShPostState state, String postId,
                              String siteId, String composition) {
            this(html, site, url, layout, regions, state, postId, siteId, composition, null, null);
        }

        /** A render that says nothing about the site's declared locale — every caller but one. */
        public ShRenderedPage(String html, String site, String url, String layout,
                              List<String> regions, ShPostState state, String postId,
                              String siteId, String composition, Integer revision) {
            this(html, site, url, layout, regions, state, postId, siteId, composition, revision, null);
        }
    }

    /**
     * Which of the three shapes a page actually is (SH559).
     *
     * <h3>The label that stopped describing what it labels</h3>
     *
     * <p>The preview banner ended with {@code layout <site> (fidelity)}. On a site converted with
     * {@code --accept all} — eleven pages holding section lists, a home page carrying sixteen
     * annotated fields — that string was the only statement anywhere about how the page was
     * produced, and it named <b>the other mode</b>. The name was accurate when one layout served
     * one mode; SH464 then made a single generated layout serve all three and the title kept the
     * word it was born with. Nothing downstream reads it, which is why it survived: the only reader
     * is a person or an agent looking at the page.</p>
     *
     * <p>It cost a wrong inference in the work that found it. Reading {@code layout R6Aut
     * (fidelity)} beside a page that had lost its styling, the first hypothesis was that an
     * authorable conversion had been handed a fidelity layout, and the real cause — a site-wide
     * theme switch — took a detour to find. <b>A label that is wrong is worse than no label</b> for
     * exactly that reason: it is evidence, and it points somewhere.</p>
     *
     * <h3>The same two fields the layout branches on</h3>
     *
     * <p>{@code content} is the source's own markup and {@code sections} is the list a page
     * composes from, which is precisely what {@code PAGE_BODY_HTML} tests with
     * {@code {{#if content}}} and {@code {{#getRelation sections}}}. Reading them here means the
     * banner and the page cannot come to disagree — SH281's rule, the same one that has
     * {@code themeTitleOf} read through the renderer.</p>
     *
     * <p>{@code null} rather than a guess when the page holds neither: this route renders every
     * post type, and most of them are not conversions. A banner that called an {@code Article}
     * "authorable" would be the same defect one type over.</p>
     */
    private static String compositionOf(Map<String, Object> model, String html) {
        boolean verbatim = present(model.get("content")) || keepsMarkupInASection(html);
        boolean composed = present(model.get("sections"));
        if (verbatim && composed) {
            return "mixed";
        }
        if (verbatim) {
            return "verbatim";
        }
        return composed ? "authorable" : null;
    }

    /**
     * SH774 — did this page render verbatim markup through a section?
     *
     * <h3>What `content` stopped answering, and where the answer moved</h3>
     *
     * <p>SH559 read the banner off the page's own fields, *"which is the only place the answer
     * is"*. Since SH763 that sentence is false: a page whose {@code <body>} begins with an accepted
     * block has no {@code content} and every byte of the source's markup in {@code SectionMarkup}
     * posts, so this reported <b>authorable</b> of a page that is mixed. SH559's own rule convicts
     * that — <i>a label that is wrong is worse than no label</i>, because it is evidence and it
     * points somewhere.</p>
     *
     * <h3>The measurement that chose this over resolving the sections</h3>
     *
     * <p>The obvious repair is to resolve {@code sections} and ask what they are. Measured before
     * writing it: {@code ShPostUnifiedRepository} has <b>no bulk furl lookup</b> —
     * {@code findByShSite_IdAndFurlAndState} is per URL, and {@code ShFurlSpellings} tries up to
     * four spellings of each — so the pass costs <b>one query per section, per render</b>, against
     * a standalone render measured at 21 queries in total. Thirty to a hundred and fifty per cent,
     * for a diagnostic banner. The other candidate was a flag on {@code Page}, which is the
     * replication-only field SH763 removed, re-added to a shipped type so a label can be right.</p>
     *
     * <p><b>That was an inference, and the number already existed</b> (SH776).
     * {@code ShRenderHelperSweepBenchmarkTest} has measured a section-composed page since SH312:
     * five sections is <b>11 queries a page against a floor of 6</b>, by id and by URL alike —
     * one lookup per entry, exactly as reasoned. Cited here rather than re-derived, because a
     * measured number and a plausible one are different kinds of claim and this file should carry
     * the first.</p>
     *
     * <h3>So it reads what was rendered, which is not a proxy for the subject — it IS the subject</h3>
     *
     * <p>The banner describes <em>this render</em>, and the composed page is where the page's own
     * fields and its sections have already been brought together, at no cost. The markup branch of
     * the generated layout is the only thing in the SH95 vocabulary that draws an {@code html}
     * field, and the composer turns its {@code sh-field} into {@code data-shio-prop} — so the
     * annotation is the mark. A hand-written template that annotates its own {@code html} field
     * would read the same way, which is the honest limit and is a page that genuinely carries
     * markup somebody wrote by hand.</p>
     */
    private static boolean keepsMarkupInASection(String html) {
        return html != null && html.contains("data-shio-prop=\"html\"");
    }

    /** A field that holds something — an empty list and a blank string are both absent. */
    private static boolean present(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Collection<?> items) {
            return !items.isEmpty();
        }
        return !value.toString().isBlank();
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

    /**
     * Not an error: this path answers with a redirect (SH385).
     *
     * <h2>Why it is an exception and not a field on {@link ShRenderedPage}</h2>
     *
     * <p>A redirect has <b>no page</b> — no layout, no theme, no model — so a record that carried one
     * would be a rendered page with every field empty, and each of the five callers of {@code render}
     * would have to learn to check before touching any of them. An exception says "there is nothing to
     * render here" in the one place that can, and only the HTTP layer has to care.</p>
     *
     * <h2>Deliberately not a {@link ShTeachingException}</h2>
     *
     * <p>Those are 4xx problems, and this is a working answer: the reader gets the page they were
     * looking for, at its new address. Making it one would put a redirect in the diagnostics window and
     * in {@code verify}'s counts. It <b>is</b> named in {@code noRollbackFor} beside them, because a
     * redirect must not mark a read-only transaction for rollback for having worked.</p>
     */
    public static class ShPageRedirectException extends RuntimeException {

        private final String to;
        private final boolean permanent;

        public ShPageRedirectException(String to, boolean permanent) {
            super("redirect to " + to);
            this.to = to;
            this.permanent = permanent;
        }

        public String to() {
            return to;
        }

        public boolean permanent() {
            return permanent;
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
