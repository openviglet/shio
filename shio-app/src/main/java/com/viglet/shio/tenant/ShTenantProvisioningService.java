/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */
package com.viglet.shio.tenant;

import java.util.List;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.post.type.ShPostTypeAdminService;
import com.viglet.shio.api.post.type.dto.ShPostTypeDef;

import lombok.extern.slf4j.Slf4j;

/**
 * Provisions a newly-created tenant (SH22 Part C) with a copy of the default
 * tenant's content model, so a brand-new workspace is usable immediately instead
 * of having no post-types. Widgets are global (not {@code @TenantId}-scoped) and
 * so are already shared; only the post-types need copying.
 *
 * <p>Hibernate's {@code CurrentTenantIdentifierResolver} resolves the tenant once
 * per session-open, so reading the source model and writing the copy run in
 * <strong>separate transactions</strong> under different {@link ShTenantContext}
 * values (the same pattern as {@code ShTenantIsolationIT}). Idempotent: a
 * post-type already present in the target tenant is skipped.
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Service
@Slf4j
public class ShTenantProvisioningService {

    private final ShPostTypeAdminService shPostTypeAdminService;
    private final TransactionTemplate txTemplate;

    public ShTenantProvisioningService(ShPostTypeAdminService shPostTypeAdminService,
                                       PlatformTransactionManager txManager) {
        this.shPostTypeAdminService = shPostTypeAdminService;
        this.txTemplate = new TransactionTemplate(txManager);
    }

    /**
     * Copy the default tenant's post-types into {@code tenantId}.
     *
     * @return the number of post-types created (0 for the default tenant itself).
     */
    public int provision(String tenantId) {
        if (tenantId == null || tenantId.isBlank()
                || ShTenantContext.DEFAULT_TENANT.equals(tenantId)) {
            return 0;
        }
        List<ShPostTypeDef> defs = runInTenant(ShTenantContext.DEFAULT_TENANT,
                shPostTypeAdminService::list);
        int created = runInTenant(tenantId, () -> {
            int n = 0;
            for (ShPostTypeDef def : defs) {
                if (shPostTypeAdminService.provisionFromDef(def)) {
                    n++;
                }
            }
            return n;
        });
        log.info("Provisioned tenant '{}' with {} post-type(s)", tenantId, created);
        return created;
    }

    /**
     * Run {@code work} in a fresh transaction with {@code tenant} bound to the
     * tenant context (so the opening Hibernate session resolves to it), restoring
     * the caller's previous tenant afterwards.
     */
    private <T> T runInTenant(String tenant, Supplier<T> work) {
        String previous = ShTenantContext.getTenantId();
        ShTenantContext.setTenantId(tenant);
        try {
            return txTemplate.execute(status -> work.get());
        } finally {
            if (previous != null) {
                ShTenantContext.setTenantId(previous);
            } else {
                ShTenantContext.clear();
            }
        }
    }
}
