/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.review.dto;

import java.util.List;

/**
 * What an approve or a revert actually did (SH109).
 *
 * <p>{@code skipped} carries a reason per post rather than a count, because every reason here
 * is one a human has to act on differently: a post with no draft needs nothing, a post the
 * reviewer cannot write needs someone else, and an update that was never published has to be
 * undone as a creation instead. A number would send them back to guess which.</p>
 *
 * @param session the run this acted on
 * @param action  {@code approve} or {@code revert}
 * @param changed how many posts it acted on
 * @param posts   the post ids it acted on
 * @param skipped the ones it did not, and why
 */
public record ShReviewOutcome(String session, String action, int changed, List<String> posts,
                              List<Skipped> skipped) {

    /** One post that was left alone, and the reason. */
    public record Skipped(String postId, String reason) {
    }
}
