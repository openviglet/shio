/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.auth.dock;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.persistence.model.auth.ShUser;
import com.viglet.shio.persistence.repository.auth.ShUserRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * SH971 — what the console's dock reports to one person.
 *
 * <p>Once every kind of event reaches the dock, the dock is how a notification centre becomes
 * something people learn to ignore: an author wants their saves, a reviewer wants agent runs, an
 * operator wants schedules and form arrivals. So each curator mutes the kinds they do not want.
 * Stored on the user, so the choice follows them across devices.</p>
 *
 * <p>Muting a kind never hides its record, and never hides a failure: the activity trail and the
 * agent's feed still carry every event, and a report in tone error reaches the dock whatever is
 * muted, because a failure is the report least safe to lose. That rule is the console's to apply;
 * this only keeps the list.</p>
 *
 * <p>The kinds are a closed set, so a typo is a teaching 400 with the set and the nearest match
 * (P6) rather than a mute that silently matches nothing.</p>
 */
@Service
public class ShDockPreferencesService {

    /**
     * The report kinds the dock distinguishes, in the order the settings dialog lists them. A
     * refusal is not one: it is always a failure, and a failure is never muted.
     */
    public static final List<String> KINDS =
            List.of("saves", "agent-runs", "schedules", "arrivals");

    /**
     * What is stored and served: the kinds this person muted, empty meaning "report everything",
     * and whether the mascot stays still. {@code still} stops only the motion: the caption and the
     * live-region announcement report exactly as before (SH971). A document written before the
     * field existed has no {@code still} and reads as false, which is what the dock did then.
     */
    public record ShDockPreferences(List<String> muted, boolean still) {
        public ShDockPreferences {
            muted = muted == null ? List.of() : List.copyOf(muted);
        }

        public ShDockPreferences(List<String> muted) {
            this(muted, false);
        }
    }

    private final ShUserRepository userRepository;
    private final ObjectMapper objectMapper;

    public ShDockPreferencesService(ShUserRepository userRepository, ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ShDockPreferences of(String username) {
        ShUser user = userRepository.findByUsername(username);
        if (user == null || user.getDockPreferences() == null || user.getDockPreferences().isBlank()) {
            return new ShDockPreferences(List.of());
        }
        return objectMapper.readValue(user.getDockPreferences(), ShDockPreferences.class);
    }

    @Transactional
    public ShDockPreferences save(String username, ShDockPreferences preferences) {
        ShUser user = userRepository.findByUsername(username);
        if (user == null) {
            throw new NoUserRecordException(username);
        }
        for (String kind : preferences.muted()) {
            if (!KINDS.contains(kind)) {
                throw new UnknownDockKindException(kind, preferences.muted().indexOf(kind));
            }
        }
        // Sorted and deduplicated, so the same choice is always the same bytes (P5).
        ShDockPreferences stored =
                new ShDockPreferences(new ArrayList<>(new TreeSet<>(preferences.muted())), preferences.still());
        user.setDockPreferences(objectMapper.writeValueAsString(stored));
        userRepository.save(user);
        return stored;
    }

    /** A kind outside the closed set: the set, the nearest match and where it was (P6). */
    public static class UnknownDockKindException extends ShTeachingException {

        public UnknownDockKindException(String kind, int index) {
            super(ShProblem.of("unknown-dock-kind", HttpStatus.BAD_REQUEST,
                            "'" + kind + "' is not a kind the dock reports")
                    .withAllowed(KINDS)
                    .withDidYouMeanFrom(kind)
                    .withAt("muted[" + index + "]")
                    .withFix("Mute one of the listed kinds.")
                    .withExample(new ShDockPreferences(List.of("arrivals"))));
        }
    }

    /** A sign-in with no user row yet, which the console's first open creates. */
    public static class NoUserRecordException extends ShTeachingException {

        public NoUserRecordException(String username) {
            super(ShProblem.of("no-user-record", HttpStatus.NOT_FOUND,
                            "No user record for '" + username + "'")
                    .withDetail("Dock preferences are kept on the user, and this sign-in has none yet.")
                    .withFix("Open the console once, which creates the record, then save again."));
        }
    }
}
