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

import org.springframework.stereotype.Service;

import com.viglet.shio.api.agent.safety.ShAgentSessionContext;
import com.viglet.shio.persistence.model.history.ShHistory;
import com.viglet.shio.persistence.repository.history.ShHistoryRepository;

/**
 * Records content-lifecycle audit entries (SH51). The {@link ShHistory} entity
 * has always been part of the model but was never written; this service closes
 * that gap by persisting one row per unified-post transition.
 *
 * <p>Recording is intentionally <b>inline</b> with the caller's write
 * transaction (not an {@code AFTER_COMMIT} listener like the SH12 webhook
 * dispatcher): the audit row must commit/roll back atomically with the content
 * change it describes, and Hibernate's {@code @TenantId} discriminator needs the
 * request-thread {@code ShTenantContext} that an async listener could lose. A
 * record is therefore never written for a transaction that rolls back.
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
public class ShContentAuditService {

    private final ShHistoryRepository shHistoryRepository;

    public ShContentAuditService(ShHistoryRepository shHistoryRepository) {
        this.shHistoryRepository = shHistoryRepository;
    }

    /**
     * Persist a single audit entry. Participates in the caller's transaction.
     *
     * @param action transition kind — see {@link ShContentAction}
     * @param postId the affected post id (stored as {@code shObject})
     * @param siteId the post's site id (may be {@code null})
     * @param title  the post title at the time, for a human-readable description
     * @param actor  who performed it (may be {@code null} → "system")
     */
    public void record(ShContentAction action, String postId, String siteId, String title, String actor) {
        ShHistory history = new ShHistory();
        history.setAction(action.name());
        history.setShObject(postId);
        history.setShSite(siteId);
        history.setOwner(actor != null && !actor.isBlank() ? actor : "system");
        history.setDate(Instant.now());
        history.setDescription(describe(action, title));
        // SH106: which agent run made this, in a column rather than as a substring of the
        // owner string. Read from the request thread rather than passed in, because every
        // writer would otherwise have to thread it through — and the one that forgot would
        // produce a change invisible to the review queue, which is the failure mode that
        // makes an unreviewable change look like a human's.
        ShAgentSessionContext.current().ifPresent(history::setAgentSession);
        shHistoryRepository.save(history);
    }

    private static String describe(ShContentAction action, String title) {
        String label = (title != null && !title.isBlank()) ? title : "(untitled)";
        return action.verb() + " \"" + label + "\"";
    }
}
