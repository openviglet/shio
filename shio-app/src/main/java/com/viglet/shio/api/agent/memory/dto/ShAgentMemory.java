/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.memory.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * The memory listing (SH114) — every note this credential can see, plus how many
 * of them there are.
 *
 * <p>{@code notes} is capped by the configured limit and {@code found} always
 * describes the full set, so a cut is visible rather than inferred (SH138's rule).</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@JsonInclude(Include.NON_NULL)
public record ShAgentMemory(int found, List<ShAgentNote> notes) {
}
