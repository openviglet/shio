/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.persistence.model.note;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import org.hibernate.annotations.TenantId;

import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/**
 * A curator's correction, anchored where it is about (SH1003).
 *
 * <h2>The channel that did not exist</h2>
 *
 * <p>The review queue reviews <em>runs</em>, not documents. A curator who wanted one paragraph fixed
 * had no place to say so and an agent had nothing to read, so the correction lived in a conversation
 * somewhere else and the next session started without it. Instance memory (SH114) was the nearest
 * thing and is scoped to a site — it answers "how we write here", not "this sentence is wrong".</p>
 *
 * <h2>Three columns of anchor, narrowing</h2>
 *
 * <p>{@link #objectId} is required; {@link #fieldPath} and {@link #revisionNo} are not. "This page
 * is wrong", "this field is wrong" and "this field was wrong at version 7" are one kind of statement
 * at three resolutions, and three tables for them would be three read paths for one question.</p>
 *
 * <h2>Kind rather than three tables</h2>
 *
 * <p>A {@code REQUEST} is a comment somebody is expected to act on and a {@code REPLY} is a comment
 * under another one. The alternative — an assignee column, a request table and a comment table —
 * would be three shapes that all mean "somebody said something about this", and the day they
 * disagreed would be the day a request stopped appearing in a thread.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Getter
@Setter
@Entity
@Table(name = "sh_note")
public class ShNote implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** What somebody said, and what is expected of it. */
    public enum Kind {
        /** A remark. Nobody is waiting on it. */
        COMMENT,
        /** Something to act on — the one a work list is made of. */
        REQUEST,
        /** A message under another one. */
        REPLY,
        /**
         * SH1061 — this field has been checked against the source it translates.
         *
         * <p>A fourth kind rather than a comment, because a work list must not offer it as
         * something to act on and a reader must not have to parse a body to tell "reviewed" from
         * "please review". It carries the field it is about, like any other note, so the console's
         * translation workspace and an agent reading {@code include=notes} see the same marks.</p>
         */
        REVIEWED
    }

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", length = 36, updatable = false, nullable = false)
    private String id;

    /**
     * The post, folder or site this is about.
     *
     * <p>A plain string and not an association, for the invariant {@code agents.md} states: a post's
     * key is composite, so a foreign key would have to name a state — and the row it named is the
     * one publishing deletes. A note has to outlive exactly that.</p>
     */
    @Column(name = "object_id", length = 36, nullable = false)
    private String objectId;

    /** The field, when the note is about one — {@code data.subtitle}. */
    @Column(name = "field_path", length = 255)
    private String fieldPath;

    /** The revision it is about, when the note is about a version rather than the page now. */
    @Column(name = "revision_no")
    private Integer revisionNo;

    /** The note this one replies to; {@code null} on a root. */
    @Column(name = "parent_id", length = 36)
    private String parentId;

    @Column(name = "kind", length = 16, nullable = false)
    private String kind;

    /** A paragraph, not a document: a thread on a page must not become the page. */
    @Column(name = "body", length = 4000, nullable = false)
    private String body;

    @Column(name = "author", length = 255)
    private String author;

    /** The run that wrote it, when an agent did (SH106). */
    @Column(name = "agent_session_id", length = 255)
    private String agentSessionId;

    /** Whether it is still asking for something. */
    @Column(name = "resolved", nullable = false)
    private boolean resolved;

    @Column(name = "resolved_by", length = 255)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @TenantId
    @Column(name = "tenant_id", updatable = false)
    private String tenantId;
}
