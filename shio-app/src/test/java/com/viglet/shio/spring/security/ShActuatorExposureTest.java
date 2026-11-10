/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.spring.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * SH1436 — the actuator is an operator's, not a user's.
 *
 * <p>{@code management.endpoints.web.exposure.include=*} mapped every endpoint, and past sign-in
 * nothing narrowed it: any principal (every free sign-up on a hosted instance) could download
 * {@code /actuator/heapdump}, the JVM's memory with every secret in it, and {@code POST
 * /actuator/loggers}. Through the real filter chain, because the refusal is the chain's.</p>
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShActuatorExposureTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private FilterChainProxy springSecurityFilterChain;
    private MockMvc mockMvc;

    @BeforeAll
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @Test
    @DisplayName("SH1436: the shipped exposure is health and info, pinned")
    void theExposureIsHealthAndInfo() throws java.io.IOException {
        // Read from the file that ships, not from this context: the suite has its own properties,
        // and the instance an operator runs reads this one.
        java.util.Properties shipped = new java.util.Properties();
        try (var in = java.nio.file.Files.newBufferedReader(
                java.nio.file.Path.of("src", "main", "resources", "application.properties"))) {
            shipped.load(in);
        }
        assertThat(shipped.getProperty("management.endpoints.web.exposure.include"))
                .as("SH1436: widening this maps heapdump, env and loggers again")
                .isEqualTo("health,info");
    }

    @Test
    @DisplayName("SH1436: a signed-in non-admin reaches no heap dump, environment or log levels")
    void aSignedInUserReachesNoOperatorEndpoint() throws Exception {
        for (String path : new String[] {"/actuator/heapdump", "/actuator/env", "/actuator/loggers"}) {
            int status = mockMvc.perform(MockMvcRequestBuilders.get(path).with(editor()))
                    .andReturn().getResponse().getStatus();
            assertThat(status).as("SH1436: %s answered a signed-in non-admin", path).isIn(403, 404);
        }
        int changed = mockMvc.perform(MockMvcRequestBuilders.post("/actuator/loggers/ROOT")
                        .with(editor()).with(csrf())
                        .contentType("application/json").content("{\"configuredLevel\":\"TRACE\"}"))
                .andReturn().getResponse().getStatus();
        assertThat(changed).as("SH1436: a signed-in non-admin changed the log level").isIn(403, 404);
    }

    @Test
    @DisplayName("SH1436: health still answers a signed-in caller")
    void healthStillAnswers() throws Exception {
        int status = mockMvc.perform(MockMvcRequestBuilders.get("/actuator/health").with(editor()))
                .andReturn().getResponse().getStatus();
        assertThat(status).as("SH1436").isNotIn(401, 403, 404);
    }

    @Test
    @DisplayName("SH1436: even an administrator finds no heap dump, because it is not exposed")
    void anAdminFindsNoHeapDump() throws Exception {
        int status = mockMvc.perform(MockMvcRequestBuilders.get("/actuator/heapdump")
                        .with(user("sh1436-admin").roles("ADMIN")))
                .andReturn().getResponse().getStatus();
        assertThat(status).as("SH1436: the endpoint is not mapped at all").isEqualTo(404);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor editor() {
        return user("sh1436-editor").roles("USER");
    }
}
