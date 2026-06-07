/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.exchange.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Exchange representation of a {@code ShSite} (SH68). Mirrors the persisted
 * scalar fields and lists the ids of the site's root folders so the import side
 * can re-anchor the tree. The {@code id} is the source id; clone (SH70) remaps
 * it and every reference to it.
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ShExchangeSite(
        String id,
        String name,
        String description,
        String url,
        String furl,
        String postTypeLayout,
        String searchablePostTypes,
        String formSuccess,
        String owner,
        List<String> rootFolderIds) {

    public ShExchangeSite {
        rootFolderIds = rootFolderIds == null ? List.of() : List.copyOf(rootFolderIds);
    }
}
