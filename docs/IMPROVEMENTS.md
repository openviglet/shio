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

### XI.28 Shio in the Claude Code directories (SH1245)

The plugin exists (`claude-plugin/`, installable with `claude plugin marketplace add
openviglet/shio`). So does the bare MCP bridge (`npx -y @viglet/shio mcp`). Nothing
outside this repo points at either.

This quarter, the owner's visibility plan reaches Claude Code users through claude-tray.
It already has:

- the awesome-claude-code submission drafted (VS6 in the visibility repo);
- a "More tools by alegauss" section in seven READMEs (VS9).

Shio is the one Viglet product built for that audience, so it is the cheap link from
that launch.

The directories, each a separate entry:

- the official MCP Registry, a `server.json` for `@viglet/shio`;
- the Claude Code plugin and MCP lists the visibility repo already tracks for claude-tray;
- the "More tools" section, with a line for Shio.

Claude drafts each entry with the facts checked against the plugin and the npm package.
The owner submits each one under his account. It is done when each entry is live, with
its link recorded in the visibility repo's `actions.json`.

Measure: weekly npm downloads of `@viglet/shio`, and referrals to `/claude-code/`.

### XI.85 One way to edit a page (SH1319)

The post editor is a form with a live preview pane beside it; the Universal Editor is
the rendered page with editable regions, a composer since Block BF, reached from its own
route and its own site list. Both save the same post. A curator opening a page from the
content browser lands in the form; one opening the site in the editor lands in the page.
The two have different save models (SH335), different language switching and different
review affordances.

Direction: pick one primary and make the other an explicit mode of it. The likely
primary for a curator is the page itself (what the visitor sees, click to edit), with
the form available as a side panel for fields the page does not render (SEO, dates,
references); the form-first editor stays the builder's default for types with no
rendered page (menus, strings). One entry point: opening a page from anywhere goes to
the same place, and the mode switch keeps the selection.

This is a decision before it is code. The deliverable is the decision recorded in
`docs/agents/console-ui.md`, judged against SH1315's tasks 1, 3 and 6 measured in both
editors, and then the merge, filed as its own tasks.

### XI.95 Where /bento/admin goes (SH1330)

Found by the console check run for SH1313 on a clean instance (2026-10-30). The
`bento-admin-hub` probe expects `/bento/admin` to redirect to `/bento/admin/users`. The
console now lands on `/bento/areas/administration`, the area hub SH1193 introduced. One
of the two is stale. Either the probe kept a redirect the hubs replaced, or the route
regressed a promise somebody still relies on (a bookmark to `/bento/admin`).

Decide it from SH1193's own record of what `/bento/admin` should open, then update the
probe's `redirectsTo` or the route to match. Read `bento-nav.test.mjs` first, since it
holds the router's routes. Done when the probe passes and the expectation it holds cites
the task that set it.

### XI.96 The post editor's panes at desktop width (SH1331)

Found by the console check run for SH1313 on a clean instance (2026-10-30). The
`split-pages` probe reports: "SH1094: /bento/content/post/<id> at 1440px shows 3 pane(s)
and 3 tab(s), not 2 and 0". The translate page measured 2/0 as expected, and at 390px
both pages measured 1 pane and 2 tabs. So at 1440px the post editor draws a third pane
and the tab strip that should appear only on narrow screens. The third pane is likely
the note rail or the review panel, which SH1094's two-pane rule predates.

First decide whether three panes at 1440px is the intended layout now. If it is, the
probe's expectation is what changed, and it should say why. If not, the editor's split
should hold two panes and no tabs at desktop width. Done when the probe passes with an
expectation that cites the task that set it.

### XI.97 The managed-by-code banner's contrast (SH1332)

Found by the console check run for SH1313 on a clean instance (2026-10-30), on
`post-type-edit:BuildNote` and its bento twin. axe reports `color-contrast` as serious
on `.text-violet-700/80`. That is the "managed by code" banner in
`post-type.edit.page.tsx` (`border-violet-200 bg-violet-50 ... dark:bg-violet-950/30`).
In the dark theme its body text is violet-700 at 80% on a near-black ground. Any post
type a `shio push` or the API created shows this banner, so every code-managed type's
editor fails axe. The provisioned fixture only met it because SH1313's check created
one.

Fix: give the banner the console's own tokens or a dark-theme text colour that passes
4.5:1, and measure it in both themes. Note SH1328: an opacity-modified colour class may
not be generated at all, so read the computed colour, not the class. Done when both
probes pass axe on a code-managed type.

### XI.98 A new page as a child of the selected one (SH1333)

Split from SH1316, whose design asked where a curator creates a page and answered "in
the tree, as a child of the selected node". The pages view at a site's root (SH1316)
offers one New page link, which opens the new-post form in the root folder. A page meant
to live under `/about` therefore lands at `/x` and has to be moved, or the curator
switches to Folders and finds `about`'s folder.

The obstacle is data, not layout. The view's rows come from `/agent/find`, and a find
row carries the address, type, title, locale and publication, but not the folder. The
new-post route needs a folder id (`/content/folder/<id>/post-type/<type>/new`). There
are two ways to get it. One is to add the folder (its path, or its id behind `ids=true`)
to the find row, opt-in so a default read costs nothing (P3), which also helps an agent
placing a sibling. The other is to resolve it per click with `/agent/read`, which
already returns `folder`. The first composes better, since the tree is drawn once from
one read.

Then each page row gets a "New page here" action, and the new post's URL starts under
the parent's path. Done when a page created from `/about`'s row is served at
`/about/<slug>`, checked live.

### XI.101 Failed checks in the home inbox, measured first (SH1336)

Split from SH1318, whose design listed "pages with a failed check (verify findings,
missing alt text)" among what the home inbox shows. Home now names pending agent runs,
open notes and the curator's own unpublished drafts, each linking to where it is
resolved. A page that fails a check is not there.

The obstacle is cost, not data. `/agent/verify` renders pages to answer `page` and
`a11y`, so calling it from the home page on every visit would put a render pass on the
console's front door. P3 makes that a measured question before it is a design one. The
cheap half is already counted elsewhere: the media library's needs-alt chip (SH1031)
reads `asset-no-alt` per page of the library, and verify's `content` and `routes` checks
do not render.

Direction: measure `/agent/verify?checks=content,routes` against a site of the
showcase's size and decide from the number. If it is cheap, the summary read
(`/v2/console/summary`) carries a count of error findings with the first few addresses,
and the inbox lists them as items linking to the page's insights. If it is not cheap,
the summary carries the last verify run's result, which the publish gate already
produces, rather than running one. Done when a page with an error-level finding appears
in "Waiting for you" as a link to it, checked live, with the cost measured and recorded.

### XI.103 The way to a new page (SH1338)

Measured by SH1315's `create-page` after SH1326 (form publishes): six clicks over six
screens, and four of the six screens are the way to the form. The rail's Content opens a
hub; the site list is one card on it; the site opens on its pages; its New Page opens
the form. Only then does the curator type a title and press Publish… and confirm.

The site list as a hub card is SH1193's grouping, and it is right for a console with
many surfaces; what is missing is a direct route for the most common job. Two
candidates, both cheap and both P1-neutral (they add a way in, not a new write):

- the command palette offers "New page in <site>" when the reader has one site, or when the
  current route is inside a site, routing to the same new-post form (palette commands already
  route to forms, never write: SH1028);
- the Content hub names the reader's sites directly when there are few (one to four), so the
  Sites card is skipped.

Judged by `create-page`, which should reach about four clicks over three screens. Record
the budget when it drops, and keep the task's start at /bento (SH1315's rule), so the
saving is real and not a deep link.

### XI.104 A menu item's live preview asks for a render that cannot exist (SH1343)

Found by the strict console check run for SH1329 on a clean instance with the
marketing-site blueprint (2026-10-30). The `post-edit:MenuItem` probe fails on `400 POST
/api/v2/agent/render?format=html`, and the answer is a teaching error: `Site 'Console
Check' binds no layout for post-type 'MenuItem'`, `allowed: ["Page"]`. The Menu, Page
and every section editor in the same run pass.

`PostLivePreview` (`src/components/post-live-preview.tsx`) asks for a render only when
`renderable === true` or an `on` page is known (SH1167, SH1323), so one of the two gates
let a MenuItem through. Either `renderable` reads true for a type the site binds no
layout to, or `useUsedOn` returns a page that uses the item and the server's `on` path,
written for sections (`ShAgentRenderService`, SH1323), refuses a MenuItem drawn through
a Menu rather than through a page's sections field. Reproduce first: open the item in
the console, read the request body, and see which gate it came through.

Fix at the gate that is wrong. If the `on` path is the one, it should either draw the
item on the page through its menu, which is the picture a curator editing a menu entry
wants, or the console should not pass `on` for a type the server cannot draw that way. A
400 the editor asked for is a red probe on every check run, and a curator sees a preview
that failed for a reason that is not theirs. Done when `post-edit:MenuItem` passes and
an assertion names the gate that let the request through.

### XI.105 The token form's save bar never appears (SH1344)

Found by the strict console check run for SH1329 on a clean instance (2026-10-30). The
`bento-token-new` probe at `/bento/admin/tokens/new` fails with "the save bar did not
appear after scrolling", for both the save bar itself and its morph. The probe scrolled,
so the form is tall enough to scroll at the check's viewport, which is what separates it
from the auth-provider, exchange-provider and webhook forms beside it: those pass with
"too short to scroll".

The save-bar morph is SH901's contract: an entity form's Save and Cancel live in the
shell's hero and morph into a sticky bar once the hero scrolls away, so a curator at the
bottom of a long form can still save. Either the token form does not compose
`BentoEntityShell` with a `formId` the way its siblings do, so there is no morph to
show, or its scroll container is not the one the shell observes and the hero never
reports leaving.

Reproduce by opening the form at the check's viewport, scrolling to the bottom and
reading whether the hero's actions are present at all. Fix the form to compose the shell
the way the webhook form does, or move its scroll onto the observed container. Done when
`bento-token-new` passes in the strict console check.

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

## LXXV The areas this round did not reach

## XCI Finding, selecting and acting on content

## LXXVI Documentation the matrix still names

## LXXVII The console's lists, redrawn (Block AR)

The sites list read as dated, cramped and out of proportion, and the drawings in
docs/design/shio-site-list.dc.html and shio-site-grid.dc.html say why and what replaces
it. The sections below are the Shio half: the backend facts a row needs, the sites list
and its grid, the admin lists and the content browser on the same cells, and the ten
hand-drawn tables moved onto the design system's one table, after a lint names them. The
package half is VDS204 to VDS207 in the design system's own backlog.

## XCII The public site (shio.viglet.org)

## XCIII The console as an application: cost, resilience and access

## XCIV The design system's consumer contract, adopted and gated

## XCV What a visitor finds first: release, links and a demo

Filed from the portfolio review of 2026-10-30 (visibility repo, estrategia/portfolio.md
and produtos/shio.md): no channel gets a launch before the page, the release and the
README say the same thing. Shio's are the furthest apart of the suite.

### XCV.1 The 2026.3 line is released on openviglet/shio (SH1339)

**Where it showed.** On 2026-10-30 the Releases tab of openviglet/shio shows v0.3.8 as
Latest: a 2021 jar built with JDK 14 and Gradle, followed by two pre-releases from 2020.
The repository has no release workflow at all; the 2026.3 line reaches people only as
`ghcr.io/openviglet/shio-ce` (pushed by hand through `publish-docker.yml`, last on
2026-07-08) and the `@viglet/*` npm packages.

**Why it costs.** The README describes October's agent-ready CMS, and the first thing an
evaluator opens beside it says the project stopped in 2021. The owner confirmed on
2026-10-30 that the 2026.3 line is released on openviglet/shio (visibility repo,
produtos/shio.md and estrategia/portfolio.md).

**Design.** A release workflow on the 2026.3 branch that tags the version from
`pom.xml`, attaches the jar, writes notes for people (what changed for a curator and for
an agent, then fixes), and calls `publish-docker.yml` so the image and the release never
disagree again. The first run makes 2026.3 Latest. The 2020–2021 releases stay as
history, marked as the pre-rewrite line in their titles or as pre-releases, so none of
them can be Latest.

### XCV.2 The README points at the docs where they live (SH1340)

**Where it showed.** The README's Docs badge and its "Documentation" line point at
docs.viglet.org/shio, which since SH1262 is frozen with a moved notice and now answers
301; the documentation lives in `shio-site/docs` and is served at
openviglet.github.io/shio/docs/. The GitHub homepage field still says shio.viglet.org,
also a 301.

**Why it costs.** Two redirects on the first click read as an abandoned address, and a
search engine or AI assistant that follows the README indexes the old host. On
2026-10-29 the product sites moved to openviglet.github.io and the old addresses were
left without redirects of their own (visibility repo, CLAUDE.md and produtos/shio.md,
2026-10-30).

**Design.** Point the README's badge and documentation links at
openviglet.github.io/shio/docs/, sweep the repository's other READMEs (CLI, SDKs,
plugin) for docs.viglet.org/shio and shio.viglet.org, and set the homepage field to
openviglet.github.io/shio. A docs check that refuses the old hosts in README files keeps
it from coming back.

### XCV.3 One tool count, derived (SH1341)

**Where it showed.** The README lists 12 MCP tools (SH678 made `shio_marketplace` the
twelfth), the site's `MCP_TOOLS` lists the same twelve, the docs say twelve, but the
landing's surface section hard-codes "Eleven tools, six resources"
(`shio-site/src/components/sections/surface.tsx`), and `docs/agents/mcp.md` still says
eleven twice.

**Why it costs.** The tool count is the claim a reader of an agent-ready CMS checks
first, and two different numbers on the README and the landing read as carelessness
(visibility repo, produtos/shio.md, 2026-10-30).

**Design.** The landing derives the number from `MCP_TOOLS.length` (and the resources
from their list) instead of spelling it, and a site test compares that list against the
server's registered tools, as `showcase-coverage.test.mjs` already does for the
showcase. The agent docs drop the stale "eleven". Any future tool then moves every count
at once.

### XCV.4 A hosted showcase with a guest console (SH1342)

**Where it showed.** Shio has no public instance anyone can open. The site shows a
scripted Claude Code session and four screenshots of the Quilhara showcase (SH1258), and
the owner confirmed on 2026-10-30 that no production site built with Shio exists to
point at: the showcase and the promo video are the window (visibility repo,
produtos/shio.md).

**Why it costs.** The portfolio review of 2026-10-30 names a demo that opens without
installing as what every launch channel is missing for Turing, Shio and Dumont
(estrategia/portfolio.md). For a CMS, the visitor wants to click the rendered site and
the console behind it; screenshots and `docker run` ask for trust or for ten minutes
first.

**Design.** A hosted instance on the Viglet Cloud with the Quilhara showcase applied
from the marketplace (SH1257 already makes that one click), its rendered pages public,
and the console open to a guest role that can browse, edit drafts and see the review
queue but not publish, reset from the blueprint on a schedule. The README and the site
link it beside `docker run`. If a guest console is too costly to keep safe, the rendered
showcase alone, with a recorded console tour, is the first step.
