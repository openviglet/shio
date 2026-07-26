/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.verify;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;

/**
 * The check groups {@code /api/v2/agent/verify} can run ({@code checks=}).
 *
 * <p>Grouped rather than per-rule for the same reason the context pack projects by
 * section: the useful request is "skip the ones that need the delivery layer", not
 * "run rules 3, 7 and 11". Every group defaults to on — a verification that quietly
 * skipped a class of defect would be worse than no verification at all, so opting out
 * is explicit.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public enum ShAgentCheck {

    /** SH99 — the content is internally consistent: fields, references, assets, furls. */
    CONTENT,

    /** SH100 — the content is reachable: every published furl resolves, links land. */
    ROUTES,

    /**
     * SH103 — recent asynchronous failures (webhooks, transforms, scheduled publishes,
     * 5xx). Included by default because the failures it reports are invisible
     * everywhere else, and on a healthy instance it reports nothing and costs nothing.
     */
    DIAGNOSTICS;

    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Lower-case names in declaration order — the {@code allowed} set of the 400. */
    public static List<String> names() {
        return Arrays.stream(values()).map(ShAgentCheck::wireName).toList();
    }

    /**
     * Parses a comma-separated {@code checks=} value.
     *
     * @param raw blank or {@code all} means every group
     * @throws ShTeachingException 400 naming the groups and the nearest match
     */
    public static Set<ShAgentCheck> parse(String raw) {
        if (raw == null || raw.isBlank() || "all".equalsIgnoreCase(raw.trim())) {
            return EnumSet.allOf(ShAgentCheck.class);
        }
        EnumSet<ShAgentCheck> checks = EnumSet.noneOf(ShAgentCheck.class);
        for (String token : raw.split(",")) {
            String name = token.trim();
            if (!name.isEmpty()) {
                checks.add(resolve(name));
            }
        }
        return checks.isEmpty() ? EnumSet.allOf(ShAgentCheck.class) : checks;
    }

    private static ShAgentCheck resolve(String name) {
        for (ShAgentCheck check : values()) {
            if (check.wireName().equalsIgnoreCase(name)) {
                return check;
            }
        }
        throw new UnknownCheckException(name, names());
    }

    /** A 400 that teaches the check vocabulary (SH80). */
    public static class UnknownCheckException extends ShTeachingException {

        public UnknownCheckException(String sent, Collection<String> allowed) {
            super(ShProblem.badRequest("unknown-check", "Unknown check '" + sent + "'")
                    .withDetail("'" + sent + "' is not a check group of the verify report.")
                    .withAllowed(allowed)
                    .withDidYouMeanFrom(sent)
                    .withFix("Name check groups as a comma-separated list, or omit `checks` "
                            + "to run all of them.")
                    .withExample("?checks=content,routes"));
        }
    }
}
