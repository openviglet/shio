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

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import jakarta.servlet.http.Cookie;

/**
 * SH117 — the CSRF exemption on {@code /api/v2/post-type} must cover the stateless
 * {@code shio} CLI and <em>not</em> a logged-in browser, now that both reach the same
 * path.
 *
 * <p>Why this is worth its own test: the matcher is a security control whose failure
 * modes are both silent. Match too widely and the console's content model becomes
 * cross-site writable with nothing in the UI to show it; match too narrowly and the
 * CLI gets a 403 for a CSRF token it has no session to obtain. Neither shows up in a
 * functional test of either client, because MockMvc's {@code webAppContextSetup}
 * builds no security filter chain at all.</p>
 */
class ShStatelessRequestMatcherTest {

    private static final String SESSION_COOKIE = "JSESSIONID";

    private final RequestMatcher matcher = new ShStatelessRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher("/api/v2/post-type/**"),
            SESSION_COOKIE);

    @Test
    void aCookielessCallerOnThePathIsExempt() {
        assertThat(matcher.matches(request("/api/v2/post-type/Article"))).isTrue();
    }

    @Test
    void aSessionBearingCallerOnThePathIsNotExempt() {
        MockHttpServletRequest request = request("/api/v2/post-type/Article");
        request.setCookies(new Cookie(SESSION_COOKIE, "abc123"));
        assertThat(matcher.matches(request)).isFalse();
    }

    @Test
    void anUnrelatedCookieDoesNotCount() {
        MockHttpServletRequest request = request("/api/v2/post-type/Article");
        request.setCookies(new Cookie("XSRF-TOKEN", "abc123"), new Cookie("theme", "dark"));
        assertThat(matcher.matches(request)).isTrue();
    }

    /** Cookie names are case-insensitive in practice; a `jsessionid` must still count. */
    @Test
    void theSessionCookieNameIsMatchedCaseInsensitively() {
        MockHttpServletRequest request = request("/api/v2/post-type/Article");
        request.setCookies(new Cookie("jsessionid", "abc123"));
        assertThat(matcher.matches(request)).isFalse();
    }

    @Test
    void anotherPathIsNeverExemptEvenWithoutACookie() {
        assertThat(matcher.matches(request("/api/v2/post-unified"))).isFalse();
    }

    private static MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", uri);
        request.setRequestURI(uri);
        return request;
    }
}
