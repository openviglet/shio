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
        username = freshUser();
    }

    /** A user of its own, for a test that must not see what another test saved (PER_CLASS). */
    private String freshUser() {
        String name = "sh971-" + UUID.randomUUID().toString().substring(0, 8);
        ShUser user = new ShUser();
        user.setUsername(name);
        user.setEnabled(1);
        userRepository.save(user);
        return name;
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
    void sh971AStillMascotIsStoredBesideTheMutedKinds() {
        String own = freshUser();
        service.save(own, new ShDockPreferences(List.of("saves"), true));

        ShDockPreferences read = service.of(own);
        assertThat(read.still())
                .as("SH971: the curator froze the mascot and the choice did not survive the round trip")
                .isTrue();
        assertThat(read.muted()).as("SH971: freezing the mascot must not touch the muted kinds").containsExactly("saves");
    }

    @Test
    void sh971ADocumentWrittenBeforeStillExistedReadsAsAnimated() {
        String own = freshUser();
        ShUser user = userRepository.findByUsername(own);
        user.setDockPreferences("{\"muted\":[\"arrivals\"]}");
        userRepository.save(user);

        ShDockPreferences read = service.of(own);
        assertThat(read.still())
                .as("SH971: a stored choice from before the field existed must read as the dock was then")
                .isFalse();
        assertThat(read.muted()).containsExactly("arrivals");
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
