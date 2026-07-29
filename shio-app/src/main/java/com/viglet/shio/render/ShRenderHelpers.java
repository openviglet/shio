/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jakarta.annotation.PostConstruct;

import org.springframework.stereotype.Component;

import com.github.jknack.handlebars.Options;
import com.viglet.shio.api.agent.ShAddress;
import com.viglet.shio.api.agent.ShAddressResolver;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;
import com.viglet.shio.render.ShTemplateEngine.ShHelperShape;
import com.viglet.shio.utils.ShStableMap;

/**
 * SH183 — the closed capability set, as registered helpers instead of bindings on an
 * unfiltered script engine.
 *
 * <h2>This is what {@code shObject.js} was trying to be</h2>
 *
 * <p>FSP's façade exposed seven functions and nothing else — {@code navigation},
 * {@code navigationFolder}, {@code query}, {@code queryByPostType}, {@code searchComponent},
 * {@code formComponent}, {@code getRelation} — each a one-line wrapper over a Spring bean.
 * That is an allow-list <em>expressed as a convention</em>: the script could reach anything
 * the JVM could, and the façade was merely the polite way to do it. Registered helpers are
 * the same allow-list expressed as a fact.</p>
 *
 * <h2>Why this is not a loss of expressiveness</h2>
 *
 * <p>The 2018 regions spent between 767 and 1098 characters each on hand-written
 * JavaScript, and reading them, almost none of it was capability: it was a call to one of
 * the seven functions followed by a loop remapping {@code TITLE}/{@code DESCRIPTION}/
 * {@code TEXT} into a shape the template could iterate. A block helper yields the rows
 * already shaped, so the whole remap becomes the block's body:</p>
 *
 * <pre>{@code
 * {{#query folder="/blog" type="Article" limit=10}}
 *   <div class="card"><h5>{{title}}</h5><a href="{{link}}">Read More</a></div>
 * {{/query}}
 * }</pre>
 *
 * <h2>What is here and what is not</h2>
 *
 * <p>{@code query}, {@code navigation} and {@code file_url} are the three the shipped sample
 * used. SH247 adds {@code getRelation} and {@code search}, which leaves {@code form} as the
 * only one of the seven still missing — and it is missing for a reason that is not
 * implementation effort: a form needs a decision about <em>where a submission goes</em>, and
 * that is a subsystem (storage, spam, notification, GDPR) rather than a helper. It stays
 * filed rather than stubbed, because a helper that silently renders nothing is worse than the
 * unknown-helper error Handlebars raises with a line and a column.</p>
 *
 * <p><b>Every row the three listing helpers yield has the same shape</b> ({@link #row}), which
 * is the property that makes them interchangeable in a template: an author who has written a
 * card for {@code {{#query}}} can iterate a relation or a search result with the same body. A
 * helper that yielded its own row shape would make the capability set five vocabularies
 * instead of one.</p>
 *
 * <h2>Determinism</h2>
 *
 * <p>Every listing is sorted before it is yielded, and by a total order — a date with an
 * id tiebreak, never a date alone, because two posts written in the same millisecond
 * would otherwise swap places between renders and move SH101's digest. Rows are built
 * with {@link ShStableMap} for the same reason (SH136).</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShRenderHelpers {

    /** A listing with no ceiling is a page that gets slower as the site grows. */
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 500;

    /** The model path holding the site being rendered — read by every listing helper. */
    private static final String SITE_ID = "site.id";

    private final ShTemplateEngine engine;
    private final ShAddressResolver shAddressResolver;
    private final ShFolderRepository shFolderRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShPostTypeRepository shPostTypeRepository;
    private final ShSiteRepository shSiteRepository;
    private final ShRenderModel shRenderModel;

    public ShRenderHelpers(ShTemplateEngine engine,
                           ShAddressResolver shAddressResolver,
                           ShFolderRepository shFolderRepository,
                           ShPostUnifiedRepository shPostUnifiedRepository,
                           ShPostTypeRepository shPostTypeRepository,
                           ShSiteRepository shSiteRepository,
                           ShRenderModel shRenderModel) {
        this.engine = engine;
        this.shAddressResolver = shAddressResolver;
        this.shFolderRepository = shFolderRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shPostTypeRepository = shPostTypeRepository;
        this.shSiteRepository = shSiteRepository;
        this.shRenderModel = shRenderModel;
    }

    /**
     * What every listing helper puts in a row, sorted (SH260).
     *
     * <p>Declared here, beside {@link #row}, because this is the method that decides it. SH251's
     * lint reads it to avoid warning about a legitimate listing body, and before this it kept its
     * own copy — a fifth restatement of the capability set, whose failure mode was a warning on a
     * correct template.</p>
     */
    public static final Set<String> ROW_KEYS =
            Set.of("id", "title", "summary", "url", "link", "type", "name");

    @PostConstruct
    void register() {
        // The shape is declared with the helper (SH260): the engine is the one place that knows
        // the capability set, and everything that reads it — the unknown-helper refusal, the SH251
        // field scanner — asks rather than restating.
        // The signature travels with the registration for the same reason (SH272): a hash
        // parameter's name exists only inside the helper's body, so nothing downstream could
        // derive it, and the context pack now answers "what may a template call" from here.
        engine.register("query", ShHelperShape.BLOCK,
                "{{#query folder= type= limit=}}…{{/query}}", this::query);
        engine.register("navigation", ShHelperShape.BLOCK,
                "{{#navigation folder=}}…{{/navigation}}", this::navigation);
        engine.register("search", ShHelperShape.BLOCK,
                "{{#search q= type= limit=}}…{{/search}}", this::search);
        engine.register("getRelation", ShHelperShape.BLOCK_PATH_ARG,
                "{{#getRelation FIELD}}…{{/getRelation}}", this::getRelation);
        engine.register("file_url", ShHelperShape.INLINE_PATH_ARG,
                "{{file_url FIELD}}", this::fileUrl);
    }

    /**
     * {@code {{#getRelation SECTIONS}}…{{/getRelation}}} — the posts a reference field names,
     * in the order the field names them (SH247).
     *
     * <h3>It takes the field's value, not the field's name</h3>
     *
     * <p>Which means it needs no access to the post-type, and it works on any of the three
     * shapes a reference actually takes in this model: a {@code Content Select}'s post id
     * (a single string), an SH95 {@code Multi Select} of friendly URLs (a list), and a
     * single friendly URL. That is what FSP's {@code getRelation} took too.</p>
     *
     * <h3>Each entry resolves by id, then by friendly URL — in that order</h3>
     *
     * <p>The same rule SH181 settled for {@code GET /api/v2/site/{id}}: the declared key wins a
     * collision. A UUID is not a plausible friendly URL, so in practice the two never compete,
     * and fixing the order means a template's idea of what a reference points at cannot depend
     * on what else happens to be in the site. The URL half is scoped to the <b>current
     * site</b>, like {@code query}'s {@code folder} — a relation that could reach another
     * site's content would make a shared region's output depend on which page slotted it.</p>
     *
     * <h3>An entry that resolves to nothing is skipped, not marked</h3>
     *
     * <p>Deliberately unlike SH183's missing <em>region</em>, and the difference is who else can
     * see it. A region slot is named inside a layout's HTML, so before SH193 nothing but the
     * renderer could report an unfilled one — hence the marker. A dangling reference in a
     * {@code Content Select} or a {@code references: "post"} {@code Multi Select} is already an
     * SH154 {@code dangling-reference} finding in {@code shio verify}, with an address and a
     * {@code fix}. Marking it in the page as well would put the same defect in two reports that
     * can disagree, and the row shape would have to grow a {@code missing} flag every template
     * then has to branch on. {@code {{else}}} still renders when <em>nothing</em> resolved, so
     * "the references are all broken" is not silently an empty {@code <div>}.</p>
     */
    Object getRelation(Object context, Options options) throws IOException {
        String siteId = string(options.context.get(SITE_ID));
        List<String> references = referencesOf(context);
        if (siteId == null || references.isEmpty()) {
            return options.inverse();
        }
        ShPostState state = state(options);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String reference : references) {
            resolveReference(siteId, reference, state).ifPresent(post -> rows.add(row(post)));
        }
        if (rows.isEmpty()) {
            return options.inverse();
        }
        StringBuilder out = new StringBuilder();
        for (Map<String, Object> row : rows) {
            out.append(options.fn(row));
        }
        return out;
    }

    /**
     * {@code {{#search q="handbook" type="Article" limit=10}}…{{/search}}} — the site's posts
     * matching a term (SH247).
     *
     * <h3>It is the CDA's predicate, not a second idea of what "matches" means</h3>
     *
     * <p>{@code FULLTEXT_PREDICATE} (SH131), reached through the repository's {@code search*}
     * default methods so the term is normalized exactly once — normalizing again searches for
     * {@code %%term%%} and matches nothing, which looks like an empty index rather than a bug.
     * Title, summary and stored field data, the same predicate the delivery search and the SH240
     * console listing use. Three surfaces, one answer.</p>
     *
     * <h3>What this is and what it is not</h3>
     *
     * <p>A term the <b>template</b> supplies, literal or read from the model
     * ({@code q=TOPIC} takes the page's own field). That is a tag page, a "more about this"
     * listing, a curated collection — genuinely the thing several of the 2018 regions were
     * doing by hand. It is <b>not</b> a search-results page driven by {@code ?q=}, because the
     * model holds no request parameters: SH183 kept the raw request out of template scope on
     * purpose, so putting a query string back in is a decision of its own rather than a
     * consequence of registering this helper. That is SH259.</p>
     *
     * <p>A blank {@code q} renders {@code {{else}}} rather than the whole site: a search box
     * nobody has typed in yet must not read as "everything matched".</p>
     */
    Object search(Object context, Options options) throws IOException {
        String siteId = string(options.context.get(SITE_ID));
        String term = hash(options, "q", null);
        if (siteId == null || term == null) {
            return options.inverse();
        }
        String type = hash(options, "type", null);
        List<ShPostState> states = statesFor(state(options));

        List<ShPostUnified> found = type == null
                ? shPostUnifiedRepository.searchBySitesAndStateIn(List.of(siteId), states, term)
                : searchByType(siteId, type, states, term);

        List<Map<String, Object>> rows = preferDraft(found, state(options)).stream()
                .sorted(byDateThenId())
                .limit(limit(options))
                .map(this::row)
                .toList();
        if (rows.isEmpty()) {
            return options.inverse();
        }
        StringBuilder out = new StringBuilder();
        for (Map<String, Object> row : rows) {
            out.append(options.fn(row));
        }
        return out;
    }

    /**
     * A type filter resolved to an id before the query, not filtered afterwards.
     *
     * <p>Not politeness: {@code p.shPostType.name} in JPQL joins {@code ShPostType}, whose
     * {@code tenant_id} lives on the {@code ShObject} root, so Hibernate emits the tenant
     * predicate against a subclass alias that has no such column and the query dies at prepare
     * time (the SH22 join hazard). The repository's {@code searchBySiteAndTypes*} methods take
     * ids for exactly that reason.</p>
     *
     * <p>An unknown type name yields nothing rather than everything — a filter that silently
     * stopped filtering would be a template quietly rendering the whole site.</p>
     */
    private List<ShPostUnified> searchByType(String siteId, String type, List<ShPostState> states,
                                             String term) {
        ShPostType postType = shPostTypeRepository.findByName(type);
        if (postType == null) {
            return List.of();
        }
        return shPostUnifiedRepository.searchBySiteAndTypesAndStateIn(
                siteId, List.of(postType.getId()), states, term);
    }

    /** The reference values a field holds, whatever shape it holds them in. */
    private List<String> referencesOf(Object value) {
        if (value instanceof Iterable<?> many) {
            List<String> out = new ArrayList<>();
            for (Object item : many) {
                String text = string(item);
                if (text != null && !text.isBlank()) {
                    out.add(text.trim());
                }
            }
            return out;
        }
        String single = string(value);
        return single == null || single.isBlank() ? List.of() : List.of(single.trim());
    }

    /** By id, then by friendly URL within this site — SH181's order. */
    private Optional<ShPostUnified> resolveReference(String siteId, String reference,
                                                     ShPostState state) {
        List<ShPostState> states = statesFor(state);
        Optional<ShPostUnified> byId = preferDraft(
                shPostUnifiedRepository.findById(reference).stream().toList(), state).stream()
                .findFirst();
        if (byId.isPresent()) {
            return byId;
        }
        String url = reference.startsWith("/") ? reference : "/" + reference;
        // SH279 — a friendly URL is unique per site and that uniqueness is *reported* by SH99's
        // `duplicate-url` rule rather than enforced (SH243 is open about a trashed post still holding
        // one), so two posts can answer here. No ordering is added because `preferDraft` gained a
        // total order in SH278: this read is already deterministic through the shared writer, which
        // is the argument for having put the order there rather than at each call site.
        return preferDraft(
                shPostUnifiedRepository.findByShSite_IdAndStateIn(siteId, states).stream()
                        .filter(post -> url.equals(normalizedFurl(post)))
                        .toList(),
                state).stream()
                .findFirst();
    }

    private String normalizedFurl(ShPostUnified post) {
        String furl = post.getFurl();
        if (furl == null || furl.isBlank()) {
            return null;
        }
        return furl.startsWith("/") ? furl : "/" + furl;
    }

    /**
     * {@code {{#query folder="/blog" type="Article" limit=10}}…{{/query}}} — the posts of
     * a folder, each row a map the block body reads directly.
     *
     * <p>{@code folder} is a name chain within the current site (SH79's folder address
     * without the {@code folder:<site>/} prefix, because a template is already inside one
     * site and repeating it would let a layout read another). Omit it for the folder the
     * page being rendered lives in. {@code {{else}}} renders when nothing matched, which
     * is what stops "no articles yet" from being an empty {@code <div>}.</p>
     */
    Object query(Object context, Options options) throws IOException {
        String siteId = string(options.context.get(SITE_ID));
        if (siteId == null) {
            return options.inverse();
        }
        String folderChain = hash(options, "folder", null);
        String type = hash(options, "type", null);
        int limit = limit(options);

        Optional<ShFolder> folder = folderChain == null
                ? currentFolder(options)
                : folderByChain(siteId, folderChain);
        if (folder.isEmpty()) {
            return options.inverse();
        }

        List<Map<String, Object>> rows = rowsOf(folder.get(), type, limit, state(options));
        if (rows.isEmpty()) {
            return options.inverse();
        }
        StringBuilder out = new StringBuilder();
        for (Map<String, Object> row : rows) {
            out.append(options.fn(row));
        }
        return out;
    }

    /**
     * {@code {{#navigation}}…{{/navigation}}} — the site's top-level folders, in the
     * position order the console shows, each row {@code {name, url, id}}.
     *
     * <p>{@code folder="/docs"} narrows it to that folder's children, which is FSP's
     * {@code navigationFolder} without a second helper name to remember.</p>
     */
    Object navigation(Object context, Options options) throws IOException {
        String siteId = string(options.context.get(SITE_ID));
        if (siteId == null) {
            return options.inverse();
        }
        String folderChain = hash(options, "folder", null);
        Optional<ShFolder> parent = folderChain == null
                ? rootFolder(siteId)
                : folderByChain(siteId, folderChain);
        if (parent.isEmpty()) {
            return options.inverse();
        }
        List<ShFolder> children =
                new ArrayList<>(shFolderRepository.findByParentFolderOrderByPositionAsc(parent.get()));
        if (children.isEmpty()) {
            return options.inverse();
        }
        StringBuilder out = new StringBuilder();
        for (ShFolder child : children) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", child.getId());
            row.put("name", child.getName());
            row.put("title", child.getName());
            out.append(options.fn(ShStableMap.copyOf(row)));
        }
        return out;
    }

    /**
     * {@code {{file_url FIELD}}} — a {@code File} field's post id as the public byte path
     * SH38 serves it on. The 2018 navigation region registered a {@code file_url} helper
     * of its own in JavaScript for exactly this; it is one line of Java and no engine.
     *
     * <p>An id that resolves to nothing renders as the empty string rather than as a
     * broken path: a {@code <img src="/file_source/undefined/…">} is a 404 the browser
     * reports and the author does not.</p>
     */
    Object fileUrl(Object context, Options options) {
        String id = string(context);
        if (id == null || id.isBlank()) {
            return "";
        }
        // SH279 — the same row the rest of the render reads. `findById` returns a row per state and
        // this took whichever came first, so a draft that renamed the file could produce the
        // published path or the draft one depending on the database. The bytes are immutable (SH145)
        // but `data.FILE` is the *name* in the path, so the two rows can disagree.
        return preferDraft(shPostUnifiedRepository.findById(id), state(options)).stream()
                .findFirst()
                .map(this::bytePath)
                .orElse("");
    }

    private String bytePath(ShPostUnified filePost) {
        Map<String, Object> fields = shRenderModel.fieldsOf(filePost);
        Object name = fields.get("FILE");
        return name == null ? "" : "/file_source/" + filePost.getId() + "/" + name;
    }

    // --- resolution -------------------------------------------------------------

    private Optional<ShFolder> rootFolder(String siteId) {
        ShSite site = shSiteRepository.findById(siteId).orElse(null);
        if (site == null) {
            return Optional.empty();
        }
        // SH279's second half found this one with its own lint: the first half added the
        // comparator *here*, inline, which left the codebase with one ordered reader and eight
        // unordered ones. `findRootFolder` is now the only place that picks, and it is where the
        // "one root by construction" reasoning lives — SH245's rule, that extracting a rule means
        // asking which callers should use it and not only which copies to delete.
        return shFolderRepository.findRootFolder(site);
    }

    /**
     * Resolves a name chain through {@link ShAddressResolver} rather than walking it here
     * — P10, and it means a template's idea of where {@code /docs/guides} is cannot drift
     * from the agent surface's.
     */
    private Optional<ShFolder> folderByChain(String siteId, String chain) {
        ShSite site = shSiteRepository.findById(siteId).orElse(null);
        if (site == null) {
            return Optional.empty();
        }
        return shAddressResolver.find(ShAddress.parse("folder:" + site.getName() + "/" + trim(chain)))
                .flatMap(resolved -> shFolderRepository.findById(resolved.id()));
    }

    private Optional<ShFolder> currentFolder(Options options) {
        String postId = string(options.context.get("post.id"));
        if (postId == null) {
            return Optional.empty();
        }
        // SH279 — one row, chosen the same way everything else chooses. SH134 moves both rows of a
        // post together so they agree about the folder today; that is a property of the move verb
        // rather than of this read, and relying on it silently is what the sweep is about.
        return preferDraft(shPostUnifiedRepository.findById(postId), state(options)).stream()
                .findFirst()
                .map(ShPostUnified::getShFolder);
    }

    private List<Map<String, Object>> rowsOf(ShFolder folder, String type, int limit, ShPostState state) {
        return preferDraft(
                shPostUnifiedRepository.findByShFolderAndStateIn(folder, statesFor(state)), state)
                .stream()
                .filter(post -> type == null || type.equals(typeNameOf(post)))
                .sorted(byDateThenId())
                .limit(limit)
                .map(this::row)
                .toList();
    }

    /**
     * The states a listing reads, given the state the page is being rendered in.
     *
     * <p>A DRAFT render reads both and prefers the draft; a PUBLISHED render reads only
     * published rows. Asking for both and filtering afterwards was the old shape and it is one
     * predicate the database can apply itself.</p>
     */
    private List<ShPostState> statesFor(ShPostState state) {
        // SH278 — delegated, not duplicated. This method and `preferDraft` were extracted here by
        // SH247 and then not applied to the thing that resolves the *layout*, which is how a preview
        // could render the published template while the curator edited the draft. One writer now
        // (SH245's rule), in `ShRenderState`.
        return ShRenderState.statesFor(state);
    }

    /**
     * One row per post id, the DRAFT winning — the same rule {@code /agent/read} and the console
     * listing use: a preview has to show the edit that has not been published yet, or it is not a
     * preview.
     *
     * <p>Shared by all three listing helpers since SH247. It was inline in {@code rowsOf}, and a
     * second copy in {@code search} would have been a second chance to get the preference
     * backwards — the failure mode being a preview that shows the live page while the curator is
     * looking for their unpublished change.</p>
     */
    private List<ShPostUnified> preferDraft(List<ShPostUnified> posts, ShPostState state) {
        // SH278 — and it gained a total order in the move: the fold used to depend on whatever order
        // the database returned, which was invisible here because a listing is sorted afterwards
        // anyway, and very visible in `ShPageRenderService`, where `findFirst` was the answer.
        return ShRenderState.preferDraft(posts, state);
    }

    /**
     * Newest first, with an id tiebreak — a <b>total</b> order.
     *
     * <p>Date alone ties for two posts written in the same millisecond, and a listing that
     * reorders itself between renders moves SH101's digest for a change nobody made. Shared, so
     * every helper that yields a listing is stable for the same reason rather than by luck.</p>
     */
    private Comparator<ShPostUnified> byDateThenId() {
        return Comparator
                .comparing((ShPostUnified p) -> p.getDate(),
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ShPostUnified::getId);
    }

    private String typeNameOf(ShPostUnified post) {
        return post.getShPostType() == null ? null : post.getShPostType().getName();
    }

    /** A row is the post's fields plus the four things a listing always writes. */
    private Map<String, Object> row(ShPostUnified post) {
        Map<String, Object> row = new LinkedHashMap<>(shRenderModel.fieldsOf(post));
        row.put("id", post.getId());
        row.put("title", post.getTitle());
        row.put("summary", post.getSummary());
        row.put("url", post.getFurl());
        // `link` is the site-relative address a delivered page would use. It is the post's
        // friendly URL and not a preview path on purpose: the markup a template produces
        // has to be the markup the front end serves, or the preview is proving the wrong
        // page. The preview route rewrites nothing.
        row.put("link", post.getFurl());
        row.put("type", typeNameOf(post));
        return ShStableMap.copyOf(row);
    }

    // --- option reading ---------------------------------------------------------

    private ShPostState state(Options options) {
        return "PUBLISHED".equals(string(options.context.get("render.state")))
                ? ShPostState.PUBLISHED
                : ShPostState.DRAFT;
    }

    private int limit(Options options) {
        Object raw = options.hash("limit");
        if (raw instanceof Number number) {
            return Math.clamp(number.intValue(), 1, MAX_LIMIT);
        }
        if (raw != null) {
            try {
                return Math.clamp(Integer.parseInt(raw.toString().trim()), 1, MAX_LIMIT);
            } catch (NumberFormatException e) {
                return DEFAULT_LIMIT;
            }
        }
        return DEFAULT_LIMIT;
    }

    private String hash(Options options, String key, String fallback) {
        String value = string(options.hash(key));
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String string(Object value) {
        return value == null ? null : value.toString();
    }

    private String trim(String chain) {
        String trimmed = chain.trim();
        return trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
    }
}
