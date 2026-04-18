-- Multi-tenant support: add tenant_id to ShObject.
-- Single shared schema: the tenant discriminator is filtered by Hibernate via @TenantId.

ALTER TABLE "SH_OBJECT" ADD COLUMN IF NOT EXISTS "TENANT_ID" VARCHAR(255);

UPDATE "SH_OBJECT" SET "TENANT_ID" = 'shio' WHERE "TENANT_ID" IS NULL;

CREATE INDEX IF NOT EXISTS "IDX_SH_OBJECT_TENANT" ON "SH_OBJECT" ("TENANT_ID");
