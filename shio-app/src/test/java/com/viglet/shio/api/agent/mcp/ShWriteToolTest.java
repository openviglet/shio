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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.viglet.shio.api.agent.ShAgentProofService;
import com.viglet.shio.api.agent.ShAgentBatchService;
import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.agent.dto.ShAgentChangeSet;
import com.viglet.shio.api.agent.mcp.tools.ShWriteTool;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * SH952 — {@code shio_write} passes on the arguments it takes.
 *
 * <p>The tool built every op through {@code ShAgentBatch.Op}'s six-field constructor — the form
 * whose own javadoc says it is for the ops that never schedule — so {@code when} arrived at the
 * batch service as {@code null} on the one surface that advertised it. A publish dated for next
 * Monday went live <em>now</em> and reported success. {@code expect}, {@code locale},
 * {@code translationOf} and {@code url} were lost the same way, and the REST batch deserialises all
 * eleven, which is why nothing looked wrong from the console.</p>
 *
 * <p><b>The assertion is on the op the tool hands over</b>, which is the boundary the defect
 * crossed. What the batch service then does with {@code when} and {@code expect} is asserted where
 * that lives; putting an end-to-end schedule here would test SH342's scheduler a second time and
 * still not say whether the tool passed the field — which is the only thing that was wrong.</p>
 */
class ShWriteToolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** One op through the tool, returning the batch the service was actually handed. */
    private ShAgentBatch captureBatch(ObjectNode op) {
        ShAgentBatchService batchService = mock(ShAgentBatchService.class);
        ShAgentProofService proofService = mock(ShAgentProofService.class);
        // Internally consistent rather than realistic: ShAgentChangeSet refuses to represent a
        // truncated set (SH138), so `ops` has to match the rows. Nothing here reads the return —
        // the assertion is on the batch the service was handed.
        ShAgentChangeSet done =
                new ShAgentChangeSet(false, 0, 0, List.of(), null, null, null);
        when(batchService.run(any(), anyBoolean(), any())).thenReturn(done);
        when(proofService.attach(any(), anyBoolean())).thenReturn(done);

        ObjectNode arguments = objectMapper.createObjectNode();
        ArrayNode ops = arguments.putArray("ops");
        ops.add(op);

        new ShWriteTool(batchService, proofService).call(arguments);

        ArgumentCaptor<ShAgentBatch> captor = ArgumentCaptor.forClass(ShAgentBatch.class);
        verify(batchService).run(captor.capture(), anyBoolean(), any());
        return captor.getValue();
    }

    @Test
    void sh952AScheduledPublishReachesTheBatchWithItsInstant() {
        ObjectNode op = objectMapper.createObjectNode();
        op.put("op", "post.publish");
        op.put("address", "post:Blog/hello");
        op.put("when", "2026-12-25T09:00:00Z");

        ShAgentBatch.Op sent = captureBatch(op).ops().get(0);

        assertThat(sent.when())
                .as("SH952: the schema advertises `when` for post.publish. Dropped, a publish "
                        + "dated for Christmas happens now and answers success — which is a wrong "
                        + "answer rather than a refusal, and the worse of the two")
                .isEqualTo("2026-12-25T09:00:00Z");
    }

    @Test
    void sh952AConditionalWriteReachesTheBatchWithItsExpectation() {
        ObjectNode op = objectMapper.createObjectNode();
        op.put("op", "post.upsert");
        op.put("address", "post:Blog/hello");
        op.put("expect", "a1b2c3d4e5f60718");

        ShAgentBatch.Op sent = captureBatch(op).ops().get(0);

        assertThat(sent.expect())
                .as("SH952: SH721's guard makes a write conditional on the state it was planned "
                        + "against. Dropped, every MCP write is unconditional and the lost update "
                        + "it exists to prevent happens silently")
                .isEqualTo("a1b2c3d4e5f60718");
    }

    @Test
    void sh952ATranslationReachesTheBatchWithItsLocaleAndSource() {
        ObjectNode op = objectMapper.createObjectNode();
        op.put("op", "post.upsert");
        op.put("address", "post:Blog/ola");
        op.put("locale", "pt-br");
        op.put("translationOf", "post:Blog/hello");
        op.put("url", "/ola/");

        ShAgentBatch.Op sent = captureBatch(op).ops().get(0);

        assertThat(sent.locale()).isEqualTo("pt-br");
        assertThat(sent.translationOf()).isEqualTo("post:Blog/hello");
        assertThat(sent.url())
                .as("SH952: SH467's declared spelling — a trailing slash is the difference between "
                        + "two addresses, and it has to survive the tool that carries it")
                .isEqualTo("/ola/");
    }

    @Test
    void sh952AnOpThatNamesNoneOfThemIsUnchanged() {
        // The control. All five are optional, and a create that mentions none must still arrive as
        // a create rather than as one carrying empty strings the batch would have to interpret.
        ObjectNode op = objectMapper.createObjectNode();
        op.put("op", "post.upsert");
        op.put("address", "post:Blog/hello");
        op.put("type", "Article");

        ShAgentBatch.Op sent = captureBatch(op).ops().get(0);

        assertThat(sent.op()).isEqualTo("post.upsert");
        assertThat(sent.address()).isEqualTo("post:Blog/hello");
        assertThat(sent.type()).isEqualTo("Article");
        assertThat(sent.when()).isNull();
        assertThat(sent.expect()).isNull();
        assertThat(sent.locale()).isNull();
        assertThat(sent.translationOf()).isNull();
        assertThat(sent.url()).isNull();
    }
}
