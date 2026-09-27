# Build and deploy

> Split out of the repo-root `agents.md` (see its routing table for the index).
> Read this before touching the area it covers. Detail here is authoritative.

## A red suite is a stop (SH579)

**A suite that is red either blocks the next commit or is a declared, dated exception in
[`red-suites.json`](../../red-suites.json). There is no third state, and "I did not know" was
the third state for nineteen commits.**

Four suites are gates, and each one is a command a person types:

| Key | Command | What it needs |
|---|---|---|
| `java` | `./mvnw -pl shio-app -am clean test -Dskip.npm=true` | nothing |
| `js` | `pnpm test` | `pnpm install` at the root |
| `conformance` | `pnpm conformance` | a **running instance** built from HEAD |
| `docs` | `pnpm conformance:docs` | a checkout of `vigletdocs`, via `SHIO_DOCS_DIR` |

The first three are the ones `agents.md` calls the gates, because they are what validates a change.
`docs` is the fourth **key** — its own job in `suites.yml`, its own trigger, its own red — and it
joined this table in SH798, which is when it started leaving a stamp. Until then it was the one gate
you could run without leaving evidence you had, and it carried twelve commits of debt while the
comparators reported the tree honestly and had nothing to say about it (SH797).

### What this is for

Measured at `6bc4ba8`, clean tree: conformance failed one of five and `pnpm -C cli test` failed
one of 1148. **Eleven commits landed over the first and eight over the second, and no commit
message mentions either.** Neither red stopped anything, and nothing anywhere said it should.

The instructive part is that one of those two alarms was *wrong*. The conformance failure was a
real finding and became SH577. The other was `assertion-debt.test.mjs` unable to see
`shio-site/scripts`, so it reported two tasks as unasserted that had shipped two assertions each
— a reader defect, fixed in three lines, and it sat there for eight commits because a red nobody
is obliged to answer is a red nobody reads. **An undeclared red teaches people that red means
nothing, and it teaches that whether or not the red is true.** That is the cost, and it is paid
by every *later* alarm, not by the one being ignored.

### It happened again, and the rule was not the part that failed (SH755)

`gh run list` on 2026-08-14: **eight consecutive `Suites` runs, every one a failure**, the oldest
on 2026-08-13, with `red-suites.json` empty the whole time. At `98f554d4` the jobs read `mvnw clean
test` **success**, `pnpm test` **failure**, `pnpm conformance` **failure**.

Nobody disagreed with the rule above. What happened is that the only gate anybody typed was the
Java one — it needs nothing, it is fast, and **it was green** — and a green gate is the most
persuasive argument available that the other two can wait. The rule held exactly where somebody
chose to look.

So the ledger's claim is now compared against what the gates actually said:

- **Every gate leaves a stamp, from one table.** `scripts/gates.mjs` holds a row per gate — command,
  args, log, label, and the prose saying why that gate is wrapped — and `scripts/run-gate.mjs <key>`
  runs any of them, keeping the output and stamping the outcome (SH802; it replaced four wrappers
  that differed in three constants, one of which had been announcing every gate as `JS suites` since
  the second was written). They write `.gates/<key>.json`: exit code, commit, time. Untracked (`/.gates/`),
  because a stamp is evidence about a command run *here*, and a committed one would let a clone
  inherit a claim about a run it never made. `pnpm conformance:raw` and a bare `./mvnw` are the
  unwrapped commands if you need them, and they leave no stamp. All four are deliberately the same
  shape: wrappers that differed would be behaviours to remember.
- **One run of a gate at a time (SH803).** The runner takes `.gates/<key>.lock` and refuses a second
  run of the same gate, naming the pid and since when — because whoever meets it usually forgot they
  had backgrounded one. Measured, by accident: an overlapping pair of `pnpm java` reported **3 errors
  over 1092 tests** where the tree alone reports **1894 green**, and `grep` answered `Binary file
  shio-test.log matches` because two writers interleaved into one fixed-name log. Since SH800 that is
  *recorded* — the loser stamped `red: true` for a failure that never happened, and the comparators
  then demand a `red-suites.json` entry against an open task. **A false red is worse than an absent
  stamp**: absence says nothing, this manufactures a declaration for a defect nobody can find.
  Per gate, not global — `java` and `js` share neither a log nor a build directory, and running them
  together is what people do to save time. A holder that has died is cleared and said so, since a
  gate refusing to work because of a crash three days ago is worse than the collision it guards.
- **Each comparator skips its own gate, and only its own (SH800).** `ShDeclaredRedTest` runs inside
  `java` and `red-suites.test.mjs` inside `js`, and a comparator reading its own gate's stamp reads
  the *previous* run's — which fails one red run's successor for a failure already fixed, then
  passes on the third, teaching people to re-run twice and believe neither answer. So each excludes
  itself and judges the other three, and between them every gate is judged exactly once. That
  arrangement only became possible when `java` started leaving a stamp; before SH800 it was the one
  gate outside the comparison entirely, which is the wrong one to lose — SH755's own finding is that
  eight red CI runs went unread because *the only gate anybody typed was the Java one, and it was
  green*.
- **`red-suites.test.mjs` compares both directions** — a gate that answered red with no entry, and
  an entry for a gate that has since gone green. The second matters as much as the first: an
  exception outliving its red means the next real failure is already excused.
- **`ShDeclaredRedTest` reports the same thing inside the Java suite**, which is the point rather
  than a duplicate. A check that only the skipped gates carry is a check about looking; this one
  prints all three gates' last outcome in front of the person reading the green one.

**An absent stamp fails nothing.** A fresh clone has none and CI runs each gate in its own job;
"nobody has run this here" is true rather than wrong, so it is printed. That printing *is* the
repair — the sentence whose absence cost eight pushes.

**`pnpm gates` prints that table without running anything (SH819).** Until it existed the table was
printed only *by* `ShDeclaredRedTest` and `red-suites.test.mjs`, so reading four lines derived from
four small files meant running the Java suite or the JS suite. It runs nothing, stamps nothing, and
**exits 0 whatever the colours are**: a reporter that failed on a red would be a fifth gate whose
verdict came from another gate's leftovers, which is SH770's mistake promoted to a command. It
reports; the gates judge. It is also the one reader with no gate in progress, so every row has an
honest last outcome — each comparator has one it must not read. The lines come from `gateLines` in
`gate-stamp.mjs`, shared with the JS comparator so this is not a third copy of the formatting; the
JVM copy stays because it is another language.

**And a stale stamp can be OWED, which is the verdict that was missing (SH841).** SH770 taught every
reader that a stamp from another commit is not this tree's colour and excluded it from *both*
directions — right, since a stale red blocks a commit over a fixed defect and a stale green reads as
*this tree is green*. Excluded from both it reads as **absent**, and absent is where the reasoning
stopped. Measured by running the gate nothing had asked for: `conformance` went **twenty commits**
without a run, eight of them editing the four files its replication scenarios drive, and it came back
123/123 — nothing broken, and nothing had asked. A gate nobody needed and a gate that is owed are
different facts, and only the second has evidence: the commits since the stamp touched what it
judges. `GATE_COVERS` in `gate-stamp.mjs` names each gate's **subject** — `GATE_RUNS` names its
command, and the two are apart because a reader needs the subject without loading the runner — and
`owedGates` counts the commits since a stale stamp that touched it. It is a **warning on the line,
never a failure**: a gate needing a packaged jar and a running instance cannot be a precondition for
a commit, and the honest response is to run it rather than declare it. The list is a **floor, not a
ceiling**: `js` reads the whole tree and so carries `null`, meaning any change at all; the others
name what is *certain*, so a path missing there makes the report silent rather than wrong. Being
quiet about a real debt is the failure this accepts; a warning that fires on every commit is the next
thing nobody reads.

**And that is exactly what it did for two commits (SH844).** The paragraph above forbids a false
alarm and the subject beneath it declared `cli/` and `shio-app/` for `conformance` — both of which
also hold the suites of the two gates that are **not** conformance. Found by *using* the instrument:
`pnpm gates` reported three owed commits and every file they touched was another gate's test. A
subject is the code a gate **exercises** — `cli/src/`, `cli/conformance/`, `shio-app/src/main/`,
`fixtures/` — and a gate's *own* tests belong in it, which is why `java` keeps `shio-app/` whole and
the check compares across gates instead of banning test trees. `GATE_SUITES` names where each unit
gate's suites live and `red-suites.test.mjs` refuses a subject that reaches into another's; the
overlap between `conformance` and `docs` on `cli/conformance/` stays, deliberately, because neither
can name the other's files with a prefix and a run of either is cheap. The shipped subject reached
**two** trees, not the three the first draft of that assertion claimed — `shio-react/test/` is under
neither prefix, which is the difference between counting gates wronged and trees claimed.

**A build that never began is stamped INCONCLUSIVE, not red (SH828).** A non-zero exit is only a
*colour* if a suite ran, and the exit code cannot tell you: Maven exits 1 for a failed test and 1
for a failed `clean`. So the runner reads the log it already keeps — a run that reached surefire
prints `Tests run:`, node's runner prints its counts — and where a failure carries neither, it
writes `inconclusive: true` with the exit code and a reason instead of `red: true`. The process
still exits non-zero, so whoever ran it sees the failure; what changes is that no comparator is
told the code is broken.

Measured twice in one session, both times the IDE's Java language server holding something under
`shio-app/target` so `clean` could not delete it. Zero tests ran, the stamp said red,
`red-suites.test.mjs` demanded a ledger entry against an open task for a defect nobody can find,
and the pair then reached the deadlock below. SH803 had already written the rule for a different
cause — *a false red is worse than an absent stamp* — and a lock, a compile error and an
unresolvable dependency are all the same shape. The one case deliberately kept **red** is a Spring
context that will not load: surefire still reports a count for it, and that failure is about the
code. No comparator needed a new branch, because a stamp with no `red` already reads as *no run
recorded*, exactly as `cleared` does.

**Both comparator gates can be red about nothing but each other, and no run clears it (SH818).**
The arrangement above has a fixed point. `java` judges `js` and `js` judges `java`, so once **both**
stamps are red, every run of either re-reads the other's red and stamps itself red again. It is
reached the ordinary way: a real red in one makes the other red about it, and fixing the first
defect is no longer enough. Measured here — the Java suite reported **1907 tests, one failure**, and
the failure was the js stamp; the js suite was clean but for the java stamp. Neither had a defect
and there was no sequence of runs that said so.

Both readers now **print** the cycle and name the exit when they see it, and neither asserts on it:
two red gates is a genuine red until somebody has read the two kept logs and found nothing in them,
and a check that excused the state would turn the arrangement that catches an undeclared red into
the one that hides a double failure. Read `shio-test.log` and `shio-js-test.log` first. If both are
clean but for the comparator, the exit is **`pnpm gates:clear java js`**, then `pnpm java` and
`pnpm test` in that order.

That verb exists because the escape already worked without it — `rm .gates/*.json`, since an absent
stamp is honestly *no run recorded* — and left nothing behind, so a stamp discarded to break a cycle
was indistinguishable from one discarded to dodge a red nobody wanted to fix. `clear` writes
`cleared: true` instead of unlinking: no colour, for the same reason an absent stamp has none, and
a record of when and at which commit somebody decided the last run was not evidence. It deliberately
does not decide whether you are in a cycle and does not re-run anything — a verb that cleared a
stamp and re-ran the gate is a verb that turns any red green if you type it twice.

### The escape that belongs to the wrong layer (SH782)

Inside a template literal the backslash is the **string's**, not the regex's:

| written | the pattern actually built | how it fails |
|---|---|---|
| `` new RegExp(`\b${x}\b`) `` | U+0008 either side of `x` | matches nothing — a false red |
| `` new RegExp(`${x}\s*:`) `` | `${x}s*:` — the letter `s` | matches the wrong thing — a false green |

Neither is a syntax error. The first shipped this week and made a check report *0 of 8 section types
documented* against pages naming all eight; the second had been sitting in `theme.test.mjs` behind a
**negative** assertion, passing for the wrong reason — `s*` accepts nothing, so `--a:` matched by
luck while `--a : #111` would have slipped past.

`cli/test/regex-escape.test.mjs` reads the bytes between the backticks of every
`new RegExp(<template>)` and reports a single backslash before `bBdDwWsS`. Today: **34 patterns
over 202 files, 0 wrong.**

**It opened at two, not zero, which is the point of writing it.** A grep for the call sites found
fifteen and said they all looked fine; the scanner found the two that were not. That is SH322's rule
paying off — the grep is the deliverable, and it finds the site nobody read.

**Running it also found two defects in the scanner itself**, both of which would have made it lie:

- A backslash *inside* `${…}` was being collected, so `` `${leaf.replace(/[^\w]/g, …)}` `` — a
  perfectly correct regex literal in an interpolation — was reported. It read the half it had just
  decided to skip.
- Line numbers were computed on the comment-stripped text, so every finding named a line whose
  content was something else. Comments are now blanked rather than dropped, using the shared
  stripper's own `isProse` so it stays one opinion about what a comment is (SH740).

Both were found by *running* it against the repository rather than by reading it, which is the only
way either would have surfaced.

### A half is not a whole, and two readers said it was (SH781)

`roadkeep ship --part` records the half of a task that landed and **leaves the roadmap line open**,
so progress reaches the ledger without the work leaving the backlog. Two rules read that state as
*shipped*:

- **SH594** (`known-debt.test.mjs`) — an entry whose owner has shipped is the worst state a debt
  ledger has, so it fails. It read the changelog alone.
- **SH579** (`red-suites.test.mjs`) — an exception must name a task the roadmap lists and the
  changelog does not. Same reading, its own copy.

Together with `--part` they are unsatisfiable: the first half of a task orphans every entry naming
it, while the work — and the entry's reason — is still open. That is the collision SH764 named from
the other side, where *the act of recording a finding disabled the gate that would confirm it*. Here
the act of recording progress disabled the exception tracking the rest of it.

It was hit for real shipping an evidence half with a `red-suites.json` entry against it, and worked
around by filing the remainder as its own id — acceptable once, wrong as a convention, because it
grows the backlog for a bookkeeping reason rather than a product one.

**The fix is the rules' own stated intent**, which both implementations had drifted from: the
roadmap decides and the changelog only confirms. An owner is spent when the changelog holds it *and
the roadmap does not*. `known-debt.test.mjs` was already reading both files — `openIds` is three
tests down — so this was a comparison that was available and not being made.

**Two enforcers, and only the first was reachable by reading.** Fixing SH594 and pointing a real
entry at a genuinely partial task is what surfaced SH579's separate copy; deducing it from the code
would have found one and shipped half a fix.

### The file-level red with green assertions (SH698, SH778)

`cli/test/fixture-traits.test.mjs` fails intermittently at **file** level with every assertion in it
green:

```
✖ test\fixture-traits.test.mjs (1052.1779ms)
  'test failed'
```

Two sightings on 2026-08-17, in roughly fifteen full runs. It is worth knowing about because the
report names nothing: `node --test` marks the file failed and has no assertion to point at.

**What has been ruled out, by measurement rather than by guessing.** The kept run (SH354's rule —
`shio-js-test.red.log` survives the green re-run) says `1499 pass, 1 fail`, and the one failure is
the file. So:

| Candidate | Ruled out by |
|---|---|
| An assertion | Both tests in the file reported ✔ and no sub-test appears in the failing list |
| An unhandled rejection or exception | SH698's handlers print before anything can swallow the reason, and printed nothing |
| The teardown deadline | `closeFixture` rejects naming the server and its open sockets; that message is absent, and the outer test owning the `t.after` reported ✔ |

**What is left is the file's own process exit.** `node --test` marks a file failed when its process
exits non-zero whatever its tests said, and nothing reported that code — so SH778 added the third
instrument: `process.on("exit")` prints the code and the active handles when it is not zero. It does
not change the outcome; it makes the next sighting say which cause it was.

**It did not reproduce in twelve runs after that** — six ordinary, six at `--test-concurrency=32` —
which is why the instrument shipped and the cure did not, and it went on not reproducing for about
**eighty** more.

**Then it was provoked rather than waited for, and the fourth candidate was wrong too.** Forty full
runs at `--test-concurrency=16` produced a sighting on the first, and a second loop at the same
concurrency reproduced it again on its first run; thirty at `24` produced none. Waiting had cost
eighty runs and a `red-suites.json` entry. Repetition at the right concurrency cost four minutes.

The kept run names it in one line nobody had seen, because it is not a test failure at all:

```
✔ SH528: the fixture exhibits every trait that broke a real replica (128.0571ms)
✔ SH528: the two fixtures stay in step (9.5499ms)
Assertion failed: !(handle->flags & UV_HANDLE_CLOSING), file src\win\async.c, line 94
✖ test\fixture-traits.test.mjs (571.5735ms)
```

**libuv aborts the process.** Not an assertion, not a rejection, not the deadline — a native
assertion inside Node's own event loop (libuv 1.52.1, Node 26.3.1) on a Windows async handle being
closed while already closing. The file's `t.after(() => source.close())` tears down the captured
fixture server while `--test-force-exit` is forcing the process down, and the two races.

That also explains **why the third instrument never fired in some hundred and twenty runs**, which
had begun to read as evidence the exit was not the cause: an abort does not run `process.on("exit")`
handlers. The instrument was unreachable by construction on the one path it was written for. A
negative result from an instrument that cannot fire is not a negative result — it is the same
zero-reporting failure this repository warns every scan about, arriving through the runtime.

**The lesson is the method, not the handle.** SH698 concluded that a flake which will not appear on
demand is diagnosed by what a failing run keeps. True, and incomplete: *make it appear*. Both
sightings came from the first run of a loop at a concurrency the ordinary gate does not use.

**So the loop is a command now (SH808):** `pnpm flake`, defaulting to 40 runs at
`--test-concurrency=16`, with `--runs`, `--concurrency` and `--files`. The defaults are the settings
that reproduced rather than a taste — sixteen twice, on the first run each time; twenty-four found
nothing in thirty — and that is the property to keep, because **a repeater that ran the suite at the
gate's own setting would find nothing**, which is exactly what eighty runs of waiting had already
proved. It stops at the first non-zero exit and keeps the whole run through `runWithLog`, since the
libuv line was one among fifteen hundred and a `grep` for the summary is what discarded the evidence
the first two times.

Its own first hunt reported a `SIGHTING` on run 1 whose log read `'C:\Program' is not recognised`:
`runWithLog` defaults to `shell: true`, and a shell re-splits `process.execPath` on the spaces in
`C:\Program Files\nodejs\node.exe` — which that function's own comment predicts in as many words. A
repeater that calls its own spawn failure a sighting is worse than no repeater, and the only reason
it took one minute rather than an afternoon is that it keeps the run.

**The cure was the flag, and two better-sounding ones were measured and rejected first (SH780).**

`closeIdleConnections()` on the fixture server — the keep-alive sockets `fetch` leaves behind — read
as the obvious candidate and **did not fix it**: the abort came back on run 20 of the next loop.
Reaching one step further to `closeAllConnections()` turned SH698's own deadline test red, because
that test *holds* a socket on purpose to prove the deadline fires. Destroying it made the instrument
unreachable — this defect's own shape, met a second time inside its fix.

So the measurement asked whether the other half of the race — `--test-force-exit` — is needed at all.
**The suite exits on its own, and the flag is gone.** Without it the abort does not reproduce in
**120 runs** at the concurrency that produced roughly three sightings in ~160.

**Getting to that answer took three measurements and the first two were wrong**, which is the part
worth keeping. Both said the flag was buying a lot of time — 12.6 s, then ~9.5 s — and on that basis
SH780 recorded a decision to keep it. Both were taken on a machine that was busy: the first while
provocation loops ran, the second while other work overlapped. Three identical runs then came back
**25.8 s, 22.6 s and 16.3 s**, a ten-second spread — larger than the effect being measured.

Measured properly, alternating the two configurations so drift falls on both equally:

| | pair 1 | pair 2 | pair 3 | pair 4 | median |
|---|---|---|---|---|---|
| with `--test-force-exit` | 12.8 s | 11.8 s | 11.7 s | 11.6 s | **11.7 s** |
| without it | 11.7 s | 11.9 s | 10.9 s | 12.9 s | **11.8 s** |

**There is no cost.** The flag bought nothing, and the trade SH780 recorded was between a real defect
and a number that does not exist.

**"The flag is gone" was true of one script (SH814).** It was removed where the flake was found —
`cli`'s `test` — and stayed on `conformance` and `conformance:docs`, which are the two suites that
drive a fixture server: the sockets there are the very handle shape that produced the abort, so the
gates keeping the flag were the ones least able to afford it, and the conformance gate is also the
slowest and least often re-run. The watchdog SH807 added to replace it had the mirror-image reach —
one script of nine. Both are now over every suite, and `cli/test/test-runner-flags.test.mjs` holds
the nine as an asserted list with the two rules beside it, so a tenth cannot be added without
choosing. The general shape is worth more than the instance: **a fix reported in the singular
usually was one**, and the ledger sentence recording it does not know how many places it was true of.

**And there was no drain to explain (SH805, retired).** A probe in the runner — `beforeExit` plus
`process.getActiveResourcesInfo()` — shows the loop is never held: `beforeExit` fires at 6698 ms and
`exit` at 6699 ms, one millisecond apart, with only the two stdio pipes alive. Nothing lingers.
Per-file drain is 50–140 ms and the whole suite alone is 45 ms, both of which are process startup and
teardown.

The rule this leaves: **an unpaired timing measurement on a developer machine measures the machine.**
Alternate the configurations, take medians, and check the spread of identical runs before believing a
difference — here the spread was twice the claim.

Two candidates were measured and rejected on the way, both of which sound better than the answer.
`closeIdleConnections()` on the fixture server — the keep-alive sockets `fetch` leaves — **did not
fix it**: the abort came back on run 20 of the next loop. Reaching one step further to
`closeAllConnections()` turned SH698's own deadline test red, because that test *holds* a socket on
purpose to prove the deadline fires; destroying it made the instrument unreachable, which is this
defect's own shape met a second time inside its fix.

**And what the flag was also doing is now reported instead of hidden (SH807).** It killed any process
with a handle still open — so a leaked handle used to be *invisible*, because the run passed. Without
it the run does not fail either: it does not end, and CI burns its 20-minute timeout printing no
summary at all.

`scripts/test-watchdog.mjs`, imported by the cli suite, is the whole answer, and the mechanism is one
word: **`unref()`**. An unref'd timer never keeps a process alive by itself, so a healthy run never
meets it — the loop empties, the process exits, the timer dies having done nothing. It fires only
when something *else* is holding the loop at the deadline, which is exactly the condition being
watched and cannot be detected by asking what is pending, because at that moment plenty legitimately
is.

What it prints is `process.getActiveResourcesInfo()`, and there is no stack to print beside it:
nothing threw, and every test passed. The handle **is** the finding. A leaked listening server comes
back as

```
Still alive: ["PipeWrap","PipeWrap","TCPServerWrap"]
✖ test\zz-leak-probe.test.mjs (8046.1734ms)
```

so a hang becomes a named file. The deadline is 120 s, `SHIO_TEST_WATCHDOG_MS` moves it, and the
assertion drives a real leaking process rather than asserting that `setTimeout` was called.

Two Windows-shaped traps met while writing it, both worth knowing: `--import` rejects a bare absolute
path with `ERR_UNSUPPORTED_ESM_URL_SCHEME` (it reads `D:` as a protocol, so pass the `file://` href),
and a test that spawns node inherits **`NODE_TEST_CONTEXT`** from the runner it is itself running
under, which makes the child behave as a nested test child — that one made both assertions fail
against a watchdog that was working perfectly.

`closeFixture` is idempotent regardless (SH780): the second call joins the first rather than handing
libuv a handle that is already closing. That is **not** what removed the flake — nothing forces the
exit now — but closing twice is a thing a teardown should survive, and the old behaviour answered the
second caller with `ERR_SERVER_NOT_RUNNING`.

#### The socket it held was not the state it meant (SH1146)

SH698's deadline test held a raw connection that **sent nothing** and asserted that `closeFixture`
could not finish. It was green on Windows every run and red on every Linux runner for as long as the
JS job was read, reporting the silence it exists to catch — which is this instrument's own failure
shape for the third time in its history.

The cause is in `closeFixture` itself: it calls `closeIdleConnections()` on purpose (see above), and
**an accepted socket that has sent no request is idle by that definition.** Measured on Node 26
Linux: `close` completes in **0 ms** with the socket outstanding and `getConnections()` back to 0.
The precondition — one connection open — passes first, so the run looks like it is measuring
something.

The fix is the second of the two the section proposed: hold the socket in a state `close` cannot
reclaim. The test now writes a complete request the handler never answers and waits for the server's
own `request` event, which makes the connection **active** on every platform. That premise does not
move with the runtime, where "accepted but silent" moved under it.

**And the premise is now a test rather than a comment.** `SH1146: an idle accepted socket does not
hold the close` asserts that the close does *not* reject — the opposite direction to the test beside
it — so if `closeIdleConnections()` ever stops reaching such a socket, that is a named failure and a
deliberate decision, not SH698 passing for a reason nobody checked. Removing the in-flight request
fails the held-socket test in one run, which is how the pair was verified.

**That premise was Linux's, not Node's (SH1162).** On Node 26 Windows the same idle accepted socket is
*not* reclaimed: the close waits out its deadline with `getConnections()` at 1, so the premise test
was red on every Windows run from the moment it merged — the mirror of the defect it was written to
close. It now asserts each platform's measured answer, and a platform that changes its answer fails
by name. The held-socket test is unaffected, because an **active** socket holds the close on both.

### A stamp taken against another commit is not a colour (SH770)

The comparison above is between a **claim** and a **measurement**, and nothing asked whether the
measurement was about this tree. Measured: `ShDeclaredRedTest` failed over `.gates/conformance.json`
holding `red: true` at `4b20d8e0` while HEAD was `42d02432`, two commits later. It was right, and by
luck — the defect it blocked over happened to still be open.

Both directions are wrong and the second is worse. A **stale red** blocks a commit over a failure
that may already be fixed, which teaches people to re-run until it clears. A **stale green** reads
as *this tree is green*, which is the exact sentence SH755 exists to stop somebody believing.

So a stamp whose `sha` is not HEAD is excluded from both lists and reported instead:

```
conformance — last run at 950cdaed, 2026-08-17T11:44:57.024Z — NOT this tree, so not a colour
```

That is a more useful line than either a colour or a silence, because it names the run somebody has
to do.

**And a stamp at HEAD can still be about another tree (SH1103).** The documented order is implement,
run the gates, ship, commit. `ship` rewrites the ledger at the HEAD the green run was taken at, and
the JS suite reads it: the coverage obligation counts shipped ids, and the red-suites comparator reads
the roadmap. So a ship could turn `pnpm test` red after its last green run, and the stamp still read
as fresh. That is how SH1038's commit landed over a red. A stamp now carries `inputs`, a SHA-256 over
the four files a ship or a coverage mirror writes between a run and a commit (`STAMP_INPUTS`:
ROADMAP, CHANGELOG, IMPROVEMENTS, `docs/coverage-named.json`), hashed as path, NUL, raw bytes, NUL.
Same sha with different inputs is stale. `ShDeclaredRedTest` computes the identical digest, and
`red-suites.test.mjs` holds its file list to the script's. A stamp without the field is not stale for
lacking it, for the reason below. In practice, run the gates **after** `ship`; a stamp taken before it
now says so.

**The commit alone, and deliberately not SH764's `compiles()`.** That predicate answers *could this
change have produced different classes*, which is right about the JVM and wrong here: **no gate
writes a `java` stamp** — the Java suite is the java gate and cannot stamp its own outcome — so
every stamp that exists belongs to `js` or `conformance`, and both read the whole tree. `pnpm test`
reads `ROADMAP.md` and `CHANGELOG.md`; `pnpm conformance` reads `docs/`. A documentation commit
really can flip either colour, so there is nothing provably inert to exempt.

Unknown either side is **not** stale. A stamp with no `sha` predates the field and a checkout with
no git cannot be asked — inventing staleness from an absence is the same mistake in the other
direction.

### The suite went red about its own logging (SH806)

One run of the Java gate reported **8 errors over 1856 tests**, none of them an assertion. Three
`@SpringBootTest` classes failed to start and the rest was Spring's failure-threshold cascade:

```
Logging system failed to initialize using configuration from 'null'
ERROR in SizeAndTimeBasedRollingPolicy - Timeout while waiting for clean-up job to finish
```

The next run was green, 1894 of 1894, with the string absent. A re-run "fixing" it is what SH770
calls being right by luck, and since SH800 the false red is *recorded* — the stamp says `red: true`
and the comparators then demand a `red-suites.json` entry for a defect nobody can find.

**The cause was not volume in `store/logs/`, which holds ten files.** `logback-spring.xml` includes
Spring Boot's `file-appender.xml`, whose `LOG_FILE` falls back to `${java.io.tmpdir}/spring.log`, and
tests never set `logging.file.name` — the test tree's own `application.properties` **shadows**
production's, where that key lives. So every context in the suite opened a rolling appender **in the
system temp directory**, and its policy ran a clean-up over that directory looking for archives.
Measured on this machine: `%TEMP%` holds **309,064 entries**, and `spring.log` had reached 3.2 MB
with a fortnight of `.gz` beside it. That is the timeout — and the quieter half nobody had noticed is
that the suite was writing megabytes into a developer's temp folder on every run.

`src/test/resources/logback-test.xml` fixes it outright: logback and Spring Boot both prefer that
name over `logback-spring.xml`, so the suite logs to a console it discards and writes nothing. A
property would not have done it — the fallback lives in the XML, not in Spring's property binding.

**Two rounds of verifying-by-breaking were themselves wrong, which is the part to copy.** Deleting
the config and re-running passed, twice, for two different reasons: a stale `logback-test.xml` sat in
`target/test-classes/` (SH282's trap, through a resource instead of a class), and the assertion was
a plain JUnit test — but `logback-spring.xml` is read by **Spring Boot's** logging system, not by
logback, so without a context logback falls back to its own console default and the check was asking
about a world the test was not in. It boots a context now, and deleting the config fails with
`ROOT → FILE → …\Temp\/spring.log`.

### A byte no rendering shows you (SH804)

An edit wrote a literal **backspace** — one byte — where the two characters of `\b` were meant. The
editor showed nothing; `grep` showed the line as expected; Node's failure output printed
`expected: /… js\b/` beside `actual: '… js'`, a string that regex plainly contains. An assertion
failing while both halves render as a match is the least diagnosable shape available, and `od -c` was
the only thing that answered.

SH782's class, arriving by a route SH782 cannot watch: not through JavaScript at all, but through the
edit. So the rule is about the **byte** — no source or document may hold a C0 control character other
than tab, newline and carriage return — which needs no parsing and catches every variant whatever
introduced it.

**It was not a ratchet over an empty set.** The first run found two live defects, both in this file,
both invisible for as long as they had been there:

- the SH782 table's *written* column showed `` new RegExp(`${x}`) `` where it meant
  `` new RegExp(`\b${x}\b`) ``. Both `\b` had been resolved into backspaces, so the row documenting
  the defect had become an instance of it and read as the **opposite** of its own point;
- `✖ test\fixture-traits.test.mjs` had lost its `\f` to a form feed and printed as
  `testixture-traits.test.mjs` — read aloud here several times without anybody asking where the `f`
  went.

`\0` is the one C0 byte this repository writes on purpose: a composite-key separator that cannot
occur in the data being joined. Six files use it and each is **named**, so a seventh adoption is a
diff somebody reads. `public/assets/` is excluded as built vendor output — a rule about what we type
has no business reading minified third-party bundles.

The lint reads **itself** like any other file, and that is the part worth copying. Its first draft
skipped itself in the walk *and* shipped with the literal bytes in its own character class — a
self-exclusion hiding precisely the thing it exists to find. Both declarations are `\u` escapes now
and both fixtures are built from character codes, so it passes its own rule honestly.

#### And then it was green for its whole life over files it never opened (SH1119)

Two more literal backspaces were found by `grep -P '\x08'` over the tree — not by the lint above,
which was running and passing. Its walk was an **allow-list of ten trees**, and it had
`shio-react/src` but not `shio-react/test`, and no `claude-plugin/` at all. Both defects were in
exactly those two places. Measured: the ten trees reach **1,890** files, a walk from the repository
root reaches **2,220**, and the 330 unread held both.

So the walk starts at the root and names what is *not* ours instead — dependencies, build output,
git's store, the vendored `.roadkeep` engine, an instance's `store/`. **A deny-list cannot miss a
package that does not exist yet**, which is the property the tree list did not have: an allow-list
also has to be extended by whoever adds a package, and a scan that silently stops covering a new one
is indistinguishable from one that never ran. SH814's shape again, where a flag was removed from one
script of nine.

`SH1119: the walk reaches the trees an allow-list of ten left out` names seven files — the two that
held a defect, one per tree that had no representative, and one that *was* listed so a pass is not
the walk agreeing with itself. Verified in the direction that matters: narrow the walk back to `cli`
and the byte scan goes **green** while that assertion fails, which is this defect exactly.

**What the two repairs found, which is why this was a task and not a two-character fix.** The
published-files check (`claude-plugin/test/plugin.test.mjs`) came back clean — no `SH<n>` ships
today, so nothing drifted while it was blind, though nothing could have said so. The other did not:
`unsaved-work.test.mjs` asserted that `use-unsaved-guard.ts` must **not** call `useBlocker(`, on the
grounds that it throws outside a data router — and SH1090 had since given the console
`createBrowserRouter` and this guard that very call, which **the next test in the same file
asserts is there**. Two assertions in one file requiring opposite things, and only the corrupt one
could pass. The premise is real and is kept where it belongs: held against `App.tsx`, the file that
decides it, rather than by forbidding the call at the one place that has to make it.

The cause belongs with the bytes: a **shell heredoc resolves `\b` before the file is written**, so a
scripted edit inserting a regex corrupts it silently where an editor tool would not. Worth knowing
while fixing this, because the same hazard is one layer up — the doc comment first written for the
widened walk contained `` `*/src` `` and closed its own block comment.

### A reserved range protects an id you type, not one you compose (SH801)

SH787 reserved everything from the nine-thousandth id up for fixtures and asserted it from both
ends. SH799 then needed a list of synthetic ids and wrote them `` `SH9${800 + at}` ``. Every *value*
is in the reserve and perfectly safe. The **source** is not: the static text left in the file is
`SH9`, a real shipped task, and `assertion-debt`'s walk reads source rather than runtime values. It
credited SH9, which that file asserts nothing about.

A reservation protects an id somebody **types**. It cannot reach one that is composed, because the
thing every reader of this repository's ids actually sees is the literal prefix. Put the whole number
inside the interpolation — `` `SH${9800 + at}` `` — one character apart, and the wrong one reads
better.

Guarded in `regex-escape.test.mjs`, which already owns *a literal built by interpolation is not the
literal its author meant* (SH782, with regexes). **JavaScript only, and that is complete rather than
a gap**: Java has no template literals, so the shape cannot occur there — not that something else
covers it.

Its own two fixtures are built by concatenation rather than written out, because a file spelling the
hazard literally would be the only thing the check ever reports. That is SH782's oldest problem, in
its own header, met a second time by the rule beneath it.

### A bare word in a scan is bounded by its neighbours, or by nothing (SH794)

SH792's first run read `exempt` inside **ShCsrfExemption**`LintTest` and reported three innocent
files. SH782's class from the other side: there a regex could not spell an escape the string ate,
here one spells a word without saying it is one. Both read as the rule the author meant and are a
different rule, and neither goes red — they **over-report**, which looks like diligence.

**The measurement said the opposite of what was expected.** Across 32 declared scanners there is
exactly one other unbounded word alternation, and it is **correct**:
`AccessDeniedException|ShAclPermission|GrantedAuthority` in `ShAclAssociationWalkLintTest` asks *does
this file make a security decision*, and `SimpleGrantedAuthority` is a true instance of that. Every
other bare word carries **adjacent context**, which bounds a branch as well as `\b` does —
`(?:find|get|read)\w*\(` cannot match inside `target` because `\s+` precedes it, and
`\.(hasSizeGreaterThan|isGreaterThan)\(` cannot match inside `isGreaterThanOrEqualTo` because the
`\(` forces a backtrack to the longer branch.

So the rule is **not** "always write `\b`". It is about what the scan reads, and it keys off the
`@scans` declaration SH740 already requires:

- a **code** scan sees an identifier as a *usage*, and a longer identifier containing the word is
  usually still the concept — bare is fine;
- a **prose** scan sees identifiers as *mentions*. A sentence naming `ShCsrfExemptionLintTest` is
  talking about it, not doing what it does, so a substring hit there is a false positive by
  construction — bare is a defect.

Enforced in `regex-escape.test.mjs`, which already owns "a scan's pattern is not what its author
meant". **The Java scanners are not checked and nothing else checks them** — their declaration
carries no prose/code mode this reader can consult, and saying another instrument covers it is the
SH792 defect written fresh.

Two things its own first run cost. Walking `ROOT` instead of `TREES` took **90 seconds** and read
every `node_modules` the filter then discarded — 64 ms scoped, and a gate that slow is one somebody
removes. And **SH792's catalogue caught the sentence above as it was being written**: *the Java
scanners are not checked and nothing else checks them* is hand-off-shaped, so the reader demanded a
verdict for it. That is the first time it fired on a claim being made rather than one already there,
and it earned a fourth kind — `uncovered`, for a sentence that names a gap instead of asserting
somebody else fills it. Which is the thing to write when tempted by a hand-off.

**SH862 — the third instance, and it is the window rather than the word.** SH792's trigger is a
comment line and always was; the *name* was then looked for across the next three lines whatever they
were, so a sentence about an exemption sitting above a call read that call's argument as the
instrument being handed to. Measured twice on one commit:

```
// SH859 — no file is exempt any more, and this is what says so.
assert.deepEqual(offences("cli/src/diff.mjs", `a${NUL}b`), []);
```

reported `control-characters.test.mjs -> diff.mjs`, a hand-off nobody wrote — and the author's only
way out was to **rename the fixture**, which is a workaround the next author cannot read. So the
window stops at the first line that is not prose: a wrapped sentence continues on the next *comment*
line, which is where SH789's own name was and the case the window exists for, and a line of code ends
it. `namedBy` is the whole rule and is fixtured from both sides, including a blank line, since a gap
between the sentence and the code would otherwise move the defect down one line.

Removing it **dropped a CLAIMS entry**, and that is the finding rather than a side effect:
`known-debt.test.mjs -> floor-kind.test.mjs` was the reader's own over-match written down. Its
`SELF` reason said so outright — the name came from a `path:` field on a code line under a sentence
about a staleness check three tests further down the same file. An entry that exists only because a
scan over-matched reads exactly like a case somebody triaged, which is what makes this family worth
the fourth fix rather than the fourth workaround.

### A hand-off between two instruments is a claim, and it is the one kind nothing verified (SH792)

SH789's defect was not the exclusion and not its reason — both were right. It was the half that
points at somebody else's file: *nothing is lost, `ShDeclaredRedTest` reads the js stamp from inside
the Java gate.* An exclusion is checkable because the code performs it. The sentence excusing it is
**prose about another module**, as reliable as the day it was written and re-read by nobody, because
the file carrying it is green. SH583 one level up: two instruments *dividing* one idea are
complementary only by accident, and the seam is invisible from both sides.

`cli/test/handoffs.test.mjs` holds every such sentence, classified and asserted exactly — the
`ShAgentP1ConformanceTest` shape, so gaining one is a reviewable diff with a verdict rather than a
number quietly moving. Four kinds, and only the first can rot: `hands-off` (this instrument excludes
a case and names another as the cover), `reverse` (this one exists *because* another does not cover
it — the healthy direction, where the gap is filled instead of pointed at), `self-exclusion` (a file
cut from its own walk, citing another as precedent for the technique), `historical`.

**The count, which was the deliverable (SH322):** eleven sentences, five of them live hand-offs, and
**four of the five verify** — the fifth is SH789's. So exclusions mostly *were* the cheap option they
looked like. That is worth stating because the opposite was expected, and a grep that confirms a
practice is as much of a result as one that condemns it. A `hands-off` entry must carry `Verified:`
and how, or the second test fails: a claim recorded without being read is the original defect
written down rather than fixed.

Two things its own first run taught. The regex needed a leading `\b` — without it `exempt` matches
inside **ShCsrfExemption**`LintTest`, so three files that merely name that class read as hand-offs,
and a guard for unread claims that over-counts teaches the wrong lesson about how much of this there
is. And the file is cut from its own walk, which is a `self-exclusion` of exactly the kind it
catalogues — stated in the code rather than left quiet, because its header has to name the
instruments it is about, in sentences about exclusion.

### Two comparators, and the seam between them was open (SH789)

The two directions of the same comparison live in different files, on purpose. `red-suites.test.mjs`
holds both — a red gate nothing declares, and a declaration whose gate went green — but it excludes
`js`, because it **is** the js gate and reading its own previous stamp would fail one run for a
failure already fixed, then pass on the third. It says so, and says nothing is lost because
`ShDeclaredRedTest` reads the js stamp from inside the Java gate.

That was half true. `ShDeclaredRedTest` only ever asked whether a **red** gate was undeclared. The
reverse existed only on the side that cannot judge `js` — so **the repository's only exception, for
the js gate, was judged by neither**, and roughly fifty consecutive green runs never retired it.

The seam is the thing to remember, not the missing branch: when one instrument excludes a case and
names another as covering it, that claim is a *reference to code somebody else wrote* and is exactly
as reliable as the day it was written. Both files now hold both directions, with the js exclusion
still on the JS side and nowhere else.

**A stale green must not retire a declaration** any more than a stale red may block a commit — SH770
in both directions, which is what makes an out-of-date stamp read as an absent one rather than as
evidence.

**What it found, on the run that added it.** The `fixture-traits` exception, declared 2026-08-17 and
expiring 2026-08-24, against two sightings and ~50 green runs since. It was deleted here rather than
by SH780, which is what its own text expected — on different grounds, and that is the point. An
exception is a licence to commit over a red, and there was no red. SH778 and SH780 stay open: they
are about diagnosing the flake, not licensing it, and SH778's third instrument now prints the process
exit code, so a third sighting arrives with a diagnosis rather than a mystery. The right response to
that sighting is SH579's rule from the top, not a licence already sitting in the ledger.

Had nobody decided, the expiry would have fired on the 24th — a gate going red **for the ledger
rather than for the code**, which is the worst available way to learn this.

### Declaring one

`red-suites.json` is the only place an exception may live, and it is deliberately hostile:

- **Time-boxed** — at most fourteen days from `declared`, and an expired entry fails
  `cli/test/red-suites.test.mjs`. A forgotten exception goes red rather than quiet.
- **Tied to open work** — `task` must be a task `ROADMAP.md` still lists and `CHANGELOG.md`
  does not. The day the task ships, the ledger goes red until the entry is deleted, so the
  exception cannot outlive its reason and the deletion lands in the same commit as the fix.
- **Reasoned** — `why` is the whole review surface, and the check enforces a length because a
  shrug passes every other rule in the file. Say what is red, what the failure actually shows,
  and why it is safe to keep committing over it.

This is the contract `assertion-debt.test.mjs` uses for its DEBT list and
`ShSilentCatchInventoryTest` — which since **SH917** counts *decisions* rather than `catch` blocks.
A method whose comment carries `one-decision:` contributes **one** entry however many catches
implement it, and the marker has to name who records instead. The reason is a measurement: over
three tasks the `turing` figure went 1 → 3 → 7 → 11, and three of the ten added were one decision
written three times — a numeric parse per type, each `try { … } catch (NumberFormatException) {
return null }`. Collapsing them into one generic helper took the figure to 9 and changed nothing
about the program, which is the tell: the number was pricing duplication rather than risk. It prints
both figures on every run (`103 silent catch(es) in 101 decision(s)`), and the recurring shape the
marker is for is a method contractually unable to throw — every catch converts to a return value and
the *caller* files the incident, which this reader cannot follow. Also
`ShSilentCatchInventoryTest` for its inventory: a named list with a reason beside each entry,
never a ceiling. A ceiling lets one task pay the debt and the next spend it.

**Run `pnpm test` *after* `roadkeep ship`, not before it.** `assertion-debt.test.mjs` derives
"shipped" from `CHANGELOG.md`, which the ship writes — so a suite run before it cannot see the
task you are about to commit, and a green run is evidence about the previous state. This has
already produced a red push: SH658's suite ran green, shipped, and committed, and the next
iteration opened on `+ 'SH658'` in the debt diff. The ship is cheap and the suite is not, so the
order is ship → run → commit, and a task whose assertion names its own id passes on the first
run instead of the second. Nothing lints it locally; `.github/workflows/suites.yml` is what
catches it, one push later.

**Naming another task's id in an assertion message pays that task's debt — spell a reference
`see SHnnn` (SH731).** The reader credits any `SH\d+` on a non-comment line, which is what makes
the rule checkable without a second registry to keep true, and the cost is that it cannot tell
*this assertion is the one SH322 asked for* from *this message mentions SH322 in passing*. It has
mislabelled three tasks; the third was a failure message ending "…rather than one more repair
(SH322)", a citation of the rule the lint follows, and the suite went red claiming somebody had
paid SH322 off. Nothing was wrong except the sentence. So `see SHnnn`, `see also SHnnn` and
`cf. SHnnn` are skipped by the reader, and a bare `(SHnnn)` still reads as a claim.

The convention cannot be *enforced* — nothing can read an intent that was never written — but it
does not have to be remembered: both failures name the file that credits the id and the spelling
that fixes it, which is the moment it is learnable. SH731 chose this over the other candidate
(*the first id on a line is the subject, the rest are references*) on a measurement, not a
preference: over 405 suite files and 699 credited ids, the first-id rule loses all credit for **58
ids that are genuinely asserted**, because a message may legitimately assert two; the citation
spelling costs **0**. A debt ledger must err toward keeping a payment rather than inventing one.

**And a named list's reason field is a code line, so it paid debt too (SH843).** The not-a-comment
rule is right and its blind spot is what *else* is not a comment: a ledger entry's `why:` is a
string in an object literal, and this repository is built out of named lists carrying a sentence
each. Measured across every suite file — **832 ids credited anywhere, three whose every credit was
a reason field**. The third is the argument: SH842 shipped one iteration earlier, its own rule sends
a batch payment to `DEBT`, and it never arrived because the sentence explaining where SH113's
assertion lives paid it — silently, in the commit that wrote the sentence, which is SH731's failure
by a route SH731 does not cover. `withoutDeclaration` had always run over **every** file; no other
file had written the `debt-declaration` markers, so a general mechanism read as private to one
suite. Two ledgers carry them now, real debt went **102 → 103**, and the cure is deliberately not a
broader comment rule — a reason field is not a comment, and blanking every string would stop an
assertion message paying anything. Two things the round is worth remembering for: the first guard
written for it **named the three ids in a loop** and so credited all three, which is the defect
committed in the act of refusing it — a derived property replaced the list; and a stray `start` with
no `end` would cut the rest of a file out of the walk, retiring debt in silence, so
`aDeclarationMarkerIsBalanced` reads the region exactly the way `withoutDeclaration` does rather
than counting occurrences, because this very file names the marker once more in a failure message.

**And since SH529 each DEBT group carries a verdict, because a list is not a decision.** SH527
established the count and held it; the question it left open was *which of these earn an
assertion* — not all do, and saying which is the work rather than a way out of it. Every group now
reads EARNS or DOES NOT, with the shape the assertion would take, ordered by **whether the defect
could return** rather than by cost. That inverts the obvious order: a *removal* is the cheapest
entry and ranks first, because an absence has nobody looking at it, while the sixteen console
entries rank last despite being the largest group — a broken editor page is seen the first time
somebody opens the console. One entry (SH238) is marked as earning **a reader, not an assertion**:
it is asserted in `shio-react/e2e`, a tree this reader does not walk because it needs a running
instance, and a second assertion would duplicate a passing one.
**Writing the first group's assertion found a defect (SH620).** `ShRemovalsStayedRemovedTest`
closes SH20, SH21 and SH60 — and SH21 had never finished: four tracked files, ~456 KB of the legacy
AngularJS bundle, still in `src/main/resources/ui/` under a commit whose subject is *"delete legacy
AngularJS resources/ui tree"*, shipping in every jar for two blocks. Nothing referenced them and
nothing served them (`resources/ui/**` is not a served root). A deletion of 330 files is reviewed
by its diff, and a diff shows what went rather than what stayed. The debt entry said "unasserted";
the assertion came back red.

### The ledgers of what is knowingly wrong, and one reader (SH594)

The device is sound and it has now been invented **eight** times. Each is a named list rather than
a ceiling, each is checked in both directions, and each was the right call on its own. Together
they were six formats, eight readers and eight places to look, and nothing joined them.

| name | what it holds | since |
|---|---|---|
| `verify-baseline.json` | accepted `verify` findings, per project | SH142 |
| `assertion-debt DEBT` | shipped ids carrying no assertion | SH527 |
| `open-findings.json` | corpus assertions red at HEAD | SH560 |
| `red-suites.json` | a suite allowed to be red, with its reason | SH579 |
| `docs-open-findings.json` | published examples red against a live instance | SH499 |
| `floor-kind TOLERATED` | absolute floors over a shrinking corpus | SH672 |
| `response budgets AT_THE_WALL` | response ceilings with no headroom, and why | SH743 |
| `per-tool AT_THE_WALL` | MCP tools with no room for another argument | SH795 |

The `name` column is `LEDGERS`' own key, and **this table is asserted against it** (SH796) — as is
the identical one in `known-debt.test.mjs`'s header. Both had drifted: SH741 made the *count* an
assertion, so every ledger added since was forced into the array and none into either index, which
left the two documents a reader consults first saying four and five while eight existed. A header
under-reporting the device is the failure this file was written to stop, wearing its own name.

`cli/test/known-debt.test.mjs` does **not** merge them: their scopes are genuinely different, and
merging would throw away the per-ledger checks that make each honest. It answers the one question
none of them can — *what is this repository knowingly carrying, and which of it has an owner who
has already gone.* Three checks, and the second is the reason it exists: **no entry may be owned
by a task that has already shipped**, every entry must name an owner (an unsigned suppression can
never be shown to have gone green), and an owner in neither `ROADMAP.md` nor `CHANGELOG.md` is a
dangling reference from a retirement or a renumber. A fifth ledger is one row in `LEDGERS`, which
is the point — the failure was four bespoke readers, so a fifth bespoke reader would be the same
mistake again.

**It landed red and found the case its own rationale predicted.** `open-findings.json` held a
finding owned by SH555 *after SH555 had shipped* — the fix went to the render side and the CLI's
generated layout still writes `{{CSS}}`. Re-opening a shipped id is not one of the two honest
resolutions, so it was re-filed as **SH606** and the entry repointed; and SH606 is a real question
rather than paperwork, because SH555 made those fields `Handlebars.SafeString` and the corpus
assertion checks a brace count whose meaning that fix changed underneath it. One of the two
artefacts has to go, and the round that picks is the deliverable. `source-corpus.test.mjs`'s
`CLOSED_OWNERS` is now empty, exactly as its own comment said emptying it would mean.

### And red has to be visible without somebody choosing to look

[`.github/workflows/suites.yml`](../../.github/workflows/suites.yml) runs `js` and
`conformance` on every push to the working branch and on every pull request — the conformance
job builds the jar from the commit under test and boots it, which is the only way that suite
can run at all. That is the JS half of what SH241/SH284's rationale asked for; the Maven build's
own push-versus-pull-request question is still SH241's.

## A running instance makes `clean` fail, and it does not look like that (SH783)

```
Failed to clean project: Failed to delete .../shio-app/target/viglet-shio.jar
```

**That is a file lock, not a build problem.** A `java -jar` instance — the one the conformance
sequence needs, on 2710 or 2711 — holds `target/viglet-shio.jar` open, and Windows will not let
Maven delete it. Nothing in the output says so.

Hit four times in one session. What it looks like is a broken build, and the reflex it teaches is to
re-run — which usually works, because by then the instance has been stopped for an unrelated reason,
so the lesson never lands and the next person pays again.

**Stop the instance before any `./mvnw … clean`.** That is the whole rule.

### The second half, which is the expensive one

**A `clean` that fails means the build did not run at all.** Everything above it in the log is the
*previous* run — so a `Tests run: … Failures: 0` line scrolled past on the way to the error is a
green summary of a tree that is no longer there. Read `BUILD FAILURE` before reading any count. This
cost a false read in the same session that filed it.

### Why there is no preflight, which was the first idea

A `pre-clean` check that named the holder was the obvious repair and is refused on proportionality:
nothing in this repository starts the instance — it is always a hand-typed `java -jar` — and the
build declares no `exec` or `antrun` plugin, so the check would mean adding a plugin execution to
**every** build, for every developer and for CI, where no instance ever runs. A cross-cutting build
change to improve one local error message is a worse trade than a sentence in the file a session
already reads before building.

So this is written here rather than kept in somebody's notes, which is the same reason SH282's
*"Nothing to compile"* trap is in `agents.md`: a fact that decides what you do next is worth nothing
if it is only in the memory of whoever met it last.

## Build & Deploy

### JavaScript workspace (pnpm)
The JS packages live in a **pnpm workspace** (`pnpm-workspace.yaml` + root
`package.json`): `shio-react` (console), `shio-client` (CDA core),
`shio-react-sdk` (React SDK, depends on the core via `workspace:*`),
`shio-model` (the post-types DSL — SH63), `shio-sections` (the page-section
vocabulary — SH95, depends on the DSL; **both publish since SH156**),
`create-shio-app` (Next.js starter),
`cli` (`@viglet/shio`, the `shio` command — SH16) and `claude-plugin` (private;
the package exists so the plugin's manifests have a test — SH97). Run
`pnpm install` once at the repo root. **Node ≥26** — `shio-sections`' tests import
`.ts` directly and rely on native type stripping.
**Tests are `node --test "test/*.test.mjs"` per package**, listed in the root `test` script — no
vitest, no jest. `shio-react` joined that list in **SH306**, with `jsdom` as its only test
dependency: it runs the served `render/shio-editor.js` in a real DOM, which is the only faithful way
to test it (every function in it *is* DOM behaviour, and evaluating it from the Java suite is closed by
the no-script-engine non-goal). **These do not run under `./mvnw test`** — the Maven gate is Java only,
so a rule that must never regress silently also belongs in `ShEditorBridgeTest` as a textual guard.
**They run in CI since SH579** (`.github/workflows/suites.yml`, on push and on pull request); what
SH241 still owns is the Maven build's own trigger.
**The conformance suite is a second glob, and the glob is the opt-in (SH448).**
`cli/conformance/*.conformance.mjs` — run with `pnpm conformance` at the root (or
`pnpm --filter @viglet/shio run conformance`), never by `pnpm test`, because it needs a
**running instance** and the npm registry. It reads `SHIO_URL`/`SHIO_USER`/`SHIO_PASSWORD`
the way the CLI does (defaults `http://localhost:2710`, `admin`/`admin`), seeds a
`ShioConf…` site per run and removes it in `after`; `SHIO_CONFORMANCE_KEEP=1` leaves it for
inspection. Two scenarios: **replication** (a `node:http` fixture → `clone` → `propose
--accept` → `convert` into the instance → `content-pull` back → `verify` → `npm install
--dry-run` on what `init` wrote) and **native render** (`render.provision` → post-type →
site binding → posts → `/preview/<site>/` answers 200 carrying `sh-region`,
`data-shio-region`, `data-shio-region-missing` and `data-shio-prop`).

Three rules it exists under, and each is the reason a defect shipped:
- **An unreachable instance is a failure, never a skip.** `requireInstance` throws with the
  command that fixes it. The precedent is `ShPostgresSchemaIT` reporting `0.00 s / Skipped`
  without Docker while a reader concludes the schema was proved.
  **And a *stale* instance is the third misconfiguration, which cost a session (SH460).** A run
  went red on two shipped fixes because the JVM had started at 16:05:10 and the classes under test
  were compiled at 18:47:21 — after every commit it was validating. The CLI half was green in the
  same run, because it loads from disk. `requireInstance` now compares the manifest's `builtAt`
  against `git log -1`'s commit time **before any scenario builds anything**, so a stale instance
  produces one message naming both sides instead of a plausible-looking defect report. An instance
  reporting no `builtAt` is stale by definition — the field ships with the gate.
  `SHIO_CONFORMANCE_ALLOW_STALE=1` is the escape hatch for an instance you did not build, and
  `cli/test/conformance-live-build.test.mjs` proves the gate against a `node:http` stub, which is
  the one part of this suite that runs *without* an instance because the case worth asserting is
  the one no real instance volunteers.
  **And then the net was too wide, in a way that collided with another rule (SH764).** Both
  triggers fired on a tree that could not have changed the JVM: the instance was built from the
  exact commit under test and the only tracked changes were `docs/ROADMAP.md` and
  `docs/IMPROVEMENTS.md`, written by roadkeep minutes earlier. Since findings are filed as roadkeep
  tasks and a red suite is a stop, *recording a finding disabled the gate that would confirm it* —
  and the only way past was to commit first and verify after, which is the order the gate exists to
  prevent. So dirty is scoped to **paths that compile** (`compiles()` — the `docs/` tree and
  root-level markdown are inert, everything else counts, because a false green here is the failure
  this gate exists to prevent and a false red is merely loud), and the **clock is the last resort
  rather than the second**: the build's own commit is diffed against HEAD over those paths first,
  so a merge commit that moved only documentation no longer reads as a stale JVM. Note the rule is
  a directory and not an extension — seven `README.md` files ship *inside the jar* as blueprint
  carried files, so `*.md` would have exempted a packaged resource.
- **Nothing is injected.** Real sockets, real `fetch`, real npm — SH438, SH442 and SH446 all
  passed under SH422 precisely because it substitutes those three.
- **Every assertion names the task it would have caught**, so the file reads as the record of
  what a unit test structurally cannot see. On its first run it found SH453 and SH454.

**And that third rule is also how it grows (SH458).** The suite ran **green** on the instance where
a hand pass found SH455, SH456 and SH457 in one sitting — the expected state of a first version, not
a failure of SH448, because it asserts happy paths through the modules. So three assertions were
added **while those tasks were still open**, red by construction, each turning green as its fix
arrives (SH338 — the instrument precedes the fixes). What each one buys, and why none of them could
have been a unit test:
- **The process's exit code.** Every `convert` in the suite was a *module* call, so the shell's view
  of the command was the one observable it could not have. SH456 lives exactly there: the whole
  report printed, three posts written, exit **1**. The suite now spawns `src/index.mjs` as a process
  for one conversion. Same seam as SH445 — code fixed twice against a harness before anything ran
  the command.
- **The public route's negative cases.** The happy path was asserted and the lying path was not, so
  `/sites/<site>/<anything>/<anything>/` answering 200 with the home page went unnoticed. Two
  assertions: an unknown format segment, and a root-relative asset path.
- **That the rendered page's own references resolve.** Three files were named by hand and proved;
  the fourth was not. It now reads `src`, `<link href>` and `url()` off the served HTML and fetches
  each — the check no unit test can make, because both halves (the bytes are on the instance; the
  page asks for a path) are green in isolation and only the replica sees them disagree.

The fixture gained a root-level `/favicon.ico` for the first of those. It is the only asset in it
with **no folder segment**, so it is the only one whose upload takes `convert`'s root-folder branch —
and a fixture where nothing is at the root is a fixture that cannot see a defect in the root case.

Native build scripts are allowlisted via `onlyBuiltDependencies` in
`pnpm-workspace.yaml` (`esbuild`, `@tailwindcss/oxide`) — pnpm 11 no longer reads
the `pnpm` field in `package.json`, so this setting lives in the workspace file.
`@viglet/viglet-design-system` resolves from public npmjs (no scope mapping needed).

**`playwright`, `pixelmatch` and `pngjs` are declared at the workspace root, and that placement is
load-bearing (SH444).** They were `shio-react`'s (and, for the last two, nobody's — in no lockfile at
all), so pnpm's strict isolation put `playwright` in `shio-react/node_modules`, and a bare specifier
resolves from the **importing module** upward — `cli/src/`, `cli/node_modules`, the root — never
through a sibling. So `shio snapshot` and `shio audit` refused in a checkout that had the package
*and* the cached browsers, with a teaching error that is correct for a consumer of `@viglet/shio`
(whose own `pnpm add -D playwright` **is** on that upward path) and, here, read as advice already
followed that changed nothing. The root is the only placement both consumers see by the same walk:
`cli/src/audit.mjs`/`snapshot.mjs` and `shio-react/e2e/console-check.mjs`. Declaring it per package
would mean two ranges to align and, on drift, two chromium revisions for one repo. Neither the
placement nor the dependency reaches consumers — a devDependency is not installed by them, and
`files: ["src"]` means the tests never ship. **Do not move them back into a package**;
`browser-refusal.test.mjs` asserts they resolve, because every other browser test injects its capture
and so the whole suite passed while the commands could not run at all.

**An unmet peer range now refuses the install, because as a warning it cost the whole bundle
(SH242).** A grouped Dependabot bump set `shio-react`'s `monaco-editor` to `0.56.0` while
`monaco-graphql@1.8.0` declares `peerDependencies: { "monaco-editor": ">= 0.20.0 < 0.53" }`. pnpm
resolved it, warned, and failed nothing — then `pnpm run compile:build` (i.e. `mvn package`, the
release jar) died in the Monaco worker bundle with `Rolldown failed to resolve import
"monaco-editor/esm/vs/editor/editor.worker"` and no line of ours in the trace. What made it fatal
rather than merely unsupported: 0.56 added an `exports` map whose patterns re-prefix `esm/vs/`, so
every `monaco-editor/esm/vs/…` deep path — used by monaco-graphql, by `@graphiql/react` and by our
own `src/lib/monaco-workers.ts` — expands to `esm/vs/esm/vs/…` and resolves to nothing. **The fix is
the pin, not an alias**: `@graphiql/react` already depends on `0.52.2`, so `0.56.0` was the outlier,
and aligning on it satisfies the peer range and collapses the copies to one. A Vite `resolve` alias
remapping the broken prefix was tried first and is the wrong shape — it makes some specifiers
resolve and fails at whichever import the bundler reaches next, because the packages are
incompatible. Two guards, because neither covers the other's moment: `strictPeerDependencies: true`
in `pnpm-workspace.yaml` turns the bump itself into `ERR_PNPM_PEER_DEP_ISSUES` (it fires only when a
manifest change makes pnpm *resolve*, and it belongs in that file — in `.npmrc` pnpm 11 silently
downgrades it to the warning it replaces), and
`shio-react/test/monaco-workers-resolve.test.mjs` scans those three sources for their Monaco
specifiers and resolves each one, so a tree that stops resolving is red without a build. Dependabot
is capped at `0.52.x` for this dependency; lift it when GraphiQL moves.

### Maven Integration
`frontend-maven-plugin` (2.0.0) in `shio-app/pom.xml` runs, all from `../shio-react`:
1. `install-node-and-pnpm` (Node `v26.3.1` + pnpm `11.8.0`)
2. `pnpm install` — pnpm finds the workspace root (`../`) and installs all members
3. `pnpm run compile:build` (`tsc -b` + `vite build`)

**A verification run needs `clean` (SH282).** Measured: with a call to a method that does not exist,
`./mvnw -pl shio-app -am compile` prints **"Nothing to compile - all classes are up to date"** and
**BUILD SUCCESS**; `clean compile` fails correctly with `cannot find symbol`. The cause is not the
compiler — this build uses plain javac — it is that **the IDE compiles into `target/classes` on save**,
so the `.class` outlives the `.java` and `maven-compiler-plugin`'s staleness check skips the module.
Eclipse JDT also emits a class with `java.lang.Error: Unresolved compilation problem` embedded rather
than no class, so the broken member fails at *runtime*, in whatever test calls it — one missing accessor
read as 53 errors across five unrelated test classes. `-Dmaven.compiler.useIncrementalCompilation=false`
does **not** help; the staleness check is what short-circuits. So: **`mvnw … clean test`, and treat
"Nothing to compile" as no information.** The tell for the underlying defect is a `ServletException`
wrapping `java.lang.Error`.
**And the tell is now a build failure, not something you have to recognise (SH282 shipped).**
`ShIdeCompiledClassLintTest` scans `target/classes` and `target/test-classes` for the marker Eclipse
JDT embeds and fails naming the file, with `clean` as the fix — 813 class files, 4.4 MB, ~94 ms, and
verified by planting one and watching the run go red with "Nothing to compile" in the same output.
Two things it deliberately does not do. It cannot see a **stale but valid** class (an IDE-written
class and a Maven-written one both post-date their source — that is why the module was skipped), so
`clean` stays the rule rather than becoming redundant. And **redirecting the IDE's output is not
available**: `java.project.outputPath` has no effect on a Maven project (the path comes from the
`pom`), and `.vscode/` is `.gitignore`d, so no editor setting can be a repository guarantee. A
developer who wants the cause gone rather than caught can set `"java.autobuild.enabled": false`
locally, at the price of losing cross-file diagnostics for unopened files.
**One warning is expected output; anything else is new (SH271).** A clean run prints exactly one:

```
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes
because bootstrap classpath has been appended
```

That is SH250's `-javaagent:mockito-core` appending to the bootstrap classpath, so class-data sharing
narrows to boot classes. **Accepted deliberately** — the only ways to remove it are `-Xshare:off`,
which gives up the sharing that still works, or dropping the agent, which was the fix for a flake. No
test can assert it (a test runs inside the JVM whose startup printed it). The other three families are
guarded by `ShBuildWarningLintTest`: every plugin in both POMs is pinned (a `<profile>` declaration
inherits from the same POM's `<build>`, which is how Maven merges them), no logback appender uses a
`<layout>` where logback wants an `<encoder>`, and `showDeprecation` stays on so javac names a
deprecated API instead of saying "recompile with `-Xlint:deprecation`". **So a second warning line in a
build log is news** — which is the property SH250's verification depended on and got by luck.

**Measure the suite from the run, not from `target/` (SH283) — and keep the run (SH354).**
The verified command:

```
./mvnw -pl shio-app -am clean test -Dskip.npm=true > shio-test.log 2>&1; echo "EXIT=$?"
```

then read the count out of the file it kept:

```
grep -E "Tests run:.*Failures.*Errors" shio-test.log | tail -1
```

**The guard reads all three gates, since SH791.** `RUNS_THE_SUITE` matched `mvnw … test` alone —
fair while the rule it served was SH354's, and wrong the moment SH788 generalised that rule to three
gates on a failure measured against `pnpm test`. It was checking the gate the incident did *not*
happen to.

Broadening it was measured first, because `pnpm test` appears in these files as prose constantly
where `mvnw … test` does not — nineteen inline mentions in this file alone. The measurement that
settles it is narrower than "prose or command": **a line can only be judged if it also holds a shell
pipe**, and across both files the count of lines naming a gate and holding one is **zero**. A bare
`pnpm test` in a sentence has no pipe and can never be flagged, so the distinction the guard looked
like it would need turned out not to be one it has to make.

The non-vacuity check now names **one command per gate**, which is the part worth copying: the old
anchor asked only whether the scan found *something*, and the Maven lines satisfied that on their
own — so the guard could claim three gates while reading one and stay green doing it. Reverting the
pattern now fails with `no documented command for the `pnpm test` gate reached this scan`.

**Two commands rather than one, since SH788.** The one-liner this replaced ended
`| tee shio-test.log | grep -E "…" | tail -1`, and it was wrong in a way that took a year to
notice: a pipeline's exit status is its **last** command's, so the shell reported `tail`'s. The
same shape on `pnpm test` printed `fail 2` above `EXIT=0`. The redirect keeps the run exactly as
the `tee` did — that is SH354's property and it is unchanged — and the verdict now comes from the
command that has one. Add `set -o pipefail` first if you want it back on one line.

(One line each, deliberately: `ShTestCommandDocLintTest` reads these commands a line at a time, so
a backslash continuation would hide the redirect from the guard and the guard would pass on a
command it can no longer see — which is how it caught its own author breaking it.)

**The `tee` is not decoration.** Without it this pipeline answers "how many" and throws away
"which": the `<<< FAILURE!` lines and the stack trace go into the pipe, and `target/surefire-reports/`
— the only other copy — is removed by the `clean` that SH282 makes mandatory on the next run. That is
not hypothetical: shipping SH293, one run in six reported 1186 tests and **1 failure**, five identical
runs passed, and the failing class is still unknown. On red, read `shio-test.log`
(`grep -E "<<< (FAILURE|ERROR)"`) **before** running anything else; the file is `.gitignore`d and
lives outside `target/` for exactly that reason. `ShTestCommandDocLintTest` fails the build if a
documented command filters a run without keeping it — it caught the second copy of this very command
the moment it was written.

**And `pnpm test` keeps its run too, without anybody remembering to (SH645).** The same lesson had to
be learned twice: `pnpm test` reported `cli test: ℹ fail 1` on an otherwise green tree, twice, and
**neither time was the failing test's name captured** — both runs were read through a `grep` for the
summary line, which is the one thing a filter leaves behind. It has never reproduced, and that is
what makes it a task rather than a note: a red that is green next time teaches people red means
nothing, whether or not it was true.

`pnpm test` is now `scripts/test-with-log.mjs` — the same eight-package filter, spawned, with every
byte streamed to stdout **and** to `shio-js-test.log`, exiting with the child's code. A script rather
than a pipeline because `tee` and `Tee-Object` differ in buffering, encoding and what they do to the
exit code, and a runner that swallowed a non-zero exit would trade the one reliable gate for the
flake it is chasing. Verified in both directions: green, and with a deliberate failing test, where
the exit is 1 and the test's name is in the log. `pnpm test:raw` is the unlogged form. The log is
`.gitignore`d by the existing `/*.log`.

**And the next run cannot erase it (SH735).** The fixed name is right and its own comment says why —
the interesting comparison is against the *previous* run, and a directory growing a file per
invocation is one nobody prunes. What followed from it was not: the stream opens `"w"`, so the next
run truncates the log, and after a red one **the next run is the one somebody makes to see whether
it reproduces**. Measured in the same session that shipped this: `fixture-traits.test.mjs` went red
at file level with all nine assertions green — SH698's exact symptom — the re-run was green, and it
took SH698's own stderr handler output with it. That handler exists *because* the failure never
prints a reason. This is SH354's lesson failing inside the instrument built from it.

A red run is now copied to **`shio-js-test.red.log`** and a green one is not, so the file's presence
means something. Copy rather than rename, and green untouched, so the ordinary comparison keeps
working exactly as it did — and a failure to copy is written to stderr rather than thrown, because a
caller reading "could not copy" instead of the failing test's name is worse off than before. Also
covered by the existing `/*.log`.

**Both cheap causes the task named are ruled out**, so the next sighting starts from the log rather
than from these: no cli test uses a fixed temp path (every one is `mkdtemp`), `create-shio-app`'s
`emit.test.mjs` only reads and compares rather than running `emit` into the tree, and every test
server binds `listen(0)`. What is left is contention under `pnpm`'s concurrent workspace run — the
suite has never failed when `cli/` is run on its own — and that is a hypothesis, not a finding.

Two traps, and the first causes the second. **`-q` suppresses surefire's `Results:` summary entirely**
— it is a Maven INFO line, so quiet mode removes the only authoritative total. Without it the obvious
fallback is to sum `target/surefire-reports/*.txt`, and **that directory accumulates**: reports from a
targeted `-Dtest=X` run sit beside the full run's, so the sum double-counts those classes (977 claimed
during Block P, 974 on a clean run). So drop `-q` when the number matters, and keep `clean` for SH282's
reason. Both traps are the same shape — *an artefact of an earlier command changing what a later one
appears to say* — which is worth remembering as a class, since `target/` holds several.

**The suite runs in four JVMs, and eight is slower than four (SH414).** `maven-surefire-plugin` sets
`forkCount=4`/`reuseForks=true`. This is the other half of SH317's bill: SH317 priced the *count* of
isolated Spring contexts (~1.5 s each, 36 of them, about two fifths of the run) and closed the
obvious remedy — the datasource URL is part of the context cache key, so two URLs are two contexts by
construction and "share a context but not a database" cannot be had. What was left was not to remove
a boot but to stop the boots waiting for each other.

Measured on the **surefire phase**, isolated from the ~18 s of compile in the Maven total, because
SH317 recorded ±20 s of noise in that total and the whole win fits inside it — SH283's lesson one
level along, and the reason the numbers below are worth writing down at all:

| `forkCount` | surefire phase | runs |
|---|---|---|
| 1 | **91.8 s** (87.4–96.1) | 2 |
| 4 | **68.4 s** (68.1–68.6) | 3 |
| 8 | 77.4 s | 1 |

Two findings, and the second one matters more than the 23 s. **The curve turns back up well before
the core count** — that machine has 28 — because what is being parallelised is 36 Spring boots
contending for one H2 process image and one CPU cache, not 176 independent test classes. So
`forkCount` here is a measured number, not a fraction of the hardware; do not "improve" it to `1C`.
And **the forked run is the stable one**: three runs at four forks landed within 0.5 s of each other
against a 9 s spread at one fork. By SH250's rule that is the larger gain, since a suite whose wall
clock is stable is a suite whose regressions are visible.

`reuseForks=true` caps it at four JVMs rather than one per class. Each fork keeps its **own** context
cache, so a configuration used by classes that land in different forks boots more than once — which
is why the win is 25% rather than 75%, and why the ceiling `ShContextCacheHeadroomTest` guards is
per-fork and still comfortable. It is also why the run stays correct rather than merely faster: a
fork's `jdbc:h2:mem:shio-test` is private to that JVM, so classes on the shared datasource get *more*
isolation than they had, never less. Validated on four green full runs (1298 tests) before it landed.

**Failsafe is deliberately left at one fork**, and since SH415 it does not run at all unless asked.

**The ITs are opt-in (SH415): `verify` skips them, `-DskipITs=false` runs them.** `skipITs` is a
property in `shio-app/pom.xml`; failsafe reads it itself, so the default needs no plugin config.
Measured first: the failsafe phase is **31.1 s of a 120 s `verify`**, and **16.2 s of it is
`ShAgentTenantIsolationIT` — one test** — because SH414's cold start applies here too, at one fork,
with nothing to amortise it over.

**The reason it is safe is the opposite of how it looks, and this is the part to know.** These ITs
were never a gate. **No workflow in this repository runs the Java suite** — `build.yml` is the only
one that calls Maven `verify`, it is `workflow_dispatch` (manual), and it passes
`-Dmaven.test.skip=true`; `console-check.yml` runs on push and PR but with `-DskipTests package`, and
the rest are manual or docs. Failsafe also carries `testFailureIgnore=true`, so **a red IT does not
fail the build** even when one runs. So nothing that was catching something has been switched off;
what changed is that a run now *says* the ITs did not run, instead of `verify` letting a developer
assume they did.

**Flipping the default is what found SH416**, and that is the argument for the split rather than
against it: the cross-DB classes had never run in either state of the machine, and asking them to run
explicitly is what made it visible. Without a Docker daemon they **skip** — `@Testcontainers(disabledWithoutDocker = true)`,
`0.00 s`, counted as `Skipped: 6` — so "`verify` proves the schema on PostgreSQL" was false on any
machine without one, the same absence-you-cannot-grep-for that SH322 is about. With a daemon up they
**errored**, 6 of them. Both fixed under SH416 below. The honest repair for the coverage is still to
wire the suite to push and PR, which this split makes *easier* rather than harder: a 68 s unit gate on
every push and a deliberate second gate for tenancy and schema, instead of one blob no workflow can
afford. **When to spend the flag is in `agents.md`** — tenancy, Liquibase, anything that reads
differently on PostgreSQL/MariaDB, or the CDA/static-file delivery paths — with the rule that a schema
or tenancy change takes the flag *even when the unit suite is green*, that being exactly the case
where green means least.

**A `@Container` field read from `@DynamicPropertySource` must be started eagerly (SH416).** Both
cross-DB ITs now carry `static { CONTAINER.start(); }` beside the field, and 26 ITs pass with
`Errors: 0, Skipped: 0` for the first time. The cause: `@Container` starts the container in
`TestcontainersExtension.beforeAll`, and Spring makes its first context-load attempt **before** that
runs, so the `registry.add("spring.datasource.url", POSTGRES::getJdbcUrl)` supplier is invoked against
a container with no mapped port.

**The symptom names none of that**, which is the part worth keeping. All three methods report
`ApplicationContext failure threshold (1) exceeded` — Spring declining a *second* attempt, saying
nothing about why the first failed — and it took three runs to separate from a cold image pull
(`postgres:16` turned out to be two weeks old in the local cache):

| setup | result |
|---|---|
| `@Container` only, default threshold | fails, 3.9 s, no container |
| `@Container` only, `failure.threshold=5` | **passes**, 18.7 s, zero errors |
| eager `static` start, default threshold | **passes**, 17.1 s |

The middle row is the diagnosis: a second attempt succeeding where the first failed means the
configuration was right and only the timing was wrong. Raising the threshold is **not** the fix —
it makes a green run depend on Spring retrying, which is a race left in place. A class initializer is
deterministic (P5): the JVM completes it before any member is read, so it does not depend on
extension ordering — swapping `@Testcontainers` ahead of `@SpringBootTest` was measured and changed
nothing. `@Container` stays for teardown, and `start()` on a running container is a no-op.

**Two guards, because one fix would have been the smaller half.**
`ShTestcontainersEagerStartLintTest` fails the build naming any `@Container` field a
`@DynamicPropertySource` reads without an eager start — verified by planting the defect and watching
it name `ShMariaDbSchemaIT.java: MARIADB`. And **failsafe's `testFailureIgnore` is now `false`**: it
was `true`, inherited from the repository's first Maven commit rather than chosen, and it is the other
half of why this survived a release. With no daemon these classes skip and read as a pass; with one
they errored but the build stayed green. *Neither* state told anybody — and now that the ITs are
opt-in, the only person who runs them is one who asked for an answer.

Parallelising failsafe was **not** measured and the SH414 numbers argue against bothering: at one
fork the phase is 31 s of which 16 s is a single cold start, so a second fork buys concurrency and
pays a second cold start — and under contention that start grew from 13 s to 24 s.

**Two things that look like the same idea and are not.** `spring.main.lazy-initialization=true` was
tried and rejected on measurement: **26 failures and no time win** (2:17 against a 2:21 baseline,
i.e. inside the noise). The template helpers are registered from beans discovered at refresh, so
deferring bean creation makes `ShPageRenderTest`, `ShRenderFormTest`, `ShTemplateValidatorTest` and
four others fail with `could not find helper` — a real incompatibility, not a test that needs fixing.
And JUnit-level parallelism (`junit.jupiter.execution.parallel.enabled`) is a *different* proposition
from forking: it runs classes concurrently **inside one context against one datasource**, which is
precisely the cross-class contamination SH246/SH314/SH315 spent three tasks isolating. Forking gives
the concurrency without touching that, which is the only reason it is safe here.

### Why the guards in this repository look the way they do (SH122/SH128/SH130/SH131/SH136)

Moved here by SH356, because these are rules for the next guard rather than history of the last one.

**When an invariant is violated for the first time, write the guard for that invariant — not a
resolution to be careful.** The evidence is that both guards written this way found *more instances
than the defect that motivated them, on the day they were written*: SH122's found three broken CDA
paths, SH128's found four, including a scoped token that could not list the children of any folder
below a site root. Every one of those paths already had a behavioural test, and every one passed,
because the tests exercised the root case and the defect only appears below it.

Four corollaries, each paid for:

- **Prefer a textual lint to a behavioural test** when the invariant is about how code is *written*
  rather than what it returns. A behavioural test proves the paths it exercises; the next instance
  lands in the path nobody thought to add. A lint covers the paths that do not exist yet.
- **Give the lint an escape hatch that costs a sentence.** `ShFolderSite-exempt`, the `nativeQuery`
  carve-out: a guard with no exemption gets deleted the first time it is wrong, while a guard whose
  exemption is a line of prose gets an argument in review instead.
- **Some invariants cannot be tested behaviourally, and knowing which is the skill.** `Map.copyOf`'s
  iteration order comes from a salt fixed once per *process*, so "build it twice and compare" passes
  over broken code in every run there will ever be. Before writing a guard, ask what a broken
  implementation would do differently **inside one JVM** — if the answer is nothing, the guard has to
  be textual, or the code has to be restructured until the defect becomes observable. SH136 did both.
- **Prove a guard by breaking the code**, not by watching it pass. And two shapes that look like
  guards and are not: **a version assertion that compares a constant with itself** (SH130 replaced
  one with a check against the *observable obligations* of the revision claimed — the tautology would
  have kept passing after somebody bumped the constant past a breaking change, which is the only
  moment it had a job), and **a golden that pins prose**: keep every key and the literal value of the
  fields that are themselves contract, collapse free text to a type token, because a golden that
  fails on a copy edit stops being read and the diff is the entire product.

**When a design note hedges about a condition, check it before writing the hedge (SH250, kept by
SH356).** That section's own last paragraph said the wiring "should be checked against the JaCoCo
argLine, if that is ever added" — and JaCoCo sets it **today**. So surefire's late-replacement
`@{argLine}` was load-bearing rather than defensive: `${argLine}` would have replaced JaCoCo's agent
with Mockito's and coverage would have stopped being collected, silently. The other half of that
task generalises further: **fixing a flake is worth more than its size suggests**, because a suite
that goes red for no reason teaches everybody that red might mean nothing.

### Premises about code we do not own (SH274, SH275)

Six claims about third-party behaviour were load-bearing and lived in comments, where a version bump
can falsify any of them silently. **The bar is not "can it be asserted" but "does it expiring change
behaviour, or only tidiness?"** — asserting all six would be as wrong as asserting none, because a
guard nobody needs is a guard nobody maintains. The judgement, one row at a time:

| Premise | Verdict |
|---|---|
| Nashorn's classes are not on the classpath | **asserted** — `ShRenderNoScriptEngineTest`; SH183's whole non-goal |
| the default `TemplateLoader` reads the classpath | **asserted** (SH274) — and the evidence is the *message* naming `/application.hbs`: it fails to find, it does not refuse to look. "It throws either way" would have been a false confirmation |
| the default resolver chain includes `JavaBeanValueResolver` | **asserted** (SH274) — expiring silently puts the persistence model back in template scope, the liability Block P exists to avoid |
| Mockito self-attaches without a `-javaagent` | **stale, and replaced** — SH250 made it false and nothing noticed. The live premise is its successor: the surefire `argLine` still carries the agent, now asserted by `ShBuildWarningLintTest` |
| `Map.copyOf` iterates from a per-JVM salt | **not assertable, declared** — SH136's own finding, and why that guard is a lint rather than a test |
| the MCP Java SDK is Jackson-2 / Boot-3 | **not assertable** — it is a premise about a dependency this project does not have, so checking it means adding the thing being evaluated (**SH276**) |

Two durable lessons came out of measuring rather than reasoning. **The guard was tested and its reason
was not:** the suite proved *our* engine walks no bean, never that an *unconfigured* one does — so a
library that became safe by default would have turned both guards into dead weight and both comments
into lies, with nothing failing. And **a compound assertion hides which half is load-bearing**:
`[{{secret.value}}][{{secret.class.name}}]` → `[][]` read as two routes closed by `MapValueResolver`,
when `class` is blocked by Handlebars itself. Half that assertion was evidence for the guard and half
for the library; they are pinned separately now.

**What test isolation costs, measured (SH317).** SH315's rule — a test that measures the whole
instance may not share it — means a distinct `spring.datasource.url`, and the URL is part of the
Spring test context cache **key**, so every such class boots an application context of its own.
The price is **~1.5 s per context**: a probe class declaring its own datasource and doing nothing but
boot reported 1.585 s and 1.390 s on two full runs, against 0.002 s for a class with no context at
all. At 33 isolated databases that is roughly **50 s of a ~124 s suite — about two fifths**, and it
grows by a second and a half every time the rule is correctly applied. So the answer to "does the
next one cost two seconds or twenty" is *about one and a half*, which is cheap enough that the rule
stays as it is.

Three things that measurement establishes, none of them obvious beforehand:

- **The suite's wall clock cannot see it.** Two runs of an identical tree came in at 2:09 and 2:30,
  so a ±20 s noise band swallows the one-context delta whole. The number has to come from the probe
  class's own surefire line — SH283's lesson one level along.
- **The probe must be deleted after measuring.** A class kept only to measure would permanently
  consume the cache headroom `ShContextCacheHeadroomTest` (SH347) guards, and crossing *that* ceiling
  evicts a context, recreates a schema mid-run and fails a later test with no defect behind it. The
  recipe lives in that class beside the dated constant, and the count and cost print on every run.
- **"Share a context but not a database" stays closed.** Two URLs are two contexts by construction;
  changing that needs a schema per class inside one URL, or a reset between classes, and both trade a
  1.5 s boot for a cleanup that has to be exhaustive to be *safe*. 1.5 s is the number such a
  proposal now has to beat.

**The budget is declared, and spending a slot is a diff (SH423).** SH347 made the ceiling visible by
asserting it, which reports arrival and not approach — measured over Block Q, eleven tasks took the
count from 38 to 44 and nothing asked once; then four tasks in one session took it to the wall and
the first anybody heard was a red build in the middle of unrelated work. So
`ShContextCacheHeadroomTest` now carries **`DECLARED_CONTEXTS`**, asserted exactly: the diff that
changes it is the moment somebody is asked, in both directions — a task that *frees* a slot lowers
it, or the budget it returned is one the next task spends believing it was always there. The
headroom prints on every run.
**And the counter was undercounting by exactly the margin.** It counted distinct `jdbc:h2:mem:`
URLs; Spring keys on the whole `MergedContextConfiguration`, so two classes on one URL with
different properties are two contexts and were read as one. It said 56 where the suite created
**62**. The counter now reads `properties`, `@Import`, `webEnvironment` and `@DynamicPropertySource`
— measured to be the closed set, since no test uses `@TestPropertySource`, `@ContextConfiguration`,
`@ActiveProfiles` or `@MockitoBean` — and the margin dropped from 8 to **2**, its one remaining job
being the four classes whose properties are registered at runtime. `*IT.java` is excluded: failsafe
is a separate JVM with its own cache. **The current state is 62 of 64 with zero spare**, so the next
context is a deliberate decision about the ceiling rather than a surprise.

**Skip the frontend build for backend-only work.** The three frontend executions
bind to `generate-resources`, so a plain `mvn compile`/`test` triggers the
(slow, Node-dependent) React build. For backend compile/test, always pass
**`-Dskip.npm=true`** — the single `<skip>${skip.npm}</skip>` toggle on the
plugin, which leaves the Java build untouched:
`./mvnw -pl shio-app -am test -Dskip.npm=true`.
(Still run the **full** suite, not a single `-Dtest` class.)
This file used to document `-Dskip.installnodepnpm=true -Dskip.pnpm=true`, which are
`frontend-maven-plugin`'s **per-goal** properties and are not what this pom is wired
for — passing them silently runs the React build anyway, so the documented
backend-only command was neither.

### Jackson 3 is the rule, and the annotations package is not Jackson 2 (SH574)

This repository is on **Jackson 3**: `tools.jackson.databind`, `tools.jackson.core`. The four
packages Jackson 3 replaced — `com.fasterxml.jackson.` **`databind`**, **`core`**, **`dataformat`**,
**`datatype`** — may not be imported by any Java source here.

**`com.fasterxml.jackson.annotation` is not one of them, and this is the whole trap.** Jackson 3 kept
the annotations where they were and *depends* on them: `tools.jackson.core:jackson-databind:3.1.4`
pulls `jackson-annotations:2.21`, which is where `@JsonInclude` and `@JsonProperty` still live. So a
grep for `com.fasterxml.jackson` answers ~92 hits across 56 files and **87 of them are correct**.
"No `com.fasterxml`" is a false rule, and stating it that way is why the real one went unwritten and
unchecked until SH574.

**Why a wrong import compiles.** `springdoc-openapi` → `swagger-core-jakarta` drags
`com.fasterxml.jackson.core:jackson-databind:2.21.4` onto the classpath, so Jackson 2 resolves
without anyone asking for it. `ShConvertedLayoutRenderTest` imported `JsonNode`, `ObjectMapper`,
`ArrayNode` and `ObjectNode` from it and passed for as long as nobody looked. That transitive arrival
is springdoc's business and stays — banning it in the pom would break the dependency that needs it —
so the rule is about what *we* import and what *our* poms declare, never about the resolved tree.

**`asString()`, not `asText()`.** Jackson 3 makes `asString()` the abstract accessor and keeps
`asText()` as a legacy alias, so a file on the Jackson 2 spelling compiles while drifting from every
other file — the kind of difference review does not see.

`ShJacksonVersionLintTest` holds all three parts: no replaced-package import in either source root,
no `com.fasterxml.jackson` groupId declared in either pom, and a vacuity guard asserting the
annotations exemption still covers something (if it ever reaches zero the exemption is dead code and
should go). It reads import statements, so a fully-qualified reference in a method body and the
package named in prose — `ShMcpServer` discusses a Maven coordinate in a comment — are outside it: a
declared lower bound, per SH273/SH322.

### Publishing to npm
`.github/workflows/publish-js-sdk.yml` (manual `workflow_dispatch`): bumps the
publishable packages, builds, and runs `pnpm publish` in dependency order
(`shio-model` → `shio-sections` → `shio-client` → `shio-react-sdk` → `create-shio-app` →
`@viglet/shio`). `pnpm publish` rewrites `workspace:*` to the published version
automatically — which is why the order is load-bearing, since `shio-sections` depends on
`shio-model` that way. Needs the `NPM_TOKEN` secret.

### Vite Config
- Output: `../shio-app/src/main/resources/public/`
- Proxy: `/api` and `/logout` and `/csrf` → `http://localhost:2710`
- Path alias: `@` → `./src`
- Chunk splitting: vendor-ui (radix), vendor-lib (other node_modules)
- **`import.meta.dirname`, never `__dirname` (SH542).** Every package here is `"type": "module"`, so
  the CJS global only works because Vite's default `bundle` config loader rewrites it — Vite 8 warns,
  and under `configLoader: 'native'` (planned to become the default) it is a `ReferenceError` at
  config load, which fails `compile:build` and so `mvn package`. All nine configs use the ESM form
  and `cli/test/vite-config-esm.test.mjs` walks the repo to keep it that way.

### Static Resources (Spring)
- `/store/**` → file_source parent directory
- `/file_source/**` → file_source directory
- `/thirdparty/**` → `classpath:/META-INF/resources/webjars/`
- `/console/**` → React SPA (permitAll in security)


## The gate whose fallback was null in the shape it demands (SH609)

SH460 asks two questions at once: is this instance running the code under test, and can a matching
commit still say so. On a clean tree the commit hash answers it. On a **dirty** tree the hash no
longer describes what compiled, so SH500 falls back to `builtAt` — and that field was `null` on
precisely the deployment the conformance gate insists on.

`ShAgentBuildIdentity` read the timestamp off `CodeSource.getLocation()`. Under `java -jar` that is
a Spring Boot **nested** URL — `jar:file:/opt/shio.jar!/BOOT-INF/classes!/` — and `Paths.get(uri)`
refuses it: the scheme is `jar:`, there is no filesystem provider for it, and the call throws. The
throw was caught and the field became absent.

**The consequence was not a wrong answer but a gate that was off in the only state anybody runs it
in.** A working tree with edits in it is the normal case, so `SHIO_CONFORMANCE_ALLOW_STALE=1` became
the standing incantation — and an escape hatch used on every run is a check deleted slowly, which is
the shape SH579's ledger exists to refuse.

Everything before the first `!/` is the outer jar, and its own `lastModifiedTime` is the right
answer: a repackaged jar is written once, so its timestamp is exactly *when this artifact was
produced*. The exploded `target/classes` case is untouched, and it is still the newest-class walk
there, because the change that makes an instance stale is usually in some other class.

**The assertion is the deliverable** (SH527): a case that resolves the identity from a fat-jar
`CodeSource` and requires a timestamp, so the null cannot return the next time packaging changes.

One test was written and deleted rather than weakened: `jar:file:/x.jar` with no nesting marker
cannot be constructed — `java.net.URL` refuses it outright with *"no !/ in spec"*. The defensive
bound stays in the code, because a `substring` running off the end inside build identity would turn
a diagnostic into a startup failure, but there is no test for it and the reason is written where the
next reader will look for one.

## Two jars, one class, and a comment that described a removal nobody did (SH939)

`ShUtils.isJSONValid` parsed a string as a `JSONObject`, fell back to `JSONArray`, and answered
whether either worked. It was the codebase's only `org.json` consumer and **nothing called it**.

Its POM declaration carried the comment *"Used directly by ShUtils; previously came transitively via
the now-removed turing-java-sdk"*. Both halves were false, and the second cost a wrong fix before
`dependency:tree` corrected it: `turing-java-sdk` is **not** removed — it still brings `org.json`
through `turing-commons` at `20260522`. Deleting the declaration did not remove the library, it
silently **downgraded** it from `20260814`. What the declaration was really doing was pinning a
newer release, so it is a `dependencyManagement` pin now and says so.

The cost was on the **test** classpath. `spring-boot-starter-test` brings `org.skyscreamer:jsonassert`,
which carries `com.vaadin.external.google:android-json` — a 2013 Android stub of `org.json` for
environments that have no real one. This one does. So two implementations of `org.json.JSONObject`
sat there, resolved by classpath order. Spring Boot printed *"Found multiple occurrences ... You may
wish to exclude one of them"* on every boot, and nothing enforced the advice. Production carried a
single provider throughout, so a test touching `JSONObject` could have passed against an
implementation the product does not ship, and the suite would have said nothing either way.

**SH916's `ShDeclaredDependencyUseLintTest` could not see this, and is right not to.** It asks
whether a judged group is imported under `src/main/java`; `org.json` *was*. The dead thing was one
level in — the importer — and "is this declaration used" and "is its user reachable" are different
questions, the second of which no lint here answers.

So `ShDuplicateClasspathClassLintTest` holds the **observable** instead: a named list of class
resources that must have exactly one provider, because the causes are unbounded (an unused method
holding a dependency open, a starter changing what it drags in, a transitive under a new name) and
the symptom is one `getResources` call. Named rather than swept — a whole-classpath duplicate scan
finds dozens that are deliberate, and a lint reporting those is one somebody switches off. It
carries a vacuity check too: `getResources` finding **nothing** returns an empty list, and zero
providers would sail past a check for "not more than one".

It earned its place immediately — it failed on the real defect while the fix was still being
written, which is how the false premise in the comment got found.

One knock-on worth knowing: deleting the method removed **two silent catches**, so
`ShSilentCatchInventoryTest`'s `utils` row went from 2 to gone. That is the one way that population
is allowed to shrink without an argument about whether a degradation is worth reporting — the code
that could degrade is no longer there.

## The lints that read what is written about them (SH613)

`ShRenderCacheReachLintTest` scanned raw file text for `@Transactional`. SH206 removed that
annotation from `ShAgentVerifyService`, explained in the javadoc **why** — and the build went red,
because a sentence naming the annotation is indistinguishable from declaring it. **A lint whose
finding can be produced by writing about the rule is one people learn to route around by not writing
about it**, which costs exactly the explanation the next reader needs.

SH206 fixed the one lint in front of it. The population was never counted.

### The measurement, and the shape of the finding

**20 tests read Java source** (the instrument prints the list and each one's mode on every run, so
the number is not written down here). Of those, **ten were reading prose by accident**. The other
ten were not defects — and that is the actual finding: `ShSilentCatchInventoryTest` looks for
exemption markers, `ShFolderSiteInvariantTest` keys on `ShFolderSite-exempt`, and
`ShByteStabilityLintTest` waives a line by the comment beside it. **From outside, deliberate and
accidental prose-reading are the same scan.** "Strip comments everywhere" would have broken three
correct lints.

So the answer is a declaration, not a rule. `@ShSourceScan(CODE | PROSE | BOTH)` states what a scan
reads, and `ShSourceScanLintTest` holds it: **CODE must actually use `ShSourceText`** — a scan that
claims to read code and then greps raw text is SH206's defect wearing a label — and PROSE/BOTH must
carry a written reason, because an accident cannot explain itself.

**The declaration says what a scan reads, and not how much of it (SH825).** A different failure sits
beside the one above: a scan about a *construct* — an annotation's arguments, one method pair, one
sentence — matching against the whole file. It always fails **green**, because matching too widely
finds an escape that is not there, so the guard passes over exactly the case it exists for. Four
turned up in one session (SH794, SH816, SH822's near-miss, SH824), which looked like a pattern
worth a shared *match-within-this-span* helper.

It is not one, and the count is the useful part. **Sixty-five scans declare themselves** — 28
`@ShSourceScan`, 37 `// @scans`. A heuristic over broad containment checks flagged 23 files and 37
call sites; reading them left **one**: `ShAgentTransactionalOverloadLintTest` read
`body.contains(EXEMPT)`, so a single `@Transactional-overload-exempt:` comment disabled the lint for
the whole file — and the one bean carrying such a comment declares **33 methods**. Its own prose had
always said *"there is one honest reason to leave **the pair** mixed"*; nothing had compared the
sentence to the code. It is scoped to the pair the comment sits above now, and narrowing it found no
new violations, so the other 32 were clean.

Everything else was a false positive of the name heuristic — the variable holds a line, or a `catch`
body, or the question genuinely is about the file. `ShOffThreadTenantLintTest` is the one worth
knowing about: it looks like a second instance and is not, because its boundary tokens match
**import** lines, so narrowing to the call site invents two findings the file-level question
correctly absorbs. Measured before changing it. **Two in sixty-five means a fixture each, not a
helper** — four instances looked like a pattern and were four instances.

### Five strippers, not two

SH613 estimated this was "the second copy the repository will want and the first it should not
have". There were **five**: a whole-file state machine, three byte-identical per-line
`stripComment` methods, and an inline skip inside a loop. The three per-line copies used
`line.indexOf("//")`, which truncates a URL inside a string literal — in a repository full of URLs
and address grammars. **A rule enforced by five implementations is five rules.** `ShSourceText.code`
(literal- and text-block-aware, the one of the five that was correct) and `codeLine` (for the scans
that report `file:line` and so cannot lose the numbering) are now the only two.

### What the fix measured: two Spring contexts that were never there

`ShContextCacheHeadroomTest` asserts the context count **exactly**, so a slot cannot be spent
without somebody being asked. Reading through the stripper took it from **63 to 61**: four files
carried a `@SpringBootTest` or a quoted `properties = "spring.datasource.url=…"` **in a javadoc**,
including two lints that are not Spring tests at all.

The direction matters. The budget was reported as *more* spent than it is, so the wall SH423 exists
to warn about was two slots further away than the warning said. And that file had **already noticed
the hazard for itself** — it excludes its own source because its examples would count — without
asking whether anything else was affected. That is the whole argument for an instrument over a fix:
the reader who spots a defect in front of them is the reader least likely to ask how many others
there are.

### One side effect worth recording

The new test classes changed surefire's ordering, which turned SH635's twice-seen intermittent
`ShBlueprintApplyTest` failure into a **deterministic** one — and its permanent capture then named
the cause on the first red run. Declared in [`red-suites.json`](../../red-suites.json) against
SH635 rather than fixed here.

## A release commits what `emit` produced, and now reads it first (SH218)

`publish-js-sdk.yml` bumps every publishable package, re-runs `emit`, and commits the generated
trees. **Half of SH218 was already built** — the re-emit step exists, and the task's own reasoning
for it stands: without it the trees keep the old dependency range and `emit.test.mjs` turns the
*next* push red, so a release's side effect gets reported against whoever pushed after it and the
obvious reading of a red build ("what did I break?") is wrong.

What was not built is the section's last sentence: **a job that regenerates content should fail
loudly if the regeneration changes something it did not expect.** It did not. `emit` ran, and
whatever came out was committed unread.

The drift test cannot cover this. It runs on a push and answers *"the tree matches its source"* —
which is true immediately after the release commits, because the release committed exactly what
`emit` wrote. The unchecked moment is the one where a job writes into
`shio-app/src/main/resources/` and commits it in the same breath.

### The rule is about files, not lines

A release changes **versions**. A generated file appearing or disappearing during one is either an
overlay somebody edited without running `emit`, or a generator that broke — and a release commit is
the worst possible place for either, because it is the one nobody reviews.

So an **addition or deletion** under the generated trees fails the release; a **modification** is
expected, and is printed on success too (P9: "it changed six files" is the cheapest proof the step
ran at all). Deliberately *not* "every changed line must be a semver range": that reads the diff's
text, and a rule that has to parse a diff to decide whether a release is sound will one day be wrong
about a quoted string.

A rename is classified as an **addition**. Porcelain reports `R old -> new`, and reading that as one
path with an arrow in it would file a renamed generated file as a modification — the surprise this
exists to catch, arriving in the one shape that looks innocent.

The decision lives in `create-shio-app/src/emit-guard.mjs` as a pure function, so the rule is
assertable without putting a working tree into six states; `scripts/emit-guard.mjs` is the wiring,
and the step runs **before the publish steps**, because a surprise found after pushing to the
registry is one nobody can act on.

**One slip worth recording**: the test asserting that ordering first searched the workflow for
`npm publish` and found it in a comment near the top explaining how `pnpm publish` rewrites
`workspace:*` — SH613's lesson arriving in a YAML file. It now matches the step declaration. The
same pass fixed a comment that still said `emit` composes *three* packages; SH216 took it to six,
which is the kind of number a comment should not carry when `PACKAGES` is the list and a test
asserts it.

## The gates run on the branch the work happens on (SH241)

Three workflows existed and none of them ran on a push: two were `workflow_dispatch`-only, and the
console check triggered on `push: branches: [main]` while the default branch is **`2026.3`**. **A
gate that does not run is a script** — and not hypothetically: picking up Block O's follow-ups meant
building the console, and it was already red, unreported.

SH579 fixed half of that before this task got to it. `suites.yml` runs `pnpm test` and
`pnpm conformance` on push and pull request. What was still dispatch-only is **the Java suite** and
**the console walk** — two of the three gates `agents.md` names.

### The Java job is the one only CI can give honestly

`./mvnw -pl shio-app -am clean test` from a clone. The reason it has to run here *as well as*
locally is SH282: without `clean`, a call to a method that no longer exists prints *"Nothing to
compile"* and BUILD SUCCESS, because the IDE compiled into `target/classes` on save and the `.class`
outlived the `.java`. `ShIdeCompiledClassLintTest` protects the person about to commit; **it cannot
protect the branch**, because a *stale but valid* class is indistinguishable from a compiled one. A
job that starts from a clone has no stale anything.

It publishes **surefire's own `Results:` line** as the run's headline, and `tee`s the log rather than
piping it. Both are SH283 and SH354 in the one place a number is read by somebody who did not run
the command: `target/surefire-reports/` accumulates, so summing it is wrong after any targeted run;
`-q` suppresses the only authoritative line; and a filter that reads the total discards the
`<<< FAILURE!` lines with it, leaving a red run that says which *count* and not which *test*.

### The console walk goes on pull request, not push

Deliberate, and the asymmetry is the decision. It boots a jar, provisions a site from a blueprint
and drives Chromium — the most expensive gate here. On every push to a branch somebody is iterating
on, that is the job people learn to cancel; a pull request is when "does the console still work" is
actually being asked.

### Asserted, because a trigger is invisible

`cli/test/ci-gates.test.mjs` holds the triggers: the default branch is named, all three gates have a
job that runs unasked, the console walk runs on a pull request, the Java job publishes surefire's
count and never uses `-q`, and **no workflow names a branch this repository does not develop on** —
the original defect was a *stale* branch name, which reads exactly like a configured one. Proved
non-vacuous by restoring `push: branches: [main]`, which fires two of the five.

### A non-vacuity floor must not be a number the corpus can fall below by being right

Three suites went red in one session for the same non-reason: shipping five tasks took the open
backlog from 24 to 19 while `improvements-debt.test.mjs` (twice, in two different assertions) and
`ShRoadmapLineLengthRatchetTest` all still floored at 20. Nothing was broken — the roadmap had done
its job — and each red cost a diagnosis before it could be dismissed.

A floor over a corpus that is **meant to empty** is a second deadline nobody declared. The ledger
only grows, so `shipped.size > 100` is fine; the roadmap and `IMPROVEMENTS.md` are not. Prefer a
**relative** invariant that holds at any size, including zero — `open.size >= pointed.size`,
`withIds.length >= pointed.size` — and keep an absolute only as `isNotEmpty`, which is the question
a non-vacuity check is actually asking: *did the reader read anything?* The shape assertions beside
it (this regex matches what roadkeep writes, and rejects what it must not) are what prove the reader
is correct; the count only proves it ran.

`cli/test/floor-kind.test.mjs` is what enforces this, and **it reads both halves of the repository
(SH738)**. It shipped reading only `cli/test/*.test.mjs` for the `assert.ok(x.length > N)` shape,
which left `shio-app/src/test/java/**` — where the governed-docs readers with the longest history
live — entirely outside it. SH737 then happened there, in exactly the shape above: a floor over
`IMPROVEMENTS.md`'s heading count, written at 50, lowered to 10, and wrong again at 10 when block
completions pruned the file to exactly that. The classifier did not miss it; it could not see it,
for two independent reasons — the tree, and AssertJ spelling a bound as a *method*
(`hasSizeGreaterThan(N)`, `isGreaterThan(N)`) that no `assert.ok` pattern can express.

Both are fixed, and the extension is asserted rather than assumed: `theWalkReachesTheJavaSuites`
runs the classifier over SH737's actual line and requires it to be flagged. Measured when the walk
was extended — 277 more files, five of which read a shrinking corpus, and **one** floor among them,
which counts the ledger and is therefore the honest kind. So the population it now reports is the
whole repository rather than the half somebody happened to write the reader in.

### A budget needs room under it, or it refuses instead of measuring (SH736, SH743)

A ceiling set at its own total does not size the next addition — it rejects it, whatever it is, and
the cheapest way to pass becomes deleting somebody else's payload, which is a non-goal by name. Two
gates now assert headroom rather than hoping for it:

| Gate | Rule |
|---|---|
| `cli-help-budget.test.mjs` (SH736) | `cli.help.all` keeps **4%** free; the trim that bought it took 9950 → 9475 |
| `ShAgentResponseBudgetTest` (SH743) | every `response.*` key keeps **4%** free, or is declared in `AT_THE_WALL` with the reason it cannot be trimmed |

The response side was measured at 13 keys, two of them at the wall — `manifest` 2636/2650 and
`manifest.check` 385/390, 99.5% and 98.7% — and nothing reported it. SH230 found them by reading
printed numbers while measuring something else, and `agent-surface.md` records an earlier round
closing at "one token of headroom" as though that were a result.

Both are **declared lists, never a slack factor**, for the reason every ledger here uses. And the
two exemptions are real rather than lazy: `format=json` is *defined* as the full manifest, so
trimming it is a P2 decision, and `include=features,limits` is already the smallest reading there
is. A third key arriving at its wall fails the build; an exemption that stops being tight also
fails, so the list shrinks rather than accumulates.

### The ledger and the suite can disagree in two directions (SH745)

`assertion-debt.test.mjs` **fails** on one: a task the ledger calls shipped that no suite asserts.
It now **prints** the other: a task the roadmap still calls open whose id the suite already names.

That second direction is what hid SH47 — implemented, proved by `ShStaticFileTenantIsolationIT`,
and left 💭 for blocks, found only when the backlog shrank enough that somebody read it.

**It prints and does not fail, and that is the design.** Failing would fire on a practice this
project requires: SH448's shape is to write the assertion *first* and red, and a defect closes with
the assertion that would have caught it. Both put an open id in the suite legitimately, and nothing
static separates "asserted and passing, so it shipped" from "asserted and failing, so it is being
worked on". The report demonstrated this on its first run by naming **itself** — SH745 was open and
the test asserting it was already written.

So read a line there as a question, not a defect: either `roadkeep ship` it, or it is red-first work
and there is nothing to do. What *is* asserted is that both readers found something, because a
report whose inputs came back empty prints a reassuring zero for ever.

**And it cannot catch the commoner case, which is worth knowing before trusting it.** Block F's
three SH22 follow-ups were filed in one batch against the code as it stood, and none was revisited
while the code moved past them: SH47 was fully built with an isolation IT proving it, SH46 was built
in the page header and missing only from the sidebar, SH45 had its enforcement half shipped. All
three were found by *reading the line*, and this report saw none of them, because it keys on ids the
suite asserts and none of those ids appeared in an assertion. **A batch of follow-ups filed against
one snapshot is the shape to re-read directly** — the instrument only covers the tasks somebody
already wrote a test for.

### A new ledger of what is knowingly wrong is a row, not a reader (SH741)

`known-debt.test.mjs` is the one reader over this repository's ledgers of accepted wrongness, and
its `LEDGERS` count is **asserted** so that inventing another without joining fails the run. There
are six: `open-findings.json`, `red-suites.json`, `assertion-debt`'s `DEBT`,
`docs-open-findings.json`, `verify-baseline.json`, and `floor-kind`'s `TOLERATED`.

The sixth is the case the assertion was written for and did not catch. SH672 built `TOLERATED` in
assertion-debt's shape and said so in its own header, and never joined — so for several blocks the
count read five while six existed, and `TOLERATED` had none of the checks the other five get. What
that cost is specific: an exemption whose floor was deleted or renamed stays forever, excusing
nothing, and never fires so never asks to be re-read. SH740 changed how that file reads sources
(comments stripped, where it had read raw), which could have killed entries silently; answering
"did it" needed a throwaway script.

A `TOLERATED` key names a floor rather than a task, so the owner-based checks declare
`ownerIsSubject` and a separate assertion asks the equivalent question: **does the floor this
excuses still exist?** It shares `cli/test/support/floors.mjs` with the lint rather than
re-grepping, which is SH583's rule — two implementations of "what is a floor" would agree only by
accident.

### A ledger's slice matches its bracket, and the same entry twice is how you find out (SH785)

Three of the seven rows above do not parse a JSON file — they slice a declaration out of a **source**
file, from `const X = [` to the next `];`. `DEBT` closes with `].sort(byId);`. So that search ran
three hundred lines past the end, swallowed `REVEALED` whole, and the row printed **144** for a list
of **120** — while `assertion-debt.test.mjs`, forty lines earlier in the same log, printed 120.
Two numbers for one list, in one run, for three blocks.

Nothing failed, and that is the shape worth remembering. The guard beside the slice —
`assertion-debt no longer declares DEBT as an array` — asserted `to > from`, and a scan looking for
*a* terminator always finds one: every source file below has a `];` somewhere. An overrun on a debt
ledger also fails in the safe-looking direction, reporting **more** debt than exists.

Two changes. `enclosedBy(text, "const DEBT = [")` **matches** the opening bracket instead of
searching for a string that closes it, and all three rows use it. And the assertion that would have
caught it is not about brackets at all: **no ledger may report the same entry twice**. A ledger holds
each thing once, so a duplicate can only be the reader's own overrun — whatever it swallowed past the
end is another list of the same kind of thing. Twenty-four of the 144 were an id already counted, and
the pre-fix scan reproduces all twenty-four.

The row also reads **both** of assertion-debt's declarations now. SH642 split one number into two
named lists — work with nothing behind it, and work whose test exists without naming the id — and the
assertion that holds the debt is over `[...DEBT, ...REVEALED_IDS]`. Reading only `DEBT` answers
SH594's question with two thirds of its subject missing, which is the same defect as reading too much
and merely looks more careful.

### Fixture ids live at SH9000+, and the range is reserved from both ends (SH787)

`assertion-debt`'s walk credits any `SH<n>` on a code line in a suite file. `"SH3"` in a fixture
array and `SH3` in an assertion message are the same eight characters, and **no reader can separate
them** — so the separation is reserved rather than detected.

**Write every fixture id at `SH9000` or above.** Two assertions hold it: a suite may only spell an id
the ledger knows or one in the reserve, and a governed file may never name an id in the reserve. The
second is what makes the first mean anything — reserving a range is a claim about the future.

The measurement is why this is a rule and not advice. SH786's fixture used `"SH3"`, and that went
red immediately because SH3 was *on* the debt list. Scanning for the general case then found **six
fixture ids in the SH9xx range across four files** — `red-suites`, `coverage-obligation`,
`known-debt` and a Java ratchet — all chosen when they were "far past anything real" and propagated
by copying, with the comment in one of them citing another as precedent. The ledger's highest id was
**SH788**. `SH900` was a hundred and twelve tasks away, and on the day it shipped it would have
arrived **pre-paid** by a fixture: asserted, off the debt list, and nothing anywhere saying so. No
care at the moment of writing SH900 would have caught that.

SH630's version of this rule — a hardcoded list of one file's fifteen illustrations, checked in one
direction — was folded in, because two readers of one idea agree only by accident (SH583) and the
hand-maintained list was the half that rots.

**The guard fired one command after it shipped, and its message named the wrong cause (SH790).**
SH787's own changelog sentence spelled the boundary in the obvious words. Roadkeep reads an
id-shaped token in a governed file as an id that is **spent**, so it derived the next task past it —
and that task was filed inside the range reserved twenty minutes earlier. The guard was right; the
text said *move the range up before allocating here*, and nobody had allocated anything. Following
it would have raised the reserve, left the sentence, and derived the next task past the new boundary
too.

So the message now computes the tell instead of assuming one. A roadmap or ledger **line** carrying
the id is `ALLOCATED` — raise `RESERVED_FROM` and move the fixtures — and an id that appears only
inside a **sentence** is `SPELLED`: reword the prose, leave the range alone. Both can fire at once,
which is what the incident actually looked like.

Two things worth keeping from the repair. Write a range boundary in words (*above the
nine-thousandth*) and not as a number, in anything roadkeep governs. And **`roadkeep renumber` moves
the line, not the section heading that repeats the id** — that needs `section-amend --title`, which
is the second command the incident needed and the one nothing had told anybody about.

### The other half of a bad slice is the one that stops short (SH786)

SH785 fixed a scan that ran past a declaration's end. The matcher that replaced it counts brackets,
and it counted them on the raw file — so every bracket in somebody's *explanation* of an entry moved
the depth too. **The measurement is the finding here**: `AT_THE_WALL`'s reasons carry **eleven pairs
of parentheses inside Java strings**, and the row's count was right because all eleven happen to
balance. Eleven-for-eleven, and one `(` in a new reason moves the end of the declaration.

`enclosedBy` now counts on `masked(text)` — comment lines and quoted spans blanked, same length, so
an offset found in the mask addresses the original. `code()` could not be used for this: it deletes
lines, and an offset into its result means nothing in the file.

**`masked()` blanks a trailing comment where `code()` deliberately keeps one**, which is not a second
opinion about what a comment is — SH740 exists to prevent that — but the two having opposite
conservative directions because they are asked different questions. `code()` looks for a **claim**,
so keeping a trailing comment errs towards crediting a payment rather than inventing a debt.
`masked()` looks for **structure**, and a bracket in a trailing comment is not structure. Blanking
the strings first is what makes it safe: a `//` inside a string is already gone.

The assertion carrying the id is a declaration built to break it — three unbalanced `]` in prose,
and a second list after it so a stop-short and a run-past are distinguishable in one check. Unmasked
it yields one id; masked, three. A second test re-takes the eleven-parenthesis measurement on the
real file, so the day the mask stops being load-bearing it says so rather than guarding nothing.

**Write fixture ids in the `SH9xxx` range.** This one first used `"SH3"`, and `assertion-debt` read
that as an assertion carrying SH3, credited it, and went red saying somebody had paid a debt off. A
real id inside an assertion message *is* a payment to that reader.

### A JS scan over this repository declares what it reads (SH740)

SH613 gave the Java suites `@ShSourceScan` + one shared `ShSourceText`, because a textual guard
cannot tell a rule from a sentence about the rule. The JS half had four private ideas of what a
comment is and three guards were tripped by prose about their own rule — SH722's message citing
SH322, then twice in one session while fixing that.

`cli/test/support/source-text.mjs` is now the one answer and `cli/test/source-scan.test.mjs` the
lint. A scan declares `// @scans code`, `prose` or `both`; `code` is held to using the shared
stripper, and `prose`/`both` need a written `reason:` because an accident cannot explain itself.

Three strippers, not one, and that is the part that is **not** a port of the Java rule. `code()`
drops whole-line comments, line-oriented and stateless so a regex literal cannot derail it
(SH630). `masked()` blanks the same spans **in place** — same length as the input — for a reader
that needs a *position* rather than text (SH786, below). `unquoted()` is for Markdown, where prose is the whole document and "strip the comments"
means nothing: it drops fenced blocks, inline code spans and quoted phrases, because when a
document is asked *do you claim X*, a phrase it has put in quotation marks is a quotation. That is
what lets the sweep checklist quote the two false sentences it replaced instead of paraphrasing
them to protect a counter. It matches within a line only — a quotation broken across a line break
is not stripped, so wrap a quoted claim onto one line.

**And `absent-claims.test.mjs` keeps a fourth, which is correct and was nearly "fixed" (SH822).**
It looks like a copy of `unquoted` with the newline guard missing, and that reads as a defect: an
unpaired quotation mark running away. The two take **different input**. `unquoted` reads a
*document*, line by line, where the guard is what stops one stray quote swallowing a page. That
one reads a *sentence*, already split on a full stop or a blank line, where a quotation
**legitimately wraps** — the advice above ("wrap a quoted claim onto one line") is advice to
whoever writes the doc, and a scan cannot assume it was followed. Excluding the newline there
breaks it, and the sentence boundary is what bounds the damage.

Measured, because the difference is one character and invisible: swapping in `unquoted` makes that
scan report **three more claims**, every one a wrapped quotation — `content-api.md` quoting SH137,
`docs-process.md` quoting the sentence that cost SH694, and that file's own list of the phrasings
being matched. Each would be filed as a standing absence claim somebody has to sign. A test pins
both directions rather than a comment asserting them, and the general rule is worth more than the
instance: **before merging two functions that look identical, ask what each is handed** — these
differ only in the input's shape, which no amount of reading the bodies reveals.

#### The console's copy was the other kind of stripper, and it deleted code (SH1118)

Everything above is the **CLI** side, where SH630 had already established the line-oriented rule.
The console's `shio-react/test/support/source-text.mjs` was a regex over the whole file —
`source.replace(/\/\*[\s\S]*?\*\//g, "")` — which opens on a slash and two stars **anywhere**,
string literals included, and closes on the next star-slash. Thirty-one suites across two packages
read source through it, and any one of them could be silenced by a string in a file it does not own.

**The measurement came first and it is the reason the design's own fix was not taken.** Across this
repository's JS sources: **19 spans, 77,285 characters** removed by a strip that began mid-line.
`cli/test/clone.test.mjs` hid **656 lines** behind a `robots.txt` fixture's `Disallow: /*`;
`theme.test.mjs` 508 behind a CSS selector; the console's own `routes.const.ts` lost its route table
to `` `${consolePath}/*` ``. §LXIV.19 proposed throwing when the strip *removes too large a
fraction*, and the same walk refutes it: files here are legitimately up to **91 %** comment
(`app/routes/index.ts`, `theme.ts`, `index.css`), so any ceiling low enough to catch the defect
rejects the honest files. What separates them is not how much was removed but **where the removal
began**, which is decidable per line. A cost that is argued rather than measured fails the same way
in somebody else's tree.

So the console's copy now reads the way the CLI's does — an opener counts at the start of a line
(`/*`, `/**`, `{/*`), a `/*` anywhere else is a trailing comment or a string, and the two are told
apart by blanking the line's quoted spans first. Two details that are not obvious and were each
measured: a line that *is* a comment is **not** quote-blanked, because an apostrophe pair in prose
would swallow the closer and the comment would read as unclosed; and a `/* c */ code` line keeps the
code after the closer, which the first attempt dropped and `fetch-cancellation.test.mjs` caught in
one run, since its planted file is exactly that shape.

**One case stays genuinely undecidable, and it throws.** An opener at a line start that never
closes means the file ends inside a comment, and returning the tail as an empty string is agreeing
with every assertion over it. That raises an error naming SH1118 and the line, because *a reader
that cannot see the file should say so rather than agree with everything.*

**And ten call sites wrote those two lines, not one — which is the part worth keeping.**
`cli/src/theme.mjs` already carried a comment explaining in as many words why the pattern is wrong,
and the safe reading was already exported beside it. The knowledge was in the tree and nine readers
did not have it. A silenced scan is invisible by construction, so nothing could count them from
inside a suite the silence had already made green: `cli/test/comment-stripper.test.mjs` is that
count, printing it on every run and holding the **six** that remain as a named list under SH1147
rather than a ceiling, because a ceiling lets one task pay it down and the next spend it. Verified
in both directions — a planted seventh copy fails it, and the two assertions in
`shio-react/test/source-text.test.mjs` both fail against the body they replaced.

**SH1147 emptied the list, with a third reading rather than either of the two.** All six stripped
line comments as well as block ones, and neither existing CLI reading fits: `code` keeps a trailing
`//` comment, which a scan asserting an absence can trip on, and `masked` blanks strings, which is
where `flag-meaning` finds flag names. So `cli/test/support/source-text.mjs` exports `uncommented`:
per line (SH630), whole-comment lines dropped, a trailing `//` cut only outside a quote and after
whitespace, so `http://` and a regex literal's `\/\/` stay. The five CLI scans read through it and
all stayed green on text the old strip had been deleting. The console's `surface-digest.mjs` is a
build script and keeps a reading of its own with the same rule, proved by its output: byte-identical
over the console's pages before and after. `DECLARED` is `{}`, kept as a declaration.

The population is **files that scan this repository's own source or docs**, not every suite that
reads a file: `"foo.mjs"` is usually a fixture filename in JS, and taking every read gives 66,
mostly temp trees a suite wrote itself. A suite that reads back a tree it just wrote cannot be
tripped by prose. The count and every file's mode print on each run.

**How that population is found is structural, and SH742 is why.** The first rule asked whether a
file named `ROOT`, `REPO`, or a two-level relative URL — a naming convention, deciding membership
by how a constant is spelled. It found 15 and missed 9, and the misses were the worst available:
`docs-vocabulary`, `docs-coverage` and `docs-examples`, the three readers that scan the *published
documentation*. The question now is whether the file names a path **inside this repository** — a
repo directory, a governed Markdown file, or `SHIO_DOCS_DIR` — which a fixture read cannot, since
its path is built at runtime. That is 23, gaining all 9 and losing none.

Two traps that cost a run each, both worth knowing before editing this:

- **The population map is keyed by repo-relative path, never basename.** There are two
  `marketplace.test.mjs` (`cli/test/`, `shio-site/scripts/`), and a basename key silently
  overwrote one with the other — so a declaration added to the hidden one had no effect and the
  file it hid was never asked for one.
- **A declaration is a comment, so where it is inserted matters.** Adding one "after the last
  `import` line" put it inside an embedded Python heredoc in `shio-site/scripts/marketplace.test.mjs`
  — the last `import` in that file is `import json, sys, zipfile` inside a template literal — and
  broke the test. Put it at the top of the file, with the header.

### The console lint is a CI check, not a fourth gate (SH665)

`pnpm lint` runs in the `pnpm test` job and is **not** one of the three SH579 gates: it has no
`red-suites.json` ledger and a red lint is not a red suite. It gates in that job rather than beside
it so a broken lint cannot hide a broken suite behind one red tick.

It had not run since ESLint 10. `shio-react/eslint.config.js` extended
`eslint-plugin-react-hooks@7.1.1`'s preset, whose flat config still spells
`plugins: ["react-hooks"]`; ESLint 10 rejects an array there **before reading a rule**, so `eslint`
exited 2 on **every** file — including ones nobody had touched — which reads as a config error
rather than a finding and is easy to walk past. Nothing in CI ran it, so there is no way to date
when it broke.

**Registered, not pinned.** 7.1.1 is the newest stable and every release past it is a canary, so
there is no version to move to; the config declares `plugins: { 'react-hooks': reactHooks }` and
takes the *rules* from the preset, which keeps tracking the plugin's recommendation. When a release
ships the object form this collapses back to `extends`.

**Green today, red on anything new.** Repairing the config surfaced **36 findings in 29 files** —
15 `set-state-in-effect`, 14 `only-export-components`, 1 `exhaustive-deps` — of which one was a real
defect and is fixed (`use-discovery.ts` had a `let alive = true` with no cleanup returning it to
false, so a response arriving after unmount still called `setState`; `prefer-const` was the tell,
and `--fix` would have silenced it by making the flag `const`). The remaining **33** are in
`shio-react/eslint-suppressions.json`, ESLint 10's own baseline: a named list rather than a ceiling,
and `--prune-suppressions` removes an entry once its finding is fixed, so it only shrinks. Proved in
both directions — a new `prefer-const` violation in `src/lib/utils.ts` takes the run to exit 1, and
removing it returns it to exit 0 with the 33 still suppressed. The two remaining **warnings** are
visible and do not fail: `--max-warnings` is left off deliberately, since any number there is the
ceiling this baseline exists to avoid.

## Command-line properties were dropped without a word (SH680)

`Shio.main` built a `SpringApplication` and called **`application.run()`** — no `args`. Every
`--key=value` on the command line was dropped, and dropped *silently*: no warning, no log line, and
the instance starts normally on the defaults.

Measured, before and after, with the most universal thing anyone does with a Boot jar:

```
java -jar target/viglet-shio.jar --server.port=8099

before:   2710=200  8099=000     (and `server.port` appears 0 times in the startup log)
after:    2710=000  8099=200
```

It was found sideways, while verifying SH676: an instance pointed at a local catalogue with
`--shio.marketplace.enabled=true` reported *"shio.marketplace.enabled is false"*. The property was
right and the value was right, and nothing said the argument had been ignored — **the failure is
indistinguishable from the feature not working**, which is what cost the session rather than the
wrong port.

The workaround that did work — environment variables, which bind through relaxed naming — is one
nobody has cause to look for while the flag they typed appears to be accepted.

### Nothing depended on the broken behaviour

Checked in the same pass, as the task asked. `Dockerfile` and `Dockerfile.local` configure through
`JAVA_OPTS`, `-D` system properties and environment variables; no deployment artefact in the tree
passes a `--flag`. So this widens what works rather than changing what already did.

### The guard is a lint, because no unit test naturally covers it

The fix is two characters and the argument-less form **compiles, starts, serves every request and
passes every suite here**. It is a property of the process boundary, and the only behavioural test
would be launching a JVM and reading a port — a slow, flaky assertion about Spring rather than about
this code.

So `ShBootstrapArgsLintTest` reads the source and requires that a `run(` inside a
`main(String[] args)` carries them. It reads the parameter's declared name rather than assuming
`args`, and a second test pins the exact pair of lines that shipped so the rule cannot silently match
nothing.

## What the console costs to load, measured at last (SH1043)

The agent's cost is governed to the endpoint: `token-budgets.properties` holds a ceiling per
response, a raise carries its reason, and P3 makes a regression a build failure. The half of the
product a **person** waits for had no number at all — the three gates are the Maven suite, the JS
suite and conformance, and none of them looks at a byte. That asymmetry is how a 4.8 MB vendor chunk
came to be preloaded on every console route with nobody noticing.

**Measured first, and the measurement is what made the task worth doing.** On this tree the console
loads **7,473,483 bytes raw / 2,200,187 gzip** before the first row of content, and `vendor-graphiql`
is 4.8 MB of it — preloaded on every route, for one page whose own chunk is 3 KB. That is SH1044,
and it now has a number to argue against rather than an impression.

- `pnpm --filter shio-react run size` prints the report; `console-size-budget.json` holds the
  ceilings and a sentence per chunk; `test/console-size-budget.test.mjs` is the gate.
- **It reads the bundler's own answer.** Which chunks are *eager* is a question about the static
  import graph and Vite has already answered it: the entry is the `<script type="module">` and every
  statically imported chunk gets a `<link rel="modulepreload">` in the emitted HTML. Parsing the
  chunks' own `import` statements would be a second implementation of that analysis, disagreeing
  with the first on the day a bundler option changes — and the browser reads the same file.
- **A build with no preload links is refused rather than measured.** Turning `build.modulePreload`
  off would otherwise leave the reader measuring the entry alone and passing every ceiling forever:
  the report-zero shape, reached by a config change nobody connected to this gate.
- **Ceilings are keyed by the chunk's stem**, not its hashed filename, or every edit would make the
  budget stale. Four kinds of breach are reported, and the last two are the ones a plain total
  misses: a new eager chunk the budget does not name, and an entry that is no longer eager — so a
  ceiling cannot outlive the thing it was about, which is exactly what SH1044 is about to do to two
  of them.
- **The comparison needs a build and `pnpm test` does not make one**, because `vite build` is a
  minute against a suite that runs in eight seconds. So it measures wherever a build exists and says
  out loud when there is none — "no build in this tree" and "the console got smaller" must never
  read the same. What runs on **every** run is the planted build: a synthetic `dist` drives the
  reader and the comparison end to end, so the gate has been shown to fail rather than only ever
  having been run against something that satisfies it.
- **The seed has ~4% of room under it, and a test asserts that it still does** (SH736's lesson one
  gate over): a ceiling set to the measurement refuses the next change instead of measuring it, and
  the cheapest way to pass then is to delete somebody else's feature — the degrade-the-measurement
  non-goal by name.

## A budget per route, and the two things measuring it refuted (SH1113)

SH1043 above measures **one** number for the whole console: what every route pays before the first
row. That is the right shape for a bundle and the wrong shape for what a curator experiences, which
is a *page*. A route that fetches three collections to draw two figures costs exactly the same eager
bytes as one that fetches nothing, so that gate reports it as free — SH1050 was that page, and
nothing would have caught it.

The instrument was nearly there: `e2e/console-check.mjs` already drives a browser through every
route against a live instance, which is the only place a page's own requests are observable. So it
records three numbers per route, `console-route-budget.json` holds the ceilings the way
`console-size-budget.json` holds its own, and `scripts/route-cost.mjs` is the comparator.
`--record-cost` seeds the file from a run; `test/route-cost-budget.test.mjs` is the half a
`pnpm test` can hold, and it carries the design's own assertion: **a planted extra fetch on one
route fails that route's entry and no other.**

**Two things the first measurement refuted, both of them in the first implementation rather than in
the design.** This is the part worth reading, because both were invisible until real numbers
existed:

- **Counting every same-origin request re-measured the bundle.** The eager assets are ~1.73 MB on
  every route, so the post editor's own reads showed up as 1,747,000 against a site list's
  1,730,000 — a 20 KB signal under a 1.7 MB constant, which is the *same* "a total hides a page"
  failure the task was filed about, reproduced one level up. It would also have failed this gate
  every time the bundler regrouped a chunk, a regression SH1043 already owns: two gates answering
  for one number, which is what P10 forbids. The filter is now the **data plane** —
  `/api/v2/**` and `/graphql` — and what is left is small, stable, and exactly the sentence the
  design wrote. The static assets are not unmeasured; they are measured by the gate whose subject
  they are.
- **A single run cannot establish a tight time ceiling.** Across 52 families one seeded at **26x**
  the baseline and several neighbours at 1.0x, on pages differing by one list read. So `firstRow` is
  a deliberately **coarse** guard (`FIRST_ROW_SLACK`, with a floor) that catches a page an order of
  magnitude worse and nothing finer. A tight ceiling seeded from one run is a flaky gate, which this
  project treats as worse than no gate — it is the one people learn to re-run past.

**The three numbers are therefore held three different ways, and the differences are the design:**

| Number | Held | Why |
|---|---|---|
| `requests` | at the measurement, **no headroom** | Discrete and deterministic — the page either asks for the catalogue or it does not. An extra fetch is the defect this exists to catch, so room in the ceiling would let the first one through, and the first one is the only one anybody would notice. |
| `bytes` | with ~8% of room | Payloads grow with the content on an instance; a ceiling at the measurement refuses the next change instead of measuring it (SH736). |
| `firstRow` | a **ratio** against a named baseline route, coarsely | Milliseconds move with the machine, so a ceiling in them is one nobody can reproduce and therefore one that gets raised until it means nothing. |

**What the count leaves out, and where it gates (SH1171).** `/api/v2/events` is not counted. Every
screen opens SH968's shared stream and SH969's bounded replay, and whether either was counted
depended on when it opened: the 404 page read 7 in the seed and 9 in a later run of the same code.
A held stream is a connection, not a read, and its cost has its own budget (`events.idle`, SH967).
The gate also fails **only against the jar**. The ceilings are a production-build measurement, and
under the Vite dev server `React.StrictMode` runs every effect twice: every family came back over by
about 6 requests and 16 KB. A dev run prints its findings as notes and exits clean, the way SH927
treats dev-only noise. To gate, run `--base http://localhost:2710`.

**Two asymmetries that make it runnable on a thin instance without being toothless on a full one.**
The route list is *discovered* from the instance — one post editor per post-type it finds — so the
key is the **family** (the name up to its first colon): `post-edit`, not `post-edit:Page`, which is
also the better claim, since the second is a fact about somebody's content. And a **walked family
with no entry is a finding** while an **entry nothing walked is only a note**: a new screen must
declare what it costs, but "this instance has no posts" is a missing fixture and not a regression.
Where a family has several members the **costliest** is compared — a ceiling tracking the cheapest
passes on the slow page, which is the same mistake one level down again.

**A route is costed when it put a first row on screen — not when its probe passed.** Both halves
were bought. The dead page has to be excluded: one that died on navigation made whatever requests it
got to first, so costing it would teach the budget that a broken page is a cheap one and then fail
the day somebody fixes it. But `probe.ok` was the first predicate and it was **wrong, and flaky with
it**: a probe fails for reasons that say nothing about cost — a missing frost surface, a `mustSee`
string, a hero heading — and which of those fired moved across three consecutive runs against one
instance (41, 45 and 46 of 57 probes). The costed set moved with them, so families appeared and
disappeared from the comparison and the gate reported as unbudgeted a route a previous run had
costed. A page that rendered its rows and then failed an assertion about its chrome made exactly the
requests it always makes. `cost.ms != null` is the stable predicate and already excludes the page
that rendered nothing.

**Two numbers out of the first run are worth more than the gate.** Across 43 costed families:

- `unknown-route` — a path the console does not have, rendering a 404 — still makes **7 API calls**.
  That is the shell's own cost, paid by every route, so an entry at 8 is *one call of its own* rather
  than eight. It is also the file's own guard: a 404 has no data of its own, so if that entry is ever
  megabyte-sized, `DATA_READ` has stopped filtering and every number here is measuring the bundle
  again. `route-cost-budget.test.mjs` asserts it rather than asserting a blanket ceiling, and the
  blanket was the first draft — see the next point for what refuted it.
- `post-edit` makes **23**, so sixteen are its own, against a site list's 8. That is the ceiling this
  budget most exists for, and it is the gap the first implementation reported as 1,747,000 against
  1,730,000.
- `graphql` is the one entry measured in **megabytes** — 1.13 MB, all of it the schema introspection
  the explorer fetches before a curator types a character. It is a genuine data read, not the bundle
  leaking back in, which is why the blanket `bytes < 1 MB` assertion had to go: it would have failed
  a real finding as a defect in the instrument. Held rather than fixed here, so shrinking it becomes
  an argued change rather than a discovery.

**Where it runs, and one position that was bought.** It rides `console-check.yml`, which already
boots a jar, provisions a site and drives Chromium on every pull request — no second workflow, and a
regression therefore blocks a merge rather than a commit. The cost record is written **the moment
the route loop ends**, not at the end of `main`: the first recording run walked all fifty-seven
routes and then died on a 401 in a later probe, so nothing was written and twenty minutes of
measurement went in the bin. Nothing after that line costs a route, so nothing after it can be
allowed to lose what it measured.

## The chunking rule that caused what it was written to prevent (SH1044)

`vite.config.ts` carried a `manualChunks` function whose own comment said what it was for: keep
GraphiQL, Monaco and CodeMirror out of the eager bundle, *"since [the vendor-lib rule] matches every
node_modules id"*. **It did the opposite.** A manual chunk is a chunk the bundler must place, and
under rolldown a named vendor chunk becomes a static dependency of the entry — so `index.html`
preloaded `vendor-graphiql` on every console route, for a page whose own chunk is 3 KB.

**The first attempt was the one §LXXII.2 proposed** — give CodeMirror its own chunk, matched ahead
of the GraphiQL rule, because the Ace widget imports CodeMirror and the widget map names that widget
eagerly. It moved the total by **4 KB**, and that is the finding: the ordering was a real defect and
not the cause.

| | raw | gzip |
|---|---|---|
| manual chunks, CodeMirror folded into `vendor-graphiql` | 7298.3 KB | 2148.6 KB |
| manual chunks, CodeMirror given its own vendor chunk | 7293.9 KB | 2146.2 KB |
| **no manual chunks at all** | **1506.4 KB** | **403.5 KB** |

Two changes ship together and both are load-bearing. **Deleting `manualChunks`** lets the bundler
put each editor behind the dynamic import that reaches it: Monaco (2.8 MB) and the GraphiQL page
(678 KB) behind the lazy route. **Making the Ace widget lazy** keeps CodeMirror (573 KB raw, 197 KB
gzip) out of the entry — without it the widget map still names the editor directly, and the map is
imported by every screen that draws a post's fields. The split is per *field kind*, which is the
only place the question can be answered: the editor arrives when somebody opens a `PageLayout`,
`Region` or `Theme`.

Three things worth keeping from it:

- **A rule stating an intention was believed for as long as nothing measured it.** The comment was
  wrong from the day the bundler changed under it, and read correctly the whole time.
- **Hand-grouping cost more than bytes.** The SH1017 CodeMirror version conflict surfaced as a dead
  editor in a chunk named `vendor-graphiql`, so it read as a GraphiQL fault for as long as it did
  (`codemirror-singleton.test.mjs` records that).
- **The `@radix-ui` → `vendor-ui` rule produced no output file**, which is SH1045's subject seen
  from the build side: nothing in the console imports Radix directly any more, so the rule named a
  chunk that never existed. It went with the rest.

SH1043's gate is what made this checkable, and it did two jobs here: it priced the before and after,
and it **refused the stale entries** — a ceiling for a chunk that is no longer eager fails, which is
why `vendor-graphiql.js`, `vendor-graphiql.css`, `vendor-codemirror.js` and `vendor-lib.js` are
deleted from the budget in the same commit rather than left as numbers nobody was measuring.

## Eighteen declarations nothing reached, and the walk that found them (SH1045)

SH1043 priced what the console *loads* and SH1044 spent that price. This is the same argument one
level earlier, about what it **declares**: `shio-react/package.json` listed sixty dependencies and
eighteen of them were reached by nothing. A package nobody imports ships no bytes and costs anyway —
installed on every CI run, pinned in the lockfile, audited, renovated, and read by the next person
as evidence that the console uses it.

They were not random. The shadcn layer under `src/components/ui/` is now a **re-export of the design
system** (`export { Dialog, ... } from "@viglet/viglet-design-system"`, and `lib/utils.ts` re-exports
`cn` the same way), so the twelve `@radix-ui/react-*` packages, `radix-ui`, `clsx`, `tailwind-merge`,
`class-variance-authority`, `@tanstack/react-table` and `@tailwindcss/typography` are the previous
implementation's dependencies outliving it. The design system declares them itself, so pnpm installs
them under its own tree and nothing breaks; SH1043's gate measured the eager load at **1506.4 KB raw
/ 403.5 KB gzip before and after**, which is the right record for a removal that touches no import.

**Four ways to be reachable, and only one is an import.** `scripts/dependency-reachability.mjs`
(at the repository root since SH1114; it lived under `shio-react/scripts/` while the console was its
only subject) classifies each declaration as imported by a package's own files, required as a **peer** by
something already reachable, run as a **binary** by a `package.json` script, or the `@types` of
something reachable. The three that are not imports are why this can be trusted to delete: a grep
for the name would have taken `sonner` and `next-themes` (peers of the design system, imported
nowhere here), `typescript` (reached as `tsc`), and `tailwindcss` itself (reached from CSS by
`@import`, which a JavaScript-only scan does not read).

Two details the reader is wrong without:

- **The peer pass runs to a fixed point over the *reachable* set**, never over everything declared.
  A package that is only the peer of an unreachable package is unreachable too — otherwise one dead
  entry keeps the next one alive and the list can never shrink.
- **The CSS at-rules are scanned** (`@import`, `@plugin`, `@config`, `@source`). Tailwind v4
  registers a theme and a plugin from CSS, so a package can decide what the console looks like and
  appear in no `.ts` file at all. `@tailwindcss/typography` was deleted precisely because no
  `@plugin` line ever registered it, which means it was not loaded either.

`shio-react/test/dependency-reachability.test.mjs` is the console's gate and
`pnpm --filter shio-react run deps` prints its inventory. Exemptions live in the script's `KEPT` map
with a sentence each, and it is **empty**: everything unreachable was deleted rather than excused. A
stale exemption fails too — for the reason SH1043's budget fails on a ceiling that outlives its chunk.

## The same walk over all eleven manifests, and the two breaches only npm sees (SH1114)

SH1045 above read one manifest and left ten unread, and the reason that is worse than "ten more of
the same" is where the others end up. **Seven of the eleven packages are published**, so a dead
dependency there is not merely installed on every CI run here — it is downloaded on every consumer's
install. (§LXXII.20 estimated four. `private: true` is what decides, and only `shio-react`,
`shio-site`, `claude-plugin` and the two examples carry it; the list is asserted rather than
remembered, because which packages are published is what decides where these rules apply.)

The reader was written to be lifted, so the work was turning two console-specific constants into
`PACKAGES` — one declaration per package: `walk` (every directory whose files can reach a package),
`root` (configs that import from outside a walked directory), and `shipped`.

**`shipped` is the half that earns the task**, and it only matters for a published package: *which of
the walked directories a consumer's install actually runs*. Without it two breaches are invisible
here and break somewhere else, and neither is what SH1045 looks for:

- a **`dependencies` entry reached only from code npm does not pack** is a download on every install
  for a file the consumer never loads. It is not dead — the walk reaches it — so SH1045's rule is
  satisfied by it;
- a **`devDependency` reached from shipped code** is quieter and worse: it resolves here, because the
  whole workspace is installed, and throws on the consumer's first import. Every gate in this
  repository passes.

It is a declaration rather than a derivation from `files`, because `files` names build output for half
these packages — `shio-model` ships `dist/`, which is bundled and not walked, and the source behind it
is `src/`. So `shipped` says which *walked* directory stands behind what npm packs, and
`shippedAgreesWithFiles` checks it against the manifest so the two cannot drift: a package that starts
packing `test/` fails rather than going on being described as test-only.

**Two corrections the first run bought, both of them to the new rules rather than to the code.**

- **A peer that is also a devDependency is correct, and the rule called it a breach.** A React library
  declares `react` as a peer — the consumer supplies it — and as a devDependency so it is installed
  here to build and test against. `shio-sections` and `shio-react-sdk` are both in exactly that shape
  and were both reported. SH1045's own note is that an unused-dependency check gets turned off after
  its first false positive; this one had one waiting on day one. The exemption is asserted against the
  two real manifests, so it cannot outlive its reason.
- **A packed directory carrying its own `package.json` is a payload, not unread source.**
  `create-shio-app` packs `template/`, whose imports resolve against *that* manifest once a consumer
  scaffolds with it, never against this one.

`cli/test/workspace-reachability.test.mjs` is the gate and `pnpm run deps` walks all of them. It
holds the design's assertion — a dead dependency planted in a package **other than the console**
failing **by name**, driven against each of the eleven declarations rather than reasoned about once,
because what failed before was never the rule but that only one package was ever asked. `PACKAGES` is
checked against `pnpm-workspace.yaml`, which is the guard that would have caught SH1045's own gap: a
reader with a hand-kept list passes for the wrong reason the moment the workspace grows a package it
does not name. The workspace's real run classifies **99 declarations across twelve packages with zero
findings**, and the count is asserted with a floor — 43 of those were SH1045's one package, so a
number near that would mean the other manifests are being read as empty.

## The docs gate stays a warning locally, and it was 135 commits unrun (SH1070)

`pnpm gates` found `docs` 135 commits behind, and running it turned up two drifts SH628 and SH653
exist to catch: `developer-guide.md` stated Spring Boot 4.1.0 against the pom's 4.1.1, and
COVERAGE.md's summary disagreed with its rows. Both were corrected in vigletdocs. The summary was
fixed by the docs pass that is still uncommitted there.

The task asked what stops a third. The answer was **already in the tree**, so nothing was built:

- **CI runs it on every push** (`suites.yml`, SH655) and clones vigletdocs to do it, so a clone
  with no sibling checkout is handled where it matters.
- **Every `pnpm test` prints it**: SH755's table names each gate, and SH841 marks a stale one whose
  subject has changed since as `OWED: N commit(s)`. The 135 was that line, read.

Making `docs` block a local commit was rejected. Its subject is another repository, and SH771
already decided that a red which only a second repository can fix goes stale on the next ship
rather than gating it. When a run *is* fresh and red, SH755 fails `pnpm test` until
`red-suites.json` declares it, so a red somebody has seen cannot stay quiet.

## Casing names its locale (SH1085)

A `toLowerCase()` or `toUpperCase()` with no argument reads `Locale.getDefault()`. On a Turkish JVM,
`"PUBLISH"` then becomes `"publısh"`. That was the `event` field of every signed webhook, which the
`create-shio-app` receiver matches byte for byte, and it also broke the friendly URL a title mints
and the slug an export and an import each derive. `ShLocaleCasingLintTest` fails on any bare casing
call in `shio-app`, and every one found so far is `Locale.ROOT`: none of them is text for a person
to read. The task counted nine and the scan found eleven. The lint's third test sets a Turkish
default and asserts the webhook's wire value and a minted URL are unchanged.

## A pin only this machine could resolve (SH1076)

SH110 raised `<viglet-core.version>` to 2026.3.11, a version only a local `~/.m2` held because
viglet-core is built on this machine. Every local gate passed; every CI run failed before the
reactor — `Non-resolvable import POM`, no test count, no failing name — for a month. The pin is now
2026.3.12, published to Maven Central.

The instrument is `scripts/check-published-deps.mjs`, the first step of the Java job: it reads the
pin from `pom.xml` and asks Central for that BOM (`HEAD`), failing with the version, the URL and the
fix. It needs the network, so it is never part of `pnpm test`; `ci-gates.test.mjs` asserts the step
exists and runs before the suite, and that the reader still finds the pin. **Before bumping
viglet-core, run `node scripts/check-published-deps.mjs`** — a local build proves nothing about
what CI can download.

## The console's cost, from the instance that serves it (SH1112)

SH1043's number had two readers, `pnpm --filter shio-react run size` and the build gate, and neither
was reachable from an instance. `GET /api/v2/agent/manifest?include=console` now answers it:
`ShConsoleCost` reads the `index.html` the instance serves from `classpath:public/`, takes the entry
module, every `modulepreload` and every stylesheet — the script's three patterns — and reports each
asset and the totals, raw and gzipped at level 9, computed once. The section is **opt-in**, like
SH979's `surfaces`, so no ordinary manifest read pays for it; an instance started with
`-Dskip.npm=true` serves no console and answers no section rather than a zero.

The ceiling travels with the build: `vite.config.ts`'s `shio-console-budget` plugin emits the
`eager` and `chunks` ceilings of `console-size-budget.json` beside `index.html`, so the answer
carries `ceiling` next to the number it is judged against.

**Raw is exact and gzip is within 1%.** `src/test/resources/console-cost/` is a small built console
and `expected.json` is what the script measured on it; `ShConsoleCostTest` and
`shio-react/test/console-cost-parity.test.mjs` hold both readers to that file. Raw bytes match to
the byte. Gzip does not, and cannot be made to: Node ships Chromium's zlib and the JVM its own, and
neither promises the same stream at level 9 — measured on the fixture, 9787 against 9726 (0.6%). A
comparison against the build gate's ceilings should use `raw`.

## A size verdict about somebody else's build (SH1126)

SH1043's comparison runs against whatever console build sits in
`shio-app/src/main/resources/public`, and **`pnpm test` never built one**. So the gate measured a build
of *some* tree with nothing saying which — and it went wrong in both directions, on consecutive tasks:
one passed `pnpm test` against a build that predated its own change, and the next failed the headroom
check only because a build made outside the session happened to include it. Neither verdict was about
the bytes the task under test had produced.

The conformance gate had the same shape and SH460 closed it there — the instance states its build time
and commit, and the harness refuses one older than HEAD. A console build carries the same identity now:
a Vite plugin emits **`build-info.json`** beside `index.html`, holding the commit, the build time and a
digest of the sources the bundle was built from.

**The digest is the comparison; the commit is for a person.** A commit cannot be compared against a
working tree — the tree is dirty most of the time anybody runs this, and refusing a dirty tree would
refuse every build made while working. The digest is over file contents, so it matches a dirty tree
exactly when the build came from it. The commit is recorded because it is what a reader wants when the
digests differ and they are working out which build they have.

`SOURCE_INPUTS` is the decision: `src`, plus `index.html` (the entry), `vite.config.ts` (the chunking)
and `package.json` (the dependency names). §LXXII.23 asked for `src`; the other three decide what the
bundle contains. **The stated limit:** it does not cover the installed dependency *bytes*, so a
`pnpm update` that changes a vendor chunk without touching any of these leaves the digest matching.
That is the right trade — the failure this closes was seen twice and both times it was a source edit,
and hashing `node_modules` would cost more than the build it protects. The lockfile is out too: it
moves on an install that changes nothing the bundle sees.

**Three ways to be "not measured", one outcome.** No build, a build from before `build-info.json`
existed, and a build from another tree all produce no total, because a number from a different tree is
worse than no number. What differs is the sentence, and each names what to do. One function
(`buildProvenance`) decides, so the three cannot drift apart.

**A stale build must not fail the suite**, or every source edit would demand a build before
`pnpm test`. So locally it is a diagnostic — which would leave the number real only where somebody
happened to build. Hence the other half: CI's `js` job now runs `pnpm run build:console` (~10s against
a 20-minute budget) and sets `SHIO_REQUIRE_CONSOLE_BUILD=1`, which turns "not measured" into a failure
there. `ci-gates.test.mjs` asserts both halves *and their order* — a build after the suite measures
nothing, and the two lines look equally correct in a diff.

One shape worth knowing: `scripts/console-build-info.mjs` stays `.mjs` with a hand-written
`.d.mts` beside it, because `node --test` runs the suites with no build step while `vite.config.ts` is
type-checked by `tsc -b`. Moving it to TypeScript would mean compiling it before a test could import
it, which is the build-before-you-can-measure problem this task exists to remove.
