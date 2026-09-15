/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.persistence.repository.review;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.viglet.shio.persistence.model.review.ShReviewDecision;

/**
 * The standing decisions a curator has made (SH1001).
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Repository
public interface ShReviewDecisionRepository extends JpaRepository<ShReviewDecision, String> {

    Optional<ShReviewDecision> findBySessionAndPostId(String session, String postId);

    List<ShReviewDecision> findBySessionOrderByDecidedAtAsc(String session);

    /**
     * Every decision the listed runs hold, in one query.
     *
     * <p>One lookup for the page, the way the queue already resolves its runs and its addresses: a
     * listing of twenty runs asking per run is twenty queries answering one question.</p>
     */
    List<ShReviewDecision> findBySessionIn(Collection<String> sessions);

    /** The decisions standing against these posts, whichever run made them. */
    List<ShReviewDecision> findByPostIdIn(Collection<String> postIds);
}
