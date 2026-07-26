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
 * Raised when a {@code shio} CLI {@code push}/{@code delete} (SH16) targets a
 * system post-type. System types are part of the platform contract and are
 * read-only over the admin API regardless of the {@code system} flag sent.
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class ShSystemPostTypeException extends ShTeachingException {

    public ShSystemPostTypeException(String name) {
        super(ShProblem.conflict("system-post-type", "Post-type '" + name + "' is a system type")
                .withDetail("System post-types are part of the platform contract and cannot be "
                        + "modified or deleted over the API, whatever `system` flag is sent.")
                .withFix("Create your own post-type instead of changing '" + name
                        + "', or extend it with a new type that has the fields you need."));
    }
}
