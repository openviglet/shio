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

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.api.agent.ShAgentRunService;
import com.viglet.shio.api.agent.ShAgentSessionContext;
import com.viglet.shio.persistence.model.history.ShHistory;
import com.viglet.shio.persistence.repository.history.ShHistoryPageableRepository;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Read-only audit / activity API (SH51). Exposes the {@link ShHistory} trail
 * recorded by {@link ShContentAuditService} — who did what, when — that the
 * model has always persisted but never surfaced. Console-authenticated and
 * tenant-scoped (the {@code @TenantId} on {@code ShHistory} filters every read).
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

    private final ShHistoryPageableRepository shHistoryPageableRepository;
    private final ShAgentRunService shAgentRunService;

    public ShHistoryAPI(ShHistoryPageableRepository shHistoryPageableRepository,
                        ShAgentRunService shAgentRunService) {
        this.shHistoryPageableRepository = shHistoryPageableRepository;
        this.shAgentRunService = shAgentRunService;
    }

    /**
     * Recent activity across all content in the current tenant, newest first.
     *
     * @param agent SH999 — {@code agent} for what a run wrote, {@code curator} for what a person
     *              did (a console session is one of theirs), or a session id for one run
     * @param actor SH999 — one principal's rows, which is the other question the Activity page is
     *              asked: an agent and a person share a credential, so filtering by name and
     *              filtering by machine are not the same filter and the page needs both
     */
    @GetMapping
    public List<ShHistoryResponse> recent(@RequestParam(name = "limit", defaultValue = "100") int limit,
                                          @RequestParam(name = "agent", required = false) String agent,
                                          @RequestParam(name = "actor", required = false) String actor) {
        return shape(shHistoryPageableRepository.findAllByOrderByDateDesc(page(limit)), agent, actor);
    }

    /** Activity for a single post (the {@code shObject} id), newest first. */
    @GetMapping("/object/{objectId}")
    public List<ShHistoryResponse> byObject(@PathVariable String objectId,
                                            @RequestParam(name = "limit", defaultValue = "100") int limit,
                                            @RequestParam(name = "agent", required = false) String agent,
                                            @RequestParam(name = "actor", required = false) String actor) {
        return shape(shHistoryPageableRepository
                .findByShObjectOrderByDateDesc(objectId, page(limit)), agent, actor);
    }

    /** Activity for a single site, newest first. */
    @GetMapping("/site/{siteId}")
    public List<ShHistoryResponse> bySite(@PathVariable String siteId,
                                          @RequestParam(name = "limit", defaultValue = "100") int limit,
                                          @RequestParam(name = "agent", required = false) String agent,
                                          @RequestParam(name = "actor", required = false) String actor) {
        return shape(shHistoryPageableRepository
                .findByShSiteOrderByDateDesc(siteId, page(limit)), agent, actor);
    }

    /**
     * The page, filtered and named (SH999).
     *
     * <h2>Why this one filters after the query and the agent feed does not</h2>
     *
     * <p>The agent feed is <b>resumable</b>: it hands back a cursor, so a page short of its limit is
     * read as "that is all there was" and the rows behind the cursor are never asked for again. This
     * page is not — the console asks for the newest N and re-asks, so a filtered page that is short
     * is a short page and nothing is lost. Filtering here keeps three finders from becoming twelve
     * for a screen that reads at most five hundred rows.</p>
     */
    private List<ShHistoryResponse> shape(List<ShHistory> rows, String agent, String actor) {
        List<ShHistory> kept = rows.stream()
                .filter(row -> wroteIt(row, agent))
                .filter(row -> actor == null || actor.isBlank() || actor.equals(row.getOwner()))
                .toList();
        Map<String, String> names = shAgentRunService.names(kept.stream()
                .map(ShHistory::getAgentSessionId)
                .filter(session -> session != null && !ShAgentSessionContext.isConsole(session))
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        return kept.stream().map(row -> ShHistoryResponse.of(row, names)).toList();
    }

    /** The same three-way split the agent feed applies, and for the same reason (SH999). */
    private static boolean wroteIt(ShHistory row, String agent) {
        if (agent == null || agent.isBlank()) {
            return true;
        }
        String session = row.getAgentSessionId();
        boolean machine = session != null && !ShAgentSessionContext.isConsole(session);
        if ("agent".equalsIgnoreCase(agent)) {
            return machine;
        }
        if ("curator".equalsIgnoreCase(agent)) {
            return !machine;
        }
        return agent.equals(session);
    }

    private static Pageable page(int limit) {
        int capped = Math.min(limit > 0 ? limit : DEFAULT_LIMIT, MAX_LIMIT);
        return PageRequest.of(0, capped);
    }

    /**
     * Stable read DTO — never leaks the {@code tenant_id} discriminator.
     *
     * @param changedFields SH989 — the names of the fields the write changed; {@code null} when the
     *                      row recorded none (a create, a delete, an older row), {@code []} when it
     *                      recorded that nothing changed
     */
    public record ShHistoryResponse(String id, String action, String description,
                                    String owner, Instant date, String shObject, String shSite,
                                    List<String> changedFields, String agent, String agentName,
                                    String subjectKind) {

        static ShHistoryResponse of(ShHistory h, Map<String, String> names) {
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
                    h.getSubjectKind());
        }
    }
}
