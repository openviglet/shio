/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.history;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.persistence.model.history.ShHistory;
import com.viglet.shio.persistence.repository.history.ShHistoryPageableRepository;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Read-only audit / activity API (SH51). Exposes the {@link ShHistory} trail
 * recorded by {@link ShContentAuditService} — who did what, when — that the
 * model has always persisted but never surfaced. Console-authenticated and
 * tenant-scoped (the {@code @TenantId} on {@code ShHistory} filters every read).
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@RestController
@RequestMapping("/api/v2/history")
@Tag(name = "History", description = "Content audit / activity trail (read-only)")
public class ShHistoryAPI {

    /** Hard cap so a malicious/large {@code limit} can never page the whole table. */
    private static final int MAX_LIMIT = 500;
    private static final int DEFAULT_LIMIT = 100;

    private final ShHistoryPageableRepository shHistoryPageableRepository;

    public ShHistoryAPI(ShHistoryPageableRepository shHistoryPageableRepository) {
        this.shHistoryPageableRepository = shHistoryPageableRepository;
    }

    /** Recent activity across all content in the current tenant, newest first. */
    @GetMapping
    public List<ShHistoryResponse> recent(@RequestParam(name = "limit", defaultValue = "100") int limit) {
        return shHistoryPageableRepository.findAllByOrderByDateDesc(page(limit))
                .stream().map(ShHistoryResponse::of).toList();
    }

    /** Activity for a single post (the {@code shObject} id), newest first. */
    @GetMapping("/object/{objectId}")
    public List<ShHistoryResponse> byObject(@PathVariable String objectId,
                                            @RequestParam(name = "limit", defaultValue = "100") int limit) {
        return shHistoryPageableRepository.findByShObjectOrderByDateDesc(objectId, page(limit))
                .stream().map(ShHistoryResponse::of).toList();
    }

    /** Activity for a single site, newest first. */
    @GetMapping("/site/{siteId}")
    public List<ShHistoryResponse> bySite(@PathVariable String siteId,
                                          @RequestParam(name = "limit", defaultValue = "100") int limit) {
        return shHistoryPageableRepository.findByShSiteOrderByDateDesc(siteId, page(limit))
                .stream().map(ShHistoryResponse::of).toList();
    }

    private static Pageable page(int limit) {
        int capped = Math.min(limit > 0 ? limit : DEFAULT_LIMIT, MAX_LIMIT);
        return PageRequest.of(0, capped);
    }

    /** Stable read DTO — never leaks the {@code tenant_id} discriminator. */
    public record ShHistoryResponse(String id, String action, String description,
                                    String owner, Instant date, String shObject, String shSite) {
        static ShHistoryResponse of(ShHistory h) {
            return new ShHistoryResponse(h.getId(), h.getAction(), h.getDescription(),
                    h.getOwner(), h.getDate(), h.getShObject(), h.getShSite());
        }
    }
}
