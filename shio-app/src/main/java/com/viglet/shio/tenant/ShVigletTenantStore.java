/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.tenant;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.core.tenancy.VigletTenantMembershipStatus;
import com.viglet.core.tenancy.VigletTenantRef;
import com.viglet.core.tenancy.VigletTenantRole;
import com.viglet.core.tenancy.VigletTenantStatus;
import com.viglet.core.tenancy.VigletTenantStore;
import com.viglet.shio.persistence.model.tenant.ShTenant;
import com.viglet.shio.persistence.model.tenant.ShTenantMembership;
import com.viglet.shio.persistence.model.tenant.ShTenantMembershipStatus;
import com.viglet.shio.persistence.model.tenant.ShTenantRole;
import com.viglet.shio.persistence.model.tenant.ShTenantStatus;
import com.viglet.shio.persistence.repository.tenant.ShTenantMembershipRepository;
import com.viglet.shio.persistence.repository.tenant.ShTenantRepository;

/**
 * T397 / §XIV.9 — the {@link VigletTenantStore} SPI over Shio's {@code ShTenant} /
 * {@code ShTenantMembership} registry, expressed through the neutral
 * {@link VigletTenantRef} so the lifted shared services
 * ({@code VigletTenantMembershipService}, {@code VigletTenantResolutionFilter},
 * {@code VigletQuotaService}, {@code VigletTenantTeardownService}) never bind to
 * Shio's JPA entities.
 *
 * <p>This is the membership-model bridge of the cross-product tenancy parity work:
 * the duplicated <em>logic</em> (idempotent signup, the resolution gate, teardown)
 * now lives once in {@code viglet-core-tenancy} and drives all three products,
 * while the duplicated <em>rows</em> (Shio's own entity, its id strategy) stay here
 * behind this one interface.
 *
 * <p><strong>Shio's discriminator is the tenant {@code name}</strong> (the value
 * Hibernate's {@code @TenantId} stamps on content rows), not the entity's surrogate
 * UUID primary key. The neutral {@link VigletTenantRef#id() id} therefore maps to
 * {@link ShTenant#getName()} (and the slug, since Shio has no separate slug), so
 * the shared services canonicalize, bind and scope by the same value Shio's
 * {@code ShTenantContext}/{@code ShTenantResolver} already use.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class ShVigletTenantStore implements VigletTenantStore {

    private final ShTenantRepository shTenantRepository;
    private final ShTenantMembershipRepository shTenantMembershipRepository;

    public ShVigletTenantStore(ShTenantRepository shTenantRepository,
            ShTenantMembershipRepository shTenantMembershipRepository) {
        this.shTenantRepository = shTenantRepository;
        this.shTenantMembershipRepository = shTenantMembershipRepository;
    }

    // --- reads -------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Optional<VigletTenantRef> findById(String tenantId) {
        return shTenantRepository.findByName(tenantId).map(ShVigletTenantStore::toRef);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VigletTenantRef> findBySlug(String slug) {
        // Shio identifies a tenant by its name; the name doubles as the slug.
        return shTenantRepository.findByName(slug).map(ShVigletTenantStore::toRef);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VigletTenantRef> findAll() {
        return shTenantRepository.findAll().stream().map(ShVigletTenantStore::toRef).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isActiveMember(String tenantId, String username) {
        return shTenantMembershipRepository.findByTenant_NameAndUsername(tenantId, username)
                .map(ShTenantMembership::isActive)
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isOwnedBy(String tenantId, String username) {
        return shTenantMembershipRepository.findByTenant_NameAndUsername(tenantId, username)
                .filter(m -> m.getRole() == ShTenantRole.OWNER)
                .isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public List<VigletTenantRef> activeTenantsOf(String username) {
        return shTenantMembershipRepository.findByUsername(username).stream()
                .filter(ShTenantMembership::isActive)
                .map(ShTenantMembership::getTenant)
                .map(ShVigletTenantStore::toRef)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VigletTenantRef> ownedTenantOf(String username) {
        return shTenantMembershipRepository.findByUsername(username).stream()
                .filter(m -> m.isActive() && m.getRole() == ShTenantRole.OWNER)
                .map(ShTenantMembership::getTenant)
                .map(ShVigletTenantStore::toRef)
                .findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public int membershipCount(String tenantId) {
        return (int) shTenantMembershipRepository.countByTenant_Name(tenantId);
    }

    // --- writes ------------------------------------------------------------

    @Override
    @Transactional
    public VigletTenantRef createTenant(String slug, String name, String plan) {
        ShTenant tenant = new ShTenant();
        tenant.setName(slug); // Shio's name is the discriminator (== slug)
        tenant.setEnabled(true);
        tenant.setCreatedDate(Instant.now());
        if (plan != null) {
            tenant.setPlan(plan);
        }
        return toRef(shTenantRepository.saveAndFlush(tenant));
    }

    @Override
    @Transactional
    public void addMembership(String tenantId, String username, VigletTenantRole role,
            VigletTenantMembershipStatus status) {
        ShTenant tenant = shTenantRepository.findByName(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown tenant: " + tenantId));
        ShTenantMembership membership = new ShTenantMembership();
        membership.setTenant(tenant);
        membership.setUsername(username);
        membership.setRole(toShRole(role));
        membership.setStatus(toShMembershipStatus(status));
        membership.setCreatedDate(Instant.now());
        shTenantMembershipRepository.save(membership);
    }

    @Override
    @Transactional
    public void updateStatus(String tenantId, VigletTenantStatus status) {
        ShTenant tenant = shTenantRepository.findByName(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown tenant: " + tenantId));
        tenant.setStatus(toShStatus(status));
        shTenantRepository.saveAndFlush(tenant);
    }

    @Override
    @Transactional
    public int deleteTenant(String tenantId) {
        ShTenant tenant = shTenantRepository.findByName(tenantId).orElse(null);
        if (tenant == null) {
            return 0;
        }
        int memberships = (int) shTenantMembershipRepository.countByTenant_Name(tenantId);
        // Memberships FK the tenant row — remove them first.
        shTenantMembershipRepository.deleteByTenant_Id(tenant.getId());
        shTenantRepository.delete(tenant);
        return memberships;
    }

    // --- mapping -----------------------------------------------------------

    static VigletTenantRef toRef(ShTenant tenant) {
        return new VigletTenantRef(tenant.getName(), tenant.getName(), tenant.getName(),
                toVigletStatus(tenant.getStatus()), tenant.getPlan());
    }

    private static VigletTenantStatus toVigletStatus(ShTenantStatus status) {
        return status == ShTenantStatus.SUSPENDED
                ? VigletTenantStatus.SUSPENDED
                : VigletTenantStatus.ACTIVE;
    }

    private static ShTenantStatus toShStatus(VigletTenantStatus status) {
        return status == VigletTenantStatus.SUSPENDED
                ? ShTenantStatus.SUSPENDED
                : ShTenantStatus.ACTIVE;
    }

    private static ShTenantRole toShRole(VigletTenantRole role) {
        return switch (role) {
            case OWNER -> ShTenantRole.OWNER;
            case ADMIN -> ShTenantRole.ADMIN;
            case MEMBER -> ShTenantRole.MEMBER;
        };
    }

    private static ShTenantMembershipStatus toShMembershipStatus(VigletTenantMembershipStatus status) {
        return switch (status) {
            case ACTIVE -> ShTenantMembershipStatus.ACTIVE;
            case INVITED -> ShTenantMembershipStatus.INVITED;
            case SUSPENDED -> ShTenantMembershipStatus.SUSPENDED;
        };
    }
}
