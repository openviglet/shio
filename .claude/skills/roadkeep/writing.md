---
saves: add
because: writes the line and its rationale in one transaction, under the same limits
---

# roadkeep — the write path

The reference half of the `roadkeep` skill, read when a turn is about to write. `SKILL.md`
beside this file is the orientation and states the two rules a schema cannot check; this
page states what every write refuses and why.

## Writing and shipping

When this session's roadkeep tools are available, **prefer them** — named
`mcp__roadkeep__*` where a project's own `.mcp.json` declares the server and
`mcp__plugin_<plugin>_roadkeep__*` where a plugin provides it, so read the prefix off the
tool list rather than typing it: the whole write path and the reads a task needs are there
— `add`, `block_add`, `block_drop`, `block_merge`, `declare`, `claim`, `scope`, `status`, `amend`,
`restate`, `ship`, `retire`, `supersede`, `revise`, `defer`, `resume`, `dismiss`, `reopen`,
`record_add`, `record_amend`,
`record_move`, `record_drop`, `record_renumber`, `non_goal_add`, `non_goal_amend`,
`non_goal_drop`, `criterion_add`, `criterion_amend`, `criterion_drop`, `criterion_list`,
`section_add`, `section_amend`, `section_move`, `section_drop`, `budget`,
`brief`, `pick`, `list`, `deps`, `lint`, `config`, `govern`, `engines`, `merge_check` — same engine and same
refusals, with the fields arriving as a schema instead of flag names typed from memory.
**The CLI takes these spellings too**: `roadkeep next_id` and `--replacement` run as `next-id`
and `--with`, with a note on stderr naming the CLI's own — so a name remembered from either
surface works, and a verb this CLI has is never respelled into another act.
`init`, `adopt` and `install` run *before* a project is governed, or on its wiring, and want
the CLI — `declare` above is the one write on a configured tree, which is why it is served
and they are not. **It takes two vocabularies**: a *role* writes that file and the `[files]`
key governing it, and one of the two *opt-in tables* — `criteria`, `non_goals` — opens the
list a verb refuses over, empty, which is what opting in means; `govern` is what tunes the
numbers in it afterwards. Reach for it when a write refuses over an undeclared role or
table, which is the refusal that now names this command rather than a hand edit. **And it is
what the tool list is composed from**: a verb that is one role's whole grammar — `defer` and
`resume` for the store, `supersede` and `revise` for the decisions file, `dismiss` and
`reopen` for the dismissed one — is published only where that role
is declared, so a vocabulary grows for the projects that opted into it and costs the rest
nothing. Absent is not missing: the name still resolves and the call is refused naming the
`declare` that opens it, and the list changes under a session that runs one. The last of them
wires this file, the tools and the guard into a project running the tool from a checkout,
and `install --check` is what holds its copy of this file in step — though you will rarely
type it, because **the gate now asks**: a vendored launcher, hook or skill behind the
roadkeep answering here is `install.stale`, filed at that file, and `install` is what closes
it. A project holding its version on purpose says `[install] pinned = true` and the finding
stops; the check and `engines` still answer, a pin being a decision and not a claim that the
files agree. **And the write knows which side is newer**: `install` records `[install]
wired = "<version>"`, so a project whose surfaces came from a *later* engine is refused
rather than downgraded — the gate says nothing there and `install --check` names the
direction, `--vendor` being what moves the engine forward instead.
**`install --vendor` pins the engine itself**, which nothing else here does:
it copies the highest-versioned roadkeep this machine can reach into `.roadkeep/` and the
launcher resolves that ahead of any sibling checkout. By version and never by search order,
so every machine pins the same one; a *working* checkout is skipped unless `ROADKEEP_SRC`
names it, a tree mid-refactor being the thing a pin exists to stop running; `.git` is
excluded so the copy is an artefact and not a second repository; and what landed is asked
its version, a disagreement being a refusal that leaves the tree there to look at. The pin
lands **first** and the surfaces are then written from it, so one run is enough and what it
leaves is a tree its own `--check` passes; every later `install` keeps that engine unless
you name a `--source`. Add
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
that tree is gone. Every guarantee below holds either way. Four copies of this tool can
be in play at once — the plugin your hook and this file come from, the action CI gates on,
one vendored under `.roadkeep/`, and whatever `roadkeep` you are calling — and they are
allowed to differ. **`engines` reads them all** and answers `agreed`, `behind` or
`unpinnable` — the last being one version and a modified checkout, which is no commit the
plugin could match and so is not agreement; it exits 1 on either of the two that are not,
and on a vendored copy at another version, which the launcher runs while your shell reaches
something else. It exits 1 too where the directory the running copy was imported from now
states a **different** version: `install --vendor` replaces that tree in place, and a
server holding the old modules answers for a path nothing on disk matches — restart the
session. **A fifth is read and never judged**: the merge driver git would run, which is a
command and not a version, so it is a row and not a verdict — the copy that runs when
nobody is watching, and `merge --check` is where whether git can run it is answered.
Reach for it when a hook denies a
write the command you just ran would have made, because then the refusal is that copy's
rule and not this one's. **And `engines --invoke` prints, on one line and alone, the
command that reaches the copy wired to this project** — reach for it before composing any
shell call, because the tools here always find the right copy and a shell does not: a
stale one in another plugins root does not fail, it agrees with a rule that has moved. It is
**read off this project's own `.mcp.json`** and not derived, so a `$ROADKEEP_HOME` pin, a
vendored copy and a committed bridge all answer correctly — whatever the harness runs is
what it prints, up to the program and never the `mcp` after it. On a
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
says which track while the number stays derived, per family — `next-id --prefix <letter>`
being the read that says what it will be. Where it declares `ref_scheme
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
which is what `add --section "<t>" --section-body-file <path>` is for); the shipped marker
never reaches the roadmap.
**A line renders a pointer, and the pointer has to resolve**: `add --section "<title>"`
writes the rationale in the same transaction — the prose on stdin or
`add --section "<title>" --section-body "<the reasoning>"`,
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
moved under it**: `ship <id> --why "…" --superseded-design "<what it was wrong about>"` is the trace,
parenthesised into the ledger's own sentence with the anchor, because the deletion
otherwise leaves the one reader who could ever know it was stale — you — with nowhere to
say so; refused on a line that pointed at no design and on a `--part`, whose section
stays. **And the half of it that was right went somewhere**:
`ship <id> --why "…" --recorded-in <path>` names
the file it moved to — the docstring above the code, the test a criterion became —
appended to the same sentence beside that clause and derived whole, so a decision that
outlives the work explaining it keeps an address; refused at those same two doors and on
a path this repository does not have. Never a section copied into a second file, which is
the accreting rationale this tool exists to refuse. **And the third of its three contents
is the decision**: `ship <id> --why "…" --decides "<the constraint>"` files one line into the `decisions` role
— an id, a marker, the task's own claim and your sentence, which is what an ADR is read as
this format. It writes a fourth file, so it lands before the deletion and refuses the whole
transaction where the role is undeclared, naming `declare decisions`; it reaches the
closure door too, that one deleting the section as well, and is refused on a `--part`.
**A decision keeps a body, and the ship names where it goes**: that line is one sentence, and
what was *weighed* — the alternatives rejected, the consequences accepted — is a paragraph
`section add <id> --role decisions --title "…"` writes into that same file, under
`[limits.decisions] section`, its own word limit. **Where the anchor is an address, the ship
names it**: `--decides-ref <anchor>` beside `--decides` on a project setting `ref_scheme =
"outline"`, required there for `add --ref`'s own reason — nothing derives it, and a record
filed without one has a body no later call can bind — and refused where the anchor is the id.
`anchors --role decisions --next` is where a free one comes from, per namespace, so a project
giving that file its own `[refs]` gets its own numbering and one declaring none shares the
improvements file's; the refusal names both that read and the flag. Never the design moved: that one
says how the work was built and is correctly deleted by this very ship, and this one says
what it cost, which is the half no other store keeps. Nothing deletes it — `section drop`
included, while the entry stands — so the file grows only by decisions somebody made.
**And that file's one departure is `supersede <id> --by <id>`**: a decision leaves by being
replaced, so both entries stay and the marker says which is live — the forward pointer and
the retired marker in one write, both ids being decisions already filed. No reason field:
why one replaced another is the argument in the entry that replaced it, one line away. A
decision is superseded once, and what replaces the replacement supersedes that one.
**A typo in one is `revise <id> --decides "…"`**, not a second decision: the entry keeps its
line, its id and its marker, and a `(superseded by <id>)` clause is carried through rather
than retyped. **The claim beside it is respellable and never rewordable** — `revise --symptom`
and `record amend --symptom` take a correction only where it folds to the one on record, so
bytes that never arrived are fixable and a claim that turned out wrong is still `retire`.
And `--why` is **required**, because the roadmap's sentence states a problem and
the ledger's states an outcome, so inheriting it files a defect report under a heading
meaning "done" (`record amend <id> --why` is the repair where one already did). That repair
asks `--lines <n>` only where the entry's continuation is prose **nobody parsed**: an entry
wrapped by `--checked` alone is corrected in one call, the carried lines staying where the
ship wrote them, because re-supplying a derivation is not what the count is for. **A path
ledger prose names has to resolve**: `ship`, `retire` and both `record` verbs refuse a
sentence citing a file this repository does not have, because an entry there claims the
work is done — the gate's own `path.missing`, asked before the prose exists rather than
after it lands, and never asked of a roadmap line, which is free to name the artefact its
task exists to write. And `retire <id> [--superseded-by <id>] --reason "…"` is the same
transaction, two more doors
— **open on every project, including one that declares `[ledger] marker = false`**: there
the retirement is the one line in that file to carry a marker, a departure being the one
status a ledger of shipped work does not state about itself. **And a line that turned out to
be part of another is neither**: `retire <id> --folds-into <other>` is the third door, where
this symptom becomes a criterion on the open line that absorbs it and the line leaves, in one
write — refused where that id has already left, an absorbed line needing somewhere to go.
`ship` is not the way round
it either way: an outcome filed under ✅ is a shipment, and `Backlog.retired` reads the
marker. **`amend <id> --add-dep <d>` and `amend <id> --drop-dep <d>` are the narrow doors on
the group**, and
what to reach for whenever one dep changes: `amend <id> --dep <d>` given at all replaces the
whole group,
so a seventh dep means naming all seven, six of them to say nothing changed. Drop by the
spelling the file shows or the bare id — the `✅` is derived and is not yours to reproduce
— and one of the two forms per call, never both. A dep renders *into* the line and the
line's ceiling is shared with the `why`, so an addition that does not fit is refused
**naming the dep** rather than the sentence that did not move.
**The `symptom` is not one of `amend`'s fields** — it is the falsifiable claim the
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
<id>` completes it. **Pass `ship <id> --part "<which half>" --remainder "<what is left>"`**, because the entry
records the half that landed and nothing else records the other one: without it the next
reader recovers the rest by subtracting the ledger from the line, several sessions later,
from prose written for another purpose. It becomes the open line's `why`, so `brief`
prints both halves as fields — the entry says what happened and the line says what is
owed. The symptom is untouched: a task half-delivered is still that symptom's task. The
later `ship <id>` completes it — replacing that entry in place and dropping the qualifier, which is
the only thing that keeps "local half" from outliving the local half. That replacement
states a *different* sentence, so on a ledger written before the tool, where the partial's
bullet **wraps**, it takes `ship <id> --lines <n>` for the same reason `record amend` does, carries
the same two permissions, and is refused without it; the count is a flag on this verb
rather than a detour through that one because you asked to finish work, and it is refused
on every path that replaces no entry.
A **second** `--part` is refused and says why: one id carries one partial and then the
completion, so work arriving in more halves than that files each delivered step as its own
line, and the refusal spells the id that line takes under this project's `[ids]`. **A
pause is none of those three**: `defer <id> --reason "…"` moves the line to the deferred
store, keeping the id, the deps, the symptom and the section a departure deletes. The reason
is *wrapped* around the design carried forward, so what bounds it is the **line** and not the
`why` limit — `budget <id> --defer` is that number before you compose one, and a reason past
it is refused. Refused too
where `[files]` declares no `deferred` path, and never scaffolding one on the way past, a
store invented at the moment one is needed being a format decided by a verb; `init
--deferred` writes the key and the skeleton together on a project being *created*, and
**`declare deferred` is that same opt-in on one already configured** — the door to reach for
when any verb refuses over an undeclared role, `[files]` being written once by the command
that refuses to run twice, so a role declined at scaffold time was otherwise a hand edit to
configuration this tool owns. It writes the role's file with the block headings the roadmap
already carries and inserts the one key, leaving every other byte of `roadkeep.toml` alone —
`declare deferred --path docs/PAUSED.md` where that file belongs somewhere other than the
role's own default;
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
and `resume`**, so a refusal about a pause never reads like one about a typo. **A finding
nobody filed is a fifth thing**: `dismiss --block <x> --symptom "…" --why "…" --premise "…"`
records what was traced and deliberately not filed, minting an id the way `add` does and
filing it in the dismissed store. The **premise** is required and is the whole point — the
claim a later commit breaks — because *checked, fine* is unfalsifiable and gets traced
again; it is *wrapped* around the reason, so what bounds that sentence is the line and not
the `why` limit, and an entry that reaches the file without one is `premise.missing`.
`declare dismissed` opens the store and no scaffold ever writes it, a project having ruled
nothing out on the day it is created. `reopen <id> [--marker <m>]` files the entry as work
when the premise breaks, reporting the premise once and leaving it behind — the line arrives
carrying the subject and the reason. **Its design has to be written in the same call**:
`reopen <id> --section "<title>" --section-body-file <path>` writes both halves, because a
dismissal carries no design by definition, so a reopen without one leaves a pointer the gate
reports as `ref.unresolved` and the write says which anchor owes it. `show <id>` on a
dismissed id says where it is and names both doors, rather than answering *never written*
about an entry sitting in the file. Reach for
`retire`
only when the work is not coming back. `record add --block <x> --symptom "…" --why "…"` is
the fourth — the entry alone, roadmap untouched, for **any** shipped work with no open line
to carry it: never planned is one case, and so is a task that shipped inside another's
sentence and needs an entry of its own. It is
also **the revert**: `record add --block <x> --symptom "…" --why "…" --supersedes <id>`
writes the entry saying the work did not hold
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
(103 lines and 9,773 bytes here). Reach for it by default: measured on the eleven pairs
this ledger knows the answer to, the true partner is inside those five for nine, worst
rank three — the entries are ranked on **both** their prose fields, the `why` being where
two authors of one defect agree. The order is the answer and no score is printed. **And the `add` volunteers a
wider read whether or not you made this one**: every insertion names the three lines of its
block nearest the symptom it just filed — its deliveries **and** its open lines, which is
where the other session filing your defect this morning is. Half the insertions measured here
and on a second backlog show a row a bare `--near` cannot reach at any width, that flag's
subject being the ledger — so **`delivered <block> --near "<the symptom>" --open` widens this
read to that same corpus**, at its own five rather than the write's three, and is how you ask
again for the order an `add` showed you once. It needs `--near`: unranked, the two halves are
the two listings the row already names. Each row says which half it came from. Same order,
same silence about
scores, and never a refusal — a read volunteered, not the gate the measurement rules out.
Three of however many the block holds, and the row says which of how many and names a
command per half, so the volunteered rows are bounded out loud rather than read as the whole. A letter nothing
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
<n>` if you have read both, or `record renumber <id> --line <n> --to <new>` to give one its
own address — the new id derived, one past the highest in its family, where you pass none.
To *fix* an entry use `record amend <id> --why "…"` (or `record amend <id> --part "<which
half>"` on a partial) —
never drop-and-re-add, which moves the line to the end of its block and shows a reviewer a
deletion where a word changed. On a ledger written before the tool, where a bullet
**wraps**, that correction is refused until `record amend <id> --lines <n>` says how many
lines it replaces:
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
**At a terminal, `-` reads stdin on every prose argument** — `add --section-body -` and
`section add <a> --title "…" --body -`, and `--why`, `--reason` and `restate --symptom` on
every verb that takes one.
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
`add --section-body-file <path>` and `section add <a> --title "…" --body-file <path>` name
the paragraph by **path** and the retry
re-reads it, costing the corrected field alone. Prefer the path over the heredoc for prose
you drafted before filing it; naming both the prose and its path is refused. **`section
amend <id>` is how a live design is corrected**: `section amend <id> --body -` or
`section amend <id> --body-file <path>` replaces its
own prose, `--title` its heading, the subtree and the anchor are untouched, and it is the
only door for prose. **Reach for `section amend <id> --replace "<old>" --with "<new>"` on a
one-clause correction** and never re-emit the body for one: it edits the prose already on disk, so a
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
`block add <x> --title "…" --organise <role>` is you saying that file is to be organised by
blocks, and the refusal
names it where that is the state. Block order is what `list` reports and what a reader
takes for the shape of the plan, so `block add <x> --title "…" --after <label>` opens one
**between** two existing
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
loose prose is dropped only under `block merge <x> --prose`, and `block drop <x> --prose`
takes an emptied heading's note the same way — loose prose only, never work. Reach for it
the moment `lint` reports
`block.repeated` or a write refuses with `RepeatedHeading`. **A sub-heading grouping
entries inside its own block is not that state** and needs no repair: one label is one
*region*, a heading inside another's subtree is already owned by it, and the write appends
after everything the region holds. Two headings neither of which is inside the other are
two addresses, wherever they sit and at whatever level. `non-goal add --lead "…" --why
"…"` writes the one bullet that is not a task line, where `[non_goals]` declares the list
governed: addressed by its lead, which is unique and checked, and carrying no marker, dep
or pointer, because a constraint has no status to state. It opens the list where the
roadmap carries no heading, as `criterion add` opens its block's, and says so. `non-goal
amend <lead> --why "…"` corrects the reason **where the bullet already sits**, for `record
amend`'s reason: `add` appends, so drop-and-re-add moves a constraint to the end of a list
a reader takes for the plan's shape. The lead is not one of its fields — it is the
address — and a bullet carrying no bold head is refused by name, that shape's repair being
the pair below.
`non-goal drop <lead>` is the other half, and what a changed *lead* takes: the lead is the
address, so a constraint whose lead changes is one dropped and one written. **Call
`non-goal list` before an `add`** — the list binds what may be proposed, so reading it
after the line exists is reading it too late; it prints on a project that never opted in,
and nothing checks a proposal against it for you, that being a judgement about meaning and
this tool having no model (L4). A `quoted` row under a constraint names the open lines
whose design cites its lead — what the `non-goal.reaches` note falls silent for, read from
the rule's side; whether that is an answer or a citation of one is yours to read. **That
list's positive twin is `criterion`**, where `[criteria]` declares it governed: a non-goal
says what is not built and this says what must
be **true** for a block to be finished, which nothing else states — a definition of done
written into a rationale section is one `ship` correctly deletes, and then a block closes on
emptiness. `criterion add --block <x> --lead "…" --why "…"` writes one, **opening that
block's `## Done when — Block X` heading** where there is none, as `priority add` does — but
never the block, a label the roadmap does not declare being refused. The address is the
**pair**, so one lead under two blocks is two claims and not a duplicate; `criterion amend
<lead> [--block <x>] --why "…"` and `criterion drop <lead> [--block <x>]` are the other two,
with the address needed only where two lists carry the lead, and the heading survives the last
bullet — a block whose criteria all went is one somebody asked the question about. **And the
other unit is the line**: `criterion add --task <id> --lead "…" --why "…"`, `criterion amend
--task <id> <lead> --why "…"` and `criterion drop --task <id> <lead>` address the same four
verbs to a task, which is what
an agent about to execute one wants — the spec is the symptom, the non-goals, the design and
this, and only this was written one altitude up. The id has to be a line the roadmap still
carries, naming both addresses is refused, and the list **leaves with the line**: a ship or a
retirement takes the whole `## Done when — <id>` region in its own transaction, where a
block's list stays, that one outliving its lines. **Say which of them you checked**:
`ship --checked "<lead>"`, repeatable, moves that criterion's own sentence under the ledger
entry, so a claim somebody verified is told from one nobody looked at — a lead the list does
not carry is refused, and a partial keeps its list and so takes none. `brief`
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

`amend <id> --why "…" --dep <d> --ref <x.y>` corrects an existing line's `why`, dep group or
pointer — the fields that
are a fact or a compression — and never its `symptom`, which is the claim the line is, or
its `id`, which is what `renumber` is for. That is the door a project adopting the tool
needs; a greenfield one rarely calls it. Which is also where a roadmap line **wraps**:
`add` refuses to write one, so the count `restate <id> --symptom "…" --lines <n>` asks for
is a thing only an
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
