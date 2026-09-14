/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.events;

import java.io.IOException;

/**
 * Where one stream's events go (SH967).
 *
 * <p>An interface rather than the {@code SseEmitter} directly, because an emitter only writes once
 * Spring's return-value handler has initialised it, so the hub could otherwise be asserted only
 * through a whole HTTP exchange. {@link ShSseEventSink} is the one production implementation.</p>
 */
public interface ShEventSink {

    /**
     * Sends one event.
     *
     * @param id   the position a reconnect resumes after, sent as the SSE {@code id}
     * @param name the SSE {@code event} name
     * @param data the JSON payload
     * @throws IOException when the connection is gone — the hub drops the stream
     */
    void send(String id, String name, Object data) throws IOException;

    /** Sends an SSE comment, which a client ignores and a proxy counts as traffic. */
    void heartbeat() throws IOException;

    /** Ends the stream from the server side. */
    void close();
}
