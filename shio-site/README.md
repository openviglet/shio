# shio-site

The public site for **Viglet Shio**, published to
[openviglet.github.io/shio](https://openviglet.github.io/shio/).

Stack: **Vite + React 19 + TypeScript + Tailwind CSS v4 + shadcn/ui**, the same
conventions as [`turing-site`](https://turing.viglet.org) in the sibling
repository. It is standalone: no Shio SDK, no backend, no runtime data source.
Everything on the page is static and prerendered.

## Develop

```bash
pnpm --filter shio-site dev        # vite dev server on :5173
```

## Build

```bash
pnpm --filter shio-site build      # → dist/
```

That runs four steps, and the order matters:

1. `tsc -b`: the typecheck gate.
2. `vite build`: the client bundle plus the `index.html` template.
3. `vite build --ssr src/entry-server.tsx`: `render()` and the route table.
4. `node scripts/prerender.mjs`: one static `index.html` per route, then
   `scripts/og-image.mjs` rasterizes `public/og.svg` to `dist/og.png`.

There is **no router**. `ROUTES` in [`src/App.tsx`](src/App.tsx) maps a pathname
to a page, and the prerender and the client both read it, so a bleeding-edge
Vite has nothing framework-shaped to break. Every route is a full page load.

**Adding a page is two rows**, and the build refuses to let you write only one:
a `ROUTES` entry in `App.tsx`, and a `routes` entry in
[`src/entry-server.tsx`](src/entry-server.tsx) carrying the title and
description. `routesAreDeclared()` runs at import time and throws if the two
tables disagree in either direction: a route with no page would otherwise
prerender the landing page under someone else's `<title>`, and a page missing
from the table would silently never get a file.

The prerender patches the `<head>` with `replaceOrThrow` for the same reason: if
the template drifts, the build fails rather than shipping a page carrying
another route's `<title>`.

## What the site argues

The page opens on **two sessions under one switch** (a person building a page in
the console, and an agent building the same page), and the section below it is the
**four ways in** (`PATHS`): the console, an agent, files in a repository, the
delivery API. Then six deployments (`USE_CASES`), of which only three involve an
agent.

That ordering is a correction, and it is the one rule to keep in mind when editing
any copy here. The site used to lead with *"the CMS whose operator is Claude Code:
your agent builds the site, you approve it."* Every sentence of that was true of
one path and the composition of them said something false: that the product cannot
be used by hand. Law **P1 is a build order** (a capability lands MCP → files/CLI →
REST → console so that nothing ends up reachable only by a mouse), and it has never
been a statement about who may operate the CMS. A person can create the site,
model, write, publish and run the team without an agent existing anywhere;
[`/console`](src/pages/console.tsx) is the page that says so, including the four
capabilities that genuinely live on the CLI.

`scripts/site-claims.test.mjs` holds what can be checked of that: the four paths in
order with the console first, a use case that involves no agent, and the console
page naming its own limits.

## Content

**The page copy lives in [`src/lib/site-content.ts`](src/lib/site-content.ts)**:
the ten design laws, the two hero sessions, the four paths and six use cases, the
MCP tool list, the CLI verbs, the token measurements, the console capability and
limit lists, the guardrails, the comparison matrix and the links,
with the depth pages in [`src/lib/feature-content.ts`](src/lib/feature-content.ts).
What stays in [`src/components/sections/`](src/components/sections/) is a section's
own heading and lead, because those are written against the layout they sit in.

Four rules for editing any of it:

- **Every claim is about a real surface.** A route, a tool name, a flag or an
  error body that appears there is the one the build produces. `docs/agents/` is
  the authority for each.
- **A measured number is generated, never typed.** `scripts/token-budgets.mjs`
  reads `shio-app/src/test/resources/token-budgets.properties` (the one file
  the Java suite asserts every response against) and writes
  `src/lib/token-budgets.generated.json` on every build. A renamed key fails the
  build rather than dropping a bar. A figure inside a *sentence* reads it back
  through `ceiling()` in [`src/lib/token-budgets.ts`](src/lib/token-budgets.ts);
  two of them had been typed, and both were a measurement taken three tasks
  before the ceiling they quoted moved.
- **Prose gets the writing pass, and part of it is a lint.** Run the `ai-writing`
  skill over anything you write here before handing it over: the tells worth
  catching are density and inflation, and no grep sees those.
  [`scripts/ai-writing.test.mjs`](scripts/ai-writing.test.mjs) is the half that
  can be automated: the checklist's copy-paste artefacts, the phrases that are a
  tell on their own, five counts read back from the surface each describes, and
  the em dash.
- **This site does not use the em dash**, in any of its four spellings: the
  character, `&mdash;`, `&#8212;` and `&#x2014;`. SH929 removed 262 of them. It is
  the loudest tell in machine-written English, and a page whose argument is that
  an agent built the site cannot afford to read as though one wrote the copy.
  What replaces it is never a comma by default. The mark stands in for a
  parenthesis, a colon, a semicolon or a full stop, and it hides which, so read
  the sentence and write what it was already asking for. The lint reads code
  rather than raw files, which leaves a note between contributors alone; what it
  will not let past is anything a reader is handed, a comment inside a rendered
  code sample included. The one exception it names out loud is
  `public/marketplace/**`, which republishes another module's bytes (SH930).

## The showcase (SH1258)

`/showcase` and the landing section of the same name present Quilhara, the finished sample
site published in the marketplace. Nothing about it is typed here:

- **The pages and what each demonstrates** come from
  [`docs/specs/SH1250-showcase-matrix.json`](../docs/specs/SH1250-showcase-matrix.json), the matrix
  `cli/test/showcase-matrix.test.mjs` holds to the product. Each depth page names a manifest
  feature key (`showcase` in `feature-content.ts`) and links to the page the matrix maps it to;
  `showcasePageFor()` throws at build time on a key the matrix does not map.
- **The screenshots** are captured from an applied showcase by
  [`scripts/showcase-screenshots.mjs`](scripts/showcase-screenshots.mjs) (run by hand against an
  instance, output committed under `marketplace/quilhara/screenshots/`), published beside the
  package by `scripts/marketplace.mjs`, and listed in `src/lib/marketplace-screenshots.generated.json`,
  so the site shows the same files the console shows before an install. Re-run the capture when
  the package's look changes.

## The Markdown twins

Every route also emits `<path>.md`: `/` → `/index.md`, everything else appends
`.md`, and the twins come to something like an eighth of the HTML. The exact pair
is printed by the prerender on every build and patched into `dist/llms.txt` from
there, because it exists only once every route has been written. It used to be
typed here *and* in `llms.txt`, and the two disagreed with each other before
either disagreed with the build.

The twin is **converted from the same render**, not written separately: the copy
lives in `src/lib/*-content.ts` but the *composition* lives in the JSX, so
generating Markdown from the data alone would re-declare that composition and
the two would drift. [`scripts/markdown.mjs`](scripts/markdown.mjs) does the
conversion and carries the reasoning; it is the one piece of real logic here and
therefore the one piece with a test (`pnpm --filter shio-site test`).

Two things to know when writing a page:

- The nav and the footer never reach a twin; only `<main>` is converted.
- **A `hidden` element is dropped too**, which is why the landing twin carries the
  console session and not the agent one: they are tab panels, and the inactive one
  is hidden. That is the right trade rather than an oversight: an agent reading the
  twin does not need a scripted demo of itself, and the substance of the agent path
  is in the sections below, which the twin carries in full.
- A `<pre>` whose lines are separate `<span class="block">` elements (the session
  panels, which colour each line) gets one Markdown line per span. Joining them ran
  a whole file tree onto one line.
- **`data-md-skip` on an element drops it from the twin**, children included.
  The call to action carries it: it converts a reader and costs an agent forty
  identical words on ten pages.

`dist/manifest.json` lists the routes, their twins and their sizes in the shape
`/api/v2/agent/manifest` uses, so the discovery pattern the product teaches is
the one this site is discovered by.

## Static assets

Served from the site root, in [`public/`](public/): `robots.txt`, `sitemap.xml`,
`llms.txt`, `favicon.svg` and `og.svg`. There is no `CNAME`: the site has no custom domain.

## The base path

The site is the repository's GitHub Pages project page, so it is served under **`/shio/`**,
not at a host's root. `base` in [`vite.config.ts`](vite.config.ts) says so once, and Vite
hands it to both builds as `BASE_URL`. Two rules follow:

- **Every internal link goes through `withBase()`** ([`src/lib/base.ts`](src/lib/base.ts)):
  write `withBase("/console/")`, never `"/console/"`. The dev server and a local preview
  answer the bare form, so only the published site would 404. The prerender checks every
  `href` and `src` it writes and fails the build on one without the base.
- **The docs area's base is `/shio/docs`**, and the links typed in its pages carry it
  (`/shio/docs/<slug>/`); `scripts/docs-links.test.mjs` fails on one written as `/docs/…`.

## The documentation, at /docs (SH1262)

[`docs/`](docs/) is Shio's end-user documentation: its own Astro + Starlight project and pnpm
workspace member, with base `/docs`, building into `dist/docs` as the **last** step of `build`
(Astro empties its outDir and `vite build` empties `dist/`, so any other order publishes the site
without it; the Pages workflow checks for `_site/docs/index.html`). The pages came from
vigletdocs' `docs-shio/` through [`docs/scripts/import-vigletdocs.mjs`](docs/scripts/import-vigletdocs.mjs),
which says what it rewrote and why; edit them here now. Links between pages are absolute
(`/docs/<slug>/#anchor`), because Astro does not add the base to a link typed by hand, and
`scripts/docs-links.test.mjs` fails on one that reaches no page or no heading.

Because the pages now sit in this tree, the repository's own lints read them: the first run
found the REST reference still documenting the cache-clearing endpoint SH954 removed.

## Publish

[`.github/workflows/publish-github-pages.yml`](../.github/workflows/publish-github-pages.yml)
is the whole path to [openviglet.github.io/shio](https://openviglet.github.io/shio/): it installs the
workspace, runs `pnpm run build:site`, assembles `_site/` from `dist/` and deploys
it to this repository's GitHub Pages with `actions/deploy-pages`.

It is **`workflow_dispatch` only**, like `publish-js-sdk.yml`: a deploy that
fires on every push to a long-lived branch is one nobody can hold still while
reviewing it. Run it from the Actions tab when the site is ready to go out.

The build *is* the gate: `tsc -b` fails the job on a type error, and the
prerender throws on a drifted template or on a route declared in only one of the
two tables. `dist-ssr/` is a build input and never reaches the published tree.
The job needs no secret, only a one-time repository setting: **Settings → Pages →
Source = GitHub Actions**, with no custom domain.

## Backlog

**SH929, the same day**, reversed a call SH925 had made in writing. That file counted the em
dash, decided it was house style and wrote the exemption into the lint. It is not house style:
it is the loudest tell in machine-written English, and a page whose argument is that an agent
built the site is the last place that can afford it. 262 came out, in four spellings, and the
rewrite was a reading pass rather than a substitution, because the mark stands in for four
different stops and hides which. Two things are worth carrying from it. The source scan came
back clean while `dist/` still had one, because `code()` drops any line starting with `//` and
the landing page renders a code sample whose third line is a comment; the lint now reads every
template-literal body as well. And four published surfaces were outside every file list the
site had: the Vite template's `og:description`, which reaches every page because the prerender
patches the title and the description meta and not that one; the social card, an SVG with text
in it and the only place `&#8212;` appeared; and three build scripts that write strings straight
onto the page. `public/marketplace/**` is the one exception left, and SH930 is the task that
decides it.

**SH930 decided it, and the answer was not the one the exception implied.** That tree republishes
`shio-app/src/main/resources/blueprints/**` byte for byte, so the sixteen em dashes in it are not
this site's to rewrite, but reading them split the set, and the split is the rule. Six were
**sample content**: the body of the first blog post, the FAQ answer on the landing page, the title
a landing site publishes. That is prose Shio hands a reader on somebody else's marketing site, and
it is exactly what this rule is about, so it was rewritten and `SH930: sample content …` in
`scripts/ai-writing.test.mjs` now holds the `content/` tree. Ten were **field help** on `Page`,
`Menu` and `MenuItem`, plus three blueprint summaries. Those are product chrome a curator reads in
the console, and the console's own bundles carry **38** more of the same kind: rewriting ten while a
curator meets thirty-eight on the surrounding screen makes the product less consistent, not more.
They stay, and if that set is ever taken they go in the same pass. **The rule follows the prose to
its reader, not to a module.**

**Block S closed again on 2026-08-25**, on SH925, and it is the first closure in this list that
moved claims rather than checking that none had moved. Five counts on these pages had gone stale
without anything being able to see it: the matrix said seventeen questions and had eighteen rows,
`/features/content-as-files` said ten projected keys where the CLI carries eleven, the two manifest
figures were a measurement taken three tasks before the ceiling they quoted moved, `llms.txt`
called P7 *"Never surprise the human"* where every other surface says *the curator*, and the
Markdown size pair was typed here and in `llms.txt` and the two disagreed with each other before
either disagreed with the build. Each was true the day it was typed. The rule this file states
(every claim is about a real surface) had no instrument, so `scripts/ai-writing.test.mjs` is the
one it gets: the AI-writing checklist's greppable half, plus those five counts read back from the
surface each describes. Four of its seven rules were red when it was written. The one lesson worth
carrying: `llms.txt` is the page nobody re-reads, and it was outside every lint the site had: the
actor-vocabulary sweep did not walk `shio-site/public` and did not know `.txt`. It does both now.

The positioning rewrite described further up is older than that, and has no roadkeep id:
it was written in an environment where the engine is not installed, so the entry
belongs under **Block F (Market positioning)**, which is the block that exists for
exactly this and is currently empty.

Nothing else. Block S closed on 2026-08-13, and it closed by **retirement** rather than by
shipping: SH540 (the public read-only demo instance that would have turned the hero's
scripted session into a live one) was judged infrastructure and demo work rather than
product, so it left the backlog and the hero keeps its script. A block emptied by a
retirement completes exactly as one emptied by a ship, because what changed is what
these pages may claim.

This paragraph said SH540 "is all that is left" for a day after it stopped being true,
which is the failure mode a backlog note in a README has and the reason each closure
below gets a line of its own.

**W closed again on 2026-08-20**, on SH879 and SH880, and both change what a *contributor* may leave
unmeasured rather than anything a reader of this site can do, the same verdict every instrument row
in the matrix carries. It is worth one line for what it measured: the two defects the day before it
were both found by somebody building a site, and the corpus that runs on every commit could not have
caught either, because no archetype carried a page title of the page-then-brand shape, joined by an em dash, that both were about.
Two of seven state any navigation and **one** produces a menu, so a single fixture edit would leave
that whole path unexercised. A named list watches all three shapes now, in both directions. SH880
asked the same question of the *render* and found a live defect doing it: the generated layout's new
menu branch had never been rendered anywhere, and giving it a fixture showed the layout shipping
`${MENU_TITLE}` uninterpolated: a nav that falls back and looks exactly like one that works.

**Q closed a seventh time on 2026-08-20**, on SH878 alone, and it moved no claim here either; the
block was reopened for it the same day, because fixing a blueprint's navigation turned up the same
defect one surface along. A replica's nav listed every page by the `<title>` written for search;
it is now the menu the **source** states, built from the `<nav>` entries the capture already
recorded in source order. `CLONE_STEPS` describes capture and convert and says nothing about
navigation, and `MODES` is about what a mode keeps rather than about what its chrome says, so
neither is now untrue, which is the honest reason and not a shrug: the copy here is deliberately
about the trade a mode makes, and a replica's menu is below the level it describes.

**Q closed a sixth time on 2026-08-20**, on SH871, SH872 and SH873, and it moved no claim, because a
round three closures ago had already moved the one it would have. `MODES` has carried **Annotated
fidelity** as a third mode since SH864 measured it, saying *the source's own markup, byte for byte*
and *editable fields on that markup*; this round is what made that sentence cheap to be true. SH871
gave the run a way to declare the fields it had been naming as forty preconditions nobody could
meet, and SH872 then removed the need to declare anything: the text stays in the markup that carries
it, so `Page` keeps its four fields on a site of any size instead of gaining one per captured block.
Both are the *cost* of a promise this page already made, which is the shape of closure this list has
seen most often and is still worth a line. SH873 is the one that reaches a reader (the two section
blueprints ship a curated menu instead of listing every page by its SEO title), and this site makes
no claim about a blueprint's navigation, so it changes nothing here either. **The roadmap is empty
again at this commit**, and Block Q is dropped with this sweep.

**Q closed a fifth time on 2026-08-19**, on SH845, SH846 and SH847, and this one did not move a claim
either, but for the opposite reason to the round below, which is why it gets its own line. `MODES`
has said *the source's own colours* since SH831, and the round found that on a source built by the
commonest CSS toolchain the bridge had been carrying almost nothing across: a palette declared inside
`@layer` was refused as a variant, so three colour tokens ranked out of utility-class literals stood
in for 319 declared properties, one of them fully transparent. SH846 gave the mapping a floor and a
third verdict (a slot can now *refuse* what it was offered and print the reason), and SH847 found the
accent where a stylesheet actually states it, on the buttons. So the copy was already right about the
kind of value taken and became true about the values; nothing here may claim more than it did, and the
one claim worth checking next time is that `authorable` says colours, type and corner radius, which
is still exactly the three the bridge names.

**Q and W closed a third and fourth time on 2026-08-19**, on SH840 and SH838, and neither moved a
claim here, which is worth one line because the round is the one most likely to look as if it had.
Every id in it is an instrument turned on this project's own work: SH837 asserted the half of a fix
nothing distinguished from its absence, SH838 gave a real capture the one admission rule only a
synthetic stylesheet reached, SH839 corrected a comment claiming a measured cost of zero that a run
refuted, and SH840 made the colour a page states outright win its slot over one merely repeated. The
site already said *the source's own colours* and now says it of both halves of the palette rather
than one, so the copy was right and became true rather than the other way round.

**Blocks Q and W closed together on 2026-08-19**, on SH835 and SH836, and Q moved the same claim a
second time: SH834 took the source's **type and corner radius** as well as its colours, so `MODES`'
authorable row names all three. SH835 gave `classify` a route for an FAQ written as a `<dl>`: a
capability, not a fix: the block used to answer *I do not know*, which is the answer the other routes
are worth trusting by. SH836 is a lint reading its own scopes correctly and reaches no page here.

**Block Q closed on 2026-08-19 earlier the same day, and that one moved a claim first**, the first
closure in this list that did. SH831 gave the theme an authorable replica is presented with its **colours from
the source**: `convert` reads the palette `propose` already extracted and fills the theme's six
colour tokens by the role the source uses each one in, naming in the run every one it could not
fill. So `MODES`' authorable row no longer says only *"the source's styling is replaced"*; it
says the structure is replaced and the colours are the source's, which is a different promise and
a true one. The rest of the round is invisible here by construction: SH829 and SH830 are the
agent's digest and the conversion's own warnings, SH832 is a defect (an empty row in a collection
was drawing its section's own words), and SH833 is the corpus. **The roadmap is empty at this
commit** (no block holds an open line), so there is nothing this site is waiting on.

**Block F closed on 2026-08-14** with SH45: API calls metered as a flow, one bucket per
tenant per UTC day per surface, with a `ShBillingExporter` hook for a closed day. It
changes no claim on these pages: the site sells the agent surface and the delivery layer,
and a tenant's invoice is neither.

**Block V closed the same day**, on SH759. Nineteen tasks of proving the vocabulary
against a running instance rather than against a mock, which is the block a marketing
page can least honestly quote, since what it bought is confidence that the claims already
here are true. Nothing to add; that is the point of recording it.

Block O closed on 2026-08-14 and changed no claim here, and the reason is worth one line because
the block's last task is the kind that usually does move copy. SH244 gave `folder.copy` to the
agent surface, so P1's own sentence on this site (*"a console-only feature is incomplete"*) has
one fewer counter-example behind it, and the ops line in the protocol sample (`ops: post.upsert,
post.move, folder.upsert, ...`) is abbreviated rather than counted, so a fifteenth op does not
make it wrong. A list that ends in an ellipsis is the reason this closure is a paragraph instead
of an edit.

Block M closed on 2026-08-14 and changed no claim here, and this is the one closure where that
needed checking rather than reasoning: SH230 **moved a published number**, taking
`response.manifest.terse` from 2410 to 2050. It is safe because `scripts/token-budgets.mjs` names
the four ceilings this site shows (`manifest.check`, `manifest`, `mcp.tools.list.total` and the
task figure), and the one that moved is not among them. That is the generator's whole design
working: a budget can change without anyone having to remember whether the marketing copy quoted
it, because the copy cannot quote a number the generator does not hand it.

Block V closed on 2026-08-13 and changed no claim here, and it is the block most likely to have
been assumed rather than checked: thirteen entries, every one of them a suite. The two numbers
this copy could have got wrong are the ones a testing block does not move: twelve MCP tools and
thirty-one CLI verbs, both still what `tools/list` and the command registry answer. SH714 did add
keys to `token-budgets.properties`, which is the file `scripts/token-budgets.mjs` reads on every
build, and they are additive: the generator names the keys it needs, so a `live.*` row it does not
ask for cannot reach the page. The one claim worth re-reading was the guardrails copy, because
SH717 and SH726 both changed what a refusal *says*, and neither sentence is quoted here, which is the
same property that saved Block N.

Block Q closed on 2026-08-12 and Block I on 2026-08-04, and neither changed a claim here, but
both are recorded **ten days late**, which is the finding rather than the verdict. Their sweeps
were never run: no closure paragraph, no `block drop`, and two headings standing over nothing in
`docs/ROADMAP.md` while `stats` printed `0` beside each. What saved them is that the docs half was
already green (every Block I and Block Q cluster in `COVERAGE.md` reads ✅), so the omission cost
two stale headings rather than a wrong page.

Checked rather than assumed, and the check is cheap because both blocks ended in internals: Block
Q's last four (SH692–SH695) are `verify`/`judge` reading regions and splitting kept markup, and
Block I's (SH408–SH412) are projection lints and memory-note decay. Nothing there is quotable, and
the `/replicate` copy names the four phases (clone, propose, convert, diff), which are still the
four commands. The lesson is the one Block S taught two paragraphs up in a different way: a block
completes when `stats` says so, and nothing was asking `stats`.

Block J closed on 2026-08-12 and changed no claim here, but unlike the two below it that was
not free: it was checked, and the check found the drift somewhere else. The site names
twelve MCP tools including `shio_marketplace` and thirty-one CLI verbs including
`marketplace`, so `site-content.ts` was already right; the **published documentation** was
not, and had been listing `shio_blueprint` and `shio_forget` as tools the product decided
against and never shipped. The reason the site survived a block the docs did not is the same
one that saved it from SH541: `site-content.ts` lists the tools as an array the copy iterates,
so a drifted entry is a visible wrong row rather than a number in a sentence nobody re-derives.

Block N closed on 2026-08-11 and repeatedly on 2026-08-14, and no closure changed a claim here.
The repetition is worth recording once rather than counting: "a closed block stays closed" is an
assumption the sweep can be built on and should not be, and this is a property of *that* block
rather than bad luck: "realignment of what already shipped" is a category that receives findings
forever, not a project that finishes, so each new finding empties it again. Filed against the tool
as `RK1180`: a standing block and a project block empty for different reasons and only one of them
is finished. Deliberately not a running tally, because a number here would need an edit per sweep
for no reader's benefit. No closure changed a claim here, and the second is the best
evidence for why. It shipped `SHIO_ADMIN_PASSWORD` (SH706), the one thing in the block a
marketing page would plausibly quote, and the copy that could have been wrong (`PLUGIN_INSTALL`
in `site-content.ts`) writes `SHIO_PASSWORD=...` as a placeholder rather than a credential. The
same property saved it from SH541: the licence is a **link** to `LICENSE` (`site-content.ts`'s
`license` key), not the string `GPL v3`, so moving 195 files to Apache 2.0 could not leave it
wrong. The published documentation spelled that one out and was wrong for the whole time after,
which is the argument for linking or eliding a fact instead of quoting it, and SH669 now derives
the licence from `package.json` + `LICENSE` on every conformance run. The rest of both halves is
instruments, which the site does not claim.

Block T closed on 2026-08-11 and changed no claim here, and the check that says so is worth
naming rather than assuming: the site's one documentation claim is the Resources card,
*"Install, model, author, deliver: the end-user documentation set"*, which is a statement
about the set's **shape**. Block T added five curator pages and rewrote two, so the set got
larger and more correct without that sentence becoming any less true. A block that only
*adds* pages cannot move a claim phrased about coverage; one that removed a hub could.

Block K closed on 2026-08-10 and changed no claim here, which is the verdict rather than
an omission: its user-facing half is `state=` on the agent read and `shio diff --against
published`, and the page's P9 line names the *kind* of proof ("a lint, the routes, a render
digest") rather than an inventory. The one figure it moved (`tools/list`, up ten tokens for
`shio_read`'s new argument) the build regenerated from
`shio-app/src/test/resources/token-budgets.properties` with nothing typed by hand, which is
what that generator exists for.

Block H closed on 2026-08-10 and the P2 comparison gained a row for it: the manifest
now **routes** an intent to one call rather than listing everything, which is the
finding that block ended on: it was already complete, and completeness was never the
constraint.
