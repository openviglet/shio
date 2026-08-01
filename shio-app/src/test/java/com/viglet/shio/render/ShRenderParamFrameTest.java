/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * SH259 — what a template is allowed to know about the request.
 *
 * <p>The question was never how to pass a parameter. SH183's non-goal resolution is that a layout
 * is <b>content</b> and must never have the raw {@code HttpServletRequest} in scope — that is what
 * made the 2019 engine a sandboxing liability. So the answer is the smallest thing that serves a
 * search page, pagination and a filter: query-string values as strings, and nothing else.</p>
 *
 * <p>No Spring context. {@code readerParams} reads a map, and the rules worth guarding are which
 * entries it drops — a whole application context to assert that would be the SH317 cost for
 * nothing.</p>
 *
 * @author Alexandre Oliveira
 */
class ShRenderParamFrameTest {

    @Test
    @DisplayName("SH259: the reader's query reaches the frame")
    void theQueryReachesTheFrame() {
        assertThat(ShRenderAPI.readerParams(request(Map.of("q", "café"))))
                .containsEntry("q", "café");
    }

    /**
     * The renderer's own two, and the reason is not tidiness: a template that branches on
     * {@code param.editor} renders differently inside the Universal Editor from the way it renders
     * for a reader, which is the one thing an inline editor must never make possible.
     */
    @Test
    @DisplayName("SH259: the renderer's own parameters are not the reader's")
    void theRenderersOwnParametersAreExcluded() {
        Map<String, String[]> sent = new LinkedHashMap<>();
        sent.put("state", new String[] { "PUBLISHED" });
        sent.put("editor", new String[] { "true" });
        sent.put("q", new String[] { "shoes" });

        assertThat(ShRenderAPI.readerParams(request(sent)))
                .containsOnlyKeys("q");
    }

    /**
     * A repeated parameter is a caller mistake, and the frame is strings. Taking the first is the
     * only answer that is stable across two requests that sent the same thing (P5).
     */
    @Test
    @DisplayName("SH259: a repeated parameter flattens to its first value, deterministically")
    void aRepeatedParameterTakesTheFirst() {
        Map<String, String[]> sent = Map.of("q", new String[] { "first", "second" });

        assertThat(ShRenderAPI.readerParams(request(sent))).containsEntry("q", "first");
    }

    @Test
    @DisplayName("SH259: no query is an empty frame, not an absent one")
    void noQueryIsAnEmptyFrame() {
        assertThat(ShRenderAPI.readerParams(request(Map.of()))).isEmpty();
    }

    @Test
    @DisplayName("SH259: `param` is a reserved frame, so a field cannot shadow it")
    void paramIsReserved() {
        assertThat(ShRenderModel.FRAMES)
                .as("""
                        fields go into the model first and the reserved keys overwrite them, so a \
                        post-type declaring a field called `param` must lose to the frame — the \
                        reverse would let content decide what the reader's query says""")
                .contains("param");
    }

    private static MockHttpServletRequest request(Map<String, ?> params) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        params.forEach((name, value) -> {
            if (value instanceof String[] many) {
                request.setParameter(name, many);
            } else {
                request.setParameter(name, String.valueOf(value));
            }
        });
        return request;
    }
}
