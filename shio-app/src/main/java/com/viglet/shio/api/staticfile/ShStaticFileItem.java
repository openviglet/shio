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

/**
 * A single static file (a {@code File}-post-type {@link com.viglet.shio.persistence.model.post.unified.ShPostUnified})
 * as listed by the SH61 file-manager console page. The bytes are served from
 * {@code /api/v2/staticfile/{id}} (and {@code /file_source/{id}/{fileName}}).
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
        String siteName) {
}
