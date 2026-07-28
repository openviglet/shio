/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SH190 — an incident carries the frame it threw from.
 *
 * <p>The dead end this closes: an {@code api-error} finding reported the exception message and then
 * advised that "the instance log has the stack trace (the body never will)" — accurate, and a
 * protocol that stops one step short, because it directs the agent to a file it has no access to.
 * The message alone identified SH188 only because that exception named its own field; a
 * {@code NullPointerException} inside a service method produces a finding that says a call failed
 * and nothing about where.</p>
 *
 * <p>No Spring context: the extraction is a pure function of a throwable, which is what makes the
 * exclusions cheap to assert — and the exclusions are the design.</p>
 *
 * @author Alexandre Oliveira
 */
class ShAgentIncidentFramesTest {

    @Test
    @DisplayName("the frame names Shio's own class, method and line")
    void itNamesTheThrowingFrame() {
        String frames = ShAgentIncidentFrames.of(thrownHere());

        assertThat(frames)
                .as("""
                        Class.method:line, which is what a human looks for first in a trace and \
                        what the agent had no way to obtain.""")
                .startsWith("ShAgentIncidentFramesTest.thrownHere:");
    }

    @Test
    @DisplayName("only Shio's own frames, and at most three")
    void itTrimsToOurOwnCode() {
        String frames = ShAgentIncidentFrames.of(nestedFiveDeep(5));

        assertThat(frames.split(" ← "))
                .as("""
                        A full trace is mostly Spring, Tomcat and reflection — dozens of frames \
                        identical for every failure in the process. Trimming to com.viglet.shio is \
                        what makes this a line rather than a page.""")
                .hasSize(ShAgentIncidentFrames.MAX_FRAMES);
        assertThat(frames)
                .doesNotContain("java.")
                .doesNotContain("org.junit");
    }

    @Test
    @DisplayName("the deepest cause is the one reported, not the wrapper")
    void itReportsTheRootCause() {
        RuntimeException wrapped = new RuntimeException("wrapped", thrownHere());

        assertThat(ShAgentIncidentFrames.of(wrapped))
                .as("""
                        the wrapper's frames are where the exception was re-thrown; the reader wants \
                        where it started — the same choice `rootMessage` already makes for the text.""")
                .startsWith("ShAgentIncidentFramesTest.thrownHere:");
    }

    @Test
    @DisplayName("a failure with nothing of ours in it is null, not an empty string")
    void nothingOfOursIsNull() {
        Throwable framework = new IllegalStateException("from somewhere else");
        framework.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("org.apache.catalina.Whatever", "service", "Whatever.java", 1),
                new StackTraceElement("java.base/java.lang.Thread", "run", "Thread.java", 2),
        });

        assertThat(ShAgentIncidentFrames.of(framework))
                .as("""
                        honest rather than empty: a malformed request rejected by Tomcat has no \
                        frame of ours to name, and "" would read as "we looked and there was no \
                        code here". Absent means the field is omitted (NON_NULL).""")
                .isNull();
    }

    @Test
    @DisplayName("a Spring proxy frame is skipped, because it is not a location anybody can read")
    void proxyFramesAreSkipped() {
        Throwable proxied = new IllegalStateException("through a proxy");
        proxied.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("com.viglet.shio.api.agent.Thing$$SpringCGLIB$$0",
                        "doIt", "Thing.java", 1),
                new StackTraceElement("com.viglet.shio.api.agent.Thing", "doIt", "Thing.java", 42),
        });

        assertThat(ShAgentIncidentFrames.of(proxied))
                .as("`…$$SpringCGLIB$$0.doIt` is the same method one indirection out; naming it "
                        + "sends the reader to a generated class")
                .isEqualTo("Thing.doIt:42");
    }

    @Test
    @DisplayName("a null throwable is null, so the caller needs no guard")
    void nullIsNull() {
        assertThat(ShAgentIncidentFrames.of(null)).isNull();
    }

    private static RuntimeException thrownHere() {
        return new IllegalStateException("boom");
    }

    private static RuntimeException nestedFiveDeep(int depth) {
        return depth <= 0 ? new IllegalStateException("deep") : nestedFiveDeep(depth - 1);
    }
}
