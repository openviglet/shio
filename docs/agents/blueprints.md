# Blueprints — appliable starting points

> Split out of the repo-root `agents.md` (see its routing table for the index).
> Read this before touching the area it covers. Detail here is authoritative.

### Blueprints — appliable starting points (Block J)

A **blueprint** is a section of a site the instance can apply: a content model, a folder
tree, sample pages, the parameters that make it somebody's, and the promise it keeps about
what exists afterwards (law P8 — skipping steps needs *appliable* units, not recipes).
Owner: `com.viglet.shio.blueprint`; packages live at `shio-app/src/main/resources/blueprints/<name>/`.

A package is a directory with `blueprint.json` plus the files it references:
`postTypes` are SH16 `ShPostTypeDef` JSON (the same bytes `shio pull` writes), `content`
files are SH82 desired-state documents, `files` are optional front-end sources, and `verify`
is the promise. **Nothing in the format is a new serialization**, which is what keeps it
from becoming a third contract (P10).

**The rules that are load-bearing:**
- **A package may not name its own site, and may not `prune`.** The site is the caller's; a
  `site` key in a content document is refused at load rather than ignored (an ignored key
  looks like it worked). Prune's blast radius depends on what an author forgot to mention,
  and that author is a package the user did not write.
- **Substitution is `{{name}}` and nothing else** — no conditionals, no expressions, so the
  same parameters always produce the same site (P5). `site` is a reserved parameter, always
  available, never declarable. An unknown parameter is a teaching 400, never ignored.
- **Everything is validated at load** (`ShBlueprintRegistry`): a missing referenced file, a
  path that escapes the package, an unknown manifest key, a `{{placeholder}}` with no
  declared parameter, a `verify.posts` entry the content never declares. These are the
  *author's* mistakes; a malformed package is a **409, not a 400** — nothing the caller sent
  caused it. `ShBlueprintRegistryTest` runs without a Spring context, so it is cheap enough
  to run on every change to a package.
- **The model is provisioned, never upserted.** An existing post-type of the same name is
  left exactly as it is (including a field a human added) and the response *names the fields
  it lacks* — the bootstrap `Page` (SH71: `title` + `content`) versus the section
  vocabulary's `Page` is the collision this actually happens to. SH152 reconciles.
- **A plan writes nothing and needs no write transaction (SH153).** It used to provision the
  package's post-types inside its own transaction and roll back — accurate, and it meant a plan
  could not run on a read-only credential, a replica, or inside a `readOnly` transaction, which
  is the wrong constraint on a surface whose safety story is "look before you write". Instead the
  plan **declares** what it would create — the site, and each post-type with the fields it would
  carry — and the batch planner resolves against that: `ShAgentBatchService.Declared`, the
  pending-folder idea one resource wider. Two rules keep it honest. It is **additive, never a
  substitute**: a live post-type is still resolved live and the declared names are *unioned* with
  its own, so a plan can never come back greener than the run it predicts — with `mergePostTypes`
  off, nothing is declared for an existing type and SH168's "a real run will refuse this write"
  survives. And it is **honoured on a dry run only**, so a declaration cannot stand in for an
  apply that did not happen. `ShBlueprintWriteService.plan` is `@Transactional(readOnly = true)`,
  which makes that a claim the database enforces rather than one a test has to remember.
- **The server never writes a project file, and never substitutes one.** `files` are returned
  and the CLI writes them (never over an existing one without `--force`); an MCP-only caller
  is told what it has *not* got — **and since SH166, that the bytes are templates**. Each row
  of an apply names the `{{placeholder}}`s standing in that file, read from the **bytes** and
  not from the manifest's parameter list (a package may declare a parameter its files never
  use, and reporting that one sends the caller hunting for something that is not there). Every
  name has its resolved value in the `params` the same response echoes, defaults included,
  which is what makes the report actionable rather than a warning. Server-side substitution
  was refused for a reason worth keeping: the bodies travel on a **read**
  (`?include=files`, or `shio://blueprint/{name}/files`) that has no parameter values, and
  giving a cacheable GET a write's arguments fights SH116.
- **An apply records itself (SH155).** It writes an SH114 note scoped to the site, keyed
  `blueprint.<name>`, saying which package and version built it and with which parameters —
  and the response names the key in `recordedAs`. One note **per package**, because a site can
  receive a `landing` and then a `blog` and a single key would erase the first; parameters are
  sorted and `site` dropped so the same apply writes the same bytes (P5). A memory note rather
  than a table because it then reaches the next session through the context pack it already
  reads (P2). Written *after* the writer's transaction, so a note that cannot be filed leaves
  the apply standing — the response just does not claim a record it does not have. This is not
  drift detection: it makes "is this site still what the blueprint says" *askable*, and the
  answer is still a `dryRun` away.
- **Packages load from the classpath, plus one directory an operator names (SH96).**
  `shio.blueprint.path`, off by default. That *is* the trust model: whoever can set it could
  already put a jar on the classpath, so it grants no new authority, and the person who set it
  is the one vouching for what is in it. Three rules — a **name collision refuses** at load
  naming both origins (shadowing would change what every existing `blog` apply does; ignoring
  would make the operator's package do nothing); a configured path that is not a readable
  directory **fails** rather than loading fewer packages; and the **same validation runs**, so
  "it loaded" means the same thing whichever source it came from. Every package carries its
  `origin`, reported on the listing for local ones only. No URL fetching, no registry
  resolution — that is the community half (SH217).

**The first-party set (SH94, SH119, SH156):** `blog` · `docs` · `product-catalog` ·
`marketing-site` · `landing` · **`nextjs-starter`**. The last one is the
`create-shio-app` template as a package (SH119). **Three packages carry front-end `files`,
all generated by the same `pnpm -C create-shio-app run emit`** — `nextjs-starter` gets the
template tree; `marketing-site` and `landing` get **the same tree plus the section overlay**
in `shio-sections/front-end/` (replace-by-path, so the section-aware catch-all *is*
`app/[...slug]/page.tsx`, plus `lib/sections.ts` and a merged `package.json` dependency whose
range is read from the workspace). `test/emit.test.mjs` fails the build on drift and asserts
the overlay changes **exactly three files** — if that grows, the overlay has become a fork.
**If you change `create-shio-app/template/` or `shio-sections/front-end/`, run `emit`.**
`blog`/`docs`/`product-catalog` are left out on purpose: they compose no sections, so the
overlay would ship a dependency they never import — what they want is a listing page (SH216).
The overlay is only usable because **`@viglet/shio-model` and `@viglet/shio-sections` now
publish** (SH156); before that, a package carrying a page that imports the vocabulary would
have produced a project whose `npm install` fails. Substitution is split by who
knows the answer: the server substitutes a package's *content*, the client its *carried
files* (the project's own name is local identity, and the server never writes a project
file) — and the JS regex is character-for-character `ShBlueprintParams`' so the two halves
cannot disagree. A package's path refs are checked per *segment* for `..`, not per
substring, or `app/[...slug]/page.tsx` would be rejected as traversal. Each declares a `verify` promise, checked at load against its own content and
after an apply against the instance (alongside the SH99 lint); `shio apply --blueprint` exits
non-zero when that fails.


**`shio init --blueprint` checks its preconditions before it writes anything (SH447).** It did not,
and the two halves of that compounded. `init` had **neither `--param` nor `--params`** — it sent
`{projectName}` and nothing else — so a package declaring a required parameter could not be
scaffolded through it *by any invocation*: the server refused on `brand`, and the run exited
non-zero **having already written the whole Next.js app**. Measured: 15 files,
`app/[...slug]/page.tsx`, a correct `.env.local` — reported by one line that mentions none of them.
The next command is where it bites: `shio init <same-dir> --yes` then refuses with *"Target
directory is not empty"*, which is true and unexplainable from anything the caller was told, and the
recovery is to delete a directory whose contents the tool never admitted writing. **The order was
the defect** — `params` are validated against the schema before a byte is written on every other
path, and here the scaffold ran first and the content application validated second.

So: the same `--param`/`--params` flags `apply` has, through **one parser** (`blueprint-params.mjs`
— SH245, since the second copy of a verb is the one that stops matching), plus a
`missingParams` check that runs **before** `writeProjectFiles`. It restates the server's rule
exactly — in `required`, no `default`, no value — because anything looser would refuse a run the
server would have accepted, which is a worse failure than the one being fixed. Only when a `--site`
is given, since that is precisely when the content half runs and therefore the only case that was
ever refused: the refusal moves earlier, it does not widen. Everything else stays the server's —
an unknown name and a wrong type get `ShBlueprintParams`' teaching 400 with the declared list and
the nearest match, and a second copy of those rules here would be two validators that eventually
disagree about one schema.

**Validated rather than merely reported, and the choice is not cosmetic.** Printing `✓ Created …
(15 files)` before the refusal makes the run honest and *still* leaves the directory that blocks
the retry. Only checking first makes the refusal true.

**And running the fix against a live instance found the same defect with a second trigger.** With
`brand` supplied, a `--site` naming a site that does not exist refused *after* the fifteen files,
exactly as the parameter had — and the remedy the server's own message names (`createSite: true`)
was one `init` could not express, because `--create-site` was on `apply` and not here. Both closed:
the flag exists and travels to the request, and without it an absent site is a teaching refusal
before the write. A resolve that fails for any reason *other* than 404 is deliberately swallowed —
this is a courtesy check ahead of a call that will judge authoritatively, and turning a network blip
into a broken scaffold would be a worse trade than the one it fixes.

**A scaffold may only declare a version the registry can serve, and that is now a table (SH446).**
The replication chain ran green to its last step and the last step did not install: `shio init`
scaffolds the Next.js app that serves a converted site, prints `npm install` as the next command,
and that command failed — `ETARGET — No matching version found for @viglet/shio-client@^2026.3.1`,
on both scaffolds. The range came from **the workspace**, which is at `2026.3.1` and unpublished
while the registry has `2026.2.1`, so *every version bump re-broke every scaffold* and nothing said
so, because nothing in the repository installs from the registry. Worth stating as the end of the
chain rather than a packaging chore: `clone`, `propose`, `convert` and `verify` all reported
success, the gate said `ok — 0 error(s)`, and the replica was viewable by nobody. **P9 failing at
the last hop** — every cheap textual proof passes and the perception loop never closes.

`create-shio-app/src/published.mjs` is the one table, and `resolveNeeds`' comment used to assert the
exact opposite of what it did ("read from the workspace … so a release cannot leave the generated
packages pointing at a version nobody published" — reading it from the workspace is what
*guarantees* that). The ranges are **caret on the latest published version**, so `^2026.2.1`
resolves today *and* resolves to `2026.3.1` the day it ships: the release does not have to edit the
scaffold. `scaffold-versions.test.mjs` holds the tree against the table and pins the mechanism —
it asserts the template's range is **not** the workspace's — and the SH448 conformance suite hands
the result to **npm**, which is the only thing that can say whether a range is real. That last
assertion was red from the day the suite shipped and is green now.

**One row is `null` and that is a release, not a range.** `@viglet/shio-model` and
`@viglet/shio-sections` have never been published (E404), and `marketing-site`/`landing` carry a
front end that *imports* the vocabulary — so no version choice makes them installable. Recorded as
a row rather than left absent, because a guess would look like a fix; `emit` prints the gap every
time it runs, at the moment the file is written rather than leaving it for a stranger's
`npm install`. **`shio init` with no `--blueprint` is unaffected** — it carries only published
packages, which is why the default path installs today.

**Two templates, and neither serves an authorable replica (SH502).** SH446 asked whether the
scaffold installs and got that; the half it left unmeasured is the one an authorable site needs.
Measured, one `npm install --dry-run` per template: the built-in one **exits 0** and carries no
sections renderer — nothing under `app/` or `lib/` reads a Page's `sections` — and the
`marketing-site` blueprint, the only scaffold that draws them, answers **E404 on
`@viglet/shio-sections`**. So a fidelity replica has two working paths (Shio's own renderer, or a
front end it does not need) and an authorable replica has none: its content *is* sections.
**Publishing `shio-sections` is the whole fix**, and the version write is the report while the
publish is the behaviour — only the first shipped, which is the split the `shio-replication-test`
skill already names. Until it lands, `init --site` refuses the built-in template for a site whose
Pages carry sections: two calls (`find` type=Page, then one `read`), a courtesy check that swallows
every read failure so the no-instance path SH119 requires still works, and the message names
`/sites/<site>/default/en-us/` as the front end that exists today rather than recommending a
blueprint whose install fails. The refusal's own text is read from `published.mjs`, so the day the
package ships it recommends `--blueprint marketing-site` instead — and the conformance measurement
flips with it: while the table says unpublished, npm **must** refuse that template, and once it does
not the same line requires a clean install.

## The three packages that carry an index page (SH216)

SH156 gave `landing` and `marketing-site` a front end and deliberately stopped there. `blog`, `docs`
and `product-catalog` do not compose sections — their models are ordinary posts with an HTML field,
which the starter's catch-all already renders — so the section overlay would ship them a dependency
they never import and let three packages claim a front end no better than the plain starter's.

What each was actually missing is **one page the starter cannot provide, because it is about the
model rather than about any page**: the posts newest first at `/blog`, a navigation tree at `/docs`,
a grid at `/products`. Three shapes, which is exactly why it is three overlays and not one.

### Where a per-package overlay lives, which was the open question

**`create-shio-app/overlays/<name>/`.** The section overlay lives in `shio-sections` because *the
vocabulary owns how it renders* — the same argument that puts its post-type defs in
`src/post-types.ts`. These have no vocabulary, so the owner is whoever owns the starter, which is
`create-shio-app`.

**Not beside the blueprint**, which was the tempting answer: `blueprints/<name>/files/` is the tree
`emit` *writes*, so a source overlay in the same directory would put input and output in one place
and leave the drift test comparing a thing with itself.

**None of the three declares `needs`**, and that is the whole distinction from the section overlay:
an index page uses the SDK the starter already depends on, while a section overlay brings a
vocabulary with it. `ShBlueprintApplyTest` asserts the route rather than a file count — a count
would pass just as well if the overlay carried the wrong file.

### Two things the work turned up

- **The SDK's two-call design shapes what an index page can show.** `query` returns *summaries* —
  title, summary, furl, date — and no `attrs`. So the blog list sorts on the post's `date` rather
  than the Article's own `publishedAt`, and the author is on the post's page: fetching fifty posts to
  print fifty bylines is the N+1 that design exists to refuse. The catalogue is the one page that
  pays for a second call, because a catalogue without prices is not a catalogue, and it bounds the
  cost with a named `PAGE_SIZE` rather than a `size` somebody passes.
- **Carrying a front end means declaring the parameter it uses.** The starter tree contains
  `{{projectName}}`, which only the two section packages declared — so the first emit made
  `GET /agent/blueprints` answer **400** for the whole catalogue, and forty-one tests failed on a
  registry that could not load. The teaching error was right: *a placeholder with no parameter behind
  it would silently leave a gap in the content*. Adding `projectName` to the three manifests is the
  fix, and the failure is the argument for the check.

## A stranger's package cannot take the catalogue down (SH217)

SH96 shipped the local half of the registry: the classpath, plus one directory an operator names.
That is a real trust model — whoever can set `shio.blueprint.path` can already put a jar on the
classpath — but it left one property the *community* half needs before it needs an index.

**A package that failed validation failed the whole registry.** Not hypothetical: during SH216 a
single first-party manifest missing a single parameter made `GET /api/v2/agent/blueprints` answer
4xx for all six packages, and forty-one tests failed on a catalogue that could not load. A directory
anybody can drop a folder into is that defect with a worse author.

So a **local** package that will not load is now refused on its own, named, and reported; the rest of
the catalogue is unaffected.

### The three cases, which are one rule read from three sides

| What happened | What the registry does | Why |
|---|---|---|
| A **first-party** package will not load | still fails the whole registry | it is our build defect, and shipping five of six would hide it until somebody counted |
| A **local** package will not load | refused alone, reported as a `BLUEPRINT` incident | its author is not somebody the operator chose to trust with the catalogue |
| A **local** package collides with a first-party name | still fails the whole registry | a conflict about what a name *means*, not one package's problem — SH96's reasoning is unchanged: shadowing rewrites every existing `blog` apply, ignoring makes the operator's package do nothing |

**Refused is not softened.** A package that names its own site is still unavailable to apply, and the
reason still says which rule it broke. What changed is the blast radius — the old behaviour was not
stricter validation, it was the same validation charged to everybody.

### Where the refusal surfaces, and why not in the catalogue

`GET /agent/diagnostics`, as a `BLUEPRINT` incident. A refused package is not something the instance
carries, so it does not belong in the list of what it carries — and wrapping that list in an envelope
would charge every caller tokens on every call to be told nothing was refused (P3). An incident costs
nothing until there is one.

**That is also the validation pass SH217 asked for, with no new contract**: an author points an
instance at their directory and reads the refusal out of the surface they were going to publish
against. The reported reason is the teaching error's **detail**, not its title — the title is the
class of problem ("Blueprint package is malformed") and the detail is the sentence naming which rule
broke, which is the round trip the error format exists to remove.

Distribution — the `shio-blueprint-*` convention, a curated index, a provenance signal — is **SH643**,
because a curated index needs a place to live and somebody to say what goes in it. That is a decision
about the project rather than a change to it.

## Reloading the local package directory (SH219)

`ShBlueprintRegistry` caches on first access and never invalidated. That was **free** while the
classpath was the only source — packages cannot change inside a running JVM, so a cache with no
invalidation was not a cache with a bug, it was a constant. SH96 changed the premise: an operator
can drop a package into `shio.blueprint.path` and nothing sees it until the process restarts, which
is the wrong default for a directory whose whole point is iterating on a package.

`POST /api/v2/agent/blueprints/reload`, **admin-only**.

### Explicit, not a watcher

A registry that re-scanned itself would re-run validation at a moment nobody chose, so a half-saved
manifest would change what the catalogue says between two applies — an author's editing state
becoming every caller's problem. An explicit reload puts the moment in a person's hands, which is
the same argument SH96 makes about the directory: **the trust model is that somebody decided.**

SH217 made that failure smaller without removing it, and the difference is worth stating: a
half-saved *local* manifest is now refused on its own and the catalogue survives, so a badly-timed
reload costs one package rather than all of them. What a reload still cannot make safe is a
**collision**, which fails the registry by design — so the answer names how many packages loaded,
and a caller who gets fewer than they expected sees it in the reply rather than by applying one and
finding out.

### Why admin, and why it answers with counts

The role is the same statement SH96 makes about the path: whoever configured it is vouching for what
is in it, so whoever reloads it should be somebody the instance already trusts with that. It is the
one path under `/api/v2/agent/**` that needs more than `authenticated()`, and it has to precede the
namespace's own rule **in both security chains** or the blanket rule answers first and the role is
never checked.

It answers with `loaded` and `rejected` rather than the catalogue. A caller who has just added a
package wants to know whether it is there; re-sending every summary to say so would charge them a
listing they can ask for when they want it (P3). And `rejected` carries SH217's reasons, which makes
this **the author's loop closed in one call**: drop the package in, reload, read why it was refused —
no restart, and no second surface to learn.

**It rebuilds rather than merges.** A registry that only ever grew would keep answering `apply` for a
package whose files an operator has deleted.
