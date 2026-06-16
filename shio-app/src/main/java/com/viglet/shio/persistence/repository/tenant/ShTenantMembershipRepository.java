/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.persistence.repository.tenant;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.viglet.shio.persistence.model.tenant.ShTenantMembership;

/**
 * Repository for tenant memberships (SH22 / Turing parity). Like
 * {@link ShTenantRepository}, {@link ShTenantMembership} is not {@code @TenantId}-scoped
 * (it is queried before the request tenant is known), so these see every tenant.
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Repository
public interface ShTenantMembershipRepository extends JpaRepository<ShTenantMembership, String> {

    /** Join-fetches the tenant so callers can read it after the session closes. */
    @Query("select m from ShTenantMembership m join fetch m.tenant where m.username = :username")
    List<ShTenantMembership> findByUsername(@Param("username") String username);

    Optional<ShTenantMembership> findByTenant_IdAndUsername(String tenantId, String username);

    Optional<ShTenantMembership> findByTenant_NameAndUsername(String tenantName, String username);

    /** Remove all memberships of a tenant — called by teardown before deleting the tenant row. */
    void deleteByTenant_Id(String tenantId);
}
