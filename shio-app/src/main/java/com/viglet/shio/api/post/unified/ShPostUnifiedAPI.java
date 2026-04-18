/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.unified;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Read-only preview API exposing the unified post model.
 *
 * <p>Kept deliberately minimal: the legacy {@code /api/v2/post} endpoints
 * remain the source of truth until the migration lands in production and
 * write paths are switched over.</p>
 */
@RestController
@RequestMapping("/api/v2/post-unified")
@Tag(name = "PostUnified", description = "Preview API for the unified post model")
public class ShPostUnifiedAPI {

    private final ShPostUnifiedRepository shPostUnifiedRepository;

    public ShPostUnifiedAPI(ShPostUnifiedRepository shPostUnifiedRepository) {
        this.shPostUnifiedRepository = shPostUnifiedRepository;
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShPostUnified> get(@PathVariable String id,
                                             @RequestParam(name = "state", defaultValue = "PUBLISHED") ShPostState state) {
        return shPostUnifiedRepository.findByIdAndState(id, state)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/versions")
    public List<ShPostUnified> versions(@PathVariable String id) {
        return shPostUnifiedRepository.findById(id);
    }
}
