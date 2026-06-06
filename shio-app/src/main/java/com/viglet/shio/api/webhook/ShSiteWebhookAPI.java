/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */
package com.viglet.shio.api.webhook;

import java.security.Principal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.persistence.model.webhook.ShSiteWebhook;
import com.viglet.shio.persistence.repository.webhook.ShSiteWebhookRepository;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/**
 * CRUD for per-site outbound webhook subscriptions (SH12). Mirrors
 * {@code ShApiTokenAPI}: console-only (falls under {@code anyRequest
 * authenticated}), repository-direct, no service layer.
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@RestController
@RequestMapping("/api/v2/site-webhook")
@Tag(name = "Site Webhook", description = "Outbound webhooks fired on publish/unpublish/delete")
@Slf4j
public class ShSiteWebhookAPI {

    private final ShSiteWebhookRepository shSiteWebhookRepository;

    public ShSiteWebhookAPI(ShSiteWebhookRepository shSiteWebhookRepository) {
        this.shSiteWebhookRepository = shSiteWebhookRepository;
    }

    @GetMapping
    public List<ShSiteWebhook> list() {
        return shSiteWebhookRepository.findAllByOrderByNameAsc();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShSiteWebhook> get(@PathVariable String id) {
        return shSiteWebhookRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ShSiteWebhook create(@RequestBody ShSiteWebhook body, Principal principal) {
        ShSiteWebhook webhook = new ShSiteWebhook();
        webhook.setName(body.getName());
        webhook.setSiteId(body.getSiteId());
        webhook.setUrl(body.getUrl());
        // Auto-generate a shared secret when the caller doesn't supply one.
        webhook.setSecret(body.getSecret() != null && !body.getSecret().isBlank()
                ? body.getSecret()
                : UUID.randomUUID().toString().replace("-", ""));
        webhook.setEvents(body.getEvents() != null ? new HashSet<>(body.getEvents()) : new HashSet<>());
        webhook.setEnabled(body.isEnabled());
        webhook.setOwner(principal != null ? principal.getName() : "system");
        webhook.setCreatedDate(Instant.now());

        shSiteWebhookRepository.saveAndFlush(webhook);
        log.info("Created site webhook '{}' -> {} by {}", webhook.getName(), webhook.getUrl(), webhook.getOwner());
        return webhook;
    }

    @PutMapping("/{id}")
    public ResponseEntity<ShSiteWebhook> update(@PathVariable String id, @RequestBody ShSiteWebhook body) {
        return shSiteWebhookRepository.findById(id)
                .map(existing -> {
                    existing.setName(body.getName());
                    existing.setSiteId(body.getSiteId());
                    existing.setUrl(body.getUrl());
                    existing.setEvents(body.getEvents() != null ? new HashSet<>(body.getEvents()) : new HashSet<>());
                    existing.setEnabled(body.isEnabled());
                    // Only replace the secret when a non-blank one is supplied —
                    // never clobber it to blank on a routine update.
                    if (body.getSecret() != null && !body.getSecret().isBlank()) {
                        existing.setSecret(body.getSecret());
                    }
                    shSiteWebhookRepository.saveAndFlush(existing);
                    return ResponseEntity.ok(existing);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Boolean>> delete(@PathVariable String id) {
        shSiteWebhookRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", true));
    }
}
