/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.form;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.api.form.dto.ShSiteFormRequest;
import com.viglet.shio.persistence.model.form.ShSiteForm;
import com.viglet.shio.persistence.repository.form.ShSiteFormRepository;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/**
 * Where a site's form submissions land, and which post-types it invites (SH632).
 *
 * <p>Console-only, like {@code ShSiteWebhookAPI} which it mirrors: it falls under
 * {@code anyRequest().authenticated()}, is repository-direct and has no service layer. That is the
 * whole security posture of this surface and it is deliberate — <b>the operator configures the
 * destination and the public endpoint only reads it</b>. A `FORM` token (SH631) carries neither
 * {@code ROLE_CDA} nor {@code ROLE_USER}, so a credential sitting in a public page cannot reach any
 * mapping here, which is what makes "site configuration, never template content" true rather than
 * intended.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@RestController
@RequestMapping("/api/v2/site-form")
@Tag(name = "Site Form", description = "Where form submissions land, and which post-types a site invites")
@Slf4j
public class ShSiteFormAPI {

    private final ShSiteFormRepository shSiteFormRepository;

    public ShSiteFormAPI(ShSiteFormRepository shSiteFormRepository) {
        this.shSiteFormRepository = shSiteFormRepository;
    }

    @GetMapping
    public List<ShSiteForm> list() {
        return shSiteFormRepository.findAllByOrderBySiteIdAsc();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShSiteForm> get(@PathVariable String id) {
        return shSiteFormRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * One row per site.
     *
     * <p>A second row for a site it already has is refused rather than created: two configurations
     * for one site means the endpoint resolves to whichever sorts first, and an operator who edited
     * the wrong one would see their change have no effect. The refusal names the row to edit (P6).</p>
     */
    @PostMapping
    public ResponseEntity<Object> create(@RequestBody ShSiteFormRequest body, Principal principal) {
        if (body.siteId() == null || body.siteId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "title", "siteId is required",
                    "fix", "Name the site this configures — a form configuration belongs to one "
                            + "site, which is what keeps the destination out of template content."));
        }
        var existing = shSiteFormRepository.findFirstBySiteIdOrderByIdAsc(body.siteId());
        if (existing.isPresent()) {
            return ResponseEntity.status(409).body(Map.of(
                    "title", "This site already has a form configuration",
                    "fix", "Edit it instead: PUT /api/v2/site-form/" + existing.get().getId(),
                    "id", existing.get().getId()));
        }

        ShSiteForm form = new ShSiteForm();
        form.setSiteId(body.siteId());
        form.setFolderUrl(body.folderUrl());
        form.setAllowedTypes(body.allowedTypes());
        // Never defaulted to true, whatever the body says about the rest: creating the row is not
        // the same act as opening the door (P7).
        form.setEnabled(body.enabled());
        form.setOwner(principal != null ? principal.getName() : "system");
        form.setCreatedDate(Instant.now());

        shSiteFormRepository.saveAndFlush(form);
        log.info("Created form configuration for site '{}' -> folder '{}', types '{}', enabled={} by {}",
                form.getSiteId(), form.getFolderUrl(), form.getAllowedTypes(), form.isEnabled(),
                form.getOwner());
        return ResponseEntity.ok(form);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ShSiteForm> update(@PathVariable String id, @RequestBody ShSiteFormRequest body) {
        return shSiteFormRepository.findById(id)
                .map(existing -> {
                    // `siteId` is deliberately not updatable: moving a configuration to another site
                    // is creating that site's configuration, and doing it by edit would silently
                    // leave the first site accepting nothing.
                    existing.setFolderUrl(body.folderUrl());
                    existing.setAllowedTypes(body.allowedTypes());
                    existing.setEnabled(body.enabled());
                    shSiteFormRepository.saveAndFlush(existing);
                    return ResponseEntity.ok(existing);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Boolean>> delete(@PathVariable String id) {
        shSiteFormRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", true));
    }
}
