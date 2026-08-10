/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.spring.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * The chain-wide 403, with a body (SH622).
 *
 * <p>SH499's execution run found {@code GET /api/v2/cda/site} answering 403 to console HTTP Basic
 * with Spring's own {@code {timestamp, status, error, path}}. The cause was structural: P6 lives in
 * {@code @ControllerAdvice} and an authorization denial never reaches a controller, so every
 * endpoint had one 4xx with no {@code fix} — the one a caller meets before any other.</p>
 *
 * <p>Distinct from {@link ShCsrfAccessDeniedHandler}, which stays wired to {@code CsrfFilter} alone
 * for SH339's stated reason: a CSRF rejection and an authorization denial have different remedies,
 * and one handler for both would have to guess which it is sending. This is the second of those two
 * — the caller authenticated, and what it holds does not reach here.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(ShProblemJson.of(
                ShCredentialProblems.forbidden(request).toProblemDetail()));
    }
}
