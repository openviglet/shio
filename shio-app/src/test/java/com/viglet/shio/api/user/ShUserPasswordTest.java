/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.security.Principal;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.viglet.shio.persistence.model.auth.ShUser;
import com.viglet.shio.persistence.repository.auth.ShUserRepository;

/**
 * SH1334 — a user created through the API stored its password as typed, echoed it back, and could
 * not sign in, because authentication compares against a bcrypt hash. Found live while checking
 * SH1317: Basic auth with the password just set answered 401.
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShUserPasswordTest {

    private static final String USERNAME = "sh1334-curator";
    private static final String PASSWORD = "sh1334-pass-1";

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private ShUserRepository shUserRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;
    private Principal admin;

    @BeforeAll
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        admin = Mockito.mock(Principal.class);
        Mockito.when(admin.getName()).thenReturn("admin");
    }

    @AfterAll
    void tearDown() {
        ShUser made = shUserRepository.findByUsername(USERNAME);
        if (made != null) {
            shUserRepository.delete(made);
        }
    }

    @Test
    @DisplayName("SH1334: a new user's password is stored as a hash that signs in, and never sent back")
    void aNewUsersPasswordIsEncodedAndNeverEchoed() throws Exception {
        String created = mockMvc.perform(post("/api/v2/user/" + USERNAME).principal(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + USERNAME + "\",\"password\":\"" + PASSWORD + "\","
                                + "\"firstName\":\"Ana\",\"lastName\":\"Curator\",\"email\":\"ana@example.invalid\"}"))
                .andReturn().getResponse().getContentAsString();

        assertThat(created)
                .as("SH1334: the create response carried the password, so a caller read back what it typed")
                .doesNotContain(PASSWORD)
                .doesNotContain("\"password\"");

        String stored = shUserRepository.findByUsername(USERNAME).getPassword();
        assertThat(stored)
                .as("SH1334: the password was stored as typed, readable by anyone with the database")
                .isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, stored))
                .as("SH1334: what was stored is not a hash the sign-in check accepts, so the account is locked out")
                .isTrue();

        String listed = mockMvc.perform(get("/api/v2/user").principal(admin))
                .andReturn().getResponse().getContentAsString();
        assertThat(listed)
                .as("SH1334: the user list sends every user's password hash to the console")
                .doesNotContain("\"password\"");
    }

    @Test
    @DisplayName("SH1335: an edit sets a new password when it carries one, and keeps the old one when blank")
    void anEditChangesThePasswordOnlyWhenItCarriesOne() throws Exception {
        String user = "sh1335-editor";
        mockMvc.perform(post("/api/v2/user/" + user).principal(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + user + "\",\"password\":\"first-pass\",\"firstName\":\"Edu\"}"));
        try {
            mockMvc.perform(put("/api/v2/user/" + user).principal(admin).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"" + user + "\",\"password\":\"second-pass\",\"firstName\":\"Edu\"}"));
            assertThat(passwordEncoder.matches("second-pass", shUserRepository.findByUsername(user).getPassword()))
                    .as("SH1335: the password typed into an edit was dropped, so no password can ever be changed")
                    .isTrue();

            mockMvc.perform(put("/api/v2/user/" + user).principal(admin).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"" + user + "\",\"password\":\"\",\"firstName\":\"Eduarda\"}"));
            ShUser edited = shUserRepository.findByUsername(user);
            assertThat(edited.getFirstName()).isEqualTo("Eduarda");
            assertThat(passwordEncoder.matches("second-pass", edited.getPassword()))
                    .as("SH1335: an edit with the password left blank cleared or replaced it, locking the user out")
                    .isTrue();
        } finally {
            ShUser made = shUserRepository.findByUsername(user);
            if (made != null) {
                shUserRepository.delete(made);
            }
        }
    }
}
