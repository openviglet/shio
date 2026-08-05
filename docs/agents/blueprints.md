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
