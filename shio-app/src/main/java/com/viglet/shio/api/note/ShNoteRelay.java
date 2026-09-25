/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.note;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.persistence.repository.agent.ShAgentRunRepository;

/**
 * SH1006 — the dock's chat, relayed to the connected agent over notes.
 *
 * <p>Shio hosts no model, so it cannot answer a curator's question, and it does not need to: the
 * agent already driving the instance can. A question from the dock is a note of kind
 * {@code request} on the page the curator is on. The agent reads the open ones from
 * {@code shio://requests} and answers with {@code note.add} as a {@code reply} on that thread; the
 * dock shows the reply. No new contract (P10): notes, their kinds and {@code note.add} already
 * exist, and this is a read over them plus the one question the console needs answered first,
 * whether any agent is around to answer.</p>
 */
@Service
public class ShNoteRelay {

    /** How recently a run must have started for the dock to offer the chat. */
    public static final Duration WINDOW = Duration.ofHours(8);

    /** How many open requests the resource lists, newest first. */
    static final int LIMIT = 50;

    /** What the console asks before it shows a composer. */
    public record Status(boolean agentActive, long windowHours) {
    }

    private final ShNoteService noteService;
    private final ShAgentRunRepository runRepository;

    public ShNoteRelay(ShNoteService noteService, ShAgentRunRepository runRepository) {
        this.noteService = noteService;
        this.runRepository = runRepository;
    }

    /**
     * Whether an agent run started on this tenant within {@link #WINDOW}. A composer with nobody
     * behind it would be a promise the product cannot keep, so without one the dock stays a status
     * surface.
     */
    @Transactional(readOnly = true)
    public Status status() {
        return new Status(runRepository.existsByStartedAtAfter(Instant.now().minus(WINDOW)), WINDOW.toHours());
    }

    /** The open requests as {@code shio://requests} renders them: one line each, then how to answer. */
    @Transactional(readOnly = true)
    public String render() {
        return render(noteService.openWork(null, "request", LIMIT));
    }

    static String render(List<ShNoteService.OpenNote> requests) {
        StringBuilder text = new StringBuilder("open requests: ").append(requests.size()).append('\n');
        for (ShNoteService.OpenNote each : requests) {
            text.append("- note=").append(each.id())
                    .append(" on ").append(each.address() != null ? each.address() : "id:" + each.objectId())
                    .append(" by ").append(each.author() == null ? "?" : each.author())
                    .append(": ").append(oneLine(each.body())).append('\n');
        }
        if (!requests.isEmpty()) {
            text.append("answer: shio_write note.add {address: \"id:<object>\", data: {kind: \"reply\", "
                    + "parent: \"<note>\", body: \"...\"}}, then note.resolve the request\n");
        }
        return text.toString();
    }

    private static String oneLine(String body) {
        String flat = body == null ? "" : body.replaceAll("\s+", " ").trim();
        return flat.length() > 280 ? flat.substring(0, 279) + "…" : flat;
    }
}
