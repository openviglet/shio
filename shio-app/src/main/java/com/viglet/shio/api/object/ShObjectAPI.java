/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.object;

import java.security.Principal;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.api.acl.ShContentAclService;
import com.viglet.shio.api.cda.dto.ShCdaPath;
import com.viglet.shio.api.object.dto.ShConsoleListing;
import com.viglet.shio.api.acl.ShContentAclService.AclEntry;
import com.viglet.shio.persistence.model.acl.ShContentAcl;
import com.viglet.shio.persistence.model.object.ShObject;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Content-tree navigation and move/copy API, rebuilt for the unified post model
 * (the legacy {@code ShObjectAPI} was removed in the nuclear refactor). Backs the
 * React console's content browser: {@code /list}, {@code /path}, {@code /copyto},
 * {@code /moveto}.
 *
 * @author Alexandre Oliveira
 */
@RestController
@RequestMapping("/api/v2/object")
@Tag(name = "Object", description = "Content object navigation API")
public class ShObjectAPI {

    private final ShObjectNavigationService shObjectNavigationService;
    private final ShObjectWriteService shObjectWriteService;
    private final ShContentAclService shContentAclService;

    public ShObjectAPI(ShObjectNavigationService shObjectNavigationService,
                       ShObjectWriteService shObjectWriteService,
                       ShContentAclService shContentAclService) {
        this.shObjectNavigationService = shObjectNavigationService;
        this.shObjectWriteService = shObjectWriteService;
        this.shContentAclService = shContentAclService;
    }

    /**
     * An object's children — child folders whole, posts one page at a time (SH165).
     *
     * <p>It used to return every child of a folder, so a folder with ten thousand posts was
     * one query, one response and one render; the CDA listing has been paged since SH1 and this
     * one never was. The parameters are the CDA's plus the two the console needs and a delivery
     * API does not: {@code sort}, because the browser's own sort would otherwise sort a single
     * page, and {@code q}, because its search box would otherwise search one.</p>
     *
     * <p>An unknown {@code sort} is a teaching {@code 400} rather than a fallback to the
     * default — see {@link ShUnknownListingSortException}.</p>
     *
     * @param page zero-based page index (default {@code 0})
     * @param size posts per page (default {@code 50})
     * @param sort {@code title} (default), {@code date} or {@code modifiedDate}
     * @param q    a case-insensitive substring matched against folder names and post titles
     */
    @GetMapping("/{id}/list")
    public ResponseEntity<ShConsoleListing> list(@PathVariable String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String q) {
        shContentAclService.requireRead(id);
        return shObjectNavigationService.list(id, page, size, ShConsoleListingSort.of(sort), q)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/path")
    public ResponseEntity<ShCdaPath> path(@PathVariable String id) {
        shContentAclService.requireRead(id);
        return shObjectNavigationService.path(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Duplicates objects into {@code destId}.
     *
     * <h2>Both ends are authorized (SH201)</h2>
     *
     * <p>The destination needs WRITE — that is where the new content lands. The sources
     * need <b>READ</b>, because a copy is a read of them: without that check, write on any
     * single folder was enough to lift content out of a subtree the caller was never
     * granted read on, and the copy is then theirs to keep.</p>
     */
    @PutMapping("/copyto/{destId}")
    public List<ShObject> copyTo(@PathVariable String destId, @RequestBody List<String> ids) {
        shContentAclService.requireWrite(destId);
        requireEach(ids, shContentAclService::requireRead);
        return shObjectWriteService.copyTo(destId, ids);
    }

    /**
     * Relocates objects. The mover is threaded through because a move is now an
     * audited content change rather than a silent re-parent (SH139).
     *
     * <h2>Both ends are authorized (SH201)</h2>
     *
     * <p>A move needs WRITE on the <b>sources</b> as well as the destination: from the
     * owner's point of view a move out of their subtree is a removal, and this endpoint
     * used to check only where the content was going. The agent path has checked both ends
     * since SH134/SH137, so this was the console being the looser of the two surfaces —
     * Block L's invariant read from the other end. The rule to keep is symmetrical: the two
     * surfaces enforce the same ACL, and whichever is stricter is the correct one.</p>
     */
    @PutMapping("/moveto/{destId}")
    public List<ShObject> moveTo(@PathVariable String destId, @RequestBody List<String> ids,
                                 Principal principal) {
        shContentAclService.requireWrite(destId);
        requireEach(ids, shContentAclService::requireWrite);
        return shObjectWriteService.moveTo(destId, ids,
                principal != null ? principal.getName() : null);
    }

    /**
     * Applies a permission check to every id in the request body <em>before</em> the write
     * service runs, so a partially-authorized batch moves nothing at all rather than
     * relocating the ids that happened to be listed first.
     */
    private static void requireEach(List<String> ids, java.util.function.Consumer<String> check) {
        if (ids == null) {
            return;
        }
        ids.stream().filter(java.util.Objects::nonNull).forEach(check);
    }

    /** The explicit ACL grants set directly on an object (SH54); inherited grants are not expanded. */
    @GetMapping("/{id}/acl")
    public ResponseEntity<List<ShContentAcl>> getAcl(@PathVariable String id) {
        shContentAclService.requireRead(id);
        return ResponseEntity.ok(shContentAclService.getAcl(id));
    }

    /** Replace an object's ACL grants (SH54). Editing requires WRITE on the object (admins always pass). */
    @PutMapping("/{id}/acl")
    public ResponseEntity<List<ShContentAcl>> putAcl(@PathVariable String id, @RequestBody List<AclEntry> entries) {
        shContentAclService.requireWrite(id);
        return ResponseEntity.ok(shContentAclService.replaceAcl(id, entries));
    }

    /**
     * Cache invalidation hook. The page-cache subsystem the legacy endpoint drove
     * is not part of the current backend, so this is a no-op kept for the console's
     * "clear cache" action (it expects a 200).
     */
    @GetMapping("/{id}/clear-cache")
    public ResponseEntity<Void> clearCache(@PathVariable String id) {
        return ResponseEntity.ok().build();
    }
}
