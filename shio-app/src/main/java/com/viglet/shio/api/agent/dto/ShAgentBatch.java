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
public record ShAgentBatch(boolean dryRun, List<Op> ops, String confirm, boolean links) {

    /** Every caller from before SH981 asked for the console's links. */
    public ShAgentBatch(boolean dryRun, List<Op> ops, String confirm) {
        this(dryRun, ops, confirm, false);
    }

    /** The two-field form, for the batches that never delete (and every pre-SH108 caller). */
    public ShAgentBatch(boolean dryRun, List<Op> ops) {
        this(dryRun, ops, null, false);
    }

    /**
     * One operation.
     *
     * <p>Deliberately a flat, sparse record rather than a polymorphic hierarchy: a
     * tool schema is context an agent pays for on every turn (the SH83 argument), and
     * six shallow shapes are cheaper to state and to get right than six classes.
     * Fields not relevant to an {@code op} are simply absent.</p>
     *
     * @param op      one of {@code site.upsert}, {@code folder.upsert},
     *                {@code folder.move}, {@code folder.delete}, {@code post.upsert},
     *                {@code post.move}, {@code post.publish}, {@code post.unpublish},
     *                {@code post.delete}
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
     * @param to      the new address, for {@code post.move} and {@code folder.move} — the
     *                two ops that <em>change</em> an address rather than acting on one. Kept
     *                separate from {@code address} because a move needs both ends:
     *                re-addressing an upsert instead would create a second post and orphan
     *                the first, or — for a folder — leave the old subtree to be deleted.
     *                A folder's {@code to} is its whole name chain, since that is what
     *                addresses a folder (SH79); the last segment is its new name
     * @param data    the post's field values, keyed by post-type attribute name
     * @param locale  the locale a {@code post.upsert} <b>creates</b> in (SH348), absent meaning the
     *                instance default. Deliberately create-only: an upsert that relocalized an
     *                existing post would make a routine `shio push` change what language a live page
     *                claims to be in, and changing a post's locale is its own gesture the way
     *                {@code post.move} is for its address. Create is also where the loss actually was
     *                — Block I promises the tree is a complete description of a site, so a tree that
     *                cannot say "this page is pt-br" rebuilds it as `en` somewhere else.
     * @param translationOf the {@code post:} address, or bare URL in the same site, of the post this
     *                one is a translation <b>of</b> (SH348) — an <em>address</em> and never a group id,
     *                which is SH150's shape: {@code moves} names a {@code from} address rather than an
     *                id, because an agent must not have to invent an identifier the server owns.
     *                Honoured on create, like {@code locale}, and resolved to the target's translation
     *                group. Pointing at the group's anchor keeps the relationship a forest.
     * @param url     the friendly URL <b>as the source declares it</b>, for {@code post.upsert}
     *                (SH467) — the one thing an {@code address} structurally cannot carry. A source
     *                serves {@code /model-catalog/} with a 200 and 301s {@code /model-catalog} to
     *                it; a replica that stores the collapsed form mirrors that exactly backwards,
     *                and a document-relative {@code ./app/main.js} on the page then resolves one
     *                level too high. That is a correctness defect, not a redirect cost.
     *                <p>It is a field on the <em>write</em> rather than a fourth component on
     *                {@code ShAddress} because {@code ShAddress} is a value: a fourth component
     *                makes {@code post:S/x} and {@code post:S/x/} two unequal addresses for one
     *                post, which is the P5 identity split, and the render digest hashes the
     *                address. So the address stays collapsed and the declared spelling rides here,
     *                the way {@code folder}, {@code locale} and {@code translationOf} already do.
     *                <p><b>Refused unless it is a spelling of {@code address}</b> — the two must
     *                name one post, so all it may differ in is the slash the grammar drops. Absent
     *                means the address's own path, which is what every caller before SH467 sent
     * @param when    an ISO-8601 instant for {@code post.publish} and {@code post.unpublish}:
     *                schedule the transition instead of doing it now (SH342). Absent means now,
     *                which is what the op has always meant — so the two are never the same call,
     *                and a {@code when} in the past is refused rather than treated as "now". SH15's
     *                sweep is what fires it; this is the agent-native form of a capability the
     *                manifest advertised and only an id-keyed console route could reach.
     */
    public record Op(String op, String address, String type, String folder, String to,
                     JsonNode data, String when, String locale, String translationOf, String url,
                     String expect, Integer revision) {

        /**
         * The eleven-field form, before SH987 gave the vocabulary a rollback.
         *
         * <p>{@code revision} is a field on the op rather than a key inside {@code data} for
         * SH413's rule: {@code data} is the document a write declares, and this names <em>which
         * version</em> the op is about — the same category as {@code expect}, which sits beside
         * it for the same reason.</p>
         */
        public Op(String op, String address, String type, String folder, String to, JsonNode data,
                  String when, String locale, String translationOf, String url, String expect) {
            this(op, address, type, folder, to, data, when, locale, translationOf, url, expect,
                    null);
        }

        /**
         * The ten-field form, before SH721 made a write conditional on the state it was planned
         * against.
         */
        public Op(String op, String address, String type, String folder, String to, JsonNode data,
                  String when, String locale, String translationOf, String url) {
            this(op, address, type, folder, to, data, when, locale, translationOf, url, null);
        }

        /** The nine-field form, before SH467 made the declared spelling survive the address. */
        public Op(String op, String address, String type, String folder, String to, JsonNode data,
                  String when, String locale, String translationOf) {
            this(op, address, type, folder, to, data, when, locale, translationOf, null);
        }

        /** The eight-field form, before SH348 made the sibling link declarable. */
        public Op(String op, String address, String type, String folder, String to, JsonNode data,
                  String when, String locale) {
            this(op, address, type, folder, to, data, when, locale, null);
        }

        /** The seven-field form, before SH348 made a locale declarable. */
        public Op(String op, String address, String type, String folder, String to, JsonNode data,
                  String when) {
            this(op, address, type, folder, to, data, when, null, null);
        }

        /** The six-field form, for the ops that never schedule. */
        public Op(String op, String address, String type, String folder, String to, JsonNode data) {
            this(op, address, type, folder, to, data, null, null, null);
        }

        /** The five-field form, for callers that never move. */
        public Op(String op, String address, String type, String folder, JsonNode data) {
            this(op, address, type, folder, null, data, null, null, null);
        }
    }
}
