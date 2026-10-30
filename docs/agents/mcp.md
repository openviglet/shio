# MCP and the Claude Code plugin

> Split out of the repo-root `agents.md` (see its routing table for the index).
> Read this before touching the area it covers. Detail here is authoritative.

### MCP: `POST /mcp` (SH75/SH83)
Same capabilities over the Model Context Protocol (JSON-RPC 2.0, revision `2025-06-18`),
hand-rolled in `com.viglet.shio.api.agent.mcp`, **not** the MCP Java SDK, which *was*
Jackson-2/Boot-3 while this app is Jackson-3/Boot-4. `GET /mcp` is a deliberate `405`
(this server sends nothing unprompted). `shio mcp` in the CLI is a stdio bridge:
`claude mcp add shio -- shio mcp`.
- **That premise is dated now, and looking once found it had moved (SH276).** It is the
  repository's largest inherited claim by code defended (~1500 lines) and the one kind SH274's
  tripwires cannot reach, the artefact is not on the classpath, and adding it to check is the
  change being evaluated. So the instrument is a **date**: `ShMcpServer` carries
  `SDK-PREMISE-CHECKED` / `SDK-PREMISE-VERSION` and **`ShMcpSdkPremiseTest`** fails the build
  once the record passes an 18-month shelf life. Read on 2026-08-02 against
  `io.modelcontextprotocol.sdk:mcp:2.0.0`: the SDK now publishes `mcp-json-jackson3`, which
  depends on `tools.jackson.core:jackson-databind:3.0.3` (*this app's own Jackson 3*) and
  `mcp-core` depends on no Spring artefact at all, so the Boot-3 half is gone. What survives is
  `jackson-annotations:2.20` transitively: a Jackson **2 annotations** jar, metadata rather than
  a second parser. **Not an argument to adopt the SDK**, the standing argument is now live
  evidence rather than a claim about the alternative: `ShMcpToolBudgetTest` caps what
  `tools/list` costs every turn and `ShMcpWireConformanceTest` pins the wire against a golden.
  When either starts costing more than the SDK would, that is the signal to revisit.
- **Tools** (`ShMcpTool` beans, auto-registered): `shio_context` · `shio_find` ·
  `shio_read` · `shio_write` · `shio_apply` · `shio_publish` · `shio_assets` ·
  `shio_changes` · `shio_verify` · `shio_digest` · `shio_remember`. Every tool is a *shape*
  over a REST surface, if a tool needs new logic, the logic goes on the REST side first.
- **Resources**: `shio://manifest` · `shio://blueprints` · `shio://context` ·
  `shio://schema/{postType}` · `shio://blueprint/{name}` (+ the `/files` suffix, SH166).
  The last one is the only route an MCP-only caller has to a package's **carried files**,
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
  the two arguments do, see the budget note below.
- `shio_remember` (SH160) is the **eleventh** tool, and the one place P1 was allowed to
  outrank P3. SH114 argued against it correctly for Claude Code, a schema is paid every
  turn and a coding agent has a shell, and wrongly for a pure MCP client, which could read
  the memory and never write one, so `instanceMemory: true` was a half-truth for exactly the
  clients the manifest informs. It wins because the memory is the only mechanism that makes
  session N+1 cheaper than N and is write-once-read-many: a store nobody can write to stays
  empty. **Forgetting is an empty note, not a twelfth tool.** A `remember` property on
  `shio_write` was rejected as a cheaper shape that does not deliver the capability, most
  conclusions worth keeping are reached while *reading*, with no write to hang them on.
- **`shio://ops` is the write vocabulary (SH332)**: every op `/agent/batch` accepts and the keys each
  takes, owned by `ShAgentOps`. It exists because the op set was prose inside one tool's description,
  and two of SH331's four wrong calls were an agent inferring a key nothing stated; a **resource**
  rather than more description, since `tools/list` is paid every turn (P3). The op *names* come from
  `ShAgentBatchService.OPS`, and the per-op keys are asserted against the planner's own refusals,
  **a source scan is not faithful here**, because a handler reads `op.address()` through a helper
  (measured: the scan finds nothing on five of the nine). An op added to `OPS` with no spec **fails
  the build**. Three requirement kinds, because two are conditional (SH175): `type`/`folder` on
  `post.upsert` only when creating, `to`|`folder` on `post.move`. **It is also
  `GET /agent/context?include=ops` (SH346)**, same bytes, so an HTTP-only agent reads the same
  document; see agent-surface.md for why that section and not a manifest one.
- **`shio://diagnostics` is the failure report, and MCP is the surface that could not ask (SH596)**,
  `/agent/diagnostics` folds asynchronous and 5xx failures by cause and carries what the failed
  response no longer can: a count, first and last occurrence, a `fix`, and the frames naming the
  throwing method. It was reachable over REST, from the CLI and from the console, and over MCP it was
  reachable *not at all*, eleven tools and four resources, and it was none of them. That is heavier
  than a missing row sounds, because MCP is not one surface among several for a plugin user: **it is
  the only one**, since the Claude Code plugin wires an `.mcp.json` and nothing else. So the reader
  most likely to meet a 500 (an agent driving an instance it did not build) was the one reader
  whose fallback was a server log on somebody else's machine. A **resource**, terse: it is a read
  with no arguments like `shio://context`, and `tools/list` is paid every turn (P3). **And the
  conformance check said it was fine**, which is the half worth remembering: `ShAgentP1ConformanceTest`
  classified the `diagnostics` feature as `shio_changes` on the true observation that incidents ride
  the change feed (SH144), they do, as rows in a stream, and the fold by cause is a different
  document. The `Reach` vocabulary had no way to say *a resource delivers this and no tool needs to*,
  so the honest answer had nowhere to go and a neighbouring capability was recorded instead. A
  classification can only be as honest as its categories allow, so SH596 added `MCP_RESOURCE`, checked
  against the registered `ShMcpResource` beans rather than believed, with its own vacuity guard, an
  empty registry would pass every row of the new category, which is the same shape as the finding.
- **A conformance driver speaks the transport, not the module (SH561).** `replication.conformance.mjs`
  removed three substitutions (the served source, the running instance, the registry) and kept a
  fourth nobody named: three of its four scenarios `import * as convert from "…/convert.mjs"`, which
  is a function call. Claude Code spawns `shio mcp` and speaks newline-delimited JSON-RPC, and the
  layer an import removes is the whole of it: the declared **input schema**, the **name** an agent
  must get right, the **framing**, the **`tools/list` budget**, and the **shape of a refusal**. A
  tool renamed, a required property added, a response past its budget, all four pass a module run.
  `mcp-transport.conformance.mjs` spawns the bridge with a hand-rolled client (no SDK, for
  `fixture-site.mjs`'s reason: a library that knows the protocol papers over the framing this
  exists to check) and asserts initialize, every tool's name and object schema, the budget, a real
  `tools/call`, a refusal, and an unknown tool name. **stdout only**, the bridge's status line goes
  to stderr because one stray line on stdout desynchronises a client, so the channel separation is
  part of the contract and is asserted.
  Two things it measured on its first run. **`tools/list` had two tokens of headroom**: ~2373
  against 2375, over the wire, which no module call serialises. And the budget must be measured on
  **`result.tools`**, the subject `ShMcpToolBudgetTest` names, counting the whole `result` read 2376
  and reported a one-token regression that was the envelope. Two instruments for one budget have to
  agree on the subject or the second is noise with a stack trace.
  **They also have to agree on the number, and for three raises they did not (SH673).** The suite
  states the budget as a constant rather than reading it, which is right, it runs against an
  instance it did not build, but "a constant that disagrees with the file is a visible edit" only
  holds if something looks. Nothing did: 2375 was correct at SH342 and stayed while SH619, SH222 and
  SH227 raised the properties file to 2400, until a payload at 2397 sat between them and the gate
  went red over a cost the project had decided to pay. `cli/test/budget-restatement.test.mjs` now
  holds every restated budget against its key in `pnpm test`, both sides are files, so no instance
  is involved, and a second restatement is a row in its table rather than another test.
  **The replication chain has no MCP tool**, so "the same chain over `tools/call`" is not a scenario
  that exists: `clone → propose → convert` is a CLI path and the MCP tools are the content
  surface. The module driver stays (faster, and it localises a failure) but it is not evidence
  about the wire.
- **`shio://usage` is what the caller has spent, and the meter had counted it without telling it
  (SH754)**, SH45 published the API-call meter at `GET /api/v2/tenant/usage` and through the
  `ShBillingExporter` SPI: a curator's route and a billing route. The meter's own surface keys name the
  gap (`agent` is one of the three it counts) so the caller generating the largest share of the
  number was the one that could not read it, and an agent told to work inside a budget with no way
  to query its spend has been given advice rather than a bound. A **resource** on SH596's
  judgement: a read with no arguments is state to look at, not an act to perform, and `tools/list`
  is paid every turn. The twin is `GET /agent/context?include=usage` rather than a new route,
  following `shio://ops`, a new capability does not get its own endpoint by default. Both call
  `ShAgentUsageService`, so identical bytes is a property of the construction rather than of
  review. **Written *and* pending are both stated**: the meter flushes on an interval, so a
  document reporting only durable counts answers zero for the first minute of every window, and an
  agent that reads zero right after doing work stops believing the number. The section is opt-in
  and, unlike `manifest` and `ops`, for a second reason of its own, it changes on *every* call,
  so letting it into the digest would make SH116's conditional read never match. Manifest flag:
  `usageMeter`, classified `MCP_RESOURCE` in `ShAgentP1ConformanceTest`.
- **Every resource declares its HTTP twin, and a build guard checks the route exists (SH346)**,
  `ShMcpResource.httpTwin()`, asserted against the live handler mappings by
  `ShMcpResourceHttpTwinLintTest`. Publishing a document over MCP alone is a P1 break;
  `shio://ops` was one for a block, and the instrument is what answers the question for the other
  five resources instead of one fix answering it for one.
- **Prompts**: `new-site` · `add-section` · `translate-site` · `audit-content`.
- **One name per concept, and an unrecognised argument refused once (SH331)**,
  `ShMcpVocabulary`, called from `ShMcpServer.toolsCall`, so there is a single place where arguments
  are read. Filed as four renames; **SH340's measurement changed it into one guard**, because four of
  six read tools were accepting an unknown key and answering 200, the default behaviour of the
  surface, not one tool's slip. A known **synonym** is rewritten and **reported** in a `note:` line
  (a silent rewrite leaves the caller sending the alias forever); anything else is a teaching 400
  carrying the declared set and a `didYouMean`. Four rules: the alias table holds only names *this
  product itself uses* elsewhere (`postType`/`postTypeId`/`shPostType` for `type`, `scope` for
  `site`, `cursor` for `since`, `check` for `checks`, `addresses` for `address`), never a guess at
  what a model might type; an alias **never displaces** a canonical value the caller also sent (two
  spellings present is two values, so it is refused rather than resolved by guessing); the recursion
  reaches **an op's arguments**, because `shio_write` declares only `ops` and that is where the real
  mistake was; and it **must never touch a `data` key**, the guard is structural, since `data` is
  declared with no `properties` and therefore has nothing to validate. That last one is SH168's line
  and the first thing a "simplification" would break: an upsert declares whole state, so a near-miss
  on a field name silently replaces the field it meant. Closed set, server-owned, unambiguous, all
  three, or refuse. Measured by `ShAgentNaiveClientBenchmarkTest`: **0 silent drops, 6/6 naive calls
  landing on the first attempt.**
- **Error routing matters**: a fixable failure (bad address, unknown post-type) is a
  tool *result* with `isError:true` so the model reads the SH80 `fix`; a JSON-RPC
  `error` is only for an unusable message (unknown method/tool), which the client
  swallows. Getting this backwards throws away the teaching error. The generalisation,
  for the next protocol surface: **ask who has to change, and send the error where that
  party will see it.**
- **Four rules the tool surface was designed against (SH83, moved here by SH356).** **A tool that
  cannot be a shape over REST is a design smell**, `find`/`read` had none, and the tempting move was
  to put the query logic in the tool; adding `GET /agent/find` and `/read` first cost an hour, kept
  P10 true, and handed the same capability to the CLI and to any plain HTTP caller for free.
  **A partly-available capability ships as the part that works plus a manifest flag**: never a
  plausible-looking shim: `shio_verify` waited for SH99 because a verify that only pretends is
  *worse than absent*, since the agent stops looking, and the same reasoning made `shio_changes` an
  explicitly-labelled window rather than a fake cursor. **Prompts are procedures, never content**,
  they encode the call order that avoids mistakes (plan before write, publish as its own step) and
  say nothing about tone or copy; there is no inference in Shio, and an MCP prompt is a protocol
  object rather than a system prompt. **Read the drafts**, the CDA only sees `PUBLISHED`, and an
  agent that cannot read back its own unpublished work is not composing, it is blind, so these reads
  go to the unified model, DRAFT-preferred.
- **Attribution cannot be retrofitted (SH75, moved here from IMPROVEMENTS by SH356).** The
  session id is minted by the transport on the first `initialize`, because rows written
  before it existed simply do not have one, there is no backfill for a fact nobody
  recorded. It rides in the audit **actor string** (`alice (agent:7f3c…)`) rather than in a
  column of its own: SH106 owns the credential model and should migrate the schema once,
  and the person stays visible in every row because an agent acts *as* somebody.
- A tool-count test fails the build past 12, tool schemas are context paid every turn.
  The real ceilings are `mcp.tools.list.total` (**2400** after SH227, measured 2397) and
  `mcp.tools.list.max-per-tool` (460) in
  `src/test/resources/token-budgets.properties`, both measured; raising one is allowed and
  must be justified in the commit, and the description is trimmed *first*, which is what
  took `shio_remember` from 167 to 128 before the ceiling moved. **`shio_apply` is the tool
  to watch and it is now the constraint**: at **457** against 460 it is the largest, because
  it carries the blueprint verb as well as the document one, and it is why SH157's `verify`
  argument could go on `shio_write` and not on it (SH227). The answer there is a trim, not a
  raise.
  `ShMcpWireConformanceTest` pins `tools/list` against a golden, regenerate with
  `-Dshio.mcp.golden.update=true` and review the diff when the surface really changes.

Addresses (SH79): `post:<site>/<friendly-url>` · `folder:<site>/<name-chain>` ·
`site:<name>` · `id:<uuid>`. **Writes take only `post:`/`folder:`**, a folder path and
a friendly URL are separate namespaces, and a write that guessed wrong would create
content in the wrong place silently. CSRF-exempt **only for callers holding no session
cookie** (SH167, see below), authenticated like every other API; the SH54 ACL and SH22
quota apply on the agent path identically.

### The Claude Code plugin (SH97)

`claude-plugin/` (published via the repo-root `.claude-plugin/marketplace.json`):
`claude plugin marketplace add openviglet/shio && claude plugin install shio`. An `.mcp.json`
entry running the CLI's stdio bridge through `npx`, three skills (`shio-site`,
`shio-content-model`, `shio-verify`, `shio-replicate`), three commands (`/shio-start`,
`/shio-blueprint`, `/shio-check`) and an `AGENTS.fragment.md` for sessions without the plugin.
It is the **distribution channel for the whole concept**. `claude-plugin/test/plugin.test.mjs`
validates the manifests, a skill whose description does not state its trigger never loads, and
that failure is invisible to every other test in this repository.

**The front door read everything except the capability list (SH595).** `/shio-start` exists to
learn an instance before touching it, and it called `shio_context`, `shio://blueprints` and
`shio_verify`: the model, the packages, the lint state, while never reading `shio://manifest`,
the resource the same server already publishes and the only document carrying what differs
between one instance and the next: 57 `features`, the `limits`, 28 `endpoints` with a worked
example each, and a `startHere` sequence written for exactly this reader. The cost is on record
and is SH587's: a session diagnosed a 500 by grepping the server's log while `diagnostics: true`
sat in that list, and twice proposed building a capability the list already flagged shipped. That
was a *repo-local* skill; a fresh installation reaches the identical dead end by following the
plugin's own front door, which is what makes it the plugin's defect rather than one clone's. So
the command reads the manifest first, reports the `features` that are **false** and the `limits`,
those are what an assumption gets wrong, and then reads `shio_remember`, because whoever
worked on that instance last may have written down what it is for. **The assertion is about
order, because the defect was:** nothing was missing, the resource was published and the command
was free to read it, so a test that only looked for the string would pass on a file that reads
the manifest last, the same dead end reached more slowly. `plugin.test.mjs` compares the
manifest's position against every other call the command makes. The rule underneath is general:
a skill that *lists* capabilities goes stale and lies, while a skill that says **ask the
instance** cannot, which is also why the replication procedure moved out of this repo's
`.claude/skills/`: 223 lines that existed in one clone, into `shio-replicate`, keeping only
what is true of any instance.

### The MCP Registry entry (SH1245)

`cli/server.json` is the official MCP Registry's description of the stdio bridge: npm package
`@viglet/shio`, argument `mcp`, and the three variables `resolveAdmin` reads. The registry
checks ownership by matching its `name` against `mcpName` in `cli/package.json`, and installs
exactly the version it names, so the release workflow stamps `server.json` together with the
workspace version and commits it. `cli/test/mcp-registry.test.mjs` reads every fact back from
the package. Publishing the entry (`mcp-publisher publish`) is the owner's step, under the
`io.github.openviglet` namespace.


## The fourth registry is the published one (SH604)

SH599 counted three registries of the tool surface and kept them in step. There were four, and the
fourth is `claude-plugin/README.md`, **the only one whose reader cannot check it against the
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

So the guard refuses **three or more distinct tool or resource names on one line**, an inventory by
count, whatever it calls itself, and it walks **every published file**, not the three the id-leak
guard happened to name. The skills and commands pass unchanged, because each names one tool while
stating a rule. That is what makes the distinction a property of the plugin rather than of the file
somebody remembered to check.

The README now routes to `shio://manifest`: the instance describing itself, which is right by
construction where a list in a README is right until the next release.

**Landed in the order the task set**: the assertion first, red on line 28 naming the four tools it
found there, then the deletion. A guard written after the fix would have been a guard nobody had
seen fail.

## `shio_apply` proves its own writes (SH227)

`shio_apply` takes `verify: true` and returns the lint of what the apply touched, in the
same encoding `GET /agent/verify` uses, the argument SH157 gave `shio_write` and could
not afford here.

**The gap was on the wrong tool.** A desired-state apply writes a whole section in one
call, so it is the write *most* likely to want a lint of what it just did; the batch that
got the argument is the finer-grained one. The blueprint verb on this same tool has always
verified unconditionally (`ShBlueprintApplied.Verified`), which left the **document**
branch as the only unproved write on the MCP surface.

**Where the budget actually was.** The task argued from `shio_apply` at 457 against a 460
per-tool ceiling. That figure was one revision stale, SH342 had already trimmed the tool
to 435, so the per-tool ceiling was never the binding constraint. `mcp.tools.list.total`
was: at 2395 with 2390 of tools in it, the list had been a wall for any new argument
anywhere.

**And that was the second time, which is what SH793 is about.** The advice here used to be
*read the per-tool number before planning a trim, `ShMcpToolBudgetTest` prints every tool
when it fails, and forcing it to fail is the cheapest way to get the table.* A measurement
you have to break the build to see is one nobody takes, and the proof is the third instance:
the P1 list declined `postTypeMerge` because `shio_apply` was "3 tokens under" the ceiling,
and when somebody finally measured it was **19**. Same file, same figure, stale twice; both
earlier repairs corrected the number, which is why it went stale again.

So the table is printed on **every** run now, green included:

```
[SH793] per-tool MCP schema cost, ceiling 460 est. tokens
  shio_apply        441  headroom   +19
  shio_write        434  headroom   +26
  …
```

The number is gone from the P1 reason rather than corrected, a reason states the *shape* of
a cost and names what prints it, and `ShAgentP1ConformanceTest` now refuses any reason
spelling `N tokens under`. Two tools sit within 26 tokens of the ceiling, which nothing had
said out loud before this printed.

**The next capability was an argument, exactly as the budget file predicted (SH811).** That file said
in as many words that the next thing to arrive here would be *"an argument, a trim, or a conversation
about which tool leaves"*, and SH811 took the first, because the measurement left no real choice:

| option | cost |
|---|---|
| `include=editable` on `shio_digest` | **24** tokens (146 → 170), after the description was cut to its decision |
| a thirteenth tool the size of `shio_digest` | **146** tokens, and `ShMcpServerTest` refuses a thirteenth anyway |

The twelve tools summed to **2499 against a 2500 ceiling** (one token of headroom) so *any*
addition needed the line moved. It went to **2560** rather than to the 2523 measured, because a
ceiling at its wall refuses the next addition instead of measuring it, which is the failure SH743
named; 37 free is about two arguments at the ~20 SH795 measured one to cost.

It also belongs on `shio_digest` on the merits and not only on the budget: that tool takes an address
and answers a structural question about the rendered page, and *which fields are editable* is a third
question of exactly that kind, off exactly that render (P10). SH809's `/agent/editable` gave the
capability an `AGENT_ROUTE`; this is the `MCP_TOOL` reach the inventory calls the bar P1 actually
asks for.

Two guards caught the change on the way, both by design: the `tools/list` **golden** moved by exactly
four lines (every MCP client reads those bytes and nothing else in the build checks them) and SH673
found the budget **restated** in `mcp-transport.conformance.mjs`, which is what that rule exists for.

**And a tool at the wall is now declared rather than discovered (SH795).** `AT_THE_WALL` in
`ShMcpToolBudgetTest` holds a tool with no room for another argument and why it cannot be
trimmed, the device `ShAgentResponseBudgetTest` uses for response budgets, one layer down,
joined to `known-debt.test.mjs` as the **eighth** ledger so it prints with everything else
this project knowingly carries.

The threshold is measured, not chosen: **one argument costs about 20 est. tokens**, and three
recorded instances agree, `createSite` on `shio_apply` came in 18 over, a single boolean of
schema put `tools/list` 19 over, and `postTypeMerge` is the third. An absolute rather than a
fraction of the ceiling, because what is being fitted is an argument and its cost does not
scale with the tool holding it. So `shio_apply` (+19) is an entry and `shio_write` (+26) is
one argument from becoming one.

Both directions are checked, which is what stops the list accumulating: a tool that reaches
the wall undeclared fails, and an entry that has *gained* room fails too, an exemption
excusing nothing hides the next tool to arrive.

**What the trims were, and the rule they came from.** All three were description prose
restating a parameter the model reads one line later:

| Dropped | Kept |
|---|---|
| "Set `blueprint` to apply a ready-made section … instead of declaring one" | that the verb exists, and `shio://blueprints` |
| "dryRun=true shows the diff" | `changed=0 means no drift` |
| "the post's identity in this document" | "the post's identity here" |

They gave back 10 of the ~26 the argument cost; the remaining 5 raised the total to 2400.
A trim that funds only part of a raise is still the right order, it is what makes the
raise a measured remainder rather than the first answer.

**The wire is golden-tested.** `ShMcpWireConformanceTest` holds
`src/test/resources/mcp/golden/tools-list.json`; a new argument is an intended protocol
change, regenerated with `-Dshio.mcp.golden.update=true` and reviewed as a diff. Here it
was four lines, which is the point of the check.

**One thing the proof will not do.** `ShAgentProofService` lints *post* addresses, so a
folder-only apply attaches nothing. A test that declares only folders passes for the wrong
reason.

## A refusal names its kind, not only its problem (SH757)

Every REST refusal carries `type: https://shio.viglet.com/errors/<slug>`. **No MCP refusal carried
it**, `ShMcpServer.renderProblem` emitted title, detail, `fix`, `didYouMean`, `allowed`, `example`
and `at`, and never the type. Measured against a live instance, `folder.copy` into its own subtree
answered four lines of excellent prose in which the string `folder-copy-cycle` appeared nowhere.

That is heavier than a missing field, for the reason SH596 gives about `shio://diagnostics`: **MCP
is not one surface among several for a plugin user, it is the only one.** So the reader with no
HTTP of its own could tell one refusal from another only by matching English, and the conformance
suite had an assertion saying otherwise (`/duplicate-url/`, from SH719's own ledger entry, which
names the slug as what the losing racer is told), which is how this was found.

`Type: <slug>` now follows the title. **The bare slug, not the URI**: the
`https://shio.viglet.com/errors/` prefix is a constant 34 characters on every refusal and the last
segment is the whole of what anybody compares (P3). `about:blank`, `ProblemDetail`'s default,
prints nothing, because that is the absence of a kind rather than a kind called blank.

**The wire golden could not have caught this**, and that is worth knowing before trusting it:
`tools-call-error.json` redacts the text as `#string`, so it pins the *shape* of an error result
and is blind to everything the model actually reads. What caught it was a live suite asserting a
string, which is the tier that exists for exactly this gap.

## The population nobody could ask about (SH760)

`cli/test/live-coverage.test.mjs` reports which of the agent's vocabulary a **running instance** has
ever been asked, and it held three populations: MCP tools, batch ops, CLI verbs. **Resources were
not one of them**, so the matrix reported full coverage of three while a fourth sat at zero, and
that reads as reassurance.

Measured when the population was added: **0 of 8**. Not one `ShMcpResource` had ever been fetched
over the wire. Three of them exist *specifically* for the reader with no other route,
`shio://ops` is the write vocabulary (SH332), `shio://diagnostics` the failure report (SH596),
`shio://usage` the spend (SH754), and each was argued into being in prose without once being read
from an instance.

**This is SH596's lesson one level down**: a classification can only be as honest as its categories
allow. There, `Reach` had no way to say "a resource delivers this", so a neighbouring capability was
recorded and the check came back green over the gap. Here the matrix had no way to *ask*.

Mechanics: `mcpResources()` reads the URIs from the beans (never a typed list, for `mcpTools`'
reason), `mcp.readResource(uri)` is the one greppable call shape, and a **template** is held by the
prefix `ShMcpResource#matches` keys on, reading `shio://schema/Page` covers `shio://schema/`.
`UNEXERCISED.resources` opens at all eight, so a resource arriving with no live coverage is a diff
somebody reads rather than a percentage that absorbs it.

**The vacuity guard matters more here than for the other three**, because this population opens at
zero: a reader that had stopped matching would print the same finding it shipped to report. So it is
driven against a sample where the answer is known, a fetched URI counts, a URI named in prose does
not, and one template is not credited by another being read.

## The scarcest number in the product, printed on green (SH976)

`tools/list` is paid on every turn of every session, so its ceiling is what decides whether a
capability can be an MCP tool at all, and a green run printed the per-tool rows and never the total.
The number that settles the design question was visible only in the failure it caused, which is the
same shape SH793 found one level down and fixed for the per-tool figure.

Every run now prints the whole surface: `tools/list` against its ceiling with the tool count,
`initialize`, and (since SH976 gave them ceilings) `resources/list` and `prompts/list`. Those two
had no budget at all, which was the wrong pair to leave unmeasured: a resource is where work goes
when the tool list is full, so the surface absorbing the pressure was the one nothing watched. Their
ceilings are loose on purpose (they are paid once, when a client lists them) and exist to make growth
visible rather than to ration it.

**The affordance ladder is computed, not remembered.** The choice between an op, a parameter, a
resource and an endpoint is made in nearly every task on this surface and was being made from memory.
`ShMcpToolBudgetTest` measures the marginal cost of one more of each from the live server, an op name
in `shio_write`'s enum, a parameter averaged over every tool's schema, an entry in `resources/list`,
an entry in the manifest's endpoint index, and prints them. On the first run: **an op name is single
digits; a parameter is an order of magnitude more; a resource and an endpoint are another order up,
with the resource the dearer of the two.** Only the first of those is asserted, because it is the
claim the docs make everywhere: the batch vocabulary is the surface that grows, and a task reaching
for a thirteenth tool is answering a question this number already answered.

## The headroom, allocated before it is spent (SH977)

Four designed additions each said, in their own task, to measure against the per-tool ceiling first,
and each would have passed alone: a state perspective and `include=revisions` on `shio_read`, a
schedule predicate and query fields on `shio_find`, `when` and `cancel` on `shio_publish`, and the
locale pair on `shio_write`. At SH976's measured rate a parameter is about 30 est. tokens, so three of
those are 180, against 40 free. Whichever landed first would have taken the room, and the rest would
have shipped as a resource or an op **by accident** rather than by the ladder. A ceiling that is raced
for is not a budget; it is an order of arrival.

So the room is divided in `token-budgets.properties` before anybody spends it. A **reservation** is
the most a tool may cost (not the most it may grow by) and `ShMcpToolBudgetTest` refuses a tool over
its reservation, naming the ladder's cheaper shape. The whole allocation (reserved tools at their
reservation, every other tool at what it costs today) has to fit `mcp.tools.list.total`, so the room
cannot be promised twice. The run prints the table.

`mcp.tools.list.total` rises once for this, from 2640 to 2820, with the arithmetic beside it: 180 for
the three parameter pairs plus SH811's two arguments of headroom. `shio_write` and `shio_apply` get
none of it, both are `AT_THE_WALL`, and SH1073 already spent `shio_write`'s last two tokens on
`locale` and `translationOf`. The ladder was applied before the ceiling was: anything expressible as a
batch op costs about three tokens and belongs in the enum, and these are not, they are parameters of
a read, a search and a publish.

A later task that adds an argument cites its reservation rather than the ceiling, and one that wants
more has to say which planned capability is giving up its room.

## `initialize` is kept, not just answered (SH999)

The handshake's `clientInfo` (the name and version a client chooses for itself) used to be read
for nothing. It is now written to `sh_agent_run` against the session id the same call mints, which
is what turns the uuid on every audit row into `claude-code` on the change feed and on the console's
Activity page. Writing it never refuses the handshake: `initialize` is the first call of a session,
so a failure there would turn "your agent has no label" into "your agent cannot start", and a run
with no name is a state both readers already handle.

`shio_changes` gains **`by`** (`agent`, `curator`, or a session id), REST's own filter, which P1 puts
here first. It was paid for out of `shio_publish`'s reservation rather than out of the ceiling:
SH977 held 216 there for SH992's `when` and `cancel`, those shipped at 197, and a reservation whose
capability has landed is room the allocation counts and nobody can spend.

## `clientInfo.intent` (SH1000)

Alongside the name SH999 keeps. The handshake is the one call whose purpose is a client saying what
it is, and "what I am here to do" is the same kind of fact, declared once per run rather than once
per turn, which is what `tools/list` would have cost. `shio_changes`'s `by` argument uses the same
vocabulary as the REST filter (`agent`, `curator`, or a session id).

## `shio_find` gained six words inside its reservation (SH1022)

The schema now spells `subtree`, `locale`, `modifiedSince`, `modifiedBefore`, `author` and
`modifier` beside the nine it had, and it is **still inside SH977's 255-token reservation for this
tool**. That was paid for by trimming the nine, not by raising the ceiling: `tools/list` is re-sent
every turn of every session, so a schema is the one place where a word costs its length times every
turn anybody ever takes. `ShMcpToolBudgetTest` is what says whether the trim covered it.

The tool is a shape over `ShAgentReadService.find(ShContentQuery, …)`, the same call
`/agent/find` makes, with the same parsing and the same refusals (P10). So there is no MCP-only
reading of `modifiedSince` to get wrong, and no second place to add the next filter.

## `shio_assets` walks a large library (SH1030)

The tool gained `after`, the cursor `shio_find` has carried since SH146, and the answer carries it
beside `truncated`. The dead end it removes is the same one: `truncated` on its own tells a caller
it is missing rows and not how to get them, and an agent choosing an image from a large media
library is exactly the caller that meets a cap. A list it cannot continue reads as "there is no
such image".

Paid for **inside the tool**, not out of the ceiling: its description lost the words a caller does
not act on and it measured 184 either side, which is what the reservation system is for. The two
assertions are the pair that matters, the cursor is emitted, and it is read back as `after`. A
cursor a tool advertises and does not read is worse than none: the caller walks and gets page one
for ever.

## `shio://requests` is what a curator asked from the dock (SH1006, SH1007)

Shio hosts no model, so a question typed into the console's dock is a `request` note on the page
the curator is on. `shio://requests` (`ShNoteRelay`, REST mirror `GET /api/v2/note/open?kind=request`)
lists the open ones, newest first, one line each, with the note id, its anchor as `id:<object>`,
the author and the body. The answer is `note.add` on that anchor with
`data: {kind: "reply", parent: <note>, body}`, then `note.resolve`. A reply written in the same run
that drafted the change names that run (`agentSessionId`, SH106), and the dock then offers the
curator "open the change" and "approve and publish" through the review queue's own decisions call.
So draft first and reply second, in one session. It is a resource and not a tool, for the usual
reason: read once when a session starts or resumes, not billed on every turn's `tools/list`.
`ShAgentCapabilityJoinTest` classifies it as the read half of the notes channel that
`batchWrites` claims.

**A request about a field (SH1019).** A line on `shio://requests` whose note carries a field
path, anchored to a post and the field the curator asked about, wants a value for that field.
Reply with `note.add` whose `body` is the value itself, nothing around it, because the dock
offers to write that body into the form as it stands. Read the field and what it accepts first
(`shio_read` of the page, and its type's constraints).

**A request for a translation (SH1062).** The Languages board asks for a translation by creating
the missing draft and putting a `request` note on it (or on a stale translation, asking for it to
be brought up to date). A row or a locale column asks for each of its cells the same way, one
draft and one note per cell, and the translation workspace puts the note on the page it has open. The page the note hangs on **is** the target: its `locale` is the language
to write, and its translation group leads to the source. Read both, write the translated fields
into that page with `post.upsert` as a draft, one run whose `intent` names the request, then reply
on the thread and `note.resolve` it. Do not publish: the curator approves from the dock (SH1007) or
the review queue. The board shows the cell as *requested* while the note is open
(`ShSiteCoverage.Cell.requested`), so a request left unresolved keeps saying nobody has answered.

## `shio://notes` is every open note, across sites (SH1215)

The console's Open notes page reads `GET /api/v2/note/open`; `shio://notes` (`ShNoteRelay.renderOpen`)
is the same list for an agent, and that endpoint is its HTTP twin. One line per note, newest first,
capped at 50: its kind, the note id, its page by address, the field when it has one, the author and
the body, then how to close one (`note.resolve` with `data.note`). Requests are rows here too, so a
session that reads this needs no second read of `shio://requests`, which stays the dock's narrower
channel. A resource and not a manifest endpoint: the endpoint index is held under its row count and
`response.manifest` ceiling on purpose, and a read made once per session is what resources are for.
`console/surfaces.json` names it as the `notes` surface's agent equivalent with `kind: "resource"`,
which `ShConsoleSurfaceP1Test` resolves against the registered resources.
