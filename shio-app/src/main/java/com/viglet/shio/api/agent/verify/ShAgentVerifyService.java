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

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.agent.ShAddress;
import com.viglet.shio.api.agent.ShAddressResolver;
import com.viglet.shio.api.agent.ShAgentChangesService;
import com.viglet.shio.api.agent.ShAgentProperties;
import com.viglet.shio.api.agent.ShAgentSiteAccess;
import com.viglet.shio.api.agent.diagnostics.ShAgentIncidentLog;
import com.viglet.shio.api.agent.dto.ShAgentChanges;
import com.viglet.shio.api.agent.dto.ShAgentResolved;
import com.viglet.shio.api.agent.memory.ShAgentMemoryService;
import com.viglet.shio.api.agent.verify.dto.ShAgentReport;
import com.viglet.shio.api.agent.verify.dto.ShAgentReport.Finding;
import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.api.staticfile.ShFileStorageService;
import com.viglet.shio.object.ShFolderSite;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.render.ShTemplateGraph;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
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
@Transactional(readOnly = true)
public class ShAgentVerifyService {

    private static final List<ShPostState> BOTH_STATES =
            List.of(ShPostState.DRAFT, ShPostState.PUBLISHED);

    /** Widgets whose stored value is another post's id. */
    private static final Set<String> REFERENCE_WIDGETS =
            Set.of(ShSystemWidget.CONTENT_SELECT, ShSystemWidget.FILE);

    /** Widgets whose stored value is markup worth scanning for links and images. */
    private static final Set<String> MARKUP_WIDGETS =
            Set.of(ShSystemWidget.HTML_EDITOR, ShSystemWidget.ACE_HTML);

    /** Widgets that hold no value of their own — never "required". */
    private static final Set<String> CONTAINER_WIDGETS =
            Set.of(ShSystemWidget.TAB, ShSystemWidget.RELATOR);

    /** The public path the SH38 subsystem serves stored bytes on. */
    private static final String FILE_SOURCE_PREFIX = "/file_source/";

    private final ShAgentSiteAccess siteAccess;
    private final ShFolderRepository shFolderRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShFileStorageService fileStorage;
    private final ShAgentIncidentLog incidentLog;
    private final ObjectMapper objectMapper;
    private final ShAgentProperties properties;
    private final ShAddressResolver addressResolver;
    private final ShAgentChangesService shAgentChangesService;
    private final ShAgentDeliveryProbe deliveryProbe;
    private final ShAgentMemoryService shAgentMemoryService;
    private final ShTemplateFieldLint templateFieldLint;
    private final ShTemplateGraph templateGraph;

    public ShAgentVerifyService(ShAgentSiteAccess siteAccess,
                                ShFolderRepository shFolderRepository,
                                ShPostUnifiedRepository shPostUnifiedRepository,
                                ShFileStorageService fileStorage,
                                ShAgentIncidentLog incidentLog,
                                ObjectMapper objectMapper,
                                ShAgentProperties properties,
                                ShAddressResolver addressResolver,
                                ShAgentChangesService shAgentChangesService,
                                ShAgentDeliveryProbe deliveryProbe,
                                ShAgentMemoryService shAgentMemoryService,
                                ShTemplateFieldLint templateFieldLint,
                                ShTemplateGraph templateGraph) {
        this.siteAccess = siteAccess;
        this.shFolderRepository = shFolderRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.fileStorage = fileStorage;
        this.incidentLog = incidentLog;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.addressResolver = addressResolver;
        this.shAgentChangesService = shAgentChangesService;
        this.deliveryProbe = deliveryProbe;
        this.shAgentMemoryService = shAgentMemoryService;
        this.templateFieldLint = templateFieldLint;
        this.templateGraph = templateGraph;
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
     * @param address one or more comma-separated {@code post:} addresses
     * @param since   an SH88 cursor: only what changed after it
     */
    public ShAgentReport verify(String site, String checks, Integer limit, String folder,
                                String address, String since) {
        Set<ShAgentCheck> requested = ShAgentCheck.parse(checks, deliveryProbe.enabled());
        int cap = limit == null || limit <= 0
                ? properties.getVerify().getDefaultLimit()
                : Math.min(limit, properties.getVerify().getMaxLimit());

        List<ShSite> sites = siteAccess.readable(site);
        ShAgentVerifyScope scope = scope(sites, site, folder, address, since);
        Scan scan = scan(sites, scope);
        List<Finding> findings = new ArrayList<>();
        List<String> notes = new ArrayList<>(scope.notes());
        boolean proving = requested.contains(ShAgentCheck.DELIVERY);

        if (requested.contains(ShAgentCheck.CONTENT)) {
            content(scan, findings);
        }
        // Routes runs before delivery because it decides what delivery has to adjudicate:
        // a link that resolves against the content graph needs no request at all, and the
        // ones that do not are exactly the population SH141 exists to settle.
        List<Unresolved> unproven = new ArrayList<>();
        ShFrontEndRoutes declared = declaredRoutes(site);
        if (requested.contains(ShAgentCheck.ROUTES)) {
            routes(scan, findings, declared, proving ? unproven : null, notes);
        }
        if (proving) {
            delivery(scan, findings, notes, unproven, explicitlyAsked(checks));
        }
        if (requested.contains(ShAgentCheck.DIAGNOSTICS)) {
            diagnostics(findings);
        }

        List<Finding> ordered = findings.stream().sorted(ORDER).toList();
        int errors = (int) ordered.stream().filter(Finding::isError).count();
        List<Finding> capped = ordered.size() > cap ? ordered.subList(0, cap) : ordered;
        return new ShAgentReport(
                errors == 0,
                requested.stream().sorted().map(ShAgentCheck::wireName).toList(),
                scan.checked(),
                errors,
                ordered.size() - errors,
                ordered.size(),
                ordered.size() > cap,
                // Absent rather than `false` on a full pass: a flag that is always present
                // is a flag nobody reads, and this one has to be read.
                scope.partial() ? Boolean.TRUE : null,
                scope.describe(),
                notes.isEmpty() ? null : List.copyOf(notes),
                List.copyOf(capped));
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
            return ShAgentVerifyScope.full(readable(sites));
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
                keep(rows, shPostUnifiedRepository.findById(addressResolver.resolve(each).id()));
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

    /** Every readable row, both states, plus the site-less ones nobody else asks for. */
    private List<ShPostUnified> readable(List<ShSite> sites) {
        List<ShPostUnified> rows = new ArrayList<>();
        for (ShSite each : sites) {
            rows.addAll(shPostUnifiedRepository.findByShSite_IdAndStateIn(each.getId(), BOTH_STATES));
        }
        // Site-less posts are nobody's rows, which is exactly why they need asking for.
        rows.addAll(shPostUnifiedRepository.findWithoutSite(BOTH_STATES));
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
    private Scan scan(List<ShSite> sites, ShAgentVerifyScope scope) {
        Map<String, ShSite> siteById = new LinkedHashMap<>();
        Set<String> allIds = new HashSet<>();
        Set<String> publishedIds = new HashSet<>();
        Map<String, Map<String, List<String>>> publishedByUrl = new HashMap<>();
        for (ShSite each : sites) {
            siteById.put(each.getId(), each);
            Map<String, List<String>> byUrl = new HashMap<>();
            for (Object[] row : shPostUnifiedRepository.findUrlIndexBySiteId(each.getId(), BOTH_STATES)) {
                String id = (String) row[0];
                String furl = (String) row[1];
                allIds.add(id);
                if (row[2] == ShPostState.PUBLISHED) {
                    publishedIds.add(id);
                    if (!isBlank(furl)) {
                        byUrl.computeIfAbsent(normalized(furl), ignored -> new ArrayList<>()).add(id);
                    }
                }
            }
            publishedByUrl.put(each.getId(), byUrl);
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
        List<ShPostUnified> posts = draftPreferred.values().stream()
                .sorted(Comparator.comparing(ShPostUnified::getId))
                .toList();
        return new Scan(siteById, posts, published, allIds, publishedIds, publishedByUrl,
                folders(siteById.keySet(), scope.folderIds()),
                shFolderRepository.findParentFolderIds(),
                shPostUnifiedRepository.findFolderIdsWithPosts());
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
        }
        duplicateUrls(check, scan, findings);
        folderFindings(check, scan, findings);
        templateFields(check, scan, findings);
        unusedTemplates(check, scan, findings);
        duplicateTemplateTitles(check, scan, findings);
        unpublishedTemplates(check, scan, findings);
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
            for (ShPostUnified region : unused.regions()) {
                findings.add(Finding.warning(check, "unused-region", address(region), null,
                        "No layout in '" + site.getName() + "' slots a region called '"
                                + region.getTitle() + "', so nothing renders it. A layout reaches a "
                                + "region by title, with `sh-region=\"" + region.getTitle() + "\"`.",
                        "Add `<div sh-region=\"" + region.getTitle() + "\"></div>` to the layout "
                                + "that should show it, or delete the region if it is finished with. "
                                + "A title that merely differs in case or spacing is the usual "
                                + "cause — a slot is matched exactly."));
            }
            for (ShPostUnified layout : unused.layouts()) {
                findings.add(Finding.warning(check, "unused-layout", address(layout), null,
                        "No post-type in '" + site.getName() + "' is bound to the layout '"
                                + layout.getTitle() + "', so no page renders through it."
                                + (unused.boundLayouts().isEmpty()
                                        ? " This site binds no layouts at all."
                                        : " Bound: " + String.join(", ", unused.boundLayouts()) + "."),
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

    private void fieldFindings(String check, ShPostUnified post, String address, JsonNode data,
                               ShPostTypeAttr attr, Scan scan, List<Finding> findings) {
        String widget = widgetName(attr);
        String field = attr.getName();
        if (field == null || field.isBlank()) {
            return;
        }
        JsonNode value = data.get(field);

        if (attr.getRequired() == 1 && !CONTAINER_WIDGETS.contains(widget) && isEmpty(value)) {
            findings.add(Finding.error(check, "required-field-empty", address, field,
                    "Required field '" + field + "' is empty.",
                    "Set it with a post.upsert op on POST /api/v2/agent/batch, or drop the "
                            + "required flag on the post-type if it is genuinely optional."));
        }
        // SH154 — a Multi Select that declares it holds post URLs (the SH95 vocabulary's
        // `sections`). Checked before the id-shaped reference widgets because it is a different
        // kind of reference: a list of friendly URLs, not one id.
        if (ShPostReferenceFields.referencesPosts(attr, objectMapper)) {
            sectionFindings(check, post, address, field, value, attr, scan, findings);
            return;
        }
        if (!REFERENCE_WIDGETS.contains(widget) || isEmpty(value) || !value.isString()) {
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
     * Two published posts claiming one URL. Only published rows count: a draft sharing a
     * URL with the live page it will replace is the normal editing state, and flagging it
     * would fire on every single edit.
     *
     * <p>Asked <b>per subject against the site's index</b> rather than by grouping the loaded
     * rows, so a scoped run still finds the duplicate whose twin is outside its scope — the
     * case a self-grouping version would quietly call clean. On a full pass every member of a
     * group is a subject, so the output is the same one-finding-per-post it always was.</p>
     */
    private void duplicateUrls(String check, Scan scan, List<Finding> findings) {
        for (ShPostUnified post : scan.published()) {
            if (post.getShSite() == null || isBlank(post.getFurl())) {
                continue;
            }
            List<String> claiming = scan.publishedByUrl()
                    .getOrDefault(post.getShSite().getId(), Map.of())
                    .getOrDefault(normalized(post.getFurl()), List.of());
            if (claiming.size() <= 1) {
                continue;
            }
            String others = claiming.stream().sorted()
                    .reduce((first, second) -> first + ", " + second).orElse("");
            findings.add(Finding.error(check, "duplicate-url", address(post), null,
                    "Published URL '" + post.getFurl() + "' is claimed by "
                            + claiming.size() + " posts (" + others + ") — which one a "
                            + "visitor gets is undefined.",
                    "Give one of them a different URL with a post.move op "
                            + "(POST /api/v2/agent/batch), or unpublish the duplicate."));
        }
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
                        List<Unresolved> unproven, List<String> notes) {
        String check = ShAgentCheck.ROUTES.wireName();
        // From the site index, not from the loaded rows: "does this link resolve" is a
        // question about the whole site, and a scoped run that answered it from its own
        // subjects would report every link out of the scope as broken (SH143).
        Map<String, Set<String>> publishedUrls = new HashMap<>();
        scan.publishedByUrl().forEach((siteId, byUrl) ->
                publishedUrls.put(siteId, new HashSet<>(byUrl.keySet())));
        int silenced = 0;
        for (ShPostUnified post : scan.published()) {
            String address = address(post);
            if (isBlank(post.getFurl())) {
                findings.add(Finding.error(check, "url-missing", address, null,
                        "This post is published but has no friendly URL, so no route reaches it.",
                        "Give it one with a post.move op (POST /api/v2/agent/batch): "
                                + "{\"op\":\"post.move\",\"address\":\"id:" + post.getId()
                                + "\",\"to\":\"post:<site>/<url>\"}."));
            }
            silenced += markup(check, post, address, publishedUrls, declared, unproven, findings);
        }
        if (silenced > 0) {
            notes.add(silenced + " unresolved link(s) matched routes declared as the front end's "
                    + "(memory key '" + ShFrontEndRoutes.MEMORY_KEY + "') and were not reported"
                    + (unproven == null ? "." : " as warnings — they are proved below instead."));
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
                       List<Unresolved> unproven, List<Finding> findings) {
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
            ShHtmlOutline.Outline outline = ShHtmlOutline.of(value.asString());
            for (ShHtmlOutline.Link link : outline.links()) {
                silenced += linkFinding(check, post, address, attr.getName(), link.href(),
                        siteUrls, declaredRoutes, unproven, findings);
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
                            List<Unresolved> unproven, List<Finding> findings) {
        if (!isInternal(href)) {
            return 0;
        }
        String path = ShAddress.normalizePath(stripQuery(href)).toLowerCase(java.util.Locale.ROOT);
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
     * <h2>The cost this accepts</h2>
     *
     * <p>The fetches happen inside the report's read transaction, which holds a connection for
     * as long as the network pass takes. That is why the budget is a hard 15 seconds by default
     * and why the group is not in the default set unless an operator has enabled fetching:
     * the alternative — two round trips and a report that cannot answer {@code ok} — costs the
     * caller more than it costs the pool.</p>
     */
    private void delivery(Scan scan, List<Finding> findings, List<String> notes,
                          List<Unresolved> unproven, boolean explicit) {
        String check = ShAgentCheck.DELIVERY.wireName();
        List<ShPostUnified> pages = scan.published().stream()
                .filter(post -> post.getShSite() != null && !isBlank(post.getFurl()))
                .toList();
        Set<String> withSubjects = new LinkedHashSet<>();
        pages.forEach(post -> withSubjects.add(post.getShSite().getId()));
        unproven.forEach(link -> withSubjects.add(link.siteId()));

        Map<String, String> origins = new LinkedHashMap<>();
        for (String siteId : withSubjects) {
            ShSite owner = scan.siteById().get(siteId);
            if (owner == null) {
                continue;
            }
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
            return;
        }

        List<Target> targets = targets(pages, unproven, origins);
        Fetches fetches = fetch(targets, origins.keySet());
        report(check, targets, fetches, findings, notes);
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
    private Fetches fetch(List<Target> targets, Set<String> siteIds) {
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
        return new Fetches(byUrl, unreachable, exhausted, skipped, siteIds);
    }

    /** The findings the fetches imply — one per target, plus one per unreachable origin. */
    private void report(String check, List<Target> targets, Fetches fetches,
                        List<Finding> findings, List<String> notes) {
        fetches.unreachable().forEach((siteId, failure) -> {
            ShSite owner = siteById(siteId);
            findings.add(Finding.error(check, "delivery-unreachable",
                    owner == null ? null : ShAddress.site(owner.getName()).toString(), null,
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
        if (fetches.exhausted() && fetches.skipped() > 0) {
            notes.add("The delivery check stopped after " + fetches.byUrl().size()
                    + " request(s) and left " + fetches.skipped()
                    + " unproved — raise shio.agent.verify.max-fetches or "
                    + "shio.agent.verify.fetch-budget-millis, or narrow the run with folder=.");
        }
    }

    /** The site behind an id, for a finding that names a site rather than a post. */
    private ShSite siteById(String siteId) {
        return siteAccess.readable().stream()
                .filter(site -> siteId.equals(site.getId()))
                .findFirst()
                .orElse(null);
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
    private record Fetches(Map<String, ShAgentDeliveryProbe.Outcome> byUrl,
                           Map<String, String> unreachable, boolean exhausted, int skipped,
                           Set<String> siteIds) {
    }

    // --- SH103: recent asynchronous failures --------------------------------

    /**
     * The incident log as findings.
     *
     * <p>A failed webhook is a defect in the same sense a broken link is — the site is
     * not what the content says it is — so it belongs in the report an agent already
     * reads rather than in a second call it has to remember to make. The detailed view
     * (first/last seen, counts) stays on {@code GET /api/v2/agent/diagnostics}.</p>
     */
    private void diagnostics(List<Finding> findings) {
        String check = ShAgentCheck.DIAGNOSTICS.wireName();
        for (ShAgentIncidentLog.Incident incident : incidentLog.recent(0)) {
            String repeats = incident.count() > 1 ? " (x" + incident.count() + ")" : "";
            findings.add(new Finding(check, incident.kind().wireName(),
                    incident.kind() == ShAgentIncidentLog.Kind.TRANSFORM
                            ? ShAgentReport.WARNING : ShAgentReport.ERROR,
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
            if (scan.publishedIds().contains(post.getId())
                    && !scan.publishedIds().contains(section.getId())) {
                findings.add(Finding.error(check, "unpublished-dependency", address, field,
                        "This page is published and names '" + url + "', which is not — a visitor "
                                + "gets the hole the editor cannot see.",
                        "Publish it with a post.publish op, or take this page down until it is."));
            }
        }
    }

    /**
     * The post a URL names in this site, in either state.
     *
     * <p>Draft-preferred is not what matters here — existence is — so both states are asked for,
     * and the repository is asked directly rather than the scan, because the scan holds only what
     * this credential may read (see the class note on the allow-list).</p>
     */
    private Optional<ShPostUnified> referenced(String siteId, String url) {
        if (siteId == null) {
            return Optional.empty();
        }
        return shPostUnifiedRepository.findByShSite_IdAndFurlAndState(siteId, url, ShPostState.DRAFT)
                .or(() -> shPostUnifiedRepository.findByShSite_IdAndFurlAndState(siteId, url,
                        ShPostState.PUBLISHED));
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
        for (String key : data.propertyNames()) {
            if (!allowed.contains(key)) {
                findings.add(Finding.error(check, "unknown-field", address, key,
                        "This post stores '" + key + "', which post-type '"
                                + name(post.getShPostType()) + "' does not declare, so nothing "
                                + "renders or delivers it.",
                        "Add '" + key + "' to the post-type (PUT /api/v2/post-type/"
                                + name(post.getShPostType()) + "), or drop the key with a "
                                + "post.upsert that omits it."));
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

    private String fileNameOf(ShPostUnified file) {
        JsonNode data = data(file);
        JsonNode name = data.get(ShSystemWidget.FILE.toUpperCase(java.util.Locale.ROOT));
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
    private static boolean isInternal(String href) {
        return href != null && href.startsWith("/") && !href.startsWith("//");
    }

    private static String stripQuery(String href) {
        int cut = href.length();
        int query = href.indexOf('?');
        int fragment = href.indexOf('#');
        if (query >= 0) {
            cut = query;
        }
        if (fragment >= 0 && fragment < cut) {
            cut = fragment;
        }
        return href.substring(0, cut);
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
     * @param folderParents  every folder id that is some folder's parent
     * @param foldersWithPosts every folder id holding at least one post
     */
    private record Scan(Map<String, ShSite> siteById, List<ShPostUnified> posts,
                        List<ShPostUnified> published, Set<String> allIds,
                        Set<String> publishedIds,
                        Map<String, Map<String, List<String>>> publishedByUrl,
                        List<Scanned> folders, Set<String> folderParents,
                        Set<String> foldersWithPosts) {

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
