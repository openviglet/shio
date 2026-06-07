/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.exchange.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Exchange representation of a {@code ShFolder} (SH68). Carries both anchors so
 * the import side can reconstruct the tree unambiguously: a <strong>root</strong>
 * folder ({@code rootFolder == true}) references its {@code siteId} and has a
 * {@code null parentFolderId}; a <strong>child</strong> folder references its
 * {@code parentFolderId} and has a {@code null siteId} — matching the entity
 * invariant ({@code ShFolder.shSite} is set only on roots).
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ShExchangeFolder(
        String id,
        String name,
        String furl,
        boolean rootFolder,
        String siteId,
        String parentFolderId,
        int position,
        String owner) {
}
