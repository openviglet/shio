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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * The result of an agent search (SH83's {@code find}).
 *
 * <p><b>Identifiers, not bodies.</b> The whole reason {@code find} and {@code read} are
 * two tools rather than one search-that-returns-content: a query that matched twelve
 * posts and returned twelve bodies has spent the agent's context on eleven it did not
 * want. Find narrows, read fetches — and read projects (SH123). A row here is a
 * decision aid, which is why it carries the title and nothing else quotable.</p>
 *
 * @param count     rows returned (not rows that exist — see {@code truncated})
 * @param truncated the limit cut the result set; narrow the query or raise {@code limit}
 * @param results   the matches, ordered by address so two identical queries agree (P5)
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@JsonInclude(Include.NON_NULL)
public record ShAgentFound(int count, boolean truncated, List<Row> results, List<String> notes) {

    /** The three-field form: nothing to note, which is the common case. */
    public ShAgentFound(int count, boolean truncated, List<Row> results) {
        this(count, truncated, results, null);
    }

    /**
     * @param address the canonical {@code post:} address — how you read or write it
     * @param type    post-type name
     * @param state   {@code DRAFT} / {@code PUBLISHED}
     * @param title   the post title, so the agent can choose without a second call
     * @param id      omitted unless the caller asked for ids (SH121)
     */
    @JsonInclude(Include.NON_NULL)
    public record Row(String address, String type, String state, String title, String id) {
    }
}
