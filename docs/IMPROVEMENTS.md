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

### LX.2 The contract, vendored where it is tripped over (SH941)

page-reference.mjs states its own argument in its header, and it is the one worth
keeping: the catalogue answers whether a component exists, but not whether a page reads
as the same product, and that answer lives in two documents and nine artboards which no
node_modules read will surface. A contract an author has to browse another repository to
find is one they read after the fifth screen, which is what BENTO-AUTHORING's own
opening warns against.

So the bin writes those surfaces into the consumer, where an agent session and a person
both trip over them, in two disciplines worth adopting as written rather than
reinventing. Copy is rewritten on every run and drift is a finding, because a vendored
authority that has drifted is read with the same trust as one that has not. Declaration
is the consumer's own entries re-derived inside a file the adopter also writes in,
compared as parsed JSON rather than bytes, so indentation is not drift and the check
survives being left switched on.

roadkeep-gui carries the result: .claude/skills/viglet-ds-pages holding SKILL.md,
authoring.md and boundary.md, and docs/design holding nine vds-*.dc.html artboards laid
out by canvas.json, whose vds- pages belong to the package and whose other pages are
that repository's own and are preserved. Shio has neither directory. This task runs the
bin and commits what it writes; nothing in those files is hand-authored, because the
next run rewrites them.

### LX.3 Drift as a failure, not a discovery (SH942)

A vendored file is read with the trust of the package that wrote it, so the interesting
failure is not that it is missing but that it is stale. page-reference.mjs is built for
this: --check reports what would change and writes nothing, exiting non-zero on drift;
--json gives the machine-readable form; --warn reports and still exits zero, which is
the shape a repository adopts before it has paid off its debt.

The gate belongs inside the JS gate rather than beside it. This project runs three
through scripts/run-gate.mjs, and agents.md's rule about never piping a gate into grep
applies: a pipeline's exit status is its last command's, which is how pnpm test once
printed fail 2 above EXIT=0. So the check runs within pnpm test, redirected rather than
filtered.

Two things it must assert beyond an exit code, both learned here. SH897's browser check
passed with bento.css deleted, so this one is broken on purpose until it fails: plant a
drifted byte in a vendored file and require the check to name that file. And the
declaration half is compared as parsed JSON, never as bytes, or reformatting the
consumer's own file reads as drift and the gate becomes the first thing somebody
switches off.

SH937's pattern covers the rest: a line waiting on a design-system release gets a probe,
so the release that ends the wait turns the suite red instead of going unnoticed.

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

### LX.6 Which screens are worth drawing, and which are already decided (SH945)

roadkeep-gui put nine artboards in docs/design before building, and two of them,
DirecaoA and DirecaoB, are titled nao escolhida. That is an artboard earning its keep:
it made a choice between two directions cheap and visible, and the record of the
rejected one survives in the file. It drew first because it was greenfield, with no
screen to disagree with.

Shio is not in that position and should not copy the practice wholesale. Blocks AQ and
AR already migrated the console: 23 pages, zero legacy chrome, one route tree, the three
shapes and the panel taken from the package. Drawing those screens would re-decide a
settled question, cost days, and yield nothing, because what an artboard resolves is
already resolved by the shell and by BENTO-AUTHORING.

What is unsettled is the surfaces this round invents, where a wrong guess is paid for in
backend work. Five qualify: version history with a diff, the translation workspace,
release bundles, the agent-supervision inbox, and the asset browser. Each is a layout
with real alternatives whose data model follows from the choice, which is the
DirecaoA/DirecaoB case exactly.

So this is a task inside the block rather than a gate in front of it, and its scope is
those five. The artboards go in docs/design beside the vendored vds- pages, on this
repository's own canvas.json pages, which page-reference.mjs preserves rather than
rewrites.

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

### LX.10 The plugin this repository ships, loaded where it is written (SH949)

.claude-plugin/marketplace.json declares the openviglet marketplace with one plugin,
shio, sourced from ./claude-plugin: a manifest, an .mcp.json wiring the MCP server, four
skills and three commands, with a test asserting manifest paths, skill descriptions and
command safety. .claude/settings.json enables roadkeep@alegauss and knows only the
alegauss marketplace. So the skills that tell an agent how to build a site in Shio are
published and never loaded in the repository that wrote them.

Add openviglet to extraKnownMarketplaces and shio@openviglet to enabledPlugins. That
forces a decision worth forcing: ten project skills under .claude/skills/shio-* and four
plugin skills overlap in subject, and two authorities on one subject is the drift
agents.md already names. The split that survives is that plugin skills serve a session
driving an instance, and project skills serve a session changing this source tree. Write
that sentence into the routing table.

The instrument extends claude-plugin/test/plugin.test.mjs: no skill name appears on both
sides, and settings.json enables the marketplace this repository publishes.

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

### LXI.1 Stop serving uploads as executable documents (SH950)

ShStaticFileAPI sets Content-Disposition inline on both of its file responses, and
ShSiteAssetDelivery does the same for site assets. A grep of the backend for
X-Content-Type-Options or Content-Security-Policy returns nothing. The content type is
probed from the file name first, so evil.svg is served as image/svg+xml and payload.html
as text/html, from the same origin the console runs on, and the upload endpoint accepts
an agent token.

This task is the one-commit mitigation, deliberately separated from the policy work in
AV2 so the hole does not stay open while an allow-list is argued. Register a response
filter on every asset route that sends X-Content-Type-Options nosniff; serve anything
outside a short render-safe list of raster image types with Content-Disposition
attachment; and send a sandbox Content-Security-Policy on asset responses so an SVG
opened directly runs nothing.

The assertion carries the id at the boundary: a GET of an uploaded SVG answers with
nosniff, a sandbox CSP and no inline disposition, and the same GET of a PNG still
renders inline. The delivered site's own image tags are unaffected, because an img
element never executes script.

### LXI.2 An upload policy that says what it refused and why (SH951)

The only sanitising in the static-file services is of tenant and file names. Nothing
inspects content, and spring.servlet.multipart.max-file-size is 1024MB. So after AV1 the
bytes are no longer executable when served, but they are still stored, still listed, and
still copied into a content tree by shio pull.

Add an upload type policy with allow, deny and sanitize per media type, defaulting to
deny for HTML and JavaScript and sanitize for SVG. The SVG sanitizer strips script,
foreignObject, event-handler attributes and non-data xlink:href, and records
SANITIZED:true in the File post's data so the fact is content rather than a log line. A
refusal is a teaching 400 carrying allowed and a fix naming the policy key, which is the
shape every other refusal on the agent surface already has.

The agent legs land together: shio_assets rows gain unsafe and sanitized flags, and shio
verify gains asset-unsafe-type over the projected tree. One decision must be written
into the task rather than assumed: what shio convert uploads today, since a replication
run that captures inline SVG must keep working. The assertions plant a script in an SVG
and a refusal of an HTML file.

### LXI.3 A tool that accepts an argument and ignores it (SH952)

ShAgentBatch.Op carries eleven fields: op, address, type, folder, to, data, when,
locale, translationOf, url and expect. ShAgentBatchService reads when for scheduling and
expect for the SH721 concurrency guard. ShWriteTool's schema advertises when as the
ISO-8601 instant for post.publish, and its call method builds every op through the
constructor whose javadoc reads the six-field form, for the ops that never schedule,
which passes null for the other five. No test calls shio_write with any of them; the
golden tools-list only records the schema.

So over MCP, which is how most agents write, a publish meant for next Monday happens
now, a write meant to be conditional on the state it was planned against is
unconditional, and a translation is created without its locale. The REST batch
deserialises all eleven fields, which is why nothing looked wrong from the console.

The instrument lands first and generalises: a conformance test walks every MCP tool's
declared inputSchema and each op spec in ShAgentOps, and asserts every declared property
is either read by the handler or refused with a teaching 400. Then ShWriteTool passes
all eleven fields. The assertion carries the id: shio_write with when schedules, and
with a stale expect refuses.

### LXI.4 A count that is the number acted on (SH953)

In object.children.page.tsx the batch delete loops the selection, awaits deleteObject
for each id with .catch(() => {}), and then shows content.itemsDeleted with the
selection's size. The select-all cap is a thousand, so a thousand sequential round trips
that all fail still report a thousand deleted. There is no progress, no cancel, and the
refresh returns to page zero. The folder write service has real refusals, restore
conflicts and 409s, and none of them can reach the curator through this loop.

The content API doc already states the rule this breaks: the number in the dialog is the
number acted on. The honest fix does not wait for the bulk endpoint Block BD adds.
Collect each outcome, count successes, and report refusals by problem title with their
detail, keeping the curator on the page they were on. When BD's bulk verb lands, the
loop becomes one call and this reporting stays.

The assertion carries the id: a batch in which one delete is refused reports the
successful count, names the refused item, and never shows the selection size as the
number deleted.

### LXI.5 A button that has lied since the cache it cleared was removed (SH954)

ShObjectAPI.clearCache is a GetMapping on {id}/clear-cache returning
ResponseEntity.ok().build(). The console's content browser renders a Clear Cache button
on every row that calls it and then toasts content.cacheCleared. Nothing is cached at
that scope any more, so every press is a success message about nothing.

Decide it rather than inherit it. The honest move is to delete the action, its i18n keys
and the endpoint together, since the CDA's cache-control headers and the image transform
cache are invalidated by publishing, not by a row button. If an operator does need to
purge derived images, that is a named verb with a real effect and an agent equivalent,
filed as its own line.

The instrument is the part that generalises past one button: a lint over the controller
scan that fails on any mapping whose body is a bare ok() with no side effect, held as an
asserted list of exceptions with reasons, and on any GET mapping named as an action. The
assertion for this task is that no console call and no manifest entry names clear-cache.

### LXI.6 Errors are instructions, on both sides of the wall (SH955)

grep for toast.error(t("common.error")) over shio-react returns twenty-four hits, three
of them in the post editor inside catch blocks that do not bind the error. The server's
problem type documents fix, allowed, didYouMean and at, and calls a 4xx carrying none of
them incomplete. The console's axios interceptor handles CSRF capture and
backend-offline reporting and nothing else, so no code path reads
application/problem+json.

P6 is a law about errors, not about the agent surface, and today it holds on one side
only. One interceptor parses a problem document into a typed error. A single reporter
renders detail as the headline, fix as the action, didYouMean as a clickable correction
and allowed as a collapsible list, and the form reads at to pin the message on that
field. The backend's half is small: populate at with data.<field> on single writes,
which today only the batch does.

The instrument is a lint that fails on a catch in a save path that reports a fixed
string, and a backend assertion that every console-facing 4xx carries fix. Block AX
routes these reports into the assistant dock; this task makes them exist.

### LXI.7 One sanitizer, at the one boundary every writer crosses (SH956)

html-editor.widget.tsx calls document.execCommand for its formatting, reads innerHTML as
the value, and has no paste handler. Nothing in the console or the backend sanitises
content: the only sanitize calls in the tree are for tenant and file names. The renderer
emits field HTML unescaped by design, and an agent's post.upsert writes the same field
with no filter at all, so the gap is not a console gap.

Put the defence at the boundary every writer crosses rather than in each writer. An
allow-list sanitizer runs in the unified write service for every field whose widget is
HTML, covering the console, REST, the agent batch and the Universal Editor's draft
merge, which the analysis found proposing its own sanitize step separately. A dry run
reports what would be stripped as a finding, so an agent learns why its markup changed
instead of discovering it later. shio verify gains html-unsafe for existing content,
because a sanitizer on writes says nothing about rows already stored.

The block content model in Block BC is the long answer to an opaque HTML body; this is
the short one, and it must not wait for that. The assertion plants an onerror attribute
through each writer.

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

### LXII.1 A census of the chrome vocabulary that may only shrink (SH957)

SH886 opened Block AQ with three figures grepped by hand and two of them were wrong,
which is why its census walks the tree instead. This sweep needs the same instrument
before its first deletion, or the last reference to a console chrome survives because
nobody could see it.

A test walks shio-react's src, test and scripts for SectionCardChromeProvider,
useSectionChrome, a comparison against a chrome value, a chrome prop, and the word
chrome in a comment that explains two eras. It holds the matches as a per-file debt list
asserted to equal what it finds, so a new reference fails the build naming the file and
a removal fails it until the list is shortened in the same commit. It prints both counts
on every run.

The non-vacuity case plants a SectionCardChromeProvider in a temporary copy of a clean
file and asserts the census names it. The list reaching zero is this block's acceptance
criterion, which turns one chrome from a claim in a changelog into a number a later
reader can re-run.

### LXII.2 One provider at the root instead of one per route (SH958)

bento-section-chrome.tsx creates its context with console as the default. Shio pays for
that on every route: App.tsx wraps about twenty-five route elements, and
graphql.routes.tsx one more, in SectionCardChromeProvider chrome=bento. The wrappers are
not decoration. A surface mounted without one renders AdaptiveSectionCard and EmptyState
in the console look, and nothing fails when a new route forgets it.

Mount one provider above the router, so every element under it, inside the shell or
outside it, reads bento, and delete the per-route wrappers. This is the interim form:
VDS145 flips the package's default to bento, after which the root provider is a no-op,
and AW7 deletes it.

The assertion carries the id: a route element mounted with no wrapper of its own renders
the bento form of EmptyState, and the census from AW1 shrinks by the wrappers removed.
The surface digest is taken before and after and must show no surface changing chrome,
since every route already rendered bento through its own wrapper.

### LXII.3 The console branches, deleted where they are still reachable (SH959)

empty-state.tsx documents the case exactly: a component rendered under no
SectionCardChromeProvider concludes there is a console chrome to render into, and
renders its monochrome console block. panel.tsx falls back the same way when chrome is
not bento or there is no header, not-found.page.tsx renders whichever chrome the path
implies, and static-file.list.page.tsx picks its container by chrome.
outside-shell-chrome.test.mjs was written to record that pages outside the shell took
the console look.

With AW2's root provider every one of these reads bento, so the console branches become
unreachable, and they are deleted rather than left as a fallback nobody exercises. The
bento vocabulary page loses its ChromeWitness, which exists to print which chrome a
provider chose. outside-shell-chrome.test.mjs is rewritten to assert the opposite of
what it recorded: login, setup and the catch-all render the bento forms.

The census from AW1 drops by every branch removed. The design system's own
AdaptiveSectionCard keeps its console branch until VDS146, and this task does not touch
the package.

### LXII.4 Instruments that measured a migration, re-derived for a console that has one chrome (SH960)

SH896's surface digest hashes each surface's era, its chrome in source order and its
frosted-surface classes, and SH886's census holds every page by the chrome it renders.
Both were the right instruments for a migration, and SH910 already rewrote eleven
readers whose premise was two eras. What is left is the era field itself: every surface
reports bento, the classifier still carries the rules for console, and a saved survey
compares a field that cannot change.

Re-derive rather than delete, because the digest is the P9 tool for proving a page did
not change without opening it. The era field leaves the digest, the
chrome-in-source-order and frost fields stay, and the saved survey is regenerated in the
same commit so a comparison against it means something. The console-chrome census either
folds into AW1's vocabulary census or is deleted with the reason recorded, since two
walks asserting the same absence are two files that can disagree about it.

SH938 is the warning worth carrying: it filed a defect off a census log line without
reading the declaration beneath it. The rewritten readers state in their headers what
they measure and what they do not.

### LXII.5 One page tree, organised by domain (SH961)

src/app/bento holds the pages written for the shell; src/app/console holds the
twenty-three that were adapted into it, including the post editor, the post-type editor,
the content browser, search, trash, the review queue and the GraphQL explorer. Every one
renders bento. The split is a record of how each page arrived, and it misleads the
reader who needs it most: an agent adding a surface greps both trees and has to decide
which era a new page belongs to.

Merge them into one tree organised by domain, content, admin, editor and system, with
git mv so history follows each file, and rewrite the imports and route declarations in
the same commit. ROUTES.CONSOLE is not a chrome reference, it is the console product,
whose base is /bento, and it stays; the legacy /console redirects stay too, because they
protect bookmarks, not a chrome.

The assertions are that no import path contains app/console or app/bento, that
route-params.test and the route scanner from SH928 still find every route, and that the
build and the census are green.

### LXII.6 The prose, brought to one chrome (SH962)

page-hero.tsx explains that the console branch went with the shell that rendered it,
post-type.edit.page.tsx that its sections render differently in the two chromes, App.tsx
that the visual editor renders neither era's chrome, and object.children.page.tsx that
it is one header for both chromes. docs/agents/console-ui.md carries the census, the
digest's era classifier, the chrome adapter and its two silent failure modes as current
guidance. Each was accurate when written and is now an instruction to keep supporting
something that is gone.

Rewrite comments to say what the code does now, in one chrome, and leave the migration's
reasoning to git log, which is where agents.md puts history. In console-ui.md, sections
whose subject was the migration collapse into one short record of what the console is
today, and the census and digest sections describe the instruments as AW1 and AW4 left
them.

The docs-shio pages are checked for the same vocabulary, and any user-visible mention of
a console look is removed. The census from AW1 counts comments, so this task is where
its debt list reaches the provider AW7 removes and nothing else.

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

### LXIII.4 The change feed, pushed to the screens that are open (SH967)

No SseEmitter or event-stream response exists anywhere in the backend. The agent surface
reads changes by polling a keyset feed, and the console does not read it at all, so a
curator watching the content browser while an agent writes sees nothing until they
navigate.

Compose rather than fork: GET /api/v2/events streams the same change-feed entries the
agent reads, filtered to the session's tenant and to what its content ACL may see,
starting from a watermark and resuming from Last-Event-ID after a reconnect, with a
heartbeat. The CLI gains shio changes --follow over the same stream, and the agent's
polling read is unchanged. Scheduler incidents and form arrivals, which AX6 and AX7
report, join the feed as entry kinds rather than as separate channels.

Measure before relying on it: a benchmark states the cost of one idle connection and of
a fan-out of a hundred entries to fifty sessions, and the budget lands beside the
others. The assertions are that a write reaches an open stream, that an entry the reader
cannot see is withheld, and that a reconnect with Last-Event-ID loses nothing.

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
