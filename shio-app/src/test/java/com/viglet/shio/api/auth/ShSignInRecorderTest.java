/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.lint.ShSourceScan;
import com.viglet.shio.lint.ShSourceText;
import com.viglet.shio.persistence.model.auth.ShUser;
import com.viglet.shio.persistence.repository.auth.ShUserRepository;

/**
 * SH1238 — a person's last sign-in is recorded, once an hour at most, and a call is not a sign-in.
 */
@ShSourceScan(ShSourceScan.Reads.CODE)
class ShSignInRecorderTest {

    private static final Instant NOON = Instant.parse("2026-10-05T12:00:00Z");

    @Test
    @DisplayName("SH1238: a sign-in moves lastLogin, and another within the hour writes nothing")
    void recordsAndThrottles() {
        ShUserRepository users = mock(ShUserRepository.class);
        ShUser ana = new ShUser();
        ana.setUsername("ana");
        when(users.findByUsername("ana")).thenReturn(ana);
        ShSignInRecorder recorder = new ShSignInRecorder(users);

        assertThat(recorder.record("ana", NOON)).isTrue();
        assertThat(ana.getLastLogin())
                .as("SH1238: the sign-in is when the users list says this person was last here")
                .isEqualTo(NOON);
        assertThat(recorder.record("ana", NOON.plusSeconds(1800)))
                .as("SH1238: a burst of re-authentications is one sign-in, not one write each")
                .isFalse();
        assertThat(recorder.record("ana", NOON.plusSeconds(3601))).isTrue();
        verify(users, times(2)).save(any());
    }

    @Test
    @DisplayName("SH1238: a name with no user, from an outside identity provider, records nothing")
    void unknownNameRecordsNothing() {
        ShUserRepository users = mock(ShUserRepository.class);
        ShSignInRecorder recorder = new ShSignInRecorder(users);
        assertThat(recorder.record("stranger", NOON)).isFalse();
        assertThat(recorder.record(" ", NOON)).isFalse();
        verify(users, never()).save(any());
    }

    @Test
    @DisplayName("SH1238: the console's login records it; a token or Basic call does not")
    void onlyTheSignInPathsRecord() throws IOException {
        Path auth = Path.of("src", "main", "java", "com", "viglet", "shio");
        assertThat(ShSourceText.code(Files.readString(auth.resolve(Path.of("api", "auth", "ShLoginAPI.java")))))
                .as("SH1238: the console's POST /api/v2/login is the sign-in, and it no longer records it")
                .contains("shSignInRecorder.record(request.username()");
        try (var tree = Files.walk(auth)) {
            assertThat(tree.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.getFileName().toString().equals("ShLoginAPI.java"))
                    .filter(p -> !p.getFileName().toString().equals("ShSignInRecorder.java"))
                    .filter(p -> {
                        try {
                            return ShSourceText.code(Files.readString(p)).contains("shSignInRecorder.record(");
                        } catch (IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    }).toList())
                    .as("SH1238: a token or Basic call authenticates every request; recording it "
                            + "would be a write per agent call, and it is a call, not a sign-in")
                    .isEmpty();
        }
    }
}
