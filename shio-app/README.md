# `shio-app` — the Viglet Shio backend (Java 21 + Spring Boot 4)

The Java content engine behind **[Viglet Shio](https://openviglet.github.io/shio/)**, the
**open source, agent-ready CMS**. This
module is the whole server: the content model, the **Content Delivery API**, the
**agent gateway**, the **MCP (Model Context Protocol) server**, the page
renderer, and the packaged React administration console.

| | |
|---|---|
| **Maven coordinates** | `com.viglet.shio:shio-app` |
| **Packaging** | executable Spring Boot `jar` |
| **Java** | 21 |
| **Spring Boot** | 4.1 |
| **Docker image** | [`ghcr.io/openviglet/shio`](https://github.com/openviglet/shio/pkgs/container/shio) |
| **License** | [Apache-2.0](../LICENSE) |
| **Website** | [openviglet.github.io/shio](https://openviglet.github.io/shio/) |
| **Docs** | [openviglet.github.io/shio/docs](https://openviglet.github.io/shio/docs/) |

> **Distribution.** `shio-app` is a **runnable application**, not a library, and
> it is **not published to Maven Central**. Consume it as the Docker image above
> or as the executable jar you build below. Its own dependencies —
> `com.viglet.core:*` ([viglet-core](https://github.com/openviglet/viglet-core))
> and `com.viglet.turing:turing-java-sdk`
> ([Viglet Turing](https://turing.viglet.org)) — come from GitHub Packages.

## Run

```bash
# from the repository root
./mvnw spring-boot:run -pl shio-app
```

- **Console** — <http://localhost:2710> (`admin` / `admin`)
- **Sample site** — <http://localhost:2710/sites/viglet/default/en-us>
- **API prefix** — `/api/v2/`

Or as a jar:

```bash
./mvnw -pl shio-app -am clean package -DskipTests
java -jar shio-app/target/shio-app-*.jar
```

Or with Docker:

```bash
docker run -p 2710:2710 ghcr.io/openviglet/shio:latest
```

## What this module exposes

| Surface | Path | For |
|---|---|---|
| **MCP server** | `POST /mcp` | 13 tools — `shio_context`, `shio_find`, `shio_read`, `shio_write`, `shio_apply`, `shio_publish`, `shio_assets`, `shio_blueprint`, `shio_changes`, `shio_verify`, `shio_digest`, `shio_remember`, `shio_forget` — plus `shio://manifest`, `shio://blueprints`, `shio://context` and `shio://schema/{postType}` resources |
| **Agent gateway** | `/api/v2/agent/**` | Manifest, context pack, planned batches, teaching errors, review queue, diagnostics, render digests |
| **Content Delivery API** | `/api/v2/cda/**` | Stable read-only REST + GraphQL, token-scoped — what the TypeScript SDKs talk to |
| **Console API** | `/api/v2/**` | Sites, folders, posts, post-types, static files, import/export |
| **Renderer** | `/sites/**`, `/preview/**` | Page → layout → region rendering, Handlebars templates, themes, the Universal Editor bridge |

Addressing (used everywhere): `post:<site>/<friendly-url>` ·
`folder:<site>/<name-chain>` · `site:<name>` · `id:<uuid>`. A site's home page is
`post:<site>/`.

## Build and test

```bash
# backend only — always `clean`, always `-Dskip.npm=true`
./mvnw -pl shio-app -am clean test -Dskip.npm=true

# with the cross-database integration tests (needs a Docker daemon)
./mvnw -pl shio-app -am clean verify -DskipITs=false
```

`-Dskip.npm=true` skips the `frontend-maven-plugin` build of the React console
and runs against a previously built `src/main/resources/public`. The integration
tests are opt-in; `ShPostgresSchemaIT` and `ShMariaDbSchemaIT` **skip silently
without Docker**.

## Databases

**H2** out of the box (`store/`), with **PostgreSQL** and **MariaDB** supported.
The schema is owned by **Liquibase** — `spring.jpa.hibernate.ddl-auto=none` is
deliberate and must stay that way. Multi-tenancy is a `tenant_id` discriminator
applied through `@TenantId`.

## Layout

```
src/main/java/com/viglet/shio/   Java sources — API, services, persistence, renderer, MCP
src/main/resources/
  public/                        Built React console (from ../shio-react)
  ui/                            Legacy AngularJS console
  import/                        Default data — post-types.json, sample sites
  blueprints/                    Appliable starting points (landing, marketing-site, nextjs-starter)
  db/                            Liquibase changelogs
store/                           Runtime data — file source, logs, H2 database
```

## More

- Per-area guides: [`docs/agents/`](../docs/agents/) —
  [renderer](../docs/agents/renderer.md),
  [agent surface](../docs/agents/agent-surface.md),
  [MCP](../docs/agents/mcp.md),
  [content API](../docs/agents/content-api.md),
  [database](../docs/agents/database.md),
  [security](../docs/agents/security.md),
  [build](../docs/agents/build.md)
- The ten design laws: [`agents.md`](../agents.md)
- The concept: [`docs/specs/SH74-agent-native-cms.md`](../docs/specs/SH74-agent-native-cms.md)
- Everything else: the [repository README](../README.md)

---

Part of the [Viglet](https://www.viglet.org) open source family, alongside
[Viglet Turing](https://turing.viglet.org). Apache-2.0.
