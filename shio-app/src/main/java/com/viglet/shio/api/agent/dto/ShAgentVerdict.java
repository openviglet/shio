/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * What a curator decided about one change of one run (SH1002).
 *
 * <p>The read an agent needs and could not have: a revert leaves ordinary restore and delete audit
 * rows that look like a curator's own edit, so "my page came back" and "somebody rejected my page"
 * were the same thing on the feed.</p>
 *
 * @param session  the run it was about, so a resumed session can tell its own last run from an
 *                 earlier one
 * @param address  the page, when it still exists. Absent means it is gone, which is itself the
 *                 answer to what to do about the verdict
 * @param decision {@code APPROVE}, {@code REJECT} or {@code HOLD}
 * @param note     why — always there on a rejection, and the whole reason this read exists
 * @param by       the curator who decided
 * @param at       when
 */
@JsonInclude(Include.NON_NULL)
public record ShAgentVerdict(String session, String address, String decision, String note,
                             String by, Instant at) {
}
