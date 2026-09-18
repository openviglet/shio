/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.persistence.repository.reference;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.reference.ShObjectReference;

/**
 * The reverse index SH1033's numbers argued for (SH1034).
 *
 * <p>Every method here is an equality predicate on an indexed column, which is the entire point:
 * the substring scan it replaces examined 50,002 rows at fifty thousand posts, and the prototype
 * of these reads examined four. Nothing in this interface may grow a {@code like}.</p>
 *
 * <p>No association is navigated, so the {@code agents.md} JPQL invariant does not arise — this
 * entity deliberately holds ids rather than {@code ShObject} references, which is the same reason
 * it exists instead of {@code ShReference}.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Repository
public interface ShObjectReferenceRepository extends JpaRepository<ShObjectReference, String> {

    /** Every edge pointing at this object, in either state. */
    List<ShObjectReference> findByToObjectId(String toObjectId);

    /** Every edge pointing at this object from rows in the given state. */
    List<ShObjectReference> findByToObjectIdAndFromState(String toObjectId, ShPostState fromState);

    /** Whether anything at all points here — the question {@code asset-unused} asks. */
    boolean existsByToObjectId(String toObjectId);

    /**
     * The objects, of those named, that something points at.
     *
     * <p>The inverse question in one query rather than one per candidate, which is the whole
     * complaint SH1034's line makes: finding unused assets used to mean a full scan per asset.</p>
     */
    @Query("select distinct r.toObjectId from ShObjectReference r where r.toObjectId in :ids")
    List<String> findReferencedAmong(@Param("ids") Collection<String> ids);

    /**
     * How many distinct posts point at each of the objects named.
     *
     * <p>{@code count(distinct r.fromPostId)} rather than {@code count(*)}: a post that references
     * one asset from two fields, or from both its draft and its published row, is <b>one</b> place
     * a curator has to look. Counting rows would report an asset as used three times by one page,
     * and a badge saying 3 over a list of 1 is the kind of disagreement P9 exists to prevent.</p>
     *
     * <p>One query for a whole page of a listing, which is the property that keeps this off
     * SH1033's curve: a count per row would be N queries where the scan was one.</p>
     */
    @Query("select r.toObjectId, count(distinct r.fromPostId) from ShObjectReference r "
            + "where r.toObjectId in :ids group by r.toObjectId")
    List<Object[]> countReferrersAmong(@Param("ids") Collection<String> ids);

    /** What the maintenance step clears before writing a row's current edges. */
    @Modifying
    void deleteByFromPostIdAndFromState(String fromPostId, ShPostState fromState);

    /** Both states of a post, for a delete. */
    @Modifying
    void deleteByFromPostId(String fromPostId);
}
