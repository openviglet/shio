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
 * {@code POST /api/v2/import}).
 *
 * <h2>Every failure teaches (SH189)</h2>
 *
 * <p>This used to answer {@code ResponseEntity.badRequest().build()} for all of them, so a caller
 * could not tell a manifest-schema mismatch from a bad zip from a package with no site in it —
 * while {@code ShExchangeException} knew exactly which. Bringing the 2018
 * {@code openviglet/bootstrap-site} package forward meant reading the {@code ShExchange*} records
 * in Java to infer seven field renames as a result. See {@link ShImportFailedException}.</p>
 *
 * <h2>Reachable by a stateless caller (SH189/SH167)</h2>
 *
 * <p>CSRF is narrowed with {@code ShStatelessRequestMatcher} rather than left blanket: exempt for a
 * caller holding no session cookie, checked for one that does. That is what lets a credential-based
 * client — the CLI, a CI job — POST a package with HTTP Basic while the console's cookie-bearing
 * form stays protected, and it is the same treatment SH167 gave the post-type, review, CDA and
 * agent paths. Law P1's remaining half for this capability is a command and an MCP route, which is
 * <b>SH268</b>: an exchange package is a file, and a client with no filesystem cannot produce one.</p>
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
            throw ShImportFailedException.noFile();
        }
        try (InputStream in = file.getInputStream()) {
            ShSite site = shSiteImportService.importPackage(in,
                    principal != null ? principal.getName() : null);
            return ResponseEntity.status(HttpStatus.CREATED).body(site);
        } catch (IOException | ShExchangeException e) {
            // Classified rather than flattened: the four causes need four different next actions,
            // and the cause's own message is the part that names the offending field.
            throw ShImportFailedException.of(e, file.getOriginalFilename());
        }
    }
}
