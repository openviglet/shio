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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.api.post.type.ShPostTypeAdminMapper;
import com.viglet.shio.api.post.type.dto.ShPostTypeDef;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import com.viglet.shio.persistence.repository.widget.ShWidgetRepository;
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;
import com.viglet.shio.utils.ShStableMap;
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
 * <p><b>Both halves of that last sentence are asserted now, and its scope is narrower than it
 * reads (SH294).</b> {@code ShRenderModelSourceTest} compares the model above with
 * {@code /import/post-types.json}, the 2018 artefact itself, and declares every divergence;
 * {@code ShImportRendersSourceTest} imports a package whose templates are keyed the 2018 way and
 * fetches the page back, so composition, {@code sh-region} and theme resolution are evidence
 * rather than a claim. What "no migration" means is the <b>content</b>: a 2018 template's
 * {@code data} is keyed {@code TITLE}/{@code HTML}/{@code JAVASCRIPT}/{@code CSS} and this
 * renderer reads those keys unchanged. It has never meant the <b>envelope</b> — SH189 records
 * that bringing the real package forward cost seven manifest field renames inferred by reading
 * the {@code ShExchange*} records, and that conversion is still a caller's job.</p>
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
            ShSystemPostType.PAGE_LAYOUT, ShSystemPostType.REGION, ShSystemPostType.THEME,
            ShSystemPostType.REDIRECT, ShSystemPostType.SITE_SCRIPTS);

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
                mapper.toDef(ensureTheme()),
                mapper.toDef(ensureRedirect()),
                mapper.toDef(ensureSiteScripts()));
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

    /**
     * SH289 — adds fields an existing render type is missing, and changes nothing else.
     *
     * <h2>Why this is a separate call and not a better {@code ensure}</h2>
     *
     * <p>{@code ensure} must stay provision-never-upsert (SH152): a startup that rewrote somebody's
     * model is worse than one that leaves a stale one. But that rule left instances created before
     * SH252 with an HTML editor on a stylesheet and instances created before SH277 with <b>no title
     * field at all</b> — where a template cannot be addressed and cannot be given a title, which is
     * a broken instance rather than a stale preference, and one whose owner had no way to find out.
     * So the drift becomes something the operator can <em>see</em> and <em>apply</em>, on their
     * decision rather than the JVM's.</p>
     *
     * <p><b>Additive only.</b> A field the type already declares is left exactly as it is —
     * including its widget, its label and its flags — so this cannot undo a curator's edit. That
     * makes the SH252 widget drift deliberately <em>out</em> of scope: changing a field is a
     * different decision from adding a missing one, and doing both under one verb would mean an
     * operator fixing a missing title also silently accepted a widget change. Removal is never
     * offered at all.</p>
     *
     * @param dryRun report what would be added and write nothing
     * @return one line per type that is missing something, naming the fields
     */
    @Transactional
    public List<String> reconcile(boolean dryRun) {
        List<String> report = new ArrayList<>();
        // Self-invocation on purpose: it bypasses the proxy, so `drift`'s readOnly=true does NOT
        // apply and the comparison runs inside this writing transaction — which is what a repair
        // needs. Called from outside (the GET), the same method gets the read-only transaction its
        // annotation asks for. Two regimes, one comparison.
        for (ShRenderDrift drifted : drift()) {
            report.add(drifted.postType() + ": " + String.join(", ", drifted.missingFields()));
            if (dryRun) {
                continue;
            }
            ShPostType live = shPostTypeRepository.findByName(drifted.postType());
            if (live == null) {
                continue;
            }
            Set<ShPostTypeAttr> attrs = live.getShPostTypeAttrs() == null
                    ? new HashSet<>() : new HashSet<>(live.getShPostTypeAttrs());
            specsFor(drifted.postType()).stream()
                    .filter(spec -> drifted.missingFields().contains(spec.name()))
                    .forEach(spec -> attrs.add(attr(spec, live)));
            live.setShPostTypeAttrs(attrs);
            shPostTypeRepository.save(live);
        }
        return List.copyOf(report);
    }

    /**
     * SH295 — which render types this instance has that are missing a field the model declares.
     *
     * <h2>Why the read exists, when {@code reconcile(dryRun)} already computed it</h2>
     *
     * <p>Because that is a {@code POST}. Learning whether an instance needs repairing required
     * issuing a <em>write</em> verb and trusting a flag not to write — so a read-only caller, which
     * is most of them, could not ask at all. The drift is a <b>fact about the instance</b>, and
     * SH289 left it visible only as {@code untitled-template}: a finding on a <em>post</em>, which
     * fires once somebody has already written a template that cannot be addressed. An instance that
     * has run {@code ensure} and written nothing looked exactly like a healthy one.</p>
     *
     * <h2>Why here and not in the context pack or a verify finding</h2>
     *
     * <p>Not a {@code verify} finding: a post-type has no SH79 address, which is the constraint that
     * shaped SH289 in the first place and has not gone away. Not the SH77 context pack either —
     * every session would pay for a line most instances do not need (P3), and the pack is read
     * before writing rather than when somebody asks whether the instance is sound. This endpoint
     * already answers "can this instance render"; drift is the other half of that same question,
     * and it costs a caller who does not ask exactly nothing.</p>
     *
     * <p>One computation, two doors: {@link #reconcile} formats <em>this</em> rather than repeating
     * the comparison, so a plan cannot describe a repair different from the one that runs (SH184's
     * rule), and the pair cannot drift the way the creation and repair paths did before SH289.</p>
     *
     * @return one entry per type that is missing something, sorted; empty when the instance is sound
     */
    @Transactional(readOnly = true)
    public List<ShRenderDrift> drift() {
        List<ShRenderDrift> drifted = new ArrayList<>();
        for (String name : NAMES) {
            ShPostType live = shPostTypeRepository.findByName(name);
            if (live == null) {
                // Absent, not drifted: `ensure` creates it, and reporting a type this instance has
                // never had as "missing a field" would send the operator to the wrong verb.
                continue;
            }
            Set<String> declared = new java.util.TreeSet<>();
            if (live.getShPostTypeAttrs() != null) {
                live.getShPostTypeAttrs().forEach(attr -> declared.add(attr.getName()));
            }
            List<String> missing = specsFor(name).stream()
                    .map(FieldSpec::name)
                    .filter(field -> !declared.contains(field))
                    .toList();
            if (!missing.isEmpty()) {
                drifted.add(new ShRenderDrift(name, missing));
            }
        }
        return List.copyOf(drifted);
    }

    /**
     * One render type that is missing fields the shipped model declares (SH295).
     *
     * @param postType     the type's name — not an SH79 address, because a post-type has none
     * @param missingFields the field names, in the model's own order, which is the order
     *                      {@code reconcile} would add them in
     */
    public record ShRenderDrift(String postType, List<String> missingFields) {
    }

    /**
     * The fields one render type is defined by — the one table {@link #ensure} and
     * {@link #reconcile} both read.
     *
     * <p>Two readers of one list rather than a creation path and a repair path that each know the
     * model: SH245's rule, and the failure it prevents is specific — a field added to {@code ensure}
     * and not to the repair list would be created on new instances and never offered to old ones,
     * which is precisely the drift this exists to close.</p>
     */
    private static List<FieldSpec> specsFor(String name) {
        return switch (name) {
            case ShSystemPostType.PAGE_LAYOUT -> List.of(
                    titleField(),
                    field(ShSystemPostTypeAttr.HTML, "HTML", ShSystemWidget.ACE_HTML, 2, true),
                    field(ShSystemPostTypeAttr.THEME, "Theme", ShSystemWidget.TEXT, 3, false),
                    field(ShSystemPostTypeAttr.JAVASCRIPT, "JavaScript",
                            ShSystemWidget.ACE_JS, 4, false),
                    // SH421 — the declared opt-out from `site-scripts-dropped`. A Check Box for
                    // `Redirect.PERMANENT`'s reason: it is a yes/no an author ticks, and the widget
                    // catalogue already has one. Not required, and unticked by default, so every
                    // layout that exists today keeps being checked — an opt-out that defaulted to
                    // "opted out" would silence the rule for the whole estate on upgrade.
                    field(ShSystemPostTypeAttr.NO_SITE_SCRIPTS, "Omits site scripts on purpose",
                            ShSystemWidget.CHECK_BOX, 5, false));
            case ShSystemPostType.REGION -> List.of(
                    titleField(),
                    field(ShSystemPostTypeAttr.HTML, "HTML", ShSystemWidget.ACE_HTML, 2, true),
                    field(ShSystemPostTypeAttr.JAVASCRIPT, "JavaScript (browser)",
                            ShSystemWidget.ACE_JS, 3, false));
            // SH252 — a stylesheet, not an HTML document. Note the limit: `ensure` never touches an
            // existing post-type (SH152), so an instance that already created its Theme keeps
            // `Ace Editor - HTML` on this field, and SH289's reconcile is deliberately additive so
            // it does not change it either — a widget change is a different decision from a missing
            // field, and bundling them would have an operator accept one by fixing the other.
            case ShSystemPostType.THEME -> List.of(
                    titleField(),
                    field(ShSystemPostTypeAttr.CSS, "CSS", ShSystemWidget.ACE_CSS, 2, false),
                    field(ShSystemPostTypeAttr.JAVASCRIPT, "JavaScript",
                            ShSystemWidget.ACE_JS, 3, false),
                    // SH195 — the JS editor for JSON, because the catalogue has no JSON widget and
                    // JSON is valid JavaScript. Named here rather than shrugged at: SH252 seeded a
                    // CSS widget for exactly this reason, and a `Ace Editor - JSON` is the same
                    // one-line fix if this field turns out to be edited by hand often.
                    field(ShSystemPostTypeAttr.TOKENS, "Design tokens",
                            ShSystemWidget.ACE_JS, 4, false));
            // SH385 — and note what is NOT here: a `FROM`. A redirect's source is the post's own
            // friendly URL, which is the whole design rather than a shortcut. Three things fall out
            // of it for free: the router already resolves a path to a post, so a redirect costs no
            // new query and no site-wide scan on a 404; SH323 makes a friendly URL site-unique, so
            // "two redirects claim one path" cannot happen; and a redirect shadowing a real page is
            // already SH99's `duplicate-url` error, which is the finding SH385 asked for and nobody
            // had to write. A `FROM` field would have needed a lookup, a uniqueness rule and a
            // shadow rule, all of which exist.
            // SH389 — one field, and it is JSON in a JavaScript editor for `Theme.TOKENS`' reason
            // (SH195). Not required: a site with no third-party scripts is the common case, and a
            // required field would make the post refuse to exist until somebody invented content.
            case ShSystemPostType.SITE_SCRIPTS -> List.of(
                    titleField(),
                    field(ShSystemPostTypeAttr.SCRIPTS, "Scripts (JSON)",
                            ShSystemWidget.ACE_JS, 2, false));
            case ShSystemPostType.REDIRECT -> List.of(
                    titleField(),
                    field(ShSystemPostTypeAttr.TO, "Redirect to", ShSystemWidget.TEXT, 2, true),
                    field(ShSystemPostTypeAttr.PERMANENT, "Permanent (301)",
                            ShSystemWidget.CHECK_BOX, 3, false));
            default -> List.of();
        };
    }

    private ShPostType ensurePageLayout() {
        return ensure(ShSystemPostType.PAGE_LAYOUT, "Page Layouts", "Page Layout",
                "An HTML skeleton with named sh-region slots");
    }

    private ShPostType ensureRegion() {
        return ensure(ShSystemPostType.REGION, "Regions", "Region",
                "HTML that renders one kind of content, slotted into a layout by name");
    }

    private ShPostType ensureTheme() {
        return ensure(ShSystemPostType.THEME, "Themes", "Theme",
                "The stylesheet and script a whole site shares");
    }

    private ShPostType ensureSiteScripts() {
        return ensure(ShSystemPostType.SITE_SCRIPTS, "Site Scripts", "Site Scripts",
                "The consent, analytics and chat scripts a whole site carries, in load order");
    }

    private ShPostType ensureRedirect() {
        return ensure(ShSystemPostType.REDIRECT, "Redirects", "Redirect",
                "One old path in, one address out — the friendly URL is the path it answers");
    }

    private ShPostType ensure(String name, String plural, String title, String description) {
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
        for (FieldSpec spec : specsFor(name)) {
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
        // findFirstByName, not findByName: the derived single-result form throws
        // IncorrectResultSizeDataAccessException on a duplicated catalogue row, which would be a
        // second 500 hiding behind the one SH325 came to fix (SH321's shape, third occurrence).
        ShWidget widget = shWidgetRepository.findFirstByName(name);
        if (widget == null) {
            throw new UnseededWidgetException(name);
        }
        return widget;
    }

    /**
     * The instance's widget catalogue is missing a row this model needs (SH325).
     *
     * <h2>Why it teaches instead of throwing</h2>
     *
     * <p>This used to be an {@code IllegalStateException} — a <b>500</b> — and it was reachable on
     * every install older than SH252: the catalogue was seeded only when it was entirely empty, and
     * only inside {@code ShOnStartup}'s first-time gate, so {@code Ace Editor - CSS} never arrived
     * and SH183's render model could not be created at all. The seeding is fixed, which makes this
     * branch nearly unreachable — and that is exactly the state SH189 warns about: nobody expected
     * the branch to be reached, so the server knew precisely which widget was missing and answered
     * with the least useful thing available.</p>
     *
     * <p>A <b>409</b> rather than a 500 because the caller can act on it: restarting the instance
     * now seeds what is absent, and the widget can be created directly if a restart is not
     * available. It names the widget, because "not seeded" without the name sends the reader
     * hunting through a field table.</p>
     */
    public static class UnseededWidgetException extends ShTeachingException {

        public UnseededWidgetException(String widgetName) {
            super(ShProblem.conflict("widget-not-seeded",
                            "Widget '" + widgetName + "' is missing from this instance's catalogue")
                    .withDetail("The render post-types (PageLayout, Region, Theme) declare a field "
                            + "backed by widget '" + widgetName + "', and no row of that name "
                            + "exists here. The widget catalogue is code-owned and seeded at "
                            + "startup; an instance created before this widget was added has been "
                            + "missing it since (SH325).")
                    .withFix("Restart the instance — the catalogue is seeded per widget on every "
                            + "startup, so the missing row is created. If a restart is not "
                            + "available, create it with POST /api/v2/widget and retry.")
                    .withExample("POST /api/v2/render/post-types"));
        }
    }

    private static FieldSpec field(String name, String label, String widget, int ordinal, boolean required) {
        return new FieldSpec(name, label, widget, ordinal, required);
    }

    /**
     * The (post-type, field) pairs a template can only emit into a raw-text element (SH657).
     *
     * <h2>Derived here because the rule is a property of this table</h2>
     *
     * <p>{@code ShRenderHelpers.RAW_TEXT_FIELDS} used to spell these by hand while stating its
     * selection rule in prose — *the fields the product declares as {@code Ace Editor -
     * CSS/JavaScript}* — and it held four of the six. {@code Theme.TOKENS} (SH195) and
     * {@code SiteScripts.SCRIPTS} (SH389) were each added here, correctly, and neither reached the
     * list. A hand-kept list that states a rule can diverge from it in silence, and the divergence
     * is invisible until a template emits one: JSON with {@code &quot;} for every key does not
     * parse. That is SH461's defect and SH555's, in the one place that already knows the answer.</p>
     *
     * <p><b>The widget is the whole rule.</b> A field declared with a code editor for CSS or
     * JavaScript is one a template can only put inside {@code <style>} or {@code <script>}, which
     * are raw-text elements where entity escaping is not the encoding — it removes correctness and
     * defends nothing, because the post is written by whoever writes the template that emits it
     * (SH461's finding, which is why the cure belongs on the value). {@code Ace Editor - HTML} is
     * deliberately not included: a {@code PageLayout}'s or {@code Region}'s {@code HTML} is a
     * <em>template</em>, rendered rather than interpolated, so it never reaches a stash at all.</p>
     *
     * <p>Every other field of every other type keeps escaping. An author is free to add a field
     * called {@code CSS} to an {@code Article}, and that one is a string in a document body with no
     * declared context — the case escaping exists for — which is why this is keyed by pair.</p>
     */
    public static Map<String, Set<String>> rawTextFields() {
        Map<String, Set<String>> byType = new LinkedHashMap<>();
        for (String name : NAMES) {
            Set<String> fields = specsFor(name).stream()
                    .filter(spec -> ShSystemWidget.ACE_CSS.equals(spec.widget())
                            || ShSystemWidget.ACE_JS.equals(spec.widget()))
                    .map(FieldSpec::name)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (!fields.isEmpty()) {
                byType.put(name, fields);
            }
        }
        // `ShStableMap`, not `Map.copyOf`: the immutable factories iterate in an order derived from
        // a per-JVM salt, which `ShByteStabilityLintTest` refuses on any observable path (P5). It
        // caught this on the first run, which is the argument for that lint in one line.
        return ShStableMap.copyOf(byType);
    }

    /**
     * The title field every template needs (SH277).
     *
     * <p>Identical on all three, because the thing it names is the same on all three: the title is
     * how a slot ({@code sh-region="NAV"}), a binding ({@code postTypeLayout}) and a layout's
     * {@code THEME} field address a template. Required, because a template with no title is
     * content nothing can reach — the strongest form of SH193's {@code unused-region}.</p>
     */
    private static FieldSpec titleField() {
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
