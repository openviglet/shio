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

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.viglet.core.tenancy.VigletTenantMembershipService;
import com.viglet.core.tenancy.VigletTenantRef;
import com.viglet.core.tenancy.VigletTenantSlugValidator;
import com.viglet.shio.persistence.model.tenant.ShTenant;
import com.viglet.shio.persistence.model.tenant.ShTenantMembership;
import com.viglet.shio.persistence.model.tenant.ShTenantMembershipStatus;
import com.viglet.shio.persistence.model.tenant.ShTenantRole;
import com.viglet.shio.persistence.repository.tenant.ShTenantMembershipRepository;
import com.viglet.shio.persistence.repository.tenant.ShTenantRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Tenant membership + self-service signup (SH22 / Turing parity). Owns the
 * user&harr;tenant&harr;role join and the self-service signup.
 *
 * <p>T397 / §XIV.9 — re-homed onto the shared {@link VigletTenantMembershipService}
 * + {@link VigletTenantSlugValidator} (lifted from this class and Turing's
 * {@code TurTenantService}): the idempotent-signup / slug-validation / post-create
 * provisioning logic now lives once in {@code viglet-core-tenancy} and drives all
 * three products. This class is the thin Shio-facing adapter that maps the neutral
 * {@link VigletTenantRef} the shared service returns back to the {@link ShTenant}
 * entity the controllers expect; the membership reads/writes that don't carry
 * shared logic stay repository-direct here.
 *
 * <p>Shio identifies a tenant by its {@code name} (no separate slug), so the name
 * doubles as the slug the shared service validates.
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
@Slf4j
public class ShTenantMembershipService {

    private final VigletTenantMembershipService membershipService;
    private final VigletTenantSlugValidator slugValidator;
    private final ShTenantRepository shTenantRepository;
    private final ShTenantMembershipRepository shTenantMembershipRepository;

    public ShTenantMembershipService(VigletTenantMembershipService membershipService,
                                     VigletTenantSlugValidator slugValidator,
                                     ShTenantRepository shTenantRepository,
                                     ShTenantMembershipRepository shTenantMembershipRepository) {
        this.membershipService = membershipService;
        this.slugValidator = slugValidator;
        this.shTenantRepository = shTenantRepository;
        this.shTenantMembershipRepository = shTenantMembershipRepository;
    }

    /** Normalize + validate a tenant name; rejects bad shapes (400) and reserved names (400). */
    public String normalizeName(String name) {
        return slugValidator.normalizeSlug(name);
    }

    /**
     * Self-service signup: create a tenant named {@code name} with {@code owner} as
     * its {@code OWNER}, then provision the default content model (via the shared
     * provisioner hook). Idempotent — if the tenant already exists and {@code owner}
     * already owns it, returns it; otherwise 409.
     */
    @Transactional
    public ShTenant signup(String name, String owner) {
        VigletTenantRef ref = membershipService.signup(name, name, owner);
        return shTenantRepository.findByName(ref.id())
                .orElseThrow(() -> new IllegalStateException("Tenant vanished: " + ref.id()));
    }

    /**
     * Ensure {@code username} has a membership of at least {@code role} in {@code tenant}
     * (idempotent). Used by the admin create path so the creator is never locked out
     * by the hard-403 gate.
     */
    @Transactional
    public ShTenantMembership addMembership(ShTenant tenant, String username, ShTenantRole role) {
        if (tenant == null || !StringUtils.hasText(username)) {
            return null;
        }
        return shTenantMembershipRepository.findByTenant_IdAndUsername(tenant.getId(), username)
                .orElseGet(() -> {
                    ShTenantMembership membership = new ShTenantMembership();
                    membership.setTenant(tenant);
                    membership.setUsername(username);
                    membership.setRole(role);
                    membership.setStatus(ShTenantMembershipStatus.ACTIVE);
                    membership.setCreatedDate(Instant.now());
                    return shTenantMembershipRepository.save(membership);
                });
    }

    /** The tenants {@code username} actively belongs to (backs {@code GET /tenant/mine}). */
    @Transactional(readOnly = true)
    public List<ShTenant> tenantsOf(String username) {
        if (!StringUtils.hasText(username)) {
            return List.of();
        }
        return shTenantMembershipRepository.findByUsername(username).stream()
                .filter(ShTenantMembership::isActive)
                .map(ShTenantMembership::getTenant)
                .toList();
    }

    /** Whether {@code username} has an ACTIVE membership in the tenant named {@code tenantName}. */
    @Transactional(readOnly = true)
    public boolean isActiveMember(String tenantName, String username) {
        if (!StringUtils.hasText(tenantName) || !StringUtils.hasText(username)) {
            return false;
        }
        return shTenantMembershipRepository.findByTenant_NameAndUsername(tenantName, username)
                .map(ShTenantMembership::isActive)
                .orElse(false);
    }

    /** The tenant named {@code name}, or empty (used by the switch endpoint). */
    @Transactional(readOnly = true)
    public Optional<ShTenant> findByName(String name) {
        return shTenantRepository.findByName(name);
    }
}
