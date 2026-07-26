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

import tools.jackson.databind.JsonNode;

/**
 * One MCP prompt — a named, parameterized starting move the human picks from a menu
 * (SH75 registry, SH83 set).
 *
 * <h2>This is not Shio hosting prompts for a model</h2>
 *
 * <p>Worth being precise, because the non-goal is binding: Shio hosts <b>no model and
 * no prompts</b> in the sense that matters — there is no inference here, no provider
 * key, nothing that generates content. An MCP prompt is a <em>protocol object</em>: a
 * slash-command the user invokes in their own client, whose text is assembled from
 * this instance's real state. It is discovery, not intelligence. The distinction is
 * that the text below describes *how to drive Shio*, and never what to say.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public interface ShMcpPrompt {

    String name();

    String description();

    List<Argument> arguments();

    /**
     * Renders the user-message text.
     *
     * @param arguments the caller's {@code arguments} object; never {@code null}
     */
    String render(JsonNode arguments);

    /**
     * @param name        argument name
     * @param description what it is for
     * @param required    whether the client must supply it
     */
    record Argument(String name, String description, boolean required) {
    }
}
