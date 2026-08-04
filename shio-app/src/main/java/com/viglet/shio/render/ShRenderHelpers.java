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
import java.util.HashSet;
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
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
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
 * used. SH247 added {@code getRelation}, {@code search} and finally {@code form}, so
 * <b>all seven exist</b> and {@code ShTemplateValidator}'s known-missing set is empty.</p>
 *
 * <p>{@code form} was filed rather than built for a block, on the argument that a form needs a
 * decision about <em>where a submission goes</em> — a subsystem (storage, spam, notification,
 * GDPR) rather than a helper. <b>The artefact contradicted it.</b> FSP's {@code ShFormComponent}
 * is two files and stores nothing; its {@code ShFormConfiguration} carries {@code method},
 * {@code pageLayout}, {@code createPost} and {@code folder}, so 2018 had answered the question
 * and its answer was that a submission becomes a post in a named folder. What ships here is the
 * rendering half only — see {@link #form} for why the receiving half is a security decision and
 * not a rendering one.</p>
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
            Set.of("id", "title", "summary", "url", "link", "type", "name", "locale");

    /**
     * What a {@code {{#form}}} row carries (SH247) — a <b>field</b>, not a post.
     *
     * <p>Kept apart from {@link #ROW_KEYS} rather than merged into it, and that is the whole
     * design decision this helper makes. The five listing helpers are interchangeable because
     * they yield the same row; a form yields something categorically different — a post-type's
     * *declaration* rather than a post — and pretending otherwise would buy one shared set at the
     * cost of the property the shared set exists for, since a card body written for
     * {@code {{#query}}} is meaningless over a field. Two shapes, each honest, and SH251's lint asks
     * {@link #rowKeysFor} which one applies rather than warning on a correct template.</p>
     */
    public static final Set<String> FORM_FIELD_KEYS =
            Set.of("name", "label", "widget", "input", "required", "ordinal");

    /**
     * The row keys a block of this helper yields — the question SH251's lint has to ask.
     *
     * <p>Declared here for {@link #ROW_KEYS}' own reason, and it exists because the second row
     * shape arrived without it: {@code FORM_FIELD_KEYS} was declared, documented as "the lint
     * reads both", and read by nothing, so {@code {{label}}} in a correct {@code {{#form}}} body
     * was reported as an undeclared field. Exactly the failure the row-keys comment above warns
     * about — a restatement of the capability set whose drift shows up as a warning on a correct
     * template — committed while adding the set that was meant to prevent it.</p>
     */
    public static Set<String> rowKeysFor(String scope) {
        return "form".equals(scope) ? FORM_FIELD_KEYS : ROW_KEYS;
    }

    @PostConstruct
    void register() {
        // The shape is declared with the helper (SH260): the engine is the one place that knows
        // the capability set, and everything that reads it — the unknown-helper refusal, the SH251
        // field scanner — asks rather than restating.
        // The signature travels with the registration for the same reason (SH272): a hash
        // parameter's name exists only inside the helper's body, so nothing downstream could
        // derive it, and the context pack now answers "what may a template call" from here.
        engine.register("query", ShHelperShape.BLOCK,
                "{{#query folder= type= limit= page= sort= field= value=}}…{{/query}}", this::query);
        engine.register("navigation", ShHelperShape.BLOCK,
                "{{#navigation folder=}}…{{/navigation}}", this::navigation);
        engine.register("search", ShHelperShape.BLOCK,
                "{{#search q= type= limit=}}…{{/search}}", this::search);
        engine.register("getRelation", ShHelperShape.BLOCK_PATH_ARG,
                "{{#getRelation FIELD}}…{{/getRelation}}", this::getRelation);
        engine.register("file_url", ShHelperShape.INLINE_PATH_ARG,
                "{{file_url FIELD}}", this::fileUrl);
        engine.register("form", ShHelperShape.BLOCK,
                "{{#form type=}}…{{/form}}", this::form);
        engine.register("menu", ShHelperShape.BLOCK,
                "{{#menu title=}}…{{/menu}}", this::menu);
        engine.register("translations", ShHelperShape.BLOCK,
                "{{#translations}}…{{/translations}}", this::translations);
    }

    /**
     * {@code {{#translations}}…{{/translations}}} — this page's siblings in the other languages (SH383).
     *
     * <h3>The gap it closes</h3>
     *
     * <p>SH348 gave a file {@code locale:} and {@code translationOf:} and stopped at the projection.
     * Nothing put the siblings in template scope, so a six-language site could be <b>stored</b> and
     * could not render the one control every page of it carries — the switcher — or the {@code
     * hreflang} pair that tells a search engine the same thing. Both come from these rows.
     *
     * <h3>It takes no arguments, which is the whole shape</h3>
     *
     * <p>The subject is always the page being rendered: a translation set is a property of <i>this</i>
     * post, not something a template selects. So there is no hash to get wrong, and it is the only
     * block helper here with an empty signature — {@code {{#navigation}}} defaults its {@code folder=}
     * and could still be pointed elsewhere, and that difference is real rather than an omission.
     *
     * <h3>The group is SH348's, and the anchor rule is what makes it a set</h3>
     *
     * <p>Siblings share {@code translationGroupId} (SH53's column, which SH348's {@code translationOf}
     * resolves into). Every translation names the group's <b>anchor</b> and the anchor names nothing,
     * so the group is a forest rather than a chain — which means "the siblings" is one equality test
     * and not a walk. A post whose group is blank, or which is alone in its group, yields
     * {@code {{else}}}: a monolingual page is not a defect and must not render an empty switcher.
     *
     * <h3>Same site only, like every reference here</h3>
     *
     * <p>{@code getRelation}'s rule, for its reason: a relation that reached another site's content
     * would make a shared region's output depend on which page slotted it. That does bound what this
     * supports — a locale-per-<i>site</i> layout gets nothing — and the bound is the design's own:
     * SH323 makes a friendly URL site-unique, so {@code /en/about} and {@code /pt/sobre} are ordinary
     * distinct URLs in <b>one</b> site, which is why SH383 adds no locale-prefixed route. A second
     * addressing scheme beside SH79 for no new capability is what that would be.
     *
     * <h3>Ordered by locale, not by date</h3>
     *
     * <p>Every other listing here sorts newest-first, and for a switcher that is wrong twice: the order
     * would change when somebody edited one translation, and a language picker whose items move is a
     * control people misclick. Alphabetical by locale tag is stable, and it is the order a reader can
     * predict.
     *
     * <h3>The current page is not in its own list</h3>
     *
     * <p>Which is the decision a caller would otherwise have to undo with {@code {{#unless}}} on every
     * template. A switcher marking the current language is doing something else — comparing
     * {@code post.locale} — and that is available without spending a row on it.
     */
    Object translations(Object context, Options options) throws IOException {
        String siteId = string(options.context.get(SITE_ID));
        String self = string(options.context.get("post.id"));
        if (siteId == null || self == null) {
            return options.inverse();
        }
        ShPostState state = state(options);
        List<ShPostUnified> rows = rowsOf(siteId, statesFor(state));
        // The group is read off the row rather than from the model, and deliberately: putting
        // `translationGroup` on the `post` frame would publish a **UUID** to every template for a
        // value no template can use — SH79's rule is that an address a human writes is never a UUID,
        // and the model is what a human writes against. `rowsOf` is the SH353 per-transaction read
        // that is already in hand, so finding self in it costs nothing.
        String group = rows.stream()
                .filter(post -> self.equals(post.getId()))
                .map(ShPostUnified::getTranslationGroupId)
                .filter(each -> each != null && !each.isBlank())
                .findFirst()
                .orElse(null);
        if (group == null) {
            return options.inverse();
        }
        List<Map<String, Object>> siblings = preferDraft(
                rows.stream().filter(post -> group.equals(post.getTranslationGroupId())).toList(),
                state).stream()
                // Excluded after the draft/published fold, not before: publishing keeps the draft's
                // id (SH321), so the pair is one id either way — but folding first is what makes the
                // *sibling* rows prefer the draft, which is the whole point of the shared writer.
                .filter(post -> !self.equals(post.getId()))
                .sorted(Comparator.comparing(ShRenderModel::locale)
                        .thenComparing(ShPostUnified::getId))
                .map(this::row)
                .toList();
        if (siblings.isEmpty()) {
            return options.inverse();
        }
        StringBuilder out = new StringBuilder();
        for (Map<String, Object> row : siblings) {
            out.append(options.fn(row));
        }
        return out;
    }

    /**
     * {@code {{#menu title="Primary"}}…{{/menu}}} — a curated menu (SH382).
     *
     * <h3>Why this exists when {@code {{#navigation}}} already walks folders</h3>
     *
     * <p>SH381 gave a folder row its {@code url}, and the folder tree is still the wrong source for a
     * menu. A real site's navigation is <b>curated</b>: a label that differs from the page title,
     * groups that are not folders, an ordering that is editorial, entries that leave the site, a page
     * deliberately left out, and one menu per locale on a translated tree. A folder chain expresses
     * none of those, so every site hand-wrote its nav into every layout — which is the thing this
     * whole file exists to stop.</p>
     *
     * <h3>It knows no post-type name, and that is a boundary rather than a shortcut</h3>
     *
     * <p>The {@code Menu} and {@code MenuItem} post-types live in {@code @viglet/shio-sections}: they
     * are ordinary <b>content</b>, so {@code post.upsert}, the file projection and SH154's
     * {@code dangling-reference} all work on them with no new mechanism, which is SH249's test for
     * whether something belongs in the render model or beside it. This helper must therefore not
     * hardcode their names — the server knowing a JS package's vocabulary by string would make the
     * renderer opinionated about a convention instead of a model. So the contract is <b>structural</b>:
     * a menu is a post whose {@code items} field lists addresses, and anybody's post-type qualifies.</p>
     *
     * <h3>Two candidates yield nothing, never a guess</h3>
     *
     * <p>A title is not unique the way a friendly URL is, so two posts can answer. SH381 settled this
     * exact shape for a folder's {@code url}: a fabricated answer wearing a deterministic hat is worse
     * than none, because none is visibly unfinished. {@code {{else}}} renders, and the template author
     * sees an empty menu rather than somebody else's.</p>
     *
     * <h3>A nested menu is a row with a name and no url — SH381's convention, reused</h3>
     *
     * <p>Which is why nesting needs no new row key and no new helper. An item that is itself a menu
     * yields {@code name} with no {@code url}, and SH381 already established that as a <b>group
     * heading</b>: a template branches on {@code {{#if url}}} and recurses with
     * {@code {{#menu title=title}}}. One shape, one convention, arbitrary depth.</p>
     *
     * <h3>A cycle stops at the repeat, and it was never able to hang</h3>
     *
     * <p>The obvious claim here is that a menu naming itself would loop until the request died, and it
     * is <b>false</b> — worth writing down, because it is what the first draft of this comment said.
     * {@link ShTemplateEngine} refuses partials outright, so a template cannot include itself and
     * every {@code {{#menu}}} nesting is expanded from finite template text. Recursion is bounded by
     * the layout's own nesting depth whatever the content says.</p>
     *
     * <p>What the guard actually buys is therefore smaller and still worth having. Without it, a
     * layout nesting the helper three deep over a two-menu cycle renders <i>the cycle</i> —
     * {@code Loop → Loop Inner → Loop} — which is wrong output that looks like a real menu, and pays a
     * site scan per level for it. With it, the second entry into a menu already open renders
     * {@code {{else}}}: the author sees an empty branch, which is what they meant and is visibly
     * unfinished. The set of menus <b>open on this thread</b> rather than a depth cap, because a cap
     * has to pick a number that both permits a short cycle to repeat and refuses a legitimate deep
     * menu. Reporting it is {@code shio verify}'s job, per this file's standing rule: mark what only
     * the renderer can see, leave to the lint what the lint reports.</p>
     */
    Object menu(Object context, Options options) throws IOException {
        String siteId = string(options.context.get(SITE_ID));
        String title = hash(options, "title", null);
        if (siteId == null || title == null) {
            return options.inverse();
        }
        ShPostState state = state(options);
        Optional<ShPostUnified> found = menuByTitle(siteId, title, state);
        if (found.isEmpty()) {
            return options.inverse();
        }
        ShPostUnified menu = found.get();
        if (!OPEN_MENUS.get().add(menu.getId())) {
            return options.inverse();
        }
        try {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (String reference : referencesOf(shRenderModel.fieldsOf(menu).get(ITEMS))) {
                resolveReference(siteId, reference, state)
                        .map(post -> menuRow(siteId, post, state))
                        .ifPresent(rows::add);
            }
            if (rows.isEmpty()) {
                return options.inverse();
            }
            StringBuilder out = new StringBuilder();
            // No default ceiling, unlike every other helper here: a listing's length is the site's and
            // grows on its own, while a menu's length is *authored*. Cutting it at 50 would silently
            // drop the last entries of a menu somebody wrote by hand, which is the one place a
            // truncation is certain to be wrong. `MAX_LIMIT` is still the backstop.
            for (Map<String, Object> row : rows.subList(0, Math.min(rows.size(), MAX_LIMIT))) {
                out.append(options.fn(row));
            }
            return out;
        } finally {
            Set<String> open = OPEN_MENUS.get();
            open.remove(menu.getId());
            // And the ThreadLocal itself goes when the outermost menu closes. Removing only the id
            // would leave an empty set attached to every request thread that ever rendered a menu —
            // harmless in size and exactly the shape a real leak has, which is why the rule exists.
            if (open.isEmpty()) {
                OPEN_MENUS.remove();
            }
        }
    }

    /** The field a menu's entries live in — the one name this helper does depend on. */
    private static final String ITEMS = "items";

    /**
     * The menus open on this thread, which is what makes a content-authored cycle terminate.
     *
     * <p>A {@code ThreadLocal} rather than something threaded through the context, and the reason is
     * that it is correct here for a specific fact rather than by convenience: a Handlebars render is
     * synchronous on the calling thread, so "open on this thread" is exactly "open in this render".
     * The {@code finally} above is what keeps it from leaking into the next request on a pooled
     * thread.</p>
     */
    private static final ThreadLocal<Set<String>> OPEN_MENUS = ThreadLocal.withInitial(HashSet::new);

    /**
     * The one post in this site whose title is this, and which looks like a menu.
     *
     * <p>Case-insensitive, following {@code {{#navigation}}}'s index-post match for SH79's reason: a
     * title is a label and matching two labels byte-for-byte makes the field almost never resolve.
     * "Looks like a menu" is the structural test above — it has a non-empty {@code items} — so a page
     * that happens to share the title is not a candidate and the helper stays free of a type name.</p>
     */
    private Optional<ShPostUnified> menuByTitle(String siteId, String title, ShPostState state) {
        List<ShPostUnified> candidates = preferDraft(
                rowsOf(siteId, statesFor(state)).stream()
                        .filter(post -> title.equalsIgnoreCase(
                                post.getTitle() == null ? null : post.getTitle().trim()))
                        .filter(post -> !referencesOf(shRenderModel.fieldsOf(post).get(ITEMS)).isEmpty())
                        .toList(),
                state);
        return candidates.size() == 1 ? Optional.of(candidates.getFirst()) : Optional.empty();
    }

    /**
     * One entry: the base row, with the label and the link the menu vocabulary lets an author override.
     *
     * <h3>The link, in the order the overrides are meant to win</h3>
     *
     * <p>An explicit {@code target} first — that is the entry that points at a page and says so. Then
     * {@code href}, the entry that leaves the site. Then the post's <b>own</b> friendly URL, which is
     * the cheap case: a page listed directly in a menu needs no wrapper post at all. And last,
     * nothing — a post that is itself a menu is a group heading, and an absent {@code url} beside a
     * present {@code name} is SH381's legitimate row rather than a defect.</p>
     *
     * <p>A {@code target} that resolves to nothing yields no url rather than falling through to the
     * item's own address: the fallback would send a visitor to the wrapper post, which is a page with
     * a label and no content. SH154's {@code dangling-reference} is what reports it, and this is the
     * "leave to the lint what the lint reports" half of the same rule.</p>
     */
    private Map<String, Object> menuRow(String siteId, ShPostUnified post, ShPostState state) {
        Map<String, Object> fields = shRenderModel.fieldsOf(post);
        Map<String, Object> row = new LinkedHashMap<>(row(post));
        String label = string(fields.get("label"));
        row.put("name", label == null || label.isBlank() ? post.getTitle() : label.trim());

        List<String> target = referencesOf(fields.get("target"));
        String href = string(fields.get("href"));
        String url;
        if (!target.isEmpty()) {
            url = resolveReference(siteId, target.getFirst(), state)
                    .map(ShPostUnified::getFurl)
                    .orElse(null);
        } else if (href != null && !href.isBlank()) {
            url = href.trim();
        } else if (referencesOf(fields.get(ITEMS)).isEmpty()) {
            url = post.getFurl();
        } else {
            url = null;
        }
        row.put("url", url);
        row.put("link", url);
        return ShStableMap.copyOf(row);
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
                rowsOf(siteId, states).stream()
                        .filter(post -> url.equals(normalizedFurl(post)))
                        .toList(),
                state).stream()
                .findFirst();
    }

    /**
     * SH353 — the site's rows in these states, read once per read-only transaction.
     *
     * <h3>What it costs when it is not shared</h3>
     *
     * <p>SH312 measured it: five sections stored as friendly URLs cost <b>10</b> queries a page
     * against 5 for the same five stored as ids — two an entry, because the {@code findById} above
     * misses and this read runs after it. Every entry read the whole site to filter one {@code furl}
     * in memory, and a page composed of sections is the page most likely to be built that way (a
     * {@code Content Select} holds an id, an SH95 {@code Multi Select} holds URLs). It is now one
     * read per transaction: 15 queries a page became 11.</p>
     *
     * <h3>Why caching this is allowed where caching a listing's rows is not</h3>
     *
     * <p>SH312 deferred caching helper results and that still stands — a {@code {{#query}}} listing's
     * rows <em>are</em> the answer, so holding them is a freshness decision about content. This is
     * not that. "Which post does this URL name" is a <b>resolution</b>, the same kind of question
     * SH311 caches for templates, and the lifetime does the rest:
     * {@link ShReadOnlyTransactionCache} is read-only-transaction scoped, so nothing can be stale
     * longer than the call that asked, and a writing transaction gets no cache at all. The filter
     * still runs per entry against the rows, so an entry that resolves to nothing still resolves to
     * nothing.</p>
     */
    private List<ShPostUnified> rowsOf(String siteId, List<ShPostState> states) {
        return SITE_ROWS.get(siteId + "|" + states,
                () -> List.copyOf(
                        shPostUnifiedRepository.findByShSite_IdAndStateIn(siteId, states)));
    }

    /** SH353's population: one site's rows in one state set, per read-only transaction. */
    private static final ShReadOnlyTransactionCache SITE_ROWS = new ShReadOnlyTransactionCache();

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

        // SH384 — the whole matching set first, then the window. That order is what the measurement
        // licensed: the folder read is already whole-folder (213 rows loaded to render 10, measured by
        // ShRenderListingPageBenchmarkTest before any of this was written), so filtering, sorting and
        // windowing are list operations over rows already in hand. They cost no read, and the total is
        // exact for free — which is why the pager below reports a real count rather than "has more".
        // Every hash key is read **here**, in the helper's own body, and passed down. SH291's lint
        // follows exactly one call from a registered helper, so a key read two levels away is not
        // wrong code — it is code the lint cannot attribute to a signature, which is how a signature
        // silently goes stale. Reading them together also puts the whole call shape in one place.
        String sort = hash(options, "sort", "newest");
        String field = hash(options, "field", null);
        String value = hash(options, "value", null);
        List<ShPostUnified> matching = matching(folder.get(), type, field, value, sort, options);
        int page = page(options);
        int pages = Math.max((matching.size() + limit - 1) / limit, 1);
        List<Map<String, Object>> rows = matching.stream()
                .skip((long) (page - 1) * limit)
                .limit(limit)
                .map(this::row)
                .toList();

        // The pager's facts, as Handlebars data variables. They are `@`-prefixed rather than row keys
        // for two reasons: a row is one post and these are properties of the *listing*, so putting
        // them on every row would repeat them per item and imply they varied; and SH251's scanner
        // skips `@` tokens by construction, so no field lint has to learn about them. Published before
        // `fn` so a template can read them inside the block, which is the only scope Handlebars gives a
        // block helper — there is no way to write into the enclosing one, and inventing a mechanism
        // for it would be a second one to learn.
        options.data("page", page);
        options.data("pages", pages);
        options.data("total", matching.size());
        options.data("hasPrev", page > 1);
        options.data("hasNext", page < pages);
        options.data("prevPage", Math.max(page - 1, 1));
        options.data("nextPage", Math.min(page + 1, pages));

        if (rows.isEmpty()) {
            // Covers "the folder is empty" and "page 7 of 3" with one branch, and both mean the same
            // thing to a template: there is nothing to draw here.
            return options.inverse();
        }
        StringBuilder out = new StringBuilder();
        for (int index = 0; index < rows.size(); index++) {
            // `@index`, `@first` and `@last` — Handlebars' own `{{#each}}` vocabulary rather than a
            // third spelling of it, so nothing new has to be learned. They are what makes a pager
            // drawn **once** expressible: the block body repeats per row, so
            // `{{#if @last}}<nav>page {{@page}} of {{@pages}}</nav>{{/if}}` is the only way to emit it
            // a single time, and without these the pager repeated per item. A test found that.
            options.data("index", index);
            options.data("first", index == 0);
            options.data("last", index == rows.size() - 1);
            out.append(options.fn(rows.get(index)));
        }
        return out;
    }

    /**
     * Every post the listing's predicates accept, in the order {@code sort=} asks for (SH384).
     *
     * <h3>The filter is a field and a value, not a {@code tag=} convention</h3>
     *
     * <p>{@code field="industry" value="finance"} rather than a blessed field name, because a blessed
     * one would be a convention the model does not declare — the same argument that keeps
     * {@code {{#menu}}} free of a post-type name. A value <b>matches an entry</b> and not just the whole
     * field: SH154's {@code references: post} Multi Select and an ordinary tag list are both stored as a
     * JSON array, so {@code value="finance"} has to accept {@code ["finance","energy"]} or the filter is
     * useless on exactly the field shape a hub page filters by. {@code referencesOf} is what normalizes
     * the two, so a single string and a list are one code path.
     *
     * <p>Case-insensitive, following the {@code {{#navigation}}} index-post match for SH79's reason: a
     * field value is a label, and byte equality between a label somebody typed and a label somebody
     * else typed makes the filter almost never fire.
     */
    private List<ShPostUnified> matching(ShFolder folder, String type, String field, String value,
                                        String sort, Options options) {
        return preferDraft(
                shPostUnifiedRepository.findByShFolderAndStateIn(folder, statesFor(state(options))),
                state(options))
                .stream()
                .filter(post -> type == null || type.equals(typeNameOf(post)))
                .filter(post -> field == null || value == null || carries(post, field, value))
                .sorted(order(sort))
                .toList();
    }

    /** Whether one of this field's values is this value, case-insensitively. */
    private boolean carries(ShPostUnified post, String field, String value) {
        return referencesOf(shRenderModel.fieldsOf(post).get(field)).stream()
                .anyMatch(entry -> entry.equalsIgnoreCase(value));
    }

    /**
     * The listing's order (SH384).
     *
     * <p>Three, and every one is <b>total</b> — the shared rule, because a listing that reorders itself
     * between renders moves SH101's digest for a change nobody made. {@code oldest} is the default
     * reversed, which reverses the id tiebreak too and is still total; a legitimate archive reads
     * forwards, which is the case this exists for. An unrecognised value falls back to the default
     * rather than rendering nothing: a typo in {@code sort=} should cost a reader the order they asked
     * for, not the listing.
     */
    private Comparator<ShPostUnified> order(String sort) {
        return switch (sort.toLowerCase(java.util.Locale.ROOT)) {
            case "oldest" -> byDateThenId().reversed();
            case "title" -> Comparator
                    .comparing((ShPostUnified post) -> post.getTitle() == null ? "" : post.getTitle(),
                            String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(ShPostUnified::getId);
            default -> byDateThenId();
        };
    }

    /**
     * Which page of the listing (SH384). One-based, because a pager's first page is page 1.
     *
     * <h3>Why there is no {@code offset=}</h3>
     *
     * <p>The design offered either. Two spellings of one window is one too many: they would have to
     * define a precedence for a caller who passes both, and a <b>silent</b> precedence is precisely
     * what SH382's {@code menu-item-ambiguous-link} exists to report rather than resolve. {@code page=}
     * with {@code limit=} expresses every window an offset could — "skip the featured post and show
     * three" is {@code limit=3 page=2}.
     *
     * <h3>Why it does not read {@code param.page} itself</h3>
     *
     * <p>SH259 put the query string in template scope and deliberately kept it out of helper internals:
     * {@code {{#search q=TOPIC}}} takes its term from the template, not from the request. So a paged
     * archive is {@code {{#query page=param.page}}} — the template declares where the number comes
     * from, which means the same helper serves a request-driven archive, a hardcoded second page and a
     * page number computed from the post's own field, with no branch here and no request in scope.
     */
    private int page(Options options) {
        Object raw = options.hash("page");
        if (raw instanceof Number number) {
            return Math.max(number.intValue(), 1);
        }
        if (raw == null) {
            return 1;
        }
        try {
            return Math.max(Integer.parseInt(raw.toString().trim()), 1);
        } catch (NumberFormatException e) {
            // A `?page=` a reader typed by hand, or a template naming a field that is empty. Page one
            // is the honest answer: refusing would render nothing for a request that is merely wrong
            // about the page, and this value comes off a URL somebody else controls.
            return 1;
        }
    }

    /**
     * {@code {{#navigation}}…{{/navigation}}} — the site's top-level folders, in the
     * position order the console shows, each row {@code {id, name, title, url, link}}.
     *
     * <p>{@code folder="/docs"} narrows it to that folder's children, which is FSP's
     * {@code navigationFolder} without a second helper name to remember.</p>
     *
     * <h2>The {@code url} this promised for two releases and did not carry (SH381)</h2>
     *
     * <p>This docstring said {@code {name, url, id}} while the body built {@code {id, name, title}}.
     * The one helper a site's primary menu exists for therefore rendered labels nobody could click,
     * and the comment is the tell that the field was intended and dropped — a fixture in
     * {@code ShTemplateFieldLintTest} had been writing {@code <a href="{{url}}">{{name}}</a>}
     * against it the whole time.
     *
     * <p>It was not a one-line fix because <b>a folder has no URL</b>. SH79 keeps the two namespaces
     * apart deliberately: only a post has a friendly URL, and a folder chain is a different kind of
     * name. So the row's {@code url} had to be a decision, and it is the folder's <b>index post</b>
     * — the post inside it whose friendly URL is the folder's own chain, which is what a reader means
     * by "the Solutions section". Absent where there is none, and <b>never a fabricated path</b>: a
     * menu whose links 404 is worse than one whose entries are plain text, because the second is
     * visibly unfinished and the first is not.
     *
     * <p><b>An absent {@code url} beside a present {@code name} is a legitimate row</b>, not a
     * defect — it is a group heading, which is exactly what a nested menu needs, so a template
     * branches on it: {@code {{#if url}}<a href="{{url}}">{{name}}</a>{{else}}<span>{{name}}</span>{{/if}}}.
     * Reporting a folder that has no index post is therefore <em>nobody's</em> job here; if it ever
     * becomes one it is {@code verify}'s, by the rule that a defect belongs in one report.
     *
     * <p><b>The match is case-insensitive</b>, and that follows from SH79 rather than being a
     * convenience: a folder name is a human label ("Solutions") and a friendly URL is a slug
     * ("/solutions"), so demanding byte equality between two namespaces that are deliberately
     * different would make the field almost never resolve. Two posts in one folder matching
     * case-insensitively is ambiguous, and an ambiguous row is emitted <b>without</b> a url —
     * guessing between them is the fabricated path under another name.
     *
     * <p>{@code link} carries the same value, following {@link #row}'s own precedent: a card body
     * written for {@code {{#query}}} then iterates a menu unchanged, which is the one-vocabulary
     * property this class's header claims.
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
        ShPostState state = state(options);
        StringBuilder out = new StringBuilder();
        for (ShFolder child : children) {
            String url = indexUrlOf(child, state);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", child.getId());
            row.put("name", child.getName());
            row.put("title", child.getName());
            row.put("url", url);
            row.put("link", url);
            out.append(options.fn(ShStableMap.copyOf(row)));
        }
        return out;
    }

    /**
     * The friendly URL of a folder's index post, or {@code null} when it has none (SH381).
     *
     * <p>The chain comes from {@link ShAddressResolver#addressOf(ShFolder)} rather than from a
     * second parent walk written here: that method already knows the one thing this gets wrong
     * silently — the owning site lives on the <em>root</em> folder, so a nested folder read directly
     * yields a site-less address (SH128).
     *
     * <p>Only the posts <em>in</em> the folder are considered. A post elsewhere whose URL happens to
     * equal this chain is not this section's landing page, and treating it as one would let a page in
     * an unrelated folder capture a menu entry.
     */
    private String indexUrlOf(ShFolder folder, ShPostState state) {
        String chain = normalizePath(shAddressResolver.addressOf(folder).path());
        if (chain == null) {
            return null;
        }
        List<ShPostUnified> matches = preferDraft(
                shPostUnifiedRepository.findByShFolderAndStateIn(folder, statesFor(state)), state)
                .stream()
                .filter(post -> chain.equalsIgnoreCase(normalizePath(post.getFurl())))
                .toList();
        // Exactly one, or none. Two posts whose URLs differ only in case is an ambiguity, and
        // choosing between them by sort order would be a fabricated path wearing a deterministic hat.
        return matches.size() == 1 ? matches.get(0).getFurl() : null;
    }

    /** A leading slash, no trailing one, so `/solutions/` and `solutions` compare as one path. */
    private String normalizePath(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        String withLeading = trimmed.startsWith("/") ? trimmed : "/" + trimmed;
        String withoutTrailing = withLeading.length() > 1 && withLeading.endsWith("/")
                ? withLeading.substring(0, withLeading.length() - 1)
                : withLeading;
        return "/".equals(withoutTrailing) ? null : withoutTrailing;
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

    /**
     * {@code {{#form type="Contact"}}…{{/form}}} — a post-type's fields, in ordinal order
     * (SH247). <b>The seventh capability, and the last.</b>
     *
     * <h3>What it renders, and what it deliberately does not</h3>
     *
     * <p>It yields one {@link #FORM_FIELD_KEYS} row per declared field and nothing else. The
     * {@code <form>} element, the {@code action}, the inputs and the submit button are the
     * <b>author's</b> markup. That is not a smaller version of the 2019 helper being cautious —
     * it is SH183's rule applied: HTML is content, and a renderer that emitted
     * {@code <input class="form-control">} from Java would be the one place in this engine where
     * markup is a Java decision, un-themeable and un-overridable by the content that owns every
     * other tag on the page.</p>
     *
     * <h3>Where the submission goes: nowhere, and that is the decision</h3>
     *
     * <p>This capability blocked for a block on "a form needs storage, spam handling,
     * notification and retention — a subsystem, not a helper". <b>The artefact says otherwise.</b>
     * FSP's {@code ShFormComponent} is two files and neither stores anything; its
     * {@code ShFormConfiguration} carries exactly four settings — {@code method},
     * {@code pageLayout}, {@code createPost} and {@code folder} — so 2018 <em>had</em> answered
     * the question and its answer was that a submission becomes a <b>post in a named folder</b>.
     * No subsystem, because the CMS already is one.</p>
     *
     * <p>Reproducing that half means a public, unauthenticated endpoint that writes content, which
     * is a security decision rather than a rendering one and is deliberately not taken here: the
     * renderer's declared scope is <b>preview-grade</b> (production delivery is Next.js over the
     * CDA, P10), and a preview needs the form to <em>look</em> right, not to accept a stranger's
     * data. So Shio renders the fields and never receives a submission. The receiving half is a
     * task of its own, and it now has a design instead of an open question.</p>
     *
     * <h3>An unknown post-type renders {@code {{else}}}</h3>
     *
     * <p>Not a refusal, and for {@code getRelation}'s reason: an unknown type is already an SH99
     * finding with an address and a {@code fix}, so failing the page would put one defect in two
     * reports that can disagree. {@code {{else}}} means "there is no form here", which is what a
     * visitor should see and what an author will notice.</p>
     */
    Object form(Object context, Options options) throws IOException {
        String typeName = hash(options, "type", null);
        if (typeName == null || typeName.isBlank()) {
            return options.inverse();
        }
        ShPostType postType = shPostTypeRepository.findByName(typeName.trim());
        if (postType == null || postType.getShPostTypeAttrs() == null) {
            return options.inverse();
        }
        List<Map<String, Object>> fields = postType.getShPostTypeAttrs().stream()
                // Ordinal, with the name as a tiebreak: the set is unordered, and two fields
                // sharing an ordinal would otherwise swap between renders and move SH101's digest.
                .sorted(Comparator.comparingInt(ShPostTypeAttr::getOrdinal)
                        .thenComparing(ShPostTypeAttr::getName,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .map(ShRenderHelpers::formField)
                .toList();
        if (fields.isEmpty()) {
            return options.inverse();
        }
        StringBuilder out = new StringBuilder();
        for (Map<String, Object> field : fields) {
            out.append(options.fn(field));
        }
        return out;
    }

    /** One declared field, as a template sees it. */
    private static Map<String, Object> formField(ShPostTypeAttr attr) {
        String widget = attr.getShWidget() == null ? null : attr.getShWidget().getName();
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("name", attr.getName());
        field.put("label", attr.getLabel());
        field.put("widget", widget);
        // The widget is Shio's vocabulary; `input` is the browser's. Both are given because a
        // template that branched on the widget name would hard-code this catalogue, and one that
        // only had `input` could not tell a Date from a Text it happens to render the same way.
        field.put("input", inputType(widget));
        // `required` is a byte in the model, not a boolean; a template branches on truth.
        field.put("required", attr.getRequired() == 1);
        field.put("ordinal", attr.getOrdinal());
        return ShStableMap.copyOf(field);
    }

    /**
     * The HTML input a widget wants, or {@code text} when nothing better is known.
     *
     * <p>Deliberately a small, total mapping rather than a registry: an unknown widget is a
     * {@code text} input, which is wrong in a way an author can see and fix in their own markup,
     * where a refusal would make adding a widget a breaking change for every form template.</p>
     */
    private static String inputType(String widget) {
        if (widget == null) {
            return "text";
        }
        return switch (widget) {
            case "Text Area", "Ace Editor - HTML", "Ace Editor - Javascript", "Ace Editor - CSS",
                    "HTML Editor" -> "textarea";
            case "Combo Box", "Multi Select", "Content Select", "Relator" -> "select";
            case "Check Box" -> "checkbox";
            case "Date" -> "date";
            case "File" -> "file";
            default -> "text";
        };
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
                .comparing(ShPostUnified::getDate,
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
        // SH383 — on **every** row, not only a translation's. It is a property of every post, it is
        // already loaded, and a multilingual site wants it on a listing too: a card that renders
        // `lang` on a quoted excerpt is the ordinary case, and so is a `{{#query}}` that has to skip
        // rows in the wrong language. Adding it to `{{#translations}}` alone would have been a second
        // row shape for one field, which is the trade `FORM_FIELD_KEYS` is documented as *worth* and
        // this one plainly is not.
        row.put("locale", ShRenderModel.locale(post));
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
