# SH13: Draft / preview mode (short-lived preview tokens + console UX), implementable spec

> Roadmap: [ROADMAP.md](../ROADMAP.md) Block C · Design: [IMPROVEMENTS.md](../IMPROVEMENTS.md) §III.2
> Status: 📋 designed · deps: SH2 (shipped), SH5 (shipped)

## 1. Context: what already exists (do not rebuild)

- **Draft-visibility is fully solved (SH5).** [ShApiToken](../../shio-app/src/main/java/com/viglet/shio/persistence/model/token/ShApiToken.java) carries an `env` (`PROD`/`PREVIEW`); a `PREVIEW` token reads draft-preferred (prefers the `DRAFT` row, falls back to `PUBLISHED`), resolved centrally in [ShCdaService](../../shio-app/src/main/java/com/viglet/shio/api/cda/ShCdaService.java) so REST (SH1) and GraphQL (SH4) inherit it. **SH13 does not touch read resolution at all**, it only changes *how a PREVIEW key comes into being*.
- **The token filter already grants preview authority + carries a per-site allow-list (SH2/SH5).** [ShApiTokenFilter](../../shio-app/src/main/java/com/viglet/shio/spring/security/auth/ShApiTokenFilter.java) authenticates a `Key` header on the CDA surfaces, grants `ROLE_CDA_PREVIEW` for a `PREVIEW` env, and carries scope + `siteIds` + `env` into [ShApiTokenContext](../../shio-app/src/main/java/com/viglet/shio/spring/security/auth/ShApiTokenContext.java).
- **The consumer is already in the starter (SH9).** [`create-shio-app`](../../create-shio-app)'s `app/api/preview/route.ts` enables Next.js **Draft Mode** and redirects; `lib/shio.ts`'s `getShioClient()` returns the PREVIEW client whenever Draft Mode is on. Today it gates on a **static shared secret** (`SHIO_PREVIEW_SECRET`) and reads through a **single long-lived** server token (`SHIO_PREVIEW_TOKEN`). SH9 explicitly flagged the per-editor link as SH13.
- **The token CRUD has no service layer.** [ShApiTokenAPI](../../shio-app/src/main/java/com/viglet/shio/api/token/ShApiTokenAPI.java) is a thin controller over [ShApiTokenRepository](../../shio-app/src/main/java/com/viglet/shio/persistence/repository/token/ShApiTokenRepository.java). SH13 mirrors that shape.
- **The post editor loads the unified DRAFT row.** [post.edit.page.tsx](../../shio-react/src/app/content/post.edit.page.tsx) already has the post's `furl` and `shFolder.shSite.id`; the site's front-end base lives on [`ShSite.url`](../../shio-app/src/main/java/com/viglet/shio/persistence/model/site/ShSite.java) (the console already uses it for the visual-editor iframe).

**What SH13 adds:** an authenticated console user mints a **short-lived (expiring) preview token** for one post and is handed a ready-to-open **preview URL** at the site's front-end. The token is a real `ShApiToken` (`scope=PREVIEW`, `env=PREVIEW`, confined to that one site via the SH2 allow-list) that **expires**, so a preview link is safe to paste into Slack and dies on its own. The starter's `/api/preview` route is taught to accept that per-request token (still backward-compatible with the static secret). This is the durable counterpart to SH5's permanent preview *deployment* key.

## 2. Goal & scope

**In scope**
- `ShApiToken` gains `expiresAt` (nullable `Instant`) + `ephemeral` (`boolean`, default `false`); the filter rejects an expired token (treated as not-enabled). Liquibase changeset.
- `ShPreviewTokenAPI` at `/api/v2/preview-token` (console-authenticated, **not** a CDA surface): `POST {postId, ttlSeconds?}` mints an ephemeral PREVIEW token confined to the post's site, builds the front-end preview URL, and returns `{token, previewUrl, expiresAt, ttlSeconds}`. Opportunistically purges expired ephemeral tokens on each mint.
- `ShCdaProperties.Preview` config (`shio.cda.preview.*`): enable switch, default + max TTL, and the front-end route shape (`path`/`tokenParam`/`urlParam`).
- The management list (`GET /api/v2/api-token`) excludes ephemeral tokens so preview tokens never clutter the token UI.
- Console: a **Preview draft** action on the post editor that mints a token and opens the preview URL in a new tab; i18n in `en`+`pt`.
- Starter: `/api/preview` accepts `?token=` (per-request ephemeral token stored in an httpOnly cookie); `getShioClient()` prefers the cookie token under Draft Mode. The static-`secret` path keeps working. Recipe updated.

**Out of scope (deferred)**
- A scheduled background sweep for expired tokens, purge is opportunistic on mint (no `@EnableScheduling` introduced). A dedicated sweep can come with SH15's scheduler.
- Per-post / field-level preview permissions, preview of *unsaved* in-browser edits (that is the SH10 Universal Editor's live bridge), and preview history/audit.
- A console screen listing active preview tokens, they are ephemeral by design.

## 3. Two axes, unchanged

SH5 fixed the meaning of the token axes; SH13 keeps them:
- **`scope`** = *what operations* (`READ`/`PREVIEW`/`WRITE`). An ephemeral token is `PREVIEW`.
- **`env`** = *which content state* (`PROD`/`PREVIEW`). An ephemeral token is `PREVIEW`, so `ShCdaService` reads it draft-preferred with **no new code**.
- **`expiresAt`** (new) = *for how long*. Orthogonal to both; `null` = never (every pre-SH13 token).
- **`ephemeral`** (new) = *minted by the preview flow*, a presentation flag so these rows are hidden from the token-management list and are the only rows the purge deletes.

## 4. Data model

`ShApiToken` gains:
```java
@Column(name = "expires_at")
private Instant expiresAt;              // null = never expires (all durable tokens)

@Column(name = "ephemeral")
private boolean ephemeral = false;      // true = minted by the SH13 preview flow
```
`isExpired(Instant now)` helper: `expiresAt != null && expiresAt.isBefore(now)`.

Schema is Liquibase-managed (`ddl-auto=none`), a new changeset adds the columns (see §8). Existing rows get `expires_at = NULL` / `ephemeral = false` (or `NULL`, treated as false), so every durable token is unchanged.

## 5. Filter: expiry enforcement (`ShApiTokenFilter`)

After the existing `enabled` check, one guard:
```java
if (apiToken.getExpiresAt() != null && apiToken.getExpiresAt().isBefore(Instant.now())) {
    filterChain.doFilter(request, response);   // pass through unauthenticated, like a disabled token
    return;
}
```
An expired preview token therefore stops authenticating automatically, no separate revocation step. Everything downstream (scope gate, rate limit, authorities, `ShApiTokenContext`) is unchanged.

## 6. Mint endpoint (`/api/v2/preview-token`)

`ShPreviewTokenAPI` (constructor-injected `ShApiTokenRepository`, `ShPostUnifiedRepository`, `ShCdaProperties`, `@Slf4j`), console-authenticated (falls under `anyRequest authenticated`, **not** the CDA `Key` surface). One endpoint:

`POST /api/v2/preview-token`: body `{ "postId": "...", "ttlSeconds": 900 }` (ttl optional):
1. When `shio.cda.preview.enabled=false`, return `404 Not Found` (`ResponseEntity.notFound()`), so the console treats a `404` as "feature off" and stays quiet. The feature is on by default.
2. Resolve the post draft-preferred: `findById(postId)` → prefer the `DRAFT` row, else `PUBLISHED`; `404` if none.
3. Resolve `site = post.getShSite()` (or `post.getShFolder().getShSite()`); read `base = site.getUrl()`. If `base` is blank → `400` `{ "error": "site has no url" }` (the console surfaces a toast).
4. Clamp ttl: `ttl = min(ttlSeconds ?? defaultTtlSeconds, maxTtlSeconds)`, floor at 1s.
5. Mint:
   ```java
   ShApiToken t = new ShApiToken();
   t.setTitle("preview:" + postId);
   t.setToken(UUID.randomUUID().toString().replace("-", "").substring(0, 25));  // same generator as ShApiTokenAPI
   t.setOwner(principal != null ? principal.getName() : "system");
   t.setCreatedDate(now); t.setEnabled(true);
   t.setScope(ShApiTokenScope.PREVIEW); t.setEnv(ShApiTokenEnv.PREVIEW);
   t.setSiteIds(Set.of(site.getId()));        // SH2 confinement to this one site
   t.setExpiresAt(now.plusSeconds(ttl)); t.setEphemeral(true);
   ```
6. Build `previewUrl = trimTrailingSlash(base) + path + "?" + tokenParam + "=" + token + "&" + urlParam + "=" + enc(furl)` where `furl` defaults to `/` and is URL-encoded; `path`/`tokenParam`/`urlParam` come from config (defaults `/api/preview`, `token`, `url`).
7. Opportunistic purge: `shApiTokenRepository.deleteByEphemeralTrueAndExpiresAtBefore(now)` (best-effort; keeps the table from accumulating dead rows without a scheduler).
8. Return `200 ShPreviewTokenResponse{ token, previewUrl, expiresAt, ttlSeconds }`.

`ShPreviewTokenResponse` is a `record(String token, String previewUrl, Instant expiresAt, long ttlSeconds)`.

## 7. Config (`shio.cda.preview.*`)

`ShCdaProperties` gains a nested `Preview preview = new Preview()`:
```
shio.cda.preview.enabled=true
shio.cda.preview.ttl-seconds=900          # default lifetime of a minted preview token (15 min)
shio.cda.preview.max-ttl-seconds=3600     # hard ceiling on a requested ttl (1 h)
shio.cda.preview.path=/api/preview        # front-end Draft-Mode route (SH9 convention)
shio.cda.preview.token-param=token
shio.cda.preview.url-param=url
```
All commented (defaults shown) in `application.properties`.

## 8. Repository + Liquibase

`ShApiTokenRepository` gains:
```java
List<ShApiToken> findByEphemeralFalseOrderByTitleAsc();      // management list (hides preview tokens)
void deleteByEphemeralTrueAndExpiresAtBefore(Instant when);  // opportunistic purge
```
`ShApiTokenAPI.list()` switches to `findByEphemeralFalseOrderByTitleAsc()`.

Liquibase `db/changelog/v2026.2.2.yaml` (added to `db.changelog-master.yaml`), two `addColumn` changesets with `preConditions onFail: MARK_RAN` + `columnExists` negation (Turing pattern), DB-agnostic types (`TIMESTAMP`, lowercase `boolean`):
```yaml
- changeSet:
    id: sh13-api-token-expires-at
    preConditions: { onFail: MARK_RAN, not: [ columnExists: { tableName: api_token, columnName: expires_at } ] }
    changes: [ addColumn: { tableName: api_token, columns: [ column: { name: expires_at, type: TIMESTAMP } ] } ]
- changeSet:
    id: sh13-api-token-ephemeral
    preConditions: { onFail: MARK_RAN, not: [ columnExists: { tableName: api_token, columnName: ephemeral } ] }
    changes: [ addColumn: { tableName: api_token, columns: [ column: { name: ephemeral, type: boolean, defaultValueBoolean: false } ] } ]
```

## 9. Console UX

- `ShPreviewTokenService` (`src/services/preview-token/`): `mint(postId, ttlSeconds?) -> { token, previewUrl, expiresAt, ttlSeconds }` via `POST /v2/preview-token`.
- [post.edit.page.tsx](../../shio-react/src/app/content/post.edit.page.tsx): a **Preview draft** button (`Eye` icon) in the sticky save bar, shown only when the post has a site. On click → `mint(postId)` then `window.open(previewUrl, "_blank", "noopener")`. A `400` (no site url) shows `content.previewNoUrl`; a `404` (feature disabled / post gone) hides/*toasts* `content.previewError`; other failures toast `content.previewError`.
- i18n: `content.previewDraft`, `content.previewNoUrl`, `content.previewError`, `content.previewOpening` in `en` + `pt`.

## 10. Starter consumption (`create-shio-app`)

- `app/api/preview/route.ts`: if `?token=` is present, **enable Draft Mode**, set an httpOnly cookie `shio-preview-token` (path `/`, `maxAge` ≈ a few hours, `sameSite=lax`), and redirect to the (relative) `url`. The existing `?secret=` path is unchanged; `?disable=1` also clears the cookie.
- `lib/shio.ts`: `getShioClient()`, under Draft Mode, if the `shio-preview-token` cookie is set, return a per-request `new ShioClient({ baseUrl, apiToken: cookieToken })`; else fall back to `shioPreview`; else `shio`. (A tiny `previewClientFor(token)` helper keeps it tidy.)
- `recipes/nextjs-rendering.md` §4: document the ephemeral-token link `/api/preview?token=<minted>&url=/blog/hello` as the console-issued form, next to the static-secret form.

## 11. Acceptance criteria

1. `POST /api/v2/preview-token {postId}` (authenticated) returns a 25-char `token`, a `previewUrl` of the form `<site.url>/api/preview?token=<token>&url=<encoded furl>`, and an `expiresAt` ≈ now + default ttl. The minted row is `scope=PREVIEW`, `env=PREVIEW`, `ephemeral=true`, `siteIds={site}`.
2. The minted token reads draft-preferred via the CDA **immediately** (it is a PREVIEW-env key, inherits SH5) and is **confined to its site** (SH2 allow-list): a CDA read for another site is denied.
3. After `expiresAt`, the same token no longer authenticates on the CDA (`ShApiTokenFilter` passes it through unauthenticated → `401/403`).
4. A requested `ttlSeconds` above `max-ttl-seconds` is clamped to the max; an absent ttl uses `ttl-seconds`.
5. Minting a token for a post whose site has a blank `url` returns `400`; `shio.cda.preview.enabled=false` returns `404`.
6. Ephemeral tokens do **not** appear in `GET /api/v2/api-token`; minting purges already-expired ephemeral rows.
7. Pre-SH13 tokens (`expires_at = NULL`) never expire, SH1/SH2/SH5 behaviour preserved.
8. Starter: opening the `previewUrl` enables Draft Mode and renders draft-preferred content through the minted token; `?disable=1` reverts to published.

## 12. Tests

- `ShApiTokenFilterTest` (extend): `expiredTokenIsIgnored` (an `expiresAt` in the past → no authentication, chain proceeds) and `unexpiredEphemeralTokenAuthenticates` (future `expiresAt` + `ephemeral=true` still grants `ROLE_CDA_PREVIEW`). (crit 3, 7.)
- `ShPreviewTokenAPITest` (Mockito unit, mirrors the filter test's style): seed a `ShPostUnified` (DRAFT) under a site with a `url`; assert the minted token's scope/env/ephemeral/siteIds/`expiresAt`, the `previewUrl` shape + furl encoding, ttl clamping to max, the blank-url `400`, the disabled `404`, and that the purge finder is invoked. (crit 1, 4, 5, 6.)

## 13. Estimate

~1 day: model + filter guard + repo + migration (2h), mint endpoint + config + DTO (3h), console service + button + i18n (2h), starter route + lib + recipe (1h), tests (2h).
