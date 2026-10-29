---
title: Token Economy
description: "What a call to Viglet Shio costs in tokens, the budgets enforced in CI, how to spend less, and the benchmark the project holds itself to."
sidebar:
  label: Token Economy
---

If an agent operates your CMS, the cost of operating it is measured in **tokens**. That
makes response size a product property, not an implementation detail, so Shio treats it
like one: every response on the agent surface has a declared ceiling, and **a build that
makes a response bigger fails.**

This page is what a call costs, how to make it cost less, and what the project measures
itself against. It also publishes the two numbers that are *less* flattering than a reader
might expect, with the reasons, because a cost that is argued rather than measured is a
defect.

---

## Every response has a budget

The budgets live in one file in the repository, in estimated tokens, using the same
4-characters-per-token heuristic the instance itself reports:

| Response | Budget (est. tokens) |
|---|---|
| `tools/list` (the whole MCP tool catalogue) | 2375 |
| …per tool | 460 |
| MCP `initialize` | 200 |
| `GET /agent/manifest` | 2650 |
| …`?format=terse` | 2400 |
| Context pack, per site row (terse) | 145 |
| Context pack, per site row (JSON) | 220 |
| Context pack, per site row (`agents-md`) | 430 |
| Context pack, per post-type row | 25 |
| `find`, per row (terse) | 110 |
| `read`, per row (terse) | 60 |
| `verify`, per finding (terse) | 60 |

Two things about the table are the point rather than the detail:

- **`tools/list` is the most expensive fixed cost on the surface**, because it is re-sent on
  every turn of every session before any work happens. That is why the tool count is capped
  and why some capabilities are a *parameter* on an existing tool instead of a tool of their
  own.
- **Raising a budget is allowed, and is meant to be visible.** It is a reviewable edit with
  a reason in the commit message. What is not allowed is a response quietly growing.

Every response also carries an `X-Shio-Est-Tokens` header, so you can measure your own
traffic without guessing.

---

## Four ways to spend less

### 1. `format=terse`

Most reads take `?format=terse` and answer in plain text instead of JSON:

```http
GET /api/v2/agent/find?site=mysite&type=Article&format=terse
```

On listing-shaped responses (`find`, `read`, `verify`, `changes`, a change set) this saves
roughly **a third to a half**.

:::note[Terse mode saves ~10% on the manifest, not a third]
The manifest is mostly per-endpoint prose that is kept **verbatim** in terse mode on
purpose: it is the part a caller reads to learn what an endpoint does, and compressing it
would be saving tokens by removing the value. So the manifest goes from 2650 to 2400, about
a tenth. If you want a smaller manifest, ask for less of it (`include=`), do not switch
representation.
:::

### 2. Ask for less

| Instead of | Use |
|---|---|
| The whole manifest | `?include=limits,features`: only the sections you need |
| The whole context pack | `?include=model` or `?include=sitemap`, and `?budget=<n>` |
| A whole document | `GET /agent/read?fields=TITLE,ABSTRACT` |
| Every row | `limit=`, then walk with `after=<cursor>` |
| Object ids you will not use | nothing, **ids are off by default**, because an id is the most expensive field a row can carry |

### 3. Conditional reads

Every read that supports it answers `304 Not Modified` to an `If-None-Match` whose tag still
matches:

```http
GET /api/v2/agent/changes?since=<cursor>
If-None-Match: "…"
```

A `304` costs a round trip and **no body**. This is what makes polling the change feed cheap
enough to do often, and the reason to walk the feed with `since=` rather than reading the
window: a windowed read is a moving edge and can never be unchanged, while a cursor-based
read of an unchanged range is byte-identical and free.

### 4. Fold the proof into the write

```http
POST /api/v2/agent/batch?verify=true&digest=true
```

The lint report and the render digests come back **with** the write, instead of costing two
more calls. The whole point of the surface is that a task is three or four calls, not thirty.

---

## What a context pack replaces

One `GET /agent/context` returns the content model, the sitemap by path, and the conventions
earlier sessions recorded. Without it, an agent discovers the same facts by listing sites,
listing folders, reading a sample post to infer a shape, and asking for post types, several
round trips whose answers it then has to hold.

The project measures that as an **attribution ratio** per source, rather than as one headline
number, because an aggregate mixes savings and names none:

| Source of the saving | Fewer calls | Fewer tokens |
|---|---|---|
| Discovery (the context pack vs exploring) | ≥ 40× | ≥ 11× |
| Path addressing instead of id round trips | ≥ 20× | ≥ 500× at scale |
| The lint instead of a round trip to a person | ≥ 50× | ≥ 150× |

Those are **floors** asserted in CI against a console-shaped baseline, and the scale rows are
required to *rise* with fixture size rather than being asserted at one size: a claim that a
saving grows has to be measured at two sizes or it is not a claim.

---

## The benchmark

Four canonical tasks are run against the real instance on every build, and both their call
count and their token cost are held:

| Task | Calls | Est. tokens |
|---|---|---|
| Fix a broken link | 3 | 400 |
| Build a marketing site | 4 | 2700 |
| Translate a site | 3 | 800 |
| Add a post type and 10 posts | 3 | 1800 |

Four calls to build a site is the number worth internalising: **a session, not a
conversation.**

:::caution[What the build-a-site number does and does not include]
The benchmark ends at `verify` (content that passes the checks) **not** at a page a person
can open in a browser. Rendering and publishing are separate steps, and closing that gap is
an open task. So read 2700 tokens as "a correct, verified site's worth of content", not "a
finished website".
:::

---

## Instance memory: the cheapest call is the one you do not repeat

```http
PUT /api/v2/agent/memory
```

A decision an earlier session worked out: a naming convention, a layout choice, why a field
exists, is stored on the instance and comes back in the context pack. Re-using a key
**rewrites** that note rather than adding a second one, because the next session pays for
every near-duplicate it has to reconcile.

Over a long-lived site this is the largest saving available, and the one nothing can measure
for you.

---

## Related Pages

| Page | Description |
|---|---|
| [The Agent Surface](/shio/docs/agent-surface/) | Every endpoint, and the parameters named above |
| [MCP Server](/shio/docs/mcp/) | Why there are twelve tools and not twenty |
| [Configuration Reference](/shio/docs/configuration-reference/) | The `shio.agent.*` limits behind these numbers |
| [The `shio` CLI](/shio/docs/cli/) | `shio context`, and the CI gates |
