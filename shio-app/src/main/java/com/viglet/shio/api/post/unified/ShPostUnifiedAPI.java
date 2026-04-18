/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.unified;

import java.security.Principal;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.CreateRequest;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;

import tools.jackson.databind.JsonNode;

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
    private final ShFolderRepository shFolderRepository;
    private final ShPostUnifiedWriteService shPostUnifiedWriteService;

    public ShPostUnifiedAPI(ShPostUnifiedRepository shPostUnifiedRepository,
                            ShFolderRepository shFolderRepository,
                            ShPostUnifiedWriteService shPostUnifiedWriteService) {
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shFolderRepository = shFolderRepository;
        this.shPostUnifiedWriteService = shPostUnifiedWriteService;
    }

    @PostMapping
    public ShPostUnified create(@RequestBody CreatePayload payload, Principal principal) {
        return shPostUnifiedWriteService.create(new CreateRequest(
                payload.folderId(),
                payload.postTypeId(),
                payload.data(),
                payload.state() != null ? payload.state() : ShPostState.DRAFT,
                principal != null ? principal.getName() : null));
    }

    public record CreatePayload(String folderId, String postTypeId, JsonNode data, ShPostState state) {
    }

    @GetMapping("/folder/{folderId}")
    public List<ShPostUnified> listByFolder(@PathVariable String folderId,
                                            @RequestParam(name = "state", defaultValue = "PUBLISHED") ShPostState state) {
        return shFolderRepository.findById(folderId)
                .map(folder -> shPostUnifiedRepository.findByShFolderAndState(folder, state))
                .orElse(List.of());
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
