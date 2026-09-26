/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.auth.view;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.api.auth.ShCurrentUsername;
import com.viglet.shio.api.auth.view.ShSavedViewService.Row;
import com.viglet.shio.api.auth.view.ShSavedViewService.Save;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * SH1024 — the signed-in curator's saved views, for the list whose filter bar offers them.
 *
 * <p>A console preference with no agent leg, like SH971's dock: an agent states its query every
 * time, and a name for one is a curator's convenience. It is classified that way in
 * {@code surfaces.json} rather than argued here.</p>
 */
@RestController
@RequestMapping(value = "/api/v2/user/current/views", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "User", description = "User API")
public class ShSavedViewAPI {

    private final ShSavedViewService service;

    public ShSavedViewAPI(ShSavedViewService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "One list's saved views the signed-in curator can open")
    public List<Row> list(@RequestParam String list) {
        return service.visible(ShCurrentUsername.get(), list);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Save the current view under a name, replacing one of the same name")
    public Row save(@RequestBody Save save) {
        return service.save(ShCurrentUsername.get(), save);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete one of the signed-in curator's saved views")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(ShCurrentUsername.get(), id);
        return ResponseEntity.noContent().build();
    }
}
