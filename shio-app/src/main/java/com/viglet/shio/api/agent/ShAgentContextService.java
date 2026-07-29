/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.agent.dto.ShAgentContext;
import com.viglet.shio.api.agent.memory.ShAgentMemoryService;
import com.viglet.shio.api.cda.ShCdaMapper;
import com.viglet.shio.api.cda.dto.ShCdaPostTypeSchema;
import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.render.ShTemplateEngine;
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

    public ShAgentContextService(ShFolderRepository shFolderRepository,
                                ShPostUnifiedRepository shPostUnifiedRepository,
                                ShPostTypeRepository shPostTypeRepository,
                                ShCdaMapper mapper,
                                ShAgentProperties properties,
                                ShAgentSiteAccess siteAccess,
                                ShAgentMemoryService memoryService,
                                ShAgentManifestService shAgentManifestService,
                                ShTemplateEngine shTemplateEngine) {
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
                ? new ShAgentContext.Instance("Viglet Shio CMS", version(),
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
        return sections.contains(ShAgentContextSection.MANIFEST)
                ? digested.withCapabilities(ShAgentManifestSection.project(
                        shAgentManifestService.manifest(),
                        // features + limits + startHere. Not the endpoint index: it is ~87% of
                        // the manifest, it changes only when the build does, and it is exactly
                        // the thing SH116's ETag makes free to keep. Folding it in here would
                        // re-send it every time a post changed.
                        EnumSet.of(ShAgentManifestSection.FEATURES, ShAgentManifestSection.LIMITS,
                                ShAgentManifestSection.START_HERE)))
                : digested;
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
        return sites.stream()
                .map(site -> new ShAgentContext.Site(
                        ids ? site.getId() : null,
                        site.getName(),
                        site.getUrl(),
                        ids ? rootFolder(site).map(ShFolder::getId).orElse(null) : null,
                        postsBySite.getOrDefault(site.getId(), 0L)))
                .toList();
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
        List<ShPostUnified> posts = folderPosts(folder);

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
                    post.getFurl(), post.getTitle(), 0, 0);
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
        Map<String, ShPostUnified> byId = new LinkedHashMap<>();
        for (ShPostUnified post : shPostUnifiedRepository.findByShFolderAndStateIn(folder, BOTH_STATES)) {
            ShPostUnified existing = byId.get(post.getId());
            if (existing == null || post.getState() == ShPostState.DRAFT) {
                byId.put(post.getId(), post);
            }
        }
        return byId.values().stream()
                .sorted(Comparator.comparing((ShPostUnified post) -> nullSafe(post.getTitle()))
                        .thenComparing(ShPostUnified::getId))
                .toList();
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
    private java.util.Optional<String> templateHelpers(List<ShAgentContext.Type> model) {
        boolean renders = model.stream().map(ShAgentContext.Type::name)
                .anyMatch(ShRenderPostTypes.NAMES::contains);
        List<String> signatures = shTemplateEngine.registeredSignatures();
        if (!renders || signatures.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of("A PageLayout's and a Region's HTML is Handlebars, and the "
                + "helpers it may call are exactly these: " + String.join(", ", signatures)
                + ". Anything else is refused when you write the post, with a line and a column. "
                + "A layout slots a region by title with sh-region=\"TITLE\", and binds to a "
                + "post-type through the site's postTypeLayout map.");
    }

    private List<String> conventions(List<ShSite> sites, List<ShAgentContext.Type> model,
                                     boolean ids, String site) {
        List<String> conventions = new ArrayList<>();
        conventions.add("Writes land in DRAFT. Publishing is a separate, explicit call "
                + "(POST /api/v2/post-unified/{id}/publish) — nothing you write becomes visible on its own.");
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
        conventions.add("Address content as post:<site>/<url> (a post's friendly URL) or "
                + "folder:<site>/<path> (its chain of folder names) — build it from a sitemap row's "
                + "site + url/path. GET /api/v2/agent/resolve?address=... returns the id when an "
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
        templateHelpers(model).ifPresent(conventions::add);
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

    /** A 404 that lists the site names this credential can actually see (SH80). */
    public static class UnknownSiteException extends ShTeachingException {

        public UnknownSiteException(String sent, List<String> allowed) {
            super(ShProblem.of("unknown-site", org.springframework.http.HttpStatus.NOT_FOUND,
                            "No readable site '" + sent + "'")
                    .withDetail("No site with that id or name is visible to this credential.")
                    .withAllowed(allowed)
                    .withDidYouMeanFrom(sent)
                    .withFix(allowed.isEmpty()
                            ? "This credential can read no sites — check the token's site allow-list."
                            : "Pass ?site= with one of the listed names, or omit it for every site.")
                    .withExample("?site=" + (allowed.isEmpty() ? "<name>" : allowed.get(0))));
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
