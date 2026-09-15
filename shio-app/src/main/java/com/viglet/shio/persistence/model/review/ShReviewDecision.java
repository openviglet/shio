/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.persistence.model.review;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import org.hibernate.annotations.TenantId;

import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.Getter;
import lombok.Setter;

/**
 * What a curator decided about one change in one run, and why (SH1001).
 *
 * <h2>Why HOLD is the reason this table exists</h2>
 *
 * <p>{@code APPROVE} and {@code REJECT} already leave a trace — the page goes live, or it goes back
 * — so a design could have inferred them. {@code HOLD} leaves none: "I have read this and I am not
 * ready" is a decision with no side effect, and it is exactly the state a queue has to be able to
 * hold, or a curator's only way to park one page is to leave the whole run undecided.</p>
 *
 * <p>The same absence covers the <em>reason</em>. A rejection's why exists nowhere today, which is
 * the half of this a curator actually feels: the agent that wrote the page cannot be told what was
 * wrong with it, and the next person to open the run cannot see that anybody looked.</p>
 *
 * <h2>The current answer, not a log of answers</h2>
 *
 * <p>{@code (session, postId)} is unique, so a curator who changes their mind replaces the row. The
 * history of what <em>happened</em> is the SH51 audit trail's job; this is the standing decision,
 * and a queue that had to fold a list of them per change to find out where it stood would be the
 * same read done twice.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Getter
@Setter
@Entity
@Table(name = "sh_review_decision",
        // SH729 — declared here as well as in the changelog: the unit suite builds H2 from this
        // metadata with Liquibase off, so a constraint that lives only in the changelog is a
        // constraint no test in `mvn test` has ever seen.
        uniqueConstraints = @UniqueConstraint(name = "UK_sh_review_decision_session_post",
                columnNames = {"session", "post_id"}))
public class ShReviewDecision implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** What a curator can say about one change. */
    public enum Kind {
        /** Put it live. Needs the PUBLISH permission (SH995), like every other publish. */
        APPROVE,
        /** Undo it, with a reason the next reader — and the agent — can act on. */
        REJECT,
        /** Read and parked: the draft stays, and the queue stops counting it as waiting. */
        HOLD
    }

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", length = 36, updatable = false, nullable = false)
    private String id;

    /** The run, as {@code ShHistory.agent_session_id} records it. */
    @Column(name = "session", length = 255, nullable = false)
    private String session;

    /**
     * The post decided about.
     *
     * <p>A plain string for the invariant {@code agents.md} states: a post's key is composite, so a
     * foreign key would have to name a state — and the row it named is the one publishing
     * deletes.</p>
     */
    @Column(name = "post_id", length = 36, nullable = false)
    private String postId;

    @Column(name = "decision", length = 16, nullable = false)
    private String decision;

    /** Why. Required on a {@code REJECT}, because a rejection nobody can read teaches nothing. */
    @Column(name = "note", length = 1000)
    private String note;

    @Column(name = "actor", length = 255)
    private String actor;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    @TenantId
    @Column(name = "tenant_id", updatable = false)
    private String tenantId;
}
