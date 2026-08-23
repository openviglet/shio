# Shio — Roadmap (active backlog)

> **One line per unshipped task** — 📋 designed · 💭 idea · ⏳ partial · 🛠 in progress.
> Shipped work is in [CHANGELOG.md](CHANGELOG.md); the design behind a line is at its
> `→ §` pointer in [IMPROVEMENTS.md](IMPROVEMENTS.md). **A line is one sentence: what + why
> + pointer** — anything longer belongs at the pointer (SH341).
>
> This file is owned by **roadkeep** and never hand-edited: `roadkeep brief` picks and briefs
> the next task, `list`/`stats` count, `deps` resolves a blocker, `gaps` says what happened to
> an id that is in neither file. Blocks with no line left are finished — see the ledger.

## Block AQ — The console adopts the shared bento layer (§LIV)

- 📋 **SH887** (deps: viglet-design-system VDS8) **the console imports no bento component, so two products share a design system and look unrelated** — The layer must render here before a route is built on it, and only the package can ship it. → §LIV.2
- 📋 **SH888** (deps: SH887) **there is no bento route tree: the console root mounts a SidebarProvider every bento component refuses** — A parallel shell is what makes each surface reversible, which a 43-page migration needs most. → §LIV.3
- 📋 **SH889** (deps: SH888) **Shio has no nav declaration a rail and a palette can share, so each surface would be listed twice** — The package supplies the schema and both readers; declaring a surface once is what makes them agree. → §LIV.4
- 📋 **SH890** (deps: viglet-design-system VDS23) **the console's orange is spelled as Tailwind gradient classes inside a header component it no longer owns** — A shared component that names a colour makes one product look borrowed; the accent belongs in a token. → §LIV.5
- 📋 **SH891** (deps: SH888) **bento chrome strings have no Shio translations, so a shared rail and palette would render raw keys** — The console ships English and Portuguese, and chrome the package owns still needs both here. → §LIV.6
- 📋 **SH892** (deps: viglet-design-system VDS22) **the post, post-type and site editors are the heaviest forms here and a parallel route would fork all three** — The chrome adapter renders one form in either shell, so the field logic is written once. → §LIV.7
- 📋 **SH893** (deps: SH888) **no gate holds the bento a11y baseline here: icon-only labels, string ARIA values, the reduced-motion guard** — Those rules held in one product because one team read one file, and Shio has not read it. → §LIV.8
- 📋 **SH894** (deps: viglet-design-system VDS5) **nothing fails when a component is re-declared here instead of imported from the design system** — All 24 ui files are shims by discipline alone, and discipline is not an instrument. → §LIV.9
- 📋 **SH897** (deps: SH888) **e2e/console-check.mjs drives console routes only, so a bento surface is unvisited by the one script that clicks** — The check is the only thing that opens a real page, and it would go green across a migration it never saw. → §LIV.12

## Block AR — The curator's console, surface by surface (§LV)

- 📋 **SH898** (deps: SH888) **the console home is a flat card grid under a console header, and it is the first thing a curator sees** — It is the cheapest surface and the one that decides whether the redesign reads as real. → §LV.1
- 📋 **SH899** (deps: SH898) **the four site pages hand-roll their list and their forms, with no mosaic and no save-bar morph** — Sites are the entry to every other surface, so the list shape settles here for the ten that follow. → §LV.2
- 📋 **SH900** (deps: SH899) **the content browser and the two folder pages are the curator's main workspace and still console-shaped** — This is where a curator spends the day, and a half-migrated console is most visible here. → §LV.3
- 📋 **SH901** (deps: SH892) **the post editor and the new-post flow are the heaviest forms here, with 15 dynamic widgets inside them** — The widgets must not be rewritten, which is what the chrome adapter exists to make possible. → §LV.4
- 📋 **SH902** (deps: SH901) **the three post-type pages carry the attribute editor and a console sticky save bar with no scroll morph** — Post types are the one name-keyed surface and the editor a curator returns to most after posts. → §LV.5
- 📋 **SH903** (deps: SH900) **search, trash and the static-file list are console pages with no empty state, no mosaic and no hero** — These three are where an empty result is normal, which is exactly what the shared empty state is for. → §LV.6
- 📋 **SH904** (deps: SH899) **the six user, group and role pages are console forms and lists with identity fields inside the form** — Identity belongs in the hero, so these six are the clearest case for the entity shell. → §LV.7
- 📋 **SH905** (deps: SH904) **eleven infrastructure admin pages — tokens, providers, webhooks, email, tenants — are console list-and-form pairs** — They are the most repetitive surface here, so a shared list and shell pays for itself most. → §LV.8
- 📋 **SH906** (deps: SH905) **marketplace, the review queue and the activity log are console pages with bespoke layouts each** — The review queue is the curator's approval surface, and it should not be the last thing that looks old. → §LV.9
- 📋 **SH907** (deps: SH901) **the Universal Editor pages wrap a preview iframe whose bridge depends on the surrounding chrome** — This is the one surface where a shell swap can break behaviour rather than only appearance. → §LV.10
- 📋 **SH908** (deps: SH903) **the GraphiQL page and the not-found page are the last two console surfaces and both embed a third-party UI** — A not-found page in the wrong chrome is the most likely place a user lands mid-cutover. → §LV.11
- 📋 **SH909** (deps: SH906, SH907) **with every surface migrated, two route trees and two chromes are still mounted side by side** — The parallel route was chosen for reversibility, and an unclaimed reversal becomes permanent. → §LV.12
- 📋 **SH910** (deps: SH909) **the console shell, its sidebar and its page header stay declared after nothing renders them** — They are also the last Shio consumer of the design system's console-era exports. → §LV.13
- 📋 **SH911** (deps: SH909) **the published documentation shows the console this work replaces, in every screenshot and every walkthrough** — A curator following the docs after the cutover is looking at a product that no longer exists. → §LV.14

## Block AS — What Turing's index never receives (typed fields, a retry, a binary's text)

- 📋 **SH912** (deps: —) **a failed index push is never retried, so a published page stays unfindable until a human re-publishes** — The incident is recorded and nothing acts on it, which turns a thirty-second Turing restart into a permanent hole in the index. → §LVI.2
- 📋 **SH913** (deps: —) **a published PDF is indexed by title only, so its text answers no search and grounds no chat answer** — A school publishes its rules and fees as attachments, and the mapper reads a post's data JSON, which never holds a binary's text. → §LVI.3
- 📋 **SH914** (deps: —) **a post-type's own fields reach Turing folded into text, and numbers and booleans reach it not at all** — A school filtering by level, campus or tuition needs those as typed fields, and the mapper folds prose and drops every number on purpose. → §LVI.1

## Priority

## Done when — SH887

- **One page in this console renders the whole bento vocabulary** The subpath and its
  stylesheet are installed, and a hero, a form section, an entity tile and a mosaic all
  render correctly here rather than only in the other product.

## Done when — SH888

- **A bento route tree exists with no sidebar provider** The shell mounts the rail, the
  user menu and the back-to-top, the console tree is untouched, and no bento component
  reaches for the sidebar context.

## Done when — SH889

- **Every Shio surface is declared once, for both readers** One array against the
  package's entry schema feeds the rail and the palette, and adding a surface in the
  next block is a single-line change.

## Done when — SH890

- **No shared component names an orange, and no page overrides one** The brand accent is
  a token set once at the root, and the chip tint, the ring and the gradient stops all
  derive from it.

## Done when — SH891

- **The bento chrome renders in English and Portuguese** Every chrome string exists in
  both locale bundles with an object-form default, so a missing key renders as words
  rather than as an identifier.

## Done when — SH892

- **The three heavy forms render in either chrome, unforked** The chrome adapter is
  adopted, the fifteen widgets and the runtime schema are unchanged, and the console
  path still renders as it did.

## Done when — SH893

- **An accessibility violation on a bento surface fails a gate here** Icon-only labels,
  the screen-reader spans, string ARIA values and the reduced-motion guard are checked
  in this repository rather than assumed from the package.

## Done when — SH894

- **A component re-declared here fails this repository's CI** The export-manifest lint
  runs on every push and names the import that replaces the local copy, and all 24
  existing shims still resolve.

## Done when — SH897

- **The browser check visits bento routes and asserts bento facts** console-check.mjs
  knows the bento route tree and fails when the hero is missing, the card is not
  frosted, or the save bar is visible before any scroll.

## Done when — SH898

- **The home page is composed from package tiles, with a digest taken** It renders a
  bento hero and count tiles rather than a flat card grid, and the first
  before-and-after digest of this block is recorded from it.

## Done when — SH899

- **The four site pages render as a mosaic and an entity shell** The list uses the
  package's list page rather than a hand-rolled grid, the edit page lifts identity into
  the hero, and the tile shape is settled for the screens after it.

## Done when — SH900

- **The content browser works in bento with no console chrome left** The tree, the
  listing, selection, copy and move all behave as before, and where the shared
  vocabulary did not fit, what was used instead is written down.

## Done when — SH901

- **The post editor renders in bento with its widgets untouched** All fifteen widgets
  and the runtime schema are unchanged, identity moved to the hero, the save bar is the
  scroll morph, and the widgetSettings guard still holds.

## Done when — SH902

- **The post-type editor keeps its attribute editor and gains the morph** The ordered
  attribute list behaves as before, the name-keyed identity works with the shell's
  inline edit, and no console sticky save bar is left on these pages.

## Done when — SH903

- **Search, trash and static files each say their own empty** All three use the
  package's empty state with a title and description of their own, and the upload
  affordance on the file list still works.

## Done when — SH904

- **The six identity pages use the entity shell** Title, description and enabled are in
  the hero and are no longer form fields, and the shell's delete flow replaced three
  hand-rolled dialogs.

## Done when — SH905

- **All eleven infrastructure pages are migrated and each was measured** The render
  digest is run between pairs rather than only at the end, and any place the shell did
  not fit is recorded rather than worked around eleven times.

## Done when — SH906

- **The review queue no longer looks like the old console** All three pages are
  migrated, and the read-mostly ones use a plain section where a mosaic would have been
  the wrong shape.

## Done when — SH907

- **The Universal Editor still annotates and writes through the bridge** A real
  click-through confirms selection and editing inside the bento shell, since the sizing,
  the scroll container and the overlay stacking all changed.

## Done when — SH908

- **The GraphiQL and not-found pages render in bento** The embedded editor still works
  inside the new shell, and the not-found page is correct in the chrome a mid-cutover
  visitor will actually land in.

## Done when — SH909

- **Bento is the default and no route answers with the old shell** Console routes
  redirect to their bento equivalents so bookmarks survive, and the digest and the
  browser check both confirm one chrome is mounted.

## Done when — SH910

- **The console shell is deleted, not merely unreachable** The sidebar, page header and
  breadcrumb chrome are gone with the console route tree, and the duplication lint
  confirms nothing re-imports a console-era export.

## Done when — SH911

- **The published documentation shows the console that exists** The docs-shio
  walkthrough and every screenshot are retaken, in the same commit range as the cutover
  rather than as a follow-up.

## Non-goals (do NOT add as tasks)

Binding constraints — rationale in [IMPROVEMENTS.md](IMPROVEMENTS.md) §0.3–§0.4, §0.7 and
[specs/SH74-agent-native-cms.md](specs/SH74-agent-native-cms.md) §11.

- **Shio does not host a model.** No bundled LLM, no prompt runner, no provider API
  keys in the CMS, no "generate this page for me" button. Shio is the substrate an
  external agent drives; adding inference trades a defensible protocol position for
  a commodity feature and puts someone else's rate limits inside the content engine.
- **Do not rewrite the backend in Node/Next/Go/PHP.** Java/Spring Boot is the
  content engine and the glue with Turing + Dumont; a rewrite is high-cost,
  high-risk, zero user-visible value (§0.3).
- **No third API contract.** MCP is a *shape* over the CDA (SH1) + console services,
  never a parallel semantic (law P10).
- **The agent path is not a privilege escalation.** Tenant isolation (SH22),
  content ACL (SH54) and token scopes apply identically on every agent surface.
- **No feature whose only interface is the console** (law P1) — every capability
  needs an MCP/CLI/file path.
- **No GraalJS server-side JS template engine** (the former Block D, SH17/SH18 —
  dropped). An agent writes Next.js; an in-JVM script engine is sandboxing
  liability with no agent-side benefit. Groovy is likewise not the answer (§0.4).
- **Don't couple the JS SDK to React** — the framework-agnostic core (SH6) is the
  base; React is one consumer.
- **Don't optimize a benchmark by degrading correctness** — a terse response that
  omits something the agent must then ask for twice is a regression, not a win.
- **The instance does not run a browser.** Raster stays in the CLI — SH102's shio
  snapshot, Playwright an optional dynamic import — because that is where the agent
  already has a shell (P4) and where the human opens the PNG. Chromium per request is a
  different operational thing, and the diff ratio is a number the CLI already computes.
