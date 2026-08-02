# MCP and the Claude Code plugin

> Split out of the repo-root `agents.md` (see its routing table for the index).
> Read this before touching the area it covers. Detail here is authoritative.

### MCP — `POST /mcp` (SH75/SH83)
Same capabilities over the Model Context Protocol (JSON-RPC 2.0, revision `2025-06-18`),
hand-rolled in `com.viglet.shio.api.agent.mcp` — **not** the MCP Java SDK, which *was*
Jackson-2/Boot-3 while this app is Jackson-3/Boot-4. `GET /mcp` is a deliberate `405`
(this server sends nothing unprompted). `shio mcp` in the CLI is a stdio bridge:
`claude mcp add shio -- shio mcp`.
- **That premise is dated now, and looking once found it had moved (SH276).** It is the
  repository's largest inherited claim by code defended (~1500 lines) and the one kind SH274's
  tripwires cannot reach — the artefact is not on the classpath, and adding it to check is the
  change being evaluated. So the instrument is a **date**: `ShMcpServer` carries
  `SDK-PREMISE-CHECKED` / `SDK-PREMISE-VERSION` and **`ShMcpSdkPremiseTest`** fails the build
  once the record passes an 18-month shelf life. Read on 2026-08-02 against
  `io.modelcontextprotocol.sdk:mcp:2.0.0`: the SDK now publishes `mcp-json-jackson3`, which
  depends on `tools.jackson.core:jackson-databind:3.0.3` — *this app's own Jackson 3* — and
  `mcp-core` depends on no Spring artefact at all, so the Boot-3 half is gone. What survives is
  `jackson-annotations:2.20` transitively: a Jackson **2 annotations** jar, metadata rather than
  a second parser. **Not an argument to adopt the SDK** — the standing argument is now live
  evidence rather than a claim about the alternative: `ShMcpToolBudgetTest` caps what
  `tools/list` costs every turn and `ShMcpWireConformanceTest` pins the wire against a golden.
  When either starts costing more than the SDK would, that is the signal to revisit.
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
- **`shio://ops` is the write vocabulary (SH332)** — every op `/agent/batch` accepts and the keys each
  takes, owned by `ShAgentOps`. It exists because the op set was prose inside one tool's description,
  and two of SH331's four wrong calls were an agent inferring a key nothing stated; a **resource**
  rather than more description, since `tools/list` is paid every turn (P3). The op *names* come from
  `ShAgentBatchService.OPS`, and the per-op keys are asserted against the planner's own refusals —
  **a source scan is not faithful here**, because a handler reads `op.address()` through a helper
  (measured: the scan finds nothing on five of the nine). An op added to `OPS` with no spec **fails
  the build**. Three requirement kinds, because two are conditional (SH175): `type`/`folder` on
  `post.upsert` only when creating, `to`|`folder` on `post.move`. **It is also
  `GET /agent/context?include=ops` (SH346)** — same bytes, so an HTTP-only agent reads the same
  document; see agent-surface.md for why that section and not a manifest one.
- **Every resource declares its HTTP twin, and a build guard checks the route exists (SH346)** —
  `ShMcpResource.httpTwin()`, asserted against the live handler mappings by
  `ShMcpResourceHttpTwinLintTest`. Publishing a document over MCP alone is a P1 break;
  `shio://ops` was one for a block, and the instrument is what answers the question for the other
  five resources instead of one fix answering it for one.
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
  swallows. Getting this backwards throws away the teaching error. The generalisation,
  for the next protocol surface: **ask who has to change, and send the error where that
  party will see it.**
- **Four rules the tool surface was designed against (SH83, moved here by SH356).** **A tool that
  cannot be a shape over REST is a design smell** — `find`/`read` had none, and the tempting move was
  to put the query logic in the tool; adding `GET /agent/find` and `/read` first cost an hour, kept
  P10 true, and handed the same capability to the CLI and to any plain HTTP caller for free.
  **A partly-available capability ships as the part that works plus a manifest flag** — never a
  plausible-looking shim: `shio_verify` waited for SH99 because a verify that only pretends is
  *worse than absent*, since the agent stops looking, and the same reasoning made `shio_changes` an
  explicitly-labelled window rather than a fake cursor. **Prompts are procedures, never content** —
  they encode the call order that avoids mistakes (plan before write, publish as its own step) and
  say nothing about tone or copy; there is no inference in Shio, and an MCP prompt is a protocol
  object rather than a system prompt. **Read the drafts** — the CDA only sees `PUBLISHED`, and an
  agent that cannot read back its own unpublished work is not composing, it is blind, so these reads
  go to the unified model, DRAFT-preferred.
- **Attribution cannot be retrofitted (SH75, moved here from IMPROVEMENTS by SH356).** The
  session id is minted by the transport on the first `initialize`, because rows written
  before it existed simply do not have one — there is no backfill for a fact nobody
  recorded. It rides in the audit **actor string** (`alice (agent:7f3c…)`) rather than in a
  column of its own: SH106 owns the credential model and should migrate the schema once,
  and the human stays visible in every row because an agent acts *as* somebody.
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

