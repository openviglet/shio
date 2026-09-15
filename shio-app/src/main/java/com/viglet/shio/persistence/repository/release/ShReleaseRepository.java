/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.persistence.repository.release;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.viglet.shio.persistence.model.release.ShRelease;

/**
 * Releases, by the site and name that address them (SH993).
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Repository
public interface ShReleaseRepository extends JpaRepository<ShRelease, String> {

    Optional<ShRelease> findBySiteIdAndName(String siteId, String name);

    /** In name order, which is the order a teaching error lists them and a projection writes them. */
    List<ShRelease> findBySiteIdOrderByNameAsc(String siteId);

    /**
     * SH993 — the SH15 sweep asks this for releases whose instant has arrived.
     *
     * <p>Archived ones are excluded here rather than filtered afterwards: archiving a scheduled
     * release is how a launch is called off for good, and a sweep that still fired it would publish
     * the set somebody had just put away.</p>
     */
    List<ShRelease> findByStateAndArchivedAtIsNullAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
            String state, java.time.Instant due);

    /** SH993 — what the context pack lists: the site's releases, newest first. */
    List<ShRelease> findBySiteIdAndArchivedAtIsNullOrderByCreatedAtDesc(String siteId);
}
