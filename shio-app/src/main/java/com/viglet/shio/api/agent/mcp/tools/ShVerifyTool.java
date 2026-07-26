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

import com.viglet.shio.api.agent.mcp.ShMcpTool;
import com.viglet.shio.api.agent.verify.ShAgentVerifyService;
import com.viglet.shio.api.agent.verify.ShAgentVerifyTerse;

import tools.jackson.databind.JsonNode;

/**
 * {@code shio_verify} (SH99) — the tool SH83 deliberately did not ship.
 *
 * <p>It was held back for want of something real to verify: a tool that answered "looks
 * fine" without checking anything would leave the model believing its work had been
 * reviewed, which is strictly worse than knowing it had not. With the SH99/SH100 rules
 * behind it, that objection is gone and the manifest's {@code contentLint} flips with
 * it.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShVerifyTool implements ShMcpTool {

    private final ShAgentVerifyService shAgentVerifyService;

    public ShVerifyTool(ShAgentVerifyService shAgentVerifyService) {
        this.shAgentVerifyService = shAgentVerifyService;
    }

    @Override
    public String name() {
        return "shio_verify";
    }

    @Override
    public String description() {
        return "Check the content you just wrote before asking a human to look: empty required "
                + "fields, references to posts that do not exist, images with no bytes, two pages "
                + "on one URL, empty folders, links that resolve to nothing, and anything that "
                + "failed asynchronously. Returns ok plus a fix per finding. Run it after a write "
                + "and before a handoff.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return ShMcpTool.schema(Map.of(
                "site", ShMcpTool.string("Site name; omit for every site you can read."),
                "checks", ShMcpTool.string(
                        "Comma-separated: content, routes, diagnostics. Omit for all three."),
                "limit", ShMcpTool.integer("Findings to return (default 200).")),
                List.of());
    }

    @Override
    public String call(JsonNode arguments) {
        return ShAgentVerifyTerse.report(shAgentVerifyService.verify(
                ShMcpArgs.string(arguments, "site"),
                ShMcpArgs.string(arguments, "checks"),
                ShMcpArgs.integer(arguments, "limit")));
    }
}
