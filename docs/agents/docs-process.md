# Roadmap and docs maintenance

> Split out of the repo-root `agents.md` (see its routing table for the index).
> Read this before touching the area it covers. Detail here is authoritative.

## These three files are owned by `roadkeep`

[`roadkeep.toml`](../../roadkeep.toml) declares this project's format — `SH` ids, `§x.y`
outline anchors, the markers, the limits, and the two slots this ledger's lines never carried.
The roadkeep plugin is enabled in `.claude/settings.json`, so a clone gets it: a hook that
**denies a hand-edit** to any of the three and names the tool, the `mcp__roadkeep__*` tools
whose input schema *is* this schema, and its own skill with the write path. `ship <id>` makes
all three edits below in one transaction; `brief <id>` starts a task without reading a file;
`lint` is the gate, and what it reports is now **5 findings, all `section.too-long` in
IMPROVEMENTS.md** — the residue SH356 tracks, down from the 278 the adoption inherited. At that
count a *new* finding is nobody's backlog: it is yours, and the CI workflow's baseline line goes
away the day `lint` exits 0.

**There is nothing here to keep in step.** This project used to run the tool from a sibling
checkout, with the skill, the hook matcher and `.mcp.json` vendored into `.claude/` — so a
session could load a skill several versions behind the checkout answering its commands and
trust it like the original. Those copies are gone: the plugin is installed from the
`alegauss` marketplace, declared in `.claude/settings.json` and committed, so the skill a
session loads *is* the published one. Updating the tool is `claude plugin marketplace update
alegauss` followed by `claude plugin update roadkeep@alegauss` — one place, no per-project step.

Everything below is why the rules are what they are. It is no longer a procedure to perform
by hand.

### What a stale section becomes (SH356)

A section whose task is already in the ledger is a `section.stale` finding: `ship` deletes the
rationale as its third edit, so a survivor is one that was written or moved by hand before the tool
owned the files. There were **43**, and the tempting answer — delete them, the CHANGELOG and `git
log` hold the history — is **wrong for most of them.** Reading the first four settles it: they open
with *"Design records that bind what follows"* and *"Standing rule that survives"*, and they carry
sentences like "every future address-taking surface inherits this". That is not history. It is an
**invariant filed under a shipped task's number**, and deleting it loses a rule that governs work
nobody has started.

So the triage is two-way, and it is a reading rather than a rule a script can apply:

- **A standing rule** → move it into the area file under `docs/agents/`, which is the file somebody
  working in that area actually opens, and *then* drop the section. Nothing is lost; it stops being
  filed under a task number a reader has no reason to look up.
- **History** — what we did, in what order, and which tasks it opened → drop it. The ledger entry
  says what shipped and `git log` has the prose.

The instrument is a ratchet on the count (`ShImprovementsStaleSectionRatchetTest`), for SH341's
reason and with one difference that matters: a section gate **cannot** start at zero the way the line
gate ended, because dropping a section is destructive where shortening a line is not. It may only
fall, and it falls when somebody has read the section and decided which of the two kinds it is.

### The four line-level defects, and the hole they exposed (SH356)

Beside the sections sat four findings that are **corruption, not prose**, and three had exactly one
correct reading: two `U+0008` control characters — in the entry *describing* a bug where `\b` in a
Java string literal was a real backspace, which acquired the same bug while explaining it — a link to
`ShAgentTokenHeaderFilter`, renamed to `ShEstTokensHeaderFilter` in `8b60084a`, and a note
(`- **SH110** … is not in this block`) that was never an entry and is a `>` note now. All three are
repaired. The fourth, a duplicate `SH347`, is **two deliveries under one id** — the unplanned raise
of the context-cache ceiling, and the later work that made the ceiling visible — and it stays.

**What is worth carrying forward is why they survived so long.** `lint --fix` does not touch them,
and the reason is that *nothing can*: every write verb takes a whole entry, and the damage is a
character inside one somebody wrote correctly. The guard's refusal names four commands — `ship`,
`record add`, `record drop`, `retire` — and none of them opens. `record drop` even looks like the
answer for the duplicate and is not: it removes the **later** entry, which here is the one that
earned the id from a roadmap line. Filed upstream as **RK126** (no verb repairs an entry's interior)
and **RK127** (two entries for one id are not always one entry twice).

**The side that used to be open is now asked about, not silent.** The guard denied `Edit` and
`Write` on a governed path and said nothing about a `Bash` payload, so `sed`, `python -c` or a
heredoc rewrote the ledger with no refusal (**RK128**). That is closed upstream: the hook matcher
includes `Bash` and answers `ask` — it cannot answer `allow` without widening what an agent may
write, and `deny` would refuse `git add docs/ROADMAP.md` — and **RK175** digests every governed
file after each verb, so an approved shell write is *reported once* at the end of the turn
instead of passing as though a verb made it. The rule is unchanged and the mechanism now states
it: if a verb exists, use the verb. Where none does, the approval is the record.

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
line is its IMPROVEMENTS.md design section — **and `ShDesignRationalePointerLintTest` fails the
build on five things about it (SH351)**: an unnumbered section (nothing can point at it), a number
or a body used twice, a pointer that resolves to nothing, a pointer that resolves to a section which
does not name that task *while the task has its own*, and a section whose task has left the backlog
without saying why it is still there. Measured when it landed: 121 sections, 82 pointers, **11 of
them dead** and 14 more landing on somebody else's design. If a task has no rationale section, the
line carries **no pointer** — a pointer to nowhere reads as though the design exists. Per-task implementable specs live in
`docs/specs/SH<n>-<slug>.md`; the product concept is `docs/specs/SH74-agent-native-cms.md`.
Which blocks are finished is **`roadkeep stats`** and not a sentence here: a block with no line
left is done, and a heading that says so is a fact with a shelf life sitting in a file resident
every turn.

### The roadmap holds three things (and what it took to get back to them)

**Block headings, open task lines, the non-goals. Nothing else.** The file had reached **535
lines to carry 48 tasks** — eight of them describing what a Block *is*, thirty-plus `> ✅ SHxxx
shipped` notes narrating the ledger a second time, a 20-line numbering history, a marker legend,
a `Part 1 / Part 2` split, and a 66-line **priority queue every one of whose ids had already
shipped**. It is now 118 lines with the same 48 lines byte-for-byte.

The test for whether a sentence belongs is mechanical, and it is the same one that keeps this
file honest: **roadkeep has a verb for every kind of content the roadmap is allowed to hold.**
`add` writes a line, `block add` a heading, `non-goal add` a bullet. There is no verb that writes
a block description or a shipped announcement, and that absence is the answer — not a gap to
route around with an `Edit` the hook denies anyway. Where each removed kind actually lives:

| What the file was carrying | Where it belongs | What answers it now |
|---|---|---|
| `> ✅ **SHxxx** shipped → CHANGELOG` | the ledger entry `ship` already wrote | `roadkeep show <id>` |
| Block descriptions, lineage, the design behind a block | IMPROVEMENTS.md at the `→ §` pointer | `roadkeep brief <id>` |
| Priority queue, "current focus", suggested block order | `priority` in [`roadkeep.toml`](../../roadkeep.toml) | `roadkeep pick` / `brief`, which name the tier that fired |
| Numbering history, reserved slots, what happened to a retired id | `git log` | `roadkeep gaps` — 36 gaps, 27 resolved against the commit that removed them |
| Marker legend, per-block counts | `[markers]` in `roadkeep.toml` | `roadkeep stats` |

**The lesson is the one SH341 measured, one level up.** SH341 found the *lines* were 142 words
against a one-sentence rule; the prose *between* them was four times the file and no gate saw it,
because `lint` reads task lines and prose-file sections and a blockquote is neither. So the
discipline cannot come from the linter here — it comes from never writing a sentence into the
file that a verb would not have written, and from **asking instead of reading**: every count in
this section came from `stats`, `gaps` and `show`, and not one of them needed the file open.

**Counting the backlog is `roadkeep stats` (or `--json`), and `roadkeep audit` names every
marker-bearing line it could not read.** Both read the format from `roadkeep.toml`, so neither
can disagree with the parser that writes the lines — which is why `scripts/status-roadmap.py`
was retired rather than kept beside it: it answered the same question (32 📋 · 43 💭 · 4 ⏳ ·
0 🛠, agreeing to the line on the day it went) with a second implementation of the format, and
two counters that agree today are two that will disagree the moment the format moves. `stats`
takes `--block`, `list --ids` is the ids alone, and `audit` reports every line it skipped with
the reason — including the dangerous shape, a bullet with a marker and no `**SH<n>**`, which
would otherwise be dropped in silence. `roadkeep lint` is the same reader as a gate.

**The ledger holds one heading per block, and nothing may be a heading inside one (SH610).**
`CHANGELOG.md` is newest-first per block, so a run of follow-ups arriving after another block's
region had opened re-established the label with a heading of its own — `### Block K follow-ups —
…`, seven of them across K, I and J. `roadkeep lint` reported every one as `block.repeated` and
nothing read the report, so the condition grew until **`ship` could not write into three of the
twelve blocks**: it files an entry under a heading by *position*, and with two candidates it
refuses rather than guess. It surfaced on SH560, on a file that task never touched.

Two things a repair has to know, both learned by getting them wrong first:

- **Renaming the second heading is not the merge.** It detaches every entry below it — that is
  what `block.missing` means — and it did, for ten shipped entries, before being reverted. The
  entries have to **move** into the one region, and the rule is to preserve the order the file
  already had: a follow-up region sitting *above* its `##` goes to the top of that block's
  entries, one sitting below goes to the bottom. Do not re-sort by date — `ship` appends at the
  **end** of a block's region, so within a block the ledger runs oldest to newest, and a merge
  that "corrected" it to newest-first would put every future entry in the wrong place.
- **A block's region ends at the next heading of any level.** So a `###` cannot group entries
  inside a block at all. The title survives as a **bold paragraph**, which no parser reads as a
  declaration.

`cli/test/changelog-blocks.test.mjs` is the obligation the lint never had — `pnpm test` is a gate,
and a merge that recreates the condition now fails in the same run that would have shipped. It
matches a block label at **any** heading level on purpose: a `##`-only regex reports *none* of the
seven against the pre-merge ledger, since all of them were `###`.

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

1. **When a task ships: `ship <id>`.** One transaction, validated before anything is
   written: the ledger entry appears under its Block, the roadmap line **is deleted**, its
   `§x.y` section is dropped from `IMPROVEMENTS.md`, and every line whose `(deps: …)` named
   it is re-annotated. `--why` restates the sentence as an outcome; the design's own is kept
   verbatim otherwise, and the tool rewrites neither.
   The roadmap therefore carries **no `> ✅ … shipped` pointer** for a shipped epic — that
   convention is retired, because a line the tool cannot write is a line somebody has to
   hand-edit, and the hook denies it. `git log` and the changelog are the history; a follow-up
   that needs to name its shipped parent does so in its own `(deps: SH22)`, which is where a
   reader can resolve it (`roadkeep deps <id>`).
2. **When you add a new task: `add … --ref <x.y>` then `section add <x.y>`.** The anchor is
   yours to choose because this project numbers by hand (`ref_scheme = "outline"`), which is
   the one field `add` cannot derive — it refuses without it rather than inventing a number.
   Between the two commands `lint` reports `ref.unresolved`, which is true and momentary.
   Status markers (✅/📋/💭/⏳/🛠) live **only** in `ROADMAP.md`.
3. **Status belongs to exactly one file.** If a marker in `IMPROVEMENTS.md`
   disagrees with `ROADMAP.md`/`CHANGELOG.md`, the roadmap files win — fix it.
4. **Keep ROADMAP entries terse** (≈1 sentence: *what + why + pointer*).
   Implementation detail goes in the CHANGELOG entry, code, and commits.
5. **Non-goals are binding.** Check `ROADMAP.md` → "Non-goals" (and IMPROVEMENTS §0)
   before proposing new work.


## The rationale file's own debt, and the ratchet that holds it (SH356)

SH341 took `ROADMAP.md`'s 42 over-long lines to zero. SH356 asked where the rest of
`roadkeep lint`'s findings had gone and answered: into `IMPROVEMENTS.md`, as **45 sections over
the 250-word budget, 42 whose task had already shipped, 9 that no line pointed at and 3 anchors
claimed twice**, beside four line-level corruptions. Measured again when it closed, by roadkeep's
own counters: **0, 0, clean, clean**, and the corruption gone with SH361.

**Two of the four already have a gate that cannot be bypassed, and one has none.** `add` and
`section add` *refuse* prose over budget at write time, and `lint` reports an unresolved or
duplicated anchor — a second implementation of either here would be a reader that can disagree
with the tool. **Stale is the one with no gate**: `ship` drops a section as the third of its three
edits, so the tool never leaves one behind, but that is a property of the write path being *taken*
rather than a fact about the file, and all 42 survivors predate roadkeep owning these files. The
hook denies the hand-edit; `cli/test/improvements-debt.test.mjs` asserts the outcome, because a
guard on the door is not the same statement as a fact about the room. It is also the costliest of
the four to a reader — this file is defined as rationale for *unshipped* work, so a survivor reads
as a live argument for something already built, and nothing on the page says which it is.

It re-states roadkeep's rules rather than shelling out, the same bargain
`cli/test/changelog-blocks.test.mjs` documents and for the same reason: roadkeep resolves to a
Python entry point `pnpm test` cannot depend on. The drift that permits is a red run here against
a green `roadkeep lint` — visible, and cheap to resolve.

**One thing to know before writing about a task in a suite file.** `cli/test/assertion-debt.test.mjs`
walks these trees for `SH<n>` and cannot tell an assertion from a comment, so naming an id in prose
**pays off its debt**. It happened three times while SH356 was being written, and the workaround
was to stop naming the ids — prose degraded to protect a counter. SH630 is the repair; until it
ships, name in a suite file only the ids that file actually asserts.

## The debt reader counts an id a suite *names* (SH567)

`assertion-debt.test.mjs` decides a task is asserted by finding its id in a suite file. That is what
makes it cheap and what makes it wrong in one specific way: **a mention is not an assertion.**

It has now happened three times in one run, each time costing a red build and a correction:

- A backend test's assertion message said "SH7's review queue is the moderation step" while
  asserting something else entirely. The id came off the debt list; the debt was not paid.
- A new site test named `SH530` in its header prose, as a reference to that task's rule.
- A test filed under `SH537` asserted route parity — which is `SH532`'s claim, not SH537's.

The rule that follows: **name an id only where the file asserts it**, and write cross-references
without the bare token. Nothing enforces this — the reader cannot tell prose from proof, which is
precisely why the discipline sits with the writer.

**A second trap, found in the same task.** `shio-site/package.json`'s `test` script named its three
files by hand, so a fourth was scanned by the reader and **never executed**: the debt would have
read as paid by assertions nobody ran. It is a glob now. The general shape is worth carrying — an
instrument that reads a tree and a runner that reads a list will diverge, and the divergence is
silent in the direction that looks like progress.

SH567's own subject turned out to be already fixed (the reader walks `shio-site/scripts`), so the
task became its second half: re-judging the ten. Three are now asserted beside the script that owns
each — the generated token figures, the prerender's route table, the Markdown twin — and the debt
went 39 → 36. What is left of that group is the **copy**, which is the harder half and the reason
the group ranks second by return-risk: a marketing claim that has gone false is invisible until
somebody reads the page against the product.
