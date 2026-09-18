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

import com.viglet.shio.api.agent.ShAgentManifestService;
import com.viglet.shio.api.agent.ShTokenBudget;
import com.viglet.shio.api.agent.dto.ShAgentEndpoint;

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

    /** The prefix a per-tool reservation is declared under (SH977). */
    private static final String RESERVED = "mcp.tools.list.reserved.";

    @Autowired
    private ShMcpServer shMcpServer;
    @Autowired
    private ShAgentManifestService shAgentManifestService;
    @Autowired
    private ObjectMapper objectMapper;

    private static int budget(String key) throws IOException {
        String value = budgets().getProperty(key);
        assertThat(value).as("no budget declared for '%s' in %s", key, BUDGETS).isNotNull();
        return Integer.parseInt(value.trim());
    }

    private static Properties budgets() throws IOException {
        Properties properties = new Properties();
        try (InputStream in = ShMcpToolBudgetTest.class.getResourceAsStream(BUDGETS)) {
            assertThat(in).as("missing %s", BUDGETS).isNotNull();
            properties.load(in);
        }
        return properties;
    }

    /** SH977 — the room each planned addition holds, by tool: the most that tool may cost. */
    private static Map<String, Integer> reservations() throws IOException {
        Map<String, Integer> reserved = new LinkedHashMap<>();
        Properties properties = budgets();
        for (String key : new java.util.TreeSet<>(properties.stringPropertyNames())) {
            if (key.startsWith(RESERVED)) {
                reserved.put(key.substring(RESERVED.length()),
                        Integer.parseInt(properties.getProperty(key).trim()));
            }
        }
        return reserved;
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
        // SH976 — the totals, on green. The per-tool rows say which tool to shorten and never
        // answered the question a pull request actually asks: is there room for this at all. That
        // number decided whether `postTypeMerge` could reach an agent, and it was visible only in a
        // failure message — so the one figure the surface is rationed by was the one nobody could
        // see while there was still something to do about it.
        out.append("%n[SH976] the whole MCP surface, per turn and per session%n".formatted());
        out.append(line("tools/list", listCost("tools/list", "tools"),
                budget("mcp.tools.list.total"), perTool.size() + " tools"));
        out.append(line("initialize", callCost("initialize"), budget("mcp.initialize"),
                "the handshake"));
        out.append(line("resources/list", listCost("resources/list", "resources"),
                budget("mcp.resources.list.total"), "paid when a client lists them"));
        out.append(line("prompts/list", listCost("prompts/list", "prompts"),
                budget("mcp.prompts.list.total"), "paid when a client lists them"));
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
    // SH1032 drops `shio_write`, the second time this list has emptied the same way: raising the
    // per-tool ceiling to 520 for `asset.meta` left it 27 free, which is over ROOM_FOR_ONE_ARGUMENT,
    // and SH993 dropped `shio_apply` for exactly this reason one raise earlier. An entry that
    // excuses nothing hides the next tool to reach a wall, so it goes rather than being reworded.
    //
    // WHAT TO EXPECT NEXT, since an empty list says "nobody has hit one" and not "nobody will":
    // `shio_write` is still the tool to watch. Its enum IS the write vocabulary, so every future op
    // lands here at about four tokens and nowhere else — roughly seven before 520 binds again. That
    // is a prediction, not an excuse, which is why it is a comment and not an entry.
    private static final Map<String, String> AT_THE_WALL = Map.of();

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

    /** One line of the SH976 summary: what it costs, what it may cost, and what is left. */
    private static String line(String what, int estimated, int limit, String note) {
        return "  %-15s %5d of %5d  %+5d free  (%s)%n"
                .formatted(what, estimated, limit, limit - estimated, note);
    }

    /** What a listing payload costs, by the same heuristic the instance reports. */
    private int listCost(String method, String field) {
        return ShTokenBudget.estimate(
                objectMapper.writeValueAsString(call(method).get("result").get(field)));
    }

    /** What a whole result costs — the handshake is one, and it has no list inside it. */
    private int callCost(String method) {
        return ShTokenBudget.estimate(
                objectMapper.writeValueAsString(call(method).get("result")));
    }

    @Test
    @DisplayName("SH976: the lists a client reads once are budgeted too")
    void everyListedSurfaceHasACeiling() throws IOException {
        Map<String, Integer> lists = new LinkedHashMap<>();
        lists.put("mcp.resources.list.total", listCost("resources/list", "resources"));
        lists.put("mcp.prompts.list.total", listCost("prompts/list", "prompts"));

        List<String> over = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : lists.entrySet()) {
            int limit = budget(entry.getKey());
            if (entry.getValue() > limit) {
                over.add("%s = %d of %d".formatted(entry.getKey(), entry.getValue(), limit));
            }
        }

        assertThat(over)
                .as("""
                        SH976: `tools/list` had a ceiling and the other two lists had none, which \
                        matters because a resource is where work goes when the tool list is full — \
                        so the surface that absorbs the pressure was the unmeasured one. Trim, or \
                        raise the ceiling in token-budgets.properties with the reason beside it.""")
                .isEmpty();
        assertThat(lists.values())
                .as("SH976: a list that measured zero would agree with every ceiling, which is the "
                        + "shape this file warns about everywhere else")
                .allSatisfy(cost -> assertThat(cost).isPositive());
    }

    /**
     * SH976 — what each way of adding a capability costs, measured on this build.
     *
     * <h2>Why this is a test and not a paragraph</h2>
     *
     * <p>The choice between an op, a parameter, a resource and an endpoint is made in nearly every
     * task on this surface, and it was made from memory: the ladder existed as prose in three area
     * docs and as an argument in a P1 reason, and the only figure anybody had was the one a red
     * build printed. This computes it from the live server, so the cheapest shape is a fact on the
     * run rather than a recollection.</p>
     *
     * <p>Each rung is the <b>marginal</b> cost of one more of that thing — an average over what is
     * there, which is what a next addition actually resembles. The ordering is asserted, because
     * that is the claim the docs make; the numbers are printed, because they are what informs the
     * next design.</p>
     */
    @Test
    @DisplayName("SH976: the affordance ladder is computed, not remembered")
    void theAffordanceLadderIsMeasured() throws IOException {
        JsonNode write = tool("shio_write");
        JsonNode ops = write.at("/inputSchema/properties/ops/items/properties/op/enum");
        assertThat(ops.isArray() && !ops.isEmpty())
                .as("SH976: shio_write's op enum is where an op name lands, and this scan no longer "
                        + "finds it — the ladder's cheapest rung cannot be measured")
                .isTrue();
        int perOp = ShTokenBudget.estimate(objectMapper.writeValueAsString(ops)) / ops.size();

        int parameters = 0;
        int parameterCost = 0;
        for (JsonNode each : call("tools/list").get("result").get("tools")) {
            JsonNode properties = each.at("/inputSchema/properties");
            parameters += properties.size();
            parameterCost += ShTokenBudget.estimate(objectMapper.writeValueAsString(properties));
        }
        int perParameter = parameterCost / Math.max(1, parameters);

        JsonNode resources = call("resources/list").get("result").get("resources");
        int perResource = ShTokenBudget.estimate(objectMapper.writeValueAsString(resources))
                / Math.max(1, resources.size());

        List<ShAgentEndpoint> endpoints = shAgentManifestService.manifest().endpoints();
        int perEndpoint = ShTokenBudget.estimate(objectMapper.writeValueAsString(endpoints))
                / Math.max(1, endpoints.size());

        System.out.printf("%n[SH976] what one more of each costs, on this build%n"
                        + "  %-28s %4d est. tokens  (paid every turn, inside shio_write)%n"
                        + "  %-28s %4d est. tokens  (paid every turn, on the tool that takes it)%n"
                        + "  %-28s %4d est. tokens  (paid when a client lists resources)%n"
                        + "  %-28s %4d est. tokens  (paid on a manifest read)%n%n",
                "an op name", perOp, "a parameter", perParameter,
                "an MCP resource", perResource, "a declared endpoint", perEndpoint);

        assertThat(perOp)
                .as("SH976: an op name is the cheapest way to add a capability — that is why the "
                        + "batch vocabulary is the one that grows, and a task that reaches for a "
                        + "thirteenth tool is answering a question this number already answered")
                .isLessThan(perParameter)
                .isLessThan(perResource)
                .isLessThan(perEndpoint);
    }

    /**
     * SH977 — the headroom is allocated before it is spent.
     *
     * <h2>What went wrong without this</h2>
     *
     * <p>Four designed additions each said, in their own task, to measure against the per-tool
     * ceiling first — and each would have passed alone. Between them they need about 180 est. tokens
     * and the list had 40 free, so whichever landed first took the room and the rest would have
     * shipped as a resource or an op <em>by accident</em> rather than by SH976's ladder. A ceiling
     * that is raced for is not a budget; it is an order of arrival.</p>
     *
     * <p>So the room is divided in the properties file, and this holds the division: a tool over its
     * reservation fails, and the whole allocation — reserved tools at their reservation, the rest at
     * what they cost today — has to fit the ceiling. A task adding an argument then cites its
     * reservation instead of the ceiling, and a task that wants more has to say which planned
     * capability is giving up its room.</p>
     */
    @Test
    @DisplayName("SH977: a planned addition spends its own reservation, not whatever is left")
    void everyPlannedAdditionHasItsOwnRoom() throws IOException {
        int perTool = budget("mcp.tools.list.max-per-tool");
        int total = budget("mcp.tools.list.total");
        Map<String, Integer> reserved = reservations();
        Map<String, Integer> costs = perTool();

        List<String> problems = new ArrayList<>();
        reserved.forEach((name, room) -> {
            Integer cost = costs.get(name);
            if (cost == null) {
                problems.add(name + ": reserved and not served — the reservation is holding room "
                        + "for a tool that does not exist");
                return;
            }
            if (cost > room) {
                problems.add("%s = %d over its reservation of %d. An op name costs about 3 est. "
                        .formatted(name, cost, room)
                        + "tokens and a parameter about 30 (SH976's ladder), so the cheaper shape is "
                        + "usually an op on shio_write — and if this really is a parameter, raise the "
                        + "reservation and name the planned capability giving up the room");
            }
            if (room > perTool) {
                problems.add("%s: reserved %d above the per-tool ceiling of %d"
                        .formatted(name, room, perTool));
            }
        });

        int allocated = costs.entrySet().stream()
                .mapToInt(entry -> Math.max(entry.getValue(), reserved.getOrDefault(entry.getKey(), 0)))
                .sum();

        StringBuilder out = new StringBuilder(
                "%n[SH977] the tool list, allocated — %d of %d est. tokens spoken for%n"
                        .formatted(allocated, total));
        costs.forEach((name, cost) -> out.append("  %-16s %4d  reserved %4d  to spend %+5d%n"
                .formatted(name, cost, reserved.getOrDefault(name, cost),
                        reserved.getOrDefault(name, cost) - cost)));
        System.out.println(out);

        assertThat(problems).as("SH977: the allocation above is what each planned addition may "
                + "spend, and this is what makes it binding").isEmpty();
        assertThat(allocated)
                .as("SH977: every reservation plus every unreserved tool at today's size must fit "
                        + "the ceiling, or the room has been promised twice and the ceiling is a "
                        + "queue. Raise mcp.tools.list.total with the argument beside it, or take "
                        + "the room from a reservation that is no longer planned.")
                .isLessThanOrEqualTo(total);
    }

    private JsonNode tool(String name) {
        for (JsonNode each : call("tools/list").get("result").get("tools")) {
            if (name.equals(each.get("name").asString())) {
                return each;
            }
        }
        throw new AssertionError("no tool named " + name);
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
