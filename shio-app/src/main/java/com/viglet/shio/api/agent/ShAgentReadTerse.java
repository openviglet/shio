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
        // SH993 — the release a `release=` listing is the posts of, before its rows.
        if (found.release() != null) {
            out.append(ShTerse.line("release")
                    .word(found.release().address())
                    .attr("state", found.release().state())
                    .attr("posts", String.valueOf(found.release().posts()))
                    .attr("scheduled-at", found.release().scheduledAt())
                    .attr("description", found.release().description())
                    .build());
        }
        for (ShAgentFound.Row row : found.results()) {
            out.append(ShTerse.line("row")
                    .word(row.address())
                    .attr("type", row.type())
                    .attr("state", row.state())
                    // SH545 — a bare marker next to `state`, because it is the question `state`
                    // looks like it answers and does not: `state=DRAFT published` is a live page
                    // with a draft behind it, and both words are needed to read that. A flag
                    // rather than `published=false` on every draft row: the JSON omits it for the
                    // same measured reason, so the two forms say the same thing.
                    .flag("published", Boolean.TRUE.equals(row.published()))
                    // SH1324 — live, with a draft not yet published; absent like `published`.
                    .flag("changed", Boolean.TRUE.equals(row.changed()))
                    .attr("id", row.id())
                    .attr("locale", row.locale())
                    // SH991 — present only on a `scheduled=` listing, so an ordinary row is unchanged.
                    .attr("publish-at", row.publishAt())
                    .attr("unpublish-at", row.unpublishAt())
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
                    // SH545 — see `found` above: `state` says which row, this says whether the
                    // page is live, and only one of the two is what a curator asked about.
                    .flag("published", Boolean.TRUE.equals(doc.published()))
                    .attr("folder", doc.folder())
                    // SH348 — absent when it is the instance default, which the context pack
                    // publishes; `ShTerse.attr` already omits a null, so the conditional costs
                    // nothing here and a monolingual instance's line is byte-identical.
                    .attr("locale", doc.locale())
                    .attr("translationOf", doc.translationOf())
                    // SH585 — the stored spelling, absent whenever the address already says it.
                    // Here as well as in the JSON because a terse reader is the one that cannot
                    // ask a second time: it is the projection a `pull` uses.
                    .attr("url", doc.url())
                    // SH991 — only with include=schedule and only when one is set.
                    .attr("publish-at", doc.schedule() == null ? null : doc.schedule().publishAt())
                    .attr("unpublish-at", doc.schedule() == null ? null : doc.schedule().unpublishAt())
                    .attr("id", doc.id())
                    // SH1073 — SH721's digest, which the JSON form carried and this line dropped:
                    // a terse reader asking for it got the row without the one value it asked for.
                    // Absent unless asked, so every other read's line is byte-identical.
                    .attr("digest", doc.digest())
                    // SH981 — where a person opens it, and where they look at it.
                    .attr("curator", doc.curatorUrl())
                    .attr("preview", doc.previewUrl())
                    // SH1304 — and where a visitor sees it, on a live page only.
                    .attr("live", doc.liveUrl())
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
            if (doc.impact() != null && doc.impact().kind() != null) {
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
            // SH996 — what publishing this draft would do to the site's URLs, on its own line for
            // `impact`'s reason: a template's blast radius and a URL moving are different facts, and
            // a post can have both.
            if (doc.impact() != null && doc.impact().routes() != null) {
                out.append(ShTerse.line("routes")
                        .attr("goes-live", doc.impact().routes().goesLive())
                        .attr("moves-to", doc.impact().routes().moves())
                        .attr("stops", doc.impact().routes().stops())
                        .attr("inbound", doc.impact().routes().inbound() == null ? null
                                : String.valueOf(doc.impact().routes().inbound()))
                        .build());
            }
            if (doc.revisions() != null) {
                // SH987 — one line per revision, for the same reason `impact` gets its own: the
                // header describes the post as it is, and these describe what it used to be. Never
                // a body: a caller reads these to choose a number, then reads that one.
                for (var revision : doc.revisions()) {
                    out.append(ShTerse.line("revision")
                            .word(String.valueOf(revision.version()))
                            .attr("state", revision.state())
                            .attr("reason", revision.reason())
                            .attr("by", revision.actor())
                            .attr("at", revision.capturedAt() == null
                                    ? null : revision.capturedAt().toString())
                            .attr("digest", revision.digest())
                            .attr("changed", revision.changed() == null || revision.changed().isEmpty()
                                    ? null : String.join(",", revision.changed()))
                            .build());
                }
            }
            if (doc.notes() != null) {
                // SH1003 — one line per open note, and the body last because it is the only
                // field whose length is unbounded. A session reading terse gets the correction
                // here or not at all: a note only the JSON carried would be a note this surface
                // does not have.
                for (var note : doc.notes()) {
                    out.append(ShTerse.line("note")
                            .word(note.kind())
                            .attr("id", note.id())
                            .attr("field", note.field())
                            .attr("by", note.by())
                            .attr("says", note.body())
                            .build());
                }
            }
            if (doc.working() != null) {
                // SH1008 — said only while it is true, so a page nobody else is on is byte-identical
                // to what it was. `until` rather than a duration: a session that holds the answer for
                // a turn can tell whether it has gone stale without asking again.
                out.append(ShTerse.line("working")
                        .word(doc.working().agentName() == null
                                ? doc.working().session() : doc.working().agentName())
                        .attr("session", doc.working().session())
                        .attr("at", doc.working().at())
                        .attr("until", doc.working().until())
                        .attr("for", doc.working().intent())
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
