---
name: shio-roadmap-docs
description: How to maintain the Shio roadmap/docs — the three docs/ files (ROADMAP.md, CHANGELOG.md, IMPROVEMENTS.md), their single-responsibility split, the cross-file update rules, and where a finished task's decision goes (docs/agents/ + docs/specs/). Also the one-task-one-commit rule and the end-user documentation decision (vigletdocs docs-shio). Use whenever adding a new task, marking a task shipped, editing any of those files or agents.md, or picking the next SH-number. Covers task numbering, non-goals, keeping the files in sync, and batching under /loop.
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
documentation").

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

**Task numbering — there is no counter file, so derive it and derive it correctly.**
Take the max across all three docs plus `agents.md`:

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

1. **When a task ships (mark it ✅):** move its full entry from `ROADMAP.md` →
   `CHANGELOG.md` (under its Block heading; the detailed shipped report lives
   *there*, not in the roadmap), and replace it in `ROADMAP.md` with **nothing**
   (delete it) or, if siblings still reference it or it's an epic header, a single
   terse pointer line:
   `> ✅ **<scope> shipped (SH..–SH..)** → see [CHANGELOG.md](CHANGELOG.md) Block X.`
   Then **delete** its design subsection from `IMPROVEMENTS.md`. `git log` is the
   history — do **not** leave a shipped implementation report behind or re-accrete
   one. This is the founding rule. **The roadmap must never carry a multi-sentence ✅
   task body.** When an epic's children all ship, collapse the whole epic to one
   shipped pointer. **Then run the end-user-docs decision** (below).
2. **When you add a new task:** add the one-liner to `ROADMAP.md` (with `deps:` + a
   `→ §x.y` pointer) and, if it needs design, add the rationale subsection to
   `IMPROVEMENTS.md`. Status markers (✅/📋/💭/⏳/🛠) live **only** in `ROADMAP.md`.
3. **Status belongs to exactly one file.** If a marker in `IMPROVEMENTS.md` disagrees
   with `ROADMAP.md`/`CHANGELOG.md`, the roadmap files win — fix it.
4. **Keep entries terse.** A ROADMAP entry is **one sentence: what + why + `→`
   pointer.** The reasoning belongs in IMPROVEMENTS, which is what the pointer
   addresses; a line that restates it makes the roadmap a history instead of a queue,
   and lets two files disagree about one design. Never put multi-paragraph release
   notes in a table cell or a task line.
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
**end-user** documentation is a *separate Docusaurus repo*:

```
D:\Git\viglet\vigletdocs\vigletdocs.github.io\docs-shio
```

⚠️ **Shio has no `COVERAGE.md` gate yet** — Turing's `docs-turing/COVERAGE.md` is the
matrix mapping every shipped feature to either an end-user page (with anchor) or an
explicit "deliberately internal — no public page" row. Shio's `docs-shio` has no
equivalent, so there is nothing that reports an orphan. Do not invent the file
casually; **creating it is a task worth filing** (it is exactly the SH338 shape — a
reachability defect with no instrument). Until it exists, run the decision by hand:

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

**Commit the docs separately.** `docs-shio` lives in a *different git repo*, so its
changes are their own commit there (`cd` into the vigletdocs repo first —
`run-commit.cmd` stages relative to CWD). The Shio-repo commit (rule 7) carries the
code + internal `docs/` sync. Keep `-m` titles ASCII.

**Batch note.** When shipping a run of tasks under `/loop` (rule 8), this decision
runs per task, but it is fine to accumulate several small page edits and land them in
one focused vigletdocs commit at a natural boundary (e.g. once a whole block ships)
rather than a trivial commit per task.
