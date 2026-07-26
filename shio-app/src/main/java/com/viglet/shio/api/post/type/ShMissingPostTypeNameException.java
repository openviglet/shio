/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.type;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;

/**
 * Raised when {@code POST /api/v2/post-type} arrives without an API name (SH117).
 * The name is the post-type's whole identity on this surface, so there is nothing
 * to create without it — and a caller that omitted it is one request away from
 * succeeding, which is what makes this worth a teaching body (SH80) rather than a
 * bare 400.
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ShMissingPostTypeNameException extends ShTeachingException {
    public ShMissingPostTypeNameException() {
        super(ShProblem.badRequest("missing-post-type-name", "A post-type needs an API `name`")
                .withDetail("A post-type is addressed by its API name on this surface, so `name` is "
                        + "what identifies the type being created.")
                .withFix("Send `name` in the body — a stable, code-friendly identifier such as "
                        + "\"Article\". `title` is the human label and may differ.")
                .withExample(example()));
    }

    /** A minimal correct body, ordered so the response is byte-stable (P5). */
    private static Map<String, Object> example() {
        Map<String, Object> example = new LinkedHashMap<>();
        example.put("name", "Article");
        example.put("title", "Article");
        example.put("fields", java.util.List.of());
        return example;
    }
}
