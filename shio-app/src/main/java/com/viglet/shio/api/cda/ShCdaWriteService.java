/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.cda;

import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.cda.dto.ShCdaEditablePost;
import com.viglet.shio.api.cda.dto.ShCdaPost;
import com.viglet.shio.api.post.unified.ShPostDraftMergeService;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.spring.security.auth.ShApiTokenContext;

import tools.jackson.databind.JsonNode;

/**
 * Content Delivery API (CDA) write service (SH10) — the save-back half of the
 * Universal Editor bridge. Mutating requests reach this service only with a
 * {@code WRITE}-scope token ({@code ROLE_CDA_WRITE}), enforced in
 * {@code ShSecurityConfig} + {@code ShApiTokenFilter}.
 *
 * <p>Every edit lands on the post's {@link ShPostState#DRAFT} row so the live
 * site (the {@code PUBLISHED} snapshot) is untouched until an explicit publish —
 * a {@code PREVIEW}-env client (SH5) then renders the draft for live preview.
 * The actual row writes delegate to {@link ShPostUnifiedWriteService}; this
 * service only resolves the draft (seeding one from {@code PUBLISHED} when
 * needed), merges the attribute patch into the JSON {@code data} map, and
 * re-applies the SH2 per-site allow-list on the write path.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Service
@Transactional
public class ShCdaWriteService {

    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShPostUnifiedWriteService shPostUnifiedWriteService;
    private final ShCdaMapper mapper;
    // SH335 — the merge itself is not this surface's; only the allow-list that guards it is.
    private final ShPostDraftMergeService draftMergeService;
    /** SH995 — publishing from the editor is the same permission it is everywhere else. */
    private final com.viglet.shio.api.acl.ShContentAclService shContentAclService;

    public ShCdaWriteService(ShPostUnifiedRepository shPostUnifiedRepository,
                             ShPostUnifiedWriteService shPostUnifiedWriteService,
                             ShCdaMapper mapper,
                             ShPostDraftMergeService draftMergeService,
                             com.viglet.shio.api.acl.ShContentAclService shContentAclService) {
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shPostUnifiedWriteService = shPostUnifiedWriteService;
        this.mapper = mapper;
        this.draftMergeService = draftMergeService;
        this.shContentAclService = shContentAclService;
    }

    /**
     * The draft-preferred post plus its post-type field schema, for the editor
     * properties panel. Honours the SH2 per-site allow-list.
     */
    @Transactional(readOnly = true)
    public Optional<ShCdaEditablePost> getEditable(String postId) {
        return draftMergeService.editable(postId)
                .filter(post -> siteAllowed(siteIdOf(post)))
                .map(post -> new ShCdaEditablePost(
                        mapper.toPost(post),
                        mapper.toFields(post.getShPostType())));
    }

    /**
     * Merge an attribute patch into the post's draft and (optionally) publish it.
     * Returns the resulting draft (or published, when {@code publish}) post, or
     * {@link Optional#empty()} when the post does not exist or the token may not
     * write its site — the same 404/empty collapse the read side uses (SH2).
     */
    public Optional<ShCdaPost> patch(String postId, Map<String, JsonNode> attrChanges,
                                     boolean publish, String actor) {
        // SH335 — the allow-list first, then the shared merge. The check stays here because it is
        // this surface's; the merge left because it never was.
        boolean allowed = draftMergeService.editable(postId)
                .map(post -> siteAllowed(siteIdOf(post)))
                .orElse(false);
        if (!allowed) {
            return Optional.empty();
        }
        return draftMergeService.merge(postId, attrChanges, publish, actor).map(mapper::toPost);
    }

    /** Promote the post's draft to {@code PUBLISHED} (Universal Editor "publish"). */
    public Optional<ShCdaPost> publish(String postId, String actor) {
        // SH995 — the delivery API's write path publishes too, and asked for nothing above write.
        shContentAclService.requirePublish(postId);
        Optional<ShPostUnified> draft = shPostUnifiedRepository.findByIdAndState(postId, ShPostState.DRAFT)
                .filter(post -> siteAllowed(siteIdOf(post)));
        if (draft.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(mapper.toPost(shPostUnifiedWriteService.publish(postId, actor)));
    }

    // --- helpers ---

    /** SH2 per-site enforcement — mirrors {@code ShCdaService.siteAllowed}. */
    private boolean siteAllowed(String siteId) {
        return ShApiTokenContext.current()
                .map(context -> context.allowsSite(siteId))
                .orElse(true);
    }

    private static String siteIdOf(ShPostUnified post) {
        ShSite site = post != null ? post.getShSite() : null;
        return site != null ? site.getId() : null;
    }
}
