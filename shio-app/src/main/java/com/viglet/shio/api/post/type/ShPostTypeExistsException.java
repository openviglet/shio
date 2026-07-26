/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.type;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;

/**
 * Raised when {@code POST /api/v2/post-type} names a post-type that already
 * exists (SH117). The surface has two write verbs on purpose: {@code PUT} is an
 * idempotent upsert — what {@code shio push} needs, since converging a model it
 * already declared must be a no-op — while {@code POST} creates and refuses to
 * clobber, which is what the console's "new post-type" form needs, since a name
 * collision there is a mistake to report rather than content to replace.
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class ShPostTypeExistsException extends ShTeachingException {
    public ShPostTypeExistsException(String name) {
        super(ShProblem.conflict("post-type-exists", "Post-type '" + name + "' already exists")
                .withDetail("POST creates a post-type and refuses to overwrite one, so that a name "
                        + "collision is reported instead of replacing an existing content model.")
                .withFix("Pick a different API name, or PUT /api/v2/post-type/" + name
                        + " to replace the existing definition on purpose."));
    }
}
