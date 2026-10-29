# Database schema, Liquibase, multi-tenancy

> Split out of the repo-root `agents.md` (see its routing table for the index).
> Read this before touching the area it covers. Detail here is authoritative.

## Database Schema (Liquibase, follows Turing)

The schema is owned by **Liquibase**, NOT by Hibernate. `spring.jpa.hibernate.ddl-auto=none`
in `application.properties`, never set it back to `update`/`create` for the app
(schema drift on `@TenantId` columns was the reason we moved off it).

### Layout
- Changelogs: `shio-app/src/main/resources/db/changelog/`
  - `db.changelog-master.yaml`: `include`s every versioned file in order (wired via `spring.liquibase.change-log`).
  - `v2026.2_baseline.yaml`: generated baseline (full `createTable` set incl. `TENANT_ID`).
  - New features add `vX.Y.Z.yaml` + one `include` line in the master.
- `shio-app/liquibase.properties`: config for the `liquibase-maven-plugin` (diff/generate only; the app applies changelogs at boot via `spring.liquibase.*`).
- Dev DB: `jdbc:h2:file:./store/db/shioDB` (relative to `shio-app/`).

### Load-bearing H2 URL params
The datasource URL **must** keep `;DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true`.
Liquibase creates tables with unquoted mixed-case names (`ShPostTypeAttr`), while
Hibernate (`globally_quoted_identifiers=true`) queries them **quoted**
(`"ShPostTypeAttr"`, `"TENANT_ID"`). The params make the casing match, without
them you get `Column "..." not found` at runtime.

**Characterize by execution, not by argument (SH122, kept here by SH356).** The `@TenantId`
JOINED-join hazard's boundary was guessed twice and wrong twice, first "projections only", then
"not predicates", each time from a plausible reading of the evidence, which was shipped `WHERE`
queries with green tests. Running each form settled it in minutes and produced a rule both correct
and simpler than either guess. Where a lint's scope depends on where a runtime failure actually
falls, the cheapest way to find the line is to cross it on purpose.

**And the same method found it fixed (SH1290).** Spring Boot 4.1.2 brought Hibernate 7.4.13, and
the four tests that pinned the hazard as a prepare-time failure stopped seeing one. Running each form
again, under two tenants and on H2, PostgreSQL 16 and MariaDB 11.4, gave one answer: Hibernate now
emits `join ("ShPostType" spt1_0 join "ShObject" spt1_1 on ...) on ... and spt1_1.tenant_id = ?`, so
the predicate lands on the alias that has the column, and a name predicate naming another tenant's
post type returns nothing. `ShTenantJoinHazardTest` asserts that, with two tenants, because the
failure that matters for a discriminator is a leaked row, not an exception. The `agents.md` invariant
went with it. `ShTenantJoinLintTest` stays: the `.id` form reads the FK column already on the owning
table and needs no join, so the rule is now about cost and one spelling, not correctness.

### Adding a migration
1. Hand-write a `vX.Y.Z.yaml` changeset (preferred for incremental changes, see
   Turing's `turing-app/.../db/changelog/vX.Y.Z.yaml` for the `addColumn` /
   `preConditions: onFail: MARK_RAN` pattern), **or** regenerate via diff:
   `mvn -pl shio-app org.liquibase:liquibase-maven-plugin:5.0.3:generateChangeLog -Dliquibase.url=... -Dliquibase.outputChangeLogFile=...`
   (invoke the plugin goal directly so the frontend build is skipped).
2. Add the `include` line to `db.changelog-master.yaml`.
3. Keep types **DB-agnostic**, `ShLiquibaseChangelogAgnosticTest` fails the build on
   `TIMESTAMP WITH TIME ZONE`, `CHARACTER LARGE OBJECT`, `BINARY LARGE OBJECT`,
   `BOOLEAN`, and `ENUM(`. Use `TIMESTAMP`, `CLOB`, lowercase `boolean`, and
   `VARCHAR` (map enums `@Enumerated(STRING)`) instead. **`ENUM(...)` is NOT
   allowed**, H2/MySQL/MariaDB accept inline `ENUM`, but PostgreSQL rejects it
   (`type "enum" does not exist`), so the SH26 cross-DB ITs would fail. The lint
   is only a textual guard; the real proof that the changelog applies on
   MariaDB + PostgreSQL is the SH26 Testcontainers ITs (see below).
4. **A constraint goes in the changeset *and* on the entity, both, every time (SH729).**
   Unique index, unique constraint, `addNotNullConstraint`: declare it as
   `@Table(indexes = @Index(..., unique = true))` or
   `@Table(uniqueConstraints = @UniqueConstraint(...))` or `@Column(nullable = false)`
   as well. `ShLiquibaseEntityConstraintLintTest` fails the build naming the pair that
   disagree, so you do not have to remember this, but you do have to know *why*, because
   the failure it prevents is invisible: **the unit suite runs with
   `spring.liquibase.enabled=false` and builds H2 from entity metadata**, so a
   changelog-only constraint simply is not there while the suite runs. SH719 shipped a
   unique index and a test that a second insert is refused; the test passed **by
   performing the insert**, and was caught only because it had been written to fail first
   and did not. The cross-DB ITs do execute the changelog, but they are opt-in since
   SH415 and skip without Docker, they answer *does the migration apply*, not *does the
   suite know about it*. The comparison is by column **set**, never by index name:
   Hibernate invents its own identifier for a `@Column(unique = true)`, and the 2026.2
   baseline carries generator names like `UK8q73y8bnx7kpktylf71gcco05_INDEX_9`.
5. **Name a table exactly as its `createTable` did (SH1089).** Two conventions coexist: the
   SH26 baseline created PascalCase tables (`ShPostTypeAttr`, `ShSite`), and every changelog
   since v2026.3.36 creates snake_case ones (`sh_post_unified`). A change to a baseline table
   uses the baseline's spelling. SH1010 wrote `sh_post_type_attr`, passed 2233 tests, and a
   clean store refused to start, because the suite never runs Liquibase and the database does
   not fold case. `ShLiquibaseChangelogReferenceLintTest` walks the changelogs in master order
   and fails naming the file, changeset and identifier when a change names a table or column
   no earlier changeset created, spelled exactly, suggesting the created spelling when the two
   fold together. SH729's lint folds on purpose and could not see this. The change types it
   reads are listed in `UNDERSTOOD`; a new type is a failure until the walk is taught what it
   names.

### Cross-DB schema-drift ITs (SH26)
`ShMariaDbSchemaIT` / `ShPostgresSchemaIT` (extending `ShCrossDatabaseSchemaITBase`,
package `com.viglet.shio.tenant`) boot the **full Spring context** against a real
MariaDB / PostgreSQL Testcontainers engine with `spring.liquibase.enabled=true` +
`ddl-auto=none`, proving the full `db.changelog-master.yaml` applies, every entity
maps onto it (the `ShOnStartup` seed inserts run real DML), and
`ShTenantBackfillRunner.TENANT_TABLES` resolves to real tables. Run via `mvn verify`
(failsafe); auto-skipped without Docker (`@Testcontainers(disabledWithoutDocker=true)`).
Note: most tables are **PascalCase** entity names (no `@Table`, e.g. `ShPostTypeAttr`),
the backfill list must use those, not snake_case, or it silently no-ops.

### Multi-tenancy (SH22: core DB isolation)
Tenant isolation uses Hibernate's native `@TenantId` (DISCRIMINATOR), gated by
`shio.multi-tenant` (default `false`). `ShTenantFilter` resolves the tenant from the
OIDC `workspace_id` claim → username and sets `ShTenantContext` (ThreadLocal, default
`"shio"`); `ShTenantResolver` (a `CurrentTenantIdentifierResolver`) hands it to Hibernate.
`@TenantId` covers the active content tree: **`ShObject`** (the JOINED root, one `@TenantId`
there filters polymorphic *and* `ShSite`/`ShFolder`/`ShPostType` subclass queries; subclasses
need **no** column of their own), **`ShPostUnified`** (`tenantId` is **not** part of its
composite `@IdClass`, Hibernate adds `tenant_id=?` next to the `(id,state)` PK), and
`ShReference` (dropped by SH1100, below), plus the 6 pre-existing ones (ShApiToken/ShSiteWebhook/
ShPostTypeAttr/ShRelatorItem/ShWorkflowTask/ShHistory). **Gotchas:**
1. **The discriminator column MUST be lowercase `tenant_id`.** Hibernate emits the
   `@TenantId` *filter predicate* UNQUOTED (`where x.tenant_id = ?`) even with
   `globally_quoted_identifiers=true` (only the SELECT projection is quoted). PostgreSQL
   folds the unquoted ref to lower-case, so an exact-case `TENANT_ID` column (what
   `QUOTE_ALL_OBJECTS` creates) is never matched (`column does not exist`). A default H2
   folds it UP instead, so a quoted lower-case `"tenant_id"` column would be missed there.
   Lower-case `tenant_id` + the load-bearing `DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true`
   H2 params (which both the dev DB **and the unit-test datasource** must carry) make all
   three engines agree. Backtick-quoting the column does NOT help, it quotes the projection,
   not the predicate. The baseline + `v2026.2.1` columns were renamed `TENANT_ID`→`tenant_id`
   with `validCheckSum: ANY`; unmapped legacy/draft tables keep `TENANT_ID` (never queried).
2. A populated table's new `tenant_id` must be added **nullable** and listed in
   `ShTenantBackfillRunner.TENANT_TABLES` so the startup backfill sets `'shio'` (a `NOT NULL`
   ALTER fails; a NULL `tenant_id` under live `@TenantId` is invisible).
3. The resolver resolves the tenant **once per session-open**
   (`validateExistingCurrentSessions()=false`), so tests that switch tenants must use a **new
   transaction per tenant**, see `ShTenantIsolationIT` (the proof that A can't see B; no Docker).

**The agent surface needed its own proof (SH84).** `ShAgentTenantIsolationIT` runs every Block H
surface from a second tenant, context pack, `find` including full-text, `read`, the address
resolver **with its `id:` escape hatch**, the batch engine, desired-state plan/apply, and each
one fails *absent* rather than *forbidden* (the SH2 rule), with alpha's content intact
afterwards. Not a corollary of `ShTenantIsolationIT`: storage isolation is about rows, and this
surface resolves content by **address** and exists to describe a whole instance in one call, so
a context pack carrying another tenant's sitemap would look exactly like a working context pack.
The invariant is standing, not per-task, *the agent path is never a privilege escalation*. Each
expected failure runs in **its own transaction**, because a failed `@Transactional` call marks
the current one rollback-only and every assertion after it would silently never execute.

**SaaS follow-ups (shipped on top of the core):** a tenant **catalog**, `ShTenant`
(`sh_tenant`, deliberately **not** `@TenantId`, the global catalog) + admin CRUD on
`ShTenantAPI` (`ROLE_ADMIN`-gated writes; reserved `shio` tenant seeded by `ShTenantOnStartup`,
undeletable). **Delivery-path tenant resolution:** `ShApiTokenFilter` looks up the token via
`ShApiTokenRepository.findByTokenAcrossTenants` (native, the derived `findByToken` is itself
`@TenantId`-filtered, a chicken-and-egg) and propagates the token's tenant via
`ShTenantFilter.TENANT_REQUEST_ATTR`, which `ShTenantFilter` prefers over the principal, so CDA
delivery is scoped to the content owner's tenant. **Per-tenant provisioning:**
`ShTenantProvisioningService` copies the default model into a new tenant (read-under-`shio` +
write-under-new-tenant in separate txns; `ShPostTypeAdminService.provisionFromDef` preserves the
`system` flag `upsert` guards). **Cache:** `Vary: Key` on CDA responses. **Console:** a Tenants
admin page. **Gotcha (drift):** SH2/SH5 added `api_token.scope`/`.environment` + `api_token_site`
via `ddl-auto=update` with no changeset, invisible to the SH26 ITs (which never touch a token),
so a clean Liquibase DB couldn't authenticate a CDA token; fixed in `v2026.2.6.yaml`. **Follow-up
(SH47):** per-tenant `file_source` storage, the upload/serve subsystem now ships (SH38, see
**Static files** above), id-keyed under `shio.file-source.path`; SH47 prefixes `tenantId` into
that layout from `ShTenantContext`.

### Tests
Tests skip Liquibase (`spring.liquibase.enabled=false`) and build the schema from
entity metadata via `ddl-auto=create-drop` on a fresh in-memory H2 per run.

**Which means a constraint declared only in the changelog does not exist in the unit suite,
and this cost a green run on a broken fix (SH719).** The changelog added a unique index; the test
that asserted the second insert was refused **passed by inserting it**, because H2's schema came
from `@Table(indexes = …)` and knew nothing about the changeset. A guard the tests cannot see is a
guard whose regression nothing reports until production. So a constraint that the code depends on
is declared **in both places**, `@Table(indexes = @Index(..., unique = true))` for the suite,
Liquibase for every real database, and the duplication is deliberate rather than an oversight.
The cross-DB ITs are the only place the changelog itself is exercised, which is the other half of
why `-DskipITs=false` is the rule on a schema change.

**The test context cache has a chosen ceiling, and a guard keeps room under it (SH347).**
`spring.test.context.cache.maxSize=64` in `src/test/resources/application.properties`, because
SH315's isolated-datasource rule has produced **28** in-memory databases and the framework default is
**32**. Crossing it is not a time cost: an evicted context is rebuilt, `ddl-auto=create-drop`
recreates the schema mid-run, and rows a later test in the *shared* datasource asserts about are gone,
which surfaces as a test failing only in the full suite and passing in isolation. That happened once
(`ShAgentDurableIncidentTest#theCapDropsTheOldest`) and read as a flake. `ShContextCacheHeadroomTest`
now fails while there is still room, and asserts the ceiling is **configured** rather than inherited,
since the original defect was a limit nobody had chosen. It counts a **lower bound**, a context key is
the whole property set, not the URL, which is what the margin of 8 covers; widen the margin before
widening the counter.

**Mockito runs as a `-javaagent`, not by self-attaching (SH250).** Surefire and failsafe both carry
`@{argLine} -javaagent:${org.mockito:mockito-core:jar}`, with the path supplied by
`maven-dependency-plugin`'s `properties` goal and `argLine` declared **empty** in `<properties>` so
the late replacement always resolves. **`@{argLine}` and never `${argLine}`**, JaCoCo already sets
that property, so the plain form would replace its agent and coverage would silently stop being
collected. Before this, instrumentation was installed by attaching to the running JVM, which
succeeds or fails on class-loading order: `ShTenantSuspendedAccessTest` failed six of six once and
passed on every re-run. A flake is worse than a break, because it teaches people that red might mean
nothing. If you add another agent, append to `argLine` the same way.


## `sh_site_form`: where a site's submissions land (SH632)

A table keyed by site, following `site_webhook` and for its reason: per-site delivery configuration
an operator owns, which a site does not need to carry in order to be a site. Columns are the SH22
shape (lowercase `tenant_id`, `NOT NULL`, `@TenantId` on the entity) and `site_id` is a plain
column rather than an association, because navigating past `.id` of an `ShObject` subclass emits the
tenant predicate against an alias that has no such column.

**The design decision is what is *not* here.** 2018's `ShFormConfiguration` carried `folder` as a
**template** setting, and that is the one part of the 2018 answer SH364 does not adopt: a folder
chosen in a template is a folder an attacker chooses, because a template is content and content is
what a compromised author edits. So the destination is configuration *of the site*.

Nothing here stores a submission. A submission **is a post**, so the trash, the export, the ACL and
the agent surface already apply, that was SH364's third question and it resolves by not building
anything. A row says only: which folder, which types, whether the site is accepting.

**Both defaults point at refusal.** `enabled` is `false` on create, so making the row is not the same
act as opening the door; an empty `allowed_types` invites nothing rather than everything. The API
refuses a second row for a site and names the one to edit instead (P6), because two configurations
mean the endpoint resolves to whichever sorts first and an operator editing the other would watch
their change have no effect, a defect with no symptom where it is made.

**Two guards classified it before it could ship quietly**, which is worth recording because both
failed the build rather than being remembered:

- `ShAnonymousRouteAccessTest` (SH363) refused the new mappings until declared `AUTHENTICATED`.
- `ShStatelessReachabilityLintTest` (SH339) refused until the P1 question was answered in writing.
  It is **`CONSOLE_ADMIN`**, and unlike every other row there the reason is not that the capability
  is stronger than the agent's: an agent can already create a folder and a post-type, so naming
  those adds nothing. What `enabled` adds is different in kind, it converts an authenticated
  capability into a **public** one. The agent still builds the form; the curator opens the door. If
  that ever reads as too strict the fix is a scope that may configure but not enable, not moving the
  row.

## The tenant a pool thread does not have (SH612)

`ShTenantContext` is a thread-local and `@TenantId` is resolved from it at the moment a Hibernate
session opens. Two recorders wrote from a pool thread that never had one set, the webhook
dispatcher inside `dispatchAsync(...).whenComplete(...)` and the Turing listener inside
`executor.execute(...)`. Both run past `AFTER_COMMIT`, where there is no request, no session and no
tenant, so the store's `REQUIRES_NEW` write was stamped with the default discriminator, `shio`.

**SH22 did not miss this so much as never reach it.** Until SH204 the row held a URL, a message and
a timestamp, and being filed under the wrong tenant made it *invisible* rather than *wrong*. SH204
added a site id, so a misfiled row now carries an identifier belonging to a tenant that cannot see
it: it matches no scoped read and resolves to no name in an unrestricted one. Nothing leaks, but
the operator whose site actually failed is the one person who cannot see the failure, and the
webhook that never arrived is exactly the incident a tenant is paying for.

**`ShTenantHandoff` carries it the way `siteId` and `what` are already carried**: a plain value read
while the tenant is still bound, carried by the lambda, applied on the far side. Nothing queries and
nothing lazily initialises, which is what makes a value safe to cross this boundary at all.

**The restore is not housekeeping, it is the other half of the fix.** A pool thread is *reused*.
Setting the tenant and walking away leaves it bound for whatever the pool runs there next, and that
task's write is then stamped with a tenant it never had, a foreign row **created** rather than one
misfiled, which is strictly worse than the defect being repaired. So the previous value is saved and
put back in a `finally`, and an absent capture **clears** rather than sets: `ShTenantResolver` falls
back to the default when nothing is bound, so "no tenant" has a meaning and it is not "keep whatever
the last task left".

### The instrument, and the third boundary it found

What decides whether reading two recorders was enough is a question a reading cannot answer, *does
any other `@TenantId` write happen off-thread?* Per SH338, the instrument lands with the fix:
**`ShOffThreadTenantLintTest`** classifies every thread boundary in `shio-app` as either
tenant-carrying or **declared, with a reason**. It strips comments first, because this project
explains its thread boundaries in prose and a lint that fires on the sentence describing a hazard is
one nobody keeps. It also asserts the walk still reaches the two files SH612 repaired, a scan that
found nothing passes a source lint perfectly.

It answered a question nobody had asked. `ShPostScheduler` is a timer with no tenant by
construction, and its finders read `ShPostUnified`, which is `@TenantId`, so under the default
discriminator **a non-default tenant's scheduled publish is not found at all and never fires**. That
is a different defect from SH612 rather than an instance of it (there is nothing to capture; the fix
is a sweep that iterates tenants), and it is **SH639**. The lint holds the file as a declared
exemption naming that task, so the exemption is a pointer rather than a silence.

Validated with `-DskipITs=false`, a tenancy change is the case the ITs exist for, and both schema
ITs were confirmed to have *run* rather than skipped without Docker.

## The sweep that ran as nobody (SH639)

`ShPostScheduler.sweep()` is a timer, so it runs on a thread that never had a tenant bound, and
`ShPostUnified` carries `@TenantId`, so Hibernate appended the default discriminator to
`findScheduledPublishDue`. **A post another tenant scheduled was not published late: it was never
found.** Nothing threw, so the incident log had nothing to say either.

It is the same absence SH612 found in the two off-thread incident recorders, one layer earlier and
with a larger consequence. SH612 misfiled the *record* of a failure; this silently declined to do
the work.

### Iterating, because a timer has no tenant to carry

SH612's fix captures the tenant on the committing thread. There is nothing to capture here, so the
sweep runs **one pass per tenant**, through `ShTenantHandoff.runAs` rather than by binding the
context itself, because the timer's thread is reused and a tenant left bound would stamp the next
tick with the last tenant of this one.

Three questions the shape had to answer:

- **A suspended tenant is skipped.** Publishing for an account the platform has stopped is the one
  outcome worse than publishing late, the request path already answers them 403, and a timer that
  ignored that would be a way around the suspension that leaves no request behind.
- **A tenant provisioned mid-sweep waits for the next tick.** The list is read once, so a pass runs
  over a consistent set; the next tick is sixty seconds away and a post due now was due before that
  tenant existed.
- **One tenant's failure does not abandon the rest**, for the reason each post already is caught
  individually: a sweep that stopped at the first bad row would let one tenant's broken content
  silence every other tenant's schedule.

**A single-tenant install still runs exactly one pass with nothing bound** and never reads the
tenant table, so the common install pays nothing for this.

### The coverage that existed could not see it

`ShPostUnifiedAPITest` exercises the transition by calling `publishScheduled(id)` directly, the
thing the sweep *delegates to*. It proves the publish works and says nothing about whether the sweep
ever finds the row, and the defect lived entirely in the finder's implicit predicate, which is the
one part that test bypasses. **`ShScheduledSweepTenantIT`** drives `sweep()` itself; it was
confirmed to fail with the fix reverted, and it is an IT because `shio.multi-tenant=true` is what
makes the discriminator mean anything (and because the alternative was a fifth multi-tenant Spring
context in the unit suite).

## A tenant's stored bytes are kept, not counted (SH45)

`sh_tenant.storage_bytes` (BIGINT, NOT NULL, default 0, `v2026.3.25.yaml`) holds what a tenant is
storing, and `ShTenantStorageMeter` adjusts it as bytes arrive and leave.

**Why it is not a query, which is the whole design.** The other two metered resources are a
`count()` on an indexed, `@TenantId`-filtered repository. Storage has no such column: SH399 stamps a
file's size as `"SIZE"` **inside `ShPostUnified.data`, a VARCHAR of JSON**. So both obvious readings
are O(files) on a path `VigletQuotaService.check` runs before every upload, walking
`<root>/<tenant>` is disk I/O per check, and summing the JSON means loading and parsing every File
post, because a `JSON_VALUE`/`->>` predicate is engine-specific and the SH26 cross-DB ITs refuse it.

**The cost is drift, and `reconcile` is what answers for it** rather than a hope: SH47 gave every
tenant its own directory, so the true figure is one subtree away. It is deliberately **not
scheduled**, what cadence that runs at is an operator's decision, and a job nobody asked for is a
cost this task did not measure. `record` clamps at zero, because a total below empty is the one
reading that cannot be true.

**What moves it is `ShFileStorageService`, not the services above it (SH751).** `store`, `copy`,
`delete` and `deleteTenant` are where bytes are actually written and removed, and upload, replace,
the SH749 copy, the cleanup listener and tenant teardown are all callers of those four. Putting the
call in each caller is what SH751 records failing: three sites were supposed to remember, none did,
and a fourth arrived with SH749 while the rule was still unwritten. The meter is reached through an
`ObjectProvider` because the dependency is a genuine cycle, `reconcile` calls
`bytesOnDiskFor` on the storage service, and Spring refuses a constructor cycle outright.

Two rules keep the figure honest, and both have an assertion in `ShTenantStorageMeterTest`:

- **A delta is recorded exactly when `bytesOnDiskFor` would have counted it**: under
  `<root>/<tenant>`, never the pre-SH47 flat layout `resolve` can still hand back. Meter a legacy
  write and every reconcile moves the figure; a correction that always corrects reads exactly like
  the drift it exists to remove.
- **Deltas are differences, measured before and after.** `store` uses `REPLACE_EXISTING`, so a
  replace of the same name adds only the difference; counting "the size written" would overstate on
  every overwrite. `delete` measures before removing, because afterwards there is nothing to ask.

The acceptance test is the one §XII.29 named: **a reconcile after a mixed run of stores, replaces,
copies and deletes changes nothing.** Recording never throws, storage succeeding and accounting
failing must not be reported as a failed upload.

**There is no `checkCanStore` yet, and that is deliberate.** `VigletQuotaService.check(resource)`
compares the *current* count against the limit, which is exact for sites and posts because each
write adds one. Bytes do not: a single 1 GB upload under a 512 MB plan passes that test whenever the
tenant is at zero, so the check would advertise a guarantee it does not give.

**That is no longer true, and SH110 is what changed it.** `viglet-core` 2026.3.11 adds
`VigletQuotaService.check(resource, delta)`: "does what I am about to write fit", where the old
form asked "is this tenant already full". The algebra is worth noting rather than trusting:
`count + 1 > max` is the same predicate as `count >= max`, so the one-argument forms are the new
one at `delta = 1` and delegate to it rather than keeping a copy. No existing caller's behaviour
moved, and `VigletQuotaServiceTest` asserts that equivalence across the boundary.

`ShTenantQuotaService.checkCanStore(bytes)` is the Shio-side entry point, called on **both** write
paths and before either commits anything:

- **upload** charges `file.getSize()`, before the post row and before the bytes, a 402 raised after
  either leaves the tenant holding something they were just told they could not have.
- **replace** charges the **difference** (`new - stored`), because replacing a 900 MB video with a
  1 MB one frees 899 MB and charging the new size would refuse a write that makes the tenant
  smaller. A negative delta is a no-op in the shared check.

The overflow guard in the kernel is deliberate: `held + delta > max` wraps to negative on a delta
near `Long.MAX_VALUE` and would **admit** the write it exists to refuse, so the comparison is
`delta > max - held` instead.

## A tenant's API calls are a flow, so they get a table (SH45)

`sh_tenant_api_call` (`v2026.3.26.yaml`) is one row per **(tenant, day, surface)**, unique on those
three, moved only by addition. `ShTenantApiCallMeter` accumulates in heap and flushes to it;
`ShTenantApiCallFilter` classifies each request; `ShBillingExportService` reads a period out.

**Why not the shape the paragraph above just described.** Storage is a **level**, how much is held
right now, which is what `VigletResourceCounter.count(tenant, resource)` models and why one column
fitted. An API call is a **flow**: the billing question is calls *per period*, and a level has no
period to be asked about. A cumulative column grows forever and resetting it monthly is a scheduled
job nobody designed. So the period is part of the key and the row *is* the bucket. The period is a
UTC day, stored as ISO-8601 **text**: a month is the billing unit but cannot answer "how much so
far", an hour multiplies the rows by 24 for a question nobody asks, and text compares and orders
identically on all three engines where DATE arithmetic is where the dialects diverge.

**Why not `ShCdaRateLimiter`, which already counts calls.** Its own javadoc disqualifies it: the
buckets live in one JVM's heap, so a second node doubles the budget and a restart loses the count.
Fine for a burst guard, disqualifying for anything a bill is computed from.

**The accumulator in front of the table is not that defect again**, and the difference is worth
being precise about: it decides nothing from what it holds. The flush is `calls = calls + :delta`
**in the database**, and addition commutes, two nodes flushing 100 each leave 200 in either order,
which is exactly what a level could not do. A crash loses at most one flush window
(`shio.meter.flush-interval-millis`, default 60s; `@PreDestroy` flushes an orderly shutdown), and
the drain is `AtomicLong.getAndSet(0)` rather than removing the map entry, because removal loses
whatever a concurrent request added between the read and the removal.

**Three surfaces, recorded apart**: `cda` (`/api/v2/cda/**`, `/graphql`), `agent`
(`/api/v2/agent/**`, `/mcp`), `console` (everything else under `/api/**`). "CDA only, or the agent
and console too?" is a *pricing* question; totalling them here would throw the answer away with no
migration back. `401`/`403`/`429` are **not** counted, they cost no credential to produce, so
counting them would let a stranger run up a tenant's bill; a `404` is counted, because the instance
did the work of deciding.

**The filter is mounted at `LOWEST_PRECEDENCE - 30`, and that is load-bearing.** It puts the meter
*inside* the Spring Security chain, where `ShTenantFilter` has resolved the tenant and has not yet
run the clear it always does on the way out. Outside it, the meter reads an empty tenant on every
request and files nothing, a meter reading zero, which is the SH751 failure exactly.

**Not `@TenantId`-scoped**, like `sh_tenant` itself: it is the global usage catalog an operator
exports across every tenant. The unique key is declared **twice**, Liquibase and
`@Table(uniqueConstraints)`: per SH729, because `mvn test` builds H2 from entity metadata with
Liquibase off, and it is what turns a lost insert race into a retry instead of a duplicate bucket.

**Export is a hook, not an endpoint.** Publish a `ShBillingExporter` bean and it receives every
tenant's `ShTenantUsage` when the meter first notices the UTC day rolled; it is opt-in
(`shio.meter.export-on-rollover`, default off, P7), at-least-once per node, and implementations
must be idempotent on `(tenant, period)`. The pull side is `GET /api/v2/tenant/usage?from=&to=`,
scoped to the caller's own tenant, and it is the backstop for a period no node was up to watch
close. `usage(period)` is driven from the tenant catalog rather than from the meter rows, so a
tenant that used nothing is present with an empty map, an absent row is indistinguishable from a
tenant the export forgot.

## `sh_post_revision`: what a page was, after the row that held it is gone (SH986)

A post is `(id, state)` and `ShPostState` is exactly `DRAFT` and `PUBLISHED`, so `versions()` has
always returned at most two rows, SH14's own ledger entry says so. Publishing deletes the live row
and writes the draft over it, which means the bytes a visitor was reading a minute ago are **gone**:
not archived, not superseded, gone. `ShHistory` records that a publish happened and carries no
payload, so the review queue's undo was narrower than it read and a rollback had nothing to roll
back to.

**A separate table, not a third state.** A third state would be read by every query that filters on
state, indexed by Turing, delivered by the CDA and counted by the quota, each of which would have
to learn to exclude it, and the one that forgot would serve an archive row to a visitor. A revision
is not a state of the post; it is a record of what the post was.

Two columns are deliberately plain strings rather than associations:

- **`post_id` is not a foreign key.** The post's primary key is composite, so a foreign key would
  have to name a state, and the row it names is the one publishing deletes. A revision has to
  outlive exactly that deletion, which is the whole point of keeping it.
- **`post_type_id`, never the type's name**, per the JPQL invariant in `agents.md` and because a
  rename is a move (SH163): a record of what the post *was* names the thing that does not move.

**Where the capture happens is the guarantee.** `ShPostRevisionService.capture` runs inside the
publishing transaction, on the row that just became visible, so a revision exists for every state a
visitor ever saw, and a capture that rolled back took the publish with it. The *incoming* row is
captured rather than the outgoing one, which is what makes the claim true of the current live state
and not only of the ones already replaced.

A draft save is captured too, behind `shio.versions.capture-on-save`, **off** by default: a save
happens on every keystroke-sized edit an agent makes, and the question a curator asks is "what was
live on Tuesday". Retention is `shio.versions.keep`, fifty per post, pruned in the same write that
adds one, so the bound holds with no scheduled job, and an instance that never runs maintenance
still has a bounded table. The prune is by `version_no` and never by age: a page published once a
year would otherwise lose the only revision it has.

`version_no` is `max + 1` and **not** `count + 1`. A count reissues the number a pruned revision
used, the unique constraint refuses the insert, and it presents as publishing having stopped
working, long after the prune and nowhere near it. There is a test that forces exactly that
sequence rather than waiting for a table to fill.

A restore lands on the **draft**, never on the live row: the curator asked to go back to a version,
and publishing it is the separate deliberate act draft-default exists to keep separate (P7). The
restore is itself captured with reason `ROLLBACK`, because an undo that destroys what it undid is
the defect this table was added for, one level up.

It is a schema change, so it was validated with `-DskipITs=false` and Docker running:
`ShPostgresSchemaIT` and `ShMariaDbSchemaIT` both **ran** (19s and 29s, not the `0.00 s` of a
Docker-less skip), so the changelog is known to apply on all three engines rather than assumed to.

## `ShHistory.changed_fields`: which fields a write changed (SH989)

A nullable `VARCHAR(1024)` holding a **JSON array** of field names, written by
`ShContentAuditService` from what `ShPostUnifiedWriteService` computed in the same transaction,
update, publish, and both restores. A JSON array rather than a comma list because field names are a
post's JSON keys and nothing constrains them.

**The bound is the writer's**, SH544's lesson applied before the incident rather than after:
`ShChangedFields.encode` keeps the array inside `ShHistory.CHANGED_FIELDS_MAX_LENGTH`, and a list
that does not fit keeps the names that do and ends on `…+N`, the count left out. The width is measured
in UTF-16 units, never fewer than the characters a `VARCHAR` counts. `ShChangedFieldsTest` holds the
constant to the width the changelog declares, the way `ShAuditDescriptionLengthTest` holds
`description`'s, against the YAML, because `ddl-auto=none`.

**`null` and `[]` are different statements.** `null` is not recorded: a create (against nothing every
field is new, which tells a reader nothing), a delete, and every row written before `v2026.3.30`.
`[]` is recorded with nothing changed, a save that sent the same document. No backfill: an old row
has no second document to compare against, and inventing names for it would be the one wrong answer.

Validated with `-DskipITs=false` and Docker running: `ShPostgresSchemaIT` (6.9s) and
`ShMariaDbSchemaIT` (20.1s) both ran rather than skipping, so the changeset is known to apply on all
three engines.

## `sh_post_unified.schedule_requested_by`: who set a schedule (SH991)

A nullable `VARCHAR(255)` beside the two SH15 schedule columns, written by
`ShPostUnifiedWriteService.schedule` with the schedule and cleared when the draft is left with none,
including after the sweep fires the last one, so a stale name never attributes a schedule somebody
else set afterwards. The sweep still publishes and unpublishes as `scheduler`, which is what ran it; the
audit row it writes carries `(scheduled by <person>)` as its detail, read off the draft before the
transition. No backfill: a schedule set before `v2026.3.31` has no recorded requester, and inventing
one would be the one wrong answer. `include=schedule` on a read names it as `requestedBy`.

## `sh_release` and `sh_release_item` (SH993)

A release is a named set of posts in one site that goes live as one unit. `sh_release` holds the name
(unique per site, because it is the address and the projected file name), a description, `site_id`, a
state of PLANNED, SCHEDULED, PUBLISHED or ROLLED_BACK, `scheduled_at`, the last actor and the two
timestamps; `sh_release_item` holds `release_id`, `post_id`, `added_at` and `version_before`, the SH986
revision number the post was at when the release ran, which is where a rollback returns to, and null
until it runs.

`site_id` and `post_id` are plain VARCHARs and not foreign keys, for SH986's reason and the JPQL
invariant: a post's key is `(id, state)` so a foreign key would have to name the row publishing deletes,
and an `ShSite` is an `ShObject` subclass that JPQL must not navigate past `.id`. Both unique
constraints are declared on the entities as well as in `v2026.3.32.yaml`, since the unit suite builds
its schema from the metadata (SH729).

`sh_release.archived_at` (SH993) is the second changeset, `v2026.3.33`: a timestamp rather than a
boolean, for `deleted_date`'s reason, "when" is free here and answers the question a boolean raises.
Archiving hides a release from the context pack's listing and from the sweep's due query; it never
removes a row, and `find?release=` still reads what the release holds.

## `sh_post_revision.note`: what a person said about a moment (SH998)

`v2026.3.36` adds one nullable `VARCHAR(510)`. Today exactly one thing writes it: the reason a curator
gave for publishing past a failing gate, so `shio versions` and the console's history show the waiver
beside the version it belongs to rather than only in the audit feed. Nullable with no backfill,
every revision captured before the column existed has nothing to say, and a default sentence would be
a claim nobody made. It is bounded in `ShPostRevisionService.capture` for SH544's reason: the record
describes the write and must never be what refuses it, so a reason that ran long is clipped and the
publish still happens.

Why it is not a fourth value of the `reason` column: that one answers *why a snapshot was taken* and
its three values are the reasons a row exists at all. A waived publish is still a publish, the
snapshot is there because the row went live, so the waiver is a note on the revision and the enum
goes on answering the question it was written for.

## `sh_agent_run`: which agent a session id belongs to (SH999)

`v2026.3.37`. The primary key **is the session id**, not a surrogate: the transport mints it and it
is already unique, so a second key would let two rows claim one run, the one thing a lookup by
session must not find. There is no foreign key to `sh_history` either: the row is written on
`initialize`, before the run has written anything, and a run that wrote nothing is a fact worth
keeping rather than an orphan.

It follows that a run row can be **absent**, and that is by design: the REST agent surface accepts a
session id it did not mint (SH106), so a caller can write under a session that never introduced
itself. Both readers report the id in that case rather than inventing a name. No backfill, a run
that happened before this table existed has no name to give it.

Console sessions (`console:<curator>:<date>`, SH996) are never written here. They are people.

## `sh_agent_run.intent`: what a run was for (SH1000)

`v2026.3.38`, one nullable `VARCHAR(1000)`. A sentence, not a document: a run whose premise needs
more than a thousand characters is a run that should have been two, and a column that will hold a
plan invites one. It is clipped rather than refused, for SH544's reason and harder here than
anywhere, the record describes the run and must never be what stops it, so a client whose premise
ran long gets a shorter premise and not a failed handshake.

Writes are **merge, never replace**: a `null` argument leaves what is on record. That is what makes
`X-Shio-Intent` safe to send on every request and safe to send once, a later call that does not
repeat the sentence must not erase it.

## `sh_review_decision`: what a curator decided, and why (SH1001)

`v2026.3.39`. Unique on `(session, post_id)`: the row is the **current answer**, not a log of
answers, and a queue that had to fold a list per change to find out where it stands would be the
same read done twice. `post_id` is a plain VARCHAR for the JPQL invariant `agents.md` states, a
post's key is composite, so a foreign key would have to name a state, and the row it named is the
one publishing deletes.

A decision outlives the content it is about, which is the point of the table and a trap for a
fixture: a test that rebuilds its posts per run has to delete these too, or its answer depends on
what the previous test decided.

## `sh_note`: the channel back from a curator (SH1003)

`v2026.3.40`. `object_id` is a plain VARCHAR for the JPQL invariant `agents.md` states: a post's key
is composite, so a foreign key would have to name a state, and the row it named is the one
publishing deletes, a note has to outlive exactly that. `parent_id` is self-referential and
nullable, because a thread with no second message is by far the common case and a thread table for
it would be a join answering "no". `body` at 4000 is a paragraph, not a document: a thread on a page
must not become the page.

## `data` was a large object on PostgreSQL, and no SQL predicate over it worked (SH1099)

Found while taking SH1033's numbers, and it is the strongest argument in this file for why a green
`clean test` means least on a storage change. `ShPostUnified.data` is `@Lob String`. On H2 and
MariaDB that is a text column and every predicate over it behaves. On PostgreSQL **pgjdbc writes a
`@Lob` as a large object**: the `text` column stores an OID, the document lives in
`pg_largeobject`, and Hibernate resolves it on read, so the application is correct and the SQL is
blind. Measured on a 5,000-post fixture (`ShPostgresAssetUsageScanIT`): 5,001 rows, 5,001 large
objects, **zero** rows whose `data` is readable as text, and the entity read returning the right
document.

Two shipped surfaces were **wrong rather than slow** on every PostgreSQL instance, and SH1034 took
one of them away without meaning to:

- `findReferencingFileId`: where-is-this-used answered **empty**, so a curator was told an asset is
  unreferenced and deleted it. **Closed by SH1034**, not by a repair here: `listUsage` reads
  `sh_object_reference`, which is written from the entity at write time, so the read never touches
  this column. Moving a read off a broken column fixes that read and nothing else.
- `FULLTEXT_PREDICATE`, which matches `lower(cast(p.data as string))`, console and CDA search still
  find nothing in a page's body, which is exactly what SH240 added that predicate to fix. **This is
  what SH1099 is now about.**

It survived because the unit suite is H2 and the cross-DB ITs assert **schema shape**, columns
exist, changelogs apply, and never column *semantics*. A test that writes a document and then asks
SQL to find it is the check none of them made.

**How it closed.** The four long-text fields (`ShPostUnified.data` and `summary`,
`ShPostRevision.data`, `ShPublishGate.definition`) dropped `@Lob` for
`@JdbcTypeCode(SqlTypes.LONGVARCHAR)`, bound with `setString` on every engine. Not
`LONG32VARCHAR`: Hibernate does not count that type as string-like, so every HQL `like` over the
field fails validation and the context does not start. `ShLobStringLintTest` refuses a `@Lob`
anywhere in the model, because H2 cannot show the difference.

`v2026.3.46.yaml` (PostgreSQL only) converts the rows written before the fix. Each value that is a
bare integer naming an existing large object is replaced by that object's text, and the object is
unlinked, which deleting its row never did. It is idempotent and leaves a JSON value alone. It is
the project's first raw `sql` change, so `ShLiquibaseChangelogReferenceLintTest` now reads that type
too: raw SQL may not change structure, and every `sh_` table it names must exist.
`ShPostgresAssetUsageScanIT` asserts the column holds documents, that no object is created, that a
body-term predicate matches, and it runs the changeset's own SQL, twice, over a hand-seeded legacy
row.

## What a leading wildcard costs, per engine (SH1033)

The same predicate, three answers, which is why §LXX.5 asked for each database rather than one:

| Engine | Plan | Can any index serve `like '%x%'`? |
|---|---|---|
| H2 | `scanCount` = the table, 1,002 → 10,002 → 50,002 | No |
| MariaDB 11.4 | `type = ALL`, `key = null`, 5,001 rows | No, B-trees do not order substrings, and `FULLTEXT` matches *words*, so a hyphenated UUID is neither the stored value nor a term identifying one asset |
| PostgreSQL 16 | `data` applied as a `Filter:`, never an `Index Cond:` | **Yes**, `pg_trgm` gives a bitmap index scan |

Two cautions on the PostgreSQL row. At 5,003 narrow rows the planner **declines** the trigram index
on cost (194 against 221); it is proven usable with `enable_seqscan = off`, and the plan then shows
`Index Cond: (data ~~ '%…%')`. And the option is measured, not recommended: a GIN index over every
post's whole document is maintained on every write and would put the cheapest path on one engine of
three. That trade is SH1034's to make, the measurement exists so it is made knowing the branch is
there.

The claim to state carefully is therefore **"the `data` predicate is applied as a filter, never as
an index condition"**, not "the plan is a sequential scan". The first draft asserted the second and
PostgreSQL falsified it for the wrong reason: it reaches for `idx_post_unified_type` to satisfy
`state`, then removes 2,501 rows with the substring, an index scan by node name and a full read of
the state partition by behaviour.

## `sh_object_reference`: which post points at which object (SH1034)

`v2026.3.42`. A derived index: from-post, from-state, to-object, field name. SH1033 priced both
sides before it was built, the substring read it replaces examines the whole table twice per dialog
(1,002 / 10,002 / 50,002 rows at the three measured sizes), and the maintenance is one delete plus
one insert per reference, measured identical at fifty thousand rows and at zero. **Two curves, not
two numbers**, which is what chose the index over a cached count: a count makes one dialog cheap and
leaves "which assets does nothing reference" at one full scan per asset.

Four decisions worth keeping:

- **A new table, and `ShReference` is why rather than an oversight.** That table stores
  object-to-object edges and cannot hold these: it joins `object_from`/`object_to` to `ShObject`, and
  a post is not an `ShObject`. `ShTemplateGraph` reached the same conclusion from the other side and
  wrote it down, "a post-to-post edge would need a new table". Nothing in main wrote `ShReference`,
  and **SH1100 dropped it and `ShReferenceDraft`** (`v2026.3.48.yaml`, preconditioned on existence).
  The exchange format never carried either: a package holds posts and their data, and this index is
  rebuilt from the data on import, so there was nothing to move. Both names left
  `ShTenantBackfillRunner.TENANT_TABLES`, which the three schema ITs hold to the applied schema, and
  `ShLiquibaseChangelogReferenceLintTest` asserts the applied changelog no longer has them.
- **`from_state` is a column, not an assumption.** A post's key is `(id, state)`, so draft and
  published are two documents that may reference different assets, the point of a draft. An index
  keyed on the id alone would answer with the union and never be able to say which.
- **No foreign keys**, for the reason `sh_note` and `sh_review_decision` give: a post's key is
  composite, so an FK would have to name a state, and the row it named is the one publishing deletes.
- **The backfill is a startup gate, not a runtime fallback.** §LXX.6 proposed keeping the `LIKE` as
  the fallback "for a tenant whose backfill has not run", and that cannot work: **an empty answer and
  an unindexed post are the same observation**, so a read that fell back on finding no rows would
  fall back on every asset nothing references, the exact query the table exists to stop running.
  `ShObjectReferenceBackfillRunner` fills it once when it is empty, so the read is unconditional.

Maintenance has no choke point (`ShPostUnifiedWriteService` saves from fifteen places; five other
classes bypass it), so **`ShReferenceIndexMaintenanceLintTest` is the guard**, a class that writes
`setData` and persists must name `ShObjectReferenceService`, or the build fails. A stale derived
index is silent, and that is the whole argument for a lint over a convention.

## `sh_saved_view`: a curator's names for filtered lists (SH1024)

`v2026.3.47.yaml`: `owner`, `list_key` (browser, search, trash, review), `name`, `path`, `query`,
`shared`, `created_at`, lowercase `tenant_id` (`@TenantId`, no backfill since the table is new).
Unique on (owner, list_key, name, tenant_id), declared on the entity as well, so a save under a name
already used replaces the row instead of adding one, and the H2 unit suite sees the constraint.
Nothing reads the query string server-side: the list that opens a view validates it, as it does a
pasted link.
