/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository.ShListingKey;

/**
 * SH278 — which row of a post the renderer reads, and in what order, in one place.
 *
 * <h2>Why this is shared rather than repeated</h2>
 *
 * <p>SH247 extracted the DRAFT-preference out of {@code ShRenderHelpers}' listing helpers with a
 * stated reason: "a second copy of the preference is a second chance to get it backwards, and the
 * failure mode is a preview showing the live page while the curator hunts for their unpublished
 * change". {@code ShPageRenderService} had no copy at all — it read both states and took whichever
 * row came first — so the rule was extracted once and then not applied to the thing that resolves
 * the <em>layout</em>. This is the one writer for both (SH245's rule).</p>
 *
 * <h2>The order is total, and that is the SH278 half</h2>
 *
 * <p>A derived finder with no {@code order by} returns rows in whatever order the database chose, so
 * {@code findFirst} was undefined in two different ways: between the DRAFT and PUBLISHED rows of one
 * template, and between two templates that share a title (SH263). The second cannot be made
 * <em>correct</em> — a duplicate title is a defect — but it can be made <b>consistent</b>, which
 * matters more than it sounds: a duplicate that renders the same way every time is a defect somebody
 * recognises, while one that alternates between runs looks like flakiness in the renderer and moves
 * SH101's digest for a change nobody made. It also means the same content renders identically on H2,
 * MariaDB and PostgreSQL, which is P5 and was not true before.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
final class ShRenderState {

    private ShRenderState() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * The states to read for a render in {@code state}.
     *
     * <p>A DRAFT render reads both and prefers the draft — that is what makes a preview a preview. A
     * PUBLISHED render reads only published rows, because what a visitor gets must not depend on an
     * unpublished edit.</p>
     */
    static List<ShPostState> statesFor(ShPostState state) {
        return state == ShPostState.PUBLISHED
                ? List.of(ShPostState.PUBLISHED)
                : List.of(ShPostState.PUBLISHED, ShPostState.DRAFT);
    }

    /**
     * One row per post id, the DRAFT winning on a draft render, in a <b>total</b> order.
     *
     * <p>Sorted by title then id before the fold, so the winner among rows that share a title is the
     * same one on every engine and every run. Ties on title are impossible to resolve meaningfully —
     * that is the SH263 defect — so they resolve by id, which is arbitrary and stable, which is the
     * only pair of properties available.</p>
     */
    static List<ShPostUnified> preferDraft(List<ShPostUnified> posts, ShPostState state) {
        Map<String, ShPostUnified> byId = new LinkedHashMap<>();
        for (ShPostUnified post : posts.stream().sorted(byTitleThenId()).toList()) {
            if (state == ShPostState.PUBLISHED && post.getState() != ShPostState.PUBLISHED) {
                continue;
            }
            ShPostUnified held = byId.get(post.getId());
            if (held == null || post.getState() == ShPostState.DRAFT) {
                byId.put(post.getId(), post);
            }
        }
        return List.copyOf(byId.values());
    }

    /**
     * The same fold over sort keys rather than entities (SH419).
     *
     * <p>A second implementation of one rule is what SH245 is about, so it is written to be
     * obviously the same one — same pre-sort, same winner, same order — and
     * {@code ShRenderListingOrderTest} holds the two against one population rather than trusting
     * the resemblance. It exists because the listing must decide <em>which</em> rows to load before
     * loading them: SH384 measured 213 entity rows read to render 10, and the fold is the step that
     * decides which of the 213 survive.</p>
     */
    static List<ShListingKey> preferDraftKeys(List<ShListingKey> keys, ShPostState state) {
        Map<String, ShListingKey> byId = new LinkedHashMap<>();
        for (ShListingKey key : keys.stream().sorted(byKeyTitleThenId()).toList()) {
            if (state == ShPostState.PUBLISHED && key.state() != ShPostState.PUBLISHED) {
                continue;
            }
            ShListingKey held = byId.get(key.id());
            if (held == null || key.state() == ShPostState.DRAFT) {
                byId.put(key.id(), key);
            }
        }
        return List.copyOf(byId.values());
    }

    /** {@link #byTitleThenId()} over a key — the same null handling, which is the part that matters. */
    static Comparator<ShListingKey> byKeyTitleThenId() {
        return Comparator
                .comparing((ShListingKey key) -> key.title() == null ? "" : key.title())
                .thenComparing(key -> key.id() == null ? "" : key.id());
    }

    /** Title, then id — a total order over rows the database returned in none. */
    static Comparator<ShPostUnified> byTitleThenId() {
        return Comparator
                .comparing((ShPostUnified post) -> post.getTitle() == null ? "" : post.getTitle())
                .thenComparing(post -> post.getId() == null ? "" : post.getId());
    }
}
