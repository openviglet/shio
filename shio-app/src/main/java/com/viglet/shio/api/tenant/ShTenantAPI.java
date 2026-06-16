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
import com.viglet.shio.persistence.model.tenant.ShTenantRole;
import com.viglet.shio.persistence.repository.tenant.ShTenantRepository;
import com.viglet.shio.property.ShAppProperties;
import com.viglet.shio.tenant.ShTenantContext;
import com.viglet.shio.tenant.ShTenantFilter;
import com.viglet.shio.tenant.ShTenantMembershipService;
import com.viglet.shio.tenant.ShTenantProvisioningService;
import com.viglet.shio.tenant.ShTenantTeardownService;
import com.viglet.shio.tenant.ShTenantTeardownService.ShTenantTeardownReport;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

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
    private final ShTenantMembershipService shTenantMembershipService;

    public ShTenantAPI(ShAppProperties shAppProperties, ShTenantRepository shTenantRepository,
                       ShTenantProvisioningService shTenantProvisioningService,
                       ShTenantTeardownService shTenantTeardownService,
                       ShTenantMembershipService shTenantMembershipService) {
        this.shAppProperties = shAppProperties;
        this.shTenantRepository = shTenantRepository;
        this.shTenantProvisioningService = shTenantProvisioningService;
        this.shTenantTeardownService = shTenantTeardownService;
        this.shTenantMembershipService = shTenantMembershipService;
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
        // Give the creating principal an OWNER membership so the hard-403 gate (SH22/C)
        // never locks the admin out of the tenant they just created.
        shTenantMembershipService.addMembership(tenant, tenant.getOwner(), ShTenantRole.OWNER);
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
                    // SH22/D — the plan drives quotas; editable for non-default tenants.
                    if (!existing.isDefaultTenant() && body.getPlan() != null && !body.getPlan().isBlank()) {
                        existing.setPlan(body.getPlan().trim());
                    }
                    shTenantRepository.saveAndFlush(existing);
                    return ResponseEntity.ok(existing);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** The tenants the current user actively belongs to (Turing parity, SH22/C). */
    @GetMapping("/mine")
    public List<ShTenant> mine(Principal principal) {
        if (principal == null) {
            return List.of();
        }
        return shTenantMembershipService.tenantsOf(principal.getName());
    }

    /**
     * Self-service signup: create a tenant (named in the body) owned by the current
     * user, with an {@code OWNER} membership, then provision the default model.
     */
    @PostMapping("/signup")
    public ResponseEntity<ShTenant> signup(@RequestBody ShTenant body, Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        ShTenant tenant = shTenantMembershipService.signup(body.getName(), principal.getName());
        return ResponseEntity.ok(tenant);
    }

    /**
     * Switch the session's active tenant (Turing parity). Requires an ACTIVE
     * membership; sets the session attribute {@code ShTenantFilter} reads first.
     */
    @PostMapping("/{name}/switch")
    public ResponseEntity<ShTenant> switchTenant(@PathVariable String name, Principal principal,
                                                 HttpServletRequest request) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        ShTenant tenant = shTenantMembershipService.findByName(name)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown tenant"));
        if (!tenant.isDefaultTenant()
                && !shTenantMembershipService.isActiveMember(tenant.getName(), principal.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member of this tenant");
        }
        request.getSession(true).setAttribute(ShTenantFilter.TENANT_SESSION_ATTR, tenant.getName());
        return ResponseEntity.ok(tenant);
    }

    /**
     * Impersonate a tenant for this session (admin-only, audited): sets the session
     * attribute {@code ShTenantFilter} reads, bypassing the membership requirement.
     */
    @PostMapping("/{id}/impersonate")
    public ResponseEntity<ShTenant> impersonate(@PathVariable String id, HttpServletRequest request) {
        ShTenant tenant = shTenantRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown tenant"));
        request.getSession(true).setAttribute(ShTenantFilter.TENANT_SESSION_ATTR, tenant.getName());
        log.warn("[TenantImpersonate] session bound to tenant '{}'", tenant.getName());
        return ResponseEntity.ok(tenant);
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
