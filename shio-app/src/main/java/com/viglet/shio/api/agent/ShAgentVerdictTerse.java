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

import com.viglet.shio.api.agent.dto.ShAgentVerdict;

/**
 * The verdicts, in the terse encoding the agent surface uses everywhere else (SH1002).
 *
 * <p>One line per verdict, and the fields in the order a reader acts on them: what was decided,
 * about which page, and why. The reason is last because it is the only field whose length is
 * unbounded — a line a parser reads left to right should have its variable part at the end.</p>
 *
 * <p>An empty answer is one line saying so rather than an empty document. "Nothing was rejected" is
 * the good news a resumed session is reading for, and a blank response is indistinguishable from a
 * read that failed.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShAgentVerdictTerse {

    private ShAgentVerdictTerse() {
    }

    public static String render(List<ShAgentVerdict> verdicts) {
        if (verdicts == null || verdicts.isEmpty()) {
            return "verdicts 0\n";
        }
        StringBuilder out = new StringBuilder("verdicts " + verdicts.size() + "\n");
        for (ShAgentVerdict verdict : verdicts) {
            out.append(ShTerse.line("verdict")
                    .word(verdict.decision())
                    .word(verdict.address())
                    .attr("by", verdict.by())
                    .attr("at", verdict.at() == null ? null : verdict.at().toString())
                    .attr("run", verdict.session())
                    .attr("why", verdict.note())
                    .build());
        }
        return out.toString();
    }
}
