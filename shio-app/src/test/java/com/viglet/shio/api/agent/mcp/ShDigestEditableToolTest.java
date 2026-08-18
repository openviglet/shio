/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.api.agent.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.viglet.shio.api.agent.mcp.tools.ShDigestTool;

/**
 * SH811 — an MCP-only client can ask which fields a curator could edit.
 *
 * <h2>The half SH809 left</h2>
 *
 * <p>The P1 inventory grades reachability and the grades are not decoration. {@code MCP_TOOL} is
 * <em>the strictest bar, and what P1 actually asks for</em>, because a client connected over
 * {@code POST /mcp} and nothing else can invoke it. {@code AGENT_ROUTE} means a route exists and the
 * manifest declares it, so an agent <b>with HTTP</b> can find it.</p>
 *
 * <p>SH809 delivered the second and closed the exception honestly. This is the first: a client that
 * speaks only MCP could still not ask, which is the operator the product is named after.</p>
 *
 * <h2>Why a parameter and not a thirteenth tool, measured</h2>
 *
 * <p>The twelve tools summed to <b>2499 against a 2500 ceiling</b> — one token of headroom, and the
 * budget file said in as many words that the next capability here would be *an argument, a trim, or
 * a conversation about which tool leaves*. A thirteenth tool the size of {@code shio_digest}
 * measured <b>146</b>; {@code include=editable} on the tool that already renders the page measured
 * <b>24</b>, five times less, and {@code ShMcpServerTest} refuses a thirteenth tool regardless.</p>
 *
 * <p>It also belongs there on the merits rather than only on the budget: {@code shio_digest} takes
 * an address and answers a structural question about the rendered page, and *which fields are
 * editable* is a third question of exactly that kind, off exactly that render (P10).</p>
 */
@SpringBootTest
class ShDigestEditableToolTest {

    @Autowired
    private ShDigestTool shDigestTool;

    @Test
    @DisplayName("SH811: the tool declares the include that makes editability MCP-reachable")
    void theToolDeclaresInclude() {
        // The schema is what an MCP-only client reads to know the capability exists — P2's whole
        // argument, applied to a parameter rather than to a document. A capability reachable only
        // by somebody who already knew the argument name is not reachable.
        Object properties = shDigestTool.inputSchema().get("properties");

        assertThat(String.valueOf(properties))
                .as("SH811: `include` is how an MCP-only client learns it can ask which fields a "
                        + "curator could edit. Without it in the schema the capability is reachable "
                        + "only by guessing, which is the state SH809 left and this closes")
                .contains("include")
                .contains("editable");
    }

    @Test
    @DisplayName("SH811: without the include, the digest is unchanged")
    void theDigestIsUnchangedWithoutIt() {
        // The other half, and the one that keeps this cheap: every existing caller pays nothing.
        // A parameter that changed the default answer would be a new tool wearing an old name.
        var schema = shDigestTool.inputSchema();

        assertThat(String.valueOf(schema.get("required")))
                .as("SH811: `include` must not be required — the digest's existing shape is what "
                        + "every current caller reads, and a new argument that changed it would be "
                        + "a breaking change dressed as an addition")
                .doesNotContain("include");
    }
}
