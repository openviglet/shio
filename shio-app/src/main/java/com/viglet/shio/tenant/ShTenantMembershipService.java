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
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.shio.persistence.model.tenant.ShTenant;
import com.viglet.shio.persistence.model.tenant.ShTenantMembership;
import com.viglet.shio.persistence.model.tenant.ShTenantMembershipStatus;
import com.viglet.shio.persistence.model.tenant.ShTenantRole;
import com.viglet.shio.persistence.repository.tenant.ShTenantMembershipRepository;
import com.viglet.shio.persistence.repository.tenant.ShTenantRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Tenant membership + self-service signup (SH22 / Turing parity, mirrors
 * {@code TurTenantService}). Owns the user&harr;tenant&harr;role join and the
 * rules that gate access: {@link #isActiveMember} drives the hard-403 gate in
 * {@code ShTenantFilter}, {@link #tenantsOf} backs {@code GET /tenant/mine}, and
 * {@link #signup} creates a tenant with an {@code OWNER} membership.
 *
 * <p>Shio identifies a tenant by its {@code name} (no separate slug), so the name
 * doubles as the slug — validated to a safe, lowercase, hyphenated token and
 * checked against a reserved list.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
@Slf4j
public class ShTenantMembershipService {

    /** 2&ndash;63 lowercase letters, digits or inner hyphens (the tenant name doubles as a slug). */
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$");

    private static final Set<String> RESERVED_NAMES = Set.of(
            "www", "app", "api", "admin", "console", "shio", "localhost", "signup",
            "login", "logout", "oauth2", "static", "assets", "default", "system",
            "platform", "support", "mail", "root", "store", "file_source");

    private final ShTenantRepository shTenantRepository;
    private final ShTenantMembershipRepository shTenantMembershipRepository;
    private final ShTenantProvisioningService shTenantProvisioningService;

    public ShTenantMembershipService(ShTenantRepository shTenantRepository,
                                     ShTenantMembershipRepository shTenantMembershipRepository,
                                     ShTenantProvisioningService shTenantProvisioningService) {
        this.shTenantRepository = shTenantRepository;
        this.shTenantMembershipRepository = shTenantMembershipRepository;
        this.shTenantProvisioningService = shTenantProvisioningService;
    }

    /** Normalize + validate a tenant name; rejects bad shapes (400) and reserved names (400). */
    public String normalizeName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A tenant name is required");
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        if (!NAME_PATTERN.matcher(normalized).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid name: use 2-63 lowercase letters, digits or hyphens");
        }
        if (RESERVED_NAMES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Name '" + normalized + "' is reserved");
        }
        return normalized;
    }

    /**
     * Self-service signup: create a tenant named {@code name} with {@code owner} as
     * its {@code OWNER}, then provision the default content model. Idempotent — if the
     * tenant already exists and {@code owner} already owns it, returns it; otherwise 409.
     */
    @Transactional
    public ShTenant signup(String name, String owner) {
        String normalized = normalizeName(name);
        if (!StringUtils.hasText(owner)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An owner is required");
        }
        ShTenant existing = shTenantRepository.findByName(normalized).orElse(null);
        if (existing != null) {
            boolean ownedByCaller = shTenantMembershipRepository
                    .findByTenant_IdAndUsername(existing.getId(), owner)
                    .filter(m -> m.getRole() == ShTenantRole.OWNER)
                    .isPresent();
            if (ownedByCaller) {
                return existing;
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Tenant name '" + normalized + "' is already taken");
        }

        ShTenant tenant = new ShTenant();
        tenant.setName(normalized);
        tenant.setOwner(owner);
        tenant.setEnabled(true);
        tenant.setCreatedDate(Instant.now());
        ShTenant saved = shTenantRepository.saveAndFlush(tenant);

        addMembership(saved, owner, ShTenantRole.OWNER);
        log.info("Tenant '{}' signed up by '{}'", normalized, owner);

        // Best-effort: a provisioning failure must not undo the tenant + membership.
        try {
            shTenantProvisioningService.provision(normalized);
        } catch (RuntimeException e) {
            log.warn("Tenant '{}' created but provisioning failed: {}", normalized, e.getMessage());
        }
        return saved;
    }

    /**
     * Ensure {@code username} has a membership of at least {@code role} in {@code tenant}
     * (idempotent). Used by signup and by the admin create path so the creator is never
     * locked out by the hard-403 gate.
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
