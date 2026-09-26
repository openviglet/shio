/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.object;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.viglet.shio.api.cda.ShCdaMapper;
import com.viglet.shio.api.cda.dto.ShCdaFolder;
import com.viglet.shio.api.cda.dto.ShCdaPath;
import com.viglet.shio.api.object.dto.ShConsoleFolderRow;
import com.viglet.shio.api.object.dto.ShConsoleListing;
import com.viglet.shio.api.object.dto.ShConsolePostRow;
import com.viglet.shio.object.ShFolderSite;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;

/**
 * Maps the content tree onto the console's navigation shapes (SH120).
 *
 * <p><b>It composes {@link ShCdaMapper} rather than restating it.</b> {@code /path}
 * returns the CDA's own {@code ShCdaPath} — the shapes were already identical apart from
 * {@code shSite} versus {@code site}, so there was nothing to keep separate — and the
 * listing rows carry the CDA components with the console's extra ones appended. Law P10 in
 * the small: the console is a shape over the same reads, not a second reading of the same
 * data.</p>
 *
 * <p>The one thing this mapper must never do is read {@code folder.getShSite()} directly —
 * only a root folder carries it (SH128), so a nested folder would silently answer with no
 * site. {@link ShFolderSite} walks up.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShConsoleNavigationMapper {

    private final ShCdaMapper cdaMapper;
    /** SH1026 — the image each card of a page shows, read for the page at once. */
    private final ShConsoleThumbnails thumbnails;

    public ShConsoleNavigationMapper(ShCdaMapper cdaMapper, ShConsoleThumbnails thumbnails) {
        this.cdaMapper = cdaMapper;
        this.thumbnails = thumbnails;
    }

    /**
     * A folder's children: its child folders, one page of posts, and the paging the CDA
     * listing has carried since SH1 (SH165).
     *
     * @param page         the zero-based index of the page {@code posts} is
     * @param size         the page size asked for
     * @param totalPosts   how many posts the folder holds after any filter, not how many are
     *                     in this page — that is what makes a cut visible (SH138's rule)
     * @param totalFolders the same rule for the folder half (SH1025), which is what tells a
     *                     caller whether {@code folders} is a page or the whole set
     */
    public ShConsoleListing listing(String folderId, List<ShFolder> folders,
                                    List<ShPostUnified> posts, int page, int size,
                                    long totalPosts, long totalFolders) {
        Map<String, String> images = posts.isEmpty() ? Map.of() : thumbnails.of(posts);
        return new ShConsoleListing(folderId, folders.stream().map(this::folderRow).toList(),
                posts.stream().map(post -> postRow(post, images.get(post.getId()))).toList(), page, size,
                totalPosts, totalFolders);
    }

    /** The breadcrumb, in the CDA's own shape. */
    public ShCdaPath path(ShSite site, List<ShFolder> breadcrumb, ShFolder currentFolder) {
        return new ShCdaPath(
                cdaMapper.toSite(site, rootFolderId(site, breadcrumb)),
                breadcrumb.stream().map(cdaMapper::toFolder).toList(),
                cdaMapper.toFolder(currentFolder));
    }

    private ShConsoleFolderRow folderRow(ShFolder folder) {
        ShCdaFolder shared = cdaMapper.toFolder(folder);
        return new ShConsoleFolderRow(shared.id(), shared.name(), shared.root(),
                folder.getDate(), folder.getPosition());
    }

    private ShConsolePostRow postRow(ShPostUnified post, String thumbnail) {
        var shared = cdaMapper.toSummary(post);
        return new ShConsolePostRow(shared.id(), shared.title(), shared.summary(), shared.furl(),
                shared.postType(), shared.locale(), shared.date(), shared.modifiedDate(),
                post.getShPostType() != null ? post.getShPostType().getTitle() : null,
                post.isPublished() || post.getState() == ShPostState.PUBLISHED,
                post.getPublishStatus(),
                post.getState() != null ? post.getState().name() : null,
                thumbnail);
    }

    /**
     * The site's root folder id, taken from the breadcrumb's first entry when there is one.
     *
     * <p>The CDA's site DTO carries it because a front end needs an entry point to walk
     * from, and a breadcrumb's first element <em>is</em> the root folder — so this costs no
     * query. Listing a site rather than a folder leaves it null, which is the honest answer:
     * a site has several root folders and the caller is looking at all of them.</p>
     */
    private String rootFolderId(ShSite site, List<ShFolder> breadcrumb) {
        if (site == null || breadcrumb.isEmpty()) {
            return null;
        }
        ShFolder root = breadcrumb.get(0);
        return root != null ? root.getId() : null;
    }
}
