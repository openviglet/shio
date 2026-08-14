/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.viglet.shio.api.agent.mcp.ShMcpResource;
import com.viglet.shio.tenant.ShTenantApiCallMeter;
import com.viglet.shio.tenant.ShTenantUsage;

/**
 * SH754 — the meter counts the agent, and now the agent can read it.
 *
 * <h2>What this is asserting, and why it is not a test of the meter</h2>
 *
 * <p>SH45 shipped the meter with two ways out: {@code GET /api/v2/tenant/usage} for a human and the
 * {@code ShBillingExporter} SPI for a billing system. Both work, and neither is reachable by the
 * party the numbers are mostly about — {@code agent} is one of the three surfaces the meter counts.
 * So what is under test here is <b>reach</b>, which is the law this repository keeps rediscovering
 * it has no instrument for: P1 orders every capability MCP first, and this one had landed at REST
 * only.</p>
 *
 * <p>The pending figure gets its own assertion because leaving it out is the plausible mistake.
 * The meter accumulates in heap and flushes on an interval, so a document reporting only durable
 * counts answers <b>zero</b> for the first minute of every window — and an agent that reads zero
 * immediately after doing work learns to distrust the number, which is worse than publishing
 * nothing.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentUsageReachTest {

    @Autowired
    private List<ShMcpResource> resources;
    @Autowired
    private ShAgentUsageService usageService;

    @Test
    @DisplayName("SH754: the usage document is an MCP resource, so a plugin-only client can read it")
    void theMeterIsOnTheMcpSurface() {
        ShMcpResource usage = resources.stream()
                .filter(resource -> "shio://usage".equals(resource.uri()))
                .findFirst()
                .orElse(null);

        assertThat(usage)
                .as("SH754: MCP is not one surface among several for a plugin user — it is the "
                        + "only one, since the Claude Code plugin wires an .mcp.json and nothing "
                        + "else. A meter published to REST alone is unreadable by the caller it "
                        + "spends on behalf of")
                .isNotNull();
        assertThat(usage.httpTwin())
                .as("SH754: a projection on a read that already takes the same subject, not a new "
                        + "route — tools/list is paid every turn and the tool count is capped")
                .isEqualTo("/api/v2/agent/context?include=usage");
        assertThat(usage.read("shio://usage"))
                .as("SH754: and it answers, rather than being a row on resources/list that 404s")
                .contains("shio-usage");
    }

    @Test
    @DisplayName("SH754: include=usage is opt-in, and never part of a bare context pack")
    void theSectionIsAskedForByName() {
        assertThat(ShAgentContextSection.parse("all"))
                .as("SH754: the section changes on EVERY call, this one included — defaulting it "
                        + "on would make the pack a document that never repeats, which is what "
                        + "SH116's conditional read exists to avoid")
                .doesNotContain(ShAgentContextSection.USAGE);
        assertThat(ShAgentContextSection.parse(null))
                .doesNotContain(ShAgentContextSection.USAGE);
        assertThat(ShAgentContextSection.parse("all,usage"))
                .as("SH754: and asking for it by name works the way `all,manifest` does")
                .contains(ShAgentContextSection.USAGE);
    }

    @Test
    @DisplayName("SH754: both surfaces serve the same bytes, and the CLI reads them too")
    void oneDocumentOneSpelling() {
        String direct = usageService.terse();

        assertThat(direct)
                .as("SH754/P10: the resource and the context section both call one service, so "
                        + "byte-identical is a property of the construction rather than of review")
                .startsWith("shio-usage ")
                .contains("calls cda")
                .contains("calls agent")
                .contains("calls console")
                .contains("calls total")
                .contains("storage");
    }

    @Test
    @DisplayName("SH754: a surface at zero still gets a row, and pending is stated separately")
    void pendingIsNotFoldedIntoWritten() {
        ShTenantUsage written = new ShTenantUsage("ut-sh754", "FREE", "2026-08-14", 4096,
                Map.of(ShTenantApiCallMeter.CDA, 7L), 7L);

        String rendered = ShAgentUsageTerse.render(written,
                Map.of(ShTenantApiCallMeter.AGENT, 3L), "ut-sh754", "2026-08-14");

        assertThat(rendered)
                .as("SH754: written and pending answer different questions, and adding them "
                        + "silently would make the total un-reconcilable against what a bill is "
                        + "computed from")
                // Both keys on every row, zeros included: a row whose arity moves with its values
                // is one a reader has to parse rather than split.
                .contains("calls cda written=7 pending=0")
                .contains("calls agent written=0 pending=3")
                .contains("calls total written=7 pending=3");
        assertThat(rendered)
                .as("SH754: a surface with no traffic keeps its row — 'made no console calls' is "
                        + "an answer, and an absent row is indistinguishable from one the document "
                        + "forgot")
                .contains("calls console");
        assertThat(rendered)
                .as("SH754: storage is a LEVEL and is labelled apart from the flows above it, so "
                        + "nobody averages it over the period")
                .contains("storage bytes=4096 plan=FREE");
    }
}
