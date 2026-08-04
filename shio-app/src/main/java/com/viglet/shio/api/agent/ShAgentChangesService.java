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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.agent.diagnostics.ShAgentIncidentStore;
import com.viglet.shio.api.agent.dto.ShAgentChanges;
import com.viglet.shio.api.history.ShAuditFeedWatermark;
import com.viglet.shio.api.history.ShContentSubject;
import com.viglet.shio.persistence.model.agent.ShAgentIncident;
import com.viglet.shio.persistence.model.history.ShHistory;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.render.ShTemplateGraph;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.history.ShHistoryRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.spring.security.auth.ShApiTokenContext;

/**
 * The change feed (SH88) — a resumable read over the SH51 audit trail.
 *
 * <h2>What it is for</h2>
 *
 * <p>Two jobs, and both of them are Block I's: a human's console edits have to flow
 * <em>back</em> into the file tree ({@code shio pull --content}), and a follow-up session
 * has to be able to read the delta instead of re-reading the world. Both need a position
 * that survives between calls, which is precisely what SH83's window could not give —
 * see {@link com.viglet.shio.api.agent.dto.ShAgentChanges} for why a timestamp alone is
 * not one.</p>
 *
 * <h2>The ceiling, and why it is not a lag any more (SH147)</h2>
 *
 * <p>Audit rows are written <b>inside</b> the writer's transaction (SH51 chose that
 * deliberately, so an audit row commits with the change it describes), so their timestamps
 * are a <em>stamp</em> order and not a commit order: a transaction that stamped earlier can
 * commit after a faster one has already been read, and a cursor that moved past the faster
 * one would never see it. SH88 shipped a lag as the mitigation — hold back anything younger
 * than {@code now - lagMillis} — which is a guess about how long a commit takes, and a bulk
 * apply of hundreds of posts outran it.</p>
 *
 * <p>The ceiling is now {@link ShAuditFeedWatermark#ceiling()}: the position of the oldest
 * write that has stamped and not finished, exclusive. A row is reportable only once every
 * write stamped before it has committed or rolled back, which is a fact the registry holds
 * rather than a duration anybody estimated. {@code lagMillis} stays, defaults to <b>0</b>,
 * and means the one thing an in-process registry cannot see: another application node
 * writing to the same database. See {@link ShAuditFeedWatermark} for why a {@code seq}
 * column with gap detection was rejected — it relocates the guess rather than removing it.</p>
 *
 * <h2>Scope</h2>
 *
 * <p>A site-scoped credential sees the trail of the sites it can read, and nothing else.
 * That is not a refinement of SH83's tool — that tool read the whole trail regardless of
 * the SH2 allow-list, so a scoped token could see the titles of content it could not
 * fetch. Both surfaces now route through here, which is why the leak closes for both.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
@Transactional(readOnly = true)
public class ShAgentChangesService {

    private final ShHistoryRepository shHistoryRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShFolderRepository shFolderRepository;
    private final ShAgentSiteAccess siteAccess;
    private final ShAddressResolver shAddressResolver;
    private final ShAgentProperties properties;
    private final ShAgentIncidentStore incidentStore;
    private final ShTemplateGraph templateGraph;
    private final ShAuditFeedWatermark watermark;

    public ShAgentChangesService(ShHistoryRepository shHistoryRepository,
                                 ShPostUnifiedRepository shPostUnifiedRepository,
                                 ShFolderRepository shFolderRepository,
                                 ShAgentSiteAccess siteAccess,
                                 ShAddressResolver shAddressResolver,
                                 ShAgentProperties properties,
                                 ShAgentIncidentStore incidentStore,
                                 ShTemplateGraph templateGraph,
                                 ShAuditFeedWatermark watermark) {
        this.shHistoryRepository = shHistoryRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shFolderRepository = shFolderRepository;
        this.siteAccess = siteAccess;
        this.shAddressResolver = shAddressResolver;
        this.properties = properties;
        this.incidentStore = incidentStore;
        this.templateGraph = templateGraph;
        this.watermark = watermark;
    }

    /**
     * Reads the feed.
     *
     * @param site  site name / id / {@code site:} address to scope to; {@code null} for
     *              every readable site
     * @param since a cursor from a previous call; {@code null} starts from a window
     * @param limit entries to return; {@code null} uses the configured default
     * @param ids   include object ids (SH121 default: off)
     */
    public ShAgentChanges changes(String site, String since, Integer limit, boolean ids) {
        ShAgentProperties.Changes config = properties.getChanges();
        int cap = limit == null || limit <= 0
                ? config.getDefaultLimit()
                : Math.min(limit, config.getMaxLimit());
        // SH147 — the oldest write that has stamped and not finished, minus whatever margin an
        // operator configured for a node this registry cannot see. Not `now - lag`: that was a
        // guess about commit duration, and a bulk apply outran it.
        Instant ceiling = watermark.ceiling().minusMillis(Math.max(0, config.getLagMillis()));

        Scope scope = scope(site);
        ShAgentChangeCursor from = since == null || since.isBlank()
                ? null
                : ShAgentChangeCursor.parse(since);

        // limit + 1 on the feed so `more` is a fact rather than an inference from a full
        // page — a full page that happens to be the last one would otherwise send the
        // caller back for an empty round trip every time.
        List<ShHistory> entries = from == null
                ? newestFirst(scope, ceiling, cap)
                : afterCursor(scope, ceiling, from, cap + 1);
        boolean more = from != null && entries.size() > cap;
        if (more) {
            entries = entries.subList(0, cap);
        }
        if (from == null) {
            // The window query returns newest first; the response is always oldest first
            // so the cursor is always the last row (see the DTO's "two modes" note).
            entries = new ArrayList<>(entries);
            Collections.reverse(entries);
        }

        List<ShAgentChanges.Change> changes = shape(entries, scope, ids);
        String cursor = entries.isEmpty()
                ? (from == null ? new ShAgentChangeCursor(ceiling, "").toString() : from.toString())
                : new ShAgentChangeCursor(last(entries).getDate(), last(entries).getId()).toString();

        // SH144 — the incident stream, read over the same range and under the same cursor.
        Incidents failures = incidents(from, ceiling, cap, cursor, more);
        return new ShAgentChanges(changes.size(), from == null, since, failures.cursor(),
                more || failures.more(), changes,
                failures.rows().isEmpty() ? null : failures.rows(),
                notes(from == null, scope, config, changes, cap, failures));
    }

    /**
     * The incidents in this range, and what they do to the cursor.
     *
     * <h2>The invariant: an incident may repeat, a change may never be lost</h2>
     *
     * <p>Two streams paged independently under one scalar position cannot both be exactly-once.
     * The obvious move — return the further of the two positions — is <b>wrong</b>, and wrong in
     * the unacceptable direction: an instance whose newest incident is minutes ahead of the
     * audit page being walked would advance the cursor past audit rows the caller had never
     * seen, silently. (This is not hypothetical; it is what the SH88 tie-break test caught.)</p>
     *
     * <p>So the cursor advances past an incident only when the audit stream reports <b>no
     * more</b> — meaning it has been drained to the ceiling, and there is no unread row for a
     * later position to skip. While the audit page is still catching up, the cursor stays the
     * audit's and the incidents in that range are simply read again next time. Re-delivering an
     * incident costs nothing: rows are folded, so a repeat is idempotent, and an incident that
     * is still happening is re-delivered <em>by design</em> anyway.</p>
     *
     * <p><b>Incidents are not site-scoped, and the response says so.</b> They carry no site —
     * a 5xx on a request path or a failed image transform is an instance-level fact — so a
     * site-scoped read cannot narrow them and does not pretend to. That is not a new
     * visibility: the same caller can read {@code /agent/diagnostics}, which has been
     * instance-wide since SH103. Attributing an incident to a site where one can be known is
     * a follow-up, and until then the note is the honest form.</p>
     */
    private Incidents incidents(ShAgentChangeCursor from, Instant ceiling, int cap,
                                String changeCursor, boolean changesHaveMore) {
        List<ShAgentIncident> rows = from == null
                ? reversed(incidentStore.recent(cap, ceiling))
                : incidentStore.since(ceiling, from.at(), from.id(), cap + 1);
        boolean more = from != null && rows.size() > cap;
        if (more) {
            rows = rows.subList(0, cap);
        }
        List<ShAgentChanges.Incident> shaped = rows.stream()
                .map(ShAgentChangesService::incident)
                .toList();
        String cursor = changeCursor;
        if (!rows.isEmpty() && !changesHaveMore) {
            ShAgentIncident tail = rows.get(rows.size() - 1);
            String candidate =
                    new ShAgentChangeCursor(tail.getLastSeen(), tail.getId()).toString();
            cursor = ShAgentChangeCursor.later(changeCursor, candidate);
        }
        return new Incidents(shaped, cursor, more);
    }

    private static List<ShAgentIncident> reversed(List<ShAgentIncident> newestFirst) {
        List<ShAgentIncident> oldestFirst = new ArrayList<>(newestFirst);
        Collections.reverse(oldestFirst);
        return oldestFirst;
    }

    private static ShAgentChanges.Incident incident(ShAgentIncident row) {
        return new ShAgentChanges.Incident(
                row.getKind() == null
                        ? null
                        : row.getKind().toLowerCase(java.util.Locale.ROOT).replace('_', '-'),
                row.getSubject(),
                row.getMessage(),
                row.getFix(),
                row.getOccurrences(),
                row.getLastSeen() == null ? null : row.getLastSeen().toString(),
                row.getFirstSeen() == null ? null : row.getFirstSeen().toString(),
                row.getFrames());
    }

    private record Incidents(List<ShAgentChanges.Incident> rows, String cursor, boolean more) {
    }

    // --- querying ---

    private List<ShHistory> newestFirst(Scope scope, Instant ceiling, int cap) {
        PageRequest page = PageRequest.of(0, cap);
        if (scope.unrestricted()) {
            return shHistoryRepository.findWindow(ceiling, page);
        }
        return scope.siteIds().isEmpty()
                ? List.of()
                : shHistoryRepository.findWindowInSites(ceiling, scope.siteIds(), page);
    }

    private List<ShHistory> afterCursor(Scope scope, Instant ceiling, ShAgentChangeCursor from,
                                        int cap) {
        PageRequest page = PageRequest.of(0, cap);
        if (scope.unrestricted()) {
            return shHistoryRepository.findSince(ceiling, from.at(), from.id(), page);
        }
        return scope.siteIds().isEmpty()
                ? List.of()
                : shHistoryRepository.findSinceInSites(ceiling, scope.siteIds(), from.at(),
                        from.id(), page);
    }

    /**
     * Which sites this call may read.
     *
     * <p>{@code unrestricted} means "no allow-list applies", which is the console case and
     * the reason entries whose site could not be recorded are still visible there: an
     * admin reading their own instance's audit trail should not silently lose rows. A
     * token-scoped read never gets that fallback, because an unattributable row cannot be
     * proven to be inside the allow-list.</p>
     */
    private Scope scope(String site) {
        if (site != null && !site.isBlank()) {
            List<ShSite> sites = siteAccess.readable(site);
            return new Scope(false, sites.stream().map(ShSite::getId).toList(), byId(sites));
        }
        List<ShSite> readable = siteAccess.readable();
        boolean unrestricted = ShApiTokenContext.current().isEmpty();
        return new Scope(unrestricted, readable.stream().map(ShSite::getId).toList(), byId(readable));
    }

    private record Scope(boolean unrestricted, List<String> siteIds, Map<String, String> names) {
    }

    private static Map<String, String> byId(List<ShSite> sites) {
        Map<String, String> names = new HashMap<>();
        for (ShSite site : sites) {
            names.put(site.getId(), site.getName());
        }
        return names;
    }

    // --- shaping ---

    private List<ShAgentChanges.Change> shape(List<ShHistory> entries, Scope scope, boolean ids) {
        // One lookup per distinct object, not per row: a post that was created, updated
        // and published appears three times in a feed and has one address.
        Map<String, Optional<String>> addresses = new LinkedHashMap<>();
        // SH193 — one graph read per distinct object, for the same reason the addresses are
        // memoized: a region that was updated and published is two rows and one blast radius.
        Map<String, Optional<ShAgentChanges.Affects>> impacts = new LinkedHashMap<>();
        List<ShAgentChanges.Change> changes = new ArrayList<>(entries.size());
        for (ShHistory entry : entries) {
            String objectId = entry.getShObject();
            ShContentSubject subject = ShContentSubject.of(entry.getSubjectKind());
            Optional<String> address = objectId == null
                    ? Optional.empty()
                    : addresses.computeIfAbsent(objectId, id -> addressOf(id, subject));
            // A folder has no template graph, so the SH193 read is skipped rather than run and
            // discarded — a folder-heavy feed would otherwise pay a post lookup per row for a
            // question folders cannot raise.
            Optional<ShAgentChanges.Affects> affects = objectId == null
                    || subject == ShContentSubject.FOLDER
                    ? Optional.empty()
                    : impacts.computeIfAbsent(objectId, this::affectsOf);
            changes.add(new ShAgentChanges.Change(
                    entry.getAction(),
                    subject == ShContentSubject.POST ? null : subject.wire(),
                    address.orElse(null),
                    entry.getDate() == null ? null : entry.getDate().toString(),
                    entry.getOwner(),
                    entry.getDescription(),
                    entry.getShSite() == null ? null : scope.names().get(entry.getShSite()),
                    ids ? objectId : null,
                    affects.orElse(null)));
        }
        return List.copyOf(changes);
    }

    /**
     * The blast radius of a template change, or empty for everything else (SH193).
     *
     * <p>DRAFT-preferred like {@link #addressOf}, because the graph is a question about the
     * template as it now stands rather than as it was published. A row whose object is gone — the
     * normal state of a {@code DELETE} — gets nothing, which is right: the pages it used to reach
     * are answered by the entry itself, and computing a graph from a deleted template would
     * describe a site that no longer exists.</p>
     */
    private Optional<ShAgentChanges.Affects> affectsOf(String objectId) {
        List<ShPostUnified> rows = shPostUnifiedRepository.findById(objectId);
        return rows.stream()
                .filter(row -> row.getState() == ShPostState.DRAFT)
                .findFirst()
                .or(() -> rows.stream().findFirst())
                .flatMap(templateGraph::impactOf)
                .map(impact -> new ShAgentChanges.Affects(impact.kind(), impact.layouts(),
                        impact.postTypes(), impact.pages(), impact.published()));
    }

    /**
     * DRAFT-preferred, like every other agent read, so the address matches what a read
     * returns — and a {@code folder:} address when the audited object is a folder (SH200).
     *
     * <p>The row says which it is (SH196), so this asks one repository rather than both: before
     * the subject was stored, every folder entry paid for a post lookup that could only miss.
     * The folder lookup is the one that may see a trashed row on purpose: the entry the caller
     * most needs to address is the deletion, and a deletion that arrived without an address would
     * be a change nobody can act on.</p>
     */
    private Optional<String> addressOf(String objectId, ShContentSubject subject) {
        if (subject == ShContentSubject.FOLDER) {
            return shFolderRepository.findAnyById(objectId)
                    .map(folder -> shAddressResolver.addressOf(folder).toString());
        }
        List<ShPostUnified> rows = shPostUnifiedRepository.findById(objectId);
        return rows.stream()
                .filter(row -> row.getState() == ShPostState.DRAFT)
                .findFirst()
                .or(() -> rows.stream().findFirst())
                .map(row -> shAddressResolver.addressOf(row).toString())
                // Kept as a fallback for a row written before the subject column existed: those
                // are POST by default, and a folder delete audited by SH200 is one of them.
                .or(() -> shFolderRepository.findAnyById(objectId)
                        .map(folder -> shAddressResolver.addressOf(folder).toString()));
    }

    /**
     * What the response cannot show. Every note is here because its absence would make a
     * true statement read as a stronger one (the SH103 rule, and SH138's).
     */
    private List<String> notes(boolean window, Scope scope,
                              ShAgentProperties.Changes config,
                              List<ShAgentChanges.Change> changes, int cap,
                              Incidents failures) {
        List<String> notes = new ArrayList<>();
        if (!failures.rows().isEmpty()) {
            // Two facts a caller has to know before acting on the array: it is not narrowed by
            // `site=`, and a row it has seen before can come back because it happened again.
            notes.add(failures.rows().size() + " incident(s) are included: asynchronous failures "
                    + "in this range. They carry no site, so `site=` does not narrow them, and a "
                    + "recurring one reappears with a higher count because its position is its "
                    + "last occurrence");
        }
        if (window) {
            notes.add("window: the most recent " + cap + " entries at most, not a delta — pass "
                    + "since=<cursor> to get everything after this position");
        }
        if (config.getLagMillis() > 0) {
            // SH159: the note names the lag, not the ceiling instant it produces. The instant
            // was `now - lag`, recomputed per call, so this line alone made every response
            // different bytes from the last one and no `changes` read could ever revalidate —
            // on the endpoint whose whole job is being polled. It also told the caller nothing
            // it could act on: the actionable number is how far behind the edge of the feed is,
            // and that is a constant.
            //
            // SH147 turned it into a disclosure about *another node*, and made it conditional in
            // practice as well as in code: the default is 0 now, so a single-instance feed pays
            // no prose for a hole it does not have.
            notes.add("entries younger than " + config.getLagMillis() + "ms are also held back, "
                    + "a margin for a write this instance cannot see (another node against the "
                    + "same database); a write in flight on this instance holds the feed back on "
                    + "its own, for exactly as long as it runs");
        }
        if (!scope.unrestricted()) {
            notes.add("scoped to " + scope.siteIds().size() + " readable site(s); entries for "
                    + "other sites, and entries whose site was not recorded, are not shown");
        }
        if (changes.stream().anyMatch(change -> change.address() == null)) {
            notes.add("an entry with no address refers to an object that no longer exists — "
                    + "usually its own deletion; its title is in `what`");
        }
        return List.copyOf(notes);
    }

    private static ShHistory last(List<ShHistory> entries) {
        return entries.get(entries.size() - 1);
    }
}
