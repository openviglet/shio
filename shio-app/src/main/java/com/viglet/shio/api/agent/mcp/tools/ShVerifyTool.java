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
 * <p>The three scoping arguments (SH143) are what make it worth calling <em>after every
 * write</em> rather than once at the end: this is the caller the full pass was the wrong
 * shape for. A scoped report answers "is what I just did correct", and says so in
 * {@code partial} so it is never mistaken for "is the site correct".</p>
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
        // Trimmed against the SH132 budget: this text is re-sent every turn, so each defect
        // class is named once and tersely rather than described.
        return "Check content you just wrote before a human looks: empty required fields, "
                + "dangling references, missing image bytes, duplicate URLs, empty folders, "
                + "unresolved links, async failures. Returns ok plus a fix per finding.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return ShMcpTool.schema(Map.of(
                "site", ShMcpTool.string("Site name; omit for every site you can read."),
                "checks", ShMcpTool.string("content, routes, diagnostics. Omit for all."),
                "limit", ShMcpTool.integer("Findings to return (default 200)."),
                "folder", ShMcpTool.string("folder: address — this subtree only."),
                "address", ShMcpTool.string("post: addresses, comma-separated."),
                "since", ShMcpTool.string("A shio_changes cursor — only newer content."),
                // SH990 — REST's state=: lint what is live, or the draft a publish would put there.
                "state", ShMcpTool.string("published or draft.")),
                List.of());
    }

    @Override
    public String call(JsonNode arguments) {
        return ShAgentVerifyTerse.report(shAgentVerifyService.verify(
                ShMcpArgs.string(arguments, "site"),
                ShMcpArgs.string(arguments, "checks"),
                ShMcpArgs.integer(arguments, "limit"),
                ShMcpArgs.string(arguments, "folder"),
                ShMcpArgs.string(arguments, "address"),
                ShMcpArgs.string(arguments, "since"),
                ShMcpArgs.string(arguments, "state")));
    }
}
