/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.folder;

import java.security.Principal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.api.folder.dto.ShFolderRequest;
import com.viglet.shio.api.object.ShObjectNavigationService;
import com.viglet.shio.api.object.ShObjectNavigationService.ShObjectPath;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Console folder management API, rebuilt for the unified post model. Mirrors the
 * {@code ShSiteAPI} pattern (thin controller + {@link ShFolderWriteService}) and
 * backs the React console's {@code ShFolderService} ({@code /v2/folder} CRUD).
 *
 * @author Alexandre Oliveira
 */
@RestController
@RequestMapping("/api/v2/folder")
@Tag(name = "Folder", description = "Folder management API")
public class ShFolderAPI {

    private final ShFolderRepository shFolderRepository;
    private final ShFolderWriteService shFolderWriteService;
    private final ShObjectNavigationService shObjectNavigationService;

    public ShFolderAPI(ShFolderRepository shFolderRepository, ShFolderWriteService shFolderWriteService,
                       ShObjectNavigationService shObjectNavigationService) {
        this.shFolderRepository = shFolderRepository;
        this.shFolderWriteService = shFolderWriteService;
        this.shObjectNavigationService = shObjectNavigationService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShFolder> get(@PathVariable String id) {
        return shFolderRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{parentFolderId}")
    public ResponseEntity<ShFolder> create(@PathVariable String parentFolderId, @RequestBody ShFolderRequest payload,
                                           Principal principal) {
        if (payload.name() == null || payload.name().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return shFolderWriteService.create(parentFolderId, toEntity(payload),
                        principal != null ? principal.getName() : null)
                .map(folder -> ResponseEntity.status(HttpStatus.CREATED).body(folder))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ShFolder> update(@PathVariable String id, @RequestBody ShFolderRequest payload,
                                           Principal principal) {
        return shFolderWriteService.update(id, toEntity(payload), principal != null ? principal.getName() : null)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Map the request DTO onto a transient {@link ShFolder} carrier (SonarQube
     * java:S4684) so the existing {@link ShFolderWriteService} signatures stay
     * unchanged. The write service reads only the folder name off it.
     */
    private static ShFolder toEntity(ShFolderRequest payload) {
        ShFolder folder = new ShFolder();
        folder.setName(payload.name());
        return folder;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        return shFolderWriteService.delete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @GetMapping("/{id}/path")
    public ResponseEntity<ShObjectPath> path(@PathVariable String id) {
        return shObjectNavigationService.path(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
