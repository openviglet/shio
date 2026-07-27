/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.mcp;

import java.security.Principal;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.api.agent.ShAgentSessionContext;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The MCP streamable-HTTP transport (SH75).
 *
 * <h2>Scope: a request/response server, on purpose</h2>
 *
 * <p>The Streamable HTTP transport lets a server answer a POST with either a single
 * JSON object or an SSE stream, and lets it refuse the GET that would open a
 * server-to-client stream. This server takes the JSON option and returns {@code 405}
 * for the GET, because it has nothing to say unprompted: every tool here is a
 * synchronous read or an atomic write, there are no subscriptions, no progress
 * notifications and no sampling. Adding a stream would be transport machinery in
 * service of no capability — and the day one exists (SH90's watch mode is the obvious
 * candidate) the GET can start answering without any client changing.</p>
 *
 * <h2>Sessions</h2>
 *
 * <p>A session id is minted on {@code initialize} and returned as
 * {@code Mcp-Session-Id}; clients echo it on later requests. It carries no server-side
 * state — this server is stateless and any instance can answer any request — it exists
 * so every write can be attributed to the run that made it
 * ({@link ShAgentSessionContext}), which is what makes SH109's review queue possible.
 * A client that never sends one still works; its writes are simply attributed to the
 * principal alone.</p>
 *
 * <p><b>Auth</b> is the same as the rest of the agent surface: any authenticated
 * principal, so both a console credential and a CDA {@code Key} token reach it. The
 * dedicated {@code AGENT} scope with its own budget is SH106.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@RestController
@RequestMapping(ShMcpAPI.MCP_PATH)
@Tag(name = "MCP", description = "Model Context Protocol endpoint (streamable HTTP)")
public class ShMcpAPI {

    public static final String MCP_PATH = "/mcp";
    public static final String SESSION_HEADER = "Mcp-Session-Id";
    public static final String PROTOCOL_HEADER = "MCP-Protocol-Version";

    private final ShMcpServer shMcpServer;
    private final ObjectMapper objectMapper;

    public ShMcpAPI(ShMcpServer shMcpServer, ObjectMapper objectMapper) {
        this.shMcpServer = shMcpServer;
        this.objectMapper = objectMapper;
    }

    /**
     * Handles one JSON-RPC message.
     *
     * <p>The body arrives as a {@code String}, not a bound {@code JsonNode}: a
     * malformed message must come back as JSON-RPC {@code -32700}, and letting the
     * framework reject it first would answer an MCP client with an HTTP error it has no
     * handler for.</p>
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "MCP JSON-RPC endpoint: initialize, tools/list, tools/call, resources, prompts")
    public ResponseEntity<ObjectNode> rpc(
            @RequestBody(required = false) String body,
            @RequestHeader(name = SESSION_HEADER, required = false) String sessionId,
            Principal principal) {

        JsonNode message;
        try {
            message = body == null || body.isBlank() ? null : objectMapper.readTree(body);
        } catch (JacksonException e) {
            return ResponseEntity.ok(shMcpServer.parseError(e.getOriginalMessage()));
        }
        if (message == null) {
            return ResponseEntity.ok(shMcpServer.parseError("the body was empty"));
        }

        boolean initializing = isInitialize(message);
        String session = sessionId != null && !sessionId.isBlank()
                ? sessionId
                : (initializing ? UUID.randomUUID().toString() : null);

        ShAgentSessionContext.set(session);
        try {
            ObjectNode response = shMcpServer.handle(message);
            if (response == null) {
                // A notification: JSON-RPC forbids a response, and the transport says
                // so with 202 rather than an empty 200 that a client might try to parse.
                return ResponseEntity.accepted().build();
            }
            ResponseEntity.BodyBuilder ok = ResponseEntity.ok();
            if (initializing && session != null) {
                ok.header(SESSION_HEADER, session);
            }
            return ok.body(response);
        } finally {
            ShAgentSessionContext.clear();
        }
    }

    /**
     * The server-to-client stream, which this server does not offer.
     *
     * <p>{@code 405} with {@code Allow: POST} is the spec's own way of saying so, and
     * every compliant client handles it — as opposed to a hanging stream that never
     * emits, which looks identical to a broken server.</p>
     */
    @GetMapping
    @Operation(summary = "Not offered: this server sends no unprompted messages")
    public ResponseEntity<Void> stream() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .header(HttpHeaders.ALLOW, "POST, DELETE")
                .build();
    }

    /**
     * Ends a session. Accepted and answered {@code 204} for protocol conformance;
     * there is nothing to free, because a session id is an attribution label rather
     * than server-side state.
     */
    @DeleteMapping
    @Operation(summary = "End an MCP session")
    public ResponseEntity<Void> end() {
        return ResponseEntity.noContent().build();
    }

    private static boolean isInitialize(JsonNode message) {
        JsonNode method = message.get("method");
        return method != null && method.isString() && "initialize".equals(method.asString());
    }
}
