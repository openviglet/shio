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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.api.site.dto.ShSiteRequest;
import com.viglet.shio.exchange.ShExchangeException;
import com.viglet.shio.exchange.site.ShSiteExportService;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;
import com.viglet.shio.tenant.ShTenantQuotaService;

import io.swagger.v3.oas.annotations.tags.Tag;
import tools.jackson.databind.JsonNode;

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
    /** SH1056 — which languages a site is in, the one resolver every surface reads. */
    private final ShSiteLocaleService shSiteLocaleService;
    /** SH1060 — what it has in each of them, and what it has not. */
    private final ShSiteCoverageService shSiteCoverageService;
    private final ShSiteListingService shSiteListingService;

    public ShSiteAPI(ShSiteRepository shSiteRepository, ShSiteWriteService shSiteWriteService,
                     ShSiteExportService shSiteExportService,
                     ShTenantQuotaService shTenantQuotaService,
                     ShSiteLocaleService shSiteLocaleService,
                     ShSiteCoverageService shSiteCoverageService,
                     ShSiteListingService shSiteListingService) {
        this.shSiteListingService = shSiteListingService;
        this.shSiteRepository = shSiteRepository;
        this.shSiteWriteService = shSiteWriteService;
        this.shSiteExportService = shSiteExportService;
        this.shTenantQuotaService = shTenantQuotaService;
        this.shSiteLocaleService = shSiteLocaleService;
        this.shSiteCoverageService = shSiteCoverageService;
    }

    /**
     * Every site, each with its pages by state and its last change (SH1217) — so a row of the
     * console's list can say whether a site is alive without a read of its own.
     */
    @GetMapping
    public List<ShSiteListing> list() {
        return shSiteListingService.list();
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
        // SH648 — the collision a soft-delete introduced, and the one nothing else can explain.
        // `ShSite.name` is unique and a trashed site keeps holding its name, so without this the
        // create passes every service check and comes back as a body-less constraint violation
        // naming a site the caller cannot find in any listing.
        requireNameFree(payload.name(), "name");
        requireNameFree(payload.furl(), "friendly URL");
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
     * SH1056 — the languages a site is in, as the console shows and edits them.
     *
     * <p>The author's view: an undeclared site reports the locales its content is written in,
     * drafts included, which is what the context pack reports too. The delivery API derives from
     * what its token can read instead (SH1123) — the difference is the reader, not the rule.</p>
     */
    @GetMapping("/{id}/locales")
    public ResponseEntity<ShSiteLocales> locales(@PathVariable String id) {
        return resolve(id)
                .map(site -> ResponseEntity.ok(shSiteLocaleService.of(site)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * SH1131 — what this site has in each of its languages, and what it has not (SH1060's read).
     *
     * <p>A row per page and a cell per language — {@code current}, {@code stale} (SH1058) or
     * {@code missing} — with the address to open in each, plus the per-language totals over the
     * whole site rather than over the page in hand. That is the work list a Languages board draws
     * and the one an agent can act on; {@code missingLocale} on {@code /agent/find} answers the
     * same question one language at a time, and this answers every language at once.</p>
     *
     * <p>Paged, because a site's pages are its content and a board has to open on a large one. The
     * cost is two queries whatever the page: one projection of the site's rows and one read of the
     * sources' documents, and the second is skipped entirely on a site whose translations carry no
     * SH1058 stamp.</p>
     */
    @GetMapping("/{id}/coverage")
    public ResponseEntity<ShSiteCoverage> coverage(@PathVariable String id,
                                                   @RequestParam(name = "page", defaultValue = "0") int page,
                                                   @RequestParam(name = "size", defaultValue = "50") int size) {
        return resolve(id)
                .map(site -> ResponseEntity.ok(shSiteCoverageService.of(site, page, size)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * SH1056 — declares a site's locale policy, replacing any it had.
     *
     * <p>The body is the document {@code site.upsert} takes under {@code data.locales} —
     * {@code {"default":…,"enabled":[…],"fallback":{…}}} — and it is validated by the same method,
     * so a mis-cased {@code pt-BR} is the same teaching 400 on both doors. This one replaces where
     * the upsert only fills, which is the point of it: changing a decision somebody made is the
     * curator's gesture, and the upsert's refusal to make it names this page.</p>
     */
    @PutMapping("/{id}/locales")
    public ResponseEntity<ShSiteLocales> declareLocales(@PathVariable String id,
                                                        @RequestBody JsonNode policy,
                                                        Principal principal) {
        String json = shSiteLocaleService.declare(policy)
                .orElseThrow(ShSiteLocales.EmptyEnabledException::new);
        return shSiteWriteService.declareLocales(id, json, principal != null ? principal.getName() : null)
                .map(site -> ResponseEntity.ok(shSiteLocaleService.of(site)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * SH1056 — stops declaring a policy, so the site reports the locales its content is in again.
     *
     * <p>Answers with the policy the site reports <em>now</em>, because "cleared" is not an answer
     * a curator can check: what the switcher will offer after this is.</p>
     */
    @DeleteMapping("/{id}/locales")
    public ResponseEntity<ShSiteLocales> clearLocales(@PathVariable String id, Principal principal) {
        return shSiteWriteService.declareLocales(id, null, principal != null ? principal.getName() : null)
                .map(site -> ResponseEntity.ok(shSiteLocaleService.of(site)))
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

    /**
     * The name or furl is free, or the caller is told which trashed site is holding it (SH648).
     *
     * <p>Only a <em>trashed</em> holder reaches here — a live one was refused two lines above with
     * the 409 this endpoint has always answered. The two are separate on purpose: "there is already
     * a site called that" is a fact the caller can check, and "there is one you deleted" is not.</p>
     */
    private void requireNameFree(String value, String field) {
        shSiteWriteService.nameHeldByTrash(value).ifPresent(trashed -> {
            throw new ShSiteNameHeldByTrashException(value, field);
        });
    }

    /**
     * Puts the site and everything in it in the trash (SH648) — reversible via {@link #untrash}.
     *
     * <p>It used to purge, so the largest deletion in the product was the only one with no undo
     * while every level beneath it had one. {@link #purge} is that gesture, now asked for by
     * name.</p>
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, Principal principal) {
        // SH454 — the actor, because the site delete now goes down through the folder and post
        // writers and each of them records an audit entry. Without it the most destructive gesture
        // in the console is the one with no name against it.
        return shSiteWriteService.delete(id, principal != null ? principal.getName() : null)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    /** SH648 — deleted sites currently in the trash, newest first. Mirrors {@code /folder/trash}. */
    @GetMapping("/trash")
    public List<ShSite> trash() {
        return shSiteWriteService.listTrash();
    }

    /**
     * SH648 — restore a trashed site: the site, its folders and the posts that went into the
     * trash with it. Mirrors {@code /folder/{id}/untrash}.
     */
    @PostMapping("/{id}/untrash")
    public ResponseEntity<Void> untrash(@PathVariable String id, Principal principal) {
        return shSiteWriteService.restore(id, principal != null ? principal.getName() : null)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    /**
     * SH648 — permanently delete a site (irreversible; this is what fires each post's
     * {@code DELETE} lifecycle event, so the SH12 webhook and the SH38 byte cleanup run here and
     * not on the reversible delete above).
     *
     * <p>Takes a live site as well as a trashed one, which is what keeps "remove this site and
     * everything in it" a single call for the callers that never wanted the trash — a teardown, a
     * disposable replication target (SH591). It trashes on the way through rather than refusing.</p>
     */
    @DeleteMapping("/{id}/purge")
    public ResponseEntity<Void> purge(@PathVariable String id, Principal principal) {
        return shSiteWriteService.purge(id, principal != null ? principal.getName() : null)
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
