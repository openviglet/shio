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

/**
 * What an address resolved to (SH79).
 *
 * <p>Carries the {@code id} on purpose: this endpoint exists precisely because
 * today's console and CDA endpoints are id-keyed, so returning the id is the whole
 * point of the call. It also returns the <b>canonical</b> address, which is how a
 * caller learns the exact spelling of what it addressed loosely — a lenient
 * {@code site:Blog/blog/hello} comes back as {@code post:Blog/blog/hello}.</p>
 *
 * @param kind    {@code site} / {@code folder} / {@code post}
 * @param id      the object id — what id-keyed endpoints still need
 * @param address the canonical address, which round-trips through the resolver
 * @param title   site name / folder name / post title
 * @param parent  containing folder id (posts) or root folder id (sites)
 * @param type    post-type name (posts only)
 * @param state   {@code DRAFT} / {@code PUBLISHED} (posts only)
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@JsonInclude(Include.NON_NULL)
public record ShAgentResolved(
        String kind,
        String id,
        String address,
        String title,
        String parent,
        String type,
        String state) {
}
