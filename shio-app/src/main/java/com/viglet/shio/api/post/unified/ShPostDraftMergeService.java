/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.unified;

import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.UpdateRequest;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * SH335 — merging a few fields into a post's draft, once, for both surfaces that need it.
 *
 * <h2>Why this exists</h2>
 *
 * <p>The Universal Editor edits two or three fields of a page, which is a <b>merge</b>. The console
 * write surface offers whole state ({@code PUT} a complete {@code data} map, SH168's rule that an
 * upsert declares everything), so the editor was pointed at the CDA's {@code PATCH} instead — and
 * that is <b>P10 read backwards</b>: a console feature reaching its own content through a third
 * contract. It made the editor unusable on a fresh instance (no {@code WRITE} token exists yet),
 * parked the most dangerous credential in {@code localStorage} to do something the session cookie
 * already authorized, and carried publish authority nothing gates.</p>
 *
 * <p>The two candidate fixes were a client read-modify-write and a merge verb. The first is cheaper
 * and reintroduces a lost update the {@code PATCH} does not have — in a product whose whole point is
 * several curators on one site, that is the wrong thing to trade away. So: the merge verb, and this
 * is it.</p>
 *
 * <h2>What it deliberately does not do</h2>
 *
 * <p><b>No authorization.</b> The merge is the same operation from either surface; the
 * <em>permission</em> is not, and collapsing them is how one caller ends up borrowing the other's
 * guard. The CDA checks its SH2 token allow-list, the console checks the SH176 content ACL, and both
 * do it before calling here. One merge, two guards — which is what P10 asks for and what the fork
 * was avoiding.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
@Transactional
public class ShPostDraftMergeService {

    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShPostUnifiedWriteService shPostUnifiedWriteService;
    private final ObjectMapper objectMapper;

    public ShPostDraftMergeService(ShPostUnifiedRepository shPostUnifiedRepository,
                                   ShPostUnifiedWriteService shPostUnifiedWriteService,
                                   ObjectMapper objectMapper) {
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shPostUnifiedWriteService = shPostUnifiedWriteService;
        this.objectMapper = objectMapper;
    }

    /**
     * Merges {@code attrChanges} into the post's draft, optionally publishing it.
     *
     * @param postId      the post
     * @param attrChanges field name → new value; a field not named is left exactly as it was, which
     *                    is the whole difference from an update
     * @param publish     promote the resulting draft
     * @param actor       recorded as the modifier
     * @return the resulting row, or empty when there is no such post — the caller decides whether
     *         that is a 404 or the SH2 collapse
     */
    public Optional<ShPostUnified> merge(String postId, Map<String, JsonNode> attrChanges,
                                         boolean publish, String actor) {
        Optional<ShPostUnified> seed = editable(postId);
        if (seed.isEmpty()) {
            return Optional.empty();
        }
        ShPostUnified draft = ensureDraft(postId, seed.get());
        ShPostUnified updated = shPostUnifiedWriteService.update(postId,
                new UpdateRequest(mergeData(draft.getData(), attrChanges), ShPostState.DRAFT, actor));
        return Optional.of(publish
                ? shPostUnifiedWriteService.publish(postId, actor)
                : updated);
    }

    /** Draft row if present, else the published one — the editor edits the draft. */
    public Optional<ShPostUnified> editable(String postId) {
        Optional<ShPostUnified> draft =
                shPostUnifiedRepository.findByIdAndState(postId, ShPostState.DRAFT);
        return draft.isPresent()
                ? draft
                : shPostUnifiedRepository.findByIdAndState(postId, ShPostState.PUBLISHED);
    }

    /**
     * The existing draft, or one seeded from the published row.
     *
     * <p>An already-published post may carry no draft at all (a migrated row, for one), and it has
     * to become editable without disturbing the live snapshot somebody is serving.</p>
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

    /** The changes over the existing {@code data} object. Unreadable JSON starts a fresh object. */
    private JsonNode mergeData(String currentData, Map<String, JsonNode> attrChanges) {
        JsonNode parsed = currentData == null || currentData.isBlank() ? null : safeRead(currentData);
        ObjectNode node = parsed != null && parsed.isObject()
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
        } catch (RuntimeException unreadable) {
            // Same degradation the CDA had: a post whose `data` is not an object is edited as if it
            // were empty rather than refusing the write. SH270 records the render-time half.
            return null;
        }
    }
}
