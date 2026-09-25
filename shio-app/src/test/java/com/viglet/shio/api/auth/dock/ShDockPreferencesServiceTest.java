/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.auth.dock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.viglet.shio.api.auth.dock.ShDockPreferencesService.ShDockPreferences;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.persistence.model.auth.ShUser;
import com.viglet.shio.persistence.repository.auth.ShUserRepository;

/**
 * SH971 — a curator's dock preferences round-trip through the user record.
 *
 * <p>Through the real repository on purpose: the column is new, and a changeset that does not
 * create it passes every test that never reads it (Liquibase is invisible to a suite that mocks the
 * repository). A save and a read here is what proves {@code ShUser.dockPreferences} exists.</p>
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShDockPreferencesServiceTest {

    @Autowired
    private ShDockPreferencesService service;

    @Autowired
    private ShUserRepository userRepository;

    private String username;

    @BeforeAll
    void setup() {
        username = "sh971-" + UUID.randomUUID().toString().substring(0, 8);
        ShUser user = new ShUser();
        user.setUsername(username);
        user.setEnabled(1);
        userRepository.save(user);
    }

    @Test
    void sh971ANewCuratorHasNothingMuted() {
        assertThat(service.of(username).muted())
                .as("SH971: a curator who never chose must get every report, as before this existed")
                .isEmpty();
    }

    @Test
    void sh971APreferenceIsStoredSortedAndReadBack() {
        service.save(username, new ShDockPreferences(List.of("schedules", "arrivals", "arrivals")));

        assertThat(service.of(username).muted())
                .as("SH971: the choice did not survive a round trip through ShUser.dockPreferences,"
                        + " or it is not sorted and deduplicated, so one choice has two spellings (P5)")
                .containsExactly("arrivals", "schedules");
    }

    @Test
    void sh971AnUnknownKindTeachesTheSetAndTheNearestMatch() {
        assertThatThrownBy(() -> service.save(username, new ShDockPreferences(List.of("arrival"))))
                .isInstanceOf(ShTeachingException.class)
                .satisfies(thrown -> {
                    var problem = ((ShTeachingException) thrown).problem();
                    assertThat(problem.toProblemDetail().getProperties())
                            .as("SH971: the refusal must list the kinds (P6)")
                            .containsEntry("allowed", ShDockPreferencesService.KINDS);
                    assertThat(problem.didYouMean()).isEqualTo("arrivals");
                });
    }
}
