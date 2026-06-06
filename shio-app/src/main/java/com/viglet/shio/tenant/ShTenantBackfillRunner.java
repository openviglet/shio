package com.viglet.shio.tenant;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

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
     * changelog. Two entities pin an explicit {@code @Table}: {@code api_token}
     * (ShApiToken) and {@code site_webhook} (ShSiteWebhook).
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
            "api_token",
            "site_webhook"
    );

    @PersistenceContext
    private EntityManager entityManager;

    @Bean
    ApplicationRunner tenantBackfill() {
        return args -> backfill();
    }

    @Transactional
    public void backfill() {
        for (String table : TENANT_TABLES) {
            try {
                int updated = entityManager
                        .createNativeQuery("UPDATE " + table + " SET tenant_id = '"
                                + ShTenantContext.DEFAULT_TENANT
                                + "' WHERE tenant_id IS NULL")
                        .executeUpdate();
                if (updated > 0) {
                    log.info("Backfilled tenant_id on {} rows of {}", updated, table);
                }
            } catch (RuntimeException e) {
                log.debug("Skipping tenant_id backfill for {}: {}", table, e.getMessage());
            }
        }
    }
}
