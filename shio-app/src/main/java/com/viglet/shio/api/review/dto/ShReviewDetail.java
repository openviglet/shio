/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.review.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * What one agent run changed (SH109) — the list a human approves, reverts or ignores.
 *
 * @param session the SH106 agent session id
 * @param actor   the principal whose credential the run used
 * @param entries its changes, <b>oldest first</b>: the order the run made them is the order
 *                that reads as a story, and a reviewer following a build wants the beginning
 */
public record ShReviewDetail(String session, String actor, List<Entry> entries) {

    /**
     * One change.
     *
     * @param action    the SH51 action — {@code CREATE}/{@code UPDATE}/{@code PUBLISH}/
     *                  {@code UNPUBLISH}/{@code DELETE}/{@code RESTORE}
     * @param postId    the post it touched, for the console's diff and edit links
     * @param address   its SH79 address, when the post still exists. Absent means the object is
     *                  gone — which is itself the answer to "what happened to it"
     * @param title     the post's title now, or the one recorded at the time if it is gone
     * @param siteId    the site
     * @param date      when it happened
     * @param draft     the post currently has a DRAFT row
     * @param published it currently has a PUBLISHED row
     * @param trashed   it is in the SH55 trash
     * @param revertable this entry has an undo the queue can actually perform. A
     *                  {@code PUBLISH} of a post since deleted does not, and offering a button
     *                  that fails is worse than not offering one
     */
    @JsonInclude(Include.NON_NULL)
    public record Entry(
            String action,
            String postId,
            String address,
            String title,
            String siteId,
            Instant date,
            boolean draft,
            boolean published,
            boolean trashed,
            boolean revertable) {
    }
}
