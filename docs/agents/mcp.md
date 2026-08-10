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
- **`shio://diagnostics` is the failure report, and MCP is the surface that could not ask (SH596)** —
  `/agent/diagnostics` folds asynchronous and 5xx failures by cause and carries what the failed
  response no longer can: a count, first and last occurrence, a `fix`, and the frames naming the
  throwing method. It was reachable over REST, from the CLI and from the console, and over MCP it was
  reachable *not at all* — eleven tools and four resources, and it was none of them. That is heavier
  than a missing row sounds, because MCP is not one surface among several for a plugin user: **it is
  the only one**, since the Claude Code plugin wires an `.mcp.json` and nothing else. So the reader
  most likely to meet a 500 — an agent driving an instance it did not build — was the one reader
  whose fallback was a server log on somebody else's machine. A **resource**, terse: it is a read
  with no arguments like `shio://context`, and `tools/list` is paid every turn (P3). **And the
  conformance check said it was fine**, which is the half worth remembering: `ShAgentP1ConformanceTest`
  classified the `diagnostics` feature as `shio_changes` on the true observation that incidents ride
  the change feed (SH144) — they do, as rows in a stream, and the fold by cause is a different
  document. The `Reach` vocabulary had no way to say *a resource delivers this and no tool needs to*,
  so the honest answer had nowhere to go and a neighbouring capability was recorded instead. A
  classification can only be as honest as its categories allow, so SH596 added `MCP_RESOURCE`, checked
  against the registered `ShMcpResource` beans rather than believed, with its own vacuity guard — an
  empty registry would pass every row of the new category, which is the same shape as the finding.
- **A conformance driver speaks the transport, not the module (SH561).** `replication.conformance.mjs`
  removed three substitutions — the served source, the running instance, the registry — and kept a
  fourth nobody named: three of its four scenarios `import * as convert from "…/convert.mjs"`, which
  is a function call. Claude Code spawns `shio mcp` and speaks newline-delimited JSON-RPC, and the
  layer an import removes is the whole of it: the declared **input schema**, the **name** an agent
  must get right, the **framing**, the **`tools/list` budget**, and the **shape of a refusal**. A
  tool renamed, a required property added, a response past its budget — all four pass a module run.
  `mcp-transport.conformance.mjs` spawns the bridge with a hand-rolled client (no SDK, for
  `fixture-site.mjs`'s reason: a library that knows the protocol papers over the framing this
  exists to check) and asserts initialize, every tool's name and object schema, the budget, a real
  `tools/call`, a refusal, and an unknown tool name. **stdout only** — the bridge's status line goes
  to stderr because one stray line on stdout desynchronises a client, so the channel separation is
  part of the contract and is asserted.
  Two things it measured on its first run. **`tools/list` has two tokens of headroom**: ~2373 against
  2375, over the wire, which no module call serialises. And the budget must be measured on
  **`result.tools`**, the subject `ShMcpToolBudgetTest` names — counting the whole `result` read 2376
  and reported a one-token regression that was the envelope. Two instruments for one budget have to
  agree on the subject or the second is noise with a stack trace.
  **The replication chain has no MCP tool**, so "the same chain over `tools/call`" is not a scenario
  that exists: `clone → propose → convert` is a CLI path and the eleven tools are the content
  surface. The module driver stays — faster, and it localises a failure — but it is not evidence
  about the wire.
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
`shio-content-model`, `shio-verify`, `shio-replicate`), three commands (`/shio-start`,
`/shio-blueprint`, `/shio-check`) and an `AGENTS.fragment.md` for sessions without the plugin.
It is the **distribution channel for the whole concept**. `claude-plugin/test/plugin.test.mjs`
validates the manifests — a skill whose description does not state its trigger never loads, and
that failure is invisible to every other test in this repository.

**The front door read everything except the capability list (SH595).** `/shio-start` exists to
learn an instance before touching it, and it called `shio_context`, `shio://blueprints` and
`shio_verify` — the model, the packages, the lint state — while never reading `shio://manifest`,
the resource the same server already publishes and the only document carrying what differs
between one instance and the next: 57 `features`, the `limits`, 28 `endpoints` with a worked
example each, and a `startHere` sequence written for exactly this reader. The cost is on record
and is SH587's: a session diagnosed a 500 by grepping the server's log while `diagnostics: true`
sat in that list, and twice proposed building a capability the list already flagged shipped. That
was a *repo-local* skill; a fresh installation reaches the identical dead end by following the
plugin's own front door, which is what makes it the plugin's defect rather than one clone's. So
the command reads the manifest first, reports the `features` that are **false** and the `limits`
— those are what an assumption gets wrong — and then reads `shio_remember`, because whoever
worked on that instance last may have written down what it is for. **The assertion is about
order, because the defect was:** nothing was missing, the resource was published and the command
was free to read it, so a test that only looked for the string would pass on a file that reads
the manifest last — the same dead end reached more slowly. `plugin.test.mjs` compares the
manifest's position against every other call the command makes. The rule underneath is general:
a skill that *lists* capabilities goes stale and lies, while a skill that says **ask the
instance** cannot, which is also why the replication procedure moved out of this repo's
`.claude/skills/` — 223 lines that existed in one clone — into `shio-replicate`, keeping only
what is true of any instance.


## The fourth registry is the published one (SH604)

SH599 counted three registries of the tool surface and kept them in step. There were four, and the
fourth is `claude-plugin/README.md` — **the only one whose reader cannot check it against the
source.** It listed **ten tools and four resources** against a server carrying **eleven and seven**,
so `shio_remember` and `shio://diagnostics` had both shipped and the plugin had never learned
either.

Those two cost the most to miss: `remember` is where a convention survives a session, and
`diagnostics` is what makes a 5xx readable to somebody whose only surface is MCP. Both landed within
three commits of the README, and the README is what a new installation reads first.

### The rule is not "regenerate the list"

**Naming a tool to teach a rule is teaching; enumerating tools is a copy.** The AGENTS fragment
naming `shio_apply` while stating what apply guarantees should stay. An inventory of a surface the
reader can ask the instance for should not exist at all.

So the guard refuses **three or more distinct tool or resource names on one line** — an inventory by
count, whatever it calls itself — and it walks **every published file**, not the three the id-leak
guard happened to name. The skills and commands pass unchanged, because each names one tool while
stating a rule. That is what makes the distinction a property of the plugin rather than of the file
somebody remembered to check.

The README now routes to `shio://manifest`: the instance describing itself, which is right by
construction where a list in a README is right until the next release.

**Landed in the order the task set**: the assertion first, red on line 28 naming the four tools it
found there, then the deletion. A guard written after the fix would have been a guard nobody had
seen fail.
