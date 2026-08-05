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
import java.util.Optional;

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

import com.viglet.shio.api.site.dto.ShSiteRequest;
import com.viglet.shio.exchange.ShExchangeException;
import com.viglet.shio.exchange.site.ShSiteExportService;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;
import com.viglet.shio.tenant.ShTenantQuotaService;

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
    private final ShTenantQuotaService shTenantQuotaService;

    public ShSiteAPI(ShSiteRepository shSiteRepository, ShSiteWriteService shSiteWriteService,
                     ShSiteExportService shSiteExportService,
                     ShTenantQuotaService shTenantQuotaService) {
        this.shSiteRepository = shSiteRepository;
        this.shSiteWriteService = shSiteWriteService;
        this.shSiteExportService = shSiteExportService;
        this.shTenantQuotaService = shTenantQuotaService;
    }

    @GetMapping
    public List<ShSite> list() {
        return shSiteRepository.findAll();
    }

    /**
     * One site, by id — <b>or by its furl or name</b> (SH181).
     *
     * <p>The fallback exists because the console asked this id-keyed endpoint for
     * {@code /api/v2/site/cafeteria} — a site's <em>furl</em> — and got a pair of 404s
     * behind a spinner, which is the third instance of one confusion: SH117 settled
     * id-versus-name for post-types, this is the same mistake for sites. A read that can
     * answer either key cannot be asked the wrong one, and the alternative — hunting the
     * swap in the caller — fixes one call site and leaves the class open.</p>
     *
     * <p><b>The reads get this, the writes do not.</b> {@code PUT}, {@code DELETE} and
     * {@code /export} stay strictly id-keyed: a read that resolved a near-miss shows the
     * curator the wrong site, while a write that did would change it.</p>
     */
    @GetMapping("/{id}")
    public ResponseEntity<ShSite> get(@PathVariable String id) {
        return resolve(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** id → furl → name, in that order: the id is the declared key and must win a collision. */
    private Optional<ShSite> resolve(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return shSiteRepository.findById(key)
                .or(() -> Optional.ofNullable(shSiteRepository.findByFurl(key)))
                .or(() -> Optional.ofNullable(shSiteRepository.findByName(key)));
    }

    @PostMapping
    public ResponseEntity<ShSite> create(@RequestBody ShSiteRequest payload, Principal principal) {
        if (payload.name() == null || payload.name().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (shSiteRepository.findByName(payload.name()) != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        // SH22/D — enforce the tenant's plan site quota (402); no-op single-tenant.
        shTenantQuotaService.checkCanCreateSite();
        ShSite created = shSiteWriteService.create(toEntity(payload),
                principal != null ? principal.getName() : null);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ShSite> update(@PathVariable String id, @RequestBody ShSiteRequest payload,
                                         Principal principal) {
        return shSiteWriteService.update(id, toEntity(payload), principal != null ? principal.getName() : null)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Map the request DTO onto a transient {@link ShSite} carrier (SonarQube
     * java:S4684) so the existing {@link ShSiteWriteService} signatures stay
     * unchanged. Only the fields the write service reads are populated.
     */
    private static ShSite toEntity(ShSiteRequest payload) {
        ShSite site = new ShSite();
        site.setName(payload.name());
        site.setDescription(payload.description());
        site.setUrl(payload.url());
        site.setFurl(payload.furl());
        site.setPostTypeLayout(payload.postTypeLayout());
        site.setSearchablePostTypes(payload.searchablePostTypes());
        site.setFormSuccess(payload.formSuccess());
        return site;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, Principal principal) {
        // SH454 — the actor, because the site delete now goes down through the folder and post
        // writers and each of them records an audit entry. Without it the most destructive gesture
        // in the console is the one with no name against it.
        return shSiteWriteService.delete(id, principal != null ? principal.getName() : null)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    /**
     * Export a site as a portable exchange zip (SH69) — the console "Download"
     * action. 404 if the site does not exist.
     *
     * <p><b>And a failure the caller can fix teaches (SH349).</b> This used to catch nothing, so an
     * export failure was the body-less 500 SH189 removed from import. One of the exchange layer's
     * export-side kinds is the caller's — a post whose {@code data} is not JSON — and
     * {@link ShExportFailedException} answers that with a 409 naming the post. Everything else is the
     * instance's own IO and is rethrown unchanged, because {@code ShAgentErrorRecordingFilter}
     * already records it as an SH190 incident with frames.</p>
     */
    @GetMapping(value = "/{id}/export", produces = "application/zip")
    public ResponseEntity<byte[]> export(@PathVariable String id) {
        try {
            return shSiteExportService.exportSite(id)
                    .<ResponseEntity<byte[]>>map(archive -> ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                                    .filename(archive.fileName()).build().toString())
                            .contentType(MediaType.parseMediaType("application/zip"))
                            .body(archive.content()))
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (ShExchangeException e) {
            throw ShExportFailedException.forCaller(e).map(RuntimeException.class::cast).orElse(e);
        }
    }
}
