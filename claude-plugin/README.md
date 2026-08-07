# The Shio plugin for Claude Code

An agent session that starts **already knowing** Shio, instead of spending its first ten
calls discovering it. This is also the distribution channel for the whole agent-native
concept: it is how a developer adopts it in one command.

## Install

```
claude plugin marketplace add openviglet/shio
claude plugin install shio
```

Then point it at your instance:

```
export SHIO_URL=http://localhost:2710      # the default
export SHIO_USER=admin
export SHIO_PASSWORD=...
```

The plugin's MCP server runs `@viglet/shio`'s stdio bridge (`shio mcp`) via `npx`, so there
is nothing else to install. If you already have the CLI on your PATH, `claude mcp add shio --
shio mcp` is the equivalent without the plugin.

## What it adds

**MCP server** — `shio`, exposing `shio_context`, `shio_find`, `shio_read`, `shio_write`,
`shio_apply`, `shio_publish`, `shio_assets`, `shio_changes`, `shio_verify`, `shio_digest`,
plus the `shio://manifest`, `shio://blueprints`, `shio://context` and
`shio://schema/{postType}` resources.

**Skills** — loaded when the work calls for them, not on every turn:

| Skill | For |
|---|---|
| `shio-site` | Building and changing pages: addressing, `shio_apply`, draft-then-publish, moves |
| `shio-content-model` | Post-types and widgets as code: `shio pull`/`push`, the DSL, the traps |
| `shio-verify` | Checking work before handing it over: the lint's severities, digests, incidents |

**Commands**

| Command | Does |
|---|---|
| `/shio-start [site]` | Reads the instance — model, sitemap, blueprints, current lint state — and stops |
| `/shio-blueprint <name> <site> [k=v]` | Plans a blueprint, asks, applies, reports the verification |
| `/shio-check [site]` | Verifies and reports errors / warnings / not-checked separately |

**`AGENTS.fragment.md`** — paste into your project's `AGENTS.md` so the conventions survive
a session that does not have the plugin. Hand-written today; `shio context --as agents-md`
will generate it from the live instance.

## The rules the plugin encodes

They are the ones that are expensive to learn by making the mistake:

- Content is addressed by **path**, never by a UUID you carry between calls.
- `shio_apply` before `shio_write`: a statement re-runs safely, a script does not.
- **`dryRun` first**, always. Read the plan; a `delete` line is a stop-and-ask.
- Writes land in **DRAFT**. Publishing is a separate, explicit step.
- Rename with **`post.move`**. Upserting at a new address creates a second post.
- **Verify before reporting done**, and say what you did *not* check.
