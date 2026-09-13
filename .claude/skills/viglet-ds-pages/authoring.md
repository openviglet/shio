# Authoring a bento page

The rules that keep every bento screen looking like the same product. They held
across a hundred-odd pages in one console because one team read one file; this
is that file, addressed to anyone importing
`@viglet/viglet-design-system/bento`.

The first mistake is a page that looks bento inside a console that does not.
Adopt the shell before the pages.

## 1. The shell, and the page's regions

The shell is these regions, and every one of them is owned once — by the shell, by a page,
or by the product passing a prop. Almost all of the divergence between two products built
from this layer is an argument about that ownership rather than about a component.
[docs/reference/page-anatomy.dc.html](reference/page-anatomy.dc.html) draws it.

**The nav is the rail** — fixed, one width, desktop only, with its gutter reserved by
`bento-rail-gutter` on whatever wraps the routed page (§7).

**The header carries a set, in this order:** the mark and wordmark; a back control where
the route has a parent; the palette trigger, with the platform's own keyboard hint. On the
trailing edge: the locale where a second one ships, the ground, and the signed-in user.
`BentoBackToTop` sits at the corner.

Two things the header is not. It is not a second always-visible nav — the rail is the nav
and the palette is the mobile one, and a second eats the width the content needs. And it is
not a home for one surface's controls: a switcher or a pending count belongs to the surface
that owns it, not to every page that renders beneath it.

**The shell owns the reading column.** `main` sets the max width, the gutters and the
vertical rhythm once, so every page begins and ends on the same line; a page sets none of
the three. A narrower column for a single-question form is a variant the shell offers by
name, not a class each page repeats. The moment pages set their own they disagree, and the
defect exists only *between* screens — which is why nobody reviewing one of them sees it.

**A page may own an aside**, inside that column and scrolling with it: filters, a contents
list, a conversation. That is not the console era's sidebar, which collapses, remembers its
width and pushes content — the reason that era needs a provider and this one does not.

**The footer is the one region this chrome has not settled.** `AppFooter` is exported — a
hairline, then the product name, its version and a few links — and it is written `mt-auto`,
so it expects a shell that is a flex column with the main growing. No bento shell mounts
one. Either the shell carries a footer for every page or the chrome has none; what must not
happen is one page growing its own.

**The signed-in user is two routes**, and `BentoUserMenu` has a default for neither (§3).
Anything past them — a tenant, a review count — belongs to the surface that owns it.

## 2. Structure — thin config, not bespoke pages

There are three page shapes. Almost every screen is one of them plus data.

**A detail screen** is `BentoEntityShell` wrapping a form. The shell owns the
identity hero, the save-bar morph and the delete flow; the form is a render prop
receiving `{ staged, onStateChange }` and groups its fields in
`BentoFormSection`s. Wire the query hooks in the page and put no shell mechanics
there.

**A list screen** is one `BentoListPage` call with a `renderTile`. Use
`BentoEntityTile` for the common icon-chip + status-pill + title + meta shape;
hand-roll a tile only when the entity genuinely needs a different layout.

**A form screen with its own hero** is `BentoFormHero` as the first child inside
the `<form>` it submits. It renders both halves of the morph itself.

**A frosted box with arbitrary content** — a stats strip, a toolbar, a listing,
a message — is `BentoPanel`. It is the one container in this layer with **no
heading**, which is the point rather than an omission: those surfaces sit under a
hero that already names the page, and a heading on them is noise that also puts a
section in the document outline the page does not have. Two class slots,
`className` on the frosted container and `contentClassName` on the inner wrapper,
and no padding of its own — a table wants `p-0` and a toolbar wants `py-2`.
Never hand-roll `bento-glass rounded-2xl border`: the moment two call sites pick
different radii the product is inconsistent for a reason no diff shows.

Three rules that outrank convenience:

- **A back-link eyebrow leads with the arrow, and only a back-link does.** The
  eyebrow above a hero's title is small, upper-case and set in muted text, which
  is the same treatment whether it names the parent list or merely says what kind
  of thing this is. The arrow is what separates them: with it the line reads as
  the way back, without it as a label. So it is not decoration, and it is not
  optional on one page because the layout looked tidier — it is the only
  difference a reader has.
  - `BentoEntityShell` gives you this for free: it links the eyebrow to
    `listRoute` and puts the arrow in, so you pass the label alone.
  - Every other hero takes `backTo` + `backLabel`. `BentoHero` and
    `BentoFormHero` both render `BentoBackLink` from them.
  - An eyebrow that needs something after the link — a status marker, a count —
    is the one case for passing `eyebrow` yourself, and it composes
    `BentoBackLink` rather than re-typing an arrow. `eyebrow` wins over `backTo`,
    so a page that sets both silently loses the arrow.
  - **Never put one on an eyebrow that navigates nowhere.** An arrow promises a
    destination, and a promise the click does not keep is worse than the plain
    label would have been.
- **Identity lives in the hero, never in the form.** Title, description, icon
  and enabled state are the shell's; a form field for any of them is a second
  place to edit the same thing.
- **The save bar is always the morph.** Controls start in the hero and a fixed
  bar fades in as the hero scrolls away, so nothing is duplicated on screen and
  the bar appears exactly when the title leaves. Never a hand-rolled
  `<div className="sticky …">`, and never a permanently visible bar.
  - `BentoEntityShell` gives you this for free.
  - Every own-hero form uses `BentoFormHero`. Do not compose a fade-out control
    group and a `BentoScrollSaveBar` yourself: they are two halves that drift.
  - A page that saves **imperatively** passes its own buttons through
    `BentoFormHero`'s `actions`, which replaces the default pair in *both*
    copies and keeps the morph.
  - Keep the **destructive** action in the hero's `trailing` only. A controlled
    dialog rendered twice opens two modals at once, and an action inside the
    fade-out group vanishes as the reader scrolls.

## 3. What the package will not hold

The layer is chrome; the map of your product is yours. Four things arrive as
props, and the package has no default for any of them:

| You supply | To |
|---|---|
| The nav array, already filtered by privilege or licence | `BentoNavRail`, `BentoCommandPalette` |
| Routes for account, sign-out and any tenancy surfaces | `BentoUserMenu` |
| A resolved layout and callbacks to persist it | `BentoListPage` |
| A keyword suggester, if you want one | `IconPickerDialog` |

`BentoNavItem` is the render contract, deliberately narrower than what you will
keep: carry `privilege`, licence flags and anything else on your own type, and
pass the filtered result.

An entry appears when its route does. That is how you say a reader may see
something — the package cannot read your feature model, and should not try.

## 4. Colour — a tone is a token

No component in the layer names a colour. A tone is
`--vg-bento-tone-<name>-from` / `-to` in the preset, and the chip reads them, so
you re-key the palette by redefining variables rather than forking a component.
The status intents (`on`, `warn`, `error`, `danger`) work the same way.

Accents — a focus border, the rail's active marker, a hover ring — take your
`--primary`. A shared component marking "you are here" in a colour of its own is
the clearest way to make one product look wrong.

**Claim `--primary`, or four surfaces stay neutral.** The rail's active marker is
not the only thing reading it: `.bento-tile:hover` takes its glow from
`--primary`, `.bento-editing` and `.bento-new-tile` take their borders, and the
default `Button` variant is `bg-primary text-primary-foreground`. The preset's
value is a neutral rather than a brand, so keying only the accent leaves all four
near-black on light and near-white on dark — which is what "you are here" looks
like in a product that thinks it has re-keyed.

Claim it at `:root`, through the inputs, the way the accent is claimed:

```css
:root {
  --vg-primary-base: …;                 /* the mark, on light */
  --vg-primary-base-dark: …;            /* and on dark */
  --vg-primary-foreground-base: …;      /* what the solid fill carries */
  --vg-primary-foreground-base-dark: …;
}
```

**Setting `--vg-primary` itself is the mistake the inputs exist to prevent.** Your
stylesheet imports this package and then declares its own `:root`, so it lands
after the preset's dark block at the same specificity and in no layer. One value
set there wins on *both* grounds, and the dark ground silently gets the light
value. The inputs are read per ground, so you never write a dark block.

The solid fill carries text, so that pair holds 4.5:1 on both grounds (§6). An
accent stop at full chroma usually does not, so this value is often a deeper step
than the one the chip is drawn with.

If you need a one-off tint, set `--bento-tone-from` / `--bento-tone-to` on a
subtree instead of touching the tokens.

## 5. i18n

- Reuse the keys you already have. Do not mint a parallel namespace for a
  surface that already has one: the same entity rendered in two chromes is the
  same entity.
- Always pass a default in the **object** form: `t("key", { defaultValue: "…" })`.
  The string form is ambiguous and test mocks generally do not honour it.
- Add every new key to every locale you ship.

## 6. Accessibility

This is a gate, not advice — the catalogue's stories run under axe on every
push, and a violation fails the build.

- **Icon-only controls** get an `aria-label`. Decorative glyphs get
  `aria-hidden` and convey their meaning through an adjacent `sr-only` span:
  screen readers do not reliably announce `aria-label` on a bare `<svg>`, and
  Testing Library's `getByLabelText` will not match it either.
- **Keyboard**: inline edit is reachable and committable by keyboard, the
  palette is arrow-key and Enter navigable, and active rows carry
  `aria-current="page"` (rail) or `aria-selected` (palette).
- **ARIA values are string literals** — `aria-expanded="true"`,
  `aria-selected={active ? "true" : "false"}`. A bare attribute or a
  `{boolean}` expression is a lint failure.
- **Headings increase by one.** The hero is `h1`, so a section under it is `h2`
  — which is what `BentoFormSection` renders by default. A repeated label, like
  the sticky bar's title, is not a heading at all.
- **Contrast holds at 4.5:1**, including text on a tinted surface. Tinted pills
  need the darker text token in light mode.
- **Reduced motion**: every animation in `bento.css` is turned off under
  `@media (prefers-reduced-motion: reduce)`, and a test asserts that every
  animating selector is named in that guard. A new keyframe that is not is a
  failing build, not a review comment.

## 7. Responsive

- The grid is `grid-cols-2 md:grid-cols-4 lg:grid-cols-6` with `col-span-2`
  tiles and `row-span-2` for a featured one. **Keep spans in multiples of two**
  so tiles reflow cleanly at every breakpoint — this is what keeps two consoles'
  grids aligned.
- The nav rail is desktop-only. Reserve its gutter with `bento-rail-gutter` on
  whatever wraps the routed page. On mobile, navigation is the header's command
  trigger and the global shortcut; do not add a second always-visible nav that
  eats mobile width.
- There is **no sidebar provider**, and no context between the shell's pieces.
  The console era needs one because its sidebar collapses, remembers and pushes
  content; the rail is fixed, one width, and hidden below `md`. That is about the
  shell — a page's own aside is §1, and it needs no provider either.

## 8. Tests

- Every shared component here carries vitest and Testing Library coverage, so a
  regression surfaces once rather than in each console.
- Router-dependent components render inside a `MemoryRouter`.
- Assert on the readable `defaultValue` strings rather than on i18n keys, so a
  test reads like the screen does.
- jsdom implements neither `matchMedia`, `ResizeObserver` nor `scrollIntoView`.
  This package's own setup polyfills all three; a consumer testing these
  components will need the same.

## What this does not cover

Which components are the shared layer and which stay in a product is
[docs/BENTO-BOUNDARY.md](BENTO-BOUNDARY.md).

Every rule above, drawn rather than stated, is [docs/reference/](reference/) — eight
artboards, one per decision. Look there when a sentence here is clear and you still cannot
picture the page it describes.
