/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

/**
 * The terse line format (SH78) — a compact alternative encoding for agent reads.
 *
 * <p><b>Why not just JSON.</b> JSON spends most of a listing's bytes on structure
 * that repeats per row: braces, quotes around every key, and the key names
 * themselves once per element. A 50-row listing pays that 50 times. The terse form
 * states the shape once, in the verb, and then only the values — which is roughly a
 * third to a half fewer tokens on listings, the single largest agent read. JSON
 * remains the default (it is what every other client expects); {@code format=terse}
 * is opt-in, and the agent tools (SH83) opt in.</p>
 *
 * <h2>Grammar</h2>
 * <pre>
 * line       := verb (SP value)* (SP key '=' value)* LF
 * value      := bare | quoted
 * bare       := [^ "\n]+            // no space, no quote
 * quoted     := '"' (escaped)* '"'  // used iff the value is empty or holds a space or a quote
 * </pre>
 *
 * <p>One rule, applied everywhere: a value is quoted exactly when it is empty or
 * contains a space or a {@code "} (escaped as {@code \"}). Absent/blank attributes
 * are omitted rather than emitted empty — the payload never pays for a null.</p>
 *
 * <p>Lines are built and measured <em>before</em> being committed, so a
 * {@link ShTokenBudget} can refuse one without leaving a half-written row.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShTerse {

    private ShTerse() {
        // factory holder
    }

    /** Starts a line with the given verb (the row's type, e.g. {@code node}). */
    public static Line line(String verb) {
        return new Line(verb);
    }

    /** A single, not-yet-committed terse line. */
    public static final class Line {

        private final StringBuilder out = new StringBuilder();

        private Line(String verb) {
            out.append(verb);
        }

        /** Appends a positional value. Nulls become {@code -} to keep arity stable. */
        public Line word(Object value) {
            out.append(' ').append(value == null ? "-" : quote(String.valueOf(value)));
            return this;
        }

        /** Appends a bare marker word (e.g. {@code managed}) when {@code present}. */
        public Line flag(String name, boolean present) {
            if (present) {
                out.append(' ').append(name);
            }
            return this;
        }

        /** Appends {@code key=value}; skipped entirely when the value is null or blank. */
        public Line attr(String key, Object value) {
            if (value == null) {
                return this;
            }
            String text = String.valueOf(value);
            if (text.isBlank()) {
                return this;
            }
            out.append(' ').append(key).append('=').append(quote(text));
            return this;
        }

        /** Appends {@code key=value} for a count, skipped when zero (zero is the default). */
        public Line count(String key, long value) {
            return value == 0 ? this : attr(key, value);
        }

        /** @return the finished line, newline included. */
        public String build() {
            return out.append('\n').toString();
        }
    }

    static String quote(String value) {
        if (!value.isEmpty() && value.indexOf(' ') < 0 && value.indexOf('"') < 0
                && value.indexOf('\n') < 0) {
            return value;
        }
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + '"';
    }
}
