/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.type.dto;

import java.util.List;

/**
 * Lossless, round-trippable description of one post-type attribute (SH16),
 * consumed by the {@code shio} CLI's {@code pull}/{@code push} (post-types as
 * code).
 *
 * <p>Unlike the delivery-shaped {@code ShCdaSchemaField} (SH11) — which drops
 * {@code Tab} fields, collapses every scalar to {@code multiple}/non-multiple
 * and never carries {@code widgetSettings} — this DTO preserves everything the
 * authoring model needs to be re-applied: the raw widget name (including
 * container widgets such as {@code Tab}) and the verbatim {@code widgetSettings}
 * JSON. Relator (and {@code Tab}) children are nested under {@code fields}.</p>
 *
 * <p>The entity {@code id} is intentionally omitted — a post-type is identified
 * by its API {@code name}, and ids are server-assigned on {@code push}.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
public record ShPostTypeAttrDef(
        String name,
        String label,
        String description,
        String widget,
        boolean required,
        boolean isTitle,
        boolean isSummary,
        int ordinal,
        String widgetSettings,
        List<ShPostTypeAttrDef> fields) {
}
