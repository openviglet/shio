/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;

/**
 * SH266 — reads {@code ?digest=} as either a flag or a page count.
 *
 * <h2>Why one parameter carries both</h2>
 *
 * <p>SH194 shipped {@code ?digest=true} with a fixed ten-page cap, and SH266 was filed because ten
 * was a number that read well rather than one anybody had measured. SH264 has since measured it — a
 * swept page is 6 queries — so the cap can be a decision instead of a guess, and the decision has
 * two halves: what the instance allows (an operator's, {@code shio.agent.render.max-write-digests})
 * and what this caller wants (an agent iterating on one layout wants two; a migration verifying a
 * section wants forty).</p>
 *
 * <p>A second parameter — {@code digest=true&digestPages=40} — would have been the obvious shape and
 * is worse: two names for one decision, one of which is meaningless without the other. Everywhere
 * else on this surface a count is the parameter ({@code limit}, {@code size}, {@code budget}), so
 * {@code ?digest=40} is the form a caller already knows. {@code true} keeps meaning "the default",
 * which is what every existing caller sends.</p>
 *
 * <h2>Why a bad value teaches instead of defaulting</h2>
 *
 * <p>{@code ?digest=yes} is a caller that believes it asked for something. Silently reading it as
 * {@code false} would answer a different question and look successful — the same false green SH80's
 * teaching errors exist to prevent, and the reason an unknown {@code format} has always been a 400
 * rather than a fallback to JSON.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShAgentDigestPages {

    private ShAgentDigestPages() {
        throw new IllegalStateException("Utility class");
    }

    /** Whether the caller asked for digests at all. */
    public static boolean wanted(String value) {
        if (value == null || value.isBlank() || "false".equalsIgnoreCase(value.trim())) {
            return false;
        }
        if ("true".equalsIgnoreCase(value.trim())) {
            return true;
        }
        return count(value) != null;
    }

    /**
     * The page count the caller named, or {@code null} for the instance's default.
     *
     * @throws BadDigestValueException a teaching 400 for anything that is neither
     */
    public static Integer count(String value) {
        if (value == null || value.isBlank()
                || "true".equalsIgnoreCase(value.trim()) || "false".equalsIgnoreCase(value.trim())) {
            return null;
        }
        try {
            int pages = Integer.parseInt(value.trim());
            if (pages <= 0) {
                // Zero is not "no digests" — that is `false`, and a caller writing `digest=0`
                // has almost certainly computed the number and got it wrong.
                throw new BadDigestValueException(value);
            }
            return pages;
        } catch (NumberFormatException e) {
            throw new BadDigestValueException(value);
        }
    }

    /** A 400 that names both spellings (SH80). */
    public static class BadDigestValueException extends ShTeachingException {

        public BadDigestValueException(String sent) {
            super(ShProblem.badRequest("bad-digest-value",
                            "`digest` is true, false, or a page count")
                    .withDetail("'" + sent + "' is neither a boolean nor a positive number of "
                            + "pages. Each page in a digest proof is a real render, so the count "
                            + "is what bounds the work a write does on your behalf.")
                    .withAllowed(java.util.List.of("true", "false", "<pages>"))
                    .withFix("Send `digest=true` for this instance's default, or a count such as "
                            + "`digest=40`, which the server clamps to its own ceiling.")
                    .withExample("POST /api/v2/agent/batch?digest=40"));
        }
    }
}
