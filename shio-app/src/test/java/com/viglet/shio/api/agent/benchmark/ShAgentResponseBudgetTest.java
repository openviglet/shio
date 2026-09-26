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
// Its own database, for the reason spelled out on ShAgentTaskBenchmarkTest: several of these
// responses describe the whole instance, so on the shared test datasource their size depends on
// what every other test created. This class had headroom left and the task benchmark did not —
// which is the difference between a gate that will fail for the wrong reason and one that
// already has.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shio-budget;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true",
        "shio.schedule.enabled=false" })
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
    /** SH1104 — the layout the budget pages render through, so the editable read composes. */
    private String layoutName;
    private String siteId;
    private String rootId;
    private String widgetId;
    private String fixtureSuffix;

    @BeforeAll
    void setup() throws Exception {
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
        measureEverything();
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
            rootId = root.getId();
            siteId = site.getId();
            fixtureSuffix = suffix;

            ShWidget widget = new ShWidget();
            widget.setName("Text");
            entityManager.persist(widget);
            widgetId = widget.getId();

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
     * SH1104 — the page the editable budgets measure, seeded AFTER every other response is measured.
     *
     * <p>Post types are instance-wide, and the context pack lists them: seeding a layout and two
     * section types beside the rest moved {@code context.json} from 182 to 212 without the product
     * changing at all. Measuring first keeps every other budget's fixture the one it was set against.</p>
     */
    private void seedComposingPage() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            String suffix = fixtureSuffix;
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShSite site = entityManager.find(ShSite.class, siteId);
            ShFolder root = entityManager.find(ShFolder.class, rootId);
            ShWidget widget = entityManager.find(ShWidget.class, widgetId);
            // SH1104 — a page that composes, so `/agent/editable` has something to answer. Without a
            // layout binding the read falls back to `source: content`, and a measurement of that is
            // envelope: a number that passes forever and says nothing about the catalogue. So the
            // page type is bound to a layout whose <main> declares two section types it accepts,
            // each with the fields a properties panel draws.
            layoutName = "BUDGET_LAYOUT_" + suffix;
            site.setPostTypeLayout("{\"" + postTypeName + "\":\"" + layoutName + "\"}");
            ShPostType layoutType = entityManager
                    .createQuery("select t from ShPostType t where t.name = :name", ShPostType.class)
                    .setParameter("name", com.viglet.shio.post.type.ShSystemPostType.PAGE_LAYOUT)
                    .getResultList().stream().findFirst().orElseGet(() -> {
                        ShPostType created = new ShPostType();
                        created.setName(com.viglet.shio.post.type.ShSystemPostType.PAGE_LAYOUT);
                        created.setTitle("Page Layout");
                        created.setSystem((byte) 1);
                        created.setDate(when);
                        created.setOwner("admin");
                        entityManager.persist(created);
                        ShPostTypeAttr html = new ShPostTypeAttr();
                        html.setId(UUID.randomUUID().toString());
                        html.setName("HTML");
                        html.setLabel("HTML");
                        html.setOrdinal(1);
                        html.setShWidget(widget);
                        html.setShPostType(created);
                        entityManager.persist(html);
                        return created;
                    });
            List<String> sectionTypes = new ArrayList<>();
            for (String section : List.of("Hero", "Text")) {
                ShPostType sectionType = new ShPostType();
                sectionType.setName("Budget" + section + suffix);
                sectionType.setTitle("Budget " + section);
                sectionType.setSystem((byte) 0);
                sectionType.setDate(when);
                sectionType.setOwner("admin");
                entityManager.persist(sectionType);
                int ordinal = 1;
                for (String field : List.of("heading", "body", "cta")) {
                    ShPostTypeAttr sectionAttr = new ShPostTypeAttr();
                    sectionAttr.setId(UUID.randomUUID().toString());
                    sectionAttr.setName(field);
                    sectionAttr.setLabel(field);
                    sectionAttr.setOrdinal(ordinal++);
                    sectionAttr.setShWidget(widget);
                    sectionAttr.setShPostType(sectionType);
                    entityManager.persist(sectionAttr);
                }
                sectionTypes.add(sectionType.getName());
            }
            ShPostUnified layout = new ShPostUnified();
            layout.setId(UUID.randomUUID().toString());
            layout.setState(ShPostState.DRAFT);
            layout.setShSite(site);
            layout.setShFolder(root);
            layout.setShPostType(layoutType);
            layout.setTitle(layoutName);
            layout.setFurl("/budget-layout");
            layout.setDate(when);
            layout.setData("{\"HTML\":\"<html><body><h1>{{post.title}}</h1><main sh-accepts=\\\""
                    + String.join(" ", sectionTypes) + "\\\"><p>{{headline}}</p></main></body></html>\"}");
            entityManager.persist(layout);

            // The budget pages are the base seed's; binding their type to the layout is what makes
            // them compose, so the read has fields and a catalogue to answer with.
        });
    }

    /**
     * One test, not one per response. A breach should show every measured size at once:
     * "context grew by 40" is a different diagnosis from "everything grew by 40", and
     * one assertion per response would report only the first.
     */
    /**
     * Every canonical response, measured once (SH743).
     *
     * <p>Moved out of the assertion it used to live in because there are now three readers of
     * {@code measured} and JUnit does not order test methods: a headroom check that ran first
     * would read an empty map and pass, which is the report-zero-forever shape every instrument
     * in this repository is warned about.</p>
     */
    private void measureEverything() throws Exception {
        measure("manifest", get("/api/v2/agent/manifest"));
        // SH174's two cheaper reads of the same document. They are budgeted separately rather
        // than folded into `manifest` because their whole purpose is to be a fraction of it —
        // a ceiling they shared would let the saving erode without failing anything.
        measure("manifest.terse", get("/api/v2/agent/manifest").param("format", "terse"));
        measure("manifest.check", get("/api/v2/agent/manifest")
                .param("include", "features,limits"));
        measure("context.terse", context("terse"));
        measure("context.json", context(null));
        measure("context.agents-md", context("agents-md"));
        measure("find.terse", get("/api/v2/agent/find")
                .param("site", siteName).param("format", "terse"));
        measure("read.terse", get("/api/v2/agent/read")
                .param("address", "post:" + siteName + "/page-1").param("format", "terse"));
        // SH987 — the read that asks for the page's history, budgeted separately from the one that
        // does not. Its own row rather than a raised `read.terse`: the whole claim of an opt-in is
        // that the caller who does not ask pays nothing, and a shared ceiling would let the default
        // grow into the room the option was given.
        measure("read.terse.revisions", get("/api/v2/agent/read")
                .param("address", "post:" + siteName + "/page-1").param("format", "terse")
                .param("include", "revisions"));
        measure("verify.terse", get("/api/v2/agent/verify")
                .param("site", siteName).param("format", "terse"));
        measure("blueprints.terse", get("/api/v2/agent/blueprints").param("format", "terse"));
        measure("changes.terse", get("/api/v2/agent/changes")
                .param("site", siteName).param("format", "terse"));
        // SH989 — the feed asking which fields each write changed, on its own row for SH987's reason:
        // the default must not grow into the room the option was given.
        measure("changes.terse.fields", get("/api/v2/agent/changes")
                .param("site", siteName).param("format", "terse").param("include", "fields"));
        measure("memory", get("/api/v2/agent/memory").param("site", siteName));
        measureTypeRow();
        seedComposingPage();
        // SH1104 — the editable read, and its catalogue on its own row for SH987's reason: the
        // catalogue scales with what a layout declares, and a shared ceiling would let the plain
        // read grow into the room the option was given.
        measure("editable", get("/api/v2/agent/editable")
                .param("address", "post:" + siteName + "/page-1"));
        measure("editable.insertable", get("/api/v2/agent/editable")
                .param("address", "post:" + siteName + "/page-1").param("include", "insertable"));
    }

    /**
     * SH1104 — the editable budget measures a page that renders, or it measures nothing.
     *
     * <p>A page with no layout binding answers {@code source: content} with no fields and no
     * containers, and that envelope passes any ceiling forever. So the budget pages are bound to a
     * layout, and this holds the binding: rendered, with the two declared section types in the
     * catalogue.</p>
     */
    @Test
    @org.junit.jupiter.api.DisplayName("SH1104: the editable budgets measure a page that renders, with a catalogue")
    void theEditableBudgetMeasuresAPageThatComposes() throws Exception {
        assertThat(measured)
                .as("SH1104: /agent/editable was on no budget, and its catalogue scales with the layout")
                .containsKeys("editable", "editable.insertable");
        mockMvc.perform(get("/api/v2/agent/editable").principal(principal)
                        .param("address", "post:" + siteName + "/page-1").param("include", "insertable"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.source").value("rendered"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.containers[0].accepts.length()").value(2));
    }

    @Test
    void everyCanonicalResponseStaysWithinItsBudget() {
        assertThat(measured)
                .as("nothing was measured, so every assertion in this class agrees with anything")
                .isNotEmpty();

        assertThat(breaches)
                .as("SH113: a response got more expensive. Design law P3: a token regression is a "
                        + "build failure, not a footnote. Either make the payload leaner, or "
                        + "raise the ceiling in src/test/resources/token-budgets.properties "
                        + "and say why in the commit message — a ceiling moved without a "
                        + "reason is the whole mechanism defeated.%n%nMeasured:%n%s",
                        report())
                .isEmpty();
    }

    /**
     * Budgets knowingly accepted with no room under them, and why each is (SH743).
     *
     * <p>Empty is the healthy state. An entry is a ceiling somebody decided may sit at its wall,
     * and it has to say why — because the failure it produces looks exactly like an ordinary
     * regression, and the caller who meets it is whichever task happens to add the next line.</p>
     */
    private static final Map<String, String> AT_THE_WALL = Map.of(
            // `manifest` was here and is not any more (SH809). Adding `/agent/editable` took it over
            // its ceiling, and the ceiling was raised to the 4% headroom this very rule requires
            // rather than to the measurement — so the entry stopped excusing anything, and the
            // staleness check below said so on the run that raised it. That is the list working:
            // an exemption outliving its wall hides the next budget that needs one.
            // `manifest.check` was here and left with SH1002, the same way `manifest` left with
            // SH809 and for the same reason: adding `reviewVerdicts` took it over 390, and the
            // ceiling went to 420 — the 4% headroom this rule asks for — rather than to the
            // measurement. A wall that has room behind it excuses nothing, and the staleness check
            // below said so on the run that raised it. The list is empty now, which is the state it
            // is supposed to be in between breaches.
            );

    /** Below this fraction of its ceiling free, a budget refuses the next line rather than sizing it. */
    private static final double REQUIRED_HEADROOM = 0.04;

    @Test
    void everyBudgetHasRoomUnderIt() {
        // SH743 — the half SH736 added for the CLI help gate, on the surface it was found on.
        //
        // A ceiling at its total does not measure the next addition, it refuses it, and the
        // cheapest way to pass is to delete something else's payload — the "optimize a benchmark
        // by degrading correctness" non-goal by name. Measured when this landed: two of thirteen
        // keys were at 99.5% and 98.7% and nothing said so. SH230 found them by reading the
        // printed numbers while measuring something else, and agent-surface.md records an earlier
        // round closing at "one token of headroom" as though that were a result.
        //
        // A named list rather than a global slack factor, for the reason every ledger here uses:
        // a ceiling lets one task pay and the next spend it.
        List<String> tight = new ArrayList<>();
        measured.forEach((name, estimated) -> {
            String ceiling = budgets.getProperty(PREFIX + name);
            if (ceiling == null) {
                return;
            }
            int limit = Integer.parseInt(ceiling.trim());
            int required = (int) Math.round(limit * REQUIRED_HEADROOM);
            if (limit - estimated < required && !AT_THE_WALL.containsKey(name)) {
                tight.add(String.format(java.util.Locale.ROOT,
                        "%s = %d of %d (%.1f%% used, needs %d free)",
                        name, estimated, limit, (estimated * 100.0) / limit, required));
            }
        });

        assertThat(tight)
                .as("""
                        A budget with no room under it is not measuring the next addition, it is \
                        refusing it — and whoever adds the next line pays for every line before \
                        theirs (SH736 for the CLI help, SH743 here). Trim the payload so the \
                        ceiling means something again, or add the key to AT_THE_WALL with the \
                        reason it cannot be trimmed, in a diff somebody reviews.%n%nMeasured:%n%s""",
                        report())
                .isEmpty();
    }

    @Test
    void theAcceptedWallsAreStillWalls() {
        // The staleness check every declared list here earns (SH741): an entry that is no longer
        // tight is an exemption excusing nothing, and it hides the next one. Cheap, and the only
        // thing that makes the list above shrink rather than accumulate.
        List<String> loosened = new ArrayList<>();
        AT_THE_WALL.forEach((name, why) -> {
            Integer estimated = measured.get(name);
            String ceiling = budgets.getProperty(PREFIX + name);
            if (estimated == null || ceiling == null) {
                loosened.add(name + " is declared at the wall but nothing measures it any more");
                return;
            }
            int limit = Integer.parseInt(ceiling.trim());
            if (limit - estimated >= (int) Math.round(limit * REQUIRED_HEADROOM)) {
                loosened.add(name + " now has room (" + estimated + " of " + limit + ")");
            }
            assertThat(why).as("%s is accepted without a reason anybody can weigh", name)
                    .hasSizeGreaterThan(40);
        });

        assertThat(loosened)
                .as("An accepted wall that is no longer a wall is a line to delete: it excuses "
                        + "nothing and it hides the next budget that needs the exemption (SH743).")
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
