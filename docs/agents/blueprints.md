# Blueprints: appliable starting points

> Split out of the repo-root `agents.md` (see its routing table for the index).
> Read this before touching the area it covers. Detail here is authoritative.

### Blueprints: appliable starting points (Block J)

A **blueprint** is a section of a site the instance can apply: a content model, a folder
tree, sample pages, the parameters that make it somebody's, and the promise it keeps about
what exists afterwards (law P8, skipping steps needs *appliable* units, not recipes).
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
- **Substitution is `{{name}}` and nothing else**: no conditionals, no expressions, so the
  same parameters always produce the same site (P5). `site` is a reserved parameter, always
  available, never declarable. An unknown parameter is a teaching 400, never ignored.
- **Everything is validated at load** (`ShBlueprintRegistry`): a missing referenced file, a
  path that escapes the package, an unknown manifest key, a `{{placeholder}}` with no
  declared parameter, a `verify.posts` entry the content never declares. These are the
  *author's* mistakes; a malformed package is a **409, not a 400**, nothing the caller sent
  caused it. `ShBlueprintRegistryTest` runs without a Spring context, so it is cheap enough
  to run on every change to a package.
- **The model is provisioned, never upserted.** An existing post-type of the same name is
  left exactly as it is (including a field a curator added) and the response *names the fields
  it lacks*, the bootstrap `Page` (SH71: `title` + `content`) versus the section
  vocabulary's `Page` is the collision this actually happens to. SH152 reconciles. **It bit the
  console smoke check (SH1144):** `shio-react/e2e/provision.mjs` applied marketing-site to a clean
  instance without the opt-in, so the apply kept the bootstrap `Page` and refused the content at
  `ops[20]` (`undeclared-field`), and every check that provisions first walked a site with no pages
  on exactly the instance CI starts from. It sends `mergePostTypes: true` now, which is additive, and
  `e2e-fixture-site.test.mjs` holds it. Its log line reads the change count from `content.changed`,
  since the apply's content half is an `ShAgentChangeSet`. It read `applied.changed`, which is
  always absent, so every run printed `changed=?` (SH1176). A clean instance now reads `changed=45`. **Whether they should differ at all (SH1163) was measured,
  not argued:** the bootstrap `Page` is `title` + `content` (HTML Editor); the vocabulary's,
  marketing-site's and landing's add `description` and `sections` and use Ace HTML for `content`;
  nextjs-starter's has `summary` instead. So reseeding the bootstrap with the vocabulary's type would
  still collide with nextjs-starter, and no single seed agrees with every package. What changed
  instead is the refusal: without the opt-in, an apply whose content is refused `undeclared-field`
  while an existing type lacked fields is re-told as `400 blueprint-needs-merge`, naming each type
  and its missing fields and the two doors that take the opt-in (`--merge-post-types`,
  `"mergePostTypes": true`). MCP's `shio_apply` still takes neither, by SH152's token-ceiling choice.
- **A plan writes nothing and needs no write transaction (SH153).** It used to provision the
  package's post-types inside its own transaction and roll back, accurate, and it meant a plan
  could not run on a read-only credential, a replica, or inside a `readOnly` transaction, which
  is the wrong constraint on a surface whose safety story is "look before you write". Instead the
  plan **declares** what it would create, the site, and each post-type with the fields it would
  carry, and the batch planner resolves against that: `ShAgentBatchService.Declared`, the
  pending-folder idea one resource wider. Two rules keep it honest. It is **additive, never a
  substitute**: a live post-type is still resolved live and the declared names are *unioned* with
  its own, so a plan can never come back greener than the run it predicts, with `mergePostTypes`
  off, nothing is declared for an existing type and SH168's "a real run will refuse this write"
  survives. And it is **honoured on a dry run only**, so a declaration cannot stand in for an
  apply that did not happen. `ShBlueprintWriteService.plan` is `@Transactional(readOnly = true)`,
  which makes that a claim the database enforces rather than one a test has to remember.
- **The server never writes a project file, and never substitutes one.** `files` are returned
  and the CLI writes them (never over an existing one without `--force`); an MCP-only caller
  is told what it has *not* got, **and since SH166, that the bytes are templates**. Each row
  of an apply names the `{{placeholder}}`s standing in that file, read from the **bytes** and
  not from the manifest's parameter list (a package may declare a parameter its files never
  use, and reporting that one sends the caller hunting for something that is not there). Every
  name has its resolved value in the `params` the same response echoes, defaults included,
  which is what makes the report actionable rather than a warning. Server-side substitution
  was refused for a reason worth keeping: the bodies travel on a **read**
  (`?include=files`, or `shio://blueprint/{name}/files`) that has no parameter values, and
  giving a cacheable GET a write's arguments fights SH116.
- **An apply records itself (SH155).** It writes an SH114 note scoped to the site, keyed
  `blueprint.<name>`, saying which package and version built it and with which parameters,
  and the response names the key in `recordedAs`. One note **per package**, because a site can
  receive a `landing` and then a `blog` and a single key would erase the first; parameters are
  sorted and `site` dropped so the same apply writes the same bytes (P5). A memory note rather
  than a table because it then reaches the next session through the context pack it already
  reads (P2). Written *after* the writer's transaction, so a note that cannot be filed leaves
  the apply standing, the response just does not claim a record it does not have. This is not
  drift detection: it makes "is this site still what the blueprint says" *askable*, and the
  answer is still a `dryRun` away.
- **Packages load from the classpath, plus one directory an operator names (SH96).**
  `shio.blueprint.path`, off by default. That *is* the trust model: whoever can set it could
  already put a jar on the classpath, so it grants no new authority, and the person who set it
  is the one vouching for what is in it. Three rules, a **name collision refuses** at load
  naming both origins (shadowing would change what every existing `blog` apply does; ignoring
  would make the operator's package do nothing); a configured path that is not a readable
  directory **fails** rather than loading fewer packages; and the **same validation runs**, so
  "it loaded" means the same thing whichever source it came from. Every package carries its
  `origin`, reported on the listing for local ones only. No URL fetching, no registry
  resolution, that is the community half (SH217).

**The first-party set (SH94, SH119, SH156):** `blog` · `docs` · `product-catalog` ·
`marketing-site` · `landing` · **`nextjs-starter`**. The last one is the
`create-shio-app` template as a package (SH119). **Three packages carry front-end `files`,
all generated by the same `pnpm -C create-shio-app run emit`**, `nextjs-starter` gets the
template tree; `marketing-site` and `landing` get **the same tree plus the section overlay**
in `shio-sections/front-end/` (replace-by-path, so the section-aware catch-all *is*
`app/[...slug]/page.tsx`, plus `lib/sections.ts` and a merged `package.json` dependency whose
range is read from the workspace). `test/emit.test.mjs` fails the build on drift and asserts
the overlay changes **exactly three files**, if that grows, the overlay has become a fork.
**If you change `create-shio-app/template/` or `shio-sections/front-end/`, run `emit`.**
`blog`/`docs`/`product-catalog` are left out on purpose: they compose no sections, so the
overlay would ship a dependency they never import, what they want is a listing page (SH216).
The overlay is only usable because **`@viglet/shio-model` and `@viglet/shio-sections` now
publish** (SH156); before that, a package carrying a page that imports the vocabulary would
have produced a project whose `npm install` fails. Substitution is split by who
knows the answer: the server substitutes a package's *content*, the client its *carried
files* (the project's own name is local identity, and the server never writes a project
file), and the JS regex is character-for-character `ShBlueprintParams`' so the two halves
cannot disagree. A package's path refs are checked per *segment* for `..`, not per
substring, or `app/[...slug]/page.tsx` would be rejected as traversal. Each declares a `verify` promise, checked at load against its own content and
after an apply against the instance (alongside the SH99 lint); `shio apply --blueprint` exits
non-zero when that fails.


**`shio init --blueprint` checks its preconditions before it writes anything (SH447).** It did not,
and the two halves of that compounded. `init` had **neither `--param` nor `--params`**, it sent
`{projectName}` and nothing else, so a package declaring a required parameter could not be
scaffolded through it *by any invocation*: the server refused on `brand`, and the run exited
non-zero **having already written the whole Next.js app**. Measured: 15 files,
`app/[...slug]/page.tsx`, a correct `.env.local`, reported by one line that mentions none of them.
The next command is where it bites: `shio init <same-dir> --yes` then refuses with *"Target
directory is not empty"*, which is true and unexplainable from anything the caller was told, and the
recovery is to delete a directory whose contents the tool never admitted writing. **The order was
the defect**, `params` are validated against the schema before a byte is written on every other
path, and here the scaffold ran first and the content application validated second.

So: the same `--param`/`--params` flags `apply` has, through **one parser** (`blueprint-params.mjs`,
SH245, since the second copy of a verb is the one that stops matching), plus a
`missingParams` check that runs **before** `writeProjectFiles`. It restates the server's rule
exactly (in `required`, no `default`, no value) because anything looser would refuse a run the
server would have accepted, which is a worse failure than the one being fixed. Only when a `--site`
is given, since that is precisely when the content half runs and therefore the only case that was
ever refused: the refusal moves earlier, it does not widen. Everything else stays the server's,
an unknown name and a wrong type get `ShBlueprintParams`' teaching 400 with the declared list and
the nearest match, and a second copy of those rules here would be two validators that eventually
disagree about one schema.

**Validated rather than merely reported, and the choice is not cosmetic.** Printing `✓ Created …
(15 files)` before the refusal makes the run honest and *still* leaves the directory that blocks
the retry. Only checking first makes the refusal true.

**And running the fix against a live instance found the same defect with a second trigger.** With
`brand` supplied, a `--site` naming a site that does not exist refused *after* the fifteen files,
exactly as the parameter had, and the remedy the server's own message names (`createSite: true`)
was one `init` could not express, because `--create-site` was on `apply` and not here. Both closed:
the flag exists and travels to the request, and without it an absent site is a teaching refusal
before the write. A resolve that fails for any reason *other* than 404 is deliberately swallowed,
this is a courtesy check ahead of a call that will judge authoritatively, and turning a network blip
into a broken scaffold would be a worse trade than the one it fixes.

**A scaffold may only declare a version the registry can serve, and that is now a table (SH446).**
The replication chain ran green to its last step and the last step did not install: `shio init`
scaffolds the Next.js app that serves a converted site, prints `npm install` as the next command,
and that command failed, `ETARGET — No matching version found for @viglet/shio-client@^2026.3.1`,
on both scaffolds. The range came from **the workspace**, which is at `2026.3.1` and unpublished
while the registry has `2026.2.1`, so *every version bump re-broke every scaffold* and nothing said
so, because nothing in the repository installs from the registry. Worth stating as the end of the
chain rather than a packaging chore: `clone`, `propose`, `convert` and `verify` all reported
success, the gate said `ok — 0 error(s)`, and the replica was viewable by nobody. **P9 failing at
the last hop**, every cheap textual proof passes and the perception loop never closes.

`create-shio-app/src/published.mjs` is the one table, and `resolveNeeds`' comment used to assert the
exact opposite of what it did ("read from the workspace … so a release cannot leave the generated
packages pointing at a version nobody published", reading it from the workspace is what
*guarantees* that). The ranges are **caret on the latest published version**, so `^2026.2.1`
resolves today *and* resolves to `2026.3.1` the day it ships: the release does not have to edit the
scaffold. `scaffold-versions.test.mjs` holds the tree against the table and pins the mechanism,
it asserts the template's range is **not** the workspace's, and the SH448 conformance suite hands
the result to **npm**, which is the only thing that can say whether a range is real. That last
assertion was red from the day the suite shipped and is green now.

**One row is `null` and that is a release, not a range.** `@viglet/shio-model` and
`@viglet/shio-sections` have never been published (E404), and `marketing-site`/`landing` carry a
front end that *imports* the vocabulary, so no version choice makes them installable. Recorded as
a row rather than left absent, because a guess would look like a fix; `emit` prints the gap every
time it runs, at the moment the file is written rather than leaving it for a stranger's
`npm install`. **`shio init` with no `--blueprint` is unaffected**, it carries only published
packages, which is why the default path installs today.

**Two templates, and neither serves an authorable replica (SH502).** SH446 asked whether the
scaffold installs and got that; the half it left unmeasured is the one an authorable site needs.
Measured, one `npm install --dry-run` per template: the built-in one **exits 0** and carries no
sections renderer (nothing under `app/` or `lib/` reads a Page's `sections`) and the
`marketing-site` blueprint, the only scaffold that draws them, answers **E404 on
`@viglet/shio-sections`**. So a fidelity replica has two working paths (Shio's own renderer, or a
front end it does not need) and an authorable replica has none: its content *is* sections.
**Publishing `shio-sections` is the whole fix**, and the version write is the report while the
publish is the behaviour, only the first shipped, which is the split the `shio-replication-test`
skill already names. Until it lands, `init --site` refuses the built-in template for a site whose
Pages carry sections: two calls (`find` type=Page, then one `read`), a courtesy check that swallows
every read failure so the no-instance path SH119 requires still works, and the message names
`/sites/<site>/default/en-us/` as the front end that exists today rather than recommending a
blueprint whose install fails. The refusal's own text is read from `published.mjs`, so the day the
package ships it recommends `--blueprint marketing-site` instead, and the conformance measurement
flips with it: while the table says unpublished, npm **must** refuse that template, and once it does
not the same line requires a clean install.

## The three packages that carry an index page (SH216)

SH156 gave `landing` and `marketing-site` a front end and deliberately stopped there. `blog`, `docs`
and `product-catalog` do not compose sections, their models are ordinary posts with an HTML field,
which the starter's catch-all already renders, so the section overlay would ship them a dependency
they never import and let three packages claim a front end no better than the plain starter's.

What each was actually missing is **one page the starter cannot provide, because it is about the
model rather than about any page**: the posts newest first at `/blog`, a navigation tree at `/docs`,
a grid at `/products`. Three shapes, which is exactly why it is three overlays and not one.

### Where a per-package overlay lives, which was the open question

**`create-shio-app/overlays/<name>/`.** The section overlay lives in `shio-sections` because *the
vocabulary owns how it renders*, the same argument that puts its post-type defs in
`src/post-types.ts`. These have no vocabulary, so the owner is whoever owns the starter, which is
`create-shio-app`.

**Not beside the blueprint**, which was the tempting answer: `blueprints/<name>/files/` is the tree
`emit` *writes*, so a source overlay in the same directory would put input and output in one place
and leave the drift test comparing a thing with itself.

**None of the three declares `needs`**, and that is the whole distinction from the section overlay:
an index page uses the SDK the starter already depends on, while a section overlay brings a
vocabulary with it. `ShBlueprintApplyTest` asserts the route rather than a file count, a count
would pass just as well if the overlay carried the wrong file.

### Two things the work turned up

- **The SDK's two-call design shapes what an index page can show.** `query` returns *summaries*,
  title, summary, furl, date, and no `attrs`. So the blog list sorts on the post's `date` rather
  than the Article's own `publishedAt`, and the author is on the post's page: fetching fifty posts to
  print fifty bylines is the N+1 that design exists to refuse. The catalogue is the one page that
  pays for a second call, because a catalogue without prices is not a catalogue, and it bounds the
  cost with a named `PAGE_SIZE` rather than a `size` somebody passes.
- **Carrying a front end means declaring the parameter it uses.** The starter tree contains
  `{{projectName}}`, which only the two section packages declared, so the first emit made
  `GET /agent/blueprints` answer **400** for the whole catalogue, and forty-one tests failed on a
  registry that could not load. The teaching error was right: *a placeholder with no parameter behind
  it would silently leave a gap in the content*. Adding `projectName` to the three manifests is the
  fix, and the failure is the argument for the check.

## A stranger's package cannot take the catalogue down (SH217)

SH96 shipped the local half of the registry: the classpath, plus one directory an operator names.
That is a real trust model, whoever can set `shio.blueprint.path` can already put a jar on the
classpath, but it left one property the *community* half needs before it needs an index.

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
| A **local** package collides with a first-party name | still fails the whole registry | a conflict about what a name *means*, not one package's problem, SH96's reasoning is unchanged: shadowing rewrites every existing `blog` apply, ignoring makes the operator's package do nothing |

**Refused is not softened.** A package that names its own site is still unavailable to apply, and the
reason still says which rule it broke. What changed is the blast radius, the old behaviour was not
stricter validation, it was the same validation charged to everybody.

### Where the refusal surfaces, and why not in the catalogue

`GET /agent/diagnostics`, as a `BLUEPRINT` incident. A refused package is not something the instance
carries, so it does not belong in the list of what it carries, and wrapping that list in an envelope
would charge every caller tokens on every call to be told nothing was refused (P3). An incident costs
nothing until there is one.

**That is also the validation pass SH217 asked for, with no new contract**: an author points an
instance at their directory and reads the refusal out of the surface they were going to publish
against. The reported reason is the teaching error's **detail**, not its title, the title is the
class of problem ("Blueprint package is malformed") and the detail is the sentence naming which rule
broke, which is the round trip the error format exists to remove.

Distribution (the `shio-blueprint-*` convention, a curated index, a provenance signal) is **SH643**,
because a curated index needs a place to live and somebody to say what goes in it. That is a decision
about the project rather than a change to it.

## Reloading the local package directory (SH219)

`ShBlueprintRegistry` caches on first access and never invalidated. That was **free** while the
classpath was the only source, packages cannot change inside a running JVM, so a cache with no
invalidation was not a cache with a bug, it was a constant. SH96 changed the premise: an operator
can drop a package into `shio.blueprint.path` and nothing sees it until the process restarts, which
is the wrong default for a directory whose whole point is iterating on a package.

`POST /api/v2/agent/blueprints/reload`, **admin-only**.

### Explicit, not a watcher

A registry that re-scanned itself would re-run validation at a moment nobody chose, so a half-saved
manifest would change what the catalogue says between two applies, an author's editing state
becoming every caller's problem. An explicit reload puts the moment in a person's hands, which is
the same argument SH96 makes about the directory: **the trust model is that somebody decided.**

SH217 made that failure smaller without removing it, and the difference is worth stating: a
half-saved *local* manifest is now refused on its own and the catalogue survives, so a badly-timed
reload costs one package rather than all of them. What a reload still cannot make safe is a
**collision**, which fails the registry by design, so the answer names how many packages loaded,
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
this **the author's loop closed in one call**: drop the package in, reload, read why it was refused,
no restart, and no second surface to learn.

**It rebuilds rather than merges.** A registry that only ever grew would keep answering `apply` for a
package whose files an operator has deleted.

## A re-apply defaults to what the site was built with (SH220)

SH155 records the package, its version and its parameters as an instance-memory note. Re-applying
still meant re-supplying every `--param` from memory or from a transcript, **and that is a
correctness problem, not a tedium one.** A parameter the operator misremembers does not fail: it
rebuilds the site with a different brand, headline or CTA, and `changed: n` reports it as ordinary
work. The safe default is the value that produced the site.

`shio apply --blueprint <name> --site <site>` now reads that record and fills in what the caller did
not pass.

**An explicit `--param` always wins.** The record is a default, not a policy, an operator
re-applying with a new headline is doing the thing the flag is for, and a record that overrode it
would make the site unchangeable through the command that changes it.

**And it says which values it defaulted**, always, before the apply runs. That is the load-bearing
half: a parameter that was silently supplied is one nobody reviews, which would make this feature a
second way to rebuild a site wrong rather than a fix for the first. Nothing is printed when nothing
was defaulted, a line saying "0 parameters taken" on every first apply is how a reader learns to
skip the output that matters.

### The hazard the task did not name: the record is prose, and it can be cut

`ShBlueprintRecord.noteFor` writes a sentence bounded at 400 characters, and when the parameters do
not fit it ends with `(+3 more)`. **That admission is the only reason the record is safe to parse
back.** A reader that dropped it would supply *some* parameters and omit others, the same defect
this task is about, arriving from the other side and silently. So `truncated` travels with the
values, and the caller is told: *the record does not name 3 of its parameters, so those fall back to
the package's defaults.* Worded as a limit rather than a failure, because the record did not lose
them; it never held them.

Two smaller decisions in the parser, both from cases that exist: a value is everything after the
**first** `=` (a CTA href with a tracking parameter is ordinary), and the record chosen is the one
whose blueprint name matches, a site can carry several, and re-applying `docs` into a site that
also has `blog` must not inherit `blog`'s brand.

### The format now lives in two languages

Written in Java, parsed in `cli/src/blueprint-record.mjs`, with nothing joining them. So
`ShBlueprintRecordTest` pins the literal sentence and names the CLI file to change with it,
**a reworded sentence would leave the CLI silently defaulting nothing, and "nothing to default" is
exactly what a first apply looks like.** Pinned as a string rather than by re-implementing the
parser in Java, because a second implementation of the format is what the pin is protecting against.

*(Found while writing it: the first parser read `1.0.0` as `1`, because its terminator alternation
included `.`. That looks right in every test that only checks the package name.)*

## Two templating languages, one delimiter (SH395, partial)

SH395 asks why six packages ship and **not one declares a `Theme`, a `PageLayout` or a binding**, so
applying any of them leaves the pages on SH169's labelled fallback. §XI.16 read that as an authoring
omission. It is not.

**A blueprint parameter is `{{name}}` and so is a Handlebars expression.** A package carrying a
layout was refused at load *and* again at substitution, `{{post.title}}`, `{{theme.css}}` and
`{{#query}}` all read as parameters nobody declared. **The format could not express a layout**, which
is why nobody wrote one.

The unknown-name refusal is now lifted inside the render fields (`HTML`, `CSS`, `JAVASCRIPT`) and
nowhere else. Substitution still runs there, which is the point: a layout may say `{{blogTitle}}` in
its masthead and get the caller's value, while the renderer's own expressions pass through untouched.

**What the exemption costs, stated rather than hidden:** a typo'd parameter inside a template
(`{{blogTitel}}`) is no longer caught, and cannot be, the check's basis is that an undeclared name
is a mistake, and inside Handlebars an undeclared name is the normal case. Bounded by being
field-scoped, and outside those fields the refusal is unchanged. The registry and the substituter
read **one** set, because two would drift in a direction where the quiet failure writes
`{{post.title}}` into somebody's page.

A content document may also declare `postTypes` and `postTypeLayout` now, which compile to
`render.provision` and the site binding. The desired-state service already emitted both *before* any
folder or post, so the ordering the traps are about was never the problem.

### Why this is partial

**No package ships a layout yet, so SH395's symptom stands** and the task is `⏳` rather than
shipped. Trying to write one for `blog` turned up the next question: `Theme` and `PageLayout` are
seeded system types, but a blueprint apply in a bare instance did not resolve them, so a package
that declares a template needs its post-types question answered before the JSON is worth writing.
Recorded in §XI.16 rather than guessed at here.

### And the two gaps behind it (SH395, completed)

The collision above was the first blocker. Removing it exposed two more, both of the same shape,
a capability that existed and could not be *reached* from a package:

**`render.provision` was reachable only by naming vocabulary.** The op has always ensured the render
five (`PageLayout`, `Region`, `Theme`, `Redirect`, `SiteScripts`), but the only way a document could
emit it was `postTypes`, which is the SH95 *section vocabulary* list. So a document that declared a
`PageLayout` post could not ask for the type that post is of, and applying one into a bare instance
failed on a post-type nobody could have provisioned. A document that **writes a template** now
implies the provision, inferred rather than declared, which this codebase is usually reluctant to
do, and right here because a document writing a `PageLayout` has already said it needs `PageLayout`.
Making the author say it twice is the trap the task is about.

**And a plan refused what the same plan would have created**: SH153, one type-family along.
`Declared.withPostTypesFrom` read only `data.types`, so the render five were invisible to a dry run:
an apply worked and `--dry-run` did not, which is the worst of the three outcomes, because the dry
run is the call an agent makes to find out whether the real one is safe.

`blog` now ships a `Theme`, a `PageLayout` and an `Article → layout` binding, and the assertion
**renders the applied site** rather than counting its content, `contains("--ink")` proves the theme
resolved, and `contains("The Acme Blog")` proves the package's own parameter still substituted inside
the template, which is the half the exemption had to keep working.

`ShBlueprintApplyTest` gained its own database on the way. It applies whole packages and asserts
instance-wide counts, and on the shared context another test's minimal `Theme` post-type, created
without the `CSS` field this package writes, made the apply refuse a field. SH315's rule arriving
late, and the same isolation SH635's flake wanted.

**The other five packages still ship no design.** The mechanism is theirs to use; `marketing-site` is
the one SH396 needs.

## The generator was on the replication path (SH518)

`shio apply --blueprint marketing-site --site X` wrote everything it promised, eight post-types
*as the package then stood*, four folders, thirteen posts, three pages composed of ten sections,
every field authorable (SH873 and SH881 have since taken it to eleven), and then
`/preview/X/` answered **400, binds no layout for post-type 'Page'**. The generator that writes a
`Theme` and a `PageLayout` existed, on the **replication** path: `convert` writes both. So a site
that started from a blueprint could not be looked at inside Shio, and getting one to render meant
harvesting those two posts from a throwaway conversion, retitling them and binding the site by hand.

That is backwards for the product. **Replication is the unusual case; a blueprint is how something
new starts**, and it is the demo a stranger asks for.

### Declared in the package, not generated in Java

§XVII.70 offered both. Declaring won for two reasons:

- SH395 had just made a package able to carry its design, and a blueprint stays *a declaration of
  content* rather than gaining a code path that writes some of it.
- **A converter's layout and a package's are different artefacts.** `convert`'s exists for
  replication *fidelity*, it reproduces a captured page. A package's is a *designed starting
  point*. Sharing them would couple the product's design to the fidelity of somebody else's site,
  and the first time one needed to change, the other would be the reason it could not.

`marketing-site` and `landing` now ship a section-rendering layout, a theme and a `Page → layout`
binding. The assertion **renders the applied page** and finds `data-shio-section`, `data-shio-prop`
(so it is editable, not merely visible) and the theme's own custom property.

### The token regression, measured and raised

P3 makes this a build failure rather than a footnote, and it was: `build-marketing-site` went
**2700 → 3375** and `add-type-and-10-posts` **1800 → 2063**.

The trim-first rule was applied and is the wrong lever here, what grew is the *report* of ops the
caller asked for, so shrinking it means saying less about what was written. And the cost it replaces
is worse on this very metric: the four hand-run ops the task records cost a throwaway conversion and
four calls no budget counts, because nobody could automate them. Raised to 3400 and 2100 with that
written beside the numbers; the ratio still clears its floor at 2.3x.

`add-type-and-10-posts` moved for a reason that is not its own task, it shares an instance with the
blueprint catalogue, and that is stated where the number is, because a figure that moved for
somebody else's change is the one a later reader will misattribute.

`ShBlueprintRendersTest` gets its own database: post-types are instance-wide, and
`ShBlueprintApplyTest` bends one **on purpose** to prove a mismatch is reported, so every later apply
in that class meets it. This assertion was written there first and failed on exactly that.

### The navigation the package already carried and never used (SH873)

Found by **authoring** rather than replicating, the second task shape SH603 asked for, and the
first thing it surfaced. Building a pharmacy site from `marketing-site` produced a menu reading
*Contato, Farmacêutico responsável (Farmácia Vitalis, Farmácia Vitalis, Produtos) Farmácia
Vitalis*: five pages, six entries, alphabetical, the home page buried in the middle under its own
brand name. The nav was

```
{{#query type="Page" sort="title" limit="20"}}<li><a href="{{link}}">{{title}}</a></li>{{/query}}
```

and `title` is also what the layout puts in `<title>`, so **one string served two audiences with
opposite needs**, a search result wants the brand appended, a menu wants two words. `sort="title"`
was the only ordering available and every page was an entry.

**The fix was built three blocks earlier.** SH382's `{{#menu title=}}` renders a curated menu out of
ordinary `Menu`/`MenuItem` content, and its javadoc enumerates this defect as its own reason for
existing: *"a label that differs from the page title, groups that are not folders, an ordering that
is editorial, entries that leave the site, a page deliberately left out"*. Both packages even
**carried `Menu.json` and `MenuItem.json` in their `post-types/` directory**: and neither manifest
listed them, so nothing provisioned the types and no layout could have used one. The one place a new
user meets Shio's navigation was demonstrating the shape SH382 exists to replace.

So the two types join both manifests, each package ships a `Primary` menu with short-labelled items
in editorial order, and the layout renders `{{#menu}}` with SH381's `{{#if url}}` branch, which
costs one `{{#if}}` and gives a curator a group heading for free. **No model change**, and that is
the decision: `Page.navLabel`/`navOrder` would be a *second* navigation mechanism competing with the
one that already has a helper, a lint rule (SH382's `MenuItem` pointing nowhere) and console
support.

Not extended to `convert`'s generated layout, which carries the same query (SH575): a replica has no
authored menu, and building one from a captured `<nav>` is a different task from this one, that is
SH878, which shipped the day after.

**And SH873 swept up two files of three (SH881).** It added the two post-types it needed and looked
no further, so `SectionMarkup.json` survived in the same position: carried by both section packages,
regenerated on every `emit`, named by neither manifest, installed by nothing. The direction was a
real question with two defensible answers (is the directory authoritative or the manifest?) and one
measurement settled it: **`landing` already provisions `SectionLogoWall`, `SectionRichText` and
`SectionTestimonial` and writes a post of none of them.** Carry-and-provision-the-whole-vocabulary
was already the rule, exactly as `emit.mjs`'s own comment argues (*a site that received the full
vocabulary can grow a testimonial section next month*), and one file was the exception to it. So it
is listed, and **`blueprint-manifest.test.mjs` holds the directory and the manifest together in both
directions**, a carried file no manifest names, and a manifest naming a file the package does not
carry, which fails at apply time on somebody else's instance. Six packages, 26 post-type files, and
the run prints both counts. Cost measured before the change and after: `build-marketing-site`
**3928 → 3939**, eleven tokens for one more provision, inside the ceiling SH873 set.

**Every section editable, and the page itself not (SH884).** Both packages have shipped a design
since SH518 and annotate every section field; the page's own fields were annotated nowhere. Measured
live on an applied `marketing-site` (`Page | 0/4 | DRAWN: description, title`) so a curator could
edit a hero's heading and not the title of the page it sits on. Two shapes, and **SH766 settled both
for the layout `convert` writes**, which is the whole finding: the pattern existed and had not
reached the packages.
- **`title` is drawn and reachable.** `<footer><p>{{post.title}}</p></footer>` is visible body
  content, so it gains `sh-field="title"` where it already draws, an attribute, and no pixel moves.
- **`description` reaches only the head.** It is `<meta name="description" content="{{post.summary}}">`
  and nothing else, and an editor cannot attach to a `<meta>`. So a `hidden` element carries the
  declaration (SH766's *annotate without drawing*) rather than inventing a visible element, which
  would be a worse defect than the one it fixes.

How it was found is the part worth keeping: not by reading the layout, which several rounds had
done, but by running `shio annotations` against a site the package had just built. **The check could
not have said it before SH882** (its verdict was a sentence rather than a measurement) which is
why the gap survived every round that looked at these packages.

**Measured: `build-marketing-site` 3928 tokens against a 3800 ceiling**, raised to 4000 with the
reason beside the number. Two more post-types and four more posts is five more ops in the apply's
report (SH518's lever argument, unchanged) and the alternative costs a curator four writes and a
re-publish on every site, which no budget counts. Ratio 2.79x against an unmoved baseline.

## What an applied package may do, stated once (SH643)

A blueprint provisions post-types and writes pages **under the credentials of whoever applies it**.
That is fine while every package ships in the jar and the only other source is a directory an
operator chose, SH96's model, where *somebody decided* is the whole of the trust. It stops being
fine the moment a package can come from a stranger, and the first thing a stranger needs is not an
index: it is a statement of what applying one can and cannot do to them.

**Every bound already existed.** What did not exist is anywhere they were true together, they lived
in six comments across four classes, which is a security model somebody has to reconstruct by
reading the implementation. `ShBlueprintBoundsTest` is that reconstruction, done once and pinned:
a package cannot name its own site, cannot prune, cannot overwrite a model it did not create, and
cannot create a site or widen a post-type without an explicit flag; a failure is isolated; and the
`shio-blueprint-` prefix is reserved for packages that did not ship with the instance.

**Deliberately not new restrictions.** Writing the statement is the task; tightening the model on
the strength of having written it would be answering a question nobody asked with a change nobody
reviewed.

The prefix is **reserved, not required**. Reserving it costs nothing and makes it mean something,
a reader seeing `shio-blueprint-acme` knows it is not first-party. Whether a stranger's package
*must* carry it is distribution policy, because it decides who may publish under the product's name,
and that belongs with the index.

### It found a published claim that had stopped being true

`docs-shio/blueprints.md` told readers *"an unknown parameter is a teaching error, never quietly
dropped"*. **SH395 made that false** in this same session: inside a template field (`HTML`, `CSS`,
`JAVASCRIPT`) an unknown name belongs to the page renderer and passes through. The page now says so,
including the cost, a mistyped parameter *inside a template* is not caught, because there an
undeclared name is the normal case. Two rules were added beside it: one package's failure is not the
catalogue's (SH217), and a package cannot come from a URL.

That is the argument for writing the statement down rather than leaving it in comments. The
contradiction was three commits old and nothing would have found it, because the two halves are in
different repositories.

### What was deferred, and what settled it

The **index** and a **provenance signal** were SH646, deferred because they are not code, they are
a place for the list to live and somebody to say what enters it, and the trigger was named as the
first package somebody outside the project asks to publish.

## The catalogue is a marketplace, on the shape Turing already runs (SH646)

`GET /api/v2/marketplace` answers what this instance could install. Two properties:
`shio.marketplace.enabled` (**false**) and `shio.marketplace.url`, plus a timeout. The item shape is
Turing's, a format with a working console behind it is worth more than a marginally better one,
with two fields a recipe does not need. **`params`** rides in the *listing*, so a console draws the
install form without downloading the package (P2 at catalogue level), and **`requires`** names the
SH151/SH152 opt-ins, so "this will alter a post-type you already have" is a consent asked *before*
the install rather than a surprise after it (P7).

**This does not overturn SH96, and the reason is where the operator stands.** `ShBlueprintProperties`
refused URL fetching because it *"moves the decision from an operator to a network"*. It still does:
the catalogue is off until somebody turns it on and names the URL, which is the same act as naming
a directory. Nothing changes about what an installed package may do (SH643's bounds) or how it is
validated (the registry, identically for first-party and stranger). The marketplace distributes what
the trust model already accepts.

### Three decisions the tests are about

- **The built-in entries are derived from `ShBlueprintRegistry`, never restated.** The six packages
  already carry a title, a version and a `params` schema; a hand-written JSON beside them is a
  duplicate that goes stale with nothing failing when it does. They carry no `downloadUrl` because
  there is nothing to fetch, they are here.
- **An unreachable catalogue does not empty the listing.** SH217 held that a stranger's package
  cannot disable the catalogue; a URL that is slow, moved, or serving an object instead of an array
  is the same hazard one level up. The failure becomes `source.state=unreachable` with the reason,
  *beside* the built-in six. A duplicate id is dropped and named, for the registry's collision
  reason: neither winner is defensible.
- **The README proxy's allow-list is the catalogue itself.** An endpoint that fetches whatever URL
  it is handed is an SSRF, and a scheme or host check is a guess about which addresses are safe.
  The catalogue already declares every README it has, so a URL it does not name is refused, the
  reachable set is exactly what the operator's own catalogue offered.

Off is a *state the listing reports*, not a 404: an instance always has packages, so the endpoint
always has a true answer, and a console showing an error where there is content is the failure that
avoids. The README proxy is the one that does refuse when disabled, naming the property (P6).

## An install leaves a package the registry loads, or it leaves nothing (SH675)

`POST /api/v2/marketplace/{id}/install` downloads the zip, expands it into `shio.blueprint.path`
and reloads. `?dryRun=true` does all of that except the move into place. `DELETE
/api/v2/marketplace/{id}` is the same door in reverse, a catalogue you can only add to is one
nobody experiments with, and refuses a first-party package, which is on the classpath and would
come back on the next restart.

**Three of the four steps already existed.** The registry reads that directory, holds a package to
the same checks as a first-party one, refuses a name collision naming both origins, isolates one
that will not load (SH217), and records its origin. So what this adds is the transport and the
refusals only a download can need: a zip entry that escapes its directory, a package whose manifest
`name` disagrees with the catalogue `id`, an archive with no `blueprint.json`, a name already taken,
and a package larger than source text plausibly is. Each is a teaching error (P6).

**Only the name is checked before the registry sees it.** Everything else the format requires is the
registry's, identically for this package and a first-party one, a second validator would be a
second opinion about one format, and the pair would eventually disagree. The name is the exception
because it is the only field the *catalogue* also asserts, and nothing downstream can notice them
disagreeing: you would install one thing and apply another.

### The rollback, and why SH217 was not already enough

SH217 makes a broken local package harmless to the **catalogue**. It does not make it harmless to
the **operator**, who asked for an install, was told it worked, and now has a directory the registry
refuses on every read. So the reload happens *inside* the install: if the new package comes back
rejected, the directory is removed and the refusal is the answer.

This is not theoretical, it fired on the first run of this task's own tests, because the fixture
manifest declared neither `postTypes` nor `content` and the registry correctly refuses a package
that would apply nothing. The rollback was proved by an accident before it was proved on purpose.

### Where the two writes sit on the security surface

They were **CONSOLE_ADMIN** for one task and are **stateless-writable** now (SH677), the path left
`sessionRequired()` the way `/api/v2/render/**` did: the capability became reachable.

The *reasoning* did not change. Installing a stranger's package is the operator's act, and an agent
already applying blueprints gains nothing from it; what a wide install route would add is *fetching
a package and planting it where the registry loads it*, after which every later apply runs whatever
arrived. What was wrong was the **mechanism**: "holds a session" does not identify an operator, it
identifies a browser, and it made the capability unreachable from a terminal, which is exactly the
console-only failure P1's non-goal names.

So the gate belongs on the credential. It should be `hasRole("ADMIN")`, beside the blueprint reload
it is strictly larger than, and it is `authenticated()` instead, because **that role is currently
unreachable for a locally authenticated user (SH681)**: `ShAuthorityResolver` grants `ROLE_ADMIN`
from the Administrator group and is wired only into the OAuth2/OIDC services, while
`ShCustomUserDetailsService` builds authorities from `ShRole` rows alone, and `GET /api/v2/role`
answers `[]`. Measured: `POST /agent/blueprints/reload` answers 403 to the seeded admin over a
session *and* over Basic auth, and has since SH219. **SH682** tightens the marketplace once SH681
makes the role real.

## The CLI, and what it is for (SH677)

`shio marketplace list|show|install|uninstall`, over the Basic auth the CLI already carries. `list`
prints where the entries came from on every run, a listing that silently shrank is
indistinguishable from a catalogue that shrank, and `install` takes `--dry-run`.

What it buys beyond P1 compliance is the pairing, which had no terminal form at all:

```
shio marketplace install shio-blueprint-blog
shio apply --blueprint shio-blueprint-blog --site mysite
```

Two decisions worth keeping. **A 404 from this surface throws rather than becoming `null`**,
`ShioAdminClient`'s shared helper maps 404 to an empty answer, which is right for "no such
post-type" and wrong here, where the two 404s are the most instructive refusals the marketplace has
(the feature is off; no entry has that id) and both carry `fix`. And **`show` fetches the README
through the instance**, which is not only about CORS: the server proxies only a URL its own
catalogue declares, so the command cannot be talked into fetching an arbitrary host.

Driving it against a live instance is also what found a defect in the catalogue itself: `builtIn`
was derived from "the registry has it", which stops being the same thing at the first install, so a
package downloaded from a stranger was reported as having shipped with the instance. It reads the
package's `origin` now.

## `shio_marketplace` is read-only, and that is the design (SH678)

The twelfth MCP tool lists what this instance could install and shows one package in detail. It
does **not** install, and the reason is structural rather than staged: **MCP tools call services in
process**. An API token reaches `/mcp` and is barred from `/api/v2/marketplace`, the refusal names
the four namespaces a token authenticates (SH2), so an install verb here would hand that token
what the URL layer denies it. *"The agent path is not a privilege escalation"* is a non-goal by
name. `ShMarketplaceCatalogTest#theMcpToolCannotInstall` asserts the schema has exactly one
property, so the verb cannot come back later because it looked like a gap.

What the tool adds is the half a terminal cannot give a client that has no terminal: **discovery**.
That is SH160's argument arriving a second time, a pure MCP client could read the memory and not
write one; here it can apply a package and never learn one exists.

`features.marketplace` reports whether a catalogue is configured, read from the property. `false`
does not mean no packages (the tool still lists what shipped with the instance) it means nothing
new can arrive. It is a `features` flag rather than an "unclaimed" declaration in
`ShAgentCapabilityJoinTest` because the marketplace is *off by default*, which is exactly the
instance fact that table exists to report.

### What it cost, measured

The tool is **96 tokens** and `tools/list` went 2397 → 2493, so `mcp.tools.list.total` rose
2400 → 2500. The trim came first and found nothing in the fat tools, `shio_apply` (441) and
`shio_write` (427) were read line by line and are already phrases, with two prior trims recorded in
their own comments. What it *did* find was in the new tool: the first draft measured 111 and ended
"Apply an installed package with shio_apply", which restates `shio_apply`'s own `blueprint`
parameter. Dropping it paid 15 of the 93.

One feature key also pushed `response.manifest.terse` 2400 → 2410 and `response.manifest.check`
380 → 390, both by single digits. **Headroom is now ~7 tokens on `tools/list` and 10 on
`response.manifest`**, and this is the twelfth tool against `ShMcpServerTest`'s cap of twelve, so
the next capability on this surface is an argument, a trim, or a conversation about which tool
leaves.

## The console's blueprint surface, which did not exist (SH679)

`/console/admin/marketplace`: a filterable grid of package cards, a detail dialog with the
package's parameters and its README, a dry run, install and uninstall. Before it,
`grep -ril blueprint shio-react/src` returned nothing: a curator could not see what the instance
can build from, which is the curator's side of the product's headline capability.

Under **Administration** rather than Content, because what the page does is install software into
the instance, not author anything. It sits beside the API tokens and the webhooks for the same
reason they are there.

### Three decisions

- **The opt-ins are a consent, not a footnote.** A package declaring `requires` gets an amber
  panel in the dialog saying it "can change more than the content it adds". P7's whole point is
  that "this will alter a post-type you already have" must not be a surprise, and a badge in a
  corner is a surprise with extra steps.
- **The dry run runs before the install, and says nothing was written.** The same courtesy the
  apply verb extends, at the step before it.
- **A refusal shows the server's own sentence.** Every 4xx here carries `fix` (P6), and the two
  commonest (the feature is not enabled, no entry has that id) both say what to do. Showing a
  bare status would throw the instruction away.

### What was verified by looking, and what falls short

Driven in a real browser against a live instance: the grid, the detail dialog, the dry run
reporting *"a versão 1.0.0 seria instalada em …, nada foi escrito"*, and an install that put
`shio-blueprint-docs` on disk. The locale resolved to `pt`, which is also the proof both key files
are wired.

**The README is raw markdown in a monospace block, not rendered.** §XI.25 asked for it rendered;
the console has no markdown renderer and adding a dependency for one panel is a larger decision
than this task. The text is generated by `marketplace.mjs` and short, so it reads acceptably, but
it is a shortfall against the section rather than a choice the section anticipated.

**Install progress is not streamed**, which §XI.25 also floated. The backend install is one
synchronous call over a ~100 KB download; an SSE progress channel would be infrastructure ahead of
its problem.

The writes still want the role SH682 will give them.

## Install and apply in one command, still two operations (SH1251)

`shio marketplace install <id> --apply --site <name>` installs, then runs the apply
`shio apply --blueprint` runs, with the same flags (`--create-site`, `--merge-post-types`,
`--param`, `--dry-run`). The server keeps them as two operations (SH675's reasons hold: an install
is auditable and survives an apply that fails), so a failed apply leaves the package installed and
the error says to retry with `shio apply`.

It reads the catalogue first, because the install half is not always there: a built-in package and
an installed one have nothing to download, and the server answers 409 to both. Calling it anyway
and swallowing the 409 would hide a real conflict behind an expected one.

**The CLI's dry run cannot plan the apply of a package that is not installed yet**: it composes
the two existing calls, and the apply call plans from the registry. It plans the install and says
why there is no apply plan rather than printing an empty one.

**`POST /api/v2/marketplace/{id}/setup` can**, and it is what the console dialog calls.
`ShMarketplaceSetup` installs if needed and then applies, or, with `dryRun`, plans both: for a
package not yet installed, `ShMarketplaceInstaller.staged` downloads and validates it into a temp
directory, `ShBlueprintRegistry.readStaged` reads it under every rule a load applies, and
`ShBlueprintService.plan(ShBlueprint, ...)` plans from that object. So the plan describes the bytes
the confirm will install, and nothing is left on disk. An apply always goes through the registry:
a package that is not registered is not one this instance carries. Admin-gated, as every
marketplace write is (SH682).

**The dialog** (`marketplace.setup.tsx`) draws the package's `params` schema as a form (with the
declared defaults filled in), takes a site name and the two opt-ins, and offers **Plan** first.
The confirm stays disabled until a plan exists, and **any input change throws the plan away**, so
the confirm can only write the plan the curator saw. It replaces the read-only parameter list the
dialog used to show. `e2e/marketplace-setup.check.mjs` walks it end to end against a running
console with the built-in `blog` package: disabled before a plan, no site after the plan, plan
invalidated by an edit, and the site present after the confirm.

## The published catalogue, and why the six are republished under a prefix (SH676)

`shio-site/scripts/marketplace.mjs` derives the catalogue from
`shio-app/src/main/resources/blueprints/` and writes `public/marketplace/`, `marketplace.json`,
one zip per package, one generated README each. The site's `build` runs it before Vite, so the
GitHub Pages workflow publishes it; **the zips are gitignored**, because ~430 KB of binaries that
only restate bytes tracked one directory away is not something a reviewer can read.

**The six are published as `shio-blueprint-<name>`, and that is load-bearing rather than
cosmetic.** `ShMarketplaceCatalog` drops a remote entry whose id collides with a package the
instance already has. The six *are* built into every instance, so published under their own names,
every entry would be discarded everywhere: a catalogue that proves the format and disproves the
install path, which is the opposite of the point. Under the prefix SH643 reserved they are
genuinely installable. The manifest inside each zip is rewritten to that same name, because
`ShMarketplaceInstaller` refuses a package that disagrees with the catalogue about what it is
called; forgetting that rewrite would make every published package refuse to install *with a
correct error*, which is worse, because it reads as the installer being broken.

**Verified end to end, not asserted:** the generated tree served on loopback, a real instance
pointed at it, `?dryRun=true` reporting `loaded: 6` and writing nothing, the install reporting
`loaded: 7`, `/agent/blueprints` listing the package with its origin, and the uninstall taking the
directory back to empty.

### Two decisions worth keeping

- **A hand-rolled, stored, deterministic zip** (`scripts/zip.mjs`, no dependencies). The workspace
  has no zip library and the site build has no runtime deps at all; a stored archive is the simple
  part of the format, and these are 50–110 KB of source text where compression buys nothing.
  Determinism is not about diff noise here (nothing is committed) but so that a package
  downloaded from the site is reproducible from the commit it was built at. The one trap is
  arithmetic: `0o100644 << 16` is a *negative* int32 in JavaScript.
- **The zip is verified by an implementation this repo did not write.** A writer checked by its own
  reader proves the two agree and nothing else, and these archives exist to be opened by Java's
  `ZipInputStream`. The test shells out to Python's `zipfile` and calls `testzip()`, which checks
  every entry's CRC, and it *fails* rather than skips when python is absent.

**`requires` is published empty, and that is a statement.** `createSite` and `mergePostTypes` are
request options on an apply, not manifest keys, and whether either is needed depends on the target
instance rather than on the package. A publisher may declare them about their own package; the
generator will not guess. Worth knowing when reading SH646's consent story: nothing validates a
publisher's `requires` against what the package actually does.

## A blueprint's strings have two audiences, and only one is published prose (SH930)

SH929 took the em dash out of the public site and stopped at `public/marketplace/**`, which is
generated from `blueprints/**` and republishes those bytes rather than restating them. Sixteen live
there. SH930 was filed to decide whether the rule reaches them, offering two answers: published
prose everywhere this project writes, or the site alone.

**Reading the sixteen split them, and the split is the decision.**

| kind | where | count | verdict |
|---|---|---|---|
| sample content | `<blueprint>/content/*.json` | 6 | **rewritten**, it becomes a page the user publishes |
| field help, blueprint summary | `post-types/*.json`, `blueprint.json` | 10 | kept, product chrome a curator reads |

The line is the audience, not the module. `"Replace it (or delete it) once you have something to
say"` is prose Shio hands a reader on somebody else's marketing site; `{{brand}}, {{headline}}` is
the title of a landing page a new instance publishes. Those are the rule's subject. `"Friendly URLs
in render order, a MenuItem, a nested Menu, or a page"` is a form label, and the task's own phrase
is *published prose*, which a form label is not.

The measurement that settled the second half: the console's own i18n bundles carry **38** em dashes
of exactly that kind. Rewriting ten field-help strings while a curator meets thirty-eight on the
surrounding screen makes the product less consistent, not more, so the ten stay with the
thirty-eight, and if that set is ever taken they go in the same pass.

`SH930: sample content a blueprint publishes carries no em dash` in
[`shio-site/scripts/ai-writing.test.mjs`](../../shio-site/scripts/ai-writing.test.mjs) holds the
`content/` tree so a seventh cannot arrive with the next blueprint. It lives beside the site's own
rule because that is where the rule is owned, it reaches across modules the way
`scripts/marketplace.mjs` already does, and it refuses to pass on an empty sweep, a moved
blueprints tree would otherwise report a clean result. Verified by restoring one dash: the rule
fires and names the file.

## Patterns: a block that works becomes a file (SH1040)

Every blueprint is whole-site or whole-page, with a fixed target and no mode that inserts into an
existing page, and nothing captured an arrangement that already existed. So a curator who got a
pricing block right could only reuse it by rebuilding it, or by asking an agent to, which is the
same work moved. `shio patterns save|list|apply` closes that: a pattern is
`shio/patterns/<name>.json`, the types and field values of a page's sections in order.

**A file in the tree, not a package in the registry, and that reverses the design's own
sentence.** SH1040 said patterns join the blueprint registry. They do not, because of where each
thing lives: the registry scans the *server's* classpath and one operator directory, while the tree
is the *project*, which the server cannot see and has no business writing into. Putting a captured
pattern in the registry therefore means uploading it, which is the marketplace's job and a different
task. A pattern is content an agent authors, and P4 is explicit that files beat APIs for authoring,
`Edit`, `Write` and `Grep` are what make a captured block reviewable in a pull request, which is the
property the design was after when it cited Gutenberg's file-registered patterns.

**Apply composes; it does not write.** A pattern application is a desired-state document, the
sections it creates, plus the target Page re-declared, handed to the same `plan`/`applyConfirmed`
engine every other document goes through, so it is one batch, one transaction and the same teaching
errors. The Page is re-declared **whole** because SH168 says an upsert declares whole state: sending
`sections` alone would clear its title. Reading it first is also what makes `--after` mean anything,
since it counts the array the page actually has.

Three properties worth keeping. **Names are derived, so a re-apply is idempotent**, the same
pattern into the same page writes the same posts rather than a second copy, and `--as` is how a
caller asks for two. **The sections land beside the page's own**, in the folder its first section is
in (`/sections` when it has none), because a conversion and both blueprints already follow that
convention and a pattern must not invent a second. **An unfilled `{{placeholder}}` is refused before
anything is written**, with the pattern's declared parameters in the refusal: leaving it in writes
literal braces onto a live page, which reads as a rendering bug three steps from its cause.

A capture writes **literal** values and says so on stdout, a pattern only becomes reusable once
somebody decides which of those values are the pattern and which were that page's, and that decision
is a hand edit replacing a value with `{{name}}` and declaring it under `params`. That is the
smallest thing that could work, and it is the one a diff shows.

**Why no MCP tool arrives with this.** A pattern application *is* a desired-state document, and
`shio_apply` and `shio_write` already take documents, so an agent holding the file and the target
page can compose and send exactly what `patterns apply` sends, without a thirteenth tool that
`ShMcpServerTest` would refuse anyway. What the verb adds is the composition: reading the page,
deriving idempotent urls, splicing at a position. That is convenience over a surface that is already
reachable, which is the one case P1's order does not oblige, and it is also why the design's
`shio_apply --at --mode insert` is not here: that would need the *server* to hold the pattern, which
means uploading a project file, which is the marketplace's job.

**The console's half is SH1108.** A pattern rail has nothing to list while a pattern is only a
project file, so `shio patterns push <name>` (admin) copies the file to `PUT
/api/v2/agent/patterns/{name}`, which stores it **byte for byte** in `shio.blueprint.path/.patterns/`
(`ShPatternStore`), the marketplace's operator directory and its refusal without one, but not its
installer: that downloads by catalogue id and takes no upload, which the paragraph SH1040 closed on
had assumed it did. The registry skips the directory, since it holds no `blueprint.json`. The store
validates what `parsePattern` validates, so a pushed file the rail could not place is refused.

- **The file stays the authority.** `GET /agent/patterns` answers each copy's `sha256:` digest over
  the stored bytes, and `shio patterns list --remote` compares it with the file's: `pushed`, `stale,
  push it again`, `not pushed`, or `pushed, with no file here`. The rail cannot see the project, so it
  says when a copy was pushed and never that it is current.
- **One document, one write path.** The rail builds the document `shio patterns apply` sends,
  `placementDocument` moved into `patterns-store.mjs` for that reason, and
  `shio-react/src/app/editor/pattern-placement.ts` mirrors it, and sends it to the same `POST
  /agent/apply`. `pattern-placement.test.mjs` holds the mirror to the definition on the same page and
  pattern at every position, which is the assertion that the rail writes the CLI's array.
- The rail fills **declared defaults only**; a placeholder without one is named in a toast with the
  CLI line that places it. It passes a free `--as` (`name`, then `name-2`…), since Insert pressed
  twice means two blocks and the same instance twice would splice the same posts in again.

## A blueprint nobody applies is a blueprint nothing checks (SH1121)

`checks=a11y` found two rules broken by `marketing-site`'s layout, no `<main>`, and a first heading
of `<h2>`, and that one was repaired in the same commit, because the headline benchmark applies it
and went over its token budget. **`landing` had exactly the same defect and no benchmark**, so nothing
reported it: its `PageLayout` emitted `<html lang>` and a `<body>` of sections, no main region and no
`h1` anywhere on any page it built. A package this product recommends as a starting point shipped two
accessibility failures our own gate names, and the only reason the sibling was clean is that something
unrelated happened to render it.

The repair follows the sibling's, and is one line of the content document: wrap the section loop in
`<main>`, and choose `<h1>` for the first section's heading through the `@first` data frame
`getRelation` has published since SH772, `{{#if @first}}<h1 …>{{else}}<h2 …>{{/if}}`.

**The half worth keeping is `ShBlueprintAccessibilityTest`.** Every appliable unit this product
recommends should be provably clean against our own gate, and the way to know that is an assertion
rather than a sweep. The subjects are **read out of the registry**, every blueprint whose *content*
declares a `PageLayout`, applied to a fresh site each, then `verify(site, "a11y", 200)` has to report
nothing. Three things follow from doing it that way rather than naming three packages:

- A blueprint that gains a layout **joins the test by existing**, and one that ships a broken layout
  fails the day it lands. A fixed list would have held exactly the packages that were already in the
  tree, which is what `landing` was.
- The required parameters come from each manifest's own `params.required`, so a package that wants
  something other than a `brand` is applied correctly instead of failing with a message about this
  test. Declared defaults are left alone: a default is part of what the package ships, and overriding
  one would check a configuration nobody gets.
- The blueprints that carry their markup in `files/app/layout.tsx`, `docs`, `product-catalog`,
  `nextjs-starter`: are **absent from the subjects because nothing in them declares a layout**, which
  is §LXXIV.11's "worth confirming rather than assuming" confirmed by construction rather than by a
  comment. Shio never renders that tree, so our gate has no page to judge there.

It carries a vacuity guard for the reason every inventory here does: a registry that failed to load,
or a rename of the `PageLayout` type, would leave no subjects, and an empty loop agrees with every
claim including this one. The counts are printed by the run and deliberately not written here.

**It spends a context slot, and the cheaper answer is refused**: worth recording because it is the
next reader's first idea. SH347's guard asks before a slot is spent, and there was one left, so
sharing `ShBlueprintRendersTest`'s database was tried: that class isolates one for this same reason and
already applies `marketing-site`. It works, and `ShBenchmarkIsolationLintTest#everyDatabaseNameIsClaimedOnce`
refuses it, *two classes on one in-memory database is the shared fixture again, wearing the
isolation's clothes*, with an exemption only for the SH22 cross-database ITs, which are one scenario
across files. That rule is right and this is not its exception. So `DECLARED_CONTEXTS` went 78 → 79 and
`spring.test.context.cache.maxSize` 81 → 82 beside it, which is the deliberate act SH423 asks for.

## A package carries its images (SH1252)

`blueprint.json` may declare `assets`: paths under the package's `assets/` directory. Each one is
read at load with the rest of the package, so a missing file, a path outside `assets/`, a `..`, or
an asset at the top of `assets/` (a site's root is not a folder an upload can address) refuses the
package by name, as a broken `postTypes` file does. `assets/images/hero.webp` lands as `hero.webp`
in the site folder `/images`, and content references it by that path.

The server writes these and the CLI does not, which is the line between `assets` and `files`:
`files` are templates for the caller's project, `assets` are bytes that belong in the CMS (SH38).
The folders go into the desired-state document so the content engine plans and creates them;
`ShBlueprintAssetWriter` then uploads into them through the asset library's own `upload` and
`replace`, after the content apply and before the verify. Each asset is `create`, `replace` (the
bytes differ; the post keeps its id and SH1035 keeps the old bytes as a rendition) or `noop` (same
SHA-256), so a converged re-apply writes nothing (P5). The rows ride on `ShBlueprintApplied.assets`,
omitted when empty, and the CLI and the console's plan print them.

`ShBlueprintRegistry.MAX_ASSET_BYTES` is 32 MB for a package's assets together: half the
installer's 64 MB unpacked bound, against a showcase that plans under 8 MB. Raise it only for a
package that measures above it, and write the number beside the constant. The bytes are
`@JsonIgnore`, so no listing that names a package can carry its photography.

## A catalogue entry a curator can see (SH1253)

An entry may declare `screenshots`, up to `ShMarketplaceItem.MAX_SCREENSHOTS` (four) image URLs.
`GET /api/v2/marketplace/screenshot?url=` proxies them on the README's allow-list: only a URL the
configured catalogue declares, and only a PNG, JPEG, GIF or WebP, served with `nosniff`. A declared
SVG is refused too, because it is a document that can carry script and the console's origin is the
one SH951 keeps such files off. The card shows the first, the dialog all of them, and
`shio marketplace show` prints the URLs. `shio-site/scripts/marketplace.mjs` publishes a package's
`screenshots/` images beside its zip and leaves them out of the archive.

The README is rendered now, which closes SH679's recorded shortfall. No library:
`marketplace.readme.tsx` covers the subset the generator writes (headings, paragraphs, lists, fenced
code, inline code, bold, links), builds React elements rather than HTML, so a stranger's README cannot
inject markup, and loads behind `import()` with the setup panel, so the eager budget does not carry it.
