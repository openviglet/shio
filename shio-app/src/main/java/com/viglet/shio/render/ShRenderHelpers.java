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

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Options;
import com.viglet.shio.api.acl.ShContentAclService;
import com.viglet.shio.api.agent.ShAddress;
import com.viglet.shio.api.agent.ShAddressResolver;
import com.viglet.shio.api.staticfile.transform.ShImageTransformSigner;
import com.viglet.shio.api.staticfile.transform.ShImageTransformSpec;
import com.viglet.shio.property.ShImageTransformProperties;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import org.springframework.data.domain.PageRequest;

import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository.ShListingKey;
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
    private final ShImageTransformSigner imageTransformSigner;
    private final ShImageTransformProperties imageTransformProperties;
    /** SH1298 — who may read a members-only page, asked only on the published render. */
    private final ShContentAclService shContentAclService;

    public ShRenderHelpers(ShTemplateEngine engine,
                           ShAddressResolver shAddressResolver,
                           ShFolderRepository shFolderRepository,
                           ShPostUnifiedRepository shPostUnifiedRepository,
                           ShPostTypeRepository shPostTypeRepository,
                           ShSiteRepository shSiteRepository,
                           ShRenderModel shRenderModel,
                           ShImageTransformSigner imageTransformSigner,
                           ShImageTransformProperties imageTransformProperties,
                           ShContentAclService shContentAclService) {
        this.shContentAclService = shContentAclService;
        this.engine = engine;
        this.shAddressResolver = shAddressResolver;
        this.shFolderRepository = shFolderRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shPostTypeRepository = shPostTypeRepository;
        this.shSiteRepository = shSiteRepository;
        this.shRenderModel = shRenderModel;
        this.imageTransformSigner = imageTransformSigner;
        this.imageTransformProperties = imageTransformProperties;
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
     * What a {@code {{#menu}}} row carries: a listing row, plus where it stands relative to the page
     * being rendered (SH1267), so a layout can write {@code aria-current="page"}.
     */
    public static final Set<String> MENU_KEYS = java.util.stream.Stream.concat(ROW_KEYS.stream(),
            java.util.stream.Stream.of("current", "ancestor")).collect(java.util.stream.Collectors.toUnmodifiableSet());

    /** What a {@code {{#translations}}} row carries: a listing row and its language's own name (SH1270). */
    public static final Set<String> TRANSLATION_KEYS = java.util.stream.Stream.concat(ROW_KEYS.stream(),
            java.util.stream.Stream.of("language")).collect(java.util.stream.Collectors.toUnmodifiableSet());

    /**
     * What an {@code {{#image}}} row carries (SH390) — an <b>asset</b>, not a post.
     *
     * <p>Its own set for {@link #FORM_FIELD_KEYS}' reason: a listing's rows are interchangeable
     * because they are all posts, and an image is a different kind of thing. Merging them would put
     * {@code srcset} on every {@code {{#query}}} row, which is a promise no listing keeps.</p>
     */
    public static final Set<String> IMAGE_KEYS =
            Set.of("url", "width", "height", "alt", "altAttr", "widths", "srcset", "sizes");

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
        return switch (scope == null ? "" : scope) {
            case "form" -> FORM_FIELD_KEYS;
            case "menu" -> MENU_KEYS;
            case "translations" -> TRANSLATION_KEYS;
            case "image" -> IMAGE_KEYS;
            default -> ROW_KEYS;
        };
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
                "{{#query folder= type= limit= page= sort= field= value= locale=}}…{{/query}}", this::query);
        engine.register("navigation", ShHelperShape.BLOCK,
                "{{#navigation folder=}}…{{/navigation}}", this::navigation);
        engine.register("search", ShHelperShape.BLOCK,
                "{{#search q= type= limit= locale=}}…{{/search}}", this::search);
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
        engine.register("image", ShHelperShape.BLOCK_PATH_ARG,
                "{{#image FIELD sizes= decorative=}}…{{/image}}", this::image);
        engine.register("t", ShHelperShape.INLINE_PATH_ARG,
                "{{t \"key\"}}", this::text);
    }

    /**
     * {@code {{t "skip"}}} — one line of a layout's own text, in the page's language (SH1271).
     *
     * <h3>What it closes</h3>
     *
     * <p>A layout serves every language a site publishes, and what it writes itself (a skip link,
     * the footer's lines, a navigation's {@code aria-label}) had one language, because a template
     * cannot vary by {@code post.locale}: Quilhara shipped no skip link rather than an English one
     * on its Portuguese pages. SH1271's first half gave menus the page's language through their
     * translation group; this is the same answer for the text a layout carries.</p>
     *
     * <h3>A table is a post, found by its shape, like a menu</h3>
     *
     * <p>A post titled {@code Strings} whose {@code strings} field holds {@code key = value} lines,
     * one per locale: content a curator edits in the console and an agent writes with
     * {@code post.upsert}, with no new mechanism. No post-type name is known here, for
     * {@link #menu}'s reason. The one in the page's locale wins; with none, the one in the site's
     * default locale; with neither, the only one there is. A key the table does not hold renders
     * the key itself, so a missing line is visible on the page rather than silently empty.</p>
     */
    Object text(Object context, Options options) {
        String key = string(context);
        if (key == null || key.isBlank()) {
            return "";
        }
        String siteId = string(options.context.get(SITE_ID));
        if (siteId == null) {
            return key;
        }
        String pageLocale = string(options.context.get("post.locale"));
        String defaultLocale = string(options.context.get("render.defaultLocale"));
        List<ShPostUnified> tables = preferDraft(rowsOf(siteId, statesFor(state(options))).stream()
                        .filter(post -> STRINGS_TITLE.equalsIgnoreCase(
                                post.getTitle() == null ? null : post.getTitle().trim()))
                        .filter(post -> string(shRenderModel.fieldsOf(post).get(STRINGS)) != null)
                        .toList(),
                state(options));
        Optional<ShPostUnified> table = tables.stream()
                .filter(post -> pageLocale != null && pageLocale.equalsIgnoreCase(ShRenderModel.locale(post)))
                .findFirst()
                .or(() -> tables.stream()
                        .filter(post -> defaultLocale != null
                                && defaultLocale.equalsIgnoreCase(ShRenderModel.locale(post)))
                        .findFirst())
                .or(() -> tables.size() == 1 ? Optional.of(tables.getFirst()) : Optional.empty());
        return table.map(post -> lines(string(shRenderModel.fieldsOf(post).get(STRINGS))).get(key.trim()))
                .orElse(null) instanceof String value ? value : key;
    }

    /** The title that makes a post a string table, and the field its lines live in (SH1271). */
    private static final String STRINGS_TITLE = "Strings";
    private static final String STRINGS = "strings";

    /** {@code key = value} lines; a blank line or one starting with {@code #} is not an entry. */
    private static Map<String, String> lines(String body) {
        Map<String, String> entries = new LinkedHashMap<>();
        if (body == null) {
            return entries;
        }
        for (String line : body.split("\\R")) {
            String trimmed = line.trim();
            int eq = trimmed.indexOf('=');
            if (trimmed.isEmpty() || trimmed.startsWith("#") || eq <= 0) {
                continue;
            }
            entries.putIfAbsent(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim());
        }
        return entries;
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
                .map(post -> translationRow(post, options.context.get("site.localeLabels")))
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
     * A listing row plus {@code language}, the sibling's language named in itself (SH1270), or the
     * site's own label for it when its locale policy sets one (SH1281): {@code pt-br} and
     * {@code pt-pt} are both "Português" in the table, and only the site can tell them apart.
     */
    private Map<String, Object> translationRow(ShPostUnified post, Object labels) {
        Map<String, Object> row = new LinkedHashMap<>(row(post));
        String locale = ShRenderModel.locale(post);
        Object label = labels instanceof Map<?, ?> named ? named.get(locale) : null;
        row.put("language", label instanceof String text && !text.isBlank()
                ? text : ShLanguageNames.of(locale));
        return ShStableMap.copyOf(row);
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
        String pageLocale = string(options.context.get("post.locale"));
        Optional<ShPostUnified> found = menuByTitle(siteId, title, state, pageLocale);
        if (found.isEmpty()) {
            return options.inverse();
        }
        ShPostUnified menu = inLocale(siteId, found.get(), pageLocale, state);
        if (!OPEN_MENUS.get().add(menu.getId())) {
            return options.inverse();
        }
        try {
            Here here = new Here(string(options.context.get("post.id")),
                    string(options.context.get("post.url")));
            List<Map<String, Object>> rows = new ArrayList<>();
            for (String reference : referencesOf(shRenderModel.fieldsOf(menu).get(ITEMS))) {
                resolveReference(siteId, reference, state)
                        .map(post -> menuRow(siteId, post, state, here))
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
    private Optional<ShPostUnified> menuByTitle(String siteId, String title, ShPostState state,
                                                String pageLocale) {
        List<ShPostUnified> candidates = preferDraft(
                rowsOf(siteId, statesFor(state)).stream()
                        .filter(post -> title.equalsIgnoreCase(
                                post.getTitle() == null ? null : post.getTitle().trim()))
                        .filter(post -> !referencesOf(shRenderModel.fieldsOf(post).get(ITEMS)).isEmpty())
                        .toList(),
                state);
        if (candidates.size() == 1) {
            return Optional.of(candidates.getFirst());
        }
        // SH1271 — translations of one menu may share its title. That is one menu in several
        // languages rather than two candidates, so the page's language picks; two menus that are
        // not translations of each other stay ambiguous, for SH381's reason.
        String group = candidates.isEmpty() ? null : candidates.getFirst().getTranslationGroupId();
        if (group == null || group.isBlank()
                || !candidates.stream().allMatch(post -> group.equals(post.getTranslationGroupId()))) {
            return Optional.empty();
        }
        return candidates.stream()
                .filter(post -> pageLocale != null && pageLocale.equalsIgnoreCase(ShRenderModel.locale(post)))
                .findFirst();
    }

    /**
     * SH1271 — the translation of this menu into the page's language, or the menu itself.
     *
     * <p>A layout serves every language a site publishes and names its menu once. Menus are posts,
     * so they already carry SH348's translation group, and a curator translates one the way they
     * translate a page; this is what lets {@code {{#menu title="Main"}}} render the Portuguese menu
     * on a Portuguese page without the layout knowing either title. No sibling in the page's
     * language yields the menu that was named, which is the monolingual case unchanged.</p>
     */
    private ShPostUnified inLocale(String siteId, ShPostUnified menu, String pageLocale,
                                   ShPostState state) {
        String group = menu.getTranslationGroupId();
        if (pageLocale == null || group == null || group.isBlank()
                || pageLocale.equalsIgnoreCase(ShRenderModel.locale(menu))) {
            return menu;
        }
        return preferDraft(rowsOf(siteId, statesFor(state)).stream()
                        .filter(post -> group.equals(post.getTranslationGroupId()))
                        .filter(post -> pageLocale.equalsIgnoreCase(ShRenderModel.locale(post)))
                        .filter(post -> !referencesOf(shRenderModel.fieldsOf(post).get(ITEMS)).isEmpty())
                        .toList(),
                state).stream()
                .findFirst()
                .orElse(menu);
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
    private Map<String, Object> menuRow(String siteId, ShPostUnified post, ShPostState state,
                                        Here here) {
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
        // SH1267 — whether this entry is the page being rendered, or leads to it.
        Optional<ShPostUnified> page = targetOf(siteId, post, state);
        boolean current = page.isPresent() && page.get().getId().equals(here.id());
        row.put("current", current);
        row.put("ancestor", !current && leadsTo(siteId, post, state, here, MENU_DEPTH));
        return ShStableMap.copyOf(row);
    }

    /**
     * The page being rendered, as a menu row compares against it (SH1267).
     *
     * @param id  the current post's id, or {@code null} outside a page render
     * @param url its friendly URL
     */
    private record Here(String id, String url) {

        /** Whether {@code url} is a path above this page: {@code /trails} above {@code /trails/x}. */
        boolean under(String url) {
            if (this.url == null || url == null || url.isBlank() || "/".equals(url.trim())) {
                return false;
            }
            String prefix = url.trim().endsWith("/") ? url.trim() : url.trim() + "/";
            return this.url.startsWith(prefix);
        }
    }

    /** How deep a group heading is searched for the current page: the depth a real menu has. */
    private static final int MENU_DEPTH = 4;

    /**
     * The page a menu entry points at: its {@code target}, or the entry itself when it is a page
     * listed directly. An {@code href} leaves the site and a group heading has no page, so neither
     * has one; the same order {@link #menuRow} resolves the link in.
     */
    private Optional<ShPostUnified> targetOf(String siteId, ShPostUnified post, ShPostState state) {
        Map<String, Object> fields = shRenderModel.fieldsOf(post);
        List<String> target = referencesOf(fields.get("target"));
        if (!target.isEmpty()) {
            return resolveReference(siteId, target.getFirst(), state);
        }
        String href = string(fields.get("href"));
        if ((href == null || href.isBlank()) && referencesOf(fields.get(ITEMS)).isEmpty()) {
            return Optional.of(post);
        }
        return Optional.empty();
    }

    /**
     * SH1267 — whether this entry leads to the page being rendered without being it.
     *
     * <p>Two ways, and the second is why it is not a URL comparison alone. An entry whose page sits
     * above this one in the URL ({@code Trails} while a trail renders), because that is where a
     * visitor came from and what a breadcrumb would name; never the home page, which is above
     * everything and would mark itself on every page. And a group heading (a nested menu, SH381's
     * row with no url) whose entries lead here, searched {@link #MENU_DEPTH} deep, since a cycle is
     * content and must not cost a render.</p>
     */
    private boolean leadsTo(String siteId, ShPostUnified post, ShPostState state, Here here,
                            int depth) {
        if (here.id() == null || depth <= 0) {
            return false;
        }
        Optional<ShPostUnified> page = targetOf(siteId, post, state);
        if (page.isPresent()) {
            return here.under(page.get().getFurl());
        }
        for (String reference : referencesOf(shRenderModel.fieldsOf(post).get(ITEMS))) {
            Optional<ShPostUnified> child = resolveReference(siteId, reference, state);
            if (child.isEmpty()) {
                continue;
            }
            Optional<ShPostUnified> childPage = targetOf(siteId, child.get(), state);
            if (childPage.isPresent() && here.id().equals(childPage.get().getId())
                    || leadsTo(siteId, child.get(), state, here, depth - 1)) {
                return true;
            }
        }
        return false;
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
        return withPosition(options, rows);
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
     *
     * <p>SH1278 — {@code locale=} narrows to one language, {@code locale=post.locale} to the page's
     * own. It means what the CDA's {@code /search?locale=} means (SH1057): an exact tag, and blank
     * is every locale. Absent is every locale too, so an existing template returns no less.</p>
     */
    Object search(Object context, Options options) throws IOException {
        String siteId = string(options.context.get(SITE_ID));
        String term = hash(options, "q", null);
        if (siteId == null || term == null) {
            return options.inverse();
        }
        String type = hash(options, "type", null);
        String locale = hash(options, "locale", null);
        List<ShPostState> states = statesFor(state(options));

        List<ShPostUnified> found = type == null
                ? shPostUnifiedRepository.searchBySitesAndStateIn(List.of(siteId), states, term, locale)
                : searchByType(siteId, type, states, term, locale);

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
                                             String term, String locale) {
        ShPostType postType = shPostTypeRepository.findByName(type);
        if (postType == null) {
            return List.of();
        }
        return shPostUnifiedRepository.searchBySiteAndTypesAndStateIn(
                siteId, List.of(postType.getId()), states, term, locale);
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
    /**
     * SH1105 — posts that exist only for the render in progress, resolved before the database.
     *
     * <p>A section thumbnail renders a page with one section of a type the page has never placed,
     * whose fields carry placeholder values. That section is not stored and must not be: a preview
     * writes nothing (SH1014's rule). A render-scoped registry rather than a key in the template
     * model, because the model is what templates read, and a transient post a template could print
     * would be a second way to reach content that bypasses every read rule.</p>
     */
    private static final ThreadLocal<Map<String, ShPostUnified>> TRANSIENT = new ThreadLocal<>();

    /** Run one render with these transient posts resolvable by their id, and never beyond it. */
    public static <T> T withTransient(Map<String, ShPostUnified> posts, java.util.function.Supplier<T> render) {
        Map<String, ShPostUnified> previous = TRANSIENT.get();
        TRANSIENT.set(posts);
        try {
            return render.get();
        } finally {
            if (previous == null) {
                TRANSIENT.remove();
            } else {
                TRANSIENT.set(previous);
            }
        }
    }

    private Optional<ShPostUnified> resolveReference(String siteId, String reference,
                                                     ShPostState state) {
        Map<String, ShPostUnified> transients = TRANSIENT.get();
        if (transients != null && transients.containsKey(reference)) {
            return Optional.of(transients.get(reference));
        }
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
        // SH1278 — the same `locale=` as `{{#search}}`, and absent is every locale.
        String locale = hash(options, "locale", null);
        int page = page(options);
        // SH419 — the window is decided before the rows are loaded. `listing` folds, filters and
        // sorts *sort keys* and then reads only the ≤limit posts it will actually draw, so the
        // entity count stops growing with the folder. The total below is still exact, and every
        // comparator and predicate is the one that was here before — see `listing`'s note for why
        // the ORDER BY itself deliberately did not move into the query.
        Listing listing = listing(folder.get(), type, field, value, locale, sort, page, limit, options);
        int pages = Math.max((listing.total() + limit - 1) / limit, 1);
        List<Map<String, Object>> rows = listing.rows().stream().map(this::row).toList();

        // The pager's facts, as Handlebars data variables. They are `@`-prefixed rather than row keys
        // for two reasons: a row is one post and these are properties of the *listing*, so putting
        // them on every row would repeat them per item and imply they varied; and SH251's scanner
        // skips `@` tokens by construction, so no field lint has to learn about them. Published before
        // `fn` so a template can read them inside the block, which is the only scope Handlebars gives a
        // block helper — there is no way to write into the enclosing one, and inventing a mechanism
        // for it would be a second one to learn.
        //
        // SH420 — and taken back afterwards. All ten land in ONE data scope shared with every
        // enclosing block, so an inner listing was overwriting the outer's position: a layout whose
        // outer `{{#query}}` body held an inner one and then read `{{#if @last}}` drew its pager on
        // every outer row, because the inner block's final iteration leaves `@last = true` behind and
        // the outer body reads it next. Measured 2 of 2 where 1 was expected. `restore` puts back
        // exactly what was there — including *absent*, which is not the same as false: an outer
        // `{{#if @last}}` outside any listing must stay unrendered rather than become `false`.
        //
        // Save-and-restore of the three the design offered, because it is the only one that costs a
        // template nothing. Namespacing (`@query.last`) is a rename and therefore a break, and moving
        // them onto the row gives up drawing a pager once — which is the entire reason they are data
        // variables rather than row keys.
        Map<String, Object> outer = saved(options);
        try {
            return emit(options, page, pages, listing, rows);
        } finally {
            restore(options, outer);
        }
    }

    /** The ten data variables `{{#query}}` owns, in the order the pager reads them. */
    private static final List<String> LISTING_DATA = List.of(
            "page", "pages", "total", "hasPrev", "hasNext", "prevPage", "nextPage",
            "index", "first", "last");

    /**
     * What an enclosing block had in the shared data scope, so this one can put it back (SH420).
     *
     * <p>A {@link LinkedHashMap} and not a {@code Map.of}, because a value of {@code null} —
     * "nothing was there" — is exactly the case that has to survive the round trip, and the immutable
     * factories reject it. Restoring `false` where there had been nothing would light up an
     * `{{#if @last}}` that a template put outside any listing on purpose.</p>
     */
    private static Map<String, Object> saved(Options options) {
        Map<String, Object> held = new LinkedHashMap<>();
        for (String key : LISTING_DATA) {
            held.put(key, options.data(key));
        }
        return held;
    }

    private static void restore(Options options, Map<String, Object> held) {
        held.forEach(options::data);
    }

    /**
     * The block's own rendering, with the ten data variables set (SH420).
     *
     * <p>Split out of {@code query} for one reason: it is everything that must happen <b>between</b>
     * the save and the restore, and a `try`/`finally` around a body this long invites somebody to add
     * a return path outside it. Its own method makes the bracket structural.</p>
     */
    private CharSequence emit(Options options, int page, int pages, Listing listing,
                              List<Map<String, Object>> rows) throws IOException {
        options.data("page", page);
        options.data("pages", pages);
        options.data("total", listing.total());
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
     * One block body per row, with {@code @index}, {@code @first} and {@code @last} (SH777).
     *
     * <h3>What was missing, and how it was found</h3>
     *
     * <p>{@code options.fn(row)} passes a plain context. Handlebars' positional variables come from
     * a <b>data frame</b>, and this helper created none — so inside {@code {{#getRelation sections}}}
     * a template could not ask which row it was on or whether it was the last. SH772 found it by
     * needing it: the captured stylesheet had to be emitted <em>once</em> for a page whose kept
     * markup is all in sections, and the natural spelling — fire on the first row carrying markup —
     * did not exist. It shipped a per-row emission and stated the bound instead.</p>
     *
     * <h3>Handlebars' own vocabulary, and the same three names SH384 already publishes</h3>
     *
     * <p>{@code emit} has done exactly this for a listing since SH384, for the same reason and with
     * the same comment: a pager drawn once is only expressible as
     * {@code {{#if @last}}…{{/if}}}, and without the frame it repeated per item. This is that idiom
     * made shared rather than copied — a second spelling of "which row is this" would be a second
     * thing to learn about one product.</p>
     *
     * <h3>What it makes expressible, which is more than the task that found it</h3>
     *
     * <p>A separator between rows and not after the last. A first section that renders larger.
     * {@code aria-setsize} on a list a screen reader is counting. A numbered list that survives a
     * reorder. Every one of those is a field on every section today, carrying its own position —
     * a value that goes stale the moment a curator moves one.</p>
     */
    private CharSequence withPosition(Options options, List<Map<String, Object>> rows)
            throws IOException {
        StringBuilder out = new StringBuilder();
        for (int index = 0; index < rows.size(); index++) {
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
                                        String locale, String sort, Options options) {
        return preferDraft(
                shPostUnifiedRepository.findByShFolderAndStateIn(folder, statesFor(state(options))),
                state(options))
                .stream()
                .filter(post -> type == null || type.equals(typeNameOf(post)))
                .filter(post -> locale == null || locale.equals(post.getLocale()))
                .filter(post -> field == null || value == null || carries(post, field, value))
                .sorted(order(sort))
                .toList();
    }

    /**
     * One page of a listing, and the exact total — without loading the rows it will not draw (SH419).
     *
     * <h3>What changed and, more importantly, what did not</h3>
     *
     * <p>SH384 measured <b>213 entity rows loaded to render 10</b>, the same for page 1 and page 19.
     * Every row past the window was read whole, hydrated with its JSON {@code data}, and discarded;
     * the cost grew with the folder rather than with the page, so a 5000-post archive paid 5000 rows
     * for the first ten of them.</p>
     *
     * <p><b>Not one predicate, comparator or fold moved.</b> The draft-preference fold, {@code order},
     * the {@code type=} match and the pager's exact total are the same code operating on the same
     * values in the same order — the change is that they operate on <em>sort keys</em> rather than on
     * entities, and only the survivors are loaded. That is deliberate rather than timid: pushing the
     * ORDER BY down is not portable here ({@code date} is nullable, the comparator is
     * {@code nullsLast}, MariaDB has no {@code NULLS LAST} and the engines disagree about a
     * {@code DESC} null), and a listing whose page boundaries differ by database is SH101's digest
     * moving for a change nobody made on one deployment and not another.</p>
     *
     * <p>The {@code field=} filter is the one case that still reads whole rows: it is a predicate over
     * the post's JSON, so the JSON is what it needs. Declared here rather than quietly handled, and
     * the benchmark records both paths so the exception has a number instead of a footnote.</p>
     */
    private Listing listing(ShFolder folder, String type, String field, String value, String locale,
                            String sort, int page, int limit, Options options) {
        ShPostState state = state(options);
        if (field != null && value != null) {
            // The predicate reads `data`, so there is nothing to save by not loading it.
            List<ShPostUnified> all = matching(folder, type, field, value, locale, sort, options);
            return new Listing(window(all, page, limit), all.size());
        }

        String typeId = type == null ? null : idOfPostType(type);
        if (type != null && typeId == null) {
            // An unknown type name matches nothing, which is what the in-memory path answers too —
            // never an error, for the reason the CDA gives at the same fork.
            return new Listing(List.of(), 0);
        }

        // SH634 — the window itself, when the order can be spelled portably. `newest` and `oldest`
        // are date sorts, and `coalesce(date, floor)` gives every engine the null placement the
        // Java comparator has; `title` cannot follow, because Java's CASE_INSENSITIVE_ORDER and a
        // database collation are not the same function and the difference would move page
        // boundaries per deployment. So `title` keeps the SH419 path, which is correct and scans —
        // declared here rather than left for somebody to discover from the benchmark.
        // SH1298 — a reader who fails a gate takes the key path, where the gated rows can be left
        // out before they are counted; the windowed query would count them.
        boolean gated = hidesGated(state);
        if (windowable(sort) && !gated) {
            List<ShListingKey> rows = "oldest".equals(sort.toLowerCase(java.util.Locale.ROOT))
                    ? shPostUnifiedRepository.findListingWindowOldest(folder, statesFor(state),
                            state, typeId, locale, DATE_FLOOR, PageRequest.of(page - 1, limit))
                    : shPostUnifiedRepository.findListingWindowNewest(folder, statesFor(state),
                            state, typeId, locale, DATE_FLOOR, PageRequest.of(page - 1, limit));
            long total = shPostUnifiedRepository.countListingWindow(folder, statesFor(state),
                    state, typeId, locale);
            return new Listing(load(rows), (int) total);
        }

        List<ShListingKey> keys = ShRenderState.preferDraftKeys(
                shPostUnifiedRepository.findListingKeys(folder, statesFor(state), locale), state);
        List<ShListingKey> matching = keys.stream()
                .filter(key -> typeId == null || typeId.equals(key.postTypeId()))
                .filter(key -> !gated || shContentAclService.visibility(key.id()).allowed())
                .sorted(orderKeys(sort))
                .toList();

        List<ShListingKey> wanted = window(matching, page, limit);
        return new Listing(load(wanted), matching.size());
    }

    /**
     * Which sorts the query can order by and get the same sequence the comparators do (SH634).
     *
     * <p>Only the date sorts. `title` is Java's {@code CASE_INSENSITIVE_ORDER} over a value that may
     * be null, and no database collation is that function — pushing it down would reorder somebody's
     * archive on one engine and not another, which is the class of defect the whole `coalesce`
     * argument exists to avoid. It stays on the SH419 path: correct, and scanning.</p>
     */
    private static boolean windowable(String sort) {
        String normalized = sort.toLowerCase(java.util.Locale.ROOT);
        return "newest".equals(normalized) || "oldest".equals(normalized);
    }

    /**
     * The instant a null {@code date} sorts as (SH634).
     *
     * <p>Below every real one, so {@code desc} puts nulls last and {@code asc} puts them first —
     * exactly what {@code Comparator.nullsLast(reverseOrder())} and its reverse do. A literal rather
     * than {@code Instant.MIN}, which no JDBC driver will bind to a timestamp column.</p>
     */
    private static final java.time.Instant DATE_FLOOR = java.time.Instant.parse("1000-01-01T00:00:00Z");

    /** The page's rows, loaded whole in one statement and put back into the window's order. */
    private List<ShPostUnified> load(List<ShListingKey> wanted) {
        if (wanted.isEmpty()) {
            return List.of();
        }
        Map<String, ShPostUnified> byKey = new LinkedHashMap<>();
        // Grouped by state so each read is one `IN`, which for a published render is a single
        // statement; a draft render can need two because the fold may keep rows of both states.
        for (ShPostState state : List.of(ShPostState.DRAFT, ShPostState.PUBLISHED)) {
            List<String> ids = wanted.stream()
                    .filter(key -> key.state() == state)
                    .map(ShListingKey::id)
                    .toList();
            if (ids.isEmpty()) {
                continue;
            }
            for (ShPostUnified post : shPostUnifiedRepository.findByIdInAndState(ids, state)) {
                byKey.put(post.getId() + "\0" + post.getState(), post);
            }
        }
        // Re-ordered by the window rather than by whatever the `IN` returned: the sort is the
        // listing's contract and a set read gives no order at all.
        return wanted.stream()
                .map(key -> byKey.get(key.id() + "\0" + key.state()))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private static <T> List<T> window(List<T> all, int page, int limit) {
        return all.stream().skip((long) (page - 1) * limit).limit(limit).toList();
    }

    /**
     * The same three orders as {@link #order}, over sort keys.
     *
     * <p>Two comparators for one contract is the shape SH245 warns about, and it is accepted here
     * for one reason: they are asserted to agree. {@code ShRenderListingOrderTest} sorts the same
     * population both ways and requires the same sequence of ids, so a change to one that is not
     * made to the other fails rather than reorders somebody's archive.</p>
     */
    private Comparator<ShListingKey> orderKeys(String sort) {
        Comparator<ShListingKey> byDateThenId = Comparator
                .comparing(ShListingKey::date, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ShListingKey::id);
        return switch (sort.toLowerCase(java.util.Locale.ROOT)) {
            case "oldest" -> byDateThenId.reversed();
            case "title" -> Comparator
                    .comparing((ShListingKey key) -> key.title() == null ? "" : key.title(),
                            String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(ShListingKey::id);
            default -> byDateThenId;
        };
    }

    /** One page of a listing: the rows to draw, and how many there are in all. */
    private record Listing(List<ShPostUnified> rows, int total) {
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
        ShPostState state = state(options);
        boolean gated = hidesGated(state);
        List<ShFolder> children = new ArrayList<>(shFolderRepository
                .findByParentFolderOrderByPositionAsc(parent.get()).stream()
                // SH1298 — a members-only section is not a menu entry for somebody it turns away.
                .filter(child -> !gated || shContentAclService.visibility(child.getId()).allowed())
                .toList());
        if (children.isEmpty()) {
            return options.inverse();
        }
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
        // SH1143 — a proposed render may carry the bytes instead of an id. Already a URL, so it is
        // the answer rather than a lookup key; see the constant's note for why nothing is stored.
        if (inlineBytes(id)) {
            return id;
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

    /**
     * {@code {{#image HERO}}…{{/image}}} — one image with the facts a {@code srcset} needs (SH390).
     *
     * <h3>The premise SH390 was filed on turned out to be stale, and that decided the design</h3>
     *
     * <p>The task offered three answers and said the choice was the task: build derivatives into the
     * tree, add an on-demand resizing endpoint, or delegate to the delivery layer and ship "the helper
     * shape". It evaluated the second as <em>"a new subsystem and a cache-invalidation question"</em> —
     * and that subsystem <b>already exists</b>: SH40/SH41 ship {@code ShImageTransformPipeline}, served
     * at {@code /file_source/**} with {@code ?w=&h=&format=&crop=}, cached, guarded and optionally
     * signed. So there was nothing to choose between: two of the three options were about building
     * something that is built.</p>
     *
     * <p>What is actually missing is small and is exactly the third option's "helper shape". A template
     * cannot emit a {@code srcset} for two reasons, and both of them need the server: it does not know
     * the image's <b>intrinsic width</b>, so every candidate it wrote would risk upscaling; and when
     * signing is on it cannot compute an <b>HMAC</b>. Nothing else about the problem was missing.</p>
     *
     * <h3>It costs nothing, which is why it is a helper rather than a pass</h3>
     *
     * <p>The dimensions are already on the {@code File} post's own {@code data} — {@code
     * ShStaticFileService} reads them at upload and stores {@code WIDTH}/{@code HEIGHT} — so this reads
     * the same {@code fieldsOf} map {@link #bytePath} already reads. <b>No extra query, no file read,
     * no image decoding.</b> A build-time derivative pass would have put N copies of every image in
     * git; this puts nothing anywhere.
     *
     * <h3>Never upscales, and that is the fidelity claim</h3>
     *
     * <p>The candidate ladder is filtered to widths <em>below</em> the intrinsic one, with the original
     * as the largest entry. A {@code srcset} offering 1920w for a 900px image tells the browser a lie
     * it will act on, and SH386 grades a replica against a source that ships a small image to a small
     * viewport — so this is a fidelity question rather than an optimisation, which is the task's own
     * framing.
     *
     * <h3>An image the instance cannot measure yields the plain url and no srcset</h3>
     *
     * <p>An SVG, or a file uploaded before the dimension reader existed, has no {@code WIDTH}. The row
     * still carries {@code url} so the template renders a working {@code <img>}; {@code srcset} is
     * empty, which in HTML means "use src" and is exactly right. Fabricating a ladder from a width
     * nobody knows is the one thing that would produce broken images rather than unoptimised ones.
     */
    Object image(Object context, Options options) throws IOException {
        String id = string(context);
        if (id == null || id.isBlank()) {
            return options.inverse();
        }
        /*
         * SH1143 — inline bytes render as themselves, with no ladder.
         *
         * <p>There is no `File` post, so there is no stored `WIDTH`, and `widths` is empty — which
         * this helper's own note says is "exactly right": an empty `srcset` means "use src" in HTML,
         * and fabricating a ladder from a width nobody knows is the one thing that produces broken
         * images rather than unoptimised ones. A preview of an unpushed image is unoptimised and
         * correct, which is what a preview owes the author.</p>
         */
        if (inlineBytes(id)) {
            // The same keys the row below carries, so a template written for one renders the other.
            // `alt` is empty and `altAttr` is nothing at all: there is no `File` post to hold an ALT,
            // and SH1031's rule is that an absent description emits no attribute rather than an
            // empty one — which is exactly what keeps `image-no-alt` reachable on the pushed page.
            // `decorative=` is still honoured, because that is the caller's statement and not the
            // asset's. No `alt=` hash is read: SH291's lint holds this signature to the keys the
            // body reads, and it caught the first draft of this branch for reading one.
            boolean decorative = "true".equalsIgnoreCase(hash(options, "decorative", "false"));
            Map<String, Object> inline = new LinkedHashMap<>();
            inline.put("url", id);
            inline.put("width", 0);
            inline.put("height", 0);
            inline.put("alt", "");
            inline.put("altAttr", altAttribute("", decorative));
            inline.put("widths", List.of());
            inline.put("srcset", "");
            inline.put("sizes", hash(options, "sizes", "100vw"));
            return options.fn(ShStableMap.copyOf(inline));
        }
        Optional<ShPostUnified> file = preferDraft(shPostUnifiedRepository.findById(id), state(options))
                .stream().findFirst();
        if (file.isEmpty()) {
            return options.inverse();
        }
        Map<String, Object> fields = shRenderModel.fieldsOf(file.get());
        String url = bytePath(file.get());
        if (url.isEmpty()) {
            return options.inverse();
        }
        int width = whole(fields.get("WIDTH"));
        int height = whole(fields.get("HEIGHT"));
        List<Integer> widths = ladderFor(width);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("url", url);
        row.put("width", width);
        row.put("height", height);
        // SH1031 — `alt` is what the asset says, and nothing more. It used to be an empty string
        // whenever ALT was null, which reads as `alt=""` on the page: the HTML for "this image is
        // decorative, skip it". So a site built the recommended way told every screen reader to
        // skip every undescribed image, and `image-no-alt` — which fires on an ABSENT attribute,
        // deliberately, because absent and empty mean different things — could never fire.
        String alt = fields.get("ALT") == null ? "" : fields.get("ALT").toString();
        // `decorative=true` as the template writes it, which is a string by the time it gets here.
        boolean decorative = "true".equalsIgnoreCase(hash(options, "decorative", "false"));
        row.put("alt", alt);
        // The whole attribute, for the reason `srcset` is whole: a template cannot compose this
        // one either. `alt="…"` when there is text, `alt=""` when the caller said `decorative=true`
        // — a recorded decision — and NOTHING at all otherwise, which is what leaves the finding
        // reachable instead of papering over it.
        row.put("altAttr", altAttribute(alt, decorative));
        row.put("widths", widths);
        // The whole attribute value, not the parts. A template cannot compose it: each candidate needs
        // an HMAC when signing is on, and that is the one thing only the server can do here.
        row.put("srcset", srcsetFor(id, url, widths));
        row.put("sizes", hash(options, "sizes", "100vw"));
        return options.fn(ShStableMap.copyOf(row));
    }

    /**
     * The whole {@code alt} attribute, or nothing (SH1031).
     *
     * <p>Three states, and the third is the one that had no spelling: described, deliberately
     * decorative, and <b>not yet described</b>. HTML has all three — {@code alt="text"},
     * {@code alt=""} and no attribute — and the helper collapsed the last two into the second,
     * which is the one assertion an accessibility check has to be able to make.</p>
     *
     * <p>Escaped here because it is emitted with {@code &#123;&#123;&#123;altAttr&#125;&#125;&#125;}:
     * a value that carries a quote would otherwise close the attribute and everything after it
     * would be markup.</p>
     */
    private static String altAttribute(String alt, boolean decorative) {
        if (alt != null && !alt.isBlank()) {
            return " alt=\"" + alt.replace("&", "&amp;").replace("\"", "&quot;")
                    .replace("<", "&lt;").replace(">", "&gt;") + "\"";
        }
        return decorative ? " alt=\"\"" : "";
    }

    /**
     * The candidate widths for an image of this intrinsic width.
     *
     * <p>A fixed ladder rather than a setting: the values are the ones device pixel ratios and common
     * breakpoints actually produce, and a per-site list would be a knob nobody could tune without
     * measuring a real audience. The intrinsic width is always the last entry, so the largest candidate
     * is the original and no entry claims a size the bytes cannot fill.
     */
    private List<Integer> ladderFor(int intrinsic) {
        if (intrinsic <= 0) {
            return List.of();
        }
        List<Integer> out = new ArrayList<>();
        for (int candidate : new int[] { 320, 640, 960, 1280, 1920 }) {
            if (candidate < intrinsic) {
                out.add(candidate);
            }
        }
        out.add(intrinsic);
        return List.copyOf(out);
    }

    /**
     * The {@code srcset} attribute value, signed per candidate when signing is enabled.
     *
     * <p>Unsigned when it is not, because the pipeline accepts an unsigned transform then and a
     * {@code sig} nobody verifies is noise in every URL. When signing <em>is</em> on and the secret is
     * missing the signer throws — and that is caught here into an <b>empty srcset</b> rather than a
     * failed render: a page that fell over because a transform could not be signed would take a site
     * down for a configuration mistake, and an image at its natural size is the correct degradation.
     * {@code shio verify} has no rule for it because the pipeline's own startup log already reports an
     * enabled signer with no secret.
     */
    private String srcsetFor(String id, String url, List<Integer> widths) {
        if (widths.size() < 2) {
            // One candidate is the original, and `srcset="… 900w"` with a single entry buys a browser
            // nothing it does not already have from `src`.
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int width : widths) {
            ShImageTransformSpec spec = new ShImageTransformSpec(width, null, null, null);
            String query = "w=" + width;
            if (imageTransformProperties.getSigning().isEnabled()) {
                try {
                    query += "&sig=" + imageTransformSigner.sign(id, spec);
                } catch (IllegalStateException e) {
                    return "";
                }
            }
            if (!out.isEmpty()) {
                out.append(", ");
            }
            out.append(url).append('?').append(query).append(' ').append(width).append('w');
        }
        return out.toString();
    }

    private int whole(Object value) {
        if (value instanceof Number number) {
            return Math.max(number.intValue(), 0);
        }
        try {
            return Math.max(Integer.parseInt(String.valueOf(value).trim()), 0);
        } catch (RuntimeException e) {
            // A `WIDTH` that is not a number means the same as an absent one: no ladder, plain url.
            // Not recorded, because an unmeasurable image is not a degradation — see the helper's note.
            return 0;
        }
    }

    /**
     * SH1143 — how large a `data:` URI a proposed render will carry, in characters.
     *
     * <p>A render request is a JSON body held in memory and rendered synchronously, so the ceiling
     * is what stops one carrying a video. Two megabytes of base64 is roughly 1.5 MB of bytes, which
     * is a generous hero image and not a film. Over it the value is not a URL this will emit: it
     * falls through to the id lookup, finds nothing, and renders as the empty string — the same
     * answer an unknown id gets, which is the conservative one.</p>
     */
    private static final int INLINE_CEILING = 2 * 1024 * 1024;

    /**
     * Whether a `File` field's value is the bytes themselves rather than a post id (SH1143).
     *
     * <p>`shio preview` renders a content file before it is pushed, and an image the author has just
     * dropped under `shio/assets/**` has no id on the instance — the field names a tree path, which
     * is not an id, so the page showed a broken image where it will show a picture. The author
     * learned the wrong thing about the page, which is the one thing a preview must not do.</p>
     *
     * <p>So a proposed render may put the bytes in the field as a `data:` URI. <b>Nothing is
     * stored</b>, which is the property that made this the answer over staging the asset under a
     * disposable name: a write needs sweeping, and P7 says a preview stores nothing.</p>
     *
     * <p>Deliberately narrow. Only `data:image/`, because this is what an `<img>` renders and a
     * `data:text/html` in a page is a script host; and only under {@link #INLINE_CEILING}.</p>
     */
    private boolean inlineBytes(String value) {
        return value.length() <= INLINE_CEILING
                && value.regionMatches(true, 0, "data:image/", 0, "data:image/".length());
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
        return readableOnly(ShRenderState.preferDraft(posts, state), state);
    }

    /**
     * SH1298 — whether this render leaves out what its reader may not read.
     *
     * <p>Only the published render does: {@code /preview}, the Universal Editor and the agent's
     * digest render drafts, and an editor must see a gated page there to edit it. Keyed on the
     * state rather than the route, because the state is what the helpers already receive. On an
     * instance with no gate this is one count that loads no entity, which is why
     * {@code ShRenderListingPageBenchmarkTest} does not move.</p>
     */
    private boolean hidesGated(ShPostState state) {
        return state == ShPostState.PUBLISHED && shContentAclService.deniesAnyGate();
    }

    /** SH1298 — the rows of a published render this reader may read, in order. */
    private List<ShPostUnified> readableOnly(List<ShPostUnified> posts, ShPostState state) {
        if (posts.isEmpty() || !hidesGated(state)) {
            return posts;
        }
        return posts.stream()
                .filter(post -> shContentAclService.visibility(post.getId()).allowed())
                .toList();
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

    /**
     * A post-type name to its id, or {@code null} when nothing is named that (SH419).
     *
     * <p>The listing filters on the id because that is a column on the post table, where the name
     * would join the JOINED {@code ShObject} root and trip the {@code @TenantId} hazard. One extra
     * read, and only when {@code type=} was given — against a whole-folder entity load it is free.</p>
     */
    private String idOfPostType(String name) {
        ShPostType found = shPostTypeRepository.findByName(name);
        return found == null ? null : found.getId();
    }

    /** A row is the post's fields plus the four things a listing always writes. */
    private Map<String, Object> row(ShPostUnified post) {
        String type = typeNameOf(post);
        Map<String, Object> row = new LinkedHashMap<>(shRenderModel.fieldsOf(post));
        composedStylesheet(row, type, post);
        rawTextFields(row, type);
        row.put("id", post.getId());
        row.put("title", post.getTitle());
        row.put("summary", post.getSummary());
        row.put("url", post.getFurl());
        // `link` is the site-relative address a delivered page would use. It is the post's
        // friendly URL and not a preview path on purpose: the markup a template produces
        // has to be the markup the front end serves, or the preview is proving the wrong
        // page. The preview route rewrites nothing.
        row.put("link", post.getFurl());
        row.put("type", type);
        // SH383 — on **every** row, not only a translation's. It is a property of every post, it is
        // already loaded, and a multilingual site wants it on a listing too: a card that renders
        // `lang` on a quoted excerpt is the ordinary case, and so is a `{{#query}}` that has to skip
        // rows in the wrong language. Adding it to `{{#translations}}` alone would have been a second
        // row shape for one field, which is the trade `FORM_FIELD_KEYS` is documented as *worth* and
        // this one plainly is not.
        row.put("locale", ShRenderModel.locale(post));
        return ShStableMap.copyOf(row);
    }

    /**
     * SH555 — a row's {@code CSS} and {@code JAVASCRIPT} are raw, for SH461's reason and at
     * SH461's seam.
     *
     * <h3>The same defect, on the emission SH461 did not reach</h3>
     *
     * <p>SH461 marked {@code theme.css} and {@code theme.javascript} {@link Handlebars.SafeString}
     * and said why the cure belongs on the value rather than on the stash: "or every hand-written
     * layout in the product keeps the defect and only the generated one is cured." SH549 then gave
     * a converted site a <em>second</em> emission of the same stylesheet — a per-page
     * {@code {{#getRelation "/theme/captured"}}<style>{{CSS}}</style>{{/getRelation}}}, so that the
     * verbatim pages of a mixed conversion get the source's CSS and the authorable ones do not.
     * That one reads a plain field off this row, and this row escaped it.</p>
     *
     * <p>Measured on a mixed conversion: the stored {@code Theme} post holds 1,061,481 characters
     * and zero entities, the page it produces carries 378 — every {@code @font-face}, every quoted
     * {@code content:} and every quoted {@code url()} — and {@code verify} reports ten
     * {@code page-reference-broken} errors naming paths that begin with a literal {@code &quot;}.
     * The fidelity path is the contrast that locates it: it emits the very same theme through
     * {@code theme.css} and serves sixty clean {@code url()} with no entity at all. So the defect
     * was never in the capture, the store or the escaping rule — one of the two emissions was
     * written as a field read.</p>
     *
     * <h3>Why by field and post-type rather than for every field</h3>
     *
     * <p>These are the fields the product declares as {@code Ace Editor - CSS/JavaScript}
     * ({@link ShRenderPostTypes}), and a template can only put them where they belong: inside
     * {@code <style>} or {@code <script>}, which are <b>raw-text elements</b>. Entity escaping is
     * not the encoding for that context, so escaping them removes correctness and defends nothing
     * — the {@code Theme} is written by whoever writes the {@code PageLayout} that emits it, which
     * is the privilege boundary SH461 already found is not there. Every <em>other</em> field on
     * every other type keeps escaping, because an {@code Article}'s body in a listing card is
     * exactly the case where it is the right encoding.</p>
     */
    /**
     * SH850 — a {@code Theme} row's {@code CSS} is the stylesheet it <b>renders as</b>, not the field.
     *
     * <h3>SH555's own sentence, with one more field in it</h3>
     *
     * <p><em>One of the two emissions was written as a field read.</em> That fixed the escaping and
     * left the composition: a theme renders as {@code ShThemeTokens.assets} — its {@code TOKENS}
     * compiled into a {@code :root} block and prepended — and this row carried the stored bytes.
     * Harmless while the two fields were independent; page-wide the moment SH424 started <b>removing</b>
     * a lifted declaration from {@code CSS} on the promise that the compiled block re-declares it.
     * Measured on a replica one round apart: dangling {@code var()} occurrences <b>231 → 958</b>, of
     * which 132 were properties lifted out of the captured sheet and still read by rules that stayed.</p>
     *
     * <p>Ahead of {@link #rawTextFields}, so the composed value is what gets SH555's answer — the
     * order is the whole of why this is two statements rather than one. And the composition itself
     * lives on {@link ShRenderModel}, which owns the other emission: two copies of it have now
     * diverged twice, and a third would be nobody's fault in particular.</p>
     */
    private void composedStylesheet(Map<String, Object> row, String type, ShPostUnified post) {
        if (ShSystemPostType.THEME.equals(type) && row.get(ShSystemPostTypeAttr.CSS) != null) {
            row.put(ShSystemPostTypeAttr.CSS, shRenderModel.themeAssetsOf(post).css());
        }
    }

    private static void rawTextFields(Map<String, Object> row, String type) {
        if (!RAW_TEXT_FIELDS.containsKey(type)) {
            return;
        }
        for (String field : RAW_TEXT_FIELDS.get(type)) {
            if (row.get(field) instanceof String value) {
                row.put(field, new Handlebars.SafeString(value));
            }
        }
    }

    /**
     * The (post-type, field) pairs a template can only emit into a raw-text element (SH555),
     * <b>derived</b> from the table that declares them (SH657).
     *
     * <p>This was a hand-kept list that stated its own selection rule — *the fields the product
     * declares as {@code Ace Editor - CSS/JavaScript}* — and held <b>four of the six</b>.
     * {@code Theme.TOKENS} and {@code SiteScripts.SCRIPTS} were each added to
     * {@link ShRenderPostTypes} on their own task, correctly, and neither reached here; nothing
     * could notice, because the rule lived in prose and the list in code. A template emitting one
     * would have produced JSON with {@code &quot;} for every key, which does not parse — SH461's
     * defect and SH555's, a third and fourth time.</p>
     *
     * <p>So the list is now computed from the widget each field is declared with, which is what the
     * sentence always meant. Adding a code-editor field to a render type now reaches this map on the
     * same commit, and the pairing is kept: an author's {@code CSS} field on an {@code Article} is a
     * string in a document body with no declared context, and still escapes.</p>
     */
    private static final Map<String, Set<String>> RAW_TEXT_FIELDS = ShRenderPostTypes.rawTextFields();

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
