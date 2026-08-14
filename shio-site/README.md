# shio-site

The public site for **Viglet Shio**, published to
[shio.viglet.org](https://shio.viglet.org/).

Stack: **Vite + React 19 + TypeScript + Tailwind CSS v4 + shadcn/ui** — the same
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

1. `tsc -b` — the typecheck gate.
2. `vite build` — the client bundle plus the `index.html` template.
3. `vite build --ssr src/entry-server.tsx` — `render()` and the route table.
4. `node scripts/prerender.mjs` — one static `index.html` per route, then
   `scripts/og-image.mjs` rasterizes `public/og.svg` to `dist/og.png`.

There is **no router**. `ROUTES` in [`src/App.tsx`](src/App.tsx) maps a pathname
to a page, and the prerender and the client both read it — so a bleeding-edge
Vite has nothing framework-shaped to break. Every route is a full page load.

**Adding a page is two rows**, and the build refuses to let you write only one:
a `ROUTES` entry in `App.tsx`, and a `routes` entry in
[`src/entry-server.tsx`](src/entry-server.tsx) carrying the title and
description. `routesAreDeclared()` runs at import time and throws if the two
tables disagree in either direction — a route with no page would otherwise
prerender the landing page under someone else's `<title>`, and a page missing
from the table would silently never get a file.

The prerender patches the `<head>` with `replaceOrThrow` for the same reason: if
the template drifts, the build fails rather than shipping a page carrying
another route's `<title>`.

## Content

**All page copy lives in [`src/lib/site-content.ts`](src/lib/site-content.ts)** —
the ten design laws, the hero transcript, the MCP tool list, the CLI verbs, the
token measurements, the guardrails, the comparison matrix and the links. The
components in [`src/components/sections/`](src/components/sections/) only render
it.

Two rules for editing that file:

- **Every claim is about a real surface.** A route, a tool name, a flag or an
  error body that appears there is the one the build produces. `docs/agents/` is
  the authority for each.
- **A measured number is generated, never typed.** `scripts/token-budgets.mjs`
  reads `shio-app/src/test/resources/token-budgets.properties` — the one file
  the Java suite asserts every response against — and writes
  `src/lib/token-budgets.generated.json` on every build. A renamed key fails the
  build rather than dropping a bar.

## The Markdown twins

Every route also emits `<path>.md` — `/` → `/index.md`, everything else appends
`.md`. Across the ten routes that is ~64 KB of Markdown against ~465 KB of HTML,
and the prerender prints the ratio on every build.

The twin is **converted from the same render**, not written separately: the copy
lives in `src/lib/*-content.ts` but the *composition* lives in the JSX, so
generating Markdown from the data alone would re-declare that composition and
the two would drift. [`scripts/markdown.mjs`](scripts/markdown.mjs) does the
conversion and carries the reasoning; it is the one piece of real logic here and
therefore the one piece with a test (`pnpm --filter shio-site test`).

Two things to know when writing a page:

- The nav and the footer never reach a twin — only `<main>` is converted.
- **`data-md-skip` on an element drops it from the twin**, children included.
  The call to action carries it: it converts a reader and costs an agent forty
  identical words on ten pages.

`dist/manifest.json` lists the routes, their twins and their sizes in the shape
`/api/v2/agent/manifest` uses, so the discovery pattern the product teaches is
the one this site is discovered by.

## Static assets

Served from the site root, in [`public/`](public/): `robots.txt`, `sitemap.xml`,
`llms.txt`, `favicon.svg`, `og.svg` and the `CNAME` that points the GitHub Pages
deploy at the custom domain.

## Publish

[`.github/workflows/publish-github-pages.yml`](../.github/workflows/publish-github-pages.yml)
is the whole path to [shio.viglet.org](https://shio.viglet.org/): it installs the
workspace, runs `pnpm run build:site`, assembles `_site/` from `dist/` and pushes
that to the `github-pages` branch of `openviglet/shio-ce`.

It is **`workflow_dispatch` only**, like `publish-js-sdk.yml` — a deploy that
fires on every push to a long-lived branch is one nobody can hold still while
reviewing it. Run it from the Actions tab when the site is ready to go out.

The build *is* the gate: `tsc -b` fails the job on a type error, and the
prerender throws on a drifted template or on a route declared in only one of the
two tables. `dist-ssr/` is a build input and never reaches the published tree.
The job needs the `GH_PKG_TOKEN` secret, the same one `publish-docker.yml` uses
for that repository.

## Backlog

Block S in [`docs/ROADMAP.md`](../docs/ROADMAP.md) — the public read-only demo
instance (SH540) is all that is left, and it is what turns the scripted session
in the hero into a live one. Its design is `§XIX.11` in
[`docs/IMPROVEMENTS.md`](../docs/IMPROVEMENTS.md).

Block V closed on 2026-08-13 and changed no claim here, and it is the block most likely to have
been assumed rather than checked: thirteen entries, every one of them a suite. The two numbers
this copy could have got wrong are the ones a testing block does not move — twelve MCP tools and
thirty-one CLI verbs, both still what `tools/list` and the command registry answer. SH714 did add
keys to `token-budgets.properties`, which is the file `scripts/token-budgets.mjs` reads on every
build, and they are additive: the generator names the keys it needs, so a `live.*` row it does not
ask for cannot reach the page. The one claim worth re-reading was the guardrails copy, because
SH717 and SH726 both changed what a refusal *says* — neither sentence is quoted here, which is the
same property that saved Block N.

Block J closed on 2026-08-12 and changed no claim here, but unlike the two below it that was
not free — it was checked, and the check found the drift somewhere else. The site names
twelve MCP tools including `shio_marketplace` and thirty-one CLI verbs including
`marketplace`, so `site-content.ts` was already right; the **published documentation** was
not, and had been listing `shio_blueprint` and `shio_forget` as tools the product decided
against and never shipped. The reason the site survived a block the docs did not is the same
one that saved it from SH541: `site-content.ts` lists the tools as an array the copy iterates,
so a drifted entry is a visible wrong row rather than a number in a sentence nobody re-derives.

Block N closed on 2026-08-11 and repeatedly on 2026-08-14, and no closure changed a claim here.
The repetition is worth recording once rather than counting: "a closed block stays closed" is an
assumption the sweep can be built on and should not be, and this is a property of *that* block
rather than bad luck — "realignment of what already shipped" is a category that receives findings
forever, not a project that finishes, so each new finding empties it again. Filed against the tool
as `RK1180`: a standing block and a project block empty for different reasons and only one of them
is finished. Deliberately not a running tally, because a number here would need an edit per sweep
for no reader's benefit. No closure changed a claim here, and the second is the best
evidence for why. It shipped `SHIO_ADMIN_PASSWORD` (SH706), the one thing in the block a
marketing page would plausibly quote, and the copy that could have been wrong — `PLUGIN_INSTALL`
in `site-content.ts` — writes `SHIO_PASSWORD=...` as a placeholder rather than a credential. The
same property saved it from SH541: the licence is a **link** to `LICENSE` (`site-content.ts`'s
`license` key), not the string `GPL v3`, so moving 195 files to Apache 2.0 could not leave it
wrong. The published documentation spelled that one out and was wrong for the whole time after —
which is the argument for linking or eliding a fact instead of quoting it, and SH669 now derives
the licence from `package.json` + `LICENSE` on every conformance run. The rest of both halves is
instruments, which the site does not claim.

Block T closed on 2026-08-11 and changed no claim here, and the check that says so is worth
naming rather than assuming: the site's one documentation claim is the Resources card,
*"Install, model, author, deliver — the end-user documentation set"*, which is a statement
about the set's **shape**. Block T added five curator pages and rewrote two, so the set got
larger and more correct without that sentence becoming any less true. A block that only
*adds* pages cannot move a claim phrased about coverage; one that removed a hub could.

Block K closed on 2026-08-10 and changed no claim here, which is the verdict rather than
an omission: its user-facing half is `state=` on the agent read and `shio diff --against
published`, and the page's P9 line names the *kind* of proof ("a lint, the routes, a render
digest") rather than an inventory. The one figure it moved — `tools/list`, up ten tokens for
`shio_read`'s new argument — the build regenerated from
`shio-app/src/test/resources/token-budgets.properties` with nothing typed by hand, which is
what that generator exists for.

Block H closed on 2026-08-10 and the P2 comparison gained a row for it: the manifest
now **routes** an intent to one call rather than listing everything, which is the
finding that block ended on — it was already complete, and completeness was never the
constraint.
