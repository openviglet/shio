/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository.ShListingKey;

/**
 * The two draft-preference folds agree, on one population (SH419).
 *
 * <h2>The risk this test is the price of</h2>
 *
 * <p>SH419 stopped the listing loading a whole folder to draw ten rows by deciding the window over
 * <em>sort keys</em> — id, state, date, title, post-type id — and loading only the survivors. That
 * required a second copy of the fold, over keys instead of entities, and a second family of
 * comparators beside {@code order}. Two writers for one rule is exactly the shape SH245 is about,
 * and the honest answer is not "they look the same" but this: the same population, folded and sorted
 * both ways, must produce <b>the same sequence of ids</b>.</p>
 *
 * <p>What it is guarding is not abstract. A listing whose order depends on which of the two paths ran
 * moves SH101's render digest for a change nobody made, and it would do so only on the pages that
 * happen to take one branch — a `field=` listing sorting differently from the same listing without
 * the filter. The null cases are where two hand-written comparators actually drift, so they are the
 * fixture: a null date, a null title, and two posts sharing a date.</p>
 *
 * <p>It needs no Spring context. The fold and the comparators are pure functions of the rows, which
 * is why they could be extracted at all.</p>
 *
 * @author Alexandre Oliveira
 */
class ShRenderListingOrderTest {

    private static final Instant T1 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-06-01T00:00:00Z");

    /** Deliberately including the three shapes a hand-written comparator gets wrong. */
    private static List<ShPostUnified> population() {
        return List.of(
                post("c", ShPostState.PUBLISHED, T2, "Banana"),
                post("a", ShPostState.PUBLISHED, T1, "apple"),
                // Same instant as `a`, so the id tiebreak is what decides — the reason the order is
                // total rather than merely sorted.
                post("b", ShPostState.PUBLISHED, T1, "Apple"),
                post("d", ShPostState.PUBLISHED, null, "no date"),
                post("e", ShPostState.PUBLISHED, T2, null));
    }

    private static List<ShListingKey> keysOf(List<ShPostUnified> posts) {
        return posts.stream()
                .map(post -> new ShListingKey(post.getId(), post.getState(), post.getDate(),
                        post.getTitle(), null))
                .toList();
    }

    @Test
    @DisplayName("SH419: the fold keeps the same rows, in the same order, over keys as over entities")
    void theFoldsAgree() {
        List<ShPostUnified> posts = population();

        for (ShPostState state : List.of(ShPostState.PUBLISHED, ShPostState.DRAFT)) {
            List<String> overEntities = ShRenderState.preferDraft(posts, state).stream()
                    .map(ShPostUnified::getId).toList();
            List<String> overKeys = ShRenderState.preferDraftKeys(keysOf(posts), state).stream()
                    .map(ShListingKey::id).toList();

            assertThat(overKeys)
                    .as("""
                            the fold decides WHICH rows the listing will load, so a disagreement here \
                            is the listing drawing different posts depending on which path ran — \
                            invisible until somebody compares two pages that should be one (%s)""",
                            state)
                    .isEqualTo(overEntities);
        }
    }

    @Test
    @DisplayName("SH419: a DRAFT row wins over its PUBLISHED twin, in both folds")
    void theDraftWinsInBoth() {
        // The property the whole preview path rests on, and the one a key-shaped fold could lose
        // silently: two rows, one id, and the draft is the one a curator is editing.
        List<ShPostUnified> pair = List.of(
                post("x", ShPostState.PUBLISHED, T1, "Published title"),
                post("x", ShPostState.DRAFT, T2, "Draft title"));

        assertThat(ShRenderState.preferDraft(pair, ShPostState.DRAFT))
                .singleElement()
                .extracting(ShPostUnified::getState).isEqualTo(ShPostState.DRAFT);
        assertThat(ShRenderState.preferDraftKeys(keysOf(pair), ShPostState.DRAFT))
                .singleElement()
                .extracting(ShListingKey::state).isEqualTo(ShPostState.DRAFT);

        // And a published render sees only the published row, in both.
        assertThat(ShRenderState.preferDraft(pair, ShPostState.PUBLISHED))
                .singleElement()
                .extracting(ShPostUnified::getState).isEqualTo(ShPostState.PUBLISHED);
        assertThat(ShRenderState.preferDraftKeys(keysOf(pair), ShPostState.PUBLISHED))
                .singleElement()
                .extracting(ShListingKey::state).isEqualTo(ShPostState.PUBLISHED);
    }

    private static ShPostUnified post(String id, ShPostState state, Instant date, String title) {
        ShPostUnified post = new ShPostUnified();
        post.setId(id);
        post.setState(state);
        post.setDate(date);
        post.setTitle(title);
        return post;
    }
}
