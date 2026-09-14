/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.revision;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.agent.ShAgentSessionContext;
import com.viglet.shio.persistence.model.post.revision.ShPostRevision;
import com.viglet.shio.persistence.model.post.revision.ShPostRevision.Reason;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.repository.post.revision.ShPostRevisionRepository;

/**
 * Every state a page was in, captured where it goes live (SH986).
 *
 * <h2>Where a capture happens, and why there</h2>
 *
 * <p>Inside the publishing transaction, before the live row is replaced. That placement is the
 * guarantee: a revision exists for <b>every state a visitor ever saw</b>, because the only way to
 * become visible is to go through that method, and a capture that rolled back took the publish with
 * it. Capturing after the write, or on a listener, would leave the one case that matters — a
 * publish that half-succeeded — with no record.</p>
 *
 * <p>Saving a draft is captured too, behind {@code shio.versions.capture-on-save}, and it is
 * <b>off</b> by default. A draft save happens on every keystroke-sized edit an agent makes; the
 * question a curator asks is "what was live on Tuesday", and answering it does not need the fifty
 * saves that produced it. An instance that wants the finer history turns it on and pays for it in
 * rows.</p>
 *
 * <h2>Retention</h2>
 *
 * <p>{@code shio.versions.keep} revisions per post, fifty by default, pruned in the same write that
 * adds one — so the bound holds without a scheduled job, and an instance that never runs
 * maintenance still has a bounded table. The prune is by {@code version_no} and never by age: a
 * page published once a year would otherwise lose the only revision it has.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
public class ShPostRevisionService {

    private final ShPostRevisionRepository shPostRevisionRepository;
    private final int keep;
    private final boolean captureOnSave;

    public ShPostRevisionService(ShPostRevisionRepository shPostRevisionRepository,
                                 @Value("${shio.versions.keep:50}") int keep,
                                 @Value("${shio.versions.capture-on-save:false}") boolean captureOnSave) {
        this.shPostRevisionRepository = shPostRevisionRepository;
        this.keep = keep;
        this.captureOnSave = captureOnSave;
    }

    /** Whether a draft save is captured on this instance. */
    public boolean capturesOnSave() {
        return captureOnSave;
    }

    /**
     * Capture one row as it stands, and prune the post's revisions to the retention.
     *
     * <p>Called inside the caller's transaction on purpose: the snapshot and the write it is about
     * are one act, and a snapshot that survives a rolled-back publish is a revision of something
     * that never happened.</p>
     *
     * @param post   the row being captured — its {@code data} is what is stored
     * @param reason why, which is what lets a reader ask for what was live rather than every save
     * @param actor  who, or {@code null} for the scheduler
     * @return the revision, or {@code null} when there is nothing to capture
     */
    @Transactional
    public ShPostRevision capture(ShPostUnified post, Reason reason, String actor) {
        if (post == null || post.getData() == null) {
            return null;
        }
        ShPostRevision revision = new ShPostRevision();
        revision.setPostId(post.getId());
        revision.setVersionNo(nextVersion(post.getId()));
        revision.setState(post.getState() == null ? null : post.getState().name());
        revision.setReason(reason.name());
        revision.setTitle(post.getTitle());
        revision.setFurl(post.getFurl());
        revision.setLocale(post.getLocale());
        // The id, never the name — see the entity, and the JPQL invariant in agents.md.
        revision.setPostTypeId(post.getShPostType() == null ? null : post.getShPostType().getId());
        revision.setData(post.getData());
        revision.setActor(actor);
        revision.setAgentSessionId(ShAgentSessionContext.current().orElse(null));
        revision.setCapturedAt(Instant.now());
        ShPostRevision saved = shPostRevisionRepository.save(revision);
        prune(post.getId());
        return saved;
    }

    /** The post's revisions, newest first. */
    @Transactional(readOnly = true)
    public List<ShPostRevision> revisions(String postId) {
        return shPostRevisionRepository.findByPostIdOrderByVersionNoDesc(postId);
    }

    /** One revision of one post, by the number a reader was shown. */
    @Transactional(readOnly = true)
    public Optional<ShPostRevision> revision(String postId, int versionNo) {
        return shPostRevisionRepository.findByPostIdAndVersionNo(postId, versionNo);
    }

    /**
     * The next number for this post.
     *
     * <p>{@code max + 1}, not {@code count + 1}: the prune deletes the oldest rows, so a count
     * reissues a number a deleted revision used and the unique constraint refuses the insert —
     * which presents as publishing having stopped working, long after and nowhere near the prune.</p>
     */
    private int nextVersion(String postId) {
        Integer highest = shPostRevisionRepository.maxVersionNo(postId);
        return highest == null ? 1 : highest + 1;
    }

    private void prune(String postId) {
        if (keep <= 0) {
            return;
        }
        List<ShPostRevision> held = shPostRevisionRepository.findByPostIdOrderByVersionNoDesc(postId);
        if (held.size() <= keep) {
            return;
        }
        shPostRevisionRepository.deleteAll(held.subList(keep, held.size()));
    }
}
