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
 * <p><b>It carries the CDA's paging since SH165</b> — {@code page}, {@code size},
 * {@code totalPosts}, the same three components in the same order. Until then it returned a
 * folder's children whole, which was documented here as a real limitation rather than papered
 * over with three constants: a folder with ten thousand posts was one query, one response and
 * one render. The envelope is now the CDA's exactly, which is what {@code
 * ShConsoleShapeOverlapTest} asserts.</p>
 *
 * <p><b>Only the posts are paged.</b> Child folders come back whole on every page, which is
 * the CDA's behaviour too: a folder's subfolders are structure rather than content — the
 * console's move/copy picker walks them and the browser sorts them locally — and a page of
 * folders would mean the picker paging as well, for a set that is bounded by how people
 * organise a site rather than by how much they publish. {@code totalPosts} is named for what
 * it counts for the same reason.</p>
 *
 * @param folderId   the folder these children belong to; the site's id when listing a site
 * @param folders    child folders, in manual (position) order — the whole set, every page
 * @param posts      one page of the folder's posts, one row per logical id, DRAFT preferred
 * @param page       the zero-based page index this response answers
 * @param size       the page size it was asked for
 * @param totalPosts how many posts the folder holds in total (after any {@code q} filter)
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@JsonInclude(Include.NON_NULL)
public record ShConsoleListing(String folderId, List<ShConsoleFolderRow> folders,
                               List<ShConsolePostRow> posts, int page, int size,
                               long totalPosts) {
    public ShConsoleListing {
        folders = folders == null ? List.of() : List.copyOf(folders);
        posts = posts == null ? List.of() : List.copyOf(posts);
    }
}
