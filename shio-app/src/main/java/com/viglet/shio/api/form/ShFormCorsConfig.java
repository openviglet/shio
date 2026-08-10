/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.form;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * The preflight the form endpoint needs, and nothing else (SH637).
 *
 * <h2>The endpoint was unreachable from the only caller it exists for</h2>
 *
 * <p>A form on {@code www.acme.com} posting to an instance answering somewhere else is
 * <b>cross-origin by construction</b>, so the browser sends a preflight first.
 * {@code ShSecurityConfig} called {@code http.cors(Customizer.withDefaults())} and there was no
 * {@link CorsConfigurationSource} bean anywhere in the application — so that call configured
 * nothing, every preflight failed, and SH633's endpoint was correct, tested and unreachable.</p>
 *
 * <h2>CORS is not the control, which is why this is permissive and the endpoint is not</h2>
 *
 * <p>CORS protects the <em>browser</em>, not the server: a non-browser client ignores it entirely,
 * so anything enforced only here is enforced only against callers who were going to behave anyway.
 * The control is {@code ShFormSubmissionAPI}'s check of the {@code Origin} header against the
 * <b>site's own</b> allow-list, which runs where the tenant is resolved and applies to every
 * caller.</p>
 *
 * <p>So the split is deliberate: this lets a legitimate page <em>through</em>, and the endpoint is
 * what stops an illegitimate one. Doing it the other way round — a per-site origin lookup here —
 * was tried first and is wrong: {@code ShTenantFilter} is registered after
 * {@code BasicAuthenticationFilter} while CORS runs at the head of the chain, so a {@code @TenantId}
 * read at preflight time would run with no tenant resolved and answer for the wrong one.</p>
 *
 * <h2>Scoped to this one path</h2>
 *
 * <p>{@link UrlBasedCorsConfigurationSource} returns {@code null} for a path it does not know, and
 * a null configuration means no CORS headers — exactly the behaviour every other endpoint has
 * today. Registering only {@code /api/v2/cda/form/**} therefore changes nothing anywhere else,
 * which matters because the console reaches the API same-origin through the dev proxy and has never
 * needed CORS at all.</p>
 *
 * <p>{@code allowCredentials} is <b>false</b>, and that is not a formality: the form endpoint
 * authenticates with a {@code Key} header rather than a cookie, so no credential should ride along
 * — and {@code true} beside a wildcard pattern is rejected by the spec anyway.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Configuration
public class ShFormCorsConfig {

    /** Where a rendered form posts (SH633) — the only path this opens. */
    private static final String FORM_PATH = "/api/v2/cda/form/**";

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration form = new CorsConfiguration();
        // A pattern rather than an origin list, because the origins are per site and live in
        // `sh_site_form` where the endpoint can read them under a resolved tenant. Narrowing here
        // would mean maintaining the same list twice, in two places that can disagree — and the
        // one that would win is the one that is not the control.
        form.setAllowedOriginPatterns(List.of("*"));
        form.setAllowedMethods(List.of("POST", "OPTIONS"));
        // The two the submission actually sends. Not `*`: a preflight that advertises every header
        // as acceptable tells a reader nothing about what the endpoint takes.
        form.setAllowedHeaders(List.of("Content-Type", "Key"));
        form.setAllowCredentials(false);
        // An hour. A preflight per submission would double the request count on a form somebody
        // fills in twice, and the answer does not change between them.
        form.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(FORM_PATH, form);
        return source;
    }
}
