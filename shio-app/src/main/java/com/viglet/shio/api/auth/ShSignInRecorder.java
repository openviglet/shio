/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.auth;

import java.time.Duration;
import java.time.Instant;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.persistence.model.auth.ShUser;
import com.viglet.shio.persistence.repository.auth.ShUserRepository;

/**
 * SH1238 — when a person last signed in.
 *
 * <p>{@code ShUser.lastLogin} was written once, by the startup seed, so the users list could not say
 * who had stopped using the instance. It is written here, and only on a sign-in a person makes: the
 * console's {@code POST /api/v2/login}, and an OAuth2 login under Keycloak, which Spring announces as
 * an {@link InteractiveAuthenticationSuccessEvent}. An HTTP Basic or API-token call authenticates
 * every request it makes and is a call, not a sign-in; recording those would put a database write
 * on the path an agent drives, once per request.</p>
 *
 * <p>Throttled to one write per person per {@link #EVERY}: a burst of re-authentications is one
 * sign-in as far as anybody reading the list is concerned.</p>
 */
@Service
public class ShSignInRecorder {

    /** How stale a recorded sign-in may be before another one is written. */
    static final Duration EVERY = Duration.ofHours(1);

    private final ShUserRepository shUserRepository;

    public ShSignInRecorder(ShUserRepository shUserRepository) {
        this.shUserRepository = shUserRepository;
    }

    /** @return whether a write happened, which the throttle decides */
    @Transactional
    public boolean record(String username, Instant at) {
        if (username == null || username.isBlank()) {
            return false;
        }
        ShUser user = shUserRepository.findByUsername(username);
        if (user == null) {
            return false;
        }
        Instant last = user.getLastLogin();
        if (last != null && last.isAfter(at.minus(EVERY))) {
            return false;
        }
        user.setLastLogin(at);
        shUserRepository.save(user);
        return true;
    }

    /** The OAuth2 login's success, which only an interactive sign-in raises. */
    @EventListener
    public void onInteractiveSignIn(InteractiveAuthenticationSuccessEvent event) {
        record(event.getAuthentication().getName(), Instant.now());
    }
}
