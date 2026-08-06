/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.api.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.persistence.model.token.ShApiToken;
import com.viglet.shio.persistence.repository.token.ShApiTokenRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH188 — a row whose {@code may_publish} is NULL must read as <em>not granted</em>,
 * not as a 500.
 *
 * <p>{@code v2026.3.11} added the column nullable with no default and no backfill,
 * recording the intent that it "read as {@code false} when absent", while
 * {@link ShApiToken} mapped it as a primitive {@code boolean} — which cannot express
 * absent. Hibernate throws {@code IllegalArgumentException} hydrating NULL into a
 * primitive, so on any install that held a token before SH107 landed, listing tokens
 * and minting a preview token both failed. Preview was dead for a reason of its own,
 * independent of the missing {@code site.url} that SH184 is about.</p>
 *
 * <p>Nothing caught it because every other test in this package is a Mockito unit test
 * that constructs {@link ShApiToken} in Java and never hydrates one — the value is
 * always the field initialiser, never the column. So this test goes the only way that
 * reproduces it: write the NULL with native SQL, clear the persistence context, and
 * read the row back the way the endpoints do.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@Transactional
class ShApiTokenMayPublishNullTest {

    /** Matches the ShTenantContext default; a NULL tenant is invisible under @TenantId. */
    private static final String TENANT = "shio";

    private static final String LEGACY_ID = "sh188-legacy-token";

    @Autowired
    private ShApiTokenRepository shApiTokenRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Writes the row an install that predates SH107 has: everything the entity needs,
     * with {@code may_publish} left NULL exactly as the changeset leaves it.
     */
    @BeforeEach
    void insertPreSh107Row() {
        entityManager.createNativeQuery(
                        "insert into api_token (id, tenant_id, title, token, enabled, ephemeral, may_publish) "
                                + "values (?1, ?2, ?3, ?4, ?5, ?6, null)")
                .setParameter(1, LEGACY_ID)
                .setParameter(2, TENANT)
                .setParameter(3, "SH188 legacy token")
                .setParameter(4, "sh188legacytokenvalue0001")
                .setParameter(5, true)
                .setParameter(6, false)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("a NULL may_publish hydrates as not-granted instead of throwing")
    void nullMayPublishHydratesAsFalse() {
        assertThatCode(() -> shApiTokenRepository.findById(LEGACY_ID))
                .as("hydrating a pre-SH107 row must not blow up on the primitive/NULL mismatch")
                .doesNotThrowAnyException();

        ShApiToken token = shApiTokenRepository.findById(LEGACY_ID).orElseThrow();
        assertThat(token.isMayPublish())
                .as("absent is what 'not granted' looks like on a pre-SH107 row — SH107's "
                        + "gate is off unless the token says otherwise")
                .isFalse();
    }

    @Test
    @DisplayName("listing tokens survives a pre-SH107 row — the endpoint that used to 500")
    void listingTokensSurvivesALegacyRow() {
        List<ShApiToken> tokens = shApiTokenRepository.findByEphemeralFalseOrderByTitleAsc();

        assertThat(tokens)
                .as("GET /api/v2/api-token reads exactly this finder")
                .extracting(ShApiToken::getId)
                .contains(LEGACY_ID);
        assertThat(tokens)
                .filteredOn(t -> LEGACY_ID.equals(t.getId()))
                .allSatisfy(t -> assertThat(t.isMayPublish()).isFalse());
    }

    @Test
    @DisplayName("granting it afterwards writes a real value, so the NULL is not sticky")
    void grantingReplacesTheNull() {
        ShApiToken token = shApiTokenRepository.findById(LEGACY_ID).orElseThrow();
        token.setMayPublish(true);
        shApiTokenRepository.saveAndFlush(token);
        entityManager.clear();

        assertThat(shApiTokenRepository.findById(LEGACY_ID).orElseThrow().isMayPublish())
                .as("an operator grant has to survive the round trip through the column")
                .isTrue();
    }
}
