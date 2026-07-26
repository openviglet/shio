/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * What has changed, resumably (SH88).
 *
 * <h2>Why a cursor and not a bigger window</h2>
 *
 * <p>SH83 shipped {@code shio_changes} as a <em>window</em> — the last N transitions —
 * and said so, because a window answers "did anything happen?" but not "what happened
 * since I last looked". The second question is the one that makes session N+1 cheaper
 * than session N (§XIII) and the one {@code shio pull --content} has to answer before it
 * can bring a human's console edits back into the file tree. A window cannot answer it:
 * the caller would have to guess a size, and guessing low silently loses edits.</p>
 *
 * <h2>The two modes, and why they share an order</h2>
 *
 * <p>Without {@code since} this is still a window — the most recent {@code limit}
 * entries — and {@code window} says so. With {@code since} it is a feed: everything
 * after that position, oldest first, with {@code more} set when the page ended early.
 * <b>Both are ordered oldest → newest</b> so the cursor is always the last row's
 * position and a caller never has to know which mode it got to advance correctly.</p>
 *
 * <h2>Why the cursor is a pair and not a timestamp</h2>
 *
 * <p>Audit rows share timestamps — a batch of twelve writes commits inside one second,
 * and on a MariaDB with second-precision columns inside one *value*. A timestamp cursor
 * therefore either re-delivers a whole second's worth of entries or drops the ones that
 * tie, and the second failure is invisible. The cursor is {@code <instant>|<id>} and the
 * feed's ordering is {@code (date, id)}, so every entry has exactly one position.</p>
 *
 * @param found   entries in this response
 * @param window  true when no {@code since} was given, i.e. this is a starting position
 *                rather than a delta
 * @param since   the cursor this response continued from, echoed for traceability
 * @param cursor  the position to pass as {@code since} next time — always present, even
 *                when nothing changed, so a caller can hold a position on a quiet instance
 * @param more    entries remain after {@code cursor} right now; call again immediately
 * @param changes the entries themselves, oldest first
 * @param notes   what this view holds back and why — the lag rule, the scope, and
 *                whether any row's object is gone. Stated rather than implied, for the
 *                same reason SH103's window states its bound
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@JsonInclude(Include.NON_NULL)
public record ShAgentChanges(int found, boolean window, String since, String cursor,
                             boolean more, List<Change> changes, List<String> notes) {

    /**
     * One content transition.
     *
     * @param action  {@code CREATE} / {@code UPDATE} / {@code PUBLISH} / {@code UNPUBLISH}
     *                / {@code DELETE} / {@code RESTORE}
     * @param address the SH79 {@code post:} address, so the next call can be a read —
     *                absent when the object no longer exists, which is the normal state
     *                of a {@code DELETE} row and the reason {@code what} carries the title
     * @param at      ISO-8601 instant of the transition
     * @param by      who did it. SH75 stamps the agent session into this string, so a
     *                caller can tell its own writes from a human's
     * @param what    the human-readable description recorded at the time
     * @param site    the site's name, for a feed that spans more than one
     * @param id      the object id — omitted unless the caller asked for ids (SH121)
     */
    @JsonInclude(Include.NON_NULL)
    public record Change(String action, String address, String at, String by, String what,
                         String site, String id) {
    }
}
