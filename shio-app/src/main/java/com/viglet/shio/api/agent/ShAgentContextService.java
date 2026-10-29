/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.agent.dto.ShAgentContext;
import com.viglet.shio.api.release.ShReleaseService;
import com.viglet.shio.persistence.model.release.ShRelease;
import com.viglet.shio.api.agent.memory.ShAgentMemoryService;
import com.viglet.shio.api.cda.ShCdaMapper;
import com.viglet.shio.api.cda.dto.ShCdaPostTypeSchema;
import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.api.site.ShSiteLocaleService;
import com.viglet.shio.api.site.ShSiteLocales;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.api.console.ShConsoleSummary;
import com.viglet.shio.api.review.ShReviewService;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.note.ShNoteRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.render.ShTemplateEngine;
import com.viglet.shio.render.ShRenderConventions;
import com.viglet.shio.render.ShRenderPostTypes;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;

/**
 * Builds the context pack (SH77) — the one call that replaces a session of
 * exploration (design law P2).
 *
 * <p><b>Composition, not a new contract</b> (P10): the model section is the SH11
 * CDA schema produced by the existing {@link ShCdaMapper}, and the per-site
 * visibility rule is the same SH2 token allow-list the CDA enforces. Nothing here
 * can show an agent content the CDA would hide from the same credential.</p>
 *
 * <p><b>Determinism</b> (P5) is a hard requirement, not a nicety: the pack's
 * {@code digest} is what lets an agent skip re-reading, and an unstable ordering
 * would change the digest on every call and destroy that saving. Every collection
 * is therefore explicitly sorted — post-types by name, sites by name, folders by
 * position then name then id, posts by title then id — never left to whatever order
 * the database returned.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
// SH345 — see ShAgentSiteAccess: on a read-only path, rollback-only can only take away a
// caller's ability to report the refusal.
@Transactional(readOnly = true, noRollbackFor = ShTeachingException.class)
public class ShAgentContextService {

    private static final List<ShPostState> BOTH_STATES = List.of(ShPostState.DRAFT, ShPostState.PUBLISHED);
    private static final String KIND_FOLDER = "folder";
    private static final String KIND_POST = "post";
    private static final String ROOT_PATH = "/";

    private final ShFolderRepository shFolderRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShPostTypeRepository shPostTypeRepository;
    private final ShCdaMapper mapper;
    private final ShAgentProperties properties;
    private final ShAgentSiteAccess siteAccess;
    private final ShAgentMemoryService memoryService;
    private final ShAgentManifestService shAgentManifestService;
    private final ShTemplateEngine shTemplateEngine;
    private final ShAgentUsageService shAgentUsageService;
    /** SH993 — the site's releases, for `include=releases`. */
    private final ShReleaseService shReleaseService;
    /** SH1050 — the review queue's own count, for `include=status`. */
    private final ShReviewService shReviewService;
    /** SH1050 — the open-note count, for `include=status`. */
    private final ShNoteRepository shNoteRepository;
    /** SH1056 — the site's declared locale policy, or the one its content implies. */
    private final ShSiteLocaleService shSiteLocaleService;

    public ShAgentContextService(ShFolderRepository shFolderRepository,
                                ShPostUnifiedRepository shPostUnifiedRepository,
                                ShPostTypeRepository shPostTypeRepository,
                                ShCdaMapper mapper,
                                ShAgentProperties properties,
                                ShAgentSiteAccess siteAccess,
                                ShAgentMemoryService memoryService,
                                ShAgentManifestService shAgentManifestService,
                                ShTemplateEngine shTemplateEngine,
                                ShAgentUsageService shAgentUsageService,
                                ShReleaseService shReleaseService,
                                ShReviewService shReviewService,
                                ShNoteRepository shNoteRepository,
                                ShSiteLocaleService shSiteLocaleService) {
        this.shAgentUsageService = shAgentUsageService;
        this.shReleaseService = shReleaseService;
        this.shReviewService = shReviewService;
        this.shNoteRepository = shNoteRepository;
        this.shSiteLocaleService = shSiteLocaleService;
        this.shFolderRepository = shFolderRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shPostTypeRepository = shPostTypeRepository;
        this.mapper = mapper;
        this.properties = properties;
        this.siteAccess = siteAccess;
        this.memoryService = memoryService;
        this.shAgentManifestService = shAgentManifestService;
        this.shTemplateEngine = shTemplateEngine;
    }

    /** Id-free by default (SH121) — see {@link #context(String, Integer, Integer, String, String, boolean)}. */
    public ShAgentContext context(String site, Integer depth, Integer budget, String include, String from) {
        return context(site, depth, budget, include, from, false);
    }

    /**
     * @param site     site id or name; {@code null} selects every readable site (and,
     *                 when exactly one exists, still walks its tree — the common
     *                 single-site case needs no parameters at all)
     * @param depth    how deep to walk the folder tree; {@code null} = configured default
     * @param budget   estimated-token ceiling; {@code null} = configured default,
     *                 {@code 0} = unbounded (capped by {@code maxNodes} regardless)
     * @param include  comma-separated section list, {@code null} = all
     * @param from     resume cursor: the object id returned as {@code truncatedAfter}
     * @param ids      emit the raw UUID on every site and sitemap row. Off by default
     *                 (SH121): the UUID is the largest single per-row cost in this
     *                 payload and SH79 made every row addressable without one
     */
    public ShAgentContext context(String site, Integer depth, Integer budget, String include,
                                  String from, boolean ids) {
        Set<ShAgentContextSection> sections = ShAgentContextSection.parse(include);
        ShTokenBudget tokens = new ShTokenBudget(resolveBudget(budget));
        int walkDepth = depth == null || depth < 0 ? properties.getContext().getDefaultDepth() : depth;

        List<ShSite> sites = siteAccess.readable(site);
        // Keyed by id, not by name — see the repository note on the SH22 @TenantId
        // join hazard. Names are resolved from the post-type rows we load anyway.
        Map<String, Long> postsBySite = counts(shPostUnifiedRepository.countDistinctBySiteId());
        Map<String, Long> postsByType = counts(shPostUnifiedRepository.countDistinctByPostTypeId());

        ShAgentContext.Instance instance = sections.contains(ShAgentContextSection.INSTANCE)
                ? new ShAgentContext.Instance("Viglet Shio", version(),
                        ShPostUnifiedWriteService.DEFAULT_LOCALE)
                : null;
        List<ShAgentContext.Site> siteRows = sections.contains(ShAgentContextSection.SITES)
                ? siteRows(sites, postsBySite, ids)
                : List.of();
        List<ShAgentContext.Type> model = sections.contains(ShAgentContextSection.MODEL)
                ? model(postsByType)
                : List.of();

        List<String> conventions = sections.contains(ShAgentContextSection.CONVENTIONS)
                ? conventions(sites, model.isEmpty() ? model(postsByType) : model, ids, site)
                : List.of();

        // Charge **every** bounded section before the sitemap gate opens, so the gate
        // sees the true remainder. Charging any of them afterwards would let the walk
        // admit rows that the later charge then pushes over the limit — the response
        // would exceed the ceiling it was asked to respect.
        chargeBounded(tokens, instance, siteRows, model, conventions);

        Walk walk = sections.contains(ShAgentContextSection.SITEMAP)
                ? walk(sites, walkDepth, tokens, from, ids)
                : new Walk(List.of(), false, null);

        ShAgentContext pack = new ShAgentContext(
                ShAgentManifestService.PROTOCOL,
                ShAgentManifestService.API_BASE + "/agent/manifest",
                instance, siteRows, model, walk.nodes(), conventions,
                null,
                new ShAgentContext.Budget(tokens.spentTokens(), tokens.limitTokens(),
                        walk.truncated(), walk.truncatedAfter(), tokens.overBudget()));

        // The digest covers the *content* of the pack, not its measurements, so it is
        // identical for the JSON and terse renderings of the same state and does not
        // churn when a budget hint changes. It does move with `ids` — that flag changes
        // what the pack *says*, not how the same statement was measured, and SH116 will
        // hand this value to `If-None-Match`, where two representations must not share
        // an entity tag.
        // SH157 — capabilities ride along when asked for, and they are attached *after* the
        // digest is taken. The digest answers "has the content changed"; the manifest is not
        // content, and letting a build number move it would send every agent re-reading every
        // site's sitemap the first time this instance was upgraded.
        ShAgentContext digested = withDigest(pack, digest(ShAgentContextTerse.body(pack)));
        if (sections.contains(ShAgentContextSection.MANIFEST)) {
            digested = digested.withCapabilities(ShAgentManifestSection.project(
                    shAgentManifestService.manifest(),
                    // features + limits + startHere. Not the endpoint index: it is ~87% of
                    // the manifest, it changes only when the build does, and it is exactly
                    // the thing SH116's ETag makes free to keep. Folding it in here would
                    // re-send it every time a post changed.
                    EnumSet.of(ShAgentManifestSection.FEATURES, ShAgentManifestSection.LIMITS,
                            ShAgentManifestSection.START_HERE)));
        }
        // SH346 — the write vocabulary, on the same terms and for the same reason: it is not
        // content, so it must not move the digest, and it is not something a reading session
        // should be charged for. `ShAgentOps.terse()` verbatim, byte-identical to shio://ops.
        ShAgentContext withOps = sections.contains(ShAgentContextSection.OPS)
                ? digested.withOps(ShAgentOps.terse())
                : digested;
        // SH754 — this tenant's spend, on the same terms and with one extra reason of its own: it
        // changes on every call, so a digest that covered it would make SH116's conditional read
        // never match. Byte-identical to shio://usage, because both call the one service.
        ShAgentContext withUsage = sections.contains(ShAgentContextSection.USAGE)
                ? withOps.withUsage(shAgentUsageService.terse())
                : withOps;
        // SH993 — the site's releases, on the same terms: opt-in, and after the digest. A release is
        // addressed by a name, and `find?release=` answers only a name it is given, so without this
        // an agent could create releases and never discover the ones already there.
        ShAgentContext withReleases = sections.contains(ShAgentContextSection.RELEASES)
                ? withUsage.withReleases(releases(sites))
                : withUsage;
        // SH1050 — what is going on here, on the same terms and for `usage`'s reason rather than
        // `releases`': a review queue moves when anybody decides anything, so a pack carrying it by
        // default would put a number that changes on every publish inside the document SH116's
        // conditional read exists to keep matching.
        ShAgentContext withStatus = sections.contains(ShAgentContextSection.STATUS)
                ? withReleases.withStatus(status(sites))
                : withReleases;
        // SH608 — attached last, and after the digest, for the reason `budget` is: this describes
        // the REQUEST, not the content, so a caller narrowing the pack must not get a different
        // entity tag for the same instance state.
        return withStatus.withOmitted(omitted(sections));
    }

    /**
     * SH1050 — the instance's state, the same numbers `/api/v2/console/summary` answers with.
     *
     * <p>Counts and the attention half only. The trail rows that endpoint carries are for a
     * dashboard drawing a list; what an agent does with this is decide whether to look somewhere
     * before it writes, and the trail itself is `/agent/changes` with a cursor on it.</p>
     */
    private ShAgentContext.Status status(List<ShSite> readable) {
        Instant now = Instant.now();
        var pending = shReviewService.pending();
        // The sites this credential may read, not every site on the instance: a pack is always the
        // caller's view, and a count they cannot act on would be the one number here that is not
        // about them. `/api/v2/console/summary` answers with the instance total, because a console
        // session is a person with an ACL rather than a token with an allow-list.
        return new ShAgentContext.Status(
                readable.size(),
                shPostTypeRepository.count(),
                pending.sessions(),
                pending.changes(),
                shPostUnifiedRepository.countScheduledBetween(now, now.plus(ShConsoleSummary.SOON)),
                shNoteRepository.countByResolvedFalse());
    }

    /**
     * The releases of every site this pack covers (SH993), newest first within each.
     *
     * <p>Counts rather than members: what a release holds is `find?site=&release=<name>`, and forty
     * addresses per release would be the regression the opt-in sections exist to avoid.</p>
     */
    private List<ShAgentContext.Release> releases(List<ShSite> sites) {
        List<ShAgentContext.Release> rows = new ArrayList<>();
        for (ShSite site : sites) {
            for (ShRelease release : shReleaseService.listed(site.getId())) {
                rows.add(new ShAgentContext.Release(
                        ShAddress.release(site.getName(), release.getName()).toString(),
                        release.getState(), release.getDescription(),
                        release.getScheduledAt() == null ? null : release.getScheduledAt().toString(),
                        shReleaseService.postIds(release.getId()).size()));
            }
        }
        return List.copyOf(rows);
    }

    /**
     * Which sections {@code include=} removed (SH608).
     *
     * <p>SH600 closed this on the manifest and the pack had the same hole one endpoint over:
     * {@code ?include=instance} returned four scalars, dropped the model, the sitemap and the
     * conventions — including the remembered notes an entire SH597 intent is measured against —
     * and answered 200. A caller narrowing a <b>discovery</b> surface is by construction one who
     * does not yet know what is there, which is why a silent projection misleads worse here than
     * a 404 would.</p>
     *
     * <p>Built from the <b>parsed section set</b> and not from the assembled pack, which is the one
     * thing that could not be ported from SH600. The pack's sections are content-shaped, so after
     * the fact a projection and an empty instance are indistinguishable: {@code sites} is an empty
     * list on a fresh instance and an empty list when nobody asked. The manifest could derive its
     * report because {@code features: null} and {@code features: {}} are different; here only the
     * request knows.</p>
     */
    private static List<String> omitted(Set<ShAgentContextSection> sections) {
        List<String> dropped = new ArrayList<>();
        for (ShAgentContextSection section : ShAgentContextSection.values()) {
            // MANIFEST, OPS and USAGE are opt-IN — absent by default and not part of the pack a
            // caller asked for. Reporting them as omitted would tell every default request that
            // three sections were withheld, which is the opposite of what this field is for.
            // Keyed off `defaults()` rather than a list repeated here, so the next opt-in section
            // cannot arrive correct in one place and wrong in this one — which is exactly how
            // SH754 landed, adding USAGE to the enum's exclusions and not to this loop's.
            if (!ShAgentContextSection.isDefault(section)) {
                continue;
            }
            if (!sections.contains(section)) {
                dropped.add(section.wireName());
            }
        }
        return dropped.isEmpty() ? null : List.copyOf(dropped);
    }

    private int resolveBudget(Integer budget) {
        ShAgentProperties.Context config = properties.getContext();
        if (budget == null) {
            return config.getDefaultBudgetTokens();
        }
        if (budget < 0) {
            throw new InvalidBudgetException(budget, config.getMaxBudgetTokens());
        }
        return budget == 0 ? 0 : Math.min(budget, config.getMaxBudgetTokens());
    }

    private List<ShAgentContext.Site> siteRows(List<ShSite> sites, Map<String, Long> postsBySite, boolean ids) {
        // SH1056 — one query for every site whose policy has to be derived, not one per row.
        Map<String, ShSiteLocales> policies = shSiteLocaleService.of(sites);
        return sites.stream()
                .map(site -> {
                    ShSiteLocales locales = policies.get(site.getId());
                    return new ShAgentContext.Site(
                            ids ? site.getId() : null,
                            site.getName(),
                            site.getUrl(),
                            ids ? rootFolder(site).map(ShFolder::getId).orElse(null) : null,
                            postsBySite.getOrDefault(site.getId(), 0L),
                            // SH591 — present only when true. A `scratch=false` on every row is a
                            // token per site to say the ordinary thing.
                            site.isScratch() ? Boolean.TRUE : null,
                            // SH1056 — and the same rule for all three: the pack already says the
                            // instance default once, and a monolingual site repeating it on every
                            // row would cost tokens to say nothing (P3).
                            ShPostUnifiedWriteService.DEFAULT_LOCALE.equals(locales.defaultLocale())
                                    ? null : locales.defaultLocale(),
                            locales.enabled().size() > 1 ? locales.enabled() : null,
                            locales.declared() ? Boolean.TRUE : null,
                            // SH1122 — the two the marker file needs and this row did not have.
                            // Blank reads as absent, for the same reason the marker treats it so:
                            // a column is either unset or holds something, and `""` is the first.
                            blankToNull(site.getFurl()),
                            blankToNull(site.getDescription()),
                            // SH1122 — and the stored policy itself, so a projection is exact rather
                            // than reconstructed from the three discovery fields above (which omit a
                            // default matching the instance's and carry no fallback at all). Absent
                            // for every site that has declared nothing, which is most of them.
                            // Only when somebody declared it: a derived policy is an observation, and
                            // a marker carrying it would turn that into a declaration on the next push.
                            locales.declared() ? locales.toNode() : null,
                            // SH1283 — what a new page in this site starts as, when it names one.
                            blankToNull(site.getPageType()));
                })
                .toList();
    }

    /** SH1122 — a column that is set to the empty string says nothing, so it is reported as nothing. */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** Post-types by name, with the CDA field schema and a usage count per type. */
    private List<ShAgentContext.Type> model(Map<String, Long> postsByType) {
        return shPostTypeRepository.findAll().stream()
                .sorted(Comparator.comparing(type -> nullSafe(type.getName())))
                .map(type -> toType(type, postsByType))
                .toList();
    }

    private ShAgentContext.Type toType(ShPostType type, Map<String, Long> postsByType) {
        ShCdaPostTypeSchema schema = mapper.toPostTypeSchema(type);
        return new ShAgentContext.Type(
                type.getName(),
                type.getTitle(),
                type.getSystem() == 1,
                type.isManaged(),
                postsByType.getOrDefault(type.getId(), 0L),
                type.isScratch(),
                schema.fields());
    }

    /**
     * Depth-first walk of every requested site's folder tree, charging each row
     * against the budget <em>before</em> committing it, so output is cut at a row
     * boundary and the cut is reported rather than hidden.
     */
    private Walk walk(List<ShSite> sites, int depth, ShTokenBudget tokens, String from, boolean ids) {
        List<ShAgentContext.Node> nodes = new ArrayList<>();
        Cursor cursor = new Cursor(from == null || from.isBlank() ? null : from.trim());
        int maxNodes = properties.getContext().getMaxNodes();
        for (ShSite site : sites) {
            Optional<ShFolder> root = rootFolder(site);
            if (root.isEmpty()) {
                continue;
            }
            walkFolder(root.get(), ROOT_PATH, depth, nodes, tokens, cursor, maxNodes, ids);
            if (cursor.stopped()) {
                break;
            }
        }
        return new Walk(List.copyOf(nodes), cursor.stopped(), cursor.lastEmittedId());
    }

    private void walkFolder(ShFolder folder, String path, int remainingDepth,
                            List<ShAgentContext.Node> nodes, ShTokenBudget tokens,
                            Cursor cursor, int maxNodes, boolean ids) {
        if (cursor.stopped()) {
            return;
        }
        List<ShFolder> children = childFolders(folder);
        FolderRows rows = folderRows(folder);
        List<ShPostUnified> posts = rows.posts();

        ShAgentContext.Node folderNode = new ShAgentContext.Node(KIND_FOLDER, path,
                ids ? folder.getId() : null,
                null, null, null, null, children.size(), posts.size());
        if (!offer(folderNode, folder.getId(), nodes, tokens, cursor, maxNodes)) {
            return;
        }
        for (ShPostUnified post : posts) {
            ShAgentContext.Node postNode = new ShAgentContext.Node(KIND_POST, path,
                    ids ? post.getId() : null,
                    post.getShPostType() == null ? null : post.getShPostType().getName(),
                    post.getState() == null ? null : post.getState().name(),
                    post.getFurl(), post.getTitle(), 0, 0,
                    // Only when true, which is SH545's rule and this record's own: a draft says
                    // nothing rather than saying `false`, so a site with no published page pays
                    // nothing per row for the field.
                    rows.live().contains(post.getId()) ? Boolean.TRUE : null);
            if (!offer(postNode, post.getId(), nodes, tokens, cursor, maxNodes)) {
                return;
            }
        }
        if (remainingDepth <= 0) {
            return;
        }
        for (ShFolder child : children) {
            walkFolder(child, childPath(path, child.getName()), remainingDepth - 1,
                    nodes, tokens, cursor, maxNodes, ids);
            if (cursor.stopped()) {
                return;
            }
        }
    }

    /**
     * Offers one row: skipped while resuming, charged and committed once past the
     * cursor, and refused (stopping the walk) when it would breach a limit.
     *
     * <p>The object id is passed alongside the row rather than read back off it,
     * because under SH121 the row usually carries no id — and pagination must not
     * depend on a field the caller asked us not to send. The cursor stays an id on
     * the wire: it is <em>one</em> per response, not one per row, so it costs nothing
     * measurable and needs no path/namespace disambiguation to be exact.</p>
     *
     * @return {@code false} when the walk must stop
     */
    private boolean offer(ShAgentContext.Node node, String objectId, List<ShAgentContext.Node> nodes,
                          ShTokenBudget tokens, Cursor cursor, int maxNodes) {
        if (cursor.skipping()) {
            cursor.consider(objectId);
            return true;
        }
        if (nodes.size() >= maxNodes) {
            cursor.stop();
            return false;
        }
        if (!tokens.tryAdd(ShAgentContextTerse.node(node))) {
            cursor.stop();
            return false;
        }
        nodes.add(node);
        cursor.emitted(objectId);
        return true;
    }

    private List<ShFolder> childFolders(ShFolder folder) {
        return shFolderRepository.findByParentFolderOrderByPositionAsc(folder).stream()
                .sorted(Comparator.comparing(ShFolder::getPosition)
                        .thenComparing(child -> nullSafe(child.getName()))
                        .thenComparing(ShFolder::getId))
                .toList();
    }

    /**
     * A folder's posts, deduped draft-preferred — the same rule the CDA's
     * {@code /list} applies, so the two views of a folder never disagree.
     */
    private List<ShPostUnified> folderPosts(ShFolder folder) {
        return folderRows(folder).posts();
    }

    /**
     * A folder's posts, deduped draft-preferred, <b>and which of them are also live</b> (SH553).
     *
     * <p>The published ids come out of the same read at no cost, which is the difference between
     * this surface and the two SH545 fixed. {@code /agent/read} pays a PK lookup per row to answer
     * the same question; here the walk already fetches both state rows in order to drop one, so the
     * fact was in hand and being discarded.</p>
     */
    private FolderRows folderRows(ShFolder folder) {
        Map<String, ShPostUnified> byId = new LinkedHashMap<>();
        Set<String> live = new HashSet<>();
        for (ShPostUnified post : shPostUnifiedRepository.findByShFolderAndStateIn(folder, BOTH_STATES)) {
            if (post.getState() == ShPostState.PUBLISHED) {
                live.add(post.getId());
            }
            ShPostUnified existing = byId.get(post.getId());
            if (existing == null || post.getState() == ShPostState.DRAFT) {
                byId.put(post.getId(), post);
            }
        }
        return new FolderRows(byId.values().stream()
                .sorted(Comparator.comparing((ShPostUnified post) -> nullSafe(post.getTitle()))
                        .thenComparing(ShPostUnified::getId))
                .toList(), live);
    }

    /** One folder's rows and the ids among them that have a PUBLISHED twin (SH553). */
    private record FolderRows(List<ShPostUnified> posts, Set<String> live) {
    }

    /**
     * Conventions: how <em>this</em> install expects to be driven.
     *
     * <p>Two sources, in this order. First the <b>derived</b> ones — facts computed
     * from the instance's own state, which an agent would otherwise learn by making a
     * mistake. They are never stored, so they cannot go stale. Then the <b>remembered</b>
     * ones (SH114), written by a previous session or a human and attributed to whoever
     * wrote them: the compounding half, and the reason session N+1 costs less than
     * session N.</p>
     *
     * <p>Derived first is deliberate. A derived line is true by construction; a
     * remembered one is somebody's claim about an earlier decision. Reading the
     * ground truth before the folklore is the right order when the two disagree.</p>
     */
    /**
     * What a template may call, for an instance that has templates (SH272).
     *
     * <h2>Why the pack and not the manifest</h2>
     *
     * <p>SH260 made the engine the one source of the capability set and every reader of it was
     * internal, so the only way to learn the set was to write a template calling something else and
     * read the 400 — a teaching error doing the job of documentation, one name per attempt, which is
     * the opposite of P2. The manifest's {@code startHere} is orientation rather than reference, and
     * an MCP resource would answer only MCP clients; this is an <em>instance-specific</em> fact,
     * which is what this section is for and what SH114's memory notes already sit beside.</p>
     *
     * <h2>Why it is conditional</h2>
     *
     * <p>P3: the pack is budgeted and read once per session, including by the many sessions that
     * never write a template. So it is emitted only when the instance's own model holds one of the
     * render post-types — the same test the neighbouring conventions make about managed and system
     * types, and it costs no query, because the model is already in hand.</p>
     */
    private List<String> templateHelpers(List<ShAgentContext.Type> model) {
        boolean renders = model.stream().map(ShAgentContext.Type::name)
                .anyMatch(ShRenderPostTypes.NAMES::contains);
        List<String> signatures = shTemplateEngine.registeredSignatures();
        if (!renders || signatures.isEmpty()) {
            return List.of();
        }
        // SH292 — the text is composed where the facts are (ShRenderConventions), because the row
        // keys and the reserved frames belong to the renderer and a copy here would be the fourth.
        return ShRenderConventions.lines(signatures);
    }

    private List<String> conventions(List<ShSite> sites, List<ShAgentContext.Type> model,
                                     boolean ids, String site) {
        List<String> conventions = new ArrayList<>();
        // SH545 — the trailing clause is what makes an absent `published` an answer rather than a
        // gap, and it is a clause rather than a sentence because the pack is what `discovery`
        // measures: this line already establishes what a write lands in, so grounding the read
        // convention costs eight words instead of restating the rule.
        conventions.add("Writes land in DRAFT. Publishing is a separate, explicit call "
                + "(POST /api/v2/post-unified/{id}/publish) — nothing you write becomes visible on "
                + "its own, and a read omits `published` until it is.");
        long managed = model.stream().filter(ShAgentContext.Type::managed).count();
        if (managed > 0) {
            conventions.add(managed + " post-type(s) are managed by code: edit shio/post-types/ and run "
                    + "`shio push`. The console and the console API reject writes to them (409).");
        }
        long system = model.stream().filter(ShAgentContext.Type::system).count();
        if (system > 0) {
            conventions.add(system + " post-type(s) are system types and cannot be modified or deleted; "
                    + "create your own type instead of changing one.");
        }
        List<String> unused = model.stream()
                .filter(type -> !type.system() && type.posts() == 0)
                .map(ShAgentContext.Type::name)
                .toList();
        if (!unused.isEmpty() && unused.size() < model.size()) {
            conventions.add("Post-type(s) with no content yet: " + String.join(", ", unused)
                    + " — prefer an existing type over inventing one.");
        }
        // Deliberately a *rule*, not a per-row field: every sitemap row already carries
        // its site, path and url, so emitting a built address on each one would pay
        // tokens to repeat what the caller can derive. State the grammar once (SH79).
        // SH433 — the home page is named here because it is the one form a caller cannot derive from
        // the grammar: an empty friendly URL reads as the malformed case, and a replication needs
        // that address first, every time.
        conventions.add("Address content as post:<site>/<url> (a post's friendly URL) or "
                + "folder:<site>/<path> (its chain of folder names) — build it from a sitemap row's "
                + "site + url/path; the home page is post:<site>/ (friendly URL `/`). "
                + "GET /api/v2/agent/resolve?address=... returns the id when an "
                + "id-keyed endpoint needs one. Ids are per-install UUIDs and are not portable.");
        // Say it once, here, rather than shipping 36 chars per row to prove it (SH121).
        if (!ids) {
            conventions.add("Rows in this pack carry no id, because an address names them and a UUID "
                    + "is the most expensive thing a row can hold. Add ?ids=true if you genuinely "
                    + "need them on every row.");
        }
        if (sites.size() > 1) {
            conventions.add("This instance hosts " + sites.size() + " sites; pass ?site= to scope the "
                    + "context pack to the one you are working on.");
        }
        // The instruction that makes the memory self-sustaining. Without it an agent
        // has no reason to write anything down, and a store nobody writes to is a
        // store that never pays back the row it costs.
        // SH160 names the MCP tool first now. This line is read by clients that have no
        // shell, and pointing those at a CLI command was the dead end that task closed.
        conventions.add("This instance remembers. When you work something out that the next "
                + "session would otherwise re-derive, write it down: the `shio_remember` tool, "
                + "`shio remember <key> \"<note>\"`, or PUT /api/v2/agent/memory with "
                + "{key, note, scope}. Notes come back in this section, attributed.");
        conventions.addAll(templateHelpers(model));
        conventions.addAll(remembered(site));
        return conventions;
    }

    /**
     * The remembered notes (SH114), rendered as convention lines.
     *
     * <p>Each carries its scope and its author inline, because the two facts change
     * how much weight the line deserves and a separate section would cost a heading
     * plus a second parse. Capped at the configured limit and never allowed to push
     * the sitemap out: memory that grows without bound would undo the saving it
     * exists to create.</p>
     */
    private List<String> remembered(String site) {
        return memoryService.memory(site, null).notes().stream()
                .map(note -> "[memory:" + note.key() + (note.scope() == null ? "" : "@" + note.scope())
                        + (note.author() == null || note.author().isBlank() ? "" : " by " + note.author())
                        + "] " + note.note())
                .toList();
    }

    private Optional<ShFolder> rootFolder(ShSite site) {
        return siteAccess.rootFolder(site);
    }

    /**
     * Charges everything except the sitemap: header, instance, sites, model and
     * conventions. Measured by rendering the pack-without-a-sitemap, so what is
     * charged is exactly what will be emitted.
     */
    private void chargeBounded(ShTokenBudget tokens, ShAgentContext.Instance instance,
                               List<ShAgentContext.Site> sites, List<ShAgentContext.Type> model,
                               List<String> conventions) {
        ShAgentContext skeleton = new ShAgentContext(ShAgentManifestService.PROTOCOL,
                ShAgentManifestService.API_BASE + "/agent/manifest",
                instance, sites, model, List.of(), conventions, null,
                new ShAgentContext.Budget(0, 0, false, null, false));
        tokens.add(ShAgentContextTerse.body(skeleton));
    }

    private static ShAgentContext withDigest(ShAgentContext pack, String digest) {
        return new ShAgentContext(pack.protocol(), pack.manifest(), pack.instance(), pack.sites(),
                pack.model(), pack.sitemap(), pack.conventions(), digest, pack.budget());
    }

    /** {@code c:} + the first 12 hex of SHA-256 — short enough to carry, long enough to trust. */
    static String digest(String canonical) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder("c:");
            for (int i = 0; i < 6; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static Map<String, Long> counts(List<Object[]> rows) {
        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : rows) {
            if (row.length >= 2 && row[0] instanceof String key && row[1] instanceof Number count) {
                counts.put(key, count.longValue());
            }
        }
        return counts;
    }

    private static String childPath(String parentPath, String name) {
        String segment = name == null ? "" : name;
        return ROOT_PATH.equals(parentPath) ? ROOT_PATH + segment : parentPath + "/" + segment;
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private String version() {
        String version = getClass().getPackage().getImplementationVersion();
        return version != null ? version : "dev";
    }

    /** Result of a sitemap walk: the rows, whether they were cut, and where to resume. */
    private record Walk(List<ShAgentContext.Node> nodes, boolean truncated, String truncatedAfter) {
    }

    /**
     * Resume + stop state for one walk. Holds the {@code from=} gate (skip rows until
     * the cursor id is passed) and the id of the last committed row, which becomes the
     * next {@code from=}.
     */
    private static final class Cursor {

        private final String resumeAfterId;
        private boolean skipping;
        private boolean stopped;
        private String lastEmittedId;

        private Cursor(String resumeAfterId) {
            this.resumeAfterId = resumeAfterId;
            this.skipping = resumeAfterId != null;
        }

        private boolean skipping() {
            return skipping;
        }

        /** While resuming: open the gate once the cursor row has gone by. */
        private void consider(String objectId) {
            if (resumeAfterId.equals(objectId)) {
                skipping = false;
            }
        }

        private void emitted(String objectId) {
            lastEmittedId = objectId;
        }

        private void stop() {
            stopped = true;
        }

        private boolean stopped() {
            return stopped;
        }

        private String lastEmittedId() {
            return stopped ? lastEmittedId : null;
        }
    }

    /**
     * A 404 that lists the site names this credential can actually see (SH80).
     *
     * <h2>Two callers, two fixes (SH329)</h2>
     *
     * <p>This resolver is shared by a read that is <em>scoped</em> by {@code ?site=} and a write
     * op that <em>addresses</em> a site, and it used to teach the read's fix to both. A batch op
     * naming a site that does not exist was answered with {@code Fix: Pass ?site= with one of the
     * listed names} — and {@code POST /api/v2/agent/batch} has no {@code ?site=}. P6 says an error
     * is an instruction; an instruction to do something that does not exist is worse than no
     * instruction, because the caller spends a call finding that out. So the fix is chosen by the
     * caller that raised it, and {@link #onWrite} is the write half.</p>
     */
    public static class UnknownSiteException extends ShTeachingException {

        public UnknownSiteException(String sent, List<String> allowed) {
            this(sent, allowed,
                    allowed.isEmpty()
                            ? "This credential can read no sites — check the token's site allow-list."
                            : "Pass ?site= with one of the listed names, or omit it for every site.",
                    "?site=" + (allowed.isEmpty() ? "<name>" : allowed.get(0)));
        }

        /**
         * The same 404 for an op that names the site rather than scoping by it.
         *
         * <p>A batch can create the site it goes on to use, so the fix names that op — which is
         * also what makes the dry run SH329 fixed reachable: plan the {@code site.upsert} first
         * and the rest of the run resolves against it.</p>
         */
        public static UnknownSiteException onWrite(String sent, List<String> allowed) {
            // No allow-list branch, deliberately. An empty `allowed` means "this credential reads
            // no sites", which on the write path has two causes that read identically and want
            // opposite advice: a scoped token, and a **brand new instance** — which is SH329's own
            // case, where "check the token's site allow-list" is simply false. `site.upsert` is the
            // answer under both, and a genuinely scoped credential is refused by
            // `requireUnscopedCredential` with a message that knows it is about scope.
            return new UnknownSiteException(sent, allowed,
                    (allowed.isEmpty() ? "No site exists yet — add a `site.upsert` op for it "
                            : "Address a site that exists, or add a `site.upsert` op for it ")
                            + "earlier in the same batch.",
                    "{\"op\":\"site.upsert\",\"address\":\"site:" + sent + "\"}");
        }

        private UnknownSiteException(String sent, List<String> allowed, String fix, String example) {
            super(ShProblem.of("unknown-site", org.springframework.http.HttpStatus.NOT_FOUND,
                            "No readable site '" + sent + "'")
                    .withDetail("No site with that id or name is visible to this credential.")
                    .withAllowed(allowed)
                    .withDidYouMeanFrom(sent)
                    .withFix(fix)
                    .withExample(example));
        }
    }

    /** A 400 for a nonsensical budget (SH80). */
    public static class InvalidBudgetException extends ShTeachingException {

        public InvalidBudgetException(int sent, int max) {
            super(ShProblem.badRequest("invalid-budget", "Invalid token budget " + sent)
                    .withDetail("A budget must be 0 (unbounded) or a positive number of tokens.")
                    .withFix("Pass a positive budget up to " + max + ", or 0 for no limit.")
                    .withExample("?budget=4000"));
        }
    }
}
