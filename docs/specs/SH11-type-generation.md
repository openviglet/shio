# SH11: TypeScript type generation from post-types, implementable spec

> Roadmap: [ROADMAP.md](../ROADMAP.md) Block B · Design: [IMPROVEMENTS.md](../IMPROVEMENTS.md) §II.6
> Status: ✅ shipped · deps: SH6 (shipped)

## 1. Context: what already existed (do not rebuild)

- **The CDA delivers `attrs` untyped.** [ShCdaPost](../../shio-app/src/main/java/com/viglet/shio/api/cda/dto/ShCdaPost.java) exposes `attrs` as a plain JSON map; the SDK's `ShioPost.attrs` is `Record<string, unknown>`. Page code has no autocomplete on `post.attrs.*`, and `ShioField`'s widget resolution is heuristic (§II.2) because no per-field widget type is delivered.
- **The content model lives only in the console API + JPA.** `ShPostType` / `ShPostTypeAttr` (+ `ShWidget`) describe each field, but were never exposed on the public CDA surface.
- **SH10 added a flat field descriptor.** [ShCdaField](../../shio-app/src/main/java/com/viglet/shio/api/cda/dto/ShCdaField.java) + `ShCdaMapper.toFields` describe top-level fields for the editor panel, but flat (no Relator children) and without the array-vs-scalar fact a code generator needs.
- **`@viglet/shio-client` (SH6) is the zero-dep client.** It already wraps the CDA read endpoints and is the dependency every JS consumer (`shio-react-sdk`, `create-shio-app`) builds on.

**What SH11 adds:** a public CDA **post-type schema** endpoint + a pure, dependency-free **codegen** in `@viglet/shio-client` and a `shio-types` CLI that turns the schema into a `.d.ts`, so page authors get autocomplete on `post.attrs.*`.

## 2. Goal & scope

**In scope**
- A read-only CDA schema surface (`GET /api/v2/cda/post-type[/{name}]`) returning each post-type's content model, recursive into Relator sub-fields, marking array-valued fields.
- `@viglet/shio-client`: `getPostTypes` / `getPostType` client methods + a pure `generateShioTypes(schemas)` transform + a `shio-types` Node CLI (fetch → generate → write).
- Wire the codegen into the `create-shio-app` starter (`npm run shio:types`) + document it.

**Out of scope (deferred / not needed)**
- Rewiring `ShioField` to resolve widgets via the schema (§II.2 follow-up), SH11 *exposes* the schema; consuming it in the render components is a later, independent change.
- A full `shio` CLI (`init`/`dev`/`pull`/`push`) → **SH16**. `shio-types` is a single-purpose bin, not that CLI.
- Numeric/boolean/date scalar refinement, every scalar widget delivers a `string` over the CDA today, so the generator emits `string` (arrays → `string[]`, Relator → `Array<{…}>`).

## 3. Data flow

```
 ShPostType / ShPostTypeAttr / ShWidget (JPA)
        │  ShCdaMapper.toPostTypeSchema (drop Tab, mark multiple, recurse Relator)
        ▼
 GET /api/v2/cda/post-type  ──►  ShioClient.getPostTypes()  ──►  generateShioTypes()  ──►  shio-types.d.ts
   (ROLE_CDA, cacheable)            (@viglet/shio-client)         (pure transform)        (one Post iface / type)
                                          ▲
                              bin/shio-types.mjs (CLI: --url/--token/--out, env fallbacks)
```

## 4. Backend

### 4.1 DTOs ([api/cda/dto](../../shio-app/src/main/java/com/viglet/shio/api/cda/dto))
- `ShCdaPostTypeSchema(name, title, namePlural, description, List<ShCdaSchemaField> fields)`.
- `ShCdaSchemaField(name, label, description, widget, boolean required, boolean multiple, boolean isTitle, boolean isSummary, int ordinal, List<ShCdaSchemaField> fields)`: recursive. A new DTO rather than extending the SH10 `ShCdaField` record (whose shape the editor panel depends on).

### 4.2 Mapper ([ShCdaMapper](../../shio-app/src/main/java/com/viglet/shio/api/cda/ShCdaMapper.java))
- `toPostTypeSchema(ShPostType)` → top-level, value-bearing fields ordered by `ordinal`.
- `toSchemaField(ShPostTypeAttr)` → recurses into a Relator's sub-attributes. `multiple` is true when the field has sub-fields (Relator) **or** the widget is in `ARRAY_WIDGETS` (`Check Box`, `Multi Select`). Layout-only widgets in `CONTAINER_WIDGETS` (`Tab`) are dropped.

### 4.3 Service ([ShCdaService](../../shio-app/src/main/java/com/viglet/shio/api/cda/ShCdaService.java))
- `listPostTypes()` (all post-types, ordered by name) + `getPostType(name)` (by API name, empty if unknown), via the injected `ShPostTypeRepository`. **Not** site-scoped, post-types are global and the schema carries the field shape only, never content (so the SH2 allow-list does not apply).

### 4.4 Endpoints ([ShCdaAPI](../../shio-app/src/main/java/com/viglet/shio/api/cda/ShCdaAPI.java))
| Method | Path | Returns | Role |
|---|---|---|---|
| GET | `/api/v2/cda/post-type` | `List<ShCdaPostTypeSchema>` | `CDA` |
| GET | `/api/v2/cda/post-type/{name}` | `ShCdaPostTypeSchema` (404 if unknown) | `CDA` |

No security change: both are GET under `/api/v2/cda/**`, already `hasRole("CDA")` and covered by the SH3 cache/rate-limit filters.

## 5. Client + codegen (`@viglet/shio-client`)

- **Types** ([types.ts](../../shio-client/src/types.ts)): `ShioFieldSchema` (recursive) + `ShioPostTypeSchema`, mirroring the DTOs.
- **Client** ([client.ts](../../shio-client/src/client.ts)): `getPostTypes()` → `GET /post-type`; `getPostType(name)` → `GET /post-type/{name}` (404 → `null`).
- **Codegen** ([codegen.ts](../../shio-client/src/codegen.ts)): `generateShioTypes(schemas, { importFrom?, banner? })` (a **pure** string transform (no fs/network, so unit-testable). Per post-type it emits `<Pascal>Attrs`, a `<Pascal>Post = ShioPost & { postType: "<name>"; attrs: <Pascal>Attrs }` (an **intersection** type, not `interface extends`) an interface is not assignable to `ShioPost.attrs`'s `Record<string, unknown>`, so `extends` would be TS2430), then a `ShioPostTypes` name→type map + `ShioPostTypeName` union. Field type: Relator → `Array<{ …inlined… }>`, `multiple` → `string[]`, else `string`; non-required fields get `?`. Names that aren't valid identifiers are PascalCased (interfaces) or quoted (property keys); output is sorted for determinism.
- **CLI** ([bin/shio-types.mjs](../../shio-client/bin/shio-types.mjs)): `shio-types --url --token --out [--import-from]`, env fallbacks `SHIO_URL` / `SHIO_CDA_TOKEN`, `--out -` writes to stdout. Imports the built bundle + `generateShioTypes`; `bin` + `files` declared in [package.json](../../shio-client/package.json).

## 6. Starter integration (`create-shio-app`)
- [template/package.json](../../create-shio-app/template/package.json) gains `"shio:types": "shio-types --url … --token $SHIO_CDA_TOKEN --out lib/shio-types.d.ts"` (the bin ships with the already-present `@viglet/shio-client` dep).
- [template/README.md](../../create-shio-app/template/README.md): a "Typed content" section, run `npm run shio:types`, cast a fetched post to its generated `…Post` type for typed `attrs`.

## 7. Acceptance criteria
1. `GET /api/v2/cda/post-type` lists every post-type's schema; `/{name}` returns one (404 unknown).
2. The schema drops `Tab` fields, marks `Multi Select` / `Check Box` and Relator fields `multiple`, and nests a Relator's sub-fields under `fields`.
3. `generateShioTypes` emits a `…Post` interface + `…Attrs` per post-type and a `ShioPostTypes` map; required→non-optional `string`, array→`string[]`, Relator→`Array<{…}>`; non-identifier names quoted/PascalCased; output deterministic.
4. `shio-types` CLI fetches the schema and writes the `.d.ts` (or stdout with `--out -`); `--help` and unknown-arg paths behave.
5. Existing CDA suites (SH1–SH5, SH10) stay green; the client/codegen tests run against the built bundle.

## 8. Tests
- Backend: [ShCdaPostTypeSchemaTest](../../shio-app/src/test/java/com/viglet/shio/api/cda/ShCdaPostTypeSchemaTest.java) (`@SpringBootTest`), crit 1–2 (Tab dropped, `multiple` for Multi Select + Relator, nested sub-fields, unknown-name empty). Full `com.viglet.shio.api.cda` package = **38 green**.
- JS: [test/codegen.test.mjs](../../shio-client/test/codegen.test.mjs) (crit 3) + `getPostTypes`/`getPostType` cases in [test/client.test.mjs](../../shio-client/test/client.test.mjs). **19 `node:test` green**; `create-shio-app` scaffold suite **10 green**.

## 9. Notes
- No schema change (read-only over existing tables); no security change (GET under the existing `ROLE_CDA` rule).
- `generateShioTypes` is exported from the package index, so SDK consumers can generate in-process (e.g. a build step) without the CLI; the CLI is the convenience wrapper.
- This also unblocks the §II.2 `ShioField` follow-up (the real widget type is now deliverable), left as a separate change.
