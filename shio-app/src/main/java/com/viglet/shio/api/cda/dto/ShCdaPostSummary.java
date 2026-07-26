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

/**
 * Content Delivery API (CDA) post listing item — no heavy attribute payload.
 *
 * <p>Frozen public read contract (SH1).</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
// SH127: nulls omitted. NON_NULL only — a `false` or a `0` here is an answer.
// Rationale and the SDK half of the change: ShCdaJsonLeanTest.
@JsonInclude(Include.NON_NULL)
public record ShCdaPostSummary(
        String id,
        String title,
        String summary,
        String furl,
        String postType,
        String locale,
        Instant date,
        Instant modifiedDate) {
}
