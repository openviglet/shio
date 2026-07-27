/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.dto;

import java.util.List;

import tools.jackson.databind.JsonNode;

/**
 * The batch mutation request (SH81) — N content operations applied as one atomic
 * unit.
 *
 * <h2>Why</h2>
 *
 * <p>Design law <b>P2</b>, on the write side. Building a 12-page site structure
 * against the id-keyed endpoints costs roughly two calls per page (create, then read
 * back the id to place the next thing) plus a folder call each — and every one of
 * those round trips is a chance for the agent to lose an id from its context. One
 * batch is one call, and because every op is <b>addressed</b> (SH79) rather than
 * id-keyed, no id ever has to survive between them.</p>
 *
 * <h2>Atomicity is the point, not a bonus</h2>
 *
 * <p>A half-applied structure is the worst outcome available: the agent cannot tell
 * which half landed without re-reading everything, so a partial success costs more
 * than a clean failure. The batch therefore commits entirely or not at all, and a
 * failing op names its index so the fix is local.</p>
 *
 * @param dryRun report what <em>would</em> change and write nothing (P7). The plan is
 *               computed against the same state the real run would see, including
 *               objects earlier ops in this same batch would have created
 * @param ops    the operations, applied in order. Order matters: a post may be
 *               upserted into a folder the previous op creates
 * @param confirm the SH108 confirm token from this batch's own {@code dryRun}. Required only
 *               when the batch deletes something, ignored otherwise — a token on a batch that
 *               destroys nothing would be a habit rather than a decision
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public record ShAgentBatch(boolean dryRun, List<Op> ops, String confirm) {

    /** The two-field form, for the batches that never delete (and every pre-SH108 caller). */
    public ShAgentBatch(boolean dryRun, List<Op> ops) {
        this(dryRun, ops, null);
    }

    /**
     * One operation.
     *
     * <p>Deliberately a flat, sparse record rather than a polymorphic hierarchy: a
     * tool schema is context an agent pays for on every turn (the SH83 argument), and
     * six shallow shapes are cheaper to state and to get right than six classes.
     * Fields not relevant to an {@code op} are simply absent.</p>
     *
     * @param op      one of {@code folder.upsert}, {@code folder.delete},
     *                {@code post.upsert}, {@code post.move}, {@code post.publish},
     *                {@code post.unpublish}, {@code post.delete}
     * @param address what the op acts on — {@code folder:<site>/<path>} or
     *                {@code post:<site>/<url>}. The lenient {@code site:} form is
     *                <b>not</b> accepted for writes: guessing which namespace was meant
     *                would silently create content in the wrong place (the SH79 rule)
     * @param type    post-type <em>name</em> — required when {@code post.upsert} has to
     *                create; ignored when it updates
     * @param folder  where a post goes: a folder address, or a bare path relative to the
     *                addressed site. Required when {@code post.upsert} has to create,
     *                because a post's friendly URL says nothing about its folder; on
     *                {@code post.move} it is the destination folder
     * @param to      the post's new address, for {@code post.move} — the one op that
     *                <em>changes</em> an address rather than acting on one. Kept separate
     *                from {@code address} because a move needs both ends: re-addressing
     *                an upsert instead would create a second post and orphan the first
     * @param data    the post's field values, keyed by post-type attribute name
     */
    public record Op(String op, String address, String type, String folder, String to,
                     JsonNode data) {

        /** The five-field form, for callers that never move. */
        public Op(String op, String address, String type, String folder, JsonNode data) {
            this(op, address, type, folder, null, data);
        }
    }
}
