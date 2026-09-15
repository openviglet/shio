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

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.viglet.shio.persistence.model.release.ShReleaseItem;

/**
 * The posts a release holds (SH993).
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Repository
public interface ShReleaseItemRepository extends JpaRepository<ShReleaseItem, String> {

    List<ShReleaseItem> findByReleaseIdOrderByAddedAtAsc(String releaseId);
}
