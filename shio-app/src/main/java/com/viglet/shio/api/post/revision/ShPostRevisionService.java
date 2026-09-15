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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.agent.ShAgentPostDigest;
import com.viglet.shio.api.agent.ShAgentSessionContext;
import com.viglet.shio.api.history.ShChangedFields;
import com.viglet.shio.persistence.model.post.revision.ShPostRevision;
import com.viglet.shio.persistence.model.post.revision.ShPostRevision.Reason;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.post.revision.ShPostRevisionRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ObjectMapper objectMapper;
    private final int keep;
    private final boolean captureOnSave;

    public ShPostRevisionService(ShPostRevisionRepository shPostRevisionRepository,
                                 ShPostUnifiedRepository shPostUnifiedRepository,
                                 ObjectMapper objectMapper,
                                 @Value("${shio.versions.keep:50}") int keep,
                                 @Value("${shio.versions.capture-on-save:false}") boolean captureOnSave) {
        this.shPostRevisionRepository = shPostRevisionRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.objectMapper = objectMapper;
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

    /**
     * The post's revisions as a caller reads them, newest first and carrying no bodies (SH987).
     *
     * <p>{@code changed} is computed pairwise here rather than stored: it is a fact about two
     * revisions, and storing it on one of them would be a derived value that goes wrong the moment
     * a prune removes its neighbour. The oldest held revision reports every field it has, which is
     * true — against nothing, everything is new.</p>
     */
    @Transactional(readOnly = true)
    public List<ShRevisionRow> rows(String postId) {
        List<ShPostRevision> held = shPostRevisionRepository.findByPostIdOrderByVersionNoDesc(postId);
        List<ShRevisionRow> rows = new ArrayList<>();
        for (int at = 0; at < held.size(); at++) {
            ShPostRevision previous = at + 1 < held.size() ? held.get(at + 1) : null;
            rows.add(row(held.get(at), previous).terse());
        }
        return List.copyOf(rows);
    }

    /** One revision, with its document, or empty when the number names nothing. */
    @Transactional(readOnly = true)
    public Optional<ShRevisionRow> row(String postId, int versionNo) {
        return shPostRevisionRepository.findByPostIdAndVersionNo(postId, versionNo)
                .map(revision -> row(revision, shPostRevisionRepository
                        .findByPostIdOrderByVersionNoDesc(postId).stream()
                        .filter(other -> other.getVersionNo() < versionNo)
                        .findFirst().orElse(null)));
    }

    private ShRevisionRow row(ShPostRevision revision, ShPostRevision previous) {
        return new ShRevisionRow(revision.getVersionNo(), revision.getState(), revision.getReason(),
                revision.getActor(), revision.getAgentSessionId(), revision.getCapturedAt(),
                revision.getTitle(), digestOf(revision), changedFields(previous, revision),
                parse(revision.getData()));
    }

    /**
     * What this revision's content would digest as <em>in the post's present place</em>.
     *
     * <p>{@link ShAgentPostDigest} is the one digest in the product (SH985 refused a second one), and
     * it hashes the folder and the translation group alongside the content — neither of which a
     * revision stores, because a revision is a record of what the post <b>said</b> and not of where
     * it lived. So the value is computed against the row that is there now, which makes it the
     * answer to the question a caller actually has: <i>if I rolled back to this, what would the post
     * digest as?</i> A post that has been moved between revisions therefore reports a digest that
     * differs from the one it had at the time, and that is the correct answer to that question.</p>
     */
    @Transactional(readOnly = true)
    public String digestOf(ShPostRevision revision) {
        ShPostUnified present = shPostUnifiedRepository
                .findByIdAndState(revision.getPostId(), ShPostState.DRAFT)
                .or(() -> shPostUnifiedRepository
                        .findByIdAndState(revision.getPostId(), ShPostState.PUBLISHED))
                .orElse(null);
        ShPostUnified as = new ShPostUnified();
        as.setId(revision.getPostId());
        as.setFurl(revision.getFurl());
        as.setLocale(revision.getLocale());
        as.setData(revision.getData());
        if (present != null) {
            as.setShFolder(present.getShFolder());
            as.setShPostType(present.getShPostType());
            as.setTranslationGroupId(present.getTranslationGroupId());
        }
        return ShAgentPostDigest.of(as);
    }

    /**
     * Field names this revision altered against the one before it, never their values.
     *
     * <p>SH989's audit rows ask the same question of a write, so both go through
     * {@link ShChangedFields#between} and cannot come to disagree about what counts as a change.</p>
     */
    private List<String> changedFields(ShPostRevision previous, ShPostRevision revision) {
        return ShChangedFields.between(previous == null ? null : parse(previous.getData()),
                parse(revision.getData()));
    }

    private JsonNode parse(String data) {
        if (data == null || data.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(data);
        } catch (RuntimeException e) {
            // A revision is a record, and a record that cannot be parsed is still a record: the
            // caller gets the row with no body rather than a 500 over one bad document.
            return null;
        }
    }

    /**
     * The post as it stands, saying what this revision said (SH990).
     *
     * <p>A <b>transient</b> copy, never saved: everything that is a fact about where the post lives —
     * its site, folder, type, address, locale and translation group — comes from {@code present}, and
     * the document and its title come from the revision. That split is SH987's for a read and it is
     * the same one for a render or a digest: a revision is a record of what the page said, so it is
     * shown at the address the page is at now, through the templates that render it now.</p>
     *
     * <p>The summary is left unset rather than carried from {@code present}: a revision does not store
     * one, and the present row's would describe a different document.</p>
     */
    public ShPostUnified asOf(ShPostRevision revision, ShPostUnified present) {
        ShPostUnified as = new ShPostUnified();
        as.setId(present.getId());
        as.setState(revision.getState() == null
                ? present.getState() : ShPostState.valueOf(revision.getState()));
        as.setShSite(present.getShSite());
        as.setShFolder(present.getShFolder());
        as.setShPostType(present.getShPostType());
        as.setFurl(present.getFurl());
        as.setLocale(present.getLocale());
        as.setTranslationGroupId(present.getTranslationGroupId());
        as.setOwner(present.getOwner());
        as.setDate(present.getDate());
        as.setModifiedDate(revision.getCapturedAt());
        as.setTitle(revision.getTitle());
        as.setData(revision.getData());
        return as;
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
