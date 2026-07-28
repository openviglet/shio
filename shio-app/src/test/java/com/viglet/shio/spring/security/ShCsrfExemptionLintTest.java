/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.spring.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SH167 — <b>a blanket CSRF exemption is a decision, not a default.</b>
 *
 * <h2>Why a lint and not a functional test</h2>
 *
 * <p>SH117's lesson was that an exemption stops being safe when a <em>new</em> caller arrives
 * on the path, and that nothing in the arriving feature notices: {@code /api/v2/post-type} was
 * exempt on the true premise that only the stateless {@code shio} CLI reached it, right up to
 * the commit that pointed the React editor at the same URL. Both halves of that failure are
 * silent — the browser works, the CLI works, and the only thing that changed is that a
 * cross-site form can now rewrite the content model.</p>
 *
 * <p>So the guard has to fire on the <em>shape of the configuration</em> rather than on any
 * one request. A functional test proves the paths that exist today behave; this proves that
 * the next path added to {@code ignoringRequestMatchers} was added on purpose, by someone who
 * had to come here and say which kind of exemption it is and why.</p>
 *
 * <h2>The rule</h2>
 *
 * <p>Every entry in the exemption list is either wrapped in {@link ShStatelessRequestMatcher}
 * — exempt only for callers holding no session cookie — or named in {@link #BLANKET_BY_DESIGN}
 * below with the reason it may stay blanket. There is no third option, and the allow-list is
 * short on purpose: a path qualifies only when a forged request could not do anything an
 * unauthenticated one could not already do.</p>
 *
 * @author Alexandre Oliveira
 */
class ShCsrfExemptionLintTest {

    private static final Path CONFIG = Path.of(
            "src", "main", "java", "com", "viglet", "shio", "spring", "security", "ShSecurityConfig.java");

    /**
     * The paths that may stay exempt for <em>every</em> caller, and why.
     *
     * <p>Each is either unauthenticated (so a forgery gains the attacker nothing they could not
     * do from their own browser) or incapable of writing. Adding to this list is allowed and
     * must be argued here — the argument is the point of the list.</p>
     *
     * <ul>
     *   <li>{@code GRAPHQL_PATH} — SH4 delivery. The generated schema has no {@code Mutation},
     *       so a forged POST cannot write, and the GraphiQL console at {@code /graphiql/**} is a
     *       cookie-bearing browser that the narrowed matcher would break for no gain. The
     *       premise is "no mutation exists", which is exactly the kind of premise SH117 watched
     *       expire, so it is written down rather than assumed.</li>
     *   <li>{@code SETUP_PATH} — SH6 first-run setup, {@code permitAll} and open only until a
     *       tenant exists; there is no session to ride.</li>
     *   <li>{@code ERROR_PATH} — the error dispatch, not a caller-reachable write.</li>
     *   <li>{@code LOGIN_PATH} / {@code LOGOUT_PATH} — pre- and post-session by definition. A
     *       CSRF token on login is protection against forced login, which this product does not
     *       currently take a position on.</li>
     *   <li>{@code BACK_CHANNEL_LOGOUT_PATH} — server-to-server; the signed logout token
     *       <em>is</em> the credential, and Keycloak sends no cookie.</li>
     * </ul>
     */
    private static final Set<String> BLANKET_BY_DESIGN = Set.of(
            "GRAPHQL_PATH",
            "SETUP_PATH",
            "ERROR_PATH",
            "LOGIN_PATH",
            "LOGOUT_PATH",
            "BACK_CHANNEL_LOGOUT_PATH");

    /**
     * The paths SH167 narrowed, kept here so the change cannot be quietly reverted.
     *
     * <p>{@code AGENT_PATH} is the one that motivated the task: the namespace is gated by
     * {@code anyRequest().authenticated()} and nothing more, so a logged-in curator's session
     * already reaches {@code POST /api/v2/agent/batch} — the endpoint that deletes content.</p>
     */
    private static final List<String> MUST_BE_NARROWED = List.of(
            "CDA_PATH", "POST_TYPE_PATH", "REVIEW_PATH", "AGENT_PATH", "MCP_PATH");

    @Test
    @DisplayName("SH167: every CSRF exemption is either session-narrowed or argued in the allow-list")
    void everyExemptionIsNarrowedOrArgued() throws IOException {
        final Set<String> blanket = blanketExemptions(exemptionBlock());

        assertThat(blanket)
                .as("""
                        SH167: these paths are CSRF-exempt for EVERY caller, including a browser \
                        carrying a console session cookie. That is only safe while no cookie-bearing \
                        client reaches the path — the premise that held for /api/v2/post-type until \
                        SH117 pointed the React editor at it, and nothing in that commit noticed.

                        Either wrap the matcher in ShStatelessRequestMatcher (the exemption then covers \
                        only callers holding no session, which is what the CLI and a token client are), \
                        or add the constant to BLANKET_BY_DESIGN in %s with the reason a forged request \
                        there can do nothing an unauthenticated one could not."""
                        .formatted(getClass().getSimpleName()))
                .isSubsetOf(BLANKET_BY_DESIGN);
    }

    @Test
    @DisplayName("SH167: the narrowed exemptions stay narrowed")
    void theNarrowedExemptionsStayNarrowed() throws IOException {
        final String block = exemptionBlock();
        for (final String constant : MUST_BE_NARROWED) {
            assertThat(block)
                    .as("%s must be exempt only for session-less callers (SH117/SH167). A bare "
                            + "mvc.matcher(%s) here makes it cross-site writable from a logged-in browser.",
                            constant, constant)
                    .contains("new ShStatelessRequestMatcher(mvc.matcher(%s), sessionCookieName)".formatted(constant));
        }
    }

    /**
     * The {@code ignoringRequestMatchers(...)} argument list, comments included.
     *
     * <p>Read from source rather than from a built {@code SecurityFilterChain} because Spring
     * Security exposes no way to enumerate a {@code CsrfConfigurer}'s ignored matchers, and a
     * reflective dig into its internals would be a lint on Spring's private shape instead of on
     * ours.</p>
     */
    private static String exemptionBlock() throws IOException {
        final String source = Files.readString(CONFIG);
        final int start = source.indexOf(".ignoringRequestMatchers(");
        assertThat(start)
                .as("%s no longer calls ignoringRequestMatchers — if the CSRF configuration moved, "
                        + "move this lint with it rather than deleting it", CONFIG)
                .isGreaterThanOrEqualTo(0);
        final int end = source.indexOf("))\n", start);
        return source.substring(start, end < 0 ? source.length() : end);
    }

    /** Every {@code mvc.matcher(CONSTANT)} in the block that is not wrapped by the narrowing matcher. */
    private static Set<String> blanketExemptions(String block) {
        final Set<String> blanket = new LinkedHashSet<>();
        final Matcher matcher = Pattern.compile("(new ShStatelessRequestMatcher\\()?mvc\\.matcher\\((\\w+)\\)")
                .matcher(stripComments(block));
        while (matcher.find()) {
            if (matcher.group(1) == null) {
                blanket.add(matcher.group(2));
            }
        }
        return blanket;
    }

    /** Comments here explain the exemptions and would otherwise be scanned as code. */
    private static String stripComments(String block) {
        return block.replaceAll("(?m)//.*$", "");
    }
}
