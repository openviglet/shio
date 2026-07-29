---
name: shio-build-test
description: Read before building, running or measuring Shio's test suite, or when diagnosing a build/test oddity. Triggers on mvnw, skip.npm, frontend-maven-plugin, surefire/failsafe, "Nothing to compile", counting tests, the pnpm workspace, Vite config, publishing to npm, argLine and the Mockito javaagent, benchmark isolation, and any failure that looks unrelated to the change you just made.
---

# Building, testing and measuring Shio

**Backend-only work: always `clean`, always `-Dskip.npm=true`, always the full suite.**

```
./mvnw -pl shio-app -am clean test -Dskip.npm=true
```

Count the tests from the *run*, not from `target/`:

```
./mvnw -pl shio-app -am clean test -Dskip.npm=true 2>&1 | grep -E "Tests run:.*Failures.*Errors" | tail -1
```

**Read [docs/agents/build.md](../../../docs/agents/build.md)** for the Maven/pnpm
wiring, Vite config, static-resource mapping and the npm publish order, and
**[docs/agents/troubleshooting.md](../../../docs/agents/troubleshooting.md)** for the
catalogue of known-weird failures.

## Four artefacts of an earlier command changing what a later one says

This is the class of bug to suspect first when a result makes no sense:

1. **`clean` is not optional (SH282).** Without it, a call to a method that does not
   exist prints **"Nothing to compile — all classes are up to date"** and **BUILD
   SUCCESS**: the IDE compiles into `target/classes` on save, so the `.class`
   outlives the `.java`. Eclipse JDT emits a class with
   `java.lang.Error: Unresolved compilation problem` embedded, so it fails at
   *runtime* in whatever test calls it — one missing accessor read as 53 errors
   across five unrelated classes. `-Dmaven.compiler.useIncrementalCompilation=false`
   does not help. Treat "Nothing to compile" as **no information**; the tell is a
   `ServletException` wrapping `java.lang.Error`.
2. **`-q` hides the total, and `target/surefire-reports/` accumulates (SH283).** `-q`
   suppresses surefire's `Results:` line — the only authoritative count — and summing
   the reports double-counts any class you ran earlier with `-Dtest=X` (977 claimed
   vs 974 real).
3. **`-Dskip.installnodepnpm=true -Dskip.pnpm=true` do nothing here.** Those are
   `frontend-maven-plugin`'s per-goal properties; this pom is wired for the single
   `<skip>${skip.npm}</skip>` toggle, so passing them runs the React build anyway.
4. **A benchmark that measures the whole instance must not share a datasource
   (SH315).** `ShBenchmarkIsolationLintTest` enforces three source rules — the
   convention was documented on four classes and broken five times, always caught by
   accident, because the code is right and only the *measurement* is wrong.

## Mockito is a `-javaagent`, not a self-attach (SH250)

Surefire and failsafe carry `@{argLine} -javaagent:${org.mockito:mockito-core:jar}`.
**`@{argLine}`, never `${argLine}`** — JaCoCo sets that property, so the plain form
replaces its agent and coverage silently stops. If you add another agent, append the
same way. Self-attaching made `ShTenantSuspendedAccessTest` fail six of six once and
pass on every re-run; a flake is worse than a break, because it teaches people that
red might mean nothing.

## Before you quote a benchmark number

Know which way it moved and why — Blocks N and O made the *baseline* lean rather than
the agent path fast, so the ratios fell three blocks running with the agent path
untouched. Quote a **source**, not a task ratio; the aggregate ≈10× is retired.
Ceilings live in `shio-app/src/test/resources/token-budgets.properties`; raising one
is allowed and must be justified in the commit.
