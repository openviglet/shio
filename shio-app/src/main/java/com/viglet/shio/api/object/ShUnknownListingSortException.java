/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.object;

import java.util.Collection;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;

/**
 * Raised when {@code GET /api/v2/object/{id}/list} is asked for an order it does not have
 * (SH165).
 *
 * <p>It refuses rather than falling back to the default, for the reason
 * {@code /agent/context}'s {@code ?format=} refuses: a listing answered in an order the caller
 * did not ask for is a {@code 200} nobody can tell from a correct one, and once the listing is
 * paged the wrong order means the wrong <em>rows</em>.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ShUnknownListingSortException extends ShTeachingException {

    public ShUnknownListingSortException(String sort, Collection<String> allowed) {
        super(ShProblem.badRequest("unknown-listing-sort", "Unknown listing sort '" + sort + "'")
                .withDetail("'" + sort + "' is not an axis this listing can order by, and the "
                        + "listing is paged — answering in another order would return other rows.")
                .withAllowed(allowed)
                .withFix("Pass one of the listed values as `sort`, or omit it for `title`.")
                .withExample("?page=0&size=50&sort=title")
                .withDidYouMeanFrom(sort));
    }
}
