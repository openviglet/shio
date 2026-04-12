/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */
package com.viglet.shio.api.auth;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import com.viglet.shio.persistence.model.auth.ShUser;
import com.viglet.shio.persistence.repository.auth.ShUserRepository;

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

    private static final String ADMIN = "admin";
    private static final int MIN_PASSWORD_LENGTH = 6;

    private final ShUserRepository shUserRepository;
    private final PasswordEncoder passwordEncoder;

    public ShSetupAPI(ShUserRepository shUserRepository, PasswordEncoder passwordEncoder) {
        this.shUserRepository = shUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of("required", isSetupRequired()));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> setup(@RequestBody Map<String, String> body) {
        if (!isSetupRequired()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Setup already completed."));
        }

        String password = body.get("password");
        if (!StringUtils.hasText(password) || password.length() < MIN_PASSWORD_LENGTH) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Password must be at least " + MIN_PASSWORD_LENGTH + " characters."));
        }

        ShUser admin = shUserRepository.findByUsername(ADMIN);
        admin.setPassword(passwordEncoder.encode(password));
        shUserRepository.saveAndFlush(admin);

        log.info("Admin password set via first-access setup.");
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    private boolean isSetupRequired() {
        ShUser admin = shUserRepository.findByUsername(ADMIN);
        return admin != null && !StringUtils.hasText(admin.getPassword());
    }
}
