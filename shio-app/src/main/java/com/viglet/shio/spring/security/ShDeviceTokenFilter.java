/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.spring.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.viglet.shio.persistence.model.token.ShDeviceToken;
import com.viglet.shio.persistence.repository.auth.ShUserRepository;
import com.viglet.shio.persistence.repository.token.ShDeviceTokenRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * SH1351 — authenticates {@code Authorization: Bearer shd_…} as the <b>person</b> who minted it.
 *
 * <h2>Exactly what a Basic login is, without the password on the device</h2>
 *
 * <p>The authorities are resolved on every call through the same {@link UserDetailsService} the
 * Basic login uses, so the device reaches every path that login reaches (the review queue included)
 * and nothing else: no scope, no allow-list, no {@code ROLE_AGENT}. The tenant is then resolved from
 * the principal by {@code ShTenantFilter}, as it is for Basic. No session is created, so the CSRF
 * exemptions that let Basic write without a cookie apply to this caller on the same paths.</p>
 *
 * <h2>A presented token that cannot authenticate answers 401 here</h2>
 *
 * <p>{@code ShApiTokenFilter} leaves the context alone on a bad key and lets authorization decide.
 * This one refuses, because the {@code shd_} prefix states the caller's intent unambiguously and the
 * one thing a native client must learn at once is that its device was revoked: a revoked token falling
 * through to a {@code permitAll} route would answer 200 and the app would not find out.</p>
 *
 * <p>The two token stores are disjoint by construction: a machine token is not a {@code shd_} value
 * and is never looked up here, so an agent's key cannot be presented as a person.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
@Slf4j
public class ShDeviceTokenFilter extends OncePerRequestFilter {

    /** The prefix that marks a person-bound device secret. */
    public static final String PREFIX = "shd_";

    private static final String BEARER = "Bearer ";

    /** {@code last_used_at} is written at most this often per device, so a call is not a write. */
    private static final Duration TOUCH_INTERVAL = Duration.ofMinutes(1);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ShDeviceTokenRepository repository;
    private final UserDetailsService userDetailsService;
    private final ShUserRepository userRepository;
    private final Clock clock;

    public ShDeviceTokenFilter(ShDeviceTokenRepository repository, UserDetailsService userDetailsService,
            ShUserRepository userRepository, Clock clock) {
        this.repository = repository;
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /** A fresh secret: the prefix plus 32 random bytes, URL-safe. */
    public static String newSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Hex SHA-256 — what is stored and looked up. */
    public static String hash(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every JVM", e);
        }
    }

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response,
            @NotNull FilterChain filterChain) throws ServletException, IOException {
        String secret = presented(request);
        if (secret == null) {
            filterChain.doFilter(request, response);
            return;
        }
        ShDeviceToken device = repository.findByTokenHash(hash(secret)).orElse(null);
        if (device == null || device.isRevoked()) {
            refuse(response, device == null
                    ? "This device token is not known to this instance."
                    : "This device token was revoked.");
            return;
        }
        // Asked first rather than caught: loadUserByUsername throws for an absent user, and a person
        // deleted since the device was minted is an answer here, not an exception.
        if (userRepository.findByUsername(device.getUsername()) == null) {
            refuse(response, "The person this device token belongs to no longer exists.");
            return;
        }
        UserDetails person = userDetailsService.loadUserByUsername(device.getUsername());
        if (!person.isEnabled() || !person.isAccountNonLocked()) {
            refuse(response, "The person this device token belongs to is disabled.");
            return;
        }

        var authentication = new UsernamePasswordAuthenticationToken(person, null, person.getAuthorities());
        authentication.setDetails(new ShDeviceTokenContext(device.getId()));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        touch(device);

        filterChain.doFilter(request, response);
    }

    private void touch(ShDeviceToken device) {
        Instant now = Instant.now(clock);
        if (device.getLastUsedAt() == null || device.getLastUsedAt().plus(TOUCH_INTERVAL).isBefore(now)) {
            device.setLastUsedAt(now);
            repository.save(device);
        }
    }

    /** The secret, when the caller presented a device bearer; anything else is not ours. */
    private static String presented(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            return null;
        }
        String value = header.substring(BEARER.length()).trim();
        return value.startsWith(PREFIX) ? value : null;
    }

    private static void refuse(HttpServletResponse response, String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, detail);
        problem.setTitle("Device token not accepted");
        problem.setProperty("fix", "Sign in again and mint a new device token with POST /api/v2/device-token.");
        problem.setProperty("example", "POST /api/v2/device-token {\"deviceName\":\"My phone\"} "
                + "with HTTP Basic, then send Authorization: Bearer shd_...");
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"Shio\", error=\"invalid_token\"");
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(ShProblemJson.of(problem));
    }

    /** Rides on the authentication's details so a controller can tell a device from a password login. */
    public record ShDeviceTokenContext(String deviceTokenId) {
    }
}
