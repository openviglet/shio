# MCP and the Claude Code plugin

> Split out of the repo-root `agents.md` (see its routing table for the index).
> Read this before touching the area it covers. Detail here is authoritative.

### MCP — `POST /mcp` (SH75/SH83)
Same capabilities over the Model Context Protocol (JSON-RPC 2.0, revision `2025-06-18`),
hand-rolled in `com.viglet.shio.api.agent.mcp` — **not** the MCP Java SDK, which is
Jackson-2/Boot-3 while this app is Jackson-3/Boot-4. `GET /mcp` is a deliberate `405`
(this server sends nothing unprompted). `shio mcp` in the CLI is a stdio bridge:
`claude mcp add shio -- shio mcp`.
- **Tools** (`ShMcpTool` beans, auto-registered): `shio_context` · `shio_find` ·
  `shio_read` · `shio_write` · `shio_apply` · `shio_publish` · `shio_assets` ·
  `shio_changes` · `shio_verify` · `shio_digest` · `shio_remember`. Every tool is a *shape*
  over a REST surface — if a tool needs new logic, the logic goes on the REST side first.
- **Resources**: `shio://manifest` · `shio://blueprints` · `shio://context` ·
  `shio://schema/{postType}` · `shio://blueprint/{name}` (+ the `/files` suffix, SH166).
  The last one is the only route an MCP-only caller has to a package's **carried files** —
  they travel on `GET /agent/blueprints/{name}?include=files` otherwise, which a client with
  no HTTP of its own cannot call. Served **verbatim, placeholders standing**, because SH119
  leaves carried-file substitution to the client (a project's npm name is local identity the
  server has no business being told); each body is preceded by the line naming the
  `{{placeholder}}`s in it, and an apply's `file <path> not-written placeholders=…` rows say
  the same thing for the caller that never fetches them. A **resource** rather than an
  argument on `shio_apply` because that tool is at 457/460 and `tools/list` is paid every
  turn. One bean serves both URIs: `ShMcpResource#matches` keys on the literal prefix before
  the first brace, so two beans would both claim `shio://blueprint/…`.
- `shio_apply` covers **two** verbs on purpose: a desired-state document, or a blueprint
  (`blueprint` + `params`). "Converge to this document" and "converge to this package" are
  one decision with two inputs, and a separate tool would cost more schema per turn than
  the two arguments do — see the budget note below.
- `shio_remember` (SH160) is the **eleventh** tool, and the one place P1 was allowed to
  outrank P3. SH114 argued against it correctly for Claude Code — a schema is paid every
  turn and a coding agent has a shell — and wrongly for a pure MCP client, which could read
  the memory and never write one, so `instanceMemory: true` was a half-truth for exactly the
  clients the manifest informs. It wins because the memory is the only mechanism that makes
  session N+1 cheaper than N and is write-once-read-many: a store nobody can write to stays
  empty. **Forgetting is an empty note, not a twelfth tool.** A `remember` property on
  `shio_write` was rejected as a cheaper shape that does not deliver the capability — most
  conclusions worth keeping are reached while *reading*, with no write to hang them on.
- **Prompts**: `new-site` · `add-section` · `translate-site` · `audit-content`.
- **One name per concept, and an unrecognised argument refused once (SH331)** —
  `ShMcpVocabulary`, called from `ShMcpServer.toolsCall`, so there is a single place where arguments
  are read. Filed as four renames; **SH340's measurement changed it into one guard**, because four of
  six read tools were accepting an unknown key and answering 200 — the default behaviour of the
  surface, not one tool's slip. A known **synonym** is rewritten and **reported** in a `note:` line
  (a silent rewrite leaves the caller sending the alias forever); anything else is a teaching 400
  carrying the declared set and a `didYouMean`. Four rules: the alias table holds only names *this
  product itself uses* elsewhere (`postType`/`postTypeId`/`shPostType` for `type`, `scope` for
  `site`, `cursor` for `since`, `check` for `checks`, `addresses` for `address`), never a guess at
  what a model might type; an alias **never displaces** a canonical value the caller also sent (two
  spellings present is two values, so it is refused rather than resolved by guessing); the recursion
  reaches **an op's arguments**, because `shio_write` declares only `ops` and that is where the real
  mistake was; and it **must never touch a `data` key** — the guard is structural, since `data` is
  declared with no `properties` and therefore has nothing to validate. That last one is SH168's line
  and the first thing a "simplification" would break: an upsert declares whole state, so a near-miss
  on a field name silently replaces the field it meant. Closed set, server-owned, unambiguous — all
  three, or refuse. Measured by `ShAgentNaiveClientBenchmarkTest`: **0 silent drops, 6/6 naive calls
  landing on the first attempt.**
- **Error routing matters**: a fixable failure (bad address, unknown post-type) is a
  tool *result* with `isError:true` so the model reads the SH80 `fix`; a JSON-RPC
  `error` is only for an unusable message (unknown method/tool), which the client
  swallows. Getting this backwards throws away the teaching error.
- A tool-count test fails the build past 12 — tool schemas are context paid every turn.
  The real ceilings are `mcp.tools.list.total` (**2360**, raised by SH160 for the eleventh
  tool, measured 2327) and `mcp.tools.list.max-per-tool` (460) in
  `src/test/resources/token-budgets.properties`, both measured; raising one is allowed and
  must be justified in the commit — and the description is trimmed *first*, which is what
  took `shio_remember` from 167 to 128 before the ceiling moved. **`shio_apply` is the tool
  to watch and it is now the constraint**: at **457** against 460 it is the largest, because
  it carries the blueprint verb as well as the document one, and it is why SH157's `verify`
  argument could go on `shio_write` and not on it (SH227). The answer there is a trim, not a
  raise.
  `ShMcpWireConformanceTest` pins `tools/list` against a golden — regenerate with
  `-Dshio.mcp.golden.update=true` and review the diff when the surface really changes.

Addresses (SH79): `post:<site>/<friendly-url>` · `folder:<site>/<name-chain>` ·
`site:<name>` · `id:<uuid>`. **Writes take only `post:`/`folder:`** — a folder path and
a friendly URL are separate namespaces, and a write that guessed wrong would create
content in the wrong place silently. CSRF-exempt **only for callers holding no session
cookie** (SH167 — see below), authenticated like every other API; the SH54 ACL and SH22
quota apply on the agent path identically.

### The Claude Code plugin (SH97)

`claude-plugin/` (published via the repo-root `.claude-plugin/marketplace.json`):
`claude plugin marketplace add openviglet/shio && claude plugin install shio`. An `.mcp.json`
entry running the CLI's stdio bridge through `npx`, three skills (`shio-site`,
`shio-content-model`, `shio-verify`), three commands (`/shio-start`, `/shio-blueprint`,
`/shio-check`) and an `AGENTS.fragment.md` for sessions without the plugin. It is the
**distribution channel for the whole concept**. `claude-plugin/test/plugin.test.mjs` validates
the manifests — a skill whose description does not state its trigger never loads, and that
failure is invisible to every other test in this repository.

