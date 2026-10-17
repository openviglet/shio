# SH26: Cross-DB schema-drift guard, implementable spec

> Roadmap: [ROADMAP.md](../ROADMAP.md) Block C · Status: ✅ shipped · deps:,

## 1. Context: what already existed (do not rebuild)

- The schema is **Liquibase-managed** (`ddl-auto=none`): a generated baseline
  (`db/changelog/v2026.2_baseline.yaml`) + incremental files wired through
  `db.changelog-master.yaml` (`spring.liquibase.change-log`).
- `ShLiquibaseChangelogAgnosticTest` **lints** the changelog YAML for non-agnostic
  types (`TIMESTAMP WITH TIME ZONE`, `CHARACTER LARGE OBJECT`, etc.).
- Both the MariaDB and PostgreSQL JDBC drivers ship in `shio-app/pom.xml`.

**The gap SH26 closes:** the lint is only a *textual* guard. The changelogs were
**never actually applied** on MariaDB / PostgreSQL, dev and the whole test suite
run on H2 only (tests even skip Liquibase, building the schema from entity
metadata via `create-drop`). Two real, latent drift problems were hiding behind
that gap, plus a silent backfill bug.

## 2. Goal & scope

**In scope**
- Testcontainers ITs that boot the **full Spring context** against real MariaDB
  + PostgreSQL engines with `spring.liquibase.enabled=true` + `ddl-auto=none`, so
  a green run proves the changelog applies *and* every entity maps onto it *and*
  the seed inserts run. A no-Docker H2 IT covers the same on the default engine.
- The cross-DB fixes those ITs surfaced (ENUM, identifier casing).
- The `ShTenantBackfillRunner.TENANT_TABLES` fix.

**Out of scope**
- A schema redesign (no rename to snake_case, no entity `@Table` churn), the
  existing PascalCase names are preserved on every engine via quoting.
- Backward compatibility with already-deployed databases (not required).

## 3. What the ITs surfaced & how it was fixed

### 3.1 Inline `ENUM(...)` is not PostgreSQL-portable
The generated baseline emitted `state ENUM('DRAFT','PUBLISHED')` on
`sh_post_unified`. H2/MySQL/MariaDB accept inline enum types; **PostgreSQL
rejects them** (`type "enum" does not exist`, PG enums need `CREATE TYPE`). The
entity maps the column `@Enumerated(STRING) @Column(length = 16)`, so the
DB-agnostic equivalent is `VARCHAR(16)` (reads back identically everywhere).
- Baseline changeset `…-39`: `ENUM('DRAFT','PUBLISHED')` → `VARCHAR(16)`, with
  `validCheckSum: ANY` so any DB that already ran the original baseline accepts
  the edited checksum.
- `ShLiquibaseChangelogAgnosticTest`: `ENUM(` added to the forbidden tokens so it
  can't be reintroduced. `agents.md` updated (ENUM is **no longer** "allowed").

### 3.2 Identifier casing: Hibernate-quoted vs Liquibase-unquoted
Shio's tables are **PascalCase** entity names with no `@Table` (`ShPostType`,
`ShObject`, …) and Hibernate runs with `globally_quoted_identifiers=true`, so it
references them **quoted** (`"ShPostType"`, `"TENANT_ID"`, `"DTYPE"`, case
preserved). Liquibase created them **unquoted**, which **PostgreSQL folds to
lower-case** (`shposttype`) → Hibernate's `"ShPostType"` never matches → context
fails to boot. (H2 with `CASE_INSENSITIVE_IDENTIFIERS=true` and MariaDB with
case-insensitive columns + case-preserving table names both tolerated it, which
is why the bug stayed hidden.)

**Fix:** each changelog file declares `objectQuotingStrategy: QUOTE_ALL_OBJECTS`,
so Liquibase preserves the exact case on every engine, matching Hibernate's
quoted references. It is set at the **changelog level** (a `databaseChangeLog`
list entry, not a `changeSet` attribute) so it doesn't alter any changeset's
checksum, and is repeated in every file because it does **not** propagate through
`include`. Turning `globally_quoted_identifiers` *off* is **not** the fix, it
re-activates the camelCase→snake_case physical naming strategy (`sh_post_type`),
which the PascalCase baseline doesn't match.

### 3.3 `ShTenantBackfillRunner.TENANT_TABLES` silently no-op'd
The pre-multi-tenant backfill (`UPDATE <table> SET tenant_id='shio' WHERE
tenant_id IS NULL`) listed **snake_case** names (`sh_post_type_attr`, …). The real
tables are the **PascalCase** entity names (`ShPostTypeAttr`, …; only `api_token`
and `site_webhook` pin an explicit `@Table`). Every snake_case name matched
nothing, and the runner swallows "table not found" at `debug`, so the backfill
silently did nothing.
- `TENANT_TABLES` rewritten to the real names and aligned with the set of tables
  the baseline actually creates a `TENANT_ID` column on (adds `site_webhook`,
  which was missing). The IT `everyTenantBackfillTableResolvesByItsRealName`
  asserts each entry exists with a `TENANT_ID` column on all three engines.

## 4. The ITs (`com.viglet.shio.tenant`)

| Class | Engine | Docker | Proves |
|---|---|---|---|
| `ShH2SchemaIT` | H2 (in-mem, prod-like params) | no | changelog applies + context boots on the default engine |
| `ShMariaDbSchemaIT` | MariaDB 11.4 (Testcontainers) | yes | same, on MariaDB |
| `ShPostgresSchemaIT` | PostgreSQL 16 (Testcontainers) | yes | same, on the strict engine |

Each runs three assertions: context boots + `DATABASECHANGELOG` populated; every
`TENANT_TABLES` entry resolves with a `TENANT_ID` column (queried via
`INFORMATION_SCHEMA`, case-insensitively); and the SH12/13/15 incremental columns
landed on top of the baseline. The Testcontainers ITs use
`@Testcontainers(disabledWithoutDocker = true)`, so CI/dev without Docker still
runs the H2 IT. Run via `mvn verify` (maven-failsafe-plugin, `*IT.java`).

## 5. Files

- **Backend:** `tenant/ShTenantBackfillRunner.java` (table names),
  `db/changelog/v2026.2_baseline.yaml` (ENUM→VARCHAR + `validCheckSum` +
  `QUOTE_ALL_OBJECTS`), `db/changelog/v2026.2.{1,2,3}.yaml` (`QUOTE_ALL_OBJECTS`).
- **Tests:** `tenant/ShH2SchemaIT.java`, `tenant/ShMariaDbSchemaIT.java`,
  `tenant/ShPostgresSchemaIT.java`, `db/ShLiquibaseChangelogAgnosticTest.java`
  (forbid `ENUM(`).
- **Build:** `shio-app/pom.xml`, Testcontainers `testcontainers` /
  `testcontainers-junit-jupiter` / `testcontainers-mariadb` /
  `testcontainers-postgresql` (test scope; versions from the Spring Boot BOM).
- **Docs:** `agents.md` (ENUM forbidden + cross-DB IT section).

## 6. Result
H2 + MariaDB + PostgreSQL all boot the full Shio context against the real
`db.changelog-master.yaml`. 9 IT tests (3 × 3 engines) green; 103 unit tests
still green.
