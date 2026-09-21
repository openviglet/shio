/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.site;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH1056 — the console's door to a site's locale policy, and the one that replaces.
 *
 * <p>{@code site.upsert} fills a policy a site does not have and keeps one somebody set, and says
 * "change it in the console" when it keeps one. These are the calls that sentence leads to: read the
 * policy the site reports, declare one over whatever it had, and stop declaring so the site goes
 * back to reporting the locales its content is written in.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShSiteLocalesApiTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ShSiteRepository shSiteRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private MockMvc mockMvc;
    private Principal curator;
    private String siteId;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        curator = Mockito.mock(Principal.class);
        Mockito.when(curator.getName()).thenReturn("admin");
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = new ShSite();
            site.setName("ut-locales-" + UUID.randomUUID());
            site.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            site.setOwner("admin");
            entityManager.persist(site);
            siteId = site.getId();
        });
    }

    @Test
    void anUndeclaredSiteReportsTheLocalesItsContentImplies() throws Exception {
        mockMvc.perform(get("/api/v2/site/" + siteId + "/locales").principal(curator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.declared").value(false))
                .andExpect(jsonPath("$.defaultLocale").value("en"))
                .andExpect(jsonPath("$.enabled[0]").value("en"));
    }

    @Test
    void aPutDeclaresThePolicyAndTheReadSaysItWasChosen() throws Exception {
        declare("{\"default\":\"pt-br\",\"enabled\":[\"pt-br\",\"en\"],\"fallback\":{\"en\":\"pt-br\"}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.declared").value(true))
                .andExpect(jsonPath("$.defaultLocale").value("pt-br"))
                .andExpect(jsonPath("$.enabled[1]").value("en"))
                .andExpect(jsonPath("$.fallback.en").value("pt-br"));

        assertThat(stored())
                .as("SH1056: stored in the one byte-stable form every door writes")
                .isEqualTo("{\"default\":\"pt-br\",\"enabled\":[\"pt-br\",\"en\"],"
                        + "\"fallback\":{\"en\":\"pt-br\"}}");
    }

    @Test
    void aPutReplacesAPolicySomebodySetWhichIsWhatTheUpsertRefusesToDo() throws Exception {
        declare("{\"default\":\"en\",\"enabled\":[\"en\"]}").andExpect(status().isOk());

        declare("{\"default\":\"pt-br\",\"enabled\":[\"pt-br\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultLocale").value("pt-br"));

        assertThat(stored())
                .as("SH1056: the console is where a decision changes — the upsert keeps it and "
                        + "says to come here")
                .contains("\"default\":\"pt-br\"");
    }

    @Test
    void aTagTheInstanceDoesNotKnowIsTheSameTeaching400TheUpsertAnswers() throws Exception {
        declare("{\"default\":\"pt-BR\",\"enabled\":[\"pt-BR\"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fix").exists());

        assertThat(stored()).as("SH1056: and nothing was written").isNull();
    }

    @Test
    void aDeleteStopsDeclaringAndAnswersWithWhatTheSiteReportsNow() throws Exception {
        declare("{\"default\":\"pt-br\",\"enabled\":[\"pt-br\"]}").andExpect(status().isOk());

        mockMvc.perform(delete("/api/v2/site/" + siteId + "/locales").principal(curator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.declared").value(false))
                .andExpect(jsonPath("$.defaultLocale").value("en"));

        assertThat(stored())
                .as("SH1056: cleared, not emptied — an empty policy would offer no language at all")
                .isNull();
    }

    @Test
    void anUnknownSiteIsNotFound() throws Exception {
        mockMvc.perform(put("/api/v2/site/no-such-site-" + UUID.randomUUID() + "/locales")
                        .principal(curator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"default\":\"en\",\"enabled\":[\"en\"]}"))
                .andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.ResultActions declare(String body) throws Exception {
        return mockMvc.perform(put("/api/v2/site/" + siteId + "/locales")
                .principal(curator)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String stored() {
        return new TransactionTemplate(transactionManager).execute(status ->
                shSiteRepository.findById(siteId).map(ShSite::getLocales).orElse(null));
    }
}
