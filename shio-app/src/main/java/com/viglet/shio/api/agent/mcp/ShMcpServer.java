/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.mcp;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.viglet.shio.api.agent.ShAgentManifestService;
import com.viglet.shio.api.error.ShTeachingException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The MCP protocol engine (SH75) — JSON-RPC 2.0 dispatch over the tool, resource and
 * prompt registries.
 *
 * <h2>Why the protocol is implemented rather than imported</h2>
 *
 * <p>The roadmap's own constraint for this task is that the host be a <b>transport +
 * registry only</b>. Measured against that, an SDK is a poor trade here: the MCP Java
 * SDK is built on Jackson 2 and a Spring Boot 3 baseline, while this application runs
 * Boot 4 with Jackson 3 (<code>tools.jackson</code>) — so importing it buys a few
 * hundred lines of envelope handling in exchange for a second JSON stack and a
 * standing version-compatibility risk on the one surface that is supposed to be the
 * product's front door. The request/response subset below is small, fully specified,
 * and directly testable.</p>
 *
 * <h2>Two error channels, and the difference matters</h2>
 *
 * <p>A <b>protocol error</b> (JSON-RPC {@code error}) says the message was
 * unusable — unknown method, malformed params. A <b>tool error</b> is a normal result
 * with {@code isError: true}, and it is what a caller gets for anything it could fix:
 * a bad address, an unknown post-type, a failed batch. That routing is deliberate.
 * Only the second one reaches the model; a protocol error is swallowed by the client
 * as a transport fault, so reporting "no post-type named Artcle" that way would throw
 * away the SH80 {@code didYouMean} that makes the next attempt succeed.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
public class ShMcpServer {

    /**
     * The MCP revision this server speaks. Answered verbatim when the client asks for
     * it, and answered <em>anyway</em> when the client asks for something else — the
     * spec's negotiation rule is that the server replies with a version it supports and
     * lets the client disconnect if that is unacceptable.
     */
    public static final String PROTOCOL_VERSION = "2025-06-18";

    private static final String JSONRPC = "2.0";
    static final int PARSE_ERROR = -32700;
    static final int INVALID_REQUEST = -32600;
    static final int METHOD_NOT_FOUND = -32601;
    static final int INVALID_PARAMS = -32602;
    static final int INTERNAL_ERROR = -32603;

    private final List<ShMcpTool> tools;
    private final List<ShMcpResource> resources;
    private final List<ShMcpPrompt> prompts;
    private final ObjectMapper objectMapper;

    public ShMcpServer(List<ShMcpTool> tools,
                       List<ShMcpResource> resources,
                       List<ShMcpPrompt> prompts,
                       ObjectMapper objectMapper) {
        // Sorted by name so tools/list is byte-stable: the list is part of every
        // session's context, and an unstable order would defeat client-side caching
        // for no reason (P5).
        this.tools = tools.stream().sorted(Comparator.comparing(ShMcpTool::name)).toList();
        this.resources = resources.stream().sorted(Comparator.comparing(ShMcpResource::uri)).toList();
        this.prompts = prompts.stream().sorted(Comparator.comparing(ShMcpPrompt::name)).toList();
        this.objectMapper = objectMapper;
    }

    /**
     * Handles one JSON-RPC message.
     *
     * @return the response, or {@code null} for a notification (which the transport
     *         answers with {@code 202 Accepted} and no body)
     */
    public ObjectNode handle(JsonNode message) {
        if (message == null || !message.isObject()) {
            // Arrays included: JSON-RPC batching was removed in this MCP revision, and
            // saying so is more useful than a bare "invalid request".
            return error(null, INVALID_REQUEST,
                    "Expected a single JSON-RPC object; batching is not part of MCP "
                            + PROTOCOL_VERSION + ".", null);
        }
        JsonNode id = message.get("id");
        String method = text(message, "method");
        if (method == null) {
            return error(id, INVALID_REQUEST, "Missing `method`.", null);
        }
        JsonNode params = message.get("params");
        JsonNode arguments = params == null || !params.isObject() ? objectMapper.createObjectNode() : params;

        // A notification has no id and gets no response, per JSON-RPC. `initialized` is
        // the only one this server expects; anything else is accepted and ignored
        // rather than answered, because answering a notification is a protocol error.
        if (id == null || id.isNull()) {
            return null;
        }

        try {
            return switch (method) {
                case "initialize" -> success(id, initialize(arguments));
                case "ping" -> success(id, objectMapper.createObjectNode());
                case "tools/list" -> success(id, toolsList());
                case "tools/call" -> success(id, toolsCall(arguments));
                case "resources/list" -> success(id, resourcesList());
                case "resources/templates/list" -> success(id, resourceTemplatesList());
                case "resources/read" -> success(id, resourcesRead(arguments));
                case "prompts/list" -> success(id, promptsList());
                case "prompts/get" -> success(id, promptsGet(arguments));
                default -> error(id, METHOD_NOT_FOUND, "Unknown method '" + method + "'.",
                        allowedMethods());
            };
        } catch (ShMcpProtocolException e) {
            return error(id, e.code(), e.getMessage(), e.data());
        } catch (ShTeachingException e) {
            // Reached only from resources/prompts; tools/call converts these itself.
            return error(id, INVALID_PARAMS, e.getMessage(), problem(e));
        }
    }

    // --- lifecycle ---

    private ObjectNode initialize(JsonNode params) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("protocolVersion", PROTOCOL_VERSION);
        ObjectNode capabilities = result.putObject("capabilities");
        capabilities.putObject("tools").put("listChanged", false);
        capabilities.putObject("resources").put("listChanged", false).put("subscribe", false);
        capabilities.putObject("prompts").put("listChanged", false);
        ObjectNode info = result.putObject("serverInfo");
        info.put("name", "shio");
        info.put("title", "Viglet Shio CMS");
        info.put("version", version());
        // The one call that replaces a session, said where a client will actually show
        // it. `instructions` is the only free-text field the protocol gives the server,
        // so it carries the same opening move as the manifest's `startHere` (SH76)
        // rather than a greeting.
        result.put("instructions",
                "Call shio_context first: it returns this instance's content model, its sitemap "
                        + "by path and its conventions in one response, and a digest you can compare "
                        + "next session instead of re-reading. Address content as "
                        + "post:<site>/<friendly-url> or folder:<site>/<path> — you never need a UUID. "
                        + "Write with shio_write (atomic, dry-runnable) or shio_apply (declarative and "
                        + "re-runnable); nothing you write is visible until shio_publish.");
        String requested = text(params, "protocolVersion");
        if (requested != null && !PROTOCOL_VERSION.equals(requested)) {
            result.put("_note", "Client asked for MCP " + requested + "; this server speaks "
                    + PROTOCOL_VERSION + ".");
        }
        return result;
    }

    // --- tools ---

    private ObjectNode toolsList() {
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode array = result.putArray("tools");
        for (ShMcpTool tool : tools) {
            ObjectNode node = array.addObject();
            node.put("name", tool.name());
            node.put("description", tool.description());
            node.set("inputSchema", canonical(objectMapper.valueToTree(tool.inputSchema())));
        }
        return result;
    }

    /**
     * Recursively sorts object keys (P5).
     *
     * <p>{@code inputSchema()} is built from {@code Map.of(…)}, whose iteration order is
     * a function of the JVM's hash seed — so without this, {@code tools/list} serialized
     * differently from one boot to the next. That is invisible in a unit test and
     * expensive in production: the tool list is context re-sent on every turn, and a
     * payload that changes bytes without changing meaning defeats client-side and
     * prompt-cache reuse for the single largest fixed cost of the surface. Found by the
     * SH130 conformance transcript.</p>
     */
    private JsonNode canonical(JsonNode node) {
        if (node == null || !node.isObject()) {
            if (node != null && node.isArray()) {
                ArrayNode sorted = objectMapper.createArrayNode();
                node.forEach(element -> sorted.add(canonical(element)));
                return sorted;
            }
            return node;
        }
        ObjectNode sorted = objectMapper.createObjectNode();
        node.propertyNames().stream().sorted()
                .forEach(name -> sorted.set(name, canonical(node.get(name))));
        return sorted;
    }

    private ObjectNode toolsCall(JsonNode params) {
        String name = text(params, "name");
        if (name == null) {
            throw new ShMcpProtocolException(INVALID_PARAMS, "Missing `name`.", toolNames());
        }
        ShMcpTool tool = tools.stream().filter(candidate -> candidate.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new ShMcpProtocolException(INVALID_PARAMS,
                        "Unknown tool '" + name + "'.", toolNames()));
        JsonNode arguments = params.get("arguments");
        JsonNode safe = arguments == null || !arguments.isObject()
                ? objectMapper.createObjectNode() : arguments;
        try {
            return content(tool.call(safe), false);
        } catch (ShTeachingException e) {
            // The whole point of SH80 is that the *model* reads the fix, so it travels
            // as a tool result rather than a protocol error, rendered as text because
            // that is what a model actually reads.
            return content(renderProblem(e), true);
        }
    }

    private ObjectNode content(String text, boolean isError) {
        ObjectNode result = objectMapper.createObjectNode();
        ObjectNode item = result.putArray("content").addObject();
        item.put("type", "text");
        item.put("text", text == null ? "" : text);
        result.put("isError", isError);
        return result;
    }

    // --- resources ---

    private ObjectNode resourcesList() {
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode array = result.putArray("resources");
        resources.stream().filter(resource -> !resource.template())
                .forEach(resource -> describe(array.addObject(), resource, resource.uri()));
        return result;
    }

    private ObjectNode resourceTemplatesList() {
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode array = result.putArray("resourceTemplates");
        resources.stream().filter(ShMcpResource::template).forEach(resource -> {
            ObjectNode node = array.addObject();
            node.put("uriTemplate", resource.uri());
            node.put("name", resource.name());
            node.put("description", resource.description());
            node.put("mimeType", resource.mimeType());
        });
        return result;
    }

    private ObjectNode resourcesRead(JsonNode params) {
        String uri = text(params, "uri");
        if (uri == null) {
            throw new ShMcpProtocolException(INVALID_PARAMS, "Missing `uri`.", resourceUris());
        }
        ShMcpResource resource = resources.stream()
                .filter(candidate -> candidate.matches(uri))
                .findFirst()
                .orElseThrow(() -> new ShMcpProtocolException(INVALID_PARAMS,
                        "No resource at '" + uri + "'.", resourceUris()));
        ObjectNode result = objectMapper.createObjectNode();
        ObjectNode item = result.putArray("contents").addObject();
        item.put("uri", uri);
        item.put("mimeType", resource.mimeType());
        item.put("text", resource.read(uri));
        return result;
    }

    private static void describe(ObjectNode node, ShMcpResource resource, String uri) {
        node.put("uri", uri);
        node.put("name", resource.name());
        node.put("description", resource.description());
        node.put("mimeType", resource.mimeType());
    }

    // --- prompts ---

    private ObjectNode promptsList() {
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode array = result.putArray("prompts");
        for (ShMcpPrompt prompt : prompts) {
            ObjectNode node = array.addObject();
            node.put("name", prompt.name());
            node.put("description", prompt.description());
            ArrayNode args = node.putArray("arguments");
            for (ShMcpPrompt.Argument argument : prompt.arguments()) {
                ObjectNode arg = args.addObject();
                arg.put("name", argument.name());
                arg.put("description", argument.description());
                arg.put("required", argument.required());
            }
        }
        return result;
    }

    private ObjectNode promptsGet(JsonNode params) {
        String name = text(params, "name");
        if (name == null) {
            throw new ShMcpProtocolException(INVALID_PARAMS, "Missing `name`.", promptNames());
        }
        ShMcpPrompt prompt = prompts.stream().filter(candidate -> candidate.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new ShMcpProtocolException(INVALID_PARAMS,
                        "Unknown prompt '" + name + "'.", promptNames()));
        JsonNode arguments = params.get("arguments");
        JsonNode safe = arguments == null || !arguments.isObject()
                ? objectMapper.createObjectNode() : arguments;

        ObjectNode result = objectMapper.createObjectNode();
        result.put("description", prompt.description());
        ObjectNode message = result.putArray("messages").addObject();
        message.put("role", "user");
        ObjectNode content = message.putObject("content");
        content.put("type", "text");
        content.put("text", prompt.render(safe));
        return result;
    }

    // --- envelopes ---

    private ObjectNode success(JsonNode id, ObjectNode result) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", JSONRPC);
        response.set("id", id);
        response.set("result", result);
        return response;
    }

    ObjectNode error(JsonNode id, int code, String message, Object data) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", JSONRPC);
        if (id == null) {
            response.putNull("id");
        } else {
            response.set("id", id);
        }
        ObjectNode error = response.putObject("error");
        error.put("code", code);
        error.put("message", message);
        if (data != null) {
            error.set("data", objectMapper.valueToTree(data));
        }
        return response;
    }

    /** A malformed body: the transport cannot even find an id to answer against. */
    public ObjectNode parseError(String detail) {
        return error(null, PARSE_ERROR, "Could not parse the JSON-RPC message: " + detail, null);
    }

    public ObjectNode internalError(JsonNode id, String detail) {
        return error(id, INTERNAL_ERROR, detail, null);
    }

    // --- helpers ---

    /**
     * Renders a teaching problem (SH80) as the text a model reads. Flattened
     * deliberately: a model parses prose more reliably than it parses a nested error
     * object it has never been shown a schema for, and the fields are the point.
     */
    private String renderProblem(ShTeachingException e) {
        var detail = e.toProblemDetail();
        StringBuilder out = new StringBuilder();
        out.append(detail.getTitle() == null ? "Request failed" : detail.getTitle());
        if (detail.getDetail() != null) {
            out.append("\n").append(detail.getDetail());
        }
        Map<String, Object> properties = detail.getProperties();
        if (properties != null) {
            appendProperty(out, properties, "fix", "Fix");
            appendProperty(out, properties, "didYouMean", "Did you mean");
            appendProperty(out, properties, "allowed", "Allowed");
            appendProperty(out, properties, "example", "Example");
            appendProperty(out, properties, "at", "At");
        }
        return out.toString();
    }

    private static void appendProperty(StringBuilder out, Map<String, Object> properties,
                                       String key, String label) {
        Object value = properties.get(key);
        if (value != null) {
            out.append("\n").append(label).append(": ").append(value);
        }
    }

    private Map<String, Object> problem(ShTeachingException e) {
        var detail = e.toProblemDetail();
        return detail.getProperties() == null ? Map.of() : Map.copyOf(detail.getProperties());
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || !value.isString() || value.asString().isBlank()
                ? null : value.asString();
    }

    List<String> toolNames() {
        return tools.stream().map(ShMcpTool::name).toList();
    }

    private List<String> resourceUris() {
        return resources.stream().map(ShMcpResource::uri).toList();
    }

    private List<String> promptNames() {
        return prompts.stream().map(ShMcpPrompt::name).toList();
    }

    private static List<String> allowedMethods() {
        return List.of("initialize", "ping", "tools/list", "tools/call", "resources/list",
                "resources/templates/list", "resources/read", "prompts/list", "prompts/get");
    }

    private String version() {
        String version = ShAgentManifestService.class.getPackage().getImplementationVersion();
        return version != null ? version : "dev";
    }

    /** Anything that must travel as a JSON-RPC {@code error} rather than a tool result. */
    static class ShMcpProtocolException extends RuntimeException {

        private final int code;
        private final transient Object data;

        ShMcpProtocolException(int code, String message, Object data) {
            super(message);
            this.code = code;
            this.data = data;
        }

        int code() {
            return code;
        }

        Object data() {
            return data;
        }
    }

    /** Exposed for the transport's capability probe and for tests. */
    public Optional<ShMcpTool> tool(String name) {
        return tools.stream().filter(candidate -> candidate.name().equals(name)).findFirst();
    }
}
