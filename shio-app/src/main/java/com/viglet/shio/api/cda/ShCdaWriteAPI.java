/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.cda;

import java.security.Principal;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.api.cda.dto.ShCdaEditablePost;
import com.viglet.shio.api.cda.dto.ShCdaPatch;
import com.viglet.shio.api.cda.dto.ShCdaPost;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Content Delivery API (CDA) write surface (SH10) — the save-back endpoints of
 * the Universal Editor bridge, living in the same {@code /api/v2/cda} namespace
 * as the SH1 read contract. Distinct from the read controller because these
 * mutate: they are gated to {@code ROLE_CDA_WRITE} (a {@code WRITE}-scope token)
 * in {@code ShSecurityConfig}, and CSRF-exempt because token auth is stateless
 * (the {@code Key} header, not a session cookie).
 *
 * <p>Edits target the {@code DRAFT} state; {@code /publish} promotes it.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@RestController
@RequestMapping("/api/v2/cda")
@Tag(name = "CDA Write", description = "Content Delivery API — Universal Editor save-back (WRITE scope)")
public class ShCdaWriteAPI {

    private final ShCdaWriteService shCdaWriteService;

    public ShCdaWriteAPI(ShCdaWriteService shCdaWriteService) {
        this.shCdaWriteService = shCdaWriteService;
    }

    @GetMapping("/post/{id}/editable")
    @Operation(summary = "Get a post's draft values plus its post-type field schema")
    public ResponseEntity<ShCdaEditablePost> editable(@PathVariable String id) {
        return ResponseEntity.of(shCdaWriteService.getEditable(id));
    }

    @PatchMapping("/post/{id}")
    @Operation(summary = "Merge attribute changes into a post's draft (optionally publish)")
    public ResponseEntity<ShCdaPost> patch(@PathVariable String id,
                                           @RequestBody ShCdaPatch body,
                                           Principal principal) {
        return ResponseEntity.of(shCdaWriteService.patch(id, body.attrs(), body.publish(), actor(principal)));
    }

    @PostMapping("/post/{id}/publish")
    @Operation(summary = "Promote a post's draft to published")
    public ResponseEntity<ShCdaPost> publish(@PathVariable String id, Principal principal) {
        return ResponseEntity.of(shCdaWriteService.publish(id, actor(principal)));
    }

    private static String actor(Principal principal) {
        return principal != null ? principal.getName() : null;
    }
}
