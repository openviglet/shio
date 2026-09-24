/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.review;

import java.security.Principal;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.api.review.dto.ShReviewDetail;
import com.viglet.shio.api.review.dto.ShReviewOutcome;
import com.viglet.shio.api.review.dto.ShReviewPending;
import com.viglet.shio.api.review.dto.ShReviewSession;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The human's side of the agent-native loop (SH109): "the agent proposed N changes".
 *
 * <p>Console-authenticated, and deliberately <b>not</b> on {@code /api/v2/agent/**}: this is
 * the surface that reviews an agent, so an agent's own credential should not be the thing that
 * approves its work. It is still an API and not a console-only feature (law P1) — a script or a
 * CI job can list the runs and act on them exactly as the console does.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@RestController
@RequestMapping("/api/v2/review")
@Tag(name = "Review", description = "Review, approve and revert what an agent run changed (SH109)")
public class ShReviewAPI {

    private final ShReviewService shReviewService;

    public ShReviewAPI(ShReviewService shReviewService) {
        this.shReviewService = shReviewService;
    }

    /** The agent runs, newest first. */
    @GetMapping("/sessions")
    @Operation(summary = "Agent runs that changed content, newest first")
    public List<ShReviewSession> sessions(
            @RequestParam(name = "limit", required = false, defaultValue = "50") int limit) {
        return shReviewService.sessions(limit);
    }

    /**
     * How much is waiting for a human — the console's navigation badge (SH207).
     *
     * <p>Its own read rather than a field on {@code /sessions}, because a badge is asked for on
     * every console render and that listing scans two thousand audit rows to answer.</p>
     */
    @GetMapping("/pending")
    @Operation(summary = "How many agent runs are still awaiting a decision")
    public ShReviewPending pending() {
        return shReviewService.pending();
    }

    /** One run's changes, oldest first. */
    @GetMapping("/sessions/{session}")
    @Operation(summary = "What one agent run changed")
    public ShReviewDetail detail(@PathVariable String session) {
        return shReviewService.detail(session);
    }

    /**
     * Decide this run's changes one at a time (SH1001).
     *
     * <h2>Why a list and not a call per change</h2>
     *
     * <p>A curator triaging eleven pages makes eleven decisions and means them together: sent one at
     * a time, a refusal halfway leaves the run in a state nobody chose, and the page has to work out
     * which of its eleven requests landed. One call, one transaction, and the per-change outcomes
     * come back in the {@code skipped} shape this surface already answers with.</p>
     *
     * <p>A {@code REJECT} with no note is refused — the record is the whole of it, and a rule the
     * console enforced while the API did not would be a preference of one surface.</p>
     */
    @PostMapping("/sessions/{session}/decisions")
    @Operation(summary = "Approve, reject or hold this run's changes, one at a time")
    public ShReviewOutcome decide(@PathVariable String session,
                                  @RequestBody List<ShReviewService.Decision> decisions,
                                  // SH1084 — SH998's waiver, as the publish button takes it: an
                                  // APPROVE publishes, so a gate can stop it.
                                  @RequestParam(name = "override", required = false) String override,
                                  Principal principal) {
        return shReviewService.decide(session, decisions, name(principal), override);
    }

    /**
     * Publish every draft this run wrote — the step SH107 keeps the agent from taking.
     *
     * <p>SH1084 — each publish passes the site's gates. A page a gate stops comes back in
     * {@code skipped} with the gate's reason, and {@code override=<reason>} is how it goes anyway.</p>
     */
    @PostMapping("/sessions/{session}/approve")
    @Operation(summary = "Publish the drafts this agent run wrote")
    public ShReviewOutcome approve(@PathVariable String session,
                                   @RequestParam(name = "override", required = false) String override,
                                   Principal principal) {
        return shReviewService.approve(session, name(principal), override);
    }

    /** Undo it, newest change first, using SH14 restore and the SH55 trash. */
    @PostMapping("/sessions/{session}/revert")
    @Operation(summary = "Undo what this agent run changed")
    public ShReviewOutcome revert(@PathVariable String session, Principal principal) {
        return shReviewService.revert(session, name(principal));
    }

    private static String name(Principal principal) {
        return principal == null ? null : principal.getName();
    }
}
