/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.agent.dto.ShAgentDoc;
import com.viglet.shio.api.agent.dto.ShAgentFound;
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
import com.viglet.shio.render.ShTemplateGraph;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Agent-shaped content reads (SH83's {@code find} and {@code read}, with SH123's
 * per-field projection).
 *
 * <h2>Why not the CDA</h2>
 *
 * <p>Composition is the rule (P10), and these reads still enforce the same SH2 site
 * allow-list — but the CDA is a <em>delivery</em> surface and only ever sees
 * {@code PUBLISHED} rows. An agent works on drafts almost by definition: it writes
 * something, reads it back, corrects it, and only then publishes. Routing these through
 * the CDA would mean the agent could not see its own work, which is not a composition
 * we want. So the rule these follow is the context pack's: one row per post,
 * <b>DRAFT-preferred</b>, so the sitemap, {@code find} and {@code read} never disagree
 * about what a post currently says.</p>
 *
 * <h2>Two calls, on purpose</h2>
 *
 * <p>{@code find} returns identifiers; {@code read} returns bodies. A single
 * search-with-content endpoint spends context on every match the agent then discards,
 * and that cost scales with how *badly* the query was aimed — precisely when the agent
 * can least afford it.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
// SH171: same reason as ShAddressResolver — a row this service reports as failed must not
// poison the transaction the successful rows are being read in.
@Transactional(readOnly = true, noRollbackFor = ShTeachingException.class)
public class ShAgentReadService {

    private static final List<ShPostState> BOTH_STATES =
            List.of(ShPostState.DRAFT, ShPostState.PUBLISHED);

    private final ShAgentSiteAccess siteAccess;
    private final ShFolderRepository shFolderRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShPostTypeRepository shPostTypeRepository;
    private final ShAddressResolver shAddressResolver;
    private final ObjectMapper objectMapper;
    private final ShAgentProperties properties;
    private final ShTemplateGraph shTemplateGraph;

    public ShAgentReadService(ShAgentSiteAccess siteAccess,
                              ShFolderRepository shFolderRepository,
                              ShPostUnifiedRepository shPostUnifiedRepository,
                              ShPostTypeRepository shPostTypeRepository,
                              ShAddressResolver shAddressResolver,
                              ObjectMapper objectMapper,
                              ShAgentProperties properties,
                              ShTemplateGraph shTemplateGraph) {
        this.siteAccess = siteAccess;
        this.shFolderRepository = shFolderRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shPostTypeRepository = shPostTypeRepository;
        this.shAddressResolver = shAddressResolver;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.shTemplateGraph = shTemplateGraph;
    }

    /**
     * Finds posts, returning addresses and titles.
     *
     * @param site   site name/id/address, or {@code null} for every readable site
     * @param type   post-type name filter
     * @param folder folder path or address — matches the folder <em>and its subtree</em>,
     *               because "everything under /blog" is the question actually being asked
     * @param q      full-text over title, summary and content
     * @param limit  row cap; {@code null} = configured default
     * @param ids    include object ids (SH121 default: off)
     */
    public ShAgentFound find(String site, String type, String folder, String q,
                             Integer limit, boolean ids) {
        return find(site, type, folder, q, limit, ids, null);
    }

    /**
     * Finds posts, returning addresses and titles — one page of them (SH146).
     *
     * <p><b>Keyset, never an offset.</b> The walk resumes from the last address returned
     * rather than from a row number, because content is being written while it runs: a post
     * created ahead of an offset shifts every later page by one, which drops a row from a
     * projection quietly — the exact failure the cap was refusing to risk. An address is
     * unique (a friendly URL is site-unique, an {@code id:} fallback is a UUID) and it is
     * already the sort key, so it is the keyset for free.</p>
     *
     * @param after the previous page's {@code cursor}; rows sorting at or before it are
     *              skipped. {@code null} starts the walk
     */
    public ShAgentFound find(String site, String type, String folder, String q,
                             Integer limit, boolean ids, String after) {
        List<ShSite> sites = siteAccess.readable(site);
        int cap = limit == null || limit <= 0
                ? properties.getRead().getDefaultLimit()
                : Math.min(limit, properties.getRead().getMaxLimit());
        String from = cursorFrom(after);

        List<ShPostUnified> matches = q != null && !q.isBlank()
                ? searchText(sites, q)
                : byFilters(sites, type);

        Set<String> folderIds = folderScope(sites, folder);
        List<ShAgentFound.Row> rows = dedupe(matches).stream()
                .filter(post -> folderIds.isEmpty() || inScope(post, folderIds))
                .filter(post -> type == null || type.isBlank() || matchesType(post, type))
                .map(post -> row(post, ids))
                .filter(row -> from == null || row.address().compareTo(from) > 0)
                .sorted(Comparator.comparing(ShAgentFound.Row::address))
                .toList();

        boolean truncated = rows.size() > cap;
        List<ShAgentFound.Row> capped = truncated ? rows.subList(0, cap) : rows;
        // The cursor is offered only when there is something past this page. On the last one
        // it is absent, which is how a caller knows the walk finished rather than having to
        // ask for an empty page to find out.
        String cursor = truncated && !capped.isEmpty()
                ? capped.get(capped.size() - 1).address()
                : null;
        return new ShAgentFound(capped.size(), truncated, List.copyOf(capped),
                notesFor(capped), cursor);
    }

    /**
     * Validates an {@code after} cursor, or {@code null} for "start at the beginning".
     *
     * <p>A cursor is an address, and the only two an unpaged row can carry are {@code post:}
     * and {@code id:} (SH172). Anything else is rejected rather than compared: lexicographic
     * comparison against a garbage string succeeds and returns a plausible page, so an
     * interpretation here would silently skip content — SH88's rule about a bare timestamp
     * cursor, one endpoint over.</p>
     */
    private static String cursorFrom(String after) {
        if (after == null || after.isBlank()) {
            return null;
        }
        String trimmed = after.trim();
        if (!trimmed.startsWith("post:") && !trimmed.startsWith("id:")) {
            throw new BadCursorException(trimmed);
        }
        return trimmed;
    }

    /**
     * What the caller should know about this result set, or {@code null}.
     *
     * <p>One sentence for the whole response rather than a marker per row (SH172): a File-heavy
     * site would otherwise pay for the same prose on every row, and the {@code id:} prefix is
     * already visible on the rows it applies to. Same shape as SH88's and SH103's notes — a
     * response that states its own limits.</p>
     */
    private static List<String> notesFor(List<ShAgentFound.Row> rows) {
        boolean anyById = rows.stream()
                .anyMatch(row -> row.address() != null && row.address().startsWith("id:"));
        return anyById
                ? List.of("some rows are addressed `id:` because the post has no friendly URL — "
                        + "give it one with a post.move op to make it addressable by path (SH100 "
                        + "reports these as url-missing)")
                : null;
    }

    /**
     * Reads one or more posts by address.
     *
     * @param addresses {@code post:} addresses (an {@code id:} address is accepted —
     *                  it is the deliberate escape hatch, not a guess)
     * @param fields    project the data down to these field names (SH123); {@code null}
     *                  or empty returns the whole document
     * @param ids       include object ids (SH121 default: off)
     */
    public List<ShAgentDoc> read(List<String> addresses, List<String> fields, boolean ids) {
        return read(addresses, fields, ids, false);
    }

    /**
     * @param impact SH262 — attach each row's blast radius when the address is a layout or region.
     *               Opt-in: most reads want the fields, and a caller that has not asked about what a
     *               template reaches should pay neither the graph walk nor the tokens
     */
    public List<ShAgentDoc> read(List<String> addresses, List<String> fields, boolean ids,
                                 boolean impact) {
        return readDocs(addresses, fields, ids, impact);
    }

    private List<ShAgentDoc> readDocs(List<String> addresses, List<String> fields, boolean ids,
                                      boolean impact) {
        if (addresses == null || addresses.isEmpty()) {
            throw new NoAddressException();
        }
        List<String> wanted = fields == null ? List.of() : fields.stream()
                .filter(field -> field != null && !field.isBlank())
                .map(String::trim)
                .toList();
        List<ShAgentDoc> docs = new ArrayList<>(addresses.size());
        for (String address : addresses) {
            // SH171 — one bad address must not take the whole call down when several were
            // asked for: the caller batched *to save round trips*, and a 404 here sends it
            // back to reading them one at a time to find out which one was wrong. A single
            // address still throws, because a one-address read that 404s is correct.
            if (addresses.size() == 1) {
                docs.add(doc(address, wanted, ids, impact));
                continue;
            }
            try {
                docs.add(doc(address, wanted, ids, impact));
            } catch (ShTeachingException failure) {
                docs.add(failed(address, failure));
            }
        }
        return List.copyOf(docs);
    }

    /**
     * The row for an address that could not be read: its own teaching error, in place of its
     * content.
     *
     * <p>Only the three fields a caller acts on travel — the slug (so it can branch), the
     * title and the fix. The full problem is what the single-address form returns, and
     * repeating all of it per row would make the failure cheaper to read than the success.</p>
     */
    private static ShAgentDoc failed(String address, ShTeachingException failure) {
        var detail = failure.problem().toProblemDetail();
        Object fix = detail.getProperties() == null ? null : detail.getProperties().get("fix");
        return new ShAgentDoc(address, null, null, null, null, null, null,
                new ShAgentDoc.Problem(
                        detail.getType() == null ? null : detail.getType().toString(),
                        detail.getTitle(),
                        fix == null ? null : fix.toString()));
    }

    private ShAgentDoc doc(String rawAddress, List<String> fields, boolean ids, boolean impact) {
        var resolved = shAddressResolver.resolve(rawAddress);
        ShPostUnified post = draftPreferred(resolved.id())
                .orElseThrow(() -> new NotAPostException(rawAddress, resolved.kind()));
        JsonNode data = readTree(post.getData());
        JsonNode projected = fields.isEmpty() ? data : project(data, fields, post);
        return new ShAgentDoc(
                shAddressResolver.addressOf(post).toString(),
                post.getShPostType() == null ? null : post.getShPostType().getName(),
                post.getState() == null ? null : post.getState().name(),
                post.getShFolder() == null ? null : folderPath(post.getShFolder()),
                fields.isEmpty() ? null : fields,
                projected,
                ids ? post.getId() : null)
                .withImpact(impact ? impactOf(post) : null)
                .withLocale(nonDefaultLocale(post))
                .withTranslationOf(anchorAddress(post));
    }

    /**
     * A post's locale, or {@code null} when it is the instance default (SH348).
     *
     * <p>Before this the agent surface could not see a locale at all — the string appeared nowhere in
     * this service — so {@code locales: true} in the manifest was a capability whose *state* no read
     * reported. That is the first of the four layers SH348 turned out to be: the projection cannot
     * carry what the read does not return, and a document cannot declare what the projection drops.</p>
     *
     * <p><b>Conditional rather than always-present</b>, which is P3 and not laziness: a monolingual
     * instance pays nothing per row, and the field appears exactly where it carries information. The
     * default comes from {@link ShPostUnifiedWriteService#DEFAULT_LOCALE} — the writer owns it, the
     * context pack reports it — so this comparison cannot disagree with what the pack published.</p>
     */
    private static String nonDefaultLocale(ShPostUnified post) {
        String locale = post.getLocale();
        if (locale == null || locale.isBlank()
                || locale.equals(ShPostUnifiedWriteService.DEFAULT_LOCALE)) {
            return null;
        }
        return locale;
    }

    /**
     * The address of this post's translation anchor, or {@code null} (SH348).
     *
     * <p>The anchor is the group member whose <b>id is the group id</b> — the post the others were
     * created from ({@code translate} seeds the group with the source's id). Every translation points
     * at it and it points at nothing, which makes the relationship a <em>forest</em>: acyclic by
     * construction, one field per file, and a reader can tell the original from its translations
     * without a second call.</p>
     *
     * <p>{@code null} for a post with no siblings, which is almost every post — so this is free on a
     * monolingual instance. Also {@code null} for the anchor itself, and that is not an omission: a
     * file saying it is a translation of itself would be a cycle the format cannot represent.</p>
     */
    private String anchorAddress(ShPostUnified post) {
        String group = post.getTranslationGroupId();
        if (group == null || group.isBlank() || group.equals(post.getId())) {
            return null;
        }
        return draftPreferred(group)
                .map(anchor -> shAddressResolver.addressOf(anchor).toString())
                .orElse(null);
    }

    /**
     * One row's blast radius, or {@code null} when the post is not a template (SH262).
     *
     * <p>Taken from the post the read already loaded rather than by re-resolving the address, so the
     * impact cannot describe a different row than the fields do — the DRAFT-preference is exactly the
     * kind of thing two lookups come to disagree about. {@link ShTemplateGraph#impactOf} answers empty
     * for anything that is not a {@code PageLayout} or a {@code Region}, which is why an ordinary post
     * asked with {@code include=impact} gets no section rather than an error: a caller reading a mixed
     * list of addresses should not have to know which ones are templates before it asks.</p>
     */
    private ShAgentDoc.Impact impactOf(ShPostUnified post) {
        return shTemplateGraph.impactOf(post)
                .map(found -> new ShAgentDoc.Impact(found.kind(), found.layouts(),
                        found.postTypes(), found.pages(), found.published()))
                .orElse(null);
    }

    /**
     * Projects the data down to the requested fields.
     *
     * <p>A field the post-type does not have is a <b>teaching error</b>, not a silently
     * empty result. Returning {@code {}} for {@code fields=headine} would look exactly
     * like an empty post, and the agent would go on to overwrite real content with
     * nothing — the one failure this projection could plausibly cause, so it is the one
     * it refuses to allow. A field the type <em>has</em> but the post has not set is
     * simply absent; that is data, not a mistake.</p>
     */
    private JsonNode project(JsonNode data, List<String> fields, ShPostUnified post) {
        Set<String> declared = declaredFields(post.getShPostType());
        List<String> unknown = fields.stream()
                .filter(field -> !declared.isEmpty() && !declared.contains(field))
                .toList();
        if (!unknown.isEmpty()) {
            throw new UnknownFieldException(unknown.get(0),
                    post.getShPostType() == null ? null : post.getShPostType().getName(),
                    declared.stream().sorted().toList());
        }
        ObjectNode projected = objectMapper.createObjectNode();
        for (String field : fields) {
            JsonNode value = data.get(field);
            if (value != null && !value.isNull()) {
                projected.set(field, value);
            }
        }
        return projected;
    }

    private Set<String> declaredFields(ShPostType postType) {
        if (postType == null || postType.getShPostTypeAttrs() == null) {
            return Set.of();
        }
        return postType.getShPostTypeAttrs().stream()
                .filter(attr -> attr.getShParentPostTypeAttr() == null)
                .map(attr -> attr.getName())
                .filter(name -> name != null && !name.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    // --- query strategies ---

    private List<ShPostUnified> searchText(List<ShSite> sites, String q) {
        // The term goes in as the caller typed it: SH131 moved the lower-case +
        // %-wrapping inside the repository, precisely because doing it here was a
        // contract you could only honour by remembering, and forgetting it produced an
        // empty result set rather than an error.
        return shPostUnifiedRepository.searchBySitesAndStateIn(
                sites.stream().map(ShSite::getId).toList(), BOTH_STATES, q);
    }

    private List<ShPostUnified> byFilters(List<ShSite> sites, String type) {
        ShPostType postType = type == null || type.isBlank() ? null : requireType(type);
        List<ShPostUnified> rows = new ArrayList<>();
        for (ShSite site : sites) {
            rows.addAll(postType == null
                    ? shPostUnifiedRepository.findByShSite_IdAndStateIn(site.getId(), BOTH_STATES)
                    : shPostUnifiedRepository.findByShSite_IdAndShPostType_IdAndStateIn(
                            site.getId(), postType.getId(), BOTH_STATES));
        }
        return rows;
    }

    private ShPostType requireType(String name) {
        ShPostType postType = shPostTypeRepository.findByName(name.trim());
        if (postType == null) {
            throw new ShAgentBatchService.UnknownPostTypeException(name,
                    shPostTypeRepository.findAll().stream()
                            .map(ShPostType::getName)
                            .filter(candidate -> candidate != null && !candidate.isBlank())
                            .sorted().toList());
        }
        return postType;
    }

    /** The folder named by {@code folder} plus every folder beneath it, as ids. */
    private Set<String> folderScope(List<ShSite> sites, String folder) {
        if (folder == null || folder.isBlank()) {
            return Set.of();
        }
        String path = folder.startsWith("folder:")
                ? ShAddress.parse(folder).path()
                : ShAddress.normalizePath(folder);
        String siteSelector = folder.startsWith("folder:") ? ShAddress.parse(folder).site() : null;
        List<ShSite> scope = siteSelector == null ? sites : siteAccess.readable(siteSelector);

        Set<String> ids = new HashSet<>();
        for (ShSite site : scope) {
            descend(site, path).ifPresent(target -> collectSubtree(target, ids));
        }
        if (ids.isEmpty()) {
            throw new ShAddressResolver.UnresolvedAddressException(
                    ShAddress.folder(scope.isEmpty() ? "?" : scope.get(0).getName(), path), List.of());
        }
        return ids;
    }

    private Optional<ShFolder> descend(ShSite site, String path) {
        Optional<ShFolder> current = siteAccess.rootFolder(site);
        for (String segment : segments(path)) {
            if (current.isEmpty()) {
                return Optional.empty();
            }
            current = shFolderRepository.findByParentFolderOrderByPositionAsc(current.get()).stream()
                    .filter(child -> segment.equalsIgnoreCase(nullSafe(child.getName())))
                    .findFirst();
        }
        return current;
    }

    private void collectSubtree(ShFolder folder, Set<String> ids) {
        if (!ids.add(folder.getId())) {
            return;
        }
        shFolderRepository.findByParentFolderOrderByPositionAsc(folder)
                .forEach(child -> collectSubtree(child, ids));
    }

    // --- shaping ---

    private ShAgentFound.Row row(ShPostUnified post, boolean ids) {
        return new ShAgentFound.Row(
                shAddressResolver.addressOf(post).toString(),
                post.getShPostType() == null ? null : post.getShPostType().getName(),
                post.getState() == null ? null : post.getState().name(),
                post.getTitle(),
                ids ? post.getId() : null,
                nonDefaultLocale(post));
    }

    private static boolean matchesType(ShPostUnified post, String type) {
        return post.getShPostType() != null && type.equalsIgnoreCase(post.getShPostType().getName());
    }

    private static boolean inScope(ShPostUnified post, Set<String> folderIds) {
        return post.getShFolder() != null && folderIds.contains(post.getShFolder().getId());
    }

    /** One row per post id, DRAFT preferred — the rule every agent read follows. */
    private static List<ShPostUnified> dedupe(List<ShPostUnified> rows) {
        Map<String, ShPostUnified> byId = new LinkedHashMap<>();
        for (ShPostUnified row : rows) {
            byId.merge(row.getId(), row, (kept, candidate) ->
                    kept.getState() == ShPostState.DRAFT ? kept : candidate);
        }
        return List.copyOf(byId.values());
    }

    private Optional<ShPostUnified> draftPreferred(String postId) {
        Optional<ShPostUnified> draft =
                shPostUnifiedRepository.findByIdAndState(postId, ShPostState.DRAFT);
        return draft.isPresent()
                ? draft
                : shPostUnifiedRepository.findByIdAndState(postId, ShPostState.PUBLISHED);
    }

    private String folderPath(ShFolder folder) {
        return shAddressResolver.addressOf(folder).path();
    }

    private JsonNode readTree(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (JacksonException e) {
            return objectMapper.createObjectNode();
        }
    }

    private static List<String> segments(String path) {
        if (path == null || "/".equals(path)) {
            return List.of();
        }
        return Arrays.stream(path.split("/")).filter(segment -> !segment.isBlank()).toList();
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    /** A 400: read was called with nothing to read. */
    public static class NoAddressException extends ShTeachingException {

        public NoAddressException() {
            super(ShProblem.badRequest("no-address", "No address to read")
                    .withDetail("`address` is required; pass it more than once to read several posts "
                            + "in one call.")
                    .withFix("Find an address with GET /api/v2/agent/find, or build one from a "
                            + "context row: post:<site>/<friendly-url>.")
                    .withExample("?address=post:Blog/blog/hello-world"));
        }
    }

    /** A 400: {@code after} is not a cursor this endpoint ever issued (SH146). */
    public static class BadCursorException extends ShTeachingException {

        public BadCursorException(String sent) {
            super(ShProblem.badRequest("bad-cursor", "'" + sent + "' is not a find cursor")
                    .withDetail("`after` continues a walk from the previous page's `cursor`, which "
                            + "is the last address that page returned. It is not a row number and "
                            + "not a timestamp — find is ordered by address, so the cursor is one.")
                    .withFix("Pass the `cursor` from the previous response verbatim, or omit "
                            + "`after` to start from the beginning.")
                    .withExample("?after=post:Blog/blog/hello-world"));
        }
    }

    /** A 400: the address resolved, but not to a post. */
    public static class NotAPostException extends ShTeachingException {

        public NotAPostException(String address, String kind) {
            super(ShProblem.badRequest("not-a-post", "'" + address + "' is a " + kind + ", not a post")
                    .withDetail("read returns post content; a folder has none.")
                    .withFix("To see what is inside a folder, use GET /api/v2/agent/find?folder=… "
                            + "or read the sitemap in GET /api/v2/agent/context.")
                    .withExample("post:Blog/blog/hello-world"));
        }
    }

    /** A 400 that refuses to answer a projection with a field the type does not have. */
    public static class UnknownFieldException extends ShTeachingException {

        public UnknownFieldException(String sent, String postType, List<String> allowed) {
            super(ShProblem.badRequest("unknown-field",
                            "No field '" + sent + "'" + (postType == null ? "" : " on " + postType))
                    .withDetail("Projecting an unknown field would return an empty document that "
                            + "looks exactly like an empty post.")
                    .withAllowed(allowed)
                    .withDidYouMeanFrom(sent)
                    .withFix("Use one of the listed field names, or drop `fields` to read all of them.")
                    .withExample(allowed.isEmpty() ? "headline" : allowed.get(0)));
        }
    }
}
