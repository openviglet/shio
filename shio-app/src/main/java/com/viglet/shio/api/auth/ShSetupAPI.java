/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.api.auth;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/**
 * First-access setup API. Allows setting the admin password when it has not been configured yet.
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@RestController
@RequestMapping("/api/v2/setup")
@Tag(name = "Setup", description = "First-access admin setup")
@Slf4j
public class ShSetupAPI {

    /**
     * SH706 — the write lives in {@link ShAdminPasswordService}, and this holds only the HTTP
     * contract. The moment the screen and the headless bootstrap encode a password through two
     * code paths, they eventually disagree about the minimum length or the encoder; SH245 is the
     * open task about exactly that shape, filed after the console's copy of a shared verb turned
     * out to be the careless one four times.
     */
    private final ShAdminPasswordService shAdminPasswordService;

    public ShSetupAPI(ShAdminPasswordService shAdminPasswordService) {
        this.shAdminPasswordService = shAdminPasswordService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of("required", shAdminPasswordService.isSetupRequired()));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> setup(@RequestBody Map<String, String> body) {
        if (!shAdminPasswordService.isSetupRequired()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Setup already completed."));
        }

        try {
            shAdminPasswordService.setPassword(body.get("password"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }

        log.info("Admin password set via first-access setup.");
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
