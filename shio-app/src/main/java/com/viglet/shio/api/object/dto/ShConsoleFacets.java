/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.object.dto;

import java.util.Map;
import java.util.TreeMap;

/**
 * How the filtered posts of one listing divide by the filter bar's facets (SH1024).
 *
 * <p>Counted over exactly the rows the listing pages, under every filter the request carried, so
 * each facet's counts add up to {@code totalPosts} and choosing a value lists as many posts as its
 * count said. Keys are the vocabulary's own words: the post-type <b>name</b>, {@code draft} and
 * {@code published}, and the locale as stored. {@code state} is the one that does not sum: a post
 * with a live row and a newer draft is both, which is what choosing either value answers too.</p>
 *
 * @param type   posts per post-type name
 * @param state  {@code draft} — posts with a draft row; {@code published} — posts that are live
 * @param locale posts per locale; a post with none is not counted
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public record ShConsoleFacets(Map<String, Long> type, Map<String, Long> state, Map<String, Long> locale) {
    public ShConsoleFacets {
        // Sorted, so one listing always serialises its counts in one order (P5).
        type = type == null ? Map.of() : new TreeMap<>(type);
        state = state == null ? Map.of() : new TreeMap<>(state);
        locale = locale == null ? Map.of() : new TreeMap<>(locale);
    }
}
