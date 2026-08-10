/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.persistence.repository.form;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.viglet.shio.persistence.model.form.ShSiteForm;

/**
 * A site's form configuration (SH632).
 *
 * <p>Keyed by {@code siteId} as a plain column, never by an association: navigating past {@code .id}
 * of an {@code ShObject} subclass emits the {@code @TenantId} predicate against an alias that has no
 * such column, which is valid JPQL that fails at query-prepare time.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Repository
public interface ShSiteFormRepository extends JpaRepository<ShSiteForm, String> {

    /**
     * This site's configuration, if it has one.
     *
     * <p>{@code findFirst…OrderByIdAsc} rather than a unique read, for SH278's reason applied to a
     * second identity: one row per site is the intent and the API enforces it, but a restore or a
     * hand-written row can produce two — and resolving <b>consistently</b> to the same one is a
     * defect somebody recognises, where a coin-flip between two configurations reads as the form
     * endpoint being flaky.</p>
     */
    Optional<ShSiteForm> findFirstBySiteIdOrderByIdAsc(String siteId);

    /** Every configured site, for the console listing. */
    List<ShSiteForm> findAllByOrderBySiteIdAsc();
}
