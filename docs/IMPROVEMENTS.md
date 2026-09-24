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

### LX.11 The published release this console does not take (SH1088)

Eight lines here are `blocked-outside` on a design-system id, and every one of those ids
has shipped: VDS129, VDS130, VDS131, VDS132, VDS133, VDS134, VDS135 and VDS140 all
landed before the `2026.3.11` version bump, and npm serves `2026.3.11`. The console's
range is `^2026.3.10`, so the lockfile is the only thing holding it — `node_modules`
carries no `corner-slot`, no `BentoShell` and a `BentoBackToTop` with no props, which is
how SH964's blocker was confirmed still real after the changelog said otherwise.

It is not a version bump, which is why it is a task. The same release carries two
deliberate removals: VDS146 took out `SectionCardChromeProvider`, which `App.tsx` mounts
above the router, and VDS147 removed the console-era page vocabulary. So the upgrade is:
take `2026.3.11`, delete the chrome provider (SH963's job, which this absorbs or hands
back), find what VDS147 renamed, and run the console's own suites — `chrome-adapter`,
`chrome-vocabulary-census` and `bento-is-the-default` exist precisely to say what moved.

What it buys is the block, not the version: Block AX opens entirely, and four of Block
AU's five lines lose their external dep. The check that it was worth doing is `roadkeep
deps` reporting no `blocked-outside` line whose id is already in the installed package —
a state a reader can confirm without opening npm.

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

### LXIV.17 A changelog that names a table nothing created (SH1089)

SH1010's changelog added a column to `sh_post_type_attr`. The table is `ShPostTypeAttr`
— the SH26 baseline was generated from H2 with case-bearing identifiers, and the
snake_case spelling every changelog since v2026.3.36 uses belongs to the tables those
changelogs created. The Java gate went green at 2233 and the clean instance refused to
start.

It cannot be otherwise as things stand. `src/test/resources/application.properties` sets
`spring.liquibase.enabled=false` and `ddl-auto=create-drop`, so during `mvn test` the
schema is the entity model and nothing in `db/changelog` runs at all. SH729 already
named this and built the instrument for one case — a constraint declared only in
Liquibase — and that lint deliberately folds case and underscores, so
`sh_post_type_attr` and `ShPostTypeAttr` are one identifier to it. The database does not
fold.

The check is cheap and needs no entity model: every `tableName` a changeset alters must
be spelled exactly as some earlier changeset created it, and every `columnName` it
references must have been created on that table. That is a walk of the changelogs in
master order, and it would have failed naming the file and the line.

Worth the same pass: `addColumn` and `createIndex` are not the only changes that name a
table, and the failure is identical for each. The lint should enumerate the kinds it
understands and fail on one it does not, so a change type nobody taught it is a red
build rather than a silent gap.

### LXIV.18 A mirror taken from a directory, not a revision (SH1115)

`coverage-mirror.mjs` reads `COVERAGE.md` from a checkout directory — `SHIO_DOCS_DIR`,
defaulting to a path on one machine — and writes the ids it finds into
`docs/coverage-named.json`. Committing that file here marks those ids named forever:
SH758's reader subtracts them and SH747's ratchet counts them as covered. Nothing
anywhere asks whether the edit that put them in the matrix was ever committed in the
repository the matrix belongs to.

Observed, not reasoned: clearing SH799's queue twice in one session left
`vigletdocs/docs-shio/COVERAGE.md` modified and uncommitted both times, and the mirror
was refreshed from that working tree and committed here each pass. The conformance check
reads the same working copy, so an uncommitted edit makes mirror and matrix agree
permanently.

This is the shape the mirror's own header warns about: a copy of a fact whose home is
elsewhere, held honest by being generated and by being checked against the authority.
The authority it is checked against is a directory, and a directory is not a revision.

The narrow fix records beside the ids what the matrix's repository was at — its HEAD,
and whether that tree was clean — and refuses a refresh taken from a dirty one, or marks
it provisional so the next run asks again. An id named from an uncommitted file is then
a state the mirror can express rather than one it forgets.

The assertion is a refresh from a dirty docs checkout refused or marked, and a clean one
unchanged.

### LXIV.19 A stripper that says when it could not read (SH1118)

`stripComments` opens a block comment on a slash and two stars wherever it finds them,
string literals included, and closes on the next star-slash — hundreds of lines later,
or never. Everything between is deleted from the scanned text, and every assertion over
that region then matches an empty string and passes.

Measured during SH1049: adding a Playwright wildcard route to `console-check.mjs` cut
the stripped view from 79,612 characters to 36,223 and turned four assertions green over
nothing. The comment written to explain the hazard quoted the pattern and caused it a
second time, an hour later.

The file says it is not a parser, which is honest about the *stripping* and silent about
the consequence. Thirty-one suites across two packages read source through it, and any
one can be silenced by a string in a file it does not own.

The narrow fix is an invariant the helper enforces itself: a strip that removes too
large a fraction, or leaves an opener unclosed, throws rather than returning. A reader
that cannot see the file should say so rather than agree with everything. SH1049's shape
test anchors on its script's last statement — the same idea, by hand, once.

The wider question is whether the two copies should have stayed separate. SH823 kept
them apart on purpose, and a bug in both is what that decision quietly costs.

The assertion is a planted opener in a string failing the strip by name, and the
thirty-one suites green after.

### LXIV.20 A control character where an escape was meant (SH1119)

Two committed regexes contain a literal backspace, U+0008, where `\b` was meant. Found
by `grep -P '\x08'` over the tree after SH1052 made the same mistake and caught it in
review.

`claude-plugin/test/plugin.test.mjs:196` reads `text.match(/<BS>SH\d{2,}<BS>/g)` and
asserts the result is empty — it always is, since no source file contains a backspace.
That check exists because five internal task ids once shipped in the published README,
package description, fragment and a skill, and it has been asserting nothing since.
`shio-react/test/unsaved-work.test.mjs:80` is a `doesNotMatch` for `useBlocker(` and
`usePrompt(`, so it also always passes; SH1090 says calling either throws against this
console's router.

Both repair to green, checked before filing: the plugin's published files carry no
`SH<n>` today, and the guard names both hooks only inside backticks, with no call. The
fix is two characters; the value is that the gates start working — and it is a task
because the repair is what finds out whether anything drifted while they were blind.

The cause belongs with them: a shell heredoc expands `\b` before the file is written, so
a scripted edit inserting a regex corrupts it silently where an editor tool would not.
Same family as the stripper in the sibling line — an instrument that reads as working
and checks nothing — reached by another route.

The assertion is a repo-wide scan: no tracked source file contains a control character
in the C0 range other than tab and newline. Three files named on the first run; a fourth
is the finding.

### LXIV.21 `--delivery` asks for more and sends less (SH1120)

`withDelivery` in `cli/src/commands/verify.mjs` composes the check list when
`--delivery` is passed without `--checks`. Its fallback is the literal
`content,routes,diagnostics`, and the comment above it argues — correctly — that sending
`delivery` alone "would quietly drop the rules that do not need a network". That list is
the bug the comment warns about: the server's default is every group, and this
hand-written copy has been missing `page` since SH516 and now misses `a11y` too. So
`shio verify --delivery` runs *fewer* checks than `shio verify`, which is the opposite
of what the flag promises, and `verify.test.mjs` pins the narrower list as if it were
correct.

A client-side copy of a server-side default is the shape, not the two group names. The
server already answers `all`, and already adds `delivery` to the default set when
fetching is on (`ShAgentCheck.defaults`), so the CLI needs to name nothing: with no
`--checks`, `--delivery` can send `all` and let the server compose the set. That removes
the list from the client permanently, which is the point — a fourth group will be added
and this line would be missed again.

The fix is `withDelivery` sending `all` when the caller named no checks, keeping the
explicit list when they did, and the test asserting the request no longer narrows the
run. The durable half is an assertion that the CLI names no check group of its own
anywhere: a wired list here is a second vocabulary that drifts from the one the server
publishes.

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

### LXVI.15 A schedule that fires past the gate (SH1138)

SH1084's lint lists every method that publishes and whether it evaluates the site's
publish gates. The SH15 sweep sits outside its pattern and does not evaluate one.
`ShPostScheduler` fires a due post through `publishScheduled`, and a due release through
`ShReleaseRunner.run`. Nothing on either path calls `ShPublishGateService`. So a page
scheduled for Monday goes live on Monday whatever a gate declared since, and whatever
the draft became after it was scheduled.

Check first what the schedule call itself evaluates. If `post.publish` with `when` gates
at scheduling time, the hole is only content that changed afterwards. If it does not,
the hole is every scheduled publish. Either way, evaluating the gate when the schedule
is set is not enough, because the draft can change until the moment it fires.

The fix evaluates the gate at fire time. A thread with no request cannot answer a 409,
so a stopped publish becomes a durable incident on the change feed, as SH969 made a
failed scheduled publish one, with the gate's title and a retry. The schedule stays, so
a curator can fix the page or waive with SH998's reason, and the release runner reports
the stopped page rather than aborting the release.

The assertion carries the id: a post scheduled past a failing gate is still a draft
after the sweep, and the feed carries an incident naming the gate. Then SH1084's lint
widens its pattern to `publishScheduled` and drops the `ShReleaseRunner#run` exemption.

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

### LXVII.12 A stream nobody has seen deliver (SH1087)

SH967 built the event stream and SH1005 gave it its first console client: the Activity
page opens an `EventSource`, and a `changes` event raises a line offering the new rows
rather than redrawing under the curator. Every assertion about that is a source scan —
the file says `EventSource`, it says `withCredentials`, it does not tear the connection
down on an error.

That is the one shape this repository already knows is not evidence. A stream that never
delivers produces no error, no log line and no failing test; it looks exactly like an
instance where nothing has happened, which on a quiet install is most of the time. The
same reasoning made SH460 refuse a dev-mode instance and SH579 refuse an unexplained
red: silence is not a pass.

The console has the instrument already — `e2e/*.check.mjs`, which caught SH702's dialog
keeping a restore button for a version it was no longer showing. The check is small and
the point is that it is end to end: open the page against a live instance, write one
post through the API, and assert the line appears without a reload. A second one asserts
the negative that costs more to get wrong — that the table did **not** move while it
appeared.

Worth measuring in the same run: what an open tab costs. SH967 bounded an idle pass at
four statements, and a page that holds a connection for as long as it is open is where
that ceiling is actually spent.

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

### LXVIII.12 A router that cannot be blocked (SH1090)

SH1011 asked for a router blocker on the post editor. React Router 7's `useBlocker` —
and `unstable_usePrompt`, which wraps it — call `useDataRouterContext`, and `main.tsx`
mounts `<BrowserRouter>`. Outside a data router that throws, so there is no blocking
navigation anywhere in this console, on any page, for any reason.

What SH1011 shipped instead covers the case its own symptom names: `beforeunload`
catches closing the tab, reloading and leaving the site, and the editor's own Cancel
asks first. What is left is an in-app route change — the rail, the command palette, a
link in the page — which walks away from unsaved work silently.

The change is `createBrowserRouter` plus `<RouterProvider>` in place of
`<BrowserRouter>` and `<Routes>`. It is mechanical and it is not small: every route
becomes an object, the shell's `<Outlet>` stays, and anything reading `useNavigate`
keeps working. The risk worth naming is that a data router changes when a route's
element mounts, which is what SH888's two-shell arrangement and SH907's
outside-the-shell editor are both sensitive to.

The instrument is the deliverable here too: a lint that fails when a page imports
`useBlocker` outside a data router would have caught this before it was written into a
design. The cheaper version is a test that mounts the router and asserts it is a data
router, which is one assertion and cannot go stale.

### LXVIII.13 A partial write no stateless caller can make (SH1091)

SH1011's design asked the CLI for `shio post set`. It was built and then withdrawn,
because all three routes to a partial write are blocked in a different place, and
choosing between them is a security decision rather than an implementation one.

`PATCH /api/v2/post-unified/{id}` is the merge the console uses and the only one
carrying `If-Match`. It refuses a cookieless caller: the path is absent from the
narrowed CSRF exemptions in `ShSecurityConfig`, and `ShStatelessReachabilityLintTest`
records that as deliberate — *the merge exists for the editor's concurrency problem,
several curators on one site, which an agent does not have*. A CLI invoked by a person
against a live instance has exactly that problem, so the recorded reason is the thing to
revisit, not to route around.

`PATCH /api/v2/cda/post/{id}` merges, is stateless, and takes a `WRITE` token — but has
no `If-Match` at all, so it cannot say *refuse this if the post moved under me*.
`post.upsert` declares whole state by design (SH168), which is the failure this verb
exists to avoid.

So the deliverable is the decision: widen the exemption to the narrowed matcher every
other stateless path already uses and update the lint's entry, or give the CDA's merge a
precondition and spend a token. The verb is small once that is settled; the working
command, its help and its conformance scenario were written and reverted in one commit,
so the cost of having tried is recorded here rather than paid twice.

### LXVIII.14 A draft lint that is silent about its own gaps (SH1092)

`ShAgentVerifyService.markup` scans published rows only, and says why: *a draft's broken
link is work in progress*. That reasoning is sound for a site-wide sweep. What is wrong
is that a caller who asks for `state=draft` is never told the rules were skipped.

The response carries one note — *state=draft: the rules reported on DRAFT rows only; the
indexes they check against still read both states* — which describes the opposite
concern. A caller reads `errors: 0, warnings: 0` and concludes the draft is clean. It is
not: `link-broken`, `link-unresolved`, `image-no-alt`, `image-missing-file` and
`html-unsafe` never ran against it.

SH1013 met this from the console and routed around it — the panel takes its missing-alt
count from `/agent/render`, whose digest does inventory a draft's images. That works for
the page in front of a curator and does nothing for `shio verify --state draft`, which
an agent runs over a site before publishing and reads as a verdict.

Two fixes, and the cheap one is not the weak one. **Say it**: name the skipped rules in
`notes`, so a green draft report stops meaning more than it does — P6 applied to a
report rather than to a refusal, and it cannot be wrong. **Or run them**, downgraded to
warnings on a draft, which is a defensible reading of *work in progress* but changes
what an existing gate reports.

The note lands either way, since even a version that runs the rules has to say what its
severity means. Start there.

### LXVIII.15 Two flakes, measured before they are fixed (SH1093)

Two suites failed once each during SH1014 and passed on an unchanged re-run, in code
that commit never touched.

`ShMarketplaceInstallerTest.aPackageInstalls` threw `AccessDeniedException` moving a
freshly extracted blueprint directory between two Windows temp directories. `Files.move`
across directories is a rename where it can be, and on Windows a handle another process
holds for a moment — an indexer, a scanner — makes that rename fail rather than wait.

`exit-path.test.mjs`'s SH440 case measured `shio resolve` at 4057ms against a 100ms
bound, which is undici's `keepAliveTimeout` exactly. The assertion is worth keeping: it
catches an exit that waits a socket out. What it cannot distinguish is a process that
failed to disarm the socket from one descheduled on a loaded machine, and this run had a
Maven build beside it.

Both are the shape agents.md names as the worst kind: *one run in six, never reproduced,
undiagnosable*. SH735 guards the half where a green re-run erases the red one before it,
so the evidence survives and nothing yet asks why.

Neither fix is a retry around the assertion, which turns a flake into a slow pass. The
installer should copy-then-delete when a move is refused, which is what a portable move
is. The exit test should split its two claims: observe that the socket is disarmed, and
keep a wall-clock bound scheduling noise cannot trip.

Measure before fixing — fifty runs each, and record the rate, so *fixed* means a number
moved rather than a run that happened to pass.

### LXVIII.16 A split view is a layout, not a card (SH1094)

SH1014 put the live preview in the editor and left it where every other panel is: a card
in a vertical stack, under the fields it previews. So looking at the page means
scrolling away from the form that changes it, which is most of what a split view is for.

The design asked for two panes with a ratio the curator sets and the console remembers.
That was not built because it is a change to the page's **shape** rather than an
addition to it. The post editor is a stack of `AdaptiveSectionCard`s inside SH901's
bento hero, SH907 puts it outside the shell, and the design system has rules about what
a page may look like — so a two-pane layout is a question for `viglet-ds-pages` before
it is a question about this editor.

Three things it has to settle. **Which panes**: form and preview is the obvious pair,
but the insights panel and the note rail are also read while typing, and a split serving
one and not the others will be argued about again. **Where the ratio lives**: per
curator, and `localStorage` is honest for a per-viewer convenience that is not content.
**What happens at phone width**: a split view is not one, and the answer cannot be that
the preview vanishes silently.

Worth doing after a second page wants the same shape. One page needing a layout is a
card; two is a layout.

### LXVIII.17 shio preview, the rung files did not get (SH1095)

Block I's claim is that `shio/content/**` is a complete description of a site and that
files beat APIs for authoring (P4). An agent edits `index.md`, runs `shio push`, and the
page changes. What it cannot do is see the page **first**.

SH1014 built the half that makes this cheap: `POST /api/v2/agent/render` takes an
address and a document and renders through the real page render service without
persisting. `shio preview <file>` is that call with the projection in front of it — read
the Markdown and its sidecars, turn them into the same `data` map `push` would send,
post it, and write the markup somewhere the author can open.

Three decisions it should make rather than inherit. **Which address**: the file's own
front matter names the post it projects, so no flag is needed for the common case, and a
`--address` override is for the file that has not been pushed anywhere yet. **What it
prints**: a path to an HTML file by default, because a terminal is not a browser, with
`--digest` for the structural answer an agent wants and `--open` for a person. **What it
does with an unpushed file**: refuse, and say that the render needs a page to render
*at* — a friendly URL, a layout and a site — which is a fact about the instance, not
about the file.

It is the one rung of P1's ladder this capability is missing: MCP has it, REST has it,
the console has it, and files do not.

### LXVIII.19 A rename the curator can do (SH1064's other half) (SH1132)

SH1064 went looking for the console's rename dialog and found there is none. A post's
friendly URL is its identity (SH81), and the only writer that changes one is `post.move`
on the agent surface: `ShPostUnifiedWriteService.setFurl` has exactly two callers, both
inside the batch service, and the console's own `PUT /post-unified/{id}` takes `data`
and a state and nothing else. A curator who wants `/pricing` to become `/plans` has to
ask an agent, or create a second post and delete the first, which is the data-loss path
`post.move` exists to prevent.

The one case where the console does see a URL change is a rollback: restoring a revision
puts that revision's `furl` on the draft, so the live row and the draft disagree until
the next publish. That is why SH996's publish plan reports `moves` and `stops` at all,
and it is where SH1064's redirect offer landed.

The shape is the one the batch already has: a dialog on the post editor carrying the new
URL, the inbound count `ShPostLinks` computes, and SH1064's redirect offer — composed
onto `post.move` through `ShAgentBatchService` as SH1021 composed the bulk verbs, never
a console writer that would define a move twice. The open question is whether it renames
the draft alone or both rows: the op moves both and the live URL changes immediately,
which is right for an agent applying a plan and is the surprise P7 forbids for a curator
who has not published.

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

### LXIX.10 The shortcuts sheet takes the bound keymap (SH1097)

BentoShortcutsDialog takes open, onOpenChange and isMac, and renders a fixed sheet
naming the shortcuts the package believes a bento shell binds. Shio binds Ctrl/Cmd+K
and, since SH889, a bare slash; it has never bound the question mark the sheet
advertises as global. The one reference a curator is pointed at is wrong both ways: it
promises a key that does nothing and hides the key that opens what they are reading
about.

The fix is a keymap the product passes as data, which makes the sheet a projection of
what is bound rather than a second declaration of it — the shape this console already
uses for its rail and its palette, where the surface declaration is the one list and
everything else reads it.

That is a design-system change, not a Shio one: the component takes no such prop, and a
Shio-local copy would be the fork the package exists to prevent. SH1028 shipped the
palette half and left this, because a console shipping its own sheet has two the moment
the package grows one.

What lands here once the prop exists: the shell passes its own bindings — the same array
its keydown handler reads, so a binding added without a row is impossible rather than
merely noticed — and a test holds the two against each other. Until then the sheet is
wrong in a way no reader can see, which is the argument for filing it rather than
leaving it in a commit message.

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

### LXX.8 Data is a large object on PostgreSQL, and nothing that reads it as text works (SH1099)

`ShPostUnified.data` is `@Lob String`. On H2 and MariaDB that is a text column and every
predicate over it behaves. On PostgreSQL pgjdbc reads `@Lob` as a *large object*: the
`text` column stores an OID, the JSON lives in `pg_largeobject`, and Hibernate resolves
it on read so the application looks entirely correct. Measured on a 5,000-post
PostgreSQL 16 fixture: 5,001 rows, 5,001 large objects, every `data` value matching
`^[0-9]+$`, and the entity read returning the right document.

Two shipped surfaces were wrong rather than slow, and **SH1034 removed one without
meaning to**: `listUsage` reads `sh_object_reference` now, written from the entity at
write time, so it never touches this column and is correct here. That is moving the read
off `data`, not repairing it. What remains is `FULLTEXT_PREDICATE` — `lower(cast(p.data
as string))` — so console and CDA search find nothing in a page's body, which is what
SH240 added it to fix.

It is invisible to the whole unit suite because that suite is H2, and the cross-database
ITs assert schema shape rather than column semantics — the gap agents.md names when it
says a green `clean test` means least on a storage change.

The fix is a JDBC type, not a changeset: `@JdbcTypeCode(SqlTypes.LONGVARCHAR)` beside
the `@Lob`, which makes pgjdbc write the text inline. Existing rows need a migration
that resolves each OID and writes it back, plus `lo_unlink` — until then every post also
leaks a large object that deleting its row does not reclaim.
`ShPostgresAssetUsageScanIT` asserts both halves.

### LXX.9 The reference tables from the model before this one (SH1100)

`ShReference` and `ShReferenceDraft` are pre-unified tables, and nothing in `src/main`
writes either. The repository has no callers, and the only mentions of the type are
three comments saying why it cannot be used: it joins `object_from` and `object_to` to
`ShObject`, and a post is not an `ShObject`. `ShTemplateGraph` says so from the render
side, recording a design it had to correct: extracting slot edges into this table was
proposed and is not available. SH1034 hit the same wall from the asset side.

So the schema carries two tables holding edges nobody can produce, and they are not
inert: both are in `ShTenantBackfillRunner.TENANT_TABLES`, so every start-up backfill
issues an UPDATE against them, and the PostgreSQL and MariaDB schema ITs assert they
exist with a `tenant_id` column: three instruments maintaining something with no writer.
A reader asking "where do references live" finds them first, which is how that
render-side design went wrong.

Not a data migration — there is nothing to move, which is the point. Drop both tables,
remove the entities and the repository, take both names out of `TENANT_TABLES`, and
settle whether the exchange format mentions them: an import reading a 2018 package is
the one caller that might, and that question belongs before the drop.

Deliberately not folded into SH1034: a table nobody writes is safe to leave for a task
that can answer the exchange question on its own evidence, and a removal inside a
feature commit is the shape that gets reverted wholesale.

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

### LXXI.8 A copy that can be addressed (SH1101)

Duplicate in the visual editor repeats the reference rather than the post, because the
array holds addresses and one replacement of it can only say "place this again". The
console names that in the control's own title, so nobody is surprised by it, and it is
still not what a curator means: they want a second hero they can edit without touching
the first.

An independent copy needs a post copy whose id the caller can read, and no surface
answers one. `PUT /api/v2/object/copyto/{dest}` builds the copy correctly, bytes and a
free title and furl included, and then returns only the folders it made: `copyPost` adds
nothing to that list, so the endpoint answers `[]` for a post. Anything that copies one
and then has to write about it is blocked the same way, which is why this is filed
against the write path rather than against the panel.

So the copy verb answers with what it made, in the shape a create does, and the editor
duplicates a section as two writes: copy the section post into its own folder, then
replace the Page's array with the new address beside the old one. The address is the
copy's own, which keeps the entry in the format the rest of the array uses. The
assertion is that two placements of one section share a heading and two copies do not.

### LXXI.9 A rail you can see, and something to drop onto (SH1105)

The rail names each accepted type and describes it, which is already the difference
between a list of post-type names and something to place. It is still the wrong medium
for the choice being made: a curator picking between a hero and a rich-text block is
picking a shape, and a sentence is the worst way to show one.

SH1039's design named the answer and the constraint together. A thumbnail is the preview
route rendering that type against a fixture of placeholder values — **HTML, not a
raster**, which keeps it inside the non-goal that the instance does not run a browser.
Same render path a page takes, so it cannot show a section the site would not draw, and
cacheable by type and theme.

The caret is the other half and belongs with it. Placement today is two buttons, above
and below the selection, which states *where* and needs no drag at all. What a drag
needs is a drop target between sections, and the console can draw one from the geometry
the bridge already sends for the overlay. They are one piece of work because the caret
exists to receive something a visual rail can be dragged from; buttons over a text rail
need neither.

The assertions are that a thumbnail renders through the preview path rather than a
second one, and that a drop between two sections writes the same array a Below button
writes.

### LXXI.10 The allow-list read by something that is not a screen (SH1106)

`sh-accepts` is read by the console, which offers only the types it names, and by
nothing else. An agent writes a Page's sections array through `post.upsert` and no check
consults the layout, so a page can name a section of a type its own template says does
not belong there — and the page still renders, because the template draws whatever the
array resolves to.

SH1039's design proposed refusing that write, with the allowed list. **That is the wrong
instrument**, and the reason is what a narrowing costs: the allow-list lives in a
layout, and layouts are edited. Refusing on write would make an existing page unwritable
the moment somebody narrowed its template — a content change refused because of an edit
nobody connected to it. It also misreads the declaration: it is a *template's statement
about composition*, not an authorization boundary, and nothing about the agent path is
less trusted than the console's.

So it is a finding rather than a 400, beside `dangling-reference`, which is the same
shape one field over: content that resolves and should not have been pointed at. `shio
verify` already walks a site's pages and already resolves their references; what it does
not do is read the layout the page renders through, which is a static resolve through
`site.postTypeLayout` and needs no render.

The assertion is a page naming a section of an undeclared type, reported once, with the
accepted list in the finding.

### LXXI.11 A pattern the console can see (SH1108)

A pattern is `shio/patterns/<name>.json` in the project, and the console is a browser
talking to an instance. It cannot read that file, so the Patterns tab SH1040's design
put beside the section types has nothing to list and Save as pattern has nowhere to
write. Capture and placement are a CLI verb, while the person most likely to know a
block is worth keeping is the curator who just composed it.

The way through is already built and was mistaken for a blocker. The marketplace
installer expands an uploaded zip into `shio.blueprint.path` — an operator directory the
registry loads beside the classpath — and reloads, rolling back a package that will not
load. So a pattern reaches the server the way every third-party package does: `shio
patterns push <name>` packages the file as a minimal blueprint and installs it. Nothing
new stores anything.

Two decisions the build makes rather than inherits. A package is **named**, so a pushed
pattern needs a prefix keeping it out of the catalogue a curator scrolls, and the
console needs a way to ask for patterns rather than for every package. And a pushed
pattern is a **copy**: the file stays the authority, and one edited on disk and not
pushed is stale on the instance, which the tab says rather than letting a curator find
out.

The assertion is a pattern captured, pushed and then placed from the rail, with the
resulting section array matching the one the CLI writes for the same pattern.

### LXXI.12 A save that says what moved (SH1109)

A save in the visual editor returns a toast. Every other surface closes its loop with
cheap textual proof — P9 is the law — and the one where a person just changed content by
hand ends with the word *saved* and a page they have to look at to believe.

The parts are built and unjoined. `/agent/render` answers a structural and an appearance
digest for any address, SH1041's change log holds an ordered set of edits keyed by post
and field, and SH972's dock is where a console surface reports. What nothing does is
take a digest pair before the write and another after it, and say what moved.

The delta is the deliverable, not the digests. Two hashes side by side tell a reader
only that something differs, which they knew — they pressed Save. One line naming what
changed, and whether the appearance moved when only text was edited, catches the defect
this editor is most likely to produce: an inline edit that reflows a layout nobody meant
to touch. Measured first, because two extra renders on a large page is a save a curator
waits for, and that number decides whether the pair is taken always or on request.

The log also serialises into the change-set shape the agent path answers with, so what a
curator did reads like what an agent did. The assertion is a save whose delta names the
field that moved, and a text-only edit reported as leaving the appearance alone.

### LXXI.13 A share link that is one page wide (SH1110)

A share link is minted for one page and bounded to that page's **site**, so the
stakeholder sent a link to the new pricing page can read every draft on that site until
it expires. The mint says so and the credential is ephemeral, which is why this shipped
rather than not at all — but it is more than the link promises.

The token row has no target column, so the narrowing is schema: a nullable path prefix
beside the site allow-list, matched against the request. A prefix rather than an SH79
address on purpose — the check runs where a security decision is made and must not
resolve content to reach a verdict, and a prefix expresses post, folder and site in one
comparison.

What the design did not settle, and what makes this a task rather than a column: **a
shared page's own sub-resources.** A page pulls images, a stylesheet and a region's
script, several rewritten into the preview's own address space, so a credential confined
to `/about` refuses the page's own pictures. The answer is one of three — leave assets
to the site bound, serve them under a path the prefix covers, or mint against the set
the page references — and choosing costs a measurement of what a real page pulls.

The assertion is a link for one page refused on another page of the same site, and the
shared page rendering whole: every image, the stylesheet and the banner.

### LXXI.14 Which links are live, and `shio share` (SH1111)

A share link is created and then nobody can see it. The token listing hides ephemeral
rows on purpose, so a curator's token screen is not a list of fifteen-minute credentials
— and a share link is ephemeral by construction, so the screen for looking at
credentials is the one place this one never appears.

That is the wrong half of the trade for a link that was **sent to somebody**. An expiry
bounds the damage and answers neither question a person has after sending one: what is
live now, and can I stop that one. Revocation already works — `enabled = false` is what
the filter checks first — so the reading is what is missing.

So the listing takes a side: `GET /api/v2/preview-share` answers the live share rows for
sites the caller may read, each with its site, what it was minted for, when it expires
and who made it; `DELETE` on one disables it. The filter is unchanged.

The other half is the sentence at the end of an agent's run. `shio share <address>`
prints a URL, the most useful thing an agent can hand a person after building something
unseen, and it calls the mint the console calls so there is one idea of what a share
link is. `--list` and `--revoke` put the reading and the stopping in the same place.

The assertion is a revoked link refused at the preview, and a listing showing one
curator's live links and not another tenant's.

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

### LXXII.18 The eager total, from the instance that serves it (SH1112)

SH1043 gave the console a number and two readers: a `pnpm` script and a gate that fails
a build. Neither is reachable from an instance, so an agent that changed a console file
and wants to know what it cost needs this checkout, this toolchain and a build — the
shape P1 exists to refuse, one surface along from where it usually bites.

The instance already serves the artefact. `index.html` and its chunks sit under the
Spring Boot static root, so the reading the script makes is available to the server with
no build step and no browser: the entry, the preload links, the stylesheets, their
bytes.

Where it belongs is the open question. SH1043's design said `/agent/diagnostics`, and
diagnostics reports **incidents** — something went wrong, with a count and a fix — while
an eager total is a standing fact about what is deployed. The manifest is where standing
facts live; `verify` is where one becomes a finding by crossing a line. And that line
lives in the repository, so a server reporting a breach needs the ceiling shipped with
the build.

That decides it: a number the caller compares is honest, a verdict needs the ceiling to
travel with the artefact. The cheap version puts the number on the manifest; the
complete one ships the budget into the build and lets `verify` judge it.

The assertion is an agent reading the eager total from a running instance and getting
the bytes the script prints for the same build.

### LXXII.19 A budget per route, from the probe that already walks them (SH1113)

SH1043's gate measures one number for the whole console: what every route pays before
the first row. That is right for a budget and blind to what a curator experiences, which
is a page. A route fetching three collections to draw two figures costs the same eager
bytes as one fetching nothing — SH1050 is that page, and nothing would have caught it.

The instrument is nearly there. `e2e/console-check.mjs` already drives a browser through
every route against a live instance, which is where request count, transfer bytes and
time to first row are observable and nowhere else: the bundle gate reads files on disk
and knows nothing about what a page then asks the server for.

So the probe records three numbers per route and holds them the way the bundle budget
holds its own — ceilings with a sentence each, a raise somebody argues for. The
per-route shape is what makes it useful: a total would let a new screen's cost hide
inside the sum.

Two things to settle while building. It needs an instance, so it belongs with
conformance rather than `pnpm test`, and where it runs decides whether a regression
blocks a commit. And time to first row moves with the machine, so it wants a ratio
against a baseline route rather than a millisecond ceiling nobody can reproduce.

The assertion is a planted extra fetch on one route failing that route's entry and no
other.

### LXXII.20 One walk over every workspace manifest, not the console's alone (SH1114)

SH1045 found eighteen dead declarations in one manifest and left ten unread. The
workspace has eleven packages — `cli`, `shio-client`, `shio-model`, `shio-sections`,
`shio-react-sdk`, `shio-js-sdk`, `create-shio-app`, `claude-plugin`, `shio-site`,
`shio-examples/*` and the console — and four of them are published to npm, where a dead
dependency is not just installed on every CI run here but on every consumer's install.

The reader is written to be lifted. `reachability` is pure over the manifest and three
lookups, and `classify` is the only part that knows this console: `WALK` names `src`,
`test`, `e2e`, `scripts` and `ROOT_FILES` names two configs. Another package's layout is
a different list, so the work is turning those constants into a per-package declaration
rather than writing a second walk.

Two things the console's run does not exercise. Nothing here is published, so nothing
tested what a published package owes: a `dependencies` entry imported only from `test/`
is a consumer's download for a file they never load, and a runtime import from a
`devDependency` breaks on install and passes every gate here. The classifier already
reports which list an entry came from and asserts nothing on it. And `bin` reachability
is read from this `package.json`'s scripts, which is the wrong file for a package whose
binary is what it ships.

The assertion is a dead dependency planted in a workspace package other than the console
failing by name, and the published packages' own runs coming back clean or naming what
they owe.

### LXXII.21 The rest of the uncancelled reads, in slices (SH1116)

SH1047 migrated the five screens its symptom named and left thirty-five in
`fetch-cancellation.test.mjs`'s declared list. The list does its job — a thirty-sixth
cannot arrive quietly — but the gate that records a debt is not the thing that pays it.

Two shapes, and the split is what makes this schedulable. Most are one read keyed on a
route parameter — a post editor, a folder editor, a provider form — and those are
`useQuery` with the signal its `queryFn` is handed, which brings caching with it. Three
are not: the content browser, the activity trail and the media library each keep a
cursor or accumulate pages by hand, which is why SH1047 gave them an `AbortController`
instead. Those want `useInfiniteQuery`, and moving them changes behaviour a curator can
see — a cached trail shows rows that were true a minute ago, which has to be decided
rather than inherited.

The hazard to carry into every one of them is the one SH1047 met: **an aborted request
rejects**, so a `catch(() => setItems([]))` turns a navigation into an empty list.
`isCancelled` in `lib/read-options.ts` exists for that, and the trash needed it rethrown
past a per-read fallback that was there for a different reason. A migration that forgets
it trades a rare race for a reliable blank screen.

Worth doing in slices, since each screen's failure path is its own.

The assertion is the list shrinking with every slice, and the browser probe green after
each.

### LXXII.22 Conditional reads from the CLI, where they are not free (SH1117)

SH1048's design said MCP and the CLI benefit for free, since they call the same REST.
Half of that is true, and the false half is the half worth having.

A browser gets it free: `no-cache` plus an `ETag` means it keeps the body and
revalidates, and axios goes through that cache. Node has none. `cli/src` sends
`If-None-Match` in one place — `clone` probing *external* assets it is copying — and
never to Shio's own API, so every `shio pull`, `verify`, `digest` and `context` pays the
full body against an unchanged instance — the case a CLI is in most of the time.

The shape exists next door. `clone.mjs` keeps `etag`/`lastModified` per row, sends the
conditional `GET` and counts the unconditional ones, which is the accounting this wants:
without a count, a store that stopped matching reads as a server that never answers 304.

What needs deciding is where the validators live. A per-invocation store is useless —
the CLI exits — so it is a file, and files are state the CLI has been careful about:
`shio/` is content, not cache. Somewhere ignored, keyed by instance and address,
invalidated by nothing, since the validator *is* the invalidation.

MCP is a separate answer: it reaches the agent surface, which carries semantic
validators of its own, so the question there is whether a long-lived session keeps one.

The assertion is a second identical read costing no body, counted rather than claimed,
and a stale store failing loudly rather than re-fetching everything.

### LXXII.23 A size verdict about somebody else's build (SH1126)

SH1043's comparison runs against whatever console build sits in
`shio-app/src/main/resources/public`, and `pnpm test` never builds one. So the gate
measures a build of *some* tree, and nothing says which. Seen on consecutive tasks:
SH1059 passed `pnpm test` against a build that predated its own change, and the next
task failed the headroom check only because a build made outside the session happened to
include it. Both verdicts were about bytes the task under test had not produced.

The conformance gate had the same shape and SH460 closed it there: the instance states
its build time and commit, and the harness refuses one older than HEAD. The console
build can carry the same identity. A small Vite plugin writes `build-info.json` beside
`index.html`, holding the commit and a digest of `shio-react/src`, and the size test
compares that digest with the tree it runs in. A match is measured. A mismatch is said
out loud, in the same words as the no-build case, rather than measured, because a number
from a different tree is worse than no number.

A stale build must not fail the suite, or every source edit would demand a one-minute
build before `pnpm test`. The gate that must see the bytes is the one that already
builds: CI's `./mvnw package` without `skip.npm`.

The assertion is a planted mismatch: a synthetic dist whose digest differs from the tree
reports "not measured" and never a total.

### LXXII.24 The seventeen the console lint still holds (SH1136)

SH1075 found the console lint red in CI with twenty errors, fixed three, and recorded
the other seventeen in `eslint-suppressions.json`. Each is declared by name in
`cli/test/lint-baseline.test.mjs` against this task. `lint-gate.test.mjs` now runs
ESLint inside `pnpm test`, so the list cannot grow unseen. It can only shrink if
somebody works it.

Three kinds, and they are not one sweep.

Ten are `react-hooks/set-state-in-effect`: languages, search, the conflict dialog, the
tenant-link notice, the bento root, the history and insights panels, the live preview,
the publish preview and `use-list-state`. SH701 to SH704 settled which fix fits which
shape. A mount effect that raises a spinner starts the read first. A dialog synced to
`open` becomes a keyed remount. Anything else is derived instead of set, as
`static-file.list.page.tsx`'s spinner now is.

Four are `react-hooks/refs`, a ref read during render: the activity list, the live
preview, `use-autosave` and `use-change-stream`. The usual fix moves the read into an
effect or an event handler. For the two hooks, check first whether a state value is what
they meant.

Three are `react-refresh/only-export-components`, in the conflict dialog and
`field-constraints.tsx`. The baseline already calls this kind an API rather than a
defect. Move the helpers to a module only if that reads better. Otherwise declare them
permanent there, with the reason.

Pay a file off by deleting its lines from both the suppressions file and DECLARED in the
same diff. A page fixed for `set-state-in-effect` joins PAID.

### LXXII.25 A folder that crosses sites leaves its pages behind (SH1137)

Found by a run during SH1078, not by reading. A folder holding a published post was
moved with `PUT /api/v2/object/moveto/{root}` from one site's root into another site's
root. The move succeeded. The folder's parent is now the new site's root. The post's
rows still carry the old site in `shSite`.

Delivery resolves a page by `findByShSite_IdAndFurlAndState`, so the page keeps
answering under the old site while the tree shows it under the new one: the split the
`ShFolderSite` invariant exists to prevent. `requireUrlFree` never runs for posts
beneath a folder, so the destination can also end up with two posts at one URL.

`ShFolderWriteService.move` is the shared writer, so the agent's `folder.move` probably
has the same hole. Check that with the same run before assuming it.

Two ways to close it. One: refuse a folder move that crosses sites, with a teaching
error naming `shio export` and `shio import`. Two: carry the subtree over, re-pointing
every post row's `shSite`, checking each friendly URL against the destination, and
treating the retired addresses as SH1064 treats a rename.

Refusing is smaller and cannot surprise a curator (P7). Carrying over is what a curator
dragging a folder between sites expects. Either way, SH1078's route delta already
reports what delivery actually does, so it becomes the assertion: after the fix, a
permitted cross-site folder move lists each published post's retired and created
address.

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

### LXXIII.8 The chip that cannot say which language is the original (SH1125)

SH1059 put each post row's locale on a chip beside the post-type badge and named the row
by it, so a page and its translation are two rows. Its design also asked that the chip
mark the site's default locale "where the site's enabled set exists", and it did not
exist then: the console could not read a site's policy until SH1056 gave it `GET
/api/v2/site/{id}/locales`.

It can now, and the browser still does not ask. So a folder holding a page in English
and in Portuguese says which row is which and not which one is the original, which is
the question a curator deleting one of them is actually asking.

The browser already resolves the folder's site on every navigation (`/path` answers it),
so this is one read of the policy per site the curator opens, cached under the same key
the query layer uses for the site, and a word on the chip whose tag equals
`defaultLocale` when the policy is declared. An undeclared policy's default is a
description of the content rather than a choice, so it is not marked: a chip saying
"default" about something nobody chose would be the same confusion SH1056 exists to
remove.

The assertion carries the id: with a declared default of `pt-br`, the `pt-br` row's
accessible name says it is the default and the `en` row's does not.

### LXXIII.9 A switcher entry for a page nobody published (SH1128)

A post's `availableLocales` is read from its whole translation group, and nothing in
that read asks for a state. `translate` creates the sibling as a DRAFT, so the moment a
curator starts a Portuguese translation, every production token's read of the English
page says it exists in `pt-br`. A switcher built from that field links to a page the
same token gets a 404 for, and it stays that way for as long as the translation is being
written.

SH1123 fixed the same defect one level up: a site's derived languages are read from the
states the token can see, so a production switcher is never offered a draft-only
language. The post-level field kept the old rule, and SH1129 carried it into GraphQL
deliberately, so the two surfaces agree while this is open rather than disagreeing in a
way nobody filed.

The fix is the one SH1123 made: the group's locales come from the rows in the states the
reader reads. `PUBLISHED` for a production token, both for a preview token. The batch
query SH1129 added takes the states as a parameter, and the single-post path moves onto
it so there is one reading of the rule instead of two.

The assertion carries the id: a published English page with a draft-only `pt-br` sibling
reports `["en"]` to a production token and `["en","pt-br"]` to a preview token, on REST
and on GraphQL.

### LXXIII.10 A promised language nobody wrote (SH1130)

SH1057's design ended on a verify finding for "a route that accepts a distinction it
does not honour", and it shipped without one, because the route now does honour the
distinction: a locale segment selects the translation, and a missing translation falls
back along the site's chain.

What is left for verify to see is the case the fallback makes invisible. A site declares
`["pt-br","en"]` and has written nothing in `pt-br`. Every `/pt-br/` URL answers 200
with the English page, because that is what the chain is for, and nothing anywhere says
the site offers a language it has not published a word in. The switcher shows it, the
SH459 check accepts it, the reader gets English under a Portuguese address.

A warning, `locale-declared-unwritten`, on a site whose declared policy enables a locale
no published post is written in. Its two exits: write the pages, or drop the locale from
the policy. The mirror belongs in the same pass: `locale-undeclared-content`, a
published post in a locale the policy does not enable, which no switcher will ever link
to.

Both read the policy the column declares and the locales the published rows carry, which
is one query per site, and both are silent on an undeclared site, whose languages are by
definition the ones its content is in.

The assertion carries the id: a site declaring pt-br with only English content reports
the first finding, and after one pt-br page is published it does not.

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

### LXXIV.8 A docs gate that can come back clean (SH1102)

`roadkeep lint` reports six `code.renamed` findings on ledger entries that were correct
when they were written, and has done so across many commits. Three symbols between them:
document.fonts and document.referrer, which are the browser's and not this package's,
and schema.graphqls, which is a filename. The rule reads a dotted name **in backticks**
as a member of a symbol this repository ought to define, finds no document and no
schema, and calls it drift. They are spelled bare here for that reason: backticking them
in this very section fired it three more times.

The cost is not those six. It is that the gate cannot come back clean, so the next
finding — a class that really was renamed, a pointer into code that moved — arrives at
the bottom of a list nobody reads to the bottom of. That is SH579's rule about a red
suite one file along: a check that always fails has stopped being a check.

Two ways out, and choosing is the task. Reword the six, which this project's own
configuration argues against: `[rules.changelog]` already declares those entries are
paragraphs written before the tool existed. Or declare what the rule must not read as a
symbol — the browser globals, and a dotted name whose tail is a file extension — which
is a roadkeep change and belongs upstream. The assertion is that `lint` exits clean here
and still fails on a symbol this repository really has renamed.

### LXXIV.9 A stamp that knows what it ran against (SH1103)

A gate stamp records the HEAD it ran at, and staleness is that sha against the current
one. That catches the run somebody skipped. It cannot catch the run that is no longer
about this tree, because `ship` writes ROADMAP.md, CHANGELOG.md and IMPROVEMENTS.md at
the same HEAD the green run was taken at — so the stamp stays fresh while the thing it
certified has changed underneath it.

That is not a corner. The documented order is implement, run the gates, ship, commit,
and the JS suite reads the ledger: `coverage-obligation.test.mjs` counts shipped ids
against the mirror and `red-suites.test.mjs` reads the roadmap. So a ship can turn `pnpm
test` red after the last green run of it, every time, by construction. It happened on
SH1038: the suite was green, the ship made the sixth unnamed id against SH799's ceiling
of five, and the commit landed over a red nobody could have seen.

Either the stamp carries what it ran against rather than only where — a digest of the
inputs the gates read, so a write between the run and the commit reads as stale — or the
gates run after the ship, which costs a second full run on every task. The first is
cheaper and says more: a stale stamp already prints the run somebody owes, and this
makes it print for the case it misses. The assertion is that a ship between a green
stamp and a read of it leaves it stale.

### LXXIV.10 A ceiling on the catalogue (SH1104)

`ShAgentResponseBudgetTest` measures sixteen responses and treats a measured one with no
declared ceiling as a breach in itself. `/agent/editable` is not among them. It never
was: SH809 shipped it and the only trace in `token-budgets.properties` is a note raising
the *manifest* ceiling because the endpoint index grew by a line.

That mattered less while the answer was a field list. SH1039 gave it
`include=insertable`, whose size is the declared catalogue — every accepted type with
the fields a properties panel would draw — so the response now scales with what a layout
declares rather than with what the page has. The precedent for exactly this shape is
already in that file twice, SH987's `read.terse.revisions` and SH989's
`changes.terse.fields`, both given their own key on the same argument: a shared ceiling
lets the default grow into the room the option was given.

The obstacle is the fixture rather than the rule. That test's pages have no layout
binding, so `editable` there answers the `source: content` fallback and a measurement of
it would be thirty tokens of envelope — a number that passes forever and says nothing.
So the budget needs a page that renders: a layout, a container declaring what it
accepts, and a couple of section types provisioned. The assertion is two keys, one for
the plain read and one for the catalogue, each measured against a page that actually
composes.

### LXXIV.11 A blueprint nobody applies is a blueprint nothing checks (SH1121)

`checks=a11y` found two rules broken by `marketing-site`'s layout — no `<main>`, and a
first heading of `<h2>` — and that one was repaired in the same commit, because the
headline benchmark applies it and went over its token budget. `landing` has exactly the
same defect and no benchmark, so nothing reported it: its PageLayout emits `<html lang>`
and a `<body>` of sections, no main region, and no `h1` anywhere on any page it builds.

Three of the six blueprints ship a PageLayout. `blog` is clean, `marketing-site` is now
clean, and `landing` is not. The other three — `docs`, `product-catalog`,
`nextjs-starter` — carry no layout of their own, and the starter's markup lives in
`files/app/layout.tsx`, which no Shio check renders. That is worth confirming rather
than assuming.

The repair follows the sibling's: wrap the section loop in `<main>`, and choose `<h1>`
for the first section's heading through the `@first` data frame `getRelation` has
published since SH772. It is small, and the reason this is a task rather than a typo is
the second half.

**A blueprint nobody applies is a blueprint nothing checks.** Every appliable unit this
product recommends should be provably clean against our own gate, and the way to know
that is an assertion rather than a sweep: apply each blueprint that ships a layout,
render a page, and assert `a11y` reports nothing. That fails on the blueprint added next
year, which a fixed list of three names cannot.

### LXXIV.12 The site marker file the projection never had (SH1122)

The tree holds content, folders, assets and a snapshot. A site root is a folder, so it
gets a `_folder.md` — carrying `site` and `folder` and nothing about the site.
Everything a site *is* lives only in its row: `url`, `furl`, the bindings that decide
whether a page renders at all, `searchablePostTypes`, `formSuccess`, and now SH1056's
locale policy. None of it pulls, pushes or diffs.

Found by SH1056, whose design asked for "a locales block in the site marker file" — a
sentence that reads as a field on an existing file and is a subsystem. The locale half
shipped against the API and stopped there, and every later task wanting a site-level
setting in the tree stops here too.

It is P4 with a hole in it: the one object an agent must configure before a page can
render is the one it cannot edit as a file. Measurable in the replication chain — a
clone reproduces content and re-derives bindings through an API call, so two instances
agree about posts and cannot be diffed about sites.

The shape is settled by what exists: a `_site.md` beside `_folder.md`, the same
serializer with its own fixed `META_ORDER`, the same three-way merge. One thing is
undecided — whether bindings are keys there or stay an API-only edge, since a binding
naming a layout by title is a reference the tree cannot check.

The assertion is a round trip: push a site, change a field on disk, pull, read the diff.

### LXXIV.13 A redirect the front end never hears about (SH1064) (SH1133)

A `Redirect` is served by `ShPageRenderService`, which answers `/sites/**` — the
instance's own delivery. A site with a front end does not go through it: Next.js owns
those URLs, asks the CDA for content by address, and never asks which addresses moved.
So SH1064's op, the console's publish-dialog offer and `verify`'s `url-orphaned` all now
write and demand a post that, for a headless site, changes nothing a visitor can
observe. The lint is the sharpest edge: it reports an error whose fix writes a row
nobody serves, and then goes quiet because the address resolves.

Nothing in the delivery layer knows the type. `@viglet/shio-client` has no redirect
call, `create-shio-app`'s template has no middleware, and the CDA has no listing —
grepping all three for the name returns the CLI and the converter only.

What this needs is one read and one consumer. The CDA gains the site's redirect table,
the SDK a `redirects()` that returns it, and `create-shio-app` a `middleware.ts` that
consults it before a 404 — the map is small, site-scoped and cacheable, so the front end
can hold it rather than ask per request. `verify` then stops claiming a repair it cannot
make: with a front end configured, `url-orphaned` says the redirect has to reach the
front end's own routing, and `shio redirects` prints which of the two serves each row.

The alternative — teaching the renderer to answer for a headless site — is not one: that
is the delivery split the product is built on.

### LXXIV.14 A hand-over with no bytes in it (SH1065) (SH1134)

The exchange format has carried binaries since SH68: `ShExchangePackageCodec` writes a
`files/` directory with one entry per `ShExchangeFile`, named by id, and
`ShSiteImportService` hands `pkg.files()` straight back to the writer that restores
them. A **site** export does not use any of it. `ShSiteExportService.buildPackage`
passes `List.of()` for that component, so the zip holds `export.json` and nothing else,
and the posts inside it include every `File` post the site had — each pointing at bytes
the receiving instance has never seen.

Nothing reports it. The import succeeds, the pages render, the images are broken:
SH767's shape, where it looks authored and the symptom is somebody opening the page.
`verify` would say so on the target, if anybody ran it there.

Two ways to close it, and choosing is the task. **Carry the bytes**: collect the `File`
posts while walking the folders and write each through the codec's `files/` path, which
the existing import already restores — the cost is a zip the size of the media library.
**Or refuse to pretend**: keep the package textual and have the export say, in its
answer and in `shio export`'s output, how many File posts it wrote without bytes.

The first is what a hand-over means. The second is honest and cheap, and is what
SH1065's boundary paragraph currently documents as the deliberate shape — the paragraph
says it carries no bytes, but nobody decided that; it is what the code does.

### LXXIV.15 The replication round the instance never hears about (SH1067) (SH1135)

SH1067 classified replication and left one thing out on purpose: a curator who finds a
page worth replicating still cannot start from the console, and nobody can ask an
instance what replications have been run against it. The capture half cannot move to the
server — a crawler behind an endpoint is SH101's request-forgery surface, and "the
instance does not run a browser" is a standing non-goal — so the naive shape, a request
the instance records and something executes, is a queue whose worker does not exist.

What could exist is the **record**. A round is a sequence of artefacts on disk —
inventory, proposal, plan, conversion — and the instance sees only the site that came
out. `shio convert` could report the round it just applied (source URL, page counts, the
plan's digest, what was accepted) and the instance could hold it beside the site. A
console entry then answers what a curator opening an unfamiliar site asks: where did
this come from.

Starting a run from the console is the harder half and should be argued separately: it
means either a worker the product does not have, or a console that hands the curator a
command to run. The second is honest and unglamorous, and it is what `shio remember`'s
notes already do for other hand-offs.

Open question this must answer first: whether the record belongs in instance memory
(SH161, which already travels with an export) or in a table of its own.
