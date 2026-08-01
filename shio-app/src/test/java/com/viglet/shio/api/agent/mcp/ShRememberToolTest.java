/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.viglet.shio.api.agent.memory.ShAgentMemoryService;
import com.viglet.shio.api.agent.memory.dto.ShAgentNote;
import com.viglet.shio.api.agent.mcp.tools.ShRememberTool;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * SH160 — <b>an MCP client can now write what it worked out.</b>
 *
 * <p>The gap this closes was narrow and total: a pure MCP client could <em>read</em> the
 * memory, because the notes ride in the context pack for free, and had no way to write one —
 * so on that client the store stayed empty and the free read returned nothing. The manifest
 * said {@code instanceMemory: true} throughout, which was a half-truth for exactly the
 * clients the manifest exists to inform.</p>
 *
 * <p>What is asserted is the tool's <em>contract</em> rather than its wording: it is
 * registered (a tool nobody can see is not a capability), it writes and it forgets, an empty
 * note is the delete rather than a validation error, and the author comes from the run rather
 * than from the arguments — that last one is the rule SH114 keeps deliberately, so an agent
 * cannot make its own inference look like a human's instruction.</p>
 *
 * @author Alexandre Oliveira
 */
// Its own database: `notes(null)` is instance-wide, and this class asserts a note it just wrote is present and a
// forgotten one is absent — both readings include every other test's memory on a shared
// datasource (SH318).
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:shio-remember;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true")
@TestInstance(Lifecycle.PER_CLASS)
class ShRememberToolTest {

    @Autowired
    private ShRememberTool tool;
    @Autowired
    private ShAgentMemoryService memoryService;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("SH160: the tool is on tools/list — a capability nobody can see is not one")
    void theToolIsRegistered() {
        assertThat(tool.name()).isEqualTo("shio_remember");
        assertThat(tool.inputSchema())
                .as("key is the only thing a caller must supply: the note may be empty (that is "
                        + "the delete) and the scope may be omitted (that is instance-wide)")
                .containsEntry("required", java.util.List.of("key"));
    }

    @Test
    @DisplayName("SH160: a note written over MCP comes back on the memory read")
    void writesANote() {
        String key = "sh160." + UUID.randomUUID().toString().substring(0, 8);
        String result = tool.call(args(key, "Sections are composed by URL, never by id.", null));

        assertThat(result).contains("remembered", key, "instance-wide");
        assertThat(memoryService.notes(null))
                .extracting(ShAgentNote::key)
                .contains(key);
    }

    @Test
    @DisplayName("SH160: re-using a key rewrites the note rather than adding a near-duplicate")
    void rewritesByKey() {
        String key = "sh160." + UUID.randomUUID().toString().substring(0, 8);
        tool.call(args(key, "First wording.", null));
        tool.call(args(key, "Second wording.", null));

        assertThat(memoryService.notes(null).stream().filter(note -> key.equals(note.key())).toList())
                .singleElement()
                .extracting(ShAgentNote::note)
                .isEqualTo("Second wording.");
    }

    /**
     * Forgetting has no tool of its own on purpose — a twelfth tool for the rarest operation
     * on the cheapest data in the system. The empty note is the same convention
     * {@code PUT /agent/memory} already uses, so the delete costs no schema at all.
     */
    @Test
    @DisplayName("SH160: an empty note forgets, and forgetting nothing is not an error")
    void anEmptyNoteForgets() {
        String key = "sh160." + UUID.randomUUID().toString().substring(0, 8);
        tool.call(args(key, "Temporary.", null));

        assertThat(tool.call(args(key, "", null))).contains("forgot", key);
        assertThat(memoryService.notes(null)).extracting(ShAgentNote::key).doesNotContain(key);

        // Idempotent: a retry after a dropped response must not look like a failure.
        assertThat(tool.call(args(key, "", null))).contains("nothing remembered");
    }

    /**
     * The one-line confirmation echoes the scope, and that is not decoration: an agent that
     * meant to scope a note to a site and omitted the argument has just written an
     * instance-wide rule, and this is where that is cheapest to notice.
     */
    @Test
    @DisplayName("SH160: the confirmation says which scope the note landed in")
    void theConfirmationNamesTheScope() {
        String key = "sh160." + UUID.randomUUID().toString().substring(0, 8);
        assertThat(tool.call(args(key, "A rule.", null))).contains("instance-wide");
    }

    private ObjectNode args(String key, String note, String site) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("key", key);
        node.put("note", note);
        if (site != null) {
            node.put("site", site);
        }
        return node;
    }
}
