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
 * The result of a batch (SH81) or of a plan/apply run (SH82) — one row per op, in
 * the order they were given.
 *
 * <p>The same shape answers all three verbs on purpose. {@code plan} is
 * {@code apply} with {@code applied=false}, and an agent that can read one can read
 * the others; a separate "plan document" type would be a second vocabulary for the
 * same facts (law P10). It is also what makes the plan reviewable by a human before
 * anything changes (P7).</p>
 *
 * @param dryRun  nothing was written
 * @param ops     how many operations were considered
 * @param changed how many of them are not {@code noop} — the number worth reading.
 *                A {@code changed} of zero on a plan is the drift-guard's green light,
 *                the same signal {@code shio push --check} gives for post-types (SH65)
 * @param results one row per op
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@JsonInclude(Include.NON_NULL)
public record ShAgentChangeSet(boolean dryRun, int ops, int changed, List<Change> results) {

    /**
     * What one op did, or would do.
     *
     * @param op      the requested operation, echoed so a result row stands alone
     * @param address the canonical address it acted on
     * @param action  {@code create} · {@code update} · {@code publish} ·
     *                {@code unpublish} · {@code delete} · {@code noop}. An unchanged
     *                object reports {@code noop} rather than being omitted: "I looked
     *                and there was nothing to do" is an answer, and omitting it would
     *                make the agent ask again
     * @param detail  a short human/agent-readable note, only when it adds something the
     *                action does not already say (e.g. which parent folders were created)
     * @param id      the object id — omitted unless the caller asked for ids (SH121)
     */
    @JsonInclude(Include.NON_NULL)
    public record Change(String op, String address, String action, String detail, String id) {

        /** The no-change outcome, which is the common one on a second run (P5). */
        public static final String NOOP = "noop";
        public static final String CREATE = "create";
        public static final String UPDATE = "update";
        public static final String PUBLISH = "publish";
        public static final String UNPUBLISH = "unpublish";
        public static final String DELETE = "delete";

        public boolean isChange() {
            return !NOOP.equals(action);
        }
    }
}
