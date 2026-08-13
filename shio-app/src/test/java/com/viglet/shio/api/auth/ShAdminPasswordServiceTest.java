/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.viglet.shio.persistence.model.auth.ShUser;
import com.viglet.shio.persistence.repository.auth.ShUserRepository;
import com.viglet.shio.property.ShAppProperties;

import org.mockito.Mockito;

/**
 * SH706 — the boundary the defect crossed: a seeded {@code admin} with a null password that only
 * a browser could fix.
 *
 * <p>Written against the service rather than through the endpoint on purpose. The endpoint was
 * never the problem: it worked, and it was the <em>only</em> door. What has to be asserted is that
 * a second door exists, that it opens on a fresh instance, and — the part that makes it safe to
 * leave in a compose file — that it stays shut everywhere else.</p>
 *
 * <p>No Spring context. This is a service with three collaborators and every rule in it is a
 * branch, so a context here would buy nothing and cost a slot {@code ShContextCacheHeadroomTest}
 * is counting (SH315).</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
class ShAdminPasswordServiceTest {

    private ShUserRepository repository;
    private ShAppProperties properties;
    private ShAdminPasswordService service;
    private ShUser admin;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(ShUserRepository.class);
        properties = new ShAppProperties();
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        service = new ShAdminPasswordService(repository, encoder, properties);

        // The seed exactly as `ShUserOnStartup` writes it: an account, and no password.
        admin = new ShUser();
        admin.setUsername(ShAdminPasswordService.ADMIN);
        admin.setPassword(null);
        Mockito.when(repository.findByUsername(ShAdminPasswordService.ADMIN)).thenReturn(admin);
    }

    @Test
    void bootstrapMakesASeededInstanceUsable() {
        properties.getAdmin().setPassword("s3cret-from-compose");

        service.bootstrapFromConfiguration();

        assertThat(admin.getPassword())
                .as("SH706: this is the whole task. `ShUserOnStartup` seeds `admin` with a null "
                        + "password and, before this, `POST /api/v2/setup` behind the console's "
                        + "first-access screen was the only writer — so a container started in a "
                        + "state no script could leave, every surface answering 401. If this is "
                        + "null, an automated deploy cannot reach its first correct call and P1 is "
                        + "broken on the one capability that gates every other one.")
                .isNotNull();
        assertThat(new BCryptPasswordEncoder().matches("s3cret-from-compose", admin.getPassword()))
                .as("SH706: the configured value must be usable as a login and stored encoded — a "
                        + "bootstrap that wrote the raw string would put the compose file's secret "
                        + "in the database in clear.")
                .isTrue();
        assertThat(service.isSetupRequired())
                .as("SH706: after the bootstrap the first-access screen must be skipped, which is "
                        + "the observable half of the feature.")
                .isFalse();
    }

    @Test
    void bootstrapNeverOverwritesAPasswordSomebodyAlreadySet() {
        admin.setPassword("$2a$10$alreadyConfiguredHashPlaceholderValue000000000000000000");
        String before = admin.getPassword();
        properties.getAdmin().setPassword("s3cret-from-compose");

        service.bootstrapFromConfiguration();

        assertThat(admin.getPassword())
                .as("SH706: an operator who changed the password in the console must not have it "
                        + "reverted by their own deployment. The variable stays in the compose file "
                        + "forever, so every restart would re-apply it — this is what makes the "
                        + "feature safe to leave there rather than something to remember to remove.")
                .isEqualTo(before);
    }

    @Test
    void bootstrapNeverFailsStartup() {
        // Every reason not to write is a log line and a return. A typo in an env var must not be an
        // outage, and `ShOnStartup` is an ApplicationRunner — a throw here halts boot.
        properties.getAdmin().setPassword("  ");
        assertThat(catchThrowableFrom(service)).as("SH706: a blank value is a no-op").isNull();

        properties.getAdmin().setPassword("short");
        assertThat(catchThrowableFrom(service)).as("SH706: a too-short value is a no-op").isNull();
        assertThat(admin.getPassword())
                .as("SH706: a value under the minimum must leave the account untouched, so the "
                        + "first-access screen is still the way in rather than a half-set password.")
                .isNull();

        properties.getAdmin().setPassword(null);
        assertThat(catchThrowableFrom(service)).as("SH706: an unset value is a no-op").isNull();
    }

    @Test
    void theRuleTheScreenEnforcesIsTheRuleTheBootstrapEnforces() {
        assertThatThrownBy(() -> service.setPassword("short"))
                .as("SH706: one owner, one minimum. The setup endpoint delegates here, so a "
                        + "password the screen refuses is one the bootstrap refuses — two code "
                        + "paths encoding the same field is the shape SH245 is open about.")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(String.valueOf(ShAdminPasswordService.PASSWORD_MINIMUM_SIZE));

        assertThatThrownBy(() -> service.setPassword(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Throwable catchThrowableFrom(ShAdminPasswordService service) {
        try {
            service.bootstrapFromConfiguration();
            return null;
        } catch (Exception e) {
            return e;
        }
    }
}
