/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio;

import jakarta.persistence.EntityManager;

/**
 * Puts a fixture into the state a post row written <b>before</b> SH719 is in.
 *
 * <h2>Why a duplicate still has to be constructible</h2>
 *
 * <p>SH719 made a second live post at one address impossible — which is the point, and which also
 * took away the fixture three tests need. {@code ShAddressResolver.DuplicateUrlException} (SH321),
 * the {@code duplicate-url} finding in {@code verify} (SH99) and the locale-scoped read's
 * determinism on a contested URL (SH323) are all about <em>rows that already exist</em>, and they
 * still have to work: {@code ShPostUrlKeyBackfillRunner} deliberately leaves a losing row's
 * {@code url_key} null rather than refusing to start, so an instance that met SH719 keeps its
 * duplicates and keeps needing them reported.</p>
 *
 * <p>So this is not a way around the constraint — it is the only honest way to <em>reach</em> the
 * state the constraint cannot prevent retroactively. Nulling the key is exactly what the backfill
 * does to the row that loses, which is why the fixture is one line of native SQL and not a second
 * mechanism.</p>
 *
 * <p>Native, and it has to be: {@code url_key} has no setter and is recomputed by
 * {@code @PrePersist}/{@code @PreUpdate}, so any JPA write would put the key straight back. That
 * absence is the guarantee for production code (no writer can forget the key) and the reason a test
 * has to go under it.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShLegacyAddressKeys {

    private ShLegacyAddressKeys() {
    }

    /**
     * Un-keys every row at {@code furl}, leaving them as a pre-SH719 duplicate pair.
     *
     * <p>Called inside the same transaction that persisted them, <b>after</b> a {@code flush()}: the
     * insert has to have reached the database for a native update to see the rows at all.</p>
     *
     * @return how many rows were un-keyed, so a fixture can assert it built what it meant to
     */
    public static int unkey(EntityManager entityManager, String furl) {
        entityManager.flush();
        return entityManager
                .createNativeQuery("update sh_post_unified set url_key = null where furl = :furl")
                .setParameter("furl", furl)
                .executeUpdate();
    }
}
