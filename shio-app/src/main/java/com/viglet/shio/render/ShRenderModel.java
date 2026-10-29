/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import com.viglet.shio.api.form.ShFormSubmissionAPI;
import com.viglet.shio.api.agent.diagnostics.ShAgentIncidentFrames;
import com.viglet.shio.api.agent.diagnostics.ShAgentIncidentLog;
import com.viglet.shio.api.site.ShSiteLocaleService;
import com.viglet.shio.api.site.ShSiteLocales;
import java.util.Optional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.stereotype.Component;

import com.github.jknack.handlebars.Handlebars;
import com.viglet.shio.object.ShFolderSite;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;
import com.viglet.shio.utils.ShStableMap;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SH183 — what a template can see, and nothing else.
 *
 * <h2>The model is maps all the way down</h2>
 *
 * <p>{@link ShTemplateEngine} resolves values with {@code MapValueResolver} only, so this
 * class is the entire surface a layout or region has access to. That is deliberate and is
 * the second half of the safety argument: handing a template the {@code ShPostUnified}
 * entity and a bean resolver would make {@code {{shFolder.shSite.shFolders}}} a walk of
 * the persistence model, and a layout is content. Projecting to maps means an expression
 * that names something outside this shape resolves to empty rather than to whatever the
 * object graph happens to expose.</p>
 *
 * <h2>Shape</h2>
 *
 * <pre>
 * {  &lt;the post's own data fields, verbatim under their declared names&gt;,
 *    post:  { id, title, summary, url, type, folder, canonical },
 *    site:  { id, name, url, baseUrl },
 *    theme: { css, javascript }   }
 * </pre>
 *
 * <h2>Two URLs per frame, and the pair is the point (SH337)</h2>
 *
 * <p>{@code post.url} is the friendly URL and {@code site.url} is the customer's front end —
 * the fields as content spells them, and {@code site.url} is <b>blank</b> on a site nobody has
 * deployed, which is the normal state of one an agent just built. {@code post.canonical} and
 * {@code site.baseUrl} are the same two facts resolved through {@link ShSiteFrontEnd} (SH184),
 * so they are never blank: they fall back to {@code /preview/<site>}, the address at which the
 * page really can be looked at. A canonical tag, an {@code og:url} and a sitemap entry are
 * unbuildable from the first pair and buildable from the second.</p>
 *
 * <p>Fields go in <b>first</b> and the three reserved keys overwrite them, so a post-type
 * that happens to declare a field called {@code site} loses to the site — the reverse
 * would let a content author shadow the frame the layout is built on, and a template
 * silently rendering a data value where it asked for the site's URL is worse than a
 * field the author can rename. Field names are kept <b>verbatim</b> rather than
 * lower-cased: the 2018 templates write {@code {{TITLE}}} and the SH95 vocabulary writes
 * {@code {{headline}}}, and normalising either would break the other.</p>
 *
 * <h2>Ordering</h2>
 *
 * <p>Built through {@link ShStableMap} (SH136). The model is iterated by {@code {{#each}}}
 * and it feeds markup that SH101 hashes, so its order is observable — and
 * {@code Map.copyOf} derives iteration order from a per-JVM salt, which would make the
 * same content render differently after a restart.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShRenderModel {

    /**
     * The reserved top-level frames of the render model — SH260's rule applied to a fourth fact.
     *
     * <p>{@code post}, {@code site} and {@code theme} are put by {@link #of}; {@code render} is put
     * by {@code ShPageRenderService}. Three writers and, before SH292, a fourth <em>private</em> copy
     * in {@code ShTemplateFieldLint} used to filter them — a set nobody owned, which is the shape
     * whose drift SH260 found showing up as a warning on a correct template. The context pack now
     * <b>tells an author</b> what these are, so a stale list would be a wrong instruction rather than
     * an internal inconsistency, and `ShRenderConventionsTest` asserts the writers against it in both
     * directions.</p>
     */
    public static final Set<String> FRAMES = Set.of("post", "site", "theme", "render", "param");

    private final ObjectMapper objectMapper;
    private final ShAgentIncidentLog incidentLog;
    /** SH1056 — the site's declared policy, read off its own column and never derived here. */
    private final ShSiteLocaleService shSiteLocaleService;

    public ShRenderModel(ObjectMapper objectMapper, ShAgentIncidentLog incidentLog,
                         ShSiteLocaleService shSiteLocaleService) {
        this.objectMapper = objectMapper;
        this.incidentLog = incidentLog;
        this.shSiteLocaleService = shSiteLocaleService;
    }

    /**
     * @param post  the page being rendered
     * @param theme the resolved theme's assets, or {@code null}
     * @return the view model
     */
    public Map<String, Object> of(ShPostUnified post, ShThemeAssets theme) {
        return of(post, theme, null);
    }

    /**
     * @param scripts SH389's three positioned lists, or {@code null} for a site with none
     */
    public Map<String, Object> of(ShPostUnified post, ShThemeAssets theme,
                                  Map<String, Object> scripts) {
        return of(post, theme, scripts, null);
    }

    /**
     * @param themeTitle SH829 — the title of the {@code Theme} post {@code theme} came from, so a
     *                   layout can name the sheet it emits. An overload rather than a fourth
     *                   component on {@link ShThemeAssets}: the record is the theme's <em>assets</em>
     *                   and is built by {@code ShThemeTokens} from three fields of the post, none of
     *                   which is its title — the caller that resolved the post is the one that knows.
     */
    public Map<String, Object> of(ShPostUnified post, ShThemeAssets theme,
                                  Map<String, Object> scripts, String themeTitle) {
        // Resolved once: both frames need it now that SH337 projects the site's origin, and
        // the folder walk below is not free.
        ShSite site = siteOf(post);
        Map<String, Object> model = new LinkedHashMap<>(substituteAnnotated(fieldsOf(post)));
        model.put("post", postFrame(post, site));
        model.put("site", siteFrame(site, scripts));
        model.put("theme", themeFrame(theme, themeTitle));
        return ShStableMap.copyOf(model);
    }

    /**
     * The post's {@code data} column, parsed. Sorted by field name so the model's own
     * iteration order does not depend on how the JSON happened to be written — two posts
     * with the same fields must render identically.
     */
    public Map<String, Object> fieldsOf(ShPostUnified post) {
        Map<String, Object> fields = new TreeMap<>();
        if (post == null || post.getData() == null || post.getData().isBlank()) {
            return fields;
        }
        try {
            JsonNode node = objectMapper.readTree(post.getData());
            if (node != null && node.isObject()) {
                for (String key : node.propertyNames()) {
                    fields.put(key, scalar(node.get(key)));
                }
            }
        } catch (JacksonException e) {
            // A post whose data is not an object renders with no fields rather than
            // failing the page: the defect belongs to the content, and SH99's lint is
            // where it is reported. A blank page would hide it.
            //
            // SH270 — and it is reported *here* too, because "SH99's lint is where it is reported"
            // was only true of a page somebody thought to lint. The symptom is a page that looks
            // empty, which is the one shape nobody investigates, and the window folds by subject so
            // a post renders a hundred times and leaves one entry.
            incidentLog.record(ShAgentIncidentLog.Kind.CONTENT,
                    "id:" + (post.getId() == null ? "?" : post.getId()),
                    "This post's data is not a JSON object, so the page renders with none of its "
                            + "fields: " + e.getOriginalMessage(),
                    "Read the post with GET /api/v2/agent/read and rewrite its fields with a "
                            + "post.upsert. The page will keep rendering empty until then.",
                    // SH204 — through `siteOf`, which is this class's own resolver and already
                    // carries the ShFolderSite-exempt reasoning: a post has its own site column and
                    // the folder walk is only the fallback for a row written before it existed.
                    // Reusing it rather than reaching for `post.getShSite()` here is the point of
                    // it having been lifted out by SH337.
                    ShAgentIncidentFrames.of(e), siteIdOf(post));
            return fields;
        }
        return fields;
    }

    /**
     * A verbatim field's annotated elements, showing the post's own fields (SH870).
     *
     * <h2>The write that was stored and never shown</h2>
     *
     * <p>SH864 measured that annotating a fidelity page is free — {@code sh-field} and
     * {@code data-shio-prop} are attributes, and attributes do not lay out — and decided where an
     * annotated page's values live: <b>the field is the address</b>. {@link ShPostDraftMergeService}
     * writes {@code data.<field>} and never creates structure, so an edit through the Universal
     * Editor lands in the post's data exactly as it does everywhere else. What was missing is this
     * half: the page's markup is stored verbatim, so the value was written and the page went on
     * showing the source's own text. The editor accepted an edit and the page denied it.</p>
     *
     * <h2>Here rather than in the composer, which is what scopes it</h2>
     *
     * <p>{@code ShRegionComposer.annotateEditable} already selects {@code [sh-field]} — over the
     * <b>whole composed document</b>. Substituting there would also rewrite every <em>section</em>
     * region, whose text the template already produced from that same field, sometimes through a
     * helper: the rendered value would be overwritten by the raw one. Doing it to the field's
     * <em>value</em> before a template ever sees it is scoped by construction — only this post's
     * data, only a field whose own value carries the annotation.</p>
     *
     * <h2>Byte-identical until edited, and that is a guard rather than a hope</h2>
     *
     * <p>Parsing and re-serialising normalises markup, so a page with nothing to substitute must not
     * be parsed at all — otherwise annotating would move pixels, which is the property SH864
     * measured. So the walk runs only where a substitution will actually apply, and returns the
     * original string byte-for-byte otherwise. Once a curator HAS edited, the serialisation is
     * jsoup's; the page has changed by then, and by their instruction.</p>
     *
     * <h2>Not a template language</h2>
     *
     * <p>{@code content} is emitted {@code {{{content}}}} without interpolation on purpose (SH464).
     * Compiling captured markup would read a {@code &#123;&#123;} in a framework's own output as a
     * directive. This is one annotated element, one declared field, and no expression: the value is
     * set as <b>text</b>, so a value carrying markup is escaped rather than parsed.</p>
     */
    private Map<String, Object> substituteAnnotated(Map<String, Object> fields) {
        Map<String, Object> out = null;
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            if (!(entry.getValue() instanceof String markup)
                    || !markup.contains(ShRegionComposer.FIELD_ATTR + "=")) {
                continue;
            }
            String rewritten = substituteInto(markup, fields);
            if (rewritten == null) {
                continue;
            }
            if (out == null) {
                out = new LinkedHashMap<>(fields);
            }
            out.put(entry.getKey(), rewritten);
        }
        return out == null ? fields : out;
    }

    /**
     * One verbatim value with its annotated elements filled in, or null where nothing applies.
     *
     * <p>Null rather than the input, so {@link #substituteAnnotated} can tell "nothing to do" from
     * "done" without comparing strings — and so the untouched case never allocates a parse.</p>
     */
    private String substituteInto(String markup, Map<String, Object> fields) {
        org.jsoup.nodes.Document document = org.jsoup.Jsoup.parseBodyFragment(markup);
        boolean applied = false;
        for (org.jsoup.nodes.Element element
                : document.select("[" + ShRegionComposer.FIELD_ATTR + "]")) {
            String name = element.attr(ShRegionComposer.FIELD_ATTR).trim();
            // A path into a collection is not this: those rows are the section's own data and its
            // template renders them. Only a flat key, which is what a verbatim block's annotation is.
            if (name.isEmpty() || name.indexOf('.') >= 0) {
                continue;
            }
            Object value = fields.get(name);
            if (!(value instanceof String text) || text.isBlank()) {
                continue;
            }
            // The source's own text is what an unedited field renders, so a value equal to it is not
            // an edit and must not cost a re-serialisation.
            if (text.equals(element.text())) {
                continue;
            }
            element.text(text);
            applied = true;
        }
        return applied ? document.body().html() : null;
    }

    /**
     * JSON → the maps, lists and scalars the resolver understands. One conversion for
     * every node type: a nested object becomes a {@code Map} and an array a {@code List},
     * which is exactly what {@code MapValueResolver} and {@code {{#each}}} expect, and
     * nothing arrives as a {@code JsonNode} the bean resolver would have to walk.
     */
    private Object scalar(JsonNode value) {
        return value == null || value.isNull()
                ? null
                : objectMapper.convertValue(value, Object.class);
    }

    /**
     * SH204 — {@link #siteOf} reduced to the id an incident stores, null-safe at both steps.
     *
     * <p>Separate from {@code siteOf} only because the caller is on a failure path and wants the
     * id rather than the entity; going through it is what keeps the incident's attribution and the
     * page's own idea of its site the same answer, including the folder-walk fallback.</p>
     */
    private String siteIdOf(ShPostUnified post) {
        ShSite site = siteOf(post);
        return site == null ? null : site.getId();
    }

    /**
     * The page's site.
     *
     * <p>Lifted out of {@code siteFrame} by SH337, which gave the {@code post} frame a reason to
     * know it too — resolving it twice would have meant a second folder walk for a fact the
     * model already had.</p>
     */
    private ShSite siteOf(ShPostUnified post) {
        // ShFolderSite-exempt: a post carries its own site column (SH128's invariant is
        // about folders), and the folder walk is the fallback for a row written before it.
        ShSite site = post == null ? null : post.getShSite();
        if (site == null && post != null) {
            site = ShFolderSite.of(post.getShFolder());
        }
        return site;
    }

    private Map<String, Object> postFrame(ShPostUnified post, ShSite site) {
        Map<String, Object> frame = new LinkedHashMap<>();
        if (post == null) {
            return ShStableMap.copyOf(frame);
        }
        frame.put("id", post.getId());
        frame.put("title", post.getTitle());
        frame.put("summary", post.getSummary());
        frame.put("url", post.getFurl());
        frame.put("type", post.getShPostType() == null ? null : post.getShPostType().getName());
        ShFolder folder = post.getShFolder();
        frame.put("folder", folder == null ? null : folder.getName());
        // SH337 — `url` is the furl and stays the furl; this is the same page addressed at the
        // site's front end. Composed here rather than left to `{{site.baseUrl}}{{post.url}}`
        // because a furl is not guaranteed to lead with `/` and that concatenation silently
        // yields `/preview/Democontato`; `pageUrl` is the one place that normalizes it.
        frame.put("canonical", site == null ? null
                : ShSiteFrontEnd.pageUrl(ShSiteFrontEnd.of(site), post.getFurl()));
        // SH383 — the fact the whole render model was missing. SH348 gave a file `locale:` and
        // stopped at the projection, so a six-language site could be *stored* and could not render
        // `<html lang>`.
        //
        // **Never blank**, which is the one decision here. The column is nullable and a row written
        // before it existed has no value, so the obvious `post.getLocale()` would emit
        // `<html lang="">` — and an empty `lang` is not "unspecified" to a browser or a screen
        // reader, it is an assertion that the language is *unknown*, which is worse than a
        // defaulted-but-valid tag. The fallback is the writer's own default, named once
        // (SH348's rule: the writer owns it, everything else reads it) — and since SH383 the
        // *defaulting* is named once there too, not just the constant.
        frame.put("locale", locale(post));
        return ShStableMap.copyOf(frame);
    }

    /**
     * A post's locale, defaulted.
     *
     * <p>Delegated to {@link ShPostUnifiedWriteService#localeOf}, which is where the defaulting lives
     * because the writer owns the default (SH348). This exists as a name in this package only so the
     * helpers can pass it as a method reference; it deliberately holds no rule of its own.</p>
     */
    static String locale(ShPostUnified post) {
        return ShPostUnifiedWriteService.localeOf(post);
    }

    private Map<String, Object> siteFrame(ShSite site, Map<String, Object> scripts) {
        Map<String, Object> frame = new LinkedHashMap<>();
        if (site == null) {
            return ShStableMap.copyOf(frame);
        }
        frame.put("id", site.getId());
        frame.put("name", site.getName());
        frame.put("url", site.getUrl());
        // SH337 — `url` is the customer's front end and is *empty* on a site nobody has
        // deployed, which is the normal state of a site an agent just built (SH184). A canonical
        // tag, an `og:url` or a sitemap entry cannot be built from an empty string, so the model
        // also carries the resolved origin, which is never blank: the external URL when there is
        // one, `/preview/<site>` when there is not. Both are here because they answer different
        // questions — "has this site been deployed?" is `url`, and it must stay answerable.
        frame.put("baseUrl", ShSiteFrontEnd.of(site).baseUrl());
        // SH636 — the two facts a form template cannot know, and the site is where they belong.
        //
        // They were first published as `@action`/`@honeypot` data variables from `{{#form}}`, and a
        // test found that unusable within the hour: `action` has to sit on the `<form>` element,
        // which necessarily WRAPS the block, and a block's data variables do not exist outside it.
        // So the shape was wrong rather than the scope — and the right one was already implied by
        // SH632, which says the destination is the SITE's, never the page's.
        //
        // `formAction` is the endpoint and nothing else: no folder, no post-type, no choice a
        // template makes. It is the same URL for every site but for its name, so a page cannot
        // point a submission anywhere the operator did not configure — the property that kept the
        // folder out of template content in the first place.
        //
        // `formHoneypot` is read from the controller that checks it rather than spelled again here:
        // a honeypot whose rendered name and checked name drift is a spam control that silently
        // stops working, with every submission passing and nothing anywhere reporting it.
        frame.put("formAction", "/api/v2/cda/form/" + site.getName());
        frame.put("formHoneypot", ShFormSubmissionAPI.HONEYPOT_FIELD);
        // SH389 — three positioned lists, always present even when empty. A layout writes
        // `{{#each site.scripts.head}}`, and Shio never injects one: the delivery layer reads the same
        // post through the CDA, so there is no path by which a consent banner behaves one way in the
        // preview and another in production (P10, and the question SH389 was filed on).
        frame.put("scripts", scripts == null ? ShSiteScripts.of(null) : scripts);
        // SH1056 — the per-site default SH383 said it could not promise, now that a site can
        // declare one. `render.defaultLocale` stays where it is and keeps its meaning (the
        // instance's), so no template built on it breaks; this is the narrower fact a template
        // actually wants for an `hreflang` or an `x-default`, and it falls back to the same value
        // so the comparison is never against nothing.
        //
        // `locales` is the DECLARED set only, and absent on a site that has not declared one. The
        // alternative is the derived union, which is a content query — on a path that composes a
        // page per request, to tell a switcher what `{{#translations}}` already tells it per post.
        // A key that costs a query per render to be usually empty is the wrong trade (P3).
        Optional<ShSiteLocales> policy = shSiteLocaleService.declared(site);
        frame.put("defaultLocale", policy.map(ShSiteLocales::defaultLocale)
                .orElse(ShPostUnifiedWriteService.DEFAULT_LOCALE));
        policy.ifPresent(locales -> frame.put("locales", locales.enabled()));
        // SH1281 — the switcher's own words for a language, when the site set any. Read here because
        // the declared policy is already in hand; `{{#translations}}` reads it before its table.
        policy.filter(locales -> !locales.labels().isEmpty())
                .ifPresent(locales -> frame.put("localeLabels", locales.labels()));
        return ShStableMap.copyOf(frame);
    }

    /**
     * SH461 — {@code css} and {@code javascript} are <b>raw</b>, and everything else here is not.
     *
     * <h3>What HTML-escaping them actually did</h3>
     *
     * <p>The documented layout is {@code <style>{{theme.css}}</style>} — the double stash, so
     * Handlebars escaped the value. CSS decodes no entities, so a captured theme's
     * {@code src: url("/fonts/inter.woff2")} reached the browser as
     * {@code url(&quot;/fonts/inter.woff2&quot;)}: four literal characters inside the URL, an
     * invalid {@code @font-face} whatever it pointed at, and a replica rendering in a serif
     * fallback. Nothing else in a stylesheet carries {@code "}, {@code <}, {@code >} or {@code &},
     * which is why the page looked styled and only the {@code url()}s were broken — the failure
     * was invisible in proportion to how ordinary the CSS was.</p>
     *
     * <h3>Why raw is the correct answer and not the convenient one</h3>
     *
     * <p>{@code <style>} and {@code <script>} are raw-text elements: entity escaping is not the
     * encoding for that context, so what it removed was correctness and what it bought was a
     * defence against {@code </style>} — from a {@code Theme} post written by whoever also writes
     * the {@code PageLayout} that emits it, three lines below, through {@code {{{content}}}}. There
     * is no privilege boundary between the two, so there was nothing to defend. The layouts stay
     * {@code {{theme.css}}}: the fix belongs where the value is built, or every hand-written layout
     * in the product keeps the defect and only the generated one is cured.</p>
     */
    private Map<String, Object> themeFrame(ShThemeAssets theme, String themeTitle) {
        Map<String, Object> frame = new LinkedHashMap<>();
        // SH829 — escaped, unlike the two below: this one is written into an *attribute*
        // (`<style data-shio-theme="{{theme.title}}">`), which is the context that made
        // `ShRegionComposer` put a theme's title on `<body>` rather than into a comment. Null when
        // no theme resolved, so `data-shio-theme=""` is the honest empty and the digest keeps
        // `style[N]`.
        frame.put("title", theme == null || themeTitle == null || themeTitle.isBlank()
                ? null : themeTitle);
        frame.put("css", theme == null ? null : raw(theme.css()));
        frame.put("javascript", theme == null ? null : raw(theme.javascript()));
        // SH195 — the same values the `:root` block carries, dotted, for the places a custom
        // property cannot reach: an inline style, an SVG fill, an email template.
        frame.put("tokens", theme == null ? Map.of() : theme.tokens());
        return ShStableMap.copyOf(frame);
    }

    /** A value a stash must not entity-escape. Null stays null, so an absent field stays absent. */
    private static Object raw(String value) {
        return value == null ? null : new Handlebars.SafeString(value);
    }

    /**
     * A {@code Theme} post as it <b>renders</b> — its {@code TOKENS} compiled and prepended (SH850).
     *
     * <h2>One of the two emissions was written as a field read</h2>
     *
     * <p>That is SH555's sentence, and it had one more field in it. A converted site's verbatim pages
     * reach the captured stylesheet through
     * {@code {{#getRelation "/theme/captured"}}<style>{{CSS}}</style>{{/getRelation}}} — a plain
     * field read off a listing row. SH555 gave that read SH461's answer on escaping. What it left is
     * that the stylesheet a theme <em>renders as</em> is not the {@code CSS} field: it is
     * {@link ShThemeTokens#assets}, the {@code TOKENS} compiled into a {@code :root} block ahead of
     * it. The fidelity path gets that through {@code theme.css}; this one got the stored bytes.</p>
     *
     * <p>Harmless while the two fields were independent, and page-wide the moment they stopped being.
     * SH424 <b>removes</b> a lifted declaration from {@code CSS} — the source re-declaring it later
     * at equal specificity is what made every token inert — on the stated promise that the compiled
     * block re-declares it. Measured on a real replica one round apart: distinct undefined properties
     * 62 → 194, dangling {@code var()} occurrences <b>231 → 958</b>. Of 133 lifted properties, 132
     * were declared nowhere on the served page and all 132 were still read by a rule that stayed.
     * A {@code var()} with no declaration and no fallback makes its declaration invalid at
     * computed-value time, so the element takes the inherited or initial value — colour, blur and
     * animation gone across the verbatim chrome, which is the half of a mixed page a curator
     * recognises as the site.</p>
     *
     * <h2>Here rather than at either call site</h2>
     *
     * <p>Two emissions of one theme have now diverged twice, in escaping and in composition, because
     * each was written where it was needed. This is the composition, once, with the degradation and
     * the incident SH270 requires — and both callers ask it rather than owning a copy. A theme whose
     * {@code TOKENS} is unparseable renders on its CSS alone and says so; returning the field
     * untouched and staying quiet is the silent degradation that costs the diagnosis, not the page.</p>
     *
     * @param theme a {@code Theme} post, or null
     * @return its stylesheet, script and tokens, never null
     */
    public ShThemeAssets themeAssetsOf(ShPostUnified theme) {
        if (theme == null) {
            return new ShThemeAssets(null, null);
        }
        Map<String, Object> fields = fieldsOf(theme);
        String css = text(fields.get(ShSystemPostTypeAttr.CSS));
        String javascript = text(fields.get(ShSystemPostTypeAttr.JAVASCRIPT));
        try {
            return ShThemeTokens.assets(text(fields.get(ShSystemPostTypeAttr.TOKENS)), css,
                    javascript);
        } catch (JacksonException e) {
            incidentLog.record(ShAgentIncidentLog.Kind.CONTENT,
                    "id:" + (theme.getId() == null ? "?" : theme.getId()),
                    "This Theme's TOKENS is not valid JSON, so the page rendered with no design "
                            + "tokens at all: " + e.getOriginalMessage(),
                    "Rewrite TOKENS as W3C Design Tokens JSON "
                            + "({\"color\":{\"accent\":{\"$value\":\"#0b5\"}}}) with post.upsert, or "
                            + "clear the field to render on the theme's CSS alone.",
                    // SH204 — `siteIdOf` is this class's own resolver, used for the same reason the
                    // catch above uses it rather than reaching for `post.getShSite()`.
                    ShAgentIncidentFrames.of(e), siteIdOf(theme));
            return ShThemeTokens.assets(null, css, javascript);
        }
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    /**
     * A {@code Theme} post's two fields. {@code PT-THEME} carried {@code CSS} and
     * {@code JAVASCRIPT} in 2018 and both were null in the one shipped sample, so the
     * intent is clearer than the usage: one place for the stylesheet and script a whole
     * site shares, referenced by each layout's {@code THEME} field.
     */
    public record ShThemeAssets(String css, String javascript, Map<String, String> tokens) {

        /** Two-field form, for a caller with no tokens to declare (they are SH195's). */
        public ShThemeAssets(String css, String javascript) {
            this(css, javascript, Map.of());
        }
    }
}
