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

import tools.jackson.databind.JsonNode;

/**
 * Exchange representation of a {@code ShPostUnified} row (SH68). One record per
 * {@code (id, state)} row — a post with both a {@code DRAFT} and a
 * {@code PUBLISHED} snapshot appears twice, sharing the same {@code id} and
 * differing in {@code state}, exactly as the unified table stores it.
 *
 * <p>The content is the unified {@code data} document carried <strong>verbatim
 * as a {@link JsonNode}</strong> — no flattening into the legacy split-model
 * attribute map. Id-bearing references <em>inside</em> {@code data} (File /
 * Content Select targets, Relator item ids) are left untouched here; rewriting
 * them on clone is the import side's concern (SH70), not the format's.</p>
 *
 * <p>{@code state} is the {@code ShPostState} name ({@code DRAFT}/{@code PUBLISHED})
 * and {@code postTypeName} references the post-type by name (resolved against the
 * package's {@link ShExchangePackage#postTypes()} or the target install) — both
 * kept as strings to keep the format decoupled from the persistence enums.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ShExchangePost(
        String id,
        String folderId,
        String postTypeName,
        String state,
        String title,
        String summary,
        String furl,
        String owner,
        boolean published,
        String publishStatus,
        int position,
        JsonNode data) {
}
