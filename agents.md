# Viglet Shio - Development Guide

## Product orientation: read this before writing code

**Shio is an agent-ready CMS: every capability is built for a coding agent first,
and a person can still do all of it from the console.** The agent builds and
maintains the site (MCP tools + content as files + blueprints); the **curator
approves**: corrects and tweaks (React console, Universal Editor, preview
links); the **CDA delivers** (REST/GraphQL + the TS SDKs). Every design decision is
judged by *agent cycles and tokens*, not by clicks.

Shio hosts **no model and no prompts**: it is the substrate an external agent
drives. The differentiator is protocol quality (discoverability, determinism,
verifiability, token economy), not intelligence. The Java/Spring content engine and
the headless JS/TS delivery layer stay as they are; we do **not** rewrite the backend.

**The ten design laws**: a feature that violates one is wrong even if requested:

| # | Law |
|---|---|
| P1 | **Agent-first design order**: every capability lands MCP → files/CLI → REST → console. A console-only feature is incomplete. |
| P2 | **One call replaces a session**: discovery is a product feature (manifest + context pack), not a docs problem. |
| P3 | **Tokens are a measured budget**: response budgets + a CI benchmark; a token regression is a build failure. |
| P4 | **Files beat APIs for authoring**: project content to disk; `Edit`/`Write`/`Grep` are the agent's cheapest tools. |
| P5 | **Determinism over cleverness**: stable ordering, path addressing, idempotent writes, byte-identical serialization. |
| P6 | **Errors are instructions**: every 4xx carries `fix`/`allowed`/`didYouMean`/`example`. |
| P7 | **Never surprise the curator**: draft-default, dry-run, explicit publish, confirm tokens, attribution, review queue. |
| P8 | **Skipping steps needs appliable units**: blueprints, not prose recipes. |
| P9 | **Close the perception loop**: cheap textual proof (lint, routes, render digest) instead of asking someone to look. |
| P10 | **Compose, don't fork**: MCP is a *shape* over the CDA + console services, never a third contract. |

Full concept, protocol design, tool surface and worked example:
[docs/specs/SH74-agent-native-cms.md](docs/specs/SH74-agent-native-cms.md).
Backlog: [docs/ROADMAP.md](docs/ROADMAP.md) Blocks H–N. Rationale:
[docs/IMPROVEMENTS.md](docs/IMPROVEMENTS.md) §0 and §VIII–§XIV.

## Project Structure

### Backend (Spring Boot 4 + Java 21)
- **shio-app/** - Main Spring Boot application
  - `src/main/java/com/viglet/shio/` - Java source
  - `src/main/resources/ui/` - Legacy AngularJS frontend
  - `src/main/resources/public/` - React frontend build output
  - `src/main/resources/import/` - Default data (post-types.json, sample sites)
  - `store/` - Runtime data (file_source, logs, H2 database)

### Frontend (React + Vite + shadcn/ui + TypeScript)
- **shio-react/** - New React frontend
  - `src/app/{content,admin,editor,system}/` - Page components, one tree by domain
  - `src/app/routes/` - Route definitions
  - `src/components/` - Reusable components
  - `src/components/ui/` - shadcn/ui components
  - `src/components/widgets/` - Dynamic form widgets (14 widgets)
  - `src/services/` - API service layer (class-based)
  - `src/models/` - TypeScript type definitions
  - `src/contexts/` - React Context providers
  - `src/i18n/` - Internationalization (en/pt)
  - `src/lib/` - Utilities (axios, utils, widget-colors)

## Where the detail lives: read the area's file before you touch it

This file is the **index**, and it is deliberately thin: it is loaded into every
session on every turn, so P3 applies to it before it applies to anything it
describes. The per-area detail below is **authoritative**; do not restate it here.

| Area | Read | Skill |
|---|---|---|
| Renderer: page → layout → region, templates, helpers, the section vocabulary | [docs/agents/renderer.md](docs/agents/renderer.md) | `shio-renderer` |
| The agent surface `/api/v2/agent/**`, and what bounds an agent (Block L) | [docs/agents/agent-surface.md](docs/agents/agent-surface.md) | `shio-agent-surface` |
| MCP `POST /mcp`, tools/resources/prompts, the Claude Code plugin | [docs/agents/mcp.md](docs/agents/mcp.md) | `shio-agent-surface` |
| Content API: console reads, unified posts, copy/move, folders, sites, static files | [docs/agents/content-api.md](docs/agents/content-api.md) | `shio-content-api` |
| Post types: the one name-keyed surface | [docs/agents/post-types.md](docs/agents/post-types.md) | `shio-content-api` |
| Content as files `shio/content/**`, the three-way merge, assets | [docs/agents/content-files.md](docs/agents/content-files.md) | `shio-content-files` |
| Blueprints: appliable starting points | [docs/agents/blueprints.md](docs/agents/blueprints.md) | `shio-content-files` |
| The `shio` CLI and the CI gate order | [docs/agents/cli.md](docs/agents/cli.md) | `shio-content-files` |
| Console UI: React patterns, widgets, editors, data model, i18n, theme | [docs/agents/console-ui.md](docs/agents/console-ui.md) | `shio-console-ui` |
| Security: CSRF, auth, CORS | [docs/agents/security.md](docs/agents/security.md) | `shio-console-ui` |
| Liquibase, migrations, cross-DB ITs, multi-tenancy (SH22) | [docs/agents/database.md](docs/agents/database.md) | `shio-database` |
| Turing indexing: published content becomes searchable, off by default | [docs/agents/turing.md](docs/agents/turing.md) | `shio-database` |
| Build, Maven/pnpm wiring, publishing | [docs/agents/build.md](docs/agents/build.md) | `shio-build-test` |
| Common issues and solutions | [docs/agents/troubleshooting.md](docs/agents/troubleshooting.md) | `shio-build-test` |
| Replication: the chain, the region rule, and **the acceptance criterion that says when a round is done** | [docs/agents/replication.md](docs/agents/replication.md) | `shio-replication-test` |
| Judging a replication run against a live instance: the checks a suite cannot make | `cli/conformance/` | `shio-replication-test` |
| The public site at shio.viglet.org: its stack, its prerender, and the one file all its copy lives in | [shio-site/README.md](shio-site/README.md) | none |
| ROADMAP / CHANGELOG / IMPROVEMENTS upkeep rules: those three are **owned by `roadkeep`** ([roadkeep.toml](roadkeep.toml)), never hand-edited | [docs/agents/docs-process.md](docs/agents/docs-process.md) | `shio-roadmap-docs` |

**Every skill above is a *project* skill, and there is a second set (SH949).** This repository
publishes the `openviglet` marketplace, whose `shio` plugin ships `shio-site`,
`shio-content-model`, `shio-replicate` and `shio-verify`, and `.claude/settings.json` now enables
it here, so a session in this tree loads exactly what a stranger installs. The split, which is what
keeps two sets from becoming two authorities on one subject: **a plugin skill serves a session
driving an instance; a project skill serves a session changing this source tree.** So replicating a
site is `shio-replicate` and changing the replication code is `shio-replication-test`; building
pages is `shio-site` and changing the renderer is `shio-renderer`. No name may appear on both
sides, and `claude-plugin/test/plugin.test.mjs` fails if one does.

Addresses (SH79), used everywhere: `post:<site>/<friendly-url>` ·
`folder:<site>/<name-chain>` · `site:<name>` · `id:<uuid>`. **Writes take only
`post:`/`folder:`**, and a site's **home page is `post:<site>/`**, the one form
the grammar does not spell (SH433). Backend `http://localhost:2710`, React dev `5173`, API prefix
`/api/v2/`.

**Three endpoints that do not exist**: an agent cannot grep for an absence, and
inventing one of these is the most common wrong turn: there is **no**
`/api/v2/post` controller, **no** generic `DELETE /api/v2/object/{id}` (delete by
type: `/v2/post-unified/{id}` or `/v2/folder/{id}`), and **no** console post-type
controller (SH117 retired the id-keyed `/api/v2/post/type` twin; one name-keyed
surface only).

## Invariants that fail silently and have no lint

Everything here compiles, reviews clean, and is wrong at runtime, or, worse,
*passes*. Where a guard exists it is named in the area's own file and you do not
need to remember the rule; the ones below are the residue with **no** automated
guard, which is why they are the only detail this index carries. Adding a lint for
one of these is always in scope (SH338: the instrument is the deliverable).

| Rule | Failure if broken |
|---|---|
| **Never navigate past `.id` of an association whose target is an `ShObject` subclass** (`ShSite`/`ShFolder`/`ShPostType`) in JPQL. Use `p.shPostType.id`, resolve the name in the service. | `Column "spt1_1.tenant_id" not found` at query-prepare time. `@TenantId` lives on the JOINED root, so an implicit join emits the predicate against a subclass alias that has no such column. Valid JPQL, invisible until runtime. |
| **Resolve a site with `ShFolderSite.of(folder)`, never `folder.getShSite()`.** | Only a site's **root** folder carries `shSite`; every child is nulled. Yields a site-less post, `folder:null/...`, or an allow-list check that denies everything. *(This one does have `ShFolderSiteInvariantTest`; the exemption comment is `ShFolderSite-exempt: <reason>`.)* |
| **Guard every `widgetSettings` parse:** `const p = JSON.parse(raw); return p && typeof p === "object" ? p : {}`. | The backend sends the literal string `"null"`. `JSON.parse("null")` returns `null`, which passes a truthy check and crashes on property access. |
| **Call the `search*` **default** methods on `ShPostUnifiedRepository`, never the raw `…Like` `@Query`.** Do not normalize a term twice. | `FULLTEXT_PREDICATE` needs a pre-lowercased `%`-wrapped term; normalizing twice searches `%%term%%` and matches nothing; reads as an empty index, not a bug (SH131). |
| **A test that mutates a post-type must detach `ShPostTypeAttr.shWidget` first** (`cascade = ALL`), the way `ShPostTypeAdminService.detach` does. | Removing an attribute cascades a `REMOVE` into the **shared widget catalog** for the whole JVM. Surfaces as `Widget 'Text' is not seeded` thrown by production code several steps from the cause (SH289). |
| **`spring.jpa.hibernate.ddl-auto=none`: the schema is Liquibase's.** The dev/test H2 URL must keep `;DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true`, and the tenant discriminator column must be lowercase `tenant_id`. | Quoted-vs-unquoted casing mismatch → `Column "..." not found` at startup, and a tenant predicate that silently matches nothing on PostgreSQL. Never fix this by flipping `ddl-auto` back. |

## Build, test, commit

**Backend-only work: always pass `-Dskip.npm=true`**, and always `clean`.

```
./mvnw -pl shio-app -am clean test -Dskip.npm=true
```

Two measured traps behind that one line, both of the same shape: *an artefact of
an earlier command changing what a later one appears to say*:

- **`clean` is not optional (SH282).** Without it, a call to a method that does not
  exist prints **"Nothing to compile - all classes are up to date"** and **BUILD
  SUCCESS**: the IDE compiles into `target/classes` on save, so the `.class`
  outlives the `.java` and the staleness check skips the module.
  `-Dmaven.compiler.useIncrementalCompilation=false` does *not* help. Treat
  "Nothing to compile" as **no information**. You no longer have to recognise the
  tell (`ShIdeCompiledClassLintTest` fails the build naming the file), but `clean`
  stays the rule, because a *stale but valid* class is indistinguishable from a
  compiled one.
- **Do not add `-q` when the number matters, and do not sum `target/surefire-reports/` (SH283).**
  `-q` suppresses surefire's `Results:` summary (the only authoritative total), and
  that directory *accumulates*, so a targeted `-Dtest=X` run's reports inflate the
  next sum.
- **Never pipe a gate into `grep`: not for the log (SH354), not for the verdict
  (SH788).** Two different losses, and knowing one does not save you from the other.
  The filter discards the `<<< FAILURE!` lines and the next `clean` removes
  `target/surefire-reports/`, so a red run leaves *no record of which test failed*:
  one run in six, never reproduced, undiagnosable. And a **pipeline's exit status is
  its last command's**, so the shell reports `grep`'s: `pnpm test` printed `fail 2`
  above `EXIT=0`. Redirect, then read, for all three gates, not just this one:
  `pnpm java` (which is that `./mvnw` line, with the log kept and the outcome stamped where the
  other gates' comparators can read it, SH800), then
  `grep -E "Tests run:.*Failures.*Errors" shio-test.log | tail -1`. On red the
  names are in the log (`grep -E "<<< (FAILURE|ERROR)"`); read it **before** the next
  `clean`. CI is already honest about this (`set -o pipefail`, asserted by
  `ci-gates.test.mjs`); the trap is local.

Run the **full** suite, not a single `-Dtest` class. JS workspace: `pnpm install`
once at the repo root, **Node ≥26**. Detail (Maven/frontend-plugin wiring, Vite,
publishing) is in [docs/agents/build.md](docs/agents/build.md).

**A red suite is a stop (SH579).** Three commands are gates (`./mvnw … clean test`,
`pnpm test`, `pnpm conformance`), and a red one either blocks the next commit or is a
dated, time-boxed entry in [`red-suites.json`](red-suites.json) naming the open task
that removes it. There is no third state, and the third state cost nineteen commits over
two red suites that nobody mentioned. This is here rather than only in the area file
because it is a rule you have to know *before* you decide whether to read anything;
`cli/test/red-suites.test.mjs` enforces the ledger and
[`.github/workflows/suites.yml`](.github/workflows/suites.yml) runs two of the three on
every push, so red is visible without somebody choosing to look.

**`clean test` is the command; the ITs are opt-in and you ask for them (SH415).** The
line above is the whole validation for almost every task: it never invoked failsafe,
and since SH415 `verify` does not either (`skipITs` defaults to true). Add
`-DskipITs=false` when, and only when, the change touches what the ten `*IT.java`
classes actually cover: **multi-tenancy** (`@TenantId`, `ShTenantContext`, provisioning,
quota, membership, suspension), **the Liquibase schema** or anything that would read
differently on PostgreSQL/MariaDB, or the **CDA/static-file delivery** paths those ITs
drive. Reach for it on a schema or tenancy change even when the unit suite is green:
that is the case the ITs exist for and the one where `clean test` passing means least.
One thing before you read a green `verify` as proof: `ShPostgresSchemaIT` and
`ShMariaDbSchemaIT` carry `@Testcontainers(disabledWithoutDocker = true)`, so **without a
Docker daemon they skip**: `0.00 s`, counted as `Skipped`, and "verify proved the schema
on PostgreSQL" is then simply false. **Start Docker before spending the flag on a schema
change**, or the one check you wanted is the one that did not run. A failure *is* now red
(SH416 set `testFailureIgnore=false`), so the exit code is trustworthy in every state
except that one.

**Measure before you fix, and the measurement is the first deliverable.** P3 makes
this binding for tokens and `ShAgentResponseBudgetTest` for response sizes; the same
rule applies to **any** cost: queries, renders, suite time. A performance change
lands as two things in this order: a benchmark that says what the cost is today,
then whatever that number argues for. Not the other way round, and not the fix
alone. A cost that is argued rather than measured is the same defect as a premise
carried in a comment, and it fails the same way: quietly, and in somebody else's
environment.

**Corollary (SH322): when a task says "and whatever a grep turns up", the grep is
the deliverable**: it lands as a lint *before* the fixes, because that is what
finds the call site nobody read. **And the same holds for a law, not just a call
site (SH338):** when a review finds a design law is being broken, the instrument
that finds every *other* break lands before the fixes. Eleven lints here guarded
internal consistency and none guarded reachability, which is why P1 (the law the
product is named after) was the only one of the ten with no instrument. It has one
now (two, in fact): **`ShAgentP1ConformanceTest`** classifies **every** manifest
`features` key and holds the ones the agent cannot reach as an asserted list (four
of which no task had named when it shipped; the run prints both counts, which is why
neither is written here), and **`ShStatelessReachabilityLintTest`** asks
the narrower question: which mutating paths an agent can write to without a cookie.
Since SH973 a third, **`ShAgentReadReachabilityLintTest`**, asks it of the *reads*:
every GET-only root is declared in the manifest or classified, because a controller
that only reads was invisible to both of the others. All three print their inventory
and write it to `shio-app/target/p1-inventory.json` (SH974); **no figure from one of
them belongs in prose uncited**, and `cli/test/agents-md-figures.test.mjs` fails the
build for one that is. Before "fix the thirteen instances", ask what would have failed.

**And a defect closes with an assertion carrying its id (SH527).** Not a unit test of
the fix's internals: *the assertion that would have caught it*, at the boundary it
crossed, naming the id so the loop can be closed from either end. Six corrections
shipped half of themselves and the other half became a new task (SH452→455, SH429→437,
SH469→473, SH468→472, SH465→475, SH470→474), three of them shipping the *report* and
leaving the *behaviour*; at 246 commits in seven days that is the expected output, not
bad luck. A rule nobody can check is a preference, so
**`cli/test/assertion-debt.test.mjs`** computes it: the ledger knows what shipped, the
suites know which ids they assert, and the difference is the **debt**, held as a named
list rather than a ceiling (a ceiling lets one task pay it and the next spend it). It
prints both counts on every run, and it is excluded from its own walk, which is not
housekeeping: on the first run its own declaration made all forty entries read as
asserted and the debt came back as three.

### Committing

**When a task is done, commit by running `run-commit.cmd` from the repo root.**
It lives at `d:\dev\bin\run-commit.cmd` and is on the system PATH, so just invoke
`run-commit.cmd` (no path needed). It runs `git add *` then `ai_commit.py`, which
generates a Conventional-Commits message from the staged diff via the OpenAI API
(`OPENAI_API_KEY` must be set) and runs `git commit`. It does **not** push and
does **not** create a branch; it commits on the current branch. Don't hand-author
commits or run `git commit` manually for this repo; always use the script.

**One task → one commit, and this is the most violated rule in the project.** Commit
the instant a task is validated, *before* starting the next; the doc sync goes in the
same commit as the code. A multi-task request is **not** permission to batch: it is a
request to run them one at a time, and a batch of ≥2 tasks must be driven with the
`/loop` skill (one task per iteration, commit at the end of it). Always pass `-m`, or
a docs commit's prose about already-shipped work gets misread as
`feat: implement <feature>`. Full mechanics: the `shio-roadmap-docs` skill.

## Docs upkeep: the three files, one job each

| File | Single responsibility |
|---|---|
| [docs/ROADMAP.md](docs/ROADMAP.md) | **Active backlog only**: one line per task, one marker each (the legend is `[markers]` in `roadkeep.toml`) |
| [docs/CHANGELOG.md](docs/CHANGELOG.md) | What has **shipped**, indexed by Block, one entry per task |
| [docs/IMPROVEMENTS.md](docs/IMPROVEMENTS.md) | **Design rationale** for *unshipped* sections only |

**All three are written by [`roadkeep`](https://github.com/alegauss/roadkeep) and never by
hand.** The plugin is enabled in [`.claude/settings.json`](.claude/settings.json), so a clone
gets the hook that denies the `Edit` and names the command to call instead.
[`roadkeep.toml`](roadkeep.toml) is the authority on this project's ids, markers, outline
pointers and limits, and the **`roadkeep` skill** (which ships with the plugin) on the write
path and the query surface. Neither is restated here: a rule in two files is two files that can
disagree, and the tool's copy is the one that is enforced. Per-task specs are
`docs/specs/SH<n>-<slug>.md`, which roadkeep does not govern.

**Run it as `python .roadkeep/scripts/roadkeep.py`, and nothing else.** The engine is vendored
into `.roadkeep/` the way `node_modules` is (git-ignored, reproduced by `install-roadkeep.cmd`,
never committed), and `ROADKEEP_HOME` points there, which is the launcher's own first resolution
step, so the guard hook, the MCP server and a hand-run command all reach one engine. Two reasons it
is not run from wherever it is found: **every distinct command path is a fresh authorization
prompt**, and **which copy answers is not decidable by looking**: this machine carries plugin
caches at four different versions across two config directories, and a live checkout can be
mid-refactor (a session lost time to an `ImportError` out of a half-edited tree). `install-roadkeep`
picks by **version**, not by position; `update-roadkeep.cmd` refreshes the plugin first when you
want something newer than any cache holds.

**This file's own split came from the same drift.** `agents.md` reached 186 KB
(~46k tokens, resident every turn) by absorbing the rationale of every shipped task:
P3 violated by the file that declares P3. When you finish a task, the decision
goes in its area file under `docs/agents/`, the history stays in `git log`, and this
index grows only when a *new area* appears. **Non-goals are binding**, and they are printed
with every task, so honouring them costs no read.

The full cross-file update rules are in
[docs/agents/docs-process.md](docs/agents/docs-process.md).
