# Common issues and solutions

> Split out of the repo-root `agents.md` (see its routing table for the index).
> Read this before touching the area it covers. Detail here is authoritative.

## Common Issues & Solutions

### Optimistic Locking on Startup
`ShPostTypeImport` sets explicit IDs on `ShPostTypeAttr` via `setId()`. With cascade persist, Hibernate treats these as detached. Solution: use `ShAssignableUuidGenerator` (like Turing's `TurAssignableUuidGenerator`) that allows pre-assigned IDs.

### CSRF 403 on PUT/POST/DELETE
1. Verify `ShCsrfHeaderFilter` is added after `BasicAuthenticationFilter.class`
2. Verify `/api/v2/csrf` endpoint exists and is in `permitAll()`
3. Frontend axios interceptor must fetch token before mutating requests
4. Cookie must not have `Secure` flag in dev (HTTP)

### widgetSettings JSON.parse("null") Crash
`widgetSettings` may contain the string `"null"` from the backend. `JSON.parse("null")` returns JavaScript `null`, which passes truthy checks but crashes on property access (`null.file`, `null.choices`, etc.). Always guard parsed settings:
```typescript
const p = JSON.parse(raw);
return p && typeof p === "object" ? p : {};
```
The `parseSettings()` helper in `post-type.edit.page.tsx` centralizes this pattern.

### Vite "Outdated Optimize Dep" (504)
After installing/removing packages (`pnpm add`/`pnpm remove`), clear Vite cache: `rm -rf node_modules/.vite` and restart dev server.

### Static Files 404
- WebJars served from `classpath:/META-INF/resources/webjars/`
- Check Maven dependency resolution (transitive deps may override explicit versions)
- `store/file_source/` path must end with `/` in resource handler

### Full-text search returns nothing (SH131)

`ShPostUnifiedRepository.FULLTEXT_PREDICATE` needs a pre-lowercased, `%`-wrapped term.
Call the `search*` **default** methods, which normalize for you — the raw `@Query`
methods are suffixed `…Like` and take the pattern. Normalizing again before calling a
`search*` method searches for `%%term%%`, which matches nothing and looks like an empty
index rather than a bug.

### Unused Imports Fail TypeScript Build
`tsc -b` with strict mode flags unused imports as errors (TS6133). When refactoring (e.g., replacing `Checkbox` with `Switch`), always remove unused imports.

### `Column "..._.TENANT_ID" not found` (or any column) at startup
Schema drift. The schema is Liquibase-managed (`ddl-auto=none`) — a column referenced by an entity does not exist because no changeset created it. Fix by adding a Liquibase changeset (see **Database Schema** above), not by flipping `ddl-auto` back to `update`. Also confirm the datasource URL still has `DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true` — dropping those reintroduces quoted-vs-unquoted casing mismatches that surface as "column not found".

### A post or address comes back with no site (SH128 invariant)

**Only a site's root folder carries `shSite`.** `ShFolderWriteService` nulls it on every
child, so reading `folder.getShSite()` on anything nested silently yields `null` — a
site-less post, an address of `folder:null/...`, or a CDA allow-list check that denies
everything (`allowsSite(null)` is `false` under a token allow-list). Always resolve with
`ShFolderSite.of(folder)` / `.idOf(folder)`, which walks to the root:

```java
// ✗ null for every folder below the root — compiles, reviews clean, wrong at runtime
row.setShSite(folder.getShSite());
// ✓ walks the parent chain to the folder that actually carries the site
row.setShSite(ShFolderSite.of(folder));
```

`ShFolderSiteInvariantTest` fails the build on the wrong form. If a direct read is
genuinely what you mean (the exchange export records `shSite` root-only; the console XP
endpoint touches it to force a LAZY proxy), add a `ShFolderSite-exempt: <reason>` comment
on or directly above the line.

### `Column "spt1_1.tenant_id" not found` in a JPQL query (SH22 join hazard)
**Never navigate past the `id` of an association whose target is an `ShObject`
subclass** (`ShSite`, `ShFolder`, `ShPostType`). `@TenantId` lives on the JOINED
`ShObject` **root**, so an implicit association join makes Hibernate emit the tenant
predicate against the *subclass* table's alias — which has no `tenant_id` column —
and the query dies at prepare time. Cheap and reliable rule:

```java
// ✗ breaks: joins ShPostType, tenant predicate hits the subclass table alias
@Query("select p.shPostType.name, count(distinct p.id) from ShPostUnified p group by p.shPostType.name")
// ✓ works: uses the FK column already on sh_post_unified — no join at all
@Query("select p.shPostType.id, count(distinct p.id) from ShPostUnified p group by p.shPostType.id")
```

Resolve `id → name` in the service from the entity list you already loaded. The
id-only form is also faster (no join). This is invisible until runtime — the
mapping is valid JPQL — so it is a code-review item, not a compile error. Found
while building the SH77 context pack; see `ShPostUnifiedRepository`'s note.

