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

import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * SH75 (MCP host) + SH83 (the tool set), at the protocol level.
 *
 * <p>What is asserted is the contract a client depends on, not the current tool list:
 * that the handshake answers with a version and capabilities, that every advertised
 * tool is self-describing, that a notification gets no response (answering one is a
 * protocol violation), and — the load-bearing one — that a <b>fixable</b> failure comes
 * back as a tool result the model can read rather than as a JSON-RPC error the client
 * swallows.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
class ShMcpServerTest {

    @Autowired
    private ShMcpServer shMcpServer;
    @Autowired
    private ObjectMapper objectMapper;

    private ObjectNode request(String method, ObjectNode params) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("jsonrpc", "2.0");
        message.put("id", 1);
        message.put("method", method);
        if (params != null) {
            message.set("params", params);
        }
        return message;
    }

    private ObjectNode call(String method, ObjectNode params) {
        return shMcpServer.handle(request(method, params));
    }

    private static List<JsonNode> array(JsonNode parent, String field) {
        return StreamSupport.stream(parent.get(field).spliterator(), false).toList();
    }

    // --- handshake ---

    @Test
    void initializeAnswersWithAVersionCapabilitiesAndTheOpeningMove() {
        JsonNode result = call("initialize", null).get("result");

        assertThat(result.get("protocolVersion").asString()).isEqualTo(ShMcpServer.PROTOCOL_VERSION);
        assertThat(result.get("capabilities").has("tools")).isTrue();
        assertThat(result.get("capabilities").has("resources")).isTrue();
        assertThat(result.get("capabilities").has("prompts")).isTrue();
        assertThat(result.get("serverInfo").get("name").asString()).isEqualTo("shio");
        // `instructions` is the only free text the protocol gives the server, so it
        // carries the same opening move as the manifest rather than a greeting.
        assertThat(result.get("instructions").asString()).contains("shio_context");
    }

    @Test
    void aVersionMismatchIsAnsweredNotRefused() {
        // The spec's rule: reply with a version you support and let the client decide.
        ObjectNode params = objectMapper.createObjectNode();
        params.put("protocolVersion", "1999-01-01");

        JsonNode result = call("initialize", params).get("result");

        assertThat(result.get("protocolVersion").asString()).isEqualTo(ShMcpServer.PROTOCOL_VERSION);
        assertThat(result.get("_note").asString()).contains("1999-01-01");
    }

    @Test
    void aNotificationGetsNoResponse() {
        ObjectNode notification = objectMapper.createObjectNode();
        notification.put("jsonrpc", "2.0");
        notification.put("method", "notifications/initialized");

        assertThat(shMcpServer.handle(notification)).isNull();
    }

    @Test
    void pingIsCheapAndEmpty() {
        assertThat(call("ping", null).get("result")).isEmpty();
    }

    // --- tools ---

    @Test
    void everyToolIsSelfDescribing() {
        List<JsonNode> tools = array(call("tools/list", null).get("result"), "tools");

        assertThat(tools).isNotEmpty().allSatisfy(tool -> {
            assertThat(tool.get("name").asString()).startsWith("shio_");
            assertThat(tool.get("description").asString()).isNotBlank();
            assertThat(tool.get("inputSchema").get("type").asString()).isEqualTo("object");
        });
    }

    @Test
    void theToolSetStaysSmallBecauseSchemasAreContext() {
        // SH83's whole argument: a 40-tool CRUD surface costs tokens on every turn of
        // every session before any work happens. If this trips, something was added
        // that should have been an argument on an existing tool.
        assertThat(shMcpServer.toolNames()).hasSizeLessThanOrEqualTo(12);
    }

    @Test
    void toolsAreListedInAStableOrder() {
        assertThat(shMcpServer.toolNames()).isSorted();
    }

    @Test
    void theIntentNamedToolsArePresent() {
        assertThat(shMcpServer.toolNames()).contains(
                "shio_context", "shio_find", "shio_read", "shio_write",
                "shio_apply", "shio_publish", "shio_assets", "shio_changes");
    }

    @Test
    void contextIsCallableAndReturnsTheTersePack() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "shio_context");
        params.set("arguments", objectMapper.createObjectNode());

        JsonNode result = call("tools/call", params).get("result");

        assertThat(result.get("isError").asBoolean()).isFalse();
        assertThat(result.get("content").get(0).get("text").asString())
                .startsWith("shio-context ");
    }

    @Test
    @DisplayName("SH227: shio_apply can ask for its own proof, the way shio_write already could")
    void applyCanAskForItsOwnProof() {
        // The gap SH157 left. It gave `verify` to shio_write for about fifteen tokens and could
        // not afford it here, so the write MOST likely to want a lint — a desired-state apply
        // writes a whole section in one call — was the one that had to spend a closing turn on
        // GET /agent/verify. The blueprint verb on this same tool has always verified
        // unconditionally, which made the document branch the only unproved write on the surface.
        //
        // Driven entirely over MCP on purpose: the site is created with shio_write's `site.upsert`
        // (free — one value in an enum that already existed, which is why shio_apply does not carry
        // a createSite argument), so this asserts the surface a client actually has.
        String site = "sh227-" + UUID.randomUUID().toString().substring(0, 8);
        ObjectNode upsert = objectMapper.createObjectNode();
        upsert.put("op", "site.upsert");
        upsert.put("address", "site:" + site);
        // `render.provision` in the same batch, because the seeded catalogue this context starts
        // with does not carry Article — and the render types are the ones an op exists to create,
        // so nothing here mutates the shared post-type catalogue by hand (SH289's trap).
        ObjectNode provision = objectMapper.createObjectNode();
        provision.put("op", "render.provision");
        provision.put("address", "site:" + site);
        ObjectNode writeArgs = objectMapper.createObjectNode();
        writeArgs.set("ops", objectMapper.createArrayNode().add(upsert).add(provision));
        assertThat(callTool("shio_write", writeArgs).get("isError").asBoolean())
                .as("the site has to exist before a document can be applied to it")
                .isFalse();

        ObjectNode applyArgs = objectMapper.createObjectNode();
        applyArgs.put("site", site);
        // A post, not just a folder: the proof lints post addresses, so a folder-only document
        // has nothing to attach and would have made this test pass for the wrong reason. `Article`
        // is provisioned by the batch above.
        applyArgs.set("folders", objectMapper.createArrayNode().add("/sh227"));
        ObjectNode post = objectMapper.createObjectNode();
        post.put("url", "/sh227/hello");
        post.put("type", "Region");
        post.put("folder", "/sh227");
        post.set("data", objectMapper.createObjectNode().put("TITLE", "Hello"));
        applyArgs.set("posts", objectMapper.createArrayNode().add(post));
        applyArgs.put("verify", true);
        String proved = text(callTool("shio_apply", applyArgs));

        applyArgs.remove("verify");
        String unproved = text(callTool("shio_apply", applyArgs));

        assertThat(proved)
                .as("""
                        `verify` on shio_apply returns the lint of what the apply touched, in the                         same encoding GET /agent/verify uses — so the closing turn of the                         write-then-check loop is an argument rather than a round trip (SH227).""")
                .contains("report ");
        assertThat(unproved)
                .as("and it stays opt-in: an apply that did not ask pays nothing for the proof")
                .doesNotContain("report ");
    }

    @Test
    void aFixableFailureComesBackAsAToolResultTheModelCanRead() {
        // The routing decision this server rests on: a teaching error (SH80) must reach
        // the *model*, so it travels as a result with isError — a JSON-RPC error would
        // be swallowed by the client as a transport fault and the `fix` would be lost.
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "shio_read");
        ObjectNode arguments = objectMapper.createObjectNode();
        arguments.put("address", "post:NoSuchSite/nope");
        params.set("arguments", arguments);

        ObjectNode response = call("tools/call", params);

        assertThat(response.has("error")).isFalse();
        JsonNode result = response.get("result");
        assertThat(result.get("isError").asBoolean()).isTrue();
        String text = result.get("content").get(0).get("text").asString();
        assertThat(text).contains("Fix:");
    }

    @Test
    void anUnknownToolIsAProtocolErrorThatNamesTheRealOnes() {
        // The other side of that line: the *message* was unusable, so the client — not
        // the model — is the one that has to change. It gets the tool list to do it.
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "shio_delete_everything");

        JsonNode error = call("tools/call", params).get("error");

        assertThat(error.get("code").asInt()).isEqualTo(-32602);
        assertThat(error.get("data").toString()).contains("shio_context");
    }

    @Test
    void anUnknownMethodListsTheOnesThatExist() {
        JsonNode error = call("tools/enumerate", null).get("error");

        assertThat(error.get("code").asInt()).isEqualTo(-32601);
        assertThat(error.get("data").toString()).contains("tools/list");
    }

    @Test
    void batchingIsRefusedWithTheReason() {
        JsonNode error = shMcpServer.handle(objectMapper.createArrayNode()).get("error");

        assertThat(error.get("code").asInt()).isEqualTo(-32600);
        assertThat(error.get("message").asString()).contains("batching");
    }

    // --- resources ---

    @Test
    void fixedResourcesAndTemplatesAreListedSeparately() {
        List<JsonNode> fixed = array(call("resources/list", null).get("result"), "resources");
        List<JsonNode> templates =
                array(call("resources/templates/list", null).get("result"), "resourceTemplates");

        assertThat(fixed).extracting(node -> node.get("uri").asString())
                .contains("shio://manifest", "shio://context")
                .doesNotContain("shio://schema/{postType}");
        assertThat(templates).extracting(node -> node.get("uriTemplate").asString())
                .contains("shio://schema/{postType}");
    }

    @Test
    void theContextResourceServesTheSamePackTheToolDoes() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("uri", "shio://context");

        JsonNode contents = call("resources/read", params).get("result").get("contents").get(0);

        assertThat(contents.get("mimeType").asString()).isEqualTo("text/plain");
        assertThat(contents.get("text").asString()).startsWith("shio-context ");
    }

    /**
     * SH596 — the reader most likely to meet a 500 could not ask what happened.
     *
     * <p>{@code /agent/diagnostics} was reachable over REST, from the CLI and from the console, and
     * over MCP it was reachable not at all. That is not one surface among several for a plugin
     * user: the Claude Code plugin wires an {@code .mcp.json} and nothing else, so an agent driving
     * an instance it did not build had a server log on somebody else's machine as its only
     * fallback. The assertion is at the <b>wire</b>, which is the boundary the gap crossed —
     * {@code resources/list} is what an MCP client actually reads, and a bean that exists but is
     * not listed is the same dead end.</p>
     */
    @Test
    void theFailureReportIsReachableOverMcp() {
        List<JsonNode> fixed = array(call("resources/list", null).get("result"), "resources");
        assertThat(fixed).extracting(node -> node.get("uri").asString())
                .contains("shio://diagnostics");

        ObjectNode params = objectMapper.createObjectNode();
        params.put("uri", "shio://diagnostics");
        JsonNode contents = call("resources/read", params).get("result").get("contents").get(0);

        assertThat(contents.get("mimeType").asString()).isEqualTo("text/plain");
        // The header states `since`, so an empty report keeps saying "nothing since then" rather
        // than "nothing ever" — the distinction the endpoint exists to preserve.
        assertThat(contents.get("text").asString()).startsWith("diagnostics ");
    }

    @Test
    void anUnknownResourceUriListsWhatExists() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("uri", "shio://manifesto");

        JsonNode error = call("resources/read", params).get("error");

        assertThat(error.get("data").toString()).contains("shio://manifest");
    }

    // --- prompts ---

    @Test
    void everyPromptDeclaresItsArguments() {
        List<JsonNode> prompts = array(call("prompts/list", null).get("result"), "prompts");

        assertThat(prompts).extracting(node -> node.get("name").asString())
                .containsExactly("add-section", "audit-content", "new-site", "translate-site");
        assertThat(prompts).allSatisfy(prompt ->
                assertThat(prompt.get("description").asString()).isNotBlank());
    }

    @Test
    void everyPromptPlansBeforeItWritesAndPublishesSeparately() {
        // These are the cheapest things in the block to get wrong: a procedure that
        // skips dryRun, or that treats publishing as part of writing, teaches an agent
        // to work in exactly the way P7 exists to prevent.
        for (String name : List.of("new-site", "add-section")) {
            ObjectNode params = objectMapper.createObjectNode();
            params.put("name", name);
            String text = call("prompts/get", params).get("result")
                    .get("messages").get(0).get("content").get("text").asString();

            assertThat(text).as(name).contains("dryRun");
            assertThat(text).as(name).contains("DRAFT");
        }
    }

    @Test
    void promptArgumentsAreInterpolated() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "translate-site");
        ObjectNode arguments = objectMapper.createObjectNode();
        arguments.put("site", "Blog");
        arguments.put("locale", "pt");
        params.set("arguments", arguments);

        String text = call("prompts/get", params).get("result")
                .get("messages").get(0).get("content").get("text").asString();

        assertThat(text).contains("\"Blog\"").contains("\"pt\"");
    }

    @Test
    void anUnknownPromptListsTheOnesThatExist() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "make-me-a-website");

        JsonNode error = call("prompts/get", params).get("error");

        assertThat(error.get("data").toString()).contains("new-site");
    }

    private JsonNode callTool(String name, ObjectNode arguments) {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", name);
        params.set("arguments", arguments);
        return call("tools/call", params).get("result");
    }

    private static String text(JsonNode result) {
        return result.get("content").get(0).get("text").asString();
    }
}
