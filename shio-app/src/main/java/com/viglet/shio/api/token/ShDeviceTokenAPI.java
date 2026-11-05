/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.api.token;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.persistence.model.token.ShDeviceToken;
import com.viglet.shio.persistence.repository.token.ShDeviceTokenRepository;
import com.viglet.shio.spring.security.ShDeviceTokenFilter;
import com.viglet.shio.spring.security.ShDeviceTokenFilter.ShDeviceTokenContext;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/**
 * SH1351 — mint, list and revoke the caller's own device tokens.
 *
 * <p>Minting needs a sign-in the instance already runs: HTTP Basic from the app's own sign-in screen
 * or a console session. It is refused to a device token, so a stolen device cannot extend itself, and
 * an API token never reaches this path at all: {@code ShApiTokenFilter} confines machine credentials
 * to the delivery and agent namespaces, which is what keeps an agent from minting a person.</p>
 *
 * <p>Stateless-writable for the same reason as the review queue: the app holds no session, and a
 * curator's browser holding one stays CSRF-checked.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@RestController
@RequestMapping("/api/v2/device-token")
@Tag(name = "Device Token", description = "Person-bound credentials for a native client (SH1351)")
@Slf4j
public class ShDeviceTokenAPI {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final ShDeviceTokenRepository repository;
    private final Clock clock;

    public ShDeviceTokenAPI(ShDeviceTokenRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** What a caller asks for. */
    public record MintRequest(String deviceName) {
    }

    /** The one answer that carries the secret; it is never shown again. */
    public record Minted(String id, String deviceName, String token, Instant createdDate) {
    }

    /** A device as the list shows it: never the secret. */
    public record Device(String id, String deviceName, Instant createdDate, Instant lastUsedAt,
            Instant revokedAt, String revokedBy, boolean current) {
    }

    @PostMapping
    @Operation(summary = "Mint a device token acting as the signed-in person; the secret is returned once")
    public ResponseEntity<Object> mint(@RequestBody(required = false) MintRequest body, Authentication authentication) {
        if (authentication.getDetails() instanceof ShDeviceTokenContext) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                    "A device token cannot mint another device token.");
            problem.setProperty("fix", "Sign in with your password (HTTP Basic) or from the console, "
                    + "then POST /api/v2/device-token.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
        }
        String name = body != null && body.deviceName() != null && !body.deviceName().isBlank()
                ? body.deviceName().strip() : null;
        if (name == null || name.length() > 100) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                    "deviceName is required and at most 100 characters.");
            problem.setProperty("fix", "Name the device so its owner can recognise it in the list.");
            problem.setProperty("example", "{\"deviceName\":\"Pixel 9\"}");
            return ResponseEntity.badRequest().body(problem);
        }
        String secret = ShDeviceTokenFilter.newSecret();
        ShDeviceToken device = new ShDeviceToken();
        device.setUsername(authentication.getName());
        device.setDeviceName(name);
        device.setTokenHash(ShDeviceTokenFilter.hash(secret));
        device.setCreatedDate(Instant.now(clock));
        repository.saveAndFlush(device);
        log.info("Minted device token '{}' for {}", name, device.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new Minted(device.getId(), name, secret, device.getCreatedDate()));
    }

    @GetMapping
    @Operation(summary = "The caller's own devices, newest first; never the secret")
    public List<Device> list(Authentication authentication) {
        String current = currentDevice(authentication);
        return repository.findByUsernameOrderByCreatedDateDesc(authentication.getName()).stream()
                .map(d -> new Device(d.getId(), d.getDeviceName(), d.getCreatedDate(), d.getLastUsedAt(),
                        d.getRevokedAt(), d.getRevokedBy(), d.getId().equals(current)))
                .toList();
    }

    /**
     * Revoke one device: its next call answers 401. The owner may revoke their own (a device may
     * revoke itself, which is the app's sign-out); an administrator may revoke anyone's.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Revoke a device token; its next call answers 401")
    public ResponseEntity<Object> revoke(@PathVariable String id, Authentication authentication) {
        ShDeviceToken device = repository.findById(id).orElse(null);
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(a -> ROLE_ADMIN.equals(a.getAuthority()));
        if (device == null || !(admin || device.getUsername().equals(authentication.getName()))) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                    "No device token with this id belongs to you.");
            problem.setProperty("fix", "GET /api/v2/device-token lists your devices and their ids.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
        }
        if (!device.isRevoked()) {
            device.setRevokedAt(Instant.now(clock));
            device.setRevokedBy(authentication.getName());
            repository.saveAndFlush(device);
            log.info("Revoked device token '{}' of {} by {}", device.getDeviceName(), device.getUsername(),
                    authentication.getName());
        }
        return ResponseEntity.ok(new Device(device.getId(), device.getDeviceName(), device.getCreatedDate(),
                device.getLastUsedAt(), device.getRevokedAt(), device.getRevokedBy(),
                device.getId().equals(currentDevice(authentication))));
    }

    private static String currentDevice(Authentication authentication) {
        return authentication.getDetails() instanceof ShDeviceTokenContext context ? context.deviceTokenId() : null;
    }
}
