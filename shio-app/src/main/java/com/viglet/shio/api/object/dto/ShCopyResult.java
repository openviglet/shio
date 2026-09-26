/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.object.dto;

import java.util.List;

import com.viglet.shio.persistence.model.object.ShObject;

/**
 * What {@code PUT /api/v2/object/copyto/{dest}} made (SH1101).
 *
 * <p>It used to answer an array of the folders it copied, and a post copy added nothing to it, so a
 * caller that copied a post had nothing to address it by. {@code folders} is that array, unchanged;
 * {@code posts} is the copied posts in the shape a create answers with enough of to write next: the
 * id, the title and friendly URL the copy was given (SH202 may have numbered both), and its address.</p>
 *
 * @param folders the folders copied, each with its whole subtree
 * @param posts   the posts copied directly into the destination
 */
public record ShCopyResult(List<ShObject> folders, List<Post> posts) {

    /** One copied post: its id, what it is called, where it answers, and how an agent names it. */
    public record Post(String id, String title, String furl, String address) {
    }
}
