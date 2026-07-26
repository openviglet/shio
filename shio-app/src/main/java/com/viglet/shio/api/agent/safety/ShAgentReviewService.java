/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.safety;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.agent.ShAddressResolver;
import com.viglet.shio.api.agent.safety.dto.ShAgentReview;
import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.api.history.ShContentAction;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.persistence.model.history.ShHistory;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.history.ShHistoryRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;

/**
 * The review queue (SH109) — the human's side of an agent-driven CMS.
 *
 * <h2>Why this is the primary workflow, not a report</h2>
 *
 * <p>Under the reorientation the human stops authoring and starts curating: the agent
 * builds, the person approves, corrects or undoes. That only works if "what did it do?" and
 * "put it back" are one screen rather than an archaeology exercise across the audit trail.
 * Everything needed for it already exists — SH51 records the transitions, SH106 stamps
 * which run made them, SH55 keeps deleted posts restorable — so this composes those rather
 * than storing a proposal queue of its own (law P10). That also means changes an agent made
 * <em>before</em> this shipped are already in the queue, which a separate store could never
 * have been.</p>
 *
 * <h2>Revert is honest about what it cannot do</h2>
 *
 * <p>Four of the six transitions have an exact inverse: a create is undone by a delete to
 * the SH55 trash, a delete by a restore from it, a publish by an unpublish and back. An
 * {@code UPDATE} does not — the audit row records that a post changed, not what it changed
 * <em>from</em> — and neither does a {@code RESTORE}. Those are reported as skipped with the
 * reason and the field-level history to go and look at (SH14), because a revert that
 * silently left half a session in place would be the one wrong answer here: the reviewer
 * would believe the site was back and it would not be.</p>
 *
 * <p>Undone in reverse chronological order, so a session that created a post and then
 * published it is unpublished before it is deleted rather than the other way round.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
@Transactional(readOnly = true)
public class ShAgentReviewService {

    /** Audit rows scanned when building the queue. The trail only grows; the queue does not. */
    private static final int WINDOW = 2000;

    /** Changes shown inline per session in the list, before opening it. */
    private static final int PREVIEW = 5;

    private final ShHistoryRepository shHistoryRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShPostUnifiedWriteService shPostUnifiedWriteService;
    private final ShSiteRepository shSiteRepository;
    private final ShAddressResolver shAddressResolver;

    public ShAgentReviewService(ShHistoryRepository shHistoryRepository,
                                ShPostUnifiedRepository shPostUnifiedRepository,
                                ShPostUnifiedWriteService shPostUnifiedWriteService,
                                ShSiteRepository shSiteRepository,
                                ShAddressResolver shAddressResolver) {
        this.shHistoryRepository = shHistoryRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shPostUnifiedWriteService = shPostUnifiedWriteService;
        this.shSiteRepository = shSiteRepository;
        this.shAddressResolver = shAddressResolver;
    }

    /**
     * The queue: agent sessions, newest first.
     *
     * @param pendingOnly hide sessions a human has already worked through. Default for the
     *                    console, because a queue that shows finished work is a queue
     *                    people stop reading
     * @param limit       sessions to return
     * @param ids         include object ids (SH121 default: off)
     */
    public ShAgentReview queue(boolean pendingOnly, Integer limit, boolean ids) {
        List<ShHistory> entries = shHistoryRepository.findAgentActivity(PageRequest.of(0, WINDOW));
        Map<String, String> siteNames = siteNames();

        // Insertion-ordered by first appearance, and the window is newest-first, so the
        // sessions come out newest-first without a second sort.
        Map<String, List<ShHistory>> bySession = new LinkedHashMap<>();
        for (ShHistory entry : entries) {
            bySession.computeIfAbsent(entry.getAgentSession(), key -> new ArrayList<>()).add(entry);
        }

        int cap = limit == null || limit <= 0 ? 50 : Math.min(limit, 500);
        List<ShAgentReview.Session> sessions = new ArrayList<>();
        int pendingTotal = 0;
        for (Map.Entry<String, List<ShHistory>> group : bySession.entrySet()) {
            ShAgentReview.Session session = summarize(group.getKey(), group.getValue(), siteNames, ids);
            pendingTotal += session.pending();
            if (pendingOnly && session.pending() == 0) {
                continue;
            }
            if (sessions.size() < cap) {
                sessions.add(session);
            }
        }
        return new ShAgentReview(sessions.size(), pendingTotal, List.copyOf(sessions));
    }

    /** Everything one session did, oldest first — the order it happened in. */
    public List<ShAgentReview.Change> changes(String session, boolean ids) {
        List<ShHistory> entries = require(session);
        Map<String, String> siteNames = siteNames();
        return entries.stream().map(entry -> shape(entry, siteNames, ids)).toList();
    }

    /**
     * Accept a session's changes.
     *
     * <p>Approval writes nothing to the content — the changes are already live or already
     * drafted. It records that a person looked, which is what turns the queue from a log
     * into a workflow: an approved session leaves it, and what remains is what still needs
     * a human.</p>
     */
    @Transactional
    public ShAgentReview.Outcome approve(String session, String reviewer) {
        List<ShHistory> entries = require(session);
        Instant now = Instant.now();
        int affected = 0;
        for (ShHistory entry : entries) {
            if (entry.isReviewed()) {
                continue;
            }
            entry.setReviewedDate(now);
            entry.setReviewedBy(reviewer == null || reviewer.isBlank() ? "console" : reviewer);
            shHistoryRepository.save(entry);
            affected++;
        }
        return new ShAgentReview.Outcome(session, affected, 0, List.of());
    }

    /**
     * Undo what a session did, newest change first.
     *
     * <p>Marked reviewed as it goes: a reverted change has been dealt with, and leaving it
     * pending would put the session back in the queue for a human who has already answered
     * it.</p>
     */
    @Transactional
    public ShAgentReview.Outcome revert(String session, String reviewer) {
        List<ShHistory> entries = new ArrayList<>(require(session));
        Collections.reverse(entries);
        Instant now = Instant.now();
        String actor = reviewer == null || reviewer.isBlank() ? "console" : reviewer;

        int reverted = 0;
        List<String> skipped = new ArrayList<>();
        for (ShHistory entry : entries) {
            Optional<String> failure = undo(entry, actor);
            if (failure.isPresent()) {
                skipped.add(failure.get());
            } else {
                reverted++;
            }
            entry.setReviewedDate(now);
            entry.setReviewedBy(actor);
            shHistoryRepository.save(entry);
        }
        return new ShAgentReview.Outcome(session, entries.size(), reverted, List.copyOf(skipped));
    }

    /**
     * Undoes one transition.
     *
     * @return the reason it could not be undone, or empty on success
     */
    private Optional<String> undo(ShHistory entry, String actor) {
        String postId = entry.getShObject();
        String what = describe(entry);
        if (postId == null || postId.isBlank()) {
            return Optional.of(what + " — no object recorded");
        }
        ShContentAction action = parse(entry.getAction());
        if (action == null) {
            return Optional.of(what + " — unknown transition '" + entry.getAction() + "'");
        }
        return switch (action) {
            case CREATE -> {
                if (rowsOf(postId).isEmpty()) {
                    yield Optional.of(what + " — already gone");
                }
                shPostUnifiedWriteService.delete(postId, actor);
                yield Optional.empty();
            }
            case DELETE -> shPostUnifiedWriteService.restoreFromTrash(postId, actor)
                    ? Optional.empty()
                    : Optional.of(what + " — not in the trash (purged, or restored already)");
            case PUBLISH -> {
                if (state(postId, ShPostState.PUBLISHED).isEmpty()) {
                    yield Optional.of(what + " — not published any more");
                }
                shPostUnifiedWriteService.unpublish(postId, actor);
                yield Optional.empty();
            }
            case UNPUBLISH -> {
                if (state(postId, ShPostState.DRAFT).isEmpty()) {
                    yield Optional.of(what + " — no draft left to publish");
                }
                shPostUnifiedWriteService.publish(postId, actor);
                yield Optional.empty();
            }
            // The two with no inverse. An UPDATE row records that a post changed, not what
            // it changed from, so undoing it means picking a version — a decision that
            // belongs to the human in front of SH14's version list, not to a bulk button.
            case UPDATE -> Optional.of(what + " — an edit has no automatic inverse; "
                    + "restore a version from the post's history");
            case RESTORE -> Optional.of(what + " — a restore is already an undo; "
                    + "delete the post if it should not be here");
        };
    }

    // --- shaping ---

    private ShAgentReview.Session summarize(String session, List<ShHistory> entries,
                                            Map<String, String> siteNames, boolean ids) {
        int pending = 0;
        int published = 0;
        Set<String> sites = new LinkedHashSet<>();
        String actor = null;
        for (ShHistory entry : entries) {
            if (!entry.isReviewed()) {
                pending++;
            }
            if (ShContentAction.PUBLISH.name().equals(entry.getAction())
                    || ShContentAction.UNPUBLISH.name().equals(entry.getAction())) {
                published++;
            }
            if (entry.getShSite() != null && siteNames.containsKey(entry.getShSite())) {
                sites.add(siteNames.get(entry.getShSite()));
            }
            if (actor == null) {
                actor = principalOf(entry.getOwner());
            }
        }
        // The window is newest-first within a session too, so the last row is the earliest.
        ShHistory newest = entries.get(0);
        ShHistory oldest = entries.get(entries.size() - 1);
        List<ShAgentReview.Change> preview = entries.stream()
                .limit(PREVIEW)
                .map(entry -> shape(entry, siteNames, ids))
                .toList();

        return new ShAgentReview.Session(session, actor,
                instant(oldest.getDate()), instant(newest.getDate()),
                entries.size(), pending, published, List.copyOf(sites), preview);
    }

    private ShAgentReview.Change shape(ShHistory entry, Map<String, String> siteNames, boolean ids) {
        ShContentAction action = parse(entry.getAction());
        return new ShAgentReview.Change(
                entry.getId(),
                entry.getAction(),
                addressOf(entry.getShObject()).orElse(null),
                entry.getDescription(),
                instant(entry.getDate()),
                entry.getShSite() == null ? null : siteNames.get(entry.getShSite()),
                entry.isReviewed(),
                entry.getReviewedBy(),
                action != null && action != ShContentAction.UPDATE && action != ShContentAction.RESTORE,
                ids ? entry.getShObject() : null);
    }

    /**
     * The human behind {@code alice (agent:7f3c…)}.
     *
     * <p>Stripped for display because the session id is already the row's key here, and
     * repeating it in every actor cell is noise in the one view where it is redundant.</p>
     */
    private static String principalOf(String owner) {
        if (owner == null) {
            return null;
        }
        int marker = owner.indexOf(" (agent:");
        return marker > 0 ? owner.substring(0, marker) : owner;
    }

    private Optional<String> addressOf(String postId) {
        if (postId == null) {
            return Optional.empty();
        }
        List<ShPostUnified> rows = rowsOf(postId);
        return rows.stream()
                .filter(row -> row.getState() == ShPostState.DRAFT)
                .findFirst()
                .or(() -> rows.stream().findFirst())
                .map(row -> shAddressResolver.addressOf(row).toString());
    }

    private List<ShPostUnified> rowsOf(String postId) {
        return shPostUnifiedRepository.findById(postId);
    }

    private Optional<ShPostUnified> state(String postId, ShPostState state) {
        return shPostUnifiedRepository.findByIdAndState(postId, state);
    }

    private Map<String, String> siteNames() {
        Map<String, String> names = new LinkedHashMap<>();
        for (ShSite site : shSiteRepository.findAll()) {
            names.put(site.getId(), site.getName());
        }
        return names;
    }

    private List<ShHistory> require(String session) {
        if (session == null || session.isBlank()) {
            throw new UnknownReviewSessionException(session);
        }
        List<ShHistory> entries = shHistoryRepository.findByAgentSession(session.trim());
        if (entries.isEmpty()) {
            throw new UnknownReviewSessionException(session);
        }
        return entries;
    }

    private static ShContentAction parse(String action) {
        try {
            return action == null ? null : ShContentAction.valueOf(action);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String describe(ShHistory entry) {
        return entry.getDescription() == null ? entry.getAction() : entry.getDescription();
    }

    private static String instant(Instant value) {
        return value == null ? null : value.toString();
    }

    /** A 404 for a session id nothing was recorded under. */
    public static class UnknownReviewSessionException extends ShTeachingException {

        public UnknownReviewSessionException(String session) {
            super(ShProblem.of("unknown-review-session", HttpStatus.NOT_FOUND,
                            "No agent session '" + (session == null ? "" : session) + "'")
                    .withDetail("A session appears in the review queue once it has written "
                            + "something. A run that only read never had anything to review, "
                            + "and one whose audit rows have been purged cannot be reviewed.")
                    .withFix("List the sessions with GET /api/v2/agent/review.")
                    .withExample("GET /api/v2/agent/review?pendingOnly=true"));
        }
    }
}
