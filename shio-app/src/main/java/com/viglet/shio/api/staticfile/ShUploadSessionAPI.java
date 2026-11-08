/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.staticfile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.Principal;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

/**
 * SH1355 — the resumable upload, beside the single-request one.
 *
 * <pre>
 * POST   /api/v2/staticfile/upload-session              {name, size, folderId, sha256, contentType?} → 201 status
 * GET    /api/v2/staticfile/upload-session/{id}         → status (offset = where to resume)
 * PUT    /api/v2/staticfile/upload-session/{id}?offset=N  raw bytes → status
 * POST   /api/v2/staticfile/upload-session/{id}/finish  → the asset's media row, as /upload returns it
 * DELETE /api/v2/staticfile/upload-session/{id}         → 204
 * </pre>
 *
 * <p>A chunk is the raw request body, not a multipart part: servlet multipart parsing is specified
 * for {@code POST} only (the reason {@code /replace} is a POST), and a chunk carries no name worth a
 * part header. {@code POST /upload} stays for every caller that has it.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@RestController
@RequestMapping("/api/v2/staticfile/upload-session")
@Tag(name = "Static File", description = "File upload / serve API (SH38)")
public class ShUploadSessionAPI {

    private final ShUploadSessionService sessions;
    /** SH1364 — finish answers with the media library's row, as {@code /upload} does. */
    private final ShStaticFileService staticFiles;

    public ShUploadSessionAPI(ShUploadSessionService sessions, ShStaticFileService staticFiles) {
        this.sessions = sessions;
        this.staticFiles = staticFiles;
    }

    @PostMapping
    public ResponseEntity<ShUploadSessionService.Status> begin(@RequestBody ShUploadSessionService.Begin begin,
                                                               Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sessions.begin(begin, nameOf(principal)));
    }

    @GetMapping("/{id}")
    public ShUploadSessionService.Status status(@PathVariable String id, Principal principal) {
        return sessions.status(id, nameOf(principal));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.ALL_VALUE)
    public ShUploadSessionService.Status append(@PathVariable String id, @RequestParam("offset") long offset,
                                                HttpServletRequest request, Principal principal) {
        try {
            return sessions.append(id, offset, request.getInputStream(), nameOf(principal));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the chunk", e);
        }
    }

    @PostMapping("/{id}/finish")
    public ShStaticFileItem finish(@PathVariable String id, Principal principal) {
        return staticFiles.itemOf(sessions.finish(id, nameOf(principal)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> abort(@PathVariable String id, Principal principal) {
        sessions.abort(id, nameOf(principal));
        return ResponseEntity.noContent().build();
    }

    private static String nameOf(Principal principal) {
        return principal != null ? principal.getName() : null;
    }
}
