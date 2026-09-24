/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.review;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.Locale;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.acl.ShContentAclService;
import com.viglet.shio.api.agent.ShAddressResolver;
import com.viglet.shio.api.history.ShContentAction;
import com.viglet.shio.api.history.ShContentSubject;
import com.viglet.shio.api.review.dto.ShReviewDetail;
import com.viglet.shio.api.review.dto.ShReviewOutcome;
import com.viglet.shio.api.review.dto.ShReviewPending;
import com.viglet.shio.api.review.dto.ShReviewSession;
import com.viglet.shio.api.folder.ShFolderWriteService;
import com.viglet.shio.api.gate.ShPublishGateService;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.history.ShHistory;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.history.ShHistoryRepository;
import com.viglet.shio.api.agent.ShAgentRunService;
import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.persistence.model.review.ShReviewDecision;
import com.viglet.shio.persistence.model.review.ShReviewDecision.Kind;
import com.viglet.shio.persistence.repository.review.ShReviewDecisionRepository;
import com.viglet.shio.api.agent.verify.ShAgentRenderService;
import com.viglet.shio.api.post.ShContentPerspective;
import com.viglet.shio.api.post.revision.ShPostRevisionService;
import com.viglet.shio.persistence.model.post.revision.ShPostRevision;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;
import com.viglet.shio.persistence.model.agent.ShAgentRun;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.tenant.ShTenantContext;
import com.viglet.shio.utils.ShStableMap;

/**
 * The review queue (SH109): what an agent run changed, and the two things a human can do
 * about it.
 *
 * <h2>Why this is a view over the audit and not a table</h2>
 *
 * <p>A "proposed change" table would be a second copy of the content — the agent's version
 * waiting to be accepted — and that is the design the unified model already rejected: a post
 * has a DRAFT row and a PUBLISHED row, and the draft <em>is</em> the proposal. So the queue is
 * the SH51 audit grouped by the SH106 session id, and the actions it offers are the ones the
 * platform already has: publish a draft (approve), and SH14 restore / SH55 trash (revert).
 * Nothing here is a third mechanism for saying what changed (P10).</p>
 *
 * <h2>What "approve" and "revert" mean, precisely</h2>
 *
 * <p><b>Approve</b> publishes every draft the run wrote. That is the whole gesture under SH107:
 * an agent's writes land in DRAFT, so approving is exactly the step the agent could not take.</p>
 *
 * <p><b>Revert</b> is per entry, because one run's changes are not one kind of change:
 * a {@code CREATE} is undone by trashing the post (SH55 — recoverable, not purged), an
 * {@code UPDATE} by restoring the draft from the published snapshot (SH14), a {@code DELETE} by
 * untrashing, and a {@code PUBLISH} by unpublishing. An entry with no honest undo — an update to
 * a post that was never published, so there is nothing to restore <em>to</em> — is reported as
 * skipped with the reason rather than approximated. A revert that guessed would be a second
 * surprise on top of the one the human came here to fix.</p>
 *
 * <p>Both are ACL-checked per post (SH54): reviewing is not a privilege, and a reviewer who
 * cannot write a post cannot approve or revert its changes either.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
public class ShReviewService {

    /** How many audit rows the session listing scans. Bounded, and it says so (SH103's rule). */
    private static final int SCAN_LIMIT = 2000;

    private final ShHistoryRepository shHistoryRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShPostUnifiedWriteService shPostUnifiedWriteService;
    private final ShFolderRepository shFolderRepository;
    private final ShFolderWriteService shFolderWriteService;
    private final ShContentAclService shContentAclService;
    private final ShAddressResolver shAddressResolver;
    private final ShAgentRunService shAgentRunService;
    private final ShSiteRepository shSiteRepository;
    private final ShAgentRenderService shAgentRenderService;
    private final ShPostRevisionService shPostRevisionService;
    private final ShReviewDecisionRepository shReviewDecisionRepository;
    /** SH1084 — the site's publish gates, which Approve used to publish around. */
    private final ShPublishGateService shPublishGateService;

    public ShReviewService(ShHistoryRepository shHistoryRepository,
                          ShPostUnifiedRepository shPostUnifiedRepository,
                          ShPostUnifiedWriteService shPostUnifiedWriteService,
                          ShFolderRepository shFolderRepository,
                          ShFolderWriteService shFolderWriteService,
                          ShContentAclService shContentAclService,
                          ShAddressResolver shAddressResolver,
                          ShAgentRunService shAgentRunService,
                          ShSiteRepository shSiteRepository,
                          ShAgentRenderService shAgentRenderService,
                          ShPostRevisionService shPostRevisionService,
                          ShReviewDecisionRepository shReviewDecisionRepository,
                          ShPublishGateService shPublishGateService) {
        this.shPublishGateService = shPublishGateService;
        this.shHistoryRepository = shHistoryRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shPostUnifiedWriteService = shPostUnifiedWriteService;
        this.shFolderRepository = shFolderRepository;
        this.shFolderWriteService = shFolderWriteService;
        this.shContentAclService = shContentAclService;
        this.shAddressResolver = shAddressResolver;
        this.shAgentRunService = shAgentRunService;
        this.shSiteRepository = shSiteRepository;
        this.shAgentRenderService = shAgentRenderService;
        this.shPostRevisionService = shPostRevisionService;
        this.shReviewDecisionRepository = shReviewDecisionRepository;
    }

    /**
     * One instruction about one change (SH1001).
     *
     * @param postId   the post, as the detail's entries name it
     * @param decision what to do about it
     * @param note     why — required on a {@link Kind#REJECT}, optional elsewhere
     */
    public record Decision(String postId, Kind decision, String note) {
    }

    /** A rejection with nothing written down, which is the rejection missing (SH1001). */
    public static class RejectionNeedsReasonException extends ShTeachingException {

        public RejectionNeedsReasonException(String postId) {
            super(ShProblem.badRequest("rejection-needs-a-reason",
                            "rejecting " + postId + " needs a reason")
                    .withDetail("A rejection nobody can read teaches nothing: the agent that wrote "
                            + "the page cannot be told what was wrong with it, and the next person "
                            + "to open the run cannot see that anybody looked.")
                    .withFix("Say what is wrong with this page, in a sentence whoever fixes it can "
                            + "act on. HOLD is the decision for \"not now\", and it takes no reason.")
                    .withExample("{\"postId\":\"" + postId + "\",\"decision\":\"REJECT\","
                            + "\"note\":\"the price table is last quarter's\"}"));
        }
    }

    /**
     * Decide one run's changes, one at a time (SH1001).
     *
     * <h2>Why this replaces the two verbs rather than joining them</h2>
     *
     * <p>Approve-everything and revert-everything are this call over every entry, and they are
     * implemented that way: two code paths for one decision is how the whole-run gesture and the
     * per-change one come to disagree about what "approve" means — which permission it asks for,
     * what it records, what it skips.</p>
     *
     * <h2>Atomic in the run, reported per change</h2>
     *
     * <p>One transaction, so a decision list either lands or does not. What it does <b>not</b> do is
     * fail the call because one post could not be acted on: that is reported as a {@code skipped}
     * row with its reason, the shape this surface already answered with, because a curator deciding
     * eleven pages wants the nine that worked and the two that did not, named.</p>
     *
     * @param actor the curator; APPROVE asks them for PUBLISH on each post (SH995)
     */
    @Transactional
    public ShReviewOutcome decide(String session, List<Decision> decisions, String actor) {
        return decide(session, decisions, actor, null);
    }

    /**
     * As {@link #decide(String, List, String)}, with SH998's waiver for the approvals a publish gate
     * stops (SH1084). One reason covers the call, because the curator writes it once for what they
     * chose to send past the check.
     */
    @Transactional
    public ShReviewOutcome decide(String session, List<Decision> decisions, String actor,
                                  String waiver) {
        return decide(session, decisions, actor, "decide", waiver);
    }

    private ShReviewOutcome decide(String session, List<Decision> decisions, String actor,
                                   String label, String waiver) {
        List<String> done = new ArrayList<>();
        List<ShReviewOutcome.Skipped> skipped = new ArrayList<>();
        Map<String, ShHistory> newest = newestPerPost(session);
        Set<String> seen = new LinkedHashSet<>();
        for (Decision decision : decisions) {
            String postId = decision.postId();
            if (postId == null || !seen.add(postId)) {
                continue;
            }
            if (decision.decision() == Kind.REJECT
                    && (decision.note() == null || decision.note().isBlank())) {
                // Refused, not defaulted: the record IS the feature here, and the console asking for
                // one while the API does not would make the rule a preference of one surface.
                throw new RejectionNeedsReasonException(postId);
            }
            try {
                apply(session, decision, newest.get(postId), actor, waiver)
                        .ifPresentOrElse(reason -> skipped.add(
                                        new ShReviewOutcome.Skipped(postId, reason)),
                                () -> done.add(postId));
            } catch (ShTeachingException teaching) {
                // A teaching refusal is the caller's problem to fix and keeps its own status; the
                // rest are this post's problem and belong beside the posts that worked.
                throw teaching;
            } catch (RuntimeException e) {
                skipped.add(new ShReviewOutcome.Skipped(postId, reason(e)));
            }
        }
        return new ShReviewOutcome(session, label, done.size(), List.copyOf(done),
                List.copyOf(skipped));
    }

    /** Carry out one decision, and record it whatever it was. */
    private Optional<String> apply(String session, Decision decision, ShHistory entry, String actor,
                                   String waiver) {
        Optional<String> refused = switch (decision.decision()) {
            case APPROVE -> approveOne(decision.postId(), actor, waiver);
            case REJECT -> entry == null
                    ? Optional.of("this run changed nothing about that post")
                    : revertOne(entry, decision.postId(), actor);
            // Nothing to carry out: the row IS the act, which is the reason this table exists.
            case HOLD -> Optional.empty();
        };
        if (refused.isEmpty()) {
            record(session, decision, actor);
        }
        return refused;
    }

    /** Publish one draft this run wrote, or say why not. */
    private Optional<String> approveOne(String postId, String actor, String waiver) {
        if (state(postId, ShPostState.DRAFT).isEmpty()) {
            return Optional.of("no draft to publish");
        }
        // SH995 — approving publishes, and approval is exactly the act a team wants to hold apart
        // from writing: the reviewer needs PUBLISH, not WRITE.
        shContentAclService.requirePublish(postId);
        // SH1084 — and it passes the site's publish gates, which Approve used to skip: a check the
        // one press the queue exists for could publish around was a rule that read as enforced. A
        // gate that stops this post is its row in `skipped`, not the end of the approval, because a
        // curator approving eleven pages wants the nine that passed. A bad waiver still throws: that
        // is the caller's to fix.
        String waived;
        try {
            waived = shPublishGateService.requirePost(postId, waiver);
        } catch (ShPublishGateService.GateFailedException stopped) {
            // The title names the gate and the page ("gate 'ready' stops post:..."), which is what a
            // curator reading the skipped list needs; the detail is the check's own findings.
            return Optional.of(stopped.problem().toProblemDetail().getTitle()
                    + " — approve it again with override=<reason> to publish anyway");
        }
        shPostUnifiedWriteService.publish(postId, actor, waived);
        return Optional.empty();
    }

    /** The standing decision, replaced rather than appended: the row is the current answer. */
    private void record(String session, Decision decision, String actor) {
        ShReviewDecision row = shReviewDecisionRepository
                .findBySessionAndPostId(session, decision.postId())
                .orElseGet(ShReviewDecision::new);
        row.setSession(session);
        row.setPostId(decision.postId());
        row.setDecision(decision.decision().name());
        row.setNote(clip(decision.note()));
        row.setActor(actor);
        row.setDecidedAt(Instant.now());
        shReviewDecisionRepository.save(row);
    }

    /** Bounded to the column, for SH544's reason: the record must never be what refuses the act. */
    private static String clip(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String trimmed = note.trim();
        return trimmed.length() > 1000 ? trimmed.substring(0, 1000) : trimmed;
    }

    /**
     * The run's last word about each post, which is what a rejection undoes.
     *
     * <p>Newest first, the order {@link #revert} walks for the reason it states: a run's own last
     * word about a post is the one to undo, so a create-then-publish pair ends as "not published,
     * then trashed" rather than as a publish applied to a post that no longer exists.</p>
     */
    private Map<String, ShHistory> newestPerPost(String session) {
        List<ShHistory> entries = new ArrayList<>(shHistoryRepository.findByAgentSession(session));
        entries.sort((left, right) -> right.getDate().compareTo(left.getDate()));
        Map<String, ShHistory> newest = new LinkedHashMap<>();
        for (ShHistory entry : entries) {
            if (entry.getShObject() != null) {
                newest.putIfAbsent(entry.getShObject(), entry);
            }
        }
        return newest;
    }

    /** What a curator has already said about this run's changes (SH1001). */
    @Transactional(readOnly = true)
    public List<ShReviewDecision> decisions(String session) {
        return shReviewDecisionRepository.findBySessionOrderByDecidedAtAsc(session);
    }

    /**
     * How many pages one opened run compares for an appearance change (SH1000).
     *
     * <p>Two renders each, so this is the ceiling on forty renders. Twenty is a run somebody reads
     * in one sitting; past that the cut is reported and the reviewer still has the list of changes,
     * which is the part that was always there.</p>
     */
    private static final int APPEARANCE_CEILING = 20;

    /** The runs, newest first. */
    @Transactional(readOnly = true)
    public List<ShReviewSession> sessions(int limit) {
        List<ShHistory> entries = shHistoryRepository.findAgentEntries(PageRequest.of(0, SCAN_LIMIT));
        Map<String, List<ShHistory>> bySession = new LinkedHashMap<>();
        Set<String> objects = new LinkedHashSet<>();
        for (ShHistory entry : entries) {
            bySession.computeIfAbsent(entry.getAgentSessionId(), key -> new ArrayList<>()).add(entry);
            if (entry.getShObject() != null) {
                objects.add(entry.getShObject());
            }
        }
        // SH615 — resolved once for the whole page rather than per session, which is the same
        // two-query shape `pending()` uses and the reason it was not `sessions()` in the first
        // place. The old count asked `state(id, DRAFT).isPresent()` inside the per-entry loop:
        // one query per audit row, answering a question that was wrong anyway.
        Set<String> unreleased = waiting(objects);
        // SH1000 — one query for the page's runs, never one per session and never per row: the
        // premise is one sentence about many changes, and this is the one place it is read.
        List<ShAgentRun> runs = shAgentRunService.runs(bySession.keySet());
        Map<String, ShAgentRun> byId = new LinkedHashMap<>();
        runs.forEach(run -> byId.put(run.getId(), run));
        // SH1000 — one query for every site the page mentions, not one per run: a queue of twenty
        // runs over three sites is three sites.
        Map<String, String> siteNames = namesOf(objects.isEmpty() ? Set.of() : entries.stream()
                .map(ShHistory::getShSite)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
        return bySession.entrySet().stream()
                .map(group -> summarize(group.getKey(), group.getValue(), unreleased,
                        byId.get(group.getKey()), siteNames))
                .limit(Math.max(1, limit))
                .toList();
    }

    /**
     * How much is waiting for a human — the navigation badge's read (SH207).
     *
     * <p>Two queries however many runs there are, which is the whole reason it is not
     * {@link #sessions}: that one resolves each touched post one at a time, and a badge is asked
     * for on every console render rather than on a page somebody opened.</p>
     *
     * <p>It counts <b>sessions</b>, because the page it opens is a list of sessions. Counting
     * changes there would produce a badge whose number appears nowhere on the page it links to,
     * which is the kind of mismatch a reviewer notices once and then stops trusting.</p>
     */
    @Transactional(readOnly = true)
    public ShReviewPending pending() {
        List<ShHistory> entries = shHistoryRepository.findAgentEntries(PageRequest.of(0, SCAN_LIMIT));
        Set<String> objects = new LinkedHashSet<>();
        for (ShHistory entry : entries) {
            if (entry.getShObject() != null) {
                objects.add(entry.getShObject());
            }
        }
        if (objects.isEmpty()) {
            return new ShReviewPending(0, 0);
        }
        Set<String> unreleased = waiting(objects);
        if (unreleased.isEmpty()) {
            return new ShReviewPending(0, 0);
        }
        Set<String> sessions = new LinkedHashSet<>();
        for (ShHistory entry : entries) {
            if (entry.getShObject() != null && unreleased.contains(entry.getShObject())) {
                sessions.add(entry.getAgentSessionId());
            }
        }
        return new ShReviewPending(sessions.size(), unreleased.size());
    }

    /**
     * The unreleased posts a curator has not already parked (SH1001).
     *
     * <p>A {@code HOLD} is a decision with no side effect — "I have read this and I am not ready" —
     * so nothing about the content says it has been looked at. If the count did not subtract it, the
     * one gesture for parking a page would leave the badge saying the same number forever, which is
     * the failure mode {@link #unreleased} itself was written to avoid one layer down.</p>
     *
     * <p>{@code APPROVE} and {@code REJECT} need no subtraction: publishing and reverting each make
     * the post released on their own, which is what makes those two inferable and {@code HOLD} not.</p>
     */
    private Set<String> waiting(Set<String> objects) {
        if (objects.isEmpty()) {
            return Set.of();
        }
        Set<String> unreleased = new LinkedHashSet<>(unreleased(objects));
        shReviewDecisionRepository.findByPostIdIn(objects).stream()
                .filter(decision -> Kind.HOLD.name().equals(decision.getDecision()))
                .forEach(decision -> unreleased.remove(decision.getPostId()));
        return unreleased;
    }

    /**
     * Which of these posts hold work a human has not released (SH207).
     *
     * <h2>Not "which have a draft" — the trap this exists to avoid</h2>
     *
     * <p>{@code publish} writes a {@code PUBLISHED} row and <b>keeps</b> the draft, because the
     * draft is the editable copy. So every post that has ever existed has one, forever, and a
     * count keyed on its presence never reaches zero. An indicator that stays lit after the
     * curator has acted is one they learn to ignore, which is worse than none: it occupies the
     * place a working one would have gone.</p>
     *
     * <p>Unreleased means one of two things, and both need the published twin to see: the draft
     * has <b>no</b> twin, so it has never been published; or the draft was modified <b>after</b>
     * its twin was published, so it holds edits nobody has released. Approving does exactly what
     * makes both false, which is the property that makes the number trustworthy.</p>
     */
    private Set<String> unreleased(Set<String> ids) {
        Map<String, Instant> drafts = new LinkedHashMap<>();
        Map<String, Instant> published = new LinkedHashMap<>();
        for (Object[] row : shPostUnifiedRepository.findStateRowsOf(ids)) {
            String id = (String) row[0];
            ShPostState state = (ShPostState) row[1];
            if (state == ShPostState.DRAFT) {
                // `modifiedDate` is null on a row nobody has edited since it was created, and
                // then the creation instant is when its content last changed.
                drafts.put(id, row[2] != null ? (Instant) row[2] : (Instant) row[4]);
            } else if (state == ShPostState.PUBLISHED) {
                published.put(id, (Instant) row[3]);
            }
        }
        Set<String> waiting = new LinkedHashSet<>();
        drafts.forEach((id, changed) -> {
            Instant released = published.get(id);
            if (released == null || (changed != null && changed.isAfter(released))) {
                waiting.add(id);
            }
        });
        return waiting;
    }

    /** One run's changes, oldest first. */
    @Transactional(readOnly = true)
    public ShReviewDetail detail(String session) {
        List<ShHistory> entries = shHistoryRepository.findByAgentSession(session);
        List<ShReviewDetail.Entry> rows = entries.stream().map(this::entry).toList();
        String actor = entries.isEmpty() ? null : entries.get(0).getOwner();
        return new ShReviewDetail(session, actor, rows, appearanceOf(rows, startedAt(entries)),
                shReviewDecisionRepository.findBySessionOrderByDecidedAtAsc(session).stream()
                        .map(row -> new ShReviewDetail.Decided(row.getPostId(), row.getDecision(),
                                row.getNote(), row.getActor(), row.getDecidedAt()))
                        .toList());
    }

    /** The run's first change, which is the moment "before this run" means (SH1000). */
    private static Instant startedAt(List<ShHistory> entries) {
        return entries.stream().map(ShHistory::getDate).filter(java.util.Objects::nonNull)
                .min(Instant::compareTo).orElse(null);
    }

    /**
     * Which of this run's pages look different than they did before it (SH1000).
     *
     * <h2>Why the comparison is against a revision and not against a stored digest</h2>
     *
     * <p>Nothing stores the appearance a page had: {@code sh_post_revision} keeps what a page
     * <em>said</em>, not what it looked like, and that is the right split — a revision is a record
     * of content, and the same content renders differently after a template changes. So the "before"
     * is produced the only honest way: the revision that preceded the run, rendered <b>through
     * today's templates</b>, which is also the question a reviewer has. A page whose look moved
     * because somebody edited a shared region is a page whose look moved.</p>
     *
     * <p>A page the run created has no before and is not compared. It is already a {@code CREATE} in
     * the counts, and calling a new page's appearance "changed" would put the one row a reviewer can
     * read from the action column into a list that is meant to hold the ones they cannot.</p>
     */
    private ShReviewDetail.Appearance appearanceOf(List<ShReviewDetail.Entry> rows, Instant startedAt) {
        if (startedAt == null) {
            return null;
        }
        // Sorted and de-duplicated by address: two runs over the same change must answer with the
        // same list in the same order (P5), and a page edited four times is one page.
        Set<String> addresses = rows.stream()
                .map(ShReviewDetail.Entry::address)
                .filter(address -> address != null && address.startsWith("post:"))
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        Map<String, String> postIds = new LinkedHashMap<>();
        for (ShReviewDetail.Entry row : rows) {
            if (row.address() != null && row.postId() != null) {
                postIds.putIfAbsent(row.address(), row.postId());
            }
        }
        List<String> moved = new ArrayList<>();
        int checked = 0;
        boolean cut = false;
        for (String address : addresses) {
            if (checked >= APPEARANCE_CEILING) {
                cut = true;
                break;
            }
            Integer before = revisionBefore(postIds.get(address), startedAt);
            if (before == null) {
                continue;
            }
            String was = appearance(address, ShContentPerspective.revision(before).state());
            String is = appearance(address, null);
            if (was == null || is == null) {
                continue;
            }
            checked++;
            if (!was.equals(is)) {
                moved.add(address);
            }
        }
        return new ShReviewDetail.Appearance(List.copyOf(moved), checked,
                cut ? "compared the first " + APPEARANCE_CEILING + " of " + addresses.size()
                        + " page(s); each comparison is two renders" : null);
    }

    /** The newest revision captured before the run began, or {@code null} for a page it created. */
    private Integer revisionBefore(String postId, Instant startedAt) {
        if (postId == null) {
            return null;
        }
        return shPostRevisionService.revisions(postId).stream()
                .filter(revision -> revision.getCapturedAt() != null
                        && revision.getCapturedAt().isBefore(startedAt))
                .map(ShPostRevision::getVersionNo)
                .max(Integer::compareTo)
                .orElse(null);
    }

    /**
     * One page's appearance hash at one version, or {@code null} when it cannot be had.
     *
     * <p>Absent rather than an exception, the rule {@code ShAgentProofService.digestsOf} already
     * follows: a page that will not render is not a reason to fail the read a reviewer opened, and
     * the {@code checked} count says how many comparisons actually happened.</p>
     */
    private String appearance(String address, String state) {
        try {
            return shAgentRenderService.digest(address, null, state).appearanceDigest();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Publish every draft this run wrote.
     *
     * <p>Deduplicated by post: a run that wrote a page four times gets published once, and a
     * count of four "approvals" would misdescribe what happened.</p>
     */
    @Transactional
    public ShReviewOutcome approve(String session, String actor) {
        return approve(session, actor, null);
    }

    /** As {@link #approve(String, String)}, with SH998's waiver for a failing gate (SH1084). */
    @Transactional
    public ShReviewOutcome approve(String session, String actor, String waiver) {
        // SH1001 — the same call, over every change. Two code paths for one decision is how the
        // whole-run gesture and the per-change one come to disagree about what approve means:
        // which permission it asks for, what it records, what it skips.
        return decide(session, touchedPosts(session).stream()
                .map(postId -> new Decision(postId, Kind.APPROVE, null))
                .toList(), actor, "approve", waiver);
    }

    /**
     * Undo what this run did, newest change first.
     *
     * <p>Order matters: the run's own last word about a post is the one to undo, and walking
     * backwards means a create-then-publish pair ends as "not published, then trashed" rather
     * than as a publish applied to a post that no longer exists.</p>
     */
    @Transactional
    public ShReviewOutcome revert(String session, String actor) {
        // SH1001 — a rejection of every change, and its reason is the gesture itself. The note is
        // supplied rather than waived: a REJECT with nothing written down is refused on the
        // per-change call, and a whole-run verb exempt from that rule would be the door.
        return decide(session, newestPerPost(session).keySet().stream()
                .map(postId -> new Decision(postId, Kind.REJECT, WHOLE_RUN_REVERT))
                .toList(), actor, "revert", null);
    }

    /** The reason a whole-run revert records, since the gesture is its own explanation (SH1001). */
    private static final String WHOLE_RUN_REVERT = "the whole run was reverted";

    /**
     * Reverts one entry.
     *
     * @return empty when it was reverted, or the reason it was not
     */
    private Optional<String> revertOne(ShHistory entry, String postId, String actor) {
        ShContentAction action = actionOf(entry);
        if (action == null) {
            return Optional.of("unknown action '" + entry.getAction() + "'");
        }
        if (subjectOf(entry) == ShContentSubject.FOLDER) {
            // The row says it is a folder, so a missing folder is a purge and is reported as one.
            // Falling through to the post branch here is what the derived subject used to do, and
            // it answered "the post no longer exists" about a section.
            return shFolderRepository.findAnyById(postId)
                    .map(folder -> revertFolder(folder, action, actor))
                    .orElseGet(() -> Optional.of(
                            "the section no longer exists — it was purged, not deleted"));
        }
        boolean hasDraft = state(postId, ShPostState.DRAFT).isPresent();
        boolean hasPublished = state(postId, ShPostState.PUBLISHED).isPresent();
        if (!hasDraft && !hasPublished && action != ShContentAction.DELETE) {
            return Optional.of("the post no longer exists");
        }
        shContentAclService.requireWrite(postId);
        switch (action) {
            case CREATE -> shPostUnifiedWriteService.delete(postId, actor);
            case UPDATE -> {
                if (!hasPublished) {
                    // Nothing to restore *to*: the run created the content this update
                    // refined, and "revert" would mean deleting a post the human may want.
                    // Reverting the CREATE entry is the honest gesture, and it is in this
                    // same list.
                    return Optional.of("never published — revert its creation instead");
                }
                shPostUnifiedWriteService.restoreDraftFromPublished(postId, actor);
            }
            // SH995 — the two branches that change what visitors see ask for PUBLISH. Reverting the
            // rest of a run is a write: it puts a draft back, which is what WRITE is for.
            case PUBLISH -> {
                shContentAclService.requirePublish(postId);
                shPostUnifiedWriteService.unpublish(postId, actor);
            }
            case UNPUBLISH -> {
                if (!hasDraft) {
                    return Optional.of("no draft to publish back");
                }
                shContentAclService.requirePublish(postId);
                shPostUnifiedWriteService.publish(postId, actor);
            }
            case DELETE -> {
                // SH243 — asked, not caught. A restore onto an occupied URL is refused, and this
                // loop's catch cannot report that refusal: the throw would come out of an inner
                // @Transactional call, which leaves the shared transaction rollback-only, so the
                // skipped row this method exists to produce would be lost to an
                // UnexpectedRollbackException at commit. The reason is the refusal's own wording.
                Optional<String> occupied = shPostUnifiedWriteService.restoreUrlConflict(postId);
                if (occupied.isPresent()) {
                    return occupied;
                }
                if (!shPostUnifiedWriteService.restoreFromTrash(postId, actor)) {
                    return Optional.of("not in the trash — it was purged, not deleted");
                }
            }
            case RESTORE -> {
                return Optional.of("a restore is already an undo");
            }
            case PURGE -> {
                return Optional.of("permanently deleted — there is nothing left to restore");
            }
        }
        return Optional.empty();
    }

    /**
     * One run, as a row in the queue.
     *
     * <h2>SH615 — what {@code pending} counts, and it was wrong in two ways at once</h2>
     *
     * <p>It asked, per audit entry, whether the post has a {@code DRAFT} row. But {@code publish}
     * writes a {@code PUBLISHED} row and <b>keeps</b> the draft as the editable copy, so every post
     * that has ever existed has one, forever: <b>the number could only go up</b>. A curator who
     * approved a run saw the page reload with the same "3 to review" and Approve still enabled on
     * work already published — no error, nothing logged, the page simply asserting something untrue
     * about the thing it exists to resolve.</p>
     *
     * <p>SH207 had already written the right definition one method away, because a navigation badge
     * has to be safely ignorable at zero and so could not reuse this one. {@link #unreleased} is
     * that definition: a draft with <b>no</b> published twin, or one modified <b>after</b> its twin
     * was published. Approving is exactly what makes both false.</p>
     *
     * <p><b>And it counted entries rather than posts</b>, so one page edited four times reported
     * four. Both are fixed together deliberately: a number that is fresh and still not a count of
     * decisions reads as correct, which is worse than one that is visibly stuck.</p>
     *
     * @param unreleased posts across the whole page that still hold work nobody has released,
     *                   resolved once by {@link #sessions} — per-session would be a query per run
     */
    /** Site id to site name, for the one place a reviewer reads them (SH1000). */
    private Map<String, String> namesOf(Set<String> siteIds) {
        Map<String, String> named = new LinkedHashMap<>();
        if (siteIds.isEmpty()) {
            return named;
        }
        shSiteRepository.findAllById(siteIds).forEach(site -> named.put(site.getId(), site.getName()));
        return named;
    }

    private ShReviewSession summarize(String session, List<ShHistory> entries,
                                      Set<String> unreleased, ShAgentRun run,
                                      Map<String, String> siteNames) {
        Map<String, Integer> byAction = new LinkedHashMap<>();
        Map<String, Integer> bySite = new LinkedHashMap<>();
        Set<String> sites = new LinkedHashSet<>();
        Instant first = null;
        Instant last = null;
        // A Set, because the unit is the decision and a page edited four times is one decision.
        Set<String> waiting = new LinkedHashSet<>();
        for (ShHistory entry : entries) {
            byAction.merge(entry.getAction() == null ? "?" : entry.getAction(), 1, Integer::sum);
            if (entry.getShSite() != null) {
                sites.add(entry.getShSite());
                // By name, because an id tells a reviewer nothing — and falling back to the id
                // rather than dropping the row, so a site this reader could not resolve is still
                // counted somewhere rather than silently missing from the total.
                bySite.merge(siteNames.getOrDefault(entry.getShSite(), entry.getShSite()), 1,
                        Integer::sum);
            }
            Instant date = entry.getDate();
            if (date != null) {
                first = first == null || date.isBefore(first) ? date : first;
                last = last == null || date.isAfter(last) ? date : last;
            }
            if (entry.getShObject() != null && unreleased.contains(entry.getShObject())) {
                waiting.add(entry.getShObject());
            }
        }
        int pending = waiting.size();
        return new ShReviewSession(session, entries.get(0).getOwner(), entries.size(),
                // SH136: byAction is a LinkedHashMap on purpose; Map.copyOf discarded
                // that insertion order on the way out.
                ShStableMap.copyOf(byAction), first, last, List.copyOf(sites),
                ShStableMap.copyOf(bySite),
                run == null ? null : run.getIntent(),
                run == null ? null : run.getClientName(), pending);
    }

    /**
     * Undoes a folder entry (SH200, extended by SH196). Every folder write is now audited, so the
     * queue sees a run's structural changes as well as its content ones — and restoring one
     * {@code DELETE} entry brings the whole subtree back, which is the gesture a human means by
     * "undo that".
     *
     * <h2>Why a rename has no undo, and is not approximated into one</h2>
     *
     * <p>A {@code CREATE} is undone by trashing the section (SH55 — recoverable, not purged), the
     * same shape as undoing a post's creation. A rename or a move is <b>not</b> undone here: the
     * audit row records what the change <em>was</em>, in its description, but the previous parent
     * is a name and not an id, and re-deriving a folder from a name is how you attach a subtree to
     * the wrong section. So it is reported as skipped, with the description named as the place the
     * old value is — the SH109 rule that a revert which guessed would be a second surprise on top
     * of the one the human came here to fix.</p>
     */
    private Optional<String> revertFolder(ShFolder folder, ShContentAction action, String actor) {
        shContentAclService.requireWrite(folder.getId());
        return switch (action) {
            case CREATE -> {
                if (folder.isTrashed()) {
                    yield Optional.of("already in the trash");
                }
                yield shFolderWriteService.delete(folder.getId(), actor)
                        ? Optional.empty()
                        : Optional.of("could not be trashed");
            }
            case DELETE -> {
                // SH243, the folder half of the same rule: one post whose URL was taken refuses the
                // whole subtree, and asking first is what keeps that refusal a reported row rather
                // than an UnexpectedRollbackException.
                Optional<String> occupied = shFolderWriteService.restoreUrlConflict(folder.getId());
                if (occupied.isPresent()) {
                    yield occupied;
                }
                if (shFolderWriteService.restore(folder.getId(), actor)) {
                    yield Optional.empty();
                }
                yield Optional.of(folder.isTrashed()
                        ? "its parent folder is in the trash — restore that instead"
                        : "not in the trash — it was purged, not deleted");
            }
            case RESTORE -> Optional.of("a restore is already an undo");
            case UPDATE -> Optional.of("a section's rename or move has no undo the queue can "
                    + "perform — the previous name is in this entry's description");
            default -> Optional.of("no undo for a folder " + action.name().toLowerCase(Locale.ROOT));
        };
    }

    private ShReviewDetail.Entry entry(ShHistory history) {
        String postId = history.getShObject();
        if (subjectOf(history) == ShContentSubject.FOLDER) {
            return shFolderRepository.findAnyById(postId)
                    .map(folder -> folderEntry(history, folder))
                    // Purged: no address and nothing to undo, but the row still says what it was
                    // and when. Reporting it as a post that vanished is the reading SH196's column
                    // exists to stop.
                    .orElseGet(() -> new ShReviewDetail.Entry(history.getAction(), postId, null,
                            history.getDescription(), history.getShSite(), history.getDate(),
                            false, false, false, false));
        }
        Optional<ShPostUnified> draft = postId == null ? Optional.empty()
                : state(postId, ShPostState.DRAFT);
        Optional<ShPostUnified> published = postId == null ? Optional.empty()
                : state(postId, ShPostState.PUBLISHED);
        ShPostUnified live = draft.or(() -> published).orElse(null);
        ShContentAction action = actionOf(history);
        boolean trashed = postId != null && live == null && trashed(postId);
        return new ShReviewDetail.Entry(
                history.getAction(),
                postId,
                live == null ? null : shAddressResolver.addressOf(live).toString(),
                live != null ? live.getTitle() : history.getDescription(),
                history.getShSite(),
                history.getDate(),
                draft.isPresent(),
                published.isPresent(),
                trashed,
                revertable(action, draft.isPresent(), published.isPresent(), trashed));
    }

    /**
     * A folder entry's row (SH200). {@code draft}/{@code published} are meaningless for a
     * section, so they are reported {@code false} rather than guessed — the fields a reviewer
     * reads are the address, whether it is in the trash, and whether the undo works.
     */
    private ShReviewDetail.Entry folderEntry(ShHistory history, ShFolder folder) {
        ShContentAction action = actionOf(history);
        boolean trashed = folder.isTrashed();
        boolean canRestore = trashed
                && (folder.getParentFolder() == null || !folder.getParentFolder().isTrashed());
        return new ShReviewDetail.Entry(
                history.getAction(),
                folder.getId(),
                shAddressResolver.addressOf(folder).toString(),
                folder.getName(),
                history.getShSite(),
                history.getDate(),
                false,
                false,
                trashed,
                folderRevertable(action, trashed, canRestore));
    }

    /**
     * Whether the queue can honestly undo a folder entry — the rules {@link #revertFolder}
     * applies, so a button is offered only where it works (SH196).
     */
    private static boolean folderRevertable(ShContentAction action, boolean trashed,
                                            boolean canRestore) {
        if (action == null) {
            return false;
        }
        return switch (action) {
            case CREATE -> !trashed;
            case DELETE -> canRestore;
            default -> false;
        };
    }

    /**
     * What an audit row is about (SH196).
     *
     * <p>Read off the row when it is there. A row written before the column existed is asked
     * about instead — the query this column exists to remove, kept for exactly the rows that
     * predate it and no others.</p>
     */
    private ShContentSubject subjectOf(ShHistory history) {
        if (history.getSubjectKind() != null) {
            return ShContentSubject.of(history.getSubjectKind());
        }
        return history.getShObject() != null
                && shFolderRepository.findAnyById(history.getShObject()).isPresent()
                        ? ShContentSubject.FOLDER
                        : ShContentSubject.POST;
    }

    /**
     * Whether the queue can honestly undo this entry — the same rules
     * {@link #revertOne} applies, so a button is offered only where it works.
     */
    private static boolean revertable(ShContentAction action, boolean draft, boolean published,
                                      boolean trashed) {
        if (action == null) {
            return false;
        }
        return switch (action) {
            case CREATE -> draft || published;
            case UPDATE -> published;
            case PUBLISH -> published;
            case UNPUBLISH -> draft;
            case DELETE -> trashed;
            case RESTORE -> false;
            // SH224 — a purge is the one transition with no undo at all: the rows are gone, so
            // there is nothing for the queue to put back. It is recorded precisely BECAUSE it
            // cannot be reversed, and offering an Approve/Revert on it would be the queue
            // promising something no code can deliver.
            case PURGE -> false;
        };
    }

    /**
     * The distinct posts this run touched, in the order it first touched them.
     *
     * <p>Folder entries are excluded (SH200): approve means "publish this run's drafts", and
     * a section has no draft. Leaving them in would list every deleted folder as skipped for
     * a reason that is true of folders in general, which reads as a failure.</p>
     */
    private List<String> touchedPosts(String session) {
        Set<String> ids = new LinkedHashSet<>();
        for (ShHistory entry : shHistoryRepository.findByAgentSession(session)) {
            if (entry.getShObject() != null && subjectOf(entry) == ShContentSubject.POST) {
                ids.add(entry.getShObject());
            }
        }
        return List.copyOf(ids);
    }

    private Optional<ShPostUnified> state(String postId, ShPostState state) {
        return shPostUnifiedRepository.findByIdAndState(postId, state);
    }

    /**
     * Whether this id is in the SH55 trash. The trash finder is native (it has to be —
     * {@code @SQLRestriction} hides trashed rows from every Hibernate read), so this is the
     * only way to tell "deleted" from "never existed".
     */
    private boolean trashed(String postId) {
        return !shPostUnifiedRepository.findTrashedById(postId, ShTenantContext.getTenantId()).isEmpty();
    }

    private static ShContentAction actionOf(ShHistory history) {
        try {
            return history.getAction() == null ? null : ShContentAction.valueOf(history.getAction());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String reason(RuntimeException e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
