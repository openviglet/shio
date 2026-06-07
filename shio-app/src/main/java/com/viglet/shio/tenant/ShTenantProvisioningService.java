/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */
package com.viglet.shio.tenant;

import java.util.function.Supplier;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.exchange.model.ShExchangePackage;
import com.viglet.shio.exchange.site.ShSiteExportService;
import com.viglet.shio.exchange.site.ShSiteImportService;

import lombok.extern.slf4j.Slf4j;

/**
 * Provisions a newly-created tenant (SH22 Part C) with a copy of the default
 * tenant's content, so a brand-new workspace is usable immediately. Rebased onto
 * the exchange engine (SH72): instead of a hand-rolled post-type copy, it
 * <strong>exports</strong> the default tenant (SH69 {@link ShSiteExportService#buildTenantPackage})
 * and <strong>imports/clones</strong> the package into the new tenant (SH70
 * {@link ShSiteImportService#provisionInto}) — one provisioning engine shared with
 * site export/import and bootstrap (SH71), so the paths can't drift. Widgets are
 * global (not {@code @TenantId}-scoped) and so are already shared.
 *
 * <p>Hibernate's {@code CurrentTenantIdentifierResolver} resolves the tenant once
 * per session-open, so reading the source and writing the copy run in
 * <strong>separate transactions</strong> under different {@link ShTenantContext}
 * values (the same pattern as {@code ShTenantIsolationIT}). Idempotent: post-types
 * already present are skipped, and content is cloned only into an empty tenant
 * (so a re-provision never duplicates sites).
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Service
@Slf4j
public class ShTenantProvisioningService {

    private final ShSiteExportService shSiteExportService;
    private final ShSiteImportService shSiteImportService;
    private final TransactionTemplate txTemplate;

    public ShTenantProvisioningService(ShSiteExportService shSiteExportService,
                                       ShSiteImportService shSiteImportService,
                                       PlatformTransactionManager txManager) {
        this.shSiteExportService = shSiteExportService;
        this.shSiteImportService = shSiteImportService;
        this.txTemplate = new TransactionTemplate(txManager);
    }

    /**
     * Copy the default tenant's content (sites + folders + posts + post-types) into
     * {@code tenantId} via the exchange engine.
     *
     * @return the number of post-types created (0 for the default tenant itself).
     */
    public int provision(String tenantId) {
        if (tenantId == null || tenantId.isBlank()
                || ShTenantContext.DEFAULT_TENANT.equals(tenantId)) {
            return 0;
        }
        ShExchangePackage pkg = runInTenant(ShTenantContext.DEFAULT_TENANT,
                shSiteExportService::buildTenantPackage);
        int created = runInTenant(tenantId, () -> shSiteImportService.provisionInto(pkg, tenantId));
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
