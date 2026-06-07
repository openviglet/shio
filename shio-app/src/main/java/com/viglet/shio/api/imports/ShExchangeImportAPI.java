/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.imports;

import java.io.IOException;
import java.io.InputStream;
import java.security.Principal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.viglet.shio.exchange.ShExchangeException;
import com.viglet.shio.exchange.site.ShSiteImportService;
import com.viglet.shio.persistence.model.site.ShSite;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Content import API (SH70): accepts an exchange package zip (SH68) and clones it
 * into the install via {@link ShSiteImportService}, returning the created site.
 * Backs the console "Import" action ({@code ShSiteService.import} → multipart
 * {@code POST /api/v2/import}). Session-authenticated and CSRF-protected like the
 * rest of the console API.
 *
 * @author Alexandre Oliveira
 */
@RestController
@RequestMapping("/api/v2/import")
@Tag(name = "Import", description = "Content exchange import API")
public class ShExchangeImportAPI {

    private final ShSiteImportService shSiteImportService;

    public ShExchangeImportAPI(ShSiteImportService shSiteImportService) {
        this.shSiteImportService = shSiteImportService;
    }

    @PostMapping
    public ResponseEntity<ShSite> importPackage(@RequestParam("file") MultipartFile file,
                                                Principal principal) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        try (InputStream in = file.getInputStream()) {
            ShSite site = shSiteImportService.importPackage(in,
                    principal != null ? principal.getName() : null);
            return ResponseEntity.status(HttpStatus.CREATED).body(site);
        } catch (IOException | ShExchangeException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
