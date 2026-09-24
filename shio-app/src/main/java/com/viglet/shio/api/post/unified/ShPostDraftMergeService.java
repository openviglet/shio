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
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.UpdateRequest;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.api.reference.ShObjectReferenceService;
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
    /** SH1034 — the reverse index, maintained beside this class's own writes. */
    private final ShObjectReferenceService shObjectReferenceService;


    /**
     * The middle segment of an indexed field address (SH475).
     *
     * <p>Bounded at six digits so {@code Integer.parseInt} cannot overflow: no collection this
     * model holds has a millionth row, and an unbounded {@code \d+} would need the parse-and-catch
     * this pattern exists to avoid.</p>
     */
    private static final Pattern INDEX = Pattern.compile("\\d{1,6}");

    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShPostUnifiedWriteService shPostUnifiedWriteService;
    private final ObjectMapper objectMapper;
    /** SH1084 — the site's publish gates, evaluated before a merge that publishes. */
    private final com.viglet.shio.api.gate.ShPublishGateService shPublishGateService;

    public ShPostDraftMergeService(ShPostUnifiedRepository shPostUnifiedRepository,
                                   ShPostUnifiedWriteService shPostUnifiedWriteService,
                                   ObjectMapper objectMapper,
            ShObjectReferenceService shObjectReferenceService,
            com.viglet.shio.api.gate.ShPublishGateService shPublishGateService) {
        this.shObjectReferenceService = shObjectReferenceService;
        this.shPublishGateService = shPublishGateService;
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
        return merge(postId, attrChanges, publish, actor, null);
    }

    /**
     * As {@link #merge(String, Map, boolean, String)}, with a waiver for a failing publish gate.
     *
     * <p>SH1084 — a publish through the merge evaluates the site's gates like every other publish,
     * after the fields are merged, so the check judges what is about to go live. A gate that stops
     * it rolls the merge back with it: the call was save-and-publish, and half of it landing would
     * be a draft the caller did not ask to leave. The gate is not authorization, which is why it is
     * here while the permission stays with each caller.</p>
     *
     * @param waiver SH998's reason for publishing past a failing gate, or {@code null}
     */
    public Optional<ShPostUnified> merge(String postId, Map<String, JsonNode> attrChanges,
                                         boolean publish, String actor, String waiver) {
        Optional<ShPostUnified> seed = editable(postId);
        if (seed.isEmpty()) {
            return Optional.empty();
        }
        ShPostUnified draft = ensureDraft(postId, seed.get());
        ShPostUnified updated = shPostUnifiedWriteService.update(postId,
                new UpdateRequest(mergeData(draft.getData(), attrChanges), ShPostState.DRAFT, actor));
        if (!publish) {
            return Optional.of(updated);
        }
        String waived = shPublishGateService.requirePost(postId, waiver);
        return Optional.of(shPostUnifiedWriteService.publish(postId, actor, waived));
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
        return shObjectReferenceService.reindexed(shPostUnifiedRepository.save(draft));
    }

    /** The changes over the existing {@code data} object. Unreadable JSON starts a fresh object. */
    private JsonNode mergeData(String currentData, Map<String, JsonNode> attrChanges) {
        JsonNode parsed = currentData == null || currentData.isBlank() ? null : safeRead(currentData);
        ObjectNode node = parsed != null && parsed.isObject()
                ? (ObjectNode) parsed
                : objectMapper.createObjectNode();
        if (attrChanges != null) {
            attrChanges.forEach((name, value) -> {
                if (!setRow(node, name, value)) {
                    node.set(name, value);
                }
            });
        }
        return node;
    }

    /**
     * SH475 — {@code features.0.title}, the one field address that is not a flat name.
     *
     * <h2>Why a path, and why exactly this one</h2>
     *
     * <p>An SH95 collection field is a {@code Relator} with <b>nested</b> fields: the value is an
     * inline array of row maps inside this post's {@code data}, not references to other posts. So a
     * row has no id, {@code sh-post} cannot address it, and SH465 left every row of every converted
     * collection unannotated — four of the seven section types are collection-shaped, which is most
     * of what a replica actually says. The address it needed is a path into the value the post
     * already holds.</p>
     *
     * <p><b>Three segments, and the middle one an integer.</b> Not a general JSON-pointer: a general
     * path can create structure, and creating structure is how a mistyped prop silently becomes a
     * shape the post-type does not declare — SH168's defect through a door SH168 does not watch.
     * This one <em>never</em> creates: the collection must already be an array on this post and the
     * index must already name a row of it. Anything else falls through to a flat key, so every prop
     * that worked before writes exactly as it did.</p>
     *
     * @return whether the name was an indexed path <em>and</em> resolved to an existing row
     */
    private boolean setRow(ObjectNode data, String name, JsonNode value) {
        if (name == null) {
            return false;
        }
        String[] parts = name.split("\\.");
        if (parts.length != 3 || parts[0].isEmpty() || parts[2].isEmpty()) {
            return false;
        }
        // Digits rather than a parse-and-catch: a silent catch is a degradation with no trace
        // (SH270), and "is this segment an index" is a question about the characters.
        if (!INDEX.matcher(parts[1]).matches()) {
            return false;
        }
        int index = Integer.parseInt(parts[1]);
        JsonNode collection = data.get(parts[0]);
        if (collection == null || !collection.isArray() || index >= collection.size()) {
            return false;
        }
        JsonNode row = collection.get(index);
        if (row == null || !row.isObject()) {
            return false;
        }
        ((ObjectNode) row).set(parts[2], value);
        return true;
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
