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

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;

/**
 * Raised when a write names a post-type that does not exist — or names none at
 * all (SH117, {@code POST /api/v2/post-unified} accepting {@code postType} by name
 * instead of {@code postTypeId}).
 *
 * <p>Like the widget catalogue, the installed post-types are a closed set, which
 * is what makes the teaching body worth its bytes (SH80): the response carries the
 * whole {@code allowed} list and the nearest match, so a caller that guessed
 * {@code "Post"} learns {@code "Page"} without a second request.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ShUnknownPostTypeException extends ShTeachingException {

    /**
     * @param name    the rejected post-type name, or {@code null} when the caller
     *                sent neither {@code postTypeId} nor {@code postType}
     * @param allowed the installed post-type names, in a deterministic order
     */
    public ShUnknownPostTypeException(String name, Collection<String> allowed) {
        super(problem(name, allowed));
    }

    private static ShProblem problem(String name, Collection<String> allowed) {
        if (name == null || name.isBlank()) {
            return ShProblem.badRequest("missing-post-type", "A post needs a post-type")
                    .withDetail("Neither `postTypeId` nor `postType` was sent, so there is no content "
                            + "model to create the post against.")
                    .withAllowed(allowed)
                    .withFix("Send `postType` with the post-type's API name (or `postTypeId` with its id).");
        }
        return ShProblem.badRequest("unknown-post-type", "Unknown post-type '" + name + "'")
                .withDetail("'" + name + "' is not an installed post-type, so no post can be created "
                        + "against it.")
                .withAllowed(allowed)
                .withFix("Name one of the installed post-types in `postType`, or create it first with "
                        + "PUT /api/v2/post-type/" + name + ".")
                .withDidYouMeanFrom(name);
    }
}
