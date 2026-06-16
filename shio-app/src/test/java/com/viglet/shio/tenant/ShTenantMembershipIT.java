/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.shio.persistence.model.tenant.ShTenant;

/**
 * SH22/C — tenant membership + signup: signup creates a tenant with an OWNER
 * membership (idempotent for the same owner, 409 for a different one), reserved
 * names are rejected, and {@code isActiveMember}/{@code tenantsOf} reflect the
 * membership. Boots the full context on a fresh H2 with Liquibase + multi-tenant
 * (so {@code sh_tenant_membership} and provisioning are exercised).
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
class ShTenantMembershipIT {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:shio-sh22c-it;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.H2Dialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.liquibase.enabled", () -> "true");
        registry.add("spring.liquibase.default-schema", () -> "PUBLIC");
        registry.add("shio.multi-tenant", () -> "true");
    }

    @Autowired
    private ShTenantMembershipService service;

    @Test
    void signupCreatesTenantWithOwnerMembership() {
        ShTenant tenant = service.signup("acme", "alice");

        assertThat(tenant.getName()).isEqualTo("acme");
        assertThat(service.isActiveMember("acme", "alice")).isTrue();
        assertThat(service.tenantsOf("alice")).extracting(ShTenant::getName).contains("acme");
        // A non-member is not active.
        assertThat(service.isActiveMember("acme", "bob")).isFalse();
    }

    @Test
    void signupIsIdempotentForTheSameOwner() {
        ShTenant first = service.signup("globex", "alice");
        ShTenant second = service.signup("globex", "alice");
        assertThat(second.getId()).isEqualTo(first.getId());
    }

    @Test
    void signupConflictsForADifferentOwner() {
        service.signup("initech", "alice");
        assertThatThrownBy(() -> service.signup("initech", "bob"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already taken");
    }

    @Test
    void reservedNameIsRejected() {
        assertThatThrownBy(() -> service.signup("admin", "alice"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void invalidNameIsRejected() {
        assertThatThrownBy(() -> service.signup("Not A Slug!", "alice"))
                .isInstanceOf(ResponseStatusException.class);
    }
}
