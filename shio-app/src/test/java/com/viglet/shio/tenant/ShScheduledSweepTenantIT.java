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

import java.time.Instant;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.tenant.ShTenant;
import com.viglet.shio.persistence.model.tenant.ShTenantStatus;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.tenant.ShTenantRepository;
import com.viglet.shio.schedule.ShPostScheduler;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * The sweep that ran as nobody (SH639).
 *
 * <h2>What this asserts, and why nothing did before</h2>
 *
 * <p>{@code ShPostScheduler.sweep()} is a timer, so it runs on a thread that never had a tenant
 * bound — and {@code ShPostUnified} carries {@code @TenantId}, so Hibernate appended the default
 * discriminator to its finders. A post another tenant scheduled was not published late: it was
 * <b>never found</b>, and nothing threw, so the incident log had nothing to say either.</p>
 *
 * <p>The existing coverage could not see it. {@code ShPostUnifiedAPITest} exercises the transition
 * by calling {@code publishScheduled(id)} directly — the thing the sweep <em>delegates to</em> —
 * so it proves the publish works and says nothing about whether the sweep ever finds the row. The
 * defect lives entirely in the finder's implicit predicate, which is the one part that test
 * bypasses.</p>
 *
 * <h2>An IT, because the defect only exists in multi-tenant mode</h2>
 *
 * <p>{@code shio.multi-tenant=true} is what makes the discriminator mean anything, and every other
 * test of it in this repository is an IT for the same reason. It also costs no Spring context in
 * the unit suite, which matters after SH423: the alternative was a fifth multi-tenant context for
 * one assertion.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
class ShScheduledSweepTenantIT {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:shio-sh639-it;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.H2Dialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.liquibase.enabled", () -> "true");
        registry.add("spring.liquibase.default-schema", () -> "PUBLIC");
        registry.add("shio.multi-tenant", () -> "true");
        // The sweep is driven by hand here. Left on its timer it would fire mid-assertion and the
        // test would be racing its own subject.
        registry.add("shio.schedule.enabled", () -> "true");
        registry.add("shio.schedule.sweep-interval-millis", () -> "3600000");
    }

    private static final String ACME = "sh639-acme";
    private static final String STOPPED = "sh639-stopped";
    private static final Instant DUE = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private ShPostScheduler shPostScheduler;
    @Autowired
    private ShPostUnifiedRepository postRepository;
    @Autowired
    private ShTenantRepository shTenantRepository;
    @Autowired
    private PlatformTransactionManager txManager;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void tenants() {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            tenant(ACME, ShTenantStatus.ACTIVE);
            tenant(STOPPED, ShTenantStatus.SUSPENDED);
        });
    }

    @Test
    @DisplayName("SH639: a due post of a non-default tenant is published by the sweep")
    void aNonDefaultTenantsScheduleFires() {
        String id = asTenant(ACME, () -> due("Acme's scheduled page").getId());

        shPostScheduler.sweep();

        assertThat(asTenant(ACME, () -> postRepository.findByIdAndState(id, ShPostState.PUBLISHED)))
                .as("""
                        The sweep runs on a timer thread with no tenant bound, and ShPostUnified is \
                        @TenantId — so before SH639 the finder asked only for the default \
                        discriminator's due posts and this row was never returned. Not published \
                        late: never found, with nothing thrown and nothing logged.""")
                .isPresent();
        assertThat(asTenant(ACME, () -> postRepository.findByIdAndState(id, ShPostState.DRAFT)))
                .as("and the schedule was cleared, so it cannot fire again on the next tick")
                .hasValueSatisfying(draft ->
                        assertThat(draft.getPublishScheduledDate()).isNull());
    }

    @Test
    @DisplayName("SH639: a suspended tenant's schedule does not fire")
    void aSuspendedTenantIsSkipped() {
        // The one outcome worse than publishing late. The request path already answers a suspended
        // tenant 403, so a timer that published for them anyway would be a way around the
        // suspension — and one nobody would see, because a timer leaves no request behind.
        String id = asTenant(STOPPED, () -> due("Should stay unpublished").getId());

        shPostScheduler.sweep();

        assertThat(asTenant(STOPPED, () -> postRepository.findByIdAndState(id, ShPostState.PUBLISHED)))
                .as("publishing on behalf of an account the platform has stopped is worse than "
                        + "publishing late, and a timer must not be the way around a suspension")
                .isEmpty();
    }

    private void tenant(String name, ShTenantStatus status) {
        shTenantRepository.findByName(name).ifPresentOrElse(
                existing -> existing.setStatus(status),
                () -> {
                    ShTenant row = new ShTenant();
                    row.setName(name);
                    row.setDescription(name);
                    row.setStatus(status);
                    row.setOwner("admin");
                    row.setCreatedDate(DUE);
                    entityManager.persist(row);
                });
    }

    /** A draft whose publish is already due, in whichever tenant is bound. */
    private ShPostUnified due(String title) {
        ShPostUnified post = new ShPostUnified();
        post.setState(ShPostState.DRAFT);
        post.setTitle(title);
        post.setData("{}");
        post.setDate(DUE);
        post.setOwner("agent");
        post.setPublishScheduledDate(DUE);
        return postRepository.save(post);
    }

    /**
     * Hibernate resolves the tenant once per session open, so each phase needs its own
     * transaction — switching the thread-local only takes effect on the next one.
     */
    private <T> T asTenant(String tenant, Supplier<T> work) {
        ShTenantContext.setTenantId(tenant);
        try {
            return new TransactionTemplate(txManager).execute(status -> work.get());
        } finally {
            ShTenantContext.clear();
        }
    }
}
