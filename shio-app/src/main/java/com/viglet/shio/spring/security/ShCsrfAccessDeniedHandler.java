/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.spring.security;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.viglet.shio.api.error.ShProblem;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import tools.jackson.databind.ObjectMapper;

/**
 * A CSRF refusal that says what to do about it (SH339).
 *
 * <h2>What it replaces, and why that mattered</h2>
 *
 * <p>{@code CsrfFilter}'s default handler calls {@code sendError(403)}, and measured on this
 * application that produces <b>403 with no content type and an empty body</b> — for
 * {@code /api/v2/render/post-types}, {@code /api/v2/site}, {@code /api/v2/site-webhook} and
 * {@code /api/v2/preview-token} alike. That is SH189's bodiless 400 on the one surface where the
 * reader is most likely to be a program: a stateless agent doing exactly what the manifest suggested,
 * getting no status text, no reason and no alternative.</p>
 *
 * <p>It is also what made SH327 <em>expensive</em>. The capability was there, the credential was
 * valid, and diagnosing "why can I not create the render model" meant discovering the CSRF dance by
 * experiment rather than reading it out of a response. Design law P6 says errors are instructions;
 * this was the largest remaining 4xx in the product that carried none.</p>
 *
 * <h2>Three rules</h2>
 *
 * <p><b>The status does not change.</b> It stays 403, because the console's axios interceptor
 * re-fetches the token and retries on exactly that — a teaching body is additive, and changing the
 * status to make it more expressive would break the client that handles it correctly today.</p>
 *
 * <p><b>It names the stateless route.</b> The fix a browser needs (fetch {@code GET /api/v2/csrf},
 * send {@code X-XSRF-TOKEN}) is not the fix an agent needs, and an agent is the caller that most
 * often arrives here — so the body says both, and points at {@code POST /api/v2/agent/batch}, which
 * is CSRF-exempt for callers holding no session (SH167's narrowed matcher). Which paths have no such
 * equivalent is the SH339 inventory in {@code ShStatelessReachabilityLintTest}.</p>
 *
 * <p><b>It is wired onto {@code CsrfFilter} only</b>, through an {@code ObjectPostProcessor}, and not
 * as the chain-wide {@code accessDeniedHandler}. A CSRF rejection and an authorization denial are
 * different failures with different remedies, and one handler for both would have to guess which
 * message it is sending — {@code CsrfFilter} runs before authentication, so it is the one place where
 * "you need a token in a header" is unambiguously the right thing to say.</p>
 *
 * @author Alexandre Oliveira
 */
@Component
public class ShCsrfAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public ShCsrfAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        ProblemDetail problem = problemFor(request).toProblemDetail();

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body(problem)));
    }

    /**
     * Two callers arrive here and they need opposite advice.
     *
     * <p>A <b>browser</b> has a session and forgot the token: it fetches one. A <b>stateless</b>
     * caller has no session, so it cannot obtain a token at all — for it, this 403 means the path is
     * not one of SH167's narrowed exemptions, which is a fact about the route rather than about the
     * request. Telling that caller to fetch a CSRF token would be advice it cannot follow, and
     * telling the browser to drop its cookie would be worse. So the branch is on the cookie, and the
     * distinction is the whole reason this handler is worth more than a generic body.</p>
     */
    private static ShProblem problemFor(HttpServletRequest request) {
        String path = request.getRequestURI() == null ? "this path" : request.getRequestURI();
        if (hasSessionCookie(request)) {
            return ShProblem.of("csrf-token-required", HttpStatus.FORBIDDEN,
                            "This request carries a session cookie and no CSRF token")
                    .withDetail("A request with a session cookie must also send the CSRF token, or "
                            + "a cross-site form could act as the signed-in user. " + path + " is "
                            + "not exempt, and the exemptions that exist apply only to callers "
                            + "holding no session at all (SH167).")
                    .withFix("GET /api/v2/csrf, then send its value as the X-XSRF-TOKEN header "
                            + "alongside the XSRF-TOKEN cookie. A stateless caller should send no "
                            + "session cookie instead and authenticate with HTTP Basic or a Key "
                            + "header.")
                    .withExample("GET /api/v2/csrf");
        }
        return ShProblem.of("csrf-token-unobtainable", HttpStatus.FORBIDDEN,
                        "A stateless caller cannot write to " + path)
                .withDetail("This request holds no session, so it cannot obtain a CSRF token — and "
                        + path + " is not one of the paths exempted for session-less callers "
                        + "(SH167). That is a fact about the route, not about this request: no "
                        + "credential makes it writable without a browser session.")
                .withFix("Use the agent surface, which is exempt for callers holding no session: "
                        + "POST /api/v2/agent/batch takes site.upsert, folder.upsert, post.upsert, "
                        + "post.move and post.publish by address. GET /api/v2/agent/manifest lists "
                        + "what this instance exposes. If this path has no agent equivalent it is a "
                        + "known gap — see the SH339 inventory.")
                .withExample("POST /api/v2/agent/batch");
    }

    private static boolean hasSessionCookie(HttpServletRequest request) {
        // Any JSESSIONID-shaped cookie: the exact name is configurable, and this only decides which
        // of two messages to send, so a broad match is right — the *authorization* decision was
        // already made by ShStatelessRequestMatcher, which reads the configured name.
        if (request.getCookies() == null) {
            return false;
        }
        for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
            if (cookie.getName() != null && cookie.getName().toUpperCase().contains("SESSION")) {
                return true;
            }
        }
        return false;
    }

    /**
     * The problem as a stable, ordered map.
     *
     * <p>Built explicitly rather than serializing {@link ProblemDetail} directly: the order is
     * observable in the bytes, so it is ours to fix rather than a Jackson introspection detail
     * (P5, and SH136's rule that an order which reaches the wire is a decision).</p>
     */
    /**
     * Delegated since SH622, which gave the chain two more refusals that write their own bytes.
     * Three copies of "which fields, in which order" is three answers to one question.
     */
    private static Map<String, Object> body(ProblemDetail problem) {
        return ShProblemJson.body(problem);
    }
}
