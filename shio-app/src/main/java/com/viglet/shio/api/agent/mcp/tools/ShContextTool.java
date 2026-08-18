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

import com.viglet.shio.api.agent.ShAgentAgentsMd;
import com.viglet.shio.api.agent.ShAgentContextService;
import com.viglet.shio.api.agent.ShAgentContextTerse;
import com.viglet.shio.api.agent.dto.ShAgentContext;
import com.viglet.shio.api.agent.mcp.ShMcpTool;

import tools.jackson.databind.JsonNode;

/**
 * {@code shio_context} (SH83) — the one call that replaces a session.
 *
 * <p>Terse <b>by default</b>: the caller is a model, and this is the single largest read of a
 * session, so the ~⅓ saving SH78 measured is worth more here than the JSON a browser would
 * want.</p>
 *
 * <p>This said <em>there is no {@code format} argument</em>, on the reasoning that an option nobody
 * should choose is still an option every turn pays to read. That was right about {@code json} and
 * wrong about one value, which SH812 is: {@code format=agents-md} renders the pack as project
 * instructions (SH115), and the P1 inventory recorded the consequence of leaving it out —
 * {@code agentsMd} was {@code CLI_ONLY} because <i>the one output meant to be written to a file was
 * reachable only by whoever had a file to write it to</i>. An MCP client is usually embedded in a
 * harness that has one. The reasoning still holds for {@code json}, which is why the argument
 * documents a single value rather than three.</p>
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
                "from", ShMcpTool.string("Resume cursor from a truncated pack."),
                // SH812 — the P1 inventory named this target down to the parameter: `agentsMd` was
                // CLI_ONLY because this tool took no `format`, so the one rendering meant to be
                // written to a file was reachable only by whoever had a shell. An MCP client is
                // usually embedded in a harness that does have a filesystem, which is the case
                // SH115 built the rendering for.
                "format", ShMcpTool.string("agents-md — the pack as project instructions.")),
                List.of());
    }

    @Override
    public String call(JsonNode arguments) {
        ShAgentContext pack = shAgentContextService.context(
                ShMcpArgs.string(arguments, "site"),
                ShMcpArgs.integer(arguments, "depth"),
                ShMcpArgs.integer(arguments, "budget"),
                ShMcpArgs.string(arguments, "include"),
                ShMcpArgs.string(arguments, "from"),
                false);

        // Terse stays the default, so every existing caller pays nothing and reads what it read
        // before. An unrecognised value falls through to terse rather than refusing: the REST side
        // teaches on a bad `format` because a caller there chose one of three, and here there is
        // one alternative to the default — a 400 would cost more to explain than it saves.
        String format = ShMcpArgs.string(arguments, "format");
        return format != null && format.startsWith("agents")
                ? ShAgentAgentsMd.render(pack)
                : ShAgentContextTerse.render(pack);
    }
}
