# SH16: the `shio` CLI (post-types as code), implementable spec

> Roadmap: [ROADMAP.md](../ROADMAP.md) Block C · Design: [IMPROVEMENTS.md](../IMPROVEMENTS.md) §III.5
> Status: ✅ shipped · deps: SH6 (shipped)

## 1. Context: what already existed (do not rebuild)

- **The SDK + CDA are the delivery surface.** `@viglet/shio-client` (SH6) wraps the CDA reads; `create-shio-app` (SH8) scaffolds a Next.js site; `shio-types` (SH11, a bin in `@viglet/shio-client`) turns the **read-only, lossy** CDA post-type schema (`GET /api/v2/cda/post-type`) into a `.d.ts`.
- **There was no `shio` command** unifying scaffold / types / content-model workflows, and **no write path for post-types**, `ShPostTypeRepository` has `save`/`delete` but no controller called them; `import/post-types.json` is no longer loaded.
- The CDA schema (SH11) is **lossy** by design: it drops `Tab`, never carries `widgetSettings`, and omits ids, so it cannot round-trip the authoring model.

**What SH16 adds:** a `@viglet/shio` CLI (`init`/`types`/`pull`/`push`/`dev`/`deploy`) **plus** a lossless, console-authenticated post-type admin API the CLI's `pull`/`push` round-trip against.

## 2. Goal & scope

**In scope**
- Backend: a lossless post-type admin API (`GET/PUT/DELETE /api/v2/post-type[/{name}]`) exposing the full authoring model (incl. `Tab`, verbatim `widgetSettings`, nested Relator fields); console-authenticated (HTTP Basic), CSRF-exempt (stateless CLI), system-type protected.
- A `@viglet/shio` workspace package (`cli/`) with `init`, `types`, `pull`, `push` (`--dry-run`/`--prune`), `dev`, `deploy`.
- Workspace + publish wiring; a `create-shio-app/scaffold` export so `init` reuses the scaffolder.

**Out of scope (deferred / not needed)**
- A React console UI for post-type CRUD over the new API (the write API is CLI-consumed; a console editor is a separate change).
- A schema change, the API writes the existing `ShPostType`/`ShPostTypeAttr` tables.
- `deploy` as a separate transport, it is a thin `push`+`types` composite.

## 3. Data flow

```
 ShPostType / ShPostTypeAttr / ShWidget (JPA)
   │  ShPostTypeAdminMapper.toDef (lossless: keep Tab, widgetSettings, recurse Relator)
   ▼
 GET  /api/v2/post-type        ─► ShioAdminClient.getPostTypeDefs ─► shio pull ─► shio/post-types/*.json
 PUT  /api/v2/post-type/{name} ◄─ ShioAdminClient.putPostTypeDef  ◄─ shio push ◄─ (diff vs remote)
 DELETE /api/v2/post-type/{name}                                     (--prune, never system types)

 GET /api/v2/cda/post-type (SH11) ─► ShioClient.getPostTypes ─► generateShioTypes ─► shio types ─► lib/shio-types.d.ts
```

## 4. Backend (`shio-app`)

### 4.1 DTOs ([api/post/type/dto](../../shio-app/src/main/java/com/viglet/shio/api/post/type/dto))
- `ShPostTypeDef(name, title, namePlural, description, workflowPublishEntity, boolean system, List<ShPostTypeAttrDef> fields)`.
- `ShPostTypeAttrDef(name, label, description, widget, boolean required, boolean isTitle, boolean isSummary, int ordinal, String widgetSettings, List<ShPostTypeAttrDef> fields)`: recursive; `widget` is the widget **name**. Ids omitted (a type is keyed by `name`).

### 4.2 Mapper ([ShPostTypeAdminMapper](../../shio-app/src/main/java/com/viglet/shio/api/post/type/ShPostTypeAdminMapper.java))
- `toDef`/`toAttrDef`, inverse of `ShCdaMapper.toPostTypeSchema`: keep **every** attr (incl. `Tab`), carry the raw widget name + `widgetSettings`, nest children under `fields`, order by `ordinal`.

### 4.3 Service ([ShPostTypeAdminService](../../shio-app/src/main/java/com/viglet/shio/api/post/type/ShPostTypeAdminService.java))
- `list()` / `get(name)` (read-only tx).
- `upsert(name, def)`: create or fully replace (idempotent by name). **Rejects system types** (`ShSystemPostTypeException` → 409). Resolves each widget via `ShWidgetRepository.findByName` (`ShUnknownWidgetException` → 400 for unknown).
  - **Cascade safety:** `ShPostTypeAttr` maps `shWidget`/`shPostType`/`shParentPostTypeAttr` as `@ManyToOne(cascade = ALL)`, so a naive orphan-removal on update would cascade a `REMOVE` into the **shared widget catalog** (and the post-type itself). Before replacing the attr set, those associations are **detached** so only the attrs are deleted.
- `delete(name)`: `false` if absent, rejects system types.

### 4.4 Endpoints ([ShPostTypeAPI](../../shio-app/src/main/java/com/viglet/shio/api/post/type/ShPostTypeAPI.java))
| Method | Path | Returns |
|---|---|---|
| GET | `/api/v2/post-type` | `List<ShPostTypeDef>` |
| GET | `/api/v2/post-type/{name}` | `ShPostTypeDef` (404 unknown) |
| PUT | `/api/v2/post-type/{name}` | `ShPostTypeDef` (upsert) |
| DELETE | `/api/v2/post-type/{name}` | 204 / 404 / 409 (system) |

### 4.5 Security ([ShSecurityConfig](../../shio-app/src/main/java/com/viglet/shio/spring/security/ShSecurityConfig.java))
- New `POST_TYPE_PATH = "/api/v2/post-type/**"` added to the **CSRF `ignoringRequestMatchers`** list (next to `CDA_PATH`), the CLI calls it statelessly with HTTP Basic, so there is no session/XSRF cookie. Authorization falls through to `anyRequest().authenticated()`. System protection is in the service, not a role.

## 5. CLI (`@viglet/shio`, `cli/`)

Pure-Node ESM, depends on `@viglet/shio-client` + `create-shio-app` (`workspace:*`).
- [src/index.mjs](../../cli/src/index.mjs), subcommand dispatch + `--help`.
- [src/args.mjs](../../cli/src/args.mjs), argv parser (`--k v`, `--k=v`, booleans, kebab→camel).
- [src/context.mjs](../../cli/src/context.mjs), injectable IO (`fetch`/`fs`/streams) + `resolveAdmin`/`resolveCda`.
- [src/admin-client.mjs](../../cli/src/admin-client.mjs), Basic-auth wrapper over `/api/v2/post-type` (reuses `ShioHttpError`).
- [src/post-types-io.mjs](../../cli/src/post-types-io.mjs), **pure**: `serializeDef` (deterministic JSON), `diffDefs`, `planPush` (prune never targets system types).
- [src/commands/](../../cli/src/commands): `init` (→ `create-shio-app/scaffold`), `types` (→ SH11 codegen, CDA token), `pull`/`push` (Basic auth), `dev` (types + spawn dev server + interval refresh), `deploy` (`push`+`types`).

**Auth split:** `pull`/`push`/`deploy` use the console user/pass (`SHIO_USER`/`SHIO_PASSWORD`), editing the model is an admin act; `types`/`dev` use a CDA token (`SHIO_CDA_TOKEN`).

## 6. Workspace / publish
- `pnpm-workspace.yaml` adds `cli`; root `package.json` `test` filters `@viglet/shio`.
- `create-shio-app/package.json` adds `"exports": { "./scaffold": "./src/scaffold.mjs", … }`.
- `.github/workflows/publish-js-sdk.yml` bumps + publishes `@viglet/shio` after `shio-client`/`create-shio-app`.
- Maven unchanged, `frontend-maven-plugin` only builds `shio-react`; the CLI ships via npm.

## 7. Acceptance criteria
1. `GET /api/v2/post-type[/{name}]` returns the lossless model (Tab kept, `widgetSettings` preserved, Relator nested); 404 unknown.
2. `PUT` creates and fully replaces (idempotent); update removes dropped fields **without deleting the shared widget catalog**; unknown widget → 400; system type → 409.
3. `DELETE` removes a non-system type (404 absent, 409 system).
4. `shio pull` writes deterministic per-type JSON; `shio push` diffs and applies (`--dry-run` changes nothing; `--prune` deletes non-system removals); `shio types` writes a `.d.ts`.
5. Existing CDA/JS suites stay green.

## 8. Tests
- Backend: [ShPostTypeAdminServiceTest](../../shio-app/src/test/java/com/viglet/shio/api/post/type/ShPostTypeAdminServiceTest.java) (`@SpringBootTest`), lossless round-trip, cascade-safe update (shared widget survives), unknown widget, system read-only, delete.
- JS: [cli/test](../../cli/test) (`node:test`, **15 green**), `post-types-io` (serialize/diff/plan + system-prune guard), `args`, `admin-client` (Basic header, 404/204→null, error→`ShioHttpError`), `types`/`push` commands with fake fetch + in-memory fs.

## 9. Notes
- No schema change (writes existing tables); no new role (console-authenticated + service-level system guard).
- `init` delegates to `create-shio-app`'s scaffolder, so `shio init` and `npm create shio-app` produce identical projects.
