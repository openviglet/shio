/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.shio.api.agent.ShAddress.Kind;

/**
 * SH79 — the address grammar. Network-free.
 *
 * @author Alexandre Oliveira
 */
class ShAddressTest {

    @Test
    void parsesEachScheme() {
        assertThat(ShAddress.parse("site:Blog")).isEqualTo(new ShAddress(Kind.SITE, "Blog", null));
        assertThat(ShAddress.parse("folder:Blog/2026/june"))
                .isEqualTo(new ShAddress(Kind.FOLDER, "Blog", "/2026/june"));
        assertThat(ShAddress.parse("post:Blog/blog/hello-world"))
                .isEqualTo(new ShAddress(Kind.POST, "Blog", "/blog/hello-world"));
        assertThat(ShAddress.parse("id:9f3c-abcd")).isEqualTo(new ShAddress(Kind.ID, "9f3c-abcd", null));
    }

    /**
     * SH433 — the home page is the one address nothing spelled.
     *
     * <p>{@code verify --checks replication} reports {@code source-page-missing} for {@code /} on
     * every replication, and its fix said "give the replica a page at that URL". Neither that URL nor
     * anything else written down said how: {@code folder:OpenViglet} is refused because a folder needs
     * a path, {@code agents.md} gave {@code post:<site>/<friendly-url>}, and an empty friendly URL
     * reads as the same malformed case. The answer — a post whose {@code furl} is {@code /} — cost
     * several probes against a live instance to find, which is the session P2 exists to remove.
     *
     * <p>It resolves and always did. This pins the two spellings a caller reaches for and, below, the
     * refusal that now names the form instead of leaving it to be guessed.
     */
    @Test
    void theHomePageIsAddressable() {
        assertThat(ShAddress.parse("post:Blog/").path())
                .as("the form the conventions, AGENTS.md and the teaching errors now all name")
                .isEqualTo("/");
        assertThat(ShAddress.parse("post:Blog//").path())
                .as("""
                        and the double slash a caller finds by probing normalizes to the same thing,                         so the one that was discovered the hard way keeps working""")
                .isEqualTo("/");
        assertThat(ShAddress.post("Blog", "/").path()).isEqualTo("/");
        assertThat(ShAddress.parse("post:Blog/").toString())
                .as("and it round-trips, which is what makes it usable as a key")
                .isEqualTo("post:Blog/");
    }

    @Test
    void aPostAddressWithNoPathNamesTheHomePageForm() {
        // The refusal a caller actually hits: `post:<site>` with the path left off is exactly how
        // somebody reaches for the root. P6 — the message is the documentation that gets read.
        assertThatThrownBy(() -> ShAddress.parse("post:Blog"))
                .isInstanceOf(ShAddress.MalformedAddressException.class)
                .hasMessageContaining("post:Blog/")
                .hasMessageContaining("friendly URL");

        // A folder has no root page and is not offered one: a folder address is a chain of names, and
        // suggesting `folder:Blog/` would send a caller at something that does not exist.
        assertThatThrownBy(() -> ShAddress.parse("folder:Blog"))
                .isInstanceOf(ShAddress.MalformedAddressException.class)
                .hasMessageNotContaining("friendly URL");
    }

    @Test
    void schemeIsCaseInsensitiveAndWhitespaceTolerant() {
        assertThat(ShAddress.parse("  POST:Blog/hello  ").kind()).isEqualTo(Kind.POST);
    }

    @Test
    void toleratesSpacesInASiteName() {
        // Real case: SH72 provisions tenant copies as "Blog (acme)".
        ShAddress address = ShAddress.parse("post:Blog (acme)/blog/hello");

        assertThat(address.site()).isEqualTo("Blog (acme)");
        assertThat(address.path()).isEqualTo("/blog/hello");
    }

    @Test
    void normalizesPathsSoEquivalentSpellingsAreOneAddress() {
        assertThat(ShAddress.parse("post:Blog/blog/hello").path()).isEqualTo("/blog/hello");
        assertThat(ShAddress.parse("post:Blog//blog//hello").path()).isEqualTo("/blog/hello");
        // A trailing slash must not create a second address for the same thing, or the
        // context pack's digest would depend on how a path was typed.
        assertThat(ShAddress.parse("folder:Blog/blog/").path()).isEqualTo("/blog");
        assertThat(ShAddress.folder("Blog", "blog").path()).isEqualTo("/blog");
    }

    @Test
    void canonicalFormRoundTrips() {
        List<String> addresses = List.of(
                "site:Blog", "folder:Blog/blog", "post:Blog/blog/hello-world", "id:9f3c-abcd");

        assertThat(addresses).allSatisfy(raw ->
                assertThat(ShAddress.parse(raw)).hasToString(raw));
    }

    @Test
    void siteRootIsDistinguishedFromALenientPath() {
        assertThat(ShAddress.parse("site:Blog").isSiteRoot()).isTrue();
        assertThat(ShAddress.parse("site:Blog/blog/hello").isSiteRoot()).isFalse();
    }

    @Test
    void malformedAddressesTeachTheGrammar() {
        assertThat(problemOf("Blog/blog")).contains("scheme");
        assertThat(problemOf("nope:Blog/blog")).contains("not an address scheme");
        // post:/folder: are meaningless without a path, and saying so beats resolving
        // to something surprising.
        assertThat(problemOf("post:Blog")).contains("needs a path");
        assertThat(problemOf("folder:Blog")).contains("needs a path");
        assertThat(problemOf("post:/blog/hello")).contains("site part is empty");
        assertThat(problemOf("")).contains("required");
    }

    @Test
    void everyMalformedAddressCarriesTheAllowedSchemesAndAnExample() {
        var problem = catchProblem("nope:Blog").toProblemDetail();

        assertThat(problem.getProperties())
                .containsEntry("allowed", List.of("site", "folder", "post", "id", "release"))
                .containsEntry("example", "post:Blog/blog/hello-world");
    }

    private static String problemOf(String raw) {
        return catchProblem(raw).toProblemDetail().getDetail();
    }

    private static ShAddress.MalformedAddressException catchProblem(String raw) {
        return (ShAddress.MalformedAddressException) assertThatThrownBy(() -> ShAddress.parse(raw))
                .isInstanceOf(ShAddress.MalformedAddressException.class)
                .actual();
    }
}
