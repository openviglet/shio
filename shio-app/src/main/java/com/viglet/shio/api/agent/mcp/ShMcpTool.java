/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.mcp;

import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;

/**
 * One tool on the MCP surface (SH75's registry contract; the tool set is SH83).
 *
 * <h2>The rule every implementation follows</h2>
 *
 * <p>A tool is a <b>shape</b> over an existing service — never a place where logic
 * lives (law P10). If a tool needs behaviour the REST surface cannot express, the
 * behaviour belongs on the REST surface and the tool calls it. This is what keeps a
 * bug from having two possible homes.</p>
 *
 * <h2>Why the schemas are hand-written and small</h2>
 *
 * <p>A tool schema is <em>context</em>: it is re-sent on every turn of every session,
 * before any work happens. Generating schemas from DTOs would produce complete,
 * correct, and ruinously expensive descriptions of records nobody needs in full. Each
 * schema here is written to be the shortest thing that still stops the agent guessing
 * — which is the same budget argument the manifest's curated endpoint index makes.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public interface ShMcpTool {

    /** Tool name as the model sees it — {@code shio_*}, intent-named, not resource×verb. */
    String name();

    /**
     * What the tool is for, in the agent's terms. Read on every turn, so it states the
     * <em>decision</em> the agent has to make ("use this instead of N calls to …")
     * rather than restating the parameter list, which the schema already carries.
     */
    String description();

    /** JSON Schema for {@code arguments}. Keep it flat and short. */
    Map<String, Object> inputSchema();

    /**
     * Runs the tool.
     *
     * @param arguments the caller's {@code arguments} object; never {@code null}
     *                  (an absent object arrives as an empty one)
     * @return the text the model sees
     * @throws com.viglet.shio.api.error.ShTeachingException for anything the caller can
     *         fix — the server renders it as a tool <em>result</em> with
     *         {@code isError}, not as a JSON-RPC protocol error, so the model reads the
     *         {@code fix} and retries instead of the client treating it as a transport
     *         fault it cannot act on
     */
    String call(JsonNode arguments);

    /** Convenience for the common {@code {"type":"object","properties":{…}}} schema. */
    static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
        return required.isEmpty()
                ? Map.of("type", "object", "properties", properties)
                : Map.of("type", "object", "properties", properties, "required", required);
    }

    /** One string property. */
    static Map<String, Object> string(String description) {
        return Map.of("type", "string", "description", description);
    }

    /** One boolean property. */
    static Map<String, Object> bool(String description) {
        return Map.of("type", "boolean", "description", description);
    }

    /** One integer property. */
    static Map<String, Object> integer(String description) {
        return Map.of("type", "integer", "description", description);
    }

    /** A free-form object property (post data, op lists, desired-state documents). */
    static Map<String, Object> object(String description) {
        return Map.of("type", "object", "description", description);
    }

    /** An array of strings. */
    static Map<String, Object> strings(String description) {
        return Map.of("type", "array", "description", description,
                "items", Map.of("type", "string"));
    }
}
