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

    /** Hibernate's default naming strategy produces these table names. */
    private static final List<String> TENANT_TABLES = List.of(
            "sh_object",
            "sh_object_draft",
            "sh_post_attr",
            "sh_post_draft_attr",
            "sh_post_type_attr",
            "sh_relator_item",
            "sh_relator_item_draft",
            "sh_history",
            "sh_reference",
            "sh_reference_draft",
            "sh_workflow_task",
            "api_token"
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
