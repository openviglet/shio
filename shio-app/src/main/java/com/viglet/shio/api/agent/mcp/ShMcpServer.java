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
import com.viglet.shio.api.agent.ShAgentRunService;
import com.viglet.shio.api.agent.ShAgentSessionContext;
import com.viglet.shio.api.error.ShDidYouMean;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.utils.ShStableMap;

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
 * registry only</b>. Measured against that, an SDK was a poor trade: the MCP Java SDK was
 * built on Jackson 2 and a Spring Boot 3 baseline, while this application runs Boot 4 with
 * Jackson 3 (<code>tools.jackson</code>) — so importing it bought a few hundred lines of
 * envelope handling in exchange for a second JSON stack and a standing version-compatibility
 * risk on the one surface that is supposed to be the product's front door. The
 * request/response subset below is small, fully specified, and directly testable.</p>
 *
 * <h3>What that sentence is worth, and when it was last true (SH276)</h3>
 *
 * <p>It is the largest inherited premise in this repository measured by the code it defends —
 * about fifteen hundred lines — and it is the one kind SH274's tripwires cannot reach, because
 * the artefact is <b>not on the classpath</b> and adding it to find out is the change being
 * evaluated. So the instrument is a date rather than an assertion, and
 * {@code ShMcpSdkPremiseTest} fails the build when this record goes stale. Move the date
 * whichever way the next reading goes; not knowing is the failure.</p>
 *
 * <p>SDK-PREMISE-CHECKED: 2026-08-02<br>
 * SDK-PREMISE-VERSION: io.modelcontextprotocol.sdk:mcp:2.0.0</p>
 *
 * <p><b>The finding: the Jackson half has moved, and the Boot half is gone.</b> Read from the
 * published POMs on 2026-08-02 — {@code mcp:2.0.0} pulls {@code mcp-json-jackson3:2.0.0} and
 * {@code mcp-core:2.0.0}; the JSON binding depends on
 * {@code tools.jackson.core:jackson-databind:3.0.3}, which is <em>this application's own Jackson
 * 3</em>, and the BOM offers {@code mcp-json-jackson2} and {@code mcp-json-jackson3} side by
 * side. {@code mcp-core} depends on no Spring artefact at all (slf4j, reactor-core, and
 * {@code jakarta.servlet-api} as <em>provided</em>), so "a Spring Boot 3 baseline" no longer
 * describes it. What survives is smaller than the original claim and is not nothing:
 * {@code mcp-core} still brings {@code com.fasterxml.jackson.core:jackson-annotations:2.20}
 * transitively — a Jackson <b>2 annotations</b> jar, which is metadata rather than a second
 * parser, and a much narrower cost than "a second JSON stack".</p>
 *
 * <p><b>This is not an argument to adopt the SDK, and the standing argument is now a different
 * one.</b> The trade was never only about Jackson; it is that the host stays transport +
 * registry, and that claim is carried by live evidence instead of by a claim about the
 * alternative: {@code ShMcpToolBudgetTest} caps what {@code tools/list} costs on every turn of
 * every session (P3), and {@code ShMcpWireConformanceTest} pins the wire against a golden. A
 * hand-rolled surface that stays inside its budget and conformant is a better ongoing case than
 * a sentence about somebody else's release train — and if either of those ever starts costing
 * more than the SDK would, that is the signal to revisit, not this paragraph.</p>
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
    private final ShAgentRunService shAgentRunService;
    private final ObjectMapper objectMapper;

    public ShMcpServer(List<ShMcpTool> tools,
                       List<ShMcpResource> resources,
                       List<ShMcpPrompt> prompts,
                       ShAgentRunService shAgentRunService,
                       ObjectMapper objectMapper) {
        this.shAgentRunService = shAgentRunService;
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
        // SH999 — the one call in the protocol whose purpose is a client saying what it is, and this
        // server discarded the half that said it. Kept against the session id this same call mints,
        // so the uuid on every audit row resolves to a name a reviewer recognises.
        JsonNode clientInfo = params == null ? null : params.get("clientInfo");
        // SH1000 — and what the run is for, from the same object. The cheapest place on this
        // protocol: `tools/list` is re-sent every turn and `initialize` is not, so a sentence
        // declared here is paid once per run rather than once per turn.
        ShAgentSessionContext.current().ifPresent(session ->
                shAgentRunService.introduce(session, text(clientInfo, "name"),
                        text(clientInfo, "version"), ShAgentRunService.MCP,
                        ShAgentSessionContext.actor(), text(clientInfo, "intent")));
        ObjectNode result = objectMapper.createObjectNode();
        result.put("protocolVersion", PROTOCOL_VERSION);
        ObjectNode capabilities = result.putObject("capabilities");
        capabilities.putObject("tools").put("listChanged", false);
        capabilities.putObject("resources").put("listChanged", false).put("subscribe", false);
        capabilities.putObject("prompts").put("listChanged", false);
        ObjectNode info = result.putObject("serverInfo");
        info.put("name", "shio");
        info.put("title", "Viglet Shio");
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
                        unknownTool(name), toolNames()));
        JsonNode arguments = params.get("arguments");
        ObjectNode safe = arguments instanceof ObjectNode object
                ? object : objectMapper.createObjectNode();
        try {
            // SH331 — one place where arguments are read, so an unrecognised key is refused once
            // rather than dropped eleven times. SH340 measured what it used to cost: four of six
            // read tools answered 200 while ignoring a key, and the one that mattered wrote a
            // memory note to the wrong scope and called it success.
            ShMcpVocabulary.Normalized normalized =
                    ShMcpVocabulary.normalize(name, safe, tool.inputSchema());
            return content(withNotes(tool.call(normalized.arguments()), normalized.notes()), false);
        } catch (ShTeachingException e) {
            // The whole point of SH80 is that the *model* reads the fix, so it travels
            // as a tool result rather than a protocol error, rendered as text because
            // that is what a model actually reads.
            return content(renderProblem(e), true);
        }
    }

    /**
     * Appends the SH331 synonym notes to a tool's own text.
     *
     * <p><b>After</b> the payload, never before: a caller that parses the first line of a terse
     * response must not have to know whether a note is there. And reported at all because a synonym
     * accepted in silence teaches nothing — the caller keeps sending it, which is the state SH340
     * measured as six silent drops in twelve calls.</p>
     */
    private static String withNotes(String text, List<String> notes) {
        if (notes.isEmpty()) {
            return text;
        }
        StringBuilder body = new StringBuilder(text == null ? "" : text);
        notes.forEach(note -> body.append("\nnote: ").append(note));
        return body.toString();
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
            // Canonicalized like every other payload built from a Map (SH136): the two
            // `valueToTree` calls on this class are the only points where an in-memory
            // container becomes bytes, and ShByteStabilityLintTest holds that count at two.
            error.set("data", canonical(objectMapper.valueToTree(data)));
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
        // SH757 — the slug, which every REST refusal carries as `type` and no MCP one did. The
        // prose teaches a model what to do; the slug is what a client BRANCHES on, and MCP is the
        // only surface a Claude Code plugin user has, so dropping it here left that reader unable
        // to tell one refusal from another except by matching English. Measured on a live instance:
        // the text was title/detail/fix/at, and `duplicate-url` — a slug SH719's own ledger entry
        // names as what the loser is told — appeared nowhere in it.
        //
        // The bare slug, not the `https://shio.viglet.com/errors/…` URI the REST body sends: the
        // prefix is a constant 34 characters repeated on every refusal, and the last segment is the
        // whole of what anyone compares (P3).
        String slug = errorSlug(detail.getType());
        if (slug != null) {
            out.append("\nType: ").append(slug);
        }
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

    /**
     * The last segment of a problem's {@code type} URI — {@code duplicate-url} out of
     * {@code https://shio.viglet.com/errors/duplicate-url} (SH757).
     *
     * <p>{@code null} where there is nothing to name: {@code ProblemDetail} defaults the type to
     * {@code about:blank}, which is the absence of a slug rather than a slug called "blank", and
     * printing it would teach a client to branch on a word that means "no kind".</p>
     */
    private static String errorSlug(java.net.URI type) {
        if (type == null) {
            return null;
        }
        String text = type.toString();
        if (text.isBlank() || "about:blank".equals(text)) {
            return null;
        }
        int slash = text.lastIndexOf('/');
        String slug = slash >= 0 && slash + 1 < text.length() ? text.substring(slash + 1) : text;
        return slug.isBlank() ? null : slug;
    }

    private static void appendProperty(StringBuilder out, Map<String, Object> properties,
                                       String key, String label) {
        Object value = properties.get(key);
        if (value != null) {
            out.append("\n").append(label).append(": ").append(value);
        }
    }

    /**
     * The SH80 teaching payload of an exception, as the error's {@code data}.
     *
     * <p>SH136: this used {@code Map.copyOf}, which replaced {@code ProblemDetail}'s
     * insertion order with a per-JVM hash order — so the payload whose <em>job</em> is to
     * teach (`fix`, `allowed`, `didYouMean`, `example`, `at`) came out in a different order
     * on every boot. The writer canonicalizes on top, so the order here only has to be
     * deterministic, not meaningful.</p>
     */
    private Map<String, Object> problem(ShTeachingException e) {
        var detail = e.toProblemDetail();
        return ShStableMap.copyOf(detail.getProperties());
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || !value.isString() || value.asString().isBlank()
                ? null : value.asString();
    }

    List<String> toolNames() {
        return tools.stream().map(ShMcpTool::name).toList();
    }

    /**
     * The refusal for a tool name that is not one of ours, with the edit rather than the catalogue
     * (SH717).
     *
     * <h2>What was wrong with the old message, which was not wrong</h2>
     *
     * <p>It answered {@code -32602} with every tool name in {@code data}. That is {@code allowed},
     * one of P6's four instruments, so nothing was missing in the sense a lint could see. It is
     * also the weakest of the four for this branch: the agent that wrote {@code shio_reed} <b>had
     * the registry already</b> — {@code tools/list} is the first thing it reads and P3 holds a
     * 2500-token budget on it — so what it lacks is not the catalogue but the one-character edit.
     * The catalogue costs it a re-read of something it is holding.</p>
     *
     * <h2>In the message, not in `data`</h2>
     *
     * <p>{@code data} stays the plain array of names. It is pinned by {@code
     * ShMcpWireConformanceTest} against a golden and read by SH710's live sweep, and more to the
     * point a JSON-RPC {@code error} is swallowed by most clients as a transport fault — the
     * sentence is the part a human or a model actually sees. So the suggestion goes where it is
     * read, and the catalogue stays where it is parsed.</p>
     *
     * <p>{@link ShDidYouMean} is the same helper every HTTP refusal on this surface uses, including
     * its rule about when a guess is more useful than misleading: nothing is offered unless the
     * edit distance is inside {@code max(2, len/3)}. A near-miss gets the edit; an unrelated string
     * gets the list, exactly as before.</p>
     */
    private String unknownTool(String name) {
        return ShDidYouMean.nearest(name, toolNames())
                .map(nearest -> "Unknown tool '" + name + "'. Did you mean '" + nearest + "'?")
                .orElseGet(() -> "Unknown tool '" + name + "'.");
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
