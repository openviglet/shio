/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.safety;

import java.security.Principal;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.api.agent.safety.dto.ShAgentReview;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The review queue (SH109) — "the agent proposed N changes": approve, or put it back.
 *
 * <h2>Why this is at {@code /api/v2/review} and not under {@code /api/v2/agent}</h2>
 *
 * <p>Because it is the <em>human's</em> surface, and the separation is structural rather
 * than a check that could be forgotten. Token authentication is confined by path
 * ({@code ShApiTokenFilter}): a {@code Key} header reaches {@code /api/v2/cda},
 * {@code /graphql}, {@code /api/v2/agent} and {@code /mcp}, and nothing else. Living
 * outside those prefixes is what makes "an agent cannot approve its own changes" a property
 * of the routing table instead of an {@code if} somebody has to remember to write — and
 * §XII's invariant is exactly the kind of rule that should not depend on being
 * remembered.</p>
 *
 * <p>Console-authenticated and CSRF-protected like every other console API, for the same
 * reason: the caller is a browser.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@RestController
@RequestMapping("/api/v2/review")
@Tag(name = "Review", description = "Agent review queue — what an agent changed, approve or revert")
public class ShAgentReviewAPI {

    private final ShAgentReviewService shAgentReviewService;

    public ShAgentReviewAPI(ShAgentReviewService shAgentReviewService) {
        this.shAgentReviewService = shAgentReviewService;
    }

    /**
     * The queue: agent sessions, newest first, with a preview of each.
     *
     * @param pendingOnly hide sessions already worked through (default {@code true} — a
     *                    queue that shows finished work is a queue people stop reading)
     * @param limit       sessions to return
     * @param ids         include object ids
     */
    @GetMapping
    @Operation(summary = "Agent sessions and what they changed, newest first")
    public ShAgentReview queue(
            @RequestParam(name = "pendingOnly", required = false, defaultValue = "true")
            boolean pendingOnly,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "ids", required = false, defaultValue = "false") boolean ids) {
        return shAgentReviewService.queue(pendingOnly, limit, ids);
    }

    /** Everything one session did, oldest first — the order it happened in. */
    @GetMapping("/{session}")
    @Operation(summary = "One agent session's changes, in the order they happened")
    public List<ShAgentReview.Change> changes(
            @PathVariable(name = "session") String session,
            @RequestParam(name = "ids", required = false, defaultValue = "false") boolean ids) {
        return shAgentReviewService.changes(session, ids);
    }

    /** Accept a session's changes. Writes no content — it records that a person looked. */
    @PostMapping("/{session}/approve")
    @Operation(summary = "Accept an agent session's changes")
    public ShAgentReview.Outcome approve(@PathVariable(name = "session") String session,
                                         Principal principal) {
        return shAgentReviewService.approve(session, name(principal));
    }

    /**
     * Undo a session's changes, newest first.
     *
     * <p>The response names every change it could <em>not</em> undo and why — an edit has
     * no automatic inverse, and reporting a partial revert as a revert is the one wrong
     * answer available here.</p>
     */
    @PostMapping("/{session}/revert")
    @Operation(summary = "Undo an agent session's changes; reports anything it could not undo")
    public ShAgentReview.Outcome revert(@PathVariable(name = "session") String session,
                                        Principal principal) {
        return shAgentReviewService.revert(session, name(principal));
    }

    private static String name(Principal principal) {
        return principal != null ? principal.getName() : null;
    }
}
