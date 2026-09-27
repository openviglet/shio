/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.exchange.site;

import java.util.List;

import com.viglet.shio.persistence.model.site.ShSite;

/**
 * What an exchange import did, beyond the site it created (SH1079).
 *
 * <p>A package is the one write whose contents nobody typed, so the answer says what arrived and
 * what the import already knows went wrong. Before this, {@code POST /api/v2/import} answered with
 * the site row alone, and a post that was skipped, a post type that did not resolve, or files the
 * import does not bring over were silent.</p>
 *
 * @param site     the first site the package created, as before
 * @param arrived  counts by kind
 * @param problems what the import noticed and could not do, in the order it met them; empty when
 *                 nothing went wrong
 */
public record ShImportOutcome(ShSite site, Arrived arrived, List<Problem> problems) {

    /**
     * Counts by kind. {@code posts} counts logical posts, so a DRAFT and a PUBLISHED row of one
     * post are one. {@code postTypesCreated} counts only the types that were new here; a type the
     * instance already had is reused and not counted. {@code files} counts the {@code File} posts
     * whose bytes were restored (SH1134).
     */
    public record Arrived(int sites, int folders, int posts, int postTypesCreated, int files) {
    }

    /**
     * One thing the import could not do.
     *
     * @param code    stable, for a caller to branch on: {@code post-skipped},
     *                {@code post-type-unresolved} or {@code file-bytes-missing}
     * @param subject what it is about: a post's title, a post type's name, or a file's name
     * @param message the sentence a person reads
     */
    public record Problem(String code, String subject, String message) {
    }
}
