# Roadmap and docs maintenance

> Split out of the repo-root `agents.md` (see its routing table for the index).
> Read this before touching the area it covers. Detail here is authoritative.

## Roadmap & docs maintenance

The roadmap lives in `docs/`, **split across three files that must be kept in
sync**. Each has one job — never duplicate content between them, and when you
touch one, check whether a sibling needs updating:

| File | Single responsibility | Granularity |
|---|---|---|
| [`docs/ROADMAP.md`](docs/ROADMAP.md) | **Active backlog only** — the source of truth for what's *unshipped* (📋 designed · 💭 idea · ⏳ partial · 🛠 in-progress). | one line per task |
| [`docs/CHANGELOG.md`](docs/CHANGELOG.md) | What has **shipped** — a searchable "Shipped Ledger" indexed by Block (A–G); `git log` is authoritative for detail. | one entry per shipped task, under its block |
| [`docs/IMPROVEMENTS.md`](docs/IMPROVEMENTS.md) | **Design rationale** (the what/why) for *unshipped* sections only. No status tables, no shipped implementation reports. | prose per active section |

Tasks are numbered `SH<n>` (not contiguous across blocks — epics like SH25/SH62/SH67/SH74
own sub-ranges; superseded ids are retired, never reused). The `→ §x.y` pointer on a task
line is its IMPROVEMENTS.md design section. Per-task implementable specs live in
`docs/specs/SH<n>-<slug>.md`; the product concept is `docs/specs/SH74-agent-native-cms.md`.
Blocks **H–L, M, N and O** have shipped (Block L except **SH110**, still blocked on SH45);
the live backlog is the per-block follow-ups and Blocks A–G's remaining SH44–SH47 tenant work.

**Counting the backlog is a script, not a grep.** `python scripts/status-roadmap.py`
(or `status-roadmap.cmd` from the repo root) parses `ROADMAP.md` and reports the four
markers per Block, with totals — the measurement SH341 made by hand. It counts list
bullets carrying one of the four active markers **and** a bold `**SH<n>**`, so shipped
work cannot inflate it: rule 1's `> ✅ … shipped` pointers are not bullets, the legend
table is not a bullet, and `- ✅ **SH22**` leads with a marker the script does not count.
A `(deps: SH183 ✅)` annotation inside an active line is not a status and does not
change its marker. `--tasks` lists each task with its line number,
`--status <DES|IDEA|PART|WIP>` narrows, `--json` is for anything programmatic, and
**`--audit`** prints every marker-bearing line it did *not* count with the reason —
including the one dangerous shape, a bullet with a marker and no `**SH<n>**`, which
would otherwise be dropped in silence. Reading 97 active lines across 10 blocks at
2026-07-29: 38 📋 · 54 💭 · 5 ⏳ · 0 🛠, with 44 lines excluded.

**Before you quote a benchmark number, know which way it moved and why.** Blocks N and O both
made the *baseline* lean rather than the agent path fast — aligning the console reads on the
CDA shapes (SH117/SH120) and then the post read (SH176) — so the SH112 token ratios fell from
3.8×–23.3× to 2.6×–12.6× and then 2.1×–12.1× with the agent path untouched. That is the
product improving and the claim shrinking, three blocks running. **SH157 is the first change
that moved a ratio the other way**, by folding discovery into one read and proof into the
write: every canonical task lost a call. The current reading is **tokens 2.8×–10.5×, tool
calls 2.3×–3.8×** — and **SH164/SH239 have since attributed it**: per-source, the lint is 15.5× at
five posts and 139.6× at forty-five (the only unbounded one), addressing 11.8× rising to 53.1×,
batching 5.0× on calls and 6.1× on tokens, discovery 4.0× on calls and **1.2×** on tokens,
projection 1.5×. So quote a *source*, not a task ratio: the aggregate ≈10× is retired precisely
because it mixes those five and moves when the baseline does.

**The cross-file update rules — follow these every time:**

1. **When a task ships (mark it ✅):** move its full entry from `ROADMAP.md` →
   `CHANGELOG.md` (under its Block heading; the detailed shipped report lives
   *there*, not in the roadmap), and replace it in `ROADMAP.md` with **nothing**
   (delete it) or, if siblings still reference it or it's an epic header, a single
   terse pointer line: `> ✅ **<scope> shipped (SH..–SH..)** → see [CHANGELOG.md](CHANGELOG.md) Block X.`
   Then **delete** its design subsection from `IMPROVEMENTS.md` — `git log` is the
   history; do not leave a shipped implementation report behind. **The roadmap must
   never carry a multi-sentence ✅ task body** — that's the changelog's job. When an
   epic's children all ship, collapse the whole epic to one shipped pointer.
2. **When you add a new task:** add the one-liner to `ROADMAP.md` (with `deps:` +
   a `→ §x.y` pointer) and, if it needs design, add the rationale subsection to
   `IMPROVEMENTS.md`. Status markers (✅/📋/💭/⏳/🛠) live **only** in `ROADMAP.md`.
3. **Status belongs to exactly one file.** If a marker in `IMPROVEMENTS.md`
   disagrees with `ROADMAP.md`/`CHANGELOG.md`, the roadmap files win — fix it.
4. **Keep ROADMAP entries terse** (≈1 sentence: *what + why + pointer*).
   Implementation detail goes in the CHANGELOG entry, code, and commits.
5. **Non-goals are binding.** Check `ROADMAP.md` → "Non-goals" (and IMPROVEMENTS §0)
   before proposing new work.

