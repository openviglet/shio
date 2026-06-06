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
 * Content Delivery API (CDA) post-type schema field (SH11). A recursive,
 * delivery-shaped description of one post-type attribute, designed to drive
 * client-side TypeScript code generation ({@code shio-types}) and the
 * widget-aware render components in the React SDK.
 *
 * <p>Unlike {@link ShCdaField} (SH10, a flat editor-panel descriptor), this DTO
 * carries the two facts a code generator needs to infer the TypeScript type of
 * {@code post.attrs[name]}:</p>
 * <ul>
 *   <li>{@code multiple} — the attribute delivers an array (a {@code Check Box} /
 *       {@code Multi Select} value, or a {@code Relator}'s item list);</li>
 *   <li>{@code fields} — the nested attributes of a {@code Relator}, so the
 *       generator can emit an object type for each item.</li>
 * </ul>
 *
 * <p>Container-only widgets ({@code Tab}) carry no delivered value and are
 * omitted from the schema by {@code ShCdaMapper}.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
public record ShCdaSchemaField(
        String name,
        String label,
        String description,
        String widget,
        boolean required,
        boolean multiple,
        boolean isTitle,
        boolean isSummary,
        int ordinal,
        List<ShCdaSchemaField> fields) {
}
