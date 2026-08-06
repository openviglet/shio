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
- **A measured number carries its source.** The figures in `MEASURES` come from
  the agent surface's own budget tests. SH533 replaces the hand-copied list with
  the benchmark's JSON artefact, which is the only version that cannot drift.

## Static assets

Served from the site root, in [`public/`](public/): `robots.txt`, `sitemap.xml`,
`llms.txt`, `favicon.svg`, `og.svg` and the `CNAME` that points the GitHub Pages
deploy at the custom domain.

## Backlog

Block S in [`docs/ROADMAP.md`](../docs/ROADMAP.md) — the transcript player
(SH531), the plotted token budget (SH533), the curator's half (SH536), the
markdown twins (SH537), the Pages workflow (SH538), the depth pages (SH539) and
the public demo instance (SH540). The design behind each is `§XIX` in
[`docs/IMPROVEMENTS.md`](../docs/IMPROVEMENTS.md).
