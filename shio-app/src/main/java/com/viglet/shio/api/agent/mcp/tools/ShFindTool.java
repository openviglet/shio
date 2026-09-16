/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.mcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.viglet.shio.api.agent.ShAgentReadService;
import com.viglet.shio.api.agent.ShAgentReadTerse;
import com.viglet.shio.api.agent.mcp.ShMcpTool;
import com.viglet.shio.api.query.ShContentQuery;

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
        return "Find posts by site, type, folder, state, locale, date or author. Addresses "
                + "and titles, never bodies: read only what you pick.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        // SH1022 — the content-query vocabulary, spelled here exactly as REST, the CLI and the
        // console listing spell it. The six words this gained are paid for out of SH977's
        // reservation by trimming the nine that were already here, not by raising the ceiling:
        // a schema is read on every turn of every session, which is what P3 is about.
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("site", ShMcpTool.string("Site name; omit for all you can read."));
        properties.put("type", ShMcpTool.string("Post-type name, e.g. Article."));
        properties.put("folder", ShMcpTool.string("Folder path /blog, or folder:<site>/<path>; its subtree."));
        properties.put("subtree", ShMcpTool.string("false: that folder only."));
        properties.put("q", ShMcpTool.string("Full text: title, summary, fields."));
        properties.put("limit", ShMcpTool.integer("Maximum rows (default 25)."));
        // Worth its schema (P3): without it an MCP-only client is back to the dead end
        // SH146 removed — a result past the cap it can see is truncated and cannot finish.
        properties.put("after", ShMcpTool.string("Cursor from a truncated result."));
        // SH990 — REST's state=, within this tool's SH977 reservation.
        properties.put("state", ShMcpTool.string("published or draft."));
        properties.put("locale", ShMcpTool.string("Locale tag, e.g. pt_BR."));
        properties.put("modifiedSince", ShMcpTool.string("ISO date/instant; inclusive."));
        properties.put("modifiedBefore", ShMcpTool.string("ISO date/instant; exclusive."));
        properties.put("author", ShMcpTool.string("Created by."));
        properties.put("modifier", ShMcpTool.string("Last changed by."));
        // SH991 — within this tool's SH977 reservation, which named a schedule predicate.
        properties.put("scheduled", ShMcpTool.string("any, or from..to in ISO instants."));
        // SH993 — the read half of the release ops, inside the same reservation.
        properties.put("release", ShMcpTool.string("Release name: its posts. Needs site."));
        return ShMcpTool.schema(properties, List.of());
    }

    @Override
    public String call(JsonNode arguments) {
        // The literal, not a constant: ShMcpDeclaredArgumentsTest reads this class as source
        // and asks whether each declared argument is named in the handler, which is how
        // SH952 found arguments a tool advertised and silently dropped.
        String subtree = ShMcpArgs.string(arguments, "subtree");
        ShContentQuery query = ShContentQuery.ALL
                .withSite(ShMcpArgs.string(arguments, "site"))
                .withType(ShMcpArgs.string(arguments, "type"))
                .withFolder(ShMcpArgs.string(arguments, "folder"))
                .withSubtree(subtree == null ? null : !"false".equalsIgnoreCase(subtree))
                .withQ(ShMcpArgs.string(arguments, "q"))
                .withState(ShMcpArgs.string(arguments, "state"))
                .withLocale(ShMcpArgs.string(arguments, "locale"))
                .withModifiedSince(ShMcpArgs.string(arguments, "modifiedSince"))
                .withModifiedBefore(ShMcpArgs.string(arguments, "modifiedBefore"))
                .withAuthor(ShMcpArgs.string(arguments, "author"))
                .withModifier(ShMcpArgs.string(arguments, "modifier"));
        return ShAgentReadTerse.found(shAgentReadService.find(query,
                ShMcpArgs.integer(arguments, "limit"), false,
                ShMcpArgs.string(arguments, "after"), false,
                ShMcpArgs.string(arguments, "scheduled"),
                ShMcpArgs.string(arguments, "release")));
    }
}
