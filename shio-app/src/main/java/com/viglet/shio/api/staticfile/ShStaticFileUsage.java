/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.staticfile;

/**
 * A post that references a static file (SH62 "used in"). Surfaced by
 * {@code GET /api/v2/staticfile/{id}/usage} so an editor can see where a file is
 * used before deleting it.
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public record ShStaticFileUsage(
        String postId,
        String title,
        String postTypeName,
        String folderName,
        String siteName,
        boolean published) {
}
