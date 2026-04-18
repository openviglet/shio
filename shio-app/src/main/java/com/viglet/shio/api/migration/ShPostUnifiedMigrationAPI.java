/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.migration;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.migration.unified.ShPostUnifiedMigrationService;
import com.viglet.shio.migration.unified.ShPostUnifiedMigrationService.MigrationResult;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Admin endpoints to trigger and inspect the unified post-model backfill.
 *
 * <p>These endpoints are idempotent: running the migration repeatedly rewrites
 * the unified rows from the legacy source of truth without affecting the
 * legacy tables.</p>
 */
@RestController
@RequestMapping("/api/v2/migration/unified")
@Tag(name = "Migration", description = "Backfill from legacy post tables into sh_post_unified")
public class ShPostUnifiedMigrationAPI {

    private final ShPostUnifiedMigrationService migrationService;
    private final ShPostUnifiedRepository shPostUnifiedRepository;

    public ShPostUnifiedMigrationAPI(ShPostUnifiedMigrationService migrationService,
                                     ShPostUnifiedRepository shPostUnifiedRepository) {
        this.migrationService = migrationService;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
    }

    @PostMapping("/run")
    public MigrationResult run() {
        return migrationService.migrateAll();
    }

    @GetMapping("/status")
    public Status status() {
        return new Status(shPostUnifiedRepository.count());
    }

    public record Status(long unifiedRowCount) {
    }
}
