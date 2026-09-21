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
 * Content Delivery API (CDA) site representation.
 *
 * <p>Frozen public read contract (SH1). Field names are public API — do not
 * rename after release. See {@code docs/specs/SH1-cda-read-contract.md}.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
// SH127: nulls omitted. NON_NULL only — a `false` or a `0` here is an answer.
// Rationale and the SDK half of the change: ShCdaJsonLeanTest.
//
// SH1123 — `locales` is on the site reads (`/site`, `/site/{id}`) and absent from the site a
// breadcrumb carries: a path is read per page, and resolving an undeclared site's languages is a
// content query that a front end needs once, not on every navigation.
@JsonInclude(Include.NON_NULL)
public record ShCdaSite(
        String id,
        String name,
        String description,
        String url,
        String rootFolderId,
        ShCdaSiteLocales locales) {

    /** A site with no locale policy attached — the shape a breadcrumb carries. */
    public ShCdaSite(String id, String name, String description, String url, String rootFolderId) {
        this(id, name, description, url, rootFolderId, null);
    }
}
