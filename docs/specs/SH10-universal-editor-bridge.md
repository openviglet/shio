# SH10: Mature the Universal Editor bridge, implementable spec

> Roadmap: [ROADMAP.md](../ROADMAP.md) Block B · Design: [IMPROVEMENTS.md](../IMPROVEMENTS.md) §II.5
> Status: ✅ shipped · deps: SH1 (shipped)

## 1. Context: what already existed (do not rebuild)

- **The iframe-side bridge is done.** ~~[shio-editor.js](../../shio-app/src/main/resources/universal-editor/shio-editor.js) (served at `/universal-editor/shio-editor.js`)~~ (*removed by SH304: it was a copy of the published package below, and the path is no longer served*) and its TS twin [shio-editor-cors.ts](../../shio-js-sdk/src/shio-editor-cors.ts) detect `data-sh-*` attributes on a rendered page, do inline `contenteditable` editing, and `postMessage` `sh:*` events to the parent (`sh:ready`, `sh:element-selected`, `sh:content-changed`, …) while accepting `sh:mode-change` / `sh:content-patch`.
- **SH2 reserved the write scope.** [ShApiTokenFilter](../../shio-app/src/main/java/com/viglet/shio/spring/security/auth/ShApiTokenFilter.java) already grants `ROLE_CDA_WRITE` for a `WRITE`-scope token and lifts the GET-only method gate for it; SH2 explicitly deferred *what* WRITE writes to SH10.
- **The unified write service exists.** [ShPostUnifiedWriteService](../../shio-app/src/main/java/com/viglet/shio/migration/unified/ShPostUnifiedWriteService.java) `update(id, UpdateRequest(data, state, modifier))` / `publish(id, publisher)` write the `sh_post_unified` rows; `data` is a JSON map, so an attribute patch is a key-merge.
- **The console route/i18n were scaffolded but dead.** `ROUTES.UNIVERSAL_EDITOR`, the sidebar link, and `universal-editor.json` (en/pt) existed with no page, route, or service behind them.

**What SH10 adds:** the *save-back* half. A CDA write surface under `/api/v2/cda/**` (gated by `ROLE_CDA_WRITE`) that merges edits into a post's `DRAFT` row and publishes on demand, plus the React console editor shell (the iframe parent) that drives the bridge and saves through that surface with a WRITE token.

## 2. Goal & scope

**In scope**
- CDA write endpoints (`editable` / `patch` / `publish`) in the `/api/v2/cda` namespace, `ROLE_CDA_WRITE`-gated, CSRF-exempt (stateless token auth).
- Draft-targeted writes: edits land on `DRAFT`; a published-only post is seeded a draft on first edit; the SH2 per-site allow-list is re-enforced on writes.
- `getEditable` returns the post + post-type **field schema** (the field→DOM mapping).
- A React console editor shell: site list + external-URL entry + WRITE-token selector, and a visual editor (iframe + `postMessage` + properties panel + save/publish).

**Out of scope (deferred)**
- In-console "preview this draft" UX → **SH13** (the draft-targeted writes + SH5 PREVIEW-env reads already make live preview *possible*; SH13 is the console UX + ephemeral tokens).
- Publish/unpublish **webhook emitter** → **SH12**.
- Strong per-widget typing of attrs in the editor panel → **SH11** (field schema here carries the widget *name*; the panel is informational, inline edit happens in the DOM).
- Media/asset picking in the editor (`sh:media-edit-request` is observed, not yet handled).

## 3. Data flow

```
 rendered page (data-sh-*) ──postMessage(sh:content-changed)──▶ visual-editor.page.tsx
        ▲  (shio-editor.js bridge)                                     │ accumulate edits
        │  sh:mode-change ◀───────────────────────────────────────────┘ on Save/Publish
        │                                                               │
        │                         PATCH/POST /api/v2/cda/post/{id}  (Key: <WRITE token>)
        │                                                               ▼
        └────── rendered from DRAFT (PREVIEW env, SH5) ◀── ShCdaWriteService ─▶ ShPostUnifiedWriteService
                                                              (merge into DRAFT data, publish)
```

## 4. Endpoints (`/api/v2/cda`, [ShCdaWriteAPI](../../shio-app/src/main/java/com/viglet/shio/api/cda/ShCdaWriteAPI.java))

| Method | Path | Body | Returns | Role |
|---|---|---|---|---|
| GET | `/post/{id}/editable` | n/a | `ShCdaEditablePost` (`{post, fields[]}`) | `CDA` |
| PATCH | `/post/{id}` | `ShCdaPatch` `{attrs: {name→JSON}, publish}` | `ShCdaPost` | `CDA_WRITE` |
| POST | `/post/{id}/publish` | n/a | `ShCdaPost` | `CDA_WRITE` |

Actor = the authenticated principal name (the token owner, set by the filter). A missing post or a site the token may not write collapses to `404`/empty (mirrors the SH2 read behaviour, never a leak).

## 5. Service (`ShCdaWriteService`, `@Transactional`)

- `patch(postId, attrChanges, publish, actor)`: enforce `ShApiTokenContext.allowsSite`; resolve the editable row (`DRAFT` preferred, else `PUBLISHED`); **seed a `DRAFT`** from the published row if none exists; merge `attrChanges` into the draft `data` JSON (`ObjectNode`); `ShPostUnifiedWriteService.update(..., DRAFT, actor)`; if `publish`, then `.publish(postId, actor)`. Returns the mapped `ShCdaPost`.
- `getEditable(postId)` (`readOnly`): draft-preferred post + `mapper.toFields(postType)`.

Title/summary are re-derived by the existing `applyTitleAndSummary` inside `update` (no duplicate logic here).

## 6. DTOs + mapper

`ShCdaPatch(Map<String,JsonNode> attrs, boolean publish)`, `ShCdaField(name,label,description,widget,required,isTitle,isSummary,ordinal)`, `ShCdaEditablePost(ShCdaPost post, List<ShCdaField> fields)`. [ShCdaMapper](../../shio-app/src/main/java/com/viglet/shio/api/cda/ShCdaMapper.java) gains `toFields(ShPostType)` (top-level attrs only (`shParentPostTypeAttr == null`) ordered by `ordinal`) + `toField`.

## 7. Security ([ShSecurityConfig](../../shio-app/src/main/java/com/viglet/shio/spring/security/ShSecurityConfig.java))

- `PATCH`/`POST /api/v2/cda/**` → `hasRole("CDA_WRITE")`, **placed before** the existing `GET /api/v2/cda/** → hasRole("CDA")` rule (both keycloak + basic branches).
- `/api/v2/cda/**` added to the CSRF `ignoringRequestMatchers`, token writes carry no session/XSRF cookie. The dead `/api/v2/universal-editor/**` CSRF entry (never built) was removed.
- `ShApiTokenFilter` unchanged (it already grants `ROLE_CDA_WRITE` + lifts the GET-only gate).

## 8. Frontend (`shio-react`)

- [ShUniversalEditorService](../../shio-react/src/services/universal-editor/universal-editor.service.ts): `getEditable` / `patch` / `publish`, each through a **credential-less** `axios.create({ withCredentials: false, headers: { Key } })` instance, critical, because the CDA filter only authenticates the token when no session is present; sending the console cookie would authenticate as the (non-`CDA_WRITE`) console user → 403.
- [editor-sites.page.tsx](../../shio-react/src/app/editor/editor-sites.page.tsx): site list (→ visual editor), external-URL entry, and a WRITE-token selector (`apiTokenService.list()` filtered to `scope === "WRITE"`, persisted in `localStorage` `sh-ue-token`).
- [visual-editor.page.tsx](../../shio-react/src/app/editor/visual-editor.page.tsx): sticky bar (Edit/Preview switch, viewport, Refresh, Save draft, Publish), `<iframe src={site.url | ?url=}>`, properties panel. Listens for `sh:ready`/`sh:element-selected`/`sh:content-changed`; posts `sh:mode-change`. Save groups pending edits by post id → one `patch` per post; Publish patches with `publish=true`.
- Routes in [universal-editor.routes.tsx](../../shio-react/src/app/routes/universal-editor.routes.tsx), mounted in `App.tsx`. New i18n keys (`save`/`publish`/`saving`/`draftSaved`/`published`/`saveError`/`selectToken`/`noWriteToken`) in en + pt.

## 9. Acceptance criteria

1. A `WRITE` token `PATCH`ing a post merges the change into the `DRAFT` row and re-derives title/summary; no `PUBLISHED` row is created.
2. A `PATCH` on a published-only post seeds a `DRAFT` (carrying the edit) and leaves the `PUBLISHED` snapshot intact until publish.
3. `PATCH` with `publish=true` (or `POST /publish`) promotes the draft to `PUBLISHED`.
4. The SH2 per-site allow-list blocks a write to a disallowed site (→ empty/404), leaving the row untouched.
5. `GET /post/{id}/editable` returns the post-type field schema.
6. `PATCH /api/v2/cda/**` with a `READ`/`PREVIEW` token → `403` (filter gate); a `WRITE` token is not method-gated and carries `ROLE_CDA_WRITE`.

## 10. Tests

- `ShCdaWriteTest` (`@SpringBootTest`): crit 1–5 against `ShCdaWriteService` with a WRITE `ShApiTokenContext` in the `SecurityContextHolder`.
- `ShApiTokenFilterTest`: added `PATCH` cases for crit 6 (READ→403, WRITE→200 + `ROLE_CDA_WRITE`).

## 11. Notes

- `ddl-auto=update`: no schema change (reuses `sh_post_unified`).
- The console editor needs a WRITE token by design (save-back rides the CDA write scope); the token value is already visible in the token admin, so no new secret exposure.
- An iframe page is only editable if it includes `data-sh-*` + the bridge `<script>` (page-author responsibility, demonstrated by `shio-examples/*`); otherwise the shell degrades to a plain preview.
