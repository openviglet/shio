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
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.history.ShHistory;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.history.ShHistoryRepository;
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

    public ShReviewService(ShHistoryRepository shHistoryRepository,
                          ShPostUnifiedRepository shPostUnifiedRepository,
                          ShPostUnifiedWriteService shPostUnifiedWriteService,
                          ShFolderRepository shFolderRepository,
                          ShFolderWriteService shFolderWriteService,
                          ShContentAclService shContentAclService,
                          ShAddressResolver shAddressResolver) {
        this.shHistoryRepository = shHistoryRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shPostUnifiedWriteService = shPostUnifiedWriteService;
        this.shFolderRepository = shFolderRepository;
        this.shFolderWriteService = shFolderWriteService;
        this.shContentAclService = shContentAclService;
        this.shAddressResolver = shAddressResolver;
    }

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
        Set<String> unreleased = objects.isEmpty() ? Set.of() : unreleased(objects);
        return bySession.entrySet().stream()
                .map(group -> summarize(group.getKey(), group.getValue(), unreleased))
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
        Set<String> unreleased = unreleased(objects);
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
        return new ShReviewDetail(session, actor, rows);
    }

    /**
     * Publish every draft this run wrote.
     *
     * <p>Deduplicated by post: a run that wrote a page four times gets published once, and a
     * count of four "approvals" would misdescribe what happened.</p>
     */
    @Transactional
    public ShReviewOutcome approve(String session, String actor) {
        List<String> done = new ArrayList<>();
        List<ShReviewOutcome.Skipped> skipped = new ArrayList<>();
        for (String postId : touchedPosts(session)) {
            Optional<ShPostUnified> draft = state(postId, ShPostState.DRAFT);
            if (draft.isEmpty()) {
                skipped.add(new ShReviewOutcome.Skipped(postId, "no draft to publish"));
                continue;
            }
            try {
                // SH995 — approving an agent's run publishes it, and approval is exactly the act a
                // team wants to hold apart from writing: the reviewer needs PUBLISH, not WRITE.
                shContentAclService.requirePublish(postId);
                shPostUnifiedWriteService.publish(postId, actor);
                done.add(postId);
            } catch (RuntimeException e) {
                skipped.add(new ShReviewOutcome.Skipped(postId, reason(e)));
            }
        }
        return new ShReviewOutcome(session, "approve", done.size(), List.copyOf(done),
                List.copyOf(skipped));
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
        List<ShHistory> entries = new ArrayList<>(shHistoryRepository.findByAgentSession(session));
        entries.sort((left, right) -> right.getDate().compareTo(left.getDate()));
        List<String> done = new ArrayList<>();
        List<ShReviewOutcome.Skipped> skipped = new ArrayList<>();
        Set<String> handled = new LinkedHashSet<>();
        for (ShHistory entry : entries) {
            String postId = entry.getShObject();
            if (postId == null || !handled.add(postId)) {
                continue;
            }
            try {
                revertOne(entry, postId, actor)
                        .ifPresentOrElse(reason -> skipped.add(
                                        new ShReviewOutcome.Skipped(postId, reason)),
                                () -> done.add(postId));
            } catch (RuntimeException e) {
                skipped.add(new ShReviewOutcome.Skipped(postId, reason(e)));
            }
        }
        return new ShReviewOutcome(session, "revert", done.size(), List.copyOf(done),
                List.copyOf(skipped));
    }

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
    private ShReviewSession summarize(String session, List<ShHistory> entries,
                                      Set<String> unreleased) {
        Map<String, Integer> byAction = new LinkedHashMap<>();
        Set<String> sites = new LinkedHashSet<>();
        Instant first = null;
        Instant last = null;
        // A Set, because the unit is the decision and a page edited four times is one decision.
        Set<String> waiting = new LinkedHashSet<>();
        for (ShHistory entry : entries) {
            byAction.merge(entry.getAction() == null ? "?" : entry.getAction(), 1, Integer::sum);
            if (entry.getShSite() != null) {
                sites.add(entry.getShSite());
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
                ShStableMap.copyOf(byAction), first, last, List.copyOf(sites), pending);
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
            default -> Optional.of("no undo for a folder " + action.name().toLowerCase());
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
