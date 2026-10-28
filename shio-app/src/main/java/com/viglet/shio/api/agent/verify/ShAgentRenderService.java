/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.verify;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.agent.ShAddressResolver;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import com.viglet.shio.api.agent.verify.dto.ShAgentDigest;
import com.viglet.shio.render.ShRegionComposer;
import com.viglet.shio.api.acl.ShContentAclService;
import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.api.agent.ShAgentReadService;
import com.viglet.shio.api.post.ShContentPerspective;
import com.viglet.shio.api.post.type.ShPostTypeAdminService;
import com.viglet.shio.api.post.revision.ShPostRevisionService;
import com.viglet.shio.api.post.revision.ShRevisionRow;
import com.viglet.shio.api.post.unified.ShPostStateNotFoundException;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.render.ShPageRenderService;
import com.viglet.shio.url.ShFurlSpellings;
import com.viglet.shio.widget.ShSystemWidget;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The render digest (SH101) — cheap textual proof that a page came out right.
 *
 * <h2>Two sources, and why both exist</h2>
 *
 * <p>{@code address=} digests the markup <b>Shio holds</b>: the post's rich-text fields.
 * That is the authoring truth, it needs no front end running, and it is the one an agent
 * can use immediately after a write. It answers "did my content land with the structure
 * I intended".</p>
 *
 * <p>{@code url=} fetches a delivery URL and digests the response. That answers the
 * strictly larger question — "does the page a visitor gets have that structure" — and it
 * is the one §IX.3 describes. It is <b>off by default</b> and restricted to the URLs of
 * sites this instance actually hosts, because an endpoint that fetches an arbitrary URL
 * on the server's behalf is a server-side request forgery with a friendly name. Enabling
 * it is one property ({@code shio.agent.render.allow-fetch}); the refusal says so.</p>
 *
 * <h2>Determinism</h2>
 *
 * <p>The digest hashes the outline only — heading levels and text, landmark counts, link
 * hrefs, image sources and whether each has alt. Not word counts (which move with a
 * typo fix) and not the source. So two digests differ exactly when the page's shape
 * differs, which is the comparison the field exists for.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
// SH345 — this service *catches* teaching exceptions to build SH249's labelled fallback, so
// a poisoned transaction here fails at commit with the fallback already computed.
@Transactional(readOnly = true, noRollbackFor = ShTeachingException.class)
public class ShAgentRenderService {

    private static final String SOURCE_CONTENT = "content";
    private static final String SOURCE_FETCH = "fetch";
    /**
     * SH249 — the page as the renderer composes it, which is what a visitor gets and the only
     * artefact that reflects both regions and sections at once. See
     * {@code docs/specs/SH249-sections-and-regions.md}.
     */
    private static final String SOURCE_RENDERED = "rendered";

    /** Plain-text widgets whose value is visible prose — a section's heading and its copy. */
    private static final List<String> TEXT_WIDGETS =
            List.of(ShSystemWidget.TEXT, ShSystemWidget.TEXT_AREA);

    private static final List<String> MARKUP_WIDGETS =
            List.of(ShSystemWidget.HTML_EDITOR, ShSystemWidget.ACE_HTML);

    private final ShAddressResolver shAddressResolver;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ObjectMapper objectMapper;
    private final ShAgentDeliveryProbe deliveryProbe;
    private final ShPageRenderService pageRenderService;
    /** SH990 — what a page said at a revision, for {@code state=rev:<n>}. */
    private final ShPostRevisionService shPostRevisionService;
    /** SH1014 — a preview of unsaved values shows content, so it is gated like authoring it. */
    private final ShContentAclService shContentAclService;
    /**
     * SH1039 — the one reader of what a post-type declares, so the catalogue and the console's
     * properties panel answer from the same definition rather than from two ideas of it.
     */
    private final ShPostTypeAdminService shPostTypeAdminService;
    /** SH1273 — rebases a console frame's markup the way `/sites/**` does. */
    private final com.viglet.shio.render.ShPreviewLinks previewLinks;

    public ShAgentRenderService(ShAddressResolver shAddressResolver,
                                ShPostUnifiedRepository shPostUnifiedRepository,
                                ObjectMapper objectMapper,
                                ShAgentDeliveryProbe deliveryProbe,
                                ShPageRenderService pageRenderService,
                                ShPostRevisionService shPostRevisionService,
                                ShContentAclService shContentAclService,
                                ShPostTypeAdminService shPostTypeAdminService,
                                com.viglet.shio.render.ShPreviewLinks previewLinks) {
        this.previewLinks = previewLinks;
        this.shPostTypeAdminService = shPostTypeAdminService;
        this.shContentAclService = shContentAclService;
        this.shPostRevisionService = shPostRevisionService;
        this.shAddressResolver = shAddressResolver;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.objectMapper = objectMapper;
        this.deliveryProbe = deliveryProbe;
        this.pageRenderService = pageRenderService;
    }

    /**
     * Digests a post's own markup, or a delivery URL.
     *
     * @param address SH79 {@code post:} address; exclusive with {@code url}
     * @param url     absolute delivery URL; requires {@code shio.agent.render.allow-fetch}
     */
    public ShAgentDigest digest(String address, String url) {
        return digest(address, url, null);
    }

    /**
     * Digests a post from the perspective {@code state} names (SH990).
     *
     * @param state the {@code state=} grammar; it names a version of the post an address points at,
     *              so it is refused beside {@code url}, which digests whatever a visitor is served
     */
    public ShAgentDigest digest(String address, String url, String state) {
        boolean hasAddress = address != null && !address.isBlank();
        boolean hasUrl = url != null && !url.isBlank();
        if (hasAddress == hasUrl) {
            throw new NoDigestTargetException(hasAddress);
        }
        // SH993 — a release names a version per post, and a digest is about one page at one version;
        // read it there instead of rendering the live page under the name of a launch.
        ShContentPerspective perspective = ShContentPerspective.parse(state)
                .requireVersion("/agent/render", "GET /api/v2/agent/read?address=<address>&state=release:<name>");
        if (hasUrl && !perspective.isDefault()) {
            throw new StateWithUrlException(state);
        }
        return hasAddress ? fromContent(address.trim(), perspective) : fromUrl(url.trim());
    }

    /**
     * SH809 — which fields on a page a curator could edit, and which regions it declares.
     *
     * <p>The one P1 exception with a route already named. {@code universalEditor} sat in
     * {@code ShAgentP1ConformanceTest}'s inventory as {@code CONSOLE_ONLY} because SH185's bridge is
     * a browser protocol the console alone speaks — so an agent could read a post, render it, and
     * diff draft against live, and still not answer <em>which fields can somebody change</em>.</p>
     *
     * <p>It composes rather than forks (P10): the same render the digest takes, read through
     * {@link ShHtmlEditable}, which parses the attributes the bridge already emits. Deriving
     * editability from post-types instead would answer what the *model* permits, and the two would
     * agree only by accident (SH583). What a curator can click is what the page says.</p>
     *
     * @param rawAddress a {@code post:} address, as everywhere else on this surface (SH79)
     * @return the fields and regions, or a page that renders to nothing reported as empty
     */
    public ShAgentEditable editable(String rawAddress) {
        return editable(rawAddress, null, false);
    }

    /** As {@link #editable(String)}, for the version of the page {@code state} names (SH990). */
    public ShAgentEditable editable(String rawAddress, String state) {
        return editable(rawAddress, state, false);
    }

    /**
     * As {@link #editable(String, String)}, with the insertable catalogue attached (SH1039).
     *
     * @param insertable {@code include=insertable} — per container, the section types the template
     *                   says fit there, each resolved against this instance's post-types. Opt-in
     *                   because it is the expensive half, and absent rather than empty when nobody
     *                   asked: an empty list is a page that composes no sections, which is a
     *                   different answer (P3, and SH169's rule about two failures looking alike)
     */
    public ShAgentEditable editable(String rawAddress, String state, boolean insertable) {
        ShContentPerspective perspective = ShContentPerspective.parse(state)
                .requireVersion("/agent/editable", "GET /api/v2/agent/read?address=<address>&state=release:<name>");
        ShPostUnified post = postFor(rawAddress, perspective);
        String address = shAddressResolver.addressOf(post).toString();

        Optional<String> html = renderedHtml(post, perspective);
        if (html.isEmpty()) {
            // The same honest fallback the digest takes for a post with no layout binding or no
            // friendly URL, which is the normal state of a site mid-build. An empty list here would
            // read as "nothing is editable" rather than "this page does not render yet", so the
            // source says which question was answered (SH169's argument, one endpoint over).
            return new ShAgentEditable(address, SOURCE_CONTENT, List.of(), List.of(),
                    insertable ? List.of() : null);
        }
        return new ShAgentEditable(address, SOURCE_RENDERED,
                ShHtmlEditable.of(html.get()), ShHtmlEditable.regions(html.get()),
                insertable ? catalogue(html.get()) : null);
    }

    /**
     * SH1039 — the catalogue for each container the page composes.
     *
     * <p>The declaration is the template's and the resolution is this instance's, which is the
     * split that makes the answer useful: a type the layout names and the instance has not got is
     * reported {@code provisioned: false} with the op that creates it, rather than dropped — the
     * page declares it, so a shorter catalogue would be a quieter lie than an honest gap
     * (SH169).</p>
     *
     * <p>One post-type read per distinct name across the page, not per container: two containers
     * accepting the same type are one lookup, and a page with four containers over eight types was
     * otherwise thirty-two reads for eight answers.</p>
     */
    private List<ShAgentInsertable.Container> catalogue(String html) {
        Map<String, ShAgentInsertable.Type> resolved = new LinkedHashMap<>();
        List<ShAgentInsertable.Container> out = new ArrayList<>();
        for (ShHtmlEditable.Container container : ShHtmlEditable.containers(html)) {
            List<ShAgentInsertable.Type> accepts = container.accepts().stream()
                    .map(name -> resolved.computeIfAbsent(name, this::typeFor))
                    .toList();
            out.add(new ShAgentInsertable.Container(container.index(), container.region(),
                    container.holds(), container.sections(), accepts,
                    accepts.isEmpty() ? UNDECLARED_FIX : null));
        }
        return List.copyOf(out);
    }

    /**
     * What an insert would need to know about one declared type.
     *
     * <p>Trimmed from {@code ShPostTypeDef} rather than returned whole: the definition carries
     * widget settings, constraints, index hints and the Turing mapping, none of which decides what
     * to place or what a properties panel draws, and this rides on a read a caller may make for
     * every page of a site (P3).</p>
     */
    private ShAgentInsertable.Type typeFor(String name) {
        return shPostTypeAdminService.get(name)
                .map(def -> new ShAgentInsertable.Type(def.name(), def.title(), def.description(),
                        true, def.fields() == null ? List.of() : def.fields().stream()
                                .map(field -> new ShAgentInsertable.Field(field.name(),
                                        field.label(), field.widget(),
                                        Boolean.TRUE.equals(field.required())))
                                .toList()))
                .orElseGet(() -> new ShAgentInsertable.Type(name, null, null, false, List.of()));
    }

    /**
     * What to do about a container that declares nothing (SH1039) — P6 inside an ordinary 200.
     *
     * <p>An empty catalogue is the shape of two different situations: the template says nothing
     * fits here, and nobody has said anything. They need different actions, so the one that is a
     * missing declaration carries the declaration to add.</p>
     */
    private static final String UNDECLARED_FIX =
            "This container declares no sh-accepts, so nothing is known to fit it. Add "
                    + "sh-accepts=\"SectionHero SectionCta\" to the element composing the sections "
                    + "in this page's PageLayout; an absent declaration is never read as "
                    + "\"anything\".";

    // --- content ------------------------------------------------------------

    /**
     * SH1014 — the page as it would read, from values nobody has saved.
     *
     * <h2>Gated by WRITE, not by READ</h2>
     *
     * <p>A caller supplies the document and gets the page back, so this composes content of their
     * choosing through this site's templates and hands them the result. Read permission is the
     * wrong question — it asks whether they may see what is stored, and nothing stored is what
     * comes back. Write is the right one: they may already author exactly this by saving it, so a
     * preview shows them nothing a save would not, and one call earlier.</p>
     *
     * <h2>Digest, and the HTML only when asked</h2>
     *
     * <p>The digest is the answer an agent wants — outline, links, images, word count — and it is
     * small. A whole page is not, and P3 is a budget: the markup is returned only to a caller that
     * asked for it, which in practice is a console frame showing the page to a person.</p>
     *
     * @param data field values laid over the stored document; a field it does not name is unchanged
     */
    public ShAgentProposed proposed(String rawAddress, String state, JsonNode data, boolean html) {
        ShContentPerspective perspective = ShContentPerspective.parse(state)
                .requireVersion("/agent/render", "POST /api/v2/agent/render");
        if (perspective.isRevision()) {
            // A revision is a record of what the page said; laying unsaved values over one would
            // render a document that has never existed and never could, under a version number.
            throw new ProposedOnRevisionException(state);
        }
        ShPostUnified post = postFor(rawAddress, perspective);
        shContentAclService.requireWrite(post.getId());

        String address = shAddressResolver.addressOf(post).toString();
        if (post.getShSite() == null || post.getFurl() == null || post.getFurl().isBlank()) {
            throw new NotAPageException(rawAddress, "post");
        }
        var rendered = pageRenderService.renderProposed(post.getShSite().getId(), post.getFurl(),
                post.getState(), data, Map.of());
        String markup = rendered.html();
        ShHtmlAppearance.Appearance look = ShHtmlAppearance.of(markup);
        ShAgentDigest digest = render(SOURCE_RENDERED, address, null, null, List.of(),
                ShHtmlOutline.of(markup), look, markersOf(markup), regionsOf(markup, look));
        return new ShAgentProposed(digest, html ? framed(markup, post, rendered) : null);
    }

    /**
     * SH1273 — the markup a console frame shows, with its site paths rebased the way delivery does.
     *
     * <p>The frame has no address of its own (it is a {@code srcdoc}), so a theme's
     * {@code url("/fonts/inter-400.woff2")} resolved against the console's origin rather than the
     * site, and asked the instance root for a font it does not serve there. Rebased into the site's
     * public {@code /sites/**} space with {@code ShPreviewLinks}, the pass that route itself runs, the
     * reference names bytes that route serves, and serves to a {@code null} origin since SH1273 too.
     * The public route and not {@code /preview}, because a sandboxed frame sends no session cookie, so
     * an authenticated route would answer it with a login. The digest above is computed from the
     * unrebased markup on purpose: an agent reads the page's own references.</p>
     */
    private String framed(String markup, ShPostUnified post,
                          com.viglet.shio.render.ShPageRenderService.ShRenderedPage rendered) {
        String site = post.getShSite().getFurl() == null || post.getShSite().getFurl().isBlank()
                ? post.getShSite().getName() : post.getShSite().getFurl();
        String prefix = com.viglet.shio.render.ShSitesAPI.Address.of(
                "/" + site + "/default/"
                        + com.viglet.shio.migration.unified.ShPostUnifiedWriteService.localeOf(post),
                null, null).prefix();
        return previewLinks.rewriteInto(markup, prefix, rendered,
                com.viglet.shio.api.agent.ShAddress.normalizePath(post.getFurl()));
    }

    /** The digest of the proposed page, and its markup where the caller asked for it. */
    public record ShAgentProposed(ShAgentDigest digest, String html) {
    }

    /** {@code state=rev:<n>} names a record; unsaved values have no place in one. */
    public static class ProposedOnRevisionException extends ShTeachingException {
        public ProposedOnRevisionException(String state) {
            super(ShProblem.badRequest("proposed-on-revision",
                    "A preview of unsaved values cannot be taken of a revision.")
                    .withDetail("state=" + state + " names a captured revision, which is a record "
                            + "of what this page said. Laying unsaved values over one would render "
                            + "a document that never existed, under a version number that says it "
                            + "did.")
                    .withFix("Drop `state`, or ask for `state=draft`, which is what a preview of "
                            + "work in progress is about.")
                    .withExample("POST /api/v2/agent/render {\"address\":\"post:Acme/about\","
                            + "\"data\":{\"title\":\"New\"}}"));
        }
    }

    private ShAgentDigest fromContent(String rawAddress, ShContentPerspective perspective) {
        ShPostUnified post = postFor(rawAddress, perspective);
        String address = shAddressResolver.addressOf(post).toString();

        // SH249 — the rendered page first, because it is what a visitor gets and the only artefact
        // that reflects regions *and* sections at once. Before this, `/agent/render` composed
        // sections and `/preview` composed regions, and neither knew the other existed: an agent
        // proving structure and a human looking at the page could be reading two different
        // compositions of the same post, which is law P9 failing where it is supposed to work.
        Optional<String> html = renderedHtml(post, perspective);
        if (html.isPresent()) {
            // SH187 — appearance only here, because it describes the *document* a visitor gets. A
            // post's own markup field is not a page and carries no stylesheet, so an appearance
            // block for it would be a section of nulls pretending to be an answer.
            ShHtmlAppearance.Appearance look = ShHtmlAppearance.of(html.get());
            return render(SOURCE_RENDERED, address, null, null, List.of(),
                    ShHtmlOutline.of(html.get()), look,
                    markersOf(html.get()),
                    // SH687 — the undefined classes are handed to the walk rather than recomputed,
                    // so a region's count and the document's list cannot disagree about what is
                    // undefined. One judgement, attributed; two would be two things to keep in step.
                    regionsOf(html.get(), look));
        }

        List<String> fields = new ArrayList<>();
        ShHtmlOutline.Outline outline = own(post, fields);
        // SH169, now the labelled fallback for a post that cannot be rendered — no layout binding
        // yet, or no friendly URL to render at. That is the normal state of a site mid-build, and
        // it is exactly the case SH169 was filed for: without this, a section-composed page
        // digested as the hash of nothing and was indistinguishable from a blank one. `source`
        // says which question was answered, so a fallback is never mistaken for the real thing.
        outline = outline.merge(composed(post, fields));
        return render(SOURCE_CONTENT, address, null, null, fields, outline);
    }

    /**
     * The page as {@link ShPageRenderService} composes it, or empty when it cannot be composed.
     *
     * <h3>Why every failure is empty rather than an error</h3>
     *
     * <p>The renderer throws a teaching exception for an unbound post-type, an absent layout and a
     * page it cannot find — all correct for somebody who asked to <em>see</em> a page, and all
     * wrong here. A digest that started failing where it used to answer would break the SH99 loop
     * for every site whose model is not finished, which is every site mid-build. So a page that
     * cannot be rendered falls back and says so in {@code source}.</p>
     *
     * <p>A post with no friendly URL (SH172's {@code id:} case) has nothing to render <em>at</em>,
     * since the render route is addressed by URL. That is the second fallback path and not a
     * defect.</p>
     */
    private Optional<String> renderedHtml(ShPostUnified post, ShContentPerspective perspective) {
        if (post.getShSite() == null || post.getFurl() == null || post.getFurl().isBlank()) {
            return Optional.empty();
        }
        try {
            // SH990 — a revision renders through the renderer's own revision path, so the digest and
            // /preview?state=rev:<n> compose the same document; a row renders at the row's state.
            return Optional.of((perspective.isRevision()
                    ? pageRenderService.render(post.getShSite().getId(), post.getFurl(), perspective,
                            Map.of())
                    : pageRenderService.render(post.getShSite().getId(), post.getFurl(),
                            post.getState()))
                    .html());
        } catch (ShTeachingException e) {
            // Unbound type, missing layout, unknown page: all "this is not a renderable page",
            // which is information the fallback carries rather than an error the caller has to
            // handle. Narrow on purpose — anything that is not a teaching exception is a real
            // failure and is not swallowed here.
            return Optional.empty();
        }
    }

    /** A post's own markup fields, in ordinal order — what the digest has always read. */
    private ShHtmlOutline.Outline own(ShPostUnified post, List<String> fields) {
        JsonNode data = data(post);
        ShHtmlOutline.Outline outline = ShHtmlOutline.of(null);
        for (ShPostTypeAttr attr : markupFields(post.getShPostType())) {
            JsonNode value = data.get(attr.getName());
            if (value == null || !value.isString() || value.asString().isBlank()) {
                continue;
            }
            fields.add(attr.getName());
            outline = outline.merge(ShHtmlOutline.of(value.asString()));
        }
        return outline;
    }

    /**
     * The structure of the posts this page references, folded in declared order (SH169).
     *
     * <p><b>An unresolvable section is marked, not skipped.</b> Contributing nothing is the
     * present failure wearing a different hat — the page would digest as if the section were
     * simply absent, and two different mistakes would hash the same. It contributes a heading
     * naming the gap instead, so the digest changes when a reference breaks and a reader can see
     * why. SH154 is what reports it as a defect; this only makes sure the proof does not lie.</p>
     *
     * <p><b>Cost:</b> a page digest now reads N+1 posts. That is inherent — the structure really
     * is somewhere else — and it is the same scoping pressure as SH143.</p>
     */
    private ShHtmlOutline.Outline composed(ShPostUnified post, List<String> fields) {
        JsonNode data = data(post);
        ShHtmlOutline.Outline outline = ShHtmlOutline.of(null);
        String siteId = post.getShSite() == null ? null : post.getShSite().getId();
        for (ShPostTypeAttr attr : referenceFields(post.getShPostType())) {
            List<String> urls = ShPostReferenceFields.urls(data.get(attr.getName()));
            if (urls.isEmpty()) {
                continue;
            }
            fields.add(attr.getName());
            for (String url : urls) {
                Optional<ShPostUnified> section = referenced(siteId, url);
                outline = outline.merge(section.isPresent()
                        ? sectionOutline(section.get())
                        : ShHtmlOutline.of("<h2>[missing section " + url + "]</h2>"));
            }
        }
        return outline;
    }

    /**
     * One referenced post's structure.
     *
     * <h2>Why this is read from the post-type and not from a list of the eight first-party types</h2>
     *
     * <p>A section's structure is not in one HTML field: SH95's components render the section's
     * title as a heading and its text fields as prose. So the mapping is stated once, in terms of
     * what the model says about a field rather than what a type is called — a title field becomes a
     * heading, a markup field contributes its own parsed structure, and any other text field
     * contributes its words. A digest that knew the eight shipped section types by name would stop
     * working the first time somebody wrote a ninth.</p>
     *
     * <p>The synthetic markup is deliberate: it runs the same parser everything else does, so
     * there is one notion of "structure" rather than a second code path that would drift from it.
     * The heading level is the vocabulary's convention (a section heading sits under the page's
     * own h1), stated here once instead of guessed per type.</p>
     */
    private ShHtmlOutline.Outline sectionOutline(ShPostUnified section) {
        JsonNode data = data(section);
        ShHtmlOutline.Outline outline = ShHtmlOutline.of(null);
        for (ShPostTypeAttr attr : textFields(section.getShPostType())) {
            JsonNode value = data.get(attr.getName());
            if (value == null || !value.isString() || value.asString().isBlank()) {
                continue;
            }
            String text = value.asString();
            if (MARKUP_WIDGETS.contains(widgetName(attr))) {
                outline = outline.merge(ShHtmlOutline.of(text));
            } else if (attr.getIsTitle() == 1) {
                outline = outline.merge(ShHtmlOutline.of("<h2>" + escape(text) + "</h2>"));
            } else {
                outline = outline.merge(ShHtmlOutline.of("<p>" + escape(text) + "</p>"));
            }
        }
        return outline;
    }

    /** Multi Select fields that declare they hold post URLs (SH154's setting). */
    private List<ShPostTypeAttr> referenceFields(ShPostType postType) {
        return topLevel(postType).stream()
                .filter(attr -> ShPostReferenceFields.referencesPosts(attr, objectMapper))
                .toList();
    }

    /** Every text-bearing field of a post-type, ordinal-ordered so the outline is stable (P5). */
    private static List<ShPostTypeAttr> textFields(ShPostType postType) {
        return topLevel(postType).stream()
                .filter(attr -> TEXT_WIDGETS.contains(widgetName(attr))
                        || MARKUP_WIDGETS.contains(widgetName(attr)))
                .toList();
    }

    private static List<ShPostTypeAttr> topLevel(ShPostType postType) {
        if (postType == null || postType.getShPostTypeAttrs() == null) {
            return List.of();
        }
        return postType.getShPostTypeAttrs().stream()
                .filter(attr -> attr.getShParentPostTypeAttr() == null)
                .filter(attr -> attr.getName() != null && !attr.getName().isBlank())
                .sorted(Comparator.comparingInt(ShPostTypeAttr::getOrdinal)
                        .thenComparing(ShPostTypeAttr::getName))
                .toList();
    }

    private static String widgetName(ShPostTypeAttr attr) {
        return attr.getShWidget() == null ? "" : attr.getShWidget().getName();
    }

    /**
     * The post a URL names in this site, either state — existence is what matters.
     *
     * <p>SH583 — every spelling, through the shared generator. An href written {@code /x} has to
     * find the post SH467 stored at {@code /x/}, or a replica whose links are the source's own
     * verifies as {@code page-reference-broken} against content that is there.</p>
     */
    private Optional<ShPostUnified> referenced(String siteId, String url) {
        if (siteId == null) {
            return Optional.empty();
        }
        for (String candidate : ShFurlSpellings.candidates(url)) {
            Optional<ShPostUnified> found = shPostUnifiedRepository
                    .findByShSite_IdAndFurlAndState(siteId, candidate, ShPostState.DRAFT)
                    .or(() -> shPostUnifiedRepository.findByShSite_IdAndFurlAndState(siteId,
                            candidate, ShPostState.PUBLISHED));
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    /** Escapes the four characters that would otherwise change the synthetic markup's shape. */
    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /** Rich-text attributes of a post-type, ordinal-ordered (P5). */
    private static List<ShPostTypeAttr> markupFields(ShPostType postType) {
        if (postType == null || postType.getShPostTypeAttrs() == null) {
            return List.of();
        }
        return postType.getShPostTypeAttrs().stream()
                .filter(attr -> attr.getShParentPostTypeAttr() == null)
                .filter(attr -> attr.getName() != null && !attr.getName().isBlank())
                .filter(attr -> attr.getShWidget() != null
                        && MARKUP_WIDGETS.contains(attr.getShWidget().getName()))
                .sorted(Comparator.comparingInt(ShPostTypeAttr::getOrdinal)
                        .thenComparing(ShPostTypeAttr::getName))
                .toList();
    }

    // --- fetch --------------------------------------------------------------

    private ShAgentDigest fromUrl(String url) {
        if (!deliveryProbe.enabled()) {
            throw new FetchDisabledException();
        }
        URI target = ShAgentDeliveryProbe.absolute(url);
        if (target == null) {
            throw new BadFetchUrlException(url, "Only absolute http(s) URLs can be fetched.");
        }
        List<String> origins = deliveryProbe.readableOrigins();
        if (!deliveryProbe.hosted(target, origins)) {
            throw new UnhostedUrlException(target.toString(), origins);
        }
        ShAgentDeliveryProbe.Outcome outcome = deliveryProbe.get(target);
        if (outcome.failure() != null) {
            throw new FetchFailedException(target.toString(), outcome.failure());
        }
        // The status travels even on a 404: "the page 404s" is the single most useful
        // thing this call can say, and throwing it away as an error would hide it.
        return render(SOURCE_FETCH, null, target.toString(), outcome.status(),
                List.of(), ShHtmlOutline.of(outcome.body()));
    }

    // --- shaping ------------------------------------------------------------

    private ShAgentDigest render(String source, String address, String url, Integer status,
                                 List<String> fields, ShHtmlOutline.Outline outline) {
        return render(source, address, url, status, fields, outline, null);
    }

    private ShAgentDigest render(String source, String address, String url, Integer status,
                                 List<String> fields, ShHtmlOutline.Outline outline,
                                 ShHtmlAppearance.Appearance appearance) {
        return render(source, address, url, status, fields, outline, appearance, null);
    }

    /**
     * SH299 — counts the defect markers a rendered page carries.
     *
     * <p>Read with {@link ShRegionComposer#DEFECT_MARKERS}, which is the composer's own list, so
     * this cannot come to count an attribute the renderer stopped emitting or miss one it added —
     * the drift SH260 removed for helpers, on the rendered side.</p>
     */
    /**
     * SH687 — the page's landmarks in document order, projected onto the wire type.
     *
     * <p>Two defects on the replica's home page were positional and therefore unsayable: the
     * captured footer renders <b>above</b> the first section, and the sections are presented by a
     * different theme than the chrome. {@code landmarks} is a histogram and could carry neither.
     * This carries the first — order, and who owns each region's markup.</p>
     */
    private static List<ShAgentDigest.Region> regionsOf(String html,
                                                        ShHtmlAppearance.Appearance appearance) {
        return ShHtmlRegions.of(html,
                        appearance == null ? List.of() : appearance.unstyledClasses(),
                        // SH694 — read from the same page, so a region's `styledBy` and the
                        // document's `definedClasses` cannot be answering about different bytes.
                        ShHtmlAppearance.definedBySheet(html))
                .stream()
                .map(region -> new ShAgentDigest.Region(
                        region.tag(), region.order(), region.markup(), region.unstyled(),
                        region.styledBy()))
                .toList();
    }

    private static ShAgentDigest.Markers markersOf(String html) {
        Document document = Jsoup.parse(html);
        int missing = document.select("[" + ShRegionComposer.ANNOTATION_MISSING + "]").size();
        int unpublished =
                document.select("[" + ShRegionComposer.ANNOTATION_UNPUBLISHED + "]").size();
        int theme = document.select("[" + ShRegionComposer.ANNOTATION_THEME_MISSING + "]").size();
        return new ShAgentDigest.Markers(missing, unpublished, theme);
    }

    private ShAgentDigest render(String source, String address, String url, Integer status,
                                 List<String> fields, ShHtmlOutline.Outline outline,
                                 ShHtmlAppearance.Appearance appearance,
                                 ShAgentDigest.Markers markers) {
        return render(source, address, url, status, fields, outline, appearance, markers, null);
    }

    /**
     * @param regions SH687 — the landmarks in document order, or {@code null} where there is no
     *                rendered page to walk. Passed in rather than derived here for
     *                {@code appearance}'s reason: only the caller holding the rendered HTML knows
     *                whether it is looking at a document or at a post's own markup field
     */
    private ShAgentDigest render(String source, String address, String url, Integer status,
                                 List<String> fields, ShHtmlOutline.Outline outline,
                                 ShHtmlAppearance.Appearance appearance,
                                 ShAgentDigest.Markers markers,
                                 List<ShAgentDigest.Region> regions) {
        List<ShAgentDigest.Heading> headings = outline.headings().stream()
                .map(heading -> new ShAgentDigest.Heading(heading.level(), heading.text()))
                .toList();
        List<ShAgentDigest.Link> links = outline.links().stream()
                .map(link -> new ShAgentDigest.Link(link.href(), internal(link.href()), link.text()))
                .toList();
        List<ShAgentDigest.Image> images = outline.images().stream()
                .map(image -> new ShAgentDigest.Image(image.src(), image.alt()))
                .toList();
        ShAgentDigest.Appearance look = appearance == null ? null : new ShAgentDigest.Appearance(
                appearance.classes(), appearance.unstyledClasses(), appearance.inlineStyles(),
                appearance.imagesWithoutSize(), appearance.externalStylesheets(),
                appearance.definedClasses(),
                // SH851 — beside `unstyledClasses`, which is the half of this question the digest
                // already answered, and carrying the same three things: which names, how many
                // reads, and the denominator that says whether the zero means anything.
                appearance.danglingTokens(), appearance.danglingTokenRefs(),
                appearance.definedTokens());
        return new ShAgentDigest(source, address, url, status,
                fields.isEmpty() ? null : List.copyOf(fields),
                headings, outline.landmarks(), links, images, outline.words(),
                digestOf(headings, outline.landmarks(), links, images),
                look,
                // SH187/SH194 — a *second* hash, deliberately not folded into `digest`. A theme
                // edit changes how the page looks and not what it is, so one hash covering both
                // could not tell "I restyled it" from "I broke the outline" — which is precisely
                // the discrimination an iterative loop needs. Two hashes, two questions.
                look == null ? null : appearanceDigestOf(look),
                // SH299 — beside the two hashes and in neither of them: a marker count says the
                // page is broken, which is a different question from "did its structure change".
                markers,
                // SH687 — and in neither hash either, for a third reason: this is the SAME structure
                // `landmarks` already hashes, read in document order. Folding it in would make the
                // digest change for pages whose shape did not, which is the discrimination the two
                // hashes above exist to protect.
                regions);
    }

    /**
     * A hash over the appearance surface only.
     *
     * <p>Its own inputs are sorted before hashing (SH136's rule that a hash sorts its own inputs
     * rather than trusting the caller), and the counts are included because "six inline styles
     * became seven" is a change to how the page looks.</p>
     */
    private static String appearanceDigestOf(ShAgentDigest.Appearance look) {
        StringBuilder canonical = new StringBuilder();
        canonical.append(String.join(",", new java.util.TreeSet<>(look.classes()))).append('|')
                .append(String.join(",", new java.util.TreeSet<>(look.unstyledClasses()))).append('|')
                .append(look.inlineStyles()).append('|')
                .append(String.join(",", new java.util.TreeSet<>(look.imagesWithoutSize()))).append('|')
                .append(String.join(",", new java.util.TreeSet<>(look.externalStylesheets()))).append('|')
                .append(look.definedClasses()).append('|')
                // SH851 — in the hash, for the reason the counts above are in it and one degree
                // stronger: 231 dangling references becoming 958 is not "six inline styles became
                // seven", it is most of a page's colour, spacing and animation going out. The
                // regression that motivated this moved no hash anywhere, which is the whole
                // complaint. Every appearance hash in the instance changes once, as it did when
                // `definedClasses` was added, and the outline hash beside it does not move at all.
                .append(String.join(",", new java.util.TreeSet<>(look.danglingTokens()))).append('|')
                .append(look.danglingTokenRefs()).append('|')
                .append(look.definedTokens());
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            // `a:` rather than `d:`, so the two hashes cannot be confused for one another in a
            // report, a log or a diff — they answer different questions.
            StringBuilder hex = new StringBuilder("a:");
            for (int i = 0; i < 6; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static boolean internal(String href) {
        return href != null && href.startsWith("/") && !href.startsWith("//");
    }

    /**
     * {@code d:} + the first 12 hex of SHA-256 over the canonical outline. Short enough
     * to carry in a report, long enough to trust — the same shape as the context pack's.
     */
    static String digestOf(List<ShAgentDigest.Heading> headings, java.util.Map<String, Integer> landmarks,
                           List<ShAgentDigest.Link> links, List<ShAgentDigest.Image> images) {
        StringBuilder canonical = new StringBuilder();
        headings.forEach(heading -> canonical.append('h').append(heading.level()).append(' ')
                .append(heading.text()).append('\n'));
        // Sorted here, not trusted from the caller (SH136). The landmark map arrived sorted
        // by construction and then lost that order to a `Map.copyOf` for as long as SH101 has
        // existed, so the same page hashed differently after a restart — silently defeating
        // the one promise a digest makes. A hash must not depend on a container's iteration
        // order: it is the one place where "the caller sorts it" is not a good enough contract,
        // because the failure is a wrong answer rather than an exception.
        new java.util.TreeMap<>(landmarks).forEach((name, count) ->
                canonical.append('l').append(' ').append(name)
                        .append(' ').append(count).append('\n'));
        links.forEach(link -> canonical.append('a').append(' ').append(link.href()).append('\n'));
        images.forEach(image -> canonical.append('i').append(' ').append(image.src())
                .append(' ').append(image.alt() == null ? "-" : "alt").append('\n'));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder("d:");
            for (int i = 0; i < 6; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * The post a perspective names, or the refusal that says which part of it does not exist.
     *
     * <p>A revision comes back as {@link ShPostRevisionService#asOf}'s transient copy, so the content
     * fallback reads what the page said then and not what the draft says now. A row the post does not
     * have is SH619's 404, which tells "never published" from "an uploaded file with no draft".</p>
     */
    private ShPostUnified postFor(String rawAddress, ShContentPerspective perspective) {
        var resolved = shAddressResolver.resolve(rawAddress);
        ShPostUnified present = draftPreferred(resolved.id())
                .orElseThrow(() -> new NotAPageException(rawAddress, resolved.kind()));
        if (perspective.row() != null) {
            return shPostUnifiedRepository.findByIdAndState(present.getId(), perspective.row())
                    .orElseThrow(() -> new ShAgentReadService.NoSuchStateException(rawAddress,
                            perspective.row()));
        }
        if (perspective.isRevision()) {
            int number = perspective.revision();
            return shPostRevisionService.revision(present.getId(), number)
                    .map(revision -> shPostRevisionService.asOf(revision, present))
                    .orElseThrow(() -> ShPostStateNotFoundException.noSuchRevision(present.getId(),
                            number, shPostRevisionService.rows(present.getId()).stream()
                                    .map(ShRevisionRow::version).toList()));
        }
        return present;
    }

    private java.util.Optional<ShPostUnified> draftPreferred(String postId) {
        var draft = shPostUnifiedRepository.findByIdAndState(postId, ShPostState.DRAFT);
        return draft.isPresent()
                ? draft
                : shPostUnifiedRepository.findByIdAndState(postId, ShPostState.PUBLISHED);
    }

    private JsonNode data(ShPostUnified post) {
        String json = post.getData();
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode parsed = objectMapper.readTree(json);
            return parsed.isObject() ? parsed : objectMapper.createObjectNode();
        } catch (JacksonException e) {
            return objectMapper.createObjectNode();
        }
    }

    // --- teaching errors (SH80) ---------------------------------------------

    /** A 400: a version was asked of a delivery URL, which is served as it is (SH990). */
    public static class StateWithUrlException extends ShTeachingException {

        public StateWithUrlException(String sent) {
            super(ShProblem.badRequest("state-with-url", "`state` does not apply to `url`")
                    .withDetail("`url` digests the page a visitor is served, which has one version. "
                            + "`state=" + sent + "` names a version of a post, which is reached by "
                            + "its address.")
                    .withFix("Send ?address=post:<site>/<url>&state=" + sent + " to digest that "
                            + "version, or drop `state` to digest the delivered page.")
                    .withExample("?address=post:Blog/blog/hello-world&state=published"));
        }
    }

    /** A 400: neither target, or both. */
    public static class NoDigestTargetException extends ShTeachingException {

        public NoDigestTargetException(boolean both) {
            super(ShProblem.badRequest("no-digest-target",
                            both ? "Pass either `address` or `url`, not both"
                                 : "Nothing to digest")
                    .withDetail(both
                            ? "`address` digests the markup Shio holds; `url` digests what a "
                                    + "visitor gets. They answer different questions, so the call "
                                    + "picks one."
                            : "A digest needs a page: a post address, or a delivery URL.")
                    .withFix("Send ?address=post:<site>/<url> for the authoring view, or "
                            + "?url=https://… for the delivered page.")
                    .withExample("?address=post:Blog/blog/hello-world"));
        }
    }

    /** A 400: the address resolved, but not to a post. */
    public static class NotAPageException extends ShTeachingException {

        public NotAPageException(String address, String kind) {
            super(ShProblem.badRequest("not-a-page",
                            "'" + address + "' is a " + kind + ", not a post")
                    .withDetail("A digest summarises one page's structure; a folder has none.")
                    .withFix("Pick a post with GET /api/v2/agent/find?folder=… and digest that.")
                    .withExample("?address=post:Blog/blog/hello-world"));
        }
    }

    /** A 400 that says which property turns fetching on, rather than pretending it 404s. */
    public static class FetchDisabledException extends ShTeachingException {

        public FetchDisabledException() {
            super(ShProblem.badRequest("fetch-disabled", "Fetching a delivery URL is disabled")
                    .withDetail("An endpoint that fetches URLs on the server's behalf is a "
                            + "request-forgery surface, so it is off unless an operator turns it on.")
                    .withFix("Set shio.agent.render.allow-fetch=true (and give each site its URL), "
                            + "or digest the authoring view with ?address=post:<site>/<url>, which "
                            + "needs no front end running.")
                    .withExample("?address=post:Blog/blog/hello-world"));
        }
    }

    /** A 400 for a URL this instance does not host. */
    public static class UnhostedUrlException extends ShTeachingException {

        public UnhostedUrlException(String sent, List<String> allowed) {
            super(ShProblem.badRequest("unhosted-url", "Refusing to fetch " + sent)
                    .withDetail("Only the origins of sites this credential can read may be "
                            + "fetched — otherwise this endpoint is a proxy for the server's "
                            + "network position.")
                    .withAllowed(allowed)
                    .withFix(allowed.isEmpty()
                            ? "No readable site has a URL configured. Set the site's URL first, "
                                    + "or digest the authoring view with ?address=."
                            : "Fetch a path under one of the listed origins.")
                    .withExample(allowed.isEmpty() ? "?address=post:Blog/blog/hello-world"
                            : "?url=" + allowed.get(0) + "/blog/hello-world"));
        }
    }

    /** A 400 for a malformed URL. */
    public static class BadFetchUrlException extends ShTeachingException {

        public BadFetchUrlException(String sent, String why) {
            super(ShProblem.badRequest("bad-fetch-url", "Cannot fetch '" + sent + "'")
                    .withDetail(why)
                    .withFix("Send an absolute URL including the scheme.")
                    .withExample("?url=https://example.com/blog/hello-world"));
        }
    }

    /**
     * A 400, not a 502: from the caller's side the actionable fact is that the front end
     * is not answering, and a fix it can act on ("start it, or digest the content
     * instead") is worth more than the correct gateway status.
     */
    public static class FetchFailedException extends ShTeachingException {

        public FetchFailedException(String url, String why) {
            super(ShProblem.badRequest("fetch-failed", "Could not fetch " + url)
                    .withDetail(why == null ? "The request did not complete." : why)
                    .withFix("Check the front end is running and reachable from this instance, "
                            + "or digest the authoring view with ?address=post:<site>/<url>.")
                    .withExample("?address=post:Blog/blog/hello-world"));
        }
    }
}
