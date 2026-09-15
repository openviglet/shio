/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.persistence.repository.note;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.viglet.shio.persistence.model.note.ShNote;

/**
 * The notes hanging on content (SH1003).
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Repository
public interface ShNoteRepository extends JpaRepository<ShNote, String> {

    Optional<ShNote> findById(String id);

    /** One object's thread, oldest first — the order it was said in. */
    List<ShNote> findByObjectIdOrderByCreatedAtAsc(String objectId);

    /**
     * The open notes on a page of objects, in one query.
     *
     * <p>One lookup for the page rather than one per row, the same shape the change feed uses for
     * its runs: twenty posts is one query, and a read per post is nineteen answering a question
     * already answered.</p>
     */
    List<ShNote> findByObjectIdInAndResolvedFalseOrderByCreatedAtAsc(Collection<String> objectIds);

    /**
     * Everything still asking for something, newest first — the work list.
     *
     * <p>Bounded by the caller, and it says so (SH103's rule): an instance that has been running
     * for a year has a thread on every page, and "every open note ever" is not a question anybody
     * has.</p>
     */
    @Query("select n from ShNote n where n.resolved = false "
            + "and (:kind is null or n.kind = :kind) "
            + "order by n.createdAt desc")
    List<ShNote> findOpen(String kind, Pageable pageable);
}
