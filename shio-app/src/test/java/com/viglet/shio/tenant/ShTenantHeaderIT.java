/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import tools.jackson.databind.ObjectMapper;

/**
 * SH1378 — a person with no session reaches a tenant of their own.
 *
 * <p>Through the whole chain, on a multi-tenant H2: a device token and a session-less person (what
 * HTTP Basic is to the chain) each sign up with no CSRF token, then name the tenant per request with
 * {@code X-Shio-Tenant} and read it back from {@code /tenant/current}. A person who is not a member
 * naming the same tenant is refused by the shared gate, exactly as a session's switch would be.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShTenantHeaderIT {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:shio-sh1378-it;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.H2Dialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.liquibase.enabled", () -> "true");
        registry.add("spring.liquibase.default-schema", () -> "PUBLIC");
        registry.add("shio.multi-tenant", () -> "true");
    }

    private static final String HEADER = ShVigletTenantResolver.TENANT_HEADER;

    @Autowired
    private WebApplicationContext context;

    private final ObjectMapper mapper = new ObjectMapper();
    private MockMvc mvc;

    @BeforeAll
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static String body(String name) {
        return "{\"name\":\"" + name + "\"}";
    }

    @Test
    @DisplayName("SH1378: a session-less person signs up, then names the tenant per request")
    void aSessionlessPersonSignsUpAndNamesTheTenant() throws Exception {
        MvcResult signup = mvc.perform(post("/api/v2/tenant/signup").with(user("sh1378-alice"))
                        .contentType(MediaType.APPLICATION_JSON).content(body("sh1378-alice-ws")))
                .andReturn();
        assertThat(signup.getResponse().getStatus())
                .as("SH1378: signup with no session and no CSRF token was 403 csrf-token-unobtainable: "
                        + signup.getResponse().getContentAsString())
                .isEqualTo(200);

        mvc.perform(get("/api/v2/tenant/current").with(user("sh1378-alice")).header(HEADER, "sh1378-alice-ws"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("sh1378-alice-ws"));

        MvcResult stranger = mvc.perform(get("/api/v2/tenant/current").with(user("sh1378-bob"))
                        .header(HEADER, "sh1378-alice-ws"))
                .andReturn();
        assertThat(stranger.getResponse().getStatus())
                .as("SH1378: a non-member naming the tenant is refused, never served")
                .isEqualTo(403);
    }

    @Test
    @DisplayName("SH1378: a device token signs up and reaches its tenant by header")
    void aDeviceTokenSignsUpAndNamesTheTenant() throws Exception {
        MvcResult minted = mvc.perform(post("/api/v2/device-token").with(user("admin"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"deviceName\":\"SH1378\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String bearer = "Bearer " + mapper.readTree(minted.getResponse().getContentAsString())
                .get("token").asString();

        MvcResult signup = mvc.perform(post("/api/v2/tenant/signup").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(body("sh1378-device-ws")))
                .andReturn();
        assertThat(signup.getResponse().getStatus())
                .as("SH1378: a device token holds no session, and signup must not ask it for one")
                .isEqualTo(200);

        mvc.perform(get("/api/v2/tenant/current").header("Authorization", bearer)
                        .header(HEADER, "sh1378-device-ws"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("sh1378-device-ws"));
    }
}
