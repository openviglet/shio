/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.events;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * {@code GET /api/v2/events} — the change feed as a Server-Sent Events stream (SH967).
 *
 * <p>For a screen that is open: the console learns about an agent's write while the curator is
 * looking at the folder it landed in, instead of on the next navigation. An agent keeps reading
 * {@code GET /api/v2/agent/changes}, unchanged — it has a cursor and a turn to spend it on, and a
 * held connection is the wrong shape for that.</p>
 *
 * <p>Each event is named {@code changes}; its {@code id} is the feed cursor after that page and its
 * data is the page's entries and incidents as JSON. Reconnecting with {@code Last-Event-ID} resumes
 * after it; {@code since=} does the same for a client that cannot set the header. With neither, the
 * stream starts at the edge of the feed. See {@link ShEventStreamHub} for whose rights each page is
 * read with.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@RestController
@RequestMapping("/api/v2/events")
public class ShEventsAPI {

    private final ShEventStreamHub hub;
    private final ShEventsProperties properties;

    public ShEventsAPI(ShEventStreamHub hub, ShEventsProperties properties) {
        this.hub = hub;
        this.properties = properties;
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(
            @RequestParam(name = "since", required = false) String since,
            @RequestParam(name = "site", required = false) String site,
            @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId) {
        SseEmitter emitter = new SseEmitter(properties.getTimeoutMillis());
        // The header wins: it is what a browser's EventSource sends on its own reconnect, and it is
        // newer than whatever `since` the page was first opened with.
        String resumeFrom = lastEventId != null && !lastEventId.isBlank() ? lastEventId : since;
        String id = hub.open(new ShSseEventSink(emitter), resumeFrom, site);
        emitter.onCompletion(() -> hub.close(id));
        emitter.onTimeout(() -> {
            hub.close(id);
            emitter.complete();
        });
        emitter.onError(failure -> hub.close(id));
        return emitter;
    }
}
