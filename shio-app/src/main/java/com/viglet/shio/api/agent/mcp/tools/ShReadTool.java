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
        return "Read the current content of one or more posts by address (drafts preferred, so "
                + "you see your own unpublished edits). Pass `fields` to fetch only the fields "
                + "you are about to change — a long rich-text body you do not need is the most "
                + "expensive thing you can read.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return ShMcpTool.schema(Map.of(
                "address", ShMcpTool.strings(
                        "One or more post addresses, e.g. post:Blog/blog/hello-world."),
                "fields", ShMcpTool.strings(
                        "Field names to return; omit for the whole document.")),
                List.of("address"));
    }

    @Override
    public String call(JsonNode arguments) {
        return ShAgentReadTerse.docs(shAgentReadService.read(
                ShMcpArgs.strings(arguments, "address"),
                ShMcpArgs.strings(arguments, "fields"),
                false));
    }
}
