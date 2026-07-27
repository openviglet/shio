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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One asynchronous failure, folded over its repeats and durable (SH144).
 *
 * <h2>What changed from SH103, and why</h2>
 *
 * <p>SH103 shipped this as an in-memory ring buffer and said so in the payload —
 * {@code since}, {@code dropped}, and a note that an empty report means "nothing since
 * process start", not "nothing ever". That honesty was load-bearing and it was also an
 * admission: "was anything failing while I was away" is a question about a period the
 * window may no longer cover, and a restart answers it with silence.</p>
 *
 * <h2>Why folding is what makes a table affordable</h2>
 *
 * <p>The objection SH103 recorded against a table was a write on the failure path plus a
 * retention policy. Folding answers most of it: a row is keyed by <em>what went wrong</em>
 * ({@code kind}, {@code subject}, {@code message}), not by each occurrence, so one broken
 * webhook retried on every publish for a month is one row with a count — the table grows
 * with the number of distinct problems, which is a number an operator would want to see
 * anyway. The remaining growth (a subject carrying an id, so every occurrence is
 * "distinct") is bounded by a cap, and what the cap drops is reported.</p>
 *
 * <h2>{@code lastSeen} is the feed position, and that is deliberate</h2>
 *
 * <p>Ordering the merged SH88 feed by {@code lastSeen} means a folded row's position moves
 * <em>forward</em> when it recurs. A cursor therefore never steps over it: an incident that
 * is still happening is re-delivered with a higher count, which is the honest answer to "is
 * this still broken". Ordering by {@code firstSeen} would be stable and wrong — a failure
 * that started before your cursor and is still going would be reported once, ever.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Getter
@Setter
@Entity
@Table(name = "sh_agent_incident")
public class ShAgentIncident implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Longest stored {@code subject}/{@code message}; longer values are truncated. */
    public static final int TEXT_LENGTH = 300;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @TenantId
    @Column(name = "tenant_id", updatable = false)
    private String tenantId;

    /** The {@code ShAgentIncidentLog.Kind}, stored by name so a new kind needs no migration. */
    @Column(name = "kind", length = 40, nullable = false)
    private String kind;

    /** What it happened to: a webhook URL, a post id, a request path. */
    @Column(name = "subject", length = TEXT_LENGTH)
    private String subject;

    /** The shortest thing that identifies the cause. */
    @Column(name = "message", length = TEXT_LENGTH, nullable = false)
    private String message;

    /** What to do about it (law P6), when there is anything generic to say. */
    @Column(name = "fix", length = 1000)
    private String fix;

    @Column(name = "first_seen", nullable = false)
    private Instant firstSeen;

    /** Also the feed position — see the class note on why it is not {@code firstSeen}. */
    @Column(name = "last_seen", nullable = false)
    private Instant lastSeen;

    /** How many times this exact failure has happened. */
    @Column(name = "occurrences", nullable = false)
    private int occurrences = 1;
}
