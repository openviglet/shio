/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.viglet.shio.persistence.model.auth.ShUser;
import com.viglet.shio.persistence.repository.auth.ShUserRepository;
import com.viglet.shio.property.ShAppProperties;

/**
 * SH1427 — the first-access screen never exists behind an identity provider.
 *
 * <p>Seen on shio.viglet.cloud: a self-registered, non-admin account landed on {@code /setup},
 * offered the instance's administrator password, because the Keycloak instance's local admin has no
 * password by design and "unset" read as "pending" forever.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
class ShSetupBehindIdentityProviderTest {

    @Test
    @DisplayName("SH1427: on a Keycloak instance setup is never pending, and the console never routes to /setup")
    void setupIsNeverPendingBehindKeycloak() {
        ShUserRepository users = adminWithoutPassword();

        assertThat(service(users, true).isSetupRequired()).as("SH1427").isFalse();
        assertThat(new ShSetupAPI(service(users, true)).status().getBody())
                .as("SH1427: what the login screen reads before sending a person to /setup")
                .containsEntry("required", false);
        assertThat(service(users, false).isSetupRequired())
                .as("local login with no admin password still has its first-access screen").isTrue();
    }

    @Test
    @DisplayName("SH1427: setting the first password on a Keycloak instance is refused with a problem")
    void theFirstPasswordIsRefusedBehindKeycloak() {
        ShUserRepository users = adminWithoutPassword();
        ShSetupAPI api = new ShSetupAPI(service(users, true));

        assertThatThrownBy(() -> api.setup(Map.of("password", "taken-by-whoever-asks")))
                .isInstanceOf(ShSetupAPI.SetupUnavailableException.class)
                .satisfies(thrown -> assertThat(((ShSetupAPI.SetupUnavailableException) thrown)
                        .problem().toProblemDetail().getStatus()).as("SH1427").isEqualTo(403));
        verify(users, never()).saveAndFlush(ArgumentMatchers.any());
    }

    private static ShUserRepository adminWithoutPassword() {
        ShUserRepository users = mock(ShUserRepository.class);
        ShUser admin = new ShUser();
        admin.setUsername(ShAdminPasswordService.ADMIN);
        when(users.findByUsername(ShAdminPasswordService.ADMIN)).thenReturn(admin);
        return users;
    }

    private static ShAdminPasswordService service(ShUserRepository users, boolean keycloak) {
        ShAppProperties properties = new ShAppProperties();
        properties.setKeycloak(keycloak);
        return new ShAdminPasswordService(users, mock(PasswordEncoder.class), properties);
    }
}
