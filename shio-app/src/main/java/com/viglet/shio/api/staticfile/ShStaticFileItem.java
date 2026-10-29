/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.staticfile;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * A single static file (a {@code File}-post-type {@link com.viglet.shio.persistence.model.post.unified.ShPostUnified})
 * as listed by the Media Library (SH61/SH62) console page. The bytes are served
 * from {@code /api/v2/staticfile/{id}} (and {@code /file_source/{id}/{fileName}}).
 *
 * <p>{@code width}/{@code height} are the image pixel dimensions stamped at upload
 * time (SH62); {@code null} for non-images or files uploaded before SH62.
 * {@code sha256} is the bytes' digest, stamped the same way (SH398) and {@code null} for
 * anything written before it — which is why it is a <em>cache</em> in front of
 * {@code GET /{id}/digest} and never a replacement for it.
 * {@code alt}/{@code caption}/{@code tags} are editable Media Library metadata
 * (SH62), stored on the post's {@code data}.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public record ShStaticFileItem(
        String id,
        String fileName,
        String contentType,
        long size,
        String owner,
        Instant date,
        String folderId,
        String folderName,
        String siteId,
        String siteName,
        Integer width,
        Integer height,
        String alt,
        String caption,
        List<String> tags,
        String sha256,
        /*
         * How many posts reference this asset, or null when nobody asked (SH1034).
         *
         * Nullable rather than 0, and the distinction is the one that matters: zero means NOTHING
         * references this asset — the answer a prune acts on — so a field that said zero because
         * the caller did not ask would make an unasked listing look like a library of orphans.
         * Only the rows a listing actually returns carry it, stamped in one indexed query for the
         * page: counting every row of the library to draw ten of them is the cost SH1033 measured
         * and SH1034 exists to remove.
         */
        Long usageCount,
        /*
         * How many rendered pages request this asset, or null when nobody asked (SH1280).
         *
         * The count `usageCount` cannot see: a theme's @font-face or a layout's CSS background is
         * in no field. Null for the same reason `usageCount` is, and stamped only on `loads=true`,
         * because it costs one render per page of the site.
         */
        @JsonInclude(Include.NON_NULL) Integer loadedBy) {

    /** The pre-SH1280 shape: a reference count, and no answer about what pages load. */
    public ShStaticFileItem(String id, String fileName, String contentType, long size, String owner,
                            Instant date, String folderId, String folderName, String siteId,
                            String siteName, Integer width, Integer height, String alt,
                            String caption, List<String> tags, String sha256, Long usageCount) {
        this(id, fileName, contentType, size, owner, date, folderId, folderName, siteId, siteName,
                width, height, alt, caption, tags, sha256, usageCount, null);
    }

    /** The pre-SH398 shape, for a caller that has no digest to report. */
    public ShStaticFileItem(String id, String fileName, String contentType, long size, String owner,
                            Instant date, String folderId, String folderName, String siteId,
                            String siteName, Integer width, Integer height, String alt,
                            String caption, List<String> tags) {
        this(id, fileName, contentType, size, owner, date, folderId, folderName, siteId, siteName,
                width, height, alt, caption, tags, null, null);
    }

    /** The pre-SH1034 shape: a digest, and no answer about usage. */
    public ShStaticFileItem(String id, String fileName, String contentType, long size, String owner,
                            Instant date, String folderId, String folderName, String siteId,
                            String siteName, Integer width, Integer height, String alt,
                            String caption, List<String> tags, String sha256) {
        this(id, fileName, contentType, size, owner, date, folderId, folderName, siteId, siteName,
                width, height, alt, caption, tags, sha256, null);
    }

    /** The same row, with the usage count a listing stamped on it. */
    public ShStaticFileItem withUsageCount(Long count) {
        return new ShStaticFileItem(id, fileName, contentType, size, owner, date, folderId,
                folderName, siteId, siteName, width, height, alt, caption, tags, sha256, count, loadedBy);
    }

    /** The same row, with how many rendered pages load it (SH1280). */
    public ShStaticFileItem withLoadedBy(Integer pages) {
        return new ShStaticFileItem(id, fileName, contentType, size, owner, date, folderId,
                folderName, siteId, siteName, width, height, alt, caption, tags, sha256, usageCount, pages);
    }
}
