---
name: shio-roadmap-docs
description: How to maintain the Shio roadmap/docs — the three docs/ files (ROADMAP.md, CHANGELOG.md, IMPROVEMENTS.md), which are owned by the roadkeep CLI and never hand-edited, plus their single-responsibility split, the cross-file update rules, and where a finished task's decision goes (docs/agents/ + docs/specs/). Also the one-task-one-commit rule, the per-task end-user documentation decision (shio-site/docs, served at /docs), and the block-completion sweep — when a block's last task ships, the docs area + shio-site + README.md are brought up to date in one pass. Use whenever adding a new task, choosing which block a task goes under (route by job to a standing home block, reopen a finished one with block add <label>, open a new one only when nothing can hold it and title it generically, never one per round or finding), marking a task shipped, shipping, retiring, linting, editing any of those files or agents.md, picking the next SH-number, or finishing a block. Covers roadkeep, roadkeep.toml, task numbering, non-goals, keeping the files in sync, COVERAGE.md, the docs sidebar, site-content.ts, and batching under /loop.
---

# Roadmap & docs maintenance

## ⛔ READ FIRST — one task, one commit (non-negotiable)

**You may NOT do more than one task before committing.** This is the single most
violated rule, so it is stated up front and it is absolute:

- **One task → one `run-commit.cmd`.** The moment a task is complete and validated,
  do the doc sync + `cd` to the repo root + `run-commit.cmd -m "<ascii title>"`
  **before touching the next task.** Finishing a task means *the commit landed* —
  code + `ROADMAP`/`CHANGELOG`/`IMPROVEMENTS` sync in that one commit.
- **A multi-task request (e.g. "execute Block P's follow-ups", a block, or a list of
  `SH<n>`s) is NOT permission to batch.** It is a request to run tasks
  **one-at-a-time, committing after each.** Never implement task 2 while task 1 is
  uncommitted. A single giant diff spanning many tasks with one commit (or no commit)
  at the end is the failure this rule exists to prevent.
- **For any batch of ≥2 tasks you MUST drive it with the `/loop` skill**
  (self-paced): exactly one task per iteration, `run-commit.cmd` at the end of the
  iteration, then let the loop advance. Do not hand-roll a loop that defers commits.
- **Self-check before starting task N+1:** run `git status`/`git log -1`. If the
  previous task's work is not already committed, STOP and commit it first. If you are
  about to edit files for a new task and the working tree still shows the prior task's
  changes, you have already broken this rule — commit now.

The full commit + batch mechanics are rules 7–8 below; the propagation to the
end-user docs repo is a **separate** commit (see "Propagating to end-user
documentation"). When a task turns out to be its **block's last** — `stats` says
so, you do not — one more unit of work follows it: see
"[When a block completes](#when-a-block-completes--the-sweep-across-the-three-public-surfaces)".

---

## ⛔ READ SECOND — the three files are owned by `roadkeep`

[`roadkeep.toml`](../../../roadkeep.toml) declares this project's format, and the roadkeep
plugin (declared in `.claude/settings.json`, so a clone gets it) carries the rest: a hook that
**denies a hand-edit** to any of the three and names the command, the `mcp__roadkeep__*` tools
whose input schema *is* this schema, and its own skill with the write path. Start a task with
`brief`, not by reading the files; `lint` is the gate. The rules below are the reasoning.

---

The roadmap is **split across three files in `docs/`** that must be kept in sync.
Each has one job — never duplicate content between them, and when you touch one,
check whether a sibling needs updating:

| File | Single responsibility | Granularity |
|---|---|---|
| [`docs/ROADMAP.md`](../../../docs/ROADMAP.md) | **Task status** — the *only* source of truth for what's unshipped. Active backlog only (📋 designed · 💭 idea · ⏳ partial · 🛠 in-progress). | one line per task |
| [`docs/CHANGELOG.md`](../../../docs/CHANGELOG.md) | What has **shipped** — a searchable "Shipped Ledger" indexed by Block; `git log` is authoritative for detail. | one entry per shipped task, under its block |
| [`docs/IMPROVEMENTS.md`](../../../docs/IMPROVEMENTS.md) | **Design rationale** (the what/why) for *unshipped* sections only. No status tables, no shipped implementation reports. | prose per active section |

Shio has **no `STRATEGY.md` and no `last-task.md`** (unlike Turing) — business
positioning lives in IMPROVEMENTS §0's non-goals, and the task counter is derived,
see below. Per-task implementable specs are `docs/specs/SH<n>-<slug>.md`; the product
concept is [`docs/specs/SH74-agent-native-cms.md`](../../../docs/specs/SH74-agent-native-cms.md).

**Task numbering — `add` derives it, and `python -m roadkeep.cli next-id` prints it.**
It scans the same sources and never fills a gap, so the grep below is the manual
equivalent kept for the reasoning; `id_sources` in `roadkeep.toml` is what adds
`agents.md` to the scan. Take the max across all three docs plus `agents.md`:

```
grep -ohE 'SH[0-9]{1,4}' docs/ROADMAP.md docs/CHANGELOG.md docs/IMPROVEMENTS.md agents.md \
  | sed 's/SH//' | sort -n | tail -1
```

SH-numbers are **non-contiguous across blocks** — epics like SH25/SH62/SH67/SH74 own
sub-ranges, and **superseded ids are retired, never reused** — so never infer the next
number from a block's header range. Because the roadmap is periodically pruned of
fully-shipped blocks, **`CHANGELOG.md` (not `ROADMAP.md`) is authoritative for the
real maximum block letter** — grep it to confirm before creating a block.

**The cross-file update rules — follow these every time:**

1. **When a task ships: `ship <id>`** — one transaction (ledger entry, roadmap line deleted,
   `§x.y` dropped, dependents re-annotated) or none of it. The `> ✅ … shipped` pointer line
   for an epic is **retired**: the tool deletes the line, and a line the tool cannot write is
   one somebody hand-edits into a file the hook defends. A follow-up names its shipped parent
   in `(deps: SH22)`, which `roadkeep deps` resolves. **Then run the end-user-docs decision**
   (below), and **then ask whether that was the block's last task** — `stats` answers it,
   see "When a block completes".
2. **Adding a task starts with its block — and the block is nearly always one that
   already exists.** `add --block` refuses a label no heading declares, so "which
   block?" is the first decision, and the way it goes wrong is by inventing a letter
   instead of reading the headings. **Reuse is the default; a new block is the
   exception you have to justify — and `block add` is not yours to run unprompted:
   propose the label and title to the user and wait, the way you would for any
   irreversible outward-facing change.** A wrong task line is one `amend`; a wrong
   block is a heading, a ledger region that keeps it forever, and a four-surface
   sweep when it empties.
   - **Read the candidates first: `block list`.** It prints **every** block ever
     opened, with its title and counts, and names the ones the roadmap no longer
     declares. A finished block is still a legitimate home, and **reopening one is
     reuse, not a new block**: `block add <label> --title "<its ledger title,
     verbatim>"` re-declares the heading, and `add --block <label>` then works. That
     needs no proposal to the user. When the reopened block empties again, amend its
     existing `SWEEP` entry in `cli/test/block-sweep.test.mjs` and don't add a second one
     (Block BI was reopened this way for SH1181). `list --block <x>` prints what an
     ambiguously-titled block actually holds.
   - **Route by job to a standing home.** These blocks have titles generic enough
     to take the next task of their kind. Look here before anything else:

     | Job | Home |
     |---|---|
     | Agent surface: MCP, manifest, context pack, `/api/v2/agent/**` | **H** |
     | Agent safety, review queue, confirm tokens, human-in-the-loop | **L** |
     | Verification loop: verify, lint, render digest, perception | **K** |
     | Gates, lints, conformance and the build's own instruments | **AY** |
     | Token economy and response budgets | **M** |
     | Content as files, the `shio` CLI | **I** |
     | Blueprints and the Claude Code plugin | **J** |
     | Renderer, layouts, regions, the visible site | **P** |
     | Replication (`shio clone` and after) | **Q** |
     | CDA / headless delivery · JS SDK DX | **A** · **B** |
     | Import/export, portability · another CMS's model or editor | **G** · **AP** |
     | Console: a surface the curator uses | **AR** |
     | Console: cost, resilience, access, load budgets | **BG** |
     | Console: authoring fields · finding/bulk · media · visual editor | **BC** · **BD** · **BE** · **BF** |
     | Releases, scheduling, the publish gate · revisions | **BA** · **AZ** |
     | The curator↔agent return channel, the assistant dock | **BB** · **AX** |
     | Localization · the design system contract · Turing indexing | **BH** · **AU** · **AS** |
     | A live defect found on a running instance | **AV** |
     | End-user docs (shio-site/docs) · the public site | **R** · **S** |

     The table routes a task. `block list` is still the authority on what exists.
     A home that turns out wrong is fixed with one `amend`.
   - **A finding filed mid-task goes where its parent is.** Put a follow-up, a bug
     or an improvement found while working on SH\<n\> in SH\<n\>'s block if that block
     is still open. If it isn't, use the home for the finding's job. Don't give a
     round of work its own block, and don't give its findings one either.
   - **Match on the *job*, not the surface.** A block groups work by what it
     accomplishes, not by which directory it edits. A task touching `shio-react/`
     belongs to whichever block owns the human-curation job, not to a new block
     opened because the files felt unfamiliar. If a reader would accept an existing
     title as covering the task, that is the block — even if the fit is loose.
   - **A new block requires a job no existing heading can honestly hold**, not merely
     work that feels new. Two tasks of a kind is not a block; it is two lines under
     an existing one. **Under an autonomous run** (`/loop`, `execute-roadkeep-tasks`)
     nobody can approve a new block. File the task under the closest home and name the
     misfit in the run's report. Never run `block add` with a new label there.
   - **The measured failure.** Blocks **X–AO** are 18 blocks holding 25 tasks, one to
     three each. Their titles describe a moment ("What the gate round left", "Two
     removals nobody is watching", "The revealed debt, next file", which repeats
     AF). Nearly all of that work was gates and lints, and it belonged under one
     instruments home. If a title names a round, a count, a sequel ("next", "the
     rest of", "left behind", "follow-ups"), a task id or a single finding, it
     describes a task line, not a block.
   - **When you genuinely must open one, name it for the capability, not for the
     task in hand.** `block add <label> --title "…"` (`--after` places it; the
     **CHANGELOG** is authoritative for the highest letter, and a new label never
     takes a letter the ledger already has; reopening that letter is the reuse
     above). The title has to be generic enough to hold *the next few
     plausible tasks of the same kind* — the block is a container for future work,
     not a label for this one. **The test is answerable before you type the title:
     name three plausible *future* tasks the heading would hold. If the second and
     third are the same task worded differently, the title is a deliverable and the
     block is really a task line under an existing heading.** A block named after a
     single deliverable empties on
     its first `ship` and drags the whole four-surface sweep behind it for one task.
     This project already paid that: Blocks **R**, **T** and **U** are three separate
     documentation blocks where one generically-titled documentation block would have
     held all three — three completion sweeps instead of one.

   **Then two commands, and the anchor is yours.** This project numbers
   its rationale by hand (`ref_scheme = "outline"`), so the pointer is **not** derived and
   `add` refuses without it:
   `add --block <x> --symptom "…" --why "…" --ref <x.y>`, then
   `section add <x.y> --title "…"` with the prose on stdin. The same reuse rule applies
   one level down: pick the next free number **in an existing family** the task belongs
   to (`§VIII.20` under `## VIII.`), and open a new `##` family only when no existing one
   can hold it. Expect `lint` to report
   `ref.unresolved` between the two commands — that is the honest state, not a mistake.
   The `deps:` group and the status marker are `add`'s own flags (`--dep`, `--status`);
   markers live **only** in `ROADMAP.md`.
3. **Status belongs to exactly one file.** If a marker in `IMPROVEMENTS.md` disagrees
   with `ROADMAP.md`/`CHANGELOG.md`, the roadmap files win — fix it.
4. **Keep entries terse.** A ROADMAP entry is **one sentence: what + why + `→`
   pointer.** The reasoning belongs in IMPROVEMENTS, which is what the pointer
   addresses; a line that restates it makes the roadmap a history instead of a queue,
   and lets two files disagree about one design. Never put multi-paragraph release
   notes in a table cell or a task line.
   **And the same rule about the file, not the line: `ROADMAP.md` holds block headings,
   open task lines and the non-goals — nothing else.** No `> ✅ … shipped` note (rule 1),
   no block description, no priority prose, no numbering history, no marker legend. The
   test is that **roadkeep has a verb for every kind of content the file may hold** —
   `add`, `block add`, `non-goal add` — so a sentence no verb writes is one that belongs
   in the ledger, at the `→ §` pointer, in `priority` in `roadkeep.toml`, or in `git log`
   (`show`, `brief`, `pick`, `gaps` are what read them back). `lint` cannot see this: it
   reads task lines and prose sections, and a blockquote is neither, which is how the file
   reached 535 lines carrying 48 tasks. Detail and the removal table:
   [`docs/agents/docs-process.md`](../../../docs/agents/docs-process.md).
5. **A finished task's *decision* goes in its area file under
   [`docs/agents/`](../../../docs/agents/), not in `agents.md`.** The root `agents.md`
   is the routing index, loaded every turn, and it grows only when a **new area**
   appears. Its history stays in `git log`.
6. **Non-goals are binding.** `ROADMAP.md` → "Non-goals" and `IMPROVEMENTS.md` §0
   list things deliberately *not* to build — check them before proposing new work.
7. **Commit the instant a task finishes — before starting the next (see the ⛔ block
   at the top).** A task is not "done" until `run-commit.cmd -m
   "<conventional-commits title>"` has landed. Do the doc sync (rules 1–2) **in the
   same commit** as the code so the docs never drift from what actually shipped. `cd`
   to the repo root first, and keep the `-m` title ASCII. **Always pass `-m`** — the
   tool otherwise infers the message from the diff, and for a docs/ROADMAP commit that
   means prose about already-shipped work gets misread as `feat: implement <feature>`.
8. **A batch of ≥2 tasks MUST run under `/loop` — mandatory, not a suggestion.** When
   the ask covers multiple tasks (a whole block, or an explicit list of `SH<n>`s),
   drive it with the `/loop` skill self-paced: **exactly one task per iteration,
   `run-commit.cmd` at the end of that iteration (rule 7), then advance.** Do not
   implement task 2 while task 1 is uncommitted, and do not hand-roll a loop that
   defers commits to the end. Only a genuinely single-task ask skips `/loop`.

## The drift these rules exist to prevent, measured twice

- **SH341 measured the roadmap:** 95 active task lines averaging **142** words, worst
  **555** — and six of the worst eight were written in the same session that then
  found the problem. Treat it as a drift the process *invites*, not a lapse. If the
  analysis feels too big for the line, that is the signal it goes in IMPROVEMENTS.
- **The same drift produced `agents.md`'s split.** It reached **186 KB (~46k tokens,
  resident every turn)** by absorbing the rationale of every shipped task — P3
  violated by the file that declares P3. `IMPROVEMENTS.md` has the same failure mode
  and Turing's grew to 539 KB once by it.

## Propagating to end-user documentation

The three `docs/` files above are the **internal** engineering roadmap. The
**end-user** documentation is the Astro Starlight area served at
https://openviglet.github.io/shio/docs/, **in this repository** (SH1262; it used to be
vigletdocs' `docs-shio/`, which is now frozen and marked moved, so never edit it):

```
shio-site/docs/src/content/docs/
```

**The gate is [`COVERAGE.md`](../../../shio-site/docs/src/content/docs/COVERAGE.md)** (SH479), the matrix mapping every
shipped block or feature cluster to either an end-user page **with an anchor**, or an
explicit 🔒 "deliberately internal — no public page" row. It carries the same shape as
Turing's `docs-turing/COVERAGE.md`, plus one verdict Turing does not need: ⚠️, *a page
exists but describes a shape the product left* — because every page published before
2026.3 predates the renderer. It sits beside the pages but is not one:
`shio-site/docs/src/content.config.ts` leaves it out of the collection.

**The decision below has a file to answer in now.** A shipped block with no row is a
visible defect rather than a habit nobody kept, so the row is part of shipping — not a
later sweep. It landed red on purpose: **21 🔴 and 12 ⚠️ of 40 clusters**, which is what
Block R is measured against, so expect to be *changing* a row from 🔴 to ✅ rather than
adding one.

**Every time a task ships, after the internal doc sync (rule 1):**

1. **Is it user-facing?** Would someone *using* Shio — a curator in the console, a
   developer calling the CDA/SDK/CLI, an agent driving MCP — do something differently
   because this shipped? If **no** (internal engineering, a lint, a refactor, a
   benchmark), it does **not** get an end-user page. Don't invent thin pages for
   internal work.
2. **If yes, find its home.** Most features extend an existing page (a new section or
   anchor) rather than warranting a new one. Fold into the closest existing page
   unless the surface is genuinely new and sizeable enough to stand alone.
3. **Write for the user, not the commit.** Explain *what the feature does and how to
   use it* — configuration keys, console steps, API/CLI/MCP calls, examples. Not a
   changelog, not a design-rationale dump. Never paste `IMPROVEMENTS.md` prose
   verbatim; match the surrounding page's tense, voice and structure.
4. **Product name & brand.** User-facing text says **Viglet Shio**; public links point
   at the public `openviglet/shio` repo.
5. **Record the verdict in `COVERAGE.md`** — the page + anchor you wrote (✅ / 🧩), or
   the 🔒 row saying why there is no page, in the same commit as the page itself. A
   verdict of "internal" is a *row*, not a silence: an absent row is indistinguishable
   from a forgotten one, which is the defect the matrix exists to make visible.

**The page, the COVERAGE row and `pnpm run coverage:mirror` go in the task's own
commit** (rule 7): since SH1262 they are all in this tree, so there is no second
commit and no second repository. Keep `-m` titles ASCII.

---

## When a block completes — the sweep across the three public surfaces

The per-task decision above keeps a *page* honest. It cannot keep the **shape** of
the product honest, because the thing a reader meets first — the docs hub, the
public site, the README's surface table — describes a *block*, not a task. Ten
tasks each correctly folded into an existing anchor still leave the landing pages
describing the product as it was before the block existed. So the block boundary
gets its own pass.

### The trigger is measured, not remembered

**After every `ship`, ask `stats` whether the shipped task's block is now empty.**

```
python -m roadkeep.cli stats          # or: roadkeep stats
```

A block printing **`0`** with no markers has no open lines left — that is the
completion signal, and today's output shows exactly one (`Block I 0`). Do not
infer completion from "I finished the tasks the user listed": a block routinely
holds lines nobody named in this session, and `stats` is the only thing that
counts them. Two corollaries:

- **`block drop <x>` is the roadkeep half.** A heading standing over nothing is
  what that verb removes, and it is the only thing allowed to remove it — the
  guard denies the hand-edit. Do it in the same commit as the sweep, and remember
  `CHANGELOG.md` keeps the block forever, which is why it (not `ROADMAP.md`) is
  authoritative for the highest block letter.
- **A block emptied by `retire`/`defer` still completes.** The sweep is about what
  the public documents claim, and a retired line changes that claim as much as a
  shipped one — usually by removing a promise.

### The four surfaces, in this order

The order is deliberate: the docs page is the deepest and most correct text, so
write it first and let the site and the README quote *it* rather than the other
way round.

**1. The docs area — the block's COVERAGE rows go green, all of them.**

```
shio-site/docs/src/content/docs/
```

Grep `COVERAGE.md` for `**Block <x>**` and resolve **every** row to ✅ / 🧩 / 🔒.
When a block completes there may be **no 🔴 and no ⚠️ row left mentioning it** —
that is the definition of the sweep being done, and it is the one check worth
re-running before committing.

- **⚠️ is a rewrite, not an addition.** It means a page exists and describes a
  shape the product left; a new section under it makes the page *contradict
  itself*. Fix the stale prose first, then add.
- **A new page needs a sidebar row or it does not exist.** Add it to the
  `sidebar` in [`shio-site/docs/astro.config.mjs`](../../../shio-site/docs/astro.config.mjs)
  under the hub the COVERAGE section names, and to that hub page's card grid — the matrix is grouped by *who does
  the job* (agent builds it / human curates it / CDA delivers it), and since SH482
  those four hubs **are** the sidebar. A page absent from it builds as an unlisted
  route and no reader ever reaches it.
- **The hub's own landing page** (`agent-builds-it.mdx`, `curator-approves-it.mdx`,
  `cda-delivers-it.mdx`, `run-it.mdx`) is what most block completions actually
  change: it is the text that summarises the capability, and it is the text a
  per-task edit never touches.
- Gate: `pnpm --filter shio-site build` (Starlight fails on an unknown sidebar
  id) and `pnpm --filter shio-site test`, whose `docs-links.test.mjs` fails on a
  link or `#anchor` inside /docs that reaches nothing, which is exactly the
  mistake this step invites.

**2. `shio-site` — only if the block changed a claim the site makes.**

The site is a marketing surface with a hard rule: **every claim is about a real
surface**. So the question is not "is the block big" but "does
[`src/lib/site-content.ts`](../../../shio-site/src/lib/site-content.ts) now say
something untrue" — a tool count, a CLI verb list, a guardrail, a phase, a
comparison row, a mode. All page copy lives in that one file; the components in
`src/components/sections/` only render it.

- **Never type a measured number.** `scripts/token-budgets.mjs` generates them
  from `shio-app/src/test/resources/token-budgets.properties` on every build. If
  the block moved a budget, the properties file is the edit and the site picks it
  up.
- **A new route is two rows, and the build enforces both**: `ROUTES` in
  `src/App.tsx` and the `routes` entry (title + description) in
  `src/entry-server.tsx`. `routesAreDeclared()` throws at import time if they
  disagree in either direction.
- **Update the README's `## Backlog` section** in
  [`shio-site/README.md`](../../../shio-site/README.md) — it names the block the
  site is currently waiting on, so a completed block leaves it wrong by
  construction.
- Gate: `pnpm --filter shio-site build` (typecheck + prerender + the `.md` twins)
  and `pnpm --filter shio-site test`.

**3. Root [`README.md`](../../../README.md) — the surface table and the counts.**

The README is the repo's front door and it carries hard numbers that a block
completion is the usual cause of going stale. Check these, and only these, unless
the block genuinely added a section:

| What | Where |
|---|---|
| What | Where | Derived? |
|---|---|---|
| MCP tool count and the `shio_*` list | `## What is Shio?` surface table | ✅ `readme-surface.test.mjs` |
| CLI verb count (`NN verbs: …`) | same table | ✅ same |
| The six surfaces themselves | same table — a new surface is a new row | ❌ read it |
| `## Packages` | a block that published or renamed a package | ❌ read it |
| `## The ten design laws` | only if a law's *instrument* changed | ❌ read it |
| `## Quick start` / `## Build from source` | a block that changed a command | ❌ read it |

**Do not re-derive the ✅ rows by hand, and do not file a task to "wire" them
(SH739).** `cli/test/readme-surface.test.mjs` has done it since SH499 and runs on
every `pnpm test`: it holds the `shio_*` list against the tools the server
registers **in both directions**, the stated tool count against the registry size,
the stated verb count against `COMMANDS` in `cli/src/index.mjs`, and the site's
list against the README's. If those numbers are wrong, `pnpm test` is already red
and you will not need this checklist to find out.

This section used to claim the opposite — "nothing derives them" and
"nothing fails when they do" — and it cost a whole sweep: an agent
hand-counted twelve tools and thirty-one verbs, concluded they were correct only by
luck, and filed a task to build a guard that had just run green in the same command.
That is why the column exists rather than a sentence. The ❌ rows are genuinely
unguarded and are what your reading time is for.

There are still **no roadkeep markers in this README** — `export --readme` is not
wired here — so every row above is an ordinary edit. That is about *who writes
them*, not about whether anything checks them.

**4. Commit — one commit, and the block drop goes with it.**

`README.md` + `shio-site/**` (the docs pages and `COVERAGE.md` included) +
`block drop <x>` + any `docs/agents/` update, from the repo root:
`run-commit.cmd -m "docs(block-<x>): <ascii title>"`.

### What the sweep is not

- **Not permission to batch.** Rule 8 still holds: the block's *tasks* ran one
  per `/loop` iteration with a commit each. The sweep is a **separate,
  final** unit of work at the boundary — one more iteration, not a merge of the
  previous ten.
- **Not a rewrite of shipped pages.** Fold into existing anchors (per-task rule
  2). The new text a block earns is almost always a hub-page paragraph and a
  COVERAGE row flip, not a new page.
- **Not a place for rationale.** `IMPROVEMENTS.md` prose is never pasted into an
  end-user page or the site. The block's *why* stays internal; the public text
  says what the reader can now do.
