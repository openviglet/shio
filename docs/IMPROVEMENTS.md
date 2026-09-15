# Shio — Improvements & Roadmap (design rationale)

> **What this file is:** the *design rationale* (the what & why) for **unshipped**
> work — the spec behind each backlog item. It is one of three roadmap documents;
> keep them in sync:
>
> | File | Holds | One line per |
> |---|---|---|
> | [ROADMAP.md](ROADMAP.md) | **Task status** (single source of truth) — active backlog | task |
> | [CHANGELOG.md](CHANGELOG.md) | What has **shipped** (git is authoritative) | shipped task |
> | **IMPROVEMENTS.md** (this file) | Design rationale for active sections | — |
>
> **Founding rule:** once a task ships, its detail is removed from here and
> reduced to a one-line entry in CHANGELOG.md — `git log` is the authoritative
> history. Do not re-accrete shipped implementation reports into this file.
> (Sections §I–§V and §VII were collapsed to pointers when Blocks H–M landed; the
> numbering is preserved so existing `docs/specs/*` cross-references still resolve.)

**Status legend** (status itself lives in ROADMAP.md): ⏳ partial · 📋 designed · 💭 idea.

**Section order** below follows the **suggested work order** in ROADMAP.md
(H → K → I → J → L → M → N), not the block letters — §IX is Block K, §X is
Block I, and so on. Each section header names its block.

**Cross-references** use relative paths from `docs/`; linked code may have moved —
verify against `git ls-files` before acting.

## Table of contents

- [0. Strategy — the agent-native reorientation](#0-strategy--the-agent-native-reorientation)
- **Part 1 — the reorientation (active)**
  - [IX. Perception & verification loop (Block K) — ✅ shipped whole](#ix-perception--verification-loop-block-k--✅-shipped-whole)
  - [X. Content as files (Block I)](#x-content-as-files-block-i)
  - [XI. Blueprints + Claude Code plugin (Block J)](#xi-blueprints--claude-code-plugin-block-j)
  - [XII. Agent safety & human-in-the-loop (Block L)](#xii-agent-safety--human-in-the-loop-block-l)
  - [XIII. Token economy, measured (Block M) — what the measurement opened](#xiii-token-economy-measured-block-m--what-the-measurement-opened)
  - [XIV. Realignment of what already shipped (Block N) — what building it opened](#xiv-realignment-of-what-already-shipped-block-n--what-building-it-opened)
  - [XV. The visible site (Block P)](#xv-the-visible-site-block-p)
- **Part 2 — pre-existing sections**
  - [I. Headless foundation — the CDA](#i-headless-foundation--the-cda) (shipped)
  - [II. JavaScript-for-pages DX](#ii-javascript-for-pages-dx) (shipped)
  - [III. Composable platform primitives](#iii-composable-platform-primitives) (shipped)
  - [IV. Server-side JS rendering — dropped](#iv-server-side-js-rendering--dropped)
  - [V. Legacy retirement](#v-legacy-retirement) (shipped)
  - [VI. Market positioning](#vi-market-positioning)
  - [VII. Content portability](#vii-content-portability) (shipped)

---

## 0. Strategy — the agent-native reorientation

> Full concept, protocol design and worked example:
> [specs/SH74-agent-native-cms.md](specs/SH74-agent-native-cms.md).

### 0.1 The goals, restated

The original brief mixed two goals — *"be relevant to today's market"* (an
architecture decision: headless/API-first) and *"let JavaScript developers build
pages"* (a DX decision). Both shipped: the CDA (Block A), the SDKs and starter
(Block B), post-types as typed code (SH62).

A third goal now supersedes them in priority, without contradicting either:

3. **"Be the CMS a coding agent can drive fast."** Optimize the whole system so an
   agent — Claude Code specifically — needs fewer cycles, fewer tokens, and fewer
   human round trips to build and then *change* a site.

This is again **not** a language decision, and again not a rewrite. It is a
decision about **protocol shape, discoverability, determinism and verifiability**.

### 0.2 What Shio is today (the facts that drive the decision)

- The headless foundation is **done**: versioned CDA with scopes/env keys/caching
  (SH1–SH5), framework-agnostic + React SDKs (SH6/SH7), Next.js starter with
  SSG/ISR/on-demand revalidation (SH8/SH9), type generation (SH11), post-types as
  typed TS code with a drift guard and a console lock (SH62–SH66), webhooks,
  preview, versioning UI, scheduling, audit, search, i18n, ACL, soft-delete
  (SH12–SH16, SH51–SH55), multi-tenancy (SH22), media + transforms (SH38–SH43,
  SH73), a full exchange/portability engine (SH68–SH72).
- Every one of those is shaped for a **human operator**: entity-shaped JSON, UUID
  addressing, a GUI-first console, docs written as prose recipes.
- The primitives an agent-native protocol needs are therefore *already here* —
  declarative sources, canonical serialization, plan/apply (`push --check`), an
  exchange package format, an audit trail, soft-delete, scoped tokens. What is
  missing is the **shape**, not the machinery.

### 0.3 Why NOT rewrite the backend (binding non-goal — unchanged)

- A rewrite costs 6–18 months just to **reach parity** — zero new user value.
- It **destroys the Turing/Dumont synergy**: shared models, security, SDKs become
  an HTTP-only island that must be re-integrated.
- **The language is not the market problem.** Reference composable CMSs are Java
  (Magnolia, dotCMS) or JS; PHP (WordPress/Drupal) *is* the legacy we want to move
  past; Go is great for infra but rare for CMS with weak admin/templating ecosystem.
- **A CMS's differentiator is never the server language** — it's the content
  model, the API, the editing experience, and delivery. Under the new orientation
  it is the *protocol*: an agent cannot tell what the server is written in, only
  how many calls it costs.
- Conclusion: rewrite = **high cost, high risk, no perceived value**. Keep Java.

### 0.4 Why in-JVM JS/Groovy templating is not the answer

Groovy would solve *server-side scripting*, but the audience never asked for it.
The same now applies to **GraalJS** (the former Block D, SH17/SH18 — **dropped**):
an agent builds the front end in Next.js, where it is strongest and where the SSG/
ISR/preview story already exists (SH8/SH9). An in-JVM script engine adds sandboxing
risk, a second template language, and hot-reload complexity, and buys the agent
nothing. The answers stay: **headless + Next.js** (Block B, shipped) for delivery,
and the **agent protocol** (Block H) for authoring.

### 0.5 The north star

> **Shio is a CMS whose primary operator is a coding agent.** The agent builds and
> maintains the site; the **human curates** — approves, corrects, tweaks; the
> **CDA delivers**. Java/Spring stays the content engine, JS/TS stays the delivery
> layer, and every new decision is judged by *agent cycles and tokens*, not clicks.

The three actors and their interfaces:

| Actor | Interface | Job |
|---|---|---|
| **Agent** (Claude Code) | MCP tools + content files on disk + CLI | Build & maintain: model, structure, content, front end, verification |
| **Human** | React console, Universal Editor (SH10), preview links, PR review | Curate: approve, correct, tweak copy, decide |
| **Front end** | CDA (REST/GraphQL) + SDKs | Deliver |

Note what this does *for the human*: with the agent doing data entry and
scaffolding, the console stops being a form-filling tool and becomes a review and
visual-tweak surface. That is a better console, not a deprecated one — and it is
why the Universal Editor and Media Library get *more* important, not less.

### 0.6 The ten design laws

Binding. A feature that violates one is wrong even if it is requested. Each new
task in Blocks H–N traces to at least one.

| # | Law | Consequence |
|---|---|---|
| **P1** | **Agent-first design order** | Every capability lands MCP → files/CLI → REST → console. A console-only feature is incomplete. |
| **P2** | **One call replaces a session** | Discovery is a product feature (manifest + context pack), not a docs problem. |
| **P3** | **Tokens are a measured budget** | Response budgets + a CI benchmark; a token regression is a build failure. |
| **P4** | **Files beat APIs for authoring** | Project content to disk; the agent's cheapest tools are `Edit`/`Write`/`Grep`. |
| **P5** | **Determinism over cleverness** | Stable order, path addressing, idempotent writes, byte-identical serialization. |
| **P6** | **Errors are instructions** | Every 4xx carries `fix`/`allowed`/`didYouMean`/`example`. |
| **P7** | **Never surprise the human** | Draft-default, dry-run, explicit publish, confirm tokens, attribution, review queue. |
| **P8** | **Skipping steps needs appliable units** | Blueprints, not prose recipes. |
| **P9** | **Close the perception loop** | Cheap textual proof (lint, routes, render digest) instead of a human round trip. |
| **P10** | **Compose, don't fork** | MCP is a shape over the CDA + console services, never a third contract. |

### 0.7 What Shio deliberately is *not*

**Shio hosts no model.** No bundled LLM, no prompt runner, no provider API keys in
the CMS, no "generate this page for me" button. Shio is the substrate an external
agent drives. The reasoning:

- The defensible asset is **protocol quality** — discoverability, determinism,
  verifiability, token economy. Inference is a commodity that someone else
  operates better.
- Embedding inference imports another vendor's rate limits, cost model, safety
  posture and outage surface into the content engine.
- The agent already *is* the intelligence, and it is on the other side of the tool
  call with far more context than the CMS could ever pass it.

Corollary: "agent-native" is measured in tool calls and tokens, never in how much
AI the product contains.

---

# Part 1 — the reorientation (active design)

## IX. Perception & verification loop (Block K) — ✅ shipped whole

The agent cannot see. Two of the three human round trips in a typical build existed only
for that reason, which made this the cheapest large win after Block H — a human cycle is
the most expensive unit in the system by orders of magnitude.

✅ **Shipped whole**: SH99–SH104, SH141–SH144, SH154, SH169, and the follow-ups closing
the block produced (SH609–SH642). The implementation reports live in
[CHANGELOG.md](CHANGELOG.md) Block K; the design subsections are gone rather than left
behind as history `git log` already holds.

**What the block established, and what later work should not relitigate:** `ok` is false
only for an *error*, because a gate that fails on what the CMS cannot prove is a gate
people bypass — and every mechanism added afterwards (SH141's delivery proof, SH142's
accepted findings, SH114-declared routes) turns a warning into a fact or an explicit
decision rather than tightening the gate. Everything the agent reads is cheap and
textual; SH102's PNG is scoped for a human. **SH187** revisits that for the other reader
→ [CHANGELOG.md](CHANGELOG.md) Block P.

**The follow-ups taught a second lesson, about the block's own instruments.** Four of
eleven were defects *in* a guard, not in the product: a lint that read prose as code, a
debt reader that could not tell a mention from an assertion, a gate whose fallback was
null in the deployment it demands, and a "flake" that was a scoping bug. A perception
loop that cannot perceive itself is the failure this block should expect next.

## X. Content as files (Block I)

> The largest cycle reducer, and the least obvious. Authoring over HTTP is the
> wrong medium for an agent; a git-diffable text tree is the right one — the agent
> is measurably better and cheaper with `Edit`/`Write`/`Grep` than with
> read-modify-write over an API.

> ✅ **SH86, SH87, SH88, SH89 and SH90 shipped** → [CHANGELOG.md](CHANGELOG.md) Block I.
> What follows is the design rationale for what the projection **still owes**.

## XI. Blueprints + Claude Code plugin (Block J)

*"Skip steps with things that are already done"* only works if the ready-made thing is
**appliable**, not readable. A prose recipe an agent must read and reimplement saves
nothing — it just moves the work. **Absorbs the old SH23 marketplace epic** (SH27–SH31).

✅ **Block J is shipped** → [CHANGELOG.md](CHANGELOG.md) Block J. The format, the verb,
the first-party set, the section vocabulary, the Claude Code plugin, the five mismatches
that shipping the two halves exposed, and the whole marketplace — catalogue, install,
published seed, CLI, MCP tool and console — are all there, and SH682 closed the block by
giving the two writes the role the blueprint reload already had.

## XII. Agent safety & human-in-the-loop (Block L)

> An agent-driven CMS is only adoptable if a human can bound it and undo it.
> **Invariant: the agent path is never a privilege escalation** — tenant isolation
> (SH22), content ACL (SH54) and token scopes apply identically on every agent
> surface. An agent-native CMS that quietly runs as root is not adoptable.
>
> SH106–SH109, SH200 and SH201 have shipped; their design notes are gone with them (see
> CHANGELOG.md Block L). What remains here is the one task still blocked, plus the follow-ups
> SH200/SH201 turned up.
>
> **The invariant grew a second edge while they landed.** SH106–SH109 were all argued as
> tightenings, and SH201 is the same rule read from the other end: after Block L the agent path
> was in places *stricter than the console*, and where that happens the console is the hole. The
> form to carry forward is symmetrical — **the two surfaces enforce the same ACL, and whichever
> is stricter is the correct one.** SH222–SH226 are the places that edge points at next.

## XIII. Token economy, measured (Block M) — what the measurement opened

> ✅ **Block M shipped in full**, including the six follow-ups the measurement itself opened
> → [CHANGELOG.md](CHANGELOG.md) Block M. The design rationale for shipped work lives there
> and in the code; `git log` is the history. What follows is only the unshipped remainder —
> and, again, all of it exists because the block did what it was built to do: produce
> numbers, and then disagree with an assumption.
>
> The current reading is **tokens 2.8×–10.5×, tool calls 2.3×–3.8×**. SH157 was the first
> change in three blocks to move a ratio by improving the *agent* path rather than by the
> baseline getting leaner, and what it left behind is mostly the shape of that argument
> getting harder: the calls that remain are real work.

## XIV. Realignment of what already shipped (Block N) — what building it opened

> The reorientation was mostly additive, but it did invalidate some existing choices.
> Block N spent them → [CHANGELOG.md](CHANGELOG.md) Block N, and so are the tasks that
> building it opened (SH163–SH167, SH233, SH234), the ones that finishing it turned up
> (SH235–SH237, SH239, SH240, SH668), and the last three, which were all about an
> instrument being wrong rather than a feature (SH729, SH731, SH737). Nothing is left
> open here. Concept spec §10 has the original table.

## XV. The visible site (Block P)

> An agent can build a site's model, folders and pages over the agent surface, and nobody
> can look at the result. This section is the gap between *correct content* and *a page a
> person sees* — the product's differentiator, and the one part of the loop with no path
> through it.

### XV.0 What the original engine actually was (the `FSP` analysis)

Before designing a renderer it is worth knowing precisely what was removed, because the
reputation of the old engine ("Nashorn, therefore unsafe, therefore never again") turns out to
describe its *host* and not its *design*. `origin/FSP` is not a divergent branch — it is an
ancestor of `HEAD`, last touched **2019-09-09** — so this is Shio's own history, readable with
`git show origin/FSP:<path>`.

The pipeline:

```
URL → ShSitesContextURL                        parse object id + `format`
    → site.postTypeLayout                      {PostType: [{format, layout, mimeType, cacheTTL}]}
    → PT-PAGE-LAYOUT post                      attrs: HTML, JAVASCRIPT, THEME
    → Nashorn eval(JAVASCRIPT)                 bindings: shContent, html, request
         ├── shObject.js                        a 174-line façade over 7 Java components
         └── handlebars.min.js                  ← the actual template language
    → ShCachePage / ShCachePageLayout           keyed page cache, honouring cacheTTL
```

Four things follow, and they reverse the conclusion I would otherwise have reached.

### XV.0.1 The template language was Handlebars, not JavaScript

`ShCacheJavascript.shObjectJSFactory()`
concatenates `classpath:/js/server-side/shObject.js` and `handlebars.min.js` and evaluates them
once per thread. Authors wrote Handlebars — logic-less, declarative, no code execution. Nashorn
was the *host* that ran Handlebars and bridged to Java; it was never the authoring surface.

**The capability set was already meant to be closed.** `shObject.js` exposes seven functions and
nothing else — `formComponent`, `searchComponent`, `navigation`, `navigationFolder`, `query`,
`queryByPostType`, `getRelation` — each a one-line wrapper over a Spring bean bound in
`ShNashornEngineConfiguration` (`shNavigationComponent`, `shQueryComponent`, `shSearchComponent`,
`shFormComponent`, `shGetRelationComponent`, plus three `*Utils`). That is an allow-list
expressed as a convention.

### XV.0.2 The liability was the host, and it was real

`ShNashornEngineConfiguration` calls
`getScriptEngine(String[])` — Nashorn's `getScriptEngine(ClassFilter)` overload exists precisely
to bound what a script may reach, and it is not used. So a layout's JavaScript had `Java.type()`
over the entire classpath: `Runtime.exec`, file I/O, the `DataSource`. The raw
`HttpServletRequest` was bound into script scope as `request`, and `shObject.js` uses it
directly. Layouts are *content*, so this made a content author a JVM operator. §0.4's objection
is correct and this is the concrete form of it — but it indicts `eval`, not Handlebars and not
the layout model.

**Two smaller design notes worth keeping.** `regionError` reports a script failure with file,
line, column, a five-line window and a caret under the offending character — better authoring
feedback than most template engines ship, and exactly the shape law P6 asks for. And the
`ThreadLocal<Map<String, ScriptContext>>` that caches script contexts per thread reuses bindings
across requests, which is a state-leak between visitors; a template evaluator with no mutable
global scope does not have that problem to solve.

### XV.0.3 What the model handled that a fixed section vocabulary does not

This is the question that
matters for SH183, and the answer is unambiguous. Navigation, queries and listings, search
results and forms are all *dynamic* — none is expressible as a static section post, and all four
were first-class in the old engine. A closed vocabulary of eight section types cannot render a
menu, a paginated article list, a search results page or a contact form. It also had two axes
this repository has no equivalent for: **format** (the same post rendered as `default`, or as
another format with its own layout, mime type and TTL — the mechanism behind alternate
representations) and **theme** (`PT-THEME`, resolved per layout).

**The lineage.** The model is OpenText WEM / Vignette V7's — page → template → component, with
presentation held as managed content rather than as files in a build. That is why the levels line
up the way they do, and it is worth knowing when weighing the design: this is a shape with
twenty years of enterprise use behind it, not an invention.

### XV.0.4 The objection: V7 rendered with a script engine too

It did, and that precedent is real — the objection deserves an answer rather than a
restatement of the non-goal. Two things changed, and neither is about capability.

*The threat model.* In V7's era a template author was a trusted employee, templates
shipped through a controlled release process, and the server sat on a corporate network.
In Shio a layout is content; content is written by an **agent**, and packages are meant
to arrive from a registry whose trust story is still an open question (SH96 exists
precisely because "where may a package come from" is unanswered). Add the multi-tenant
direction (SH22, SH45–SH47) and "content can execute arbitrary JVM code" stops being a
convenience and becomes a tenant escape. The same design is safe under one set of
assumptions and not under the other.

*The runtime.* Nashorn was deprecated in JDK 11 and **removed from the JDK in 15**. What
is left is the standalone `org.openjdk.nashorn` artifact — the one still sitting unused
in `shio-app/pom.xml`, pinned at 15.7, on a project running Java 21. Building the
product's differentiator on it would mean betting the render path on a library that
exists to ease migration *away* from itself.

So the conclusion is not "V7 was wrong". It is that the part of V7 worth keeping is the
**model** — page → template → component, presentation as managed content — and the part
that has to be replaced is the host that ran it.

### XV.0.5 What a real template looked like: regions are slotted by an HTML attribute

`openviglet/bootstrap-site` is not a project — it is an `export.json`, an SH68 exchange
package, and its ten posts are `PT-FOLDER-INDEX` ×1, `PT-ARTICLE` ×3, `PT-PAGE-LAYOUT`
×2, `PT-REGION` ×2, `PT-THEME` ×1, `PT-FILE` ×1. The presentation *is* content, and it
travels the same way content does. Four things in it settle the design:

**1. Regions are slotted by an HTML attribute.** A layout's `HTML` field is a whole
document with named holes:

```html
<body>
  <div sh-region="BOOTSTRAP_NAV_REGION"> </div>
  <div sh-region="BOOTSTRAP_CONTENT_LIST_REGION" style="margin:20px"></div>
```

`sh-region` is the composition mechanism, and it is also — already, in 2018 — exactly
the annotation contract the Universal Editor needs, which SH185 later wrote down as a
protocol → [CHANGELOG.md](CHANGELOG.md) Block P. A rendered page that keeps these
attributes tells the editor what each part of the DOM is and which post to save it to.

### XV.0.6 The layout's JavaScript is ceremony, not capability

Both layouts carry the same four
lines:

```js
load('https://cdnjs.cloudflare.com/ajax/libs/handlebars.js/4.0.11/handlebars.min.js');
var template = Handlebars.compile(html);
var html = template(shContent);
html;
```

Compile the HTML as a Handlebars template against the data, return it. That is what
Handlebars.java does natively with no script at all. (The article layout adds one small data
remap — `{title, description, text, theme}` picked out of `shContent` — which is a projection,
not logic.) Note also `load('https://cdnjs.cloudflare.com/...')`: the engine fetched its
template library **from a CDN at render time**, so the render path depended on remote code
being both reachable and unmodified.

### XV.0.7 A region's JavaScript is a view-model builder, and every part of it is a helper

The
navigation region registers a helper, calls a component, and reshapes the result:

```js
Handlebars.registerHelper('file_url', ...);              // → a value helper
var navigation = shObject.navigation(siteName, true);    // → a block helper
forEach.call(navigation, function (shFolder) { ... });   // → the helper yields this
```

and the content-list region is the same shape: `shObject.query(folderId, "PT-ARTICLE")`, then a
loop mapping `TITLE`/`DESCRIPTION`/`TEXT` and building links with
`shObject.generateObjectLink`. Between 767 and 1098 characters of hand-written JavaScript per
region, to produce what a domain-aware block helper produces in one line:

```hbs
{{#query folder=this postType="PT-ARTICLE"}}
  <div class="card"><h5>{{title}}</h5><a href="{{link}}">Read More</a></div>
{{/query}}
```

So migrating to registered helpers is not a loss of expressiveness — it is the removal of
boilerplate that exists only because the loop had nowhere else to live. The templates themselves
(`{{#each}}`, `{{#if}}`, `{{{text}}}`, custom helpers) are ordinary Handlebars and port as-is.

### XV.0.8 A theme carries site-wide assets

`PT-THEME` has `CSS` and `JAVASCRIPT` fields — both
`null` in this sample, so the intent is clearer than the usage: one place for the stylesheet and
script a whole site shares, referenced by each layout's `THEME` field.

So the honest conclusion is that the old engine's *architecture* was better than what replaced
it for the specific job of "make something a human can see" — and the only part that has to go
is one line, `scriptEngine.eval(javascript, sc)`, plus the boilerplate that existed to feed it.

## XVI. The human half (Block O) — what reading the console next found

Block O shipped, and its lesson was that **console defects answer `200`**: seven of them, none
visible to a Java test, a CLI test or the MCP golden. The two below were found the same way but
from a different angle — not by driving the console, but by fixing the *agent's* move verbs
(SH134/SH137/SH139) and having to read the console's implementation of the same operation each
time. The pattern is consistent enough to state as an expectation: **when a verb exists on both
surfaces, the console's copy is where the careless version lives**, because nothing on it is
asserted at the level the agent surface is.

> ✅ **SH202 and SH203 shipped** → [CHANGELOG.md](CHANGELOG.md) Block O. Their two durable
> rules: a copy corrects each of its names **in the scope that name is unique in** (title per
> folder, URL per site — sharing one counter renames a page for a collision the curator cannot
> see), and **the client prevents what it can see, the server refuses what it must**. The five
> sections below are what finishing them found. A sixth, **SH246** — the Java suite red by one
> since SH144 made the incident store durable — has since shipped too; its answer was the
> SH164/SH239 precedent, that a test asserting something about the whole instance has to boot
> its own database.

# Part 2 — pre-existing sections

## I. Headless foundation — the CDA

> ✅ **Fully shipped (SH1–SH5)** → [CHANGELOG.md](CHANGELOG.md) Block A; per-task
> design records in `docs/specs/SH1`–`SH5`. Under the new orientation the CDA is
> the **read substrate every agent tool composes over** (P10) — unchanged, and more
> load-bearing than before. The SH1 forward-compat constraints (state is a
> service-level param, not a URL segment; `siteId` is a first-class query param)
> still bind, and now also bind the agent surface.

## II. JavaScript-for-pages DX

> ✅ **Fully shipped (SH6–SH11, and the SH62 epic SH63–SH66)** →
> [CHANGELOG.md](CHANGELOG.md) Block B; specs `SH11-type-generation.md`,
> `SH62-post-types-dsl.md`. **Why it still matters:** post-types-as-code is the
> template the whole agent-native protocol follows — declarative source → canonical
> serialization → plan → apply → drift guard → explicit GUI/code boundary. Blocks H
> and I are that same pattern applied to content and to the protocol itself.
> (Historic subsection anchors `§II.1`–`§II.7`, cited by `docs/specs/*` and no longer
> headings here, map to SH6, SH7, SH8, SH9, SH10, SH11, SH62.)

## III. Composable platform primitives

> ✅ **Fully shipped (SH12–SH16, SH26, SH51–SH55)** → [CHANGELOG.md](CHANGELOG.md)
> Block C. The agent surface leans directly on four of them: the **audit trail**
> (SH51 → the change feed and attribution), **soft-delete** (SH55 → revert), the
> **draft/published diff** (SH14 → review), and **search**
> (SH52 → `shio_find`). Open follow-up: folder soft-delete (subtree-hide semantics).
> (Historic anchors `§III.1`–`§III.10`, cited by `docs/specs/*` and no longer headings
> here, map to SH12–SH16, SH51–SH55.)

## IV. Server-side JS rendering — dropped

> ⛔ **Dropped as a non-goal.** The former Block D (SH17 GraalJS spike, SH18 `.js`
> page templates) is removed: an agent builds the front end in Next.js, where the
> SSG/ISR/preview story already exists (SH8/SH9). An in-JVM script engine adds
> sandboxing risk, a second template language and hot-reload complexity, and buys
> the agent nothing. See §0.4.

## V. Legacy retirement

> ✅ **Fully shipped (SH19–SH21, SH48–SH50, SH56–SH61, SH73)** →
> [CHANGELOG.md](CHANGELOG.md) Block E. `shio-react` + the headless CDA are the only
> console/delivery surfaces. Note the reframe: the console surfaces rebuilt here —
> Universal Editor (SH10), Media Library (SH73) — are now the **human's** curation
> tools (§0.5), so they gained importance rather than becoming maintenance debt.

## VI. Market positioning

## VII. Content portability

> ✅ **Fully shipped (SH67 epic: SH68–SH72)** → [CHANGELOG.md](CHANGELOG.md) Block G.
> The original "new sites are blank" regression is closed. **Why it still matters:**
> the SH68 exchange package format is the substrate Block J's blueprints are built
> on, and `ShSiteImportService.provisionInto` is the engine `shio apply`
> reuses — one engine now serving site clone, tenant provisioning, bootstrap and
> blueprints.
>
> **Non-goal (do not re-add):** the legacy `ShSitesNodeJS` "export site as a
> runnable Node app" is superseded by `create-shio-app` (SH8) and, going forward, by
> blueprints → [CHANGELOG.md](CHANGELOG.md) Block J.

## XIX The public site (Block S)

Turing ships turing.viglet.org and Shio ships a README. The product with the sharpest
positioning statement in the portfolio - a CMS whose primary operator is a coding agent
- has no page that says so, and the ten laws, the eleven MCP tools, the content-as-files
projection and site replication are legible only to somebody who already cloned the
repository.

This block builds that page in the shape `apps/site` already proved in Turing: Vite,
React 19, Tailwind v4 and shadcn/ui, prerendered to static HTML per route, served at the
root of a domain, with no backend of its own. What it does not copy is the argument.
Turing's centrepiece is a live search box, because a visitor can type a query and judge
the answer. Shio's is a Claude Code session, because what is being sold is not a feature
a human operates - it is how cheaply an agent operates the product. Every section on the
landing page therefore answers one of the ten laws with something a visitor can check: a
token count, a teaching 4xx, a file tree, a draft that did not publish itself.

The site is also written for the reader it is about. An agent evaluating a CMS reads the
page it was handed, and a Claude Code-first product that answers only in HTML is making
its own argument badly.

## XX End-user documentation, round two (Block T)

Block R measured this and could not fix it. It took the coverage matrix from **33 wrong
of 40** to **6**, and every one of the six was a cluster **no Block R task ever named**
— not leftovers that ran out of time, never in scope. That is the finding the matrix
existed to produce, and it is why these were a block rather than a snagging list.

Four of the six were curator-facing, which inverts who actually needs a page: an agent
reads a manifest, a person reads a page or gives up. P7 — *never surprise the human* —
is the law the gap sat on. The seventh entry was the one a scan could not see: the image
epic's operator half scored 🧩 rather than 🔴, and a partially-covered row does not read
as an orphan.

**Two tasks are worth keeping for their shape.** SH623 and SH628 were ⚠️ — a page
describing a shape the product left — and that is a *rewrite*, because a new section
under stale prose makes the page contradict itself. SH628 landed the instrument first
(SH322): a version in prose is a claim nothing derives, so `docs-stack.conformance.mjs`
reads Java, Spring Boot, Node and pnpm out of this repository's build files and fails
any page — `README.md` included — that states another. It found three defects on its
first run, and a fourth in SH499's own reader.

## XXI Documentation the matrix still names (Block U)

Block T closed every row its own tasks named and left this file's audit still holding
two entries — one because the summary that scans it could not see the row, and one
because that summary is typed rather than derived. They are the same defect at two
altitudes, which is why they are one block rather than a footnote on the last one.

The block's rule is SH338's: the count is fixed before the page is written. A page added
under a summary that cannot count it is a page the next scan will not know exists.

## XXII Realignment of what already shipped

Block N's family. It holds the cases where a capability shipped complete against its own
brief and incomplete against a law — most often P1, where the console got an interface
and nothing else did. These are not defects in the feature; the feature works. They are
defects in its *reach*, and they are filed separately because the fix is almost never in
the code the original task wrote.

Its recent entries widened that beyond features, and mostly concern instruments failing
to reach: one blind to half the repository (SH738), a *process document* routing a
reader past an instrument that exists (SH739), a rule stopping at a language boundary
(SH740), a ledger taking a shape without the guard that makes it safe (SH741), and a
population found by naming convention rather than by structure (SH742). Nothing is left
open here → [CHANGELOG.md](CHANGELOG.md) Block N.

## XXIII The vocabulary proved live (integration tests against a running instance)

Shio's whole product claim is a vocabulary an external agent drives: thirteen MCP tools
over `POST /mcp` and `shio mcp`, thirty-three CLI commands, and the `/api/v2/agent/**`
ops underneath both. Almost all of it is proved by MockMvc in `shio-app` and by module
imports in `cli/test/` — two tiers that, by construction, cannot be wrong about the
thing an agent actually meets: the tool name it must guess right, the input schema it
must spell, the framing, the response budget, and the shape of the refusal it has to
read to recover. `cli/conformance/` is the tier with an instance, and it calls two
tools.

This block closes that gap deliberately and in order. First the instrument that counts
what has never been spoken live, because six suites with no denominator prove nothing
about coverage. Then the read half, the write half, the teaching errors, the CLI surface
and the live budgets. Then the part a per-verb suite structurally cannot reach: the
chaotic run — verbs interleaved, sequenced wrongly on purpose, permuted under a recorded
seed — because an agent does not call the vocabulary in the order a test author
imagined, and every ordering bug in the product to date has been found by somebody doing
it out of order.

Non-goals bind here as everywhere: the instance runs no browser, and none of this
becomes a third contract.

## XXIV The vocabulary proved live (Block V)

Where the rationale for Block V lives: what a running instance has been asked, and what
it has not. The tier exists because a MockMvc proof and a wire answer can disagree —
over a schema the client had to guess, a refusal slug it has to branch on, or a cache
nothing in a Spring test can have. SH707's matrix is what keeps that honest by naming,
rather than counting, the verbs no live suite has ever called.

## XXV Realignment of what already shipped (Block N)

Where the rationale for Block N lives. The block is a standing category rather than a
project: it receives findings about work that already shipped, so it empties and reopens
rather than finishing, and each reopening is one finding whose repair is not itself a
feature.

## XXVI What the replication round left behind (Block W)

Block Q closed with SH774 after a round that changed what a replica *is* to a curator:
kept markup became content they can move rather than a field they cannot reach.

What it left behind is of three kinds, and none of them is a feature.

**A sweep.** A closed block owes its reader-facing surfaces one pass, and this one
changed the thing those pages describe.

**Corpus gaps the round exposed by needing them.** Every question asked of the section
vocabulary's own render cost went unanswered because the benchmark's pages compose no
sections — so the shape the product is *about* is the one shape nothing measures.

**Capability limits found by hitting them.** A template cannot ask an aggregate question
about a relation, which decided SH772's design rather than being noticed in the
abstract.

The block exists because these were found *while* doing the work and would otherwise
evaporate with the commit that revealed them — the cheapest findings the project ever
gets, and the ones with no reporter but the person who tripped over them.

## XXVII What the gate round left (Block X)

Block W spent thirty-two tasks on the instruments that judge this repository — the
ledgers, the gates, their stamps and the scans that read source. It closed with the four
gates comparable, every declared list joined to one reader, and a flake that had
resisted a hundred and twenty runs both diagnosed and cured.

What it leaves is of two kinds, and this block is named for both.

The first is **consequence**: removing `--test-force-exit` cured the abort and also
removed something the flag had been hiding, which is now somebody's twenty-minute CI
timeout waiting to happen. A round that changes how a suite ends owes the next person
the failure mode it created.

The second is the **method** that round kept rediscovering and never wrote down as
anything runnable. A flake was summoned rather than waited for; a timing claim was wrong
twice because it was unpaired; a scan found two defects in the very document describing
them. Each of those is a technique, and each currently survives only as prose in
`docs/agents/build.md`.

And one thing Block W deliberately did not touch: the **P1 inventory** it produced holds
capabilities an agent cannot reach, most with no task against them. The law the product
is named after has an honest list of its own breaches, and the list has been read more
often than it has been worked.

## XXVIII What the editable surface started (Block Y)

SH809 took the first row of the P1 inventory that named a route it did not have, and
shipped it. The pattern it proved is the point of this block: the inventory has been
printed on every run for many blocks and read more often than it has been worked, and
one row of it turned out to be a small, composable endpoint rather than a subsystem —
because the answer was already written down in the rendered page, for the browser.

It also left two things behind, and both are the ordinary cost of shipping a new surface
rather than a defect in the idea.

The first is a **naming slip in a brand-new contract**, caught by reading the diff
rather than by any guard: an address field that every other endpoint calls `address`.
Cheap now, and a published field name is the most expensive kind of thing to change
late.

The second is the **strictness of the reachability it achieved**. P1's own hierarchy
puts an MCP tool above a declared route — a tool is what an MCP-only client can invoke,
and a route needs HTTP and the manifest to find it. `/agent/editable` is a route. That
is reachable, and it is not the bar the law actually asks for, which the inventory's own
categories say out loud.

Neither was worth holding the task for. Both are worth the block that follows it.

## XXIX The inventory as a worklist (Block Z)

Two blocks ago the P1 inventory was a thing that got printed. SH809 took one row of it
and shipped the route it named; SH811 took the same row to the stricter reach the
inventory itself grades. Both were small, because the honest entries had already done
the hard part — each one names *what would resolve it*, and the test asserts that target
absent, so shipping cannot be forgotten.

That changes what the list is for. It was written as evidence — the first honest measure
of how far P1 is actually honoured — and it is now the backlog for the law the product
is named after.

Which exposes the thing this block starts with. **The list cannot be sorted.** Whether
an entry is a gap or a decision lives in the prose of its reason, so answering *how much
is left* means reading ten paragraphs, and doing that is how this block was planned.
`reviewQueue` is a decision and says so; `visualSnapshot` is honest because the flag
reports false; three are CLI-shaped because their input is a file or somebody else's
URL. The rest are work.

The other half is to keep working it while the reading is cheap. One entry names its
target down to the parameter — a tool that takes no `format`, so the output meant to be
written to a file is reachable only by whoever has a file.

## XL Replication — the palette a source actually wears

Block Q closed and reopened around one question that four rounds of measurement did not
settle: which of a source's colours is *its* colour.

Everything upstream of that now works. `propose` lifts the declared palette whole (133
tokens where it once ranked three literals), the lifted declarations are re-emitted
rather than dropped (SH850), the theme's own names are namespaced so a collision is
reported (SH853), a floor refuses a value that cannot serve its role (SH852), and
`convert` prints which slot each token filled and why the rest kept a default (SH849).

What remains is a judgement, and the sections under this heading are about making it
from evidence a page carries rather than from a property of a colour in isolation.

## XLVI Replication — the structure a source states about itself

§XL is about a source's colours. This one is about everything else a source says plainly
about its own shape and a replica draws from somewhere other than the source: its
navigation, the order its pages are meant to be read in, the labels it gives them.

The pattern is the one the palette work kept finding. A capture records the fact,
`propose` or `convert` has no route for it, and the replica falls back to something
derived from the content model instead — defensible in isolation and wrong beside the
page it replaced. The palette version was ranking utility-class literals while 319
declared custom properties sat unread; the sections here are the same shape on structure
rather than on colour.

What makes these a family of their own rather than a line each under §XL is that the
fallback is usually **invisible**. A wrong accent is a colour somebody can see. A
navigation built from the wrong field renders, links correctly and reads as finished —
it is only wrong to a reader who knows what the source's own menu said, which is nobody
looking at the replica.

The address skips five numbers because each of them was declared and dropped by a
shipped block, and an entry citing one still exists: reusing an address would make that
entry cite this.

## XLVII The corpus, and the shapes it does not carry

SH833 established the rule for section types: a check names any type that loses its last
producer, because a vocabulary entry nothing in the corpus can produce is a capability
nothing exercises. The same question about a **source shape** has no instrument, and the
last two tasks are what that costs.

Both were found by a person building a site, not by a suite. SH873's menu of SEO titles
and SH878's replica of the same shape were sitting in code the whole corpus runs over,
every run, green. Neither could have been caught, because the shape that produces them
is not in the corpus to be captured.

The sections here are about that gap rather than about any one archetype. What makes it
worth a family is that the failure is silent in the direction nobody checks: a suite
over seven archetypes reports seven passes whether the eighth shape is missing or merely
absent, and the count of archetypes is not the count of shapes.

The distinction the entries below turn on is between a fixture that *exercises* a path
and one that merely *does not fail on it*. A capability with one producer is one edit
away from having none, and the run that removes the last one is the run that reports
nothing at all.

## XLVIII The half only the JVM can assert, asserted for the old branch

`ShConvertedLayoutRenderTest`'s own docstring states the rule this family is about: a
template that compiled and listed nothing would pass every check in JavaScript, so the
JVM renders the generated layout and reads what came out. That rule is right and it has
a failure mode of its own — the rendered assertion is about whichever branch the fixture
happens to reach, and a branch added later is covered by nothing while the test stays
green.

The sections here are about that shape rather than about any one branch. A template
assertion in JS proves the markup was written; a render assertion in Java proves it
resolved. When a template gains a branch, the second one does not follow automatically,
and nothing reports the gap: the suite renders one page and finds what it expected,
because what it expected is what the other branch also produces.

The distinction worth holding is between a fixture that reaches a branch and a fixture
that cannot tell which branch it reached. The second is the dangerous one — it passes
for the wrong reason, and it passes just as loudly.

## XLIX A package carries what its manifest does not list

A blueprint is two things at once — a directory of files and a manifest naming which of
them an apply reads — and nothing holds the two together. A file that no key lists is
carried, shipped, kept byte-identical by a generator, and installs nothing.

That is not automatically wrong: `emit.mjs` writes the whole SH95 vocabulary into both
section packages on purpose, and says why. What is wrong is that the reason it gives is
about **provisioning** — *a site that received the full vocabulary can grow a
testimonial section next month* — and a carried file that no manifest lists provisions
nothing, so the benefit the comment claims is not delivered by the arrangement that
claims it.

The sections here are about that gap. It is the second time it has produced a defect:
SH873 found `Menu` and `MenuItem` sitting in both packages' `post-types/` and in neither
manifest, so the navigation mechanism a package needed was one nothing could install,
and both packages listed a page query instead.

The general question is which of the two artefacts is authoritative. A manifest that
must list everything on disk makes the directory the truth and the manifest a derived
index; a directory that may hold more than the manifest reads makes the manifest the
truth and the extra files dead weight a generator keeps refreshing. Both are defensible
and the repository has never chosen, which is why the same defect can arrive twice.

## L An instrument that says RENDERED about what it never looked for

`shio annotations` answers one question — is this page authorable — and it is the
instrument SH465 and SH475 both shipped past. Its summary line is what a reader acts on,
and its exit code is what a gate acts on.

The join it makes is `declared minus annotated`. Nothing in it asks whether a field was
drawn. The sentence it prints about the result says it was:

    N declared field(s) are RENDERED and not editable

That is true of the population SH465 found — a collection row that draws with no
`sh-post` and no `sh-field`, which looks finished and cannot be curated — and it is a
claim about every other member of the same set, made without evidence. A field the
template never draws lands in exactly the same count and is described the same way.

The sections here are about the gap between what a check computes and what its message
says it computed. It is the shape Block W's round found a dozen times in this project's
own instruments, met once more in the one an *operator* reads: SH790 is the nearest
relative — a guard naming allocation when the cause was a sentence, which now computes
which of the two fired.

What makes it worth its own family rather than a wording fix is the second half: the
count also drives the exit code, so a mode whose pages legitimately draw none of those
fields fails the gate every time it is run, forever.

## LI Evidence a coincidence can supply

SH882 gave `shio annotations` a thing it had never had: evidence. A declared field
counts as drawn when its stored value can be found in the served page, which replaced a
sentence that asserted the same thing from the post-type alone.

Evidence has a failure mode an assertion does not, and it arrived on the first live run.
A value of `"3"` is found in almost any page, so the check reported a field as drawn on
a page that does not draw it — and the report is now more convincing than the claim it
replaced, which is the direction that costs a reader something.

The sections here are about what a match has to be before it is evidence. The rule the
first cut used — a non-empty string, found as a substring of the page's collapsed words
— is right for the population it was written against (a heading, a paragraph, a whole
markup blob) and wrong for the one it was not: a scalar short enough to occur by
accident.

A threshold on length would work and is the shape this repository keeps refusing,
because the number is not derivable from anything. What is derivable is the difference
between a value that *is* an element's content and one that merely appears inside the
page — and an annotation attaches to an element whose content is the field, which is
what makes that distinction the one the instrument is actually about.

## LII The page's own fields, on the page a package ships

A section's fields are annotated by both section layouts and have been since the
packages shipped a design. The page's own fields are not, and the difference has never
been argued — it is what nobody looked at, which is a different thing from a decision.

SH766 settled the same question for the layout `convert` writes, and settled it twice
over: a field a layout <em>draws</em> is annotated where it draws it, and a field that
reaches only the document head is declared in a `hidden` element instead — because an
editor cannot attach to a `<title>` or a `<meta>`, and injecting a visible element to
make one reachable would be a worse defect than the one it fixes.

The sections here are about that pattern not reaching the packages. The two layouts are
a starting point a curator is meant to edit, and the first thing anybody edits about a
page is its title.

What makes it worth stating rather than just fixing is how it was found: not by reading
the layout, which several tasks have done, but by running the instrument that exists to
answer exactly this question against a site the package had just built. The check had
been unable to say it — its verdict was a sentence rather than a measurement until SH882
— so the gap survived every round that looked at these packages.

## LIII One rule about what a field is, asked on two surfaces

SH872 gave the annotated values back to the markup that carries them, and the question
that left — a curator's edit stores a key no post-type declares — was answered with a
rule: **a key annotated by the post's own markup is declared by that markup.**
`ShAnnotatedFields` is that rule, and the two readers of the content model on the Java
side both ask it.

The CLI has its own reader of the same model, and it was not told. The sections here are
about the shape that leaves behind: a rule stated once, enforced where it was written,
and silently absent from the other surface that computes the same thing.

It is not the drift SH673 names, where two copies of a fact disagree. There is one fact
and one implementation; what is missing is a *caller*. That is harder to see, because
the surface that never asks does not contradict anything — it simply answers a narrower
question and prints the answer with the same confidence.

What makes it worth a family rather than a line is that the symptom does not look like a
missing rule. It looks like arithmetic: a count over a denominator that no longer means
what the reader assumes, on the one mode the rule exists for.

## LIV The console adopts the shared bento layer (Block AQ)

Shio and Turing are the same design system's two consumers and they do not look alike.
All 24 of Shio's ui files are one-line re-exports of the package, so the primitives are
already shared; what is not shared is the layer above them. Turing's bento redesign -
the airy hero, the frosted hover-lift card, the mosaic, the scroll-linked save bar - is
23 components inside that product's tree, and Shio's console has none of it: 40 of its
43 pages render a console PageHeader, 42 render a flat Card, three carry a sticky save
bar with no morph. This block does the half that has to happen before a single page
changes. The package ships the layer; Shio installs it, builds a parallel shell to
migrate into, declares its surfaces once for the rail and the palette, moves its brand
orange into a token, and translates the chrome. Four of the twelve tasks are instruments
rather than features, and they come first for the reason this project files that
argument every time: a count of what is still console chrome is what says how big this
is, and a textual render proof is what says a migrated surface is right without asking
somebody to look at it.

## LV The curator's console, surface by surface (Block AR)

Forty-three console pages, migrated behind the parallel route the previous block builds,
in an order chosen by what each slice teaches the next. Home first, because it is cheap
and it decides whether the redesign reads as real; then sites, which settle the list
shape for the ten list screens after it; then the content browser, where a curator
actually spends the day. Posts and post types come next and are the hard ones - the post
editor carries fifteen dynamic widgets whose field logic must not be rewritten, which is
what the chrome adapter is for. Admin follows in three slices sized by how alike their
pages are. The Universal Editor is deliberately late: it is the one surface where
swapping the shell can break behaviour rather than appearance, because its preview
bridge reads the chrome around it. The block ends with the three tasks that make a
migration finished rather than merely done - the cutover itself, the retirement of the
console shell, and the published documentation, which after the cutover describes a
product that no longer exists.

## LVI What Turing's index never receives

Shio publishes into Turing's index (SH84) and can rebuild it (SH413). What the index
receives is still narrower than what Shio knows: a post-type's own fields arrive as
prose or not at all, a push that fails is never retried, and an attachment's text is
never read. Each is a different kind of gap — a schema decision, a recovery path, an
extraction step — and each is visible only as a search returning the wrong thing rather
than as an error anybody sees.

## LVII What the Turing round left in the build and in a guard

## LVIII The public site (shio.viglet.org)

## LIX What the Turing round left in the build and in a guard

## LX The design system's consumer contract, adopted and gated (Block AU)

Shio is one of seven products declared in the design system's consumers.json, and the
only one whose declaration is wrong: it says the console takes three subpaths where its
source imports five. That is not bookkeeping. The package's own guards read that
declaration, so a parity check meant to catch a drifting consumer passes on one it does
not describe.

The larger gap is newer. The package stopped being only a component catalogue when VDS88
gave it a way to vendor its page contract into a consumer: viglet-ds-page-reference
writes .claude/skills/viglet-ds-pages/ and nine artboards into the consuming repository,
on the argument that a contract kept in another repository is read after the fifth
screen. roadkeep-gui adopted it. Shio has none of it — no skill, no artboards, no drift
check — and cannot, because the floor it pins is 2026.3.5, whose files list carries
check-duplicates.mjs and neither the page-reference bin nor the docs payload.

This block is the substrate the rest of the round stands on, ordered the way this
project orders instruments: the version floor that makes the tooling runnable, the
vendoring that lands the contract, the check that makes drift a failure rather than a
discovery, then the declaration corrected on both sides. It closes by asking whether the
package should be a plugin rather than copied files — a question this repository already
ships an answer to.

### LX.4 A declaration the guards trust and the source contradicts (SH943)

consumers.json is the design system's answer to a question it got wrong twice: prose
that names a subset of consumers as though it were the whole. The file's own header says
the membership is what the guards read, and that entries are a snapshot measured over
product source. For Shio the snapshot is short by more than half. It lists ".",
"./styles" and "./vite"; the console's src imports ".", "./bento", "./bento.css",
"./styles" and "./router", since SH940 "./i18n" as well, while vite.config.ts imports
"./vite", so four real entries are undeclared and none is spurious.

The header anticipates this and still missed it. It records that shio-react's test
directory greps for ./bento to census the pages importing it, and warns that counting a
lint looking for a subpath as the product taking it would record the opposite of what
SH887 says. That warning is about over-counting. What happened is the reverse: measuring
over src once and never re-deriving left three real imports undeclared.

So the fix is two-sided, and the second side is the point. Correcting Shio's entry is
one line that drifts again the next time the console takes a subpath. What must also
land is the guard that re-derives every consumer's entries from its source and fails
when a declaration and an import set disagree — the discipline page-reference.mjs
already applies to its own declaration half. SH322 is the rule being followed: when a
task says and whatever a grep turns up, the grep is the deliverable.

### LX.5 A plugin, or files rewritten on every run (SH944)

page-reference.mjs argues for copying, and the argument is good: a contract an author
must browse another repository to find is read after the fifth screen, so the surfaces
are written where a session and a person both trip over them. Nothing here disputes
that. The question is narrower — whether copying is the only way to be tripped over, now
that a plugin is a thing a repository can enable.

A plugin answers by distribution what vendoring answers by rewriting. An enabled
plugin's skills are in every session by name with no file in the consumer's tree, they
move when the plugin moves rather than when somebody remembers to run a bin, and there
is no drift to gate because there is no second copy. What a plugin does not answer is
the artboards: nine .dc.html files that open straight from the file tree are exactly
what a consumer wants on disk, and canvas.json is a file the adopter also writes in. So
the likely answer is both — skills from the plugin, artboards vendored — and that split
is what this task decides rather than assumes.

Shio need not invent the shape. claude-plugin/ here is a working example:
.claude-plugin/plugin.json, .mcp.json, three commands, four skills, and a test asserting
the manifest. The deliverable is the decision with its reasons, plus whichever half Shio
can land on its own side; the package's half is the design system's own line.

### LX.7 The reading column, owned once (SH946)

BENTO-AUTHORING section 1 says main sets the max width, the gutters and the vertical
rhythm once, and a page sets none of the three. The analysis measured the opposite in
Shio: seven lines across six bento pages at three widths, max-w-6xl on the vocabulary
page, max-w-4xl on three form pages and max-w-7xl on the post-type picker and the home
page. Nobody reviewing one screen sees it, which is the exact failure the rule names.

The package has to export the shell first, which is VDS131. On Shio's side,
bento.root.page.tsx recomposes on BentoShell, whose main carries the column as a
default, narrow or wide prop, and the six pages drop their own mx-auto, max-w, padding
and spacing classes. A single-question form asks for narrow by name instead of repeating
a class.

The proof is a before-and-after surface-digest pair asserting that nothing but the width
classes moved, which is the use the digest was built for: cheap textual evidence rather
than asking somebody to look at six screens. It has no agent leg, and its classification
is chrome.

### LX.8 The page lint, run where the duplicate gate already runs (SH947)

The vendored SKILL.md closes on two rules it calls easy to get wrong: a page sets no
width, gutters or rhythm, and a product claims --primary through the base inputs rather
than by setting --vg-primary, which would key the dark ground to the light value. Shio
broke the first six times. It is clean on the second only by luck, since nothing would
notice a write to --vg-primary if one appeared.

VDS132 ships viglet-ds-page-lint. Shio's half is shio-react/test/page-lint.test.mjs,
modelled line for line on no-duplicate-components.test.mjs: spawn the bin resolved
through the installed package against the repository root with --check, assert a clean
exit, and carry a non-vacuity case that plants a width class on a routed page in a
temporary copy and asserts the gate goes red naming the file.

That places the check inside pnpm test, which is the argument the duplicate gate already
makes: a check reachable only by a second command runs when somebody remembers. It
depends on AU7 because the first rule has no correct alternative until the shell owns
the column.

### LX.9 The local loop, resolved from the register (SH948)

SH895 made copy-ds.cmd forward to the design system's own use:local so the consumer and
the install path were resolved rather than hardcoded. Both checkouts have since moved to
git worktrees, and copy-ds.cmd --list now fails with could not find the design system
checkout, while use:local --list reports no consumer at all. The script's header records
the same failure one level down, where it did nothing useful for two version bumps
without anyone noticing.

VDS130 makes the register the source: each consumer declares its checkout, and zero
consumers found is a non-zero exit naming the register. Shio's side is to stop deriving
anything from position. copy-ds.cmd resolves the package through the
viglet-design-system latest junction, the same stable pointer the global instructions
use for Shio and Turing, or it is deleted if use:local alone now does the job.

The assertion is that copy-ds.cmd --list, run from this checkout, names Shio and exits
zero. A wrapper whose only job is a path it gets wrong is worth less than no wrapper,
because it reports a missing package instead of a broken script.

## LXI The live defects, fixed before anything is built on them (Block AV)

Everything else in this round adds a capability. This block removes behaviour that is
wrong today and reports itself as right, which is the most expensive kind of defect in a
product whose promise to the curator is that nothing surprises them.

Each task was confirmed by running or reading the code path rather than by the analysis
alone. An uploaded SVG or HTML file is served inline from the console's own origin with
no nosniff and no content security policy, and the upload has no type policy at all.
shio_write builds every operation through a constructor that discards when, locale,
translationOf, url and expect, so an agent asking for a scheduled publish gets an
immediate one and its concurrency guard never applies. Bulk delete swallows every
failure and reports the whole selection deleted. A Clear cache button calls an endpoint
whose entire body is a 200. Twenty-four save paths replace the server's teaching error
with the word Error. The rich-text field stores whatever arrives and the renderer emits
it unescaped.

They come first because later blocks stand on them: the assistant reports outcomes these
defects falsify, and releases schedule through the argument the MCP path drops. Each
closes with an assertion carrying its id, at the boundary it crossed.

## LXII One chrome: bento, and nothing left that names another (Block AW)

Blocks AQ and AR moved every console surface onto the bento shell and deleted the
console shell itself. What they left is the machinery of the move: a chrome switch that
still has two values, and a codebase that still explains itself in terms of both.

Measured, not remembered: shio-react's source names a chrome on 108 lines across
eighteen files, and its tests and scripts on 180 lines across twenty-two. App.tsx wraps
about twenty-five routes in SectionCardChromeProvider chrome=bento, because the design
system's context defaults to console, and EmptyState, Panel, the not-found page and the
static-file list still branch on useSectionChrome. A component rendered where no
provider is mounted, a login, setup or error page, still takes the console look. The
surface digest and the chrome census still classify every surface into an era nothing
renders, and twenty-three bento pages live in a directory named for the retired chrome.

The order is the one this project uses for any sweep: a lint that counts the vocabulary
and may only shrink, then one root provider, then the branches, the era instruments, the
names and the prose. The last task removes the provider itself, once the package drops
the switch in VDS146.

### LXII.7 The last chrome declaration, removed with the switch (SH963)

After AW2 through AW6, one reference remains by design: the provider at the root that
tells the package's components to render bento. It exists only because the package still
has a SectionChrome type with a console value and, until VDS145, a console default.

VDS146 removes the type, the provider, the hook and every console branch from the
package, once VDS144's census reads zero console consumers, which in today's
measurements waits on Dumont's own cutover. When that release lands, this task deletes
Shio's root provider and its import, raises the design-system floor to the release that
carries the removal, and moves AW1's census to an empty debt list asserted to stay
empty.

The design-system waits list from SH937 carries an entry for this line, with a probe
that reads the installed package's bento types for SectionChrome and a proof that the
read reached the bento declarations. That is what turns this dependency into a red test
on the day the release arrives, instead of a line somebody has to remember to re-read.

## LXIII The Viglet assistant: where the console reports, and how the curator reaches the agent (Block AX)

The design system's Block E shipped three pieces for exactly this product. VigletAvatar
is the Viglet mascot with five states: idle, working, success, error and attention.
VigletAssistant is the dock it lives in: collapsed, a status light with a typed caption,
and open, a transcript with a composer that exists only when the product supplies
onSend. useAssistantNotifications reads the toasts already on screen and moves the
mascot without wrapping toast. The reference design those were drawn from is a CMS
screen with the avatar wired to publish, a validation failure and an arriving lead. No
consumer has mounted them; SH940 made them installable here.

In Shio the dock replaces notifications rather than decorating them. Outcomes, agent
runs, review arrivals, failed schedules and form submissions all report through it, and
the header's pending-review count moves into it.

The chat half is constrained by a binding non-goal: Shio hosts no model. So the composer
appears only when an external agent is connected, and a curator's message is relayed to
that agent through MCP, with its answer arriving in the transcript. That relay needs the
run and intent records Block BB builds and lands there. This block builds the status
surface, ordered mount, instrument, channel, then each kind of report.

### LXIII.1 The dock, mounted as a status surface (SH964)

Shio calls toast.success fifty-two times, toast.error fifty-one, warning twice and info
once, and mounts the package's Toaster in App.tsx. With 2026.3.10 installed,
VigletAssistant and useAssistantNotifications are importable, and the hook's contract is
that it reads the live toast list rather than patching toast, so every existing call
moves the mascot without being touched.

Mount VigletAssistant once in the bento root with no onSend, which makes it a status
surface: state, caption and activity come from useAssistantNotifications, and the
transcript is closed. The mascot is decorative and aria-hidden, and the dock's caption
is an aria-live region, so a screen-reader user hears each outcome once, which is the
announcer Block BG would otherwise have to build.

It depends on VDS133 because the dock and BentoBackToTop are both fixed to the
bottom-right corner and the dock covers the button. The assertions: a toast.error moves
the dock to error with the toast's title as caption, an empty toast list returns it to
idle, reduced motion draws a still frame, and the dock's box does not intersect the
back-to-top control at mobile or desktop width.

### LXIII.2 Every write the console makes says how it ended (SH965)

The package's hook documents its own failure mode: if the mascot needed a second call
beside each toast, it would get one where somebody remembered and nowhere else, and it
would sit at idle while a red toast slid past. Once the dock is the reporting surface,
the same failure shifts one level down. A save that resolves and shows nothing, or a
catch that swallows, leaves no trace in the dock, in the aria-live caption, or in the
curator's understanding of what happened.

The instrument comes before any report is added: a lint over shio-react's services and
pages that finds each call to a mutating service method, a post, put, patch or delete,
inside a handler, and requires the handler to reach a toast or an explicit outcome
report on both its success and failure paths. Exceptions are held as an asserted list,
each with a reason, in the Reason-with-a-falsifier shape the conformance gates use.

Its first run is the worklist for AX3 and AV4, and it prints both counts. The
non-vacuity case plants a handler that awaits a delete and reports nothing, and requires
the lint to name it.

### LXIII.3 A refusal the curator can act on from the dock (SH966)

AV6 gives the console a typed problem document and a reporter. This task decides where
that report lives, and the answer is the dock rather than a toast stack: the caption
types the problem's detail, and the report row carries its actions.

The row uses VDS134's report kind with tone error and up to three actions: go to the
field named by at, apply the didYouMean correction, and open the allowed list. The toast
for the same failure is suppressed so the curator reads one message, not two, and the
aria-live caption announces the detail once. A success keeps its brief toast-driven
reaction and caption, because a success needs no action.

The post editor is the first consumer because it is where the teaching errors are
richest: an undeclared field, a missing required value, a stale expectation. The
assertions: a planted 400 carrying at and didYouMean produces a report whose first
action focuses that field and whose second applies the suggestion, and no second toast
appears. A refusal with no problem document still reports, with its HTTP status, because
the lint from AX2 does not accept silence.

### LXIII.5 What the agent did, told where the curator already looks (SH968)

The console's only ambient signal about agent work is bento-review-pending.tsx, the
count SH936 placed in the header because the rail carries no badge. BENTO-AUTHORING
section 1 names that exact case as what a header is not for, and it tells the curator a
number without telling them what happened.

With AX4's stream, the dock reports agent activity as it happens. A run that starts
moves the mascot to working, with the actor and the site in the caption. A run that ends
becomes a report, tone attention when it produced entries for review and success when it
did not, with actions to open the review queue filtered to that run and to open its
change set. An approval or a revert reports success to the curator who made it. Each
agent write in view bumps the dock's activity, so the mascot reacts to smaller events
than a state change.

The header count is removed in the same commit and the dock's unread count takes its
place, which is also what lets the tenant switcher stand alone in the header. The
assertions drive a run through the stream and read the report and its actions.

### LXIII.6 A schedule's outcome, reported to the people who set it (SH969)

ShPostScheduler sweeps every minute. When a scheduled publish or unpublish throws, it
logs a warning and records a SCHEDULE incident in ShAgentIncidentLog, which the agent's
diagnostics read; a success is a log line. The curator who set the date sees neither. So
a launch that did not happen is discovered by a customer, and one that did happen is
confirmed only by opening the page.

The scheduler emits its outcomes into the change feed as entries, published, unpublished
or failed with its problem, and AX4's stream carries them to open screens. The dock
reports a failure with tone error and actions to open the post and to retry now, and a
success with tone success naming the post and the time it went live. A curator with no
open screen sees the failure as an unread report at next login, because a failure that
only reaches a live session is still a failure nobody reads.

The agent side keeps its incident log, and shio diagnostics lists the same entries. The
assertions: a planted failing transition produces a failure report with a retry action,
and retry publishes.

### LXIII.7 The arriving lead (SH970)

The design the assistant was drawn from shows its attention state for one event above
all: a lead arriving from the site's form, typed into the caption as a name, an
organisation and a source. Shio has the event and not the report. ShFormSubmissionAPI is
a public POST that spends a CDA rate-limit token, refuses a filled honeypot, and writes
the submission as a draft post into the folder the site's form configuration names.
After that it is indistinguishable from any other draft, and nothing tells anyone.

A submission joins the change feed as its own entry kind, and the dock reports it with
tone attention, a caption built from the fields the form marks as its title and summary,
and actions to open the submission and to archive it. Reports are grouped when several
arrive together, so a campaign does not become fifty rows.

The inbox view, the per-site destination settings and what an agent can do with a
submission are a separate line in Block BI. The assertions post a submission, read the
arrival report and its caption, and confirm a refused honeypot produces no report.

### LXIII.8 What the dock reports, chosen per person (SH971)

Once AX3 to AX7 land, every kind of event reaches one surface. That is the point, and it
is also how a notification centre turns into something people learn to ignore. An author
wants their own saves and failures; a reviewer wants agent runs and review arrivals; an
operator wants schedule failures and form arrivals; a person presenting their screen
wants the mascot still.

A per-user preference, stored server-side so it follows the curator across devices,
chooses which report kinds reach the dock and whether the mascot animates, keeping the
caption and the aria-live announcement either way. Muting a kind never hides its record:
the activity trail and the agent's change feed still carry it, and a muted failure still
counts toward the unread badge unless explicitly excluded, since a failure is the report
least safe to lose.

This is a console preference with no agent equivalent, classified as such in the P1
declaration rather than argued in prose. The assertions set a preference, drive each
kind of event, and read which reports appear.

### LXIII.9 The dock in the one surface that owns the whole viewport (SH972)

SH907 routed the visual editor outside the bento shell because h-screen inside a sticky
header pushed the preview below the fold. AX1 mounts the dock in the bento root, so the
editor, where a curator changes content with every click on the page, is the one surface
with no reporting channel, and its saves either toast into nothing or say nothing.

Mount the dock in the editor with the package's inline mode, docked into the editor's
own toolbar rather than pinned over the preview iframe, so it never covers the page
being edited. Bridge saves drive it: working while a patch is in flight, success with
the field name when it lands, error with the problem document when it is refused. The
status light stays visible while the transcript is closed, which is what a person needs
while editing: confirmation without a panel.

The assertions render the editor, drive a bridge save through success and refusal, read
the dock's state and caption, and assert the preview iframe's bounding box is unchanged
by the dock's presence.

## LXIV What the conformance instruments cannot see (Block AY)

This round adds console surfaces, write paths and agent affordances by the dozen, and
the instruments that are meant to catch a broken law are keyed on things most of those
additions do not have. ShAgentP1ConformanceTest walks manifest feature keys, so a
capability with no flag is not classified. ShStatelessReachabilityLintTest matches
mutating mappings, so a read-only surface is invisible. The MCP budget test reveals its
total only inside a failure message, while tools/list sits within a few tokens of its
ceiling. And of the ten laws, P7 and P9 have no instrument at all, in a round that cites
P7 more than any other.

SH338 sets the order: when a review finds a law being broken, the instrument that finds
every other break lands before the fixes. So this block widens what the gates can see,
publishes the numbers they already compute, and adds the two missing censuses, before
the feature blocks that follow spend the headroom and the exceptions these instruments
exist to ration.

Its tasks are small, and together they turn a P1, P3, P7 or P9 argument written in a
rationale paragraph into something a build can read.

### LXIV.13 A gate nobody runs went quiet instead of red (SH1070)

Found by running the gate, not by reading anything: SH941 touched `docs/`, which is what
the docs gate covers, so it ran for the first time in a long while and came back red on
two suites at once.

`docs-stack` reports `Spring Boot 4.1.0 at developer-guide.md:44 — declared: 4.1.1`,
which is SH628's instrument doing what it shipped to do — a version in prose is a claim
nothing derives — against a published page the pom moved out from under. `docs-coverage`
reports SH653's summary disagreeing with its own tables: the sentence says 57 clusters,
56 covered, 50 green; the rows count 54, 54 and 48. Both are real, both live in the
published-docs checkout rather than here, and neither is a defect in the instrument.

What makes this a task rather than two corrections is the number beside it. `pnpm gates`
reports the docs gate **135 commits** behind, against 2 for java and 13 for conformance,
so both drifts sat in front of an instrument built to catch them while nothing ran it.
It is not among the three `agents.md` names as blocking and not in `red-suites.json`, so
it went quiet rather than red — and it is the one gate whose subject lives in a sibling
repository, which a clone may not have at all.

So: correct both drifts, then decide what stops the third. Either the docs gate joins
the blocking set with its sibling-checkout absence handled explicitly, or its staleness
is reported somewhere a commit passes.

### LXIV.14 A documentation queue that became a backlog (SH1072)

SH758's reader prints rather than fails, because the fix is in another repository and a
queue of one or two ids between a ship and the next docs pass is normal. SH799 put a
ceiling of five on that argument: past it the thing described is not a commit owing
something, it is a backlog.

The ceiling is now reached. Six ids shipped and unnamed: SH940, SH941, SH942, SH945,
SH949, SH950. Five of them are this round's; SH940 was already waiting when the round
began, which is what made the sixth the one that tipped it.

The fix is two commits in two repositories, and that is the whole reason this is a task
rather than a step in somebody's next commit. First `docs-shio/COVERAGE.md` in
`vigletdocs`, naming each id in its cluster row. Then `pnpm run coverage:mirror` here,
which copies the matrix back so the readers in this repository agree with the published
one.

Worth doing in the same pass, since the same checkout is open: the docs gate is red at
HEAD for its own reasons (SH1070), and one of those is that COVERAGE.md's summary counts
57 clusters over 54 rows. A pass that names six ids and leaves the summary disagreeing
with the tables has visited the file without fixing what it says.

`red-suites.json` carries the dated exception that licenses committing over this, and it
names this task, so the licence dies when the work does.

### LXIV.15 A rule that refuses what it teaches (SH1074)

SH893's reader refuses `aria-<state>={...}` unless the expression starts with a quote:
`aria-${state}=\{(?!\s*")([^}]*)\}`. Its failure message says to write
`aria-selected={active ? "true" : "false"}` — a form that begins with `active`, and
which the regex therefore rejects. The rule and the sentence teaching it disagree, and
the sentence is the one a reader acts on.

Nobody had noticed because the console has zero ARIA state attributes, which the message
itself says: *"this console had zero of these when the rule arrived"*. A guard over an
empty set is green whatever it means. It surfaced on the first attempt to add one —
SH955's field pin — where following the message verbatim failed the build.

BENTO-AUTHORING's accessibility section, vendored here by SH941, is the authority and it
agrees with the message rather than with the regex: **ARIA values are string literals**
— `aria-selected={active ? "true" : "false"}` is given as the correct form.

So the regex is what is wrong. It should accept a conditional whose branches are both
quoted strings, and keep refusing `{active}`, a bare `aria-hidden`, and anything else
that resolves to a boolean. The narrow version — require every branch of a ternary to be
a quoted literal — is the rule the contract actually states.

The console still has no ARIA state attribute the corrected rule would accept, so the
fix lands with a fixture rather than a corpus — and the first real one will be written
by somebody reading the message rather than the regex.

### LXIV.16 A dependency only this machine has (SH1076)

Found reading a failed CI run while checking what the IDE sync had pushed, and confirmed
from the job logs. Every `Suites` run on a push to 2026.3 since at least 2026-09-06
concludes failure, and all four jobs fail. `mvnw clean test` cannot read the POM:
`Non-resolvable import POM: com.viglet.core:viglet-core-bom:pom:2026.3.11 ... could not
find artifact ... in central`. The version was raised in `pom.xml` by SH110. On this
machine `~/.m2` holds 2026.3.11 because viglet-core is built locally, so every local
gate passes. The Java suite reported 2050 green today while CI could not start it.

This is the state SH579 says does not exist, arrived at by the route it names: a red
suite nobody mentioned. It is also worse than a red test. Maven stops before the
reactor, so CI reports no count and no failing name, and the three other jobs fail
downstream of the same missing artifact.

The fix is outside this backlog: publish viglet-core 2026.3.11, or pin the last
published version until it is. The part inside it is the instrument. A version bump that
points at an artifact the central repository does not have is detectable at the commit,
by resolving the BOM with an empty local repository, or offline against a list of
published versions. Without that check, the next bump fails the same way and is seen a
week later.

## LXV What the content said before (Block AZ)

A post in Shio has at most two rows, one draft and one published, under a composite key
of id and state. Publishing copies the draft over the live row and keeps nothing.
ShHistory records that something happened, with an action, an owner, a date, an agent
session and a 255-character description, and no content. So the question every content
team asks first, what did this page say before and who changed it, has no answer, and
neither does the curator's second question after an agent run: show me exactly what
changed, including on a page that did not exist yesterday.

The console's own save path has a second, related gap. The agent surface refuses a stale
write with expect and a 409 since SH721, while the console's PUT and PATCH take no
version at all, so a curator can silently overwrite fields an agent wrote a minute
earlier.

The block gives the product a memory in the order that makes each step safe: one
concurrency contract for both writers, a revision store captured in the publish
transaction, the reads and a rollback verb over it, a diff that works for a created
page, and audit rows that name the fields they changed. Releases, review and translation
all read what this block stores.

### LXV.4 A diff that reads a created page and a changed sentence (SH988)

The dialog picks the draft row and the published row and renders per-field cells capped
in height, so a long body is two blocks of text with nothing marking the sentence that
moved. The review queue shows its diff button only when an entry has a post id, a
published row and a draft, so a CREATE, which is what the publish gate makes an agent
produce, has no diff at all.

Answer once on the server rather than twice in the client: a diff endpoint returning
before, after and a field list, where before is null for a create and the published row
or a chosen revision otherwise, so the console and shio diff cannot disagree about what
changed. The console renders it with VDS140's BentoDiff and BentoVersionRail: any two
revisions selectable, word-level highlight inside a field, unchanged fields collapsed
behind a count, and for a create, the rendered page's structural digest and a preview
link instead of an empty column. Restore-whole and restore-one-field call AZ3's rollback
behind a confirm.

The review queue's diff then appears on every entry that still exists, inline rather
than as a modal, since a modal per entry is a click per entry on a long run. An approved
entry has no live comparison left, so it lists the names SH989 stored on its audit row
instead.

### LXV.6 A pushed change that names its fields (SH1080)

SH989 stored the changed field names on every audit row and made them readable on a page
of the feed, over REST include=fields, the shio_changes fields argument and shio changes
--fields. The pushed feed does not take the option. GET /api/v2/events opens a
subscription holding a site and a cursor, and ShEventStreamHub reads each page with
fields off, so shio changes --follow --fields is refused before connecting rather than
silently dropping the flag.

Carry it the way the polled read does. The events endpoint accepts include=fields, the
subscription record keeps the flag, and the hub passes it to
ShAgentChangesService.changes, whose rows already carry the names; withoutId copies them
across already. The CLI client's events() sends the parameter and the refusal in
changes.mjs goes. The default event stays byte-identical, asserted the way
ShAuditChangedFieldsTest asserts the polled row.

## LXVI Releases, scheduling and the publish gate (Block BA)

Publishing in Shio is one post at a time. ShPostScheduler fires each due row in its own
transaction, which is right for independent posts and wrong for a launch: forty pages
that belong together go live forty times, in sweep order, and a failure halfway leaves
half a launch. Nothing lists what is scheduled. The curator's publish button has none of
the three safeguards the agent's path has, no dry run, no impact preview and no confirm,
and there is no publish permission for a person at all, so any user who may write may
also put content live.

The block gives a launch a unit and a publish a gate, over primitives Shio already owns.
One state grammar on every read path is the substrate: a release is read as a
perspective, as a revision is. Scheduling becomes visible and reliable on every surface.
A release is a named set of post versions that publish atomically through the existing
batch runner, never a second writer, with a rollback that restores each item to its
anchor revision. Publishing gains a permission above write, a dry run with the routes it
changes, and gates declared as appliable checklists in the content tree.

The gate is a checklist, not an orchestrator; that line is drawn in the non-goals.

### LXVI.6 Publish as a permission above write (SH995)

ShAclPermission has two values, READ and WRITE, with WRITE implying READ, and the
content ACL service exposes requireRead and requireWrite. mayPublish lives on the API
token and its accessor returns true when no token is in play, which is every console
session. The review service's approve calls requireWrite. So the agent, whose work the
review queue exists to gate, is the only actor the product can stop from publishing, and
a team has no separation between author and approver.

Add PUBLISH above WRITE, so PUBLISH implies WRITE, and route every publish through
requirePublish: publish, unpublish and schedule, the release run and rollback, and
review approve. The migration is the design decision: every existing WRITE grant is
backfilled to PUBLISH in the same changeset, so nobody loses a capability on upgrade and
separation is opt-in from then on.

Per SH322 the sweep lands before the fix: a lint listing every method that publishes
without asking for PUBLISH, held as an asserted list. The console disables Publish and
Approve with the server's own fix text, and the ACL editor offers the third level. The
assertion: a write-only user is refused a publish and still allowed a save.

### LXVI.7 The curator's publish gets the agent's safeguards (SH996)

The visual editor's Publish patches with publish true and, when nothing was edited,
publishes the selected post outright; the post editor has the same shape, and its
payload has no dryRun and no confirm. The agent path carries a dry run, a confirm token,
a publish capability check and a review entry with a revertable flag. So the surface P7
was written for is the one with none of P7's instruments, and include=impact answers a
different question: template blast radius from layout bindings, not which URLs change.

Two halves in one commit. The merge verb accepts dryRun and answers with the change-set
shape the agent path returns, one contract and two callers. And impact gains a routes
block computed from the content graph without rendering: which URLs go live, which
change, which stop resolving, and how many published pages link to a URL this publish
creates or removes, reusing the inbound-link scanner so the lint and the preview cannot
disagree. It is absent when a publish affects nothing, so ordinary responses stay
byte-identical.

The console shows that list before the button arms, and a console publish writes a
review entry attributed to the curator. The assertions: a dry run writes nothing, and a
console publish appears in the queue.

### LXVI.8 Publish gates as appliable checklists in the content tree (SH997)

The address-scoped verify grades unresolved links, missing themes, head conventions and
field lint; render produces structural and appearance digests; shio audit reports
overflow and contrast per viewport. All are opt-in. The webhook vocabulary is PUBLISH,
UNPUBLISH and DELETE, dispatched after commit, and the only gate that exists checks who
may publish, never whether this content should.

Not a workflow engine: an orchestrator inside Shio would be a third contract over the
CDA and the console, which P10 forbids, and Shio's operator already is one. Instead a
site declares shio/gates/<name>.yaml, an ordered list of checks, verify, audit,
render-digest-unchanged-except and links-resolve, each with a severity, loaded from the
content tree the way blueprints are. post.publish, the console publish from BA7 and
release.run evaluate them server-side, so no surface can bypass them, and a failure
answers a teaching 409 whose fix names the failing check and the address that failed it.

shio gate run is the CI form and a publish dry run shows which gates would fail. A
no-unresolved-notes check arrives with notes in Block BB. The override and the outbound
events are BA9.

### LXVI.9 An override that leaves a record, and the events an orchestrator needs (SH998)

BA8 makes a gate refuse a publish. A gate with no sanctioned way past it is one a team
removes the day it blocks something urgent, and a waiver nobody can see is worse than no
gate.

A user holding PUBLISH, from BA6, may publish past a failing gate by supplying a reason.
The override is recorded as an audit row naming the gate, the failing check, the address
and the reason, and as a revision reason on the content, so both the Activity page and
shio changes show that a gate was waived and by whom. An agent credential cannot
override, which keeps the non-goal that an agent does not approve its own work.

The outbound edge gains GATE_FAILED and REVIEW_REQUESTED, dispatched when a gate refuses
and when a run enters review. That is the honest integration with an external
orchestrator: Shio reports, and something else decides whether to page a human, pause a
pipeline or open a ticket. The assertions: an override without a reason is refused, one
with a reason publishes and records both rows, and a failing gate emits GATE_FAILED with
the check in its payload.

### LXVI.10 The client asks for the version the CDA can serve (SH1081)

SH990 gave every CDA read a state parameter: published for any token, draft and rev:<n>
for a preview token, refused with state-needs-preview-token otherwise. The REST routes
and the GraphQL fields take it. The TypeScript client in shio-client/src/client.ts does
not: getPost, getPostByUrl, listChildren and query build their query strings from a
fixed set, and getPostByUrl omits locale too, although the route has taken it since
SH53. A front end written against the SDK, which is how a Next.js site reads Shio,
cannot build a draft preview of one page or show a revision, and cannot pin a
translation by URL.

Add an optional state to those four reads and locale to getPostByUrl, passed through
buildQuery, which already drops undefined values. Type state as a string rather than a
union, so rev:<n> needs no template-literal type and the server stays the one place that
says what is allowed. The 403 comes back through the client's existing error path; its
test should assert that the problem's slug survives. The React SDK, which wraps this
client, needs nothing beyond the new parameters.

### LXVI.11 A calendar over the Scheduled page (SH1082)

SH991 gave the console a Scheduled page: an agenda list, earliest first, each row linked
to its post, with the reader's timezone stated. The design also asked for a month and a
week calendar, and for rescheduling by dragging an entry to another day, through the
same schedule write the dialog uses.

That half is waiting on a component rather than on a decision. The console's
design-system notes list calendar as unavailable, and a calendar grid built inside Shio
would be exactly the second copy the no-duplicate-components gate refuses, since the
design system is where a bento-shaped date grid belongs and where its keyboard and
screen-reader behaviour would be checked.

When the design system ships one, the Scheduled page gains a view switch between the
list it has, a month grid and a week grid, fed by the same ShScheduleService entries.
Dropping an entry on a day keeps its time of day and calls the post-unified schedule
endpoint with the new instant, which records the dropper as the requester SH991 stores.
The zone line stays above every view. A test drives a drop and reads the change back
through the agent's scheduled listing.

### LXVI.12 The batch refuses an op field it does not know (SH1083)

SH992 made shio_write refuse an op field it does not declare, since a dropped `cancel`
would publish the page instead of standing it down. The REST batch, the other door to
the same service, still drops what it does not know. Measured on a clean instance: a dry
run of `site.upsert` carrying `cancle: true` answered 200 and planned the create, saying
nothing about the ignored field. On `post.publish` the same misspelling publishes now
and reports success.

The cause is binding rather than handling. `ShAgentBatch.Op` is a record that Jackson
fills, Spring's default mapper ignores unknown properties, and nothing between the
controller and the service looks at what arrived.

The fix refuses an unknown op field on `POST /api/v2/agent/batch` with the teaching
shape shio_write already uses: the field named, the allowed names (the `Op` components),
a did-you-mean, and a fix. It is scoped to the op object, not the mapper globally,
because other request bodies may rely on lenient binding and this change should not
decide that for them. `shio publish` and the TS client send only known fields, so no
caller breaks.

A test with the id posts a batch whose `post.publish` op carries `cancle` and asserts a
400 naming `cancel`, with the page still unpublished. ShAgentOpsContractTest's list of
declared keys is the natural source for the allowed names, so the vocabulary and the
refusal cannot disagree.

## LXVII The curator's return channel, and the conversation with the agent (Block BB)

Shio's thesis is that an agent builds and a curator approves. Today that is a one-way
street. An agent writes; a human approves the whole run or reverts the whole run; and
nothing travels back. The audit row records the agent session and both continuous
readers drop it. A run carries no statement of what it was trying to do. A rejection has
nowhere to record why. The agent cannot read the verdict on its own work, so every
correction needs a human to re-explain it out of band, and there is no place to leave a
note on the page or the field that is wrong.

This block closes the loop and gives the Viglet assistant its voice. Attribution becomes
visible, a run gains an intent, review moves from the run to the change with a reason,
and the agent can read its verdict. Notes anchored to an object, a field or a revision
become the channel, and they are also how the dock's chat works without Shio hosting a
model: a curator's message to the connected agent is a note, the agent reads it over MCP
and answers on the same thread, and the dock renders the thread as a conversation with
actions attached.

The agent still never approves its own work; the review decision stays off its
namespace.

### LXVII.1 A machine's change, told apart from a person's (SH999)

ShHistory.agent_session_id is written with every content write and read by the review
queue. The console history response is id, action, description, owner, date, object and
site, with no agent field and no subject kind, and the change feed passes the owner into
by while its javadoc still says the agent session is stamped into that string so a
caller can tell its own writes from a human's. That stopped being true when the actor
suffix was removed, and no test asserted the guarantee, which is why it drifted.

Add agent and agentName to the change row, omitted when null so a human row stays
byte-identical, with a by filter of agent, human or a session. Add agent, agentName and
subjectKind to the console history response, with agent and actor filters. Persisting
the MCP clientInfo name on initialize into a run row makes agentName a name rather than
a uuid, and that row is where BB2 hangs intent.

Fix the javadoc in the same commit and land ShAgentChangesAttributionTest: an agent
write and a human write by the same credential are distinguishable on the feed. The
console renders an agent chip in the Who column linking to the run.

### LXVII.2 A run that says what it was for (SH1000)

ShReviewSession holds session, actor, changes, counts by action, first and last seen,
sites and pending, and its detail entries add action, post, title, address, state and
date. Neither carries an intent; the rendered string is a count of changes by an actor
above a uuid. The MCP session is stateless and stores only its id. shio report composes
three reads into a narrative client-side and never reaches the console, so the curator
who does not open a terminal gets the version with no premise.

An agent declares its intent once and it rides every surface. Accept intent in the MCP
initialize clientInfo, the cheapest place because tools/list is resent every turn and
initialize is not; X-Shio-Intent on the agent surface and shio apply --intent persist it
onto BB1's run row; shio report reads the stored intent instead of reconstructing one.

The review queue leads with the intent sentence and a server-computed summary, counts by
action and site and the pages whose appearance digest moved. A run with no intent reads
no intent declared with a hint naming the argument that would have supplied it. The
intent is read once per run and never joins the per-row change payload, which is
measured.

### LXVII.3 Decisions per change, with a reason (SH1001)

The review API has two mutations, each taking a session: approve publishes every draft
the run wrote, revert undoes all of them. The console renders two buttons per run; the
entry rows carry badges and a diff button and no control. Each entry already has a
revertable flag, and the outcome DTO already reports skipped entries with reasons, so
the surface can report per-post outcomes and cannot take per-post instructions.

Move the decision from the run to the change. A decisions call takes a list of post,
decision of APPROVE, REJECT or HOLD, and note, applied atomically and reporting per
entry in the existing outcome shape; the two whole-run verbs become that call over every
entry. sh_review_decision makes HOLD and REJECT durable, since today a decision's only
record is its side effect, and the pending count subtracts held entries. Approve
requires BA6's PUBLISH.

The queue gains a CLI, shio review list, show and decide with notes, so CI can gate on
it, and a dry run showing what approve would publish and skip. The console gets
per-entry selection with range select, a required note on reject, and keyboard triage,
because this is a screen a curator lives in.

### LXVII.4 The verdict, readable by the one it is about (SH1002)

The conformance test holds the review queue as a decided exception, because an agent
approving its own run is what it prevents, and the manifest asserts the approve route is
absent from its endpoint index. That decision is right and this task does not touch it.
The gap is read-only: a revert produces ordinary restore and delete audit rows that look
like a curator's own edit, and approve produces publish rows, with no outcome, no reason
and no typed event.

BB3 writes decisions as typed rows, so the read is cheap. /agent/changes gains review
with decision, by, at and note, omitted when null so ordinary rows stay byte-identical,
and a filter to rejected changes only. shio://review/mine is a resource scoped to the
calling credential's own runs, off tools/list, and the first thing a resumed session
reads. shio review --since last prints rejections with their notes, and shio report
gains what the curator changed.

The assertions keep the decision intact: the manifest's endpoint index still lacks the
approve route, the new resource exposes no decide path, and a rejected change's note is
readable by the session that wrote it and by no other.

### LXVII.5 Notes: a thread anchored to an address is the next prompt (SH1003)

Searching the backend for a comment, mention or annotation class returns nothing. The
review queue reviews runs, not documents, so a curator who wants this paragraph fixed
has no channel and an agent has nothing to read. Site memory is the nearest thing,
scoped to an instance or a site rather than a field, and it has no console surface, so
the conventions that govern an agent are readable and writable by the agent alone.

A note anchors to an object, optionally a field path and optionally a revision: sh_note
holds body, author, agent session, a kind, resolved state, a parent for threading and
lowercase tenant_id, and its visibility reuses the content ACL, so a thread is exactly
as visible as the object it hangs on. shio_read include=notes attaches open notes;
note.add and note.resolve join the batch vocabulary; shio notes --open prints a work
list; open notes project into the content sidecars so Grep finds them. The console gets
a comment rail on the post editor anchored to fields, and a page for site memory.

Kinds make assignment and requests one mechanism rather than several: a comment, a
request to the agent, and a reply. BB8 builds the dock's conversation on them.

### LXVII.6 Every new projected field, with a rule for who wins (SH1004)

This round adds content to the file projection in several places. BA4 projects releases
and says they are three-way merged like content, the only task that states a merge. BB5
projects open notes into sidecars. Constraint blocks join post-type files in Block BC,
translation provenance joins sidecar metadata in Block BH, and asset alt, caption and
tags join the asset sidecar in Block BE.

content-base.json is the common ancestor. A field the projector writes but the merger
does not know is either clobbered on the next pull or in conflict on every pull,
depending on which side is treated as authoritative. Notes are the sharpest case, being
the one projected field a person edits locally and the server also mutates: resolving a
note in the console while the local tree holds an edited body is a conflict with no
rule.

The deliverable is the rule per new field, stated once in content-files.md: which side
wins, whether the field participates in the base, and how a conflict is reported. Each
gains a round-trip assertion in the existing coverage test. It is small and dull, and it
is what keeps P4's promise for everything this round projects.

### LXVII.7 One reader over the audit table, for both surfaces (SH1005)

The history endpoints take a limit and nothing else: no since, no cursor, and no action,
actor or site filter. The per-object and per-site reads have typed client methods with
no callers, and the console asks for two hundred rows and stops. /agent/changes over the
same table has a keyset cursor, a commit-order watermark and a measured budget. This is
P1 working and P10 not applied: the console built a second, weaker reader.

/api/v2/history gains since, before, action, actor, agent, site and subject, delegating
to the agent changes service rather than a second query set, with the same
instant-and-id cursor so the two surfaces cannot disagree about what a cursor is, and
the duplicated finders are removed rather than extended.

The console's Activity page gains filters mirrored into the URL and cursor paging, and
the post editor gains a history panel for that object, with each row linking to AZ4's
diff. That is the question every CMS is asked and the one this console cannot answer on
screen. Live updates arrive through SH967's stream, and the plan's notification inbox is
replaced by the assistant dock.

### LXVII.8 The dock's chat, relayed to the agent over notes (SH1006)

The design the dock was drawn from called a model API from the browser, and the package
deliberately left that out: the dock takes messages, busy and onSend, and knows no
backend. Shio's binding non-goal forbids hosting a model or holding provider keys. So
the conversation cannot be answered by Shio, and it does not need to be: the agent that
already drives the instance is the one to answer.

The composer's onSend writes a note of kind request, anchored to the page the curator is
on, or to the site when none is open, and attributed to the curator. The agent reads
open requests through shio_read include=notes and a shio://requests resource scoped to
its tenant, and answers with note.add as a reply on the thread. SH967's stream delivers
the reply to the dock, which renders the thread as its transcript, sets busy while a
request is unanswered, and raises unread when an answer arrives behind a collapsed dock.

The composer is shown only while an agent session has been active on the tenant within a
window, read from BB2's run rows, and otherwise the dock stays a status surface. The
assertions send a request, answer it through MCP, and read the reply in the transcript.

### LXVII.9 An answer that arrives with its change attached (SH1007)

The reference design's chat offered a suggested title with a button to use it, and
VigletAssistantMessage carries an action for exactly that. In Shio an agent answering a
request usually does more than suggest: it writes drafts, which land in the review queue
under its run. Without a link between the reply and those changes, the curator reads
that the work is done and then has to find it.

A reply note may reference the run whose changes answer it. The dock renders such a
reply with actions, up to three through VDS134's actions array: open the diff for that
run's changes to the anchored page, approve them, and reject them with a note. Approve
and reject go through BB3's decisions call and require its permissions, so the dock
never becomes a way around review. A reply that references no run is plain text.

The agent's side is one field on note.add, the run it answers with, which a session
already knows. The assertions answer a request with a run that drafts a change, then
approve it from the dock's action and read the published result and the decision row.

### LXVII.10 Awareness, not co-editing (SH1008)

AZ1 makes a conflicting save fail loudly instead of silently. That is necessary and it
arrives late: the curator discovers the collision after writing. The second editor on a
Shio page is usually a machine, and the requirement is that neither party writes blind,
not that both type into one document at once, which is why the non-goals rule out a
shared-document runtime.

A short-lived working mark is derived from writes rather than declared: an agent session
that wrote to an address within the last minute is working on it, and the mark expires
on its own, so it can never become a lock that blocks a write. SH967's stream carries
the mark. The post editor shows a presence line naming the agent and its run's intent,
and the dock bumps its activity when a mark appears on the page in view. The agent side
reads the same marks through shio_read on the address, so a session learns another
writer touched it a moment ago before planning its next write.

The assertions: an agent write shows the presence line within the stream's latency and
it clears after the window, and a curator's save while the mark is showing still goes
through AZ1's guard rather than being blocked.

## LXVIII The authoring surface, field by field (Block BC)

Almost every piece of authoring intelligence a modern CMS needs already exists in Shio's
backend and is unreachable from its editor. Verify computes required-field-empty,
image-no-alt, link-broken and dozens more; render produces a structural digest of the
page; a field-level merge verb exists and only the visual editor calls it. Meanwhile the
editor lets a required field save empty on every surface, has no dirty state, no
autosave and no unsaved-change guard, renders its only repeatable field read-only,
cannot preview what has not been saved, and stores a page body as one opaque HTML string
produced by document.execCommand.

The block works field by field. Enforcement lands on the three writers before any
console affordance, because a rule only the form knows is a rule an agent breaks. Then
the editor stops losing work, shows the intelligence the server already computes,
previews proposed values, and gains a structured body an agent can address paragraph by
paragraph. A post-type change learns to reconcile the content it governs. And a field
can ask the connected agent for a suggestion through the assistant, which is the moment
the Viglet assistant's reference design was drawn around, answered by the agent rather
than by a model Shio hosts.

### LXVIII.1 Required means required, on every writer (SH1009)

The post form renders the required marker and posts data unconditionally. On the backend
the create and update endpoints validate only the post-type name, and the batch refuses
undeclared keys but never an empty required one. The only enforcement is the verify
rule, which runs after the fact, so a draft missing its title reaches review looking
complete.

One evaluator, called from exactly three places so the surfaces cannot disagree: the
unified write service, the batch's post upsert beside its expectation check, and the
draft merge. A draft may be incomplete, since drafts are where work happens, so saving a
draft with an empty required field succeeds and returns a finding; publishing refuses
with a teaching 400 whose at is data.<field> and whose fix names it. A dry run returns
the same findings.

This task enforces required alone; BC2 adds the rest of the vocabulary through the same
evaluator. The console reads the finding onto the field through AV6's problem reader.
The assertion carries the id on all three writers: publishing a post with an empty
required field is refused by the console, REST and the batch, and saving it as a draft
is not.

### LXVIII.2 A constraint vocabulary, evaluated where required already is (SH1010)

ShPostTypeAttr carries name, label, description, widget, required, isTitle, isSummary,
ordinal, widget settings, indexed and two Turing fields. There is no min, max, pattern,
unique, default or enum, so a slug field accepts spaces, a rating accepts eleven and two
posts can share a supposedly unique code.

Add a constraints object to the field definition: minLength, maxLength, pattern with a
message, min, max, unique, default, enum, minItems, maxItems, and an advisory
recommendedLength that never fails a write. It is additive, so an absent block means no
constraints and the canonical post-type serialisation is unchanged for every existing
type. BC1's evaluator gains the kinds, so the three writers stay in agreement.

MCP first: a violating publish is refused with at and a fix naming the constraint, and a
dry run returns violations as findings. shio verify gains constraint-violated and shio
push --check fails on it, so a content tree is provably valid before it is applied. The
post-type editor renders a constraints section per field, and the post form shows the
constraint as help text rather than discovering it on save.

### LXVIII.3 An editor that does not lose work (SH1011)

The editor holds data in useState and never compares it with the loaded post; the page
hero accepts dirty and the editor passes only titleMissing, while the folder and site
pages pass it. No useBlocker or beforeunload exists in the console. The design system
renders an Unsaved changes cue when dirty is true, built and unreachable on the one page
that needs it. The merge verb, whose rule is that a field not named keeps its value, is
called only by the visual editor.

Three layers, cheapest first. Derive dirty from a canonical serialisation of the data
against the loaded post, with stable key order, and pass it, which lights the cue; add a
router blocker and a beforeunload guard. Then a debounced patch of only the changed
keys, sending the ETag from SH985 so autosave cannot multiply the race it would
otherwise hide, with the save bar reading saved a few seconds ago. Then a recovery
buffer in IndexedDB keyed by post id, offered explicitly and never restored silently.

The CLI gains shio post set for a single field. The assertions: navigating away from a
dirty form asks, and an autosave with a stale ETag is refused.

### LXVIII.4 The repeatable field, editable again (SH1012)

The relator widget's own comment says inline editing of relator subtrees is not wired
through the unified form and that modifying children is intentionally disabled, and it
tells the curator so. It destructures only attr, so its onChange is never called. The
console-ui doc still lists Relator as a nested items list with add and remove, a
capability lost in the unified-model migration and never reconciled.

This is the rare case where the whole chain is complete and only the console leg is
missing: nested items convert to and from data in full, post.upsert writes them, and the
Markdown projection carries them. Rebuild the widget as an array editor: add with the
child schema rendered inline, remove with undo, duplicate, collapse and expand with a
title from the child marked isTitle, and reorder by drag with a keyboard equivalent,
writing ordinals on drop. dnd-kit is already a dependency, used only to reorder a
model's fields.

Fix the doc row in the same commit, and add the guard that would have caught the drift:
every widget the documentation calls editable must call onChange. BC2's minItems and
maxItems then apply to it.

### LXVIII.5 Insights beside the form (SH1013)

A grep of shio-react/src for the agent namespace returns two i18n strings describing a
token scope, and no call. The address-scoped verify returns required-field-empty,
dangling-reference, image-no-alt, link-broken, duplicate-url and the head conventions,
each with an address and a fix, and render returns the heading outline, landmarks, link
and image inventory and word count. A logged-in curator's session can already reach that
namespace. A content-quality engine is one service file away from the person editing.

An Insights panel beside the form, grouped as Blockers, Suggestions and Page shape:
outline, word count, reading time, link counts and images missing alt. Each finding
carrying a field anchors to it and focuses the field when clicked, which is why SH955's
problem reader lands first. It runs on save and on demand, never per keystroke, since P3
applies to HTTP too.

Two honesty changes on the backend: verify accepts state=DRAFT so the panel lints what
is being edited, and every content finding carries its field name. This also answers the
SEO request without a second SEO engine. The assertion plants an image with no alt and
reads the finding anchored to its field.

### LXVIII.6 Render what would be, before writing it (SH1014)

The editor mints a preview token for a post id and opens it; the preview route renders
persisted state. Nothing renders from proposed values, and the Universal Editor starts
from a site URL rather than from the form. So a curator cannot see a change without
saving it, and an agent cannot either: a dry run proves a write is legal and says
nothing about how the page will look.

Render-from-proposed-data serves both. shio_digest accepts a data override and returns
the digest of the page as it would render. A preview render endpoint takes an address
and data and renders through the existing page render service without persisting,
guarded by the same ACL as the write so it cannot show content the caller could not
author. shio preview renders a local Markdown file against the instance's templates, the
missing half of content as files.

The console gets a split view with a persisted ratio, form on one side and a live frame
on the other, re-rendered on a debounce, with device-width toggles, keeping the token
preview for sharing. The assertion is that the overlay path writes nothing: no trash, no
webhook, no indexing and no revision.

### LXVIII.7 A block content model, stored and addressable (SH1015)

The rich-text widget stores innerHTML, and on the files side that body is the whole
Markdown document, so P4's promise that Edit and Grep are the agent's cheapest tools
lands an agent inside a single unstructured string. Four laws argue for structure. P4:
blocks serialise to Markdown with real headings, so an Edit targets a paragraph. P5: a
block array has a byte-identical canonical form where innerHTML does not. P9: the
digest's outline becomes exact rather than parsed. P6: an unknown block is a teaching
error listing the registered ones.

This task lands the model on the agent side: a new widget kind storing an ordered array
of typed blocks with stable keys and inline marks, covering heading, paragraph, list,
quote, code, image, embed and reference, plus post-type-scoped custom blocks.
post.upsert accepts blocks, shio_read returns them, and the Markdown projection gains a
round-trip guard in the existing coverage test. The HTML widget stays for imported
bodies, since shio convert produces HTML, and SH956's sanitizer already guards that
path.

BC8 delivers blocks, and BC9 gives curators an editor for them.

### LXVIII.8 Blocks delivered, with a rendered view beside them (SH1016)

BC7 stores a body as typed blocks. Delivery still thinks of a body as a string, which is
the one shape a headless front end cannot style with its own components: the CDA returns
field values, GraphQL types a body as a string, and the TypeScript client has no block
type.

The CDA returns blocks for a block field together with a rendered HTML view, so an
existing front end keeps working and a new one can map each block type to a component.
GraphQL gains a Block union over the registered types, and the shio-client types gain a
Block discriminated union with a renderer helper in the React SDK that takes a component
per block type and falls back to the rendered HTML for types it does not know.

The rendered view is produced once, at write, so delivery pays nothing per request. The
response budget gains a row for a block body, measured before this lands. The assertions
deliver a body with each built-in block type and render it through the React helper, and
deliver the same body to a client that asks only for HTML.

### LXVIII.9 The block editor (SH1017)

The current rich-text widget calls document.execCommand for bold, italic, lists, link
and code, its link tool is prompt(), and it has no paste handling. BC7 gives the body a
model and nothing to edit it with.

The block widget renders each block as its own editable region with a handle: a slash
menu inserts a block of any registered type, the handle drags it, and a keyboard chord
moves it up or down. Inline marks come from a small floating toolbar, links open an
inline form rather than prompt(), and paste converts clipboard HTML into blocks through
the same allow-list the sanitizer uses, so a paste from a word processor becomes
paragraphs and headings instead of spans. Image and reference blocks open the existing
content browser dialog.

It meets BG's accessibility rules from the start: every block region is labelled with
its type and position, moves are announced, and every mouse gesture has a keyboard path.
Undo covers block moves as well as text. The assertions insert, move and delete blocks
by keyboard alone, paste a document with headings, and compare the stored array with the
expected canonical form.

### LXVIII.10 A model change reconciled with the content it governs (SH1018)

BC2 protects every row written after it and says nothing about rows already stored: a
post type with a new required field has an instance full of content that violates it,
discovered only when each is published. The hole is older than constraints. Post-type
rename and field removal exist, and a post's data is a JSON object keyed by field name,
so a removed or renamed field leaves values under a key nothing declares. shio verify
reports orphan posts and folders and no orphan field.

Per SH322 the measurement comes first: shio post-type check reports, over existing
content, how many rows violate each declared constraint and how many carry undeclared
keys, and that number argues for the rest. Then post-type.migrate joins the batch
vocabulary with rename, drop and default mappings, running through the one writer,
planned, dry-run and confirm-tokened like every other batch.

A stale-field verify finding and a per-field count of violating posts in the post-type
editor close the loop. A model change that cannot be applied to its content is what
makes a team stop changing the model.

### LXVIII.11 Ask the agent, from the field (SH1019)

The design VigletAssistant was drawn from shows its most useful gesture inside the
editor: the curator asks for a title, and the answer arrives with a button that writes
it into the field. Shio can offer that without hosting a model, because SH1006 relays a
curator's request to the connected agent as a note and SH1007 lets an answer carry
actions.

Each text-like field in the post form gains an ask-the-agent control, shown only while
an agent session is active. It opens a short prompt prefilled with the field's label and
posts a request note anchored to the post and that field path. The agent reads the
anchored field, its constraints from BC2 and the page, and replies with a suggested
value. The dock renders the reply with an action to apply it, which writes into the form
as an unsaved edit, so it passes through the same dirty state, validation and review as
typing it would.

The agent's leg is the existing note vocabulary, with a suggestion field on a reply. The
assertions ask for a title, answer through MCP with a suggestion, apply it from the
dock, and read the form as dirty with the suggested value.

## LXIX Finding, selecting and acting on content (Block BD)

The content browser is where a curator spends the day, and it is the console's weakest
working surface. The server already pages, sorts, full-text searches and refuses an
unknown sort with a teaching error; the browser reaches almost none of it. There is no
filter by type, state, locale, date or author on any surface, including the agent's own
find. Nothing is deep-linkable. Three view-mode buttons render the same list and nothing
is virtualised. Every row action lives behind hover-only CSS with no keyboard path.
Console search calls the published-only delivery API, so a reviewer cannot find the
drafts an agent just wrote. And there is no bulk publish anywhere, while the MCP publish
tool already takes many addresses atomically.

The block builds one query vocabulary that the MCP tools, the CLI, REST and the console
all speak, puts every list's state in its URL so a filtered view is a link an agent can
hand a person, and makes the browser a keyboard-operable, virtualised table whose bulk
verbs are the agent's verbs wearing a console shape. The command palette stops listing
seventeen routes and starts finding content and running actions compiled from the same
operation registry the agent reads.

### LXIX.1 A list you can operate without a mouse (SH1020)

The analysis found no onKeyDown, tabIndex, row role or aria-selected in the browser. Its
row actions, edit, clone, copy, permissions, delete, move and view, are buttons inside a
div that is hidden until a pointer hovers the row. The site list fixed exactly this in
its own sweep, moving four mouse-only actions into a focusable, announced menu; the
browser was not part of that sweep, and BentoActionsMenu is used in Shio only by the
vocabulary specimen.

Make the list a real listbox: arrow keys move a roving focus ring, Space toggles
selection, Shift with an arrow or a click extends a range, Enter opens, Delete trashes
through the confirm flow, and slash focuses search. Rows gain a role, aria-selected and
roving tabindex, and the hover block becomes the actions menu, with each item carrying
the op id AY7's declaration names. The bindings register with the shortcuts dialog.

Range selection is what makes BD2's bulk verbs a gesture rather than hundreds of clicks,
which is why this lands first. VDS138's data table later absorbs the behaviour for every
list. The assertions select a range and trash it by keyboard alone.

### LXIX.2 Bulk verbs that are the agent's verbs (SH1021)

The MCP publish tool declares its address as an array and promises atomicity across
everything passed. The browser's batch menu offers copy, move and delete, REST offers
single-id publish and unpublish, and the only bulk-shaped endpoints in the backend are
object copy and move. So the agent half shipped and the curator half did not.

Compose rather than fork. A bulk endpoint on post-unified takes an op, ids or addresses,
dryRun, confirm and when, and delegates to the agent batch service, the same writer, so
the two surfaces cannot disagree about what publish means. It returns a result per item
with its outcome and problem, never a bare 200, and a destructive op requires the
confirm token from its own dry run. Publish goes through SH995's permission. The CLI's
publish grows a query form.

The browser's batch menu gains Publish, Unpublish, Schedule and a Delete that replaces
SH953's reporting loop with one call. The dry-run plan renders as a confirmation sheet
naming what will change, with refusals grouped by problem title and each carrying its
fix. The assertion: a selection where one item is refused publishes the rest and names
the refusal.

### LXIX.3 One query vocabulary, four surfaces (SH1022)

The console listing takes page, size, sort and q. /agent/find is richer and still thin,
with site, type, folder, q, trash, limit, ids and after but no state, date range, locale
or author, and it loads matches into a list and filters in memory, sorting by address,
the defect a benchmark already measured and fixed for the file listing. The post rows
already store owner, modifier and publisher, so an author filter needs no new column,
only a predicate.

ShContentQuery parses site, folder, subtree, type, state, locale, modifiedSince,
modifiedBefore, author, modifier, q, sort, page and size from parameters, with a
teaching 400 naming allowed on an unknown key, the pattern the unknown-sort refusal
already sets. The benchmark reporting rows fetched lands first and states what the
in-memory path costs today; then the predicate moves into the database, because a
client-side filter over a paged listing filters one page.

shio_find's schema gains the fields within SH977's reservation, shio find gains the same
flags, and the console listing accepts them. The assertions ask the same question
through all four surfaces and compare the address sets.

### LXIX.4 Every list's state in its URL (SH1023)

useSearchParams appears in two console files, neither of them a list: one is the visual
editor and one reads a post-login redirect. The search page keeps its query in state, so
the search URL is the same whatever was searched, and the browser keeps sort, view and
search the same way. This breaks P5 where a person notices: the same address does not
produce the same view, and the view on screen has no address.

A typed hook reads and writes list state through the URL, debouncing text fields and
replacing rather than pushing on keystrokes so Back does not walk one character at a
time. It applies to the browser, search, trash, static files, activity and review. The
URL parameter names match BD3's REST names exactly, which is the point: a link a curator
pastes into a chat is a request an agent can run, and shio find can print the console
URL for its own result set.

Saved views then cost a stored URL and a name, not a schema. The assertion is one per
list route: its state round-trips through the URL and a reload reproduces the rows.

### LXIX.5 The filter bar over the shared vocabulary (SH1024)

BD3 makes the questions answerable and BD4 makes a view addressable. What a curator
still lacks is a way to ask without editing a URL.

The browser, search, trash and the review queue render VDS139's BentoFilterBar over
BD3's vocabulary: a free-text field, facets for type, state and locale as menus of the
values present in scope, a date range for modified, and author as a picker over users
with an agent option that filters to agent-written content through SH999's attribution.
Active filters render as removable chips and mirror into the URL through BD4's hook, so
a filtered view is shareable the moment it is built. Facet counts come from the listing
response rather than a second request.

A saved view is a named stored URL, per user with an option to share with the tenant,
listed beside the bar. The assertions build a filter from the bar, read the URL, reload
it into an identical result set, and confirm the facet counts equal the filtered totals.

### LXIX.6 The browser's cost, measured, then paged at the server (SH1025)

The browser's list accumulates: each page appends to the previous array, and every row
carries a checkbox, badges and hidden buttons, so ten thousand rows become well over a
hundred thousand nodes. On the folder half the navigation service returns every child
folder on every page by documented design, and the browser sorts that whole set
client-side. @tanstack/react-table is declared and imported nowhere, and no
virtualisation library is present.

Measure first, as agents.md requires of any cost: a benchmark over a fixture of ten
thousand posts and five hundred folders records rows fetched, response bytes and time to
first row for the current path, checked in beside the other budgets. Then the backend
half: the listing gains optional folder paging and a totalFolders count beside
totalPosts, with the envelope assertion that keeps it identical to the CDA's extended to
the new fields. The browser requests pages instead of accumulating, and the numbers are
re-taken.

BD7 builds the views and virtualisation on top of a listing that no longer returns
everything. The assertion is the benchmark's before-and-after pair, recorded in the
commit.

### LXIX.7 Grid, table and list views, virtualised (SH1026)

A grep of the browser for viewMode returns its declaration and the three buttons' own
class and click handlers; the render below them is one unconditional map, so the choice
changes a button's colour and nothing else.

Build the two missing views over one data hook. Grid renders cards with a thumbnail from
the existing image transform pipeline, or a post-type-coloured placeholder. Table
renders VDS138's BentoDataTable with a column picker persisted per user and inline
editing of scalar fields, which goes through the merge verb with the post's ETag. All
three views are virtualised. The 965-line page is extracted into a content-browser
directory with one renderer per view, because at that size the next feature makes it
unreviewable, and the chosen view moves into the URL through BD4 instead of being
forgotten on navigation.

The declared table dependency either earns its place here or leaves the manifest. The
assertions render each view over BD6's fixture, confirm only the visible window of rows
is mounted, and edit a scalar cell whose stale ETag is refused.

### LXIX.8 Console search over the agent's find (SH1027)

The search page calls the CDA search endpoint, whose state constant is PUBLISHED; a test
pins that behaviour by name. The agent's read searches both states. So an agent writing
drafts can find them and the curator reviewing those drafts cannot, and the first thing
a curator does after an agent run is search for what it made. Two defects ride along:
the CDA applies no content-ACL check, so on an instance with an ACL set the console can
list titles from folders the user cannot open, and the folder browser's search matches
direct children only while the agent's folder scope is recursive.

One console search service composed over the agent's find, a shape rather than a third
contract, using BD3's vocabulary with state defaulting to any and the trash flag its
REST twin has. It applies the content ACL and returns a withheld count, so a curator is
told results were hidden rather than shown a short list, and a scope selector offers
this folder, this folder and below, and this site.

The lint that lands first enumerates every console-reachable read that skips the ACL
check, held as an asserted list. The assertion: a draft is found, and an unreadable
folder's post is withheld and counted.

### LXIX.9 A palette of content and actions, compiled from the agent's verbs (SH1028)

Shio passes BentoCommandPalette its seventeen-entry nav declaration, every entry a list
route, and the placeholder is honest about it: search areas. Finding a post is open the
palette, go to search, type the term again. VDS123 made the palette accept a product's
own results, so the remaining gap is Shio's.

Three result groups. Actions, compiled from the operation registry AY7's surface
declaration exposes, so the palette's commands are the MCP verbs in a console shape
rather than a fourth list: publish this page, create a post here, apply a blueprint, run
verify, open the review queue, ask the agent. Content, from BD8's search, debounced and
cancellable, each row carrying address, type and a draft or published marker.
Destinations, today's array. Matching is fuzzy with aliases and recency, and page
actions rank first when a page is open.

Any action that writes opens its dry run or confirm flow rather than firing from the
palette, enforced by a confirm field on the command type. The shortcuts sheet takes the
bound keymap as data, which removes the advertised question-mark row nothing binds and
adds the slash Shio does bind.

## LXX Media: what the bytes mean (Block BE)

Shio is strong on the bytes of an asset: an on-the-fly image transform pipeline,
content-addressed uploads, a srcset that never upscales, and assets projected to disk
for an agent to read. It is weak on what the bytes mean. The media listing is unpaged
and parses every row's JSON repeatedly before it filters. Alt text is optional, and the
image helper emits an empty alt that makes the accessibility rule unable to fire. Asset
metadata and usage have no agent reach and no manifest feature key, so the P1 census
cannot even see the gap. Where is this used is an unindexable LIKE scan, answerable in
one direction only. And replacing an asset's bytes exists in the API and not in the
console, whose only route is delete and re-upload, which breaks every field pointing at
the old id.

The security defect that belongs here, uploads served inline and executable, was
promoted to Block AV and is fixed first. This block follows the measure-first rule
twice, because both of its scaling changes, the paged listing and the reverse index, are
argued in the analysis and have not yet been measured on this instance.

### LXX.1 The listing's cost, measured, then parsed once (SH1029)

GET on the static-file listing takes folder, site, category and q, and no page or size.
The service maps every row through an item mapper and only then filters, and that mapper
reads the same data string once per key, file name, width, height, alt, caption, tags,
hash and size, and makes two filesystem calls. So a tenant with thousands of assets pays
tens of thousands of JSON parses and thousands of syscalls in one request, and the
console calls it with no filter and then filters and counts in the browser.

The measurement is the first deliverable: a benchmark reporting parses, syscalls and
response bytes at a hundred, a thousand and five thousand assets, checked in. Then the
refactor with no contract change: parse the data once per item and read every key off
that one tree, and take the file size from the stored metadata instead of the disk where
it is present.

BE2 pages the endpoint. This task's assertion is the benchmark's before-and-after pair
and an unchanged response body for the seeded sample site, byte for byte.

### LXX.2 A paged media library on every surface (SH1030)

After BE1 the listing is cheaper per row and still returns every row. The object listing
has been paged for many blocks; this one never was, so the console downloads the
library, filters by query and category client-side, and computes its chip counts over
the full array.

Page the endpoint with the envelope the object listing already uses, so the two cannot
disagree about what a page is, and push category and query predicates into the query.
That needs the content type stamped at upload, the way the hash already is, with the
name probe kept as the fallback for rows stamped before this change. Facet counts come
back in the response.

shio_assets gains a cursor beside its limit, so an agent can walk a large library
instead of stopping at a silent truncation, and the CLI's asset inventory pages rather
than assuming one response. The console drives the fetch from filter state through BD4's
URL hook, reads facets from the response, and virtualises the grid. The benchmark is
re-run and its numbers recorded, and the assertions page through a fixture with each
surface and compare the totals.

### LXX.3 Alt text as a publishing fact (SH1031)

Three facts compose the defect. The File post type declares ALT as not required. The
image helper puts an empty string when ALT is null, so the attribute is always present.
And the verify rule fires only when alt is absent, a distinction the HTML outline reader
documents as deliberate, since a missing alt and an explicitly empty one mean different
things. So on a site built with the recommended helper, image-no-alt is structurally
unreachable, and nothing looks at a File post's own ALT, because the check runs on
rendered HTML.

Add asset-no-alt, a rule over File posts whose type is an image and whose ALT is blank,
provable with no render, which makes it cheap enough to be on by default. The helper
stops fabricating the empty attribute and takes decorative as an explicit opt-in, so
decoration is a recorded decision. It is a publish check, not an upload refusal:
uploading without alt is fine, publishing a page that references such an image raises
the finding, because refusing uploads would break every capture and conversion run.

The console gets a needs-alt filter chip and an inline alt field on each card, so fixing
fifty is fifty keystrokes.

### LXX.4 Asset metadata on the agent surface, and a key the census can read (SH1032)

The meta and usage endpoints exist with no agent reach. The assets tool's javadoc says
it is read-only, the op vocabulary has no op touching an asset, the CLI has no asset
metadata verb, and the manifest indexes only the upload route from that controller. Most
importantly, the P1 conformance test classifies manifest feature keys and the only media
keys are image transforms and signed URLs, so the instrument built for this gap cannot
see it.

Close the hole in the instrument first, then the chain. Declare assetMetadata and
assetUsage as features, which alone turns an invisible gap into a failing build. Then
give them reach: an asset.meta op taking an id plus alt, caption, tags and focal point,
going through the file service so the one-writer lint stays satisfied, and a usage
argument on the assets tool, within its AY5 reservation, so where is this used is a flag
on a call the agent already makes. Metadata lands in the asset sidecar and round-trips
through pull and push under SH1004's merge rule.

Metadata edits start writing an audit row, which replace already does, so they reach the
change feed and review.

### LXX.5 What the usage query costs before anything replaces it (SH1033)

The usage query is a LIKE with a leading and trailing wildcard around the file id over
the data column of every post in the tenant, which no index can serve, and the service
runs it once per state, so every detail dialog open pays for it twice. The inverse
question, which assets nothing references, cannot be answered without running it once
per asset.

The analysis proposed a reverse-reference table maintained on every write. That may be
right, and it is exactly the kind of change agents.md says lands as a benchmark first: a
maintained row on every create, update, publish and delete is a hot-path cost paid to
make a dialog fast, and whether it is worth paying depends on numbers nobody has taken.

Time the usage query at a thousand, ten thousand and fifty thousand posts on each
supported database, and time, separately, the per-write cost an index would add, using a
prototype of the maintenance step. Record both beside the other budgets. If the read is
acceptable at the largest size, the honest change is a single-state query and a cached
count, and BE6 is retired with that reason. If it is not, the numbers become BE6's
argument and its changeset's comment.

### LXX.6 Usage in both directions, at the cost BE5 measured (SH1034)

The verify inventory has orphan rules for posts and folders and nothing for assets, and
the content-files doc records that the projection side-steps the question, which leaves
it open on the instance, where storage is actually consumed.

This task builds whichever mechanism BE5's numbers chose. If the measurement argued for
an index: sh_object_reference with from-post, to-object, field name and state,
maintained by the unified write service by walking each post's data against its post
type's File and content-select fields, which is more accurate than a substring because
it knows which field holds the reference. Usage becomes an indexed join and unused
becomes a left join that is null, with the LIKE query kept as the fallback for a tenant
whose backfill has not run. If it argued against, a single-state query with a cached
per-asset count invalidated on write.

Either way: shio_assets reports a usage count per row, shio verify gains asset-unused,
shio assets prune --dry-run names what it would free, and the media card shows a usage
badge so nobody deletes an asset blind. A schema change runs with the ITs and Docker
started.

### LXX.7 Replace, with the diff that makes it safe (SH1035)

The replace endpoint shipped with a CLI caller through push --content, and the console's
static-file service and page never call it. The detail dialog offers copy URL, download,
delete and save metadata, and no way to put new bytes behind the same id, so updating a
logo means delete and re-upload, which breaks every field that referenced it, the
outcome the replace verb's own javadoc calls unsound. Separately, the File widget
refuses when its folder setting is missing, with an error toast and no path forward.

A Replace control in the dialog calls the existing endpoint and shows what a curator
needs to trust it: old and new dimensions and size, and the count of pages that will
change, from BE4's usage argument. The previous bytes are kept as a numbered rendition
with a revert, which is what makes replacing safe enough to encourage. When the widget
has no configured folder it opens the folder picker instead of dead-ending, remembering
the choice for the session.

Replace stays off MCP tools, since base64 through a tool call is the reasoning the
assets tool already gives; the manifest names the multipart route beside upload. The
assertions replace an image, read the new bytes behind the old id, and revert.

## LXXI The visual editor becomes a composer (Block BF)

Shio's Universal Editor is a bridge of five postMessage messages, and every one concerns
a field that is already on the page. There is no component, no container, no insertion,
no move and no version in its vocabulary. Edit mode does not survive clicking a link
inside the preview, while the console's connection badge stays green. Every field
becomes contenteditable HTML whatever widget declared it. There is no undo, no discard,
and a refresh leaves the pending change set pointing at edits no longer on screen. And
the only previewable sites are those with an external front end, because the preview
token unlocks nothing for Shio's own renderer.

The model the editor needs already exists and is already agent-addressable: a Page names
its sections in order, the section vocabulary is a shared package, and post.upsert
writes a page's section array today. So this block builds the surface, not a model. The
bridge learns navigation and a versioned protocol with capabilities, sections become
selectable and movable, a server-owned catalogue says what may be inserted where, a good
block becomes a reusable pattern file, edits gain an undo log, and a stakeholder can be
sent an expiring link to a draft rendered by Shio itself.

Breakpoints stay tokens on a Theme; per-element overrides are out of scope.

### LXXI.1 A bridge that survives navigation (SH1036)

Server side, the link rewriter returns a prefix plus the site path: the link's own query
survives and the current request's editor and state parameters do not, although the
helper that preserves them exists and is called only on the redirect path. Browser side,
the click handler assigns base plus href and loses them the same way. Console side,
bridge state derives from the handshake's src equalling React state, which in-frame
navigation cannot change. So the next page, which ships no bridge script, leaves the
badge green while the selection and pending changes still point at the previous page's
posts, and Save still offers to write them.

The rewriter threads the renderer's parameters onto every href through the existing
helper, and the page script does the same on intercepted navigation. A navigated message
is posted from attach on every load, so the console re-keys its handshake, clears the
selection and re-fetches labels, and a navigate message lets the console drive the
frame. The badge keys on the page the bridge reports.

The postMessage suite asserts only that the intercepted click was prevented, never where
it went. It gains a destination assertion, and the live browser probe gains a
click-through step asserting the navigated message arrives.

### LXXI.2 Protocol version two, and fields that know their widget (SH1037)

The ready message is a post id and a URL, and the console's state machine past it is
waiting, ready or absent. Edit mode is granted by the presence of a prop attribute
alone: any element carrying one becomes contenteditable and reports innerHTML, with no
reference to the declared widget. So a plain Text field absorbs markup from a paste and
then shows the tags as visible text, because the template escapes it.

Protocol v2, negotiated. Ready carries a protocol version, a capability list, the post
id, the post type and the URL, and the console renders only the affordances the page
claims, so an external front end can implement part of the protocol and still work. The
renderer emits data-shio-prop-type from the declared widget, which is resolved at
compose time: a text field becomes plaintext-only and strips formatting on paste, a
rich-text field gets a restricted inline toolbar, and anything else edits in the panel
only. The attribute joins the asserted DOM contract table.

Sanitising is not the bridge's job: SH956 already applies the allow-list at the write
boundary, the draft merge included. The console panel renders the declared widget from
the existing widget map instead of a bare input.

### LXXI.3 Sections as objects on the page (SH1038)

The bridge listens on two prop attributes and reports an item type of text or attr. The
model is already there: a Page names its sections by address in order, every section
field is annotated, and a per-section handle is emitted in converted markup and read by
the server's region walk, so the handle exists, the server reads it and the browser
bridge does not. The properties panel has no component header, no move control and no
delete.

Add a component item type. The region composer emits the section type, its index in the
owning Page's ordered list, and a container attribute on the element composing them, so
the DOM records position as well as identity. The bridge gains a component-selected
message and accepts move, remove and duplicate for optimistic reorder, each confirmed by
a write, and every one resolves to a single deterministic write: replace the owning
Page's section array.

The agent can already do this, since post.upsert writes that array and the file
projection carries it as a diffable line, so no new write path is needed. The overlay
becomes a console-drawn layer instead of a stylesheet the page injects, because a
stylesheet cannot draw a toolbar or an insertion caret.

### LXXI.4 A server-owned catalogue of what fits where (SH1039)

The editable endpoint returns the fields drawn and the regions declared, with no
insertable set, no per-container allow-list and no catalogue. The shared section types
reach the console as post-type names in a picker. So an editor that BF3 lets select and
move a section still cannot add one, which leaves that task half a feature.

A Handlebars renderer is the right owner for the catalogue, because only the template
knows what a container accepts. An insertable endpoint returns, per container on the
page, the section types it accepts, with title, description, the fields the properties
panel will draw and a thumbnail reference. Restriction rides on the container as an
accepts attribute the template declares. Insert is a batch of two operations, upsert the
new section and upsert the Page's array, which is already atomic.

The console gets an inserter rail showing the catalogue for the selected container, with
thumbnails rendered by the preview route against a fixture so no browser runs in the
JVM, and an insertion caret between sections. MCP gains the catalogue as an include on
the editable read. The assertions insert a section into a container that accepts it, and
are refused, with the allowed list, for one that does not.

### LXXI.5 Patterns: a good block becomes a file (SH1040)

Every blueprint today is whole-site or whole-page, with a fixed target and no mode that
inserts into an existing page, and nothing captures an existing arrangement as a
blueprint. So when a curator gets a pricing block right, the only way to reuse it is to
rebuild it.

shio_apply gains at and mode insert, applying a blueprint's sections into a named
container of an existing page through BF4's insert batch. shio capture with an
as-pattern name writes shio/patterns/<name>.json from a selected set of sections, with
their field values made parameters where the author marks them, diffable and reviewable
in a pull request, the shape Gutenberg reached by registering patterns as files.
Patterns join the blueprint registry, so an agent lists and applies them the way it does
blueprints.

The console's inserter rail from BF4 gains a Patterns tab beside section types, and a
selected set of sections gains Save as pattern, which writes through the same capture
path. The assertions capture a two-section pattern from one page, apply it into another
page's container through MCP, and compare the resulting section arrays.

### LXXI.6 An edit log with undo, and a refresh that tells the truth (SH1041)

The pending map has two operations, set and clear on success. There is no per-field
revert, no discard-all and no keyboard undo. Refresh reloads the iframe and resets the
handshake and deliberately keeps the changes, so the badge counts edits no longer
visible and Save still writes them. Leaving the route is unguarded. Nothing records the
value that was there before an edit, so nothing can undo one.

The pending map becomes an ordered change log. Capturing the prior value on the first
content-changed message for a key is enough, since the message already exists and only
the record is missing. A control on each row reverts one field by posting the original
value into the frame, Discard all reverts everything, and Ctrl+Z walks the log. Refresh
replays pending changes into the reloaded frame or asks, and never silently diverges.
Leaving with pending changes asks.

The log serialises into the change-set shape the agent path already uses, and a save
closes its loop by returning the structural and appearance digest pair with a one-line
delta, measured first, reported through SH972's dock. The assertions edit, undo, refresh
and save, and read the digest delta.

### LXXI.7 A share link that unlocks Shio's own renderer (SH1042)

The preview path sits under the authenticated rule and was deliberately removed from
permitAll. The token machinery serves only an external front end: when a site's front
end is internal, the mint returns a null token with zero expiry, and its javadoc
explains why, since a credential for a console-authenticated path would unlock nothing.
That held while the only reader was a curator. For a site an agent just built, it means
the only previewable sites are those with a deployed external front end, the inverse of
the product's premise.

The preview route accepts a share credential: preview scope, one site, a post, folder,
site or release, and an expiry, which the token row already models with its ephemeral
flag, expiry and site allow-list. It is exchanged once for a cookie so the credential
does not travel in a copied URL's referer, and it grants anonymous, read-only, draft,
no-store and noindex access, never the editor bridge, with the preview banner kept.

A list-and-revoke endpoint makes active links visible and killable. shio share prints
the URL, the most useful thing an agent can hand a person at the end of a run. The
assertion: an anonymous request with no credential is still refused.

## LXXII The console as an application: cost, resilience and access (Block BG)

Shio measures what an agent costs to the token, per endpoint, with the reasoning for
every raise written beside the number, and measures nothing about what its own console
costs a person. There is no bundle budget. The lazy GraphiQL split is defeated, so an
editor nobody opened loads on every route. There is no query cache and no request
cancellation, the console API sends no ETag, one error boundary guards the whole
application, and a failed fetch leaves a spinner forever. The dashboard downloads two
whole collections to show two numbers. The accent fails AA, the primary token is never
claimed, every post-editor field is an unnamed edit box to a screen reader, and no
accessibility check runs against a rendered page in the console or in the content Shio
delivers.

The block follows agents.md's rule for any cost: a measurement first, then what the
number argues for. The bundle gate's seed is taken before the feature blocks add console
code, so their additions are visible as raises rather than folded into a baseline.
Resilience and access follow, and the assistant dock from Block AX already carries the
announcements this block would otherwise have to build.

### LXXII.1 A bundle budget, seeded before this round adds code (SH1043)

The design system has a size budget enforced by its own comparator. Shio's gates are the
Maven suite, the JS suite and conformance, and none looks at a byte, while the agent's
cost is governed by a properties file of per-endpoint ceilings with a reason for every
raise. The analysis measured the console at roughly 7.3 MB raw and 2.16 MB gzip loaded
before the first row of content.

console-size-budget.json holds one entry per built chunk plus a computed eager total in
raw and gzip bytes. A test reads the built index.html, resolves the static import graph,
computes the eager total and fails on a breach, printing both numbers on every run, so a
raise is a deliberate act with its reason in the commit.

The seed is taken now, before Blocks BC to BF add console code, so their cost appears as
raises they must argue for rather than being absorbed into a baseline. The diagnostics
endpoint reports the eager total as a finding, so an agent can check its own frontend
change without a browser. The assertion is the gate failing on a planted import of a
large module into the entry.

### LXXII.2 The editor chunk, loaded when an editor is opened (SH1044)

The analysis measured the real build: the entry chunk statically imports the GraphiQL
vendor chunk, so index.html preloads about 1.33 MB gzip of it and a render-blocking
stylesheet, while the GraphQL route's own chunk is a few kilobytes, which proves the
split is nominal. The cause is one ordering: the manual-chunk rule routes every id
containing codemirror into the GraphiQL chunk, and the Ace widget imports CodeMirror
into the widget map, which the app imports eagerly. The config's own comment asserts the
opposite of what shipped.

Split three ways. Give CodeMirror its own vendor chunk, matched before the GraphiQL
rule. Make the Ace widget a lazy component behind a Suspense boundary in the widget map,
so its chunk loads only for a post that has a code field. And fix or delete the Radix
manual-chunk rule, which produces no output file under the current bundler.

BG1's gate records the before and after in the commit. The live browser probe, which
already walks every route, starts recording request count, transfer bytes and time to
first row per route, the per-route budget this gate cannot see.

### LXXII.3 Declared dependencies that are reachable, or named as peers (SH1045)

A grep over shio-react/src finds no import of @tanstack/react-table, next-themes,
sonner, class-variance-authority, tailwind-merge, clsx or @radix-ui/react-dialog, each
declared in package.json. Some are genuinely dead. Some may be required as peers by the
design system, whose Toaster and assistant read sonner and whose theme provider may read
next-themes, and deleting a peer breaks the package at runtime rather than at build.

A reachability lint walks the declared dependencies and classifies each as imported by
product source, required as a peer by an installed dependency, read by build tooling, or
unreachable, with an asserted exemption list carrying a reason per entry. Its first run
is the deletion list: every unreachable dependency is removed in the same commit, and
the lockfile shrinks with it.

The lint then keeps the class from returning, which is the argument BG1 makes about
bytes applied to declarations. The assertions: the build and the JS suite stay green
after the removals, BG1's gate records any change, and a planted unused dependency fails
the lint by name.

### LXXII.4 One query layer, keyed on the addresses every surface uses (SH1046)

There is no query library in the manifest. The analysis counted seventeen call sites
fetching whole collections, and the browser refetches every post type on each folder
navigation to choose a default. The pattern that fixes this is applied to one endpoint
only: a hand-rolled single-flight store whose comment says components mounting together
share one request. Nothing generalised it. A service-injection provider built for this
exists with no call sites against dozens of module-scope service constructions, and it
is still mounted around the route tree.

Adopt one query layer keyed on the address grammar the MCP tools, the CLI and REST
already use, post:, folder:, site: and postType:, so a cache key is the string every
other surface addresses by, which is P10 applied to the cache. Migrate the seventeen
catalogue reads first. Repurpose the dead provider as the client provider, owning stale
time, retry and the error handler, or delete it, and prune the suppression entries
naming files that no longer exist.

BG1 records the byte cost of the library. The assertions count requests for a
folder-to-folder navigation before and after, from the live probe.

### LXXII.5 Cancellation, and a lint that keeps it (SH1047)

The analysis found no AbortController in the console. The browser's page fetch is
recreated on every change of folder, sort or query with nothing cancelling the previous
request, so a quick sequence of navigations can render a slow earlier response over a
fast later one: the list on screen belongs to a folder the curator already left.

BG4's query layer passes an abort signal to every request it makes, so migrating the
page reads onto it brings cancellation with it. This task migrates the remaining page
reads, the browser listing, search, trash, static files and activity, and lands the
guard independently of the library: a lint that every fetching effect or service call
passes a signal or carries a documented exception, held as a named list rather than a
ceiling.

The assertion reproduces the race in a test: navigate to folder A, then B before A's
delayed response arrives, and the rendered rows are B's. A planted fetch with no signal
fails the lint by name.

### LXXII.6 Validators on the console's reads (SH1048)

The CDA cache configuration registers the shallow ETag filter and a cache-control filter
for the CDA pattern; the agent web configuration registers its own for the agent and CDA
patterns. The other filter registrations are the tenant filter and an error recorder. So
the object listing, post-unified, post types, sites, static files and widgets, every
endpoint the console actually calls, carry no validator and no freshness hint.

The measurement lands first, in the shape of the agent's response-budget test: the bytes
each console read returns for the seeded sample site, checked in, so a payload
regression fails the build. Then extend the registration to the console namespace, minus
mutating and streaming paths, reusing the shallow ETag filter and a cache-control of
no-cache, which means revalidate rather than refetch. It sits below authentication and
rate limiting, so a 401, 403 or 429 never receives a validator, the rule the CDA
configuration already states.

MCP and the CLI benefit for free, since they call the same REST. With BG4 in place a
stale-while-revalidate becomes a 304. The assertion: a repeated read with its ETag
answers 304 with no body.

### LXXII.7 Failures that stay in their panel and say what to do (SH1049)

The app mounts one boundary around everything, and the codebase's own prose records
twice that the bento shell once came up blank behind it with the compiler saying
nothing. Fetch failures never reach it: the post editor's load settles every promise and
shows a spinner while the post is null, so a 500 leaves a spinner forever with no error
and no retry. The design system exports a load provider that shows a skeleton while
loading and a blank slate with Try again on error, and a skeleton primitive, and the
console uses neither.

A per-route boundary inside the shell outlet retries by re-running the route's queries,
and a per-panel boundary wraps each section panel so one dead widget does not take the
page. Every unconditioned spinner becomes the load provider with a skeleton shaped like
its content, rows for a list and tiles for a dashboard, and the error panel renders the
server's fix through SH955's reader.

The instrument comes first: the live browser probe injects a 500 on each route's primary
read and records what a curator sees. A lint holds the routes with no loading state,
shrinking only by deletion.

### LXXII.8 Home as the attention queue (SH1050)

The home page fires four requests on mount: every site, every post type, system info and
recent activity. The first two transfer full collections and use one property of each,
the length, and there is no count endpoint. The tiles start as null and render nothing
until both collections land.

One summary endpoint composes the existing services with count queries rather than
loaded rows: counts, the instance version, recent activity and the pending review count,
in one ETag'd response. The same numbers join the context pack, so an agent's first call
knows the shape of the instance, which is P2, measured against the pack's budget and
dropped if it regresses the cheapest read.

Then home becomes the attention queue, because the dashboard's question in this product
is not how much content exists but what needs a person: pending review sessions and
changes, content scheduled in the next seven days from SH991, recent agent runs with
their intent and a review action, open notes from SH1003, and instance diagnostics.
Every tile links to the page that acts on it, and shio status prints the same rows for
an agent orienting itself.

### LXXII.9 The accent measured, re-keyed and claimed through the inputs (SH1051)

Shio's accent gate asserts four tokens are present. Measured with the design system's
own arithmetic, the analysis found accent text on the background near 3.6:1 against AA's
4.5, and the gradient primary button placing white text far lower on the dark ground,
because the dark block reuses the light fill. Separately nothing in Shio sets the four
--vg-primary base inputs, so the preset's neutral renders in the surfaces
BENTO-AUTHORING says read --primary, including the rail's active marker, which is the
failure the contract names.

The gate lands before the re-key, because the numbers argue for the values. Using
VDS137's exported measurePairs, a Shio test resolves src/index.css over the published
preset and asserts every accent and primary pair at 4.5:1 on both grounds, printing each
ratio on every run. Then re-key: deepen the accent text and darken the dark-ground
fills, or give the button a foreground the pair guarantees.

Claim primary through the four base inputs, never by setting the compiled token, and
turn the accent gate into a census of claimable families driven by the preset, so a new
family fails Shio's build instead of silently rendering grey.

### LXXII.10 Fields with names, state and a keyboard path (SH1052)

The form renders a label with no htmlFor and then the widget, whose props carry no id,
no labelledBy, no describedBy, no required and no invalid. So clicking a label focuses
nothing, and every field on every post is announced as an unlabelled edit box. The
required marker is a bare asterisk with no aria-required and no text alternative, and
the description is an orphan paragraph with no aria-describedby.

Widen the widget contract to carry a field id, a described-by id, required and invalid.
Mint one stable, deterministic id per field in the form, so a render digest does not
move, render the required marker as hidden plus an sr-only word from the bundles, and
have each of the fifteen widgets forward the id and state to its own control, with
composite widgets taking labelledBy. The File widget's drop zone is a div with onClick
over an input hidden with display:none, which gives no keyboard path to attach a file,
and the rich-text widget removes its focus ring with nothing in its place.

The instruments: a source rule over the widget map asserting each component threads the
id, and a rule that a form control hidden with display:none rather than sr-only fails.

### LXXII.11 A shell that owns the landmark, the skip link and focus (SH1053)

The analysis found no aria-live, no sr-only text and no effect moving focus on route
change, a main element on three of forty-seven page components, and no skip link. The
primary button's loading state sets disabled, which drops focus from the control just
pressed. The assistant dock from SH964 now announces outcomes through its aria-live
caption, which removes the need for a separate announcer; what remains is structure and
focus.

VDS141 gives BentoShell exactly one labelled main and a skip link as its first focusable
element, and moves focus to the page heading on navigation; VDS143 keeps a loading
control focusable with aria-busy. Shio adopts both: the root renders through the shell's
main, the three page-level main elements are deleted, and every spinner gains an sr-only
label and an aria-busy ancestor. The selection count in the browser and the review delta
report through the dock rather than through a second live region.

The instruments: a landmark test asserting exactly one main per route and a
first-focusable skip link, and a heading-outline probe in the live browser check,
because heading level depends on composition a source scan cannot see.

### LXXII.12 Axe against every console route (SH1054)

The four accessibility rules are source scans for unnamed icon buttons, ARIA state
literals, keyframes and nested interactive elements, and each is blind outside its
shape: the analysis ran the scanner over raw buttons and anchors and found unnamed
controls it cannot see, one in a shared widget the gate says must never carry debt.
There is no jsx-a11y plugin, no axe and one viewport.

The live browser probe already opens about thirty routes against a running backend.
Inject axe on each, assert zero serious or critical violations, and hold everything else
as a named per-route debt map that shrinks only by deletion. Run it at three viewports,
assert the document language matches the active locale, and add the jsx-a11y plugin for
the class a source rule can catch at edit time.

It depends on BG10 and BG11 so its first debt map is not mostly the fields and landmarks
those tasks already fix, which would write the map twice. The assertion plants an
unlabelled input on one route and requires the probe to name the route, the rule and the
element.

### LXXII.13 An accessibility check family over delivered pages (SH1055)

A CMS's real accessibility liability is the pages it publishes. The verify inventory has
one accessibility rule, image-no-alt, which SH1031 makes reachable. There is no rule for
heading order, document language, empty links, landmarks or form labels, although the
outline reader already parses headings and the render endpoint already reports
landmarks, links and images.

Add a checks=a11y family composed over what the render path builds: heading-skip,
lang-missing, link-no-text, landmark-missing and form-label-missing, with image-no-alt
and asset-no-alt folded in, each carrying an address and a fix. Contrast belongs here
too, since the appearance digest already inventories classes and a theme's declared
token pairs can be measured with the same arithmetic BG9 uses for the console.

The family runs in shio verify, in the Insights panel from SH1013, and as a selectable
check in BA8's publish gates, so a team can refuse to publish a page that fails it. The
assertions render a fixture page with one violation of each rule and read each finding
with its address.

### LXXII.14 The console lint is red and only CI runs it (SH1075)

Found running `pnpm lint` in shio-react while moving files for SH961, not by reading
code. It exits 1 on one error, `react-hooks/set-state-in-effect` at line 73 of
`src/app/system/bento-tenant-link-notice.tsx`, and the same error is on the file's
previous path at the commit before the move, so the move did not introduce it.
`eslint-suppressions.json` holds every other finding by path and count, and does not
hold this one. `.github/workflows/suites.yml` runs `pnpm lint` as "The console lint (new
findings only)", so the rule SH665 set up, that a new finding fails CI, has been failing
on this file since SH931's tenant link notice arrived.

Nothing local noticed. `pnpm test` does not run ESLint, and `lint-baseline.test.mjs`
compares the suppressions file with its declared list, not with a live run. Both halves
are green while the lint is red.

Two fixes, each small. The effect sets state from the location on every change, and a
derived value or a keyed reset removes the synchronous setState. Suppressing it instead
would take a reason in the diff, since the rule describes a cascading render. The
instrument half matters more: something in `pnpm test` should run the console lint, or
state that it does not and name the gate that does. A suppressions baseline nobody
compares against a run is a list, not a gate.

### LXXII.15 What one change costs every open stream (SH1077)

SH967's fan-out benchmark measures what a change costs once `GET /api/v2/events` is open
on several screens: 100 entries read by 50 streams took 35,250 statements, 7.05 per
entry per stream, and `token-budgets.properties` now holds that at a ceiling of 8.
Nearly all of it is the per-entry read check. `ShContentAclService.canRead` loads the
object to find where its folder chain starts, climbs one `findParentIdOf` per ancestor,
and looks up ACL rows once per level until one level carries any; the principals are
three more queries whenever a level does. The stream calls it once per entry, and every
stream calls it again for the same entry.

None of that answer varies inside one pass. Entries written into one folder share a
chain, a stream's principals do not change between two entries, and ten streams held by
one curator ask the same question ten times. So the cost grows as entries × streams,
when what it depends on is folders × distinct principals.

The fix is a pass-scoped memo, not a second ACL model: resolve the chain once per folder
and the principals once per stream per pass, and keep `canRead` the single authority so
the decision cannot drift from the one the REST reads make. The benchmark already
exists, so the fix lands as a lowered ceiling in `token-budgets.properties` beside the
measured figure — the fan-out budget falling well below 7 per entry per stream is the
proof, and the SH967 withheld-entry test is what says the memo still denies.

### LXXII.16 A move's answer says what it touched, not what it moved (SH1078)

The P9 census (SH983) classified twenty-eight mutating paths and left one row that reads
as a gap rather than a decision. A move is the one console write that changes what a
visitor can reach: a post's friendly URL is composed from its folder chain, so dragging
a folder in the tree retires one set of delivery addresses and creates another. The
answer is the list of objects it touched. Which URLs those objects now answer on, and
which ones stopped answering, is nowhere in it.

Every other absence in that census has a reason that survives being written down — the
row is the answer, the write is not content, no route moved. This one does not: the
route did move, and the caller is told about the objects instead. The proof exists and
is a separate run. `shio verify --delivery` walks the site and reports what resolves,
which is the right instrument for asking whether delivery is sound and the wrong one for
asking what this call just changed. An agent that moved a folder has to decide, with no
signal, whether to spend that walk.

So: have the move's response carry the addresses it retired and the ones it created,
from what the write already computed, and let `shio verify --delivery` stay the question
about the whole site. The cheap half is that the renderer already resolves a post's
address, so the delta is a difference over values the call holds rather than a new
traversal.

### LXXII.17 An import's answer is the site, not what arrived in it (SH1079)

The P9 census (SH983) asked of every mutating path whether its answer carries a change
set, a digest, findings or a route delta. An exchange import is the write with the
widest effect in the product — a package creates a site, its folders, its posts, its
post types and its static files in one call — and it answers with
`ResponseEntity<ShSite>`: the site's own row, which the caller largely already knows,
since it named the package.

The asymmetry is the point. A single-object save can answer with the row it wrote
because the caller sent that row and can compare. An import is the opposite: nobody
typed its contents, a package built elsewhere is what a caller cannot predict, and what
it needs to know is what landed and whether any of it is broken. Both proofs exist and
neither is offered here. The import walks every object it creates, so a count by kind is
a tally it is already keeping, and `shio verify` over the new site is the finding list —
a run somebody has to decide to make, with nothing in the response to suggest it.

So: answer an import with what arrived, by kind, and with whatever the import itself
already knows went wrong — a skipped file, an unresolved post type. A first import on a
fresh instance is also the moment an operator has the least context to interpret
silence, which is when a bare created site reads as success.

## LXXIII One content model, many languages (Block BH)

Localization in Shio is complete in the data and thin everywhere above it. ShPostUnified
carries locale and translationGroupId, the unique URL key includes the locale, the file
projection carries translationOf, hreflang renders, the console's post DTO ships both
fields, and SH53's translations menu lets a curator switch to a sibling or create one.
What is missing is everything a translation team does after that. No site can declare
its default locale, enabled set or fallback order, so the menu offers every seeded
locale on every post. The public locale segment is validated and never selects. Nothing
knows a translation is out of date. The content browser drops the locale it is sent, no
coverage view exists, and translating means navigating away from the source.

The block adds the policy, makes delivery honour it, detects staleness with a digest
rather than a diff, and gives curators a coverage board and a side-by-side workspace.
The MCP write path that silently dropped locale and translationOf was a live defect and
is fixed first in Block AV. Machine translation stays outside Shio: the connected agent
translates, reached through the assistant, and every result lands as a reviewable draft.

### LXXIII.1 A locale policy, declared as content (SH1056)

The page render service states it outright: there is no per-site default locale, the
value is instance-wide, and calling it a site default would promise a setting nobody can
set. The site entity has no locale field. Verify derives a site's languages from the
locales its posts already carry, deliberately inferred rather than configured. So the
translations menu offers every seeded locale on every post, the missing-translation
warning fires only after something was translated, and a headless front end has nothing
to negotiate against.

A locale policy, default, enabled set and fallback map, stored as content: a field on
site.upsert rather than a new tool, a locales block in the site marker file that pulls
and pushes byte-stably, and a read on the CDA site so a front end can build a switcher.
The migration default is the derived union, so no site changes behaviour on upgrade and
the original argument survives as the unset case.

The resolved policy joins the context pack, the render frame's default moves onto the
site, and SH53's menu filters to the enabled set. The console gets a Languages card on
the site page. It depends on SH952 because an agent must be able to write locale at all.

### LXXIII.2 Delivery that honours the locale it is asked for (SH1057)

The public site route parses its locale segment into the link prefix, and the only place
that reads it is the validator that returns 404 for an unknown tag. It never reaches the
resolver, so on a site with two locales both URLs render the same post. Two constants
disagree about the default tag, en in storage and en-us in the delivery URL, and the
validator hides the mismatch. The CDA's list, query and search take no locale, the
GraphQL schema has no locale reference, and the TypeScript client cannot pass a locale
to the one endpoint that accepts it while its response type already declares one.

Thread the segment into a locale-aware resolve using the finder that exists, then BH1's
fallback chain, then today's locale-blind finder last, so no monolingual site changes.
Replace the second default constant with the policy's canonical tag. Add locale to CDA
list, query and search, and locale plus available locales to GraphQL with a locale
argument, measuring first the per-row available-locales lookup, which is an N+1 on a
list.

The client gains a locale option and a third argument on post-by-url. A verify finding
flags a route that accepts a distinction it does not honour.

### LXXIII.3 Staleness as a digest comparison (SH1058)

The translate verb copies the source's data and records the translation group, and
nothing captures which version of the source it came from: the row has no source digest,
source version or translated-from field. The only translation rule reports a missing
translation, so it cannot report a stale one. It is the most-used signal in every
commercial localization workflow, and Shio cannot represent it: a site can be fully
translated, then wrong, and no surface can tell.

The cheap part is a digest. Add a source_digest column by Liquibase, stamp it at
translate time from a hash over the source post's data, and never refresh it, since it
is a snapshot rather than a mirror, so staleness is a string comparison against the
source's current digest rather than a diff. The rule for which post is the source
already exists. A translation-stale finding joins verify as a warning, never an error,
because a lagging translation is ordinary work in progress.

The reads are conditional so a monolingual read stays byte-identical: shio_read reports
the flag only when true, the file projection carries translatedFrom beside translationOf
under SH1004's merge rule, and the console shows a badge on the post.

### LXXIII.4 The locale the browser was already sent (SH1059)

The content browser's row type declares locale and the server includes it, and the
mapping into rows keeps id, title, date, type and publish status and drops it. Two
locales of one page, which the unique key permits by design, render as two rows with the
same title, date and type and nothing to tell them apart. A curator deleting the one
they think is a duplicate deletes a translation.

This is the one-line defect inside the larger coverage task, separated so it does not
wait for it. Keep locale in the mapping and render it as a chip beside the post-type
badge, showing the tag and, where the site's enabled set from BH1 exists, marking the
default locale. The chip is part of each row's accessible name, so a screen reader
distinguishes the two rows as a sighted reader does.

The assertion carries the id: a folder containing a post and its translation renders two
rows whose accessible names differ by locale. BH5 builds the coverage view on the same
field.

### LXXIII.5 Coverage, from one grouped query, as a board (SH1060)

shio_find cannot filter by locale although every result carries one, so what is missing
in pt-br is a scan whose cost grows with the site rather than with the answer. The only
coverage report is the agent's missing-translation warning, which is warning-level and
returns early on a scoped scan, and no console view exists.

The query vocabulary from SH1022 gains locale and missingLocale; the second is the one
that pays, because every post whose translation group has no pt-br member is a single
grouped query on the server and a paged scan anywhere else. The same predicates reach
the CLI and shio report. A coverage endpoint returns, per locale, current, stale from
BH3 and missing, with addresses, built as one grouped query rather than one lookup per
group.

The console gets a Languages board: a row per translation group, a column per enabled
locale, and each cell current, stale or missing with a click through to the post or to
creating the translation. That turns a translation backlog into a work list, for a
person or for an agent handed it through BH7. The assertion compares the board's counts
with the endpoint's and with find.

### LXXIII.6 A translation workspace (SH1061)

SH53's menu navigates to the sibling's editor, and does the same after creating one.
There is no translation route, no split view, no source pane and no field-by-field
pairing, while every competitor treats this screen as the heart of the feature. The data
is ready: the translations read exists and field order is deterministic.

A workspace at its own route shows the source's fields read-only on one side and the
target's editable on the other, aligned by post-type ordinal, with per-field
copy-from-source and per-field mark-reviewed. Target saves land in draft, publishing
stays a separate explicit gesture, and the source pane is visibly read-only so nobody
edits English believing it is Portuguese. At narrow widths it stacks source above target
with a sticky language toggle rather than shrinking two columns. The translations read
takes an expand, so the pane loads in one request.

Mark-reviewed writes a note of kind reviewed through SH1003, so review state is visible
to an agent too. The assertions open the workspace, copy a field from source, save, and
read the target draft with the source untouched.

### LXXIII.7 Translation by the connected agent, requested from the board (SH1062)

Every commercial CMS ships an in-product translate button, and Shio's non-goal refuses
to host the model behind one. The honest version already has its parts: SH1006 relays a
curator's request to the connected agent as a note, post.upsert writes a linked sibling
with locale and translationOf, BH5's board lists what is missing, and shio verify proves
the result.

The board and the workspace gain a request action on a missing or stale cell, a row, or
a whole locale column. It writes a request note of kind translate, anchored to the
translation groups, carrying the target locale and whether stale translations should be
refreshed. The agent reads it, writes the translations as drafts in one run with an
intent naming the request, and replies on the thread referencing that run, so SH1007's
actions let the curator open the diff and approve from the dock.

Nothing is published by the request, and a stale cell becomes current only when its new
draft is approved. The assertions request a missing translation, fulfil it through MCP,
read the draft with its translationOf, and watch the board cell move from missing to
pending review.

## LXXIV The areas this round did not reach (Block BI)

The analysis behind this round was strong on the loop between the agent and the console
and thin on delivery and operations. Its coverage critique walked agents.md's area table
and found areas with no line at all, and each claim below was checked against the source
before being filed. Two of the critique's claims did not survive that check and are not
here: form submissions do have abuse controls, a CDA rate-limit token and a honeypot,
and the tenant page does expose quota and suspension.

What survived is concrete. On an instance with search indexing on, nothing can say
whether a published page reached the index. Renaming a page breaks every inbound link
while a Redirect post type the renderer already serves is never created by any writer.
Site export is an id-keyed zip with no CLI verb, no MCP reach and no console button,
while import has all three. Form submissions have no inbox and no settings screen.
Replication, one of Shio's differentiators, lives only in the CLI with no manifest key
the P1 census could classify. The conformance gate knows none of this round's new
vocabulary. And no task carries the documentation sweep for everything the round ships,
which the project's own process requires when a block closes.

### LXXIV.1 Index status, readable on every surface (SH1063)

ShTuringIndexService.active() is published as the manifest feature searchIndexing, so
the P1 census already counts it. The com.viglet.shio.turing package holds configuration,
a document mapper, field declarations, an index listener, the service, properties and a
text extractor, and no controller. In the console the word turing appears only in the
nav declaration and a stylesheet. So on an instance where indexing is on, nobody can
answer the question search exists for, is this page findable, and a failed push after
commit is recorded only as an incident.

The chain is short because the machinery exists. shio_read include=index reports the
resolved search site, the last indexed instant and the last incident for an address; a
shio://index resource publishes the routing table, which makes a site routing nowhere
visible rather than silent. shio index status and shio index reindex --dry-run use the
reindex path an existing test covers. REST adds a read beside publish, and the console
shows a card on the site page with enabled state, target, search site and last failure.

verify gains index-unroutable for a published site whose search site resolves to
nothing, the failure that costs most because it raises no error.

### LXXIV.2 A rename that offers the redirect it needs (SH1064)

ShSystemPostType declares REDIRECT, the page render service serves it, and SH385 decided
that its PERMANENT flag chooses 301 over 302. Grepping the unified write service and the
agent batch service for it returns nothing: no rename, move, unpublish or delete path
has ever created one. The content-files side is further along than the server, since a
post carries movedFrom so the three-way merge can follow a rename, but that instructs
the merge and routes nothing on the instance. So renaming pricing to plans answers 200,
and every external link, bookmark and link from the site's own published pages then
404s.

SH996 computes, for a publish, which URLs stop resolving and how many published pages
link to them. A friendly-URL change or a move reuses that computation and answers with
the count of inbound links that will break, and with redirect set it writes a Redirect
post in the same transaction. The batch vocabulary gains post.redirect rather than a
tool. verify gains url-orphaned for a previous address with inbound links that resolves
nowhere, and shio redirects lists them.

The console's rename dialog states the count before its button arms and offers the
redirect checked by default.

### LXXIV.3 Export on every surface, and what it does and does not carry (SH1065)

GET /api/v2/site/{id}/export produces a zip, and ShSiteAPI's javadoc notes the route
stays strictly id-keyed, so a caller must resolve a uuid first: the one export route of
an address-native product cannot be reached with an address. cli/src/commands holds
import.mjs and no export, no MCP tool or resource exports, and the console's services
never call the route. That asymmetry is P1 broken the way this round keeps finding it:
the half a person clicked shipped, and the half a pipeline needs did not.

The chain is small. shio export site:<name> --out writes the zip, resolving the address
and streaming to a file. The manifest indexes the route. MCP gets a resource rather than
a tool, since a zip through a tool call is the reasoning shio_assets already gives for
uploads. The console gets a menu item on the site page beside Import.

The task also writes down a boundary nobody has stated: what an export carries and what
it does not, users, tokens, revisions, notes, releases, and how that differs from shio
pull. Promotion between environments is named as that boundary's open question, with
releases from Block BA as the likely unit, rather than being solved here.

### LXXIV.4 A forms surface for the arrivals the dock reports (SH1066)

ShFormSubmissionAPI's javadoc argues that a submission is a post, so trash, export, ACL,
review and the agent surface already apply to it. That is true of storage and false of
attention. bento-nav.ts has no Forms entry, the site form configuration has no console
page, and a curator's only route to submissions is to find the folder a developer
configured, where each looks like an ordinary draft. SH970 makes an arrival visible in
the dock; there is still nowhere to go from it.

The endpoint's abuse controls already exist, a CDA rate-limit token, a honeypot and an
allowed-origins check, and this task does not re-open them. It builds the surface. A
Forms page lists submissions per site through SH1022's query vocabulary with a
submission flag, with per-row archive and mark-as-spam that write ordinary states, and a
settings panel for a site's form: destination folder, allowed origins, and which fields
supply the arrival's caption.

The agent's leg is nearly free because a submission is a post: shio_find gains
submission true, and verify gains form-destination-missing for a form whose folder no
longer exists. The assertions post a submission, find it on the Forms page and through
MCP, and archive it.

### LXXIV.5 Replication, declared so the census must decide it (SH1067)

The manifest's features map carries multiTenant, oidc, webhooks, scheduledPublish,
previewTokens, rateLimit, signedImageUrls, searchIndexing, marketplace, graphql, search
and locales, and no replication key. shio clone, shio convert and shio propose, which
turn an existing site into an authorable Shio site, exist only as CLI commands. So the
conformance test that classifies every feature cannot see them, a curator who finds a
page worth replicating cannot start from the console, and an MCP-only client cannot
start at all.

The smallest honest move comes first: declare replication as a feature key, which forces
the census to classify it and turns an absence into a decision, either an agent route or
an exception with a reason and a falsifier. Then give it the reach the decision names.
The likely shape is a resource describing replication runs and their status plus a
request, since a capture needs a browser and the instance runs none, so the work stays
in the CLI where Playwright is an optional import and the instance records and reports
it.

The console gets a replication entry that starts a run through that request and shows
its status, reporting outcomes through the dock.

### LXXIV.6 The third gate, told about the round (SH1068)

cli/conformance holds docs-vocabulary.conformance.mjs, which asks whether documented
tool names, ops and properties still exist, docs-stack.conformance.mjs, which pins the
product's description of its search story, and docs-coverage, which derives the coverage
matrix's summary from its tables. This round adds MCP arguments, op names such as
post.rollback, release.run, note.add and asset.meta, verify checks, CLI verbs,
properties and resources. Every one is vocabulary those files read, and agents.md is
explicit that a red gate is a stop.

The likely failure is quiet in the wrong direction: a block ships green on the Maven and
JS gates, its new names never enter the conformance vocabulary, and the docs later
describe tools the gate never checked. So each capability task in this round adds its
names to the conformance vocabulary sources in the same commit, and this task makes that
checkable: a test compares the op names in ShAgentOps, the tool and resource names the
MCP server registers, and the CLI's command table with the vocabulary sources, and fails
on any name present in code and absent from them.

It prints both sets on green. The assertion plants an op in the code only and requires
the gate to name it.

### LXXIV.7 Documentation as a count, not a memory (SH1069)

The shio-roadmap-docs process is explicit: when a block's last task ships, docs-shio,
shio-site and README.md are brought up to date in one pass, and each task records its
end-user documentation decision. This round files many blocks and no task mentions any
of them. shio-site's README records that all the public site's copy lives in one file,
which makes the omission cheap to fix and expensive to skip: the site would keep
describing a console without releases, revisions, notes, the assistant, a translation
workspace or publish gates.

Two mechanical changes. The coverage matrix that docs-coverage.conformance.mjs reads
gains a row for every manifest feature key and every surface in AY6's declaration, so an
undocumented capability is a failing count rather than something a person remembers, and
the conformance gate reports the count. And the block-completion sweep becomes a check
rather than a habit: when roadkeep reports a block finished, the gate fails until each
of that block's new keys and surfaces has a documented row.

The honest reason this is not housekeeping is the round's own thesis that discovery is a
product feature, which applies to a person reading shio.viglet.org as much as to an
agent reading the manifest.
