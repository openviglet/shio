/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.diagnostics;

import java.util.ArrayList;
import java.util.List;

/**
 * SH190 — the few frames of Shio's own code that a 5xx came out of.
 *
 * <h2>The dead end this closes</h2>
 *
 * <p>An {@code api-error} finding reported the exception message and then advised: <i>"This call
 * failed server-side, so its response is gone. Retry it; if it fails again the instance log has the
 * stack trace (the body never will)."</i> Accurate, and a protocol that stops one step short — it
 * directs the agent to a file it has no access to, and in the session that found this the running
 * instance was not writing to the log the repository contains.</p>
 *
 * <p>The message alone identified SH188 only because that exception named its own field. That is
 * luck. A {@code NullPointerException} inside a service method produces a finding that says a call
 * failed and nothing about where, which is exactly as useful as the bare 500 it replaced.</p>
 *
 * <h2>Why frames and not a log tail</h2>
 *
 * <p>A log-tailing tool was the obvious alternative and is worse on three counts, all of which
 * SH103 already argued: it hands over every request's data to answer a question that is always
 * about one request; it is a per-deployment path an agent should not have to know; and it cannot be
 * scoped by the SH2 site allow-list or the SH54 ACL that bound everything else on this surface.
 * Bounded, attributed evidence attached to the incident is smaller and safer than a file.</p>
 *
 * <h2>What is deliberately left out</h2>
 *
 * <p><b>Only Shio's own frames</b>, and at most {@link #MAX_FRAMES} of them. A full stack trace is
 * mostly Spring, Tomcat and reflection — dozens of frames that are identical for every failure in
 * the process and tell a reader nothing about this one. Trimming to
 * {@code com.viglet.shio} is what makes the evidence a line rather than a page, and it is the same
 * judgement a human makes when they read a trace: find the first frame you own.</p>
 *
 * <p><b>No argument values and no locals</b>, only class, method and line. A frame is a *location*;
 * arguments are content, and content in a diagnostics feed is how a log leaks the thing the ACL
 * exists to protect.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShAgentIncidentFrames {

    /** Shio's own code — the only frames worth a reader's attention. */
    private static final String OWN_PACKAGE = "com.viglet.shio.";

    /**
     * Enough to see the call path, few enough to stay one line of a report.
     *
     * <p>Three is the number a human actually reads: where it threw, who called it, and who called
     * that. More frames is a stack trace by instalments, and the argument against carrying a stack
     * trace does not weaken at four.</p>
     */
    public static final int MAX_FRAMES = 3;

    private ShAgentIncidentFrames() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * The throwing location, as {@code Class.method:line} frames innermost first.
     *
     * @param throwable what was thrown; may be {@code null}
     * @return {@code null} when there is nothing of Shio's own in the trace — which is honest
     *         rather than empty: a failure entirely inside the framework (a malformed request
     *         rejected by Tomcat) has no frame of ours to name, and an empty string would read as
     *         "we looked and there was no code here"
     */
    public static String of(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        // The *deepest* cause, matching `rootMessage`: the wrapper's frames are where the exception
        // was re-thrown, and the reader wants where it started.
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        List<String> frames = new ArrayList<>();
        for (StackTraceElement element : root.getStackTrace()) {
            String className = element.getClassName();
            if (className == null || !className.startsWith(OWN_PACKAGE)) {
                continue;
            }
            // A proxy frame is Spring's, not ours: `…$$SpringCGLIB$$0.method` is the same method
            // one indirection out and naming it sends the reader to a generated class.
            if (className.contains("$$")) {
                continue;
            }
            frames.add(simpleName(className) + "." + element.getMethodName()
                    + ":" + element.getLineNumber());
            if (frames.size() >= MAX_FRAMES) {
                break;
            }
        }
        return frames.isEmpty() ? null : String.join(" ← ", frames);
    }

    private static String simpleName(String className) {
        int lastDot = className.lastIndexOf('.');
        return lastDot < 0 ? className : className.substring(lastDot + 1);
    }
}
