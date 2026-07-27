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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * A folder's children, for the console's content browser (SH120) —
 * {@code GET /api/v2/object/{id}/list}.
 *
 * <p>The envelope is {@code ShCdaListing}'s: {@code folderId}, {@code folders},
 * {@code posts}. It used to be {@code shSite}, {@code shFolders}, {@code shPosts} and a
 * {@code breadcrumb}, which is the same information under different names plus a copy of
 * what {@code /path} returns. One shape to learn (P5), and one place to get the
 * breadcrumb.</p>
 *
 * <p><b>What it deliberately does not copy is the paging.</b> {@code ShCdaListing} carries
 * {@code page}/{@code size}/{@code totalPosts}; this listing returns a folder's children
 * whole, so those fields would be three constants pretending to be a contract. The
 * unpaged-ness is a real limitation rather than a shape decision — a folder with ten
 * thousand posts is one response — and it is tracked as SH165 rather than papered over with
 * fields that always say the same thing.</p>
 *
 * @param folderId the folder these children belong to; the site's id when listing a site
 * @param folders  child folders, in manual (position) order
 * @param posts    the folder's posts, one row per logical id, DRAFT preferred
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@JsonInclude(Include.NON_NULL)
public record ShConsoleListing(String folderId, List<ShConsoleFolderRow> folders,
                               List<ShConsolePostRow> posts) {
    public ShConsoleListing {
        folders = folders == null ? List.of() : List.copyOf(folders);
        posts = posts == null ? List.of() : List.copyOf(posts);
    }
}
