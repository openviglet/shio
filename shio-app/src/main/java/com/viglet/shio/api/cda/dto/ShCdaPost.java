/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.cda.dto;

import java.time.Instant;
import java.util.List;

import tools.jackson.databind.JsonNode;

/**
 * Content Delivery API (CDA) full post representation. The post-type
 * attributes are expanded from the {@code data} CLOB into {@link #attrs}
 * as a parsed JSON document.
 *
 * <p>Frozen public read contract (SH1). Deliberately excludes authoring
 * fields ({@code owner}, {@code modifier}, {@code publisher},
 * {@code publishStatus}).</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
public record ShCdaPost(
        String id,
        String title,
        String summary,
        String furl,
        String postType,
        String siteId,
        String folderId,
        boolean published,
        Instant date,
        Instant modifiedDate,
        Instant publicationDate,
        String locale,
        List<String> availableLocales,
        JsonNode attrs) {
}
