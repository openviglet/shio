/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.history;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.agent.ShAgentRunService;
import com.viglet.shio.persistence.model.history.ShHistory;
import com.viglet.shio.persistence.repository.history.ShHistoryRepository;
import com.viglet.shio.utils.ShStableMap;

/**
 * Who else is working on this page, right now (SH1008).
 *
 * <h2>Awareness, not co-editing</h2>
 *
 * <p>SH985 made a conflicting save fail loudly instead of silently, which is necessary and arrives
 * late: the curator finds out after writing. The second editor on a Shio page is usually a machine,
 * and what both parties need is not to type into one document — the non-goals rule out a shared
 * document runtime — but to know, before either starts, that the other is here.</p>
 *
 * <h2>Derived from writes, so it cannot outlive the work</h2>
 *
 * <p>Nothing declares a mark and nothing releases one. A run that wrote to an address inside the
 * window is working on it, and the window passing is the whole of the expiry: there is no heartbeat
 * to miss, no release to forget, and no state to reconcile after a crash. A declared presence
 * outlives the session that declared it exactly when the session died badly, which is the case it
 * was added for.</p>
 *
 * <h2>Never a lock</h2>
 *
 * <p>No write path reads this. A mark showing does not refuse a save, delay one, or change what
 * SH985's guard does — a curator who saves over an agent's work still gets the conflict, on the same
 * terms as before. That is a rule rather than a detail: a mark that could block a write would be a
 * lock derived from a timestamp, held by whoever wrote last and released by nobody.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
@Transactional(readOnly = true)
public class ShWorkingService {

    /**
     * How recently a run must have written to still count as working.
     *
     * <p>A minute, and the trade is visible in both directions: shorter and an agent that thinks
     * between two writes disappears from a page it is still on; longer and the line goes on saying
     * somebody is here after they have gone, which is the failure that teaches a reader to ignore
     * it. A constant rather than a property because a value an operator can tune is a value two
     * instances answer differently, and this one is a claim about what the reader is looking at.</p>
     */
    public static final Duration WINDOW = Duration.ofMinutes(1);

    /** One page's worth of marks: more than this and the caller is reading a feed, not a page. */
    private static final int SCAN = 200;

    private final ShHistoryRepository shHistoryRepository;
    private final ShAgentRunService shAgentRunService;

    public ShWorkingService(ShHistoryRepository shHistoryRepository,
                            ShAgentRunService shAgentRunService) {
        this.shHistoryRepository = shHistoryRepository;
        this.shAgentRunService = shAgentRunService;
    }

    /** The mark on one object, or empty — which is the ordinary answer and costs one query. */
    public Optional<Working> on(String objectId) {
        return objectId == null
                ? Optional.empty()
                : Optional.ofNullable(on(Set.of(objectId)).get(objectId));
    }

    /**
     * The mark on each of these objects, in one query for the whole set.
     *
     * <p>One query rather than one per address, for the reason every lookup on the read path is
     * batched: twenty addresses is one question about twenty ids. The rows come back newest first,
     * so the first one seen for an object is its mark and the rest of that object's rows are the
     * same run still working.</p>
     */
    public Map<String, Working> on(Collection<String> objectIds) {
        if (objectIds == null || objectIds.isEmpty()) {
            return Map.of();
        }
        Instant since = Instant.now().minus(WINDOW);
        Map<String, ShHistory> newest = new LinkedHashMap<>();
        for (ShHistory row : shHistoryRepository.findWorkingMarks(since, objectIds,
                PageRequest.of(0, SCAN))) {
            newest.putIfAbsent(row.getShObject(), row);
        }
        if (newest.isEmpty()) {
            return Map.of();
        }
        Set<String> sessions = new LinkedHashSet<>();
        newest.values().forEach(row -> sessions.add(row.getAgentSessionId()));
        Map<String, String> names = shAgentRunService.names(sessions);
        // SH1000 — what the run said it was for, which is the half of a presence line a person can
        // act on: "claude-code is here" is a fact, "claude-code is here, translating the site to
        // pt-BR" is a reason to wait a minute or to go and look at something else.
        Map<String, String> intents = shAgentRunService.intents(sessions);

        Map<String, Working> marks = new LinkedHashMap<>();
        newest.forEach((objectId, row) -> {
            String session = row.getAgentSessionId();
            marks.put(objectId, new Working(session, names.get(session), intents.get(session),
                    row.getDate(), row.getDate().plus(WINDOW)));
        });
        // Byte stability: `Map.copyOf` iterates under a per-JVM salt, so the same data would
        // serialize to different bytes after a restart — and this map reaches a read's JSON.
        return ShStableMap.copyOf(marks);
    }

    /**
     * A run that is working on a page.
     *
     * @param at    when it last wrote here
     * @param until when this mark stops being true with no further news — carried rather than left
     *              for a caller to compute, so a screen can clear the line on its own timer instead
     *              of waiting for an event that has no reason to arrive
     */
    public record Working(String session, String agentName, String intent, Instant at,
                          Instant until) {
    }
}
