/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
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

import com.viglet.shio.api.webhook.dto.ShSiteWebhookRequest;
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
    public ShSiteWebhook create(@RequestBody ShSiteWebhookRequest body, Principal principal) {
        ShSiteWebhook webhook = new ShSiteWebhook();
        webhook.setName(body.name());
        webhook.setSiteId(body.siteId());
        webhook.setUrl(body.url());
        // Auto-generate a shared secret when the caller doesn't supply one.
        webhook.setSecret(body.secret() != null && !body.secret().isBlank()
                ? body.secret()
                : UUID.randomUUID().toString().replace("-", ""));
        webhook.setEvents(body.events() != null ? new HashSet<>(body.events()) : new HashSet<>());
        webhook.setEnabled(body.enabled());
        webhook.setOwner(principal != null ? principal.getName() : "system");
        webhook.setCreatedDate(Instant.now());

        shSiteWebhookRepository.saveAndFlush(webhook);
        log.info("Created site webhook '{}' -> {} by {}", webhook.getName(), webhook.getUrl(), webhook.getOwner());
        return webhook;
    }

    @PutMapping("/{id}")
    public ResponseEntity<ShSiteWebhook> update(@PathVariable String id, @RequestBody ShSiteWebhookRequest body) {
        return shSiteWebhookRepository.findById(id)
                .map(existing -> {
                    existing.setName(body.name());
                    existing.setSiteId(body.siteId());
                    existing.setUrl(body.url());
                    existing.setEvents(body.events() != null ? new HashSet<>(body.events()) : new HashSet<>());
                    existing.setEnabled(body.enabled());
                    // Only replace the secret when a non-blank one is supplied —
                    // never clobber it to blank on a routine update.
                    if (body.secret() != null && !body.secret().isBlank()) {
                        existing.setSecret(body.secret());
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
