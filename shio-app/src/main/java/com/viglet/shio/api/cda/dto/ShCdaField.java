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

/**
 * Content Delivery API (CDA) editable-field descriptor (SH10). One per top-level
 * post-type attribute, so the Universal Editor's properties panel can render and
 * label every field of a post — not only those instrumented with
 * {@code data-sh-*} attributes in the rendered DOM (the "field mapping" half of
 * the editor bridge).
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
// SH127: nulls omitted. NON_NULL only — a `false` or a `0` here is an answer.
// Rationale and the SDK half of the change: ShCdaJsonLeanTest.
@JsonInclude(Include.NON_NULL)
public record ShCdaField(
        String name,
        String label,
        String description,
        String widget,
        boolean required,
        boolean isTitle,
        boolean isSummary,
        int ordinal) {
}
