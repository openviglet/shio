/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.blueprint;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.blueprint.dto.ShBlueprint;

/**
 * What an applied package may do, stated once and checked (SH643).
 *
 * <h2>Why this needed writing down</h2>
 *
 * <p>A blueprint provisions post-types and writes pages <b>under the credentials of whoever applies
 * it</b>. That is fine while every package ships in the jar and the only other source is a
 * directory an operator chose — SH96's model, where "somebody decided" is the whole of the trust.
 * It stops being fine the moment a package can come from a stranger, and the first thing a stranger
 * needs is not an index: it is a <b>statement of what applying one can and cannot do to them</b>.</p>
 *
 * <p>Every bound below already existed. What did not exist is anywhere they were true together —
 * they lived in six comments across four classes, which is a security model somebody has to
 * reconstruct by reading the implementation. This is that reconstruction, done once, and pinned so
 * the published statement cannot quietly stop being true.</p>
 *
 * <p><b>Deliberately not new restrictions.</b> Writing the statement is the task; tightening the
 * model on the strength of having written it would be answering a question nobody asked with a
 * change nobody reviewed.</p>
 *
 * @author Alexandre Oliveira
 */
class ShBlueprintBoundsTest {

    @Test
    @DisplayName("SH643: a package cannot choose the site it is applied to")
    void aPackageDoesNotChooseTheSite() {
        // The rule that matters most once packages come from elsewhere: the target is the
        // caller's. `ShBlueprintLocalPathTest` proves a document naming `site` is refused; this
        // states why it is in the bounds — a package that could name its own site could write into
        // one the caller never mentioned.
        assertThat(ShBlueprintRegistry.CONTENT_KEYS)
                .as("`site` comes from the request, and a content document that names one does not "
                        + "load at all")
                .doesNotContain("site");
    }

    @Test
    @DisplayName("SH643: a package cannot delete content it has not heard of")
    void aPackageDoesNotPrune() {
        // `prune` makes a desired-state document authoritative: anything not declared is removed.
        // A package declaring it would delete a site's existing content on apply, which is the one
        // irreversible thing in the whole surface.
        assertThat(ShBlueprintRegistry.CONTENT_KEYS)
                .as("`prune` is never a package's decision to make")
                .doesNotContain("prune");
    }

    @Test
    @DisplayName("SH643: creating a site and widening a post-type are both opt-in, and off")
    void theDangerousHalvesAreOptIn() {
        // SH151 and SH152. `createSite` brings a site into existence; `mergePostTypes` adds fields
        // to a type the instance already has. Both are things a caller might want and neither is
        // something a package may assume — so the default carries neither, and the caller passes
        // the flag knowing what it does.
        assertThat(ShBlueprintWriteService.Options.NONE.createSite()).isFalse();
        assertThat(ShBlueprintWriteService.Options.NONE.mergePostTypes()).isFalse();
    }

    @Test
    @DisplayName("SH643: a package seeds a model, it does not own one")
    void aPackageDoesNotOverwriteAModel() {
        // `provisionFromDef` rather than `upsert`, which is the difference between "create what is
        // missing" and "make it look like mine". An upsert would revert a field a human added and
        // flip SH66's `managed` flag so the console refused to let them add it again.
        assertThat(ShBlueprintWriteService.class.getDeclaredMethods())
                .as("no method here upserts a post-type; the model is seeded, never replaced")
                .noneMatch(method -> method.getName().toLowerCase().contains("upsertposttype"));
    }

    @Test
    @DisplayName("SH643: one package's failure is not the catalogue's")
    void aFailureIsIsolated() {
        // SH217. The property a stranger's package needs before it needs an index: a package the
        // operator did not write cannot take the first-party ones down with it.
        assertThat(ShBlueprintRegistry.class.getDeclaredMethods())
                .as("the registry reports refusals rather than only throwing them")
                .anyMatch(method -> "rejected".equals(method.getName()));
    }

    @Test
    @DisplayName("SH643: the `shio-blueprint-` prefix is reserved for packages that are not ours")
    void thePrefixIsReservedForThirdParties() {
        // The naming convention, and the only part of it worth enforcing today. Reserving the
        // namespace costs nothing and makes the prefix MEAN something later: a reader seeing
        // `shio-blueprint-acme` knows it did not ship with the instance.
        //
        // Deliberately not a refusal on third-party packages. Whether a stranger's package MUST
        // carry the prefix is distribution policy — it decides who may publish under the product's
        // name — and that decision belongs with the index, which is not built.
        List<String> firstParty = new ShBlueprintRegistry(new ShBlueprintCodec()).all().values()
                .stream()
                .filter(blueprint -> ShBlueprint.CLASSPATH.equals(blueprint.origin()))
                .map(ShBlueprint::name)
                .toList();

        assertThat(firstParty)
                .as("a first-party package taking the prefix would make it useless as a signal — "
                        + "the one thing it is for is telling a reader this did not ship with the "
                        + "instance (SH643)")
                .isNotEmpty()
                .allSatisfy(name -> assertThat(name).doesNotStartWith("shio-blueprint-"));
    }
}
