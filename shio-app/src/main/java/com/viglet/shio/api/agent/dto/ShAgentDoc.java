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
 * @param state   {@code DRAFT} / {@code PUBLISHED}: which row this is
 * @param folder  the containing folder's path, needed to create a sibling
 * @param fields  the field names actually returned when a projection was applied;
 *                {@code null} when the whole document is here. Stating it costs a few
 *                tokens and removes the "is this all of it?" round trip
 * @param data    field values keyed by post-type attribute name
 * @param id      omitted unless the caller asked for ids (SH121)
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@JsonInclude(Include.NON_NULL)
public record ShAgentDoc(String address, String type, String state, String folder,
                         java.util.List<String> fields, JsonNode data, String id,
                         Problem error, Impact impact) {

    /** The seven-field form: a row that resolved, which is every row before SH171. */
    public ShAgentDoc(String address, String type, String state, String folder,
                      java.util.List<String> fields, JsonNode data, String id) {
        this(address, type, state, folder, fields, data, id, null, null);
    }

    /** The eight-field form: SH171's failed row, before SH262 added the impact projection. */
    public ShAgentDoc(String address, String type, String state, String folder,
                      java.util.List<String> fields, JsonNode data, String id, Problem error) {
        this(address, type, state, folder, fields, data, id, error, null);
    }

    /** The same row with SH262's blast radius attached. */
    public ShAgentDoc withImpact(Impact blastRadius) {
        return new ShAgentDoc(address, type, state, folder, fields, data, id, error, blastRadius);
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
