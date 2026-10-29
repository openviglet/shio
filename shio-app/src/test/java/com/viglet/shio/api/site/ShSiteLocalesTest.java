/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.site;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.api.error.ShTeachingException;

import tools.jackson.databind.ObjectMapper;

/**
 * SH1056 — the locale policy as a value: what it accepts, what it refuses, and what it stores.
 *
 * <h2>The half that matters is the refusals</h2>
 *
 * <p>A policy is three answers that are only meaningful together, and each of the four ways to get
 * that wrong is one an agent reaches by guessing: a tag cased the way the language is written
 * ({@code pt-BR}), a default outside the set, a fallback pointing outside it, and a pair of rules
 * that point at each other. Every one of them would otherwise be stored and read back as a policy,
 * and the surface it broke would be a language switcher on somebody's live site.</p>
 *
 * @author Alexandre Oliveira
 */
class ShSiteLocalesTest {

    private static final Set<String> KNOWN = Set.of("en", "pt-br", "pt", "es", "fr");

    private final ObjectMapper objectMapper = new ObjectMapper();

    // --- derived: the answer that was already being given ----------------------------------

    @Test
    @DisplayName("an undeclared site reports the locales its content is written in, sorted")
    void derivedIsTheUnionOfTheContent() {
        ShSiteLocales locales = ShSiteLocales.derived("en", List.of("pt-br", "en", "pt-br"));

        assertThat(locales.enabled())
                .as("SH1056: this is the answer verify already gave, kept — the migration is that "
                        + "an unset column changes nothing")
                .containsExactly("en", "pt-br");
        assertThat(locales.declared())
                .as("SH1056: and it says it is an observation, not a decision")
                .isFalse();
    }

    @Test
    @DisplayName("a site written only in Portuguese is not reported as being in English")
    void derivedDefaultsToItsOwnContent() {
        assertThat(ShSiteLocales.derived("en", List.of("pt-br")).defaultLocale())
                .as("SH1056: the instance default is a fallback for a site that has said nothing, "
                        + "not a claim about a site whose every page is in another language")
                .isEqualTo("pt-br");
    }

    @Test
    @DisplayName("a site with no content at all still answers something a switcher can render")
    void derivedOnAnEmptySiteIsTheInstanceDefault() {
        ShSiteLocales locales = ShSiteLocales.derived("en", List.of());

        assertThat(locales.defaultLocale()).isEqualTo("en");
        assertThat(locales.enabled()).containsExactly("en");
    }

    // --- declared: what it accepts ----------------------------------------------------------

    @Test
    @DisplayName("a declared policy keeps the order it was declared in")
    void declaredOrderIsTheSwitchersOrder() {
        ShSiteLocales locales = ShSiteLocales.of("pt-br", List.of("pt-br", "en", "es"),
                Map.of(), KNOWN);

        assertThat(locales.enabled())
                .as("SH1056: a switcher's order is a decision, so it is not sorted the way the "
                        + "derived union is")
                .containsExactly("pt-br", "en", "es");
        assertThat(locales.declared()).isTrue();
    }

    @Test
    @DisplayName("a repeated locale is listed once, and does not make the list two long")
    void duplicatesCollapse() {
        assertThat(ShSiteLocales.of("en", List.of("en", "pt-br", "en"), Map.of(), KNOWN).enabled())
                .containsExactly("en", "pt-br");
    }

    @Test
    @DisplayName("no default named is the first locale offered")
    void theFirstOfferedIsTheDefault() {
        assertThat(ShSiteLocales.of(null, List.of("pt-br", "en"), Map.of(), KNOWN).defaultLocale())
                .isEqualTo("pt-br");
    }

    // --- declared: what it refuses ----------------------------------------------------------

    @Test
    @DisplayName("a tag cased the way the language is written is refused, and told the casing")
    void anUnknownTagIsRefusedWithTheCatalogue() {
        assertThatThrownBy(() -> ShSiteLocales.of("pt-BR", List.of("pt-BR"), Map.of(), KNOWN))
                .isInstanceOf(ShTeachingException.class)
                .hasMessageContaining("pt-BR");
    }

    @Test
    @DisplayName("a policy that enables nothing is refused, and is not the same as no policy")
    void anEmptySetIsRefused() {
        assertThatThrownBy(() -> ShSiteLocales.of("en", List.of(), Map.of(), KNOWN))
                .as("SH1056: leaving `locales` out keeps deriving them; sending an empty one asks "
                        + "for a site that offers no language at all, and the refusal says so")
                .isInstanceOf(ShSiteLocales.EmptyEnabledException.class);
    }

    @Test
    @DisplayName("a default outside the enabled set is refused")
    void aDefaultNobodyOffersIsRefused() {
        assertThatThrownBy(() -> ShSiteLocales.of("fr", List.of("en", "pt-br"), Map.of(), KNOWN))
                .isInstanceOf(ShSiteLocales.DefaultNotEnabledException.class);
    }

    @Test
    @DisplayName("a fallback pointing outside the enabled set is refused, on either side")
    void aFallbackNobodyOffersIsRefused() {
        assertThatThrownBy(() -> ShSiteLocales.of("en", List.of("en", "pt-br"),
                Map.of("pt-br", "fr"), KNOWN))
                .isInstanceOf(ShSiteLocales.FallbackNotEnabledException.class);
        assertThatThrownBy(() -> ShSiteLocales.of("en", List.of("en", "pt-br"),
                Map.of("fr", "en"), KNOWN))
                .isInstanceOf(ShSiteLocales.FallbackNotEnabledException.class);
    }

    @Test
    @DisplayName("fallback rules that point at each other are refused before they are stored")
    void aLoopIsRefused() {
        assertThatThrownBy(() -> ShSiteLocales.of("en", List.of("en", "pt", "pt-br"),
                Map.of("pt-br", "pt", "pt", "pt-br"), KNOWN))
                .as("SH1056: chain() stops a loop rather than reporting one, so refusing the data "
                        + "here is what keeps that guard a belt instead of the only thing holding "
                        + "the page up")
                .isInstanceOf(ShSiteLocales.FallbackLoopException.class);
        assertThatThrownBy(() -> ShSiteLocales.of("en", List.of("en"),
                Map.of("en", "en"), KNOWN))
                .isInstanceOf(ShSiteLocales.FallbackLoopException.class);
    }

    // --- the chain ---------------------------------------------------------------------------

    @Test
    @DisplayName("a fallback chain is walked in order and ends at the default")
    void theChainIsWhatTheMapIsFor() {
        ShSiteLocales locales = ShSiteLocales.of("en", List.of("en", "pt", "pt-br"),
                Map.of("pt-br", "pt", "pt", "en"), KNOWN);

        assertThat(locales.chain("pt-br"))
                .as("SH1056: a missing Brazilian page should show the Portuguese one before the "
                        + "English one, which a flat fall-back-to-the-default cannot say")
                .containsExactly("pt-br", "pt", "en");
        assertThat(locales.chain("es"))
                .as("an unlisted locale still ends at the default")
                .containsExactly("es", "en");
        assertThat(locales.chain(null)).containsExactly("en");
    }

    // --- the stored bytes ---------------------------------------------------------------------

    @Test
    @DisplayName("the same policy serializes to the same bytes, whatever order it arrived in")
    void theStoredFormIsStable() {
        String first = ShSiteLocales.of("en", List.of("en", "pt-br"),
                new java.util.LinkedHashMap<>(Map.of("pt-br", "en")), KNOWN).toJson(objectMapper);
        String second = ShSiteLocales.of("en", List.of("en", "pt-br"),
                Map.of("pt-br", "en"), KNOWN).toJson(objectMapper);

        assertThat(first)
                .as("SH1056: P5 — a projection of a policy nobody changed must not show a diff")
                .isEqualTo(second)
                .isEqualTo("{\"default\":\"en\",\"enabled\":[\"en\",\"pt-br\"],"
                        + "\"fallback\":{\"pt-br\":\"en\"}}");
    }

    @Test
    @DisplayName("what was stored is what is read back")
    void itRoundTrips() {
        ShSiteLocales written = ShSiteLocales.of("pt-br", List.of("pt-br", "pt", "en"),
                Map.of("pt-br", "pt", "pt", "en"), KNOWN);

        ShSiteLocales read = ShSiteLocales.parse(written.toJson(objectMapper), objectMapper)
                .orElseThrow();

        assertThat(read.defaultLocale()).isEqualTo("pt-br");
        assertThat(read.enabled()).containsExactly("pt-br", "pt", "en");
        assertThat(read.fallback()).containsExactly(Map.entry("pt", "en"),
                Map.entry("pt-br", "pt"));
        assertThat(read.declared()).isTrue();
    }

    @Test
    @DisplayName("a column holding nothing is a site with no policy, not a site with an empty one")
    void nothingStoredIsNoPolicy() {
        assertThat(ShSiteLocales.parse(null, objectMapper)).isEmpty();
        assertThat(ShSiteLocales.parse("   ", objectMapper)).isEmpty();
        assertThat(ShSiteLocales.parse("{}", objectMapper))
                .as("SH1056: a document with no enabled set cannot say which locales a site "
                        + "offers, so it is read as no answer rather than as none offered")
                .isEmpty();
    }

    @Test
    @DisplayName("a tag the catalogue has since lost still reads back, because the write judged it")
    void readingIsNotValidating() {
        assertThat(ShSiteLocales.parse("{\"default\":\"kl\",\"enabled\":[\"kl\"]}", objectMapper))
                .as("SH1056: a locale removed from the catalogue must not make the site "
                        + "unreadable — the write side is where a policy is judged")
                .isPresent();
    }

    @Test
    @DisplayName("a stored default outside its own enabled set falls back rather than lying")
    void aStoredDefaultIsCheckedAgainstItsOwnList() {
        assertThat(ShSiteLocales.parse("{\"default\":\"fr\",\"enabled\":[\"en\"]}", objectMapper)
                .orElseThrow().defaultLocale())
                .isEqualTo("en");
    }

    @Test
    @DisplayName("SH1281: labels round-trip, and only a policy that has one writes the key")
    void labelsRoundTripAndCostNothingUnset() {
        ShSiteLocales written = ShSiteLocales.of("en", List.of("en", "pt-br"), Map.of(),
                Map.of("pt-br", " Português (Brasil) "), KNOWN);

        ShSiteLocales read = ShSiteLocales.parse(written.toJson(objectMapper), objectMapper)
                .orElseThrow();
        assertThat(read.labelOf("pt-br"))
                .as("SH1281: the switcher's label survives the store, trimmed")
                .isEqualTo("Português (Brasil)");
        assertThat(read.labelOf("en")).isNull();
        assertThat(ShSiteLocales.of("en", List.of("en"), Map.of(), KNOWN).toJson(objectMapper))
                .as("SH1281: a policy without labels keeps the bytes it had before they existed (P5)")
                .doesNotContain("labels");
    }

    @Test
    @DisplayName("SH1281: a label for a locale the site does not offer is refused by name")
    void aLabelOutsideEnabledIsRefused() {
        assertThatThrownBy(() -> ShSiteLocales.of("en", List.of("en", "pt-br"), Map.of(),
                Map.of("pt-pt", "Português (Portugal)"), KNOWN))
                .isInstanceOf(ShSiteLocales.LabelNotEnabledException.class)
                .satisfies(refused -> assertThat(((ShSiteLocales.LabelNotEnabledException) refused)
                        .toProblemDetail().getTitle())
                        .as("SH1281: the refusal names the locale it could not label (P6)")
                        .contains("pt-pt"));
    }

    @Test
    @DisplayName("offers() is what a switcher and a route check ask")
    void offersIsTheMembershipQuestion() {
        ShSiteLocales locales = ShSiteLocales.of("en", List.of("en", "pt-br"), Map.of(), KNOWN);

        assertThat(locales.offers("pt-br")).isTrue();
        assertThat(locales.offers("fr")).isFalse();
        assertThat(locales.offers(null)).isFalse();
    }
}
