/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.error;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Renders every handled failure as a <em>teaching</em> RFC 9457 problem (SH80).
 *
 * <p>Two kinds of error flow through here:</p>
 * <ol>
 *   <li><b>Domain errors</b> — anything extending {@link ShTeachingException}
 *       already carries its own {@link ShProblem}; this advice only serializes it.
 *       Adding a new teaching error therefore means writing a constructor, not
 *       another handler method.</li>
 *   <li><b>Framework errors</b> — Spring's own binding/dispatch exceptions, which
 *       by default answer with a bare status and no guidance. Each one below is
 *       enriched with the {@code fix}/{@code allowed}/{@code didYouMean}/{@code example}
 *       the caller needs to succeed on the next attempt (design law P6).</li>
 * </ol>
 *
 * <p>Statuses are unchanged from the framework defaults — only the body gains
 * content — so existing clients that branch on the status code are unaffected.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@RestControllerAdvice
public class ShApiExceptionHandler {

    @ExceptionHandler(ShTeachingException.class)
    public ResponseEntity<ProblemDetail> teaching(ShTeachingException exception) {
        ShProblem problem = exception.problem();
        return respond(problem.status().value(), problem.toProblemDetail());
    }

    /**
     * A required query parameter was absent. The parameter name and type are known
     * here, so the response can state the exact call that would have worked.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> missingParameter(MissingServletRequestParameterException exception) {
        String name = exception.getParameterName();
        ShProblem problem = ShProblem
                .badRequest("missing-parameter", "Missing required parameter '" + name + "'")
                .withDetail("This endpoint requires the '" + name + "' query parameter ("
                        + exception.getParameterType() + ").")
                .withFix("Add '" + name + "=<value>' to the query string and retry.")
                .withExample("?" + name + "=<value>");
        return respond(HttpStatus.BAD_REQUEST.value(), problem.toProblemDetail());
    }

    /**
     * A parameter could not be converted to the handler's type. When the target is
     * an enum the accepted constants are a closed set, so they are returned as
     * {@code allowed} together with the nearest match — the single most common
     * "which values does this take?" round trip.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> typeMismatch(MethodArgumentTypeMismatchException exception) {
        String name = exception.getName();
        Object value = exception.getValue();
        String sent = value == null ? null : String.valueOf(value);
        Class<?> required = exception.getRequiredType();
        ShProblem problem = ShProblem
                .badRequest("invalid-parameter", "Invalid value for parameter '" + name + "'")
                .withDetail("'" + sent + "' cannot be read as "
                        + (required != null ? required.getSimpleName() : "the expected type") + ".");
        List<String> allowed = enumConstants(required);
        if (!allowed.isEmpty()) {
            problem = problem.withAllowed(allowed)
                    .withFix("Use one of the allowed values for '" + name + "'.")
                    .withDidYouMeanFrom(sent)
                    .withExample("?" + name + "=" + allowed.get(0));
        } else {
            problem = problem.withFix("Send '" + name + "' as "
                    + (required != null ? required.getSimpleName() : "the expected type") + ".");
        }
        return respond(HttpStatus.BAD_REQUEST.value(), problem.toProblemDetail());
    }

    /**
     * The database refused the write (SH544). Answered as a 400 or a 409 rather than the 500 it
     * used to be — see {@link ShDataIntegrityProblem} for which, and why a 500 is the wrong
     * answer to a request that cannot succeed by being repeated.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> dataIntegrity(DataIntegrityViolationException exception) {
        ShProblem problem = ShDataIntegrityProblem.of(exception, null);
        return respond(problem.status().value(), problem.toProblemDetail());
    }

    /** The body could not be parsed at all — almost always malformed JSON. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> unreadableBody(HttpMessageNotReadableException exception) {
        ShProblem problem = ShProblem
                .badRequest("unreadable-body", "Request body could not be read")
                .withDetail(rootMessage(exception))
                .withFix("Send a well-formed JSON body with Content-Type: application/json.");
        return respond(HttpStatus.BAD_REQUEST.value(), problem.toProblemDetail());
    }

    /**
     * Right path, wrong verb. The supported methods are known, so they are the
     * {@code allowed} set — this is what stops a caller from probing verbs.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> methodNotSupported(HttpRequestMethodNotSupportedException exception) {
        Set<String> supported = new LinkedHashSet<>();
        if (exception.getSupportedMethods() != null) {
            supported.addAll(Arrays.asList(exception.getSupportedMethods()));
        }
        ShProblem problem = ShProblem
                .of("method-not-allowed", HttpStatus.METHOD_NOT_ALLOWED,
                        "Method " + exception.getMethod() + " is not supported here")
                .withAllowed(supported)
                .withFix(supported.isEmpty()
                        ? "Check the path — no method is mapped to it."
                        : "Retry with one of: " + String.join(", ", supported) + ".");
        return respond(HttpStatus.METHOD_NOT_ALLOWED.value(), problem.toProblemDetail());
    }

    private static ResponseEntity<ProblemDetail> respond(int status, ProblemDetail body) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    private static List<String> enumConstants(Class<?> type) {
        if (type == null || !type.isEnum()) {
            return List.of();
        }
        return Arrays.stream(type.getEnumConstants()).map(String::valueOf).toList();
    }

    /**
     * The most specific message available, without leaking a stack trace: Jackson
     * nests the useful "unexpected character at line N" detail in the cause.
     */
    private static String rootMessage(Throwable throwable) {
        Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return "The body is not valid for this endpoint.";
        }
        int nested = message.indexOf(" at [");
        return nested > 0 ? message.substring(0, nested) : message;
    }
}
