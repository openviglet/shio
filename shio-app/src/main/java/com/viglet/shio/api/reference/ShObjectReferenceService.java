/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.reference;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.reference.ShPostReferenceWalk.Edge;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.reference.ShObjectReference;
import com.viglet.shio.persistence.repository.reference.ShObjectReferenceRepository;
import com.viglet.shio.widget.ShSystemWidget;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.ObjectMapper;

/**
 * Keeps {@code sh_object_reference} equal to what the posts say (SH1034).
 *
 * <h2>The cost this is allowed to have, measured before it was written</h2>
 *
 * <p>SH1033 priced both sides. The read it replaces examines the whole table, twice per dialog —
 * 50,002 rows at fifty thousand posts. The maintenance measured <b>three statements per write</b>
 * with two references, identical at fifty thousand rows and at zero, because it is bounded by the
 * post's own declared fields and touches nothing else. That is the property every method here has
 * to keep: <b>nothing in this class may read another post.</b> A maintenance step that grows with
 * the table would trade a slow dialog for a slow write, which is the worse end of the bargain.</p>
 *
 * <h2>Rewrite, never patch</h2>
 *
 * <p>{@link #reindex} deletes the row's edges and writes the current ones, rather than diffing.
 * Diffing is the same number of statements in the common case and is wrong in the uncommon one: a
 * field cleared to empty produces no edge to compare against, so a patch would leave the old row
 * behind and the asset would read as used forever. Delete-then-insert is idempotent (P5), which
 * also makes a re-run of the backfill free of consequence.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
public class ShObjectReferenceService {

    private final ShObjectReferenceRepository shObjectReferenceRepository;
    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    public ShObjectReferenceService(ShObjectReferenceRepository shObjectReferenceRepository,
            ObjectMapper objectMapper) {
        this.shObjectReferenceRepository = shObjectReferenceRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Makes the index agree with this row, whatever it said before.
     *
     * <p>A trashed row is indexed as having no edges rather than skipped: a post in the trash does
     * not keep an asset alive, so an {@code asset-unused} answer that counted it would report the
     * opposite of the truth — and {@code restoreFromTrash} re-indexes it back.</p>
     */
    @Transactional
    public void reindex(ShPostUnified post) {
        if (post == null || post.getId() == null || post.getState() == null) {
            return;
        }
        shObjectReferenceRepository.deleteByFromPostIdAndFromState(post.getId(), post.getState());
        if (post.getDeletedDate() != null) {
            return;
        }
        for (Edge edge : edgesOf(post)) {
            shObjectReferenceRepository.save(new ShObjectReference(
                    post.getId(), post.getState(), edge.toObjectId(), edge.fieldName()));
        }
    }

    /**
     * {@link #reindex} for a caller that returns the row it just saved.
     *
     * <p>Exists so a one-line {@code return repository.save(x)} stays one line: rewriting it as
     * three to fit the maintenance in is how a later edit drops the middle one.</p>
     */
    @Transactional
    public ShPostUnified reindexed(ShPostUnified post) {
        reindex(post);
        return post;
    }

    /** One state's edges, for a row that is going away (unpublish). */
    @Transactional
    public void forget(String postId, ShPostState state) {
        if (postId != null && state != null) {
            shObjectReferenceRepository.deleteByFromPostIdAndFromState(postId, state);
        }
    }

    /** Both states, for a purge. */
    @Transactional
    public void forget(String postId) {
        if (postId != null) {
            shObjectReferenceRepository.deleteByFromPostId(postId);
        }
    }

    /** Which rows point at this object, in the order the index returns them. */
    @Transactional(readOnly = true)
    public List<ShObjectReference> referrersOf(String objectId) {
        return objectId == null ? List.of() : shObjectReferenceRepository.findByToObjectId(objectId);
    }

    /** Whether anything points here — what {@code asset-unused} asks, as one indexed read. */
    @Transactional(readOnly = true)
    public boolean isReferenced(String objectId) {
        return objectId != null && shObjectReferenceRepository.existsByToObjectId(objectId);
    }

    /**
     * Of the objects named, the ones something points at.
     *
     * <p>One query for the whole set rather than one per candidate, which is the complaint
     * SH1034's line makes: the substring form answered "which assets are unused" with a full scan
     * per asset.</p>
     */
    @Transactional(readOnly = true)
    public List<String> referencedAmong(Collection<String> objectIds) {
        return objectIds == null || objectIds.isEmpty()
                ? List.of()
                : shObjectReferenceRepository.findReferencedAmong(objectIds);
    }

    /**
     * How many posts point at each of the objects named — <b>zero included</b>.
     *
     * <p>Every id asked about appears in the answer, because the caller's question is "how many"
     * and an absent key would make them write the same {@code getOrDefault} at each call site.
     * Zero is the interesting value here: it is what a prune and {@code asset-unused} act on.</p>
     *
     * <p>One query for the whole set. A count per row would put this back on SH1033's curve with
     * extra steps — N indexed reads where the point of the index is that the answer costs what the
     * answer is.</p>
     */
    @Transactional(readOnly = true)
    public Map<String, Long> referrerCounts(Collection<String> objectIds) {
        Map<String, Long> counts = new LinkedHashMap<>();
        if (objectIds == null || objectIds.isEmpty()) {
            return counts;
        }
        for (String id : objectIds) {
            counts.put(id, 0L);
        }
        for (Object[] row : shObjectReferenceRepository.countReferrersAmong(objectIds)) {
            counts.put((String) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    /**
     * The edges this row declares.
     *
     * <p>The type's reference-holding attributes are fetched by post-type <b>id</b> — never by
     * navigating {@code post.getShPostType().getShPostTypeAttrs()}, which would load the type's
     * whole attribute graph on every write to read two names from it.</p>
     */
    private List<Edge> edgesOf(ShPostUnified post) {
        if (post.getShPostType() == null || post.getShPostType().getId() == null) {
            return List.of();
        }
        List<ShPostTypeAttr> fields = referenceFields(post.getShPostType().getId());
        return fields.isEmpty()
                ? List.of()
                : ShPostReferenceWalk.edges(post.getData(), fields, objectMapper);
    }

    private List<ShPostTypeAttr> referenceFields(String postTypeId) {
        return new ArrayList<>(entityManager.createQuery("""
                select a from ShPostTypeAttr a
                 where a.shPostType.id = :type
                   and a.shWidget.name in (:file, :select)""", ShPostTypeAttr.class)
                .setParameter("type", postTypeId)
                .setParameter("file", ShSystemWidget.FILE)
                .setParameter("select", ShSystemWidget.CONTENT_SELECT)
                .getResultList());
    }
}
