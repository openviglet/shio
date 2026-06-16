/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.unified;

import java.security.Principal;
import java.util.Date;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.CreateRequest;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.UpdateRequest;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.tenant.ShTenantQuotaService;

import tools.jackson.databind.JsonNode;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v2/post-unified")
@Tag(name = "PostUnified", description = "Unified post model API")
public class ShPostUnifiedAPI {

    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShFolderRepository shFolderRepository;
    private final ShPostUnifiedWriteService shPostUnifiedWriteService;
    private final ShTenantQuotaService shTenantQuotaService;

    public ShPostUnifiedAPI(ShPostUnifiedRepository shPostUnifiedRepository,
                            ShFolderRepository shFolderRepository,
                            ShPostUnifiedWriteService shPostUnifiedWriteService,
                            ShTenantQuotaService shTenantQuotaService) {
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shFolderRepository = shFolderRepository;
        this.shPostUnifiedWriteService = shPostUnifiedWriteService;
        this.shTenantQuotaService = shTenantQuotaService;
    }

    @PostMapping
    public ShPostUnified create(@RequestBody CreatePayload payload, Principal principal) {
        // SH22/D — enforce the tenant's plan post quota (402); no-op single-tenant.
        shTenantQuotaService.checkCanCreatePost();
        return shPostUnifiedWriteService.create(new CreateRequest(
                payload.folderId(),
                payload.postTypeId(),
                payload.data(),
                payload.state() != null ? payload.state() : ShPostState.DRAFT,
                principal != null ? principal.getName() : null));
    }

    @PutMapping("/{id}")
    public ShPostUnified update(@PathVariable String id,
                                @RequestBody UpdatePayload payload,
                                Principal principal) {
        return shPostUnifiedWriteService.update(id, new UpdateRequest(
                payload.data(),
                payload.state() != null ? payload.state() : ShPostState.DRAFT,
                principal != null ? principal.getName() : null));
    }

    @PostMapping("/{id}/publish")
    public ShPostUnified publish(@PathVariable String id, Principal principal) {
        return shPostUnifiedWriteService.publish(id,
                principal != null ? principal.getName() : null);
    }

    @PostMapping("/{id}/unpublish")
    public ResponseEntity<Void> unpublish(@PathVariable String id) {
        shPostUnifiedWriteService.unpublish(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        shPostUnifiedWriteService.delete(id);
        return ResponseEntity.noContent().build();
    }

    public record CreatePayload(String folderId, String postTypeId, JsonNode data, ShPostState state) {
    }

    public record UpdatePayload(JsonNode data, ShPostState state) {
    }

    @GetMapping("/folder/{folderId}")
    public List<ShPostUnified> listByFolder(@PathVariable String folderId,
                                            @RequestParam(name = "state", defaultValue = "PUBLISHED") ShPostState state) {
        return shFolderRepository.findById(folderId)
                .map(folder -> shPostUnifiedRepository.findByShFolderAndState(folder, state))
                .orElse(List.of());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShPostUnified> get(@PathVariable String id,
                                             @RequestParam(name = "state", defaultValue = "PUBLISHED") ShPostState state) {
        return shPostUnifiedRepository.findByIdAndState(id, state)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/versions")
    public List<ShPostUnified> versions(@PathVariable String id) {
        return shPostUnifiedRepository.findById(id);
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<ShPostUnified> restore(@PathVariable String id, Principal principal) {
        try {
            return ResponseEntity.ok(shPostUnifiedWriteService.restoreDraftFromPublished(
                    id, principal != null ? principal.getName() : null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Set or clear the scheduled publish/unpublish timestamps (SH15). A
     * {@code null} field clears that schedule; the background sweep fires the
     * transition once the instant passes.
     */
    @PostMapping("/{id}/schedule")
    public ResponseEntity<ShPostUnified> schedule(@PathVariable String id,
                                                  @RequestBody SchedulePayload payload,
                                                  Principal principal) {
        try {
            return ResponseEntity.ok(shPostUnifiedWriteService.schedule(
                    id, payload.publishAt(), payload.unpublishAt(),
                    principal != null ? principal.getName() : null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    public record SchedulePayload(Date publishAt, Date unpublishAt) {
    }
}
