/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.post.type;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.persistence.model.post.type.ShPostType;

/**
 * What a form submission may create (SH631).
 *
 * <h2>The one predicate the endpoint's severity turns on</h2>
 *
 * <p>SH633's endpoint accepts a document from the internet. The rate limit, the honeypot and the
 * {@code DRAFT} default each bound how <em>much</em> or how <em>visible</em> the damage is. This
 * bounds whether there is a class of damage at all: {@code PageLayout}, {@code Region} and
 * {@code Theme} are Handlebars, compiled and executed by the renderer, so a form that could create
 * one is a stranger writing a template this instance runs.</p>
 *
 * <p>Every case below is a way the floor could be got wrong, not a tour of the API.</p>
 *
 * @author Alexandre Oliveira
 */
class ShFormEligibilityTest {

    private static ShPostType type(String name, byte system) {
        ShPostType postType = new ShPostType();
        postType.setName(name);
        postType.setTitle(name);
        postType.setSystem(system);
        return postType;
    }

    @Test
    @DisplayName("SH631: no renderer type is ever eligible, whatever a site's list says")
    void aRendererTypeIsNeverEligible() {
        for (String name : List.of(ShSystemPostType.PAGE_LAYOUT, ShSystemPostType.REGION,
                ShSystemPostType.THEME)) {
            // Named on the allow-list AND flagged non-system in the row — the worst case, and the
            // one an operator can produce by hand. Still refused.
            assertThat(ShFormEligibility.isEligible(type(name, (byte) 0), Set.of(name)))
                    .as("""
                            %s is Handlebars the renderer compiles and executes. A submission that \
                            could create one is remote template execution, not spam — so the name \
                            list refuses it even when the `system` flag on the row does not \
                            (SH631)""", name)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("SH631: the two halves of the floor are checked independently")
    void theFlagAndTheNameAreBothChecked() {
        // Belt and braces, because they fail differently: the flag is data an import or a restore
        // can carry wrongly, and the name list is code that cannot describe a type this build has
        // not heard of. A type satisfying one and not the other is exactly what to refuse.
        assertThat(ShFormEligibility.isEligibleType(type("Article", (byte) 1)))
                .as("flagged system, ordinary name — refused on the flag")
                .isFalse();
        assertThat(ShFormEligibility.isEligibleType(type(ShSystemPostType.THEME, (byte) 0)))
                .as("platform-owned name, flag says otherwise — refused on the name")
                .isFalse();
        assertThat(ShFormEligibility.isEligibleType(type("Article", (byte) 0)))
                .as("neither — eligible")
                .isTrue();
    }

    @Test
    @DisplayName("SH631: Redirect, SiteScripts and File are the same defect in different clothes")
    void theOtherPlatformTypesAreRefusedToo() {
        // A submission that could write a Redirect could point any URL of the site anywhere; one
        // that could write SiteScripts could add a <script src> to every page. Neither is a
        // rendering type and both are template execution by another route.
        assertThat(ShFormEligibility.isEligibleType(type(ShSystemPostType.REDIRECT, (byte) 0)))
                .isFalse();
        assertThat(ShFormEligibility.isEligibleType(type(ShSystemPostType.SITE_SCRIPTS, (byte) 0)))
                .isFalse();
        assertThat(ShFormEligibility.isEligibleType(type(ShSystemPostType.FILE, (byte) 0)))
                .isFalse();
    }

    @Test
    @DisplayName("SH631: an unresolved type, and an empty allow-list, both read as no")
    void theDefaultsPointAtRefusal() {
        assertThat(ShFormEligibility.isEligibleType(null)).isFalse();
        assertThat(ShFormEligibility.isEligibleType(type(null, (byte) 0))).isFalse();
        assertThat(ShFormEligibility.isEligibleType(type("  ", (byte) 0))).isFalse();

        ShPostType article = type("Article", (byte) 0);
        // The direction every default on this path takes (SH364, P7): a site that has not opted in
        // invites nothing, rather than inviting everything.
        assertThat(ShFormEligibility.isEligible(article, null)).isFalse();
        assertThat(ShFormEligibility.isEligible(article, Set.of())).isFalse();
        assertThat(ShFormEligibility.isEligible(article, Set.of("Other"))).isFalse();
        assertThat(ShFormEligibility.isEligible(article, Set.of("Article"))).isTrue();
    }

    @Test
    @DisplayName("SH631: eligibleNames answers what an operator could have typed")
    void eligibleNamesIsTheTeachingHalf() {
        // For P6: an operator who put `Theme` on the list is told what they could have put there,
        // rather than discovering by absence that nothing arrives.
        Set<String> names = ShFormEligibility.eligibleNames(List.of(
                type("Article", (byte) 0),
                type("Contact", (byte) 0),
                type(ShSystemPostType.THEME, (byte) 0),
                type(ShSystemPostType.PAGE_LAYOUT, (byte) 1)));

        assertThat(names).containsExactly("Article", "Contact");
        assertThat(ShFormEligibility.eligibleNames(null)).isEmpty();
    }
}
