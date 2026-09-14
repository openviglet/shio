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
import com.viglet.shio.api.post.revision.ShPostRevisionService;
import com.viglet.shio.api.post.revision.ShRevisionRow;
import com.viglet.shio.api.post.unified.ShPostStateNotFoundException;
import com.viglet.shio.api.agent.dto.ShAgentFound;
import com.viglet.shio.api.agent.dto.ShAgentResolved;
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
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.tenant.ShTenantContext;

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
    private final ShConsoleSurfaces shConsoleSurfaces;
    /** SH987 — the page's history, for `include=revisions` and `state=rev:<n>`. */
    private final ShPostRevisionService shPostRevisionService;

    public ShAgentReadService(ShAgentSiteAccess siteAccess,
                              ShFolderRepository shFolderRepository,
                              ShPostUnifiedRepository shPostUnifiedRepository,
                              ShPostTypeRepository shPostTypeRepository,
                              ShAddressResolver shAddressResolver,
                              ObjectMapper objectMapper,
                              ShAgentProperties properties,
                              ShTemplateGraph shTemplateGraph,
                              ShConsoleSurfaces shConsoleSurfaces,
                              ShPostRevisionService shPostRevisionService) {
        this.siteAccess = siteAccess;
        this.shFolderRepository = shFolderRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shPostTypeRepository = shPostTypeRepository;
        this.shAddressResolver = shAddressResolver;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.shTemplateGraph = shTemplateGraph;
        this.shConsoleSurfaces = shConsoleSurfaces;
        this.shPostRevisionService = shPostRevisionService;
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
        return find(site, type, folder, q, limit, ids, after, false);
    }

    /**
     * @param trash SH222 — list what is <b>in the trash</b> instead of what is live.
     *
     *              <p>A shape over this read rather than an endpoint of its own (P10): the trash is
     *              a listing of posts, and `find` is the listing. It also means the answer already
     *              carries what a restore needs — SH172's {@code id:} address, which is the only
     *              form a trashed post has, because it no longer has a reachable path.</p>
     *
     *              <p>The protocol used to say {@code "to trash (restorable)"} on every delete and
     *              give the caller nowhere to go with it. This is the read half of that undo;
     *              {@code post.restore} and {@code folder.restore} are the write half.</p>
     */
    public ShAgentFound find(String site, String type, String folder, String q,
                             Integer limit, boolean ids, String after, boolean trash) {
        List<ShSite> sites = siteAccess.readable(site);
        int cap = limit == null || limit <= 0
                ? properties.getRead().getDefaultLimit()
                : Math.min(limit, properties.getRead().getMaxLimit());
        String from = cursorFrom(after);

        List<ShPostUnified> matches;
        if (trash) {
            // Never `q` and never the live filters: a trashed row is outside every index those
            // read, and quietly answering from them would report an empty trash on an instance
            // whose trash is full — the one answer this call must not give.
            matches = trashed(sites, type);
        } else {
            matches = q != null && !q.isBlank() ? searchText(sites, q) : byFilters(sites, type);
        }

        Set<String> folderIds = folderScope(sites, folder);
        // SH545 — taken before `dedupe`, which is the only place both rows of a post are in hand.
        // Asking per row afterwards would be one query per match on the cheapest read there is.
        Set<String> live = publishedIds(matches);
        List<ShAgentFound.Row> rows = dedupe(matches).stream()
                .filter(post -> folderIds.isEmpty() || inScope(post, folderIds))
                .filter(post -> type == null || type.isBlank() || matchesType(post, type))
                .map(post -> row(post, ids, live.contains(post.getId()), trash))
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
     *
     * <p><b>Not for a {@code File} (SH501).</b> A {@code File} is {@code id:}-addressed because it
     * never has a friendly URL — the type's design, and the reason the asset route exists — so the
     * {@code post.move} this note recommends would corrupt the content model rather than fix
     * anything. On a fidelity replica, whose {@code id:} rows are <em>all</em> assets, the note is
     * absent entirely: the same exemption {@code verify} makes for {@code url-missing}, on the
     * surface that phrased the advice.</p>
     */
    private static List<String> notesFor(List<ShAgentFound.Row> rows) {
        boolean anyById = rows.stream()
                .filter(row -> !ShSystemPostType.FILE.equalsIgnoreCase(row.type()))
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
        return readDocs(addresses, fields, ids, impact, null, false, false);
    }

    /**
     * @param state SH619 — which row to read: {@code published}, {@code draft}, or {@code null} for
     *              the draft-preferred default every other caller gets. See {@link #rowFor}
     */
    public List<ShAgentDoc> read(List<String> addresses, List<String> fields, boolean ids,
                                 boolean impact, String state) {
        return readDocs(addresses, fields, ids, impact, wanted(state), false, false);
    }

    /**
     * @param digest SH721 — attach each row's state digest, which is what {@code expect} on a write
     *               op takes. Opt-in on the same {@code include} parameter as {@code impact} and for
     *               the same reason: sixteen hex characters plus a key on every row of every read is
     *               a cost every caller pays and the one about to write actually uses
     */
    public List<ShAgentDoc> read(List<String> addresses, List<String> fields, boolean ids,
                                 boolean impact, String state, boolean digest) {
        return read(addresses, fields, ids, impact, state, digest, false);
    }

    /**
     * The same read, optionally carrying the console's own links (SH981).
     *
     * <p>Opt-in for SH721's reason, measured: the two links cost 26 est. tokens on a terse
     * row — a terse read went 28 to 54 — and the build-a-site benchmark went 800 over its
     * ceiling with them on every row. A field every reader pays for and one in fifty uses
     * is the P3 regression this surface measures for; the caller who wants a link is the
     * one about to hand the page to a person.</p>
     */
    public List<ShAgentDoc> read(List<String> addresses, List<String> fields, boolean ids,
                                 boolean impact, String state, boolean digest,
                                 boolean links) {
        return read(addresses, fields, ids, impact, state, digest, links, false);
    }

    /**
     * The same read, optionally carrying the page's history (SH987).
     *
     * <p>Two riders on parameters that already exist, which is why revisions needed no endpoint and
     * no second tool: {@code include=revisions} attaches the list, and {@code state=rev:<n>} reads
     * one revision in place of a row. The subject of both is an address, which is what a read
     * already takes.</p>
     *
     * @param revisions attach the list — terse by construction, never bodies
     */
    public List<ShAgentDoc> read(List<String> addresses, List<String> fields, boolean ids,
                                 boolean impact, String state, boolean digest,
                                 boolean links, boolean revisions) {
        return readDocs(addresses, fields, ids, impact, wanted(state), digest, links, revisions,
                revisionOf(state));
    }

    /**
     * {@code state=rev:<n>}, or {@code null} when the caller asked for a row (SH987).
     *
     * <p>Spelled inside {@code state} rather than as a parameter of its own because it <em>is</em>
     * the same question: which version of this address am I reading. A caller that has
     * {@code state=published} in a script changes one value to read what was published before it.</p>
     */
    private static Integer revisionOf(String state) {
        if (state == null || !state.trim().toLowerCase(java.util.Locale.ROOT).startsWith("rev:")) {
            return null;
        }
        String number = state.trim().substring("rev:".length());
        try {
            return Integer.valueOf(number.trim());
        } catch (NumberFormatException e) {
            throw new UnknownStateException(state);
        }
    }

    /**
     * The {@code state=} parameter, resolved (SH619).
     *
     * <p>P6: an unknown value is refused with the two that exist rather than silently falling back
     * to the default, because a typo that reads as "draft-preferred" answers a question the caller
     * did not ask and looks like a correct answer.</p>
     */
    private static ShPostState wanted(String state) {
        if (state == null || state.isBlank()) {
            return null;
        }
        String text = state.trim().toLowerCase(java.util.Locale.ROOT);
        // SH987 — `rev:<n>` is a state in the sense the caller means it, and the row it resolves
        // against is the draft-preferred one, so a revision of a published-only post still reads.
        if (text.startsWith("rev:")) {
            return null;
        }
        return switch (text) {
            case "published" -> ShPostState.PUBLISHED;
            case "draft" -> ShPostState.DRAFT;
            default -> throw new UnknownStateException(state);
        };
    }

    private List<ShAgentDoc> readDocs(List<String> addresses, List<String> fields, boolean ids,
                                      boolean impact, ShPostState state, boolean digest,
                                      boolean links) {
        return readDocs(addresses, fields, ids, impact, state, digest, links, false, null);
    }

    private List<ShAgentDoc> readDocs(List<String> addresses, List<String> fields, boolean ids,
                                      boolean impact, ShPostState state, boolean digest,
                                      boolean links, boolean revisions, Integer revision) {
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
                docs.add(doc(address, wanted, ids, impact, state, digest, links, revisions, revision));
                continue;
            }
            try {
                docs.add(doc(address, wanted, ids, impact, state, digest, links, revisions, revision));
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

    private ShAgentDoc doc(String rawAddress, List<String> fields, boolean ids, boolean impact,
                           ShPostState state, boolean digest, boolean links, boolean revisions,
                           Integer revision) {
        var resolved = shAddressResolver.resolve(rawAddress);
        ShPostUnified post = rowFor(rawAddress, resolved, state);
        // SH987 — `state=rev:<n>` answers with what the page said then, at the address it is at
        // now. The row is still read, because everything around the document — the type, the
        // folder, the links — is a fact about the post rather than about the revision.
        ShRevisionRow asked = revision == null ? null : shPostRevisionService
                .row(post.getId(), revision)
                .orElseThrow(() -> ShPostStateNotFoundException.noSuchRevision(post.getId(),
                        revision, shPostRevisionService.rows(post.getId()).stream()
                                .map(ShRevisionRow::version).toList()));
        JsonNode data = asked != null ? asked.data() : readTree(post.getData());
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
                .withTranslationOf(anchorAddress(post))
                .withPublished(isPublished(post) ? Boolean.TRUE : null)
                // SH585 — the stored spelling, when the address does not already say it. SH467
                // made it storable and nothing could read it back: `address` collapses, so a
                // `shio pull` of a replica rebuilt every directory-style page one character short
                // and Block I's promise that the tree describes a site whole stopped being true.
                // `withUrl` drops it when it equals the address's path, which is almost every post.
                .withUrl(post.getFurl())
                // SH721 — last in the chain and null unless asked, so a read that did not want it
                // is byte-identical to the one it was before this shipped.
                .withDigest(digest ? ShAgentPostDigest.of(post) : null)
                // SH981 — where this is in the console, and where a person looks at it. Built from
                // the declared route rather than concatenated here: the console's prefix has moved
                // once already, and a string built in Java would have rotted without a sound.
                .withLinks(links ? shConsoleSurfaces.curatorUrl("post", post.getId()) : null,
                        links ? previewUrl(post) : null)
                // SH987 — the page's history, terse and only when asked. Last in the chain for
                // SH721's reason: a read that did not ask is byte-identical to the one before this.
                .withRevisions(revisions ? shPostRevisionService.rows(post.getId()) : null);
    }

    /**
     * Where a person looks at this page (SH981), or {@code null}.
     *
     * <p>{@code /preview/<site>/<url>} is the route SH99 gave a curator, and it answers for a draft
     * as well as for a published page — which is the whole point of handing it over: the agent has
     * just written something nobody can see yet. Absent for a post with no site or no URL, because
     * a link that cannot resolve is worse than none.</p>
     */
    private String previewUrl(ShPostUnified post) {
        ShSite site = post.getShSite();
        String furl = post.getFurl();
        if (site == null || site.getName() == null || furl == null || furl.isBlank()) {
            return null;
        }
        return "/preview/" + site.getName() + (furl.startsWith("/") ? furl : "/" + furl);
    }

    /**
     * Whether this post is <b>live</b> (SH545) — which is not what {@code state} says.
     *
     * <p>{@code publish()} writes a second row and leaves the draft in place, and every read here
     * prefers the draft, so a page that has been serving publicly for weeks reports
     * {@code state: DRAFT}. The consequence was not cosmetic: an agent that has just published
     * could confirm it in only three ways, and all three are bad — publish again (correctly
     * answered {@code noop}), call the public route and read the state off an HTTP code, or report
     * a failure that did not happen. P9 asks that a change be provable from text.</p>
     *
     * <p>One PK lookup, and only when the row read was the draft: a post read <em>as</em>
     * {@code PUBLISHED} is one that has no draft (an SH38 uploaded file), so it is live by the
     * fact of being that row.</p>
     *
     * <p>Reported only when {@code true} — see {@code ShAgentDoc.published} for the measurement
     * that settled that, and the context pack's own first convention for why silence is an
     * answer here and not a gap.</p>
     */
    private boolean isPublished(ShPostUnified post) {
        return post.getState() == ShPostState.PUBLISHED
                || shPostUnifiedRepository.findByIdAndState(post.getId(), ShPostState.PUBLISHED)
                        .isPresent();
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

    /**
     * The trash, narrowed to the sites this credential may read (SH222).
     *
     * <p>The allow-list applies exactly as it does to a live listing. A trashed post is still
     * somebody's content, and a read that widened at the moment content was deleted would make
     * deletion a way to see more than you may.</p>
     */
    private List<ShPostUnified> trashed(List<ShSite> sites, String type) {
        Set<String> readable = sites.stream().map(ShSite::getId).collect(java.util.stream.Collectors
                .toCollection(LinkedHashSet::new));
        ShPostType postType = type == null || type.isBlank() ? null : requireType(type);
        // The null-to-default fallback ShPostUnifiedWriteService already carries. `getTenantId()`
        // answers null when nothing is bound, and the query compares to a literal — so passing it
        // straight through matches no row and reports an EMPTY TRASH on a single-tenant instance,
        // which is the one wrong answer this call must never give.
        String tenant = ShTenantContext.getTenantId();
        return shPostUnifiedRepository
                .findTrash(tenant != null ? tenant : ShTenantContext.DEFAULT_TENANT).stream()
                .filter(post -> post.getShSite() != null && readable.contains(post.getShSite().getId()))
                .filter(post -> postType == null
                        || (post.getShPostType() != null
                                && postType.getId().equals(post.getShPostType().getId())))
                .toList();
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

    /**
     * Whether this instance declares a post-type by that name (SH727).
     *
     * <p>For the caller that did <b>not</b> type the name. {@link #find} refuses an unresolvable
     * type and must keep doing so — the usual caller wrote {@code Artcle} and the refusal's
     * {@code allowed} list is the whole value of the message. {@code shio_assets} is the other
     * case: it filters on {@link com.viglet.shio.post.type.ShSystemPostType#FILE}, the product's
     * own name for its own row, and on an instance that has never held an upload there is no such
     * row — so the first assets call of a new install was refused, and told to create a post-type
     * nobody had asked about.</p>
     *
     * <p>A question rather than a widened {@code find}, because the distinction it protects is the
     * one that matters: absent-and-nobody-named-it is an empty answer, absent-and-you-named-it is
     * a teaching error.</p>
     */
    public boolean hasType(String name) {
        return name != null && !name.isBlank()
                && shPostTypeRepository.findByName(name.trim()) != null;
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

    private ShAgentFound.Row row(ShPostUnified post, boolean ids, boolean live) {
        return row(post, ids, live, false);
    }

    /**
     * @param trashed SH222 — force the {@code id:} form.
     *
     *                <p>A trashed post <b>keeps its friendly URL</b>, so {@code addressOf} still
     *                builds a path address — and that address does not resolve, because the post is
     *                not live. Handing it back would give a caller a name that reads like every
     *                other row and answers nothing, which is worse than no listing: the one thing
     *                this call exists to produce is an address a restore can take.</p>
     */
    private ShAgentFound.Row row(ShPostUnified post, boolean ids, boolean live, boolean trashed) {
        return new ShAgentFound.Row(
                trashed ? ShAddress.id(post.getId()).toString()
                        : shAddressResolver.addressOf(post).toString(),
                post.getShPostType() == null ? null : post.getShPostType().getName(),
                post.getState() == null ? null : post.getState().name(),
                post.getTitle(),
                ids ? post.getId() : null,
                nonDefaultLocale(post),
                live ? Boolean.TRUE : null);
    }

    /**
     * The ids among {@code rows} that have a {@code PUBLISHED} row (SH545).
     *
     * <p>The walk fetches {@link #BOTH_STATES} and {@code dedupe} then throws the published row
     * away, so publication is already in memory and this is the last moment it is. That is what
     * makes the field free on {@code find}: no second query, and none per row.</p>
     */
    private static Set<String> publishedIds(List<ShPostUnified> rows) {
        return rows.stream()
                .filter(row -> row.getState() == ShPostState.PUBLISHED)
                .map(ShPostUnified::getId)
                .collect(java.util.stream.Collectors.toSet());
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

    /**
     * The row this read is about (SH619).
     *
     * <h2>The moment the read surface could not reach</h2>
     *
     * <p>Every agent read is draft-preferred and took no {@code state}, so the published row's
     * <em>fields</em> were unreachable and "what will change when I publish this" had no answer
     * outside the console's own diff dialog — one post at a time, in a browser, to a human. SH545
     * added {@code published}, which answers <b>whether</b> a live row exists and is the right
     * field for that; this answers <b>what</b> is in it.</p>
     *
     * <p>A {@code state} that names a row the post does not have is a <b>404 that says which</b>,
     * never a fallback to the draft-preferred row. The fallback is the tempting one and it is the
     * single worst answer available here: a caller comparing draft against live would be handed
     * the draft twice and conclude that nothing changed.</p>
     *
     * <p>{@code null} keeps the default every other caller already gets, so nothing that omits the
     * parameter can behave differently — which is what makes this additive rather than a change to
     * the read contract.</p>
     */
    private ShPostUnified rowFor(String rawAddress, ShAgentResolved resolved,
                                 ShPostState state) {
        if (state == null) {
            return draftPreferred(resolved.id())
                    .orElseThrow(() -> new NotAPostException(rawAddress, resolved.kind()));
        }
        return shPostUnifiedRepository.findByIdAndState(resolved.id(), state)
                .orElseThrow(() -> draftPreferred(resolved.id()).isPresent()
                        ? new NoSuchStateException(rawAddress, state)
                        : new NotAPostException(rawAddress, resolved.kind()));
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

    /** A 400: {@code state=} took a value that is not one of the two rows a post can have. */
    public static class UnknownStateException extends ShTeachingException {

        public UnknownStateException(String sent) {
            super(ShProblem.badRequest("unknown-state", "'" + sent + "' is not a post state")
                    .withDetail("A post has at most two rows: the draft that is edited and the "
                            + "published one that is served. `state` picks which to read.")
                    .withAllowed(List.of("draft", "published"))
                    .withDidYouMeanFrom(sent)
                    .withFix("Use `state=published` to read what is live, `state=draft` for the "
                            + "editable copy, or omit `state` for the draft-preferred default.")
                    .withExample("?address=post:Blog/blog/hello-world&state=published"));
        }
    }

    /**
     * A 404: the row asked for does not exist, though the post does (SH619).
     *
     * <p>The distinction is the whole value of the message. "No published row" means <b>never
     * published</b>, and the caller's next move is to publish it; "no draft row" means an SH38
     * uploaded file, which has only the one row and is not a thing to publish. Answering either
     * with the draft-preferred row instead — the tempting fallback — would tell a caller comparing
     * draft against live that <em>nothing changed</em>, which is the one wrong answer this whole
     * capability exists to stop being unavoidable.</p>
     */
    public static class NoSuchStateException extends ShTeachingException {

        public NoSuchStateException(String address, ShPostState wanted) {
            super(ShProblem.of("no-such-state", org.springframework.http.HttpStatus.NOT_FOUND,
                            "'" + address + "' has no "
                                    + wanted.name().toLowerCase(java.util.Locale.ROOT) + " row")
                    .withDetail(wanted == ShPostState.PUBLISHED
                            ? "The post exists and has never been published, so there is nothing "
                                    + "live to compare against."
                            : "The post exists only as a published row, which is what an uploaded "
                                    + "file looks like: it has no editable draft.")
                    .withFix(wanted == ShPostState.PUBLISHED
                            ? "Publish it with a post.publish op (POST /api/v2/agent/batch), or "
                                    + "read it without `state` to get the draft."
                            : "Read it without `state`, which returns the row that exists.")
                    .withExample("?address=" + address));
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
