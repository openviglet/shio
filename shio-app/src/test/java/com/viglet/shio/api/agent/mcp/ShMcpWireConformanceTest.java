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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * SH130 — wire conformance for the hand-rolled MCP surface.
 *
 * <h2>Why this exists alongside {@link ShMcpServerTest}</h2>
 *
 * <p>That test asserts <em>behaviour</em>: an unknown tool is a protocol error, a
 * fixable failure is a tool result. All of it would still pass if someone renamed
 * {@code inputSchema} to {@code input_schema}, moved {@code isError} inside
 * {@code content}, or answered {@code resources/read} with {@code content} instead of
 * {@code contents} — and every MCP client in existence would break. A hand-rolled
 * protocol has no compiler checking it against the spec, so the bytes need pinning
 * explicitly.</p>
 *
 * <h2>Shape transcripts, not response transcripts</h2>
 *
 * <p>The goldens pin the <b>shape</b>: every key, its nesting, and the literal value of
 * the fields whose value is itself part of the contract ({@code jsonrpc},
 * {@code protocolVersion}, {@code type}, {@code mimeType}, {@code role}, error
 * {@code code}s, and the tool/prompt/resource <em>names</em>, which are the public API).
 * Free text — descriptions, {@code instructions}, rendered content — collapses to a
 * type token, because pinning prose would turn every copy edit into a golden update and
 * the goldens would stop being read.</p>
 *
 * <p>Regenerate with {@code -Dshio.mcp.golden.update=true} after an intentional protocol
 * change, and read the diff: it is exactly the set of bytes clients will see change.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
class ShMcpWireConformanceTest {

    private static final Path GOLDEN_DIR = Path.of("src/test/resources/mcp/golden");
    private static final boolean UPDATE = Boolean.getBoolean("shio.mcp.golden.update");

    /**
     * Keys whose literal value is part of the wire contract. Everything else is
     * replaced by a type token.
     *
     * <p>{@code name} is in here on purpose: the names of tools, prompts and resources
     * <em>are</em> the API an agent is written against, so renaming {@code shio_write}
     * must be as loud as renaming an endpoint.</p>
     */
    private static final Set<String> PINNED = Set.of(
            "jsonrpc", "protocolVersion", "type", "mimeType", "role", "code", "isError",
            "listChanged", "subscribe", "required", "name", "uri", "uriTemplate", "id",
            "enum", "data");

    @Autowired
    private ShMcpServer shMcpServer;
    @Autowired
    private ObjectMapper objectMapper;

    // --- the transcripts ---

    @Test
    @DisplayName("initialize answers the exact handshake envelope")
    void initializeTranscript() throws IOException {
        assertTranscript("initialize", call("initialize", null));
    }

    @Test
    @DisplayName("tools/list answers name + description + inputSchema, for every tool")
    void toolsListTranscript() throws IOException {
        assertTranscript("tools-list", call("tools/list", null));
    }

    @Test
    @DisplayName("tools/call answers content[] + isError")
    void toolsCallTranscript() throws IOException {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "shio_context");
        params.set("arguments", objectMapper.createObjectNode());
        assertTranscript("tools-call", call("tools/call", params));
    }

    @Test
    @DisplayName("a teaching failure stays a result with isError:true")
    void toolsCallErrorTranscript() throws IOException {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "shio_read");
        ObjectNode arguments = objectMapper.createObjectNode();
        arguments.put("address", "post:NoSuchSite/nope");
        params.set("arguments", arguments);
        assertTranscript("tools-call-error", call("tools/call", params));
    }

    @Test
    @DisplayName("resources/list and resources/templates/list stay separate collections")
    void resourcesListTranscript() throws IOException {
        assertTranscript("resources-list", call("resources/list", null));
        assertTranscript("resources-templates-list", call("resources/templates/list", null));
    }

    @Test
    @DisplayName("resources/read answers contents[] — plural, per spec")
    void resourcesReadTranscript() throws IOException {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("uri", "shio://manifest");
        assertTranscript("resources-read", call("resources/read", params));
    }

    @Test
    @DisplayName("prompts/list declares every argument")
    void promptsListTranscript() throws IOException {
        assertTranscript("prompts-list", call("prompts/list", null));
    }

    @Test
    @DisplayName("prompts/get answers messages[] with a single content object")
    void promptsGetTranscript() throws IOException {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "new-site");
        assertTranscript("prompts-get", call("prompts/get", params));
    }

    @Test
    @DisplayName("a JSON-RPC error carries code, message and data")
    void errorTranscript() throws IOException {
        assertTranscript("error-unknown-method", call("tools/enumerate", null));
    }

    // --- the version claim, checked against what the envelopes actually do ---

    /**
     * {@code assertThat(version).isEqualTo(PROTOCOL_VERSION)} is a tautology — it
     * compares the constant with itself and would keep passing after the constant was
     * bumped past a breaking revision. So the version is checked against the
     * <em>observable</em> obligations of the revision it claims.
     */
    @Test
    @DisplayName("the advertised protocolVersion matches the revision the envelopes implement")
    void theAdvertisedVersionIsTheOneImplemented() {
        assertThat(ShMcpServer.PROTOCOL_VERSION)
                .as("this test encodes the obligations of one specific revision; "
                        + "bumping the constant means re-reading them")
                .isEqualTo("2025-06-18");

        JsonNode initialize = call("initialize", null).get("result");
        assertThat(initialize.get("protocolVersion").asString())
                .isEqualTo(ShMcpServer.PROTOCOL_VERSION);

        // 2025-06-18 removed JSON-RPC batching. A server still accepting an array would
        // be advertising a revision it does not implement.
        JsonNode batch = shMcpServer.handle(objectMapper.createArrayNode());
        assertThat(batch.get("error").get("code").asInt()).isEqualTo(-32600);

        // Structured capability objects (not the pre-2025 booleans).
        JsonNode capabilities = initialize.get("capabilities");
        assertThat(capabilities.get("tools").isObject()).isTrue();
        assertThat(capabilities.get("resources").get("subscribe").isBoolean()).isTrue();

        // serverInfo carries `title` as of this revision, alongside name and version.
        assertThat(initialize.get("serverInfo").propertyNames())
                .containsExactlyInAnyOrder("name", "title", "version");

        // Tool results are content[] + isError — not a bare string, and not a throw.
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "shio_context");
        JsonNode result = call("tools/call", params).get("result");
        assertThat(result.get("content").isArray()).isTrue();
        assertThat(result.get("content").get(0).get("type").asString()).isEqualTo("text");
        assertThat(result.get("isError").isBoolean()).isTrue();

        // resources/read is `contents` (plural); resource templates live on their own
        // method rather than mixed into resources/list.
        ObjectNode read = objectMapper.createObjectNode();
        read.put("uri", "shio://manifest");
        assertThat(call("resources/read", read).get("result").get("contents").isArray()).isTrue();
        assertThat(call("resources/templates/list", null).get("result")
                .has("resourceTemplates")).isTrue();
    }

    @Test
    @DisplayName("tools/list is byte-identical across calls (P5)")
    void theToolListIsByteStable() {
        // The tool list is context re-sent on every turn, so unstable bytes cost real
        // money in lost cache reuse. inputSchema() is built from Map.of, whose iteration
        // order depends on the JVM hash seed — hence the canonicalization in the server.
        String first = objectMapper.writeValueAsString(call("tools/list", null));
        String second = objectMapper.writeValueAsString(call("tools/list", null));
        assertThat(first).isEqualTo(second);

        for (JsonNode tool : call("tools/list", null).get("result").get("tools")) {
            List<String> keys = List.copyOf(tool.get("inputSchema").propertyNames());
            assertThat(keys).as("%s inputSchema keys", tool.get("name").asString())
                    .isSorted();
        }
    }

    // --- machinery ---

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

    private void assertTranscript(String name, ObjectNode response) throws IOException {
        JsonNode shape = shape(response);
        String actual = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(shape) + "\n";
        Path golden = GOLDEN_DIR.resolve(name + ".json");

        if (UPDATE || !Files.exists(golden)) {
            Files.createDirectories(GOLDEN_DIR);
            Files.writeString(golden, actual, StandardCharsets.UTF_8);
            assertThat(UPDATE)
                    .withFailMessage("Created a missing golden at %s. Review it and re-run.", golden)
                    .isTrue();
            return;
        }

        assertThat(actual.replace("\r\n", "\n"))
                .withFailMessage("""
                        The %s wire shape changed. Every MCP client reads these bytes, and \
                        nothing else in the build checks them.
                        --- expected (%s)
                        %s
                        --- actual
                        %s
                        If the change is intended, regenerate with \
                        -Dshio.mcp.golden.update=true and review the diff.""",
                        name, golden, Files.readString(golden, StandardCharsets.UTF_8), actual)
                .isEqualTo(Files.readString(golden, StandardCharsets.UTF_8).replace("\r\n", "\n"));
    }

    /**
     * A pinned key keeps its literal value — unless that value is an object, in which
     * case the key is a coincidence rather than a contract. A JSON-Schema property
     * <em>named</em> {@code type} or {@code data} is a nested schema, and pinning it
     * would drag its prose description into the golden.
     */
    private static boolean pinned(String key, JsonNode value) {
        return PINNED.contains(key) && value != null && !value.isObject();
    }

    /**
     * Collapses a response to its shape: keys preserved and sorted, pinned values kept
     * literal, everything else replaced by a type token.
     */
    private JsonNode shape(JsonNode node) {
        if (node == null || node.isNull()) {
            return objectMapper.getNodeFactory().textNode("#null");
        }
        if (node.isObject()) {
            ObjectNode out = objectMapper.createObjectNode();
            node.propertyNames().stream().sorted()
                    .forEach(key -> out.set(key, pinned(key, node.get(key))
                            ? node.get(key)
                            : shape(node.get(key))));
            return out;
        }
        if (node.isArray()) {
            ArrayNode out = objectMapper.createArrayNode();
            node.forEach(element -> out.add(shape(element)));
            return out;
        }
        String token = node.isString() ? "#string"
                : node.isBoolean() ? "#boolean"
                : node.isNumber() ? "#number" : "#other";
        return objectMapper.getNodeFactory().textNode(token);
    }
}
