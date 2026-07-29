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

