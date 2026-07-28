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

import com.viglet.shio.api.agent.ShAgentSessionContext;
import com.viglet.shio.api.agent.mcp.ShMcpTool;
import com.viglet.shio.api.agent.memory.ShAgentMemoryService;
import com.viglet.shio.api.agent.memory.dto.ShAgentNote;
import com.viglet.shio.api.agent.memory.dto.ShAgentNoteRequest;

import tools.jackson.databind.JsonNode;

/**
 * {@code shio_remember} (SH114, shipped by SH160) — write one note to the instance memory.
 *
 * <h2>Why this exists, having been argued against once</h2>
 *
 * <p>SH114 chose {@code shio remember} over a tool on a clear argument: a tool schema is paid
 * on <em>every turn of every session</em>, and a coding agent has a terminal, so a capability
 * used a handful of times per session should not tax every turn. That argument is right for
 * Claude Code and wrong for a pure MCP client, which has no shell — it can <em>read</em> the
 * memory (the notes ride in the context pack for free) and had no way to write one. So
 * {@code instanceMemory: true} in the manifest was a half-truth for exactly the clients the
 * manifest exists to inform.</p>
 *
 * <p>Two design laws collide here and the tie is worth stating. P3 says tokens are a measured
 * budget, and this costs the list about ninety of them per turn, forever. P1 says every
 * capability lands on MCP first, and a capability reachable only from a shell is incomplete.
 * P1 wins because of <b>what</b> is missing: the memory is the only mechanism in the product
 * that makes session N+1 cheaper than session N, and it is write-once-read-many — a store
 * nobody can write to stays empty, so the read that was already free returns nothing. Paying
 * a fixed per-turn cost to enable the one compounding saving is the trade Block M was built
 * to make. The ceiling in {@code token-budgets.properties} moves with it, measured and
 * justified, which is the mechanism working rather than being bypassed.</p>
 *
 * <h2>The two rejected shapes</h2>
 *
 * <p><b>A {@code remember} property on {@code shio_write}</b> would have cost a third as much,
 * and it does not deliver the capability. Most conclusions worth remembering are reached while
 * <em>reading</em> — "this site's blog uses Article, not Post" is learned from the model, not
 * from a write — and an argument on a write tool can only carry a note when a write happens to
 * coincide. It would also couple a note to a content transaction that may roll back, though
 * SH155 shows that part is solvable.</p>
 *
 * <p><b>Leaving it</b> and reporting the gap as a manifest flag was the honest default and the
 * state SH160 inherited. It is rejected because the flag would announce a hole rather than
 * close one, on the surface P1 puts first.</p>
 *
 * <h2>Forgetting is not here</h2>
 *
 * <p>Deliberately. An empty {@code note} deletes, which is the same convention
 * {@code PUT /agent/memory} already uses, so the delete costs no schema at all — and a
 * dedicated {@code shio_forget} would be a twelfth tool for the rarest operation on the
 * cheapest data in the system.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShRememberTool implements ShMcpTool {

    private final ShAgentMemoryService shAgentMemoryService;

    public ShRememberTool(ShAgentMemoryService shAgentMemoryService) {
        this.shAgentMemoryService = shAgentMemoryService;
    }

    @Override
    public String name() {
        return "shio_remember";
    }

    @Override
    public String description() {
        return "Record a convention, decision or human correction. Notes come back in "
                + "shio_context, so this is what stops the next session re-deriving what this "
                + "one worked out. Same key rewrites; empty note forgets.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return ShMcpTool.schema(Map.of(
                "key", ShMcpTool.string("Stable slug, e.g. blog.post-type."),
                "note", ShMcpTool.string("One sentence."),
                "site", ShMcpTool.string("Site name; omit for instance-wide.")),
                List.of("key"));
    }

    @Override
    public String call(JsonNode arguments) {
        String key = ShMcpArgs.string(arguments, "key");
        String site = ShMcpArgs.string(arguments, "site");
        String note = ShMcpArgs.string(arguments, "note");

        if (note == null || note.isBlank()) {
            boolean removed = shAgentMemoryService.forget(site, key);
            return removed ? "forgot " + key + "\n" : "nothing remembered under " + key + "\n";
        }
        ShAgentNote saved = shAgentMemoryService.remember(
                new ShAgentNoteRequest(site, key, note), author());
        // Terse, and it echoes the scope: an agent that meant to scope a note to a site and
        // omitted the argument has written an instance-wide rule, and the one-line
        // confirmation is where that is cheapest to notice.
        return "remembered " + saved.key()
                + (saved.scope() == null ? " (instance-wide)" : " for " + saved.scope()) + "\n";
    }

    /**
     * The run, never the model's claim. SH114 keeps attribution out of the request body
     * precisely so an agent cannot make its own inference look like a human's instruction —
     * and a tool argument would be exactly that body by another name. This is the same actor
     * {@code shio_write} stamps on the SH51 audit, so "who decided this" and "who changed
     * that" resolve to one identity (SH106).
     */
    private static String author() {
        return ShAgentSessionContext.actor();
    }
}
