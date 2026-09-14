/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.persistence.repository.post.revision;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.viglet.shio.persistence.model.post.revision.ShPostRevision;

/**
 * The revisions of a post, newest first (SH986).
 *
 * <p>Every read here is scoped to one post: this table is only ever asked "what was this page
 * before", and a query across posts would be a report nobody has asked for over a table that grows
 * with every publish in the instance.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Repository
public interface ShPostRevisionRepository extends JpaRepository<ShPostRevision, String> {

    /** Newest first, which is the order every reader wants and the order the prune walks. */
    List<ShPostRevision> findByPostIdOrderByVersionNoDesc(String postId);

    Optional<ShPostRevision> findByPostIdAndVersionNo(String postId, int versionNo);

    /**
     * The highest version this post has, or empty when it has none.
     *
     * <p>{@code max + 1} rather than a count: a prune deletes the oldest rows, so a count would
     * reissue a number a deleted revision already used and the unique constraint would refuse it —
     * which is the failure that looks like "publishing stopped working" long after the prune.</p>
     */
    @Query("SELECT MAX(r.versionNo) FROM ShPostRevision r WHERE r.postId = :postId")
    Integer maxVersionNo(@Param("postId") String postId);
}
