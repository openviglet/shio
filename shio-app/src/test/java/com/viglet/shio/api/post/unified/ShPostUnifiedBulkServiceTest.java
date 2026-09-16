/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.unified;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SH1021 — the curator's bulk verbs are the agent's verbs.
 *
 * <p>The claim this guards is not that publishing works — the batch service has been publishing
 * since {@code shio_publish} shipped, and it has its own suite. It is that the console's verbs
 * <b>go through that writer</b> rather than through a second loop of their own, which is the thing
 * that would make two surfaces drift about what publishing means. A behavioural test cannot show
 * that: a forked loop that happened to publish correctly would pass it.</p>
 *
 * <p>So this reads the composition, and executes what can be executed: the op mapping is a pure
 * function, and the refusal for an empty selection is one call.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
class ShPostUnifiedBulkServiceTest {

    private static final Path SOURCE = Path.of("src", "main", "java", "com", "viglet", "shio",
            "api", "post", "unified", "ShPostUnifiedBulkService.java");

    private static String source() throws IOException {
        return Files.readString(SOURCE, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("SH1021: each verb maps to the batch op of the same name, and no other")
    void theVerbsAreTheAgentsVerbs() {
        assertThat(ShPostUnifiedBulkService.Op.PUBLISH.op()).isEqualTo("post.publish");
        assertThat(ShPostUnifiedBulkService.Op.UNPUBLISH.op()).isEqualTo("post.unpublish");
        assertThat(ShPostUnifiedBulkService.Op.DELETE.op()).isEqualTo("post.delete");
        assertThat(ShPostUnifiedBulkService.Op.values())
                .as("""
                        SH1021: this is the browser's menu, not the whole vocabulary. A bulk verb \
                        for every op would be a second way to do everything, which is the fork \
                        this composition exists to avoid.""")
                .hasSize(3);
    }

    @Test
    @DisplayName("SH1021: it delegates to the agent's batch service rather than writing its own loop")
    void itComposesRatherThanForks() throws IOException {
        String source = source();

        assertThat(source)
                .as("""
                        SH1021: P10. A publish loop in a console service is what lets the console \
                        and the agent drift about what publishing means — the gates, the audit \
                        session, the scheduled instant and the per-item outcome are all things the \
                        second implementation gets subtly wrong later rather than now.""")
                .contains("shAgentBatchService.run(batch, true, actor)");
        assertThat(source)
                .as("SH1021: and it builds ops, so the two surfaces speak one vocabulary")
                .contains("new ShAgentBatch.Op(op.op(), \"id:\" + id");
        assertThat(source)
                .as("SH1021: no writer of its own — a call to the post writer here would be the "
                        + "fork wearing a delegation's clothes")
                .doesNotContain("shPostUnifiedWriteService");
    }

    @Test
    @DisplayName("SH1021: the session is bound for the audit row and always cleared")
    void theAuditSessionIsClearedOnEveryPath() throws IOException {
        String source = source();

        assertThat(source).contains("ShAgentSessionContext.set(");
        assertThat(source)
                .as("""
                        SH1021: the thread is pooled, so a session left bound stamps the NEXT \
                        request's audit rows with this curator's day — which is why the clear is in \
                        a finally and not after the call.""")
                .contains("} finally {")
                .contains("ShAgentSessionContext.clear();");
    }

    @Test
    @DisplayName("SH1021: permission is asked for the whole selection before anything is built")
    void permissionComesFirst() throws IOException {
        String source = source();
        int guard = source.indexOf("guard.requireAll(op, ids)");
        int build = source.indexOf("new ShAgentBatch.Op(");

        assertThat(guard).as("SH1021: the guard is gone").isGreaterThan(0);
        assertThat(guard)
                .as("""
                        SH1021: before, deliberately. The batch's own ops check per row and that is \
                        the check that matters; this earlier one is about what the caller sees — \
                        without it a curator with no publish right gets a change set in which every \
                        item is refused, and on a dry run a plan they could not carry out.""")
                .isLessThan(build);
    }

    @Test
    @DisplayName("SH1021: an empty selection is a refusal that says so, not an empty change set")
    void anEmptySelectionIsRefused() {
        ShPostUnifiedBulkService service = new ShPostUnifiedBulkService(null, null);

        assertThatThrownBy(() -> service.run(ShPostUnifiedBulkService.Op.PUBLISH, List.of(),
                true, null, null, "curator"))
                .as("SH1021: the button that sent it was enabled, so something upstream believes "
                        + "there is a selection — an empty change set would hide that")
                .isInstanceOf(ShPostUnifiedBulkService.NothingSelectedException.class);

        assertThatThrownBy(() -> service.run(ShPostUnifiedBulkService.Op.PUBLISH, null,
                true, null, null, "curator"))
                .isInstanceOf(ShPostUnifiedBulkService.NothingSelectedException.class);
    }
}
