---
name: shio-agent-surface
description: Read before touching Shio's agent surface or MCP server. Triggers on /api/v2/agent/**, POST /mcp, the manifest, the context pack, batch/plan/apply, post.upsert/post.move/folder.move, confirm tokens, the review queue, AGENT token scope, /agent/verify, /agent/changes, /agent/diagnostics, /agent/memory, /agent/find, /agent/read, ShMcpTool, ShMcpResource, tools/list budgets, teaching errors, and the SH79 address forms.
---

# Shio agent surface + MCP

The namespace shaped for a coding agent rather than a browser. It is a **shape
over** the CDA + console services (P10) and holds no business logic of its own.

**Read [docs/agents/agent-surface.md](../../../docs/agents/agent-surface.md)** for
the endpoints, and
**[docs/agents/mcp.md](../../../docs/agents/mcp.md)** for the MCP shape over them.
Between them they are authoritative on:

- every `/agent/**` endpoint, its projections (`?include=`, `?format=`, `?budget=`),
  and its conditional-read (ETag) behaviour
- the batch op set and which ops are the *only* way to do a thing —
  **`post.move` to rename or relocate a post**, **`folder.move` one level up**
- `?verify=true` / `?digest=<n>` — the run's own proof, and why a `dryRun` gets none
- what bounds an agent (Block L): `AGENT` scope, session identity, the publish gate,
  confirm tokens, the review queue
- the MCP tool list (11 of a hard cap of 12), the per-tool and total token ceilings,
  and **error routing** — a fixable failure is a tool *result* with `isError:true`,
  a JSON-RPC `error` is only for an unusable message

## The invariants that decide most design questions here

1. **The agent path is never a privilege escalation — and it cuts both ways
   (SH201).** Where the console is looser, the console is the hole, not the bar.
   Never loosen the agent path to match a curator's session.
2. **Errors are instructions (P6).** Every 4xx carries `fix`/`allowed`/
   `didYouMean`/`example`. A silent fallback where a value was *selected* is a
   defect — see SH320's one rule: a parameter that **selects or bounds work**
   teaches; one that only **adds a section** is lenient.
3. **A new capability does not get a new endpoint by default.** The MCP tool count
   is capped at 12 with 11 used and `tools/list` is paid every turn (P3), so a
   REST-only endpoint with no tool is a P1 violation. The usual answer is a
   projection on a read that already takes the same subject.
4. **Trim the description before raising a ceiling.** Budgets live in
   `shio-app/src/test/resources/token-budgets.properties`; raising one is allowed
   and must be justified in the commit. `shio_apply` is the tool to watch (457/460).

## Guards that already exist

`ShMcpWireConformanceTest` pins `tools/list` against a golden — regenerate with
`-Dshio.mcp.golden.update=true` and review the diff. `ShAgentResponseBudgetTest`
guards eleven canonical response sizes; `ShAgentTaskBenchmarkTest` and
`ShAgentSourceAttributionTest` guard the ratios (and both boot their own in-memory
database — `ShBenchmarkIsolationLintTest` enforces that). `ShAgentIncidentFramesTest`
is a source lint: a `record(...)` inside a `catch` that passes no frame fails the
build.

Concept and worked example:
[docs/specs/SH74-agent-native-cms.md](../../../docs/specs/SH74-agent-native-cms.md).
