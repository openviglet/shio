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

    @GetMapping("/{id}/list")
    public ResponseEntity<ShConsoleListing> list(@PathVariable String id) {
        shContentAclService.requireRead(id);
        return shObjectNavigationService.list(id)
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

    @PutMapping("/copyto/{destId}")
    public List<ShObject> copyTo(@PathVariable String destId, @RequestBody List<String> ids) {
        shContentAclService.requireWrite(destId);
        return shObjectWriteService.copyTo(destId, ids);
    }

    /**
     * Relocates objects. The mover is threaded through because a move is now an
     * audited content change rather than a silent re-parent (SH139).
     */
    @PutMapping("/moveto/{destId}")
    public List<ShObject> moveTo(@PathVariable String destId, @RequestBody List<String> ids,
                                 Principal principal) {
        shContentAclService.requireWrite(destId);
        return shObjectWriteService.moveTo(destId, ids,
                principal != null ? principal.getName() : null);
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
