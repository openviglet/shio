/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */
package com.viglet.shio.tenant;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.token.ShApiToken;
import com.viglet.shio.persistence.model.token.ShApiTokenScope;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;
import com.viglet.shio.persistence.repository.token.ShApiTokenRepository;

/**
 * SH22 Part D — proves token-authenticated CDA delivery is scoped to the token's
 * tenant. A {@code READ} token created under tenant {@code alpha} lists only
 * {@code alpha}'s sites via {@code GET /api/v2/cda/site}; the {@code beta} token
 * sees only {@code beta}'s. Runs the full Spring Security chain
 * ({@code ShApiTokenFilter} → {@code ShTenantFilter}) via {@code springSecurity()},
 * with {@code shio.multi-tenant=true}. No Docker (H2, like {@code ShTenantIsolationIT}).
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShTenantCdaDeliveryIT {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:shio-sh22d-it;"
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

    private static final String ALPHA = "alpha";
    private static final String BETA = "beta";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private ShSiteRepository shSiteRepository;
    @Autowired
    private ShApiTokenRepository shApiTokenRepository;
    @Autowired
    private PlatformTransactionManager txManager;

    private MockMvc mockMvc;

    @BeforeAll
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        asTenant(ALPHA, () -> { seedSite("Alpha Site"); seedToken("alpha-key"); return null; });
        asTenant(BETA, () -> { seedSite("Beta Site"); seedToken("beta-key"); return null; });
    }

    @Test
    void alphaTokenSeesOnlyAlphaSites() throws Exception {
        mockMvc.perform(get("/api/v2/cda/site").header("Key", "alpha-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Alpha Site")))
                .andExpect(jsonPath("$[*].name", not(hasItem("Beta Site"))))
                .andExpect(jsonPath("$[*].name", everyItem(not("Beta Site"))));
    }

    @Test
    void betaTokenSeesOnlyBetaSites() throws Exception {
        mockMvc.perform(get("/api/v2/cda/site").header("Key", "beta-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Beta Site")))
                .andExpect(jsonPath("$[*].name", not(hasItem("Alpha Site"))));
    }

    private void seedSite(String name) {
        ShSite site = new ShSite();
        site.setName(name);
        site.setDescription(name);
        site.setUrl("http://localhost/" + name.toLowerCase().replace(' ', '-'));
        shSiteRepository.save(site);
    }

    private void seedToken(String value) {
        ShApiToken token = new ShApiToken();
        token.setTitle(value);
        token.setToken(value);
        token.setEnabled(true);
        token.setScope(ShApiTokenScope.READ);
        token.setOwner("system");
        token.setCreatedDate(Instant.now());
        shApiTokenRepository.save(token);
    }

    private <T> T asTenant(String tenant, Supplier<T> work) {
        ShTenantContext.setTenantId(tenant);
        try {
            return new TransactionTemplate(txManager).execute(status -> work.get());
        } finally {
            ShTenantContext.clear();
        }
    }
}
