/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.persistence.model.post.revision;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import org.hibernate.annotations.TenantId;

import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.Getter;
import lombok.Setter;

/**
 * What a post was, at a moment somebody can name (SH986).
 *
 * <h2>Why a table and not a third state</h2>
 *
 * <p>A post is {@code (id, state)} and {@code ShPostState} is exactly {@code DRAFT} and
 * {@code PUBLISHED}, so publishing deletes the live row and writes the draft over it. The bytes a
 * visitor was reading are then gone — not superseded, gone — and {@code ShHistory} records that a
 * publish happened without carrying what it published.</p>
 *
 * <p>A third state would have been cheaper to write and wrong: every query that filters on state,
 * the Turing indexer, the CDA and the quota would each have to learn to exclude it, and the one
 * that forgot would serve an archive row to a visitor. A revision is not a state of the post, it is
 * a record of what the post was, so it is its own table.</p>
 *
 * <h2>Two columns that are deliberately not associations</h2>
 *
 * <p>{@code postId} is a plain string and not a foreign key: the post's key is composite, so a
 * foreign key would have to name a state, and the row it named is the one publishing deletes. A
 * revision has to outlive exactly that deletion.</p>
 *
 * <p>{@code postTypeId} is a plain string for the invariant {@code agents.md} states — never
 * navigate past {@code .id} of an association whose target is an {@code ShObject} subclass — and
 * because a type can be renamed (SH163), while a record of what the post <em>was</em> should name
 * the thing that does not move.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Getter
@Setter
@Entity
@Table(name = "sh_post_revision",
        // SH729 — declared here as well as in the changelog: the unit suite builds H2 from this
        // metadata with Liquibase off, so a constraint that lives only in the changelog is a
        // constraint no test in `mvn test` has ever seen.
        uniqueConstraints = @UniqueConstraint(name = "UK_sh_post_revision_version",
                columnNames = {"post_id", "version_no"}))
public class ShPostRevision implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Why a snapshot was taken. Stored as the name, never the ordinal. */
    public enum Reason {
        /** The row went live. Every state a visitor ever saw has one of these. */
        PUBLISH,
        /** A draft was saved, when {@code shio.versions.capture-on-save} is on. */
        SAVE,
        /** A revision was restored over the draft — the restore is itself a revision. */
        ROLLBACK
    }

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", length = 36, updatable = false, nullable = false)
    private String id;

    @Column(name = "post_id", length = 36, nullable = false)
    private String postId;

    /** Monotonic per post, assigned as {@code max + 1} inside the capturing transaction. */
    @Column(name = "version_no", nullable = false)
    private int versionNo;

    /** {@code DRAFT} or {@code PUBLISHED} — the state the captured row was in. */
    @Column(name = "state", length = 16, nullable = false)
    private String state;

    @Column(name = "reason", length = 16, nullable = false)
    private String reason;

    @Column(name = "title", length = 510)
    private String title;

    @Column(name = "furl", length = 510)
    private String furl;

    @Column(name = "locale", length = 16)
    private String locale;

    @Column(name = "post_type_id", length = 36)
    private String postTypeId;

    @Lob
    @Column(name = "data", nullable = false)
    private String data;

    @Column(name = "actor", length = 255)
    private String actor;

    /**
     * What a person said about this moment, when one had something to say (SH998).
     *
     * <p>Today that is exactly one thing: the reason a curator gave for publishing past a failing
     * gate. It is not folded into {@link Reason} because that column answers a different question —
     * why a snapshot exists — and a waived publish is still a publish. See {@code v2026.3.36}.</p>
     */
    @Column(name = "note", length = 510)
    private String note;

    /** The agent session that wrote it, when one did (SH106). */
    @Column(name = "agent_session_id", length = 255)
    private String agentSessionId;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @TenantId
    @Column(name = "tenant_id", updatable = false)
    private String tenantId;
}
