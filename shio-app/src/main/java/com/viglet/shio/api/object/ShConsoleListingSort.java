/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.object;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;

/**
 * The axes a paged console listing may be ordered by (SH165).
 *
 * <p><b>Why the sort moved to the server at all.</b> The browser used to sort the folder's
 * children itself, which was correct precisely because it held all of them. A paged listing
 * with a client-side sort silently sorts <em>one page</em> — the half-migration §XIV.7 warns
 * about, and the kind of wrong that looks right until a folder outgrows a page. So the order
 * is the server's now, and the closed set lives here rather than as a string compared in
 * three places.</p>
 *
 * <p>There is no {@code position} axis, because a post has none: manual ordering is a folder
 * property in this model ({@code ShObject.position}), which is why folders come back whole and
 * ordered and the browser's "order" option means {@code title} for the posts underneath
 * them.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public enum ShConsoleListingSort {

    /** By title, A→Z, case-insensitively — what the unpaged listing did in memory. */
    TITLE("title", Sort.by(Sort.Order.asc("title").ignoreCase())),

    /** Newest first, which is what a curator means by "sort by date" on a content list. */
    DATE("date", Sort.by(Sort.Order.desc("date"))),

    /** Most recently edited first — the axis a review pass wants. */
    MODIFIED("modifiedDate", Sort.by(Sort.Order.desc("modifiedDate")));

    private final String key;
    private final Sort sort;

    ShConsoleListingSort(String key, Sort sort) {
        this.key = key;
        this.sort = sort;
    }

    public String key() {
        return key;
    }

    /**
     * The order, with {@code title} appended as a tiebreak on every axis but its own.
     *
     * <p>A page is only stable if the order is total: two posts created in the same
     * millisecond otherwise change places between page 1 and page 2, and a row is duplicated
     * or skipped without anything looking wrong (P5).</p>
     */
    public Sort sort() {
        return this == TITLE ? sort : sort.and(Sort.by(Sort.Order.asc("title").ignoreCase()));
    }

    /** The allowed values, in declaration order — the {@code allowed} set of a teaching 400. */
    public static List<String> keys() {
        return java.util.Arrays.stream(values()).map(ShConsoleListingSort::key).toList();
    }

    /**
     * Resolves a {@code sort} parameter; blank or absent means {@link #TITLE}.
     *
     * @throws ShUnknownListingSortException on anything else — never a silent fallback to
     *         the default, which would answer {@code 200} with an order the caller did not
     *         ask for and cannot detect (the same rule {@code /agent/context}'s
     *         {@code ?format=} follows).
     */
    public static ShConsoleListingSort of(String value) {
        if (value == null || value.isBlank()) {
            return TITLE;
        }
        String wanted = value.trim();
        return resolve(wanted).orElseThrow(() -> new ShUnknownListingSortException(wanted, keys()));
    }

    private static Optional<ShConsoleListingSort> resolve(String wanted) {
        return java.util.Arrays.stream(values())
                .filter(candidate -> candidate.key.equalsIgnoreCase(wanted)
                        || candidate.name().equalsIgnoreCase(wanted))
                .findFirst();
    }
}
