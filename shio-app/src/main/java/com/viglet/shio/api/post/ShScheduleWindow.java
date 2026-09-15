/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;

/**
 * Which scheduled posts a listing asks for (SH991): any schedule at all, or one falling in a range.
 *
 * <h2>One parameter, not two</h2>
 *
 * <p>The design named {@code before} and {@code after}. {@code after} is already the listing's page
 * cursor on {@code /agent/find} and {@code shio_find}, so a second meaning under the same name would
 * be the collision nobody finds until a page silently skips rows. A range in one value —
 * {@code 2026-09-15T00:00:00Z..2026-09-22T00:00:00Z}, either end optional — costs one argument where
 * two cost two, on a tool list that is at its wall.</p>
 *
 * <h2>The grammar</h2>
 *
 * <ul>
 *   <li>{@code any} — a publish or an unpublish schedule is set, whenever it falls;</li>
 *   <li>{@code <from>..<to>} — a scheduled instant falls in {@code [from, to)}; either side may be
 *       empty, so {@code ..2026-09-22T00:00:00Z} is "due before" and {@code 2026-09-15T00:00:00Z..}
 *       is "from then on".</li>
 * </ul>
 *
 * @param from inclusive lower bound, or {@code null} for none
 * @param to   exclusive upper bound, or {@code null} for none
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public record ShScheduleWindow(Instant from, Instant to) {

    /** Any schedule at all. */
    public static final ShScheduleWindow ANY = new ShScheduleWindow(null, null);

    private static final String RANGE = "..";

    /**
     * Parses a {@code scheduled=} value.
     *
     * @return {@code null} when nothing was sent, which is no filter at all
     * @throws UnknownWindowException for anything outside the grammar
     */
    public static ShScheduleWindow parse(String sent) {
        if (sent == null || sent.isBlank()) {
            return null;
        }
        String text = sent.trim();
        if ("any".equals(text.toLowerCase(Locale.ROOT))) {
            return ANY;
        }
        int split = text.indexOf(RANGE);
        if (split < 0 || text.indexOf(RANGE, split + RANGE.length()) >= 0) {
            throw new UnknownWindowException(sent);
        }
        Instant from = instant(text.substring(0, split), sent);
        Instant to = instant(text.substring(split + RANGE.length()), sent);
        if (from != null && to != null && !from.isBefore(to)) {
            throw new UnknownWindowException(sent);
        }
        return new ShScheduleWindow(from, to);
    }

    private static Instant instant(String part, String sent) {
        String trimmed = part.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return Instant.parse(trimmed);
        } catch (DateTimeParseException notAnInstant) {
            throw new UnknownWindowException(sent);
        }
    }

    /** Whether either of a post's scheduled instants falls in this window. */
    public boolean matches(Instant publishAt, Instant unpublishAt) {
        return within(publishAt) || within(unpublishAt);
    }

    private boolean within(Instant at) {
        return at != null && (from == null || !at.isBefore(from)) && (to == null || at.isBefore(to));
    }

    /** A 400: {@code scheduled=} took a value outside the grammar. */
    public static class UnknownWindowException extends ShTeachingException {

        public UnknownWindowException(String sent) {
            super(ShProblem.badRequest("unknown-schedule-window",
                            "'" + sent + "' is not a schedule window")
                    .withDetail("`scheduled` is `any`, or a range of ISO-8601 instants written "
                            + "`from..to`, where either end may be left out and `from` comes first.")
                    .withAllowed(List.of("any", "<from>..<to>"))
                    .withFix("Send `scheduled=any` for every scheduled post, or a range such as "
                            + "`scheduled=2026-09-15T00:00:00Z..2026-09-22T00:00:00Z`.")
                    .withExample("scheduled=..2026-09-22T00:00:00Z"));
        }
    }
}
