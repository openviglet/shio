---
name: roadkeep
description: "Call the roadkeep CLI instead of editing a project's governed ROADMAP.md, CHANGELOG.md, IMPROVEMENTS.md, DECISIONS.md or STRATEGY.md. Use when adding, shipping, retiring or recording a task, changing a marker, writing a rationale section or a decision record, picking what to work on next, or reading a backlog, ledger or dependency graph — and whenever an edit to one of those files was denied or `roadkeep lint` reported a violation. Trigger words: roadmap, backlog, changelog, decision, ADR, task line, block, ship, retire, next task, roadkeep."
---

# roadkeep — call the command, never type the format

The line format is a schema at the point of insertion, not a convention to remember. Every
field is validated before a sentence exists, so a refusal costs a retry and never a
deletion. Read `roadkeep.toml` for this project's prefix, id shape, paths, markers and
limits (L6); nothing below hardcodes them. `python ".claude/hooks/roadkeep-launch.py"` is this project's entry point — the package is not installed here and `roadkeep` is on no PATH, and that launcher is committed to this repository so it finds an engine wherever this environment has one.

## The loop, in four calls

**`brief`** picks the next line and briefs it in one read — the tier it chose by, the deps,
the design section, what shipping it unblocks and the non-goals that bind it — so a session
starts with a call and not a file read. **`add`** files a new task. **`ship <id> --why
"<what now works>"`** closes one across three files in a single transaction, or none of
them. **`lint`** is the gate that says whether any of it landed, and it exits non-zero,
which is the whole difference between a gate and advice.

`<verb> --help` has the flags, and every field a schema can derive — the id, the `→ §<id>`
pointer, the status default, every dep annotation — is **derived and never typed**. Where
two workers share a checkout, reach for `brief --claim`: it answers and takes the line in
one transaction, so the next caller is sent elsewhere. Over the tool surface that is its
own tool, named `claim`.

## The reads, which are the half that goes unfound

None of these writes anything, so asking is free — and each is a turn you do not spend:

* **`budget`** prices a sentence *before* it exists: the room each field has, in characters
  and in the words a sentence is composed towards, plus the rules that are not widths. It
  is the pre-`add` read, and a refusal you never meet costs nothing.
* **`explain <code>`** says what a gate finding is, what produces it and which doors close
  it. Reach for it on a code you have not met, and never grep the package for one.
* **`repair`** spends a whole report in one call — every finding whose remedy is a complete
  command, run in order. Reach for it the moment `lint` reports anything.
* **`show`**, **`section show`** and **`section find`** read a line and its prose back, so
  an edit is composed against what is there and not a remembered version of it.
* **`list`**, **`deps`**, **`delivered`**, **`unclosed`**, **`gaps`**, **`writes`** and
  **`cost`** answer about the backlog whole: what a block already shipped, what it still
  owes, what this session wrote, and what a surface costs the turn that loads it.
  **`export`** projects a count so no prose has to restate one.

## The correction that is not an `amend`

`restate <id> --symptom "…"` is the door when the claim a line makes turned out false: the
id, the deps, the marker and the design all stay, because the work never changed and only
the description of it was wrong. Reach for it instead of `retire` plus `add`, which spends
an id and deletes a design that was already right.

1. **`symptom` states what does not work** — never a solution name: a line named after its
   fix cannot be falsified, so it never gets closed, only abandoned. 2. **`why` is one
   sentence.** A second sentence is the signal the content belongs in the rationale file,
   which is what the pointer addresses.

Markers are `[markers]` in `roadkeep.toml`: the open set is the roadmap's, `working` is the
one a claim is taken at, and the shipped and retired ones are the ledger's alone — neither is
legal in a roadmap. Limits are
`[limits]`: `roadkeep lint` names the file, line and column of anything over, and `--fix`
repairs only what is **derived** (annotation, pointer, dep order, marker codepoint,
whitespace, the queue entry whose task shipped or was retired, and a criteria heading
addressed to nothing and holding nothing — each named in the report and never dropped in
silence). On a project that arrived with drift, an absolute count answers
nothing: `lint --baseline <rev>` (`HEAD` after a write) reports **what you added** and
forgives the standing debt by name.

## Where the rest is

Two pages sit beside this file, and they are read when a turn needs them and not before:

* **`writing.md`** — the write path whole: every flag on `add`, `status`, `amend`,
  `restate`, `ship`, `retire`, `record`, `section`, `non-goal`, `defer`, `resume`, `dismiss`
  and `reopen`, what
  each transaction refuses and how the refusal is answered, the wiring verbs (`init`,
  `adopt`, `install`, `declare`, `engines`, `merge`), and every code the gate reports.
* **`asking.md`** — the query surface whole: what each read answers and in which units,
  what git answers about the ledger, the projections that go stale, and what a project may
  declare in `roadkeep.toml`.

Open the page rather than guessing, and open it rather than re-reading this one: this file
says which verb and where the rest is, and those two say what each verb does.

## Picking work

`roadkeep brief [--block <x>]` picks and briefs in one call, printing why: in-progress
first, then `priority` in `roadkeep.toml`, then the lowest ready id, never one blocked
outside. **Scope it to finish a block**, and the empty answer says which of three states
that block is in: **finished** — the ledger files entries under it — **empty**, a heading
declared before its lines, or **unknown**, the one that is a typo and the one that stays a
refusal. `--json` carries the word beside the sentence (`standing.state`, on `brief`,
`pick` and `list` alike), so a loop driving a block to completion branches on `finished`
and never matches English. Unscoped, the answer may be another block's, and the block
order is the headings' own (`list`, whose own empty listing says the same thing on
stderr). **Ready is not implementable**: the tiers rank by id, so reach for
`pick --block <label> --designed` when
you asked to *execute* and not to plan — it sets aside the markers `[markers] undesigned`
names, and says how many. Without it the answer still tells you, in the same sentence that
names the tier, that the line it chose has its design to write — which is a `section add`,
not a commit. **A pick you cannot execute is a write, not a workaround**: where what is
left of a line needs something *present* — a controller on the desk, two consoles to
measure against each other — the file has a slot for it, and until it is written the same
line comes back every call, because every tier is a function of the file. `add --requires
<word>` states it, `amend <id> --requires …` adds it to a line already there, and the word
is one `[requirements] declared` names — the one table `declare` does *not* open, because a
vocabulary is a list of words and an empty one governs nothing, so a project opting in
declares its own words once — as a list, or as a table saying what each word *is*, which
is what the refusal then quotes so a caller can weigh it rather than believe it. Then
`pick` sets those lines aside for a caller that did not
say it has them, **names** each with what it is missing, and still counts them ready: what
narrows is the offer, never the truth. A caller that does have the thing passes
`pick --have <word>`, repeatable, and `brief <id> --have <word> --designed` alike — which is
the whole difference from
`defer`, a pause being symmetric and taking the line away from the person who could have
finished it. So the honest end of an impossible pick is the requirement written and the id
handed over, never a fifth identical answer worked around in silence.
**An answer may say the line has been worked by proxy**: `against` names the shipped entries
whose own sentences cite this open id — the children a previous session filed on meeting a
task larger than itself. It narrows nothing and is not a marker; read it as the reason the
last caller did not close this line, and decide whether you are about to do the same.
**Two workers in one checkout need `--claim`**, on `brief` as well as on
`pick`: every tier is a function of the file, so a second caller reading an unchanged
backlog is handed the line the first one took — most confidently by the in-progress tier,
a 🛠 line being evidence somebody started. `--claim` answers *and* moves the marker to
in-progress in one transaction, so the next caller is sent elsewhere. `brief --claim` is
the one to reach for, being the call that starts a task anyway — and over MCP it is its
own tool, `claim`, so that `brief` and `pick` keep the read-only hint that makes asking
free; `brief <id> --claim` takes a line you were told to work on, and is **refused** where
somebody already holds that one, there being nothing for it to choose instead. An id the
ledger already holds briefs as `shipped` and quotes no cost for starting it: a task named
from anywhere other than `pick` may be one that is done. **The claim follows the marker**,
so `status <id>` on the in-progress one is the third way to start work and takes one too —
refused the same way where somebody already holds that line — while any other marker drops
it and is never refused, that being how a claim is given back. Nothing re-dates a live
claim: it is an expiry and not a lock, stepped over once `[claims] held` has passed, and
`ship`, `defer` and `renumber` each do the right thing with one. A held line is **named**
in the answer and never hidden, because a claim carries no owner and the id is the only
thing you can recognise your own by; who took it belongs in the commit.

## One task, one commit

What `ship` wrote goes in the *same* commit as the code, so the docs never describe a
state that did not ship — and a batch of ready tasks is not permission to batch the
commits.

Which is decidable only if the commit knows what is **its**. A claim carries a scope:
`claim <id> --path <p> …` says what this commit owns, declared verbatim and replacing
whatever was there, and `claim <id>` reads it back beside what **this task's own
transactions wrote** — the marker, the projections — what the working tree holds that
another live claim says is *its* own, what no claim names at all, and which declared path
would stage nothing right now. **Declare only your code**: the governed files are
supplied, and a scope naming them by hand carries paths that were never the work — the
analysis `git add -A` cannot make and a second session's work is what it sweeps up.
`claim <id> --add-path <p>` is the same write from the other end, for the file the work
turned up after the scope was declared; passing both is refused. Over MCP this verb is the tool
`scope` — not `claim`, which is `brief --claim` and takes a line; the two words are two
acts. `--porcelain` prints the paths alone, for `git add --`. Refused on a line no live
claim holds: taking a line is a marker, and nothing here dates one. **`ship` and `retire`
make that read themselves**, while the claim is still live: what the tree holds that no
claim names is named in the departure's own answer, so the analysis arrives at the moment
of committing rather than being remembered there — **and so is the `git add --` line for
the scope being released**, which after the ship no verb can answer: the claim is gone and
the id is in the ledger. Silent where no claim declared a path.
