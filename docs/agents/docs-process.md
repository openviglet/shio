# Roadmap and docs maintenance

> Split out of the repo-root `agents.md` (see its routing table for the index).
> Read this before touching the area it covers. Detail here is authoritative.

## These three files are owned by `roadkeep`

[`roadkeep.toml`](../../roadkeep.toml) declares this project's format, `SH` ids, `§x.y`
outline anchors, the markers, the limits, and the two slots this ledger's lines never carried.
The roadkeep plugin is enabled in `.claude/settings.json`, so a clone gets it: a hook that
**denies a hand-edit** to any of the three and names the tool, the `mcp__roadkeep__*` tools
whose input schema *is* this schema, and its own skill with the write path. `ship <id>` makes
all three edits below in one transaction; `brief <id>` starts a task without reading a file;
`lint` is the gate, and what it reports is now **5 findings, all `section.too-long` in
IMPROVEMENTS.md**, the residue SH356 tracks, down from the 278 the adoption inherited. At that
count a *new* finding is nobody's backlog: it is yours, and the CI workflow's baseline line goes
away the day `lint` exits 0.

**There is nothing here to keep in step.** This project used to run the tool from a sibling
checkout, with the skill, the hook matcher and `.mcp.json` vendored into `.claude/`, so a
session could load a skill several versions behind the checkout answering its commands and
trust it like the original. Those copies are gone: the plugin is installed from the
`alegauss` marketplace, declared in `.claude/settings.json` and committed, so the skill a
session loads *is* the published one. Updating the tool is `claude plugin marketplace update
alegauss` followed by `claude plugin update roadkeep@alegauss`, one place, no per-project step.

Everything below is why the rules are what they are. It is no longer a procedure to perform
by hand.

### What a stale section becomes (SH356)

A section whose task is already in the ledger is a `section.stale` finding: `ship` deletes the
rationale as its third edit, so a survivor is one that was written or moved by hand before the tool
owned the files. There were **43**, and the tempting answer, delete them, the CHANGELOG and `git
log` hold the history, is **wrong for most of them.** Reading the first four settles it: they open
with *"Design records that bind what follows"* and *"Standing rule that survives"*, and they carry
sentences like "every future address-taking surface inherits this". That is not history. It is an
**invariant filed under a shipped task's number**, and deleting it loses a rule that governs work
nobody has started.

So the triage is two-way, and it is a reading rather than a rule a script can apply:

- **A standing rule** → move it into the area file under `docs/agents/`, which is the file somebody
  working in that area actually opens, and *then* drop the section. Nothing is lost; it stops being
  filed under a task number a reader has no reason to look up.
- **History**: what we did, in what order, and which tasks it opened → drop it. The ledger entry
  says what shipped and `git log` has the prose.

The instrument is a ratchet on the count (`ShImprovementsStaleSectionRatchetTest`), for SH341's
reason and with one difference that matters: a section gate **cannot** start at zero the way the line
gate ended, because dropping a section is destructive where shortening a line is not. It may only
fall, and it falls when somebody has read the section and decided which of the two kinds it is.

**Its three checks encoded an address shape roadkeep does not enforce (SH817).** A heading had to be
`^#{3,4} X.Y` and a pointer `→ §X.Y`, the numbered form every task happened to use, and not the
form the tool writes. A new block's first `add --ref XXXI` produces a **level-2 family heading** and
a bare `→ §XXXI`, which is legal, and which `improvements-debt.test.mjs` accepts by name. The three
checks then failed in decreasing order of noise, which is the wrong order: the vacuity check went
**red** blaming its own regex, `everyOpenPointerResolves`, there because a triage once left an open
task pointing at a deleted section, went **green over the one pointer it could not parse**, and the
stale ratchet never counted a level-2 section, which is why the JS twin caught a block heading
naming shipped ids and this one did not. Both patterns now take `[IVXLC]+(?:\.\d+)*` at any heading
level, so all three check strictly more. The lesson generalises past the regex: **when an instrument
and the tool that writes its input disagree about a format, the instrument is wrong by default**,
the tool is the one with users. And a diagnostic must not share its caller's pattern; the vacuity
message now re-reads the roadmap loosely and names the pointers it could not parse, because a
message derived from the failing scan can only report that there was nothing to see.

### The four line-level defects, and the hole they exposed (SH356)

Beside the sections sat four findings that are **corruption, not prose**, and three had exactly one
correct reading: two `U+0008` control characters, in the entry *describing* a bug where `\b` in a
Java string literal was a real backspace, which acquired the same bug while explaining it, a link to
`ShAgentTokenHeaderFilter`, renamed to `ShEstTokensHeaderFilter` in `8b60084a`, and a note
(`- **SH110** … is not in this block`) that was never an entry and is a `>` note now. All three are
repaired. The fourth, a duplicate `SH347`, is **two deliveries under one id**, the unplanned raise
of the context-cache ceiling, and the later work that made the ceiling visible, and it stays.

**What is worth carrying forward is why they survived so long.** `lint --fix` does not touch them,
and the reason is that *nothing can*: every write verb takes a whole entry, and the damage is a
character inside one somebody wrote correctly. The guard's refusal names four commands, `ship`,
`record add`, `record drop`, `retire`, and none of them opens. `record drop` even looks like the
answer for the duplicate and is not: it removes the **later** entry, which here is the one that
earned the id from a roadmap line. Filed upstream as **RK126** (no verb repairs an entry's interior)
and **RK127** (two entries for one id are not always one entry twice).

**The side that used to be open is now asked about, not silent.** The guard denied `Edit` and
`Write` on a governed path and said nothing about a `Bash` payload, so `sed`, `python -c` or a
heredoc rewrote the ledger with no refusal (**RK128**). That is closed upstream: the hook matcher
includes `Bash` and answers `ask`, it cannot answer `allow` without widening what an agent may
write, and `deny` would refuse `git add docs/ROADMAP.md`, and **RK175** digests every governed
file after each verb, so an approved shell write is *reported once* at the end of the turn
instead of passing as though a verb made it. The rule is unchanged and the mechanism now states
it: if a verb exists, use the verb. Where none does, the approval is the record.

## Roadmap & docs maintenance

The roadmap lives in `docs/`, **split across three files that must be kept in
sync**. Each has one job, never duplicate content between them, and when you
touch one, check whether a sibling needs updating:

| File | Single responsibility | Granularity |
|---|---|---|
| [`docs/ROADMAP.md`](docs/ROADMAP.md) | **Active backlog only**, the source of truth for what's *unshipped* (📋 designed · 💭 idea · ⏳ partial · 🛠 in-progress). | one line per task |
| [`docs/CHANGELOG.md`](docs/CHANGELOG.md) | What has **shipped**, a searchable "Shipped Ledger" indexed by Block (A–G); `git log` is authoritative for detail. | one entry per shipped task, under its block |
| [`docs/IMPROVEMENTS.md`](docs/IMPROVEMENTS.md) | **Design rationale** (the what/why) for *unshipped* sections only. No status tables, no shipped implementation reports. | prose per active section |

Tasks are numbered `SH<n>` (not contiguous across blocks, epics like SH25/SH62/SH67/SH74
own sub-ranges; superseded ids are retired, never reused). The `→ §x.y` pointer on a task
line is its IMPROVEMENTS.md design section, **and `ShDesignRationalePointerLintTest` fails the
build on five things about it (SH351)**: an unnumbered section (nothing can point at it), a number
or a body used twice, a pointer that resolves to nothing, a pointer that resolves to a section which
does not name that task *while the task has its own*, and a section whose task has left the backlog
without saying why it is still there. Measured when it landed: 121 sections, 82 pointers, **11 of
them dead** and 14 more landing on somebody else's design. If a task has no rationale section, the
line carries **no pointer**, a pointer to nowhere reads as though the design exists. Per-task implementable specs live in
`docs/specs/SH<n>-<slug>.md`; the product concept is `docs/specs/SH74-agent-native-cms.md`.
Which blocks are finished is **`roadkeep stats`** and not a sentence here: a block with no line
left is done, and a heading that says so is a fact with a shelf life sitting in a file resident
every turn.

### The roadmap holds three things (and what it took to get back to them)

**Block headings, open task lines, the non-goals. Nothing else.** The file had reached **535
lines to carry 48 tasks**, eight of them describing what a Block *is*, thirty-plus `> ✅ SHxxx
shipped` notes narrating the ledger a second time, a 20-line numbering history, a marker legend,
a `Part 1 / Part 2` split, and a 66-line **priority queue every one of whose ids had already
shipped**. It is now 118 lines with the same 48 lines byte-for-byte.

The test for whether a sentence belongs is mechanical, and it is the same one that keeps this
file honest: **roadkeep has a verb for every kind of content the roadmap is allowed to hold.**
`add` writes a line, `block add` a heading, `non-goal add` a bullet. There is no verb that writes
a block description or a shipped announcement, and that absence is the answer, not a gap to
route around with an `Edit` the hook denies anyway. Where each removed kind actually lives:

| What the file was carrying | Where it belongs | What answers it now |
|---|---|---|
| `> ✅ **SHxxx** shipped → CHANGELOG` | the ledger entry `ship` already wrote | `roadkeep show <id>` |
| Block descriptions, lineage, the design behind a block | IMPROVEMENTS.md at the `→ §` pointer | `roadkeep brief <id>` |
| Priority queue, "current focus", suggested block order | `priority` in [`roadkeep.toml`](../../roadkeep.toml) | `roadkeep pick` / `brief`, which name the tier that fired |
| Numbering history, what happened to a retired id | `git log` | `roadkeep gaps`, 36 gaps, 27 resolved against the commit that removed them |
| Which ids are spoken for and will never be a line | `reserved_ids` in `roadkeep.toml` (SH649) | the deriver skips them; `lint` stops calling every mention a hazard |
| Marker legend, per-block counts | `[markers]` in `roadkeep.toml` | `roadkeep stats` |

**The lesson is the one SH341 measured, one level up.** SH341 found the *lines* were 142 words
against a one-sentence rule; the prose *between* them was four times the file and no gate saw it,
because `lint` reads task lines and prose-file sections and a blockquote is neither. So the
discipline cannot come from the linter here, it comes from never writing a sentence into the
file that a verb would not have written, and from **asking instead of reading**: every count in
this section came from `stats`, `gaps` and `show`, and not one of them needed the file open.

**An epic's id is a reservation, and it has to be declared to be one (SH649).** This project spells
whole programmes of work in one token, `SH74`'s spec, `SH62`'s Media Library, `SH19`'s console
migration, and ships every slice under another number, so the epic id is cited constantly and
written as a line never. Left undeclared that is a real hazard rather than a wording nit: the
deriver reads an id in this prefix as *claimed*, steps past it, and burns it, while a reader
following the pointer finds nothing. `reserved_ids` in `roadkeep.toml` says which nine those are,
with the reason each family exists, the retired GraalJS range and the absorbed marketplace epic
are there too, because a non-goal that stops naming what it refuses loses its argument's address.
**Declaring is not silencing:** a genuine typo still fails, because it is not on the list, and
`improvements-debt.test.mjs` holds the same rule inside `pnpm test`, which runs on every push where
`roadkeep lint` runs when somebody types it.

**Counting the backlog is `roadkeep stats` (or `--json`), and `roadkeep audit` names every
marker-bearing line it could not read.** Both read the format from `roadkeep.toml`, so neither
can disagree with the parser that writes the lines, which is why `scripts/status-roadmap.py`
was retired rather than kept beside it: it answered the same question (32 📋 · 43 💭 · 4 ⏳ ·
0 🛠, agreeing to the line on the day it went) with a second implementation of the format, and
two counters that agree today are two that will disagree the moment the format moves. `stats`
takes `--block`, `list --ids` is the ids alone, and `audit` reports every line it skipped with
the reason, including the dangerous shape, a bullet with a marker and no `**SH<n>**`, which
would otherwise be dropped in silence. `roadkeep lint` is the same reader as a gate.

**The ledger holds one heading per block, and nothing may be a heading inside one (SH610).**
`CHANGELOG.md` is newest-first per block, so a run of follow-ups arriving after another block's
region had opened re-established the label with a heading of its own, `### Block K follow-ups,
…`, seven of them across K, I and J. `roadkeep lint` reported every one as `block.repeated` and
nothing read the report, so the condition grew until **`ship` could not write into three of the
twelve blocks**: it files an entry under a heading by *position*, and with two candidates it
refuses rather than guess. It surfaced on SH560, on a file that task never touched.

Two things a repair has to know, both learned by getting them wrong first:

- **Renaming the second heading is not the merge.** It detaches every entry below it, that is
  what `block.missing` means, and it did, for ten shipped entries, before being reverted. The
  entries have to **move** into the one region, and the rule is to preserve the order the file
  already had: a follow-up region sitting *above* its `##` goes to the top of that block's
  entries, one sitting below goes to the bottom. Do not re-sort by date, `ship` appends at the
  **end** of a block's region, so within a block the ledger runs oldest to newest, and a merge
  that "corrected" it to newest-first would put every future entry in the wrong place.
- **A block's region ends at the next heading of any level.** So a `###` cannot group entries
  inside a block at all. The title survives as a **bold paragraph**, which no parser reads as a
  declaration.

`cli/test/changelog-blocks.test.mjs` is the obligation the lint never had, `pnpm test` is a gate,
and a merge that recreates the condition now fails in the same run that would have shipped. It
matches a block label at **any** heading level on purpose: a `##`-only regex reports *none* of the
seven against the pre-merge ledger, since all of them were `###`.

**Before you quote a benchmark number, know which way it moved and why.** Blocks N and O both
made the *baseline* lean rather than the agent path fast, aligning the console reads on the
CDA shapes (SH117/SH120) and then the post read (SH176), so the SH112 token ratios fell from
3.8×–23.3× to 2.6×–12.6× and then 2.1×–12.1× with the agent path untouched. That is the
product improving and the claim shrinking, three blocks running. **SH157 is the first change
that moved a ratio the other way**, by folding discovery into one read and proof into the
write: every canonical task lost a call. The current reading is **tokens 2.8×–10.5×, tool
calls 2.3×–3.8×**, and **SH164/SH239 have since attributed it**: per-source, the lint is 15.5× at
five posts and 139.6× at forty-five (the only unbounded one), addressing 11.8× rising to 53.1×,
batching 5.0× on calls and 6.1× on tokens, discovery 4.0× on calls and **1.2×** on tokens,
projection 1.5×. So quote a *source*, not a task ratio: the aggregate ≈10× is retired precisely
because it mixes those five and moves when the baseline does.

**The cross-file update rules, follow these every time:**

1. **When a task ships: `ship <id>`.** One transaction, validated before anything is
   written: the ledger entry appears under its Block, the roadmap line **is deleted**, its
   `§x.y` section is dropped from `IMPROVEMENTS.md`, and every line whose `(deps: …)` named
   it is re-annotated. `--why` restates the sentence as an outcome; the design's own is kept
   verbatim otherwise, and the tool rewrites neither.
   The roadmap therefore carries **no `> ✅ … shipped` pointer** for a shipped epic, that
   convention is retired, because a line the tool cannot write is a line somebody has to
   hand-edit, and the hook denies it. `git log` and the changelog are the history; a follow-up
   that needs to name its shipped parent does so in its own `(deps: SH22)`, which is where a
   reader can resolve it (`roadkeep deps <id>`).
2. **When you add a new task: `add … --ref <x.y>` then `section add <x.y>`.** The anchor is
   yours to choose because this project numbers by hand (`ref_scheme = "outline"`), which is
   the one field `add` cannot derive, it refuses without it rather than inventing a number.
   Between the two commands `lint` reports `ref.unresolved`, which is true and momentary.
   Status markers (✅/📋/💭/⏳/🛠) live **only** in `ROADMAP.md`.
3. **Status belongs to exactly one file.** If a marker in `IMPROVEMENTS.md`
   disagrees with `ROADMAP.md`/`CHANGELOG.md`, the roadmap files win, fix it.
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
duplicated anchor, a second implementation of either here would be a reader that can disagree
with the tool. **Stale is the one with no gate**: `ship` drops a section as the third of its three
edits, so the tool never leaves one behind, but that is a property of the write path being *taken*
rather than a fact about the file, and all 42 survivors predate roadkeep owning these files. The
hook denies the hand-edit; `cli/test/improvements-debt.test.mjs` asserts the outcome, because a
guard on the door is not the same statement as a fact about the room. It is also the costliest of
the four to a reader, this file is defined as rationale for *unshipped* work, so a survivor reads
as a live argument for something already built, and nothing on the page says which it is.

It re-states roadkeep's rules rather than shelling out, the same bargain
`cli/test/changelog-blocks.test.mjs` documents and for the same reason: roadkeep resolves to a
Python entry point `pnpm test` cannot depend on. The drift that permits is a red run here against
a green `roadkeep lint`, visible, and cheap to resolve.

**One thing to know before writing about a task in a suite file.** `cli/test/assertion-debt.test.mjs`
walks these trees for `SH<n>` and cannot tell an assertion from a comment, so naming an id in prose
**pays off its debt**. It happened three times while SH356 was being written, and the workaround
was to stop naming the ids, prose degraded to protect a counter. SH630 is the repair; until it
ships, name in a suite file only the ids that file actually asserts.

## The debt reader counts an id a suite *names* (SH567)

`assertion-debt.test.mjs` decides a task is asserted by finding its id in a suite file. That is what
makes it cheap and what makes it wrong in one specific way: **a mention is not an assertion.**

It has now happened three times in one run, each time costing a red build and a correction:

- A backend test's assertion message said "SH7's review queue is the moderation step" while
  asserting something else entirely. The id came off the debt list; the debt was not paid.
- A new site test named `SH530` in its header prose, as a reference to that task's rule.
- A test filed under `SH537` asserted route parity, which is `SH532`'s claim, not SH537's.

The rule that follows: **name an id only where the file asserts it**, and write cross-references
without the bare token. Nothing enforces this, the reader cannot tell prose from proof, which is
precisely why the discipline sits with the writer.

**A second trap, found in the same task.** `shio-site/package.json`'s `test` script named its three
files by hand, so a fourth was scanned by the reader and **never executed**: the debt would have
read as paid by assertions nobody ran. It is a glob now. The general shape is worth carrying, an
instrument that reads a tree and a runner that reads a list will diverge, and the divergence is
silent in the direction that looks like progress.

SH567's own subject turned out to be already fixed (the reader walks `shio-site/scripts`), so the
task became its second half: re-judging the ten. Three are now asserted beside the script that owns
each (the generated token figures, the prerender's route table, the Markdown twin) and the debt
went 39 → 36. What is left of that group is the **copy**, which is the harder half and the reason
the group ranks second by return-risk: a marketing claim that has gone false is invisible until
somebody reads the page against the product.

## The debt reader could not tell an assertion from a mention (SH630)

`assertion-debt.test.mjs` matched `SH\d+` over the raw text of every suite file, so a comment and an
assertion message were the same bytes. **The direction of the failure is what made it worth
fixing**: it never invented debt, it only *retired* it, silently, in a diff about something else, at
the moment somebody was writing carefully about why a task mattered. The better the comment, the
more debt it paid. And the workaround in use was to **stop naming ids in prose**, which degrades the
writing to protect the counter: the instrument shaping what may be written about the code it reads.

### The count chose the rule

SH630 named two candidate repairs, restrict recognition to an assertion's message argument, or
require an explicit marker, and said a count decides. Measured over the suite corpus:

| rule | ids |
|---|---|
| raw text (the old rule) | 524 |
| not on a full-line comment | 428 |
| …and inside a string literal | **428** |

**The two candidates give the same answer.** Every id that survives the comment rule is already
inside an assertion message or a test name, so the parse buys nothing and the marker convention is
not needed to distinguish anything. The simpler rule wins. **96 ids were being paid by prose.**

### Three scanners, quietly wrong

The rule holds no cross-line state outside Java, and that is not a simplification, it is what three
failures cost:

1. The first measurement used a proper comment stripper. `replication.conformance.mjs` contains a
   regex literal with a double quote inside it; the scanner read it as a string opening and
   misclassified **every line after it** in that file. It reported a plausible number and was wrong.
2. The example ids in the new tests were `SH333`/`SH529` (real tasks) so the tests **paid those
   debts by illustration**. Now `SH91xx`, checked against the ledger rather than trusted.
3. The Java text-block toggle read `"""`… and the line containing that check **contained one**, so
   the reader flipped into block mode while reading itself and counted its own comments as code. The
   fence is now built with `String.fromCharCode`, and the toggle only runs on `.java`.

Each was silently wrong rather than loudly broken, which is exactly why the task said a count
decides and why the measurement is the first deliverable rather than a step toward one.

### The debt went 35 → 124, and the 88 are held apart

Nothing regressed; the number the reader always should have reported appeared. They sit in
**`REVEALED`**, not merged into `DEBT`, because every `DEBT` group carries an SH529 verdict, EARNS
or DOES NOT, with the reason, and none of these has been through that pass. Merging would make 35
considered decisions indistinguishable from 88 unexamined ones, which is the ceiling failure in a
different costume. Triage is **SH642**.

**SH641 was retired as a duplicate**: filed one commit earlier because SH630 was misread as shipped.
Its one distinct argument, that an assertion message *is* a string literal, so stripping comments
alone would be wrong, is carried here, where the measurement then showed both rules agree.

## The triage that changed what the number meant (SH642)

SH630 took the debt from 35 to 124 by fixing what counts as an assertion. SH642 asked SH529's
question of the 88 it revealed (**which of these earn an assertion?**) and the answer was not one
of SH529's two.

**Every one of the 88 is already named by a suite file, and 63 of them within a dozen lines of an
assertion.** None is silent; none is named only in source. So the verdict for the whole population
is neither EARNS nor DOES NOT: **the test exists and does not carry the id where the reader can see
it.**

That is a different debt from the original 35, which are tasks with *nothing standing behind them*,
and one figure mispriced both. `124 in debt` read as "124 tasks with no assertion", which was false
of two thirds of it. The reader now prints two numbers.

### The pointer is the verdict and the payment instruction at once

`REVEALED` is a map from id to the suite file that names it today, split into two groups:

- **CLOSE (63)**: the mention sits beside an assertion. Paying the entry off is moving the id out
  of that file's prose and into the assertion's message, *if* that assertion is genuinely the one
  that would have caught the defect, the judgement no measurement makes.
- **HEADER (25)**: named in a class docstring: the file is about the task and no single assertion
  carries it. The expensive half, marked so nobody starts with it by accident.

Two guards keep the triage from rotting: every pointer must still name its task (a pointer at a file
that no longer mentions it sends the next reader on a wasted trip), and the two groups must
partition the map, an id in `HEADER` but not in `REVEALED` would lengthen `CLOSE` by subtraction,
which reads as progress.

**Why this rather than 88 paragraphs.** SH529's contribution was that a list is not a decision. But
a decision written 88 times without evidence is a list wearing prose; the measurement is what makes
each entry actionable, and it took one script rather than a day of reading.

## The sweep nobody asked for, and the reader that now asks (SH861)

**Block Q finished with one of three surfaces moved.** The upkeep rule names `docs-shio`,
`shio-site` and `README.md`; `COVERAGE.md` was brought up to date and the other two were not, and
the asymmetry has a cause worth keeping: **the one that moved is the one a gate asked about.**
SH799's queue ceiling failed the build at six unnamed ids, so the coverage half became a build
problem and got done. Nothing asked about the other two, so nothing happened to them, and what they
said was a replication story ending at *convert*: no acceptance criterion, no `shio judge`, and no
mention that looking like the source is deliberately not one of authorable mode's tests, the
sentence that let the block close after six false closes.

`cli/test/block-sweep.test.mjs` is the reader. For every block the ledger records and the roadmap no
longer declares a heading for (which is what `roadkeep block drop` leaves behind) it asks whether
the three surfaces moved since that block's own last ship, and it holds each recently finished block
to a **declaration**: `swept: true` with what moved, or `swept: false` with why no outward surface
describes what the block changed. The first is verified against `git log`; the second is verified by
nothing, which is why it must carry a reason a reader can disagree with. That is `handoffs.test.mjs`'
division, the classification is written down and the half that can rot is checked.

Three measurements shaped it, and each is the kind of thing only a run says:

- **The ledger is not newest-first under a heading.** Reading a block's ids in file order reported
  Block Q as finishing on SH829, thirty-one entries before the one that closed it. The log's order is
  the authority; the file's is not.
- **One `git log`, not one per id.** A `-S` pickaxe per ledger entry, three hundred searches over
  the whole history, ran past two minutes without finishing. One diff of one file answers the same
  question.
- **The window is a session, not a month.** At forty commits it named nine blocks, seven of them
  instrument rounds finished a fortnight ago whose sweep nobody was ever going to write. That is the
  shape SH642's 88 triaged entries took, and none of them moved. Twelve names the blocks that closed
  in the session doing the asking, which is the only moment the answer is cheap. What falls out of the
  window is not forgiven, the declaration stays whether or not the block is still in scope.

And the working tree counts as movement, deliberately: a sweep and the ship that closes the block are
one commit, so a reader that only looked at history would fail on the run *before* that commit and
pass on the run after, a gate nobody can satisfy while doing the work it asks for.

## Block K's sweep: what the four surfaces actually needed (2026-08-10)

Recorded because the sweep's value is in the *verdicts*, not in the edits, and three of the four
surfaces needed nothing.

**`docs-shio`** took the block's only user-facing half: `state=` on `/agent/read` (with the rule that
an absent row is a **404 that says which**, never a quiet fall back to the draft) and `shio diff
--against published` in the CLI verb table. A third page earned a section nobody would have thought
to look for, `multi-tenancy.md` now says that **background work runs per tenant**, because "the
timer fired for everyone" is the assumption an operator carries and it was true only on a
single-tenant install.

**The COVERAGE summary was wrong before this block touched it.** It claimed 5 🔴 orphans; the tables
have held **6** since the file shipped, `shio audit`'s eleven rules were listed as an orphan and
never counted. Corrected, and the correction is annotated: an independent count of every
status-bearing row comes to 48 against the 40 the summary was authored for, so the ✅/🧩 figures are
carried forward plus this block's one new row rather than recounted. **Reconciling that taxonomy is
its own task, not something to guess at inside a sweep**, a summary that is confidently wrong is
worse than one that says which half it is sure of.

**`shio-site` needed no claim changed, and that is the verdict rather than an omission.** Its P9 line
names the *kind* of proof ("a lint, the routes, a render digest") rather than an inventory, so a
fourth proof does not falsify it. The one figure the block moved, `tools/list` up ten tokens for
`shio_read`'s new argument, the build regenerated from `token-budgets.properties` with nothing typed
by hand: the whole reason that generator exists, doing its job unattended.

**The root README needed nothing, and a test says so.** `readme-surface.test.mjs` holds the verb
count against the command registry and the MCP tool list against the server; Block K added neither, so
the counts stayed right without anybody checking them. That is SH338's rule paying off, the surface
most likely to rot is the one with an instrument on it.

## A version in prose is a claim nothing derives (SH628)

`developer-guide` carried **Elasticsearch 9.3.3** for two blocks, a version that was
never this product's, of a dependency it does not have, while every name on the page
resolved and `docs-vocabulary` reported green. That is the shape a vocabulary check
structurally cannot see: it asks *does this name still exist*, and a stack row is a
**number about somebody else's artefact**.

**`cli/conformance/docs-stack.conformance.mjs`** is the third instrument beside SH499's
two. It reads Java, Spring Boot, Node and pnpm out of `pom.xml`, `shio-app/pom.xml` and
`package.json`, and fails on any page stating a different one, across `docs-shio/` **and
the root `README.md`**, because the README is the widest-reach copy of the same claim.
A page may omit a version; it may not state a wrong one.

Two decisions worth keeping:

- **A retired dependency is refused as a *claim*, not as a word.** `architecture-overview`
  says "Elasticsearch **is not** a dependency", which is the sentence the check exists to
  make true. So a match counts only in a table cell or carrying a version, and each entry
  in `RETIRED` names what is true instead, "remove the row" is not actionable.
- **`COVERAGE.md` is excluded**, because it is the ledger that records stale pages and has
  to be able to write the name of the defect it is tracking.

**It also found a defect in SH499's own reader.** `shioProperties` flattened every
`private` field onto the outer prefix, so a nested `@ConfigurationProperties` group was
wrong in both directions at once: `ShCdaProperties` produced `shio.cda.ttl-seconds`, which
binds nothing, and never produced `shio.cda.preview.ttl-seconds`, which is real, so a page
documenting the correct key was reported as documenting one that does not exist. The two
errors hid each other, which is why it survived. `boundKeys` cuts each nested class body out
before reading the outer fields, and both directions are asserted in the SH499 self-check.

## A floor may only fire when the reader breaks (SH672)

Every reader over the governed docs carries a **non-vacuity floor**: a number it must clear, so a
regex that stopped matching cannot report *"zero problems out of zero subjects"* and be green for the
one reason that proves nothing. The floors are right. Three of them were the wrong *kind* of number.

`cited.length > 5`, `anchors.size > 20` and `listed.length > 10` all counted a corpus **meant to
empty**, `roadkeep ship` drops a rationale section and takes its citation and its contents row with
it, so each was a second deadline nobody declared, enforced by a test whose stated purpose is
something else.

Not a prediction: `improvements-debt.test.mjs` records three earlier floors firing this way in one
session, when shipping five tasks took the backlog from 24 to 19 while every floor still said 20, and
a fourth when SH226 took the file from 51 sections to 50 against `> 50`. **Measured at the moment
this shipped, `§` citations stood at 6 against a floor of 5**, one ship from firing, on a corpus
that was correct.

### Classify by what the floor counts

| the subject counts | example | an absolute floor is |
|---|---|---|
| a ledger, a registry, a source tree, **grows** | `shipped.size > 100` | evidence: it can only fall if the reader broke |
| the roadmap, the rationale, **meant to reach zero** | `anchors.size > 20` | a deadline nobody declared |

Over a shrinking corpus the only honest floor is *"the reader read something"*, which is `> 0`. That
is the claim a non-vacuity floor was always making; the larger number was a guess about how much work
would remain.

### The instrument, not the three edits

`floor-kind.test.mjs` walks the suites, finds every numeric floor, and fails on one over a shrinking
corpus that demands more than `> 0`. What is tolerated is held as a **named list with a reason each**,
the `assertion-debt` shape, for its reason: a ceiling lets one task pay it and the next spend it.

Three entries are on it today and none is debt: `shipped`, `coIds` and `box.files` count growing
corpora and sit in files that *also* read the shrinking ones, which is what the walk keys on.
Declaring them rather than auto-classifying is deliberate, deciding which corpus a subject counts is
a judgement about the test, and a heuristic over variable names is the kind of guess this file exists
to stop.

## The ratchet a Shio commit cannot satisfy (SH771)

`pnpm conformance` against a clean instance: **121 of 122 pass**. The one failure was not a product
defect at all.

```
493 shipped ids are not named in COVERAGE.md, against a recorded 490.
```

SH764, SH765 and SH768 had shipped and the matrix had not moved. SH747's ratchet is right to say so.
What it could not do is let the fix land in the same commit: **`COVERAGE.md` is in `vigletdocs`**, so
the rule this project states everywhere (one task, one commit, with the doc sync in it) is
unsatisfiable for that file by construction. Three consecutive pushes went over the red, which is
the third state SH579 says does not exist, reached by following the rules rather than by skipping
them.

### The decision: the ratchet stays, because SH770 already bounded the interval

A conformance run leaves a stamp, and since SH770 a stamp taken against another commit is not read
as this tree's colour. So the next ship makes a red conformance run **stale** rather than accusing:
it stops blocking without anybody declaring an exception, and it stops excusing the moment somebody
runs the gate again. The property SH771 complained about, *an instrument that blocks work it will
not let you finish*, was removed by a task shipped the same afternoon, from the other end.

The obligation is not lost, because it lives where it can be acted on. SH758's reader runs inside
`pnpm test`, on **every** commit, and prints the ids the matrix does not name yet. That is the queue
this task asked for, and it already existed; what was missing was a gate that stopped treating the
gap as a failure of the commit in front of it.

### What did change: an instruction nobody could follow

The refusal used to end *"then lower UNNAMED in the same commit"*. That commit does not exist, the
matrix is in one repository and the ceiling is in another, so somebody following the instruction
discovers it is impossible only after doing the first half. It now names both commits:

```
1. vigletdocs/docs-shio/COVERAGE.md — name the ids in their cluster rows
2. here — `pnpm run coverage:mirror`, then lower UNNAMED to the new count
```

P6 is about a fix somebody can perform, and an instruction that cannot be carried out in the
repository it is printed in fails that in the most expensive way: it looks actionable.

### The queue is printed by the cheap gate and enforced by the dear one (SH797)

Twelve tasks then shipped in one session with the matrix untouched, SH785 to SH796, taking the count
to **502 against the recorded 490**. Nothing hid it. `pnpm test` printed
`SH758: 11 shipped id(s) COVERAGE.md does not name yet`, **with the ids**, on every one of those runs,
and every run was read for its `pass/fail` summary instead.

That is the asymmetry to remember, because it is structural rather than careless. The queue is
**printed** by `pnpm test`, which runs constantly and cannot fail on it; it is **enforced** by
`pnpm conformance:docs`, which needs `vigletdocs` checked out and is the gate least often run. So the
cheap gate whispers and the dear gate is the only thing that stops you, which is how a dozen
accumulate instead of one. A debt printed among fifteen hundred green lines is a debt nobody reads.

Two things worth copying from the repair. **Name the task doing the naming**: shipping SH797 would
itself have raised the count by one, one commit after the commit that lowered it, so it went into the
same row. And **the local comparators were right the whole time**, every Java run reported
`conformance — NOT this tree, so not a colour`, because the stamp was twelve commits old (SH770).
Nobody was told green; nobody looked.

### The queue is bounded, so it can stop being one (SH799)

SH771's argument for printing rather than failing is right about **a queue**, one or two ids between
a ship and the next docs pass, and it says nothing about twelve. So `pnpm test` now fails past
**five**, which is a session's ordinary output: at that bound SH797's backlog would have stopped the
sixth ship instead of the twelfth.

A ceiling, where every other ledger here is a named list, and the usual objection does not apply: *a
ceiling lets one task pay the debt and the next spend it* is about **permanent exemptions**, and an
entry in this population leaves the moment somebody names the id. Nothing accumulates, so there is
nothing to spend.

The bound is asserted at both sides of itself with synthetic ids rather than waited for, reaching
six real ones to discover whether the guard works is the failure it exists to prevent, and the
number is held against the two facts that chose it: at least one, or it fails the commit that ships
(what SH771 refused), and under twelve, or it would not have fired during the session that produced
the incident.

## Work that cannot start yet, and how to say so (SH784)

A task waiting on something **no commit can produce**, an intermittent failure reappearing, an
upstream release, somebody else's decision, used to be offered by `roadkeep pick` on every
iteration, and declined by hand each time. The two spellings that looked available were both wrong:
`⏳` is *in flight*, which is what `pick` ranks first; `retire` is terminal, and deletes the
rationale.

**The third one already exists, and it is a dep.** `pick`'s own contract says it: *a task blocked
outside the backlog is never offered, because shipping cannot unblock it.* A dep is not only an id,
it accepts free text naming work outside the backlog:

```
roadkeep amend SH778 --deps "a further sighting"
```

```
ready 1, blocked 1, outside 1
```

The task keeps its line, its marker, its section and its id. `pick` stops offering it, and anything
depending on it is `blocked` rather than ready. Nothing is deleted and nothing is deferred.

### Two things to know before reaching for it

**A dep spends the line's budget.** The rendered line is capped, and `deps:` sits inside that cap, so
adding one can push a `why` that already fit over the limit, the refusal names the new allowance and
the overage. Expect to shorten the sentence in the same call.

**It is not the same as pausing.** A deferred store (`deferred = "<path>"` under `[files]`) is a
real roadkeep feature and this project deliberately declares none. It would move the line *out of*
`ROADMAP.md`, and both ledger readers require an exception's task to be **on** the roadmap (SH579,
SH594), so deferring a task would delete the reason for the `red-suites.json` entry tracking its red.
The dep keeps the line where those readers can see it, which is why it is the cheaper answer here and
not merely the smaller one.

## A claim that a capability is absent, over a ledger that says it shipped (SH769)

Two turned up in one session, and only one had an instrument.

The first was caught: `improvements-debt.test.mjs` failed on an anchor a rationale cited and a ship
had removed, written by `roadkeep section amend`, which validates the prose and never resolves the
anchors it names. That half is the tool's, and is filed there as **RK1227**.

The second was not caught at all, and was found by reading. `docs/agents/agent-surface.md` said, of
the regions reader, that `styled by` *"is deliberately not here: it needs class-to-stylesheet
provenance, which `definedIn` does not keep."* SH694 kept the provenance and shipped the warning.
The sentence sat **four lines above** the section documenting that, and had been false since SH694
landed. A reader deciding whether to build the feature would have believed it.

### The obvious rule was measured first, and is noise

*An absence claim naming a shipped id is stale* sounds decidable. Measured over `docs/agents/**`,
`agents.md` and `docs/specs/**`: **98 sentences assert an absence, 38 of them beside a shipped id**,
and almost every one is true, *"there is no submissions subsystem"*, *"`{{#if_eq}}` does not
exist"*. A check reporting those is read once and then ignored, which is the failure SH551 and SH142
both name.

Narrowing to the phrasing that claims something about *this product, now*, `is deliberately not
here`, `is not part of`, `is not yet`, `is not available`, leaves **eight**. That is a population
somebody can sign.

One more narrowing came from writing the check: a phrase inside backticks or quotation marks is
being **named**, not asserted. This very section named two of them and quoted the sentence that cost
the task, and the scan read the page about the rule as two breaches of it, SH740's distinction, one
level up, and the reason its own scanners strip comments before matching. Stripping quotations also
removed two entries that had been signed by hand: both were quotations of an earlier argument rather
than claims these documents make.

### So it is a signed list, and the signature is the instrument

`cli/test/absent-claims.test.mjs` holds each claim with **one sentence naming what would make it
false**. A new claim fails until somebody writes that sentence, and writing it is the check. A claim
whose prose changed re-keys and has to be re-read by whoever changed it. A signed claim whose
sentence is gone fails too, an exemption for something that no longer exists is a line nobody
removes and the next reader trusts (SH142).

**What it cannot do is said in the file.** It cannot catch the *ship* that falsifies a standing
claim, which is the half that cost SH769: the ledger entry for SH694 does not contain the phrase
`styled by`, and the sentence's own reasoning was about a Java method's behaviour. Nothing short of
reading finds that. What changed is that the reading now has a list of ten to start from, and a
written reason beside each to check it against.

The guard is not vacuous, and that was proved rather than assumed: re-introducing the exact sentence
that cost the task makes the test fail, naming it.

## The obligation a ship does not carry, and where it is now asked (SH758)

`COVERAGE.md` lives in **vigletdocs**; the ship lives here. SH747's ratchet works, six ids in one
session, five in the next, but it can only fire from a conformance run that needs an instance, the
docs checkout and somebody choosing to look. By then the person asked to write a cluster sentence
is the person furthest from the reasons for it.

**`docs/coverage-named.json` is a mirror of which ids the matrix names**, generated by
`pnpm run coverage:mirror` and **never edited by hand**. `cli/test/coverage-obligation.test.mjs`
then asks the narrow question on every `pnpm test`: *of the ids added to the ledger since the mirror
was refreshed, which does the matrix not name?*, read from `git log <at>..HEAD -p -- CHANGELOG.md`,
so it is about what **you** just shipped rather than about the whole history SH747 already holds.

**It prints; it does not fail.** §XXIV.5 said outright that this must not be made urgent, and the
reason survives the fix: the failure being prevented is the *silent* one, and SH747 already
prevents it. A gate going red on every ship until a second repository is edited would turn one
commit into two on pain of a red suite, a cost nobody weighed. So enforcement stays with the
ratchet and what changes is **when** somebody finds out.

**A second copy of a fact is the shape SH673 says drifts**, so two things hold it: the mirror is
generated rather than written, and `docs-coverage.conformance.mjs` asserts it still equals what the
real matrix names. A hand-edit, or a mirror left stale across a matrix change, fails the gate that
reads the authoritative file. The copy is checked against the authority, never trusted beside it.

**Workflow:** name the ids in their cluster rows in `docs-shio/COVERAGE.md`, then
`SHIO_DOCS_DIR=<path>/docs-shio pnpm run coverage:mirror`, and commit the mirror here in the same
pass as the matrix there.

**And the authority it is checked against was a directory, which is not a revision (SH1115).** Both
readers above take `COVERAGE.md` out of a working *checkout*, so nothing asked whether the edit that
put an id in the matrix had ever been committed **in the repository the matrix belongs to**. Observed
rather than reasoned: clearing SH799's queue twice in one session left `COVERAGE.md` modified and
uncommitted both times, and the mirror was refreshed from that working tree and committed here each
pass. Because the conformance check reads the same working copy, mirror and matrix then agree
*permanently*, while the published page never gains the sentence and SH758's reader subtracts the
ids forever. The `at` field looks like it covers this and does not: it is **this** repository's HEAD,
which `shippedSince` needs and which says nothing about the other one.

So `refresh` records a second fact (`matrix: { at, clean, provisional }`) where `at` is the *docs*
repository's HEAD, `clean` says whether the file it read is in it, and `provisional` names the ids
the matrix's own HEAD does **not** carry, computed from `git show HEAD:./COVERAGE.md`.
`coverage-obligation.test.mjs` subtracts those, so an id named only by an uncommitted edit still owes
a row and the question comes back.

**Marked, not refused, and the distinction is the point.** A refusal makes the state something the
mirror cannot hold, and it blocks the session that edits the matrix, mirrors, and commits both, the
right things in the right order. Naming the ids means an id from an uncommitted file is *a state the
mirror can express* rather than one it forgets, and re-running against the same dirty tree keeps
reporting instead of going quiet. The refresh says so on the run that writes it, because the person
who can still commit the matrix is the one reading that line.

Two limits worth knowing. `named` deliberately stays the **working copy's** set, because the
conformance check holds the mirror equal to what the real file names, the copy is checked against
the authority, and the authority is the file; `provisional` is how the mirror disagrees with itself
honestly rather than by holding a different set. And a mirror written before SH1115 has no `matrix`
key, which reads as nothing provisional: the only backward-compatible answer, and the reason the
warning is at the writer rather than left for a reader to notice.

## A capability is a row the matrix owes (SH1069)

SH747 counts shipped **ids**, which is how a contributor sees work. A reader of the public docs sees
**capabilities**, and the product publishes exactly one list of them: the manifest's `features`
map. `docs-coverage.conformance.mjs` now reads every key from the `features.put("…")` calls
(`manifestFeatures` in `conformance/support/product-vocabulary.mjs`) and counts the ones
`COVERAGE.md` does not spell in backticks. The run prints the count and the unnamed keys.

It opened as a ratchet, `UNNAMED_FEATURES`, for SH747's reason: the matrix named one key of 63 when
this landed, and a gate that opens with 62 findings is one nobody reads. The debt is paid: COVERAGE.md
now carries **The manifest's feature keys**, one row per key with what it says and the page that
documents it, and the ceiling is **0**, so **a new key with no row fails**. That table's last column
is headed *Coverage* rather than *Status* on purpose: the summary sentence counts rows under a
*Status* header as clusters, and a key is a capability whose cluster already has a row, so counting
it again would inflate the figure by 63.

The block-completion half of SH1069 already existed as SH861's reader (above), so nothing new was
built for it. The design's other population, "every surface in AY6's declaration", pointed at an
anchor that does not exist anywhere in this tree, so nothing was built for it either.
