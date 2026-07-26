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
                .containsEntry("allowed", List.of("site", "folder", "post", "id"))
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
