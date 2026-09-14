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
 * {@code shio_read} (SH83) — fetch post content by address, projected (SH123).
 *
 * <p>Takes a <em>list</em> of addresses so reading a section is one call rather than
 * one per page; that is the same P2 argument the batch write makes, on the read side.</p>
 *
 * <p><b>{@code impact} (SH262)</b> is the one non-projection argument, and it is here rather than on
 * a twelfth tool: the tool count is capped at 12 with 11 used, and "what would editing this template
 * affect" takes exactly the same subject a read does — an address. One boolean of schema is what
 * keeps the capability from being REST-only (P1).</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShReadTool implements ShMcpTool {

    private final ShAgentReadService shAgentReadService;

    public ShReadTool(ShAgentReadService shAgentReadService) {
        this.shAgentReadService = shAgentReadService;
    }

    @Override
    public String name() {
        return "shio_read";
    }

    @Override
    public String description() {
        // SH262 added an argument, which put tools/list over its ceiling — so the description was
        // trimmed first, the rule this repository follows before raising a budget. What went is
        // wording ("the current content of one or more posts" → "post content"), not teaching: the
        // draft-preference and the reason to project both still say why, which is what a model acts
        // on. It came out 22 tokens lighter than before the argument was added.
        // SH619 trimmed it again for the same reason, before raising the budget: what went is
        // wording, and the two things a model acts on — that reads are draft-preferred, and that
        // an unprojected body is the expensive case — both still say why.
        return "Read post content by address (drafts preferred, so you see your own unpublished "
                + "edits). Project with `fields`: an unneeded rich-text body is the most "
                + "expensive read there is.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return ShMcpTool.schema(Map.of(
                // "One or more" went with the SH262 trim: the schema type is an array, which says
                // the same thing in a place a client cannot misread. The example stays — that is
                // the part that teaches the address grammar.
                "address", ShMcpTool.strings(
                        "Post addresses, e.g. post:Blog/blog/hello-world."),
                "fields", ShMcpTool.strings(
                        "Names to return; omit for all."),
                "impact", ShMcpTool.bool(
                        "For a layout or region, report what renders through it."),
                // SH619 — the only way to see what is LIVE. Two reads of one address, with and
                // without this, is "what will change when I publish", which nothing could answer.
                // SH987 — `rev:<n>` is the third reading of one parameter, and deliberately so: a
                // caller asking which version it is reading asks it in one place.
                "state", ShMcpTool.string(
                        "`published` reads the live row; `rev:<n>` a captured revision."),
                // SH1073 — SH721's digest, which REST has taken as `include=digest` since it
                // shipped and MCP could not ask for. Declaring shio_write's `expect` without this
                // would advertise a guard whose only input no MCP client can obtain.
                "digest", ShMcpTool.bool("Return the value shio_write's expect takes."),
                // SH981 — where a person edits it and where they look at it, for the turn that
                // hands the work over. Off by default: measured at 26 est. tokens a row.
                "links", ShMcpTool.bool("Return the console and preview URLs for each post."),
                // SH987 — the page's history, without bodies: numbers, dates, actors and which
                // fields each revision changed, which is what a caller reads to choose one.
                "revisions", ShMcpTool.bool("List captured revisions: number, when, who, changed.")),
                List.of("address"));
    }

    @Override
    public String call(JsonNode arguments) {
        return ShAgentReadTerse.docs(shAgentReadService.read(
                ShMcpArgs.strings(arguments, "address"),
                ShMcpArgs.strings(arguments, "fields"),
                false,
                ShMcpArgs.bool(arguments, "impact"),
                ShMcpArgs.string(arguments, "state"),
                ShMcpArgs.bool(arguments, "digest"),
                ShMcpArgs.bool(arguments, "links"),
                ShMcpArgs.bool(arguments, "revisions")));
    }
}
