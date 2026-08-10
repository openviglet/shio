/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.spring.security;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ProblemDetail;

import tools.jackson.databind.ObjectMapper;

/**
 * A {@link ProblemDetail} as the bytes the security chain writes (SH622).
 *
 * <h2>Why this is shared rather than repeated</h2>
 *
 * <p>Three refusals are now written <em>before</em> any controller runs — the 401 entry point, the
 * chain-wide 403, and SH339's CSRF handler — and each has to serialize a problem document itself,
 * because the {@code @ControllerAdvice} that does it for everything else never sees them. Three
 * copies of "which fields, in which order" is three answers to one question, and the one that would
 * drift is the field a caller reads to tell two refusals apart.</p>
 *
 * <p>The ordering is deliberate and matches the advice's: {@code type}, {@code title},
 * {@code status}, {@code detail}, then the extensions ({@code fix}, {@code allowed},
 * {@code didYouMean}). A caller that learned the shape from one refusal reads the next one the same
 * way.</p>
 *
 * <p>Its own {@code ObjectMapper}: these run in the filter chain, where injecting the application's
 * is a bean the chain builds before the context that owns it. Nothing here is
 * configuration-sensitive — it is a flat map of strings and one integer.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
final class ShProblemJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ShProblemJson() {
    }

    static String of(ProblemDetail problem) {
        return MAPPER.writeValueAsString(body(problem));
    }

    static Map<String, Object> body(ProblemDetail problem) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (problem.getType() != null) {
            body.put("type", problem.getType().toString());
        }
        body.put("title", problem.getTitle());
        body.put("status", problem.getStatus());
        if (problem.getDetail() != null) {
            body.put("detail", problem.getDetail());
        }
        if (problem.getProperties() != null) {
            body.putAll(problem.getProperties());
        }
        return body;
    }
}
