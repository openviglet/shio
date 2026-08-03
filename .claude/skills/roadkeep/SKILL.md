---
name: roadkeep
description: Call the roadkeep CLI instead of editing a project's governed ROADMAP.md, CHANGELOG.md, IMPROVEMENTS.md or STRATEGY.md. Use when adding, shipping, retiring or recording a task, changing a marker, writing a rationale section, picking what to work on next, or reading a backlog, ledger or dependency graph — and whenever an edit to one of those files was denied or `roadkeep lint` reported a violation. Trigger words: roadmap, backlog, changelog, task line, block, ship, retire, next task, roadkeep.
---

# roadkeep — call the command, never type the format

The line format is a schema at the point of insertion, not a convention to remember. Every
field is validated before a sentence exists, so a refusal costs a retry and never a deletion.
Read `roadkeep.toml` for this project's prefix, id shape, paths, markers and limits (L6);
nothing below hardcodes them. `roadkeep` is the installed entry point — `python -m roadkeep.cli` when it is
not on PATH.

## Writing and shipping

When the `mcp__roadkeep__*` tools are available, **prefer them**: the whole write path and
the reads a task needs are there — `add`, `block_add`, `claim`, `status`, `amend`, `ship`, `retire`, `defer`, `resume`,
`record_add`,
`record_amend`, `record_drop`, `record_renumber`, `non_goal_add`, `non_goal_drop`, `section_add`, `section_amend`, `section_drop`, `brief`, `pick`, `list`, `deps`, `lint` — same engine and same
refusals, with
the fields arriving as a schema instead of flag names typed from memory. `init`, `adopt` and
`install` run once per project and want the CLI — the last of them wires this file, the tools
and the guard into a project running the tool from a checkout, and `install --check` is what
holds its copy of this file in step. Every guarantee below holds either way.

`roadkeep <add|status|amend|ship|retire|record|non-goal|section> --help` has the flags. What they guarantee,
so it costs you no thought: the id, the `→ §<id>` pointer, the status default and every
`(deps: … ✅)` annotation are **derived, never typed** — where a project declares `prefix` as a
list it numbers by track, and then `add --prefix <letter>` says which track while the number
stays derived, per family; a refusal exits 2 naming the length and
the limit and writes nothing; the shipped marker never reaches the roadmap. **A line renders a
pointer, and the pointer has to resolve**: `add --section "<title>"` writes the rationale in
the same transaction — the prose on stdin or `--section-body`, both files validated before
either is written — and an `add` without it answers with the `section add` that closes the
pointer it just created, rather than leaving the gate to say so. **`ship <id> --why "<what now works>"` makes
its three edits** (ledger entry, roadmap line gone, `§<id>` deleted) plus the dependents'
annotations, or none — and `--why` is **required**, because the roadmap's sentence states a
problem and the ledger's states an outcome, so inheriting it files a defect report under a
heading meaning "done" (`record amend <id> --why` is the repair where one already did), and `retire <id> [--superseded-by <id>] --reason "…"` is the same
transaction, two more doors. **`ship <id>` is also how one that stopped halfway is finished**:
the ledger is written first, so a crash leaves the id in two files (`lint` says `id.two-files`)
and re-running `ship` closes the line without writing a second entry. It refuses instead where
the files say the work is in halves — a ⏳ line or an entry naming one — or where the line and
the entry describe different work, which is two tasks sharing an id and `renumber`'s to fix. **Half of it landing is a third answer, not a full ship
with a hedge in the sentence**: `ship <id> --part "<which half>"` records the entry as
`✅ **<id> (which half)**` and *leaves the line open* at ⏳ with its section intact, and the
later `ship <id>` completes it — replacing that entry in place and dropping the qualifier,
which is the only thing that keeps "local half" from outliving the local half. **A pause is none of those three**: `defer <id> --reason "…"` moves
the line to the deferred store, keeping the id, the deps, the symptom and the section a
departure deletes, and `resume <id> [--marker <m>]` is the return direction the ledger has
none of — the reason wraps the `why` on the way out and is unwrapped on the way back, and the
open marker is what the store could not keep, so `--marker` is where you say which it was.
A dep on a paused task resolves as **deferred**, and the line waiting on it as
`blocked-paused` — not offered, counted apart, and unblocked by a `resume` rather than a ship.
Reach for `retire` only when the work is not coming back. `record add --block <x> --symptom "…" --why "…"` is the fourth — never
planned, so the ledger entry alone and the roadmap untouched, and `record drop <id>` is its inverse:
refused unless the ledger states that id **twice** *and the two say the same thing*, then the
later entry goes and the first stays,
because removing the only record of a decision is deleting history. Two entries that differ are
two deliveries under one id, not one recorded twice: `record drop <id> --line <n>` if you have
read both, or `record renumber <id> --line <n>` to give one its own address. To *fix* an entry use
`record amend <id> --why "…"` (or `--part` on a partial) — never drop-and-re-add, which moves
the line to the end of its block and shows a reviewer a deletion where a word changed. `section add <id> --title "…"` is that
same write for a line that already exists, and
takes prose on **stdin**, within the word budget, filled to the configured width, under the
task's block — a table or list is inserted exactly as written. **`section amend <id>` is how a
live design is corrected**: `--body -` replaces its own prose, `--title` its heading, the
subtree and the anchor are untouched, and it is the only door — `section drop` is refused
while an open line points at the anchor, which is right, and shipping is not a way to fix a
paragraph. No write invents a block
heading — **`block add <x> --title "…"` is the one that declares one**, in every governed file already organised by blocks, placed after the last block's subtree and spelled at that file's own level and separator. Reach for it the moment any write refuses with "no heading declares". `non-goal add --lead "…" --why "…"` writes the one bullet that is not a task line,
where `[non_goals]` declares the list governed: addressed by its lead, which is unique and
checked, and carrying no marker, dep or pointer, because a constraint has no status to state.
`non-goal drop <lead>` is the other half, and what a *correction* takes: the lead is the address,
so a constraint whose lead changes is one dropped and one written. **Call `non-goal list` before
an `add`** — the list binds what may be proposed, so reading it after the line exists is reading
it too late; it prints on a project that never opted in, and nothing checks a proposal against it
for you, that being a judgement about meaning and this tool having no model (L4).
Every write prints one `event <id> Block <x> open|empty` line, the whole payload a
hook gets — a non-goal excepted, having neither an id nor a block. There is no second route: `Edit` on a governed file is denied, naming the command,
and `lint` gates the turn's end.

**An id is an address, and a merge can spend one twice.** `renumber <id> [--to <new>]` moves
the line, the `§<id>` section its pointer resolves to and every dep naming it, in one
transaction — the destination derived in the line's own family unless you name one, spelled the
way `[ids]` says this project spells one, and refused if any source already mentions it. A
**split** is the other direction and not this command: the cited number stays where it is, and
the half that is new is an `add --id <id>b` where `[ids] suffix` declares
one. The ledger is never opened, so the id the other branch
recorded stays theirs; the deps it moved are **named in the answer**, because which of two
collided ids a dep meant is the one thing the files do not say. `ship` and `retire` are wrong
here: both write a terminal entry for work nobody cancelled.

That leaves the two rules a schema cannot check:

`amend <id>` corrects an existing line's `why`, `--dep` group or `--ref` — the fields that are a
fact or a compression — and never its `symptom`, which is the claim the line is, or its `id`,
which is what `renumber` is for. That is the door
a project adopting the tool needs; a greenfield one rarely calls it.

A **merge conflict inside a governed file** is not a hand edit either. `merge --register`
wires `roadkeep merge` in as git's driver for the files `roadkeep.toml` declares, and it
merges by id: two branches appending under one heading is two additions, not a conflict, and
an id **both branches created** is reported by name for `renumber` to move. What it cannot
prove — prose changed on both sides, a line that does not round-trip, an output `lint` would
refuse — it hands back as git's own conflict markers and exits 1.

1. **`symptom` states what does not work** — never a solution name: a line named after its fix
   cannot be falsified, so it never gets closed, only abandoned.
2. **`why` is one sentence.** A second sentence is the signal the content belongs in the
   rationale file, which is what the pointer addresses.

Markers are `[markers]` in `roadkeep.toml`: the open set is the roadmap's, and the shipped and
retired ones are the ledger's alone — neither is legal in a roadmap. Limits are `[limits]`:
`roadkeep lint` names the file, line and column of anything over, and `--fix` repairs only
what is **derived** (annotation, pointer, dep order, marker codepoint, whitespace). On a
project that arrived with drift, an absolute count answers nothing: `--baseline <rev>`
(`HEAD` after a write) reports **what you added** and forgives the standing debt by name.

## Ask, don't count

Every query takes `--json`. **`weight [--block <x>]` is the other pre-`add` read**: what
comparable tasks cost, derived from the commits that shipped them, so whether the line being
written is one task or two is a question with an answer. An entry whose commit wrote several
is named under `batched` and left out of the percentiles, so a squashed adoption import
skews nothing. It ranks nothing and lands on no line — the size field is a non-goal. **`roadkeep brief [<id>]` starts a task in one call** — the line,
its rationale, deps resolved, the blocker chain, what it unblocks and the non-goals, bounded
to a tool result; with no id, `pick`'s own choice. Narrower: `next-id` never fills a gap;
`list|stats|audit [--block <x>]` counts and lists, naming every marker line neither could
read; `claims` is the registry read against the files — held, expired or stale, oldest first, where
each id went and where the registry lives, and `--prune` drops the rows that are not claims; `show <id>` joins one line, its section and its paths; `deps <id>` walks the graph both
ways; `gaps` resolves an id in neither file against the commit that removed it; `origin <id>
--why` reads it out of history. And **never restate a count in prose**: `export
[--readme|--site|--json]` projects it.

## Picking work

`roadkeep brief [--block <x>]` picks and briefs in one call, printing why: in-progress first,
then `priority` in `roadkeep.toml`, then the lowest ready id, never one blocked outside.
**Scope it to finish a block**: only "nothing is open in Block <x>" means finished — unscoped,
the answer may be another block's, and the block order is the headings' own (`list`).
**Ready is not implementable**: the tiers rank by id, so add `--designed` when you asked to
*execute* and not to plan — it sets aside the markers `[markers] undesigned` names, and says
how many. Without it the answer still tells you, in the same sentence that names the tier,
that the line it chose has its design to write — which is a `section add`, not a commit.
**Two workers in one checkout need `--claim`**, on `brief` as well as on `pick`: every tier is
a function of the file, so a second caller reading an unchanged backlog is handed the line the
first one took — most confidently by the in-progress tier, a 🛠 line being evidence somebody
started. `--claim` answers *and* moves the marker to in-progress in one transaction, so the
next caller is sent elsewhere. `brief --claim` is the one to reach for, being the call that
starts a task anyway — and over MCP it is its own tool, `claim`, so that `brief` and `pick`
keep the read-only hint that makes asking free; `brief <id> --claim` takes a line you were
told to work on, and is **refused** where somebody already holds that one, there being nothing
for it to choose instead. **The claim follows the marker**, so `status <id>` on the in-progress
one is the third way to start work and takes one too — refused the same way where somebody
already holds that line — while any other marker drops it and is never refused, that being how
a claim is given back. Nothing re-dates a live claim: it is an expiry and not a lock, stepped
over once `[claims] held` has passed, and `ship`, `defer` and `renumber` each do the right
thing with one. A
held line is **named** in the answer and never hidden, because a claim carries no owner and
the id is the only thing you can recognise your own by; who took it belongs in the commit.

## One task, one commit

What `ship` wrote goes in the *same* commit as the code, so the docs never describe a state
that did not ship — and a batch of ready tasks is not permission to batch the commits.
