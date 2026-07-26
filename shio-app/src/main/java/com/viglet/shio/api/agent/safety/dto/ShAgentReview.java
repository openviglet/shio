/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.safety.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * The review queue (SH109) — "the agent proposed N changes", as data.
 *
 * <h2>Why a session is the unit</h2>
 *
 * <p>Because it is the unit the human thinks in. A run of an agent is one intention — "add
 * a pricing section" — expressed as thirty audit rows, and asking a person to approve
 * thirty rows one at a time is asking them to reconstruct the intention from its
 * fragments. The session id (SH106) is what makes the grouping a fact rather than a guess
 * at which changes happened close together in time.</p>
 *
 * <p>Rows are still individually approvable, because a reviewer who accepts twenty-nine of
 * thirty changes has said something a session-level flag cannot record.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@JsonInclude(Include.NON_NULL)
public record ShAgentReview(int sessions, int pending, List<Session> results) {

    /**
     * One agent run.
     *
     * @param session   the session id — the value a caller passes back to approve or revert
     * @param actor     who the run acted as (the human whose credential it used)
     * @param startedAt ISO-8601 instant of the run's first recorded change
     * @param endedAt   ISO-8601 instant of its last
     * @param changes   how many changes it made in total
     * @param pending   how many of those a human has not yet accepted
     * @param published how many of them changed what visitors see — surfaced separately
     *                  because it is the number that decides how urgently the queue needs
     *                  looking at
     * @param sites     the site names the run touched
     * @param preview   the first few changes, so a list can be read without opening every
     *                  session; the full set is the detail call
     */
    @JsonInclude(Include.NON_NULL)
    public record Session(String session, String actor, String startedAt, String endedAt,
                          int changes, int pending, int published, List<String> sites,
                          List<Change> preview) {
    }

    /**
     * One change an agent made.
     *
     * @param id          the audit row id — what an approve or a revert names
     * @param action      {@code CREATE} · {@code UPDATE} · {@code PUBLISH} ·
     *                    {@code UNPUBLISH} · {@code DELETE} · {@code RESTORE}
     * @param address     the SH79 address of the affected post, when it still resolves
     * @param what        the human-readable description recorded at the time
     * @param at          ISO-8601 instant
     * @param site        site name, when recorded
     * @param reviewed    whether a human has accepted it
     * @param reviewedBy  who accepted it
     * @param revertible  whether {@code revert} can undo this change on its own, and if not
     *                    the field is {@code false} rather than absent — a reviewer needs to
     *                    know which rows the button will not touch <em>before</em> pressing it
     * @param postId      the affected object id; present only when the caller asked for ids
     */
    @JsonInclude(Include.NON_NULL)
    public record Change(String id, String action, String address, String what, String at,
                         String site, boolean reviewed, String reviewedBy, boolean revertible,
                         String postId) {
    }

    /**
     * What an approve or a revert did.
     *
     * @param session  the session acted on
     * @param affected how many audit rows were touched
     * @param reverted how many content changes were actually undone
     * @param skipped  changes the revert could not undo, with the reason — never silent,
     *                 because "reverted" over a partial undo is the one wrong answer here
     */
    @JsonInclude(Include.NON_NULL)
    public record Outcome(String session, int affected, int reverted, List<String> skipped) {
    }
}
