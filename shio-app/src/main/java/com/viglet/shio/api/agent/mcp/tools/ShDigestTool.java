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
        // SH990 trimmed the examples of a shape (a missing hero, an empty section) to pay for
        // `state` here and on shio_verify: they said why a digest is useful, and the list is re-sent
        // every turn to callers who have already decided to call it.
        return "Structural digest of one page: heading outline, landmarks, every link and image "
                + "with its alt text, word count. Use it to confirm a page's shape instead of "
                + "looking at it. Pass address for the authoring view, url for what a visitor gets.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return ShMcpTool.schema(Map.of(
                "address", ShMcpTool.string("post:<site>/<url> — the page as Shio holds it."),
                "url", ShMcpTool.string(
                        "Absolute delivery URL, when the instance allows fetching."),
                // SH811 — a parameter and not a thirteenth tool, decided by measurement: the twelve
                // tools sum to 2499 against a 2500 ceiling, so `tools/list` has ONE token of
                // headroom, and a new tool the size of this one costs 146. This costs a fraction of
                // that and answers from the render already being taken.
                "include", ShMcpTool.string(
                        "editable — the fields a curator could edit here."),
                // SH990 — REST's state=, which a revision renders through like /preview.
                "state", ShMcpTool.string("published, draft or rev:<n>.")),
                List.of());
    }

    @Override
    public String call(JsonNode arguments) {
        String address = ShMcpArgs.string(arguments, "address");
        String state = ShMcpArgs.string(arguments, "state");
        String digest = ShAgentVerifyTerse.digest(
                shAgentRenderService.digest(address, ShMcpArgs.string(arguments, "url"), state));

        // Only for an address: `url` digests somebody else's delivery, and the editable surface is
        // a claim about content this instance holds. Asking for it there would answer about a page
        // Shio does not own, which is worse than refusing (P6's rule about a silent default).
        String include = ShMcpArgs.string(arguments, "include");
        if (include != null && include.contains("editable") && address != null) {
            return digest + ShAgentVerifyTerse.editable(shAgentRenderService.editable(address, state));
        }
        return digest;
    }
}
