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
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

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
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;

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
    /** SH1272 — the live rows behind a page of drafts, read for the page at once. */
    private final ShPostUnifiedRepository posts;

    public ShConsoleNavigationMapper(ShCdaMapper cdaMapper, ShConsoleThumbnails thumbnails,
                                     ShPostUnifiedRepository posts) {
        this.cdaMapper = cdaMapper;
        this.thumbnails = thumbnails;
        this.posts = posts;
    }

    /** {@code publishStatus} on a listing row: what a curator needs to know about the page (SH1272). */
    public static final String LIVE = "PUBLISHED";
    public static final String CHANGED = "CHANGED";
    public static final String UNPUBLISHED = "DRAFT";

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
        Map<String, ShPostUnified> live = liveRows(posts);
        return new ShConsoleListing(folderId, folders.stream().map(this::folderRow).toList(),
                posts.stream().map(post -> postRow(post, images.get(post.getId()),
                        live.get(post.getId()))).toList(), page, size,
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

    /**
     * SH1272 — the PUBLISHED row of every draft on the page, in one query.
     *
     * <p>The listing prefers the DRAFT row, so the console shows the latest editable state, and
     * publishing never touches that row: it writes a second, PUBLISHED one beside it. So the draft's
     * own {@code published}/{@code publishStatus} columns said "draft" about every page that had ever
     * been published, whoever published it, and a curator opening a site an agent had just applied
     * saw every page as unpublished while {@code /agent/read} and {@code /sites/**} served it.</p>
     */
    private Map<String, ShPostUnified> liveRows(List<ShPostUnified> rows) {
        List<String> drafts = rows.stream().filter(post -> post.getState() == ShPostState.DRAFT)
                .map(ShPostUnified::getId).toList();
        return drafts.isEmpty() ? Map.of() : posts.findByIdInAndState(drafts, ShPostState.PUBLISHED)
                .stream().collect(Collectors.toMap(ShPostUnified::getId, Function.identity(),
                        (first, second) -> first));
    }

    /**
     * The one rule for "is this published" (SH1272): a live row exists. And it has changes waiting
     * when the draft beside it holds different data, which is the same comparison a publish makes
     * when it names what it changed.
     */
    static String statusOf(ShPostUnified post, ShPostUnified live) {
        if (post.getState() == ShPostState.PUBLISHED) {
            return LIVE;
        }
        if (live == null) {
            return UNPUBLISHED;
        }
        return Objects.equals(post.getData(), live.getData()) ? LIVE : CHANGED;
    }

    private ShConsolePostRow postRow(ShPostUnified post, String thumbnail, ShPostUnified live) {
        var shared = cdaMapper.toSummary(post);
        String status = statusOf(post, live);
        return new ShConsolePostRow(shared.id(), shared.title(), shared.summary(), shared.furl(),
                shared.postType(), shared.locale(), shared.date(), shared.modifiedDate(),
                post.getShPostType() != null ? post.getShPostType().getTitle() : null,
                !UNPUBLISHED.equals(status),
                status,
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
