/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import com.viglet.shio.api.agent.dto.ShAgentChanges;

/**
 * Terse rendering of the change feed (SH78's encoding applied to SH88).
 *
 * <p>The {@code cursor} goes on the header line and nowhere else: it is the one value the
 * caller must carry forward, and burying it after fifty {@code event} lines would make
 * the cheapest read on the surface end with a search.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShAgentChangesTerse {

    private ShAgentChangesTerse() {
        // utility
    }

    /** A {@code changes} header, one {@code event} per entry, then the notes. */
    public static String render(ShAgentChanges changes) {
        StringBuilder out = new StringBuilder(ShTerse.line("changes")
                .word(changes.found())
                .flag("window", changes.window())
                .flag("more", changes.more())
                .attr("cursor", changes.cursor())
                .build());
        for (ShAgentChanges.Change change : changes.changes()) {
            out.append(ShTerse.line("event")
                    .word(change.action())
                    .word(change.address())
                    .attr("at", change.at())
                    .attr("by", change.by())
                    .attr("site", change.site())
                    .attr("id", change.id())
                    .attr("what", change.what())
                    .build());
        }
        // SH144 — a distinct verb, not another `event`: a caller scanning for content
        // transitions must not have to inspect an attribute to find out that a line is a
        // failure. Same reason the JSON keeps them in their own array.
        for (ShAgentChanges.Incident incident : incidents(changes)) {
            out.append(ShTerse.line("incident")
                    .word(incident.kind())
                    .word(incident.subject())
                    .attr("at", incident.at())
                    .attr("count", incident.count())
                    .attr("msg", incident.message())
                    .build());
            if (incident.fix() != null && !incident.fix().isBlank()) {
                out.append("fix ").append(incident.fix()).append('\n');
            }
        }
        if (changes.notes() != null) {
            for (String note : changes.notes()) {
                out.append("note ").append(note).append('\n');
            }
        }
        return out.toString();
    }

    private static java.util.List<ShAgentChanges.Incident> incidents(ShAgentChanges changes) {
        return changes.incidents() == null ? java.util.List.of() : changes.incidents();
    }
}
