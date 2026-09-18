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
 * <p><b>The posts are paged and the folders are paged on request (SH1025).</b> SH165 left the
 * folders whole with an argument: subfolders are structure rather than content — the console's
 * move/copy picker walks them and the browser sorted them locally — for a set "bounded by how
 * people organise a site rather than by how much they publish". {@code ShConsoleListingBenchmarkTest}
 * took the number that argument rests on, and it holds for most folders and not for the one that
 * is a problem: the whole set is re-sent on <em>every page of posts</em>. So folder paging is
 * offered and not imposed — {@code folderPage}/{@code folderSize} absent still answers whole,
 * because the picker's read is correct and is not what the finding was about.</p>
 *
 * <p><b>The envelope is a superset of the CDA's, the way the rows already are.</b> The CDA's six
 * components come first, in order and by type, and {@code totalFolders} follows — a delivery
 * listing has no folder paging to count for, so this is a console-only field rather than a
 * divergence. {@code ShConsoleShapeOverlapTest} asserts the prefix rather than equality now, and
 * pins the console-only tail by name.</p>
 *
 * @param folderId     the folder these children belong to; the site's id when listing a site
 * @param folders      child folders, ordered by the database — the whole set unless a folder page
 *                     was asked for
 * @param posts        one page of the folder's posts, one row per logical id, DRAFT preferred
 * @param page         the zero-based page index this response answers
 * @param size         the page size it was asked for
 * @param totalPosts   how many posts the folder holds in total (after any {@code q} filter)
 * @param totalFolders how many child folders it holds, whether or not they were paged — which is
 *                     how a caller tells a complete answer from a page without counting the array
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@JsonInclude(Include.NON_NULL)
public record ShConsoleListing(String folderId, List<ShConsoleFolderRow> folders,
                               List<ShConsolePostRow> posts, int page, int size,
                               long totalPosts, long totalFolders) {
    public ShConsoleListing {
        folders = folders == null ? List.of() : List.copyOf(folders);
        posts = posts == null ? List.of() : List.copyOf(posts);
    }
}
