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
        StringBuilder out = new StringBuilder(header(changes));
        for (ShAgentChanges.Change change : changes.changes()) {
            out.append(event(change));
        }
        for (ShAgentChanges.Incident incident : incidents(changes)) {
            out.append(incident(incident));
        }
        if (changes.notes() != null) {
            for (String note : changes.notes()) {
                out.append(note(note));
            }
        }
        return out.toString();
    }

    /**
     * One {@code event} line.
     *
     * <p>Public because SH723's budget charges a row against the ceiling <em>before</em>
     * committing it, and what is charged has to be exactly what will be emitted — the
     * discipline {@code ShAgentContextService.offer} already follows with
     * {@code ShAgentContextTerse.node}. A second rendering that merely agreed today is the
     * drifting-constant defect SH673 was filed for.</p>
     */
    public static String event(ShAgentChanges.Change change) {
        return ShTerse.line("event")
                .word(change.action())
                .word(change.address())
                // SH196 — present only on a folder row, the same rule the JSON uses. On a row
                // that still has an address it repeats the `folder:` prefix; on a DELETE, whose
                // object is normally gone, it is the only thing that says what was deleted.
                .attr("subject", change.subject())
                .attr("at", change.at())
                .attr("by", change.by())
                .attr("site", change.site())
                .attr("id", change.id())
                .attr("what", change.what())
                .build();
    }

    /**
     * One {@code incident} line, and its {@code fix} when there is one.
     *
     * <p>SH144 — a distinct verb, not another {@code event}: a caller scanning for content
     * transitions must not have to inspect an attribute to find out that a line is a failure.
     * Same reason the JSON keeps them in their own array.</p>
     */
    public static String incident(ShAgentChanges.Incident incident) {
        StringBuilder out = new StringBuilder(ShTerse.line("incident")
                .word(incident.kind())
                .word(incident.subject())
                .attr("at", incident.at())
                .attr("count", incident.count())
                .attr("msg", incident.message())
                .build());
        if (incident.fix() != null && !incident.fix().isBlank()) {
            out.append("fix ").append(incident.fix()).append('\n');
        }
        // SH969 — the op and the address, in the order a batch entry takes them.
        if (incident.retry() != null) {
            out.append("retry ").append(incident.retry().op()).append(' ')
                    .append(incident.retry().address()).append('\n');
        }
        return out.toString();
    }

    /** One {@code note} line. */
    public static String note(String note) {
        return "note " + note + "\n";
    }

    /**
     * The header line, which is where the cursor lives and nowhere else.
     *
     * <p>SH723 put the feed's token ceiling here rather than in the manifest's {@code limits}:
     * a terse caller has no {@code X-Shio-Est-Tokens} header and no JSON {@code budget} object,
     * so this is the only place it can learn the bound <em>before</em> being cut by it — and it
     * is paid once per feed read instead of once per session, on a line that had the room. The
     * <em>ceiling</em> and not the estimate, deliberately: the estimate includes this line, so a
     * header that quoted it would have to be measured before it existed.</p>
     */
    public static String header(ShAgentChanges changes) {
        return ShTerse.line("changes")
                .word(changes.found())
                .flag("window", changes.window())
                .flag("more", changes.more())
                .attr("max-tokens", ceiling(changes))
                .attr("cursor", changes.cursor())
                .build();
    }

    /** The ceiling in force, or {@code null} when the caller asked for none (SH723). */
    private static Integer ceiling(ShAgentChanges changes) {
        return changes.budget() == null || changes.budget().limit() <= 0
                ? null
                : changes.budget().limit();
    }

    private static java.util.List<ShAgentChanges.Incident> incidents(ShAgentChanges changes) {
        return changes.incidents() == null ? java.util.List.of() : changes.incidents();
    }
}
