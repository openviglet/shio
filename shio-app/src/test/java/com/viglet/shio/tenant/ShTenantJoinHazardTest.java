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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH122 — characterization of the SH22 {@code @TenantId} join hazard, and the
 * evidence behind {@link ShTenantJoinLintTest}'s rule.
 *
 * <p>Context: {@code @TenantId} lives on the <b>JOINED root</b> {@code ShObject}.
 * {@code ShSite}, {@code ShFolder} and {@code ShPostType} are its subclasses, so
 * their own tables carry no {@code tenant_id} column. When a query makes Hibernate
 * emit the tenant discriminator predicate against a <em>subclass</em> table alias,
 * the statement cannot even be prepared.</p>
 *
 * <p>These tests exist to record <b>where the line actually is</b>, pinned by
 * execution rather than by reasoning — which mattered, because reasoning got it
 * wrong. The first hypothesis was that only projections and {@code GROUP BY} were
 * affected, since shipped queries navigated {@code p.shPostType.name} in a
 * {@code WHERE} clause and their tests were green. Executing the predicate form
 * showed it fails too: those tests were green because they never reached the finder.
 * The rule is therefore the widest one — <b>never navigate past {@code .id}</b>, in
 * any clause — and {@link ShTenantJoinLintTest} enforces exactly that.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
@Transactional(readOnly = true)
class ShTenantJoinHazardTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void navigatingASubclassFieldInAProjectionFails() {
        // The form that broke the SH77 context pack.
        assertThatThrownBy(() -> entityManager
                .createQuery("select p.shPostType.name from ShPostUnified p", String.class)
                .setMaxResults(1)
                .getResultList())
                .satisfies(thrown -> assertThat(rootCauseMessage(thrown))
                        .contains("tenant_id")
                        .contains("not found"));
    }

    @Test
    void navigatingASubclassFieldInAGroupByFails() {
        assertThatThrownBy(() -> entityManager
                .createQuery("select p.shPostType.name, count(distinct p.id) from ShPostUnified p "
                        + "group by p.shPostType.name")
                .getResultList())
                .satisfies(thrown -> assertThat(rootCauseMessage(thrown))
                        .contains("tenant_id")
                        .contains("not found"));
    }

    @Test
    void navigatingASubclassFieldInAPredicateAlsoFails() {
        // The clause does not matter — this was the wrong hypothesis. Three shipped CDA
        // paths filtered by post-type *name* in a WHERE and were broken in production
        // (see ShCdaTenantJoinRegressionTest); they passed review precisely because
        // "it's only a predicate" sounds safe. The generated SQL shows why nothing is
        // safe: Hibernate joins only the subclass table (`ShPostType spt1_0`) yet emits
        // `and spt1_1.tenant_id = ?` against a root alias it never declared.
        assertThatThrownBy(() -> entityManager
                .createQuery("select p from ShPostUnified p where p.shPostType.name = :name")
                .setParameter("name", "nothing-matches")
                .setMaxResults(1)
                .getResultList())
                .satisfies(thrown -> assertThat(rootCauseMessage(thrown)).contains("tenant_id"));
    }

    @Test
    void filteringByTheAssociationIdIsFine() {
        assertThatCode(() -> entityManager
                .createQuery("select p from ShPostUnified p where p.shPostType.id = :id")
                .setParameter("id", "nothing-matches")
                .setMaxResults(1)
                .getResultList())
                .doesNotThrowAnyException();
    }

    @Test
    void projectingOnlyTheAssociationIdIsFine() {
        // The fix: the FK column already lives on sh_post_unified, so there is no join
        // at all — safe *and* cheaper than the form it replaces.
        assertThatCode(() -> entityManager
                .createQuery("select p.shPostType.id, count(distinct p.id) from ShPostUnified p "
                        + "group by p.shPostType.id")
                .getResultList())
                .doesNotThrowAnyException();
    }

    @Test
    void anExplicitJoinDoesNotRescueTheProjection() {
        // Spelling the join out does not change which alias the tenant predicate
        // targets, so "just add a join fetch" is not a workaround — the only fix is to
        // stop reaching past the id.
        assertThatThrownBy(() -> entityManager
                .createQuery("select t.name from ShPostUnified p join p.shPostType t", String.class)
                .setMaxResults(1)
                .getResultList())
                .satisfies(thrown -> assertThat(rootCauseMessage(thrown)).contains("tenant_id"));
    }

    private static String rootCauseMessage(Throwable thrown) {
        Throwable cause = thrown;
        StringBuilder all = new StringBuilder();
        while (cause != null) {
            all.append(cause.getMessage()).append(" | ");
            cause = cause.getCause();
        }
        return all.toString();
    }
}
