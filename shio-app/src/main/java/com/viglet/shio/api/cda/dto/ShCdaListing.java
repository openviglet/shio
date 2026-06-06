/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.cda.dto;

import java.util.List;

/**
 * Content Delivery API (CDA) folder children listing — subfolders plus
 * published posts, with paging metadata for the posts collection.
 *
 * <p>Frozen public read contract (SH1).</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
public record ShCdaListing(
        String folderId,
        List<ShCdaFolder> folders,
        List<ShCdaPostSummary> posts,
        int page,
        int size,
        long totalPosts) {
}
