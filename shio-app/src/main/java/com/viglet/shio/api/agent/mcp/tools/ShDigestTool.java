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
        // SH1014 paid for `data` the way SH990 paid for `state`: by cutting the sentence that said
        // why a digest is useful. `tools/list` had seven tokens of headroom, it is re-sent every
        // turn, and a caller reading this has already decided to call it.
        return "Structural digest of one page: outline, landmarks, links and images with alt "
                + "text, word count. Pass address for the authoring view, url for what a visitor "
                + "gets.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return ShMcpTool.schema(Map.of(
                // SH1323 — these four descriptions were tightened to pay for `on` (P3: the list's
                // ceiling held rather than raised).
                "address", ShMcpTool.string("post:<site>/<url>, as stored."),
                "url", ShMcpTool.string("Delivery URL, if fetch is on."),
                // SH811 — a parameter and not a thirteenth tool, decided by measurement: the twelve
                // tools sum to 2499 against a 2500 ceiling, so `tools/list` has ONE token of
                // headroom, and a new tool the size of this one costs 146. This costs a fraction of
                // that and answers from the render already being taken.
                // SH1039 — `insertable` rides on `editable` for the same reason `editable` rides
                // here: it is one more section of an answer already being composed, and MCP is a
                // plugin user's only surface, so a catalogue reachable over REST alone would be P1
                // broken on the surface P1 is about.
                "include", ShMcpTool.string(
                        "editable: fields a curator edits; insertable: what fits each slot."),
                // SH990 — REST's state=, which a revision renders through like /preview.
                "state", ShMcpTool.string("published, draft or rev:<n>."),
                // SH1014 — the digest of a page that does not exist yet, which is what turns a dry
                // run into an answer about how the page will look.
                "data", ShMcpTool.string("Unsaved field values; needs write."),
                // SH1323 — a section has no layout, so its proposal is drawn on a page using it.
                "on", ShMcpTool.string("Page to draw section data on.")),
                List.of());
    }

    @Override
    public String call(JsonNode arguments) {
        String address = ShMcpArgs.string(arguments, "address");
        String state = ShMcpArgs.string(arguments, "state");

        // SH1014 — a parameter and not a thirteenth tool, by the rule this file already states: the
        // answer's SHAPE is a digest either way, and only the document it is taken of changes. What
        // does change is the authority — a caller supplying content gets the page composed from it,
        // so that path is gated by write, which the render service enforces rather than this tool.
        JsonNode proposed = arguments == null ? null : arguments.get("data");
        if (proposed != null && !proposed.isNull()) {
            return ShAgentVerifyTerse.digest(
                    shAgentRenderService.proposed(address, state, proposed,
                            ShMcpArgs.string(arguments, "on"), false).digest());
        }

        String digest = ShAgentVerifyTerse.digest(
                shAgentRenderService.digest(address, ShMcpArgs.string(arguments, "url"), state));

        // Only for an address: `url` digests somebody else's delivery, and the editable surface is
        // a claim about content this instance holds. Asking for it there would answer about a page
        // Shio does not own, which is worse than refusing (P6's rule about a silent default).
        String include = ShMcpArgs.string(arguments, "include");
        // SH1039 — `insertable` implies `editable`: the catalogue is per container of one page, and
        // the containers are on the answer `editable` composes. Asking for it alone would either
        // return a second shape or silently give the first, and implying it costs the caller the
        // fields they were going to ask for next (P2).
        boolean insertable = include != null && include.contains("insertable");
        if (include != null && (insertable || include.contains("editable")) && address != null) {
            return digest + ShAgentVerifyTerse.editable(
                    shAgentRenderService.editable(address, state, insertable));
        }
        return digest;
    }
}
