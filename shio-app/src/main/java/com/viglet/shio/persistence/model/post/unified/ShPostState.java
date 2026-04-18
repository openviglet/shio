/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.persistence.model.post.unified;

/**
 * Lifecycle state of a unified post row.
 *
 * <p>Together with the post id it forms the composite primary key of
 * {@code sh_post_unified}, allowing a single post to have one draft row and
 * one published row coexisting in the same table.</p>
 */
public enum ShPostState {
    DRAFT,
    PUBLISHED
}
