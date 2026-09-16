/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.cda.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import java.time.Instant;
import java.util.List;
import java.util.Map;

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
// SH127: nulls omitted. NON_NULL only — a `false` or a `0` here is an answer.
// Rationale and the SDK half of the change: ShCdaJsonLeanTest.
@JsonInclude(Include.NON_NULL)
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
        JsonNode attrs,
        /**
         * SH1016 — field name to rendered HTML, for the block fields this post has.
         *
         * <p>Beside {@code attrs}, never instead of it. A headless site that maps block types to
         * its own components reads the array; one that has been dropping a body into a div for two
         * years reads this. Delivering only the second is the shape the block model replaced, and
         * delivering only the first breaks every existing reader — so both go out and the front end
         * picks. Absent (SH127's {@code NON_NULL}) for a post with no block field, which is every
         * post that existed before SH1015.</p>
         */
        Map<String, String> rendered) {
}
