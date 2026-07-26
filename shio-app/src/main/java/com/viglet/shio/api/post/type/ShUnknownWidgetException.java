/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.type;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;

/**
 * Raised when a pushed post-type definition (SH16) references a widget name that
 * is not in the installed widget catalog ({@code ShWidget}).
 *
 * <p>The catalog is a closed set, which makes this the canonical teaching error
 * (SH80): the response carries the full {@code allowed} list, the nearest match as
 * {@code didYouMean}, and a minimal correct field as {@code example} — so a caller
 * that guessed {@code "RichText"} learns {@code "HTML Editor"} without a second
 * request. Prefer {@link #ShUnknownWidgetException(String, Collection)}; the
 * single-argument form exists for call sites with no catalog at hand and degrades
 * to a problem without the allow-list.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ShUnknownWidgetException extends ShTeachingException {

    public ShUnknownWidgetException(String widget) {
        this(widget, List.of());
    }

    /**
     * @param widget  the rejected widget name
     * @param allowed the installed widget catalog, in a deterministic order (the
     *                caller sorts, so two identical failures answer identically)
     */
    public ShUnknownWidgetException(String widget, Collection<String> allowed) {
        super(problem(widget, allowed));
    }

    private static ShProblem problem(String widget, Collection<String> allowed) {
        ShProblem problem = ShProblem
                .badRequest("unknown-widget", "Unknown widget '" + widget + "'")
                .withDetail("'" + widget + "' is not in the installed widget catalog, so no field "
                        + "can be created for it.")
                .withAllowed(allowed)
                .withFix(allowed == null || allowed.isEmpty()
                        ? "Use a widget name from the installed catalog (GET /api/v2/widget)."
                        : "Name one of the catalog widgets in the field's `widget` property.")
                .withExample(exampleField());
        // Vocabulary first, spelling second: a caller writing "RichText" or
        // "reference" is speaking another platform's dialect, which edit distance
        // cannot bridge (see ShWidgetAliases). Fall back to nearest-string only
        // when the name isn't a known foreign one.
        return ShWidgetAliases.resolve(widget, allowed)
                .map(problem::withDidYouMean)
                .orElseGet(() -> problem.withDidYouMeanFrom(widget));
    }

    /** A minimal correct field definition, ordered so the body is byte-stable. */
    private static Map<String, Object> exampleField() {
        Map<String, Object> example = new LinkedHashMap<>();
        example.put("name", "body");
        example.put("label", "Body");
        example.put("widget", "HTML Editor");
        return example;
    }
}
