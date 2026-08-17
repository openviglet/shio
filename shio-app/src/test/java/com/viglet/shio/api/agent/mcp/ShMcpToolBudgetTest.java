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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

    /**
     * SH793 — the headroom, printed on a green run, because a decision already rests on it.
     *
     * <p>The ceiling is enforced below and always was. What nothing did was <b>say how close each
     * tool is</b>: {@link #report()} existed and was reachable only from a failure message, so the
     * numbers appeared exactly when it was too late for them to inform anything.</p>
     *
     * <p>That is not tidiness. {@code ShAgentP1ConformanceTest} declines the {@code postTypeMerge}
     * capability — a thing an agent therefore cannot reach — on the grounds that {@code shio_apply}
     * is "3 tokens under" this ceiling. That figure is in prose, in another file, and no run
     * reproduced it. P3 says a cost is a measured budget; a budget that decides policy while being
     * invisible is the law honoured in the assertion and broken in the argument.</p>
     */
    @Test
    @DisplayName("SH793: the per-tool headroom is published on every run, not only on a red one")
    void theHeadroomIsVisibleWhileItCanStillInformSomething() throws IOException {
        int limit = budget("mcp.tools.list.max-per-tool");
        Map<String, Integer> perTool = perTool();

        StringBuilder out = new StringBuilder(
                "%n[SH793] per-tool MCP schema cost, ceiling %d est. tokens%n".formatted(limit));
        perTool.forEach((name, tokens) -> out.append("  %-16s %4d  headroom %+5d%n"
                .formatted(name, tokens, limit - tokens)));
        System.out.println(out);

        // Not vacuous: a report of nothing agrees with every ceiling, and the failure this is about
        // was invisibility rather than a wrong number.
        assertThat(perTool)
                .as("SH793: tools/list yielded no tool, so the headroom above is a report about an "
                        + "empty list — which is the shape every instrument here is warned about")
                .isNotEmpty();
    }

    /**
     * What one more argument costs, measured rather than chosen (SH795).
     *
     * <p>Three recorded instances agree closely: {@code createSite} on {@code shio_apply} came in
     * <b>18</b> tokens over the ceiling, a single boolean of schema put {@code tools/list} <b>19</b>
     * over, and the {@code postTypeMerge} field is the third. So a tool with fewer than twenty
     * tokens free cannot take another argument — it will refuse the next one rather than size it,
     * which is the failure SH743 named for response budgets and this is the same shape a layer
     * down.</p>
     *
     * <p>An absolute rather than a fraction of the ceiling, because the thing being fitted is an
     * argument and its cost does not scale with the tool that holds it.</p>
     */
    private static final int ROOM_FOR_ONE_ARGUMENT = 20;

    /**
     * Tools knowingly accepted with no room for another argument, and why each is (SH795).
     *
     * <p>Empty is the healthy state. An entry says <i>do not plan an argument here</i> — which is a
     * fact about work nobody has started, and until SH793 printed the headroom it was discoverable
     * only by writing the argument and watching the build refuse it.</p>
     */
    private static final Map<String, String> AT_THE_WALL = Map.of(
            "shio_apply",
            "the blueprint-apply surface, and the one the P1 list already declines postTypeMerge "
                    + "on. Its schema is a document branch and a blueprint branch sharing one tool "
                    + "by design (SH186), so the size is the composition rather than slack: the "
                    + "leaner shape would be a thirteenth tool, and tools/list is paid every turn");

    @Test
    @DisplayName("SH795: a tool with no room for another argument is declared, not discovered")
    void everyToolHasRoomForOneMoreArgument() throws IOException {
        int limit = budget("mcp.tools.list.max-per-tool");
        List<String> tight = new ArrayList<>();
        perTool().forEach((name, tokens) -> {
            if (limit - tokens < ROOM_FOR_ONE_ARGUMENT && !AT_THE_WALL.containsKey(name)) {
                tight.add("%s = %d of %d, %d free and one argument costs about %d"
                        .formatted(name, tokens, limit, limit - tokens, ROOM_FOR_ONE_ARGUMENT));
            }
        });

        assertThat(tight)
                .as("""
                        A tool with less than one argument of headroom does not measure the next \
                        addition, it refuses it — and whoever writes that argument pays for every \
                        one before theirs. That is SH743's finding for response budgets, a layer \
                        down. Trim a description to the decision it informs, or add the tool to \
                        AT_THE_WALL with the reason it cannot be trimmed, in a diff somebody reads.
                        The headroom for every tool is printed above.""")
                .isEmpty();
    }

    @Test
    @DisplayName("SH795: an accepted wall is still a wall")
    void theAcceptedWallsAreStillWalls() throws IOException {
        // The staleness check every declared list here earns (SH741, and SH743 made the same one for
        // response budgets). An entry that has gained room is an exemption excusing nothing, and it
        // hides the next tool to reach the wall. This is the only thing that makes the list shrink.
        int limit = budget("mcp.tools.list.max-per-tool");
        Map<String, Integer> perTool = perTool();
        List<String> loosened = AT_THE_WALL.keySet().stream()
                .filter(name -> perTool.containsKey(name)
                        && limit - perTool.get(name) >= ROOM_FOR_ONE_ARGUMENT)
                .map(name -> "%s now has %d free".formatted(name, limit - perTool.get(name)))
                .sorted()
                .toList();

        assertThat(loosened)
                .as("a tool on AT_THE_WALL has room again, so its entry excuses nothing and hides "
                        + "the next one. Delete it — and if the trim that freed the room was "
                        + "deliberate, that is the commit that should have.")
                .isEmpty();
        assertThat(AT_THE_WALL.keySet())
                .as("and every entry names a tool this server actually serves, or the list is "
                        + "excusing a name that no longer exists")
                .allSatisfy(name -> assertThat(perTool).containsKey(name));
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
