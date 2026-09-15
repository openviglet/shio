/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.persistence.model.post.unified.ShPostState;

/**
 * SH990 — the {@code state=} grammar, as one reading every path shares.
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
class ShContentPerspectiveTest {

    @Test
    @DisplayName("SH990: absent means the path decides, and each path supplies its own default")
    void absentIsThePathsDefault() {
        assertThat(ShContentPerspective.parse(null).isDefault()).isTrue();
        assertThat(ShContentPerspective.parse("  ").isDefault()).isTrue();
        assertThat(ShContentPerspective.parse(null).rowOr(ShPostState.DRAFT))
                .as("SH990: a preview's default is the draft, a read's is draft-preferred — so the "
                        + "grammar returns no row and the caller names the fallback")
                .isEqualTo(ShPostState.DRAFT);
    }

    @Test
    @DisplayName("SH990: draft and published in any case, since the console sends DRAFT and PUBLISHED")
    void rowsInAnyCase() {
        assertThat(ShContentPerspective.parse("published").row()).isEqualTo(ShPostState.PUBLISHED);
        assertThat(ShContentPerspective.parse("PUBLISHED").row()).isEqualTo(ShPostState.PUBLISHED);
        assertThat(ShContentPerspective.parse(" Draft ").row()).isEqualTo(ShPostState.DRAFT);
    }

    @Test
    @DisplayName("SH990: rev:<n> is a revision by the number its listing shows")
    void aRevisionByNumber() {
        ShContentPerspective perspective = ShContentPerspective.parse("REV: 12");

        assertThat(perspective.isRevision()).isTrue();
        assertThat(perspective.revision()).isEqualTo(12);
        assertThat(perspective.row()).isNull();
    }

    @Test
    @DisplayName("SH990: anything outside the grammar is refused with the three spellings, never read as the default")
    void outsideTheGrammarIsRefused() {
        for (String sent : new String[] { "live", "rev:", "rev:x", "rev:0", "rev:-3", "revision:2" }) {
            assertThatThrownBy(() -> ShContentPerspective.parse(sent))
                    .as("SH990: '%s' — before, /preview read this as the draft and said nothing", sent)
                    .isInstanceOf(ShContentPerspective.UnknownStateException.class);
        }
    }

    @Test
    @DisplayName("SH990: a path that reads rows refuses a revision rather than answering with the draft")
    void aRowOnlyPathRefusesARevision() {
        assertThat(ShContentPerspective.parse("published").requireRow("/preview", "x").row())
                .isEqualTo(ShPostState.PUBLISHED);
        assertThatThrownBy(() -> ShContentPerspective.parse("rev:3").requireRow("/preview",
                "GET /api/v2/agent/read?address=<address>&state=rev:3"))
                .isInstanceOf(ShContentPerspective.RevisionNotServedException.class)
                .hasMessageContaining("/preview");
    }
}
