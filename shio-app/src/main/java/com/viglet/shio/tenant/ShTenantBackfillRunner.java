package com.viglet.shio.tenant;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Backfills `tenant_id = 'shio'` on rows inserted before multi-tenant support
 * existed. Runs once at startup, after Hibernate has created the columns via
 * ddl-auto. Uses native SQL so Hibernate's @TenantId filter is bypassed.
 * Missing tables are tolerated — useful for partial imports or future entities.
 */
@Configuration
public class ShTenantBackfillRunner {

    private static final Logger log = LoggerFactory.getLogger(ShTenantBackfillRunner.class);

    /**
     * Tables that carry a {@code TENANT_ID} column, by their real (Liquibase +
     * Hibernate) names. Most entities have no {@code @Table}, so Hibernate names
     * the table after the entity's simple name verbatim — PascalCase, NOT
     * snake_case. An earlier snake_case list (e.g. {@code sh_post_type_attr})
     * matched nothing, so the pre-multi-tenant backfill silently no-op'd; the
     * SH26 cross-DB ITs assert each of these resolves against the applied
     * changelog. Three names pin an explicit {@code @Table}: {@code api_token}
     * (ShApiToken), {@code site_webhook} (ShSiteWebhook) and
     * {@code sh_post_unified} (ShPostUnified, the active content store — its
     * {@code TENANT_ID} column lands via SH22's {@code v2026.2.4.yaml}).
     */
    static final List<String> TENANT_TABLES = List.of(
            "ShHistory",
            "ShObject",
            "ShObjectDraft",
            "ShPostAttr",
            "ShPostDraftAttr",
            "ShPostTypeAttr",
            "ShReference",
            "ShReferenceDraft",
            "ShRelatorItem",
            "ShRelatorItemDraft",
            "ShWorkflowTask",
            "sh_post_unified",
            "api_token",
            "site_webhook"
    );

    @PersistenceContext
    private EntityManager entityManager;

    private final PlatformTransactionManager transactionManager;

    public ShTenantBackfillRunner(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Bean
    ApplicationRunner tenantBackfill() {
        return args -> backfill();
    }

    /**
     * Each table is updated in its own transaction (programmatic, via
     * {@link TransactionTemplate}) rather than relying on a {@code @Transactional}
     * proxy: this runs from an {@code ApplicationRunner} lambda where a self-call
     * would bypass the proxy (S6809), and a per-table boundary keeps a missing
     * table from poisoning the others with a rollback-only mark.
     */
    public void backfill() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        for (String table : TENANT_TABLES) {
            try {
                Integer updated = tx.execute(status -> entityManager
                        .createNativeQuery("UPDATE " + table + " SET tenant_id = '"
                                + ShTenantContext.DEFAULT_TENANT
                                + "' WHERE tenant_id IS NULL")
                        .executeUpdate());
                if (updated != null && updated > 0) {
                    log.info("Backfilled tenant_id on {} rows of {}", updated, table);
                }
            } catch (RuntimeException e) {
                log.debug("Skipping tenant_id backfill for {}: {}", table, e.getMessage());
            }
        }
    }
}
