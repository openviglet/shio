/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.mcp.tools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import tools.jackson.databind.JsonNode;

/**
 * Argument reading for MCP tools — lenient on shape, strict on meaning.
 *
 * <h2>Why lenient</h2>
 *
 * <p>Tool arguments are produced by a model, and models are reliably inconsistent about
 * one specific thing: whether a single value is sent bare or wrapped in an array
 * ({@code "address": "post:…"} vs {@code "address": ["post:…"]}). Rejecting one of those
 * costs a round trip to teach a distinction that carries no meaning — the caller wanted
 * the same thing both times. Numbers-as-strings are the same story.</p>
 *
 * <p>This is not an argument for guessing in general. Everything the caller could
 * plausibly have <em>meant differently</em> — which namespace an address is in, which
 * post-type to create — is still refused with a teaching error. The line is: normalize
 * shapes, never intent.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShMcpArgs {

    private ShMcpArgs() {
        // utility
    }

    /** A string argument, or {@code null} when absent or blank. */
    public static String string(JsonNode arguments, String field) {
        JsonNode value = arguments == null ? null : arguments.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.isString() ? value.asString() : value.toString();
        return text.isBlank() ? null : text;
    }

    /** An integer argument, tolerating a numeric string, or {@code null}. */
    public static Integer integer(JsonNode arguments, String field) {
        JsonNode value = arguments == null ? null : arguments.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.asInt();
        }
        try {
            return Integer.valueOf(value.asString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** A boolean argument; absent means {@code false}. */
    public static boolean bool(JsonNode arguments, String field) {
        JsonNode value = arguments == null ? null : arguments.get(field);
        if (value == null || value.isNull()) {
            return false;
        }
        return value.isBoolean() ? value.asBoolean() : Boolean.parseBoolean(value.asString().trim());
    }

    /**
     * A list argument, accepting an array, a single value, or a comma-separated string.
     * All three spellings mean the same thing to the caller, so all three are read.
     */
    public static List<String> strings(JsonNode arguments, String field) {
        JsonNode value = arguments == null ? null : arguments.get(field);
        if (value == null || value.isNull()) {
            return List.of();
        }
        if (value.isArray()) {
            List<String> values = new ArrayList<>();
            value.forEach(item -> {
                String text = item.isString() ? item.asString() : item.toString();
                if (!text.isBlank()) {
                    values.add(text.trim());
                }
            });
            return List.copyOf(values);
        }
        String text = value.isString() ? value.asString() : value.toString();
        return Arrays.stream(text.split(",")).map(String::trim)
                .filter(item -> !item.isEmpty()).toList();
    }

    /** An object/array argument passed through verbatim, or {@code null}. */
    public static JsonNode node(JsonNode arguments, String field) {
        JsonNode value = arguments == null ? null : arguments.get(field);
        return value == null || value.isNull() ? null : value;
    }
}
