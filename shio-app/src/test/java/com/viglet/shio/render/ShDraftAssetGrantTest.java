/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SH1282 — the grant a preview frame reads a draft asset with is bound to one site, one path and a
 * few minutes, and to nothing else. A capability that leaked past any of the three would make a draft
 * readable by whoever held a URL, which is the one thing the public route must never do.
 *
 * @author Alexandre Oliveira
 */
class ShDraftAssetGrantTest {

    private static final byte[] KEY = "a-fixed-test-key-of-thirty-two-b".getBytes();
    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");

    private static ShDraftAssetGrant at(Instant when) {
        return new ShDraftAssetGrant(KEY, Clock.fixed(when, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("SH1282: a grant admits the site and path it was minted for, path spelled either way")
    void aGrantAdmitsItsOwnSubject() {
        String grant = at(NOW).mint("quilhara", "/img/draft.png");
        assertThat(at(NOW).admits("quilhara", "/img/draft.png", grant)).isTrue();
        assertThat(at(NOW).admits("quilhara", "img/draft.png", grant)).isTrue();
    }

    @Test
    @DisplayName("SH1282: a grant reads no other asset and no other site")
    void aGrantIsBoundToItsSubject() {
        String grant = at(NOW).mint("quilhara", "/img/draft.png");
        assertThat(at(NOW).admits("quilhara", "/img/other.png", grant)).isFalse();
        assertThat(at(NOW).admits("other-site", "/img/draft.png", grant)).isFalse();
    }

    @Test
    @DisplayName("SH1282: a grant expires on its own, and a forged or altered one is refused")
    void aGrantExpiresAndCannotBeForged() {
        String grant = at(NOW).mint("quilhara", "/img/draft.png");
        assertThat(at(NOW.plus(ShDraftAssetGrant.TTL).plusSeconds(1)).admits("quilhara", "/img/draft.png", grant))
                .as("SH1282: past its expiry, a URL in somebody's history reads nothing")
                .isFalse();
        String later = Long.toString(NOW.plusSeconds(86_400).getEpochSecond());
        assertThat(at(NOW).admits("quilhara", "/img/draft.png", later + grant.substring(grant.indexOf('.'))))
                .as("SH1282: the expiry is signed, so extending it breaks the signature")
                .isFalse();
        assertThat(at(NOW).admits("quilhara", "/img/draft.png", "1.forged")).isFalse();
        assertThat(at(NOW).admits("quilhara", "/img/draft.png", null)).isFalse();
        assertThat(new ShDraftAssetGrant().admits("quilhara", "/img/draft.png", grant))
                .as("SH1282: another process's key admits nothing it did not mint")
                .isFalse();
    }
}
