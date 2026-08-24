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

- 📋 **SH893** (deps: SH888 ✅) **no gate holds the bento a11y baseline here: icon-only labels, string ARIA values, the reduced-motion guard** — Those rules held in one product because one team read one file, and Shio has not read it. → §LIV.8
- 📋 **SH897** (deps: SH888 ✅) **e2e/console-check.mjs drives console routes only, so a bento surface is unvisited by the one script that clicks** — The check is the only thing that opens a real page, and it would go green across a migration it never saw. → §LIV.12
- 📋 **SH919** (deps: —) **the duplicate gate discards its first positional root, so naming a directory scans a different one** — A gate that reports a clean tree because it never read the tree you named is worse than no gate. → §LIV.15
- 📋 **SH920** (deps: —) **two user contexts now exist and the two shells mount different ones, so a migrated page reads the wrong hook** — Each surface Block AR moves crosses that seam, and the failure is a blank shell rather than a type error. → §LIV.16

## Block AR — The curator's console, surface by surface (§LV)

- 📋 **SH898** (deps: SH888 ✅) **the console home is a flat card grid under a console header, and it is the first thing a curator sees** — It is the cheapest surface and the one that decides whether the redesign reads as real. → §LV.1
- 📋 **SH899** (deps: SH898) **the four site pages hand-roll their list and their forms, with no mosaic and no save-bar morph** — Sites are the entry to every other surface, so the list shape settles here for the ten that follow. → §LV.2
- 📋 **SH900** (deps: SH899) **the content browser and the two folder pages are the curator's main workspace and still console-shaped** — This is where a curator spends the day, and a half-migrated console is most visible here. → §LV.3
- 📋 **SH901** (deps: SH892 ✅) **the post editor and the new-post flow are the heaviest forms here, with 15 dynamic widgets inside them** — The widgets must not be rewritten, which is what the chrome adapter exists to make possible. → §LV.4
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

## Priority

## Done when — SH893

- **An accessibility violation on a bento surface fails a gate here** Icon-only labels,
  the screen-reader spans, string ARIA values and the reduced-motion guard are checked
  in this repository rather than assumed from the package.

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
