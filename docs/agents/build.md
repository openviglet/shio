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

**Measure the suite from the run, not from `target/` (SH283).** The verified command:

```
./mvnw -pl shio-app -am clean test -Dskip.npm=true 2>&1 | grep -E "Tests run:.*Failures.*Errors" | tail -1
```

Two traps, and the first causes the second. **`-q` suppresses surefire's `Results:` summary entirely**
— it is a Maven INFO line, so quiet mode removes the only authoritative total. Without it the obvious
fallback is to sum `target/surefire-reports/*.txt`, and **that directory accumulates**: reports from a
targeted `-Dtest=X` run sit beside the full run's, so the sum double-counts those classes (977 claimed
during Block P, 974 on a clean run). So drop `-q` when the number matters, and keep `clean` for SH282's
reason. Both traps are the same shape — *an artefact of an earlier command changing what a later one
appears to say* — which is worth remembering as a class, since `target/` holds several.

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

