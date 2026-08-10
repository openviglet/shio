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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * The second task shape (SH603).
 *
 * <h2>Why one number was not enough</h2>
 *
 * <p>The strongest evidence this project has about its own protocol is twelve replication rounds.
 * They are also its narrowest: one agent, one instance, one family of sources, and a corpus the same
 * reader helped design. The verification loop closes entirely inside the project — the laws are
 * enforced by lints written by the people who wrote the laws, and scored by a reader who knows where
 * everything is.</p>
 *
 * <p>That finds defects. It <b>cannot distinguish a protocol that is genuinely discoverable from one
 * a familiar reader has memorised</b>, and it says nothing about the shapes replication never
 * exercises. This is one of them: <b>build a small site from an empty instance</b> — no capture, no
 * corpus, no proposal — following only what the instance says about itself.</p>
 *
 * <h2>The deliverable is the delta, not the site</h2>
 *
 * <p>SH597's metric, applied to a shape nothing was tuned for. If discovery costs about the same
 * here as it does in the replication shape, P2 is real. If it does not, the tuning is what was being
 * measured all along — and that is a finding about the evidence rather than about the product.</p>
 *
 * <p>So this asserts a ceiling and <b>prints both numbers on every run</b>. A benchmark whose output
 * only a failure reveals is one nobody compares.</p>
 *
 * <h2>Its own database</h2>
 *
 * <p>SH315's rule: it counts calls against an instance it must start empty, and another suite's
 * content would make "from nothing" a statement about test order.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shio-sh603;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true",
        "shio.schedule.enabled=false" })
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentAuthoringCostTest {

    private static final String BUDGETS = "/token-budgets.properties";

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;
    private Properties budgets;
    private String site;
    private ShAgentBenchmark.Run run;

    @BeforeAll
    void setup() throws IOException {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        budgets = new Properties();
        try (InputStream in = getClass().getResourceAsStream(BUDGETS)) {
            budgets.load(in);
        }
        site = "sh603-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * The shape, in the order an agent with no prior knowledge would reach for it.
     *
     * <p>Every call is one the instance names: the pack says what exists, the ops vocabulary says
     * how to write, and `verify` is what SH597's `prove-the-work` intent routes to. Nothing here is
     * reached by knowing the codebase, which is the property the whole task exists to test.</p>
     */
    @Test
    @DisplayName("SH603: building a small site from an empty instance, scored like discovery")
    void buildingASiteFromNothing() throws Exception {
        ShAgentBenchmark bench = new ShAgentBenchmark(mockMvc, "authoring", "build-a-small-site");

        // 1. What is here at all. The one call P2 promises replaces a session.
        bench.call("read the context pack",
                get("/api/v2/agent/context").param("format", "terse"));

        // 2. The write vocabulary. An agent that guessed op names would be measuring its own
        // memory, which is the confound this benchmark exists to remove.
        //
        // `start-here` and not `startHere`, `folder` on the upsert, and `title`/`content` rather
        // than `TITLE`/`TEXT` — every one of those corrections came from the instance REFUSING the
        // first attempt and naming the fix. That is P6 and P2 working on the author of this
        // benchmark, which is the closest thing to an outside reader this file can have; the
        // numbers below are what it costs once the refusals have been followed.
        bench.call("read the write vocabulary",
                get("/api/v2/agent/manifest").param("include", "start-here").param("format", "terse"));

        // 3. The site, the render types, a page type, a folder and a page — one batch, dry-run
        // first, because that is what `startHere` routes an intent to.
        String build = """
                {"dryRun":%s,"ops":[
                  {"op":"site.upsert","address":"site:%s"},
                  {"op":"render.provision","data":{"types":["Page"]}},
                  {"op":"folder.upsert","address":"folder:%s/notes"},
                  {"op":"post.upsert","address":"post:%s/notes/first","type":"Page",
                   "folder":"/notes",
                   "data":{"title":"First note","content":"<p>Written from nothing.</p>"}}
                ]}""";
        bench.call("plan the build",
                post("/api/v2/agent/batch").param("format", "terse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(build.formatted("true", site, site, site)),
                build.formatted("true", site, site, site));
        bench.call("apply the build",
                post("/api/v2/agent/batch").param("format", "terse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(build.formatted("false", site, site, site)),
                build.formatted("false", site, site, site));

        // 4. Prove it, which is the step that replaces asking a human to look (P9).
        bench.call("verify the result",
                get("/api/v2/agent/verify").param("site", site).param("format", "terse"));

        run = bench.finish();

        // Before any ceiling: the run must have BUILT something. Five calls that all answered 4xx
        // would sit comfortably under both budgets while measuring nothing at all, and a cost
        // benchmark that passes on a failed run is worse than none — it reports a number nobody
        // can act on and looks like evidence.
        assertThat(run.steps())
                .as("every call in the shape succeeded, so the cost below is the cost of doing it")
                .allSatisfy(step -> assertThat(step.status())
                        .as("%s answered %d", step.label(), step.status())
                        .isLessThan(400));

        // And the site exists afterwards, which the batch's own 200 does not prove: a dry run
        // answers 200 too, and reading the plan as the apply is the mistake this guards.
        assertThat(mockMvc.perform(get("/api/v2/agent/verify").param("site", site))
                .andReturn().getResponse().getStatus())
                .as("the site is there to be verified after the run")
                .isEqualTo(200);

        assertThat(run.calls())
                .as("""
                        SH603: calls to build a small site from an empty instance, following only \
                        what the instance says. The count matters less than its stability — a shape \
                        nothing was tuned for should not cost several times what a tuned one does, \
                        and if it does, the tuning was what the replication rounds measured""")
                .isLessThanOrEqualTo(budget("calls"));
        assertThat(run.tokens())
                .as("SH603: tokens for the same, against the ceiling in " + BUDGETS)
                .isLessThanOrEqualTo(budget("tokens"));
    }

    /**
     * Printed on every run, beside the number this is meant to be compared with.
     *
     * <p>The deliverable is the <b>delta</b>, and a delta nobody can see is a number that was
     * measured once. `ShAgentDiscoveryCostTest` prints its own total the same way, so the two are
     * readable from one build log without anybody joining two files by hand.</p>
     */
    @AfterAll
    void report() {
        if (run == null) {
            return;
        }
        System.out.printf(
                "%nSH603 — the second task shape, scored with SH597's metric:%n"
                        + "  authoring.build-a-small-site  %d call(s), %d est. tokens%n"
                        + "  compare: the discovery ladder's own total, printed by "
                        + "ShAgentDiscoveryCostTest%n%n",
                run.calls(), run.tokens());
    }

    private int budget(String field) {
        String value = budgets.getProperty("benchmark.authoring.build-a-small-site." + field);
        assertThat(value)
                .as("benchmark.authoring.build-a-small-site.%s is missing from %s", field, BUDGETS)
                .isNotNull();
        return Integer.parseInt(value.trim());
    }
}
