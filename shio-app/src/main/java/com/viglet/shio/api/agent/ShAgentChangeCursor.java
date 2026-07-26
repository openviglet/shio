/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import java.time.Instant;
import java.time.format.DateTimeParseException;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;

/**
 * A position in the change feed (SH88): an instant plus the id that breaks its ties.
 *
 * <h2>Why it is readable</h2>
 *
 * <p>An opaque base64 blob would be the conventional choice, and it would be worse here.
 * A cursor is the one value a caller carries between sessions and pastes into a bug
 * report, and the whole point of the surface is that a person or an agent can reason
 * about what it asked for. {@code 2026-07-26T10:00:00Z|9f3c…} says where the reader is;
 * {@code eyJkIjoi…} says only that someone wanted it to look official. It stays a
 * <em>value</em> either way: nothing outside this class interprets its parts.</p>
 *
 * <h2>The empty id is deliberate</h2>
 *
 * <p>An empty id makes a cursor that sorts before every real entry at the same instant,
 * which is exactly what an anchor needs: on a quiet instance with no entries at all,
 * {@code <ceiling>|} is a usable starting position rather than "no cursor, call the
 * window again". Every real id compares greater than {@code ""}.</p>
 *
 * @param at the instant of the entry this cursor points at
 * @param id the entry's id; {@code ""} for a synthetic anchor
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public record ShAgentChangeCursor(Instant at, String id) {

    /** Separator. Not in an ISO-8601 instant and not in a UUID, so parsing is unambiguous. */
    private static final char SEPARATOR = '|';

    /** The wire form: {@code <iso-8601 instant>|<id>}. */
    @Override
    public String toString() {
        return at.toString() + SEPARATOR + (id == null ? "" : id);
    }

    /**
     * Parses a cursor a caller sent back.
     *
     * @throws MalformedCursorException a 400 that shows the shape instead of a stack trace
     */
    public static ShAgentChangeCursor parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new MalformedCursorException(raw, "it is empty");
        }
        String text = raw.trim();
        int split = text.indexOf(SEPARATOR);
        if (split < 0) {
            // The likeliest mistake by far: someone sent a bare timestamp, which would
            // silently drop every entry that ties with it. Refusing is the whole reason
            // the id is part of the cursor.
            throw new MalformedCursorException(raw, "it has no `|<id>` part, so entries that "
                    + "share its timestamp would be silently skipped");
        }
        try {
            return new ShAgentChangeCursor(Instant.parse(text.substring(0, split)),
                    text.substring(split + 1));
        } catch (DateTimeParseException e) {
            throw new MalformedCursorException(raw, "the part before `|` is not an ISO-8601 instant");
        }
    }

    /** A 400: the cursor cannot be interpreted, and guessing would lose entries. */
    public static class MalformedCursorException extends ShTeachingException {

        public MalformedCursorException(String sent, String why) {
            super(ShProblem.badRequest("malformed-cursor",
                            "'" + (sent == null ? "" : sent) + "' is not a change cursor")
                    .withDetail("A cursor is <iso-8601 instant>|<entry id> — " + why + ".")
                    .withFix("Pass back the `cursor` a previous GET /api/v2/agent/changes "
                            + "returned, or omit `since` to start from a window.")
                    .withExample("?since=2026-07-26T10:00:00Z|3f2a9c10-0000-4000-8000-000000000000"));
        }
    }
}
