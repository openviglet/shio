/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */
package com.viglet.shio.api.post.type;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.api.post.type.dto.ShPostTypeDef;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/**
 * Lossless post-type admin API (SH16) — the console-authenticated surface the
 * {@code shio} CLI uses for {@code pull}/{@code push} (post-types as code). It is
 * the authoring counterpart to the read-only CDA schema (SH11, token-gated).
 *
 * <p>Console-only: it falls under {@code anyRequest().authenticated()} (HTTP
 * Basic) and is CSRF-exempt (see {@code ShSecurityConfig}) so the stateless CLI
 * can call it without a session. System post-types are protected in
 * {@link ShPostTypeAdminService}.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@RestController
@RequestMapping("/api/v2/post-type")
@Tag(name = "Post Type", description = "Post-types as code — pull/push the content model (SH16)")
@Slf4j
public class ShPostTypeAPI {

    private final ShPostTypeAdminService shPostTypeAdminService;

    public ShPostTypeAPI(ShPostTypeAdminService shPostTypeAdminService) {
        this.shPostTypeAdminService = shPostTypeAdminService;
    }

    @GetMapping
    public List<ShPostTypeDef> list() {
        return shPostTypeAdminService.list();
    }

    @GetMapping("/{name}")
    public ResponseEntity<ShPostTypeDef> get(@PathVariable String name) {
        return shPostTypeAdminService.get(name)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{name}")
    public ShPostTypeDef upsert(@PathVariable String name, @RequestBody ShPostTypeDef body) {
        ShPostTypeDef saved = shPostTypeAdminService.upsert(name, body);
        log.info("Upserted post-type '{}' ({} field(s)) via admin API",
                name, saved.fields() != null ? saved.fields().size() : 0);
        return saved;
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Void> delete(@PathVariable String name) {
        return shPostTypeAdminService.delete(name)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
