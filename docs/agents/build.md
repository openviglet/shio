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
Native build scripts are allowlisted via `onlyBuiltDependencies` in
`pnpm-workspace.yaml` (`esbuild`, `@tailwindcss/oxide`) — pnpm 11 no longer reads
the `pnpm` field in `package.json`, so this setting lives in the workspace file.
`@viglet/viglet-design-system` resolves from public npmjs (no scope mapping needed).

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

