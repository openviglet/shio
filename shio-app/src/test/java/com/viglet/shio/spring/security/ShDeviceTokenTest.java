/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.spring.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.viglet.shio.persistence.model.token.ShApiToken;
import com.viglet.shio.persistence.model.token.ShApiTokenScope;
import com.viglet.shio.persistence.repository.token.ShApiTokenRepository;

/**
 * SH1351 — a device token is the person who minted it, until it is revoked, and never a machine.
 *
 * <p>Asserted from the outside through the real filter chain: a unit test over the filter would
 * restate it. The two claims the task names are the two that matter: a revoked device answers 401
 * on its next call, and a machine token cannot reach the review queue or mint a person.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShDeviceTokenTest {

    private static final String DEVICE_PATH = "/api/v2/device-token";
    private static final String REVIEW_PENDING = "/api/v2/review/pending";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ShApiTokenRepository shApiTokenRepository;

    private final ObjectMapper mapper = new ObjectMapper();

    private MockMvc mvc;

    @BeforeAll
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    }

    private JsonNode mint(String deviceName) throws Exception {
        MvcResult result = mvc.perform(post(DEVICE_PATH).with(user("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceName\":\"" + deviceName + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private static String bearer(String secret) {
        return "Bearer " + secret;
    }

    @Test
    @DisplayName("SH1351: a minted device token reaches what the person reaches, review queue included")
    void aDeviceTokenIsThePerson() throws Exception {
        JsonNode minted = mint("Pixel");
        String secret = minted.get("token").asString();
        assertThat(secret).startsWith(ShDeviceTokenFilter.PREFIX);

        mvc.perform(get(REVIEW_PENDING).header("Authorization", bearer(secret)))
                .andExpect(status().isOk());
        // A write with no session cookie and no CSRF token: the review queue's narrowed exemption
        // is what lets the phone decide, exactly as it lets a Basic script.
        mvc.perform(post("/api/v2/review/sessions/no-such-run/revert")
                        .header("Authorization", bearer(secret)))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("authenticated and past CSRF: anything but 401/403")
                        .isNotIn(401, 403));

        mvc.perform(get(DEVICE_PATH).header("Authorization", bearer(secret)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + minted.get("id").asString() + "')].current").value(true));
    }

    @Test
    @DisplayName("SH1351: a revoked device token answers 401 on its next call")
    void aRevokedDeviceTokenAnswers401() throws Exception {
        JsonNode minted = mint("iPhone");
        String secret = minted.get("token").asString();

        mvc.perform(get(REVIEW_PENDING).header("Authorization", bearer(secret)))
                .andExpect(status().isOk());
        // The app's own sign-out: a device may revoke itself, statelessly, with no CSRF token.
        mvc.perform(delete(DEVICE_PATH + "/" + minted.get("id").asString())
                        .header("Authorization", bearer(secret)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revokedBy").value("admin"));

        mvc.perform(get(REVIEW_PENDING).header("Authorization", bearer(secret)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.fix").exists());
        // Even on a public route: a revoked device must learn it at once, not read a 200.
        mvc.perform(get("/api/v2/ping").header("Authorization", bearer(secret)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("SH1351: a device token cannot mint another one")
    void aDeviceCannotExtendItself() throws Exception {
        String secret = mint("Tablet").get("token").asString();
        mvc.perform(post(DEVICE_PATH).header("Authorization", bearer(secret))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceName\":\"Clone\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.fix").exists());
    }

    @Test
    @DisplayName("SH1351: a machine token never reaches a review decision nor mints a person")
    void aMachineTokenIsNotAPerson() throws Exception {
        String key = "sh1351-" + UUID.randomUUID();
        ShApiToken agent = new ShApiToken();
        agent.setTitle("sh1351 agent");
        agent.setToken(key);
        agent.setOwner("admin");
        agent.setScope(ShApiTokenScope.AGENT);
        agent.setCreatedDate(Instant.now());
        shApiTokenRepository.saveAndFlush(agent);
        try {
            mvc.perform(post("/api/v2/review/sessions/any/approve").header("Key", key))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/api/v2/review/sessions/any/approve").header("Authorization", bearer(key)))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post(DEVICE_PATH).header("Key", key)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"deviceName\":\"Agent\"}"))
                    .andExpect(status().isUnauthorized());
        } finally {
            shApiTokenRepository.delete(agent);
        }
    }

    @Test
    @DisplayName("SH1351: an unknown device token is refused, and a device name is required")
    void unknownAndUnnamed() throws Exception {
        mvc.perform(get(REVIEW_PENDING).header("Authorization", bearer(ShDeviceTokenFilter.newSecret())))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(DEVICE_PATH).with(user("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceName\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.example").exists());
    }
}
