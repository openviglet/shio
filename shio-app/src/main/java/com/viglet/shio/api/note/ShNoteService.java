/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.note;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.acl.ShContentAclService;
import com.viglet.shio.api.agent.ShAgentSessionContext;
import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.api.agent.ShAddress;
import com.viglet.shio.persistence.model.note.ShNote;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.note.ShNote.Kind;
import com.viglet.shio.persistence.repository.note.ShNoteRepository;

/**
 * The channel back from a curator to whoever writes the page (SH1003).
 *
 * <h2>Visibility is the object's, never its own</h2>
 *
 * <p>A note is exactly as visible as the thing it hangs on: every read and write here goes through
 * the SH54 content ACL for {@code objectId}. That is a deliberate refusal to invent a second
 * permission model — a note carries a curator's judgement about a page, and "who may see this
 * remark" has one honest answer, which is "whoever may see the page".</p>
 *
 * <h2>Resolving takes WRITE, not authorship</h2>
 *
 * <p>Marking a request done is a claim about the content, so it asks what changing the content asks
 * for. Tying it to the author instead would mean an agent could not close a request a curator made
 * of it, which is the one closing gesture this feature exists to enable.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
public class ShNoteService {

    /** A paragraph, not a document — the column's width, stated where the clip happens. */
    public static final int BODY_MAX = 4000;

    /** How many open notes a work list answers with when the caller names no bound. */
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 500;

    private final ShNoteRepository shNoteRepository;
    private final ShContentAclService shContentAclService;
    private final com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository
            shPostUnifiedRepository;
    private final com.viglet.shio.persistence.repository.folder.ShFolderRepository
            shFolderRepository;
    private final com.viglet.shio.api.agent.ShAddressResolver shAddressResolver;

    public ShNoteService(ShNoteRepository shNoteRepository,
                         ShContentAclService shContentAclService,
                         com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository
                                 shPostUnifiedRepository,
                         com.viglet.shio.persistence.repository.folder.ShFolderRepository
                                 shFolderRepository,
                         com.viglet.shio.api.agent.ShAddressResolver shAddressResolver) {
        this.shNoteRepository = shNoteRepository;
        this.shContentAclService = shContentAclService;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shFolderRepository = shFolderRepository;
        this.shAddressResolver = shAddressResolver;
    }

    /** A note nobody wrote anything in, which is the note missing. */
    public static class EmptyNoteException extends ShTeachingException {

        public EmptyNoteException(String objectId) {
            super(ShProblem.badRequest("note-needs-a-body", "a note on " + objectId + " needs a body")
                    .withDetail("A note is the correction itself: the whole point is that the next "
                            + "session reads what was wrong without anybody re-explaining it.")
                    .withFix("Say what should change, in a sentence whoever fixes the page can act "
                            + "on.")
                    .withExample("{\"op\":\"note.add\",\"address\":\"post:Blog/about\","
                            + "\"data\":{\"body\":\"the price table is last quarter's\"}}"));
        }
    }

    /** A kind this vocabulary does not have. */
    public static class UnknownKindException extends ShTeachingException {

        public UnknownKindException(String sent) {
            super(ShProblem.badRequest("unknown-note-kind", "no note kind called '" + sent + "'")
                    .withDetail("A comment is a remark, a request is something to act on, a reply "
                            + "is a message under another one, and reviewed says a field has been "
                            + "checked against the source it translates (SH1061). Assignment is a "
                            + "request, not a kind of its own.")
                    .withAllowed(List.of("comment", "request", "reply", "reviewed"))
                    .withDidYouMeanFrom(sent)
                    .withFix("Pass one of the three, or omit it for comment."));
        }
    }

    /**
     * Say something about an object.
     *
     * @param objectId  the post, folder or site — the note is as visible as it is
     * @param fieldPath the field, when the note is about one; {@code null} for the whole object
     * @param revisionNo the version, when the note is about one rather than the page now
     * @param parentId  the note this replies to; {@code null} on a root
     */
    @Transactional
    public ShNote add(String objectId, String fieldPath, Integer revisionNo, String parentId,
                      String kind, String body, String author) {
        // The content ACL, not a second one: "who may remark on this page" has one honest answer.
        shContentAclService.requireRead(objectId);
        if (body == null || body.isBlank()) {
            throw new EmptyNoteException(objectId);
        }
        ShNote note = new ShNote();
        note.setObjectId(objectId);
        note.setFieldPath(blankToNull(fieldPath));
        note.setRevisionNo(revisionNo);
        note.setParentId(blankToNull(parentId));
        note.setKind(kindOf(kind).name());
        String trimmed = body.trim();
        // Clipped rather than refused, for SH544's reason: the record describes the correction and
        // must never be what refuses it.
        note.setBody(trimmed.length() > BODY_MAX ? trimmed.substring(0, BODY_MAX) : trimmed);
        note.setAuthor(author);
        note.setAgentSessionId(ShAgentSessionContext.current().orElse(null));
        note.setCreatedAt(Instant.now());
        return shNoteRepository.save(note);
    }

    /**
     * Mark one done, or re-open it.
     *
     * <p>Asks for WRITE on the object rather than for authorship: closing a request is a claim about
     * the content, and tying it to whoever wrote the note would mean an agent could not close a
     * request a curator made of it — the one closing gesture this exists to enable.</p>
     */
    @Transactional
    public ShNote resolve(String noteId, boolean resolved, String actor) {
        ShNote note = shNoteRepository.findById(noteId)
                .orElseThrow(() -> new IllegalArgumentException("Note not found: " + noteId));
        shContentAclService.requireWrite(note.getObjectId());
        note.setResolved(resolved);
        note.setResolvedBy(resolved ? actor : null);
        note.setResolvedAt(resolved ? Instant.now() : null);
        return shNoteRepository.save(note);
    }

    /** One object's thread, oldest first, for a caller that may read the object. */
    @Transactional(readOnly = true)
    public List<ShNote> of(String objectId) {
        shContentAclService.requireRead(objectId);
        return shNoteRepository.findByObjectIdOrderByCreatedAtAsc(objectId);
    }

    /**
     * The open notes on each of these objects, in one query, keyed by object.
     *
     * <p>Filtered by what the caller may read rather than refused: this answers a <em>page</em> of a
     * listing, and one unreadable object among twenty is a row to leave out, not a reason to fail
     * the read.</p>
     */
    @Transactional(readOnly = true)
    public Map<String, List<ShNote>> openOn(Collection<String> objectIds) {
        Map<String, List<ShNote>> byObject = new LinkedHashMap<>();
        if (objectIds == null || objectIds.isEmpty()) {
            return byObject;
        }
        for (ShNote note : shNoteRepository
                .findByObjectIdInAndResolvedFalseOrderByCreatedAtAsc(objectIds)) {
            // SH1061 — a reviewed mark is not something being asked of this page, and this read is
            // what an agent consults before rewriting one. The marks are in the object's own note
            // list, which is where the translation workspace reads them.
            if (Kind.REVIEWED.name().equals(note.getKind())) {
                continue;
            }
            if (shContentAclService.canRead(note.getObjectId())) {
                byObject.computeIfAbsent(note.getObjectId(), id -> new java.util.ArrayList<>())
                        .add(note);
            }
        }
        return byObject;
    }

    /**
     * Everything still asking for something, newest first — the work list.
     *
     * <p>SH1061's {@code reviewed} marks are left out unless a caller asks for that kind by name.
     * A work list is what is still being asked of somebody, and "this field has been checked" is
     * the opposite of a request: counted in, a page translated carefully would report as the one
     * with the most outstanding work.</p>
     */
    @Transactional(readOnly = true)
    public List<ShNote> open(String kind, Integer limit) {
        String wanted = kind == null || kind.isBlank() ? null : kindOf(kind).name();
        int capped = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        return shNoteRepository.findOpen(wanted, PageRequest.of(0, capped)).stream()
                .filter(note -> shContentAclService.canRead(note.getObjectId()))
                .filter(note -> wanted != null || !ShNote.Kind.REVIEWED.name().equals(note.getKind()))
                .toList();
    }

    /**
     * The same list, each row carrying the address of the page it is about (SH1003).
     *
     * <p>The address is the actionable half — a note names a page somebody has to go and fix, and a
     * uuid names nothing anybody can act on. Resolved here rather than by the caller because the
     * alternative is a request per row, which is what a work list of forty would cost.</p>
     *
     * @param site scope to one site by name; {@code null} for every site this caller can read
     */
    @Transactional(readOnly = true)
    public List<OpenNote> openWork(String site, String kind, Integer limit) {
        List<OpenNote> work = new java.util.ArrayList<>();
        for (ShNote note : open(kind, limit)) {
            ShAddress address = addressOf(note.getObjectId());
            if (site != null && !site.isBlank()
                    && (address == null || !site.equalsIgnoreCase(address.site()))) {
                continue;
            }
            work.add(new OpenNote(note.getId(), note.getObjectId(),
                    address == null ? null : address.toString(),
                    address == null ? null : address.site(),
                    note.getFieldPath(), note.getRevisionNo(), note.getKind(), note.getBody(),
                    note.getAuthor(), note.getCreatedAt()));
        }
        return List.copyOf(work);
    }

    /**
     * One open note with its page named.
     *
     * @param address the page, or {@code null} when it is gone — which is itself the answer to what
     *                to do about the note
     */
    public record OpenNote(String id, String objectId, String address, String site, String field,
                           Integer revision, String kind, String body, String author,
                           Instant createdAt) {
    }

    /**
     * The address of the object a note hangs on, or {@code null} when it no longer resolves.
     *
     * <p>ShFolderSite-exempt: the resolver walks the folder chain itself.</p>
     */
    private ShAddress addressOf(String objectId) {
        return shPostUnifiedRepository.findByIdAndState(objectId, ShPostState.DRAFT)
                .or(() -> shPostUnifiedRepository.findByIdAndState(objectId, ShPostState.PUBLISHED))
                .map(shAddressResolver::addressOf)
                .orElseGet(() -> shFolderRepository.findAnyById(objectId)
                        .map(shAddressResolver::addressOf)
                        .orElse(null));
    }

    private static Kind kindOf(String sent) {
        if (sent == null || sent.isBlank()) {
            return Kind.COMMENT;
        }
        try {
            return Kind.valueOf(sent.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new UnknownKindException(sent);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
