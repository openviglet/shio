# SH2: API-token scopes + per-site keys, implementable spec

> Roadmap: [ROADMAP.md](../ROADMAP.md) Block A · Design: [IMPROVEMENTS.md](../IMPROVEMENTS.md) §I.2
> Status: 📋 designed · deps: SH1 (shipped)

## 1. Context: what already exists (do not rebuild)

- **Token auth is shipped (SH1).** [ShApiTokenFilter](../../shio-app/src/main/java/com/viglet/shio/spring/security/auth/ShApiTokenFilter.java) reads a `Key` header, looks up [ShApiToken](../../shio-app/src/main/java/com/viglet/shio/persistence/model/token/ShApiToken.java), **rejects any non-GET** with 403, and grants `ROLE_CDA` + `ROLE_USER` for *every* path the request happens to hit.
- **The CDA read surface is shipped (SH1).** [ShCdaService](../../shio-app/src/main/java/com/viglet/shio/api/cda/ShCdaService.java) pins every read to `PUBLISHED`. SH1 §6 already committed to the forward shape: *"`siteId` is a first-class query param ... SH2 per-site key scoping filters at the service boundary without URL changes."*
- **Token CRUD is shipped.** [ShApiTokenAPI](../../shio-app/src/main/java/com/viglet/shio/api/token/ShApiTokenAPI.java) (`/api/v2/api-token`) + the React editor at `shio-react/src/app/admin/api-token/`.

**What SH2 adds:** every token carries a **scope** (`READ` / `PREVIEW` / `WRITE`) and an optional **site allow-list**. The filter enforces the scope (which HTTP methods, which preview authority) and confines token auth to the CDA namespace; the CDA service enforces the allow-list so a public delivery key can read exactly one site's published content and nothing else.

## 2. Goal & scope

**In scope**
- `ShApiTokenScope` enum + `scope` column (default `READ`) on `ShApiToken`.
- Optional `siteIds` allow-list on `ShApiToken` (empty = all sites).
- Filter: confine token auth to `/api/v2/cda/**`; map scope → granted authorities; method gate (`READ`/`PREVIEW` GET-only, `WRITE` any).
- Service: per-site enforcement on every CDA read (unknown/disallowed site → `404`/empty, never a leak).
- Token CRUD + React editor expose scope and the site allow-list.

**Out of scope (deferred)**
- What `PREVIEW` actually *sees* (drafts) → **SH13** (this spec only grants the `ROLE_CDA_PREVIEW` authority + pins the state lookup hook).
- What `WRITE` actually *writes* (CDA write-back endpoints) → **SH10** (this spec only grants `ROLE_CDA_WRITE` + lifts the method gate for those tokens).
- `env` (prod/preview) keys → **SH5**.
- Rate limiting / cache headers → **SH3**.

## 3. Data model

`ShApiTokenScope` (`com.viglet.shio.persistence.model.token`): `READ`, `PREVIEW`, `WRITE`.

`ShApiToken` gains:
```java
@Enumerated(EnumType.STRING)
@Column(name = "scope", length = 16, nullable = false)
private ShApiTokenScope scope = ShApiTokenScope.READ;

@ElementCollection(fetch = FetchType.EAGER)
@CollectionTable(name = "api_token_site", joinColumns = @JoinColumn(name = "api_token_id"))
@Column(name = "site_id")
private Set<String> siteIds = new HashSet<>();
```
`EAGER` because the filter reads `siteIds` outside any open session. `ddl-auto=update` creates the `scope` column and `api_token_site` table automatically, no migration. Existing rows get `scope = NULL`; the filter/service treat `null` scope as `READ` and `null/empty siteIds` as unrestricted, so pre-SH2 tokens keep working unchanged.

## 4. Security context carrier

`ShApiTokenContext` record (`com.viglet.shio.spring.security.auth`) is set as the `Authentication` details by the filter and read by the service:
```java
public record ShApiTokenContext(String tokenId, ShApiTokenScope scope, Set<String> siteIds) {
    public boolean allowsSite(String siteId) {
        return siteIds == null || siteIds.isEmpty()
            || (siteId != null && siteIds.contains(siteId));
    }
    public static Optional<ShApiTokenContext> current() { /* read SecurityContextHolder details */ }
}
```
`current()` returns empty when there is no token auth (console session, or tests using a raw `Principal`) → the service treats that as **unrestricted**, so SH1 behaviour is unchanged.

## 5. Filter (`ShApiTokenFilter`)

1. **Confine to CDA.** If the request path is not under `/api/v2/cda`, skip token processing entirely (let normal auth run). This closes the prior behaviour where a token authenticated *any* path, and removes the risk of a `WRITE` token reaching console write endpoints.
2. Look up + enabled check (unchanged).
3. **Method gate by scope:** `READ`/`PREVIEW` → only `GET` (else `403`, current message); `WRITE` → any method.
4. **Authorities by scope:** all → `ROLE_CDA` + `ROLE_USER`; `PREVIEW` → + `ROLE_CDA_PREVIEW`; `WRITE` → + `ROLE_CDA_WRITE`.
5. Set `authentication.setDetails(new ShApiTokenContext(id, scope, siteIds))`.

## 6. Service enforcement (`ShCdaService`)

Add `private boolean siteAllowed(String siteId)` = `ShApiTokenContext.current().map(c -> c.allowsSite(siteId)).orElse(true)`. Apply at every boundary:

| Method | Guard |
|---|---|
| `listSites()` | filter out sites the token may not see |
| `getSite(siteId)` | `siteAllowed(siteId)` else `Optional.empty()` |
| `listChildren(folderId)` | resolve folder's site; disallowed → `Optional.empty()` |
| `path(objectId)` | resolve site; disallowed → `Optional.empty()` |
| `getPost(id)` | `siteAllowed(post.shSite.id)` else `Optional.empty()` |
| `getPostByUrl(siteId,url)` | `siteAllowed(siteId)` else `Optional.empty()` |
| `query(...)` | resolve effective site (folder's or `siteId`); disallowed → empty listing |

Disallowed always collapses to the existing `404`/empty path, a per-site key cannot tell "wrong site" from "missing", which is the correct public behaviour.

## 7. CRUD + UI

- `ShApiTokenAPI.create`/`update`: persist `scope` (default `READ` when null) and `siteIds`. Token value still never re-issued on update.
- React: `ShApiToken` model gains `scope: "READ"|"PREVIEW"|"WRITE"` and `siteIds: string[]`. Editor adds a **Scope** select and a **Site access** section (all-sites switch + per-site checkboxes from `ShSiteService.query()`). List page shows a scope badge. i18n keys in `en` + `pt`.

## 8. Acceptance criteria

1. A `READ` token with `siteIds = {A}` gets `200` for site `A` reads and `404` for any site-`B` read (`getSite`, `getPost`, `by-url`, `query`, `list`, `path`).
2. The same token's `listSites()` returns only `A`.
3. A token with empty `siteIds` reads every site (unrestricted), SH1 behaviour preserved.
4. A `READ`/`PREVIEW` token doing a non-GET to `/cda/**` → `403`; a `WRITE` token is not method-gated.
5. A `Key` header on a non-`/cda` path does **not** authenticate the request (→ `401`/`403`, never `200`).
6. A `PREVIEW` token carries `ROLE_CDA_PREVIEW`; a `WRITE` token carries `ROLE_CDA_WRITE`.
7. Pre-SH2 tokens (`scope = null`, no `siteIds`) behave exactly as a `READ` unrestricted token.

## 9. Tests

- `ShApiTokenScopeTest`: filter-level: confinement (crit 5), method gate per scope (crit 4), authorities per scope (crit 6).
- Extend `ShCdaAPITest` (or `ShCdaScopeTest`), seed two sites; assert per-site allow-list (crit 1–3) by setting a `ShApiTokenContext` in the `SecurityContextHolder` around the service/MockMvc call.

## 10. Estimate

~1 day: model+context (2h), filter+service enforcement (2h), CRUD+UI (2h), tests (2h).
