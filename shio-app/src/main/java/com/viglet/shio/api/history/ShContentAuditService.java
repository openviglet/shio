/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.history;

import java.util.List;

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
 * <p>That is also what makes the row's timestamp a <em>stamp</em> order rather than
 * a commit order, which is the whole of SH147: the position comes from
 * {@link ShAuditFeedWatermark#reserve()} and not from {@code Instant.now()}, so it is
 * unique, monotonic, and held back from the SH88 feed until this transaction finishes.
 * Read that class before changing this line.
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
public class ShContentAuditService {

    /**
     * Below this many characters a truncated title identifies nothing, so {@link #describe} cuts
     * the line as a whole instead of reducing it to a verb and an ellipsis.
     */
    private static final int MIN_TITLE_ROOM = 8;

    private final ShHistoryRepository shHistoryRepository;
    private final ShAuditFeedWatermark watermark;

    public ShContentAuditService(ShHistoryRepository shHistoryRepository,
                                 ShAuditFeedWatermark watermark) {
        this.shHistoryRepository = shHistoryRepository;
        this.watermark = watermark;
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
        record(action, ShContentSubject.POST, postId, siteId, title, actor, null, null);
    }

    /**
     * Persist a post's audit entry naming the fields the write changed (SH989).
     *
     * <p>Only a caller holding the document before and after can say, which is why this is a separate
     * overload and not a lookup here: the write service has both objects in its transaction, and a
     * second read would ask the database for what the caller just replaced.</p>
     *
     * @param fields the changed field names from {@link ShChangedFields#between}; {@code null} records
     *               nothing, and an empty list records that nothing changed
     */
    public void record(ShContentAction action, String postId, String siteId, String title, String actor,
                       List<String> fields) {
        record(action, ShContentSubject.POST, postId, siteId, title, actor, null, fields);
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
        record(action, subject, objectId, siteId, title, actor, detail, null);
    }

    private void record(ShContentAction action, ShContentSubject subject, String objectId,
                        String siteId, String title, String actor, String detail, List<String> fields) {
        ShHistory history = new ShHistory();
        // SH989 — bounded by the encoder, for SH544's reason: the row describes the write and must
        // never be what refuses it.
        history.setChangedFields(ShChangedFields.encode(fields));
        history.setAction(action.name());
        // Written for every subject including POST, so a row is self-describing going forward
        // and only a pre-SH196 row needs the null-means-post reading.
        history.setSubjectKind((subject == null ? ShContentSubject.POST : subject).name());
        history.setShObject(objectId);
        history.setShSite(siteId);
        history.setOwner(actor != null && !actor.isBlank() ? actor : "system");
        // SH147 — a reserved position, not the wall clock: unique, monotonic, and the feed will
        // not report past it until this transaction commits or rolls back.
        history.setDate(watermark.reserve());
        history.setDescription(describe(action, title, detail));
        // SH106 — which agent run this was, read from the request thread. Safe precisely
        // because recording is inline with the write: an AFTER_COMMIT listener would run
        // on another thread and find nothing, which is the same reason the tenant
        // discriminator needs this to stay inline.
        history.setAgentSessionId(ShAgentSessionContext.current().orElse(null));
        shHistoryRepository.save(history);
    }

    /**
     * Composes the description, and <b>bounds it</b> (SH544).
     *
     * <p>Package-private so {@code ShAuditDescriptionLengthTest} can assert the bound
     * directly: the failure it prevents is a rolled-back transaction, which is the one thing a
     * test of the caller cannot observe without a database that enforces the width.</p>
     *
     * <p>The title is the only unbounded part, so it is the part that gives way. Eliding it
     * rather than the composed line keeps the shape — verb, quoted title, parenthesised detail
     * — which is what the SH196 readers parse. Only when {@code detail} alone leaves no usable
     * room for a title does the whole line get cut instead; that ordering is deliberate, since
     * a description reduced to {@code Updated "…} names nothing.</p>
     */
    static String describe(ShContentAction action, String title, String detail) {
        String label = (title != null && !title.isBlank()) ? title : "(untitled)";
        String suffix = (detail == null || detail.isBlank()) ? "" : " (" + detail + ")";
        String prefix = action.verb() + " \"";
        // The closing quote is the +1; `room` is what is left for the title itself.
        int room = ShHistory.DESCRIPTION_MAX_LENGTH - prefix.length() - 1 - suffix.length();
        if (label.length() <= room) {
            return prefix + label + "\"" + suffix;
        }
        if (room >= MIN_TITLE_ROOM) {
            return prefix + elide(label, room) + "\"" + suffix;
        }
        return elide(prefix + label + "\"" + suffix, ShHistory.DESCRIPTION_MAX_LENGTH);
    }

    /**
     * Cuts {@code value} to {@code max} characters, the last of which marks the cut.
     *
     * <p>Never splits a surrogate pair: an emoji in a page title is two {@code char}s, and
     * half of one is not a character any database will store back as it was given.</p>
     */
    private static String elide(String value, int max) {
        int end = max - 1;
        if (end > 0 && Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end) + "…";
    }
}
