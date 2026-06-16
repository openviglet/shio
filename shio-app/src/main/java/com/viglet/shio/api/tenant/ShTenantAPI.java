package com.viglet.shio.api.tenant;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.persistence.model.tenant.ShTenant;
import com.viglet.shio.persistence.repository.tenant.ShTenantRepository;
import com.viglet.shio.property.ShAppProperties;
import com.viglet.shio.tenant.ShTenantContext;
import com.viglet.shio.tenant.ShTenantProvisioningService;
import com.viglet.shio.tenant.ShTenantTeardownService;
import com.viglet.shio.tenant.ShTenantTeardownService.ShTenantTeardownReport;

import org.springframework.web.bind.annotation.RequestParam;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/**
 * Tenant API (SH22).
 *
 * <ul>
 *   <li>{@code GET /current} — the request's resolved tenant (any authenticated
 *       user; used by the console to know which workspace it is in).</li>
 *   <li>{@code GET/POST/PUT/DELETE} — the tenant catalog CRUD. Writes are
 *       admin-gated in {@code ShSecurityConfig} ({@code ROLE_ADMIN}); the reserved
 *       {@code shio} default tenant can't be renamed or deleted. Mirrors the thin
 *       {@code ShApiTokenAPI} / {@code ShSiteWebhookAPI} pattern (repository-direct,
 *       no service layer).</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 */
@RestController
@RequestMapping("/api/v2/tenant")
@Tag(name = "Tenant API", description = "Multi-tenant information and catalog")
@Slf4j
public class ShTenantAPI {

    private final ShAppProperties shAppProperties;
    private final ShTenantRepository shTenantRepository;
    private final ShTenantProvisioningService shTenantProvisioningService;
    private final ShTenantTeardownService shTenantTeardownService;

    public ShTenantAPI(ShAppProperties shAppProperties, ShTenantRepository shTenantRepository,
                       ShTenantProvisioningService shTenantProvisioningService,
                       ShTenantTeardownService shTenantTeardownService) {
        this.shAppProperties = shAppProperties;
        this.shTenantRepository = shTenantRepository;
        this.shTenantProvisioningService = shTenantProvisioningService;
        this.shTenantTeardownService = shTenantTeardownService;
    }

    @GetMapping("/current")
    public Map<String, Object> current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String tenantId;
        if (!shAppProperties.isMultiTenant()) {
            tenantId = ShTenantContext.DEFAULT_TENANT;
        } else if (authentication != null && authentication.isAuthenticated()) {
            String resolved = ShTenantContext.getTenantId();
            tenantId = (resolved != null && !resolved.isBlank()) ? resolved : authentication.getName();
        } else {
            tenantId = ShTenantContext.DEFAULT_TENANT;
        }
        return Map.of(
                "tenantId", tenantId,
                "multiTenant", shAppProperties.isMultiTenant()
        );
    }

    @GetMapping
    public List<ShTenant> list() {
        return shTenantRepository.findAllByOrderByNameAsc();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShTenant> get(@PathVariable String id) {
        return shTenantRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ShTenant> create(@RequestBody ShTenant body, Principal principal) {
        String name = body.getName() != null ? body.getName().trim() : "";
        if (name.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (shTenantRepository.existsByName(name)) {
            return ResponseEntity.status(409).build();
        }
        ShTenant tenant = new ShTenant();
        tenant.setName(name);
        tenant.setDescription(body.getDescription());
        tenant.setEnabled(true);
        tenant.setOwner(principal != null ? principal.getName() : "system");
        tenant.setCreatedDate(Instant.now());
        shTenantRepository.saveAndFlush(tenant);
        log.info("Created tenant '{}' by {}", tenant.getName(), tenant.getOwner());
        // Seed the new tenant with a copy of the default tenant's content model (SH22
        // Part C). Best-effort: a provisioning failure must not undo the catalog row
        // (the explicit /provision endpoint can retry). Only meaningful multi-tenant.
        if (shAppProperties.isMultiTenant()) {
            try {
                shTenantProvisioningService.provision(tenant.getName());
            } catch (RuntimeException e) {
                log.warn("Tenant '{}' created but provisioning failed: {}", tenant.getName(), e.getMessage());
            }
        }
        return ResponseEntity.ok(tenant);
    }

    /**
     * Re-run post-type provisioning for an existing tenant (SH22 Part C) — idempotent,
     * admin-only. Useful when the default model changed, or when auto-provisioning on
     * create failed.
     */
    @PostMapping("/{id}/provision")
    public ResponseEntity<Map<String, Object>> provision(@PathVariable String id) {
        return shTenantRepository.findById(id)
                .map(tenant -> {
                    int created = shTenantProvisioningService.provision(tenant.getName());
                    return ResponseEntity.ok(Map.<String, Object>of(
                            "tenant", tenant.getName(), "postTypesCreated", created));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ShTenant> update(@PathVariable String id, @RequestBody ShTenant body) {
        return shTenantRepository.findById(id)
                .map(existing -> {
                    // The reserved default tenant cannot be renamed; its name is the
                    // discriminator value every legacy/single-tenant row carries.
                    if (!existing.isDefaultTenant() && body.getName() != null && !body.getName().isBlank()) {
                        existing.setName(body.getName().trim());
                    }
                    existing.setDescription(body.getDescription());
                    existing.setEnabled(existing.isDefaultTenant() || body.isEnabled());
                    shTenantRepository.saveAndFlush(existing);
                    return ResponseEntity.ok(existing);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** Suspend a tenant — blocks its members at {@code ShTenantFilter} (admin-only). */
    @PostMapping("/{id}/suspend")
    public ResponseEntity<ShTenant> suspend(@PathVariable String id) {
        return ResponseEntity.ok(shTenantTeardownService.suspend(id));
    }

    /** Re-activate a suspended tenant (admin-only). */
    @PostMapping("/{id}/activate")
    public ResponseEntity<ShTenant> activate(@PathVariable String id) {
        return ResponseEntity.ok(shTenantTeardownService.activate(id));
    }

    /**
     * Tear a tenant down (admin-only): purges its stored bytes (file_source +
     * image cache) and removes the catalog row. {@code ?dryRun=true} reports the
     * plan without mutating anything. 409 for the reserved {@code shio} tenant,
     * 404 (in the report) for an unknown id.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ShTenantTeardownReport> delete(@PathVariable String id,
                                                         @RequestParam(name = "dryRun", defaultValue = "false") boolean dryRun) {
        ShTenantTeardownReport report = shTenantTeardownService.delete(id, dryRun);
        if (!report.tenantExisted()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(report);
    }
}
