# The bento boundary

Which of Turing's `components/bento` exports become a layer of this package, and
which stay in the product. Decided once, before any file moves, because the
alternative is deciding it twenty-seven times under time pressure and shipping a
package the second product has to import around.

## The rule

A component is **shared** when a second console would render the same thing.
A component is **product** when what makes it useful is one product's data, one
product's offer, or one product's brand.

Two consequences worth stating, because they are where the rule gets bent:

- **Generic mechanism plus product data is shared, and the data is a prop.** The
  nav rail is not Turing's because it renders Turing's routes; it is shared, and
  the routes are an argument. This is the package's existing non-goal — no
  product data here — applied to chrome.
- **Generic mechanism plus product content is product**, when the mechanism on
  its own is thin enough that this package already offers it. A stepped dialog is
  `Dialog` and `Stepper`, both already exported.

## Inventory

Counted from the barrel rather than from the file listing, because several files
export more than one component: **28 components, 4 hooks and 15 other bindings**
(tone maps, layout maths, the nav arrays and their helpers).

The roadmap line that opened this work said twenty-three components. That number
was taken per file, and misses `BentoTileGrid`, `BentoEntityTile` and
`BentoBackLink`, each of which lives inside a file named after a sibling. The
split below is 23 shared and 5 product — the twenty-three is a coincidence of
arithmetic, not the same twenty-three.

### Shared — the layer (23 components, 1 hook)

| Export | Coupling to lift before it moves |
|---|---|
| `BentoTile` | — |
| `BentoCountTile` | — |
| `BentoSection` | — |
| `BentoHero`, `BentoBackLink` | — |
| `BentoEmptyState` | — |
| `BentoFormSection` | — |
| `BentoSaveBar` | — |
| `BentoScrollSaveBar` | — |
| `BentoFormHero` | — |
| `useBentoScrollFade` | — |
| `BentoStatusMarker` | — |
| `BentoHeroIconPicker` | — |
| `BentoInlineEdit` | — |
| `BentoActionsMenu` | — |
| `BentoBackToTop` | — |
| `BentoEntityShell` | — |
| `BentoTileGrid` | — |
| `BentoEntityTile` | — |
| `BentoListPage` | Persists per-surface layout through `@/api/queries/bento-layout.queries`. The mosaic is generic; where the layout is stored is the product's. Lift to props: a resolved layout in, a save callback out. |
| `BentoNavRail` | Imports `ROUTES`. Takes the nav array as a prop. |
| `BentoUserMenu` | Imports `ROUTES` and `useFeatures`. Routes and feature flags become props. |
| `BentoCommandPalette` | Reads the nav config. Takes the same array the rail does — this is what VDS17 separates. |
| `BentoShortcutsDialog` | — |

### Product — stays in Turing (5 components, 1 hook, the nav data)

| Export | Why |
|---|---|
| `BentoActivationCard` | Turing's commercial offer rendered as a card. |
| `BentoQuotaBanner`, `useQuotaRefusal` | Quota is the shape of the licence, not of the chrome. |
| `BentoNoLlmState` | An empty state whose whole content is one product's capability. |
| `BentoVigletActivationDialog` | Activation flow — the offer again, as a modal. |
| `BentoFirstRunTour`, `BENTO_TOUR_SEEN_KEY`, `hasSeenBentoTour` | Decided below. |
| `BENTO_NAV_ITEMS`, `BENTO_NAV_SECTIONS`, `bentoNavTarget`, `bentoSectionByAreaRoute`, `bentoSectionAreaRoute`, `useVisibleBentoNav`, `useVisibleBentoSections` (`bento-nav.config.ts`) | Routes, privileges and entity ids, and the two hooks that filter them by privilege. This is the product data the package must never hold. The *schema* of an entry moves with the rail and the palette; the array does not. The span constants in the same file do not belong to it — see below. |

The first four are the ones the roadmap names as commercial, and they are the
reason this document exists: moving them as chrome would put one product's
business model into every product's console.

### Non-component modules

| Module | Verdict |
|---|---|
| `bento-tones.ts` (`BENTO_TONE_GRADIENTS`, `BentoTone`) | Shared, but re-keyed as CSS custom properties rather than moved as Tailwind class strings — a shared component that names its own colours makes one of two products look wrong. |
| `bento-layout.ts` — `BENTO_EMPHASIS_SPAN`, `BENTO_EMPHASIS_NEXT`, `resolveBentoLayout`, `toBentoLayoutEntries` | Shared. Pure functions and maps over a layout response; no product data in them. |
| `BENTO_SPAN_FEATURED`, `BENTO_SPAN_WIDE`, `BENTO_SPAN_SQUARE` | Shared, and currently in the wrong file — they are grid spans, defined in `bento-nav.config.ts` because the nav array is what sizes tiles with them. Moving the layer means splitting them out to sit beside `bento-layout.ts`; the array that uses them stays behind. |
| `bento-scroll-fade.ts` | Shared — already listed above as `useBentoScrollFade`. |
| `bento-nav.config.ts` | Product, as above. |
| `bento.styles.css` | Shared. The frosted surface and the hover-lift are what make a page read as bento. |

## The first-run tour

**Product.** It was the one genuinely open question, and the argument runs both
ways: the overlay, the step index and the seen-flag are generic, while the four
steps are Turing's.

It lands on product because of what is left after the content is removed. The
mechanism is a `Dialog` holding a step index and a `localStorage` flag — perhaps
sixty lines, over primitives this package already exports. Against that, every
distinctive thing in the file is Turing's: the `TurLogo` it renders, the four
steps, and a first step whose entire subject is turning the product's AI on.
Extracting a tour engine to serve one product's onboarding is generality bought
before there is a second buyer. If a second console wants a tour, that is the
moment the shape of a shared one is actually known.

The mechanism is cheap to rebuild and the content is not portable, which is the
test this rule uses everywhere else.

## What this does not decide

- **How** a component moves. That is VDS11 and the lines after it.
- Whether a moved component keeps its props. It does — a move whose diff also
  changes behaviour cannot be reviewed against the pages that depend on it.
- Where the authoring contract lives. Turing's `CONVENTIONS.md` is addressed to
  Turing; VDS20 rewrites it product-neutral and brings it here.
