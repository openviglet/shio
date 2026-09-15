/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.mcp.tools;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.viglet.shio.api.agent.ShAgentReadService;
import com.viglet.shio.api.agent.ShAgentReadTerse;
import com.viglet.shio.api.agent.mcp.ShMcpTool;

import tools.jackson.databind.JsonNode;

/**
 * {@code shio_find} (SH83) — narrow to a set of addresses.
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShFindTool implements ShMcpTool {

    private final ShAgentReadService shAgentReadService;

    public ShFindTool(ShAgentReadService shAgentReadService) {
        this.shAgentReadService = shAgentReadService;
    }

    @Override
    public String name() {
        return "shio_find";
    }

    @Override
    public String description() {
        return "Find posts by site, post-type, folder subtree or full text. Returns addresses "
                + "and titles only — no content. Use it to decide what to read, then call "
                + "shio_read on just those addresses.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return ShMcpTool.schema(Map.of(
                "site", ShMcpTool.string("Site name; omit for every site you can read."),
                "type", ShMcpTool.string("Post-type name, e.g. Article."),
                "folder", ShMcpTool.string(
                        "Folder path like /blog, or folder:<site>/<path>. Includes everything beneath it."),
                "q", ShMcpTool.string("Full-text over title, summary and content."),
                "limit", ShMcpTool.integer("Maximum rows (default 25)."),
                // Worth its schema (P3): without it an MCP-only client is back to the dead end
                // SH146 removed — a result past the cap it can see is truncated and cannot finish.
                "after", ShMcpTool.string("Cursor from a truncated result, to get the next page."),
                // SH990 — REST's state=, within this tool's SH977 reservation.
                "state", ShMcpTool.string("published or draft.")),
                List.of());
    }

    @Override
    public String call(JsonNode arguments) {
        return ShAgentReadTerse.found(shAgentReadService.find(
                ShMcpArgs.string(arguments, "site"),
                ShMcpArgs.string(arguments, "type"),
                ShMcpArgs.string(arguments, "folder"),
                ShMcpArgs.string(arguments, "q"),
                ShMcpArgs.integer(arguments, "limit"),
                false,
                ShMcpArgs.string(arguments, "after"),
                false,
                ShMcpArgs.string(arguments, "state")));
    }
}
