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

import java.time.Instant;

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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.tenant.ShTenantRole;
import com.viglet.shio.persistence.model.token.ShApiToken;
import com.viglet.shio.persistence.model.token.ShApiTokenScope;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;
import com.viglet.shio.persistence.repository.token.ShApiTokenRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * SH1379 — a person with no session issues an AGENT token for the tenant they run, and a token
 * never issues one.
 *
 * <p>Through the whole chain, on a multi-tenant H2. It replaces the row
 * {@code ShStatelessReachabilityLintTest} held against {@code /api/v2/api-token}, which said "never"
 * and now says "never for a token, yes for a person"; this class is where both halves are asserted.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentTokenFromDeviceIT {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:shio-sh1379-it;"
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
    private static final String WORKSPACE = "sh1379-ws";
    private static final String OWNER = "sh1379-owner";
    private static final String MEMBER = "sh1379-member";
    private static final String AGENT_BODY = "{\"title\":\"phone agent\",\"scope\":\"AGENT\"}";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private ShTenantMembershipService shTenantMembershipService;
    @Autowired
    private ShSiteRepository shSiteRepository;
    @Autowired
    private ShApiTokenRepository shApiTokenRepository;
    @Autowired
    private PlatformTransactionManager txManager;

    private final ObjectMapper mapper = new ObjectMapper();
    private MockMvc mvc;

    @BeforeAll
    void setup() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(post("/api/v2/tenant/signup").with(user(OWNER))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + WORKSPACE + "\"}"));
        shTenantMembershipService.addMembership(
                shTenantMembershipService.findByName(WORKSPACE).orElseThrow(), MEMBER, ShTenantRole.MEMBER);
        asTenant(WORKSPACE, () -> {
            ShSite site = new ShSite();
            site.setName("SH1379 Site");
            site.setDescription("SH1379 Site");
            site.setUrl("http://localhost/sh1379");
            shSiteRepository.save(site);
            ShApiToken write = new ShApiToken();
            write.setTitle("write");
            write.setToken("sh1379-write-key");
            write.setEnabled(true);
            write.setScope(ShApiTokenScope.WRITE);
            write.setOwner(OWNER);
            write.setCreatedDate(Instant.parse("2026-01-01T00:00:00Z"));
            shApiTokenRepository.save(write);
        });
    }

    @Test
    @DisplayName("SH1379: the tenant's OWNER issues an AGENT token with no session, and it reads the tenant")
    void anOwnerIssuesAnAgentTokenWithoutASession() throws Exception {
        MvcResult issued = mvc.perform(post("/api/v2/api-token").with(user(OWNER)).header(HEADER, WORKSPACE)
                        .contentType(MediaType.APPLICATION_JSON).content(AGENT_BODY))
                .andReturn();
        assertThat(issued.getResponse().getStatus())
                .as("SH1379: a person with no session was refused 403 csrf-token-unobtainable: "
                        + issued.getResponse().getContentAsString())
                .isEqualTo(200);
        String key = mapper.readTree(issued.getResponse().getContentAsString()).get("token").asString();

        MvcResult sites = mvc.perform(get("/api/v2/cda/site").header("Key", key)).andReturn();
        assertThat(sites.getResponse().getContentAsString())
                .as("SH1379: the token lands in the tenant the request named, and reads its sites")
                .contains("SH1379 Site");
    }

    @Test
    @DisplayName("SH1379: a member who does not run the tenant is refused")
    void aPlainMemberIsRefused() throws Exception {
        MvcResult refused = mvc.perform(post("/api/v2/api-token").with(user(MEMBER)).header(HEADER, WORKSPACE)
                        .contentType(MediaType.APPLICATION_JSON).content(AGENT_BODY))
                .andReturn();
        assertThat(refused.getResponse().getStatus())
                .as("SH1379: only an OWNER or ADMIN of the tenant issues a credential without a session")
                .isEqualTo(403);
        assertThat(refused.getResponse().getContentAsString()).contains("OWNER or ADMIN");
    }

    @Test
    @DisplayName("SH1379: an API token never issues another, whatever its scope")
    void aTokenNeverIssuesAToken() throws Exception {
        MvcResult refused = mvc.perform(post("/api/v2/api-token").header("Key", "sh1379-write-key")
                        .contentType(MediaType.APPLICATION_JSON).content(AGENT_BODY))
                .andReturn();
        assertThat(refused.getResponse().getStatus())
                .as("SH1379: a credential that mints credentials is the escalation Block L prevents")
                .isIn(401, 403);
    }

    @Test
    @DisplayName("SH1379: a device token issues an AGENT token with no session")
    void aDeviceTokenIssuesAnAgentToken() throws Exception {
        MvcResult minted = mvc.perform(post("/api/v2/device-token").with(user("admin"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"deviceName\":\"SH1379\"}"))
                .andReturn();
        String bearer = "Bearer " + mapper.readTree(minted.getResponse().getContentAsString())
                .get("token").asString();

        MvcResult issued = mvc.perform(post("/api/v2/api-token").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(AGENT_BODY))
                .andReturn();
        assertThat(issued.getResponse().getStatus())
                .as("SH1379: a device token is a person, and may issue what that person may: "
                        + issued.getResponse().getContentAsString())
                .isEqualTo(200);
    }

    private void asTenant(String tenant, Runnable work) {
        ShTenantContext.setTenantId(tenant);
        try {
            new TransactionTemplate(txManager).executeWithoutResult(status -> work.run());
        } finally {
            ShTenantContext.clear();
        }
    }
}
