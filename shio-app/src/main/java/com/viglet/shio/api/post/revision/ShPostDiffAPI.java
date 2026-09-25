/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.revision;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.api.post.revision.ShPostDiffService.ShPostDiff;
import com.viglet.shio.api.acl.ShContentAclService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * SH988 — {@code GET /api/v2/post-unified/{id}/diff}: two versions of a page, and what changed.
 *
 * <p>Beside the revision endpoints rather than inside {@code ShPostUnifiedAPI}, under the same
 * prefix: it is the read that composes them, and it takes the same content ACL.</p>
 */
@RestController
@RequestMapping(value = "/api/v2/post-unified", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "PostUnified", description = "Unified post model API")
public class ShPostDiffAPI {

    private final ShPostDiffService shPostDiffService;
    private final ShContentAclService shContentAclService;

    public ShPostDiffAPI(ShPostDiffService shPostDiffService, ShContentAclService shContentAclService) {
        this.shPostDiffService = shPostDiffService;
        this.shContentAclService = shContentAclService;
    }

    @GetMapping("/{id}/diff")
    @Operation(summary = "Compare two versions of a post: draft, published or rev:<n>")
    public ShPostDiff diff(@PathVariable String id,
                           @RequestParam(name = "from", required = false) String from,
                           @RequestParam(name = "to", required = false) String to) {
        shContentAclService.requireRead(id);
        return shPostDiffService.diff(id, from, to);
    }
}
