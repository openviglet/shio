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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.interceptor.NoRollbackRuleAttribute;
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.ShAddress;
import com.viglet.shio.api.agent.ShAddressResolver;
import com.viglet.shio.api.agent.ShAgentChangesService;
import com.viglet.shio.api.agent.ShAgentProperties;
import com.viglet.shio.api.agent.ShAgentSiteAccess;
import com.viglet.shio.api.agent.diagnostics.ShAgentIncidentLog;
import com.viglet.shio.api.agent.dto.ShAgentChanges;
import com.viglet.shio.api.agent.dto.ShAgentResolved;
import com.viglet.shio.api.agent.memory.ShAgentMemoryService;
import com.viglet.shio.api.reference.ShObjectReferenceService;
import com.viglet.shio.api.agent.verify.dto.ShAgentReport;
import com.viglet.shio.api.agent.verify.dto.ShAgentReport.Finding;
import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.post.ShFieldRules;
import com.viglet.shio.api.post.ShRequiredFields;
import com.viglet.shio.api.post.type.dto.ShPostTypeAttrDef;
import com.viglet.shio.api.post.type.dto.ShPostTypeDef;
import com.viglet.shio.post.type.ShVocabularyPostTypes;
import com.viglet.shio.post.ShTranslationStaleness;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.api.post.ShContentPerspective;
import com.viglet.shio.api.staticfile.ShFileStorageService;
import com.viglet.shio.object.ShFolderSite;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.post.type.ShPostFieldFlags;
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.render.ShAnnotatedFields;
import com.viglet.shio.render.ShPageRenderService;
import com.viglet.shio.render.ShPreviewLinks;
import com.viglet.shio.render.ShRenderPostTypes;
import com.viglet.shio.render.ShSiteAssets;
import com.viglet.shio.render.ShTemplateGraph;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.post.revision.ShPostRevisionRepository;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import com.viglet.shio.api.site.ShSiteLocaleService;
import com.viglet.shio.api.site.ShSiteLocales;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.url.ShFurlSpellings;
import com.viglet.shio.api.post.unified.ShHtmlSanitizer;
import com.viglet.shio.widget.ShSystemWidget;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Content lint (SH99) and route verification (SH100) — the agent's own correctness
 * check.
 *
 * <h2>Why this is the cheapest thing in Block K</h2>
 *
 * <p>Two of the three human round trips in a typical build exist because the agent
 * cannot see its own work. This removes the first one: it is the content-side
 * equivalent of {@code tsc}, and like {@code tsc} its value is that it is run
 * <em>before</em> anyone is asked to look. Everything it reports is a fact already in
 * the database — no rendering, no network, no delivery layer — so it costs one read
 * pass and answers in the same call.</p>
 *
 * <h2>The severity rule, stated once</h2>
 *
 * <p>An <b>error</b> is broken from a visitor's point of view: a published page with a
 * required field empty, a reference to a post that does not exist, an image whose bytes
 * are gone, two pages claiming one URL. A <b>warning</b> is something this process
 * cannot prove from inside the CMS — an empty folder may be a section still being
 * built, and a site-relative link may be a front-end route that Shio never sees. That
 * line is what makes {@code ok} worth branching on: if warnings failed builds, callers
 * would learn to ignore the exit code, and then the errors would go unread too.</p>
 *
 * <h2>What the content graph can and cannot answer (SH100, then SH141)</h2>
 *
 * <p>The {@code content} and {@code routes} groups fetch nothing. "Internal links return
 * 200" is answered against the content graph — the set of published friendly URLs, using
 * the same leading-slash tolerance the CDA's by-url lookup applies — rather than by
 * crawling a front end that may not be running, may be a different deployment, and may
 * legitimately own routes the CMS has never heard of. That is why {@code link-unresolved}
 * is a warning: it is what you emit when you are not sure.</p>
 *
 * <p><b>SH141 makes it sure.</b> The {@code delivery} group requests each published page
 * and each unresolved link from the site's own origin, through the one guarded fetch path
 * SH101 built ({@link ShAgentDeliveryProbe}), and turns "the front end does not serve this"
 * into an error that deserves the name. The legitimate exception — a front end that owns
 * generated routes — is <em>declared</em> in the SH114 memory rather than guessed at per
 * request; see {@link ShFrontEndRoutes}. And because the two rules answer the same
 * question, the unresolved links are handed to {@code delivery} instead of being reported
 * twice: a defect reported once as a maybe and once as a fact is a report that has to be
 * de-duplicated by whoever reads it.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
// SH206 — the class-level @Transactional this used to carry is now a TransactionTemplate built in
// the constructor, with the same two settings (read-only; a teaching exception does not mark
// rollback, so a caller that already had a transaction is not poisoned by our 400). It moved
// because an annotation cannot end a transaction in the middle of a method, and the SH141 network
// pass has to happen after the reads and before the report. See `verify` and `READ_PASS`.
// SH345 — see ShAgentSiteAccess.
public class ShAgentVerifyService {

    private static final List<ShPostState> BOTH_STATES =
            List.of(ShPostState.DRAFT, ShPostState.PUBLISHED);

    /** Widgets whose stored value is another post's id. */
    private static final Set<String> REFERENCE_WIDGETS =
            Set.of(ShSystemWidget.CONTENT_SELECT, ShSystemWidget.FILE);

    /**
     * The field a {@code File} post keeps its own name in — the one exception to the line above.
     *
     * <p>Named once, because two readers depend on it meaning the same thing and for a while they
     * did not: see {@link #isOwnFileName}. Spelled out rather than derived from
     * {@code ShSystemWidget.FILE.toUpperCase()}, which is how {@link #fileNameOf} used to reach it —
     * a widget's name and a field's name coinciding is a coincidence, not a rule.</p>
     */
    private static final String FILE_NAME_FIELD = "FILE";

    /** Widgets whose stored value is markup worth scanning for links and images. */
    /**
     * SH140 gave this set a second reader, so it lives in {@link ShPostLinks} — one definition of
     * "a field that can hold a link", or the write and the lint disagree about what they scanned.
     */
    private static final Set<String> MARKUP_WIDGETS = ShPostLinks.MARKUP_WIDGETS;

    /**
     * SH1092 — the rules {@code routes} runs over {@code scan.published()} alone, and so never over a
     * draft. Named once here so the note a {@code state=draft} report carries cannot drift from what
     * the loop actually skips; {@code ShVerifyDraftNoteTest} holds the list against the rule names the
     * loop emits.
     */
    static final List<String> PUBLISHED_ONLY_RULES = List.of("url-missing", "url-orphaned",
            "html-unsafe", "link-unresolved", "link-broken", "image-no-alt", "asset-no-alt",
            "asset-missing");

    /** Widgets that hold no value of their own — never "required". */
    private static final Set<String> CONTAINER_WIDGETS =
            Set.of(ShSystemWidget.TAB, ShSystemWidget.RELATOR);

    /** The public path the SH38 subsystem serves stored bytes on. */
    private static final String FILE_SOURCE_PREFIX = "/file_source/";

    /**
     * How many pages the SH516 check renders before it stops.
     *
     * <p>A bound rather than the whole site, for the reason {@code delivery} has one: this is the
     * only group whose cost is per page rather than per row, and a gate that takes a minute is a
     * gate somebody moves out of CI. Fifty is enough for the defect it exists for — the six that
     * survived a green gate were all on the first page of their site — and the cut is reported,
     * because a shortened list of defects reads exactly like a shorter list of defects.</p>
     */
    private static final int MAX_PAGES = 50;

    private final ShAgentSiteAccess siteAccess;
    private final ShFolderRepository shFolderRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShFileStorageService fileStorage;
    /** SH956 — the same allow-list the write path runs, asked of rows written before it existed. */
    private final ShHtmlSanitizer shHtmlSanitizer;
    private final ShAgentIncidentLog incidentLog;
    private final ObjectMapper objectMapper;
    private final ShAgentProperties properties;
    private final ShAddressResolver addressResolver;
    private final ShAgentChangesService shAgentChangesService;
    private final ShAgentDeliveryProbe deliveryProbe;
    /** SH773 — the shipped model, to compare this instance's copy of it against. */
    private final ShVocabularyPostTypes vocabularyPostTypes;
    /** SH1058 — which post is a translation's source, and which version of it. */
    private final ShTranslationStaleness shTranslationStaleness;
    /** SH1063 — whether this instance indexes, and where a site's content would go. */
    private final com.viglet.shio.turing.ShTuringIndexService shTuringIndexService;

    /** SH1064 — where the addresses this site used to serve are still written down. */
    private final ShPostRevisionRepository shPostRevisionRepository;

    /** SH1130 — the names behind the type ids a locale read selects, so templates can be left out. */
    private final ShPostTypeRepository shPostTypeRepository;
    /** SH1130 — a site's declared locale policy, read the one way that records an unreadable one. */
    private final ShSiteLocaleService siteLocaleService;

    /**
     * SH1034 — the reverse index, which is what makes {@code asset-unused} affordable.
     *
     * <p>The question "does anything reference this asset" was a table scan per asset before it
     * (SH1033 measured 50,002 rows examined at fifty thousand posts), so a rule asking it of a
     * whole library was one nobody could run. It is now one indexed query for the whole set.</p>
     */
    private final ShObjectReferenceService shObjectReferenceService;
    /** SH1066 — a site's form configuration, and the one resolver the submission endpoint uses. */
    private final com.viglet.shio.persistence.repository.form.ShSiteFormRepository shSiteFormRepository;
    private final com.viglet.shio.api.form.ShFormDestination shFormDestination;
    private final ShAgentMemoryService shAgentMemoryService;
    private final ShTemplateFieldLint templateFieldLint;
    private final ShTemplateGraph templateGraph;
    private final ShPageRenderService pageRenderService;
    private final ShSiteAssets siteAssets;

    /**
     * SH206 — the read pass's transaction, opened and <b>closed</b> programmatically.
     *
     * <h2>Why this is not the annotation it replaces</h2>
     *
     * <p>An annotation cannot end a transaction in the middle of a method, and that is precisely
     * what this needs: SH141's network pass has to run after every database read and before the
     * findings are assembled. With the class-level {@code @Transactional} the fetches happened
     * <em>inside</em> the read transaction, holding a pooled connection for as long as the network
     * took — bounded to 15 seconds and 50 requests, which made it safe rather than right.</p>
     *
     * <p><b>Programmatic rather than a second bean.</b> §IX.7 proposed splitting the read pass and
     * the report assembly into two beans, because {@code verify()} calling an annotated method of
     * its own is a self-invocation that never crosses Spring's proxy. That is true and a
     * {@code TransactionTemplate} does not care: it is not proxy-based, so the boundary can be
     * drawn inside one method and the block's largest class does not have to be cut in half to
     * move it.</p>
     *
     * <p><b>And it fixes the case a bean split would not have.</b> {@code ShAgentProofService}
     * calls {@code verify()} from inside its own read-only transaction, so on that path the fetches
     * were inside a transaction this class did not open. Because this template <em>completes</em>
     * before the first request rather than merely being a narrower annotation, the connection is
     * back in the pool on both paths.</p>
     *
     * <p>The two settings are the ones the annotation carried. Read-only, because nothing here
     * writes. And a teaching exception must not mark rollback: it is a 400 the caller asked for,
     * and on the proof path marking rollback-only would poison a transaction belonging to somebody
     * else.</p>
     */
    private final TransactionTemplate readPass;

    public ShAgentVerifyService(ShAgentSiteAccess siteAccess,
                                ShFolderRepository shFolderRepository,
                                ShPostUnifiedRepository shPostUnifiedRepository,
                                ShFileStorageService fileStorage,
                                ShHtmlSanitizer shHtmlSanitizer,
                                ShAgentIncidentLog incidentLog,
                                ObjectMapper objectMapper,
                                ShAgentProperties properties,
                                ShAddressResolver addressResolver,
                                ShAgentChangesService shAgentChangesService,
                                ShAgentDeliveryProbe deliveryProbe,
                                ShAgentMemoryService shAgentMemoryService,
                                ShTemplateFieldLint templateFieldLint,
                                ShTemplateGraph templateGraph,
                                ShPageRenderService pageRenderService,
                                ShSiteAssets siteAssets,
                                ShVocabularyPostTypes vocabularyPostTypes,
                                ShObjectReferenceService shObjectReferenceService,
                                ShTranslationStaleness shTranslationStaleness,
                                com.viglet.shio.turing.ShTuringIndexService shTuringIndexService,
                                ShPostRevisionRepository shPostRevisionRepository,
                                com.viglet.shio.persistence.repository.form.ShSiteFormRepository shSiteFormRepository,
                                com.viglet.shio.api.form.ShFormDestination shFormDestination,
                                ShPostTypeRepository shPostTypeRepository,
                                ShSiteLocaleService siteLocaleService,
                                PlatformTransactionManager transactionManager) {
        this.shPostRevisionRepository = shPostRevisionRepository;
        this.shPostTypeRepository = shPostTypeRepository;
        this.siteLocaleService = siteLocaleService;
        this.shSiteFormRepository = shSiteFormRepository;
        this.shFormDestination = shFormDestination;
        this.shTuringIndexService = shTuringIndexService;
        this.shTranslationStaleness = shTranslationStaleness;
        this.shObjectReferenceService = shObjectReferenceService;
        RuleBasedTransactionAttribute definition = new RuleBasedTransactionAttribute();
        definition.setReadOnly(true);
        definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        definition.setRollbackRules(List.of(new NoRollbackRuleAttribute(ShTeachingException.class)));
        this.readPass = new TransactionTemplate(transactionManager, definition);
        this.siteAccess = siteAccess;
        this.shFolderRepository = shFolderRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.fileStorage = fileStorage;
        this.shHtmlSanitizer = shHtmlSanitizer;
        this.incidentLog = incidentLog;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.addressResolver = addressResolver;
        this.shAgentChangesService = shAgentChangesService;
        this.deliveryProbe = deliveryProbe;
        this.vocabularyPostTypes = vocabularyPostTypes;
        this.shAgentMemoryService = shAgentMemoryService;
        this.templateFieldLint = templateFieldLint;
        this.templateGraph = templateGraph;
        this.pageRenderService = pageRenderService;
        this.siteAssets = siteAssets;
    }

    /** The whole instance — what CI asks for, and the shape every rule was written against. */
    public ShAgentReport verify(String site, String checks, Integer limit) {
        return verify(site, checks, limit, null, null, null);
    }

    /**
     * Runs the requested checks, over everything readable or over a subset (SH143).
     *
     * @param site    site id, name or {@code site:} address; {@code null} = every
     *                readable site
     * @param checks  comma-separated {@link ShAgentCheck} groups; {@code null} = all
     * @param limit   findings to return; {@code null} = configured default. The report
     *                always counts what it produced, so a cut is visible
     * @param folder  a {@code folder:} address or id: this folder and its descendants
     * @param address one or more comma-separated {@code post:} or {@code folder:} addresses.
     *                A folder here is the folder <b>itself</b> as a subject; use {@code folder}
     *                for the subtree (SH228)
     * @param since   an SH88 cursor: only what changed after it
     */
    public ShAgentReport verify(String site, String checks, Integer limit, String folder,
                                String address, String since) {
        return verify(site, checks, limit, folder, address, since, null);
    }

    /**
     * As above, over the rows of one state (SH990).
     *
     * <p>{@code state} narrows the <b>subjects</b> — the posts the content, route and page rules
     * report on — to that state's rows, so {@code published} lints what is live and {@code draft}
     * what a publish would put there. The indexes the cross-cutting rules read stay over both states,
     * because "published depends on unpublished" and "two posts claim one URL" are questions about
     * both rows whichever one is the subject. A revision is a version of one page and has no site to
     * lint around it, so {@code rev:<n>} is refused with the digest that proves one.</p>
     *
     * @param state the {@code state=} grammar, or {@code null} for the draft-preferred subjects
     */
    public ShAgentReport verify(String site, String checks, Integer limit, String folder,
                                String address, String since, String state) {
        ShPostState subjects = ShContentPerspective.parse(state)
                .requireRow("/agent/verify", "GET /api/v2/agent/render?address=<address>&state=rev:<n>")
                .row();
        Set<ShAgentCheck> requested = ShAgentCheck.parse(checks, deliveryProbe.enabled());
        int cap = limit == null || limit <= 0
                ? properties.getVerify().getDefaultLimit()
                : Math.min(limit, properties.getVerify().getMaxLimit());

        // SH206 — three phases, and the transaction spans only the first.
        Plan plan = readPass.execute(status ->
                read(site, checks, folder, address, since, requested, subjects));

        // The read transaction has committed. Everything below works on plain data — Strings in
        // `Target`, Strings in `Finding` — so the connection is in the pool while the network is
        // slow, which is the whole of this task. The bounds stay: SH141's cap and budget were
        // never only about the connection, and a verification call that takes four minutes is a
        // hang whether or not it holds one.
        if (!plan.targets().isEmpty()) {
            Fetches fetches = fetch(plan.targets());
            report(ShAgentCheck.DELIVERY.wireName(), plan.targets(), fetches, plan.siteNames(),
                    plan.findings(), plan.notes());
        }

        List<Finding> ordered = plan.findings().stream().sorted(ORDER).toList();
        int errors = (int) ordered.stream().filter(Finding::isError).count();
        List<Finding> capped = ordered.size() > cap ? ordered.subList(0, cap) : ordered;
        return new ShAgentReport(
                errors == 0,
                requested.stream().sorted().map(ShAgentCheck::wireName).toList(),
                plan.checked(),
                errors,
                ordered.size() - errors,
                ordered.size(),
                ordered.size() > cap,
                // Absent rather than `false` on a full pass: a flag that is always present
                // is a flag nobody reads, and this one has to be read.
                plan.partial() ? Boolean.TRUE : null,
                plan.describe(),
                plan.notes().isEmpty() ? null : List.copyOf(plan.notes()),
                List.copyOf(capped));
    }

    /**
     * Everything that touches the database, in one transaction that ends when this returns.
     *
     * <p>SH206 — this is the old body of {@link #verify}, minus the network pass and the findings
     * it implies. Two things moved and both were free. <b>{@code delivery} now only gathers</b>:
     * it resolves the origins and the target URLs, which is the part that reads entities, and
     * hands back plain Strings. And <b>{@code pages} runs before that gathering completes</b>
     * rather than after the fetch — the two never depended on each other, and every finding is
     * sorted by {@link #ORDER} at the end, so no report changes shape.</p>
     *
     * <p>What this returns holds <b>no entity</b>. That is the property that makes the phase split
     * correct rather than merely tidy: {@code Scan} is full of {@code ShPostUnified} and
     * {@code ShSite}, all of which detach the moment this transaction commits, so a
     * {@code Plan} carrying one would turn a slow front end into a
     * {@code LazyInitializationException}.</p>
     */
    private Plan read(String site, String checks, String folder, String address, String since,
                      Set<ShAgentCheck> requested, ShPostState subjects) {
        List<ShSite> sites = siteAccess.readable(site);
        ShAgentVerifyScope scope = scope(sites, site, folder, address, since);
        Scan scan = scan(sites, scope, subjects);
        List<Finding> findings = new ArrayList<>();
        List<String> notes = new ArrayList<>(scope.notes());
        if (subjects != null) {
            // SH990 — said, because a clean report over the published rows is not a clean report
            // over the draft a publish is about to put live, and the two read alike otherwise.
            notes.add("state=" + subjects.name().toLowerCase(java.util.Locale.ROOT)
                    + ": the rules reported on " + subjects.name() + " rows only; the indexes they "
                    + "check against still read both states");
            // SH1092 — and what it did NOT run. The route and markup rules read published rows by
            // design (a draft's broken link is work in progress), so on a draft they never ran,
            // and "errors 0" would otherwise read as a verdict on links and images it never saw.
            if (subjects == ShPostState.DRAFT && requested.contains(ShAgentCheck.ROUTES)) {
                notes.add("state=draft: " + String.join(", ", PUBLISHED_ONLY_RULES) + " read published "
                        + "rows only and did not run on these drafts, so a clean report says nothing "
                        + "about their links, images or markup; /agent/render inventories a draft's "
                        + "images and links, or verify again after publishing");
            }
        }
        boolean proving = requested.contains(ShAgentCheck.DELIVERY);

        if (requested.contains(ShAgentCheck.CONTENT)) {
            content(scan, findings);
            untranslated(scan, scope, findings);
            staleTranslations(scan, findings);
        }
        // Routes runs before delivery because it decides what delivery has to adjudicate:
        // a link that resolves against the content graph needs no request at all, and the
        // ones that do not are exactly the population SH141 exists to settle.
        List<Unresolved> unproven = new ArrayList<>();
        ShFrontEndRoutes declared = declaredRoutes(site);
        if (requested.contains(ShAgentCheck.ROUTES)) {
            routes(scan, findings, declared, proving ? unproven : null, notes, scope.partial());
            unroutableIndex(scan, findings);
        }
        Delivery delivery = proving
                ? delivery(scan, findings, unproven, explicitlyAsked(checks))
                : Delivery.NONE;
        // SH516 — after routes, because it asks the strictly larger question about the same site:
        // routes reads the content graph, this reads the page that graph produces.
        if (requested.contains(ShAgentCheck.PAGE)) {
            Set<String> loaded = new HashSet<>();
            pages(scan, findings, notes, declared, loaded);
            // SH1268 — an asset a rendered page loads is used, whatever field does or does not
            // name it: a theme's @font-face is the common case, and the index cannot see CSS.
            findings.removeIf(finding -> "asset-unused".equals(finding.rule())
                    && loaded.contains(finding.address().substring("id:".length())));
        } else if (findings.stream().anyMatch(finding -> "asset-unused".equals(finding.rule()))) {
            notes.add("asset-unused counted File and Content Select references only; the page "
                    + "check did not run, so an asset a theme or layout loads (a font, a CSS "
                    + "background) may be reported here. Add checks=page to count what pages load.");
        }
        // SH1055 — over the same pages the check above renders. Its own group rather than a rule
        // inside `page`, because a team running a publish gate asks for accessibility by name and
        // because the cost is a caller's decision: on a site with a thousand pages this is a
        // thousand renders, which is exactly what a group is for in this vocabulary.
        if (requested.contains(ShAgentCheck.A11Y)) {
            accessibility(scan, findings, notes);
        }
        if (requested.contains(ShAgentCheck.DIAGNOSTICS)) {
            diagnostics(findings);
        }
        return new Plan(findings, notes, delivery.targets(), delivery.siteNames(),
                scan.checked(), scope.partial(), scope.describe());
    }

    /**
     * The read pass's whole output — plain data, so it outlives the transaction (SH206).
     *
     * @param findings mutable on purpose: the delivery report appends to it after the fetch, which
     *                 is the one thing that happens between here and the response
     * @param targets  the URLs to prove; empty when {@code delivery} was not requested, which is
     *                 also how {@link #verify} knows to skip the network entirely
     */
    private record Plan(List<Finding> findings, List<String> notes, List<Target> targets,
                        Map<String, String> siteNames, int checked, boolean partial,
                        String describe) {
    }

    /** What the delivery gather resolved, before anything is fetched (SH206). */
    private record Delivery(List<Target> targets, Map<String, String> siteNames) {

        static final Delivery NONE = new Delivery(List.of(), Map.of());
    }

    /**
     * Whether the caller named {@code delivery} rather than receiving it in the default set.
     *
     * <p>The difference decides one thing only, and it matters: an <b>explicit</b> request
     * with no origin to fetch is a teaching 400, because the caller asked for a proof and
     * would otherwise get an {@code ok} that proved nothing. The same situation reached
     * <em>implicitly</em> — an operator enabled fetching, so the group joined the defaults —
     * must never break a call that did not ask for it, so it degrades to a warning.</p>
     */
    private static boolean explicitlyAsked(String checks) {
        return checks != null && !checks.isBlank() && !"all".equalsIgnoreCase(checks.trim())
                && java.util.Arrays.stream(checks.split(","))
                        .anyMatch(token -> ShAgentCheck.DELIVERY.wireName()
                                .equalsIgnoreCase(token.trim()));
    }

    /** The front-end routes this site declares in the SH114 memory, or nothing. */
    private ShFrontEndRoutes declaredRoutes(String site) {
        try {
            return ShFrontEndRoutes.of(shAgentMemoryService.notes(site));
        } catch (ShTeachingException e) {
            // A `site=` the memory scope cannot resolve is not a lint failure: the site
            // allow-list has already been applied by `siteAccess.readable(site)` above, so
            // getting here means the selector shape differs, and the honest answer is
            // "nothing declared" rather than a 4xx from a rule that only silences findings.
            return ShFrontEndRoutes.none();
        }
    }

    /**
     * Deterministic finding order: check group, then errors before warnings, then rule,
     * address and field. Two runs over unchanged content produce byte-identical reports
     * (P5), which is what makes a report diffable in a PR.
     */
    private static final Comparator<Finding> ORDER = Comparator
            .comparing(Finding::check, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(finding -> finding.isError() ? 0 : 1)
            .thenComparing(Finding::rule, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Finding::address, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Finding::field, Comparator.nullsLast(Comparator.naturalOrder()));

    // --- SH143: what this run covers ----------------------------------------

    /**
     * Resolves the scope params into the subject rows.
     *
     * <p>The three params <b>union</b> rather than intersect: each names something the caller
     * wants looked at, and an intersection would answer a question nobody asked ("posts in
     * this folder that also changed since that cursor"). A scope that resolves to nothing is
     * still a scope — it reports zero findings over zero objects and says so, which is the
     * honest answer to "check this folder" for an empty folder.</p>
     */
    private ShAgentVerifyScope scope(List<ShSite> sites, String site, String folder,
                                     String address, String since) {
        if (isBlank(folder) && isBlank(address) && isBlank(since)) {
            // SH635 — `site` blank is the full pass, and the only run that may report a post
            // belonging to no site. The three scope params below never can: each names something
            // inside a site, so an orphan is by construction not among what the caller asked for.
            return ShAgentVerifyScope.full(readable(sites, isBlank(site)));
        }
        Map<String, ShPostUnified> rows = new LinkedHashMap<>();
        Set<String> folderIds = new LinkedHashSet<>();
        List<String> parts = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        if (!isBlank(folder)) {
            ShFolder root = folderOf(folder);
            List<ShFolder> subtree = subtree(root);
            subtree.forEach(each -> folderIds.add(each.getId()));
            for (ShFolder each : subtree) {
                keep(rows, shPostUnifiedRepository.findByShFolderAndStateIn(each, BOTH_STATES));
            }
            parts.add("folder " + addressResolver.addressOf(root)
                    + (subtree.size() > 1 ? " +" + (subtree.size() - 1) + " subfolder(s)" : ""));
        }
        if (!isBlank(address)) {
            List<String> addresses = split(address);
            for (String each : addresses) {
                // SH228 — a `folder:` address is a legal subject here, and it means something
                // `folder=` deliberately does not: **this folder**, not this folder and its
                // descendants. The distinction is the whole reason the two params both exist
                // after this change. A write proof names the folders a run created, and a
                // just-created folder's subtree is empty by construction — scoping to the
                // subtree would have made the proof's reach depend on what happened to be
                // underneath, which is not what "prove what I just wrote" asks.
                ShAgentResolved resolved = addressResolver.resolve(each);
                if ("folder".equals(resolved.kind())) {
                    folderIds.add(resolved.id());
                } else {
                    keep(rows, shPostUnifiedRepository.findById(resolved.id()));
                }
            }
            parts.add(addresses.size() + " address(es)");
        }
        if (!isBlank(since)) {
            parts.add(sinceRows(site, since, rows, notes));
        }

        rows.values().forEach(row -> folderIds.add(folderIdOf(row)));
        folderIds.remove(null);
        return new ShAgentVerifyScope(List.copyOf(rows.values()), folderIds,
                String.join(" + ", parts), notes);
    }

    /**
     * The rows behind an SH88 cursor.
     *
     * <p>Reuses the change feed rather than reading the audit directly, so "what changed" has
     * one definition: the same cursor semantics, the same site allow-list, and the same
     * held-back-entry rule the feed already documents. A feed that had more entries than
     * fitted is a note, not a silent cut — the report is already {@code partial}, and this is
     * the second reason it is.</p>
     */
    private String sinceRows(String site, String since, Map<String, ShPostUnified> rows,
                             List<String> notes) {
        ShAgentChanges changes = shAgentChangesService.changes(site, since,
                properties.getVerify().getMaxLimit(), true);
        int resolved = 0;
        for (ShAgentChanges.Change change : changes.changes()) {
            if (change.id() == null) {
                continue;
            }
            List<ShPostUnified> found = shPostUnifiedRepository.findById(change.id());
            // A DELETE entry names an object that is gone; nothing to lint, and reporting it
            // as missing would turn a completed deletion into a defect.
            if (!found.isEmpty()) {
                keep(rows, found);
                resolved++;
            }
        }
        if (changes.more()) {
            notes.add("The change feed had more entries than this run read — re-run from "
                    + changes.cursor() + " to cover the rest.");
        }
        return "changed since " + since + " (" + resolved + " post(s))";
    }

    private void keep(Map<String, ShPostUnified> rows, List<ShPostUnified> found) {
        for (ShPostUnified row : found) {
            rows.put(row.getId() + "|" + row.getState(), row);
        }
    }

    private static List<String> split(String raw) {
        return java.util.Arrays.stream(raw.split(",")).map(String::trim)
                .filter(part -> !part.isEmpty()).toList();
    }

    /** A {@code folder:} address or a bare id, as the folder itself. */
    private ShFolder folderOf(String raw) {
        ShAgentResolved resolved = addressResolver.resolve(raw);
        return shFolderRepository.findById(resolved.id())
                .orElseThrow(() -> new UnknownFolderScopeException(raw));
    }

    private List<ShFolder> subtree(ShFolder root) {
        List<ShFolder> all = new ArrayList<>();
        java.util.Deque<ShFolder> pending = new java.util.ArrayDeque<>(List.of(root));
        while (!pending.isEmpty()) {
            ShFolder current = pending.pop();
            all.add(current);
            pending.addAll(shFolderRepository.findByParentFolder(current));
        }
        return all;
    }

    private static String folderIdOf(ShPostUnified row) {
        return row.getShFolder() == null ? null : row.getShFolder().getId();
    }

    /**
     * Every readable row, both states — plus the site-less ones <b>on a full pass only</b>.
     *
     * <p>SH635 — this is the rule {@link #folders} states one method up and has always applied:
     * <em>"an orphan belongs to no site, so no scope can name it, and a scoped run that swept them
     * all in would report content the caller did not ask about — while a full pass would still
     * catch it."</em> Posts did not get it. A caller asking {@code verify?site=blog} was told about
     * every site-less post in the instance, as an <b>error</b>, against an address in no site of
     * theirs and with a {@code fix} they cannot carry out — the post is not theirs to move.</p>
     *
     * <p>Nothing stops being reported: a run with no {@code site=} still asks for exactly these
     * rows, which is the only way they are visible at all (see
     * {@code ShPostUnifiedRepository#findWithoutSite}). What changes is who is told.</p>
     *
     * @param fullPass whether the caller named no site, so the answer covers the whole instance
     */
    private List<ShPostUnified> readable(List<ShSite> sites, boolean fullPass) {
        List<ShPostUnified> rows = new ArrayList<>();
        for (ShSite each : sites) {
            rows.addAll(shPostUnifiedRepository.findByShSite_IdAndStateIn(each.getId(), BOTH_STATES));
        }
        if (fullPass) {
            // Site-less posts are nobody's rows, which is exactly why they need asking for.
            rows.addAll(shPostUnifiedRepository.findWithoutSite(BOTH_STATES));
        }
        return rows;
    }

    // --- the read pass -------------------------------------------------------

    /**
     * One pass over everything the checks need, so no rule re-queries.
     *
     * <p>Both states are loaded rather than draft-preferred rows, because two rules turn
     * on the difference: a duplicate URL is only a live defect between {@code PUBLISHED}
     * rows, and "published depends on unpublished" cannot be asked at all without
     * knowing which ids have a published row.</p>
     *
     * <p>The <b>subjects</b> come from the scope; the {@code allIds} / {@code publishedIds} /
     * {@code publishedByUrl} indexes are always built for every readable site, because the
     * rules that use them ask cross-cutting questions a subset cannot answer (SH143). They are
     * projections — id, url, state — so they stay cheap at the size where scoping starts to
     * matter.</p>
     */
    private Scan scan(List<ShSite> sites, ShAgentVerifyScope scope, ShPostState subjects) {
        Map<String, ShSite> siteById = new LinkedHashMap<>();
        Set<String> allIds = new HashSet<>();
        Set<String> publishedIds = new HashSet<>();
        Map<String, Map<String, List<String>>> publishedByUrl = new HashMap<>();
        Map<String, Map<String, List<String>>> claimantsByUrl = new HashMap<>();
        Map<String, String> publishedLocales = new HashMap<>();
        for (ShSite each : sites) {
            siteById.put(each.getId(), each);
            Map<String, List<String>> byUrl = new HashMap<>();
            Map<String, List<String>> claimants = new HashMap<>();
            for (Object[] row : shPostUnifiedRepository.findUrlIndexBySiteId(each.getId(), BOTH_STATES)) {
                String id = (String) row[0];
                String furl = (String) row[1];
                allIds.add(id);
                // SH720 — distinct POST IDS per url, over both states. A post's DRAFT and PUBLISHED
                // rows share a furl by design (SH81), so the id is what makes two rows of one post
                // one claimant; keying by row is what forced the old check to look at published
                // rows alone, and what made it blind to the pair a race leaves.
                if (!isBlank(furl)) {
                    List<String> ids = claimants.computeIfAbsent(normalized(furl),
                            ignored -> new ArrayList<>());
                    if (!ids.contains(id)) {
                        ids.add(id);
                    }
                    // Locale is read for every claimant now, not only published ones: a finding
                    // about a draft pair has the same reason to say "these are two translations".
                    if (!isBlank((String) row[3])) {
                        publishedLocales.putIfAbsent(id, (String) row[3]);
                    }
                }
                if (row[2] == ShPostState.PUBLISHED) {
                    publishedIds.add(id);
                    if (!isBlank(furl)) {
                        byUrl.computeIfAbsent(normalized(furl), ignored -> new ArrayList<>()).add(id);
                    }
                    if (!isBlank((String) row[3])) {
                        publishedLocales.put(id, (String) row[3]);
                    }
                }
            }
            publishedByUrl.put(each.getId(), byUrl);
            claimantsByUrl.put(each.getId(), claimants);
        }

        Map<String, ShPostUnified> draftPreferred = new LinkedHashMap<>();
        for (ShPostUnified row : scope.rows()) {
            allIds.add(row.getId());
            if (row.getState() == ShPostState.PUBLISHED) {
                publishedIds.add(row.getId());
            }
            ShPostUnified kept = draftPreferred.get(row.getId());
            if (kept == null || row.getState() == ShPostState.DRAFT) {
                draftPreferred.put(row.getId(), row);
            }
        }
        List<ShPostUnified> published = scope.rows().stream()
                .filter(row -> row.getState() == ShPostState.PUBLISHED)
                .sorted(Comparator.comparing(ShPostUnified::getId))
                .toList();
        // SH990 — a state names the subjects outright; without one they are draft-preferred.
        List<ShPostUnified> posts = (subjects == null
                ? draftPreferred.values().stream()
                : scope.rows().stream().filter(row -> row.getState() == subjects))
                .sorted(Comparator.comparing(ShPostUnified::getId))
                .toList();
        return new Scan(siteById, posts, published, allIds, publishedIds, publishedByUrl,
                claimantsByUrl, publishedLocales,
                folders(siteById.keySet(), scope.folderIds()),
                shFolderRepository.findParentFolderIds(),
                shPostUnifiedRepository.findFolderIdsWithPosts(),
                // SH1106 — filled as pages are read: every page of one type on one site resolves to
                // the same templates, so a thousand pages read their layout once.
                new HashMap<>());
    }

    /**
     * The folders this run reports on: every one under a readable site, or the scope's.
     *
     * <p>The site is resolved with {@link ShFolderSite} — only a root folder carries
     * {@code shSite} (SH128), so a direct read would classify every nested folder as an
     * orphan and bury the report in false errors.</p>
     *
     * <p>An <b>orphan</b> folder is reported on a full pass only. It belongs to no site, so no
     * scope can name it, and a scoped run that swept them all in would report content the
     * caller did not ask about — while a full pass would still catch it.</p>
     */
    private List<Scanned> folders(Set<String> readableSiteIds, Set<String> only) {
        List<Scanned> scanned = new ArrayList<>();
        for (ShFolder folder : shFolderRepository.findAll()) {
            if (only != null && !only.contains(folder.getId())) {
                continue;
            }
            ShSite owner = ShFolderSite.of(folder);
            if (owner == null) {
                if (only == null) {
                    scanned.add(new Scanned(folder, null));
                }
            } else if (readableSiteIds.contains(owner.getId())) {
                scanned.add(new Scanned(folder, owner));
            }
        }
        return scanned.stream().sorted(Comparator.comparing(entry -> entry.folder().getId())).toList();
    }

    /** The one canonical form both the duplicate-URL rule and link resolution compare on. */
    private static String normalized(String furl) {
        return ShAddress.normalizePath(furl).toLowerCase(java.util.Locale.ROOT);
    }

    // --- SH99: content ------------------------------------------------------

    private void content(Scan scan, List<Finding> findings) {
        String check = ShAgentCheck.CONTENT.wireName();
        for (ShPostUnified post : scan.posts()) {
            String address = address(post);
            if (post.getShSite() == null) {
                findings.add(Finding.error(check, "orphan-post", address, null,
                        "This post belongs to no site, so nothing can reach it — not the "
                                + "console, not the CDA, not an address.",
                        "Move it into a site with a post.move op (POST /api/v2/agent/batch), "
                                + "or delete it if it was created by mistake."));
                continue;
            }
            JsonNode data = data(post);
            for (ShPostTypeAttr attr : declared(post.getShPostType())) {
                fieldFindings(check, post, address, data, attr, scan, findings);
            }
            undeclaredFields(check, post, address, data, findings);
            constraintFindings(check, post, address, data, findings);
            assetAltFindings(check, post, address, data, findings);
        }
        duplicateUrls(check, scan, findings);
        folderFindings(check, scan, findings);
        templateFields(check, scan, findings);
        unusedTemplates(check, scan, findings);
        duplicateTemplateTitles(check, scan, findings);
        untitledTemplates(check, scan, findings);
        missingThemes(check, scan, findings);
        formDestinations(check, scan, findings);
        // SH572 — the relation-literal twin of the rule above, run beside it because the two
        // answer one question about two spellings of the same reference.
        danglingRelations(check, scan, findings);
        unpublishedTemplates(check, scan, findings);
        redirectTargets(check, scan, findings);
        headConventions(check, scan, findings);
        siteScripts(check, scan, findings);
        vocabularyDrift(check, scan, findings);
        unusedAssets(check, scan, findings);
        siteLocales(check, scan, findings);
    }

    /**
     * The post types whose rows are not content anybody writes in a language (SH1130).
     *
     * <p>Every row gets a locale on write, so a layout, a theme, an uploaded file and a redirect each
     * carry {@code en} whatever the site is in. Counting them would tell a site declaring only
     * {@code pt-br} that its templates are undeclared content, and would let a site's English
     * layouts stand in for the English pages it never wrote.</p>
     */
    private static final Set<String> NOT_WRITTEN_IN_A_LANGUAGE = Set.of(
            ShSystemPostType.REGION, ShSystemPostType.THEME, ShSystemPostType.PAGE_LAYOUT,
            ShSystemPostType.SITE_SCRIPTS, ShSystemPostType.FILE, ShSystemPostType.REDIRECT);

    /**
     * SH1130 — a language a site promises and never wrote, and the mirror of it.
     *
     * <h2>Why the route cannot say it</h2>
     *
     * <p>Since SH1057 a locale segment selects the translation and a missing one falls back along
     * the site's chain. That is the right behaviour and it is what hides this: a site declaring
     * {@code ["pt-br","en"]} with nothing written in {@code pt-br} answers every {@code /pt-br/} URL
     * with the English page and a 200. The switcher offers it, SH459's route check accepts it, and
     * the reader gets English under a Portuguese address.</p>
     *
     * <h2>Two findings, both warnings</h2>
     *
     * <p>{@code locale-declared-unwritten}: a declared locale no published content is written in.
     * {@code locale-undeclared-content}: published content in a locale the policy does not enable,
     * which no switcher links to and no locale segment reaches. Warnings, because either can be the
     * middle of somebody's work — a language announced ahead of its pages — and SH99's line is that
     * {@code ok} goes false only for what is provably wrong.</p>
     *
     * <p>Silent on an undeclared site, whose languages are by definition the ones its content is in.
     * Published rows only, because the reader of a {@code /pt-br/} URL is served published content.
     * One query for every declared site in the run, addressed to {@code site:<name>} like the other
     * site-level rules: detection is the whole site's, whatever the scope's subjects are.</p>
     */
    private void siteLocales(String check, Scan scan, List<Finding> findings) {
        Map<String, ShSiteLocales> declared = new LinkedHashMap<>();
        for (ShSite site : scan.siteById().values()) {
            // `declared` costs no query and records an incident for a policy that will not parse,
            // which is then not a declaration to hold content against.
            siteLocaleService.declared(site).ifPresent(policy -> declared.put(site.getId(), policy));
        }
        if (declared.isEmpty()) {
            return;
        }
        Map<String, Map<String, Set<String>>> typesByLocale = new HashMap<>();
        Set<String> typeIds = new HashSet<>();
        for (Object[] row : shPostUnifiedRepository.findDistinctLocaleTypesBySiteIdIn(
                declared.keySet(), ShPostState.PUBLISHED)) {
            String locale = isBlank((String) row[1])
                    ? ShPostUnifiedWriteService.DEFAULT_LOCALE : ((String) row[1]).trim();
            String typeId = (String) row[2];
            typesByLocale.computeIfAbsent((String) row[0], id -> new java.util.TreeMap<>())
                    .computeIfAbsent(locale, key -> new HashSet<>()).add(typeId);
            if (typeId != null) {
                typeIds.add(typeId);
            }
        }
        Set<String> notContent = new HashSet<>();
        for (ShPostType type : shPostTypeRepository.findAllById(typeIds)) {
            if (NOT_WRITTEN_IN_A_LANGUAGE.contains(type.getName())) {
                notContent.add(type.getId());
            }
        }
        for (Map.Entry<String, ShSiteLocales> entry : declared.entrySet()) {
            ShSite site = scan.siteById().get(entry.getKey());
            ShSiteLocales policy = entry.getValue();
            Set<String> written = new java.util.TreeSet<>();
            typesByLocale.getOrDefault(entry.getKey(), Map.of()).forEach((locale, types) -> {
                if (types.stream().anyMatch(type -> type != null && !notContent.contains(type))) {
                    written.add(locale);
                }
            });
            String address = "site:" + site.getName();
            for (String locale : policy.enabled()) {
                if (written.contains(locale)) {
                    continue;
                }
                // What a reader of that locale is actually handed: the first language along the
                // chain that has anything published, which is exactly what the route serves.
                String served = policy.chain(locale).stream()
                        .filter(written::contains).findFirst().orElse(null);
                findings.add(Finding.warning(check, "locale-declared-unwritten", address, null,
                        "'" + site.getName() + "' declares '" + locale + "' and has published nothing "
                                + "written in it, so every /" + locale + "/ URL falls back along the "
                                + "site's chain"
                                + (served == null ? " and finds nothing published at all."
                                        : " and serves the " + served + " page under a " + locale
                                                + " address.")
                                + " A language switcher offers it all the same.",
                        "Publish the " + locale + " translations (post.upsert with \"locale\":\""
                                + locale + "\", then post.publish), or drop '" + locale + "' from the "
                                + "site's locales.enabled with site.upsert until they exist."));
            }
            for (String locale : written) {
                if (policy.offers(locale)) {
                    continue;
                }
                findings.add(Finding.warning(check, "locale-undeclared-content", address, null,
                        "'" + site.getName() + "' has published content written in '" + locale
                                + "', which its locale policy does not enable ("
                                + String.join(", ", policy.enabled()) + "). No switcher links to it "
                                + "and no locale segment reaches it.",
                        "Add '" + locale + "' to the site's locales.enabled with site.upsert, or move "
                                + "that content to a language the site offers."));
            }
        }
    }

    /**
     * SH389 — the site's script list, and the layouts that drop it.
     *
     * <h2>Why the third rule is the one that matters</h2>
     *
     * <p>The first two are ordinary content defects: a {@code SCRIPTS} field that is not a JSON array
     * (the render carries none of them, silently but for the CONTENT incident), and an entry with
     * neither {@code src} nor {@code inline} (skipped rather than emitted as
     * {@code <script src="">}, which re-requests the page itself in some browsers).</p>
     *
     * <p>The third is the task's own complaint turned into an instrument. Moving the declarations to
     * one ordered list fixes the duplication; it does <b>not</b> stop a layout from failing to emit
     * them, and a layout that does not is a set of pages with no consent banner that renders perfectly.
     * So: a site that has scripts, and a <em>bound</em> layout whose HTML never names
     * {@code site.scripts}, is a warning against that layout with the page count in it — SH388's
     * shape, for its reason. A layout nothing renders through is skipped, because
     * {@code unused-layout} is already that finding.</p>
     *
     * <h2>And a site with two of them</h2>
     *
     * <p>The post is found by type, so two is ambiguous and the render carries nothing (SH381's rule
     * against a fabricated answer). Reported as an <b>error</b>: unlike the layout rule this is not a
     * matter of taste — the site has scripts and every page is silently rendering without them.</p>
     */
    private void siteScripts(String check, Scan scan, List<Finding> findings) {
        for (ShSite site : scan.siteById().values()) {
            List<ShPostUnified> posts = scan.posts().stream()
                    .filter(post -> ShSystemPostType.SITE_SCRIPTS.equals(
                            post.getShPostType() == null ? null : post.getShPostType().getName()))
                    // ShFolderSite-exempt: a post carries its own site column (SH128 is about folders).
                    .filter(post -> post.getShSite() != null
                            && site.getId().equals(post.getShSite().getId()))
                    .toList();
            if (posts.isEmpty()) {
                continue;
            }
            if (posts.size() > 1) {
                findings.add(Finding.error(check, "site-scripts-ambiguous", "site:" + site.getName(),
                        null,
                        "This site has " + posts.size() + " SiteScripts posts ("
                                + posts.stream().map(ShAgentVerifyService::address).sorted()
                                        .collect(java.util.stream.Collectors.joining(", "))
                                + "), so the renderer cannot tell which list is the site's and every "
                                + "page renders with none of them.",
                        "Keep one and delete the others. There is one list per site on purpose — the "
                                + "whole point of SH389 is that the load order is a property of one "
                                + "list rather than of each copy."));
                continue;
            }
            ShPostUnified post = posts.getFirst();
            int entries = scriptFindings(check, post, findings);
            if (entries > 0) {
                droppedScripts(check, site, scan, entries, findings);
            }
        }
    }

    /** The list itself: not an array, or an entry that would emit nothing. Returns how many are live. */
    private int scriptFindings(String check, ShPostUnified post, List<Finding> findings) {
        String raw = fieldValue(post, ShSystemPostTypeAttr.SCRIPTS);
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(raw);
        } catch (JacksonException e) {
            findings.add(Finding.error(check, "site-scripts-invalid", address(post),
                    ShSystemPostTypeAttr.SCRIPTS,
                    "This site's SCRIPTS is not valid JSON (" + e.getOriginalMessage()
                            + "), so every page renders with none of the site's scripts.",
                    "Rewrite it as a JSON array of {\"src\" or \"inline\", \"position\", "
                            + "\"defer\", \"category\"}. A consent banner that silently stopped "
                            + "loading is the worst shape this defect takes, which is why it is an "
                            + "error rather than a warning."));
            return 0;
        }
        if (root == null || !root.isArray()) {
            findings.add(Finding.error(check, "site-scripts-invalid", address(post),
                    ShSystemPostTypeAttr.SCRIPTS,
                    "This site's SCRIPTS is valid JSON but not an array, so the renderer reads no "
                            + "scripts from it at all.",
                    "The value is an ordered array — the order IS the load order, which is the "
                            + "property a per-layout copy could not keep."));
            return 0;
        }
        int live = 0;
        int index = 0;
        for (JsonNode entry : root) {
            boolean empty = !entry.isObject()
                    || (text(entry, "src").isEmpty() && text(entry, "inline").isEmpty());
            if (empty) {
                findings.add(Finding.error(check, "site-scripts-entry-empty", address(post),
                        ShSystemPostTypeAttr.SCRIPTS,
                        "Entry " + index + " of SCRIPTS has neither `src` nor `inline`, so it is "
                                + "skipped — the renderer will not emit `<script src=\"\">`, which "
                                + "re-requests the page itself in some browsers.",
                        "Give it a `src` or an `inline` body, or remove the entry. Every other entry "
                                + "still loads, so this is one missing script rather than a broken "
                                + "site."));
            } else {
                live += 1;
            }
            index += 1;
        }
        return live;
    }

    /** A bound layout whose HTML never names the site's list — the task's complaint, instrumented. */
    private void droppedScripts(String check, ShSite site, Scan scan, int entries,
                                List<Finding> findings) {
        for (ShPostUnified layout : scan.posts()) {
            String typeName = layout.getShPostType() == null
                    ? null : layout.getShPostType().getName();
            // ShFolderSite-exempt: a post carries its own site column (SH128 is about folders).
            ShSite owner = layout.getShSite();
            if (!ShSystemPostType.PAGE_LAYOUT.equals(typeName) || owner == null
                    || !site.getId().equals(owner.getId())) {
                continue;
            }
            String html = fieldValue(layout, ShSystemPostTypeAttr.HTML);
            if (html != null && html.contains("site.scripts")) {
                continue;
            }
            // SH421 — the author has said this layout omits them on purpose. A transactional email
            // template, a print sheet, an AMP variant or a partner embed must NOT load a chat
            // widget, and before the field existed each of them was warned about forever. SH388's
            // lesson is the whole reason this is worth a field: a lint over templates that fires on
            // correct work is one authors learn to skip, and the true finding goes unread with it.
            if (ShPostFieldFlags.isTrue(fieldValue(layout, ShSystemPostTypeAttr.NO_SITE_SCRIPTS))) {
                continue;
            }
            var impact = templateGraph.impactOf(layout);
            if (impact.isEmpty() || !impact.get().isUsed()) {
                continue;
            }
            findings.add(Finding.warning(check, "site-scripts-dropped", address(layout),
                    ShSystemPostTypeAttr.HTML,
                    "This site declares " + entries + " script(s) and this layout never emits them, "
                            + "so the " + impact.get().pages() + " page(s) it renders carry none — "
                            + "which for a consent banner is a compliance defect that renders "
                            + "perfectly.",
                    "Emit them where they belong: `{{#each site.scripts.head}}<script src=\"{{src}}\""
                            + " {{#if defer}}defer{{/if}}></script>{{/each}}` in the head, and the "
                            + "same for site.scripts.bodyStart and site.scripts.bodyEnd. Shio never "
                            + "injects a script a layout did not ask for (P10), which is why this is "
                            + "reported rather than fixed. If this layout omits them deliberately — "
                            + "a transactional email, a print sheet, an AMP variant, a partner embed "
                            + "— tick `" + ShSystemPostTypeAttr.NO_SITE_SCRIPTS + "` on it and this "
                            + "stops asking (SH421)."));
        }
    }

    /** One JSON field's text, trimmed — the shape {@link ShSiteScripts} reads. */
    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isValueNode() && !value.isNull() ? value.asString().trim() : "";
    }

    /**
     * SH388 — a {@code PageLayout}'s {@code <head>}, read off the stored HTML.
     *
     * <h2>The design question the task was filed on, and why the answer changed the shape</h2>
     *
     * <p>SH388 asked whether these are {@code verify} findings at all or the delivery layer's (P10).
     * Both readings were about the wrong subject: the head a <em>visitor</em> receives is composed by
     * the front end over the CDA, which Shio does not own — but every defect the task named is a
     * property of the <b>layout</b>, which is content Shio stores. So it is a template lint, and it
     * says nothing about production.</p>
     *
     * <p>That also made the expensive design unnecessary. The idea proposed comparing <em>rendered</em>
     * heads across a site, which is the per-page cost SH312 and SH384 measured and which reports the
     * symptom — sixty pages sharing one description — sixty times. The cause is one line of one layout
     * and needs no render: a {@code <title>} that interpolates nothing has the same value on every page
     * bound to it, <b>by construction</b>. One scan of a field already in hand, one finding, and the
     * page count in the message rather than in the count — SH280's shape, for its reason.</p>
     *
     * <h2>The count is what makes a warning actionable</h2>
     *
     * <p>"This layout has a static title" is a fact; "and 63 published pages render through it" is the
     * reason to care. {@code impactOf} already computes it (SH193/SH288) from the graph this pass has
     * built anyway, so it costs nothing here — and a layout <b>nothing</b> renders through is skipped
     * entirely, because {@code unused-layout} is already the finding for that and a head defect on a
     * template in progress is not one.</p>
     */
    private void headConventions(String check, Scan scan, List<Finding> findings) {
        for (ShPostUnified post : scan.posts()) {
            String typeName = post.getShPostType() == null ? null : post.getShPostType().getName();
            if (!ShSystemPostType.PAGE_LAYOUT.equals(typeName)) {
                continue;
            }
            String html = fieldValue(post, ShSystemPostTypeAttr.HTML);
            List<ShHeadConventions.Finding> head = ShHeadConventions.of(html);
            if (head.isEmpty()) {
                continue;
            }
            var impact = templateGraph.impactOf(post);
            if (impact.isEmpty() || !impact.get().isUsed()) {
                // Nothing renders through it, so `unused-layout` is the finding and this would be a
                // second one about the same unfinished template.
                continue;
            }
            int pages = impact.get().pages();
            for (ShHeadConventions.Finding found : head) {
                String message = "This layout's <head> " + found.detail() + ", and " + pages
                        + " page(s) render through it — so the defect is site-wide while every page "
                        + "renders perfectly.";
                findings.add(found.error()
                        ? Finding.error(check, found.rule(), address(post),
                                ShSystemPostTypeAttr.HTML, message, headFix(found.rule()))
                        : Finding.warning(check, found.rule(), address(post),
                                ShSystemPostTypeAttr.HTML, message, headFix(found.rule())));
            }
        }
    }

    /** What to do about one head rule — the model already carries what each of them needs (SH337). */
    private String headFix(String rule) {
        return switch (rule) {
            case "head-static-title" -> "Interpolate the page's own title: `<title>{{post.title}}"
                    + " — {{site.name}}</title>`. A warning rather than an error because a one-page "
                    + "site legitimately has one title.";
            case "head-no-title" -> "Add `<title>{{post.title}}</title>`. A page with no title is "
                    + "one nothing can list, link or search for.";
            case "head-static-description" -> "Interpolate it: `<meta name=\"description\" "
                    + "content=\"{{post.summary}}\">`. Sixty pages sharing one description is the "
                    + "defect a conversion produces by writing the head once.";
            case "head-no-description" -> "Add `<meta name=\"description\" "
                    + "content=\"{{post.summary}}\">`, which is the field SH183 puts on every row.";
            case "head-no-canonical" -> "Add `<link rel=\"canonical\" href=\"{{post.canonical}}\">`. "
                    + "SH337 put that value on the model precisely because a template cannot compose "
                    + "it: a friendly URL is not guaranteed to lead with a slash.";
            case "head-relative-og-image" -> "Make it absolute with `{{site.baseUrl}}`. A crawler "
                    + "resolves a relative og:image against ITS own origin, so the tag renders "
                    + "perfectly and the card is blank — which is why this is an error.";
            case "head-noindex" -> "Remove it. A noindex carried over from a preview-shaped "
                    + "conversion makes the whole site invisible, which is why this is an error. Note "
                    + "that the preview route already sends `X-Robots-Tag: noindex` on every response, "
                    + "so a draft is not indexable whatever the layout says — the tag in the markup "
                    + "buys nothing and costs the live site.";
            default -> "See ShHeadConventions for what this rule checks.";
        };
    }

    /**
     * SH385 — a {@code Redirect} whose {@code TO} names nothing.
     *
     * <h2>Why this rule and not the other one SH385 asked for</h2>
     *
     * <p>The task named two findings: a redirect shadowing a real page, and a target that resolves to
     * nothing. The <b>first needed no rule at all</b> — a redirect's source is its own friendly URL, so
     * two posts claiming one path is already SH99's {@code duplicate-url} error, reported with both
     * addresses. That is the strongest form of "needs no new mechanism" this block has produced, and it
     * is a consequence of the field that was <em>not</em> added.</p>
     *
     * <h2>Why an error rather than a warning</h2>
     *
     * <p>Unlike a half-translated site (SH383) there is no work-in-progress reading of this: the post
     * exists to send a reader somewhere, and it names somewhere that is not there. The reader gets a
     * 404 <em>after</em> a redirect, which is worse than a plain 404 because the address they end at is
     * not the one they asked for and nothing tells them why.</p>
     *
     * <p>An <b>absolute</b> target is not checked, and that is deliberate rather than lazy: verifying
     * it would mean fetching somebody else's URL from inside the instance, which SH268 settled and
     * SH101's request-forgery argument settled again. {@code shio verify --delivery} is where a claim
     * about a URL outside this instance belongs.</p>
     */
    private void redirectTargets(String check, Scan scan, List<Finding> findings) {
        for (ShPostUnified post : scan.posts()) {
            String typeName = post.getShPostType() == null ? null : post.getShPostType().getName();
            if (!ShSystemPostType.REDIRECT.equals(typeName) || post.getShSite() == null) {
                continue;
            }
            String to = fieldValue(post, ShSystemPostTypeAttr.TO);
            if (to == null || to.isBlank()) {
                // An empty TO renders as an ordinary page rather than as a redirect, so the defect is
                // already `required-field-empty` on the same post — one field, one finding.
                continue;
            }
            if (to.startsWith("http://") || to.startsWith("https://")) {
                continue;
            }
            String furl = normalized(to);
            if (furlsIn(scan, post.getShSite().getId()).contains(furl)) {
                continue;
            }
            findings.add(Finding.error(check, "redirect-target-missing", address(post),
                    ShSystemPostTypeAttr.TO,
                    "This redirect points at '" + to + "', and site '" + post.getShSite().getName()
                            + "' has no page there — so an old link lands on a 404 at an address "
                            + "nobody asked for, which is harder to diagnose than a plain 404.",
                    "Point TO at a friendly URL this site holds (GET /api/v2/agent/find?site="
                            + post.getShSite().getName() + " lists them), use an absolute URL if the "
                            + "page now lives on another host, or delete the redirect. One exact "
                            + "path in, one address out — there are no patterns here."));
        }
    }

    /**
     * Every friendly URL one site holds, in either state.
     *
     * <p>Built once per call rather than scanned per redirect — a site with fifty redirects would
     * otherwise walk the whole post list fifty times, which is the shape SH312 measured and disliked
     * one layer along. Both states, because a redirect pointing at a page somebody has not published
     * yet is work in progress and not a broken target; {@code unpublished-dependency} is the rule for
     * that and it is a different finding.</p>
     */
    private Set<String> furlsIn(Scan scan, String siteId) {
        Set<String> furls = new HashSet<>();
        for (ShPostUnified each : scan.posts()) {
            // ShFolderSite-exempt: a post carries its own site column (SH128's invariant is about
            // folders), and every row here came from `scan.posts()`, which reads posts.
            ShSite site = each.getShSite();
            if (site != null && siteId.equals(site.getId())) {
                furls.add(normalized(each.getFurl()));
            }
        }
        return furls;
    }

    /** One field's value as text, or null. */
    private String fieldValue(ShPostUnified post, String field) {
        JsonNode value = data(post).get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    /**
     * SH383 — a translated page missing one of the site's languages.
     *
     * <h2>A warning, and never a refusal</h2>
     *
     * <p>A half-translated site is normal work in progress, so nothing here may block anything: the
     * write side already refuses only a {@code translationOf} that resolves to <b>nothing</b> (SH348),
     * which is a different claim — that is a broken link, this is unfinished work.</p>
     *
     * <h2>The site's languages are what its own content says, and there is no setting</h2>
     *
     * <p>Deliberately derived rather than configured. A per-site locale list would be a new setting for
     * every site to get wrong, and the union of the locales its posts already carry is the same answer
     * with nothing to maintain — a monolingual site has one locale and this rule is silent on it by
     * construction rather than by a flag somebody has to remember to leave off.</p>
     *
     * <h2>Only a group that is already translated, which is the difference between useful and unusable</h2>
     *
     * <p>The naive rule — warn for every post missing any of the site's locales — is the one to avoid,
     * and it is not a matter of taste: on a site with 500 English pages and one Portuguese page it
     * emits 499 findings and the report is worthless. A page nobody has ever translated is not a
     * <i>missing translation</i>, it is an untranslated page, and no evidence exists that anyone
     * intended otherwise. A group with <b>two or more</b> locales is that evidence: somebody declared
     * this content translated, so a language the rest of the site has and this group does not is a real
     * gap.</p>
     *
     * <h2>Silent on a scoped run, rather than a warning</h2>
     *
     * <p>The finding is an <b>absence</b>, and SH143's rule bites harder for those than for anything
     * else here: a run scoped to one folder cannot see the site's locale set, so every group in it
     * would look like it was missing whatever the scope excluded. {@code translation-target-missing}
     * downgrades to a warning in that case because its evidence is still a present thing; there is no
     * equivalent downgrade for this one, so it does not run.</p>
     */
    private void untranslated(Scan scan, ShAgentVerifyScope scope, List<Finding> findings) {
        if (scope.partial()) {
            return;
        }
        String check = ShAgentCheck.CONTENT.wireName();
        Map<String, Set<String>> localesBySite = new LinkedHashMap<>();
        Map<String, Map<String, ShPostUnified>> byGroup = new LinkedHashMap<>();
        for (ShPostUnified post : scan.posts()) {
            ShSite site = post.getShSite();
            if (site == null) {
                continue;
            }
            // SH1264 — a layout, a theme or an uploaded file carries `en` because every row gets a
            // locale on write, and counting it made `en` a language every translated page lacked.
            // `siteLocales` already answers this with SH1130's set; the two rules read one set now.
            if (post.getShPostType() != null
                    && NOT_WRITTEN_IN_A_LANGUAGE.contains(post.getShPostType().getName())) {
                continue;
            }
            String locale = ShPostUnifiedWriteService.localeOf(post);
            localesBySite.computeIfAbsent(site.getId(), ignored -> new java.util.TreeSet<>()).add(locale);
            String group = post.getTranslationGroupId();
            if (group == null || group.isBlank()) {
                continue;
            }
            // First writer wins per locale, and `scan.posts()` is already id-ordered, so which post
            // represents a locale is stable across runs (P5) — the address is in the finding.
            byGroup.computeIfAbsent(group, ignored -> new LinkedHashMap<>()).putIfAbsent(locale, post);
        }

        for (Map.Entry<String, Map<String, ShPostUnified>> entry : byGroup.entrySet()) {
            Map<String, ShPostUnified> group = entry.getValue();
            if (group.size() < 2) {
                continue;
            }
            ShPostUnified anchor = anchorOf(entry.getKey(), group);
            Set<String> siteLocales = localesBySite.getOrDefault(anchor.getShSite().getId(), Set.of());
            Set<String> missing = new java.util.TreeSet<>(siteLocales);
            missing.removeAll(group.keySet());
            if (missing.isEmpty()) {
                continue;
            }
            findings.add(Finding.warning(check, "translation-missing", address(anchor), null,
                    "This content is translated into " + String.join(", ", new java.util.TreeSet<>(group.keySet()))
                            + " but not into " + String.join(", ", missing)
                            + ", which the rest of this site has.",
                    "Create the missing translation with a post.upsert op naming this page in "
                            + "`translationOf` and the locale in `locale` (POST /api/v2/agent/batch). "
                            + "A warning rather than an error: a half-translated site is work in "
                            + "progress, not a defect."));
        }
    }

    /**
     * SH1063 — a site that publishes into a search index it has no route into.
     *
     * <h2>The failure that raises nothing</h2>
     *
     * <p>Indexing is switched on, a page is published, the listener builds its document — and the
     * site maps to no search site, so the push is never made. Nothing errors, nothing is recorded,
     * and the answer to "is this page findable" is no with no trace anywhere. That is the shape
     * this whole task is about, and it is the one a report can state in one line.</p>
     *
     * <p>Silent when indexing is off, because then no site routes anywhere and that is a decision
     * rather than a defect; and silent for a site with nothing published, because a site nobody
     * has published into is not yet missing from an index. A <b>warning</b>: the content is
     * correct and the configuration is not, which is a thing to fix rather than a thing to
     * refuse.</p>
     */
    private void unroutableIndex(Scan scan, List<Finding> findings) {
        if (!shTuringIndexService.active()) {
            return;
        }
        String check = ShAgentCheck.ROUTES.wireName();
        Set<String> publishing = new java.util.LinkedHashSet<>();
        for (ShPostUnified post : scan.published()) {
            if (post.getShSite() != null) {
                publishing.add(post.getShSite().getId());
            }
        }
        for (String siteId : publishing) {
            ShSite site = scan.siteById().get(siteId);
            if (site == null || shTuringIndexService.snSiteFor(site.getName()).isPresent()) {
                continue;
            }
            findings.add(Finding.warning(check, "index-unroutable",
                    ShAddress.site(site.getName()).toString(), null,
                    "Indexing is on and site '" + site.getName() + "' maps to no search site, so "
                            + "nothing it publishes is sent and nothing says so.",
                    "Add this site to `shio.turing.sites` (or set `shio.turing.default-site`), or "
                            + "turn indexing off for this instance. A warning rather than an "
                            + "error: the content is right and the routing is not."));
        }
    }

    /**
     * SH1058 — a translation whose source has changed since it was written.
     *
     * <p>{@code translation-missing} asks about absence, so a site could be fully translated, then
     * wrong, and nothing could tell. A translation now records the digest of the source it was
     * written against ({@code source_digest}), and this compares it with the source's digest as the
     * source reads now — a string comparison, not a diff.</p>
     *
     * <p>A <b>warning</b>, as {@code translation-missing} is: a translation that lags its source is
     * work in progress. It runs on a scoped run too, unlike that rule, because its evidence is a
     * present thing — the translation in scope and its source, read by id when the scope left it
     * out. A translation with no digest (written before there was one) is not known to be stale and
     * stays silent; so does one whose source is gone, which is SH348's broken link, not this.</p>
     */
    private void staleTranslations(Scan scan, List<Finding> findings) {
        String check = ShAgentCheck.CONTENT.wireName();
        Map<String, ShPostUnified> latest = new LinkedHashMap<>();
        for (ShPostUnified post : scan.posts()) {
            latest.merge(post.getId(), post,
                    (kept, other) -> kept.getState() == ShPostState.DRAFT ? kept : other);
        }
        Map<String, java.util.Optional<String>> sourceDigests = new HashMap<>();
        for (ShPostUnified post : latest.values()) {
            if (!ShTranslationStaleness.isTranslation(post) || post.getSourceDigest() == null) {
                continue;
            }
            String sourceId = post.getTranslationGroupId();
            java.util.Optional<String> current = sourceDigests.computeIfAbsent(sourceId,
                    id -> currentDigest(latest.get(id), id));
            if (current.isEmpty() || current.get().equals(post.getSourceDigest())) {
                continue;
            }
            ShPostUnified source = latest.get(sourceId);
            findings.add(Finding.warning(check, "translation-stale", address(post), null,
                    "The page this translates"
                            + (source == null ? "" : " (" + address(source) + ")")
                            + " has changed since this translation was written.",
                    "Bring this translation up to date and save it; its next edit records the source "
                            + "as it reads then. A warning rather than an error: a translation that "
                            + "lags its source is work in progress."));
        }
    }

    /**
     * The source's digest as it reads now, or empty when there is no source.
     *
     * <p>Off the scanned row when the run already read it, and by id when the scope left it out —
     * both through {@link ShTranslationStaleness}, which owns what "the source" and "as it reads
     * now" mean, so a scoped run and a whole-site run cannot answer differently.</p>
     */
    private java.util.Optional<String> currentDigest(ShPostUnified inScan, String sourceId) {
        return inScan != null
                ? java.util.Optional.of(shTranslationStaleness.digestOf(inScan))
                : shTranslationStaleness.sourceDigest(sourceId);
    }

    /**
     * The post a translation finding is hung on: the group's <b>anchor</b> (SH383).
     *
     * <h2>Why this is not "whichever we saw first"</h2>
     *
     * <p>It was, and a test caught it: the first draft took the head of a locale-keyed map filled in
     * {@code scan.posts()} order, which is id order — deterministic, and arbitrary. The finding then
     * landed on {@code /pt/sobre} rather than on {@code /about}, which reads as "the Portuguese page is
     * missing German" and points a reader at the wrong file to fix.</p>
     *
     * <p>SH53's column is the answer and needs no extra read: a standalone or master post carries its
     * <b>own id</b> as the translation group, and every translation inherits it — so the anchor is the
     * member whose id is the group id, which is the same "the anchor names nothing" rule SH348 relies
     * on. A group whose anchor has been deleted falls back to the lowest id: still deterministic, and
     * still a member of the group rather than nothing.</p>
     */
    private ShPostUnified anchorOf(String group, Map<String, ShPostUnified> members) {
        return members.values().stream()
                .filter(post -> group.equals(post.getId()))
                .findFirst()
                .orElseGet(() -> members.values().stream()
                        .min(Comparator.comparing(ShPostUnified::getId))
                        .orElseThrow());
    }

    /**
     * SH289 — a template with no title, which nothing can address.
     *
     * <h2>Why this is the reportable form of a model defect</h2>
     *
     * <p>The cause is usually the instance's own model: a render post-type created before SH277
     * declares no {@code isTitle} field, so every template written through {@code post.upsert}
     * lands with an empty title column. That defect lives on the <em>post-type</em>, which has no
     * SH79 address a finding can be hung on and no place in SH143's subject scoping — while its
     * <b>symptom</b> is a post, addressable and scoped like everything else here. So the rule
     * reports the symptom, which is also the more useful thing to be told: this template exists,
     * nothing can reach it, and here is why.</p>
     *
     * <p>An <b>error</b>, unlike the unused-template warnings beside it. A region waiting for its
     * layout is work in progress; a template with no title cannot be named by any {@code sh-region}
     * slot or {@code postTypeLayout} binding, whatever anybody intended — and it is invisible to
     * {@code unused-region} too, because {@link ShTemplateGraph} skips a blank title. It is
     * content nobody can reach and, until now, nothing reported.</p>
     */
    private void untitledTemplates(String check, Scan scan, List<Finding> findings) {
        Set<String> reported = new java.util.LinkedHashSet<>();
        for (ShPostUnified post : scan.posts()) {
            String typeName = post.getShPostType() == null ? null : post.getShPostType().getName();
            if (typeName == null || !ShRenderPostTypes.NAMES.contains(typeName)
                    || (post.getTitle() != null && !post.getTitle().isBlank())
                    || !reported.add(post.getId())) {
                continue;
            }
            findings.add(Finding.error(check, "untitled-template", address(post), null,
                    "This " + typeName + " has no title, so nothing can address it: a layout "
                            + "reaches a region with `sh-region=\"TITLE\"` and a site binds a "
                            + "layout by title in `postTypeLayout`. It is invisible to the "
                            + "unused-template checks for the same reason.",
                    "Give it a title by writing the TITLE field (post.upsert on "
                            + "POST /api/v2/agent/batch). If the write is refused as an undeclared "
                            + "field, this instance's " + typeName + " predates the title field: "
                            + "add a `render.provision` op with `data.reconcile:true` to add it "
                            + "(or POST /api/v2/render/post-types/reconcile), which changes "
                            + "nothing else. GET /api/v2/render/post-types/drift answers the same "
                            + "question without writing (SH295)."));
        }
    }

    /**
     * SH298 - the sentence that says <em>what happened</em>, when the name is used next door.
     *
     * <p>A template in the wrong site is the case the fix line gets wrong: "add `sh-region` to the
     * layout that should show it" names a layout the reader cannot find, because it is in another
     * site. Naming that site turns "something is wrong" into "here is what happened", which is what
     * decides whether a warning is acted on or added to an SH142 baseline.</p>
     *
     * <p>Scoped to the run's own sites, never the instance: a finding naming a site the caller
     * cannot read would be a disclosure, and an unhelpful one.</p>
     *
     * @return a leading-space sentence, or {@code ""} - appended, so a finding with nothing to add
     *         stays byte-identical to what it was
     */
    private String elsewhere(Scan scan, ShSite site, String kind, String title) {
        List<ShTemplateGraph.ShTemplateUse> others =
                templateGraph.usedElsewhere(scan.siteById().values(), kind, title, site);
        if (others.isEmpty()) {
            return "";
        }
        // SH308 - the layout's own address, because "used in Blog" tells the reader what happened
        // and leaves them to find it. A site name is not an address and a layout title is not one
        // either; the post is (SH79), and it is the thing to open.
        StringBuilder where = new StringBuilder();
        for (ShTemplateGraph.ShTemplateUse use : others) {
            where.append(where.isEmpty() ? "" : ", ")
                    .append("'").append(use.layout().getTitle()).append("' in '")
                    .append(use.siteName()).append("' (").append(address(use.layout())).append(")");
        }
        return " That name is used by " + where
                + " - if this one belongs there, move it (post.move) rather than re-creating it.";
    }

    /**
     * SH297 — a layout naming a theme this site does not have.
     *
     * <p>An <b>error</b>, unlike the unused-template warnings: the layout says which theme it uses
     * and there is no such post, in either state, which is a claim that turned out false rather
     * than a guess about whether somebody is still working. Reported against the <em>layout</em>,
     * because that is the post to edit — the theme does not exist, so it has no address.</p>
     *
     * <p>The render marks the same defect on the page (SH297's other half); this is how somebody
     * who is not looking at the page finds out.</p>
     */
    private void missingThemes(String check, Scan scan, List<Finding> findings) {
        for (ShSite site : scan.siteById().values()) {
            Map<String, String> broken = templateGraph.missingThemes(site);
            if (broken.isEmpty()) {
                continue;
            }
            for (ShPostUnified post : scan.posts()) {
                String typeName = post.getShPostType() == null
                        ? null : post.getShPostType().getName();
                String named = post.getTitle() == null ? null : broken.get(post.getTitle());
                if (!ShSystemPostType.PAGE_LAYOUT.equals(typeName) || named == null
                        || post.getShSite() == null
                        || !site.getId().equals(post.getShSite().getId())) {
                    continue;
                }
                findings.add(Finding.error(check, "missing-theme", address(post),
                        ShSystemPostTypeAttr.THEME,
                        "This layout's THEME names '" + named + "', and '" + site.getName()
                                + "' has no Theme post with that title — so the page renders with "
                                + "no stylesheet at all, which looks like a CSS fault rather than "
                                + "a missing post.",
                        "Create a Theme titled '" + named + "' (post.upsert on "
                                + "POST /api/v2/agent/batch), correct the THEME field to a theme "
                                + "that exists, or clear it if this layout brings its own styles. "
                                + "A theme is addressed by title, and matched exactly."));
            }
        }
    }

    /**
     * SH1066 — a site whose form sends its submissions to a folder that is not there.
     *
     * <p>An <b>error</b>: the submission endpoint refuses every post to such a site with a 500 that
     * only the visitor sees, so the form looks broken on the page and the site reports nothing.
     * Checked for an <em>enabled</em> form only — a switched-off configuration is an operator still
     * setting it up, and naming its folder as missing would be a finding about work in progress.
     * Resolved by {@code ShFormDestination}, the endpoint's own resolver, so the two cannot come to
     * disagree about whether the destination exists.</p>
     */
    private void formDestinations(String check, Scan scan, List<Finding> findings) {
        for (ShSite site : scan.siteById().values()) {
            shSiteFormRepository.findFirstBySiteIdOrderByIdAsc(site.getId())
                    .filter(com.viglet.shio.persistence.model.form.ShSiteForm::isEnabled)
                    .filter(form -> shFormDestination.resolve(site, form.getFolderUrl()) == null)
                    .ifPresent(form -> findings.add(Finding.error(check, "form-destination-missing",
                            ShAddress.site(site.getName()).toString(), null,
                            "Site '" + site.getName() + "' accepts form submissions into '"
                                    + form.getFolderUrl() + "', and no folder has that path, so "
                                    + "every submission is refused and the visitor is the only one "
                                    + "who sees it.",
                            "Create the folder (folder.upsert on POST /api/v2/agent/batch), or "
                                    + "point the form at one that exists: PUT /api/v2/site-form/"
                                    + form.getId() + " with folderUrl. A submission never creates a "
                                    + "folder of its own.")));
        }
    }

    /**
     * SH572 — a template relating an address this site has not got.
     *
     * <p>An <b>error</b>, the same severity as {@link #missingThemes} and for the same reason: the
     * template makes a claim about a specific address and the claim is false. The two are twins that
     * had different treatment — SH297 checks a {@code THEME} <em>field</em> and nothing checked a
     * {@code getRelation} <em>literal</em>, so {@code /theme/capturd} rendered 200, carried no
     * captured CSS, and reported clean. {@code getRelation} renders its else branch when nothing
     * resolves and deliberately does not mark the absence (SH247), which is right and is exactly why
     * the page cannot be the thing that tells you.</p>
     *
     * <p>Reported against the template that carries the literal, because that is the post to edit —
     * the target does not exist, so it has no address of its own.</p>
     */
    private void danglingRelations(String check, Scan scan, List<Finding> findings) {
        for (ShSite site : scan.siteById().values()) {
            Map<String, SortedSet<String>> broken = templateGraph.danglingRelations(site);
            if (broken.isEmpty()) {
                continue;
            }
            // One row per id before anything is reported. A post has a DRAFT row and a PUBLISHED row
            // that share an id, so iterating `scan.posts()` directly reports a published template's
            // broken relation TWICE — one defect, two findings, and a count that disagrees with the
            // number of things to fix. It is also order-dependent, because whether the site has a
            // published row at all depends on what the test or the operator did first, which is how
            // it shows up as a suite that is red once and green next time.
            Map<String, ShPostUnified> byId = new LinkedHashMap<>();
            for (ShPostUnified post : scan.posts()) {
                // ShFolderSite-exempt: a post carries its own site column (SH128 is about folders).
                ShSite owner = post.getShSite();
                if (owner != null && site.getId().equals(owner.getId())) {
                    byId.putIfAbsent(post.getId(), post);
                }
            }
            for (ShPostUnified post : byId.values()) {
                SortedSet<String> addresses = broken.get(post.getId());
                if (addresses == null) {
                    continue;
                }
                for (String address : addresses) {
                    findings.add(Finding.error(check, "dangling-relation", address(post),
                            ShSystemPostTypeAttr.HTML,
                            "This template relates '" + address + "' and '" + site.getName()
                                    + "' has no post at that URL, in either state — so the block "
                                    + "renders its else branch on every page that assembles from "
                                    + "it, silently. A theme related this way carries no CSS and "
                                    + "the page still answers 200.",
                            "Correct the address to a post that exists, or create it (post.upsert "
                                    + "on POST /api/v2/agent/batch). A relation literal is a "
                                    + "friendly URL within this site, matched exactly apart from "
                                    + "the leading slash — GET /api/v2/agent/find lists what is "
                                    + "there."));
                }
            }
        }
    }

    /**
     * SH280 — a bound layout with no published row, where published pages depend on it.
     *
     * <p>Since SH278 a PUBLISHED render reads only published rows at every level, so an unpublished
     * layout stops a published page rendering. The refusal says so now, but a refusal only reaches
     * whoever asked for the page; this is how the operator finds it first. A <b>warning</b>, because
     * the SH99 line is that {@code ok} is false only for what the CMS can prove is wrong and the
     * remedy here is one publish away — but reported per layout, naming the types whose pages are
     * affected, because "publish something" is not actionable and "publish this" is.</p>
     *
     * <p>Site-level rather than subject-addressed, so it is emitted once per site in the scan rather
     * than once per affected page — the number of stranded pages is in the message, not in the count.</p>
     */
    private void unpublishedTemplates(String check, Scan scan, List<Finding> findings) {
        for (ShSite site : scan.siteById().values()) {
            for (Map.Entry<String, Set<String>> stranded
                    : templateGraph.unpublishedBoundLayouts(site).entrySet()) {
                findings.add(Finding.warning(check, "unpublished-layout",
                        "site:" + site.getName(), null,
                        "Layout '" + stranded.getKey() + "' has never been published, and '"
                                + site.getName() + "' has published pages bound to it ("
                                + String.join(", ", stranded.getValue()) + ") — a published render "
                                + "reads only published rows, so those pages cannot render.",
                        "Publish the layout with a post.publish op (POST /api/v2/agent/batch). A site "
                                + "built with post.upsert has draft templates until somebody "
                                + "publishes them, because publishing is a separate act (P7)."));
            }
            // SH281 — the two levels that degrade rather than refusing. A skipped slot and a missing
            // stylesheet are both silent in the delivered page, so a report is the only way anybody
            // learns; the layout level has a 409 for the same situation (SH280).
            for (Map.Entry<String, Set<String>> stranded
                    : templateGraph.unpublishedDependencies(site).entrySet()) {
                findings.add(Finding.warning(check, "unpublished-template",
                        "site:" + site.getName(), null,
                        stranded.getKey() + " has never been published, and published pages in '"
                                + site.getName() + "' render through the layout(s) that need it ("
                                + String.join(", ", stranded.getValue()) + "). A published render "
                                + "reads only published rows, so a region is skipped and a theme "
                                + "yields no CSS — silently, in both cases.",
                        "Publish it with a post.publish op (POST /api/v2/agent/batch). Unlike a "
                                + "missing layout, neither of these refuses the render: the page is "
                                + "served incomplete or unstyled, which is why it is reported here."));
            }
        }
    }

    /**
     * SH263 — two templates in one site claiming the same title.
     *
     * <p>A layout reaches a region with {@code sh-region="NAME"} and a site binds a post-type to a
     * layout by title, so the renderer resolves by title and takes the <b>first</b> match. Two
     * {@code Region} posts called {@code NAV} therefore render whichever row comes back first —
     * intermittently, with nothing reported anywhere, and SH193's graph counts the slot once. SH202's
     * duplicate-URL defect one namespace over: the identity is a title, and nothing enforced
     * uniqueness because nothing had noticed a title <em>was</em> an identity.</p>
     *
     * <p>An <b>error</b>, unlike the unused-template warnings beside it: an unused region may be work
     * in progress, while two templates competing for one name is provably wrong whatever anybody
     * intended, and which page renders correctly is undefined.</p>
     *
     * <p>Detection is instance-wide within the site and reporting is subject-scoped (SH143): a scoped
     * run that saw only one of the two would answer that the name is unique, which is the worst false
     * green there is.</p>
     */
    private void duplicateTemplateTitles(String check, Scan scan, List<Finding> findings) {
        Set<String> inScope = scan.posts().stream()
                .map(ShPostUnified::getId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        for (ShSite site : scan.siteById().values()) {
            for (Map.Entry<String, List<String>> claimed
                    : templateGraph.duplicateTitlesIn(site).entrySet()) {
                for (ShPostUnified post : postsById(scan, claimed.getValue(), inScope)) {
                    findings.add(Finding.error(check, "duplicate-template-title", address(post), null,
                            claimed.getKey() + " is claimed by " + claimed.getValue().size()
                                    + " templates in '" + site.getName() + "' — a layout reaches a "
                                    + "region by title and a binding names a layout by title, so "
                                    + "which one renders is undefined.",
                            "Rename all but one of them (post.upsert with a different title on "
                                    + "POST /api/v2/agent/batch), or delete the copy. A title is an "
                                    + "identity here, the way a friendly URL is for a page."));
                }
            }
        }
    }

    /** The scanned posts among these ids — so the finding is reported once, against a subject. */
    private static List<ShPostUnified> postsById(Scan scan, Collection<String> ids,
                                                 Set<String> inScope) {
        List<ShPostUnified> out = new ArrayList<>();
        Set<String> seen = new java.util.LinkedHashSet<>();
        for (ShPostUnified post : scan.posts()) {
            if (ids.contains(post.getId()) && inScope.contains(post.getId())
                    && seen.add(post.getId())) {
                out.add(post);
            }
        }
        return out;
    }

    /**
     * SH193 — a Region no layout slots, or a PageLayout no post-type binds.
     *
     * <p>The inverse of the blast-radius question and the half of it that is a <b>defect</b>: this
     * is content nobody can reach, and nothing could tell it apart from content that is merely not
     * visited. It is a <b>warning</b>, deliberately — an unused template is often work in progress,
     * and SH99's rule is that {@code ok} is false only for what the CMS can prove is wrong. A
     * region waiting for the layout that will slot it is not wrong.</p>
     *
     * <p>Scoped like every other rule here: only templates among this run's subjects (SH143).</p>
     */
    private void unusedTemplates(String check, Scan scan, List<Finding> findings) {
        Map<String, List<ShPostUnified>> bySite = new LinkedHashMap<>();
        for (ShPostUnified post : scan.posts()) {
            ShSite site = post.getShSite();
            if (site != null && site.getId() != null) {
                bySite.computeIfAbsent(site.getId(), key -> new ArrayList<>()).add(post);
            }
        }
        for (Map.Entry<String, List<ShPostUnified>> entry : bySite.entrySet()) {
            ShSite site = scan.siteById().get(entry.getKey());
            if (site == null) {
                continue;
            }
            ShTemplateGraph.ShUnusedTemplates unused = templateGraph.unusedIn(site, entry.getValue());
            // SH298 - the site binds nothing, so *every* template in it is unused and the defect
            // is one missing postTypeLayout entry rather than N faulty templates. Said in each
            // finding, because a reader acting on one must not be sent to fix the same root cause
            // N times.
            String rootless = unused.boundLayouts().isEmpty()
                    ? " This site binds no layouts at all, so nothing in it renders - that is one "
                            + "missing `postTypeLayout` entry rather than a fault in this template."
                    : "";
            for (ShPostUnified region : unused.regions()) {
                findings.add(Finding.warning(check, "unused-region", address(region), null,
                        "No layout in '" + site.getName() + "' slots a region called '"
                                + region.getTitle() + "', so nothing renders it. A layout reaches a "
                                + "region by title, with `sh-region=\"" + region.getTitle() + "\"`."
                                + rootless + elsewhere(scan, site, "region", region.getTitle()),
                        "Add `<div sh-region=\"" + region.getTitle() + "\"></div>` to the layout "
                                + "that should show it, or delete the region if it is finished with. "
                                + "A title that merely differs in case or spacing is the usual "
                                + "cause — a slot is matched exactly."));
            }
            for (ShPostUnified theme : unused.themes()) {
                findings.add(Finding.warning(check, "unused-theme", address(theme), null,
                        "No bound layout in '" + site.getName() + "' reaches the theme '"
                                + theme.getTitle() + "', so its CSS and JavaScript reach no page. "
                                + "A layout names its theme in its THEME field by title, or "
                                + "relates one by friendly URL inside "
                                + "`{{#getRelation \"...\"}}` (SH558); this rule reads both."
                                + rootless + elsewhere(scan, site, "theme", theme.getTitle()),
                        "Set THEME to '" + theme.getTitle() + "' on the layout that should use "
                                + "it, or delete the theme. A layout that names it but is itself "
                                + "unbound reports unused-layout beside this."));
            }
            for (ShPostUnified layout : unused.layouts()) {
                findings.add(Finding.warning(check, "unused-layout", address(layout), null,
                        "No post-type in '" + site.getName() + "' is bound to the layout '"
                                + layout.getTitle() + "', so no page renders through it."
                                + (unused.boundLayouts().isEmpty()
                                        ? rootless
                                        : " Bound: " + String.join(", ", unused.boundLayouts()) + ".")
                                + elsewhere(scan, site, "layout", layout.getTitle()),
                        "Bind a post-type to it in the site's `postTypeLayout` map "
                                + "(`{\"<post type>\":\"" + layout.getTitle() + "\"}`), or delete "
                                + "the layout."));
            }
        }
    }

    /**
     * SH251 — a template interpolating a field its bound post-type does not declare.
     *
     * <p>In {@code content} rather than a group of its own, which was the other candidate. It is a
     * content-consistency rule (a template <em>is</em> content, which is the decision SH183 turns
     * on), it costs nothing on a site with no {@code postTypeLayout} because there are no bindings
     * to walk, and a new group would mean every existing {@code checks=content} caller silently
     * not getting it. The group list is also part of the manifest and the MCP golden, so growing
     * it should buy something semantic rather than tidiness.</p>
     *
     * <p>Scoping (SH143) is honoured by passing the scanned ids: a finding is emitted only when
     * the layout or region post holding the template is one of this run's subjects. That is the
     * same rule the rest of {@code content} follows — what a scope narrows is the subjects.</p>
     */
    private void templateFields(String check, Scan scan, List<Finding> findings) {
        Set<String> inScope = scan.posts().stream()
                .map(ShPostUnified::getId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        for (ShTemplateFieldLint.ShTemplateFinding found
                : templateFieldLint.scan(scan.siteById().values(), inScope)) {
            String address = address(found.subject());
            findings.add(found.error()
                    ? Finding.error(check, found.rule(), address, found.field(),
                            found.message(), found.fix())
                    : Finding.warning(check, found.rule(), address, found.field(),
                            found.message(), found.fix()));
        }
    }

    /**
     * SH1010 — what the model says this field will accept, and whether it does.
     *
     * <p>The same evaluator a publish is refused by, which is the whole point of it being one: a
     * report that called a page valid and a writer that refused it would be two answers to one
     * question, and the reader most likely to meet both is the agent that ran verify to decide
     * whether to publish.</p>
     *
     * <p>Advice is a <b>warning</b> and never an error. {@code recommendedLength} is a judgement
     * about how a value reads where it is shown, and a report that graded it as broken content
     * would teach whoever read it to raise the number rather than shorten the text.</p>
     */
    private void constraintFindings(String check, ShPostUnified post, String address, JsonNode data,
                                    List<Finding> findings) {
        ShPostType postType = post.getShPostType();
        for (ShFieldRules.Violation violation
                : ShFieldRules.violations(postType, data, objectMapper)) {
            findings.add(Finding.error(check, "constraint-violated", address, violation.field(),
                    violation.says(), violation.fix()));
        }
        for (ShFieldRules.Violation advice : ShFieldRules.advice(postType, data, objectMapper)) {
            findings.add(Finding.warning(check, "longer-than-recommended", address, advice.field(),
                    advice.says(), advice.fix()));
        }
    }

    private void fieldFindings(String check, ShPostUnified post, String address, JsonNode data,
                               ShPostTypeAttr attr, Scan scan, List<Finding> findings) {
        String widget = widgetName(attr);
        String field = attr.getName();
        if (field == null || field.isBlank()) {
            return;
        }
        JsonNode value = data.get(field);

        // SH1009 — the decision is ShRequiredFields', not this rule's, and that is the point of
        // moving it: a publish refuses on the same evaluation, so there is no page this reports as
        // complete and the writer refuses, or the other way round.
        if (attr.getRequired() == 1 && !CONTAINER_WIDGETS.contains(widget)
                && ShRequiredFields.isEmpty(value)) {
            findings.add(Finding.error(check, "required-field-empty", address, field,
                    "Required field '" + field + "' is empty.",
                    "Fill it in — a publish refuses this page until you do (SH1009) — or drop the "
                            + "required flag on the post-type if it is genuinely optional."));
        }
        // SH154 — a Multi Select that declares it holds post URLs (the SH95 vocabulary's
        // `sections`). Checked before the id-shaped reference widgets because it is a different
        // kind of reference: a list of friendly URLs, not one id.
        if (ShPostReferenceFields.referencesPosts(attr, objectMapper)) {
            sectionFindings(check, post, address, field, value, attr, scan, findings);
            return;
        }
        if (!REFERENCE_WIDGETS.contains(widget) || isEmpty(value) || !value.isString()
                || isOwnFileName(post, field)) {
            return;
        }
        String targetId = value.asString().trim();
        if (!scan.allIds().contains(targetId) && !existsPost(targetId)) {
            findings.add(Finding.error(check, "dangling-reference", address, field,
                    "Field '" + field + "' points at post " + targetId + ", which does not exist.",
                    "Point it at a post from GET /api/v2/agent/find, or clear the field."));
            return;
        }
        if (ShSystemWidget.FILE.equals(widget)) {
            missingAsset(check, address, field, targetId, findings);
        }
        // A published page whose reference has no published row renders a hole for a
        // visitor while looking perfectly fine to the editor, who sees both drafts.
        if (scan.publishedIds().contains(post.getId()) && !scan.publishedIds().contains(targetId)
                && !targetId.equals(post.getId())) {
            findings.add(Finding.error(check, "unpublished-dependency", address, field,
                    "This post is published but field '" + field + "' points at a post that is not.",
                    "Publish the referenced post (shio_publish / POST /api/v2/agent/batch with "
                            + "post.publish), or unpublish this one until it is ready."));
        }
    }

    private void missingAsset(String check, String address, String field, String fileId,
                              List<Finding> findings) {
        Optional<ShPostUnified> file = shPostUnifiedRepository.findById(fileId).stream()
                .min(Comparator.comparing(row -> row.getState() == ShPostState.PUBLISHED ? 0 : 1));
        String fileName = file.map(this::fileNameOf).orElse(null);
        if (fileName == null || !fileStorage.exists(fileId, fileName)) {
            findings.add(Finding.error(check, "missing-asset", address, field,
                    "Field '" + field + "' references file post " + fileId
                            + ", but its bytes are not in file storage"
                            + (fileName == null ? "" : " (" + fileName + ")") + ".",
                    "Re-upload the binary with POST /api/v2/staticfile/upload, or clear the field."));
        }
    }

    /**
     * Two <b>posts</b> claiming one URL, in whatever state (SH720).
     *
     * <h2>The promise this had to make true</h2>
     *
     * <p>The {@code duplicate-url} refusal an agent meets on a contested address ends with
     * <i>"{@code GET /api/v2/agent/verify} reports this as {@code duplicate-url} with both
     * addresses."</i> Measured against a running instance, that sentence was <b>false in the case
     * that produces it</b>: this check iterated published rows only, so the pair of drafts SH719's
     * race left behind came back as {@code report ok}. The agent's instructed sequence therefore
     * ended in a wrong conclusion — refused, sent to an instrument, told nothing is wrong — and its
     * next move is to disbelieve one of two answers, neither choice safe. A refusal naming an
     * instrument that cannot answer is worse than one naming none, because it costs a call to find
     * out.</p>
     *
     * <h2>Why widening is not the noise it looked like</h2>
     *
     * <p>The old restriction was not caution about work-in-progress, it was structural, and its
     * comment said so: <i>"a draft sharing a URL with the live page it will replace is the normal
     * editing state, and flagging it would fire on every single edit."</i> True — of an index keyed
     * by <b>row</b>. A post's DRAFT and PUBLISHED rows share a furl by design (SH81), so counting
     * rows makes every ordinary published page its own duplicate.</p>
     *
     * <p>Counting <b>distinct post ids</b> asks the question that was always meant: two rows of one
     * post are one claimant. Nothing about an ordinary edit fires, and three real cases that were
     * invisible now do — draft vs draft, draft vs another post's published row, and the published
     * pair that was already caught. It also inherits SH719's key exactly: that index is unique over
     * {@code (site, state, locale, furl)}, so a state pair of one post is legal there for the same
     * reason it is one claimant here.</p>
     *
     * <p>Two further arguments now hold that did not before. SH719 makes this state
     * <b>unreachable by ordinary work</b> — the database refuses the second insert — so what remains
     * to report is legacy damage rather than somebody's unfinished draft, and the "a report full of
     * findings gets the check switched off" objection loses its population. And an unpublished
     * duplicate is not harmless just because no visitor sees it: {@code ShAddressResolver} refuses
     * {@code post:<site>/<furl>} whatever the state, so <b>no write can name either post</b>. That
     * is the agent's problem, and this is the agent's instrument.</p>
     *
     * <p>Asked <b>per subject against the site's index</b> rather than by grouping the loaded
     * rows, so a scoped run still finds the duplicate whose twin is outside its scope — the
     * case a self-grouping version would quietly call clean. On a full pass every member of a
     * group is a subject, so the output is the same one-finding-per-post it always was.</p>
     */
    private void duplicateUrls(String check, Scan scan, List<Finding> findings) {
        // One subject per (post, url) pair rather than per row or per post, and a test found why.
        // A post's two state rows usually share a furl and collapse to one pair — so an ordinary
        // published page is one subject, which is what keeps this from firing on every edit. But
        // they need not: the fixture that pins this check moves only the PUBLISHED row onto another
        // post's address, exactly as an import or a pre-SH134 move does. Keyed by post, that row's
        // URL is invisible (the draft-preferred row still carries the old one) and the second half
        // of the collision goes unreported.
        Set<String> reported = new HashSet<>();
        List<ShPostUnified> subjects = new ArrayList<>(scan.posts());
        subjects.addAll(scan.published());
        for (ShPostUnified post : subjects) {
            if (post.getShSite() == null || isBlank(post.getFurl())
                    || !reported.add(post.getId() + " " + normalized(post.getFurl()))) {
                continue;
            }
            List<String> claiming = scan.claimantsByUrl()
                    .getOrDefault(post.getShSite().getId(), Map.of())
                    .getOrDefault(normalized(post.getFurl()), List.of());
            if (claiming.size() <= 1) {
                continue;
            }
            String others = claiming.stream().sorted()
                    .reduce((first, second) -> first + ", " + second).orElse("");
            String locales = distinctLocales(claiming, scan);
            // Whether a visitor can reach the collision decides which harm the sentence names, and
            // the published wording is kept byte-identical (SH298): an instance whose duplicates
            // were already reported sees the same finding it saw yesterday.
            boolean live = claiming.stream().allMatch(id -> scan.publishedIds().contains(id));
            String detail = live
                    ? "Published URL '" + post.getFurl() + "' is claimed by "
                            + claiming.size() + " posts (" + others + ") — which one a "
                            + "visitor gets is undefined."
                    : "URL '" + post.getFurl() + "' is claimed by " + claiming.size()
                            + " posts (" + others + "), not all of them published — so no visitor "
                            + "sees the collision and no write can name either post: an SH79 "
                            + "address is post:<site>/<furl> and carries no state.";
            findings.add(Finding.error(check, "duplicate-url", address(post), null,
                    detail
                            + (locales == null ? ""
                                    : " They are different translations (" + locales + "), which "
                                    + "does not make the URL free: an SH79 address carries no "
                                    + "locale, so no write can name either of them."),
                    "Give one of them a different URL with a post.move op "
                            + "(POST /api/v2/agent/batch), or unpublish the duplicate."
                            + (locales == null ? ""
                                    : " A translation needs a URL of its own; the CDA still "
                                    + "reports the pair through availableLocales.")));
        }
    }

    /**
     * The locales of a set of URL claimants, sorted — or {@code null} when they all share one.
     *
     * <p>SH323's half of the message. Two published translations at one URL is the case where
     * {@code duplicate-url} reads as an accusation of carelessness against somebody who made a
     * deliberate choice, so the finding says why the locale does not help: an SH79 address is
     * {@code post:<site>/<furl>} and carries no locale, so {@code ShAddressResolver} refuses the
     * pair (SH321) and {@code post.move} would refuse the URL — the CMS cannot name either post,
     * whatever tells them apart. Locale-aware delivery is for a translation with its <b>own</b>
     * URL.</p>
     *
     * <p>Read from the cross-cutting index and never from the loaded rows: a scoped run may hold
     * one of the two, and a message naming the other's locale from the subject set would be a
     * guess (SH143's rule, and SH263's — a positive wrong answer is worse than a missing one).
     * {@code null} rather than an empty string so the ordinary single-locale finding stays
     * byte-identical, which is how every appended clause in this report behaves (SH298).</p>
     */
    private String distinctLocales(List<String> claiming, Scan scan) {
        List<String> locales = claiming.stream()
                .map(id -> scan.publishedLocales().get(id))
                .filter(locale -> locale != null && !locale.isBlank())
                .distinct()
                .sorted()
                .toList();
        return locales.size() <= 1 ? null
                : "locales " + String.join(", ", locales);
    }

    /**
     * Folder-level findings.
     *
     * <p>"Holds no posts and no subfolders" is a question about content that may sit outside a
     * scope, so both halves come from instance-wide id projections rather than from the loaded
     * rows: a scoped run must never call a folder empty because it did not look inside it
     * (SH143).</p>
     */
    private void folderFindings(String check, Scan scan, List<Finding> findings) {
        Set<String> foldersWithPosts = scan.foldersWithPosts();
        Set<String> parents = scan.folderParents();
        for (Scanned entry : scan.folders()) {
            ShFolder folder = entry.folder();
            if (entry.site() == null) {
                findings.add(Finding.error(check, "orphan-folder", "id:" + folder.getId(), null,
                        "Folder '" + folder.getName() + "' is not under any site, so it has no "
                                + "address and nothing inside it can be delivered.",
                        "Delete it, or recreate the content under a site with folder.upsert "
                                + "(POST /api/v2/agent/batch)."));
                continue;
            }
            boolean isRoot = folder.getRootFolder() == 1;
            if (!isRoot && !parents.contains(folder.getId()) && !foldersWithPosts.contains(folder.getId())) {
                findings.add(Finding.warning(check, "empty-folder", folderAddress(entry), null,
                        "Folder '" + folder.getName() + "' holds no posts and no subfolders.",
                        "Add the content it was created for, or delete it with a folder.delete "
                                + "op. A section that renders empty is the most common "
                                + "\"looks done but isn't\"."));
            }
        }
    }

    /**
     * SH1034 — assets nothing references, which no rule could ask before the index.
     *
     * <h2>Why this is here and not in the inventory it belongs beside</h2>
     *
     * <p>{@code verify} has had {@code orphan-post} and {@code orphan-folder} since SH99 and
     * nothing for assets, and that was a cost problem rather than an oversight: "does anything
     * reference this file" was {@code data like '%id%'} over every post, so asking it of a library
     * meant one table scan per asset. SH1033 measured the single form at 50,002 rows examined on a
     * fifty-thousand-post instance; a hundred assets was a hundred of those. It is now one indexed
     * query for the whole set.</p>
     *
     * <h2>The count is instance-wide on purpose, and a scoped run must not narrow it</h2>
     *
     * <p>This is SH143's rule in the case that would break it worst. An asset in one site is
     * routinely referenced from another — a shared logo is the normal case, not the exotic one — so
     * a count computed inside the scanned site would report a referenced asset as unused, and the
     * fix offered is <b>delete it</b>. The reference index has no site dimension, so the count
     * answers for the instance however the run was scoped; only which assets get <em>asked</em>
     * about is scoped.</p>
     *
     * <h2>A warning, not an error</h2>
     *
     * <p>Nothing is broken: the bytes are served, the page renders, the site is correct. What an
     * unreferenced asset costs is storage, and a curator may be holding it on purpose — which is
     * exactly {@code empty-folder}'s shape, and it is a warning too.</p>
     */
    private void unusedAssets(String check, Scan scan, List<Finding> findings) {
        Map<String, ShPostUnified> assets = new LinkedHashMap<>();
        for (ShPostUnified post : scan.posts()) {
            if (post.getShPostType() != null
                    && ShSystemPostType.FILE.equals(post.getShPostType().getName())) {
                // One entry per logical asset: the draft and published rows of one upload are one
                // file on disk, and reporting both would say "two unused assets" about one.
                assets.putIfAbsent(post.getId(), post);
            }
        }
        if (assets.isEmpty()) {
            return;
        }
        Map<String, Long> counts = shObjectReferenceService.referrerCounts(assets.keySet());
        assets.forEach((id, asset) -> {
            if (counts.getOrDefault(id, 0L) > 0) {
                return;
            }
            findings.add(Finding.warning(check, "asset-unused", "id:" + id, null,
                    "No post references '" + (asset.getTitle() == null ? id : asset.getTitle())
                            + "', so its bytes are stored and never delivered.",
                    "Check `shio assets prune --dry-run` for what reclaiming it would free, then "
                            + "delete it with a post.delete op — or point a File or Content Select "
                            + "field at it if it was uploaded for a page nobody finished."));
        });
    }

    // --- SH100: routes ------------------------------------------------------

    /**
     * @param declared the front-end routes the site claims (SH141) — a link matching one is
     *                 not a guess this rule is entitled to make
     * @param unproven when non-{@code null}, the delivery check will settle unresolved links
     *                 by fetching them, so they are collected here instead of reported as
     *                 warnings. Reporting both would double every broken link
     * @param notes    where suppression is made visible: a silenced finding nobody is told
     *                 about is how a declaration turns into a blind spot
     */
    private void routes(Scan scan, List<Finding> findings, ShFrontEndRoutes declared,
                        List<Unresolved> unproven, List<String> notes, boolean partial) {
        String check = ShAgentCheck.ROUTES.wireName();
        // From the site index, not from the loaded rows: "does this link resolve" is a
        // question about the whole site, and a scoped run that answered it from its own
        // subjects would report every link out of the scope as broken (SH143).
        Map<String, Set<String>> publishedUrls = new HashMap<>();
        scan.publishedByUrl().forEach((siteId, byUrl) ->
                publishedUrls.put(siteId, new HashSet<>(byUrl.keySet())));
        // SH1064 — the addresses this site has served before and does not serve now. Empty on a
        // scoped run, which is what keeps the rule off a task it has nothing to do with.
        Map<String, Set<String>> vacated = partial ? Map.of() : vacatedUrls(scan, publishedUrls);
        Map<String, Map<String, Integer>> orphaned = new LinkedHashMap<>();
        int silenced = 0;
        for (ShPostUnified post : scan.published()) {
            String address = address(post);
            if (isBlank(post.getFurl()) && !isUrllessByDesign(post)) {
                findings.add(Finding.error(check, "url-missing", address, null,
                        "This post is published but has no friendly URL, so no route reaches it.",
                        "Give it one with a post.move op (POST /api/v2/agent/batch): "
                                + "{\"op\":\"post.move\",\"address\":\"id:" + post.getId()
                                + "\",\"to\":\"post:<site>/<url>\"}."));
            }
            silenced += markup(check, post, address, publishedUrls, declared, unproven, findings,
                    vacated, orphaned);
        }
        orphanedUrls(check, scan, orphaned, findings);
        if (silenced > 0) {
            notes.add(silenced + " unresolved link(s) matched routes declared as the front end's "
                    + "(memory key '" + ShFrontEndRoutes.MEMORY_KEY + "') and were not reported"
                    + (unproven == null ? "." : " as warnings — they are proved below instead."));
        }
        if (!partial) {
            homePage(scan, findings, check);
        }
    }

    /**
     * SH1064 — every address a site has served before and does not serve now.
     *
     * <h2>Where a "previous address" comes from when nothing stores one</h2>
     *
     * <p>No column holds a post's old URL: {@code post.move} rewrites {@code furl} in place, and
     * the audit row it writes says <em>update</em>. What does hold it is the revision table — a
     * revision captures the page as it was, {@code furl} included (SH986) — so the addresses this
     * site used to answer are the distinct revision URLs minus the ones it answers today. Derived
     * rather than stored, which is why this rule needed no migration and works on content that
     * predates it.</p>
     *
     * <p><b>Both states subtracted</b>, not just published: a page whose draft is at the new URL
     * and whose live row is still at the old one has not vacated anything, and reporting it would
     * name a URL that resolves. A rename nobody has published yet is not an orphan.</p>
     */
    private Map<String, Set<String>> vacatedUrls(Scan scan, Map<String, Set<String>> publishedUrls) {
        Map<String, Set<String>> vacated = new LinkedHashMap<>();
        for (String siteId : publishedUrls.keySet()) {
            Set<String> current = new HashSet<>(publishedUrls.getOrDefault(siteId, Set.of()));
            for (ShPostUnified post : scan.posts()) {
                if (post.getShSite() != null && siteId.equals(post.getShSite().getId())
                        && !isBlank(post.getFurl())) {
                    current.add(normalized(post.getFurl()));
                }
            }
            Set<String> gone = new LinkedHashSet<>();
            for (String furl : shPostRevisionRepository.distinctFurlsBySiteId(siteId)) {
                String path = normalized(furl);
                if (!path.isBlank() && !current.contains(path)) {
                    gone.add(path);
                }
            }
            if (!gone.isEmpty()) {
                vacated.put(siteId, gone);
            }
        }
        return vacated;
    }

    /**
     * SH1064 — an address this site used to answer, still linked to, answering nothing.
     *
     * <h2>An error, and the first one on this surface that can say the fix in one op</h2>
     *
     * <p>{@code link-unresolved} is a warning because four things could be true and the CMS cannot
     * tell them apart. Here it can: the address was a page here, it is not one now, and a
     * <b>published</b> page points at it. So the reader gets a 404 on a route this instance used
     * to serve, which is the one shape of broken link the CMS is responsible for and the one it
     * can repair without asking anybody what the path was supposed to mean.</p>
     *
     * <p>One finding per address rather than one per link: the subject is the URL and the pages
     * linking to it are its count, which is also the cheaper report (P3) — a URL linked from
     * forty pages is one line, not forty.</p>
     */
    private void orphanedUrls(String check, Scan scan,
                              Map<String, Map<String, Integer>> orphaned, List<Finding> findings) {
        orphaned.forEach((siteId, paths) -> {
            ShSite site = scan.siteById().get(siteId);
            if (site == null) {
                return;
            }
            paths.forEach((path, inbound) -> {
                String address = ShAddress.post(site.getName(), path).toString();
                findings.add(Finding.error(check, "url-orphaned", address, null,
                        "Site '" + site.getName() + "' used to serve '" + path + "' and does not "
                                + "now, and " + inbound + " published page(s) still link to it — "
                                + "so every bookmark and inbound link to that address is a 404.",
                        "Keep it answering with a post.redirect op (POST /api/v2/agent/batch): "
                                + "{\"op\":\"post.redirect\",\"address\":\"" + address
                                + "\",\"to\":\"post:" + site.getName() + "/<where it went>\"}. "
                                + "Repointing the pages here fixes only the links this instance "
                                + "can see." + frontEndRedirectNote(site)));
            });
        });
    }

    /**
     * SH1133 — what a {@code post.redirect} does not reach on its own.
     *
     * <p>A {@code Redirect} post is honoured by this instance's {@code /sites/**} delivery, and a
     * site with a front end of its own does not go through it. The fix above then writes a row that
     * changes nothing a visitor can observe, and the finding goes quiet because the address resolves.
     * So a site with a front-end URL is told where the redirect has to reach. Conditional, because the
     * URL may be this instance's own {@code /sites/} address, and the CMS cannot tell from the string
     * which of the two it is.</p>
     */
    private static String frontEndRedirectNote(ShSite site) {
        if (isBlank(site.getUrl())) {
            return "";
        }
        return " If '" + site.getUrl().trim() + "' is a front end of its own rather than this "
                + "instance's /sites/ delivery, the redirect reaches visitors only when that front end "
                + "consults GET /api/v2/cda/site/" + site.getId() + "/redirects before answering 404 "
                + "(create-shio-app's middleware.ts does).";
    }

    /**
     * A site that publishes pages and has nothing at {@code /} (SH334).
     *
     * <h2>Why this is a content finding and not a console feature</h2>
     *
     * <p>The symptom appeared in the Universal Editor — it opens {@code ShSiteFrontEnd}'s answer,
     * which is the site <em>root</em>, so a site with no page there opens on a 404 and exactly one
     * page of it is editable. The obvious fix is a page picker, and it is the wrong one to build
     * first: <b>a page at furl {@code /} is a convention nothing enforces, defaults or reports.</b>
     * The SH71 starter has one and the shipped fixtures have one, which is why this never surfaced;
     * an agent following SH79 has no reason to create one, because nothing in the address scheme,
     * the render model or this lint ever said a site needs a home. So the state is one the product
     * now produces routinely, and naming it removes most of the occasions for a picker rather than
     * decorating them.</p>
     *
     * <h2>A warning, and only for a site that publishes</h2>
     *
     * <p>Warning because SH99's rule is that {@code ok} is false only for what the CMS can prove is
     * broken now, and a site addressed entirely from an external front end's own router is a
     * legitimate design rather than a defect. Skipped entirely when the site has published nothing:
     * there is no front door missing from a building with no rooms, and an agent halfway through a
     * build would otherwise be told off for the state every build passes through.</p>
     *
     * <p>Read from {@code publishedByUrl}, which is the whole-site index rather than the run's own
     * subjects — SH143's rule for every cross-cutting question. A scoped run that answered this
     * from its scope would report a missing home page for any pass that did not happen to include
     * it.</p>
     *
     * <h2>Not on a scoped run at all, and the benchmark is why</h2>
     *
     * <p>Every other routes rule is <em>about a subject the run looked at</em>; this one is about
     * the site, so a narrowed pass would attach it to work that has nothing to do with it. That is
     * not a style objection — it was measured. Firing it on scoped runs took SH164's
     * {@code fix-broken-link} task from 400 tokens to **560**, a 40% regression on a canonical
     * loop, because a `?verify=true` proof scoped to one repaired link carried a paragraph about
     * the site's front door. P3 is a budget, and the fix is not a bigger budget: a finding an agent
     * did not ask about and cannot act on inside its current task is the thing being paid for.
     * A full pass still reports it, which is where somebody is looking at the site as a whole.</p>
     */
    private void homePage(Scan scan, List<Finding> findings, String check) {
        for (Map.Entry<String, Map<String, List<String>>> entry : scan.publishedByUrl().entrySet()) {
            Map<String, List<String>> byUrl = entry.getValue();
            if (byUrl.isEmpty() || byUrl.containsKey("/")) {
                continue;
            }
            ShSite site = scan.siteById().get(entry.getKey());
            if (site == null) {
                continue;
            }
            findings.add(Finding.warning(check, "no-home-page",
                    ShAddress.site(site.getName()).toString(), null,
                    "Site '" + site.getName() + "' publishes " + byUrl.size()
                            + " page(s) and none of them is at '/', so its front door is a 404 — "
                            + "which is also the only page the Universal Editor can open.",
                    "Give one page the friendly URL '/' with a post.move op "
                            + "(POST /api/v2/agent/batch): {\"op\":\"post.move\",\"address\":\"post:"
                            + site.getName() + "/<url>\",\"to\":\"post:" + site.getName() + "/\"}."));
        }
    }

    /**
     * Scans a published post's rich text for links and images.
     *
     * <p>Only published rows, and only the two markup widgets: a draft's broken link is
     * work in progress, and a plain-text field containing something that looks like a
     * path is not a link.</p>
     */
    private int markup(String check, ShPostUnified post, String address,
                       Map<String, Set<String>> publishedUrls, ShFrontEndRoutes declaredRoutes,
                       List<Unresolved> unproven, List<Finding> findings,
                       Map<String, Set<String>> vacated,
                       Map<String, Map<String, Integer>> orphaned) {
        JsonNode data = data(post);
        Set<String> siteUrls = post.getShSite() == null
                ? Set.of()
                : publishedUrls.getOrDefault(post.getShSite().getId(), Set.of());
        int silenced = 0;
        for (ShPostTypeAttr attr : declared(post.getShPostType())) {
            if (!MARKUP_WIDGETS.contains(widgetName(attr))) {
                continue;
            }
            JsonNode value = data.get(attr.getName());
            if (isEmpty(value) || !value.isString()) {
                continue;
            }
            htmlUnsafeFinding(check, address, attr, value.asString(), findings);
            ShHtmlOutline.Outline outline = ShHtmlOutline.of(value.asString());
            for (ShHtmlOutline.Link link : outline.links()) {
                silenced += linkFinding(check, post, address, attr.getName(), link.href(),
                        siteUrls, declaredRoutes, unproven, findings, vacated, orphaned);
            }
            for (ShHtmlOutline.Image image : outline.images()) {
                imageFindings(check, address, attr.getName(), image, findings);
            }
        }
        return silenced;
    }

    /**
     * One link, and the three answers this rule can now give (SH141).
     *
     * <p>It resolves against the content graph — nothing to say. It is <b>declared</b> as the
     * front end's — nothing to say either, because the caller has asserted a fact the CMS
     * cannot derive and taking the assertion at face value is what the declaration is for; the
     * report still counts it so the suppression is visible. Otherwise it is unresolved, and
     * whether that is a warning or a fetch away from an error depends on {@code unproven}.</p>
     *
     * @return 1 when a declaration silenced this link, so the caller can count it
     */
    private int linkFinding(String check, ShPostUnified post, String address, String field,
                            String href, Set<String> siteUrls, ShFrontEndRoutes declaredRoutes,
                            List<Unresolved> unproven, List<Finding> findings,
                            Map<String, Set<String>> vacated,
                            Map<String, Map<String, Integer>> orphaned) {
        if (!isInternal(href)) {
            return 0;
        }
        String path = ShPostLinks.normalizedPath(href);
        if (path.startsWith(FILE_SOURCE_PREFIX) || siteUrls.contains(path) || "/".equals(path)) {
            return 0;
        }
        String siteId = post.getShSite() == null ? null : post.getShSite().getId();
        String siteName = post.getShSite() == null ? null : post.getShSite().getName();
        boolean claimed = declaredRoutes.declares(siteName, path);
        if (unproven != null && siteId != null) {
            // A declaration silences a guess but not a proof: the link is still fetched, and a
            // route the front end was declared to own and does not serve is an error worth
            // having — the declaration made a claim, and the claim is checkable now.
            unproven.add(new Unresolved(siteId, path, href, address, field, claimed));
            return 0;
        }
        if (claimed) {
            return 1;
        }
        // SH1064 — the one case where the CMS is not guessing. This site served that path, it
        // does not now, and a published page still links to it: `link-unresolved`'s four
        // possibilities collapse to one fact, so it is counted for `url-orphaned` below instead of
        // reported as a maybe. Counted rather than emitted here, because the subject is the
        // address and the pages linking to it are its count.
        if (siteId != null && vacated.getOrDefault(siteId, Set.of()).contains(path)) {
            orphaned.computeIfAbsent(siteId, id -> new LinkedHashMap<>())
                    .merge(path, 1, Integer::sum);
            return 0;
        }
        // A warning, not an error: the front end owns routes the CMS never sees (a
        // listing page, a generated tag index). Reporting those as errors would make the
        // exit code useless on any real site, which costs more than the missed defect.
        // SH141's delivery check is what turns this into a provable answer.
        findings.add(Finding.warning(check, "link-unresolved", address, field,
                "Link '" + href + "' matches no published URL in this site.",
                "Publish the target, fix the link, declare it as a front-end route "
                        + "(shio remember " + ShFrontEndRoutes.MEMORY_KEY + " \"" + path
                        + "\" --site <site>), or turn on checks=delivery to have it proved."));
        return 0;
    }

    /**
     * Content that predates the sanitizer, and would not be accepted today (SH956).
     *
     * <p>SH956 put an allow-list on every write. That says nothing about rows already stored: a
     * payload written before it shipped sits there, and the renderer emits field HTML unescaped by
     * design, so it is still on the page. This asks the <b>same</b> sanitizer what it would do —
     * two rules about one question is how a corpus comes to be judged differently from a write.</p>
     *
     * <p><b>{@code HTML Editor} only</b>, exactly as the write path is. {@code Ace Editor - HTML}
     * is in {@link #MARKUP_WIDGETS} because a template holds links worth checking, and it is a
     * template: a {@code PageLayout} carries {@code <script>} on purpose, and reporting every
     * layout in the estate as unsafe would make this rule the first one anybody switches off.</p>
     *
     * <p>An error rather than a warning: the value is on a published page, and the fix is a save
     * away — re-saving the post runs the write path, which cleans it.</p>
     */
    // Package-private so the rule can be asserted directly: driving it through the whole
    // published-site scan means building a fixture the scan agrees is published, which is a
    // test of the scan rather than of this rule.
    void htmlUnsafeFinding(String check, String address, ShPostTypeAttr attr,
                                   String value, List<Finding> findings) {
        if (!ShSystemWidget.HTML_EDITOR.equals(widgetName(attr))) {
            return;
        }
        ShHtmlSanitizer.Result result = shHtmlSanitizer.sanitize(value);
        if (!result.changed()) {
            return;
        }
        findings.add(Finding.error(check, "html-unsafe", address, attr.getName(),
                "This published value carries markup the upload policy would refuse today: "
                        + String.join(", ", result.removed()) + ".",
                "Re-save the post — every write runs the allow-list, so saving it cleans the "
                        + "stored value. It was written before SH956 put the sanitizer on the "
                        + "write path, and the renderer emits field HTML unescaped, so it is on "
                        + "the page as it stands."));
    }

    /**
     * An image asset nobody has described (SH1031).
     *
     * <h2>Why this rule had to exist beside {@code image-no-alt}</h2>
     *
     * <p>{@code image-no-alt} reads rendered HTML and fires when the {@code alt} attribute is
     * <b>absent</b> — deliberately, because an absent attribute and an empty one mean different
     * things to a screen reader, and {@code alt=""} is how you say "decorative". The recommended
     * {@code &#123;&#123;#image&#125;&#125;} helper puts an empty string whenever {@code ALT} is
     * null, so on a site built the recommended way the attribute is always present and the rule is
     * <b>structurally unreachable</b>. Three correct-looking decisions composing into a check that
     * can never fire.</p>
     *
     * <h2>Why over the asset and not the page</h2>
     *
     * <p>Alt text is a property of the image, not of the page it appears on: the same photo used
     * on twelve pages needs describing once, and a rule that fired per page would report it twelve
     * times and be fixed in twelve places. It also means the finding needs <b>no render</b> —
     * which is what makes it cheap enough to be on by default, and what lets it name the asset the
     * curator has to open.</p>
     *
     * <p><b>A publish check, never an upload refusal.</b> Uploading without alt is fine and must
     * stay fine: every capture and every conversion run uploads in bulk, and refusing there would
     * break the two paths this product exists to make easy. This fires where the decision is being
     * made — the gate a site runs before content goes live.</p>
     */
    private void assetAltFindings(String check, ShPostUnified post, String address, JsonNode data,
                                  List<Finding> findings) {
        if (post.getShPostType() == null
                || !ShSystemPostType.FILE.equals(post.getShPostType().getName())) {
            return;
        }
        String fileName = text(data, ShSystemPostTypeAttr.FILE);
        if (!isImageAsset(data, fileName)) {
            return;
        }
        if (!text(data, "ALT").isBlank()) {
            return;
        }
        findings.add(Finding.warning(check, "asset-no-alt", address, "ALT",
                "Image '" + (fileName.isBlank() ? "?" : fileName) + "' has no alt text, so every "
                        + "page that uses it is undescribed to a screen reader.",
                "Set it in the Media Library, or with a post.upsert writing ALT. If the image is "
                        + "genuinely decorative, that is a decision worth recording rather than "
                        + "leaving blank — an empty alt and no alt mean different things."));
    }

    /** Whether a {@code File} post holds an image: {@code ShAssetKind}'s one rule (SH1026). */
    private boolean isImageAsset(JsonNode data, String fileName) {
        return com.viglet.shio.api.staticfile.ShAssetKind.isImage(data, fileName);
    }

    private void imageFindings(String check, String address, String field,
                               ShHtmlOutline.Image image, List<Finding> findings) {
        String src = image.src();
        if (image.alt() == null && isInternal(src)) {
            findings.add(Finding.warning(check, "image-no-alt", address, field,
                    "Image '" + src + "' has no alt attribute.",
                    "Add alt text, or alt=\"\" if the image is decorative — an absent "
                            + "attribute and an empty one mean different things to a screen reader."));
        }
        if (!isInternal(src) || !src.startsWith(FILE_SOURCE_PREFIX)) {
            return;
        }
        String[] parts = src.substring(FILE_SOURCE_PREFIX.length()).split("/", 2);
        if (parts.length == 2 && !fileStorage.exists(parts[0], decodePath(parts[1]))) {
            findings.add(Finding.error(check, "asset-missing", address, field,
                    "Image '" + src + "' has no bytes in file storage — a visitor gets a 404.",
                    "Re-upload it with POST /api/v2/staticfile/upload and point the "
                            + "markup at the returned post's URL."));
        }
    }

    // --- SH516: the page a reader gets ---------------------------------------

    /**
     * Renders each published page and resolves every reference on it against its own URL.
     *
     * <h2>The gap, which is a reader rather than a rule</h2>
     *
     * <p>{@code verify --site VILT} said <b>ok — 0 errors, 17 warnings</b>. Fetching the page it
     * approved and resolving what it asks for gave <b>38 broken links of 39</b> and <b>43 broken
     * references</b>; {@code verify} had found 12 of the 38 and none of the 43. Every check resolves
     * a reference against the content graph — does this furl exist, is that {@code File} post
     * addressable, does the layout name a theme — and every one of those was true. The page came out
     * served four segments deep, so its relative strings resolved elsewhere (SH512), its
     * query-stringed paths missed (SH515), and its links pointed at pages nobody captured. <b>The
     * content was self-consistent and the page did not work.</b></p>
     *
     * <p>Six defects survived a green gate in one session — SH455, SH461, SH462, SH512, SH515 and
     * the two that produced this check — and all six live in that gap. Each was found by hand, and
     * the hand was wrong once: a first pass reported <em>0 broken</em> on the page with a missing
     * logo, because it filtered references on a leading slash instead of resolving them.</p>
     *
     * <h2>Composed here rather than fetched, which is the same bytes</h2>
     *
     * <p>{@code /sites/**} renders through {@link ShPageRenderService} and rewrites through
     * {@link ShPreviewLinks}, and returns the result. So composing the page in this process is what
     * the reader gets, minus a network hop this instance would be making to itself — which is also
     * what lets the group be in the default set with no {@code allow-fetch} property and no
     * request-forgery surface. Reachability from <em>outside</em> is a different question and
     * {@code delivery} is where it is asked.</p>
     *
     * <h2>Judged against this instance's own routes, and the one exemption</h2>
     *
     * <p>A path reaches a page if the site publishes that furl, or an asset if
     * {@link ShSiteAssets} serves it — the two things {@code /sites/**} answers with, in that order.
     * These are <b>errors</b> and not {@code link-unresolved}'s warning, and the difference is what
     * can be proved: that rule cannot see a front end it does not host, while this one reads the
     * route table of the process that will serve the request. The exemption is the same declaration
     * ({@link ShFrontEndRoutes}), because a site delivered by somebody else's front end is exactly
     * the case that memory exists to state.</p>
     *
     * <p>Bounded at {@link #MAX_PAGES} in a deterministic order, with a note when it cuts — a
     * shortened list of defects reads exactly like a shorter list of defects (SH138) — and a note on
     * a clean pass, because a check that reports nothing has to say what it read.</p>
     */
    private void pages(Scan scan, List<Finding> findings, List<String> notes,
                       ShFrontEndRoutes declared, Set<String> loaded) {
        String check = ShAgentCheck.PAGE.wireName();
        // `site.postTypeLayout` names the types that are *pages* — the renderer's own definition,
        // read rather than guessed. Without it the subjects would include every `File` post (which
        // has a furl by design) and every section a listing composes, and each would be reported as
        // a page that does not render: fifty findings about the check rather than about the site.
        Map<String, Map<String, String>> bindings = new HashMap<>();
        List<ShPostUnified> subjects = scan.published().stream()
                .filter(post -> post.getShSite() != null && !isBlank(post.getFurl()))
                .filter(post -> isPage(post, bindings))
                .sorted(Comparator.comparing(ShAgentVerifyService::address))
                .toList();
        List<ShPostUnified> read = subjects.size() > MAX_PAGES
                ? subjects.subList(0, MAX_PAGES)
                : subjects;
        int references = 0;
        for (ShPostUnified post : read) {
            references += onePage(check, post, scan, findings, declared, loaded);
        }
        if (subjects.size() > read.size()) {
            notes.add("The page check read " + read.size() + " of " + subjects.size()
                    + " published page(s) and stopped at the cap — the rest were not checked.");
        }
        if (!read.isEmpty()) {
            notes.add("The page check rendered " + read.size() + " page(s) and resolved "
                    + references + " reference(s) against the URL each page is served at.");
        }
    }

    /**
     * SH1055 — the accessibility family, over the pages a visitor actually receives.
     *
     * <h2>Why it renders rather than reading the stored fields</h2>
     *
     * <p>Every rule here is about the <em>document</em>: whether it declares a language, whether it
     * marks a main region, whether the headings its layout and its sections compose into form an
     * outline. None of that is knowable from a post's fields — a page is a layout, a theme and a
     * list of sections, and the defect is usually in the layout rather than in anything a curator
     * typed. The same subjects and the same cap as {@code page}, for the same reasons.</p>
     *
     * <h2>It says what it read, even when it found nothing</h2>
     *
     * <p>A clean accessibility pass and a pass that rendered no pages are the same report without
     * this note, and one of them is a site with no published pages at all.</p>
     */
    private void accessibility(Scan scan, List<Finding> findings, List<String> notes) {
        String check = ShAgentCheck.A11Y.wireName();
        Map<String, Map<String, String>> bindings = new HashMap<>();
        List<ShPostUnified> subjects = scan.published().stream()
                .filter(post -> post.getShSite() != null && !isBlank(post.getFurl()))
                .filter(post -> isPage(post, bindings))
                .sorted(Comparator.comparing(ShAgentVerifyService::address))
                .toList();
        List<ShPostUnified> read = subjects.size() > MAX_PAGES
                ? subjects.subList(0, MAX_PAGES)
                : subjects;
        int violations = 0;
        for (ShPostUnified post : read) {
            String address = address(post);
            String html;
            try {
                html = pageRenderService.render(post.getShSite().getId(),
                        ShAddress.normalizePath(post.getFurl()), ShPostState.PUBLISHED).html();
            } catch (ShPageRenderService.ShPageRedirectException | ShTeachingException cannotRender) {
                // A page that does not render is `page`'s finding and not this one's. Reporting it
                // twice under two rule names would make one repair look like two defects.
                continue;
            }
            for (ShHtmlAccessibility.Violation violation : ShHtmlAccessibility.of(html)) {
                violations += 1;
                findings.add(Finding.warning(check, violation.rule(), address, null,
                        violation.detail(), violation.fix()));
            }
        }
        if (subjects.size() > read.size()) {
            notes.add("The accessibility check read " + read.size() + " of " + subjects.size()
                    + " published page(s) and stopped at the cap — the rest were not checked.");
        }
        if (!read.isEmpty()) {
            notes.add("The accessibility check read " + read.size() + " rendered page(s) and "
                    + "found " + violations + " violation(s).");
        }
    }

    /** Whether this post's type is one the site binds a layout for, i.e. one that is a page. */
    private boolean isPage(ShPostUnified post, Map<String, Map<String, String>> bindings) {
        String type = post.getShPostType() == null ? null : post.getShPostType().getName();
        return type != null && bindings
                .computeIfAbsent(post.getShSite().getId(),
                        id -> pageRenderService.layoutBindings(post.getShSite()))
                .containsKey(type);
    }

    /** One page: rendered, walked, and every reference of it resolved. Returns how many. */
    private int onePage(String check, ShPostUnified post, Scan scan, List<Finding> findings,
                        ShFrontEndRoutes declared, Set<String> loaded) {
        ShSite site = post.getShSite();
        String address = address(post);
        String url = ShAddress.normalizePath(post.getFurl());
        String html;
        try {
            html = pageRenderService.render(site.getId(), url, ShPostState.PUBLISHED).html();
        } catch (ShPageRenderService.ShPageRedirectException redirect) {
            // SH385's `Redirect` post: a 3xx is a working route with no page to read, so there is
            // nothing here to resolve and nothing wrong. Caught before the clause below, or every
            // preserved old path would be reported as a page that does not render.
            return 0;
        } catch (ShTeachingException cannotRender) {
            // A published page that does not compose is broken for every visitor, and it was
            // invisible: `routes` proves the furl resolves, which is a different claim from "and
            // then something comes out". The renderer's own sentence is the useful one here.
            findings.add(Finding.error(check, "page-unrendered", address, null,
                    "This page is published and does not render: " + cannotRender.getMessage(),
                    "Open /sites/" + site.getFurl() + "/default/en-us" + url + " — the route "
                            + "answers with the same problem document, which names the repair."));
            return 0;
        }
        // SH521 — the two reads that already held the finding, joined to a judgement. Both are
        // computed from the bytes just rendered, so this is a threshold rather than an analysis.
        emptyPage(check, address, html, findings);
        emptyImageSources(check, address, html, findings);
        unstyledPage(check, address, html, findings);
        // SH851 — the same question on the other half of the vocabulary, and the half a modern
        // stylesheet actually uses: 3,005 `var()` calls against 205 class names on the replica
        // where nothing noticed 231 dangling references becoming 958.
        danglingTokens(check, address, html, findings);
        // SH694 — the same bytes, one question further: not "is this class defined" but "by which
        // sheet", which is what makes a page serving two visual languages sayable.
        twoThemes(check, address, html, findings);
        Set<String> published = scan.publishedByUrl()
                .getOrDefault(site.getId(), Map.of()).keySet();
        Set<String> seen = new LinkedHashSet<>();
        int references = 0;
        for (ShPreviewLinks.Reference reference : ShPreviewLinks.referencesIn(html, url)) {
            // Per distinct resolved path: the same broken logo in a header on one page is one fact,
            // and forty copies of it is a report nobody reads.
            if (!seen.add(reference.asset() + " " + reference.path())) {
                continue;
            }
            references += 1;
            brokenReference(check, address, reference, site, published, declared, loaded)
                    .ifPresent(findings::add);
        }
        return references;
    }

    /** The finding for one reference, or nothing when this instance would serve it. */
    private Optional<Finding> brokenReference(String check, String address,
                                              ShPreviewLinks.Reference reference, ShSite site,
                                              Set<String> published, ShFrontEndRoutes declared,
                                              Set<String> loaded) {
        String path = pathOnly(reference.path());
        Optional<ShPostUnified> asset = siteAssets.findInSite(site.getId(), path, ShPostState.PUBLISHED);
        if (asset.isPresent()) {
            loaded.add(asset.get().getId());
            return Optional.empty();
        }
        if (reference.asset()) {
            return Optional.of(Finding.error(check, "page-reference-broken", address, null,
                    "The rendered page asks for '" + reference.raw() + "', which resolves to "
                            + path + " — and this site serves nothing there.",
                    "Upload the bytes at that path (shio convert uploads a capture's assets at the "
                            + "source's own paths), or fix the reference in the markup that emits it."));
        }
        if (published.contains(path) || published.contains(ShAddress.normalizePath(path))
                || declared.declares(site.getName(), path)) {
            return Optional.empty();
        }
        return Optional.of(Finding.error(check, "page-link-broken", address, null,
                "The rendered page links to '" + reference.raw() + "', which resolves to " + path
                        + " — and this site publishes no page there.",
                "Publish the target, fix the link, or declare the path as the front end's "
                        + "(shio remember " + ShFrontEndRoutes.MEMORY_KEY + " \"" + path
                        + "\" --site " + site.getName() + ")."));
    }

    /**
     * A published page that renders to nothing (SH521).
     *
     * <h2>The fact nobody joined to a judgement</h2>
     *
     * <p>{@code verify} answered <b>ok</b> on a page that had lost its hero, its product grid and
     * its footer. At the same moment {@code GET /agent/render} answered {@code links: 0, images: 0}
     * where the intact version answers {@code links: 11, images: 1}. <b>Shio knew.</b> The read held
     * the finding and the gate consulted neither, because every check resolved against the
     * <em>tree</em> — does a furl exist, is a {@code File} addressable, does a layout name a theme —
     * and all of that is true of a page that is empty.</p>
     *
     * <p>That is the shape of every defect this run produced: not a missing fact, a fact nobody
     * joined to a judgement. {@code unstyledClasses} is a finding written as a list and
     * {@code links: 0} one written as a number, and {@link ShHtmlOutline} computes both already.</p>
     *
     * <h2>The threshold, and why it is four conditions rather than one</h2>
     *
     * <p>A page with no heading, no link, no image <b>and</b> almost no words is empty in every
     * sense a reader has. Any one of those alone is a page somebody may have meant: a landing page
     * that is one image, a legal notice that is one block of prose. All four at once is the SH520
     * symptom — a `Page` holding a title and nothing else — and nothing else produces it.</p>
     */
    private static void emptyPage(String check, String address, String html, List<Finding> findings) {
        ShHtmlOutline.Outline outline = ShHtmlOutline.of(html);
        if (!outline.headings().isEmpty() || !outline.links().isEmpty()
                || !outline.images().isEmpty() || outline.words() >= EMPTY_PAGE_WORDS) {
            return;
        }
        findings.add(Finding.error(check, "page-empty", address, null,
                "This page is published and renders with no heading, no link, no image and "
                        + outline.words() + " word(s) — there is nothing on it to read.",
                "Check what composes it: a Page with `sections` the site does not hold, or a "
                        + "layout whose slots resolve to nothing, renders exactly this. "
                        + "GET /api/v2/agent/render?address=" + address + " shows the same counts."));
    }

    /**
     * A page drawing an image whose source rendered empty (SH1266).
     *
     * <p>SH1265 put {@code src=""} on every card of a site and this service stayed green: the
     * reference walk skips an empty attribute because it is not a reference, and {@code a11y} reads
     * {@code alt}, which was present. One finding per page, not per element: a template field that
     * resolves to nothing does it on every card, and one sentence with the count is the repair.</p>
     */
    private static void emptyImageSources(String check, String address, String html,
                                          List<Finding> findings) {
        List<String> empty = ShHtmlOutline.emptySources(html);
        if (empty.isEmpty()) {
            return;
        }
        List<String> named = empty.stream().distinct().limit(4).toList();
        findings.add(Finding.error(check, "page-image-empty", address, null,
                "This page renders " + empty.size() + " image source(s) that are empty, so a "
                        + "visitor sees a broken image and the browser requests the page again: "
                        + String.join(", ", named) + (empty.size() > named.size() ? ", …" : "") + ".",
                "The template reads a field that resolved to nothing there: a misspelled or "
                        + "shadowed field name, an image field left blank, or an asset that is not "
                        + "published. GET /api/v2/agent/render?address=" + address
                        + " shows the page as rendered."));
    }

    /**
     * A page drawing with class names its own stylesheet does not define (SH521).
     *
     * <h2>Advisory in the read, a finding only where it is knowable</h2>
     *
     * <p>{@link ShHtmlAppearance} deliberately reports {@code unstyledClasses} as advisory rather
     * than as a lint, and its reasoning is exactly right: a page may load a stylesheet this instance
     * has never seen — the 2018 {@code bootstrap-site} package is full of CDN Bootstrap classes —
     * so a rule over it would fire dozens of times on the one real example in the repository.</p>
     *
     * <p>The guard is what makes the same list a finding without inheriting that: <b>only when the
     * page loads no external stylesheet at all</b>. Then the CSS the document carries is the whole
     * of what styles it, and a class it does not define is provably drawn by nothing. That is the
     * measured case — an authorable replica whose 1,062,141 bytes of captured theme met eight
     * {@code shio-*} names, and a page carrying nine classes nothing styled while the gate said
     * ok — and it is the one case where the answer is certain.</p>
     *
     * <p>A <b>warning</b>, not an error: the premise is provable and the consequence is
     * presentational, and a class used only by a script is a real thing to have. That is the line
     * this service's own doctrine draws, applied honestly rather than in the safe direction.</p>
     */
    /**
     * A page presenting its chrome in one visual language and its sections in another (SH694).
     *
     * <h3>Why here and not on the digest</h3>
     *
     * <p>SH694 put the <em>fact</em> on the digest — each region's {@code styledBy}, which sheets
     * style what it draws — and the judgement here, because a digest describes and this service is
     * what gates. The same split {@code page-unstyled} uses one method down, for the same reason.</p>
     *
     * <p>The finding is <b>disjointness</b>, not "two sheets exist": a page whose header and
     * sections both draw from {@code style[1]} and {@code style[2]} is one design in two files,
     * which is ordinary. What is not is a replica whose captured chrome and authored sections
     * share <em>none</em> — that is the second of the two defects one glance at the viglet.org
     * home page showed and nothing Shio returned could state (SH687).</p>
     *
     * <p>A warning: two stylesheets with nothing in common is how a mid-migration site legitimately
     * looks, and the curator is who decides whether it is finished. The instrument's job is that
     * they are told rather than asked to notice.</p>
     */
    private static void twoThemes(String check, String address, String html,
                                  List<Finding> findings) {
        Map<String, Set<String>> bySheet = ShHtmlAppearance.definedBySheet(html);
        if (bySheet.size() < 2) {
            return;
        }
        List<ShHtmlRegions.Region> regions = ShHtmlRegions.of(html, List.of(), bySheet);
        Set<String> chrome = new java.util.TreeSet<>();
        Set<String> authored = new java.util.TreeSet<>();
        for (ShHtmlRegions.Region region : regions) {
            (ShHtmlRegions.SECTIONS.equals(region.markup()) ? authored : chrome)
                    .addAll(region.styledBy());
        }
        if (chrome.isEmpty() || authored.isEmpty() || !java.util.Collections.disjoint(chrome, authored)) {
            return;
        }
        findings.add(Finding.warning(check, "two-themes", address, null,
                "This page's kept markup and its authored sections share no stylesheet: the chrome "
                        + "is styled by " + String.join(", ", chrome) + " and the sections by "
                        + String.join(", ", authored)
                        + ", so a visitor sees two visual languages in one document.",
                "Either give the sections the theme the chrome uses, or convert the remaining blocks "
                        + "so the whole page is authored. GET /api/v2/agent/render?address=" + address
                        + " returns each region's styledBy under regions."));
    }

    private static void unstyledPage(String check, String address, String html,
                                     List<Finding> findings) {
        ShHtmlAppearance.Appearance appearance = ShHtmlAppearance.of(html);
        if (!appearance.externalStylesheets().isEmpty() || appearance.unstyledClasses().isEmpty()) {
            return;
        }
        List<String> named = appearance.unstyledClasses().stream().limit(6).toList();
        findings.add(Finding.warning(check, "page-unstyled", address, null,
                "This page draws with " + appearance.unstyledClasses().size()
                        + " class name(s) its own stylesheet does not define, and it loads no other: "
                        + String.join(", ", named)
                        + (appearance.unstyledClasses().size() > named.size() ? ", …" : "") + ".",
                "Either the layout names a theme written for a different markup, or the markup was "
                        + "written against class names the theme does not have. "
                        + "GET /api/v2/agent/render?address=" + address
                        + " returns the whole list under appearance.unstyledClasses."));
    }

    /**
     * A page reading custom properties its own stylesheet never declares (SH851).
     *
     * <h2>The instrument the regression walked past</h2>
     *
     * <p>SH850 took a replica's dangling {@code var()} references from <b>231 to 958</b> and every
     * instrument passed it: this service answered {@code 0 error(s), 2 warning(s)} on both rounds,
     * {@code diff} reported the same 18 losses, {@code judge} found nothing new, {@code audit}
     * rendered the page and returned no error. A person looking at it is what noticed.</p>
     *
     * <p>{@code page-unstyled} one method up asks the same question about <b>class names</b>, and a
     * stylesheet built on design tokens keeps most of its meaning elsewhere — 3,005 {@code var()}
     * calls against 205 classes on that page. A {@code var()} with no declaration and no fallback
     * is not a wrong value: the declaration is invalid at computed-value time and the element takes
     * the inherited or initial one, so it is colour, spacing and animation simply gone.</p>
     *
     * <h2>The same guard, and the same grade, for the same reasons</h2>
     *
     * <p><b>Only when the page loads no external stylesheet</b>, exactly as {@code page-unstyled}:
     * with one, the missing declaration may be in a sheet this instance has never seen, and the
     * rule would fire on every site that loads a framework from a CDN. <b>A warning</b>, because the
     * premise is provable and the consequence is presentational — the line this service's doctrine
     * draws, applied honestly rather than in the safe direction.</p>
     *
     * <p>The occurrence count is in the message beside the distinct one because they are different
     * sentences: 194 names is how much of the palette went, 958 reads is how much of the page.</p>
     */
    private static void danglingTokens(String check, String address, String html,
                                       List<Finding> findings) {
        ShHtmlAppearance.Appearance appearance = ShHtmlAppearance.of(html);
        if (!appearance.externalStylesheets().isEmpty() || appearance.danglingTokens().isEmpty()) {
            return;
        }
        List<String> named = appearance.danglingTokens().stream().limit(6).toList();
        findings.add(Finding.warning(check, "page-dangling-token", address, null,
                "This page reads " + appearance.danglingTokens().size()
                        + " custom propert(ies) its own stylesheet never declares, "
                        + appearance.danglingTokenRefs() + " time(s), and it loads no other: "
                        + String.join(", ", named)
                        + (appearance.danglingTokens().size() > named.size() ? ", …" : "")
                        + ". A var() with no declaration and no fallback makes its declaration "
                        + "invalid, so those elements take the inherited or initial value.",
                "Usually a Theme whose TOKENS and CSS disagree: `shio propose` lifts a declared "
                        + "custom property into TOKENS and removes it from CSS, so a layout that "
                        + "emits the CSS without the compiled token block loses every one of them. "
                        + "GET /api/v2/agent/render?address=" + address
                        + " returns the whole list under appearance.danglingTokens."));
    }

    /** Below this many words, with no heading, link or image, a page has nothing on it. */
    private static final int EMPTY_PAGE_WORDS = 20;

    /** A resolved reference without its query string or fragment — what a route is keyed by. */
    private static String pathOnly(String path) {
        int query = path.indexOf('?');
        int fragment = path.indexOf('#');
        int cut = query < 0 ? fragment : (fragment < 0 ? query : Math.min(query, fragment));
        return cut < 0 ? path : path.substring(0, cut);
    }

    // --- SH141: proof from the delivery side ---------------------------------

    /**
     * Fetches what the content claims and reports what the site actually serves.
     *
     * <h2>Why this is not a crawler</h2>
     *
     * <p>It follows no links it did not already know about, discovers nothing, and visits
     * each URL once. The subject list comes from the content graph — the published pages in
     * scope, plus the links SH100 could not resolve — so the pass is bounded by the content
     * rather than by the site's shape, and it terminates. A crawler would be a different
     * thing with a different failure mode: unbounded, and capable of hammering a front end
     * that never asked to be verified.</p>
     *
     * <h2>Bounded three ways, and every cut is reported</h2>
     *
     * <p>A page count ({@code max-fetches}), a wall-clock budget ({@code fetch-budget-millis}),
     * and fail-fast per origin: the <em>first</em> request to a site that does not answer ends
     * that site's pass with one finding, because "the front end is down" is one fact and fifty
     * copies of it is a report nobody reads. All three write a {@code note}, because a
     * shortened list of defects reads exactly like a shorter list of defects (SH138).</p>
     *
     * <h2>The cost this used to accept, and no longer does (SH206)</h2>
     *
     * <p>The fetches happened inside the report's read transaction, holding a connection for as
     * long as the network pass took. Bounded three ways, which made it safe rather than right: on
     * a slow front end a verification call still occupied a connection for fifteen seconds doing
     * no database work at all. This method is now the <b>gather</b> only — it reads entities and
     * yields plain Strings — and {@link #verify} fetches after the transaction has committed.</p>
     *
     * <p>The bounds stay, for the reason §IX.7 asked them to: the transaction was never their only
     * justification, and a verification call that takes four minutes is a hang either way.</p>
     */
    private Delivery delivery(Scan scan, List<Finding> findings, List<Unresolved> unproven,
                              boolean explicit) {
        String check = ShAgentCheck.DELIVERY.wireName();
        List<ShPostUnified> pages = scan.published().stream()
                .filter(post -> post.getShSite() != null && !isBlank(post.getFurl()))
                .toList();
        Set<String> withSubjects = new LinkedHashSet<>();
        pages.forEach(post -> withSubjects.add(post.getShSite().getId()));
        unproven.forEach(link -> withSubjects.add(link.siteId()));

        Map<String, String> origins = new LinkedHashMap<>();
        // SH206 — resolved here because this is the last place a site entity is in hand. It also
        // replaces `siteById`, which walked `siteAccess.readable()` once per unreachable origin and
        // once per proved page: a repository read per finding, on a list this loop already holds.
        Map<String, String> siteNames = new LinkedHashMap<>();
        for (String siteId : withSubjects) {
            ShSite owner = scan.siteById().get(siteId);
            if (owner == null) {
                continue;
            }
            siteNames.put(siteId, owner.getName());
            if (ShAgentDeliveryProbe.join(owner.getUrl(), "/") == null) {
                findings.add(Finding.warning(check, "site-url-missing",
                        ShAddress.site(owner.getName()).toString(), null,
                        "Site '" + owner.getName() + "' has no front-end URL, so nothing it "
                                + "publishes can be proved from the delivery side.",
                        "Set the site's URL in the console (Sites → " + owner.getName()
                                + "), or run checks=content,routes for this site."));
            } else {
                origins.put(siteId, owner.getUrl());
            }
        }
        if (origins.isEmpty()) {
            if (explicit && !withSubjects.isEmpty()) {
                throw new NoDeliveryOriginException();
            }
            return Delivery.NONE;
        }

        return new Delivery(targets(pages, unproven, origins), siteNames);
    }

    /**
     * The URLs to prove, in a deterministic order.
     *
     * <p>Sorted before the cap applies, so which pages a truncated pass covered is the same
     * on every run (P5) — an unsorted list capped at fifty would prove a different fifty each
     * time and make a red build look intermittent.</p>
     */
    private static List<Target> targets(List<ShPostUnified> pages, List<Unresolved> unproven,
                                        Map<String, String> origins) {
        List<Target> targets = new ArrayList<>();
        for (ShPostUnified post : pages) {
            String siteId = post.getShSite().getId();
            URI url = ShAgentDeliveryProbe.join(origins.get(siteId),
                    ShAddress.normalizePath(post.getFurl()));
            if (url != null) {
                targets.add(new Target(url.toString(), siteId, address(post), null, null, false));
            }
        }
        for (Unresolved link : unproven) {
            URI url = ShAgentDeliveryProbe.join(origins.get(link.siteId()), link.path());
            if (url != null) {
                targets.add(new Target(url.toString(), link.siteId(), link.address(), link.field(),
                        link.href(), link.declared()));
            }
        }
        return targets.stream()
                .sorted(Comparator.comparing(Target::url)
                        .thenComparing(target -> target.address() == null ? "" : target.address())
                        .thenComparing(target -> target.field() == null ? "" : target.field()))
                .toList();
    }

    /**
     * One request per distinct URL, until a bound is reached.
     *
     * <p>Distinct, because the same link on twelve pages is twelve findings but one fact about
     * the site, and asking twelve times would spend the budget proving what the first answer
     * already said.</p>
     */
    private Fetches fetch(List<Target> targets) {
        Map<String, ShAgentDeliveryProbe.Outcome> byUrl = new LinkedHashMap<>();
        Map<String, String> unreachable = new LinkedHashMap<>();
        int max = properties.getVerify().getMaxFetches();
        long deadline = System.nanoTime()
                + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(
                        properties.getVerify().getFetchBudgetMillis());
        boolean exhausted = false;
        for (Target target : targets) {
            if (byUrl.containsKey(target.url()) || unreachable.containsKey(target.siteId())) {
                continue;
            }
            if (byUrl.size() >= max || System.nanoTime() > deadline) {
                exhausted = true;
                break;
            }
            URI uri = ShAgentDeliveryProbe.absolute(target.url());
            if (uri == null) {
                continue;
            }
            // Status only, and SH302 measured why the body would buy nothing: the `origins` map above
            // holds a site only when it has its own front-end URL, so every page this pass fetches is
            // rendered by somebody else's front end and carries no `data-shio-*` to count.
            ShAgentDeliveryProbe.Outcome outcome = deliveryProbe.status(uri);
            if (outcome.failure() != null) {
                // Fail fast per origin: one unreachable front end is one finding, and the
                // remaining pages of that site are unknowable rather than broken.
                unreachable.put(target.siteId(), outcome.failure());
            } else {
                byUrl.put(target.url(), outcome);
            }
        }
        int skipped = 0;
        for (Target target : targets) {
            if (!byUrl.containsKey(target.url()) && !unreachable.containsKey(target.siteId())) {
                skipped++;
            }
        }
        return new Fetches(byUrl, unreachable, exhausted, skipped);
    }

    /**
     * The findings the fetches imply — one per target, plus one per unreachable origin.
     *
     * <p>SH206 — runs <b>after</b> the read transaction has committed, so it touches no entity.
     * {@code siteNames} is what that costs: the only thing this needed a site for was its name,
     * resolved once per site in the gather instead of once per finding through the repository.</p>
     */
    private static void report(String check, List<Target> targets, Fetches fetches,
                               Map<String, String> siteNames, List<Finding> findings,
                               List<String> notes) {
        fetches.unreachable().forEach((siteId, failure) -> {
            String owner = siteNames.get(siteId);
            findings.add(Finding.error(check, "delivery-unreachable",
                    owner == null ? null : ShAddress.site(owner).toString(), null,
                    "The front end for this site did not answer (" + failure + "), so none of "
                            + "its routes could be proved.",
                    "Start the front end, or correct the site's URL. Until it answers, run "
                            + "checks=content,routes — those need no network."));
        });
        for (Target target : targets) {
            ShAgentDeliveryProbe.Outcome outcome = fetches.byUrl().get(target.url());
            if (outcome == null || outcome.served()) {
                continue;
            }
            findings.add(target.href() == null
                    ? Finding.error(check, "route-not-served", target.address(), null,
                            "This page is published but " + target.url() + " answers "
                                    + outcome.status() + " — a visitor cannot reach it.",
                            "Deploy the front end's route for this page, or unpublish it until "
                                    + "the route exists. A published page nobody can open is the "
                                    + "failure the content graph cannot see.")
                    : Finding.error(check, "link-broken", target.address(), target.field(),
                            "Link '" + target.href() + "' matches no published URL and "
                                    + target.url() + " answers " + outcome.status()
                                    + (target.declared()
                                            ? " — it is declared as a front-end route, and the "
                                                    + "front end does not serve it."
                                            : "."),
                            target.declared()
                                    ? "Fix the link, serve the declared route, or drop the "
                                            + "pattern from the '" + ShFrontEndRoutes.MEMORY_KEY
                                            + "' note if the front end no longer owns it."
                                    : "Fix the link, publish the page it means, or add the route "
                                            + "to the front end."));
        }
        deliveryCoverage(targets, fetches, siteNames, notes);
        if (fetches.exhausted() && fetches.skipped() > 0) {
            notes.add("The delivery check stopped after " + fetches.byUrl().size()
                    + " request(s) and left " + fetches.skipped()
                    + " unproved — raise shio.agent.verify.max-fetches or "
                    + "shio.agent.verify.fetch-budget-millis, or narrow the run with folder=.");
        }
    }

    /** The site behind an id, for a finding that names a site rather than a post. */
    /**
     * SH302 — what the pass actually proved, said out loud.
     *
     * <h3>The stronger check this task proposed is not available, and that is the finding</h3>
     *
     * <p>SH141 discards the body; since SH299 that body could be counted for defect markers, turning
     * "the front end answered 200" into "…with a page that is missing a region". Measured, the idea
     * dissolves: the {@code origins} map is populated <b>only</b> for a site that has its own
     * front-end URL — a site without one gets {@code site-url-missing} and is never fetched, and
     * naming {@code delivery} for it is a teaching 400. So <em>every</em> page this pass fetches is
     * rendered by somebody else's front end, from the CDA, and carries no {@code data-shio-*} at all.
     * A marker count here could not have found a defect; it could only ever have printed zero, which
     * is the "a check that covered nothing while answering ok" failure SH141 itself warns about.</p>
     *
     * <p>What was worth taking from the task is the other half of its own question — "or a note saying
     * which pages were checked this way". A clean delivery pass used to say <b>nothing</b>: findings
     * appear only on failure, so a run that proved forty pages and a run that proved none read
     * identically. Coverage is now a note, per site, because "silence is not evidence" is the thing
     * this check is most easily wrong about.</p>
     */
    private static void deliveryCoverage(List<Target> targets, Fetches fetches,
                                         Map<String, String> siteNames, List<String> notes) {
        Map<String, Integer> provedBySite = new TreeMap<>();
        Set<String> counted = new LinkedHashSet<>();
        for (Target target : targets) {
            // A page's own URL only: a link target is somebody else's page, and it is proved as a
            // link rather than as coverage of this site's pages.
            if (target.href() != null || !counted.add(target.url())) {
                continue;
            }
            ShAgentDeliveryProbe.Outcome outcome = fetches.byUrl().get(target.url());
            if (outcome == null || !outcome.served()) {
                continue;
            }
            String name = siteNames.getOrDefault(target.siteId(), target.siteId());
            provedBySite.merge(name, 1, Integer::sum);
        }
        provedBySite.forEach((site, proved) -> notes.add("The delivery check proved " + proved
                + " published page(s) of '" + site + "' by fetching them. What it proves is that the "
                + "route answers: the page's own markup is the content checks' subject, and an "
                + "external front end carries none of Shio's render markers to look at."));
    }

    /**
     * A link SH100 could not resolve, held for the delivery check to settle.
     *
     * @param path     the normalized, query-stripped path — what gets fetched
     * @param href     the link exactly as it was written, which is what a fix must edit
     * @param declared the site declares this path as the front end's (SH141)
     */
    private record Unresolved(String siteId, String path, String href, String address,
                              String field, boolean declared) {
    }

    /**
     * One URL to prove.
     *
     * @param href {@code null} for a published page's own URL; the written link otherwise —
     *             which is also what decides whether a non-2xx is {@code route-not-served}
     *             or {@code link-broken}
     */
    private record Target(String url, String siteId, String address, String field, String href,
                          boolean declared) {
    }

    /**
     * What the network pass learned.
     *
     * @param exhausted a bound stopped the pass rather than the target list running out
     * @param skipped   targets left unproved, so the note can say how many
     */
    // SH206 — a fifth component, `siteIds`, was carried in and never read by anything. Removed
    // while restructuring this call rather than left: the phase split gives the record a new
    // journey across a transaction boundary, and a dead field is one more thing to reason about
    // on the way.
    private record Fetches(Map<String, ShAgentDeliveryProbe.Outcome> byUrl,
                           Map<String, String> unreachable, boolean exhausted, int skipped) {
    }

    // --- SH103: recent asynchronous failures --------------------------------

    /**
     * The incident log as findings.
     *
     * <p>A failed webhook is a defect in the same sense a broken link is — the site is
     * not what the content says it is — so it belongs in the report an agent already
     * reads rather than in a second call it has to remember to make. The detailed view
     * (first/last seen, counts) stays on {@code GET /api/v2/agent/diagnostics}.</p>
     *
     * <h2>Always a warning, never an error (SH330)</h2>
     *
     * <p>These findings were {@code error} for every kind but {@code transform}, which made
     * {@code shio verify} exit 1 <b>forever</b> on any instance that had ever returned a 5xx:
     * SH144 made the record durable and there is no verb to resolve one, so a single past
     * incident was a permanently red gate on content that is provably clean. That was observed
     * directly — the SH325 500 held {@code ok:false} on a healthy site until
     * {@code checks=content,routes} was named explicitly, which is a gate people bypass.</p>
     *
     * <p>The durability is right; the severity was not. SH99's rule decides it: {@code ok} is
     * false only for what the CMS can prove from inside itself <em>now</em>, and an incident is
     * by construction something that already happened — the instance cannot tell from the row
     * whether the cause still exists. That is the definition of a warning, and {@code warnings}
     * is on the payload for a caller that wants to gate on them anyway. SH142's baseline is not
     * the escape hatch either: it keys on {@code (rule, address, field)} and these findings carry
     * no address.</p>
     */
    private void diagnostics(List<Finding> findings) {
        String check = ShAgentCheck.DIAGNOSTICS.wireName();
        for (ShAgentIncidentLog.Incident incident : incidentLog.recent(0)) {
            String repeats = incident.count() > 1 ? " (x" + incident.count() + ")" : "";
            findings.add(Finding.warning(check, incident.kind().wireName(),
                    null,
                    incident.subject(),
                    incident.message() + repeats,
                    incident.fix() != null ? incident.fix()
                            : "See GET /api/v2/agent/diagnostics for when it started and how often."));
        }
    }

    /**
     * The posts a reference field names by friendly URL (SH154).
     *
     * <h2>Why this is the check that makes composition safe</h2>
     *
     * <p>A page built the SH95 way <em>is</em> its list of sections. A URL in that list that
     * resolves to nothing renders as a hole, and until this rule existed {@code verify} answered
     * {@code ok} over it — the one defect the vocabulary can produce that nothing else could see.</p>
     *
     * <h2>Two properties kept from the id-shaped rule</h2>
     *
     * <p>An <b>unpublished</b> target on a published page is the existing
     * {@code unpublished-dependency} case, not a new one: the editor sees both drafts and the
     * visitor sees the hole, which is the same failure whichever way the reference is spelled.</p>
     *
     * <p>And a target this credential <b>cannot read</b> is not dangling. The scan only holds the
     * readable sites, so a miss is re-checked against the repository — otherwise the SH2
     * allow-list would manufacture defects, and a lint that reports content the caller is not
     * allowed to see is worse than one that stays quiet about it.</p>
     */
    private void sectionFindings(String check, ShPostUnified post, String address, String field,
                                 JsonNode value, ShPostTypeAttr attr, Scan scan,
                                 List<Finding> findings) {
        List<String> urls = ShPostReferenceFields.urls(value);
        if (urls.isEmpty()) {
            return;
        }
        String siteId = post.getShSite() == null ? null : post.getShSite().getId();
        String wantedType = ShPostReferenceFields.referencedPostType(attr, objectMapper);
        List<String> accepted = acceptedSections(post, scan);
        Set<String> undeclared = new LinkedHashSet<>();
        for (String url : urls) {
            Optional<ShPostUnified> target = referenced(siteId, url);
            if (target.isEmpty()) {
                findings.add(Finding.error(check, "dangling-reference", address, field,
                        "Field '" + field + "' names '" + url + "', and no post in this site has "
                                + "that URL — the page renders a hole where that section should be.",
                        "Point it at a post from GET /api/v2/agent/find, create the missing one, "
                                + "or drop the entry. A renamed post keeps its URL only if it was "
                                + "moved with a post.move op."));
                continue;
            }
            ShPostUnified section = target.get();
            if (wantedType != null && section.getShPostType() != null
                    && !wantedType.equalsIgnoreCase(section.getShPostType().getName())) {
                findings.add(Finding.error(check, "wrong-reference-type", address, field,
                        "Field '" + field + "' names '" + url + "', which is a "
                                + section.getShPostType().getName() + " and not a " + wantedType + ".",
                        "Point it at a " + wantedType + ", or widen the field's postType setting."));
                continue;
            }
            if (!accepted.isEmpty() && section.getShPostType() != null
                    && !accepted.contains(section.getShPostType().getName())) {
                undeclared.add(section.getShPostType().getName());
            }
            if (scan.publishedIds().contains(post.getId())
                    && !scan.publishedIds().contains(section.getId())) {
                findings.add(Finding.error(check, "unpublished-dependency", address, field,
                        "This page is published and names '" + url + "', which is not — a visitor "
                                + "gets the hole the editor cannot see.",
                        "Publish it with a post.publish op, or take this page down until it is."));
            }
        }
        // SH1106 — once per type, not per entry: three FAQ blocks on a page that declares none is
        // one decision somebody has to make, and three findings would read as three.
        for (String type : undeclared) {
            findings.add(Finding.warning(check, "section-not-accepted", address, field,
                    "Field '" + field + "' composes a " + type + ", and this page's layout accepts "
                            + String.join(", ", accepted) + " — the page renders it anyway, "
                            + "against its own template's declaration.",
                    "Replace it with one of " + String.join(", ", accepted) + ", or add " + type
                            + " to the sh-accepts of the element that composes the sections."));
        }
    }

    /**
     * SH1106 — what this page's own templates say may be composed on it, read once per site and type.
     *
     * <p>A <b>warning</b> and not a refusal on write: the list lives in a layout, and layouts are
     * edited. Refusing would make an existing page unwritable the moment somebody narrowed its
     * template — a content change refused over an edit nobody connected to it. The declaration is a
     * template's statement about composition, not an authorization boundary.</p>
     */
    private List<String> acceptedSections(ShPostUnified post, Scan scan) {
        ShSite site = post.getShSite();
        ShPostType type = post.getShPostType();
        if (site == null || type == null) {
            return List.of();
        }
        return scan.acceptedByType().computeIfAbsent(site.getId() + "|" + type.getName(),
                key -> pageRenderService.acceptedSections(site, type.getName()));
    }

    /**
     * The post a URL names in this site, in either state.
     *
     * <p>Draft-preferred is not what matters here — existence is — so both states are asked for,
     * and the repository is asked directly rather than the scan, because the scan holds only what
     * this credential may read (see the class note on the allow-list).</p>
     *
     * <p>SH583 — every spelling, through the shared generator. This is the reader whose
     * disagreement is hardest to see: a link that resolves in {@code /preview} and misses here
     * reports {@code page-link-broken} against a page that exists, which reads as damage the
     * conversion caused.</p>
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

    /**
     * SH773 — a live vocabulary type whose copy of the model has lost the key three readers need.
     *
     * <h2>The measurement that filed this</h2>
     *
     * <p>SH767 was filed on a replica where {@code shio verify} answered {@code ok} over a page
     * whose sections resolved to nothing. Measuring it before fixing it found the report already
     * right — a probe writing an address into a <em>declared</em> relation reports
     * {@code dangling-reference} with no fix applied. The quiet run had another cause, and the same
     * probe found it: written against a {@code sections} field carrying <b>no</b>
     * {@code widgetSettings}, verify says nothing. Not about the address form, and not about a
     * section that genuinely does not exist.</p>
     *
     * <p>{@code ShPostReferenceFields} is the gate. SH154's rule — a {@code Multi Select} is tags
     * unless the model says otherwise — is right, and it makes
     * {@code {"multiselect":{"references":"post"}}} the one fact <b>three</b> readers depend on: the
     * dangling lint, the SH101 digest, and SH767's write check. A type that has lost it is not
     * broken content; it is content that cannot be checked, which is worse and is the only reason
     * this rule exists.</p>
     *
     * <h2>Why an instance gets into that state, and cannot get out by itself</h2>
     *
     * <p>{@link ShVocabularyPostTypes#ensure} <b>provisions and never upserts</b> (SH152),
     * deliberately: a curator who added a field to their {@code SectionHero} keeps it, and that is
     * what lets a conversion re-run against an edited site. The cost is that an instance carrying a
     * {@code Page} from before the key existed keeps the blind field forever, and nothing said so.
     * Every development box that ran the replication chain early is in that state.</p>
     *
     * <h2>What it reports, and what it deliberately does not</h2>
     *
     * <p>Only the <b>silent</b> drift: a field the catalogue declares as holding post references
     * whose live copy does not. A missing field is already loud — SH168 refuses the write naming it
     * — and a curator's <em>extra</em> field is the thing SH152 exists to protect. Widening this to
     * "every difference from the catalogue" would report a Combo Box somebody legitimately
     * re-worded, on every run, which is the noise SH551 and SH142 both warn about.</p>
     *
     * <p>Scoped to the types this scan's posts actually use, from data already loaded — a site that
     * composes no sections is told nothing about a {@code Page} it does not have.</p>
     */
    private void vocabularyDrift(String check, Scan scan, List<Finding> findings) {
        Map<String, ShPostType> inUse = new LinkedHashMap<>();
        for (ShPostUnified post : scan.posts()) {
            ShPostType type = post.getShPostType();
            if (type != null && type.getName() != null) {
                inUse.putIfAbsent(type.getName(), type);
            }
        }
        for (Map.Entry<String, ShPostType> entry : inUse.entrySet()) {
            Optional<ShPostTypeDef> shipped = vocabularyPostTypes.get(entry.getKey());
            if (shipped.isEmpty()) {
                continue;
            }
            for (ShPostTypeAttrDef field : shipped.get().fields()) {
                if (!ShPostReferenceFields.referencesPosts(field, objectMapper)) {
                    continue;
                }
                declared(entry.getValue()).stream()
                        .filter(live -> field.name().equals(live.getName()))
                        .filter(live -> !ShPostReferenceFields.referencesPosts(live, objectMapper))
                        .forEach(live -> findings.add(driftFinding(check, entry.getKey(), field)));
            }
        }
    }

    private Finding driftFinding(String check, String typeName, ShPostTypeAttrDef field) {
        return Finding.error(check, "vocabulary-drift", "id:" + typeName, field.name(),
                "This instance's '" + typeName + "' declares '" + field.name()
                        + "' without `references: post`, and the shipped model declares it with. "
                        + "Three readers key on it — the dangling-reference rule, the render digest "
                        + "and the batch's relation check — so a page naming a section that does "
                        + "not exist reports nothing, renders a hole, and looks authored.",
                "PUT /api/v2/post-type/" + typeName + " with the field's widgetSettings set to "
                        + "{\"multiselect\":{\"references\":\"post\"}}. Provisioning will not fix "
                        + "it: render.provision creates a type and never edits one (SH152), which "
                        + "is what protects a field you added.");
    }

    /**
     * Values stored under keys the post-type does not declare (SH168).
     *
     * <p>The write path refuses to create one now, so what this reports is what is already
     * there: content the console cannot render, {@code shio types} cannot type and the CDA does
     * not deliver — invisible until something looked. It is the other half of SH168, and the
     * reason the write grandfathers existing keys instead of making such a post unwritable.</p>
     *
     * <p><b>An error, not a warning.</b> The CMS can prove this one from the inside: the model
     * is right there. It is the SH99 rule applied honestly, even though it means an instance
     * carrying a pre-SH168 mistake goes red — which is the point, since nothing said so before.
     * "Known and accepted" is SH142's job, not a softer severity here.</p>
     */
    private void undeclaredFields(String check, ShPostUnified post, String address,
                                  JsonNode data, List<Finding> findings) {
        if (data == null || !data.isObject() || data.isEmpty()) {
            return;
        }
        Set<String> allowed = declaredNames(post.getShPostType());
        if (allowed.isEmpty()) {
            // A post-type declaring no fields cannot say anything about these keys, and
            // reporting every one of them would blame the content for a modelling gap.
            return;
        }
        // SH474 — a system type has no `PUT` door: ShPostTypeAdminService refuses one (409), and
        // its additive `mergeMissingFields` refuses one too. Naming an endpoint that answers 409 is
        // a fix that is not one (P6), and it is the instruction the operator got for all 66 findings
        // the File type produced on a replica of its own assets.
        boolean system = post.getShPostType() != null && post.getShPostType().getSystem() == 1;
        String typeName = name(post.getShPostType());
        // SH872 — a key this post's OWN markup annotates is declared by that markup, which is the
        // storage SH868 proposed. Without it a curator's first edit through the Universal Editor
        // turns an annotated page red here, on every annotated block they touch.
        Set<String> annotated = ShAnnotatedFields.of(data);
        for (String key : data.propertyNames()) {
            if (!allowed.contains(key) && !annotated.contains(key)) {
                findings.add(Finding.error(check, "unknown-field", address, key,
                        "This post stores '" + key + "', which post-type '" + typeName
                                + "' does not declare, so nothing renders or delivers it.",
                        system
                                ? "'" + typeName + "' is a system post-type: its model is "
                                        + "code-owned, so PUT /api/v2/post-type/" + typeName
                                        + " answers 409. Upgrade to a build that declares '" + key
                                        + "', or drop the key with a post.upsert that omits it. On "
                                        + "a File post, uploading or replacing any asset re-runs "
                                        + "the provisioning that adds a field the model is missing."
                                : "Add '" + key + "' to the post-type (PUT /api/v2/post-type/"
                                        + typeName + "), or drop the key with a post.upsert that "
                                        + "omits it."));
            }
        }
    }

    /** Every field name a post-type declares, nested ones included. */
    private static Set<String> declaredNames(ShPostType postType) {
        Set<String> names = new LinkedHashSet<>();
        collectNames(postType == null ? null : postType.getShPostTypeAttrs(), names);
        return names;
    }

    private static void collectNames(Set<ShPostTypeAttr> attrs, Set<String> into) {
        if (attrs == null) {
            return;
        }
        for (ShPostTypeAttr attr : attrs) {
            if (attr.getName() != null && !attr.getName().isBlank()) {
                into.add(attr.getName());
            }
            collectNames(attr.getShPostTypeAttrs(), into);
        }
    }

    private static String name(ShPostType postType) {
        return postType == null || postType.getName() == null ? "?" : postType.getName();
    }

    // --- helpers ------------------------------------------------------------

    /** Top-level attributes of a post-type, ordinal-ordered so findings are stable. */
    private static List<ShPostTypeAttr> declared(ShPostType postType) {
        if (postType == null || postType.getShPostTypeAttrs() == null) {
            return List.of();
        }
        return postType.getShPostTypeAttrs().stream()
                .filter(attr -> attr.getShParentPostTypeAttr() == null)
                .sorted(Comparator.comparingInt(ShPostTypeAttr::getOrdinal)
                        .thenComparing(attr -> attr.getName() == null ? "" : attr.getName()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    /**
     * Whether a referenced id is a post at all.
     *
     * <p>Asked only for ids the scan did not already see, because a reference may
     * legitimately point into a site this credential cannot read — and calling that
     * dangling would be a lie told by the allow-list rather than by the content.</p>
     */
    private boolean existsPost(String postId) {
        return !shPostUnifiedRepository.findById(postId).isEmpty();
    }

    private static String widgetName(ShPostTypeAttr attr) {
        return attr.getShWidget() == null || attr.getShWidget().getName() == null
                ? "" : attr.getShWidget().getName();
    }

    /**
     * SH140 — the lenient parse lives in {@link ShPostLinks} now, so the tolerance of a malformed
     * payload is decided once. Two copies would also have been two entries in SH270's silent-catch
     * inventory for one decision, which is the population read as twice its size.
     */
    private JsonNode data(ShPostUnified post) {
        return ShPostLinks.data(objectMapper, post);
    }

    /**
     * A {@code File} post's own {@code FILE} field, which is a name and not an address (SH470).
     *
     * <h2>Eighty-four false errors on a replica that works</h2>
     *
     * <p>{@code verify --site VigletOrg} reported <b>84 errors</b> on a replica whose every asset
     * answers 200, and all of them were this one rule on this one field:
     * {@code ✗ dangling-reference · FILE — Field 'FILE' points at post
     * inter-greek-wght-normal-CkhJZR-_.woff2, which does not exist.} It does not exist because it is
     * not a post. The eighteen assets a capture uploads are eighteen {@code File} posts, and each one
     * stores its own filename here — {@link #fileNameOf} reads exactly this field as exactly that.
     * So the two readers of one field disagreed, and the louder one was wrong.</p>
     *
     * <p><b>A gate that cries wolf is worse than one that stays quiet</b>, and 84 false errors on a
     * run that also carried true ones is past the line where a reader stops looking. It also inverts
     * what {@code verify} is for on this path: the replication checks exist so an agent can prove a
     * replica without a human, and an agent that cannot tell 84 false errors from a real one has to
     * escalate every run.</p>
     *
     * <h2>Why not drop {@code File} from {@code REFERENCE_WIDGETS}</h2>
     *
     * <p>Because the widget is genuinely used both ways. A user field — the seeded
     * {@code Article.FILE}, or the {@code hero} this class's own test asserts on — holds a
     * {@code File} post's <em>id</em>, and it is the only path to {@code missing-asset}. Dropping the
     * widget would silence 84 false errors by deleting a check that finds real ones. What is narrow
     * here is the case, so the exemption is narrow: the {@code File} post-type's own storage field.</p>
     */
    private static boolean isOwnFileName(ShPostUnified post, String field) {
        return FILE_NAME_FIELD.equals(field)
                && post != null
                && post.getShPostType() != null
                && ShSystemPostType.FILE.equals(post.getShPostType().getName());
    }

    /**
     * A post type that is reached by path rather than by friendly URL (SH501).
     *
     * <h2>Three errors on a pixel-identical replica</h2>
     *
     * <p>A fidelity capture's assets are {@code File} posts, and {@code url-missing} fired on every
     * one of them: {@code viglet-logo.png}, {@code inter-latin.woff2},
     * {@code plus-jakarta-sans-latin.woff2} — "published but has no friendly URL, so no route
     * reaches it" — on a run where all three answered <b>200</b>. A {@code File} never has a furl;
     * that is the type's design, and it is why the asset route exists: bytes are served at
     * {@code /file_source/<id>/<name>} and, since SH437, at the path the source used. The rule was
     * asking a content-post question of a type the question does not apply to.</p>
     *
     * <h2>The remedy was worse than the finding</h2>
     *
     * <p>The message handed over a {@code post.move} op to "give it one", which would put a friendly
     * URL on the type defined by not having one — so an agent that trusted the error would corrupt
     * the content model to clear it. A finding whose fix breaks the thing it is checking is worse
     * than silence, because silence at least does not act.</p>
     *
     * <h2>Why the type and not the field</h2>
     *
     * <p>This is SH470's family one rule along — there, {@code File.FILE} was read as an address;
     * here, {@code File} is expected to have a furl — and both scale with the replica: three assets
     * in a small capture, eighteen in the six-page {@code viglet.org} one. Both exemptions are the
     * same narrow shape, keyed on what the post type already declares rather than on the finding, so
     * a {@code File} referenced from a user field is still checked by every rule that applies to
     * it ({@code missing-asset}, {@code dangling-reference} on other fields).</p>
     */
    private static boolean isUrllessByDesign(ShPostUnified post) {
        return post != null
                && post.getShPostType() != null
                && ShSystemPostType.FILE.equals(post.getShPostType().getName());
    }

    private String fileNameOf(ShPostUnified file) {
        JsonNode data = data(file);
        JsonNode name = data.get(FILE_NAME_FIELD);
        if (name != null && name.isString() && !name.asString().isBlank()) {
            return name.asString().trim();
        }
        return isBlank(file.getTitle()) ? null : file.getTitle().trim();
    }

    private static String address(ShPostUnified post) {
        if (post.getShSite() == null || isBlank(post.getFurl())) {
            return "id:" + post.getId();
        }
        return ShAddress.post(post.getShSite().getName(), post.getFurl()).toString();
    }

    private static String folderAddress(Scanned entry) {
        List<String> names = new ArrayList<>();
        for (ShFolder current = entry.folder(); current != null; current = current.getParentFolder()) {
            if (current.getRootFolder() == 1) {
                break;
            }
            names.add(0, current.getName() == null ? "" : current.getName());
        }
        return ShAddress.folder(entry.site().getName(), "/" + String.join("/", names)).toString();
    }

    /** Site-relative: a path this instance could plausibly own. Not {@code //host}. */
    /** SH140 — the same two rules the inbound scan applies, so the two cannot drift apart. */
    private static boolean isInternal(String href) {
        return ShPostLinks.isInternal(href);
    }

    /** A stored file name arrives percent-encoded in markup but not on disk. */
    private static String decodePath(String segment) {
        try {
            return java.net.URLDecoder.decode(segment, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return segment;
        }
    }

    private static boolean isEmpty(JsonNode value) {
        if (value == null || value.isNull()) {
            return true;
        }
        if (value.isString()) {
            return value.asString().isBlank();
        }
        return (value.isArray() || value.isObject()) && value.isEmpty();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Everything the checks read, gathered once.
     *
     * <p>Two tiers, and the distinction is SH143's whole design. {@code posts} / {@code published}
     * / {@code folders} are the <b>subjects</b> — what a scope narrows, and the expensive part
     * (data, post-type, attribute tree). The rest are <b>indexes</b>, always built over every
     * readable site because the rules that read them ask questions a subset cannot answer:
     * whether another post claims a URL, whether a link lands anywhere, whether a folder has
     * content the run did not look at. They are id/url projections, so a full index costs
     * almost nothing next to a full read pass.</p>
     *
     * @param publishedByUrl siteId → normalized url → the published post ids claiming it
     * @param claimantsByUrl siteId → normalized url → every distinct post id claiming it, in any
     *                       state (SH720). Two rows of one post are one claimant, which is what
     *                       lets the duplicate check see a draft pair without firing on every edit
     * @param folderParents  every folder id that is some folder's parent
     * @param foldersWithPosts every folder id holding at least one post
     */
    private record Scan(Map<String, ShSite> siteById, List<ShPostUnified> posts,
                        List<ShPostUnified> published, Set<String> allIds,
                        Set<String> publishedIds,
                        Map<String, Map<String, List<String>>> publishedByUrl,
                        Map<String, Map<String, List<String>>> claimantsByUrl,
                        Map<String, String> publishedLocales,
                        List<Scanned> folders, Set<String> folderParents,
                        Set<String> foldersWithPosts,
                        Map<String, List<String>> acceptedByType) {

        int checked() {
            return posts.size() + folders.size();
        }
    }

    /** A folder with the site that owns it, resolved once via {@link ShFolderSite}. */
    private record Scanned(ShFolder folder, ShSite site) {
    }

    /**
     * {@code checks=delivery} over sites that have no front-end URL (SH141).
     *
     * <p>The same rule as the folder scope below, one check group along: the caller asked to
     * have its routes proved, there is nothing to fetch, and answering {@code ok} would be a
     * yes to a question nobody asked. Raised only when {@code delivery} was <b>named</b> —
     * reached through the default set it is a per-site warning, because a group that joined
     * the run on its own must not fail a call that did not request it.</p>
     */
    public static class NoDeliveryOriginException extends ShTeachingException {

        public NoDeliveryOriginException() {
            super(ShProblem.badRequest("no-delivery-origin",
                            "No site in this run has a front-end URL to fetch")
                    .withDetail("The `delivery` check proves a route by requesting it from the "
                            + "site's own origin, and the sites this run covers have no URL "
                            + "configured — so it would have answered ok having proved nothing.")
                    .withFix("Set each site's URL in the console (Sites → the site → URL), or "
                            + "drop `delivery` from checks and rely on the content-graph answer.")
                    .withExample("?checks=content,routes"));
        }
    }

    /**
     * A {@code folder=} scope that resolved to something that is not a folder.
     *
     * <p>A teaching 400 rather than an empty run: a scope that silently covered nothing would
     * report {@code ok} over zero objects, which is the most dangerous false green there is —
     * the caller asked a question and got a yes to a different one.</p>
     */
    public static class UnknownFolderScopeException extends ShTeachingException {

        public UnknownFolderScopeException(String raw) {
            super(ShProblem.badRequest("folder-scope-unknown",
                            "'" + raw + "' is not a folder, so there is nothing to scope to")
                    .withDetail("verify's `folder` parameter narrows the run to a folder and its "
                            + "descendants. This address resolved, but not to a folder.")
                    .withFix("Pass a folder: address (GET /api/v2/agent/resolve will confirm one), "
                            + "or use `address` to name posts directly.")
                    .withExample("GET /api/v2/agent/verify?folder=folder:Acme/blog"));
        }
    }
}
