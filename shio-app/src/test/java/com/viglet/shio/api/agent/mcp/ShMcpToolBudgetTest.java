/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.viglet.shio.api.agent.ShTokenBudget;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * SH132 — the MCP tool list is a measured budget, not a hope.
 *
 * <h2>Why this number and not the tool count</h2>
 *
 * <p>{@code ShMcpServerTest} caps the surface at twelve tools. That cap is a
 * <em>proxy</em>: what actually costs the agent money is the serialized size of
 * {@code tools/list}, which is re-sent on every turn of every session before any work
 * happens. Eight tools with essay-length descriptions are worse than twelve terse ones,
 * and the count test cannot tell the difference. Design law P3 says a token regression
 * is a build failure; this is that failure for the one payload nobody can opt out of.
 * </p>
 *
 * <h2>Where the numbers live</h2>
 *
 * <p>In {@code src/test/resources/token-budgets.properties}, alongside a note on what
 * each one guards, so raising a ceiling is a reviewable diff with a reason attached
 * rather than an edited magic number three files away. SH113 extends the same file with
 * the REST response budgets — same mechanism, same reviewability, one place to look.</p>
 *
 * <p>Estimation is the {@link ShTokenBudget} 4-chars-per-token heuristic — the same one
 * the instance reports in {@code X-Shio-Est-Tokens}, so the CI number and the runtime
 * number are the same number.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
class ShMcpToolBudgetTest {

    private static final String BUDGETS = "/token-budgets.properties";

    @Autowired
    private ShMcpServer shMcpServer;
    @Autowired
    private ObjectMapper objectMapper;

    private static int budget(String key) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = ShMcpToolBudgetTest.class.getResourceAsStream(BUDGETS)) {
            assertThat(in).as("missing %s", BUDGETS).isNotNull();
            properties.load(in);
        }
        String value = properties.getProperty(key);
        assertThat(value).as("no budget declared for '%s' in %s", key, BUDGETS).isNotNull();
        return Integer.parseInt(value.trim());
    }

    private ObjectNode call(String method) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("jsonrpc", "2.0");
        message.put("id", 1);
        message.put("method", method);
        return shMcpServer.handle(message);
    }

    @Test
    @DisplayName("the whole tool list fits its budget")
    void theToolListFitsItsBudget() throws IOException {
        JsonNode tools = call("tools/list").get("result").get("tools");
        int estimated = ShTokenBudget.estimate(objectMapper.writeValueAsString(tools));
        int limit = budget("mcp.tools.list.total");

        assertThat(estimated)
                .withFailMessage("""
                        tools/list is ~%d est. tokens, over its %d budget. This payload is \
                        re-sent on every turn of every session, so the overrun is paid \
                        before any work happens (P3).
                        Per tool:
                        %s
                        Shorten a description to the *decision* it informs, fold a tool into \
                        an argument on an existing one, or — if the surface genuinely grew — \
                        raise mcp.tools.list.total in token-budgets.properties and say why in \
                        the commit.""", estimated, limit, report())
                .isLessThanOrEqualTo(limit);
    }

    @Test
    @DisplayName("no single tool dominates the list")
    void noSingleToolDominatesTheList() throws IOException {
        int limit = budget("mcp.tools.list.max-per-tool");
        Map<String, Integer> perTool = perTool();

        assertThat(perTool)
                .withFailMessage("""
                        A tool schema is over the %d-token per-tool ceiling:
                        %s
                        A schema this size is usually two tools wearing one name, or a \
                        description restating the parameter list the schema already carries.""",
                        limit, report())
                .allSatisfy((name, tokens) -> assertThat(tokens).as(name).isLessThanOrEqualTo(limit));
    }

    @Test
    @DisplayName("the handshake is small — it is paid before the client knows the server is useful")
    void theHandshakeIsSmall() throws IOException {
        int estimated = ShTokenBudget.estimate(
                objectMapper.writeValueAsString(call("initialize").get("result")));
        assertThat(estimated).isLessThanOrEqualTo(budget("mcp.initialize"));
    }

    private Map<String, Integer> perTool() {
        Map<String, Integer> sizes = new LinkedHashMap<>();
        for (JsonNode tool : call("tools/list").get("result").get("tools")) {
            sizes.put(tool.get("name").asString(),
                    ShTokenBudget.estimate(objectMapper.writeValueAsString(tool)));
        }
        return sizes;
    }

    /** A per-tool breakdown, so a failure says which tool to shorten. */
    private String report() {
        StringBuilder out = new StringBuilder();
        perTool().forEach((name, tokens) ->
                out.append("  %-16s %4d est. tokens%n".formatted(name, tokens)));
        return out.toString();
    }
}
