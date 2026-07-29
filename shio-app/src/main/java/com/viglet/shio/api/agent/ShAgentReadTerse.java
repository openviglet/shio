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
import com.viglet.shio.api.agent.verify.ShAgentVerifyTerse;
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
        // The cursor goes on the *header*, the same call SH88's feed made: it is the one value
        // the caller has to carry forward, and after a hundred rows it would end the cheapest
        // read on the surface with a search for it.
        StringBuilder out = new StringBuilder(ShTerse.line("found")
                .word(found.count())
                .flag("truncated", found.truncated())
                .attr("cursor", found.cursor())
                .build());
        for (ShAgentFound.Row row : found.results()) {
            out.append(ShTerse.line("row")
                    .word(row.address())
                    .attr("type", row.type())
                    .attr("state", row.state())
                    .attr("id", row.id())
                    .attr("locale", row.locale())
                    .attr("title", row.title())
                    .build());
        }
        // SH172's note travels in terse too: an `id:` address is a fallback, and the reason it
        // is one belongs with the rows rather than only in the JSON form.
        for (String note : found.notes() == null ? List.<String>of() : found.notes()) {
            out.append("note ").append(note).append('\n');
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
                    // SH348 — absent when it is the instance default, which the context pack
                    // publishes; `ShTerse.attr` already omits a null, so the conditional costs
                    // nothing here and a monolingual instance's line is byte-identical.
                    .attr("locale", doc.locale())
                    .attr("translationOf", doc.translationOf())
                    .attr("id", doc.id())
                    .attr("fields", doc.fields() == null ? null : String.join(",", doc.fields()))
                    .build());
            if (doc.error() != null) {
                // SH171: the failed row says why, in the same shape the JSON form uses. Without
                // this the terse reader would see a doc with no data and no reason.
                out.append(ShTerse.line("error")
                        .word(doc.error().type())
                        .attr("title", doc.error().title())
                        .attr("fix", doc.error().fix())
                        .build());
                continue;
            }
            if (doc.impact() != null) {
                // SH262 — its own line rather than attributes on `doc`, because the header describes
                // the post and this describes everything *else* the post reaches. A reader that does
                // not know the line skips it; one that read `doc` as the whole row would have been
                // silently given a wider meaning.
                out.append(ShTerse.line("impact")
                        .word(doc.impact().kind())
                        .attr("layouts", String.join(",", doc.impact().layouts()))
                        .attr("types", String.join(",", doc.impact().postTypes()))
                        .count("pages", doc.impact().pages())
                        .count("published", doc.impact().published())
                        .build());
            }
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
        if (result.confirm() != null) {
            out.append(ShTerse.line("confirm").word(result.confirm()).build());
        }
        // SH157 — the proof, in the encoding the verify report already has. Reusing that
        // renderer rather than inventing lines here is what keeps a finding from reading one
        // way after a write and another way after `shio_verify` (P10).
        if (result.proof() != null) {
            out.append(ShAgentVerifyTerse.report(result.proof()));
        }
        return out.toString();
    }
}
