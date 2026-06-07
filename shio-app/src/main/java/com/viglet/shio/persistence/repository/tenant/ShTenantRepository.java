/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */
package com.viglet.shio.persistence.repository.tenant;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.viglet.shio.persistence.model.tenant.ShTenant;

/**
 * Repository for the tenant catalog (SH22). {@link ShTenant} is not
 * {@code @TenantId}-scoped, so these queries see every tenant.
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Repository
public interface ShTenantRepository extends JpaRepository<ShTenant, String> {

    Optional<ShTenant> findByName(String name);

    boolean existsByName(String name);

    List<ShTenant> findAllByOrderByNameAsc();
}
