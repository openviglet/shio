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
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;

/**
 * Raised when a post-type's Turing indexing declaration cannot work (SH914).
 *
 * <h2>Why this is a save-time error and not a publish-time one</h2>
 *
 * <p>A broken declaration costs nothing until a post is published, and then it costs a document
 * that is silently missing the field a facet is built on — the failure this whole task exists to
 * remove, arriving by a different road. The declaration is data about the <em>model</em>, so the
 * moment it is written is the moment it can be judged, and that is the only moment its author is
 * still holding it.</p>
 *
 * <p>Three things are refusable, and they are what remains after putting the declaration on the
 * attribute rather than in a list that names attributes by string. That choice made the fourth and
 * most likely mistake — naming a field the post-type does not have — <b>unrepresentable</b>, which
 * is worth more than any error message for it.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ShInvalidIndexDeclarationException extends ShTeachingException {

    public ShInvalidIndexDeclarationException(String title, String detail, String fix,
                                              Collection<String> allowed) {
        super(problem(title, detail, fix, allowed));
    }

    private static ShProblem problem(String title, String detail, String fix,
                                     Collection<String> allowed) {
        ShProblem problem = ShProblem
                .badRequest("invalid-index-declaration", title)
                .withDetail(detail)
                .withFix(fix)
                .withExample(exampleField());
        return allowed == null || allowed.isEmpty() ? problem : problem.withAllowed(allowed);
    }

    /** A minimal correct declaration, ordered so the body is byte-stable. */
    private static Map<String, Object> exampleField() {
        Map<String, Object> example = new LinkedHashMap<>();
        example.put("name", "tuition");
        example.put("label", "Tuition");
        example.put("widget", "Text");
        example.put("indexed", true);
        example.put("turingType", "DOUBLE");
        return example;
    }
}
