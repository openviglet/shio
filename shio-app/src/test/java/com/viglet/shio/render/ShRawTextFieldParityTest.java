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

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;

/**
 * SH657 — the raw-text list and the table that declares it cannot disagree.
 *
 * <h2>What went wrong, and why a list was the wrong shape</h2>
 *
 * <p>{@code ShRenderHelpers.RAW_TEXT_FIELDS} spelled its pairs by hand and stated its selection rule
 * in prose: <i>the fields the product declares as {@code Ace Editor - CSS/JavaScript}</i>.
 * {@code ShRenderPostTypes} declared <b>six</b> such fields and the list held <b>four</b>.
 * {@code Theme.TOKENS} (SH195) and {@code SiteScripts.SCRIPTS} (SH389) were each added to that table
 * on their own task, correctly, and neither reached the list — because nothing joined them. A
 * hand-kept list that states a rule can diverge from it in silence, and this one had, twice.</p>
 *
 * <p>The consequence is not cosmetic. A row read through {@code getRelation}, {@code query} or any
 * other listing helper hands a plain field to Handlebars, so
 * {@code <script type="application/json">{{TOKENS}}</script>} emits {@code &quot;} for every key and
 * the JSON does not parse. That is SH461's defect and SH555's, a third and fourth time, in the one
 * place that already knew the answer.</p>
 *
 * <h2>Why this test still exists after the derivation</h2>
 *
 * <p>The list is now computed from the widget, so the two <em>cannot</em> drift — which is exactly
 * the state in which a reader stops being able to tell a correct rule from a vacuous one. So this
 * asserts the derivation's <b>result</b>, named pair by pair, against nothing it computes itself:
 * the six that must be there and the ones that must not. A future change to the widget of any of
 * these fields is a change to what the renderer escapes, and it should have to argue with a test
 * rather than land as a side effect (SH322 — the instrument, not the two entries).</p>
 *
 * @author Alexandre Oliveira
 */
class ShRawTextFieldParityTest {

    @Test
    @DisplayName("SH657: the six code-editor fields are raw, named one by one")
    void everyCodeEditorFieldIsRaw() {
        Map<String, Set<String>> raw = ShRenderPostTypes.rawTextFields();

        assertThat(raw.get(ShSystemPostType.THEME))
                .as("""
                        a Theme's stylesheet and script are the original case (SH461), and TOKENS \
                        is the one that was missing: JSON in a JavaScript editor (SH195), which \
                        escaping turns into a document that does not parse""")
                .containsExactlyInAnyOrder(ShSystemPostTypeAttr.CSS,
                        ShSystemPostTypeAttr.JAVASCRIPT, ShSystemPostTypeAttr.TOKENS);

        assertThat(raw.get(ShSystemPostType.REGION))
                .containsExactly(ShSystemPostTypeAttr.JAVASCRIPT);
        assertThat(raw.get(ShSystemPostType.PAGE_LAYOUT))
                .containsExactly(ShSystemPostTypeAttr.JAVASCRIPT);
        assertThat(raw.get(ShSystemPostType.SITE_SCRIPTS))
                .as("""
                        the second one the hand-kept list never gained (SH389): a site's third-party \
                        scripts are a JSON tree, emitted into a <script>, and escaped it is not one""")
                .containsExactly(ShSystemPostTypeAttr.SCRIPTS);

        Set<String> pairs = new LinkedHashSet<>();
        raw.forEach((type, fields) -> fields.forEach(field -> pairs.add(type + "." + field)));
        assertThat(pairs)
                .as("six, which is what `ShRenderPostTypes` declares — a seventh means a field was "
                        + "added and this test is the argument it has to win first")
                .hasSize(6);
    }

    @Test
    @DisplayName("SH657: a template is not a value, and an ordinary field still escapes")
    void whatIsDeliberatelyNotRaw() {
        Map<String, Set<String>> raw = ShRenderPostTypes.rawTextFields();

        for (String type : new String[] { ShSystemPostType.PAGE_LAYOUT, ShSystemPostType.REGION }) {
            assertThat(raw.getOrDefault(type, Set.of()))
                    .as("""
                            %s's HTML is `Ace Editor - HTML` and is a TEMPLATE — rendered, never \
                            interpolated — so it reaches no stash and marking it raw would say \
                            something true about a path that does not exist""", type)
                    .doesNotContain(ShSystemPostTypeAttr.HTML);
        }

        assertThat(raw.getOrDefault(ShSystemPostType.THEME, Set.of()))
                .as("""
                        and a Theme's TITLE is a Text field: raw-text status is the widget's claim \
                        about where a value can go, not a blanket exemption for the type""")
                .doesNotContain(ShSystemPostTypeAttr.TITLE);

        assertThat(raw)
                .as("""
                        keyed by PAIR and not by field name: an author may add a field called CSS \
                        to a Text post, and that one is a string in a document body with no \
                        declared context — the case escaping exists for. Only the render types are \
                        in this map at all""")
                .containsOnlyKeys(ShSystemPostType.THEME, ShSystemPostType.REGION,
                        ShSystemPostType.PAGE_LAYOUT, ShSystemPostType.SITE_SCRIPTS);
    }
}
