# Build and deploy

> Split out of the repo-root `agents.md` (see its routing table for the index).
> Read this before touching the area it covers. Detail here is authoritative.

## A red suite is a stop (SH579)

**A suite that is red either blocks the next commit or is a declared, dated exception in
[`red-suites.json`](../../red-suites.json). There is no third state, and "I did not know" was
the third state for nineteen commits.**

Three suites are gates, and each one is a command a person types:

| Key | Command | What it needs |
|---|---|---|
| `java` | `./mvnw -pl shio-app -am clean test -Dskip.npm=true` | nothing |
| `js` | `pnpm test` | `pnpm install` at the root |
| `conformance` | `pnpm conformance` | a **running instance** built from HEAD |

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

- **Each wrapper leaves a stamp.** `pnpm test` and `pnpm conformance` run through
  `scripts/test-with-log.mjs` and `scripts/conformance-with-log.mjs`, which write
  `.gates/<key>.json` — exit code, commit, time. Untracked (`/.gates/`), because a stamp is
  evidence about a command run *here*, and a committed one would let a clone inherit a claim about
  a run it never made. `pnpm conformance:raw` is the unwrapped command if you need it.
- **`red-suites.test.mjs` compares both directions** — a gate that answered red with no entry, and
  an entry for a gate that has since gone green. The second matters as much as the first: an
  exception outliving its red means the next real failure is already excused.
- **`ShDeclaredRedTest` reports the same thing inside the Java suite**, which is the point rather
  than a duplicate. A check that only the skipped gates carry is a check about looking; this one
  prints all three gates' last outcome in front of the person reading the green one.

**An absent stamp fails nothing.** A fresh clone has none and CI runs each gate in its own job;
"nobody has run this here" is true rather than wrong, so it is printed. That printing *is* the
repair — the sentence whose absence cost eight pushes.

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

### Four ledgers of what is knowingly wrong, and one reader (SH594)

The device is sound and it has been invented **four** times — `shio/verify-baseline.json`
(accepted `verify` findings, SH142), `assertion-debt.test.mjs`'s `DEBT` (shipped ids with no
assertion, SH527), `fixtures/source-sites/open-findings.json` (corpus assertions red at HEAD,
SH560) and `red-suites.json` (a suite allowed to be red, SH579). Each is a named list rather than
a ceiling, each is checked in both directions, and each was the right call on its own. Together
they are four formats, four readers and four places to look, and nothing joined them.

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
./mvnw -pl shio-app -am clean test -Dskip.npm=true 2>&1 | tee shio-test.log | grep -E "Tests run:.*Failures.*Errors" | tail -1
```

(One line, deliberately: `ShTestCommandDocLintTest` reads these commands a line at a time, so a
backslash continuation would hide the `| grep` from the guard and the guard would pass on a command
it can no longer see — which is how it caught its own author breaking it.)

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

### A JS scan over this repository declares what it reads (SH740)

SH613 gave the Java suites `@ShSourceScan` + one shared `ShSourceText`, because a textual guard
cannot tell a rule from a sentence about the rule. The JS half had four private ideas of what a
comment is and three guards were tripped by prose about their own rule — SH722's message citing
SH322, then twice in one session while fixing that.

`cli/test/support/source-text.mjs` is now the one answer and `cli/test/source-scan.test.mjs` the
lint. A scan declares `// @scans code`, `prose` or `both`; `code` is held to using the shared
stripper, and `prose`/`both` need a written `reason:` because an accident cannot explain itself.

Two strippers, not one, and that is the part that is **not** a port of the Java rule. `code()`
drops whole-line comments, line-oriented and stateless so a regex literal cannot derail it
(SH630). `unquoted()` is for Markdown, where prose is the whole document and "strip the comments"
means nothing: it drops fenced blocks, inline code spans and quoted phrases, because when a
document is asked *do you claim X*, a phrase it has put in quotation marks is a quotation. That is
what lets the sweep checklist quote the two false sentences it replaced instead of paraphrasing
them to protect a counter. It matches within a line only — a quotation broken across a line break
is not stripped, so wrap a quoted claim onto one line.

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
