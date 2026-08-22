---
name: roadkeep
description: "Call the roadkeep CLI instead of editing a project's governed ROADMAP.md, CHANGELOG.md, IMPROVEMENTS.md, DECISIONS.md or STRATEGY.md. Use when adding, shipping, retiring or recording a task, changing a marker, writing a rationale section or a decision record, picking what to work on next, or reading a backlog, ledger or dependency graph — and whenever an edit to one of those files was denied or `roadkeep lint` reported a violation. Trigger words: roadmap, backlog, changelog, decision, ADR, task line, block, ship, retire, next task, roadkeep."
---

# roadkeep — call the command, never type the format

The line format is a schema at the point of insertion, not a convention to remember. Every
field is validated before a sentence exists, so a refusal costs a retry and never a
deletion. Read `roadkeep.toml` for this project's prefix, id shape, paths, markers and
limits (L6); nothing below hardcodes them. `python ".claude/hooks/roadkeep-launch.py"` is this project's entry point — the package is not installed here and `roadkeep` is on no PATH, and that launcher is committed to this repository so it finds an engine wherever this environment has one.

## Writing and shipping

When this session's roadkeep tools are available, **prefer them** — named
`mcp__roadkeep__*` where a project's own `.mcp.json` declares the server and
`mcp__plugin_<plugin>_roadkeep__*` where a plugin provides it, so read the prefix off the
tool list rather than typing it: the whole write path and the reads a task needs are there
— `add`, `block_add`, `block_drop`, `block_merge`, `declare`, `claim`, `scope`, `status`, `amend`,
`restate`, `ship`, `retire`, `supersede`, `defer`, `resume`, `record_add`, `record_amend`,
`record_move`, `record_drop`, `record_renumber`, `non_goal_add`, `non_goal_amend`,
`non_goal_drop`, `criterion_add`, `criterion_amend`, `criterion_drop`, `criterion_list`,
`section_add`, `section_amend`, `section_move`, `section_drop`, `budget`,
`brief`, `pick`, `list`, `deps`, `lint`, `config`, `govern`, `engines`, `merge_check` — same engine and same
refusals, with the fields arriving as a schema instead of flag names typed from memory.
`init`, `adopt` and `install` run *before* a project is governed, or on its wiring, and want
the CLI — `declare` above is the one write on a configured tree, which is why it is served
and they are not. The last of them
wires this file, the tools and the guard into a project running the tool from a checkout,
and `install --check` is what holds its copy of this file in step — though you will rarely
type it, because **the gate now asks**: a vendored launcher, hook or skill behind the
roadkeep answering here is `install.stale`, filed at that file, and `install` is what closes
it. A project holding its version on purpose says `[install] pinned = true` and the finding
stops; the check and `engines` still answer, a pin being a decision and not a claim that the
files agree. **`install --vendor` pins the engine itself**, which nothing else here does:
it copies the highest-versioned roadkeep this machine can reach into `.roadkeep/` and the
launcher resolves that ahead of any sibling checkout. By version and never by search order,
so every machine pins the same one; a *working* checkout is skipped unless `ROADKEEP_SRC`
names it, a tree mid-refactor being the thing a pin exists to stop running; `.git` is
excluded so the copy is an artefact and not a second repository; and what landed is asked
its version, a disagreement being a refusal that leaves the tree there to look at. Add
`.roadkeep/` to `.gitignore` — the command says so and does not write it. `install
--committed`
wires a launcher committed to the repository instead of a path into a checkout, which is
what reaches a session that can install no plugin and clone nothing — Claude Code on the
web; its **guard** stands down where the harness has the plugin wired for that project, so
nothing double-fires, and it never blocks a turn — its **server** stands down for nobody, two
servers being two entries the harness reads separately and an exit being the one thing it
reads as a crash. `uninstall` is the way
back out, for a project moving to the plugin: it takes out this project's entries and
nothing else, keeps the CI workflow, and needs no checkout to read, so it still works once
that tree is gone. Every guarantee below holds either way. Three copies of this tool can
be in play at once — the plugin your hook and this file come from, the action CI gates on,
and whatever `roadkeep` you are calling — and they are allowed to differ. **`engines`
reads all three** and answers `agreed`, `behind` or `unpinnable` — the last being one
version and a modified checkout, which is no commit the plugin could match and so is not
agreement; it exits 1 on either of the two that are not. Reach for it when a hook denies a
write the command you just ran would have made, because then the refusal is that copy's
rule and not this one's. **And `engines --invoke` prints, on one line and alone, the
command that reaches the copy wired to this project** — reach for it before composing any
shell call, because the tools here always find the right copy and a shell does not: a
stale one in another plugins root does not fail, it agrees with a rule that has moved. On a
project that declared `[install] enforced`, it no longer only agrees: a write from a copy
`behind` the registered plugin is **refused before the lock**, and the refusal names that
read. Only `behind` and only where that key was declared — a modified checkout is where a
developer lives — and `init`, `install`, `uninstall` and `capture filed` still write, being
how the wiring gets fixed and how a defect in this tool gets filed. The **gate** says so too,
on the same two conditions: `lint` carries a `gate.behind` note beside its verdict, clean
verdicts included, because a copy old enough to have its writes refused is old enough that
its *pass* is that copy's pass. `enforced` is **not** `pinned`: that one is about the
surfaces vendored into a project against the engine answering, and this one about that
engine against the registered plugin — two pairs, so two keys.

`roadkeep <add|status|amend|restate|ship|retire|record|non-goal|section> --help` has the
flags. What they guarantee, so it costs you no thought: the id, the `→ §<id>` pointer, the
status default and every `(deps: … ✅)` annotation are **derived, never typed** — where a
project declares `prefix` as a list it numbers by track, and then `add --prefix <letter>`
says which track while the number stays derived, per family. Where it declares `ref_scheme
= "outline"` the anchor is not derivable at all, so `add --ref <x.y>` is the field that
names it — offered over MCP too, and only there, an `add` without it on such a project
being refused `ref.missing`; there the id in a section's heading is what binds it to its
line, and it too is appended for you. **That refusal carries the whole path where the
block's prose has not started** — the `block add`, the `section add` opening the family,
the retry and the design, in order and with the arguments filled in — so read it once and
run them rather than discovering a stair per call. Where `[refs]` gives a prose file a
**namespace**,
that file's addresses are written `<prefix>:<x.y>` and it answers no bare one — two files
each numbering their own outline from `I` being one flat set of addresses otherwise, which
`anchors` reports as `doubled` and the gate as `section.ambiguous`. The prefix rides on
the pointer alone: the heading keeps the number the file wrote, and `anchors` names the
free address in each namespace. A refusal exits 2 naming the length and the limit and
writes nothing — **every field it looked at, in one message**, so a call whose `why` and
whose body are both over is corrected once rather than twice (a body arriving off a *pipe*
is the one exception: it stays unread until the line passes, since a pipe does not rewind,
which is what `--section-body-file` is for); the shipped marker never reaches the roadmap.
**A line renders a pointer, and the pointer has to resolve**: `add --section "<title>"`
writes the rationale in the same transaction — the prose on stdin or `--section-body`,
both files validated before either is written — and an `add` without it answers with the
`section add` that closes the pointer it just created, rather than leaving the gate to say
so. Under an outline it opens a block's **first** family too: `add --ref XXXI.1 --section`
declares `XXXI` in the same write — titled from the block, those words being already written
one file over — and files the design under it as a child, so a block's first design has the
shape its fifth has instead of *being* the family heading. What it opens is a container, a
heading with no prose, which is the one shape `body.empty` does not name; `section amend
<family> --title` is the correction where the block's words are the wrong ones. Without
`--section` that is still **two** calls, both named and in order, because a `section add` on
a child whose **immediate** parent is missing is refused — a nearer ancestor existing is not
enough, and the refusal names every generation in between. Two missing generations is a
guess either way: the address between them names a subtree whose title nobody has written,
so it stays refused, and a block whose prose has already started is a typo in a numeral
rather than a family to open.
**`ship <id> --why "<what now works>"` makes its three edits** (ledger entry, roadmap
line gone, `§<id>` deleted) plus the dependents' annotations, or none. It **names any
section whose prose cited what it deleted**: the ship is right and that citation is your
next edit, in *this* commit, because a shipped entry keeps no pointer and from the next
command on the reference reads exactly like a typo. Under an outline it names one thing
more: the **parent** left with no subsections, whose prose was written as an introduction
to children that have now all shipped — `section amend <parent> --body -` is that edit,
and what it should say instead is yours. **You read that design and the code may have
moved under it**: `--superseded-design "<what it was wrong about>"` is the trace,
parenthesised into the ledger's own sentence with the anchor, because the deletion
otherwise leaves the one reader who could ever know it was stale — you — with nowhere to
say so; refused on a line that pointed at no design and on a `--part`, whose section
stays. **And the half of it that was right went somewhere**: `--recorded-in <path>` names
the file it moved to — the docstring above the code, the test a criterion became —
appended to the same sentence beside that clause and derived whole, so a decision that
outlives the work explaining it keeps an address; refused at those same two doors and on
a path this repository does not have. Never a section copied into a second file, which is
the accreting rationale this tool exists to refuse. **And the third of its three contents
is the decision**: `--decides "<the constraint>"` files one line into the `decisions` role
— an id, a marker, the task's own claim and your sentence, which is what an ADR is read as
this format. It writes a fourth file, so it lands before the deletion and refuses the whole
transaction where the role is undeclared, naming `declare decisions`; it reaches the
closure door too, that one deleting the section as well, and is refused on a `--part`.
**And that file's one departure is `supersede <id> --by <id>`**: a decision leaves by being
replaced, so both entries stay and the marker says which is live — the forward pointer and
the retired marker in one write, both ids being decisions already filed. No reason field:
why one replaced another is the argument in the entry that replaced it, one line away. A
decision is superseded once, and what replaces the replacement supersedes that one.
And `--why` is **required**, because the roadmap's sentence states a problem and
the ledger's states an outcome, so inheriting it files a defect report under a heading
meaning "done" (`record amend <id> --why` is the repair where one already did). **A path
ledger prose names has to resolve**: `ship`, `retire` and both `record` verbs refuse a
sentence citing a file this repository does not have, because an entry there claims the
work is done — the gate's own `path.missing`, asked before the prose exists rather than
after it lands, and never asked of a roadmap line, which is free to name the artefact its
task exists to write. And `retire <id> [--superseded-by <id>] --reason "…"` is the same
transaction, two more doors
— **open on every project, including one that declares `[ledger] marker = false`**: there
the retirement is the one line in that file to carry a marker, a departure being the one
status a ledger of shipped work does not state about itself. `ship` is not the way round
it either way: an outcome filed under ✅ is a shipment, and `Backlog.retired` reads the
marker. **The `symptom` is not one of `amend`'s fields** — it is the falsifiable claim the
line is, so a different one is a different task — and where the premise itself turned out
false, `restate <id> --symptom "…"` is that correction and the only door to it: the id,
the deps, the marker and the section all stay, because the work never changed and only the
description of it was wrong. Reach for it instead of `retire` plus `add`, which spends an
id and deletes a design that was already right. It takes no reason: the format has nowhere
to put one, so the commit that removes the false claim is where it belongs. **It names the
two other places that claim is written** — the `why` and the section — as `amend` and
`section amend` with the id filled in, to be read and edited in this commit: whether either
still holds is a judgement, so nothing there is rewritten for you. **A misspelt
word is not that case**: `restate <id> --symptom "…" --typo` says the claim is the one
intended and a word in it was wrong, so the answer and the payload record which of the two
acts it was — without it every spelling fix reads as a premise that turned out false,
which is the one thing this verb exists to keep greppable. It is a declaration and never
inferred, and it relaxes no limit: a slip of the pen that lands over the budget is still
over it. **`ship <id>` is also how one that stopped halfway is finished**: the ledger is
written first, so a crash leaves the id in two files (`lint` says `id.two-files`) and
re-running `ship` closes the line without writing a second entry. It refuses instead where
the files say the work is in halves — a ⏳ line or an entry naming one — or where the line
and the entry describe different work, which is two tasks sharing an id and `renumber`'s
to fix. **Half of it landing is a third answer, not a full ship with a hedge in the
sentence**: `ship <id> --part "<which half>"` records the entry as `✅ **<id> (which
half)**` and *leaves the line open* at ⏳ with its section intact, and the later `ship
<id>` completes it. **Pass `--remainder "<what is left>"` beside it**, because the entry
records the half that landed and nothing else records the other one: without it the next
reader recovers the rest by subtracting the ledger from the line, several sessions later,
from prose written for another purpose. It becomes the open line's `why`, so `brief`
prints both halves as fields — the entry says what happened and the line says what is
owed. The symptom is untouched: a task half-delivered is still that symptom's task. The
later `ship <id>` completes it — replacing that entry in place and dropping the qualifier, which is
the only thing that keeps "local half" from outliving the local half. That replacement
states a *different* sentence, so on a ledger written before the tool, where the partial's
bullet **wraps**, it takes `--lines <n>` for the same reason `record amend` does, carries
the same two permissions, and is refused without it; the count is a flag on this verb
rather than a detour through that one because you asked to finish work, and it is refused
on every path that replaces no entry.
A **second** `--part` is refused and says why: one id carries one partial and then the
completion, so work arriving in more halves than that files each delivered step as its own
line, and the refusal spells the id that line takes under this project's `[ids]`. **A
pause is none of those three**: `defer <id> --reason "…"` moves the line to the deferred
store, keeping the id, the deps, the symptom and the section a departure deletes — refused
where `[files]` declares no `deferred` path, and never scaffolding one on the way past, a
store invented at the moment one is needed being a format decided by a verb; `init
--deferred` writes the key and the skeleton together on a project being *created*, and
**`declare deferred` is that same opt-in on one already configured** — the door to reach for
when any verb refuses over an undeclared role, `[files]` being written once by the command
that refuses to run twice, so a role declined at scaffold time was otherwise a hand edit to
configuration this tool owns. It writes the role's file with the block headings the roadmap
already carries and inserts the one key, leaving every other byte of `roadkeep.toml` alone;
refused where the role is declared, and it never repoints one — moving a governed file is not
this write. Any of the six roles, so a project that wants a strategy document or a
`decisions` file reaches it too — that last one being the ADR every adopter asks for, and no
scaffold ever writes it: a project has no decisions on the day it is created. And
`resume <id> [--marker <m>]` is the return direction the ledger has none of — the reason
wraps the `why` on the way out and is unwrapped on the way back, and the open marker is
what the store could not keep, so `--marker` is where you say which it was (`--marker ⏳`
where a `--part` already landed: **a half that shipped does not close the pause door**, and
the gate reads the qualifier off the ledger, so the store and that entry are the two files
agreeing). A dep on a
paused task resolves as **deferred**, and the line waiting on it as `blocked-paused` — not
offered, counted apart, and unblocked by a `resume` rather than a ship. The three writes that
reach a line by id — `amend`, `restate`, `status` — refuse a paused one **naming the store
and `resume`**, so a refusal about a pause never reads like one about a typo. Reach for
`retire`
only when the work is not coming back. `record add --block <x> --symptom "…" --why "…"` is
the fourth — the entry alone, roadmap untouched, for **any** shipped work with no open line
to carry it: never planned is one case, and so is a task that shipped inside another's
sentence and needs an entry of its own. It is
also **the revert**: `--supersedes <id>` writes the entry saying the work did not hold
*and* appends the forward pointer to the entry saying it shipped, in one write — reach for
it there, because `retire` needs a roadmap line the ship already removed and `record drop`
refuses a non-duplicate, so without it the ledger holds two records of one decision that
do not name each other. Both entries stay: the ledger is history and both happened. **Two
reads come before a proposal, not after it.** `non-goal list` says what may not be
proposed at all, and **`delivered <block>`** says what that block already shipped, as
claims — a duplicate is never refused and could not be, since two people describing one
problem use disjoint words and recognising that takes meaning this tool has none of. The
ranking is not the obstacle: measured on this ledger a lexical match ranks the true
duplicate in the top three and still scores below what an entry with **no** duplicate
scores against its own nearest neighbour, so no threshold separates them and the gate is
impossible rather than unreliable. So you read the list, and the alternative is
discovering the collision after a claim, a brief and a retirement. **`delivered <block>
--near "<the symptom you are about to propose>"` is that read bounded by the question**
— the five entries nearest it, ranked by word overlap, instead of a whole block's ledger
(103 lines and 9,773 bytes here). Reach for it by default: measured on the four pairs
this ledger knows the answer to, the true partner is inside those five every time. The
order is the answer and no score is printed, for the reason above. A letter nothing
declares is **refused** rather than answered `nothing`, that answer being read as
evidence, and where the block exists
the reply says which of live, paused, finished or empty it is. **`reversals` is how you
find them before spending an id** — a revert is filed as a delivery, so a check answers "yes,
shipped" about the entry saying the work did not hold; this reads that forward pointer
back with the reversing entry's sentence, which is the argument a fresh proposal is
against. `reversals --id <id>` exits 1 where that decision was reversed. It refuses
nothing: re-proposing reverted work is sometimes right, and which is a judgement no tool
makes. `record drop <id>` is its inverse: refused unless the ledger states that id
**twice** *and the two say the same thing*, then the later entry goes and the first stays,
because removing the only record of a decision is deleting history. Two entries that
differ are two deliveries under one id, not one recorded twice: `record drop <id> --line
<n>` if you have read both, or `record renumber <id> --line <n>` to give one its own
address. To *fix* an entry use `record amend <id> --why "…"` (or `--part` on a partial) —
never drop-and-re-add, which moves the line to the end of its block and shows a reviewer a
deletion where a word changed. On a ledger written before the tool, where a bullet
**wraps**, that correction is refused until `--lines <n>` says how many lines it replaces:
the parse holds only as much of the sentence as fits on the first one, so rewriting that
line alone leaves the tail of the old sentence under the new one. **The count is two
permissions, and this is the one worth knowing**: passed with an `n` above one, `--why`
may be the *whole span* rather than one sentence — its first line is the sentence and
every line after it is written back under the bullet verbatim, so the paragraphs survive
instead of collapsing into one line. It is the same at the `ship` that completes a
wrapped partial, and it is the reason neither needs a `<br>`. The block is not one of
its fields, because filing an entry elsewhere **is** a move: `record move <id> --to-block
<x>` is that one, and it says so — the line is re-placed under the named heading, both
positions are reported, and a heading nothing declares is refused. Reach for it when
`ship` filed an entry under the block its roadmap line was wrongly under. `section add
<id> --title "…"` is that same write for a line that already exists, and takes prose on
**stdin**, within the word budget, filled to the configured width, under the task's block
— or, where the pointer is an outline anchor, under the section that anchor extends, since
there the anchor is what states the place. A one-segment anchor **opens a new top level**,
placed after the last one and at the depth that file writes one at, which is how a block
declared in the line files gets its first design at all; a *nested* one is written one
level under the section it extends, so it stays inside the subtree its anchor names
whatever depth that file nests at, and a nested anchor whose parent is missing is still
refused, that being a typo in an address. A table or list is inserted exactly as written.
**An id-shaped token in a body has to name a line some file carries**: a design explains a
task rather than promising one, so an unclaimed id in this project's own prefix is read as
spent and the next `add` derives past it — spell an example outside the prefix, or name the
id you meant.
**At a terminal, `-` reads stdin on every prose argument** — `--section-body` and
`--body`, and `--why`, `--reason` and `restate --symptom` on every verb that takes one.
Reach for it on the sentence, not only the paragraph: a `why` or a symptom names types,
files and prior ids, so it carries the apostrophe, the backtick and the `§` a shell reads
first, and a shell that eats a backtick does not refuse — it hands over prose subtly
unlike what you wrote and the line lands. The pipe's own trailing newline comes off; a
trailing space is still yours and still refused, and two arguments asking for one pipe is
refused naming both. Over MCP there is no pipe, so every write that reads one takes it
**as a string** and refuses `-` by name. **A pipe does not rewind, and a paragraph is the
expensive argument**: an `add`
refused for a `why` three words over used to cost the whole rationale a second time, so
the body is now fetched *below* every refusal the line itself can raise — and where that
is not enough, because `section add` reports the anchor, the title and the body together,
`--section-body-file` and `--body-file` name the paragraph by **path** and the retry
re-reads it, costing the corrected field alone. Prefer the path over the heredoc for prose
you drafted before filing it; naming both the prose and its path is refused. **`section
amend <id>` is how a live design is corrected**: `--body -` or `--body-file` replaces its
own prose, `--title` its heading, the subtree and the anchor are untouched, and it is the
only door for prose. **Reach for `--replace "<old>" --with "<new>"` on a one-clause
correction** and never re-emit the body for one: it edits the prose already on disk, so a
table, a fence or a block quote the call does not name is prose that cannot be lost retyping
it — refused unless the old string occurs exactly once, which is what keeps the edit's reach
visible in the call. It is also the one form that **inherits** an overrun: a legacy section
already over the word limit for reasons the correction has nothing to do with takes the edit,
where the whole-body form is refused and the way out was shortening prose you never came to
touch. Growing it is still charged. A body-only amend leaves the **heading line's bytes** alone
too — the reader takes a `§` an author wrote under an outline and the writer would not
reproduce it, so re-rendering a heading nobody named silently restyled the file. **The anchor is
`section move <anchor> --to <address>`**, and only under an outline: `renumber` moves an
id and leaves the pointer as typed under every other scheme, so an address had no verb at
all and the one state that needs one is the address two prose files both declare —
`section.ambiguous` to the gate, refused by `add`, and what an adopting corpus arrives
with. It re-addresses the heading, every nested anchor that extends it and the `→
§<anchor>` on every line naming one of them in one transaction, taking the refusals `add`
computes about a destination: held here, declared by the sibling file, or spent by a
heading in history. A pointer follows only where **nothing else answers its address or
this heading names its task**, which is what keeps repairing one file from taking the
other file's line with it, and every one that stays is reported. The destination keeps the
parent the address already had — this write changes the address and not the place — and
under `ref_scheme = "id"` it is refused by name, the anchor being the id and `renumber`
the verb that moves both ends. `section drop` is refused while an open line points at the
anchor **or at any address under it**, named in the refusal, whether this file writes that
address as a heading or as a bullet; that is right, and shipping is not a way to fix a
paragraph. **`block list` is the read before every `add`** — it is what says the labels
exist and what each is called, in file order, with each block's open count and whether it
is live, paused, finished or empty. No other read enumerates them: `list --block` and
`delivered <block>` both demand the letter as an argument, so until this verb the only
answer was reading the roadmap, which the guard denies. A label the roadmap has lost keeps
its ledger heading and is named as such: that row is a block an `add` still refuses, and
the next sentence is its remedy. No write invents a block heading
— **`block add <x> --title "…"` is the one
that declares one**, in every governed file already organised by blocks, placed after the
last block's subtree and spelled at that file's own level and separator. Reach for it the
moment any write refuses with "no heading declares". A file organised by *nothing* is
skipped, because the level, the separator and the placement are all read off a heading it
does not have — so a ledger that is plain prose is a project every `ship` refuses;
`--organise <role>` is you saying that file is to be organised by blocks, and the refusal
names it where that is the state. Block order is what `list` reports and what a reader
takes for the shape of the plan, so `--after <label>` opens one **between** two existing
blocks: it names a neighbour rather than an index, each file placing the heading after its
own copy of that heading, and a file that wants the heading and declares no such neighbour
is refused rather than appended. `block drop <x>` withdraws a label opened by mistake: the
heading goes only from the files where its whole subtree is blank, and anything filed
under it — an open line, a paused one, a rationale section — is named in a refusal that
writes nothing, because a heading over work is not an empty heading. The ledger keeps its
heading either way, history being filed under it — which is why **`block amend <x> --title
"…"` is the door to a heading's *words***: that refusal makes drop-and-re-add impossible the
moment anything is filed, so a title was otherwise write-once. It is narrow — the label is
the identity and does not move, the subtree is untouched, and each file keeps its own level
and separator, nobody having asked for a restyle — and it writes every file that declares the
label or none, a title corrected in one file and left in another being the defect it closes.
**`block merge <x>` is the key to the
doubled heading** — the state a textual git merge, an `adopt` or a hand edit leaves, that
the gate reports `block.repeated` and every write refuses with "merge the two regions by
hand". It keeps the first heading and folds every later duplicate's entries into it, all
files or none; the ledger is included, not skipped, because history stays under a heading
of the same label. A nested section is `section move`'s to place and refused here, and
loose prose is dropped only under `--prose`. Reach for it the moment `lint` reports
`block.repeated` or a write refuses with `RepeatedHeading`. **A sub-heading grouping
entries inside its own block is not that state** and needs no repair: one label is one
*region*, a heading inside another's subtree is already owned by it, and the write appends
after everything the region holds. Two headings neither of which is inside the other are
two addresses, wherever they sit and at whatever level. `non-goal add --lead "…" --why
"…"` writes the one bullet that is not a task line, where `[non_goals]` declares the list
governed: addressed by its lead, which is unique and checked, and carrying no marker, dep
or pointer, because a constraint has no status to state. `non-goal amend <lead> --why "…"`
corrects the reason **where the bullet already sits**, for `record amend`'s reason: `add`
appends, so drop-and-re-add moves a constraint to the end of a list a reader takes for the
plan's shape. The lead is not one of its fields — it is the address — and a bullet
carrying no bold head is refused by name, that shape's repair being the pair below.
`non-goal drop <lead>` is the other half, and what a changed *lead* takes: the lead is the
address, so a constraint whose lead changes is one dropped and one written. **Call
`non-goal list` before an `add`** — the list binds what may be proposed, so reading it
after the line exists is reading it too late; it prints on a project that never opted in,
and nothing checks a proposal against it for you, that being a judgement about meaning and
this tool having no model (L4). **That list's positive twin is `criterion`**, where
`[criteria]` declares it governed: a non-goal says what is not built and this says what must
be **true** for a block to be finished, which nothing else states — a definition of done
written into a rationale section is one `ship` correctly deletes, and then a block closes on
emptiness. `criterion add --block <x> --lead "…" --why "…"` writes one, **opening that
block's `## Done when — Block X` heading** where there is none, as `priority add` does — but
never the block, a label the roadmap does not declare being refused. The address is the
**pair**, so one lead under two blocks is two claims and not a duplicate; `criterion amend
<lead> [--block <x>] --why "…"` and `criterion drop <lead> [--block <x>]` are the other two,
with the address needed only where two lists carry the lead, and the heading survives the last
bullet — a block whose criteria all went is one somebody asked the question about. **And the
other unit is the line**: `--task <id>` addresses the same four verbs to a task, which is what
an agent about to execute one wants — the spec is the symptom, the non-goals, the design and
this, and only this was written one altitude up. The id has to be a line the roadmap still
carries, naming both addresses is refused, and the list **leaves with the line**: a ship or a
retirement takes the whole `## Done when — <id>` region in its own transaction, where a
block's list stays, that one outliving its lines. `brief`
prints both, each carrying its address, so a task started through it never has to ask, and
`criterion list [--block <x>|--task <id>]` is the read across them — it says which empty it
found. **The
roadmap's fourth list is the queue**: `priority add
<token> [--first|--after <t>]` and `priority drop <token>` write the `## Priority`
section, whose entries are bare tokens — an id or `Block X`, no reason field, because why
something jumps the order is the commit that moved it. A heading declares the list, as a
block heading declares a block — and **`priority add` writes that heading** where a project
has none, above the blocks, saying so in its answer, so no queue ever needs a hand edit. The
section **wins over `priority` in
`roadkeep.toml`** where both exist; `priority list` says which one answered. Where the
config still holds it and the roadmap has no section, **both write verbs refuse and name
`priority migrate`**, which moves the order into the roadmap in one call — the gate reads
the config declaration and reports a defect there, so without that door the finding named
a file no verb opens. It leaves the config line alone; `lint` reports the leftover as
`priority.config`, whose repair is deleting one line of a file this tool does not govern.
You never take an entry out by hand: **every door a line leaves by removes it** — `ship`,
`retire` and `defer` each drop it inside their own transaction and say so, a paused line
being one `pick` can never offer either. Only `resume` leaves it to you, where in the
order it sat being the one thing the store could not keep. Every write prints one `event
<id> Block <x> <stage>` line, the whole payload a hook gets — a non-goal excepted, having
neither an id nor a block. The stage is the **same four-state answer** every query gives
(`live`, `finished`, `paused`, `empty`), so a loop branching on the event and one branching
on `--json` read one vocabulary; the `block drop` offer rides on the two of them a heading
is droppable in, and a paused block is never sent at a command that refuses it. **Absent
entirely where `[headings] permanent` says the headings outlive the work filed under them**
— then an emptied block is finished work and not a heading to withdraw, and the stage word
still says which. There is no second route: `Edit` on a governed file is denied, naming the
command, and `lint` gates the turn's end.

**An id is an address, and a merge can spend one twice.** `renumber <id> [--to <new>]`
moves the line, the `§<id>` section its pointer resolves to and every dep naming it, in
one transaction — the destination derived in the line's own family unless you name one,
spelled the way `[ids]` says this project spells one, and refused if any source already
mentions it. A **split** is the other direction and not this command: the cited number
stays where it is, and the half that is new is an `add --id <id>b` where `[ids] suffix`
declares one — the one id a caller may choose, `task_id` over MCP, offered only on such a
project and refused without the letter, because a bare number is derived. The ledger is
never opened, so the id the other branch recorded stays theirs; the deps it moved are
**named in the answer**, because which of two collided ids a dep meant is the one thing
the files do not say. `ship` and `retire` are wrong here: both write a terminal entry for
work nobody cancelled.

That leaves the two rules a schema cannot check:

`amend <id>` corrects an existing line's `why`, `--dep` group or `--ref` — the fields that
are a fact or a compression — and never its `symptom`, which is the claim the line is, or
its `id`, which is what `renumber` is for. That is the door a project adopting the tool
needs; a greenfield one rarely calls it. Which is also where a roadmap line **wraps**:
`add` refuses to write one, so the count `--lines <n>` asks for is a thing only an
imported backlog carries — and `amend` and `restate` both refuse without it there, for the
reason `record amend` does, a rewritten line otherwise leaving the note under it stranded
beneath a sentence that no longer says what it answered.

A **merge conflict inside a governed file** is not a hand edit either. `merge --register`
wires `roadkeep merge` in as git's driver for the files `roadkeep.toml` declares, and it
merges by id: two branches appending under one heading is two additions, not a conflict,
and an id **both branches created** is reported by name for `renumber` to move. What it
cannot prove — prose changed on both sides, a line that does not round-trip, an output
`lint` would refuse — it hands back as git's own conflict markers and exits 1. `install`
names it in its report and `install --register-merge` runs that half during adoption, so a
wired project is never one whose first parallel branch conflicts by hand. Wiring is two
writes — a committed `.gitattributes` line per file, and a per-clone `git config` path
that can stop resolving — so `merge --check` reads both back and exits 1 unless git would
run this driver — the one query on that command, and the one tool on this surface named
for a flag rather than a verb. Neither half is otherwise visible until the merge it was
registered for, so ask once per clone.

1. **`symptom` states what does not work** — never a solution name: a line named after its
   fix cannot be falsified, so it never gets closed, only abandoned. 2. **`why` is one
   sentence.** A second sentence is the signal the content belongs in the rationale file,
   which is what the pointer addresses.

Markers are `[markers]` in `roadkeep.toml`: the open set is the roadmap's, and the shipped
and retired ones are the ledger's alone — neither is legal in a roadmap. Limits are
`[limits]`: `roadkeep lint` names the file, line and column of anything over, and `--fix`
repairs only what is **derived** (annotation, pointer, dep order, marker codepoint,
whitespace, and the queue entry whose task shipped or was retired, named in the report and
never dropped in silence). On a project that arrived with drift, an absolute count answers
nothing: `--baseline <rev>` (`HEAD` after a write) reports **what you added** and forgives
the standing debt by name.

**A finding names the command that closes it, so never infer one.** Every code the gate
can report resolves to a door, and the report prints it under the line: a complete argv
where one exists, with the id and the line number already substituted; the two doors and
what separates them where the choice is yours; and a marked blank where the field is prose
only you can write (a title, a shorter symptom, a reason) — the tool does not compose
those, by law. `repair` spends that in **one call**: it runs the mechanical pass, then
every finding whose remedy is a complete command, re-reading the report between writes
because a repair moves the line numbers after it. What it cannot close it prints, and it
exits 1 while anything is left, so a clean exit means clean. Reach for it the moment
`lint` reports anything — composing the commands yourself is a turn per finding, and
`repair --dry-run` is the read if you want to see them first. `explain <code>` is the
third: what the class is, what produces it and which doors close it, in three lines —
reach for it when a code is one you have not met, and never grep the package for it.

## Ask, don't count

Every query takes `--json`. **`budget` is the pre-`add` read that saves a retry**: what a
line leaves its prose fields, derived from the id, the marker, the deps and the pointer —
all of which are known before the first word exists. It answers in **both units**: the
characters are what refuses, and the word aim beside them is the one a sentence can be
composed towards, so write to the words and let the gate stay unreached. **And it names the
rules that are not widths**, per field — how many sentences the field accepts and whether it
must end in a stop — because a `why` that fits every number and arrives as two sentences is
refused all the same, and a read that published only the figures cost the composition it
exists to save. A character here
is a **UTF-16 code unit**, which every payload declares and every refusal names when it
differs from what an editor shows — the stricter of the two counts, so a line this accepts
is one a gate written in Java, C# or JavaScript accepts too, and a status marker costs two
of them where `✅` costs one. `budget --block
<x> --dep <id> [--symptom "…"]` is the line an `add` is about to write, and `budget <id>`
the one an `amend` is about to rewrite; the field's own `maxLength` is the ceiling, and
what comes back is the lower number that actually binds. Where `ref_scheme = "outline"`
the pointer is structure the caller chooses, so **pass the same `--ref` you will pass
`add`** — unnamed, the answer assumes the widest anchor on file and says so, which is
never more room than the `add` will allow. **It answers for the whole transaction, not the
line alone**: `add --section` writes a body too, so every `budget` carries a `section` row
— the role's word limit, what that anchor already spends, and an aim that sits **under**
the limit, because composing to exactly the declared number is what refuses. `budget
--anchor <a>` asks the same thing on its own, which is the read a `section amend` wants —
**refused where two prose files declare that anchor**, as every other reader of one
refuses, `--role` naming which of them you mean; on a line's own budget that half comes
back empty and states the same reason; `section show <a>` is the other half of that read —
the prose whole, with the count the budget is measured in, so an `amend` is composed
against what is there rather than against a remembered version of it — and it prints the
**subtree**, so on a section with children `section show <a> --own` is the half that write
replaces. Hand the subtree back and it is refused as the wrong extent (`body.subtree`)
naming that flag, rather than counted as prose somebody wrote too long: a subsection is
amended by its own anchor. **If a body is refused anyway, do not count by hand**:
`body.too-long` names what each paragraph costs and which is the longest, so the second
draft is composed once — and a `0` there is a table or a fence, which is prose no cut can
reach. **And do not count by hand before one is refused either**: every prose argument here
is a draft this read *measures* — and **one call prices the whole `add --section`
transaction**, which `add` validates as one unit and refuses as one: `budget --block <x>
--why "<draft>" --body "<draft>"` answers for the line *and* the body it would write, while
`budget --anchor <a> --body "<draft>"` asks about a section on its own (or `--body-file`, or
`-` for stdin). Both answer with the overrun and exit 1 where it is over, which is the
refusal without the write — and a `why` three characters over re-sending the whole rationale
beside it is what pricing the two halves separately cost. Nothing is composed, so a draft
twice its limit is a number rather than an error, and the schema published for these fields
carries **no `maxLength`** on purpose: a ceiling there would refuse the very draft you are
asking about. Reach for it after the first refusal and instead of the second — the retry
after a refusal is a guess, and this is the same arithmetic answered before the write.
`budget --non-goal [--lead "…"]` is the roadmap's other bullet, whose two
limits are the list's own and not the task line's. **`budget --file <p>` is the fourth
subject**, and the one that is not about prose at all: what an every-turn file `[budgets]`
declares costs in lines and bytes and what is left — bare, every declared budget. Read it
*before* editing one, never `wc` and a subtraction; `lint` is still what refuses.
**`budget --tools` is the fifth**, and the only one about this surface rather than about
prose: what the tool list costs a session that connects the server, in characters and by
tool, largest first; **named — `budget --tools ship` — what each of that one tool's fields
spent**, and the module its `help=` strings are written in, which is the question you have
the moment `lint` says a tool is over and the ranking cannot answer. Where the description
is the largest row, it is split by where each clause is written: a tool that always passes a
flag carries that flag's own `help`, edited somewhere else. Nothing refuses it —
the number is stated so that adding a tool or a
sentence to a description stops looking free, which is the argument `[budgets]` makes about
a file that loads every turn. **`budget --brief [<id>]` is the sixth**, and the only one about
a *read*: what the answer that replaces reading the file costs a tool result, per open line and
widest first, against `[reads] brief`. Declared, that ceiling is the gate's — `read.over` names
the task whose brief does not fit. **And `config` is the read about `roadkeep.toml` itself**:
every table, key, TOML type and default this build accepts, with the sentence its
source already carries and whether *this* project declared it — `--table <name>` for one,
`--table ""` for the top level. Reach for it before writing a key rather than after the
refusal, and read the build it names: a key nothing declares is a typo, a key this copy
predates is an upgrade, and the file cannot tell them apart. **`govern <address> [<n>]` is
the write beside it**, and the only one on that file besides `declare`: the four tables whose
value is a judgement about a number — `[limits]`, `[budgets]`, `[tools]`, `[claims]` — each
already had the read that decides it somewhere else, so this takes the reading and writes the
number in one call. With no number it prints the reading alone; `--role` and `--file` name the
table a project declares per role or per path. A limit this corpus already breaks is
**refused**, not written, because one whose first act is a finding is one somebody lowers,
reads the report and raises again. **`--because "…"` is where why this number and not the
next goes**: your sentence, wrapped into comments above the key and stacked on whatever
argued it before, the same one twice being written once. The verb places the argument and
never writes it (L4); the read hands back what stands above the key, so why a number is
what it is costs a command and not a file to open. Every
verb that prints a section's size states **two** figures where they differ — `48 words,
310 with subsections (limit 300)` — because the argument is what an `amend` can shorten
and the subtree is what a reader pays; cutting to the second number cuts prose that was
never over. Under an outline, `anchors` names both free addresses before you choose one:
`next §<family>.<n>` for a child and, above the rows, the next free **top-level** — which
is what a block reused after its family shipped needs, and what the listing could not be
read for. **`--next` is that answer alone**, without the listing of spent ones: it is the
read an `add --ref` makes every time, and under a 27-anchor family the address you want is
otherwise the 28th row — the first thing a tool result truncates. Reach for the wide read
only to see where an address came from. It reads **every** prose file the project
declares, and the free top-level is **per namespace**: where `[refs]` gives a file one,
that file's own numbering is the answer and the sibling's is not, and where a project
declares none its files share one namespace, so a free address taken from either is one
the other already spent; `--role` narrows the listing and never that number, and any
address two files both declare is named as `doubled` before you pick one. `brief` prints
the `why`'s share of the line it hands over, so a task started through it never has to
ask — **and the whole of what the ship will compose**: the ledger sentence's allowance, what
each of the two clauses appended to it costs before your words, and the decisions role's own
limit on the line `--decides` files, which is not that sentence at all. **`weight [--block
<x>]` is the other pre-`add` read**: what comparable tasks cost,
derived from the commits that shipped them, so whether the line being written is one task
or two is a question with an answer. An entry whose commit wrote several is named under
`batched` and left out of the percentiles, so a squashed adoption import skews nothing.
What comes back is the distribution and what was elided from it, the sample those
percentiles summarise being `--records` and 95% of the payload. It ranks nothing and lands
on no line — the size field is a non-goal. **`remaining <id>` is that read's mirror**: what
a task has *left*, run from a query its own design declares — a fenced `roadkeep-remaining`
block in the rationale section, one `<pathspec> :: <regex>` per line. **`evidence <id>` is
that same read with the sign flipped**: a `roadkeep-evidence` block names the sites that
must *exist* for the task to be done, so what `remaining` counts down `evidence` counts
up — and neither is a verdict, the pattern being your claim and the count the answer.
Derived for the same
reason and stored for none: the first commit that closes a site changes the answer, which a
number on the line could not, and a `ship` deletes the query with the section that made the
claim. Reach for it before continuing a migration, which otherwise reads in every file
exactly like a run of unrelated defects. It is a count and never a verdict — the pattern is
the author's, so `0` says the pattern stopped matching and whether that is the work being
done is yours; a glob that names no file is reported apart from a pattern that matches none,
those being opposite facts that both count zero. A design declaring none is *answered* and
not refused, and the gate reports only a block it cannot read (`remaining.format`), never
sites left, work not being a defect in a file. **`roadkeep brief [<id>]` starts a task in one
call** — the line, its rationale, deps resolved, the blocker chain, what it unblocks and
the non-goals, bounded to a tool result; with no id, `pick`'s own choice. Narrower:
`next-id` never fills a gap, and where the number below the one it derives is named only
in prose — a "filed as <id>" a ledger entry promised before the task existed — both it and
`add` say so, because from the write on nothing records which of the two was a line;
`list|stats|audit [--block <x>]` counts and lists, naming
every marker line neither could read; `claims` is the registry read against the files —
held, expired or stale, oldest first, where each id went and where the registry lives, and
`--prune` drops the rows that are not claims; `writes` is the same read for the other
sidecar — which governed files a verb wrote and which nothing did, moving no baseline
where the `Stop` hook states it once and consumes it; `show <id>` joins one line, its
section and its paths, and on a ledger entry whose bullet **wraps** it prints every line
that entry owns — which is the count `record amend --lines` asks you to have read; `deps
<id>` walks the graph both ways; `gaps` resolves an id in neither file against the commit
that removed it, and `unclosed` is that question pointed the other way — an **open**
line whose work a commit already names, which is what a session that shipped the code
and forgot the line leaves behind; `origin <id> --why` reads it out of history, and `origin §<anchor>`
answers the other end of a pointer — a rationale address somebody's prose still cites
after a ship deleted the section, which no file records, so the three answers are the
commit that wrote it, the one that took it, and "searched and nobody ever wrote it", which
is what a typo looks like. `anchors [--family <x>]` is that question about the
**addresses**: which a heading declares now, which a ship retired while every entry citing
them stayed, and the next child nothing ever used — the read to make before reopening a
shipped family, since an outline anchor is spent once a heading used it and `section add`
refuses the reuse by name. **You know the block, not the numeral**: a prose file under an
outline declares no block heading, so `anchors --block <x>` is the way in — it names the
family that block's pointers already use and narrows to it, or names both where the block
spans two and leaves the choice with you. And **never restate a count in prose**: `export
[--readme|--site|--json]` projects it, and `export --contents` does the same for a prose
file's own table of contents — every row a heading that file already carries, so a `ship`
or a `section drop` leaves the list wrong until it runs. All three go stale the same way
and `lint` says which: `export.stale` names the flag that rewrites it.

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
stderr). **Ready is not implementable**: the tiers rank by id, so add `--designed` when
you asked to *execute* and not to plan — it sets aside the markers `[markers] undesigned`
names, and says how many. Without it the answer still tells you, in the same sentence that
names the tier, that the line it chose has its design to write — which is a `section add`,
not a commit. **Two workers in one checkout need `--claim`**, on `brief` as well as on
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
`--add-path <p>` is the same write from the other end, for the file the work turned up
after the scope was declared; passing both is refused. Over MCP this verb is the tool
`scope` — not `claim`, which is `brief --claim` and takes a line; the two words are two
acts. `--porcelain` prints the paths alone, for `git add --`. Refused on a line no live
claim holds: taking a line is a marker, and nothing here dates one. **`ship` and `retire`
make that read themselves**, while the claim is still live: what the tree holds that no
claim names is named in the departure's own answer, so the analysis arrives at the moment
of committing rather than being remembered there — **and so is the `git add --` line for
the scope being released**, which after the ship no verb can answer: the claim is gone and
the id is in the ledger. Silent where no claim declared a path.
