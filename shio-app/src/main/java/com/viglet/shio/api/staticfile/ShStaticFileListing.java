/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.staticfile;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * One page of the media library (SH1030).
 *
 * <h2>Why the envelope is the console listing's</h2>
 *
 * <p>{@code page}, {@code size} and a total, in that order and under those names, because
 * {@code ShConsoleListing} has carried them since SH165 and {@code ShCdaListing} since SH1. This
 * was the one content read nobody had paged, and giving it a third spelling of "which page is
 * this" would be the drift SH120 spent a task removing from the console listing. A reader who
 * knows one of them knows this.</p>
 *
 * <h2>Facets are the server's, and that is the point rather than a convenience</h2>
 *
 * <p>The console drew its category chips by counting the array it had, which worked exactly
 * because it had all of it. Over a page those counts describe the page — so a library of two
 * thousand images would report fifty, and the chip a curator presses to find the rest would be
 * the one telling them there is nothing to find. The counts have to come from wherever the whole
 * set is known, and after this change that is only here.</p>
 *
 * <p><b>Counted over the filtered set minus the category itself</b>, which is what makes a chip
 * usable: "image 2000" beside "video 40" while `image` is selected is the answer to "what else is
 * there", and counting inside the current category would answer "2000, 0, 0, 0" and tell nobody
 * anything.</p>
 *
 * @param items      one page of assets, newest first
 * @param page       the zero-based page index this response answers
 * @param size       the page size it was asked for
 * @param totalFiles how many assets match the filters in total — not how many are in this page,
 *                   which is what makes a cut visible (SH138's rule)
 * @param facets     how many assets each category holds, ignoring the category filter itself
 * @param pagesRendered SH1280 — on {@code loads=true}, how many pages were rendered to count
 *                   {@code loadedBy}; null when nobody asked
 * @param pagesTotal SH1280 — how many pages the rows' sites publish; above {@code pagesRendered}
 *                   when the cap cut, which makes a zero {@code loadedBy} a gap rather than a fact
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public record ShStaticFileListing(List<ShStaticFileItem> items, int page, int size,
                                  long totalFiles, Map<String, Long> facets,
                                  // Omitted when unasked, so a plain listing is the bytes it was.
                                  @JsonInclude(Include.NON_NULL) Integer pagesRendered,
                                  @JsonInclude(Include.NON_NULL) Integer pagesTotal) {

    /** The pre-SH1280 shape: no page loads were counted. */
    public ShStaticFileListing(List<ShStaticFileItem> items, int page, int size, long totalFiles,
                               Map<String, Long> facets) {
        this(items, page, size, totalFiles, facets, null, null);
    }

    /** The same page, with each row's {@code loadedBy} and what was rendered to know it (SH1280). */
    public ShStaticFileListing withLoads(ShAssetLoadService.Loads loads) {
        return new ShStaticFileListing(items.stream()
                .map(item -> item.withLoadedBy(loads.byAsset().getOrDefault(item.id(), 0)))
                .toList(), page, size, totalFiles, facets, loads.rendered(), loads.pages());
    }

    public ShStaticFileListing {
        items = items == null ? List.of() : List.copyOf(items);
        // A TreeMap, not Map.copyOf: an immutable map iterates in hash order, and this one is
        // serialised — so the same library would answer with its categories in a different order
        // from one JVM to the next, which is the byte instability ShByteStabilityLintTest exists
        // to refuse (P5). Sorted by name, so the order is a fact rather than an accident.
        facets = facets == null ? Map.of()
                : java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(facets));
    }
}
