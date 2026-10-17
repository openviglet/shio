# SH62: post-types as typed TS code (DSL → canonical JSON), implementable spec

> Roadmap: [ROADMAP.md](../ROADMAP.md) Block B · Design: [IMPROVEMENTS.md](../IMPROVEMENTS.md) §II.7
> Status: 💭 designed, not started · deps: SH16 (shipped), SH11 (shipped)
> Epic SH62 → tasks **SH63** (DSL core) · **SH64** (CLI compile) · **SH65** (drift guard) · **SH66** (console lock)

## 1. Context: what already exists (do not rebuild)

- **SH16 already round-trips the model as code.** `shio pull` writes
  `shio/post-types/*.json` (the lossless `ShPostTypeDef`), `shio push` diffs and
  applies it via `PUT /api/v2/post-type/{name}`. The pure diff/serialize logic is
  [post-types-io.mjs](../../cli/src/post-types-io.mjs) (`normalizeDef`,
  `serializeDef`, `diffDefs`, `planPush`).
- **SH11 already goes the other direction**: `generateShioTypes` in
  `@viglet/shio-client` turns the (lossy) CDA schema into a `.d.ts` for
  **consuming** content. SH62 is its **inverse for authoring**: typed code → model.
- **The post-type GUI editor exists** in `shio-react`
  (`post-type.edit.page.tsx`), and saves through the same admin API. It is a
  **second legitimate writer** of the model, the reason this spec is the "middle
  path", not "DSL as the only source of truth".
- **The widget catalog is fixed** (14 widgets) and `widgetSettings` is an opaque
  JSON **string** on the wire (`ShPostTypeAttrDef.widgetSettings`).

**What SH62 adds:** a typed TS DSL package that **compiles to the existing
canonical `ShPostTypeDef` JSON**, a CLI compile step that feeds it into the
unchanged `push`/`pull` pipeline, a CI drift guard, and a console lock so a
code-owned model isn't silently overwritten in the GUI.

## 2. Goal & scope

**In scope**
- **SH63** `@viglet/shio-model`, a pure, dependency-free typed builder; factories
  per widget; `widgetSettings` as per-widget discriminated unions; emits the
  normalized `ShPostTypeDef` (delegates to `normalizeDef`/`serializeDef`).
- **SH64** CLI compile step, `push`/`pull`/new `build` recognize `*.ts`/`*.mjs`
  DSL sources in `shio/post-types/` and compile to canonical JSON before the diff.
- **SH65** `shio push --check`, compile + diff + non-zero exit on drift (no writes).
- **SH66** Console "managed by code" lock, a `managed` flag + read-only badge in
  the post-type editor for code-owned types.

**Out of scope (deferred / not needed)**
- Lossless **decompilation** (JSON → hand-written DSL). `pull` keeps emitting JSON;
  scaffolding a *starter* `.ts` from JSON is a best-effort convenience, not a
  round-trip guarantee (comments/loops/shared helpers are not recovered).
- Any change to the admin API or the wire format, JSON stays canonical.
- System post-types, backend-owned; the DSL never claims them (`push` already 409s).
- A VSCode extension, the whole point is that `tsserver` makes one unnecessary.

## 3. Data flow

```
 shio/post-types/news.ts  (typed DSL — source of truth)
   │  import compile step (SH64): dynamic import() the module, collect default/named postType() exports
   ▼
 ShPostTypeDef objects  ──normalizeDef/serializeDef (SH16, reused)──►  canonical JSON (build output)
   │                                                                       │
   │  diffDefs / planPush (SH16, unchanged)                                │ shio build → shio/.out/*.json (optional)
   ▼                                                                       ▼
 PUT /api/v2/post-type/{name}  ◄── shio push ────────────────────  (wire format UNCHANGED)
                               ◄── shio push --check (SH65): diff only, exit 1 on drift, no writes
 GET /api/v2/post-type         ──► managed flag (SH66) ──► console editor renders read-only for code-owned types
```

The DSL is the source; JSON is the build output; the admin API and `push` diff
are untouched. A `*.json` left in `shio/post-types/` still works, `.ts`/`.mjs`
and `.json` coexist; compilation only adds a pre-step for DSL files.

## 4. SH63: DSL core (`@viglet/shio-model`)

New pure ESM workspace package (sibling to `@viglet/shio-client`; **no** React, no
network, no fs, same constraints as `generateShioTypes`).

### 4.1 Builders
- `postType(name, { title?, namePlural?, description?, workflowPublishEntity?, fields })`
  → returns a `ShPostTypeDef` (with `system: false`; ordinals auto-assigned by
  array position unless overridden).
- One factory per widget, each returning a `ShPostTypeAttrDef`. Common options on
  all: `{ label?, description?, required?, isTitle?, isSummary?, ordinal? }`.

| Factory | Widget name (wire) | Typed settings |
|---|---|---|
| `text(name, opts?)` | `Text` | n/a |
| `textArea(name, opts?)` | `Text Area` | n/a |
| `htmlEditor(name, opts?)` | `HTML Editor` | n/a |
| `checkBox(name, opts)` | `Check Box` | `{ choices: Record<string,string>, allowNull?: boolean }` |
| `comboBox(name, opts)` | `Combo Box` | `{ choices: Record<string,string>, allowNull?: boolean }` |
| `date(name, opts?)` | `Date` | n/a |
| `hidden(name, opts?)` | `Hidden` | n/a |
| `multiSelect(name, opts?)` | `Multi Select` | `{ postType?: string, folderId?: string }` |
| `aceJs(name, opts?)` | `Ace Editor - Javascript` | n/a |
| `aceHtml(name, opts?)` | `Ace Editor - HTML` | n/a |
| `contentSelect(name, opts?)` | `Content Select` | `{ folderId?: string }` |
| `file(name, opts?)` | `File` | `{ folderId?: string }` |
| `relator(name, opts)` | `Relator` | `{ title?: string, fields: AttrDef[] }` (recurses) |
| `tab(name, opts?)` | `Tab` | container, no settings, no data |

### 4.2 widgetSettings codec (the heart of SH63)
The DSL accepts **ergonomic, typed** settings and serializes them to the exact
on-the-wire JSON string the backend expects, hiding the legacy shapes:
- `choices: { tech: "Tech", biz: "Business" }` → `"{\"choices\":\"tech: Tech\\nbiz: Business\",\"allowNull\":1}"`
  (the `"id: label\n…"` string + `1`/`0` ints, matching `import/post-types.json`).
- `file({ folderId })` → `"{\"file\":{\"folderId\":\"…\"}}"`;
  `contentSelect` → `{"contentSelect":{…}}`; `multiSelect` → `{"multiselect":{…}}`.
- `relator({ title })` → `"{\"relator\":{\"title\":\"…\"}}"`; nested fields go in `fields`.
- A field with no settings emits `null` (not the string `"null"`).
- **Symmetric decode** (`fromWidgetSettings`) so a starter `.ts` can be scaffolded
  from `pull`ed JSON, and so SH64's diff compares apples to apples.

### 4.3 Output
- `toDef(postType)` returns the plain `ShPostTypeDef` object.
- Serialization reuses SH16's `normalizeDef`/`serializeDef` (move them to a shared
  pure module both `cli` and `@viglet/shio-model` import, or re-export) so the
  byte-identical canonical JSON drives the diff, **no second serializer**.

## 5. SH64: CLI compile step (`cli`)
- `readLocalDefs` (today JSON-only, [push.mjs](../../cli/src/commands/push.mjs))
  gains: for `*.ts`/`*.mjs`, dynamically `import()` the module and collect every
  `ShPostTypeDef` it exports (default export, named exports, or an exported array).
  `.ts` is loaded via the project's existing TS toolchain (the same `tsc`/loader
  `create-shio-app` ships), **no new bundler in the CLI**.
- `pull` is unchanged (still writes JSON); a new `--as ts` flag may scaffold
  starter `.ts` via the §4.2 decoder (best-effort, documented as lossy on re-pull).
- New `shio build`, compile DSL → write canonical JSON artifacts (e.g.
  `shio/.out/*.json`) for inspection/CI without pushing.
- Mixed dirs allowed: `.ts`/`.mjs` and `.json` coexist; a name defined twice is an
  error (ambiguous source).

## 6. SH65: drift guard
- `shio push --check`: run the compile + `diffDefs`, print the plan, **write
  nothing**, exit `0` iff `added+changed+removed` are all empty, else exit `1`.
- Distinct from `--dry-run` (which is informational, always exit 0). Intended for
  CI: "assert git == server".

## 7. SH66: console "managed by code" lock
- Backend: a `managed` boolean on the post-type, set true by `push`/admin-API
  upserts that originate from code (carried on `ShPostTypeDef`; persisted via a
  Liquibase `addColumn` per the **Database Schema** rules, nullable + backfill
  `false`, DB-agnostic `boolean`).
- `GET /api/v2/post-type` and the console list/get expose it.
- React `post-type.edit.page.tsx`: when `managed`, render fields **read-only** + a
  badge ("Managed by code") + a hint linking to the repo workflow. Save disabled.
  System types remain separately read-only (their existing 409 path is unchanged).
- This makes the DSL↔GUI boundary **explicit** rather than a silent
  last-writer-wins race.

## 8. Acceptance criteria
1. `@viglet/shio-model` compiles a typed `postType(...)` into a `ShPostTypeDef`
   whose `serializeDef` output is **byte-identical** to the equivalent hand-written
   `shio/post-types/*.json` (the §4.2 codec round-trips every widget's settings).
2. A bad widget setting (e.g. `comboBox` without `choices`) is a **TypeScript
   compile error**, not a runtime/`push` 400.
3. `shio push` applies a `.ts`-authored model exactly as it applies the JSON
   equivalent; the admin API and wire format are unchanged; `.ts` and `.json`
   coexist in the same dir.
4. `shio push --check` exits non-zero on drift, zero when git == server, writing
   nothing.
5. A `managed` post-type renders read-only in the console editor (save disabled,
   badge shown); system types still 409 on `push`.
6. Existing SH16/SH11 CLI + backend suites stay green.

## 9. Tests
- JS (`@viglet/shio-model`, `node:test`): widget-by-widget settings codec
  round-trip; `toDef` ordinal assignment; relator/tab nesting; byte-identical
  serialization vs a fixture JSON.
- JS (`cli`): `readLocalDefs` over a mixed `.ts`/`.json` dir (fake module loader +
  in-memory fs); `push --check` exit codes; duplicate-name error.
- Backend (`ShPostTypeAdminServiceTest`): `managed` flag persists + is returned;
  upsert sets it; system guard unchanged.
- A `ShLiquibaseChangelogAgnosticTest` pass for the new `managed` column; SH26
  cross-DB ITs still green.

## 10. Notes / design rationale (see §II.7)
- **Middle path, on purpose.** JSON stays canonical; the DSL is an optional layer.
  This survives the two facts that kill "DSL as sole source of truth": the GUI is
  a second writer (→ SH66 lock) and `pull` can't losslessly regenerate DSL (→ JSON
  remains the interchange format, `pull --as ts` is best-effort only).
- **No duplicate serializer.** SH63 and the CLI share SH16's `normalizeDef`/
  `serializeDef`, so the DSL can never disagree with the canonical bytes.
- **DSL owns the custom subset only.** System types stay backend-owned; tenants
  that diverge a model (SH22 provisioning) are out of the single-file story by
  design.
- **No VSCode extension.** `tsserver` already supplies autocomplete, validation,
  rename, and go-to-definition, the original motivation for an extension's
  schema-validation feature evaporates once the model is typed code.
