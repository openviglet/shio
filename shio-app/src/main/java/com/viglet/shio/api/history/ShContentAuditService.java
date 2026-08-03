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

import com.viglet.shio.api.agent.ShAgentSessionContext;
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
     * Persist a single audit entry for a <b>post</b>. Participates in the caller's transaction.
     *
     * @param action transition kind — see {@link ShContentAction}
     * @param postId the affected post id (stored as {@code shObject})
     * @param siteId the post's site id (may be {@code null})
     * @param title  the post title at the time, for a human-readable description
     * @param actor  who performed it (may be {@code null} → "system")
     */
    public void record(ShContentAction action, String postId, String siteId, String title, String actor) {
        record(action, ShContentSubject.POST, postId, siteId, title, actor, null);
    }

    /**
     * Persist a single audit entry for any auditable subject (SH196).
     *
     * <p>{@code detail} exists because the action vocabulary is deliberately not extended per
     * subject: a folder rename and a folder move are both {@code UPDATE}, and what distinguishes
     * them is a fact about the change rather than a new kind of change. It is appended to the
     * description in parentheses, so a reviewer reading the row is told <em>what</em> the update
     * was without a consumer having to parse an action it has never seen.</p>
     *
     * @param action   transition kind — see {@link ShContentAction}
     * @param subject  what {@code objectId} is — see {@link ShContentSubject}
     * @param objectId the affected object id (stored as {@code shObject})
     * @param siteId   its site id (may be {@code null})
     * @param title    its title/name at the time, for a human-readable description
     * @param actor    who performed it (may be {@code null} → "system")
     * @param detail   what the transition was, when the action alone does not say; may be
     *                 {@code null}, which keeps the description byte-identical to a post's
     */
    public void record(ShContentAction action, ShContentSubject subject, String objectId,
                       String siteId, String title, String actor, String detail) {
        ShHistory history = new ShHistory();
        history.setAction(action.name());
        // Written for every subject including POST, so a row is self-describing going forward
        // and only a pre-SH196 row needs the null-means-post reading.
        history.setSubjectKind((subject == null ? ShContentSubject.POST : subject).name());
        history.setShObject(objectId);
        history.setShSite(siteId);
        history.setOwner(actor != null && !actor.isBlank() ? actor : "system");
        history.setDate(Instant.now());
        history.setDescription(describe(action, title, detail));
        // SH106 — which agent run this was, read from the request thread. Safe precisely
        // because recording is inline with the write: an AFTER_COMMIT listener would run
        // on another thread and find nothing, which is the same reason the tenant
        // discriminator needs this to stay inline.
        history.setAgentSessionId(ShAgentSessionContext.current().orElse(null));
        shHistoryRepository.save(history);
    }

    private static String describe(ShContentAction action, String title, String detail) {
        String label = (title != null && !title.isBlank()) ? title : "(untitled)";
        String described = action.verb() + " \"" + label + "\"";
        return (detail == null || detail.isBlank()) ? described : described + " (" + detail + ")";
    }
}
