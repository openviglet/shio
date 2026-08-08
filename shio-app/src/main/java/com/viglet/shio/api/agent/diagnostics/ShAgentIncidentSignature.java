/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.diagnostics;

import java.util.regex.Pattern;

/**
 * SH205 — an incident's identity, with the varying detail stripped out of its prose.
 *
 * <h2>The failure this repairs</h2>
 *
 * <p>Folding is the load-bearing decision in SH144: it is what makes a durable table affordable,
 * because the row count tracks the number of distinct <em>problems</em> rather than the traffic.
 * The key was {@code (kind, subject, message)} and {@code message} is prose written for a human —
 * <i>"gave up after 3 attempt(s), last status 500"</i>. The same broken webhook answering 503 on
 * the next publish produced a different message, therefore a different row.</p>
 *
 * <p>So folding failed precisely where it matters most. A flapping target is the highest-volume
 * case and the one whose every occurrence differs, so it filled the window with near-duplicates
 * and pushed unrelated incidents out through the cap. Nothing showed it: several near-identical
 * rows is exactly what a report would look like if there really were several problems.</p>
 *
 * <h2>Why digits, and why that is safe here specifically</h2>
 *
 * <p>Every varying part of every message this codebase records is a number — an attempt count, a
 * status code, a Jackson {@code line: 1, column: 5} offset, a byte count, a timeout in
 * milliseconds, a port. Collapsing runs of digits covers all of them, including the ones inside
 * exception messages that Shio did not write and could not enumerate, which is the case an
 * allow-list of known-varying fields cannot reach.</p>
 *
 * <p>The standing objection to a scrubber is that over-normalizing folds two genuinely different
 * failures into one row and <em>nothing in the payload would show it</em>. That objection is what
 * the split answers rather than what it ignores: the signature is only the <b>key</b>, and
 * {@code message} still carries the most recent occurrence in full. Two failures that fold wrongly
 * are one row whose message is the latest of them, not one row that has hidden the other's text.
 * And the collision has to survive an identical {@code kind}, an identical {@code subject} and an
 * identical site — two different problems reaching all four is a much narrower accident than the
 * near-duplicate flood this replaces.</p>
 *
 * <p><b>Case is kept</b>, deliberately. {@code NullPointerException} and
 * {@code nullpointerexception} do not both occur, and lowercasing would fold an exception class
 * name into a sentence fragment that happened to match it. The normalization stops at the one
 * thing that is known to vary.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShAgentIncidentSignature {

    /**
     * A run of digits, however long.
     *
     * <p>The whole run and not each digit, so {@code status 500} and {@code status 42} normalize to
     * the same shape rather than to {@code status ###} and {@code status ##} — the length of a
     * number varies as much as its value.</p>
     */
    private static final Pattern DIGITS = Pattern.compile("\\d+");

    /** Collapsed, because a wrapped exception message can carry a newline into the middle. */
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** What a digit run becomes. Not a digit itself, so normalizing twice is a no-op. */
    private static final String PLACEHOLDER = "#";

    private ShAgentIncidentSignature() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * The fold identity of a message.
     *
     * @param message the human-readable cause, as recorded
     * @return the same text with digit runs collapsed and whitespace normalized, or {@code null}
     *         for a {@code null} message — the store never writes one, and returning an empty
     *         string would make every message-less incident fold together
     */
    public static String of(String message) {
        if (message == null) {
            return null;
        }
        String collapsed = WHITESPACE.matcher(message).replaceAll(" ").strip();
        return DIGITS.matcher(collapsed).replaceAll(PLACEHOLDER);
    }
}
