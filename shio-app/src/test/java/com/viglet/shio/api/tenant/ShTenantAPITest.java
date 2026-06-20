/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */
package com.viglet.shio.api.tenant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.viglet.shio.persistence.model.tenant.ShTenant;
import com.viglet.shio.utils.ShUtils;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SH22 — tenant catalog CRUD. Uses {@code webAppContextSetup} (no security filter
 * chain, mirroring {@code ShWidgetAPITest}), so admin-gating is enforced by
 * {@code ShSecurityConfig} in production but not exercised here; this covers the
 * controller behavior, including the reserved-default-tenant guard.
 *
 * @author Alexandre Oliveira
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@TestMethodOrder(MethodOrderer.MethodName.class)
@TestInstance(Lifecycle.PER_CLASS)
class ShTenantAPITest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;
    private Principal mockPrincipal;
    private String newTenantId;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @org.junit.jupiter.api.BeforeAll
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        mockPrincipal = Mockito.mock(Principal.class);
        Mockito.when(mockPrincipal.getName()).thenReturn("admin");
    }

    /**
     * T397 — the re-homed teardown (suspend/activate/delete) self-gates on the
     * shared platform-admin check ({@code ShPlatformAdminService} honours
     * {@code ROLE_ADMIN}). This test bypasses the security filter chain, so it
     * binds an admin authentication directly to satisfy that gate, mirroring the
     * {@code ROLE_ADMIN}-gated endpoints in production.
     */
    @org.junit.jupiter.api.BeforeEach
    void authenticateAsAdmin() {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "admin", "n/a", java.util.List.of(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                        "ROLE_ADMIN"))));
    }

    @org.junit.jupiter.api.AfterEach
    void clearAuthentication() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void shTenantCurrent() throws Exception {
        mockMvc.perform(get("/api/v2/tenant/current")).andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("shio"));
    }

    @Test
    void shTenantListContainsDefault() throws Exception {
        // ShTenantOnStartup seeds the reserved 'shio' tenant on boot.
        mockMvc.perform(get("/api/v2/tenant")).andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$[?(@.name == 'shio')]").exists());
    }

    @Test
    void stage01Create() throws Exception {
        ShTenant tenant = new ShTenant();
        tenant.setName("acme");
        tenant.setDescription("Acme workspace");

        RequestBuilder requestBuilder = MockMvcRequestBuilders.post("/api/v2/tenant")
                .principal(mockPrincipal).accept(MediaType.APPLICATION_JSON)
                .content(ShUtils.asJsonString(tenant)).contentType("application/json");

        ResultActions resultActions = mockMvc.perform(requestBuilder).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("acme"))
                .andExpect(jsonPath("$.owner").value("admin"));
        JsonNode node = objectMapper.readTree(resultActions.andReturn().getResponse().getContentAsString());
        newTenantId = node.path("id").asString();
    }

    @Test
    void stage02CreateDuplicateRejected() throws Exception {
        ShTenant tenant = new ShTenant();
        tenant.setName("acme");
        RequestBuilder requestBuilder = MockMvcRequestBuilders.post("/api/v2/tenant")
                .principal(mockPrincipal).accept(MediaType.APPLICATION_JSON)
                .content(ShUtils.asJsonString(tenant)).contentType("application/json");
        mockMvc.perform(requestBuilder).andExpect(status().isConflict());
    }

    @Test
    void stage03Get() throws Exception {
        mockMvc.perform(get("/api/v2/tenant/" + newTenantId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("acme"));
    }

    @Test
    void stage04Update() throws Exception {
        ShTenant tenant = new ShTenant();
        tenant.setName("acme");
        tenant.setDescription("Renamed description");
        tenant.setEnabled(false);
        RequestBuilder requestBuilder = MockMvcRequestBuilders.put("/api/v2/tenant/" + newTenantId)
                .principal(mockPrincipal).accept(MediaType.APPLICATION_JSON)
                .content(ShUtils.asJsonString(tenant)).contentType("application/json");
        mockMvc.perform(requestBuilder).andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Renamed description"))
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void stage05DeleteDefaultTenantForbidden() throws Exception {
        // Resolve the reserved 'shio' tenant's id, then try to delete it -> 409.
        ResultActions list = mockMvc.perform(get("/api/v2/tenant")).andExpect(status().isOk());
        JsonNode arr = objectMapper.readTree(list.andReturn().getResponse().getContentAsString());
        String shioId = null;
        for (JsonNode n : arr) {
            if ("shio".equals(n.path("name").asString())) {
                shioId = n.path("id").asString();
            }
        }
        mockMvc.perform(delete("/api/v2/tenant/" + shioId)).andExpect(status().isConflict());
    }

    @Test
    void stage05aSuspend() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v2/tenant/" + newTenantId + "/suspend")
                .principal(mockPrincipal)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    void stage05bActivate() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v2/tenant/" + newTenantId + "/activate")
                .principal(mockPrincipal)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void stage06DeleteDryRunKeepsTenant() throws Exception {
        mockMvc.perform(delete("/api/v2/tenant/" + newTenantId).queryParam("dryRun", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.catalogRemoved").value(false));
        // The dry run mutates nothing — the tenant is still resolvable.
        mockMvc.perform(get("/api/v2/tenant/" + newTenantId)).andExpect(status().isOk());
    }

    @Test
    void stage07Delete() throws Exception {
        mockMvc.perform(delete("/api/v2/tenant/" + newTenantId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.catalogRemoved").value(true));
    }
}
