-- Flyway baseline for multi-tenant support.
--
-- Hibernate (ddl-auto=update) creates the `tenant_id` columns from @TenantId
-- annotations; rows predating this feature are backfilled at boot by
-- ShTenantBackfillRunner. Future schema tweaks live in V2+.
SELECT 1;
