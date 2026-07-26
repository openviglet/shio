/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import java.util.List;

import com.viglet.shio.api.agent.dto.ShAgentChangeSet;
import com.viglet.shio.api.agent.dto.ShAgentDoc;
import com.viglet.shio.api.agent.dto.ShAgentFound;

/**
 * Terse renderings for the agent write and content-read payloads (SH78's encoding,
 * applied to the SH81–SH83 surfaces).
 *
 * <p>Same rule as {@link ShAgentContextTerse}: state the shape once in the verb and
 * then only the values, because the saving is per row and these are the rows an agent
 * reads most. One deliberate exception — a post's {@code data} is emitted as
 * <b>compact JSON</b> rather than flattened into terse attributes. Field values are
 * arbitrary: nested objects, arrays, rich text with newlines and quotes. A flattening
 * that survived all of that would be a second serialization format to specify, test and
 * get wrong, and it would have to be un-flattened byte-exactly before a write. JSON is
 * already that format, the agent already reads it, and it is what the write side
 * accepts.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShAgentReadTerse {

    private ShAgentReadTerse() {
        // utility
    }

    /** A {@code find} result: one {@code row} line per match. */
    public static String found(ShAgentFound found) {
        StringBuilder out = new StringBuilder(ShTerse.line("found")
                .word(found.count())
                .flag("truncated", found.truncated())
                .build());
        for (ShAgentFound.Row row : found.results()) {
            out.append(ShTerse.line("row")
                    .word(row.address())
                    .attr("type", row.type())
                    .attr("state", row.state())
                    .attr("id", row.id())
                    .attr("title", row.title())
                    .build());
        }
        return out.toString();
    }

    /** Read results: a {@code doc} header line per post, each followed by its data. */
    public static String docs(List<ShAgentDoc> docs) {
        StringBuilder out = new StringBuilder();
        for (ShAgentDoc doc : docs) {
            out.append(ShTerse.line("doc")
                    .word(doc.address())
                    .attr("type", doc.type())
                    .attr("state", doc.state())
                    .attr("folder", doc.folder())
                    .attr("id", doc.id())
                    .attr("fields", doc.fields() == null ? null : String.join(",", doc.fields()))
                    .build());
            out.append("data ").append(doc.data() == null ? "{}" : doc.data().toString()).append('\n');
        }
        return out.toString();
    }

    /** A batch/plan/apply result: one {@code change} line per op. */
    public static String changes(ShAgentChangeSet result) {
        StringBuilder out = new StringBuilder(ShTerse.line("changeset")
                .word(result.dryRun() ? "plan" : "applied")
                .attr("ops", result.ops())
                .count("changed", result.changed())
                .build());
        for (ShAgentChangeSet.Change change : result.results()) {
            out.append(ShTerse.line("change")
                    .word(change.action())
                    .word(change.address())
                    .attr("op", change.op())
                    .attr("id", change.id())
                    .attr("note", change.detail())
                    .build());
        }
        return out.toString();
    }
}
