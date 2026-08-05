# Build and deploy

> Split out of the repo-root `agents.md` (see its routing table for the index).
> Read this before touching the area it covers. Detail here is authoritative.

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
Running the JS suite in CI is SH241/SH284's open work.
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

### Static Resources (Spring)
- `/store/**` → file_source parent directory
- `/file_source/**` → file_source directory
- `/thirdparty/**` → `classpath:/META-INF/resources/webjars/`
- `/console/**` → React SPA (permitAll in security)

