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
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/**
 * Which agent a session id belongs to (SH999).
 *
 * <h2>Why a uuid needed a table</h2>
 *
 * <p>{@code ShHistory.agent_session_id} has carried the run since SH106, and it is a uuid. So every
 * surface that wanted to say "an agent wrote this" could only say "{@code 7f3c9a2e} wrote this",
 * which is the same amount of information as saying nothing to anyone who was not looking at that
 * agent's logs at the time. MCP's {@code initialize} already receives {@code clientInfo} — a name and
 * a version the client chose — and this is where it lands.</p>
 *
 * <h2>The id is the session id</h2>
 *
 * <p>Not a surrogate key. The transport mints the session id and it is already unique; a second key
 * would let two rows claim one run, which is the one thing a lookup by session must not find. It
 * follows that a run row can be absent: the REST agent surface accepts a session id it did not mint
 * (SH106), and a caller that sent one without introducing itself has a session and no run. That is
 * reported as an absent name rather than a guessed one.</p>
 *
 * <p>No association to {@code ShHistory}: this row is written on {@code initialize}, before the run
 * has written anything, and a run that wrote nothing is a fact worth keeping rather than an
 * orphan.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Getter
@Setter
@Entity
@Table(name = "sh_agent_run")
public class ShAgentRun implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The session id, as the transport minted it and as {@code ShHistory} records it. */
    @Id
    @Column(name = "id", length = 255, updatable = false, nullable = false)
    private String id;

    /** What the client called itself — MCP {@code clientInfo.name}, e.g. {@code claude-code}. */
    @Column(name = "client_name", length = 255)
    private String clientName;

    @Column(name = "client_version", length = 100)
    private String clientVersion;

    /** {@code mcp} or {@code rest} — which surface introduced this run. */
    @Column(name = "transport", length = 16)
    private String transport;

    /** The human whose credential the run used, which is a separate fact from the run. */
    @Column(name = "actor", length = 255)
    private String actor;

    /**
     * What this run said it was for, in the words of whoever started it (SH1000).
     *
     * <p>A sentence, not a document — see {@code v2026.3.38} for why the column is short. It is
     * stored here and nowhere else: putting it on the change row would repeat one sentence on every
     * row of a forty-page run, which is the same sentence forty times.</p>
     */
    @Column(name = "intent", length = 1000)
    private String intent;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @TenantId
    @Column(name = "tenant_id", updatable = false)
    private String tenantId;
}
