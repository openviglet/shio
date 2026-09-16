/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.history;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.api.agent.ShAgentChangesService;
import com.viglet.shio.api.agent.ShAgentRunService;
import com.viglet.shio.api.agent.ShAgentSessionContext;
import com.viglet.shio.persistence.model.history.ShHistory;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Read-only audit / activity API (SH51). Exposes the {@link ShHistory} trail
 * recorded by {@link ShContentAuditService} — who did what, when — that the
 * model has always persisted but never surfaced. Console-authenticated and
 * tenant-scoped (the {@code @TenantId} on {@code ShHistory} filters every read).
 *
 * <h2>One reader over the audit table (SH1005)</h2>
 *
 * <p>This used to be a <b>second</b> reader of the same rows: page zero, five hundred at most, no
 * cursor, no filter, over its own finders — while {@code /agent/changes} had a keyset cursor, a
 * site scope, a commit-order ceiling and a measured budget over the same table. That is P1 working
 * and P10 not applied, and it is the shape a console-only surface takes when nobody names it: the
 * weaker of the two readers is the one the person uses.</p>
 *
 * <p>Both surfaces now go through {@link ShAgentChangesService}. The agent's feed walks it forward
 * from a cursor and pays a token budget; this walks it backwards from the newest row, because a
 * person scrolls rather than resumes — but the query set, the scope and the cursor grammar are one,
 * so the two cannot disagree about what happened or about where a position is.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@RestController
@RequestMapping("/api/v2/history")
@Tag(name = "History", description = "Content audit / activity trail (read-only)")
public class ShHistoryAPI {

    /** Hard cap so a malicious/large {@code limit} can never page the whole table. */
    private static final int MAX_LIMIT = 500;
    private static final int DEFAULT_LIMIT = 100;

    private final ShAgentChangesService shAgentChangesService;
    private final ShAgentRunService shAgentRunService;
    /** SH1008 — the derived answer to "who else is on this page", over the same rows. */
    private final ShWorkingService shWorkingService;

    public ShHistoryAPI(ShAgentChangesService shAgentChangesService,
                        ShAgentRunService shAgentRunService,
                        ShWorkingService shWorkingService) {
        this.shAgentChangesService = shAgentChangesService;
        this.shAgentRunService = shAgentRunService;
        this.shWorkingService = shWorkingService;
    }

    /**
     * Recent activity across all content in the current tenant, newest first.
     *
     * @param before SH1005 — a {@code cursor} from a previous page: the next page back. Before it,
     *               every read of this trail was page zero, so the five-hundredth row was the last
     *               one this product could show and the audit table is the one table that only
     *               grows
     * @param since  SH1005 — a {@code latest} from a previous page, read forward instead: what has
     *               been written since the page was drawn, which is what a live screen asks
     * @param agent  SH999 — {@code agent} for what a run wrote, {@code curator} for what a person
     *               did (a console session is one of theirs), or a session id for one run
     * @param actor  SH999 — one principal's rows, which is the other question the Activity page is
     *               asked: an agent and a person share a credential, so filtering by name and
     *               filtering by machine are not the same filter and the page needs both
     * @param action SH1005 — one transition kind, e.g. {@code PUBLISH}
     * @param subject SH1005 — {@code post}, {@code folder} or {@code site} (SH196). Recorded on
     *                every row since SH196 and, until SH999, carried by no reader
     * @param site   SH1005 — one site's trail. A parameter rather than the path this used to
     *               carry: it is one narrowing among six, and a path per narrowing is how a
     *               reader becomes a query set
     */
    @GetMapping
    public ShHistoryPage recent(@RequestParam(name = "limit", defaultValue = "100") int limit,
                                @RequestParam(name = "before", required = false) String before,
                                @RequestParam(name = "since", required = false) String since,
                                @RequestParam(name = "agent", required = false) String agent,
                                @RequestParam(name = "actor", required = false) String actor,
                                @RequestParam(name = "action", required = false) String action,
                                @RequestParam(name = "subject", required = false) String subject,
                                @RequestParam(name = "site", required = false) String site) {
        return page(site, new ShHistoryFilter(agent, null, action, actor, subject, null),
                before, since, limit);
    }

    /**
     * Activity for a single post or folder (the {@code shObject} id), newest first.
     *
     * <p>The path form stays while {@code site} becomes a parameter, because this one is not a
     * narrowing of the trail — it is the post editor's own history panel asking about the object
     * the curator is looking at, and that object is what the request is <em>about</em>.</p>
     */
    @GetMapping("/object/{objectId}")
    public ShHistoryPage byObject(@PathVariable String objectId,
                                  @RequestParam(name = "limit", defaultValue = "100") int limit,
                                  @RequestParam(name = "before", required = false) String before,
                                  @RequestParam(name = "since", required = false) String since,
                                  @RequestParam(name = "agent", required = false) String agent,
                                  @RequestParam(name = "actor", required = false) String actor,
                                  @RequestParam(name = "action", required = false) String action) {
        return page(null, new ShHistoryFilter(agent, null, action, actor, null, objectId),
                before, since, limit);
    }

    /**
     * Who else is on this page right now, or nothing (SH1008).
     *
     * <p>Here rather than on the post read, because it is a fact about the audit trail and not
     * about the post: it is derived from who wrote recently, it expires on its own, and it changes
     * between two reads of a page that has not changed at all. Putting it on the post would make
     * every editor load carry it and would make a page's own payload vary for a reason that has
     * nothing to do with the page.</p>
     *
     * <p><b>It refuses nothing.</b> A mark showing does not stop a save, delay one, or change what
     * SH985's {@code expect} guard does. The console draws a line; the write path never reads
     * this.</p>
     */
    @GetMapping("/working/{objectId}")
    public ShWorkingService.Working working(@PathVariable String objectId) {
        return shWorkingService.on(objectId).orElse(null);
    }

    /** The shared read, named and shaped (SH999, SH1005). */
    private ShHistoryPage page(String site, ShHistoryFilter filter, String before, String since,
                               int limit) {
        int capped = Math.min(limit > 0 ? limit : DEFAULT_LIMIT, MAX_LIMIT);
        ShAgentChangesService.Trail trail =
                shAgentChangesService.trail(site, filter, before, since, capped);
        Map<String, String> names = shAgentRunService.names(trail.rows().stream()
                .map(ShHistory::getAgentSessionId)
                .filter(session -> session != null && !ShAgentSessionContext.isConsole(session))
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        List<ShHistoryResponse> rows = trail.rows().stream()
                .map(row -> ShHistoryResponse.of(row, names, trail.sites()))
                .toList();
        return new ShHistoryPage(rows, trail.latest(), trail.cursor(), trail.more());
    }

    /**
     * One page of the trail (SH1005).
     *
     * @param latest the newest row's position — pass it back as {@code since=} to poll for arrivals
     * @param cursor the oldest row's position — pass it back as {@code before=} for the page behind
     * @param more   there are rows beyond this page, as a fact rather than an inference from a full
     *               page: a reader at the end of the trail would otherwise be sent to an empty one
     */
    public record ShHistoryPage(List<ShHistoryResponse> rows, String latest, String cursor,
                                boolean more) {
    }

    /**
     * Stable read DTO — never leaks the {@code tenant_id} discriminator.
     *
     * @param changedFields SH989 — the names of the fields the write changed; {@code null} when the
     *                      row recorded none (a create, a delete, an older row), {@code []} when it
     *                      recorded that nothing changed
     * @param siteName SH1005 — the site as a name, resolved from the page's own scope rather than
     *                 per row. A site id told the page nothing a curator could read, and it is the
     *                 one column the Activity page needs to say which site a change was in
     */
    public record ShHistoryResponse(String id, String action, String description,
                                    String owner, Instant date, String shObject, String shSite,
                                    List<String> changedFields, String agent, String agentName,
                                    String subjectKind, String siteName) {

        static ShHistoryResponse of(ShHistory h, Map<String, String> names,
                                    Map<String, String> sites) {
            String session = h.getAgentSessionId();
            // SH999 — a console session is a curator publishing their own work (SH996). It is a
            // person, and reporting it as an agent would put a curator's own press under a chip
            // reading "agent" on the page they opened to find out who did this.
            String run = session == null || ShAgentSessionContext.isConsole(session) ? null : session;
            return new ShHistoryResponse(h.getId(), h.getAction(), h.getDescription(),
                    h.getOwner(), h.getDate(), h.getShObject(), h.getShSite(),
                    ShChangedFields.decode(h.getChangedFields()),
                    run, run == null ? null : names.get(run),
                    // SH196 has recorded this on every row since it shipped and no reader carried
                    // it, so the Activity page called a folder rename a post.
                    h.getSubjectKind(),
                    h.getShSite() == null ? null : sites.get(h.getShSite()));
        }
    }
}
