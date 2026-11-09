/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.migration.unified;

import org.springframework.http.HttpStatus;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;

/**
 * A write named a container or a post type that does not exist (SH1380).
 *
 * <p>The writer used to say so with a bare {@code IllegalArgumentException}, which nothing maps, so
 * every caller that did not catch it answered 500 with Spring's default body: an upload into an
 * unknown folder, a create with an unknown post type. A client cannot tell that from the instance
 * being down, and the phone's retry spent three attempts on a request that could never succeed. The
 * answer is a 404 naming the id, and the read that lists what does exist (P6).</p>
 *
 * <p>Thrown here, at the writer, rather than mapped by a blanket advice for the exception it
 * replaced: that advice would relabel genuine server faults as the caller's.</p>
 */
public class ShWriteTargetNotFoundException extends ShTeachingException {

    private ShWriteTargetNotFoundException(ShProblem problem) {
        super(problem);
    }

    /** No folder, and no site whose root could stand in for one, has this id. */
    public static ShWriteTargetNotFoundException noSuchFolder(String id) {
        return new ShWriteTargetNotFoundException(
                ShProblem.of("no-such-folder", HttpStatus.NOT_FOUND, "No folder or site with this id")
                        .withDetail("Nothing writable is stored under " + id + ": it is neither a folder "
                                + "nor a site, whose root folder a write may land in.")
                        .withFix("List what a folder holds with GET /api/v2/object/{id}/list, or resolve "
                                + "an address with GET /api/v2/agent/resolve?address=folder:<site>/<path>.")
                        .withExample("/api/v2/agent/resolve?address=folder:<site>/<path>"));
    }

    /** No post type has this id. */
    public static ShWriteTargetNotFoundException noSuchPostType(String id) {
        return new ShWriteTargetNotFoundException(
                ShProblem.of("no-such-post-type", HttpStatus.NOT_FOUND, "No post type with this id")
                        .withDetail("Nothing in this instance's content model is stored under " + id + ".")
                        .withFix("List the post types with GET /api/v2/post-type, and send the id of one "
                                + "of them; an agent names a type by its name on POST /api/v2/agent/batch.")
                        .withExample("/api/v2/post-type"));
    }
}
