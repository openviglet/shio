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
 * Raised when {@code DELETE /api/v2/post-type/{name}} names a post-type that content still
 * points at (SH236).
 *
 * <p>It used to be a {@code 500}: the delete guarded the {@code system} flag and nothing else,
 * and {@code sh_post_unified.post_type_id → ShPostType} is {@code onDelete NO ACTION}, so the
 * database refused with a constraint violation the caller reads as a server fault. The most
 * consequential thing a caller can ask of a post-type answered with a stack trace, on a surface
 * where every other refusal is a teaching 4xx.</p>
 *
 * <p><b>The count is what makes it actionable</b>, because it separates the two things the
 * caller might have meant: a <em>rename</em> (SH163, which keeps the content) or a genuine
 * retirement, where the number says how much work that is. Trashed posts are counted too and
 * named separately — they still hold the reference, so they still block the delete, and a caller
 * told "42 posts" who has already deleted all 42 needs to know the trash is why.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class ShPostTypeInUseException extends ShTeachingException {

    /**
     * @param name    the post-type that cannot be deleted
     * @param live    rows outside the trash
     * @param trashed rows in the SH55 trash, which hold the reference just the same
     */
    public ShPostTypeInUseException(String name, long live, long trashed) {
        super(problem(name, live, trashed));
    }

    private static ShProblem problem(String name, long live, long trashed) {
        return ShProblem.conflict("post-type-in-use",
                        "Post-type '" + name + "' is still used by content")
                .withDetail(describe(live, trashed) + " point at '" + name + "', and a post keeps "
                        + "its post-type reference, so deleting the type would leave that content "
                        + "with no content model.")
                .withFix(live + trashed == trashed && trashed > 0
                        ? "Purge the trashed posts (DELETE /api/v2/folder/{id}/purge, or the "
                                + "console's trash page), then delete the post-type."
                        : "Rename it instead if the model is what changed (POST /api/v2/post-type/"
                                + name + "/rename, which carries the posts), or delete the content "
                                + "first — GET /api/v2/agent/find?type=" + name + " lists it.");
    }

    /** Trashed rows are named separately: a caller who deleted everything needs to know why. */
    private static String describe(long live, long trashed) {
        if (trashed == 0) {
            return live + " post(s)";
        }
        if (live == 0) {
            return trashed + " trashed post(s)";
        }
        return live + " post(s) (plus " + trashed + " in the trash)";
    }
}
