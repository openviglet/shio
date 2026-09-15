/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.persistence.repository.gate;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.viglet.shio.persistence.model.gate.ShPublishGate;

/**
 * A site's publish gates (SH997).
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Repository
public interface ShPublishGateRepository extends JpaRepository<ShPublishGate, String> {

    Optional<ShPublishGate> findBySiteIdAndName(String siteId, String name);

    /** In name order, so two instances evaluate a site's gates in the same order (P5). */
    List<ShPublishGate> findBySiteIdAndEnabledTrueOrderByNameAsc(String siteId);

    /** Every gate a site declares, enabled or not — what a projection writes back. */
    List<ShPublishGate> findBySiteIdOrderByNameAsc(String siteId);
}
