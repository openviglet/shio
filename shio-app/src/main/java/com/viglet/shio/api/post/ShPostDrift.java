/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post;

import com.viglet.shio.persistence.model.post.unified.ShPostUnified;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Whether publishing this draft would change what a visitor sees (SH993).
 *
 * <p>Extracted from {@code ShAgentBatchService}, which has asked it since SH83 so that re-publishing
 * unchanged content reports {@code noop} rather than claiming a publish. A release runs the same
 * question over a whole set, and a second implementation of it is the P5 split that makes one surface
 * call a page changed while the other calls it unchanged — the data is JSON, so the comparison has to
 * be over the parsed tree and not the bytes.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShPostDrift {

    private ShPostDrift() {
        throw new IllegalStateException("Utility class");
    }

    /** True when the draft and the published row would render the same page. */
    public static boolean sameContent(ObjectMapper mapper, ShPostUnified draft,
                                      ShPostUnified published) {
        return tree(mapper, draft.getData()).equals(tree(mapper, published.getData()))
                && nullSafe(draft.getTitle()).equals(nullSafe(published.getTitle()))
                && nullSafe(draft.getFurl()).equals(nullSafe(published.getFurl()));
    }

    /**
     * A post's stored {@code data} as a tree, or an empty object when it will not parse.
     *
     * <p>Public because {@code ShAgentBatchService} reads the same column the same way, and this is
     * the one place that decides unparseable data is empty rather than an error — a second copy of
     * that decision is a second silent catch, which is what {@code ShSilentCatchInventoryTest}
     * counts (SH270).</p>
     */
    public static JsonNode tree(ObjectMapper mapper, String json) {
        if (json == null || json.isBlank()) {
            return mapper.createObjectNode();
        }
        try {
            return mapper.readTree(json);
        } catch (JacksonException e) {
            // Unparseable data is treated as empty rather than as a difference: the row is broken
            // either way, and throwing here would fail a publish for a reason the caller cannot act
            // on from this question.
            return mapper.createObjectNode();
        }
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
