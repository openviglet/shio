/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.io.IOException;
import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import com.viglet.shio.api.agent.ShEstTokensHeaderFilter;
import com.viglet.shio.api.agent.ShTokenBudget;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH113 — response budgets, enforced.
 *
 * <h2>The gap this closes</h2>
 *
 * <p>SH112's task benchmark catches a regression in what a <em>job</em> costs, which is
 * the number that matters — but only for the four jobs it models, and only once someone
 * notices the ratio moved. This is the cheap, always-on guard between those runs: a
 * ceiling on each canonical response, checked on every build, in the same role
 * {@code ShLiquibaseChangelogAgnosticTest} plays for schema portability.</p>
 *
 * <p>What it is really defending against is not a bad commit but ordinary entropy —
 * somebody adds a convenient field to a listing DTO, every row grows by twelve tokens,
 * nobody notices for three releases, and the payload is entity-shaped again. That is
 * exactly how the surface this whole product is a reaction to came to look the way it
 * does.</p>
 *
 * <h2>Measured through the instance's own header</h2>
 *
 * <p>Sizes come from {@code X-Shio-Est-Tokens} (SH124), not from re-tokenizing the body
 * here. If the header and the payload could disagree, the number an operator logs and
 * the number CI guards would be different numbers, and only one of them would be
 * checked.</p>
 *
 * <h2>Why the fixture is fixed, and what had to be excluded to keep it that way</h2>
 *
 * <p>A budget over "whatever the database happens to contain" is not a budget. The
 * fixture below is small, explicit, and its own — one site, one post-type, three posts —
 * so a breach means the <em>shape</em> got bigger, which is the thing worth failing a
 * build over.</p>
 *
 * <p>Three parts of the surface resist that, and are handled rather than ignored:</p>
 * <ul>
 *   <li>the context pack's <b>model</b> section lists every post-type on the
 *       <em>instance</em>, and a shared test context accumulates whatever other classes
 *       created. The pack is therefore measured with {@code include=}, and the model is
 *       guarded one row at a time: {@code context.type-row} is the cost of <em>this
 *       fixture's own</em> type line, which is exactly what a careless field addition
 *       moves and is not affected by how many other types exist;</li>
 *   <li>the pack's <b>conventions</b> section is prose that grows with the instance — one
 *       of its derived lines names every post-type with no content yet — so a fixed
 *       ceiling would fail on test order rather than on a shape change. What bounds it in
 *       production is {@code shio.agent.memory.default-limit} and {@code max-note-length},
 *       not a number here;</li>
 *   <li><b>diagnostics</b> is a bounded in-memory window shared across the run, and an
 *       incident carries a message somebody else wrote. Its size is an operational fact,
 *       not a shape.</li>
 * </ul>
 *
 * <p>Sizes still move a little with fixture names, so the ceilings carry headroom; they
 * do not carry enough to absorb a new field on every row.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentResponseBudgetTest {

    private static final String BUDGETS = "/token-budgets.properties";
    private static final String PREFIX = "response.";

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    /** Measured sizes, printed together at the end so a breach shows its neighbours. */
    private final Map<String, Integer> measured = new LinkedHashMap<>();
    private final List<String> breaches = new ArrayList<>();
    private Properties budgets;
    private MockMvc mockMvc;
    private Principal principal;
    private String siteName;
    private String postTypeName;

    @BeforeAll
    void setup() throws IOException {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(new ShEstTokensHeaderFilter())
                .build();
        principal = Mockito.mock(Principal.class);
        Mockito.when(principal.getName()).thenReturn("admin");
        budgets = new Properties();
        try (var in = getClass().getResourceAsStream(BUDGETS)) {
            budgets.load(in);
        }
        seed();
    }

    private void seed() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            Instant when = Instant.parse("2026-01-01T00:00:00Z");

            ShSite site = new ShSite();
            siteName = "budget-" + suffix;
            site.setName(siteName);
            site.setDate(when);
            site.setOwner("admin");
            entityManager.persist(site);

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(when);
            root.setOwner("admin");
            entityManager.persist(root);

            ShWidget widget = new ShWidget();
            widget.setName("Text");
            entityManager.persist(widget);

            ShPostType type = new ShPostType();
            postTypeName = "BudgetPage" + suffix;
            type.setName(postTypeName);
            type.setTitle("Budget page");
            type.setSystem((byte) 0);
            type.setDate(when);
            type.setOwner("admin");
            entityManager.persist(type);

            ShPostTypeAttr attr = new ShPostTypeAttr();
            attr.setId(UUID.randomUUID().toString());
            attr.setName("headline");
            attr.setLabel("Headline");
            attr.setOrdinal(1);
            attr.setRequired((byte) 1);
            attr.setIsTitle((byte) 1);
            attr.setShWidget(widget);
            attr.setShPostType(type);
            entityManager.persist(attr);

            for (int i = 1; i <= 3; i++) {
                ShPostUnified post = new ShPostUnified();
                post.setId(UUID.randomUUID().toString());
                post.setState(ShPostState.DRAFT);
                post.setShSite(site);
                post.setShFolder(root);
                post.setShPostType(type);
                post.setTitle("Budget page " + i);
                post.setFurl("/page-" + i);
                post.setDate(when);
                post.setData("{\"headline\":\"Budget page " + i + "\"}");
                entityManager.persist(post);
            }
        });
    }

    /**
     * One test, not one per response. A breach should show every measured size at once:
     * "context grew by 40" is a different diagnosis from "everything grew by 40", and
     * one assertion per response would report only the first.
     */
    @Test
    void everyCanonicalResponseStaysWithinItsBudget() throws Exception {
        measure("manifest", get("/api/v2/agent/manifest"));
        measure("context.terse", context("terse"));
        measure("context.json", context(null));
        measure("context.agents-md", context("agents-md"));
        measure("find.terse", get("/api/v2/agent/find")
                .param("site", siteName).param("format", "terse"));
        measure("read.terse", get("/api/v2/agent/read")
                .param("address", "post:" + siteName + "/page-1").param("format", "terse"));
        measure("verify.terse", get("/api/v2/agent/verify")
                .param("site", siteName).param("format", "terse"));
        measure("blueprints.terse", get("/api/v2/agent/blueprints").param("format", "terse"));
        measure("changes.terse", get("/api/v2/agent/changes")
                .param("site", siteName).param("format", "terse"));
        measure("memory", get("/api/v2/agent/memory").param("site", siteName));
        measureTypeRow();

        assertThat(breaches)
                .as("A response got more expensive. Design law P3: a token regression is a "
                        + "build failure, not a footnote. Either make the payload leaner, or "
                        + "raise the ceiling in src/test/resources/token-budgets.properties "
                        + "and say why in the commit message — a ceiling moved without a "
                        + "reason is the whole mechanism defeated.%n%nMeasured:%n%s",
                        report())
                .isEmpty();
    }

    @Test
    void theTerseRenderingIsActuallyCheaperThanTheJsonOne() throws Exception {
        // SH78's entire justification, as an assertion. If this ever inverts, `terse` is a
        // second encoding to maintain and learn for no benefit, and it should be deleted
        // rather than kept out of politeness.
        int terse = tokens(context("terse"));
        int json = tokens(context(null));

        assertThat(terse)
                .as("terse (%d) must cost less than json (%d)", terse, json)
                .isLessThan(json);
    }

    @Test
    void idsAreTheExpensiveDefaultTheyWereMeasuredToBe() throws Exception {
        // SH121 removed per-row UUIDs on the grounds that they were the single largest
        // per-row cost. Keeping the measurement here means that claim stays true rather
        // than becoming folklore about a payload nobody re-measured.
        int without = tokens(context("terse"));
        int with = tokens(get("/api/v2/agent/context")
                .param("site", siteName).param("format", "terse").param("ids", "true"));

        assertThat(with)
                .as("ids=true (%d) should cost meaningfully more than the id-free pack (%d)",
                        with, without)
                .isGreaterThan(without);
    }

    /**
     * The pack restricted to the sections this fixture owns.
     *
     * <p>{@code budget=0} because a ceiling must measure the whole response — a truncated
     * pack would pass every budget by definition, which is the one way this guard could
     * silently stop guarding.</p>
     */
    private MockHttpServletRequestBuilder context(String format) {
        MockHttpServletRequestBuilder request = get("/api/v2/agent/context")
                .param("site", siteName)
                .param("include", "instance,sites,sitemap")
                .param("budget", "0");
        return format == null ? request : request.param("format", format);
    }

    /**
     * What one post-type costs in the model section — measured on this fixture's own type
     * line, picked out of the terse pack by name.
     *
     * <p>An average over every type on the instance would move with whatever other test
     * classes created. One known row does not, and it is the number that actually matters:
     * a field added to the type shape multiplies by the size of somebody's content model.</p>
     */
    private void measureTypeRow() throws Exception {
        MvcResult full = mockMvc.perform(get("/api/v2/agent/context")
                .param("site", siteName).param("format", "terse").param("budget", "0")
                .principal(principal)).andReturn();
        String row = full.getResponse().getContentAsString().lines()
                .filter(line -> line.startsWith("type " + postTypeName + " "))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No type row for " + postTypeName + " — the model-cost guard has "
                                + "nothing to measure, which means it is not guarding."));
        record("context.type-row", ShTokenBudget.estimate(row));
    }

    private void measure(String name, MockHttpServletRequestBuilder request) throws Exception {
        record(name, tokens(request));
    }

    private void record(String name, int estimated) {
        measured.put(name, estimated);
        String property = PREFIX + name;
        String ceiling = budgets.getProperty(property);
        if (ceiling == null) {
            breaches.add(property + " has no declared ceiling (measured " + estimated + ")");
            return;
        }
        int limit = Integer.parseInt(ceiling.trim());
        if (estimated > limit) {
            breaches.add(property + " = " + estimated + ", ceiling " + limit);
        }
    }

    private int tokens(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request.principal(principal)).andReturn();
        assertThat(result.getResponse().getStatus())
                .as("A budget measured off an error response is a budget measuring nothing")
                .isEqualTo(200);
        String header = result.getResponse().getHeader("X-Shio-Est-Tokens");
        assertThat(header)
                .as("Every agent response must carry X-Shio-Est-Tokens (SH124) — it is what "
                        + "this guard, the SH112 benchmark and an operator's log all read")
                .isNotNull();
        return Integer.parseInt(header);
    }

    private String report() {
        StringBuilder out = new StringBuilder();
        measured.forEach((name, estimated) -> out.append(String.format("  %-20s %6d  (ceiling %s)%n",
                name, estimated, budgets.getProperty(PREFIX + name, "—"))));
        return out.toString();
    }

    @AfterAll
    void printMeasurements() {
        if (!measured.isEmpty()) {
            System.out.println("\nSH113 canonical response sizes:\n" + report());
        }
    }
}
