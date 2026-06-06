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
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.UpdateRequest;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.spring.security.auth.ShApiTokenContext;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

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
    private final ObjectMapper objectMapper;

    public ShCdaWriteService(ShPostUnifiedRepository shPostUnifiedRepository,
                             ShPostUnifiedWriteService shPostUnifiedWriteService,
                             ShCdaMapper mapper,
                             ObjectMapper objectMapper) {
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shPostUnifiedWriteService = shPostUnifiedWriteService;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    /**
     * The draft-preferred post plus its post-type field schema, for the editor
     * properties panel. Honours the SH2 per-site allow-list.
     */
    @Transactional(readOnly = true)
    public Optional<ShCdaEditablePost> getEditable(String postId) {
        return resolveEditable(postId)
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
        Optional<ShPostUnified> seed = resolveEditable(postId)
                .filter(post -> siteAllowed(siteIdOf(post)));
        if (seed.isEmpty()) {
            return Optional.empty();
        }

        ShPostUnified draft = ensureDraft(postId, seed.get());
        JsonNode merged = mergeData(draft.getData(), attrChanges);

        ShPostUnified updated = shPostUnifiedWriteService.update(postId,
                new UpdateRequest(merged, ShPostState.DRAFT, actor));

        if (publish) {
            return Optional.of(mapper.toPost(shPostUnifiedWriteService.publish(postId, actor)));
        }
        return Optional.of(mapper.toPost(updated));
    }

    /** Promote the post's draft to {@code PUBLISHED} (Universal Editor "publish"). */
    public Optional<ShCdaPost> publish(String postId, String actor) {
        Optional<ShPostUnified> draft = shPostUnifiedRepository.findByIdAndState(postId, ShPostState.DRAFT)
                .filter(post -> siteAllowed(siteIdOf(post)));
        if (draft.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(mapper.toPost(shPostUnifiedWriteService.publish(postId, actor)));
    }

    // --- helpers ---

    /** Draft row if present, else the published row (the editor edits the draft). */
    private Optional<ShPostUnified> resolveEditable(String postId) {
        Optional<ShPostUnified> draft = shPostUnifiedRepository.findByIdAndState(postId, ShPostState.DRAFT);
        return draft.isPresent()
                ? draft
                : shPostUnifiedRepository.findByIdAndState(postId, ShPostState.PUBLISHED);
    }

    /**
     * Return the existing draft, or seed one by copying the published row so an
     * already-published post (which may carry no draft, e.g. after a migration)
     * becomes editable without disturbing its live snapshot.
     */
    private ShPostUnified ensureDraft(String postId, ShPostUnified resolved) {
        if (resolved.getState() == ShPostState.DRAFT) {
            return resolved;
        }
        ShPostUnified draft = new ShPostUnified();
        draft.setId(postId);
        draft.setState(ShPostState.DRAFT);
        draft.setShFolder(resolved.getShFolder());
        draft.setShSite(resolved.getShSite());
        draft.setShPostType(resolved.getShPostType());
        draft.setTitle(resolved.getTitle());
        draft.setSummary(resolved.getSummary());
        draft.setFurl(resolved.getFurl());
        draft.setOwner(resolved.getOwner());
        draft.setDate(resolved.getDate());
        draft.setData(resolved.getData());
        return shPostUnifiedRepository.save(draft);
    }

    /** Merge the attribute changes into the existing {@code data} JSON object. */
    private JsonNode mergeData(String currentData, Map<String, JsonNode> attrChanges) {
        ObjectNode node;
        JsonNode parsed = currentData == null || currentData.isBlank()
                ? null
                : safeRead(currentData);
        node = (parsed != null && parsed.isObject())
                ? (ObjectNode) parsed
                : objectMapper.createObjectNode();
        if (attrChanges != null) {
            attrChanges.forEach(node::set);
        }
        return node;
    }

    private JsonNode safeRead(String data) {
        try {
            return objectMapper.readTree(data);
        } catch (RuntimeException e) {
            return null;
        }
    }

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
