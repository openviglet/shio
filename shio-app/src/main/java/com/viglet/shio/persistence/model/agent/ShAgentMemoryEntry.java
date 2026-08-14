/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.persistence.model.agent;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import org.hibernate.annotations.TenantId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One remembered decision or convention (SH114) — the instance's memory.
 *
 * <p>This is the compounding saving the whole token-economy block exists for:
 * what session <i>N</i> worked out ("this site's blog uses {@code Article}, not
 * {@code Post}"; "copy is authored in files, never through the API") session
 * <i>N+1</i> is simply told, in the context pack it already reads first. Without
 * it every session re-derives the same conclusions and pays for them twice.</p>
 *
 * <p><b>Why a table and not a post-type.</b> Memory is read on the hot path — the
 * context pack, every session, before any work — and it is not content: it is not
 * delivered, not versioned, not published, and it must survive a
 * {@code prune}. Modelling it as content would put it inside the very desired-state
 * documents it exists to inform, so an agent that forgot to declare its own memory
 * would delete it.</p>
 *
 * <h2>Attribution is a field, not a workflow</h2>
 *
 * <p>Every note records who wrote it, and the context pack prints that: a future
 * session can tell a human's instruction from another agent's inference and weigh
 * them differently. That is deliberately cheaper than an approval queue — a note
 * misleads the next session, it does not change the site — and the review queue
 * (SH109) will gate agent writes across the board rather than for this table
 * alone.</p>
 *
 * <h2>Scope</h2>
 *
 * <p>{@link #scope} is a site name, or the empty string for the whole instance.
 * Empty rather than {@code null} on purpose: the uniqueness of
 * {@code (tenant, scope, key)} is enforced by an index, and SQL indexes do not
 * treat {@code NULL}s as equal — a nullable scope would silently admit duplicate
 * instance-wide notes under the same key.</p>
 *
 * <p>That index is declared <b>twice</b> — here and in {@code v2026.3.9.yaml} — and it has to be
 * (SH729). The unit suite runs with Liquibase off and builds H2 from this metadata, so a changelog-only
 * index is simply absent while the suite runs: the upsert would be idempotent by convention there and
 * by constraint everywhere else, and no test could tell. {@code ShLiquibaseEntityConstraintLintTest}
 * is what keeps the pair from drifting apart again.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Getter
@Setter
@Entity
@Table(name = "sh_agent_memory",
        indexes = @Index(name = "ux_sh_agent_memory_key",
                columnList = "tenant_id, scope, memory_key", unique = true))
public class ShAgentMemoryEntry implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The instance-wide scope: every site, not one of them. */
    public static final String INSTANCE_SCOPE = "";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @TenantId
    @Column(name = "tenant_id", updatable = false)
    private String tenantId;

    /** Site name this note applies to, or {@link #INSTANCE_SCOPE} for all of them. */
    @Column(name = "scope", length = 255, nullable = false)
    private String scope = INSTANCE_SCOPE;

    /**
     * Stable slug identifying the note. It is the upsert key, which is what makes
     * "remember this" idempotent (P5): re-stating a convention rewrites one row
     * instead of accumulating near-duplicates the next session has to reconcile.
     */
    @Column(name = "memory_key", length = 120, nullable = false)
    private String memoryKey;

    /** The note itself, in the imperative — it is read as an instruction. */
    @Column(name = "note", length = 2000, nullable = false)
    private String note;

    /** Principal that wrote it. Printed in the context pack; see the class note. */
    @Column(name = "author", length = 255)
    private String author;

    @Column(name = "created_date")
    private Instant createdDate;

    @Column(name = "modified_date")
    private Instant modifiedDate;
}
