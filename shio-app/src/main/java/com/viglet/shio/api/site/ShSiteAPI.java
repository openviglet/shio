/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.site;

import java.security.Principal;
import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
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
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.exchange.site.ShSiteExportService;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Console site management API. The CDA delivery counterpart is read-only
 * ({@code /api/v2/cda/site}); this authoring API backs the React console's
 * {@code ShSiteService} ({@code /v2/site} CRUD).
 *
 * @author Alexandre Oliveira
 */
@RestController
@RequestMapping("/api/v2/site")
@Tag(name = "Site", description = "Site management API")
public class ShSiteAPI {

    private final ShSiteRepository shSiteRepository;
    private final ShSiteWriteService shSiteWriteService;
    private final ShSiteExportService shSiteExportService;

    public ShSiteAPI(ShSiteRepository shSiteRepository, ShSiteWriteService shSiteWriteService,
                     ShSiteExportService shSiteExportService) {
        this.shSiteRepository = shSiteRepository;
        this.shSiteWriteService = shSiteWriteService;
        this.shSiteExportService = shSiteExportService;
    }

    @GetMapping
    public List<ShSite> list() {
        return shSiteRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShSite> get(@PathVariable String id) {
        return shSiteRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ShSite> create(@RequestBody ShSite payload, Principal principal) {
        if (payload.getName() == null || payload.getName().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (shSiteRepository.findByName(payload.getName()) != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        ShSite created = shSiteWriteService.create(payload, principal != null ? principal.getName() : null);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ShSite> update(@PathVariable String id, @RequestBody ShSite payload, Principal principal) {
        return shSiteWriteService.update(id, payload, principal != null ? principal.getName() : null)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        return shSiteWriteService.delete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    /**
     * Export a site as a portable exchange zip (SH69) — the console "Download"
     * action. 404 if the site does not exist.
     */
    @GetMapping(value = "/{id}/export", produces = "application/zip")
    public ResponseEntity<byte[]> export(@PathVariable String id) {
        return shSiteExportService.exportSite(id)
                .<ResponseEntity<byte[]>>map(archive -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                                .filename(archive.fileName()).build().toString())
                        .contentType(MediaType.parseMediaType("application/zip"))
                        .body(archive.content()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
