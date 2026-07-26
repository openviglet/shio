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
import com.viglet.shio.api.agent.verify.ShAgentRenderService;
import com.viglet.shio.api.agent.verify.ShAgentVerifyTerse;

import tools.jackson.databind.JsonNode;

/**
 * {@code shio_digest} (SH101) — see a page's structure without a screenshot.
 *
 * <p>A separate tool from {@code shio_verify} because it answers a different question.
 * Verify asks "is anything wrong across the site"; this asks "what shape did this one
 * page come out". Folding the second into the first as an argument would produce a tool
 * whose output shape depended on which parameter was set, which is two tools wearing one
 * name — the thing the SH132 budget test exists to catch.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShDigestTool implements ShMcpTool {

    private final ShAgentRenderService shAgentRenderService;

    public ShDigestTool(ShAgentRenderService shAgentRenderService) {
        this.shAgentRenderService = shAgentRenderService;
    }

    @Override
    public String name() {
        return "shio_digest";
    }

    @Override
    public String description() {
        return "Structural digest of one page: heading outline, landmarks, every link and image "
                + "with its alt text, word count. Use it to confirm a page's shape — a missing "
                + "hero, an empty section, an image with no alt — instead of asking someone to "
                + "look at it. Pass address for the authoring view, url for what a visitor gets.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return ShMcpTool.schema(Map.of(
                "address", ShMcpTool.string("post:<site>/<url> — the page as Shio holds it."),
                "url", ShMcpTool.string(
                        "Absolute delivery URL, when the instance allows fetching.")),
                List.of());
    }

    @Override
    public String call(JsonNode arguments) {
        return ShAgentVerifyTerse.digest(shAgentRenderService.digest(
                ShMcpArgs.string(arguments, "address"),
                ShMcpArgs.string(arguments, "url")));
    }
}
