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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.agent.diagnostics.ShAgentIncidentLog;
import com.viglet.shio.api.agent.diagnostics.ShAgentIncidentStore;
import com.viglet.shio.api.agent.dto.ShAgentChanges;
import com.viglet.shio.api.form.ShFormSubmitter;
import com.viglet.shio.api.history.ShAuditFeedWatermark;
import com.viglet.shio.api.history.ShChangedFields;
import com.viglet.shio.api.history.ShContentAction;
import com.viglet.shio.api.history.ShContentSubject;
import com.viglet.shio.persistence.model.agent.ShAgentIncident;
import com.viglet.shio.persistence.model.history.ShHistory;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.render.ShTemplateGraph;
import com.viglet.shio.schedule.ShPostScheduler;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.history.ShHistoryRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.api.error.ShTeachingException;
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
 * <h2>The token ceiling (SH723)</h2>
 *
 * <p>{@code limit} is a <b>row</b> cap and a row's cost is not fixed, so the documented maximum
 * of 500 produced about 20,000 estimated tokens — the largest response on the agent surface, and
 * the only one with no budget, while the context pack self-limits at 4,000. The feed now carries
 * one too: rows are charged against {@link ShTokenBudget} before they are committed, the cut is
 * reported in {@code budget.truncated} and in a note, and in cursor mode the cursor stops at the
 * last row that fit. See {@link com.viglet.shio.api.agent.dto.ShAgentChanges.Budget} for which
 * end of the page a cut takes and why the two modes differ.</p>
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
@Transactional(readOnly = true, noRollbackFor = ShTeachingException.class)
public class ShAgentChangesService {

    /** How a recorder names a post it has only the id of — the sweep's, among others. */
    private static final String ID_SUBJECT = "id:";

    private final ShHistoryRepository shHistoryRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShFolderRepository shFolderRepository;
    private final ShAgentSiteAccess siteAccess;
    private final ShAddressResolver shAddressResolver;
    private final ShAgentProperties properties;
    private final ShAgentIncidentStore incidentStore;
    private final ShTemplateGraph templateGraph;
    private final ShAuditFeedWatermark watermark;
    private final ShAgentRunService shAgentRunService;

    public ShAgentChangesService(ShHistoryRepository shHistoryRepository,
                                 ShPostUnifiedRepository shPostUnifiedRepository,
                                 ShFolderRepository shFolderRepository,
                                 ShAgentSiteAccess siteAccess,
                                 ShAddressResolver shAddressResolver,
                                 ShAgentProperties properties,
                                 ShAgentIncidentStore incidentStore,
                                 ShTemplateGraph templateGraph,
                                 ShAuditFeedWatermark watermark,
                                 ShAgentRunService shAgentRunService) {
        this.shHistoryRepository = shHistoryRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shFolderRepository = shFolderRepository;
        this.siteAccess = siteAccess;
        this.shAddressResolver = shAddressResolver;
        this.properties = properties;
        this.incidentStore = incidentStore;
        this.templateGraph = templateGraph;
        this.watermark = watermark;
        this.shAgentRunService = shAgentRunService;
    }

    /** Before SH723 gave the feed a token ceiling — {@code null} takes the configured one. */
    public ShAgentChanges changes(String site, String since, Integer limit, boolean ids) {
        return changes(site, since, limit, ids, null);
    }

    /**
     * Reads the feed.
     *
     * @param site   site name / id / {@code site:} address to scope to; {@code null} for
     *               every readable site
     * @param since  a cursor from a previous call; {@code null} starts from a window
     * @param limit  entries to return; {@code null} uses the configured default
     * @param ids    include object ids (SH121 default: off)
     * @param budget SH723 — estimated-token ceiling on the page; {@code null} takes the
     *               configured one, {@code 0} asks for none. See
     *               {@link ShAgentChanges.Budget} for which end of the page a cut takes,
     *               and why that differs between the two modes
     */
    public ShAgentChanges changes(String site, String since, Integer limit, boolean ids,
                                  Integer budget) {
        return changes(site, since, limit, ids, budget, false);
    }

    /**
     * Reads the feed, naming the fields each write changed when asked (SH989).
     *
     * @param fields attach the changed field names a row recorded. Off by default so an ordinary
     *               row is byte-identical to what it was; the names are charged against the budget
     *               with the row, because they are part of the line the caller is sent
     */
    public ShAgentChanges changes(String site, String since, Integer limit, boolean ids,
                                  Integer budget, boolean fields) {
        return changes(site, since, limit, ids, budget, fields, null);
    }

    /**
     * Reads the feed, narrowed to who wrote it (SH999).
     *
     * <h2>Filtered in the query, never after the page</h2>
     *
     * <p>A post-filter would hand back a short page — or an empty one — while the rows the caller
     * asked for sat behind the cursor, and the caller would read that as "nothing happened". The
     * predicate rides with the keyset, so a filtered feed pages exactly the way the whole one
     * does.</p>
     *
     * @param by {@code agent} for every run's writes, {@code human} for every person's — <b>which
     *           includes a curator's console session</b>, because that is a person (SH996) — or a
     *           session id for one run. {@code null} is everything, and is what every caller before
     *           this one passes, so the unfiltered feed is the result set it always was
     */
    public ShAgentChanges changes(String site, String since, Integer limit, boolean ids,
                                  Integer budget, boolean fields, String by) {
        String wanted = by == null || by.isBlank() ? null : by.trim();
        // A word or an id, told apart here rather than in the query: `agent` and `human` are the
        // vocabulary, and anything else is a session, which is the one case that needs no list of
        // valid values — a session nobody wrote under is an empty feed, not a refusal.
        String kind = "agent".equalsIgnoreCase(wanted) || "human".equalsIgnoreCase(wanted)
                ? wanted.toLowerCase(java.util.Locale.ROOT) : null;
        String session = wanted == null || kind != null ? null : wanted;
        ShAgentProperties.Changes config = properties.getChanges();
        int cap = limit == null || limit <= 0
                ? config.getDefaultLimit()
                : Math.min(limit, config.getMaxLimit());
        ShTokenBudget tokens = new ShTokenBudget(resolveBudget(budget, config));
        // SH147 — the oldest write that has stamped and not finished, minus whatever margin an
        // operator configured for a node this registry cannot see. Not `now - lag`: that was a
        // guess about commit duration, and a bulk apply outran it.
        Instant ceiling = watermark.ceiling().minusMillis(Math.max(0, config.getLagMillis()));

        Scope scope = scope(site);
        ShAgentChangeCursor from = since == null || since.isBlank()
                ? null
                : ShAgentChangeCursor.parse(since);
        boolean window = from == null;

        // limit + 1 on the feed so `more` is a fact rather than an inference from a full
        // page — a full page that happens to be the last one would otherwise send the
        // caller back for an empty round trip every time.
        List<ShHistory> entries = window
                ? newestFirst(scope, ceiling, cap, session, kind)
                : afterCursor(scope, ceiling, from, cap + 1, session, kind);
        boolean more = !window && entries.size() > cap;
        if (more) {
            entries = entries.subList(0, cap);
        }
        if (window) {
            // The window query returns newest first; the response is always oldest first
            // so the cursor is always the last row (see the DTO's "two modes" note).
            entries = new ArrayList<>(entries);
            Collections.reverse(entries);
        }

        // SH144 — the incident stream, read over the same range. SH204 — and under the same
        // scope, which it was not before. Read before the budget gate rather than after,
        // because SH723 charges it as a *bounded* section: charging it afterwards would let
        // the gate admit a change row that the later charge then pushes over the ceiling,
        // which is the ordering mistake the context pack's `chargeBounded` exists to avoid.
        IncidentRows failures = incidentRows(from, ceiling, cap, scope);
        for (ShAgentChanges.Incident incident : failures.rows()) {
            tokens.add(ShAgentChangesTerse.incident(incident));
        }

        List<ShAgentChanges.Change> candidates = shape(entries, scope, ids, fields);
        Page page = gate(candidates, tokens, window);
        List<ShAgentChanges.Change> changes = candidates.subList(page.start(), page.end());
        // The window's cursor is the *edge of the feed*, so it stays the newest row this
        // range held even when the budget dropped rows off the old end — a window promises
        // "the most recent N", never the ones before them. The feed's cursor is the last row
        // that fit, which is what makes a budget cut resumable instead of lossy.
        List<ShHistory> positioned = window ? entries : entries.subList(page.start(), page.end());
        String cursor = cursorOf(positioned, window, from);
        // A budget cut in feed mode is exactly the `more` case SH88 already had: rows remain
        // after this cursor, so say so. In window mode it is not — the rows dropped are older
        // than the window's own edge, and no `since=` walks backwards to them.
        boolean cut = page.truncated(candidates.size());
        boolean changesHaveMore = more || (!window && cut);

        // Built with the ceiling already in place and the estimate still zero: the terse header
        // quotes `max-tokens` and not `est` precisely so this draft renders byte-identically to
        // what is finally returned, which is what lets the header be charged before it is known.
        ShAgentChanges draft = new ShAgentChanges(changes.size(), window, since,
                cursorWithIncidents(cursor, failures, changesHaveMore),
                changesHaveMore || failures.more(), changes,
                failures.rows().isEmpty() ? null : failures.rows(),
                notes(window, scope, config, changes, cap, failures, cut, candidates.size(),
                        tokens.limitTokens()),
                new ShAgentChanges.Budget(0, tokens.limitTokens(), cut));
        // Charged last because a note is written from what survived the gate, and the header
        // from the cursor those notes describe. Between them these four charges are exactly
        // `ShAgentChangesTerse.render(draft)` — which is the invariant SH723's test asserts,
        // and the reason `est` can be trusted as this response's real cost.
        for (String note : draft.notes()) {
            tokens.add(ShAgentChangesTerse.note(note));
        }
        tokens.add(ShAgentChangesTerse.header(draft));
        return withBudget(draft, new ShAgentChanges.Budget(tokens.spentTokens(),
                tokens.limitTokens(), cut));
    }

    /**
     * Which rows of {@code candidates} fit the budget — a half-open range into the page.
     *
     * <p><b>At least one row always survives.</b> A page that fit nothing would, in feed mode,
     * leave the cursor where it was and {@code more} true: the caller would poll the same
     * position for ever. A budget may make the feed slower to walk; it may never make it
     * unwalkable, so the first row is charged unconditionally when nothing else fits.</p>
     *
     * @param window cut the <em>oldest</em> rows (a window narrows) rather than the newest
     *               (a delta stops early and resumes)
     */
    private static Page gate(List<ShAgentChanges.Change> candidates, ShTokenBudget tokens,
                             boolean window) {
        if (candidates.isEmpty()) {
            return new Page(0, 0);
        }
        int kept = 0;
        for (int i = 0; i < candidates.size(); i++) {
            // Charged in emission order for a delta and newest-first for a window, so the row
            // a cut keeps is the one that mode promises.
            int index = window ? candidates.size() - 1 - i : i;
            if (!tokens.tryAdd(ShAgentChangesTerse.event(candidates.get(index)))) {
                break;
            }
            kept++;
        }
        if (kept == 0) {
            int index = window ? candidates.size() - 1 : 0;
            tokens.add(ShAgentChangesTerse.event(candidates.get(index)));
            kept = 1;
        }
        return window
                ? new Page(candidates.size() - kept, candidates.size())
                : new Page(0, kept);
    }

    /** A half-open range of the shaped page that survived the budget (SH723). */
    private record Page(int start, int end) {

        boolean truncated(int candidates) {
            return end - start < candidates;
        }
    }

    /** {@code null} = the configured ceiling, {@code 0} = none, anything else is clamped. */
    private static int resolveBudget(Integer budget, ShAgentProperties.Changes config) {
        int max = config.getMaxBudgetTokens();
        if (budget == null) {
            return max;
        }
        if (budget < 0) {
            // The context pack's, deliberately: one wrong budget should read the same wherever
            // it was sent, and a second exception saying the same thing is a second thing to
            // keep true (P6).
            throw new ShAgentContextService.InvalidBudgetException(budget, max);
        }
        return budget == 0 ? 0 : Math.min(budget, max);
    }

    private static ShAgentChanges withBudget(ShAgentChanges draft, ShAgentChanges.Budget budget) {
        return new ShAgentChanges(draft.found(), draft.window(), draft.since(), draft.cursor(),
                draft.more(), draft.changes(), draft.incidents(), draft.notes(), budget);
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
     * <h2>SH204 — incidents <em>are</em> site-scoped now, and the ones that cannot be are not</h2>
     *
     * <p>SH144 shipped a note saying {@code site=} does not narrow this array, because an incident
     * carried no site at all. Four of the six recorders can attribute a failure and now do, so the
     * note was describing a hole rather than a property, and it is gone.</p>
     *
     * <p>The rule is the audit stream's, exactly, and it has to be — the two are merged under one
     * cursor, so a row one of them hides and the other shows would make a single {@code since=}
     * mean two different things. An unattributed incident is visible to an <b>unrestricted</b> read
     * and to no scoped one, which falls out of {@code null in (…)} rather than being written. Note
     * that {@code unrestricted} is false whenever {@code site=} was passed, even for a console
     * session: asking for one site is itself a narrowing, and answering it with another site's
     * failures is the mismatch this task exists to remove.</p>
     *
     * <p><b>What a scoped caller loses, it loses only here.</b> {@code api-error} and
     * {@code transform} genuinely belong to no site, so a token-scoped {@code /agent/changes} no
     * longer shows them — and {@code /agent/diagnostics} still does, instance-wide, as it has since
     * SH103. The note says so rather than leaving a caller to discover it, because an agent's own
     * 5xx is the incident it most wants and the last one it would think to look elsewhere for.</p>
     */
    private IncidentRows incidentRows(ShAgentChangeCursor from, Instant ceiling, int cap,
                                      Scope scope) {
        // null means "no allow-list applies" to the store, which is the one case that reads rows
        // with no site; a list — even a full one — narrows to attributed rows in those sites.
        List<String> siteIds = scope.unrestricted() ? null : scope.siteIds();
        List<ShAgentIncident> rows = from == null
                ? reversed(incidentStore.recent(cap, ceiling, siteIds))
                : incidentStore.since(ceiling, from.at(), from.id(), cap + 1, siteIds);
        boolean more = from != null && rows.size() > cap;
        if (more) {
            rows = rows.subList(0, cap);
        }
        Map<String, Optional<String>> addresses = new HashMap<>();
        List<ShAgentChanges.Incident> shaped = rows.stream()
                .map(row -> incident(row, scope, addresses))
                .toList();
        ShAgentIncident tail = rows.isEmpty() ? null : rows.get(rows.size() - 1);
        return new IncidentRows(shaped, more,
                tail == null ? null
                        : new ShAgentChangeCursor(tail.getLastSeen(), tail.getId()).toString());
    }

    /**
     * Folds the incident stream's position into the change stream's, under the rule above.
     *
     * <p>SH723 widened what counts as "the audit page is still catching up": a page the token
     * budget cut has unread rows after it just as surely as one the row cap cut, so the cursor
     * must not advance past them either. That is why {@code changesHaveMore} is computed from
     * both and passed in rather than being read off the row count.</p>
     */
    private static String cursorWithIncidents(String changeCursor, IncidentRows failures,
                                              boolean changesHaveMore) {
        if (failures.tailCursor() == null || changesHaveMore) {
            return changeCursor;
        }
        return ShAgentChangeCursor.later(changeCursor, failures.tailCursor());
    }

    /**
     * The position to hand back, and the one place SH232 changed.
     *
     * <h2>Why an empty window's cursor is the start of the trail and not the ceiling</h2>
     *
     * <p>SH232 was filed saying the window can never revalidate, because its cursor is the
     * moving edge of the feed. SH147 had already made that untrue for a window with rows in it
     * — the cursor is the newest row's position, which does not move until a write happens, so
     * two polls a second apart are byte-identical and the second is a {@code 304}. Measuring it
     * left exactly one case: an <b>empty</b> window, whose cursor was
     * {@link ShAuditFeedWatermark#ceiling()} and therefore {@code now} to the microsecond.</p>
     *
     * <p>That is the poll this endpoint exists for — "has anything happened since I got here",
     * on an instance where the honest answer is usually no — so it was the one read that could
     * never be free, on the surface most repeated by a session that kept no cursor.</p>
     *
     * <p>An empty window means something stronger than "no rows on this page": the query is
     * {@code (scope, < ceiling)} with a limit of at least one, so an empty result is proof that
     * <b>no row exists in scope below the ceiling at all</b>. A position at the start of the
     * trail therefore skips nothing that a position at the ceiling would have skipped, and it
     * is a constant. The caller's next {@code since=} returns everything written after this
     * poll — which is what it was asking for — and the poll itself costs a tag.</p>
     */
    private static String cursorOf(List<ShHistory> positioned, boolean window,
                                   ShAgentChangeCursor from) {
        if (!positioned.isEmpty()) {
            ShHistory tail = positioned.get(positioned.size() - 1);
            return new ShAgentChangeCursor(tail.getDate(), tail.getId()).toString();
        }
        if (!window) {
            // A cursor read that found nothing holds its position, which is already stable —
            // and must be, or a quiet instance would send the caller back to a window.
            return from.toString();
        }
        return new ShAgentChangeCursor(Instant.EPOCH, "").toString();
    }

    private static List<ShAgentIncident> reversed(List<ShAgentIncident> newestFirst) {
        List<ShAgentIncident> oldestFirst = new ArrayList<>(newestFirst);
        Collections.reverse(oldestFirst);
        return oldestFirst;
    }

    /**
     * SH204 — the site is emitted as a <b>name</b>, resolved the same way a change row's is.
     *
     * <p>Through {@code scope.names()} rather than a lookup, which does the safe thing twice over:
     * the map holds only sites this caller may read, so an id it does not cover yields no
     * {@code site} rather than a name the caller was not entitled to — and there is no query on a
     * per-row path. An unrestricted read covers every site, so the only ids that fall through are
     * an unattributed row's absent one and a site deleted since the failure, and both are honestly
     * spelled as absence.</p>
     */
    private ShAgentChanges.Incident incident(ShAgentIncident row, Scope scope,
                                             Map<String, Optional<String>> addresses) {
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
                row.getFrames(),
                row.getSiteId() == null ? null : scope.names().get(row.getSiteId()),
                retryOf(row, addresses));
    }

    /**
     * SH969 — how to attempt a failed scheduled transition now, or {@code null}.
     *
     * <p>Only a {@code schedule} incident names one, and only while its post exists: a retry whose
     * address resolves to nothing is a button that answers 404. The address is resolved here, at
     * read time, rather than recorded with the failure, so a post renamed since then is retried at
     * the address it has now — and the sweep's catch stays free of a lookup that could itself throw
     * (the SH204 reasoning). Memoized per read, like a change row's address, because a failure that
     * keeps happening is one row per post however often it is read.</p>
     */
    private ShAgentChanges.Retry retryOf(ShAgentIncident row,
                                         Map<String, Optional<String>> addresses) {
        String subject = row.getSubject();
        String op = ShAgentIncidentLog.Kind.SCHEDULE.name().equals(row.getKind())
                ? ShPostScheduler.retryOp(row.getMessage())
                : null;
        if (op == null || subject == null || !subject.startsWith(ID_SUBJECT)) {
            return null;
        }
        return addresses.computeIfAbsent(subject.substring(ID_SUBJECT.length()),
                        id -> addressOf(id, ShContentSubject.POST))
                .map(address -> new ShAgentChanges.Retry(op, address))
                .orElse(null);
    }

    /**
     * The incident page, and the position its last row would advance the cursor to.
     *
     * <p>SH723 split this out of the folded {@code Incidents}: the rows have to be read — and
     * charged against the token budget — <em>before</em> the change rows are gated, while the
     * cursor can only be folded in <em>after</em>, because whether the change stream still has
     * more now depends on whether the budget cut it.</p>
     */
    private record IncidentRows(List<ShAgentChanges.Incident> rows, boolean more,
                                String tailCursor) {
    }

    // --- querying ---

    private List<ShHistory> newestFirst(Scope scope, Instant ceiling, int cap, String session,
                                        String kind) {
        PageRequest page = PageRequest.of(0, cap);
        if (scope.unrestricted()) {
            return shHistoryRepository.findWindow(ceiling, session, kind, page);
        }
        return scope.siteIds().isEmpty()
                ? List.of()
                : shHistoryRepository.findWindowInSites(ceiling, scope.siteIds(), session, kind,
                        page);
    }

    private List<ShHistory> afterCursor(Scope scope, Instant ceiling, ShAgentChangeCursor from,
                                        int cap, String session, String kind) {
        PageRequest page = PageRequest.of(0, cap);
        if (scope.unrestricted()) {
            return shHistoryRepository.findSince(ceiling, from.at(), from.id(), session, kind, page);
        }
        return scope.siteIds().isEmpty()
                ? List.of()
                : shHistoryRepository.findSinceInSites(ceiling, scope.siteIds(), from.at(),
                        from.id(), session, kind, page);
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

    private List<ShAgentChanges.Change> shape(List<ShHistory> entries, Scope scope, boolean ids,
                                              boolean fields) {
        // One lookup per distinct object, not per row: a post that was created, updated
        // and published appears three times in a feed and has one address.
        Map<String, Optional<String>> addresses = new LinkedHashMap<>();
        // SH193 — one graph read per distinct object, for the same reason the addresses are
        // memoized: a region that was updated and published is two rows and one blast radius.
        Map<String, Optional<ShAgentChanges.Affects>> impacts = new LinkedHashMap<>();
        // SH999 — one query for the page's runs, the way the addresses are memoized: twenty rows of
        // one run is one run, and a name resolved per row is nineteen queries answering a question
        // already answered.
        Map<String, String> runNames = shAgentRunService.names(entries.stream()
                .map(ShHistory::getAgentSessionId)
                .filter(session -> session != null && !ShAgentSessionContext.isConsole(session))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
        List<ShAgentChanges.Change> changes = new ArrayList<>(entries.size());
        for (ShHistory entry : entries) {
            // SH376 — a position this JVM never issued is proof of a second application node, and
            // the one thing SH147 could not say. Observed here rather than in the query because
            // this is where every row the feed actually reports passes through.
            watermark.observe(entry.getDate());
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
            // SH970 — only the CREATE a form wrote, so every other row reads no post for it.
            Optional<ShAgentChanges.Submission> submission = objectId != null
                    && subject == ShContentSubject.POST
                    && ShContentAction.CREATE.name().equals(entry.getAction())
                    && ShFormSubmitter.isSubmitter(entry.getOwner())
                    ? submissionOf(objectId)
                    : Optional.empty();
            // SH999 — a console session is a curator's own publishing (SH996), so it is a person
            // and carries no agent. Reported absent rather than as a session nobody can look up.
            String run = entry.getAgentSessionId() == null
                    || ShAgentSessionContext.isConsole(entry.getAgentSessionId())
                    ? null : entry.getAgentSessionId();
            changes.add(new ShAgentChanges.Change(
                    entry.getAction(),
                    subject == ShContentSubject.POST ? null : subject.wire(),
                    address.orElse(null),
                    entry.getDate() == null ? null : entry.getDate().toString(),
                    entry.getOwner(),
                    run,
                    run == null ? null : runNames.get(run),
                    entry.getDescription(),
                    entry.getShSite() == null ? null : scope.names().get(entry.getShSite()),
                    ids ? objectId : null,
                    affects.orElse(null),
                    submission.orElse(null),
                    // SH989 — read off the row, never recomputed: after a publish the two documents
                    // it compared are one document, so the row is the only place the answer is.
                    fields ? ShChangedFields.decode(entry.getChangedFields()) : null));
        }
        return List.copyOf(changes);
    }

    /**
     * A submission's caption, read from the post as it now stands (SH970).
     *
     * <p>Not memoized, unlike the address: a post has one {@code CREATE}, so a feed holds a
     * submission's arrival once. A submission already moved to the trash yields nothing. Its arrival
     * is still a {@code CREATE} by the form, and the {@code DELETE} after it says what became of
     * it.</p>
     */
    private Optional<ShAgentChanges.Submission> submissionOf(String postId) {
        List<ShPostUnified> rows = shPostUnifiedRepository.findById(postId);
        return rows.stream()
                .filter(row -> row.getState() == ShPostState.DRAFT)
                .findFirst()
                .or(() -> rows.stream().findFirst())
                .map(row -> new ShAgentChanges.Submission(row.getTitle(),
                        row.getSummary() == null || row.getSummary().isBlank()
                                ? null
                                : row.getSummary()));
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
                              IncidentRows failures, boolean cut, int candidates,
                              int budgetTokens) {
        List<String> notes = new ArrayList<>();
        // SH723 — the structured marker is `budget.truncated`, but the terse rendering an MCP
        // caller reads has no such field, and a cut that is not visible reads as a clean answer
        // (SH138). Said only when it happened, so an uncut feed pays nothing for it (P3).
        if (cut) {
            notes.add(window
                    ? "budget: this window was narrowed to the most recent " + changes.size()
                            + " of " + candidates + " entries to stay under " + budgetTokens
                            + " estimated tokens; the older ones are outside the window, not "
                            + "queued — pass budget=0 for no ceiling, or walk from the cursor "
                            + "with since= to page forward from here"
                    : "budget: this page stops at " + changes.size() + " of " + candidates
                            + " entries to stay under " + budgetTokens + " estimated tokens — "
                            + "the cursor is at the last entry that fit, so pass since=<cursor> "
                            + "for the rest");
        }
        if (!failures.rows().isEmpty()) {
            // SH204 replaced the first of the two facts this used to state. It said incidents carry
            // no site and `site=` does not narrow them; both halves are now false, so the note that
            // was honest about a hole would be a false statement about a closed one.
            notes.add(failures.rows().size() + " incident(s) are included: asynchronous failures "
                    + "in this range, and a recurring one reappears with a higher count because "
                    + "its position is its last occurrence");
        }
        // The replacement fact, and it is only worth its tokens on a scoped read: an unrestricted
        // caller is shown everything and has nothing to be told. `api-error` and `transform` belong
        // to no one site by nature, so this is where they went rather than a gap to be closed
        // later — and naming diagnostics is the difference between a caller looking elsewhere and
        // one concluding its own 5xx was never recorded.
        if (!scope.unrestricted() && !failures.rows().isEmpty()) {
            notes.add("incidents are scoped too: an api-error or transform failure belongs to no "
                    + "one site and so is not in this array — GET /api/v2/agent/diagnostics "
                    + "reports those, instance-wide");
        }
        if (window) {
            notes.add("window: the most recent " + cap + " entries at most, not a delta — pass "
                    + "since=<cursor> to get everything after this position");
        }
        // SH232 — without this, a cursor of 1970-01-01 reads as a bug rather than as the start
        // of an empty trail. Said only on an empty window, and constant, so the poll it explains
        // is also the poll it lets revalidate.
        if (window && changes.isEmpty()) {
            notes.add("nothing has been recorded in scope yet, so this cursor is the start of "
                    + "the trail rather than the current instant — which is what makes this poll "
                    + "byte-identical, and free with If-None-Match, until something is written");
        }
        // SH376 — said only once a foreign write has been seen, so a single-instance feed pays no
        // prose for a case it does not have (P3), and said whatever `lag-millis` is: a margin
        // smaller than the excess observed is not covering it.
        if (watermark.foreignWriteSeen() && config.getLagMillis() <= 0) {
            notes.add("this feed carries rows written by another application node, and this node's "
                    + "ordering guard covers only its own writes — set "
                    + "shio.agent.changes.lag-millis above that node's slowest write transaction, "
                    + "or an entry it commits late can land behind a cursor");
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

}
