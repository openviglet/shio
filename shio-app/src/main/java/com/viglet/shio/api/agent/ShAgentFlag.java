/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import java.util.List;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;

/**
 * SH320 — a boolean query parameter that <b>bounds work</b>, and refuses what it cannot read.
 *
 * <h2>The rule this exists to make real</h2>
 *
 * <p>One request to this surface could carry four parameters with four different opinions about an
 * unrecognised value: {@code ?digest=} teaches (SH266), {@code ?format=} teaches (SH78),
 * {@code ?include=} ignores (SH262), and {@code ?verify=}/{@code ?ids=} took Spring's own boolean
 * binding, which reads {@code yes} as <b>false</b> and says nothing. The difference was invisible
 * until somebody mistyped, and then invisible again, because three of the four answered a plausible
 * response to a different question.</p>
 *
 * <p>The rule that resolves it, and the reason this class is not applied to every flag:</p>
 *
 * <blockquote><b>A parameter that selects or bounds work teaches. A parameter that only adds a
 * section does not.</b></blockquote>
 *
 * <p>By that rule {@code format} and {@code digest} are right to teach — they choose what the
 * response <em>is</em>, so a typo silently answers a different question. {@code include} is right
 * to ignore: a typo costs the caller an extra it asked for and its own code will notice. And
 * {@code ids} stays on Spring's lenient binding, because it only adds a field to rows that are
 * otherwise identical — {@code ?ids=1} works today and a caller writing it is informal rather than
 * wrong.</p>
 *
 * <p>{@code verify} is the one that was on the wrong side: it makes the server run an SH99 lint
 * pass over everything the write touched. {@code ?verify=yes} was a caller asking for proof and
 * getting silence, with a {@code 200} in front of it.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShAgentFlag {

    private ShAgentFlag() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Reads a work-bounding flag.
     *
     * <p>{@code 1}/{@code 0} are accepted beside {@code true}/{@code false}, because they are what
     * Spring already accepted here and a caller sending them means exactly one thing. Anything else
     * is refused rather than guessed.</p>
     *
     * @param value the raw parameter, or {@code null} when absent
     * @param name  the parameter's name, so the refusal can say which one it is about
     */
    public static boolean of(String value, String name) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String sent = value.trim();
        if ("true".equalsIgnoreCase(sent) || "1".equals(sent)) {
            return true;
        }
        if ("false".equalsIgnoreCase(sent) || "0".equals(sent)) {
            return false;
        }
        throw new BadFlagException(name, sent);
    }

    /** A 400 that says which parameter and what it takes (SH80). */
    public static class BadFlagException extends ShTeachingException {

        public BadFlagException(String name, String sent) {
            super(ShProblem.badRequest("bad-flag", "`" + name + "` is true or false")
                    .withDetail("'" + sent + "' is not a boolean. `" + name + "` decides whether "
                            + "the server does extra work on your behalf, so reading it as false "
                            + "would answer a different question and look like it worked.")
                    .withAllowed(List.of("true", "false"))
                    .withFix("Send `" + name + "=true` or omit it.")
                    .withExample("POST /api/v2/agent/batch?" + name + "=true"));
        }
    }
}
