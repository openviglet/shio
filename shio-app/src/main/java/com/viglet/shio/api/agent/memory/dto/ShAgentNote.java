/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.memory.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * One remembered note as the agent surface reports it (SH114).
 *
 * <p>{@code scope} is a site name, or absent for an instance-wide note — absent
 * rather than {@code ""}, because the empty string is a storage detail (see
 * {@code ShAgentMemory}) and an agent reading this should not have to know it.</p>
 *
 * @param scope   site the note applies to; absent = every site
 * @param key     stable slug, and the upsert key
 * @param note    the instruction itself
 * @param author  who wrote it — a human's instruction and another agent's
 *                inference are both useful and are not the same claim
 * @param updated when it last changed
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@JsonInclude(Include.NON_NULL)
public record ShAgentNote(
        String scope,
        String key,
        String note,
        String author,
        Instant updated) {
}
