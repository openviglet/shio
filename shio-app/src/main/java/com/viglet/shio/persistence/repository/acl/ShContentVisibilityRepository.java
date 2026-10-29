/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.persistence.repository.acl;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.viglet.shio.persistence.model.acl.ShContentVisibility;

/** SH1285 — the members-only gates, one row per gated folder or post. */
@Repository
public interface ShContentVisibilityRepository extends JpaRepository<ShContentVisibility, String> {

    Optional<ShContentVisibility> findFirstByObjectId(String objectId);

    void deleteByObjectId(String objectId);
}
