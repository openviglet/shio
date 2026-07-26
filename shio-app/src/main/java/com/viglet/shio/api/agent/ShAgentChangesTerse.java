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
        if (changes.notes() != null) {
            for (String note : changes.notes()) {
                out.append("note ").append(note).append('\n');
            }
        }
        return out.toString();
    }
}
