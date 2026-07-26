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

import com.viglet.shio.api.agent.ShAgentContextService;
import com.viglet.shio.api.agent.ShAgentContextTerse;
import com.viglet.shio.api.agent.mcp.ShMcpTool;

import tools.jackson.databind.JsonNode;

/**
 * {@code shio_context} (SH83) — the one call that replaces a session.
 *
 * <p>Terse, always: the caller is a model, and this is the single largest read of a
 * session, so the ~⅓ saving SH78 measured is worth more here than the JSON a browser
 * would want. There is no {@code format} argument for the same reason a tool schema is
 * kept short — an option nobody should choose is still an option every turn pays to
 * read.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShContextTool implements ShMcpTool {

    private final ShAgentContextService shAgentContextService;

    public ShContextTool(ShAgentContextService shAgentContextService) {
        this.shAgentContextService = shAgentContextService;
    }

    @Override
    public String name() {
        return "shio_context";
    }

    @Override
    public String description() {
        return "Start here. Returns this Shio instance's content model (post-types and their "
                + "fields), its sitemap by path, and its conventions — everything you would "
                + "otherwise discover in 10-30 calls. Also returns a digest: store it, and next "
                + "session compare it before re-reading anything.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return ShMcpTool.schema(Map.of(
                "site", ShMcpTool.string("Site name; omit for every site you can read."),
                "include", ShMcpTool.string(
                        "Comma-separated sections to keep: instance,sites,model,sitemap,conventions."),
                "depth", ShMcpTool.integer("Folder-tree depth to walk (default 3)."),
                "budget", ShMcpTool.integer("Estimated-token ceiling; 0 for unbounded."),
                "from", ShMcpTool.string("Resume cursor from a truncated pack.")),
                List.of());
    }

    @Override
    public String call(JsonNode arguments) {
        return ShAgentContextTerse.render(shAgentContextService.context(
                ShMcpArgs.string(arguments, "site"),
                ShMcpArgs.integer(arguments, "depth"),
                ShMcpArgs.integer(arguments, "budget"),
                ShMcpArgs.string(arguments, "include"),
                ShMcpArgs.string(arguments, "from"),
                false));
    }
}
