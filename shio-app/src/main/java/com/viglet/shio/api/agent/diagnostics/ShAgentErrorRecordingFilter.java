/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.diagnostics;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Records server-side API failures into the SH103 incident log.
 *
 * <h2>Why a filter and not an {@code @ExceptionHandler}</h2>
 *
 * <p>{@code ShApiExceptionHandler} owns the SH80 teaching problems, and every case it
 * handles is a 4xx the caller already read — recording those would fill the diagnostics
 * window with the agent's own typos. What is missing is the other kind: an unhandled
 * exception, or any 5xx rendered by the framework's own error path. Adding a
 * catch-all {@code @ExceptionHandler(Exception.class)} to reach those would change what
 * the body of an unhandled failure looks like for every existing client, which is a
 * bigger change than this task should make. Observing the response instead is
 * behaviour-preserving by construction: the filter reads the status, records, and
 * returns.</p>
 *
 * <p>Registered <em>outside</em> the security chain (see {@code ShAgentWebConfig}) for
 * the same reason SH124's cost header is: a 401 or a 429 is not an instance fault, and
 * an incident log that reported them would make an unauthenticated scanner look like a
 * broken server.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public class ShAgentErrorRecordingFilter extends OncePerRequestFilter {

    private static final String FIX =
            "This call failed server-side, so its response is gone. Retry it; if it fails "
                    + "again the instance log has the stack trace (the body never will).";

    private final ShAgentIncidentLog incidentLog;

    public ShAgentErrorRecordingFilter(ShAgentIncidentLog incidentLog) {
        this.incidentLog = incidentLog;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException e) {
            // Recorded on the way past, then rethrown untouched: this filter observes,
            // it never decides what the caller sees.
            record(request, rootMessage(e));
            throw e;
        }
        if (response.getStatus() >= 500) {
            record(request, "HTTP " + response.getStatus());
        }
    }

    private void record(HttpServletRequest request, String message) {
        // The path, not the query string: a query holds caller-supplied values, and
        // folding repeats by subject is what keeps one broken endpoint from evicting
        // everything else from the window.
        incidentLog.record(ShAgentIncidentLog.Kind.API_ERROR,
                request.getMethod() + " " + request.getRequestURI(), message, FIX);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return cause.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }
}
