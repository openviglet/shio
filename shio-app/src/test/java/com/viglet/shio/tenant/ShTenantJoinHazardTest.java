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
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH122 — characterization of the SH22 {@code @TenantId} join hazard, and since SH1290 the
 * record that Hibernate fixed it.
 *
 * <p>Context: {@code @TenantId} lives on the <b>JOINED root</b> {@code ShObject}.
 * {@code ShSite}, {@code ShFolder} and {@code ShPostType} are its subclasses, so their own tables
 * carry no {@code tenant_id} column. Up to Hibernate 7.4.5, navigating past {@code .id} of such an
 * association joined only the subclass table and still emitted {@code and spt1_1.tenant_id = ?}
 * against a root alias it never declared, so the statement could not be prepared — in a
 * projection, a {@code GROUP BY}, a {@code WHERE} and an explicit join alike. SH122 pinned that
 * boundary by execution, after reasoning had guessed it wrong twice.</p>
 *
 * <p><b>SH1290: Hibernate 7.4.13 (Spring Boot 4.1.2) joins the root.</b> Every one of those forms
 * now emits {@code join ("ShPostType" spt1_0 join "ShObject" spt1_1 on ...) on ... and
 * spt1_1.tenant_id = ?}: the tenant predicate lands on the alias that has the column, and the
 * subclass row is filtered by its own tenant. Measured the same way SH122 measured the failure, by
 * running each form, and on all three engines — H2, PostgreSQL 16 and MariaDB 11.4 emitted the same
 * statement and returned the same rows. So these tests assert the fix, and they assert it with two
 * tenants: a query that merely stopped throwing could also have stopped filtering, and the
 * failure that matters for a discriminator is a row from the other tenant, not an exception.</p>
 *
 * <p>{@link ShTenantJoinLintTest} still refuses the navigating form in repository sources. Its
 * reason is now cost and consistency rather than correctness: the FK column already lives on the
 * owning table, so the {@code .id} form needs no join at all.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShTenantJoinHazardTest {

    private final String alpha = "sh1290-alpha-" + UUID.randomUUID().toString().substring(0, 8);
    private final String beta = "sh1290-beta-" + UUID.randomUUID().toString().substring(0, 8);
    private final String alphaType = "JoinAlpha" + UUID.randomUUID().toString().substring(0, 8);
    private final String betaType = "JoinBeta" + UUID.randomUUID().toString().substring(0, 8);

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String alphaTypeId;
    private String betaTypeId;

    @BeforeAll
    void seed() {
        alphaTypeId = asTenant(alpha, () -> persistTypeWithPost(alphaType));
        betaTypeId = asTenant(beta, () -> persistTypeWithPost(betaType));
    }

    @AfterAll
    void tearDown() {
        // Entity by entity and by the ids this class created, never a bulk delete: the test
        // database is shared by the whole suite.
        for (String[] seeded : List.of(new String[] {alpha, alphaTypeId}, new String[] {beta, betaTypeId})) {
            asTenant(seeded[0], () -> {
                entityManager.createQuery("select p from ShPostUnified p where p.shPostType.id = :id",
                                ShPostUnified.class)
                        .setParameter("id", seeded[1])
                        .getResultList().forEach(entityManager::remove);
                ShPostType type = entityManager.find(ShPostType.class, seeded[1]);
                if (type != null) {
                    entityManager.remove(type);
                }
                return null;
            });
        }
    }

    @Test
    void navigatingASubclassFieldInAProjectionReadsOnlyThisTenant() {
        // The form that broke the SH77 context pack.
        assertThat(asTenant(alpha, () -> entityManager
                .createQuery("select p.shPostType.name from ShPostUnified p", String.class)
                .getResultList()))
                .as("SH1290: Hibernate 7.4.13 prepares the projection, and must still filter the "
                        + "joined post type by tenant")
                .containsExactly(alphaType);
    }

    @Test
    void navigatingASubclassFieldInAGroupByReadsOnlyThisTenant() {
        List<Object[]> rows = asTenant(alpha, () -> entityManager
                .createQuery("select p.shPostType.name, count(distinct p.id) from ShPostUnified p "
                        + "group by p.shPostType.name", Object[].class)
                .getResultList());

        assertThat(rows).as("SH1290: one group, this tenant's").hasSize(1);
        assertThat(rows.getFirst()[0]).isEqualTo(alphaType);
        assertThat(((Number) rows.getFirst()[1]).longValue()).isEqualTo(1L);
    }

    @Test
    void navigatingASubclassFieldInAPredicateCannotReachAnotherTenant() {
        // The predicate form is the one three shipped CDA paths used (ShCdaTenantJoinRegressionTest).
        // Naming the OTHER tenant's post type is the probe: the row exists, under beta, and the
        // tenant predicate on the joined root is what has to keep it out.
        assertThat(asTenant(alpha, () -> titlesOfType(betaType)))
                .as("SH1290: a name predicate on the joined post type reached another tenant's row")
                .isEmpty();
        assertThat(asTenant(alpha, () -> titlesOfType(alphaType)))
                .containsExactly("post of " + alphaType);
    }

    @Test
    void anExplicitJoinReadsOnlyThisTenant() {
        assertThat(asTenant(beta, () -> entityManager
                .createQuery("select t.name from ShPostUnified p join p.shPostType t", String.class)
                .getResultList()))
                .as("SH1290: the explicit join is the same statement, and filters the same way")
                .containsExactly(betaType);
    }

    @Test
    void filteringByTheAssociationIdIsFine() {
        assertThat(asTenant(alpha, () -> entityManager
                .createQuery("select p from ShPostUnified p where p.shPostType.id = :id",
                        ShPostUnified.class)
                .setParameter("id", "nothing-matches")
                .getResultList()))
                .isEmpty();
    }

    @Test
    void projectingOnlyTheAssociationIdIsFine() {
        // The form ShTenantJoinLintTest still asks for: the FK column already lives on
        // sh_post_unified, so there is no join at all.
        assertThat(asTenant(alpha, () -> entityManager
                .createQuery("select p.shPostType.id, count(distinct p.id) from ShPostUnified p "
                        + "group by p.shPostType.id", Object[].class)
                .getResultList()))
                .hasSize(1);
    }

    private List<String> titlesOfType(String typeName) {
        return entityManager
                .createQuery("select p.title from ShPostUnified p where p.shPostType.name = :name",
                        String.class)
                .setParameter("name", typeName)
                .getResultList();
    }

    private String persistTypeWithPost(String typeName) {
        ShPostType type = new ShPostType();
        type.setName(typeName);
        type.setTitle(typeName);
        type.setSystem((byte) 0);
        type.setDate(Instant.parse("2026-01-01T00:00:00Z"));
        type.setOwner("admin");
        entityManager.persist(type);

        ShPostUnified post = new ShPostUnified();
        post.setId(UUID.randomUUID().toString());
        post.setState(ShPostState.DRAFT);
        post.setTitle("post of " + typeName);
        post.setShPostType(type);
        post.setData("{}");
        entityManager.persist(post);
        return type.getId();
    }

    /**
     * Runs {@code work} in a new transaction with {@code tenant} bound, the way
     * {@code ShTenantIsolationIT} does: Hibernate resolves the tenant once per session open.
     */
    private <T> T asTenant(String tenant, Supplier<T> work) {
        ShTenantContext.setTenantId(tenant);
        try {
            return new TransactionTemplate(transactionManager).execute(status -> work.get());
        } finally {
            ShTenantContext.clear();
        }
    }
}
