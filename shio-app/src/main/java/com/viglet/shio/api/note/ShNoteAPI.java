/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.note;

import java.security.Principal;
import java.time.Instant;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.viglet.shio.persistence.model.note.ShNote;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Notes a curator leaves on content (SH1003).
 *
 * <p>The console's half of the channel. The agent's half is the {@code note.add} and
 * {@code note.resolve} ops on {@code /agent/batch} — one vocabulary, two callers (P10), rather than
 * a second contract for the same rows. Nothing here is console-only: the ops reach every one of
 * these operations, which is what keeps this an API and not a screen.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@RestController
@RequestMapping("/api/v2/note")
@Tag(name = "Note", description = "Corrections anchored to a page, a field or a revision (SH1003)")
public class ShNoteAPI {

    private final ShNoteService shNoteService;

    public ShNoteAPI(ShNoteService shNoteService) {
        this.shNoteService = shNoteService;
    }

    /** One object's thread, oldest first — the order it was said in. */
    @GetMapping
    @Operation(summary = "The notes on one object, oldest first")
    public List<ShNoteResponse> of(@RequestParam(name = "object") String objectId) {
        return shNoteService.of(objectId).stream().map(ShNoteResponse::of).toList();
    }

    /** Everything still asking for something, newest first — the work list. */
    @GetMapping("/open")
    @Operation(summary = "Open notes, newest first")
    public List<ShNoteResponse> open(@RequestParam(name = "kind", required = false) String kind,
                                     @RequestParam(name = "limit", required = false) Integer limit) {
        return shNoteService.open(kind, limit).stream().map(ShNoteResponse::of).toList();
    }

    @PostMapping
    @Operation(summary = "Say something about a page, a field or a revision")
    public ShNoteResponse add(@RequestBody NotePayload payload, Principal principal) {
        return ShNoteResponse.of(shNoteService.add(payload.objectId(), payload.fieldPath(),
                payload.revisionNo(), payload.parentId(), payload.kind(), payload.body(),
                principal == null ? null : principal.getName()));
    }

    /** Close one, or re-open it with {@code resolved=false}. */
    @PostMapping("/{id}/resolve")
    @Operation(summary = "Mark a note done, or re-open it")
    public ShNoteResponse resolve(@PathVariable String id,
                                  @RequestParam(name = "resolved", required = false,
                                          defaultValue = "true") boolean resolved,
                                  Principal principal) {
        return ShNoteResponse.of(shNoteService.resolve(id, resolved,
                principal == null ? null : principal.getName()));
    }

    /**
     * What a caller sends to leave one.
     *
     * @param objectId   the post, folder or site
     * @param fieldPath  the field, when the note is about one
     * @param revisionNo the version, when it is about one
     * @param parentId   the note this replies to
     * @param kind       {@code comment}, {@code request} or {@code reply}; omitted means comment
     */
    public record NotePayload(String objectId, String fieldPath, Integer revisionNo,
                              String parentId, String kind, String body) {
    }

    /** Stable read DTO — never leaks the {@code tenant_id} discriminator. */
    @JsonInclude(Include.NON_NULL)
    public record ShNoteResponse(String id, String objectId, String fieldPath, Integer revisionNo,
                                 String parentId, String kind, String body, String author,
                                 String agentSession, boolean resolved, String resolvedBy,
                                 Instant resolvedAt, Instant createdAt) {

        static ShNoteResponse of(ShNote note) {
            return new ShNoteResponse(note.getId(), note.getObjectId(), note.getFieldPath(),
                    note.getRevisionNo(), note.getParentId(), note.getKind(), note.getBody(),
                    note.getAuthor(), note.getAgentSessionId(), note.isResolved(),
                    note.getResolvedBy(), note.getResolvedAt(), note.getCreatedAt());
        }
    }
}
