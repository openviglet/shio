/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import tools.jackson.databind.JsonNode;

/**
 * One post as an agent reads it (SH83's {@code read}, projected by SH123's
 * {@code fields}).
 *
 * <p>Deliberately <b>not</b> the {@code ShPostUnified} entity and not the CDA's
 * {@code ShCdaPost}: this is the authoring view (DRAFT-preferred, `data` verbatim as
 * the write side expects it) with none of the authoring metadata an agent read never
 * includes by default — no owner, date, publisher or publishStatus (the SH78 standing
 * rule). What is left is exactly what you need to change the post and write it back.</p>
 *
 * @param address the canonical {@code post:} address
 * @param type    post-type name — the vocabulary a write uses
 * @param state   {@code DRAFT} / {@code PUBLISHED}: which row this is — <b>not</b> whether the
 *                post is live. Reads are DRAFT-preferred, so this is {@code DRAFT} for any post
 *                that has a draft row, published or not; see {@code published}
 * @param folder  the containing folder's path, needed to create a sibling
 * @param fields  the field names actually returned when a projection was applied;
 *                {@code null} when the whole document is here. Stating it costs a few
 *                tokens and removes the "is this all of it?" round trip
 * @param data    field values keyed by post-type attribute name
 * @param id      omitted unless the caller asked for ids (SH121)
 * @param published <b>is this page live</b> (SH545) — {@code true} when a {@code PUBLISHED} row
 *                exists, and <b>absent otherwise</b>. {@code state} answers which row was read
 *                and cannot answer this, because a published post keeps its draft and the read
 *                prefers it; before SH545 every published page on every site reported
 *                {@code DRAFT} and an agent had to call the public route and infer publication
 *                from an HTTP code. Conditional rather than always-present because the
 *                measurement said so: an unconditional key cost 2.6% of a single-address read
 *                and dropped {@code attribution.addressing@scale.min-token-ratio} below its
 *                floor. That makes it SH348's pattern (absent where it carries nothing) rather
 *                than SH299's (present and zero), and it is the right one here — publication has
 *                a knowable default, since P7 makes DRAFT what a write lands in and the context
 *                pack states that on the first call a session makes
 * @param url     the post's <b>stored</b> friendly URL, and <b>absent whenever it is the address's
 *                own path</b> — which is every post that does not carry a trailing slash (SH585).
 *                SH467 made the declared spelling storable and this is the read half of it:
 *                {@code address} is the identity and collapses {@code /x/} to {@code /x}, so
 *                without this the one fact SH467 preserved is invisible to every reader, and a
 *                {@code shio pull} rebuilds the site one character short. Conditional for
 *                {@code locale}'s reason and with a stronger version of its argument: the default
 *                is not merely knowable, it is <em>already on the row</em> — a reader holding
 *                {@code address} and no {@code url} has the answer in its hand
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@JsonInclude(Include.NON_NULL)
public record ShAgentDoc(String address, String type, String state, String folder,
                         java.util.List<String> fields, JsonNode data, String id,
                         Problem error, Impact impact, String locale, String translationOf,
                         Boolean published, String url, String digest, String curatorUrl,
                         String previewUrl) {

    /** The fourteen-field form, before SH981 attached the console's own links. */
    public ShAgentDoc(String address, String type, String state, String folder,
                      java.util.List<String> fields, JsonNode data, String id,
                      Problem error, Impact impact, String locale, String translationOf,
                      Boolean published, String url, String digest) {
        this(address, type, state, folder, fields, data, id, error, impact, locale, translationOf,
                published, url, digest, null, null);
    }

    /** The thirteen-field form, before SH721 made a write conditional on what was read. */
    public ShAgentDoc(String address, String type, String state, String folder,
                      java.util.List<String> fields, JsonNode data, String id,
                      Problem error, Impact impact, String locale, String translationOf,
                      Boolean published, String url) {
        this(address, type, state, folder, fields, data, id, error, impact, locale, translationOf,
                published, url, null, null, null);
    }

    /** The twelve-field form, before SH585 made the stored spelling readable. */
    public ShAgentDoc(String address, String type, String state, String folder,
                      java.util.List<String> fields, JsonNode data, String id,
                      Problem error, Impact impact, String locale, String translationOf,
                      Boolean published) {
        this(address, type, state, folder, fields, data, id, error, impact, locale, translationOf,
                published, null);
    }

    /**
     * The same row with SH721's state digest attached — what {@code expect} on a write op takes.
     *
     * <p>Attached only where a caller asked for it ({@code digest=true}), which is SH121's shape
     * and not timidity: it is sixteen hex characters plus a key on <em>every</em> row of every
     * read, and the caller who needs it is the one about to write. A field every reader pays for
     * and one in fifty uses is the P3 regression this surface measures for.</p>
     */
    public ShAgentDoc withDigest(String stateDigest) {
        return stateDigest == null ? this
                : new ShAgentDoc(address, type, state, folder, fields, data, id, error, impact,
                        locale, translationOf, published, url, stateDigest, curatorUrl, previewUrl);
    }

    /**
     * The same row with SH585's stored spelling attached, or unchanged when there is nothing to
     * say — {@code declared} equal to the address's own path is the overwhelming case, and a key
     * repeating what is already on the row is one every reader pays for and none of them uses.
     */
    public ShAgentDoc withUrl(String declared) {
        if (declared == null || declared.isBlank() || address == null) {
            return this;
        }
        int slash = address.indexOf('/');
        String path = slash < 0 ? null : address.substring(slash);
        return declared.equals(path)
                ? this
                : new ShAgentDoc(address, type, state, folder, fields, data, id, error, impact,
                        locale, translationOf, published, declared, digest, curatorUrl, previewUrl);
    }

    /**
     * The nine-field form, before SH348 made a post's locale visible.
     *
     * <p><b>{@code locale} is present only when it is not the instance default</b>, which is the P3
     * shape: a monolingual instance — the overwhelming case — pays nothing per row, and the field is
     * always there where it carries information. Absence is unambiguous rather than merely cheap,
     * because the context pack publishes the default on the one call SH77 tells a session to make
     * first, so a reader interpreting an absent locale already holds the answer. That is what makes
     * this SH121's pattern (ids off by default) rather than SH299's (present and zero): a marker
     * count has no knowable default, and this does.</p>
     */
    public ShAgentDoc(String address, String type, String state, String folder,
                      java.util.List<String> fields, JsonNode data, String id,
                      Problem error, Impact impact) {
        this(address, type, state, folder, fields, data, id, error, impact, null, null, null);
    }

    /** The same row with SH348's locale attached. */
    public ShAgentDoc withLocale(String postLocale) {
        return new ShAgentDoc(address, type, state, folder, fields, data, id, error, impact,
                postLocale, translationOf, published, url, digest, curatorUrl, previewUrl);
    }

    /**
     * The address of the post this one is a translation <b>of</b> (SH348), or {@code null}.
     *
     * <p>Always the group's <em>anchor</em> — the member whose id is the group id, which is the post
     * the others were created from. Pointing every translation at the anchor rather than at each other
     * makes the relationship a forest instead of a graph: acyclic, deterministic, and expressible in
     * one field per file. The anchor itself reports nothing, which is how a reader tells the original
     * from its translations without a second call.</p>
     *
     * <p>Absent for a post with no siblings, which is almost every post — so this costs nothing on a
     * monolingual instance, the same P3 shape as {@code locale} beside it.</p>
     */
    public ShAgentDoc withTranslationOf(String anchorAddress) {
        return new ShAgentDoc(address, type, state, folder, fields, data, id, error, impact, locale,
                anchorAddress, published, url, digest, curatorUrl, previewUrl);
    }

    /** The seven-field form: a row that resolved, which is every row before SH171. */
    public ShAgentDoc(String address, String type, String state, String folder,
                      java.util.List<String> fields, JsonNode data, String id) {
        this(address, type, state, folder, fields, data, id, null, null, null, null, null);
    }

    /** The eight-field form: SH171's failed row, before SH262 added the impact projection. */
    public ShAgentDoc(String address, String type, String state, String folder,
                      java.util.List<String> fields, JsonNode data, String id, Problem error) {
        this(address, type, state, folder, fields, data, id, error, null, null, null, null);
    }

    /**
     * The same row with SH545's publication state attached, when it is live.
     *
     * <p>Callers pass {@code null} for a draft rather than {@code FALSE}: absence is the answer,
     * grounded in the convention the context pack states on a session's first call — <i>writes land
     * in DRAFT; publishing is a separate, explicit call</i>. A {@link Problem} row carries nothing
     * here either, and for a second reason: a read that could not resolve the post has no
     * publication state to report, and {@code false} would assert one.</p>
     */
    public ShAgentDoc withPublished(Boolean live) {
        return new ShAgentDoc(address, type, state, folder, fields, data, id, error, impact,
                locale, translationOf, live, url, digest, curatorUrl, previewUrl);
    }

    /**
     * The same row with the console's own link attached (SH981).
     *
     * <p>An agent's closing move used to be a sentence — "change it in the console" with no URL —
     * and the console's routes live in TypeScript the backend cannot read. They are declared now
     * ({@code console/surfaces.json}), so this is the route that declaration carries rather than a
     * string concatenated here, which is what makes it survive the next time the prefix moves.</p>
     *
     * <p>Both fields are absent when there is nothing to point at: a folder has no preview, and an
     * instance whose console has no page for a kind hands back no link rather than a broken one.</p>
     */
    public ShAgentDoc withLinks(String console, String preview) {
        return console == null && preview == null ? this
                : new ShAgentDoc(address, type, state, folder, fields, data, id, error, impact,
                        locale, translationOf, published, url, digest, console, preview);
    }

    /** The same row with SH262's blast radius attached. */
    public ShAgentDoc withImpact(Impact blastRadius) {
        return new ShAgentDoc(address, type, state, folder, fields, data, id, error, blastRadius,
                locale, translationOf, published, url, digest, curatorUrl, previewUrl);
    }

    /**
     * What editing this post would affect — SH262, and only when {@code include=impact} asked.
     *
     * <h2>Why it rides a read rather than getting an endpoint</h2>
     *
     * <p>SH193 answers "what did I just touch" on the change feed and SH194 answers it on a write.
     * The question an agent asks <em>first</em> — before rewriting a shared region, what does it
     * reach — had no surface. A {@code GET /agent/impact} would have been the clearest thing to read
     * and a fourth contract restating a graph two other surfaces already expose (P10), and it would
     * have been REST-only: the MCP tool count is capped at 12 with 11 used, so a new endpoint with no
     * tool is a capability law P1 forbids. Riding {@code /agent/read} means {@code shio_read} carries
     * it with no new schema, and the subject of a read is already an address.</p>
     *
     * <p><b>Opt-in, and absent otherwise.</b> Most reads want the fields; a caller that has not asked
     * about the blast radius should not pay for the graph walk or the tokens. Absent rather than
     * empty for a post that is not a template, the rule SH121 set for ids.</p>
     *
     * @param kind      {@code region}, {@code layout} or {@code theme} (SH286)
     * @param layouts   the layouts involved — a region's slotters, or the layout itself
     * @param postTypes the post-types whose pages render through them
     * @param pages     draft pages of those types in this site
     * @param published published pages — the number that decides whether to edit now
     */
    @JsonInclude(Include.NON_NULL)
    public record Impact(String kind, java.util.List<String> layouts,
                         java.util.List<String> postTypes, int pages, int published) {
    }

    /**
     * Why one row failed (SH171).
     *
     * <p>A multi-address read exists so N posts cost one round trip; answering the whole call
     * with a 404 because one address was wrong inverted the saving, because the agent then
     * re-read them one at a time to find out which. So a row that cannot resolve carries the
     * SH80 teaching fields <em>in place of</em> its content, and the reader keeps every row it
     * asked for. The <b>single</b>-address form still 404s — that is correct, and callers
     * depend on it.</p>
     *
     * @param type the problem slug's URI, the same value the 4xx body carries
     * @param title what is wrong, in one line
     * @param fix   what to do instead
     */
    public record Problem(String type, String title, String fix) {
    }
}
