# Common issues and solutions

> Split out of the repo-root `agents.md` (see its routing table for the index).
> Read this before touching the area it covers. Detail here is authoritative.

## Common Issues & Solutions

### `Grep` answers `binary file matches` on a source file (SH859)
ripgrep calls a file **binary** when it holds a NUL byte, and then reports that the file matched
without printing a line. `git grep` and GitHub's code search agree. Measured here: a `Grep` for
`accentFor` in `cli/src/theme.mjs` answered `binary file matches (found "\0" byte around offset
49715)` and nothing else, on a 2,000-line JavaScript module, so the cheapest tool P4 names was
silent on two of the largest files in the CLI, and an agent cannot grep for an absence.

The cause is an idiom this project uses on purpose and keeps: a composite key joined on a byte that
cannot occur in the data, so two different pairs can never collide into one (P5). What was wrong is
writing the byte **raw** rather than as an escape, which is byte-identical at runtime. Six files did;
SH804 had allow-listed them, having weighed whether the byte was deliberate and not what a raw one
costs. `control-characters.test.mjs` refuses a raw NUL everywhere now, and a second test asserts the
property itself, no file the walk reads is binary to a grep.

**The escape differs by language, and that is not a style choice.** Java resolves `\uXXXX` in the
*lexer*, before tokenizing, so the unicode escape in Java source puts a real NUL in the token stream
and the string literal does not compile: Java takes `\0`. JavaScript and TypeScript take the unicode
escape, preferred over `\0` there because `\0` followed by a digit is a legacy-octal error under
strict mode.

### A one-line edit diffs as the whole file (SH863)
`core.autocrlf` is a **per-machine** setting, so a repository that declares nothing gets a different
answer per contributor. This one declared nothing: on a machine with `input`, git stores LF and
converts nothing on checkout, so a file a Windows editor created kept CRLF on disk for ever while the
index held LF. **308 of 1864 tracked files** were in that state. Measured on a one-character change:

```
git diff --stat                      cli/src/convert.mjs | 4172 ++++----
git diff --stat --ignore-cr-at-eol   cli/src/convert.mjs |    2 +-
```

`.gitattributes` now declares `* text=auto eol=lf`, with `*.cmd`/`*.bat` pinned to CRLF (`cmd.exe` is
entitled to care, and `run-commit.cmd` is how this project commits) and the image, font and archive
types named `binary` rather than left to detection.

**The declaration alone changes nothing already on disk.** An attribute applies at checkout, so the
working tree has to be re-materialised once:

```
git status --porcelain          # must be empty — this discards anything uncommitted
git rm --cached -r . && git reset --hard
```

That took 308 down to 11, and every one of the 11 is correct: eight `.cmd`/`.bat` pinned on purpose,
and three `.png` where a `0D 0A` byte pair is a coincidence. Both gates were re-run afterwards, since
297 files' bytes changed (Java 1927/0, `pnpm test` green) and the same one-character edit then
reported `2 +-`.

### Optimistic Locking on Startup
`ShPostTypeImport` sets explicit IDs on `ShPostTypeAttr` via `setId()`. With cascade persist, Hibernate treats these as detached. Solution: use `ShAssignableUuidGenerator` (like Turing's `TurAssignableUuidGenerator`) that allows pre-assigned IDs.

### CSRF 403 on PUT/POST/DELETE
1. Verify `ShCsrfHeaderFilter` is added after `BasicAuthenticationFilter.class`
2. Verify `/api/v2/csrf` endpoint exists and is in `permitAll()`
3. Frontend axios interceptor must fetch token before mutating requests
4. Cookie must not have `Secure` flag in dev (HTTP)

### widgetSettings JSON.parse("null") Crash
`widgetSettings` may contain the string `"null"` from the backend. `JSON.parse("null")` returns JavaScript `null`, which passes truthy checks but crashes on property access (`null.file`, `null.choices`, etc.). Always guard parsed settings:
```typescript
const p = JSON.parse(raw);
return p && typeof p === "object" ? p : {};
```
The `parseSettings()` helper in `post-type.edit.page.tsx` centralizes this pattern.

### Vite "Outdated Optimize Dep" (504)
After installing/removing packages (`pnpm add`/`pnpm remove`), clear Vite cache: `rm -rf node_modules/.vite` and restart dev server.

### Static Files 404
- WebJars served from `classpath:/META-INF/resources/webjars/`
- Check Maven dependency resolution (transitive deps may override explicit versions)
- `store/file_source/` path must end with `/` in resource handler

### Full-text search returns nothing (SH131)

`ShPostUnifiedRepository.FULLTEXT_PREDICATE` needs a pre-lowercased, `%`-wrapped term.
Call the `search*` **default** methods, which normalize for you, the raw `@Query`
methods are suffixed `…Like` and take the pattern. Normalizing again before calling a
`search*` method searches for `%%term%%`, which matches nothing and looks like an empty
index rather than a bug.

### Unused Imports Fail TypeScript Build
`tsc -b` with strict mode flags unused imports as errors (TS6133). When refactoring (e.g., replacing `Checkbox` with `Switch`), always remove unused imports.

### `Column "..._.TENANT_ID" not found` (or any column) at startup
Schema drift. The schema is Liquibase-managed (`ddl-auto=none`) (a column referenced by an entity does not exist because no changeset created it. Fix by adding a Liquibase changeset (see **Database Schema** above), not by flipping `ddl-auto` back to `update`. Also confirm the datasource URL still has `DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true`) dropping those reintroduces quoted-vs-unquoted casing mismatches that surface as "column not found".

### A post or address comes back with no site (SH128 invariant)

**Only a site's root folder carries `shSite`.** `ShFolderWriteService` nulls it on every
child, so reading `folder.getShSite()` on anything nested silently yields `null`, a
site-less post, an address of `folder:null/...`, or a CDA allow-list check that denies
everything (`allowsSite(null)` is `false` under a token allow-list). Always resolve with
`ShFolderSite.of(folder)` / `.idOf(folder)`, which walks to the root:

```java
// ✗ null for every folder below the root — compiles, reviews clean, wrong at runtime
row.setShSite(folder.getShSite());
// ✓ walks the parent chain to the folder that actually carries the site
row.setShSite(ShFolderSite.of(folder));
```

`ShFolderSiteInvariantTest` fails the build on the wrong form. If a direct read is
genuinely what you mean (the exchange export records `shSite` root-only; the console XP
endpoint touches it to force a LAZY proxy), add a `ShFolderSite-exempt: <reason>` comment
on or directly above the line.

### `Column "spt1_1.tenant_id" not found` in a JPQL query (SH22 join hazard)
Seen only on Hibernate 7.4.5 and older. **Navigating past the `id` of an association whose
target is an `ShObject` subclass** (`ShSite`, `ShFolder`, `ShPostType`) made Hibernate join
the subclass table alone and emit the tenant predicate against a root alias it never declared,
so the query died at prepare time. Hibernate 7.4.13 (Spring Boot 4.1.2) joins the root and
filters correctly (SH1290); if this error comes back, the Hibernate version moved backwards.
`ShTenantJoinLintTest` still asks for the `.id` form, which needs no join:

```java
// ✗ refused by the lint: joins ShPostType and ShObject to read a name
@Query("select p.shPostType.name, count(distinct p.id) from ShPostUnified p group by p.shPostType.name")
// ✓ works: uses the FK column already on sh_post_unified — no join at all
@Query("select p.shPostType.id, count(distinct p.id) from ShPostUnified p group by p.shPostType.id")
```

Resolve `id → name` in the service from the entity list you already loaded. The
id-only form is also faster (no join). This is invisible until runtime, the
mapping is valid JPQL, so it is a code-review item, not a compile error. Found
while building the SH77 context pack; see `ShPostUnifiedRepository`'s note.


### A test passes because a sibling test already fixed the fixture (SH296)

**A mutating test must *force* its precondition, never assume the `@BeforeAll` one.**
`Lifecycle.PER_CLASS` shares one fixture across a class, so a test that writes leaves
state the next test reads, and the failure is not a red build, it is an assertion
that quietly stops asserting anything.

It has happened three times, each found by accident:

- **SH239**: a benchmark pair wrote into the folder another pair measured, so one
  ratio read 26.2× or 11.8× depending on order.
- **SH272**: a test asserting "this instance has no templates" was really asserting
  what ran first.
- **SH289**: an additive-only assertion survived a mutation that made the code
  overwrite *everything*, because a sibling had already repaired the fixture. It was
  caught only because the fix was verified by mutation rather than by a green run.

The rule in practice is `ShRenderModelReconcileTest.oldRegionWithNoTitleField()`: it
**re-creates the broken state at the top of every test that needs it** rather than
building it once. An early version returned when the type already existed, and the
additive-only test then ran against an already-repaired type, where `reconcile` finds
nothing to add and writes nothing, so the assertion passed whatever the code did.

**Two instruments were weighed and rejected, and the reasons are the point.**
`@TestMethodOrder(Random)` turns a silent order dependence into a *flake*, and SH250's
rule is that a flake is worse than a break. A lint flagging any `PER_CLASS` class whose
tests write through a shared service would flag most of the suite, the write is normal,
the *vacuous assertion* is the defect, and no scan can tell them apart. Both cost more
than the defect. What is cheap is the habit, which is why it is written here.

Related and already enforced: `ShBenchmarkIsolationLintTest` covers the other half of
this hazard, a class reading the whole **instance** from a shared database (SH315,
widened by SH318). This entry is about one class sharing its fixture with itself.

## An I/O failure that would not say why (SH756)

`ShMarketplaceInstallerTest` went red on one `clean test` and green on the next, minutes apart on
the same tree. The whole of what it reported was two paths:

```
InstallFailedException: …\shio-marketplace-1005…\shio-blueprint-newsroom
  -> …\junit-7770…\shio-blueprint-newsroom
```

**A `FileSystemException`'s message IS its two paths**, with `": " + reason` appended only when the
platform supplied one. So a message showing paths alone is a message whose reason was **null**, and
at that point the exception's *class* is the last remaining fact, `FileAlreadyExistsException` and
`AtomicMoveNotSupportedException` print identically through `getMessage()` and are different
diagnoses. The class was the thing being discarded.

**Use `ShIoFailure.describe(e)`, never `e.getMessage()`, when an `IOException` becomes a message
somebody reads.** It names the class first, then the platform's reason where there is one, then the
paths, the first two decide and the third is only *where*. `ShIoCauseLintTest` holds every
`catch (IOException …)` block in the tree to it, with a declared-exemption map for a site that
means something else.

**The lint found more than the grep did, and then found something about itself.** The hand search
turned up eight sites in three files; the lint turned up two more nobody had looked at, the image
transform pipeline, whose message goes into the agent's own diagnostics window, and the marketplace
catalogue. Its first draft matched the whole file instead of the catch block and reported nine
findings of which six were false, all from one file where `catch (IOException e)` and
`catch (RuntimeException e)` share a variable name. That draft carried a comment arguing the
looseness was acceptable. It was not: a lint wrong two thirds of the time is one somebody relaxes,
and a relaxed lint is off. It brace-matches the block now.

**What this does not do is explain the flake**, and that is deliberate. One sighting with no reason
string is not enough to choose a fix from, an indexer holding the freshly unpacked tree, a
destination left behind by an earlier run (24 stale `junit-*` directories were in `%TEMP%` when it
fired), or `ATOMIC_MOVE` unsupported across those two directories are all still open. The next
sighting will name its own class, which is what makes it decidable instead of a guess.

## A window that reopened only if the clock happened to tick (SH759)

`ShAgentSessionBudgetTest.theWindowReopens` set the session window to **one nanosecond** and spent
twice, on the argument that a window already elapsed by the second call proves the reset without a
sleep. The intent was right and the mechanism was not.

`spend` keeps the window while `resetsAt.isAfter(now)`. The first call sets `resetsAt = now + 1ns`.
If the second call's `Instant.now()` returns the **same** instant, which it does whenever the
clock's granularity is coarser than the gap, and on Windows it is by orders of magnitude, the
window is kept and the second spend is refused. Green on runs where a tick landed between two
adjacent reads; red on 2026-08-14 after many green runs on the same tree.

**A window closing is a fact about time, so the test moves time.** `ShAgentSessionBudget` takes a
`Clock`: the same shape `ShTenantApiCallMeter` uses, a public constructor for Spring and a
package-private one for a test, and `theWindowReopens` advances an hour instead of hoping the
machine was slow enough to notice a nanosecond. It also asserts the refusal **before** advancing:
without that, the test would pass against a guard that never charged anything, which is how a
reopening test most easily goes vacuous.

**Nudging the duration would have been worse than leaving it.** A longer window makes the failure
rarer without making it impossible, which turns a diagnosable flake into an undiagnosable one,
SH250's rule is that a flake is worse than a break.

**No lint, and the survey is the reason.** SH338 says the instrument that finds every *other* break
lands before the fixes, so the tree was searched for the same smell: a sub-millisecond `Duration`
standing in for elapsed time. The population was **one**, this test. An instrument over a
population that is about to be zero, keyed on a guess at how somebody might write the mistake next
time, is speculation with a build cost; the survey is recorded here instead, which is what makes it
checkable if the answer ever changes.

## Two Windows flakes, and what each one now says (SH1093)

**The keep-alive exit test** (`cli/test/exit-path.test.mjs`, SH440's live-binary case) measured
`shio resolve` at 4057 ms once, undici's `keepAliveTimeout` to the millisecond, and passed on an
unchanged re-run. Fifty idle runs and thirty under eight CPU-burning processes reproduced nothing, so
it is not scheduling noise and the rare cause is unknown. The test now asserts its two claims apart.
`SHIO_EXIT_TRACE=1` makes `finish()` in `cli/src/exit.mjs` write
`shio-exit: disarmed <n> slot(s); active <resources>` to stderr, and the test requires at least one
slot disarmed and a wall clock under 3500 ms (below the 4 s keep-alive, far above any delay a loaded
machine added). A future red names which half failed and what still held the loop.

**The marketplace install** threw `AccessDeniedException` renaming a freshly unpacked package, because
a Windows indexer or scanner held a handle on files that had just appeared. `ShMarketplaceInstaller`
now moves through `moveInto`: an atomic move, and on `AccessDeniedException` or
`AtomicMoveNotSupportedException` a copy then a best-effort delete. A copy only reads, which such a
handle allows. A destination that already exists stays a refusal, so two racing installs are not
hidden. It is tested with an injected mover refusing the rename, since a real handle cannot be
summoned on demand; fifty Maven runs of the old flake were not measured.

## A statement count is global, and the scheduler shares it (SH1276)

`ShSiteListingCostTest` failed about one full run in four with `Measured [6, 3]` and passed alone.
Hibernate's `Statistics` counter belongs to the `SessionFactory`, so it counts every thread, and the
test shares the plain context, where `ShPostScheduler` sweeps every sixty seconds and prepares three
statements of its own. Proved rather than argued: with `shio.schedule.sweep-interval-millis=5` the
reads came back `[9, 6, 7]`. The test now switches `ShScheduleProperties.enabled` off for the
measured window and takes the fewest statements over three reads per size (another thread can only
add, and a per-row cost raises every read), and under the same 5 ms sweep every read is 3. **Any
test that counts statements in a shared context has this exposure**: pause the sweep the same way,
or measure in a context with `shio.schedule.enabled=false`.

**And a lint holds it (SH1284).** `ShStatementCountContextLintTest` finds every test class calling
`getPrepareStatementCount(` (read as code, so a comment naming the guard does not count) and requires
one of the two guards; it prints the population. Measured when it landed: nine classes, seven already
in a scheduler-off context, `ShSiteListingCostTest` pausing, and `ShWorkflowTaskFetchCostTest` exposed,
now pausing like SH1276. A new scheduled job belongs to the same rule; its pause goes beside the sweep's.