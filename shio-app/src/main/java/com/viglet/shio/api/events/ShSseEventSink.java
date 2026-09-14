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

import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** An {@link ShEventSink} over a Spring {@link SseEmitter} (SH967). */
public final class ShSseEventSink implements ShEventSink {

    private final SseEmitter emitter;

    public ShSseEventSink(SseEmitter emitter) {
        this.emitter = emitter;
    }

    @Override
    public void send(String id, String name, Object data) throws IOException {
        emitter.send(SseEmitter.event().id(id).name(name).data(data, MediaType.APPLICATION_JSON));
    }

    @Override
    public void heartbeat() throws IOException {
        emitter.send(SseEmitter.event().comment("heartbeat"));
    }

    @Override
    public void close() {
        emitter.complete();
    }
}
