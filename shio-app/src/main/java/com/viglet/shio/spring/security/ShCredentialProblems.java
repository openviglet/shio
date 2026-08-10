/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.spring.security;

import org.springframework.http.HttpStatus;

import com.viglet.shio.api.error.ShProblem;

import jakarta.servlet.http.HttpServletRequest;

/**
 * What a rejected credential should have said (SH622).
 *
 * <h2>P6 is a controller-advice property, and the first error is not thrown by a controller</h2>
 *
 * <p>SH499's first execution run found {@code GET /api/v2/cda/site} and
 * {@code /api/v2/cda/post/{id}/editable} answering <b>403 with Spring's own
 * {@code {timestamp, status, error, path}}</b> to console HTTP Basic, and 401 with the same shape to
 * anonymous. The cause is structural rather than an omission on those two endpoints: the teaching
 * errors live in {@code @ControllerAdvice}, and a rejected credential <em>never reaches a
 * controller</em> — the filter chain answers first. So <b>every</b> endpoint on this product had one
 * 4xx that carried no {@code fix}, and it is the one a caller hits before any other.</p>
 *
 * <h2>The field that was missing, not the prose</h2>
 *
 * <p>A caller reading {@code 403} cannot tell "your credential is real but too narrow" from "this
 * endpoint is not for this kind of credential at all", and those need opposite next moves: widen a
 * scope, or stop using a token here and sign in. The instance knows which — the rules are the same
 * ones {@code ShSecurityConfig} matches on — so the answer existed and was simply not where the
 * error was.</p>
 *
 * <h2>Derived from the path, and deliberately not from the manifest</h2>
 *
 * <p>The manifest also knows, and reaching it from the security chain would mean the entry point
 * depending on a service the chain runs before. The prefixes below <em>are</em> the chain's own
 * rules, restated where the refusal is written; a mismatch between them is a mismatch between two
 * lines of the same config, which is much smaller than a cycle between two of them.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
final class ShCredentialProblems {

    private ShCredentialProblems() {
    }

    /** Which credential a path wants, in the words a caller needs to act. */
    private record Credential(String what, String how) {
    }

    private static Credential credentialFor(String path) {
        String uri = path == null ? "" : path;
        if (uri.startsWith("/api/v2/cda/form")) {
            return new Credential("a FORM-scope API token for this site",
                    "Issue one on /api/v2/api-token with scope FORM and the site named, then send "
                            + "it as the `Key` header. It may post only here (SH631).");
        }
        if (uri.startsWith("/api/v2/agent") || uri.startsWith("/mcp")) {
            return new Credential("an AGENT-scope API token",
                    "Send it as the `Key` header. Mint one on /api/v2/api-token with scope AGENT — "
                            + "a READ or PREVIEW token reaches this surface for GETs only, and a "
                            + "console session does not reach it at all.");
        }
        if (uri.startsWith("/api/v2/cda") || uri.startsWith("/graphql")) {
            return new Credential("a CDA API token",
                    "Send it as the `Key` header. A GET needs any scope; a PATCH or POST needs "
                            + "WRITE. Mint one on /api/v2/api-token.");
        }
        if (uri.startsWith("/preview/")) {
            return new Credential("a console session, or a preview token",
                    "Sign in to the console, or mint a link on /api/v2/preview-token and use the "
                            + "URL it returns.");
        }
        return new Credential("a console session",
                "Sign in to the console. This is not a token surface: an API token authenticates "
                        + "only /api/v2/cda/**, /graphql, /api/v2/agent/** and /mcp (SH2).");
    }

    /** No credential at all, or one this instance does not recognise. */
    static ShProblem unauthenticated(HttpServletRequest request) {
        String path = pathOf(request);
        Credential credential = credentialFor(path);
        return ShProblem.of("unauthenticated", HttpStatus.UNAUTHORIZED,
                        "No usable credential for " + path)
                .withDetail("This request carried no credential this instance accepts here, so it "
                        + "was refused before reaching any handler. " + path + " wants "
                        + credential.what() + ".")
                .withFix(credential.how());
    }

    /**
     * A credential that authenticated and is not enough.
     *
     * <p>The distinction from the 401 is the whole point: the caller <em>has</em> something, so the
     * remedy is a different scope rather than a different login, and telling them to sign in when
     * they already did is the advice that sends an agent round the loop it was already in.</p>
     */
    static ShProblem forbidden(HttpServletRequest request) {
        String path = pathOf(request);
        Credential credential = credentialFor(path);
        return ShProblem.of("insufficient-credential", HttpStatus.FORBIDDEN,
                        "This credential does not reach " + path)
                .withDetail("The credential was accepted and does not carry what this path needs — "
                        + "which is a different failure from not being signed in, and needs a "
                        + "different move. " + path + " wants " + credential.what() + ".")
                .withFix(credential.how());
    }

    private static String pathOf(HttpServletRequest request) {
        String uri = request == null ? null : request.getRequestURI();
        return uri == null || uri.isBlank() ? "this path" : uri;
    }
}
