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

    /**
     * Every friendly URL this site's posts have ever been captured at (SH1064).
     *
     * <h2>The one read that is not about a single post, and why it is allowed to be</h2>
     *
     * <p>The class rule above stands: this table is asked "what was this page before". This asks
     * exactly that of a whole site at once, because the question {@code verify} has to answer —
     * "does this broken link point at an address that <b>used to be</b> a page here?" — cannot be
     * answered post by post: the post the link points at is precisely the one nobody can name any
     * more. Without it the CMS knows the answer and reports a guess, which is the gap SH1064 is
     * about.</p>
     *
     * <p><b>A full verify only</b>, like {@code no-home-page} and for the measured reason SH334
     * gave: it is a question about the site, so a scoped run would attach a paragraph about the
     * estate to a task about one page (P3). One query per site per full run, over an index on
     * {@code post_id}; the distinct list is URLs, not rows, so a site with a long history pays for
     * its distinct addresses rather than for its publishes.</p>
     */
    @Query("SELECT DISTINCT r.furl FROM ShPostRevision r WHERE r.furl IS NOT NULL AND r.postId IN "
            + "(SELECT p.id FROM ShPostUnified p WHERE p.shSite.id = :siteId)")
    List<String> distinctFurlsBySiteId(@Param("siteId") String siteId);
}
