---
name: shio-database
description: Read before touching Shio's schema, migrations or multi-tenancy. Triggers on Liquibase changelogs, db.changelog-master.yaml, adding a column or table, ddl-auto, H2 URL parameters, @TenantId, tenant_id, ShTenantFilter/ShTenantResolver/ShTenantContext, ShTenantBackfillRunner, the cross-DB Testcontainers ITs, and any "Column ... not found" error at startup or query-prepare time.
---

# Schema, Liquibase, multi-tenancy

**Read [docs/agents/database.md](../../../docs/agents/database.md)** before writing a
changeset or touching a tenant-scoped entity.

## The four rules, three of which fail only at runtime

1. **The schema is Liquibase's, not Hibernate's.** `ddl-auto=none` — never set it
   back to `update`/`create` for the app. Schema drift on `@TenantId` columns is why
   we moved off it, and SH2/SH5 adding columns via `ddl-auto=update` with no
   changeset meant a clean Liquibase DB could not authenticate a CDA token.
2. **Don't navigate past `.id` of an association whose target is an `ShObject`
   subclass** (`ShSite`/`ShFolder`/`ShPostType`) in a repository query;
   `ShTenantJoinLintTest` refuses it. Up to Hibernate 7.4.5 the implicit join put the
   tenant predicate on a subclass alias with no `tenant_id` and the query died at
   prepare time; 7.4.13 (Boot 4.1.2) joins the `ShObject` root and filters correctly on
   H2, PostgreSQL and MariaDB (SH1290, `ShTenantJoinHazardTest`). The rule stays
   because the `.id` form needs no join at all.
   ```java
   // ✗ joins ShPostType and ShObject to read a name
   select p.shPostType.name, count(distinct p.id) from ShPostUnified p group by p.shPostType.name
   // ✓ uses the FK column already on sh_post_unified — no join at all
   select p.shPostType.id,   count(distinct p.id) from ShPostUnified p group by p.shPostType.id
   ```
3. **The discriminator column must be lowercase `tenant_id`**, and the H2 URL must
   keep `;DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true` — in the dev DB
   **and** the unit-test datasource. Hibernate emits the `@TenantId` *predicate*
   unquoted even under `globally_quoted_identifiers=true`; backtick-quoting the
   column quotes the projection, not the predicate.
4. **A populated table's new `tenant_id` goes in nullable and into
   `ShTenantBackfillRunner.TENANT_TABLES`** — using the **PascalCase** entity name
   (most tables have no `@Table`), or the backfill silently no-ops. A `NOT NULL`
   ALTER fails; a NULL `tenant_id` under live `@TenantId` is invisible.

## Guards that already exist

`ShLiquibaseChangelogAgnosticTest` fails the build on `TIMESTAMP WITH TIME ZONE`,
`CHARACTER LARGE OBJECT`, `BINARY LARGE OBJECT`, `BOOLEAN` and `ENUM(` — use
`TIMESTAMP`, `CLOB`, lowercase `boolean`, `VARCHAR` with `@Enumerated(STRING)`. It is
only a textual guard: the real proof is `ShMariaDbSchemaIT` / `ShPostgresSchemaIT`
(`mvn verify`, auto-skipped without Docker), which boot the full context against real
engines. `ShTenantIsolationIT` proves A cannot see B — a test that switches tenants
needs a **new transaction per tenant**, since the resolver resolves once per
session-open.

Spec: [docs/specs/SH22-multi-tenancy.md](../../../docs/specs/SH22-multi-tenancy.md) ·
[docs/specs/SH26-cross-db-schema-guard.md](../../../docs/specs/SH26-cross-db-schema-guard.md)
