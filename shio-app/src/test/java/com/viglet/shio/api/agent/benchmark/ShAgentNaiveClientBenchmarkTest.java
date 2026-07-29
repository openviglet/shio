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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.viglet.shio.api.agent.mcp.ShMcpServer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * SH340 — <b>first-attempt success, as a number.</b>
 *
 * <h2>The gap this closes</h2>
 *
 * <p>{@code ShAgentTaskBenchmarkTest} asserts call and token ratios over a canonical task, which means
 * it measures a <b>correct script</b>: a script never sends {@code postType} where the surface wants
 * {@code type}, or {@code addresses} where it wants {@code address}. So it is structurally blind to
 * SH331 — and the real session behind Block P's MCP build sent <b>four wrong calls in about
 * twenty-five</b>. Every one of them was a rule the agent applied consistently that this product
 * breaks in one of its surfaces, so they are not noise: they are the cost of an inconsistent
 * vocabulary, and until now nothing measured it.</p>
 *
 * <h2>Two design points, both from SH340's own filing</h2>
 *
 * <p><b>The fixture is driven the way a naive client would drive it</b>, because writing it with the
 * correct keys defeats the entire purpose. The calls below are the mistakes a real agent made, plus a
 * systematic sweep that adds one unrecognised key to an otherwise valid call for every read tool —
 * SH322's shape, since the sweep is what finds the *next* occurrence rather than these.</p>
 *
 * <p><b>A silently dropped argument counts more heavily than a teaching error.</b> A teaching error
 * costs one round trip and the SH80 {@code fix} makes the second attempt land. A dropped argument
 * costs a <em>wrong outcome nobody notices</em>: SH331's {@code scope}/{@code site} case reported
 * success and wrote the note instance-wide, where no session working on that site will ever read it,
 * and finding out why meant reading the tool's Java. So they are counted separately, with separate
 * ceilings, and the drop ceiling is the one that matters.</p>
 *
 * <h2>Why the numbers are here and not in the assertion</h2>
 *
 * <p>{@code token-budgets.properties}, beside the SH132 MCP budgets and the SH113 response budgets —
 * a second place to keep a number is a place that goes stale. Lowering a ceiling here is what SH331
 * and SH332 are for; raising one must be justified in the commit.</p>
 *
 * @author Alexandre Oliveira
 */
// SH315 — its own database, because `shio_context` with no site describes the whole instance and
// `shio_remember` writes to it. On the shared test datasource both would depend on what every other
// test created, which is the difference between a measurement and a coincidence.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shio-naive;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true",
        "shio.schedule.enabled=false" })
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentNaiveClientBenchmarkTest {

    private static final String BUDGETS = "/token-budgets.properties";

    /**
     * The site every naive call names.
     *
     * <p>It has to exist, and the first version of this benchmark got that wrong in a way worth
     * recording: with no such site, `find`, `verify`, `read` and `write` answered a teaching error
     * because the <em>site</em> was missing, and the run scored them as vocabulary failures. The
     * number was real and it was measuring the fixture. A benchmark about argument names must fail
     * only for an argument name.</p>
     */
    private static final String SITE = "NaiveBench";
    private static final String URL = "/home";

    @Autowired
    private ShMcpServer shMcpServer;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    @org.junit.jupiter.api.BeforeAll
    void seedASiteTheCallsCanName() {
        new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> {
                    com.viglet.shio.persistence.model.site.ShSite site =
                            new com.viglet.shio.persistence.model.site.ShSite();
                    site.setName(SITE);
                    site.setDate(java.time.Instant.parse("2026-01-01T00:00:00Z"));
                    site.setOwner("admin");
                    entityManager.persist(site);

                    com.viglet.shio.persistence.model.folder.ShFolder root =
                            new com.viglet.shio.persistence.model.folder.ShFolder();
                    root.setName("root");
                    root.setRootFolder((byte) 1);
                    root.setShSite(site);
                    root.setDate(java.time.Instant.parse("2026-01-01T00:00:00Z"));
                    root.setOwner("admin");
                    entityManager.persist(root);

                    com.viglet.shio.persistence.model.post.type.ShPostType type =
                            new com.viglet.shio.persistence.model.post.type.ShPostType();
                    type.setName("Article");
                    type.setTitle("Article");
                    type.setSystem((byte) 0);
                    type.setDate(java.time.Instant.parse("2026-01-01T00:00:00Z"));
                    type.setOwner("admin");
                    entityManager.persist(type);

                    for (com.viglet.shio.persistence.model.post.unified.ShPostState state
                            : com.viglet.shio.persistence.model.post.unified.ShPostState.values()) {
                        com.viglet.shio.persistence.model.post.unified.ShPostUnified post =
                                new com.viglet.shio.persistence.model.post.unified.ShPostUnified();
                        post.setId("naive-home");
                        post.setState(state);
                        post.setShSite(site);
                        post.setShFolder(root);
                        post.setShPostType(type);
                        post.setTitle("Home");
                        post.setFurl(URL);
                        post.setLocale("en");
                        post.setOwner("admin");
                        post.setDate(java.time.Instant.parse("2026-01-01T00:00:00Z"));
                        post.setData("{\"title\":\"Home\"}");
                        entityManager.persist(post);
                    }
                });
    }

    /** What became of a naive call. */
    private enum Outcome {
        /** {@code isError:true} with a {@code fix} — one round trip, then it lands. */
        TEACHING_ERROR,
        /** Succeeded while ignoring a key the caller sent. A wrong outcome nobody notices. */
        SILENT_DROP,
        /**
         * Succeeded <em>and said so</em>: the synonym was read and a note named the canonical key.
         *
         * <p>Distinguished from {@link #SILENT_DROP} by the note, which is the only observable
         * difference between "understood you" and "ignored you" from outside — and the reason SH331
         * reports rather than quietly rewrites. The first version of this benchmark had no such row
         * and therefore scored SH331's own fix as a drop.</p>
         */
        SYNONYM_ACCEPTED,
        /** Nothing unusual was sent, so there was nothing to accept or refuse. */
        ACCEPTED
    }

    private record Naive(String label, String tool, Map<String, Object> arguments, String wanted) {
    }

    /**
     * The four mistakes from SH331's table that are expressible as a single tool call, plus the
     * naive spellings of two more concepts.
     *
     * <p>Every {@code dryRun} is deliberate: this measures the vocabulary, not the writer, and a
     * benchmark that mutated content to find out whether an argument was read would be measuring two
     * things. {@code shio_remember} is the exception and has to be — its whole defect is that the
     * write <em>succeeds</em>.</p>
     */
    private List<Naive> naiveCalls() {
        List<Naive> calls = new ArrayList<>();
        calls.add(new Naive("read: plural name for a repeatable field", "shio_read",
                Map.of("addresses", List.of("post:" + SITE + URL)), "address"));
        calls.add(new Naive("find: the fourth spelling of one concept", "shio_find",
                Map.of("site", SITE, "postType", "Article"), "type"));
        // The real mistake was inside an op, not at the top level: shio_write declares only `ops`,
        // and `postType`-for-`type` was written in the op object. Sending it at the top level
        // instead — which the first version of this row did — measures a *shape* mistake and
        // teaches for a different reason than the one under test.
        calls.add(new Naive("write: the same concept, inside an op", "shio_write",
                Map.of("ops", List.of(new LinkedHashMap<>(Map.of(
                                "op", "post.upsert",
                                "address", "post:" + SITE + URL,
                                "postType", "Article",
                                "data", Map.of()))),
                        "dryRun", true),
                "type"));
        calls.add(new Naive("verify: singular for a comma-separated set", "shio_verify",
                Map.of("site", SITE, "check", "content"), "checks"));
        calls.add(new Naive("changes: the word the response itself uses", "shio_changes",
                Map.of("cursor", "2026-01-01T00:00:00Z|naive-home"), "since"));
        calls.add(new Naive("remember: the endpoint's word, not the tool's", "shio_remember",
                Map.of("scope", SITE, "key", "sh340-probe", "note", "measured by SH340"), "site"));
        return calls;
    }

    /** The read tools a sweep can call without writing anything. */
    private static final List<String> READ_TOOLS = List.of(
            "shio_context", "shio_find", "shio_read", "shio_verify", "shio_digest", "shio_changes");

    @Test
    @DisplayName("SH340: a naive client's calls are counted, and a silent drop costs more")
    void aNaiveClientsCallsAreCounted() throws IOException {
        Map<String, Outcome> outcomes = new LinkedHashMap<>();
        List<String> report = new ArrayList<>();

        for (Naive naive : naiveCalls()) {
            Set<String> declared = declaredProperties(naive.tool());
            JsonNode result = callTool(naive.tool(), naive.arguments());
            Outcome outcome = classify(result, naive.arguments().keySet(), declared);
            outcomes.put(naive.label(), outcome);
            report.add("  %-14s %-46s sent=%s wanted=%s".formatted(
                    outcome, naive.label(), unknownKeys(naive.arguments().keySet(), declared),
                    naive.wanted()));
        }

        // The sweep: one unrecognised key added to an otherwise valid call. This is the half that
        // finds the next occurrence, and it is why the row count is not six forever.
        for (String tool : READ_TOOLS) {
            Set<String> declared = declaredProperties(tool);
            Map<String, Object> arguments = new LinkedHashMap<>();
            arguments.put("shio340UnknownKey", "x");
            if ("shio_read".equals(tool) || "shio_digest".equals(tool)) {
                // Both require an address, and a refusal for a *missing required* argument is not
                // the thing being measured — without this the sweep scores them as compliant for
                // the wrong reason, which is the same fixture defect the SITE constant above fixed.
                arguments.put("address", "post:" + SITE + URL);
            }
            JsonNode result = callTool(tool, arguments);
            Outcome outcome = classify(result, arguments.keySet(), declared);
            outcomes.put("sweep: " + tool, outcome);
            report.add("  %-14s sweep %s".formatted(outcome, tool));
        }

        long drops = outcomes.values().stream().filter(o -> o == Outcome.SILENT_DROP).count();
        long landed = outcomes.entrySet().stream()
                .filter(entry -> !entry.getKey().startsWith("sweep"))
                .filter(entry -> entry.getValue() == Outcome.SYNONYM_ACCEPTED
                        || entry.getValue() == Outcome.ACCEPTED)
                .count();
        long naive = outcomes.keySet().stream().filter(key -> !key.startsWith("sweep")).count();
        long failedFirstAttempts = naive - landed;

        System.out.printf("%n[SH340] naive client — %d calls: %d/%d landed first try, "
                + "%d SILENT DROP%n", outcomes.size(), landed, naive, drops);
        report.forEach(System.out::println);
        System.out.println();

        assertThat(drops)
                .as("""
                        SH340: a dropped argument is the expensive failure — the call reports                         success and the outcome is wrong, which is what SH331's scope/site case did                         (the note landed instance-wide and finding out meant reading the tool's                         Java). A teaching error costs one round trip; this costs a wrong answer                         nobody looks for.""")
                .isLessThanOrEqualTo(budget("naive.max-silent-drops"));
        assertThat(failedFirstAttempts)
                .as("""
                        first-attempt success, which is what this benchmark is named for: a call a                         real agent made, landing without a round trip. SH331 turned these from                         teaching errors and silent drops into accepted synonyms — a ceiling above                         zero here means one of them regressed.""")
                .isLessThanOrEqualTo(budget("naive.max-failed-first-attempts"));

        // The sweep is a different claim and gets a different assertion: every read tool must
        // *refuse* an argument it does not declare. Counting these against a budget would be
        // backwards — a refusal here is the fix working, so the number to watch is not how many
        // there are but whether any tool is missing from the set.
        assertThat(outcomes.entrySet().stream()
                        .filter(entry -> entry.getKey().startsWith("sweep"))
                        .filter(entry -> entry.getValue() != Outcome.TEACHING_ERROR)
                        .map(Map.Entry::getKey)
                        .toList())
                .as("""
                        an unrecognised argument is refused, once, where arguments are read                         (SH331). Before that, four of these six answered 200 having ignored the                         key — which is how a wrong outcome reports success.""")
                .isEmpty();
    }

    @Test
    @DisplayName("the guard is not vacuous: a correct call is ACCEPTED and a bad one TEACHES")
    void theGuardCanTellTheThreeApart() {
        Set<String> contextProperties = declaredProperties("shio_context");
        assertThat(contextProperties)
                .as("if the schema came back empty every key would look unknown and every row "
                        + "would read as a silent drop — the classifier would be measuring nothing")
                .isNotEmpty();

        assertThat(classify(callTool("shio_context", Map.of()), Set.of(), contextProperties))
                .as("a call with no unknown keys cannot be a drop")
                .isEqualTo(Outcome.ACCEPTED);
        assertThat(classify(callTool("shio_read", Map.of("address", "post:nope/nope")),
                declaredProperties("shio_read")))
                .as("an address that does not resolve is the teaching path (SH80)")
                .isEqualTo(Outcome.TEACHING_ERROR);
    }

    private Outcome classify(JsonNode result, Set<String> declared) {
        return classify(result, Set.of(), declared);
    }

    /**
     * A result plus what the caller sent, as one of three outcomes.
     *
     * <p>The {@code SILENT_DROP} test is the interesting one and it is deliberately mechanical: the
     * call carried a key the tool's own {@code inputSchema} does not declare, and it succeeded
     * anyway. That is checkable without knowing what the key <em>meant</em>, which is what lets the
     * sweep cover tools nobody has made a mistake on yet.</p>
     */
    private Outcome classify(JsonNode result, Set<String> sent, Set<String> declared) {
        boolean isError = result.get("isError") != null && result.get("isError").asBoolean();
        if (isError) {
            return Outcome.TEACHING_ERROR;
        }
        Set<String> unknown = unknownKeys(sent, declared);
        String body = result.get("content").get(0).get("text").asString();
        if (unknown.isEmpty()) {
            // A nested alias (inside an op) leaves every top-level key declared, so the only signal
            // that something was renamed is the note itself — which is the point of reporting it.
            return body.contains("note: read `") ? Outcome.SYNONYM_ACCEPTED : Outcome.ACCEPTED;
        }
        boolean reported = unknown.stream().allMatch(key -> body.contains("`" + key + "`"));
        return reported ? Outcome.SYNONYM_ACCEPTED : Outcome.SILENT_DROP;
    }

    private static Set<String> unknownKeys(Set<String> sent, Set<String> declared) {
        Set<String> unknown = new LinkedHashSet<>(sent);
        unknown.removeAll(declared);
        return unknown;
    }

    /** The tool's declared argument names, read from {@code tools/list} rather than from memory. */
    private Set<String> declaredProperties(String tool) {
        JsonNode tools = call("tools/list", null).get("result").get("tools");
        for (JsonNode each : tools) {
            if (tool.equals(each.get("name").asString())) {
                JsonNode properties = each.get("inputSchema").get("properties");
                Set<String> names = new LinkedHashSet<>();
                if (properties != null) {
                    properties.propertyNames().forEach(names::add);
                }
                return names;
            }
        }
        throw new IllegalStateException("no such tool: " + tool);
    }

    private JsonNode callTool(String tool, Map<String, Object> arguments) {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", tool);
        params.set("arguments", objectMapper.valueToTree(arguments));
        return call("tools/call", params).get("result");
    }

    private ObjectNode call(String method, ObjectNode params) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("jsonrpc", "2.0");
        message.put("id", 1);
        message.put("method", method);
        if (params != null) {
            message.set("params", params);
        }
        return shMcpServer.handle(message);
    }

    private static long budget(String key) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = ShAgentNaiveClientBenchmarkTest.class.getResourceAsStream(BUDGETS)) {
            assertThat(in).as("missing %s", BUDGETS).isNotNull();
            properties.load(in);
        }
        String value = properties.getProperty(key);
        assertThat(value).as("no budget declared for '%s' in %s", key, BUDGETS).isNotNull();
        return Long.parseLong(value.trim());
    }
}
