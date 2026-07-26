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
                         java.util.List<String> fields, JsonNode data, String id) {
}
