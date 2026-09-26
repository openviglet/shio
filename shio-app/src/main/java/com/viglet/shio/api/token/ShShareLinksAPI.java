/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.token;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.api.acl.ShContentAclService;
import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.token.ShApiToken;
import com.viglet.shio.persistence.model.token.ShApiTokenScope;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;
import com.viglet.shio.persistence.repository.token.ShApiTokenRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * SH1111 — the share links that are live, and stopping one.
 *
 * <p>A share link was minted, sent, and then visible nowhere: the token screen hides ephemeral rows
 * on purpose, and a share link is ephemeral by construction. Revocation already worked —
 * {@code enabled = false} is the first thing the filter checks — so what was missing was the
 * reading. This lists the live SHARE rows for sites the caller may read, each with its site, the
 * page it opens, when it expires and who made it; {@code DELETE} disables one.</p>
 *
 * <p>On the mint's route rather than the exchange's. SH1111's design put the listing at
 * {@code GET /api/v2/preview-share}, but that path is {@code permitAll} because the exchange is
 * anonymous, and carving an authenticated exception out of it would make one path answer to two
 * rules. The mint is already authenticated, and this is the same object seen from after.</p>
 *
 * <p>The secret never leaves: a listed link carries the row's id, which revokes it and opens
 * nothing. Tenant-scoped by the repository's derived query, and narrowed to readable sites by the
 * content ACL, so a curator sees the links on sites they could have minted one for.</p>
 */
@RestController
@RequestMapping("/api/v2/preview-token")
@Tag(name = "Preview", description = "Share links for Shio's own preview")
public class ShShareLinksAPI {

    private final ShApiTokenRepository shApiTokenRepository;
    private final ShSiteRepository shSiteRepository;
    private final ShContentAclService shContentAclService;
    private final Clock clock;

    public ShShareLinksAPI(ShApiTokenRepository shApiTokenRepository, ShSiteRepository shSiteRepository,
                           ShContentAclService shContentAclService, Clock clock) {
        this.shApiTokenRepository = shApiTokenRepository;
        this.shSiteRepository = shSiteRepository;
        this.shContentAclService = shContentAclService;
        this.clock = clock;
    }

    /**
     * One live share link.
     *
     * @param id      the row's id: what {@code DELETE} takes, and not the credential
     * @param page    the page it opens ({@code path_prefix}), or {@code null} for a site-wide link
     *                minted before SH1110
     */
    public record ShShareLink(String id, String site, String page, Instant expiresAt, String owner,
                              Instant created) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "The share links that are live, for sites the caller may read")
    public List<ShShareLink> live() {
        return shApiTokenRepository
                .findByScopeAndEnabledTrueAndExpiresAtAfterOrderByExpiresAtAsc(ShApiTokenScope.SHARE,
                        Instant.now(clock))
                .stream()
                .filter(row -> row.getSiteIds() != null && row.getSiteIds().size() == 1)
                .filter(row -> shContentAclService.canRead(row.getSiteIds().iterator().next()))
                .map(this::linkOf)
                .toList();
    }

    @DeleteMapping("/{id}")
    @Transactional
    @Operation(summary = "Stop a share link: it is refused at the preview from the next request")
    public ShShareLink revoke(@PathVariable String id) {
        ShApiToken row = shApiTokenRepository.findById(id)
                .filter(token -> token.getScope() == ShApiTokenScope.SHARE)
                .filter(token -> token.getSiteIds() != null && token.getSiteIds().size() == 1
                        && shContentAclService.canRead(token.getSiteIds().iterator().next()))
                .orElseThrow(() -> new UnknownShareLinkException(id));
        row.setEnabled(false);
        shApiTokenRepository.saveAndFlush(row);
        return linkOf(row);
    }

    private ShShareLink linkOf(ShApiToken row) {
        String siteId = row.getSiteIds().iterator().next();
        String site = shSiteRepository.findById(siteId).map(ShSite::getName).orElse(siteId);
        return new ShShareLink(row.getId(), site, row.getPathPrefix(), row.getExpiresAt(), row.getOwner(),
                row.getCreatedDate());
    }

    /** No share link by that id that this caller may see; the listing is where the ids come from. */
    public static class UnknownShareLinkException extends ShTeachingException {

        public UnknownShareLinkException(String id) {
            super(ShProblem.of("unknown-share-link", HttpStatus.NOT_FOUND,
                            "No share link '" + id + "' that you can see")
                    .withDetail("It expired, was already revoked, belongs to a site you cannot read, "
                            + "or the id is not one.")
                    .withFix("List the live ones and revoke by their id: shio share --list")
                    .withExample("GET /api/v2/preview-token"));
        }
    }
}
