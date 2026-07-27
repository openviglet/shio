/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */
package com.viglet.shio.api.token;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;

import com.viglet.shio.api.token.dto.ShApiTokenRequest;
import com.viglet.shio.persistence.model.token.ShApiToken;
import com.viglet.shio.persistence.model.token.ShApiTokenEnv;
import com.viglet.shio.persistence.model.token.ShApiTokenScope;
import com.viglet.shio.persistence.repository.token.ShApiTokenRepository;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/**
 * CDA (Content Delivery API) Token management.
 * Tokens are read-only: they only allow fetching published content.
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@RestController
@RequestMapping("/api/v2/api-token")
@Tag(name = "API Token", description = "CDA Token management for read-only content access")
@Slf4j
public class ShApiTokenAPI {

    private final ShApiTokenRepository shApiTokenRepository;

    public ShApiTokenAPI(ShApiTokenRepository shApiTokenRepository) {
        this.shApiTokenRepository = shApiTokenRepository;
    }

    @GetMapping
    public List<ShApiToken> list() {
        // Durable tokens only — short-lived preview tokens (SH13) are hidden here.
        return shApiTokenRepository.findByEphemeralFalseOrderByTitleAsc();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShApiToken> get(@PathVariable String id) {
        return shApiTokenRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ShApiToken create(@RequestBody ShApiTokenRequest body, Principal principal) {
        ShApiToken token = new ShApiToken();
        token.setTitle(body.title());
        token.setDescription(body.description());
        token.setToken(UUID.randomUUID().toString().replace("-", "").substring(0, 25));
        token.setOwner(principal != null ? principal.getName() : "system");
        token.setCreatedDate(Instant.now());
        token.setEnabled(true);
        token.setScope(body.scope() != null ? body.scope() : ShApiTokenScope.READ);
        token.setSiteIds(body.siteIds() != null ? new HashSet<>(body.siteIds()) : new HashSet<>());
        token.setEnv(body.env() != null ? body.env() : ShApiTokenEnv.PROD);
        token.setMayPublish(body.mayPublish());

        shApiTokenRepository.saveAndFlush(token);
        log.info("Created CDA API token '{}' by {}", token.getTitle(), token.getOwner());
        return token;
    }

    @PutMapping("/{id}")
    public ResponseEntity<ShApiToken> update(@PathVariable String id, @RequestBody ShApiTokenRequest body) {
        return shApiTokenRepository.findById(id)
                .map(existing -> {
                    existing.setTitle(body.title());
                    existing.setDescription(body.description());
                    existing.setEnabled(body.enabled());
                    existing.setScope(body.scope() != null ? body.scope() : ShApiTokenScope.READ);
                    existing.setSiteIds(body.siteIds() != null ? new HashSet<>(body.siteIds()) : new HashSet<>());
                    existing.setEnv(body.env() != null ? body.env() : ShApiTokenEnv.PROD);
                    existing.setMayPublish(body.mayPublish());
                    // Token value is never updated
                    shApiTokenRepository.saveAndFlush(existing);
                    return ResponseEntity.ok(existing);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Boolean>> delete(@PathVariable String id) {
        shApiTokenRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", true));
    }
}
