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
 * Raised when a hand edit targets a post-type that is "managed by code" (SH66) —
 * owned by the {@code shio} CLI {@code push}. The React editor already renders such
 * types read-only with the save action disabled; this is the server-side backstop
 * that keeps the GUI from silently overwriting a code-owned model (a
 * last-writer-wins race). The fix is to edit the DSL/JSON in the repo and
 * {@code push}, not the console.
 *
 * <p>Since SH117 both writers share {@code /api/v2/post-type}, so what makes a
 * write a hand edit is its {@code managed:false} claim rather than which path it
 * arrived on — see {@link com.viglet.shio.api.post.type.dto.ShPostTypeDef}.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class ShManagedPostTypeException extends ShTeachingException {

    public ShManagedPostTypeException(String name) {
        super(ShProblem.conflict("managed-post-type", "Post-type '" + name + "' is managed by code")
                .withDetail("It is owned by the repository (SH66) and is read-only over the console API.")
                .withFix("Edit the post-type in shio/post-types/ and run `shio push`."));
    }
}
