/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.shio.api.agent.ShAgentReadService;
import com.viglet.shio.api.agent.dto.ShAgentDoc;
import com.viglet.shio.api.agent.mcp.tools.ShReadTool;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * SH1073 — an MCP client can obtain the value {@code shio_write}'s {@code expect} takes.
 *
 * <p>SH721 made a write conditional on a digest, and the digest came from
 * {@code read?include=digest} — REST only. {@code shio_read} had no way to ask, and the terse line
 * both surfaces render dropped the field even when the JSON carried it. So declaring {@code expect}
 * on {@code shio_write} alone would have advertised a guard whose only input no MCP client can
 * get; this is the half that makes the declaration true.</p>
 *
 * <p>Asserted at the tool, the boundary the value has to cross twice: into the service as a flag,
 * and back out as a {@code digest=} attribute on the line a model actually reads. What the digest
 * covers is {@code ShAgentBatchTest}'s, where SH721 put it.</p>
 */
class ShReadToolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ShAgentDoc doc(String digest) {
        return new ShAgentDoc("post:Blog/hello", "Article", "DRAFT", "/", null,
                objectMapper.createObjectNode(), null, null, null, null, null, null, null, digest);
    }

    private String call(ShAgentReadService service, boolean askForDigest) {
        ObjectNode arguments = objectMapper.createObjectNode();
        arguments.putArray("address").add("post:Blog/hello");
        if (askForDigest) {
            arguments.put("digest", true);
        }
        return new ShReadTool(service).call(arguments);
    }

    @Test
    void sh1073AReadThatAsksForTheDigestGetsTheValueExpectTakes() {
        ShAgentReadService service = mock(ShAgentReadService.class);
        when(service.read(any(), any(), anyBoolean(), anyBoolean(), any(), eq(true), anyBoolean(),
                anyBoolean()))
                .thenReturn(List.of(doc("a1b2c3d4e5f60718")));

        String terse = call(service, true);

        verify(service).read(any(), any(), eq(false), eq(false), any(), eq(true), eq(false),
                eq(false));
        assertThat(terse)
                .as("SH1073: the terse line is what a model reads. A digest only the JSON carries "
                        + "is a value the MCP client asked for and was not given")
                .contains("digest=a1b2c3d4e5f60718");
    }

    @Test
    void sh1073AReadThatDoesNotAskIsTheLineItWasBefore() {
        // The control, and the reason the flag is opt-in: sixteen hex characters on every row of
        // every read is a cost every caller pays and the one about to write actually uses.
        ShAgentReadService service = mock(ShAgentReadService.class);
        when(service.read(any(), any(), anyBoolean(), anyBoolean(), any(), eq(false), anyBoolean(),
                anyBoolean()))
                .thenReturn(List.of(doc(null)));

        String terse = call(service, false);

        verify(service).read(any(), any(), eq(false), eq(false), any(), eq(false), eq(false),
                eq(false));
        assertThat(terse).doesNotContain("digest");
    }
}
