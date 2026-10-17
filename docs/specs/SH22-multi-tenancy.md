# SH22: Multi-tenancy / SaaS (core DB isolation), implementable spec

> Roadmap: [ROADMAP.md](../ROADMAP.md) Block F · Status: ✅ shipped (core slice) · deps: SH2

## 1. Context: what already existed (do not rebuild)

A partial multi-tenant foundation was already in the tree (`com.viglet.shio.tenant`):

- `ShTenantContext`: `ThreadLocal<String>` current tenant, `DEFAULT_TENANT = "shio"`.
- `ShTenantFilter`: sets the context from the OIDC `workspace_id` claim (falling back
  to the authenticated username), **only when `shio.multi-tenant=true`**; clears it in
  `finally`. Registered after `BasicAuthenticationFilter` in `ShSecurityConfig`.
- `ShTenantResolver`: Hibernate `CurrentTenantIdentifierResolver<String>` +
  `HibernatePropertiesCustomizer` registering `hibernate.tenant_identifier_resolver`.
- `ShTenantBackfillRunner`: at startup, native `UPDATE <t> SET tenant_id='shio' WHERE
  tenant_id IS NULL` over a `TENANT_TABLES` list (the SH26 fix made the names real).
- Hibernate native `@TenantId` (DISCRIMINATOR strategy) already on **6** entities:
  `ShApiToken`, `ShSiteWebhook`, `ShPostTypeAttr`, `ShRelatorItem`, `ShWorkflowTask`,
  `ShHistory`. The baseline already defines `TENANT_ID VARCHAR(255)` columns on the
  `ShObject` and `ShReference` tables.

**The gap SH22 closes:** the *active content store leaked across tenants*. With
`multi-tenant=true` the discriminator was applied to the 6 entities above, but **not** to
the content tree that actually serves a site:

1. `ShObject` (the JOINED-inheritance **root** of `ShSite`/`ShFolder`/`ShPostType`) had the
   `TENANT_ID` column but **no `@TenantId`** → reads unfiltered.
2. `ShPostUnified` (`sh_post_unified`, the unified DRAFT/PUBLISHED post store that replaced
   `ShPost`/`ShPostAttr`) had **no column and no annotation**, the critical leak.
3. `ShReference` had the column but **no `@TenantId`**.

(Legacy `ShPost`/`ShPostAttr`/`*Draft` are no longer mapped JPA entities, only legacy
tables remain, so they cannot leak via Hibernate and are left untouched.)

## 2. Goal & scope

**In scope (this slice)**: make the active content tree genuinely tenant-isolated using
the existing Hibernate `@TenantId` mechanism, and *prove* it:

- `@TenantId` on `ShObject`, `ShPostUnified`, `ShReference`.
- A Liquibase migration adding `TENANT_ID` to `sh_post_unified` (the only one missing a
  column), + that table added to the startup backfill.
- An isolation IT proving tenant A's rows are invisible to tenant B (unified posts + the
  object tree, across polymorphic and concrete-subclass queries).

**Out of scope (deferred follow-ups)**: a tenant **catalog** (`ShTenant` entity + CRUD/
provisioning API), per-tenant `file_source` storage, tenant-keyed CDA HTTP cache, per-tenant
post-type **seeding**, and a console tenant-management UI. The runtime tenant identity is
**unchanged**: the `workspace_id`-claim/username model `ShTenantFilter` already implements.

## 3. Design: validated Hibernate mechanics

### 3.1 `@TenantId` on the JOINED root (`ShObject`)
One `@TenantId` field on the `@Entity` **root** filters polymorphic (`from ShObject`) *and*
concrete subclass (`from ShFolder`) queries, subclass SELECTs join back to the root table
where the `tenant_id = ?` predicate sits. The discriminator column lives on the **root table
only**, so `ShSite`/`ShFolder`/`ShPostType` need **no new column and no annotation**
(the baseline already created `ShObject.TENANT_ID nullable:false`).

### 3.2 `@TenantId` + composite `@IdClass` (`ShPostUnified`)
`@TenantId` is an ordinary discriminator attribute, independent of the key. `tenantId` is
**not** added to `ShPostUnifiedId` (IdClass fields map 1:1 to the `@Id` fields `id`+`state`);
Hibernate simply adds `tenant_id = ?` alongside the `(id, state)` PK. Existing `@Index`es
are untouched.

### 3.3 Single-tenant safety + ordering
With `multi-tenant=false`, `ShTenantFilter` never sets the context, the resolver returns
`"shio"`, and every row is written/read under `'shio'`, **no behavior change**.
`ShObject`/`ShReference` columns are `nullable:false` (no NULL rows exist). `sh_post_unified`
is already populated, so its new column is added **NULLABLE** (a `NOT NULL` ALTER would fail)
and the startup backfill (runs *after* Liquibase, *before* the first request) fills `'shio'`,
hence `sh_post_unified` **must** be in `TENANT_TABLES`. Once a row has a NULL `tenant_id` and
`@TenantId` is live it becomes invisible, so the column+backfill land before the annotation
takes effect within a single deploy: Liquibase → backfill `ApplicationRunner` → first request.

### 3.4 The PostgreSQL casing trap: the column MUST be lowercase `tenant_id`
The first build surfaced a latent cross-DB defect (the SH26 ITs caught it): the Postgres
schema IT failed to boot with `column spt1_1.tenant_id does not exist`, while H2 + MariaDB
passed. Root cause, Hibernate emits the **`@TenantId` filter predicate UNQUOTED**
(`where x.tenant_id = ?`) *even with* `globally_quoted_identifiers=true` (the SELECT
projection *is* quoted; only the discriminator predicate is not). Under SH26's
`QUOTE_ALL_OBJECTS`, an exact-case `TENANT_ID` column is created; PostgreSQL folds the
unquoted predicate ref to lower-case and never finds it. (H2 with
`CASE_INSENSITIVE_IDENTIFIERS` and MariaDB's case-insensitive columns both tolerated it,
which is why every prior `@TenantId` entity "worked": the startup seed never ran a
tenant-filtered SELECT against them, and the native backfill, already lowercase
`tenant_id`: silently failed on Postgres table-name folding too.)

**Fix:** the tenant discriminator column is **lowercase `tenant_id`** everywhere. Lower-case
satisfies all three reference styles: the quoted projection (`"tenant_id"`), the unquoted
folded predicate (`tenant_id`), and the lower-case native backfill. The 9 `@TenantId`
entities use `@Column(name = "tenant_id")`; the columns are renamed in SH26's baseline
(7 tables) + `v2026.2.1` (`site_webhook`), each edited changeset carrying `validCheckSum: ANY`
(SH26's own precedent) so databases that already ran the original baseline accept the edited
checksum. Unmapped legacy/draft tables (`ShObjectDraft`, `ShPostAttr`, `ShPostDraftAttr`,
`ShReferenceDraft`, `ShRelatorItemDraft`) keep `TENANT_ID`, no entity maps them, so they are
never queried by Hibernate. **Do NOT backtick-quote the column** (`` `TENANT_ID` ``): that
quotes the projection but the predicate stays unquoted, so it does not fix Postgres.

The flip side surfaced in the **unit tests**: a default H2 folds the unquoted predicate
*up* to `TENANT_ID`, missing the quoted lower-case `"tenant_id"` column. So the unit-test
datasource (`src/test/resources/application.properties`) must carry the same load-bearing
`DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true` H2 params the dev/prod URL (and
the SH26 schema ITs) already use, with them, the case-insensitive comparison matches on H2,
MariaDB matches case-insensitively, and PostgreSQL matches the lower-case column exactly.

### 3.5 Resolver cleanup
`ShTenantResolver.resolveCurrentTenantIdentifier()` had a dead `… ? DEFAULT : DEFAULT`
ternary (same value both branches, `ShAppProperties` unused). Collapsed to
`return ShTenantContext.DEFAULT_TENANT;` and the unused `ShAppProperties` wiring dropped,
behavior identical (the multi-tenant gate lives entirely in `ShTenantFilter`).

## 4. Changes

- **Entities** (idiom `@TenantId @Column(name="tenant_id", updatable=false) private String tenantId;`):
  the 3 newly-scoped, `persistence/model/object/ShObject.java` (manual accessors),
  `persistence/model/post/unified/ShPostUnified.java` (explicit accessors; `ShPostUnifiedId`
  **unchanged**), `persistence/model/reference/ShReference.java` (field-level `@Getter`,
  class-level Lombok `@Setter`), **plus** the 6 pre-existing (`ShApiToken`, `ShSiteWebhook`,
  `ShPostTypeAttr`, `ShRelatorItem`, `ShWorkflowTask`, `ShHistory`) lowercased from `TENANT_ID`
  to `tenant_id` (§3.4).
- **Migration:** new `db/changelog/v2026.2.4.yaml`, `addColumn tenant_id VARCHAR(255)`
  (nullable) on `sh_post_unified`, with the `objectQuotingStrategy: QUOTE_ALL_OBJECTS` +
  `preConditions onFail MARK_RAN` + negated `columnExists` idiom (copied from v2026.2.3);
  `include` line added to `db.changelog-master.yaml`.
- **Baseline rename (§3.4):** in `v2026.2_baseline.yaml` the 7 `@TenantId`-entity tables
  (`ShHistory`/`ShObject`/`ShPostTypeAttr`/`ShReference`/`ShRelatorItem`/`ShWorkflowTask`/
  `api_token`) and in `v2026.2.1.yaml` (`site_webhook`) the `TENANT_ID` column is renamed to
  `tenant_id`, each edited changeset gaining `validCheckSum: ANY`. Unmapped legacy/draft
  tables keep `TENANT_ID`.
- **Test datasource (§3.4):** `src/test/resources/application.properties` H2 URL gains
  `DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true` to mirror dev/prod.
- **Backfill:** `"sh_post_unified"` added to `ShTenantBackfillRunner.TENANT_TABLES`. The SH26
  `ShH2/MariaDb/PostgresSchemaIT` loop this list (asserting via `UPPER(COLUMN_NAME)='TENANT_ID'`,
  case-insensitive) so they assert `sh_post_unified`'s column on all three engines, no IT edit.
- **Resolver:** `tenant/ShTenantResolver.java` dead-code removal.

## 5. The isolation IT (`com.viglet.shio.tenant.ShTenantIsolationIT`)

Mirrors `ShH2SchemaIT` (no Docker, runs on every `mvn verify`): full `@SpringBootTest` on
in-memory H2 with the prod-like params, Liquibase enabled, **plus `shio.multi-tenant=true`**.

Because the `CurrentTenantIdentifierResolver` resolves the tenant **once per session open**
(`validateExistingCurrentSessions()=false`), each tenant phase runs in its **own
transaction** via `TransactionTemplate` (an `asTenant(tenant, supplier)` helper sets the
`ShTenantContext` ThreadLocal, runs the unit of work in a fresh transaction, then clears it).

- `unifiedPostsAreIsolatedPerTenant`: saves 2 `ShPostUnified` (DRAFT, `data="{}"`) as
  `alpha`, 1 as `beta`; asserts `beta` sees only its own row via `findByState` and cannot
  resolve alpha's id via `findByIdAndState`, and vice-versa for `alpha`.
- `objectTreeIsIsolatedAcrossPolymorphicAndConcreteQueries`: saves a `ShFolder` as `alpha`;
  asserts `beta`'s `count(o) from ShObject o` **and** `count(f) from ShFolder f` both
  exclude alpha's row (proving the discriminator hits the polymorphic root *and* the concrete
  subclass), and `beta` cannot resolve alpha's folder by id while `alpha` still can.

## 6. Known limitation (addressed by the follow-ups, §8)

In true multi-tenant mode a brand-new tenant does **not** automatically see the system
post-types of `'shio'`, those rows belong to the `shio` tenant. This is closed by the
per-tenant provisioning follow-up (§8 Part C).

## 7. Verification

- `./mvnw -pl shio-app test`: full module suite incl. `ShLiquibaseChangelogAgnosticTest`
  (confirms `v2026.2.4` is `VARCHAR` / no banned types).
- `mvn verify`: Failsafe ITs: `ShTenantIsolationIT` + `ShH2SchemaIT` need no Docker;
  `ShMariaDbSchemaIT`/`ShPostgresSchemaIT` run when Docker is present and now assert
  `sh_post_unified.TENANT_ID` on MariaDB/PostgreSQL.

## 8. Follow-ups: the SaaS layer (shipped on top of the core)

Built incrementally, each part green on `mvn verify` (112 unit + 14 IT) + the console build.

- **A · Tenant catalog.** `ShTenant` (`sh_tenant`, **not** `@TenantId`, the global catalog)
  + repository + admin CRUD on `ShTenantAPI` (`ROLE_ADMIN`-gated writes via `ShSecurityConfig`;
  the reserved `shio` tenant is rename/disable/delete-proof, seeded each boot by
  `ShTenantOnStartup`). Liquibase `v2026.2.5.yaml`. Tests: `ShTenantAPITest`.
- **D · Delivery-path tenant resolution.** `ShApiTokenFilter` resolves the token
  **tenant-agnostically** (`ShApiTokenRepository.findByTokenAcrossTenants`, native, the derived
  finder is itself `@TenantId`-filtered) and propagates the token's tenant via a request
  attribute (`ShTenantFilter.TENANT_REQUEST_ATTR`) that `ShTenantFilter` prefers over the
  principal. So token CDA/GraphQL delivery is scoped to the content owner's tenant. Proof:
  `ShTenantCdaDeliveryIT`. **Drift fix:** `v2026.2.6.yaml` adds `api_token.scope`,
  `api_token.environment` and the `api_token_site` table, SH2/SH5 added these via
  `ddl-auto=update` with no changeset, so a clean Liquibase DB couldn't authenticate a token
  (the SH26 ITs never insert/select one, so they missed it).
- **C · Per-tenant provisioning.** `ShTenantProvisioningService.provision(tenantId)` copies the
  default tenant's post-types into a new tenant, reads under `shio`, writes under the new
  tenant in **separate transactions** (resolver binds tenant per session-open), via
  `ShPostTypeAdminService.provisionFromDef` (reuses the SH16 attr/widget engine but preserves
  `system`, which `upsert` guards). Auto-run on `create`; idempotent
  `POST /api/v2/tenant/{id}/provision`. Proof: `ShTenantProvisioningIT`.
- **F · Tenant-keyed cache.** `ShCdaCacheControlFilter` emits `Vary: Key` so shared caches key
  by token (→ tenant/site/env); a body-hash `ETag` collision is harmless (a `304` reuses the
  requester's own body). Test: `ShCdaCacheControlFilterTest.cdaGetVariesByToken`.
- **B · Console UI.** A **Tenants** admin page (`tenant.list.page.tsx` / `tenant.page.tsx`)
  mirroring the API-token pages (list/create/edit/delete + Provision) wired into routes,
  sidebar and en/pt i18n (`tenantAdmin.json`); `ShTenantService` gains the CRUD + `provision`.
- **E · Not applicable.** Per-tenant `file_source` storage: the file upload/serve subsystem is
  **not implemented** in the current backend (no `WebMvcConfigurer`/resource handler, no upload
  controller, no storage service, only a legacy `permitAll` matcher on `/file_source/**`).
  There is nothing to isolate; when the feature is (re)built it must resolve
  `file_source/{tenantId}/...` from `ShTenantContext` with a path-traversal guard (mirror
  Turing's `TurFilesystemStorageService`).

### Still open (future)
Tenant quotas/billing, nav gating by `multiTenant`/role, and the per-tenant file storage of
Part E once the upload feature exists. Cross-tenant superadmin impersonation was SH44 and is
**retired** (→ [CHANGELOG.md](../CHANGELOG.md) Block F): a console-only privileged switch is
what the "No feature whose only interface is the console" non-goal forbids, so the
principal-derived `ShTenantContext` resolution stays the only way a tenant is entered.

- `./mvnw -pl shio-app test`: full module suite incl. `ShLiquibaseChangelogAgnosticTest`
  (confirms `v2026.2.4` is `VARCHAR` / no banned types).
- `mvn verify`: Failsafe ITs: `ShTenantIsolationIT` + `ShH2SchemaIT` need no Docker;
  `ShMariaDbSchemaIT`/`ShPostgresSchemaIT` run when Docker is present and now assert
  `sh_post_unified.TENANT_ID` on MariaDB/PostgreSQL.
