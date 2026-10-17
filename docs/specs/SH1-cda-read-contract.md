# SH1: Versioned read-only CDA contract, implementable spec

> Roadmap: [ROADMAP.md](../ROADMAP.md) Block A · Design: [IMPROVEMENTS.md](../IMPROVEMENTS.md) §I.1
> Status: 📋 designed · deps:, (none; foundation task)

## 1. Context: what already exists (do not rebuild)

The CDA story is **further along than it looks**. Grounding facts verified in the tree:

- **CDA token auth is shipped.** [ShApiTokenFilter](../../shio-app/src/main/java/com/viglet/shio/spring/security/auth/ShApiTokenFilter.java) reads a `Key` request header, looks up [ShApiToken](../../shio-app/src/main/java/com/viglet/shio/persistence/model/token/ShApiToken.java), **rejects any non-GET** with 403, and grants `ROLE_CDA` + `ROLE_USER`. Its own Javadoc calls it the *"CDA (Content Delivery API) Token authentication filter."* It runs `addFilterBefore(..., BasicAuthenticationFilter.class)`.
- **A delivery-ready content model is shipped.** [ShPostUnified](../../shio-app/src/main/java/com/viglet/shio/persistence/model/post/unified/ShPostUnified.java) is a single table keyed by `(id, ShPostState)` where `ShPostState ∈ {DRAFT, PUBLISHED}`. The post-type attributes live in a JSON CLOB column `data`. There is a DB index `idx_post_unified_furl` on `(furl, state)`, i.e. **path lookup by friendly URL is already indexed for**.
- **Some read endpoints exist but on the wrong contract.** [ShPostUnifiedAPI](../../shio-app/src/main/java/com/viglet/shio/api/post/unified/ShPostUnifiedAPI.java) (`/api/v2/post-unified`) returns the **raw JPA entity** and mixes read with write (create/update/publish/delete). Returning the entity leaks Hibernate proxies and internal columns and is not a freezable public contract.
- **Repository finders present:** `findByIdAndState`, `findByShFolderAndState`, `findByState`, `findById(String)→List` in [ShPostUnifiedRepository](../../shio-app/src/main/java/com/viglet/shio/persistence/repository/post/ShPostUnifiedRepository.java).

**What SH1 adds:** a *separate, read-only, DTO-based, documented* namespace `/api/v2/cda/**` that is the single stable contract a JavaScript site depends on. It does **not** touch the write side, and it does not yet add scopes (SH2) or cache headers (SH3), those build on this.

## 2. Goal & scope

**Goal:** freeze a public read contract for content delivery so `@viglet/shio-client` (SH6) and `create-shio-app` (SH8) have a stable surface.

**In scope**
- New `/api/v2/cda/**` REST namespace, GET-only.
- Hand-written response **DTO records** (never the JPA entity).
- `PUBLISHED`-state reads only (draft/preview deferred to SH5/SH13).
- friendly-URL (`furl`) **path resolution**, the feature that powers Next.js `[...slug]`.
- OpenAPI documentation under a `CDA` tag.
- Security wiring so a `Key` token (`ROLE_CDA`) reaches these endpoints.

**Out of scope (explicit, deferred)**
- Token scopes / per-site keys → **SH2**.
- `Cache-Control`/`ETag` + rate limiting → **SH3** (but DTOs must be cache-friendly: see §6).
- GraphQL delivery schema → **SH4**.
- Preview/draft exposure → **SH5/SH13**.
- Any write/publish path (stays on `/api/v2/post-unified`).

## 3. API surface (the frozen contract)

Base path: `/api/v2/cda`. All GET. All return `application/json`. Auth: `Key: <token>` header (existing filter).

| Method | Path | Purpose | Query params |
|---|---|---|---|
| GET | `/cda/site` | List published sites | n/a |
| GET | `/cda/site/{siteId}` | One site (metadata + root folder id) | n/a |
| GET | `/cda/object/{folderId}/list` | Children of a folder (subfolders + published posts) | `page`, `size` (default 0/50), `locale` |
| GET | `/cda/object/{id}/path` | Breadcrumb path for a folder or post | n/a |
| GET | `/cda/post/{id}` | One published post (attrs expanded) | n/a |
| GET | `/cda/post/by-url` | Resolve a published post by site + friendly URL | `siteId` (req), `url` (req, e.g. `/developers/intro`), `locale` |
| GET | `/cda/query` | Query published posts | `siteId`, `folderId`, `postType`, `page`, `size`, `locale` |
| GET | `/cda/search` | Full-text search of published posts | `q` (req), `siteId`, `page`, `size`, `locale` |

`locale` (SH53 on `by-url`, SH1127 on the listings) narrows to posts written in that tag, the
posts only, never a folder's subfolders, and counts only those in `totalPosts`. Omitted or blank,
every locale, which is what each read answered before it took one.

**404 semantics:** unknown id, unpublished content, or unresolvable url → `404` with the error body in §5.4. Never return a draft.

## 4. Response DTOs (Java records: frozen shape)

Place in `com.viglet.shio.api.cda.dto`. These are the contract; the JPA entity is mapped into them by a `ShCdaMapper` (§5.2). Field names are **public API, do not rename after release**.

```java
// ShCdaSite
public record ShCdaSite(
    String id,
    String name,
    String description,
    String url,
    String rootFolderId,
    ShCdaSiteLocales locales) {}   // SH1123 — on /site reads; absent on a path's site

// ShCdaSiteLocales (SH1123 — what a language switcher is built from)
public record ShCdaSiteLocales(
    String defaultLocale,
    List<String> enabled,
    Map<String, String> fallback,
    boolean declared) {}

// ShCdaFolder (lightweight, used in listings + path)
public record ShCdaFolder(
    String id,
    String name,
    boolean root) {}

// ShCdaPostSummary (listing item — no heavy attrs)
public record ShCdaPostSummary(
    String id,
    String title,
    String summary,
    String furl,
    String postType,
    Instant date,
    Instant modifiedDate) {}

// ShCdaPost (full — attrs expanded from the data CLOB)
public record ShCdaPost(
    String id,
    String title,
    String summary,
    String furl,
    String postType,
    String siteId,
    String folderId,
    boolean published,
    Instant date,
    Instant modifiedDate,
    Instant publicationDate,
    JsonNode attrs) {}      // the parsed `data` JSON document

// ShCdaListing (folder children)
public record ShCdaListing(
    String folderId,
    List<ShCdaFolder> folders,
    List<ShCdaPostSummary> posts,
    int page,
    int size,
    long totalPosts) {}

// ShCdaPath (breadcrumb)
public record ShCdaPath(
    ShCdaSite site,
    List<ShCdaFolder> breadcrumb,   // root → ... → current
    ShCdaFolder currentFolder) {}
```

Notes:
- `attrs` is the parsed `ShPostUnified.data` CLOB as a `JsonNode`, **not** a re-stringified blob. Parse once in the mapper; on parse failure return `attrs = {}` (mirror the `parseSettings()` guard documented in `agents.md`).
- DTOs expose **no** `owner`/`modifier`/`publisher`/`publishStatus`, those are authoring concerns, not delivery. Keeps the public surface minimal and safe.

## 5. Implementation

### 5.1 Controller: `ShCdaAPI`

`com.viglet.shio.api.cda.ShCdaAPI`, `@RestController @RequestMapping("/api/v2/cda")`, `@Tag(name = "CDA")`. Constructor-inject `ShCdaService`. One thin method per row in §3; no business logic in the controller. `by-url` and `{id}` that miss → `ResponseEntity.notFound()`.

### 5.2 Service + mapper: `ShCdaService` / `ShCdaMapper`

- `ShCdaService` orchestrates repository calls, always pinning `ShPostState.PUBLISHED`.
- `ShCdaMapper` converts entities → DTOs and parses `data` → `JsonNode` (Jackson `ObjectMapper`, cached). Centralizing the mapping is what lets the entity evolve without breaking the contract.

### 5.3 Repository additions

Add to `ShPostUnifiedRepository`:

```java
Optional<ShPostUnified> findByShSite_IdAndFurlAndState(String siteId, String furl, ShPostState state);
Page<ShPostUnified> findByShFolderAndState(ShFolder folder, ShPostState state, Pageable pageable);
Page<ShPostUnified> findByShSite_IdAndState(String siteId, ShPostState state, Pageable pageable);
List<ShPostUnified> findByShSite_IdAndShPostType_NameAndState(String siteId, String postType, ShPostState state);
```

The `furl` finder rides the existing `idx_post_unified_furl (furl, state)` index, no migration needed. Folder children for **subfolders** reuse the existing `ShFolderRepository` (find by `parentFolder`).

### 5.4 Error body

Reuse the project's existing error bean if present ([bean/error](../../shio-app/src/main/java/com/viglet/shio/bean/error)); otherwise:
```json
{ "status": 404, "error": "Not Found", "path": "/api/v2/cda/post/abc" }
```

### 5.5 Security wiring (`ShSecurityConfig`)

The token filter already authenticates `Key` requests and sets `ROLE_CDA`. Add an explicit rule so the intent is documented and so the namespace is GET-restricted at the chain level (defense in depth, the filter already blocks non-GET, this is belt-and-suspenders). In **both** the Keycloak and the basic branches of [ShSecurityConfig](../../shio-app/src/main/java/com/viglet/shio/spring/security/ShSecurityConfig.java):

```java
authorizeRequests.requestMatchers(mvc.matcher(HttpMethod.GET, "/api/v2/cda/**"))
        .hasRole("CDA");
```

placed before `anyRequest().authenticated()`. Do **not** `permitAll()` the CDA, it requires a token (anonymous public access is a deliberate later choice, not a default). No CSRF entry needed (GET is CSRF-exempt).

## 6. Forward-compatibility constraints (so SH2/SH3/SH5 don't break the contract)

- **DTOs must be deterministic and side-effect free** → SH3 can wrap responses in `ETag` = hash(body) trivially.
- **State is a service-level parameter, not a URL segment** → SH5 preview just flips the pinned state for a `PREVIEW`-scoped token; URLs stay identical.
- **`siteId` is a first-class query param on listing/query** → SH2 per-site key scoping filters at the service boundary without URL changes.

## 7. Acceptance criteria

1. `GET /api/v2/cda/site` with a valid `Key` returns `200` + `List<ShCdaSite>`; **without** a key returns `401`.
2. A **non-GET** to any `/api/v2/cda/**` returns `403` (existing filter), covered by a test.
3. `GET /api/v2/cda/post/{id}` of a DRAFT-only post (no published row) returns `404`.
4. `GET /api/v2/cda/post/by-url?siteId=S&url=/x/y` resolves the published post whose `furl` matches, `404` otherwise.
5. `attrs` in `ShCdaPost` is a JSON object (parsed), and a post whose `data` is malformed returns `attrs: {}`, not a 500.
6. No response anywhere in `/cda/**` contains `owner`, `publisher`, `publishStatus`, or a Hibernate proxy field.
7. OpenAPI (`/swagger-ui`) shows a `CDA` tag with all 7 operations.

## 8. Tests

- `ShCdaAPITest` (`@WebMvcTest` or `@SpringBootTest` + MockMvc): one test per acceptance criterion 1–7. Seed one site, one folder, one PUBLISHED post + one DRAFT-only post.
- `ShCdaMapperTest` (plain unit): entity→DTO field mapping; malformed-`data` guard (criterion 5); field-exclusion guard (criterion 6, assert serialized JSON has no forbidden keys).

## 9. Rollout

1. Add DTOs + mapper + service + repository finders.
2. Add controller.
3. Wire security rule (both branches).
4. Add tests.
5. Document in OpenAPI (annotations) and add a short "Content Delivery API" page under `docs/`.
6. Leave `/api/v2/post-unified` untouched (console keeps using it). A later cleanup can make the console-side read go through the entity while public delivery goes through `/cda`.

## 10. Estimate

~1–1.5 day: DTOs+mapper (2h), service+repo finders (2h), controller (1h), security+OpenAPI (1h), tests (3h), docs (1h).
