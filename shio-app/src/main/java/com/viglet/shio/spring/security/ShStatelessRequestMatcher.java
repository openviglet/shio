/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.spring.security;

import org.springframework.security.web.util.matcher.RequestMatcher;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Matches a request on {@code delegate}'s path <em>only when the caller holds no
 * session cookie</em> — a CSRF exemption narrow enough to share a path between a
 * CLI and a browser (SH117).
 *
 * <p>A blanket exemption is right for a surface only stateless clients reach: the
 * CDA and the agent namespace authenticate with a token or HTTP Basic and hold no
 * session, so there is no cookie for an attacker's cross-site form to ride and
 * nothing for {@code CsrfFilter} to validate. {@code /api/v2/post-type} stopped
 * being such a surface when SH117 retired the id-keyed console twin and moved the
 * React editor onto it: the same path is now reached both by the {@code shio} CLI
 * (stateless, must not need a token it cannot get) and by a logged-in browser
 * (cookie-bearing, and therefore forgeable). Exempting it wholesale would have
 * made "unify the two APIs" quietly mean "drop CSRF protection on the content
 * model".</p>
 *
 * <p>The session cookie is the right discriminator because it is exactly the
 * credential a cross-site request can replay: a browser attaches it to a forged
 * POST automatically, while the CLI never has one. It is also readable
 * <em>before</em> authentication, which matters — {@code CsrfFilter} runs ahead of
 * the authentication filters, so the principal is not available to consult and a
 * matcher that tried to would always see an anonymous request.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public class ShStatelessRequestMatcher implements RequestMatcher {

    private final RequestMatcher delegate;
    private final String sessionCookieName;

    public ShStatelessRequestMatcher(RequestMatcher delegate, String sessionCookieName) {
        this.delegate = delegate;
        this.sessionCookieName = sessionCookieName;
    }

    @Override
    public boolean matches(HttpServletRequest request) {
        return delegate.matches(request) && !hasSessionCookie(request);
    }

    /**
     * Whether the request carries a session cookie. Deliberately the cookie and
     * not {@code getSession(false)}: a filter earlier in the chain may already have
     * created a session for a stateless caller (the policy is {@code IF_REQUIRED}),
     * and treating that as "browser" would break the CLI on its own first request.
     */
    private boolean hasSessionCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie cookie : cookies) {
            if (sessionCookieName.equalsIgnoreCase(cookie.getName())) {
                return true;
            }
        }
        return false;
    }
}
