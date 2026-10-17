# SH5: Per-environment keys (prod/preview), implementable spec

> Roadmap: [ROADMAP.md](../ROADMAP.md) Block A · Design: [IMPROVEMENTS.md](../IMPROVEMENTS.md) §I.5
> Status: 📋 designed · deps: SH2 (shipped)

## 1. Context: what already exists (do not rebuild)

- **Token scopes + per-site keys are shipped (SH2).** [ShApiToken](../../shio-app/src/main/java/com/viglet/shio/persistence/model/token/ShApiToken.java) carries a `scope` (`READ`/`PREVIEW`/`WRITE`) and an optional `siteIds` allow-list. [ShApiTokenFilter](../../shio-app/src/main/java/com/viglet/shio/spring/security/auth/ShApiTokenFilter.java) confines token auth to the CDA surfaces, grants authorities by scope, and carries scope + allow-list to the service via the [ShApiTokenContext](../../shio-app/src/main/java/com/viglet/shio/spring/security/auth/ShApiTokenContext.java) auth-detail.
- **The CDA read service pins state (SH1).** [ShCdaService](../../shio-app/src/main/java/com/viglet/shio/api/cda/ShCdaService.java) reads every post at a single `ShPostState`, until now a hard-coded `PUBLISHED`. SH1 §6 already committed to "state is a service-level concern, not a URL segment", precisely so SH5 can flip it per request without touching the public URL contract.
- **The unified model keeps both rows.** [ShPostUnifiedWriteService](../../shio-app/src/main/java/com/viglet/shio/migration/unified/ShPostUnifiedWriteService.java): `create` always writes a `DRAFT` row; `publish` adds a `PUBLISHED` row **without** removing the draft; `unpublish` deletes only the `PUBLISHED` row. So a live post has a `DRAFT` row that is the latest editorial state and (when published) a `PUBLISHED` snapshot. "See drafts" therefore means **draft-preferred**: prefer the `DRAFT` row, fall back to `PUBLISHED`.

**What SH5 adds:** every token carries an **environment** (`PROD` / `PREVIEW`). A `PROD` key reads only `PUBLISHED` content (today's behaviour). A `PREVIEW` key reads draft-preferred, so a preview deployment of the *same* front-end codebase sees unpublished edits with only a token swap. The state resolution is centralized in `ShCdaService`, so REST (SH1) and GraphQL (SH4) inherit it identically.

## 2. Goal & scope

**In scope**
- `ShApiTokenEnv` enum (`PROD`/`PREVIEW`) + `environment` column (default `PROD`) on `ShApiToken`.
- Filter: read `env`, carry it in `ShApiTokenContext`, grant `ROLE_CDA_PREVIEW` for `PREVIEW` keys.
- Service: replace the hard-coded `PUBLISHED` with a per-request resolution, `PROD` → `PUBLISHED` only (unchanged, efficient single-state query); `PREVIEW` → draft-preferred across every read (point lookups + listings + query/GraphQL).
- Token CRUD + React editor expose the environment; list page shows an env badge.

**Out of scope (deferred)**
- **Short-lived / ephemeral preview tokens + the console "preview this draft" UX → SH13.** SH5 ships the durable, long-lived per-environment key (a `PREVIEW` key for a preview *deployment*); SH13 layers expiring tokens and the in-console preview link on the same draft-visibility machinery.
- Per-environment site URLs / hostnames, an env is a content-state selector, not a deployment-target descriptor.

## 3. Relationship to the SH2 `PREVIEW` scope

SH2 already has a `PREVIEW` **scope** that grants `ROLE_CDA_PREVIEW`. SH5 keeps the two axes distinct:

- **`scope`** (`READ`/`PREVIEW`/`WRITE`) = *what operations* a key may perform (read-only, preview-marker, write-back). Unchanged.
- **`env`** (`PROD`/`PREVIEW`) = *which content state* a key sees (published vs draft-preferred). New.

Draft visibility in `ShCdaService` is driven **solely by `env`** (`ShApiTokenContext.preview()` reads `env == PREVIEW`), so there is exactly one driver and no ambiguity. The `PREVIEW` scope's `ROLE_CDA_PREVIEW` authority remains an authorization marker that SH13 builds on; it does not by itself expose drafts.

## 4. Data model

`ShApiTokenEnv` (`com.viglet.shio.persistence.model.token`): `PROD`, `PREVIEW`.

`ShApiToken` gains:
```java
@Enumerated(EnumType.STRING)
@Column(name = "environment", length = 16)
private ShApiTokenEnv env = ShApiTokenEnv.PROD;
```
`ddl-auto=update` adds the column, no migration. Existing rows get `environment = NULL`; the filter/service treat `null` as `PROD`, so pre-SH5 tokens keep reading published content exactly as before.

## 5. Security context carrier

`ShApiTokenContext` gains an `env` component:
```java
public record ShApiTokenContext(String tokenId, ShApiTokenScope scope, Set<String> siteIds, ShApiTokenEnv env) {
    /** Back-compat: callers (and SH2 tests) that don't supply an env get PROD. */
    public ShApiTokenContext(String tokenId, ShApiTokenScope scope, Set<String> siteIds) {
        this(tokenId, scope, siteIds, ShApiTokenEnv.PROD);
    }
    public boolean preview() { return env == ShApiTokenEnv.PREVIEW; }
    // allowsSite(...) + current() unchanged
}
```

## 6. Filter (`ShApiTokenFilter`)

1. Resolve `ShApiTokenEnv env = apiToken.getEnv() != null ? apiToken.getEnv() : ShApiTokenEnv.PROD;`.
2. Grant `ROLE_CDA_PREVIEW` when `scope == PREVIEW` **or** `env == PREVIEW` (a preview key is, by definition, preview-capable).
3. `authentication.setDetails(new ShApiTokenContext(id, scope, siteIds, env));`.

## 7. Service resolution (`ShCdaService`)

`private boolean preview()` = `ShApiTokenContext.current().map(ShApiTokenContext::preview).orElse(false)`, no token / console / tests ⇒ `false` ⇒ `PUBLISHED` (the SH1 behaviour, unchanged).

| Method | `PROD` (unchanged) | `PREVIEW` (draft-preferred) |
|---|---|---|
| `getPost(id)` | `findByIdAndState(id, PUBLISHED)` | `DRAFT` then `PUBLISHED` |
| `getPostByUrl(siteId,url)` | per candidate, `PUBLISHED` | per candidate, `DRAFT` then `PUBLISHED` |
| `path(objectId)` (post branch) | `PUBLISHED` | `DRAFT` then `PUBLISHED` |
| `listChildren` / `query` / `queryPosts` | single-state `PUBLISHED` `Page` query | fetch `{DRAFT,PUBLISHED}` via `…StateIn`, dedup by id preferring `DRAFT`, paginate in memory |

The `PROD` path keeps the existing efficient single-state repository `Page` queries byte-for-byte. The `PREVIEW` path uses new `…StateIn` list finders + an in-memory `draftPreferred(rows, pageable)` that dedups by post id (keeping the `DRAFT` row) and slices the page. Preview traffic is internal/low-volume, so the in-memory page is an acceptable trade for not duplicating every paged query. The SH2 per-site allow-list still applies on every branch.

## 8. Repository

`ShPostUnifiedRepository` gains list finders over a state set (used only by the preview branch):
```java
List<ShPostUnified> findByShFolderAndStateIn(ShFolder shFolder, Collection<ShPostState> states);
List<ShPostUnified> findByShSite_IdAndStateIn(String siteId, Collection<ShPostState> states);
List<ShPostUnified> findByShSite_IdAndShPostType_NameAndStateIn(String siteId, String postType, Collection<ShPostState> states);
```

## 9. CRUD + UI

- `ShApiTokenAPI.create`/`update`: persist `env` (default `PROD` when null).
- React: `ShApiToken` model gains `env: "PROD" | "PREVIEW"`. Editor adds an **Environment** select to the Access card (with a description line). List page shows an env badge next to the scope badge. i18n keys (`apiToken.env*`) in `en` + `pt`.

## 10. Acceptance criteria

1. A `PROD` token reading a post that has both a (newer) `DRAFT` and a `PUBLISHED` row gets the **published** values; a `PREVIEW` token gets the **draft** values. (`getPost`, `getPostByUrl`.)
2. A post with only a `DRAFT` row (never published) is **invisible** to a `PROD` token (`404`/empty) and **visible** to a `PREVIEW` token.
3. Listings/query: a `PREVIEW` token's listing includes the draft-only post; a `PROD` token's does not. No post appears twice.
4. A `PREVIEW` token carries `ROLE_CDA_PREVIEW`; the `env` is carried in `ShApiTokenContext` and drives `preview()`.
5. Pre-SH5 tokens (`environment = null`) behave exactly as `PROD`, SH1/SH2 behaviour preserved.
6. The SH2 per-site allow-list is still enforced under both environments.

## 11. Tests

- `ShApiTokenFilterTest`: add: a `PREVIEW`-env token carries `ROLE_CDA_PREVIEW` and a `ShApiTokenContext` whose `preview()` is true (crit 4).
- `ShCdaEnvTest` (new, `@SpringBootTest`): seed a post with divergent `DRAFT`/`PUBLISHED` rows + a draft-only post; assert prod vs preview visibility for `getPost`, `getPostByUrl`, and a listing (crit 1–3), and that no-token access stays published-only (crit 5).

## 12. Estimate

~1 day: model+context+filter (2h), service resolution + repo finders (3h), CRUD+UI (2h), tests (2h).
