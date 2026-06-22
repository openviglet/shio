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

/**
 * Raised when a console write ({@code PUT /api/v2/post/type/{id}}) targets a
 * post-type that is "managed by code" (SH66) — owned by the {@code shio} CLI
 * {@code push}. The React editor already renders such types read-only with the
 * save action disabled; this is the server-side backstop that keeps the GUI from
 * silently overwriting a code-owned model (a last-writer-wins race). The fix is
 * to edit the DSL/JSON in the repo and {@code push}, not the console.
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class ShManagedPostTypeException extends RuntimeException {

    public ShManagedPostTypeException(String name) {
        super("Post-type '" + name + "' is managed by code and is read-only in the console. "
                + "Edit it in the repository and run `shio push`.");
    }
}
