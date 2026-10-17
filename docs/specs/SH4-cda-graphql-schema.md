# SH4: Real GraphQL delivery schema, implementable spec

> Roadmap: [ROADMAP.md](../ROADMAP.md) Block A · Design: [IMPROVEMENTS.md](../IMPROVEMENTS.md) §I.4
> Status: 📋 designed · deps: SH1 (shipped)

## 1. Context: what already exists (do not rebuild)

- **Spring for GraphQL is wired** (`spring-boot-starter-graphql`, `spring.graphql.path=/graphql`, GraphiQL on). The only schema resource is a **placeholder** [`graphql/schema.graphqls`](../../shio-app/src/main/resources/graphql/schema.graphqls) (`type Query { _placeholder: String }`) whose own comment admits "the actual schema is built programmatically by `ShGraphQLSchema`", a class that **was never written**. SH4 writes it.
- **The CDA read service is shipped (SH1) and already enforces SH2 scoping + `PUBLISHED`-only.** [ShCdaService](../../shio-app/src/main/java/com/viglet/shio/api/cda/ShCdaService.java) (`getPost`, `getPostByUrl`, `query`) is the single read boundary; it filters by the token's per-site allow-list via [ShApiTokenContext](../../shio-app/src/main/java/com/viglet/shio/spring/security/auth/ShApiTokenContext.java). GraphQL **delegates to it** so it inherits scoping for free, GraphQL is a second *shape* over the same contract, not a second contract (a non-goal in IMPROVEMENTS §0).
- **Post-types model the content shape.** [ShPostType](../../shio-app/src/main/java/com/viglet/shio/persistence/model/post/type/ShPostType.java) → `Set<ShPostTypeAttr>` (each with `name`, `label`, `shWidget`). [ShCdaPost.attrs](../../shio-app/src/main/java/com/viglet/shio/api/cda/dto/ShCdaPost.java) delivers the post body as a parsed JSON map keyed by attr `name`.
- **Token auth is confined to `/api/v2/cda/**` (SH2).** [ShApiTokenFilter](../../shio-app/src/main/java/com/viglet/shio/spring/security/auth/ShApiTokenFilter.java) authenticates the `Key` header, rate-limits (SH3), and sets the context. `/graphql` is currently `permitAll`, i.e. it would bypass SH2 entirely. SH4 closes that.

**What SH4 adds:** a programmatically generated read schema (one GraphQL object type per content post-type, with a field per attribute) served at `/graphql`, plus the security wiring that puts `/graphql` behind the same CDA token + scope model as the REST surface.

## 2. Goal & scope

**In scope**
- Generate a `GraphQLSchema` at startup from the content post-types: a `ShContent` interface (common post fields) + one object type per `system=0` post-type implementing it, each adding a `String` field per `ShPostTypeAttr` (keyed by attr `name`).
- A `Query` root with three entry points, all delegating to `ShCdaService` (so SH2 scoping + `PUBLISHED`-only are inherited): `post(id)`, `postByUrl(siteId, url)`, `posts(siteId, folderId, postType, page, size)`.
- A `TypeResolver` mapping each `ShCdaPost` to its generated type (fallback `ShGenericPost` for system / unmapped types).
- Serve it by replacing the placeholder schema via a `GraphQlSourceBuilderCustomizer.schemaFactory(...)`.
- Put `/graphql` behind `ROLE_CDA`: extend `ShApiTokenFilter` to treat `/graphql` as a CDA delivery surface (auth + rate-limit + context), allowing the GraphQL `POST` as a read (the schema defines **no `Mutation`**, so a write is impossible by construction).

**Out of scope (deferred / noted)**
- Strongly-typed attr scalars / nested Relator object types. Every attr field is `String` for now (the CDA delivers attrs as an untyped JSON map; strong per-widget typing is SH11's post-type-schema work). Multi-value/object attrs serialize as their JSON text.
- Mutations / a write surface (CDA write is SH10).
- Hot schema rebuild on post-type change. The schema is built **once at startup**; creating/editing a post-type needs a restart to appear in the schema. Documented caveat (mirrors SH3's per-instance caveat).
- Preview/draft via GraphQL (`PREVIEW` scope still only reads `PUBLISHED` here, draft exposure is SH13).

## 3. Generated schema shape

```graphql
interface ShContent {
  id: ID!
  title: String
  summary: String
  furl: String
  postType: String
  siteId: String
  folderId: String
  published: Boolean
  date: String            # ISO-8601 instant
  modifiedDate: String
  publicationDate: String
  locale: String                  # SH1129 — the language this post is written in
  availableLocales: [String!]!    # SH1129 — every language its translation group is in
}

# one per content post-type, e.g. "News":
type News implements ShContent {
  # ...all ShContent fields re-declared (graphql-java requires it)...
  headline: String        # one field per ShPostTypeAttr.name
  body: String
}

type ShGenericPost implements ShContent { # fallback for system / unmapped types
  # ...ShContent fields only...
}

type Query {
  post(id: ID!): ShContent
  postByUrl(siteId: String!, url: String!, state: String, locale: String): ShContent
  posts(siteId: String, folderId: String, postType: String, page: Int = 0, size: Int = 50,
        state: String, locale: String): [ShContent!]!
}
```

`locale` (SH1129) is REST's parameter of the same name: `postByUrl` resolves only the translation
in that language, `posts` lists only posts written in it. `availableLocales` on a `posts` page is
read for every row's translation group in **one** query rather than one per row,
`ShCdaAvailableLocalesBenchmarkTest` holds a page of five and a page of twenty-five to the same
statement count. Both names are now reserved: a post-type attribute called `locale` becomes the
field `locale2`, as any attribute colliding with a common field already did.

A client queries exactly the fields a page needs via inline fragments:
```graphql
{ postByUrl(siteId:"s1", url:"/news/hello") { title ... on News { headline body } } }
```

**Name sanitizing.** Post-type and attr names are coerced to the GraphQL name grammar `[_A-Za-z][_0-9A-Za-z]*` (invalid chars → `_`, leading digit → `_`-prefixed); type names get an upper-case initial, query/attr fields a lower-case initial. Collisions (after sanitizing, or an attr colliding with a common field) are de-duplicated / dropped so the schema always builds. An empty content-type set still yields a valid schema (`ShGenericPost` carries the interface).

## 4. Components

| Class | Type | Responsibility |
|---|---|---|
| `api/cda/graphql/ShGraphQLSchema` | `@Component` | `@Transactional(readOnly=true) GraphQLSchema buildSchema()`, reads post-types, builds interface + object types + `Query` + code registry (data fetchers + type resolver). All field fetchers are explicit (no `PropertyDataFetcher` reliance; `Instant`→`String` is done in the fetcher). |
| `api/cda/graphql/ShGraphQLConfig` | `@Configuration` | `GraphQlSourceBuilderCustomizer` bean → `builder.schemaFactory((reg, wiring) -> schema.buildSchema())`. |
| `ShCdaService#queryPosts(...)` | new method | Returns `List<ShCdaPost>` (full posts **with** attrs) for the `posts` query, `query(...)` returns attr-less summaries. Shared branching extracted to a private `queryPage(...)`. |
| `ShApiTokenFilter` | edit | `/graphql` joins `/api/v2/cda/**` as a CDA delivery surface; the `READ`/`PREVIEW` GET-only gate is skipped for `/graphql` (its `POST` is a read). Auth + rate-limit + context unchanged otherwise. |
| `ShSecurityConfig` | edit | `POST /graphql` → `hasRole("CDA")` (both keycloak + basic chains); `/graphql/**` removed from `permitAll` (the GraphiQL shell `/graphiql/**` stays open, it needs a `Key` to actually query). CSRF already ignores `/graphql`. |

## 5. Security model

GraphQL is a delivery surface, so it carries the **same** auth as the REST CDA: a `Key` header → `ROLE_CDA`, per-site allow-list enforced inside `ShCdaService`. Because the schema has **no `Mutation`**, allowing the GraphQL `POST` for read/preview tokens introduces no write path. A session console user (no `ROLE_CDA`) gets `403` on `/graphql`, delivery is token-only, by design.

## 6. Acceptance criteria

1. `POST /graphql` with a valid `Key` and `{ post(id:"<published id>") { title } }` returns the post; an unpublished/unknown id returns `data.post = null`.
2. `postByUrl` resolves a `[...slug]` friendly URL identically to `GET /api/v2/cda/post/by-url`.
3. A `News` post queried via `... on News { headline }` returns the attr value from the post `data`; a missing attr → `null` (never a 500).
4. A per-site token (`siteIds=[s1]`) querying `posts(siteId:"s2")` gets an empty list, same confinement as REST (SH2).
5. `POST /graphql` **without** a `Key` → `401/403` (no longer `permitAll`); `Key` POST is **not** rejected by the SH2 GET-only gate.
6. No SH1/SH2/SH3 behaviour regresses (existing CDA + filter tests stay green).

## 7. Tests

- `ShGraphQLSchemaTest` (unit): build a schema over mock post-types → assert the interface, a per-type object with its attr fields, `Query` fields, and name sanitizing; an empty post-type set still builds (carries `ShGenericPost`).
- `ShApiTokenFilterTest` (extend): a `READ` token on `POST /graphql` authenticates (the GET-only gate does **not** fire); a `Key` on `/graphql` sets `ShApiTokenContext`.
- (Service) `ShCdaService#queryPosts` returns full posts with attrs and honours the per-site allow-list, covered by extending `ShCdaScopeTest` or a focused unit.

## 8. Rollout

1. `ShCdaService#queryPosts` (+ extract `queryPage`).
2. `ShGraphQLSchema` (schema + fetchers + resolver).
3. `ShGraphQLConfig` (customizer).
4. `ShApiTokenFilter` + `ShSecurityConfig` (put `/graphql` behind `ROLE_CDA`).
5. Tests.
6. Document the build-at-startup caveat + the inline-fragment query pattern.

## 9. Estimate

~1 day: schema builder (3h), service method + wiring (1h), security integration (1h), tests (2h), docs (1h).
