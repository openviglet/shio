---
title: Replication
description: "Point Shio at a site that already exists: capture it, propose what could become editable content, convert it, and serve it, in fidelity or authorable mode."
sidebar:
  label: Replication
---

Point Shio at a website that already exists and get back a site a curator can edit.

That is three commands (`clone`, `propose`, `convert`) and one decision you should make
*before* you run them, because choosing wrong wastes the run.

---

![Clone, propose, convert, publish, serve, judge](/shio/docs/img/diagrams/shio-replication-flow.svg)

## Two modes, and the choice is the whole thing

| | **Fidelity** | **Authorable** |
|---|---|---|
| What `convert` writes | The source's markup, verbatim, behind a Theme + Page Layout + Page | The captured blocks as **sections**: real fields, in a generated layout that annotates each one |
| How close it looks | Pixel-identical is achievable and has been achieved | The pixels have moved: the captured CSS classes are dropped and the page is drawn with Shio's own vocabulary, styled by a theme whose colours are taken from the source |
| Can a curator edit it? | **Not on its own**: verbatim markup carries no field annotations, so the Universal Editor opens a page with nothing to edit. `convert --annotate` adds them without touching the pixels: see [Fidelity with editable blocks](#fidelity-with-editable-blocks) | **Yes.** Every field is annotated and editable |
| On a page that mixes them | The kept markup is a section of its own, movable and replaceable: see below | The accepted blocks, in the same list, in source order |
| Use it for | Proving the capture is faithful; archiving a site as-is | Actually adopting the site into a CMS |

You choose by what you accept in the `propose` step: **accept nothing and you get
fidelity; accept the blocks and you get authorable content.**

### Most pages are both, and that is the point

The table above is honest about a **block**. A *page* has three shapes, and the third is
the ordinary one: accept the hero, leave the source's header and footer, and the page is
**mixed**.

The kept markup on such a page is not a leftover you have to live with. Each run of it is
a **`SectionMarkup` post in the page's own `sections` list**, in the place it stood, so a
curator can move it, replace it with a real section, or delete it, from the same control
they compose the rest of the page with. That is how a replica becomes authorable one block
at a time instead of all at once.

Two things follow, and both are visible on the page:

- **A mixed page keeps the source's stylesheet** for as long as any of its markup is still
  there. An authorable page does not receive it, deliberately: it draws `shio-*` names and
  the captured CSS defines none of them.
- **The page's own title is editable on a mixed page too.** It is annotated whether or not
  Shio drew it, so the Universal Editor reaches it even where the visible heading is the
  one the capture recorded.

Authorable is the mode that makes the replica a *CMS site* rather than a copy. Fidelity is
the pleasant one to test (it reaches 0.00% difference and looks finished) which is
exactly why a run that only tested fidelity has proved nothing about the path you probably
care about.

### Fidelity with editable blocks

`--annotate` is the third shape, and it is the one to reach for when the source's own
markup is what you want to keep *and* somebody still has to be able to change the words.

```bash
shio convert --site mysite --create-site --annotate
```

`propose` already worked out which node on a captured page is a heading and which is an
intro; the authorable mode spends that mapping one way: read the values out, write them
into section posts, redraw the page with Shio's vocabulary. `--annotate` spends it the
other way: the mapping is **stamped onto the source's own markup** as editable fields, and
nothing else about the page changes. Strip the annotations back out and the bytes are the
source's body exactly.

Measured on a real site: **2.02% pixel difference** for a page carrying 14 editable fields,
against **6.76%** for the authorable rebuild of the same page. The page keeps its own
classes, its own stylesheet and its own layout, and a curator can open it in the Universal
Editor and fix a headline.

Three things worth knowing before you use it:

- **You ask for it.** Plain fidelity is defined by having no editable field, and a flag is
  what keeps that definition true. Without `--annotate`, nothing changes.
- **Wholly verbatim pages only.** On a mixed page the kept markup is split into
  `SectionMarkup` posts, and a field has to live on the post holding the bytes it
  annotates, so the run annotates the pages nobody accepted a block on.
- **Nothing is stored until somebody edits it.** The text is already in the markup, so an
  untouched annotation costs no field, no post-type change and no bytes. Edit one and the
  page shows your value in place of the source's; the rest of the page is untouched.

---

## The pipeline

### 1. Capture

```bash
shio clone https://example.com --depth 3 --assets --markup --site mysite
```

`clone` walks the origin and writes an **inventory and a report**: no content yet. Flags
worth knowing:

| Flag | Effect |
|---|---|
| `--assets` | Download the referenced images, stylesheets, fonts, icons, including the ones a written stylesheet's `url()` calls name |
| `--markup` | Keep each page's markup, which is what fidelity mode serves |
| `--render` | Ask a browser to render the page first, for a source whose content is built by JavaScript |
| `--scripts` | Inventory the third-party scripts, so `propose` can offer them as a Site Scripts list instead of silently dropping them |
| `--depth N` | How far to walk |
| `--refresh` | Re-check a capture you already have, paying only for what actually moved |

The capture separates what a replica **needs to render** from what merely exists, and a
downloaded byte no post references is reported as its own finding: a replica whose images
are all present but unreferenced is not a replica.

#### Re-capturing without paying for it again

A capture of a real site is the expensive step: a few hundred pages and a few hundred
files can take minutes and tens of megabytes, while everything after it reads the
inventory off disk and runs in seconds. `--refresh` re-checks a capture you already have:

```bash
shio clone --refresh --assets --markup --site mysite
```

Pass no URL. The existing capture says where the site is, so a mistyped origin cannot
refresh against a different host. Every page **and every asset** is asked for
conditionally, using what the last capture recorded about it, and anything the server
reports as unchanged costs one request and no download. The report says what it did:
how many pages and assets were unchanged, how many were re-read, and, when it happens,
which files it had to fetch in full because they were no longer in the tree.

Two things to know. A refresh re-checks **what was captured**; it does not walk for pages
that have appeared since, though it counts and names any it saw linked from a page that
changed. And a capture made before this existed carries nothing to be conditional about,
so its first refresh fetches in full and every later one is cheap.

### 2. Propose

```bash
shio propose                     # read what it would do
shio propose --accept all        # …and accept it
```

`propose` reports, per captured block, which section type it would become, which fields it
would carry, the markup it would keep, and **what each conversion would lose**. That last
column is why this is a separate step and not a flag, you are approving a lossy
transformation, so you get to see the loss.

Every candidate also carries **`keeps N%`**: how much of that block's own text the fields
would actually hold. The rest has no field to live in, so it is what a converted page loses
even when the section type is right. A block that would keep less than half its text is
walked into rather than accepted whole, and the ones that cannot be improved on are named in
a note before anything is written.

Each candidate names the **evidence** that proposed its type, so you can check the claim against
the page instead of taking the type name on trust: *two or more `<details>`, which is a disclosure
list*, *3 `<dt>`/`<dd>` pairs, which is a definition list*, *4 repeated units, each with text*. A
block whose shape matches no rule is proposed as a post-type of its own rather than forced into the
nearest one: the classifier answers "I do not know" instead of guessing, and that answer is the one
worth trusting the others by.

`--accept all` **declines a candidate whose derivation cannot fill a field the section type
requires**, and says which and why. Those blocks stay verbatim markup, which keeps their
content, instead of becoming a section `shio verify` would refuse with
`required-field-empty`. Naming an id accepts it anyway (that is your call), and the command
tells you what the gate will say about it.

It also proposes the captured stylesheet as **design tokens plus CSS**, and the captured
third-party scripts as a Site Scripts list: every entry for the head, with a consent
category where the host declared one and a blank where it did not. A guess presented as a
fact would be worse than a blank on a consent gate, and the report counts how many need a
curator.

### 3. Convert

```bash
shio convert --site mysite --create-site
```

`convert` applies the accepted plan through the ordinary desired-state path: one `Page` per
captured page at the source's own URL, the sections it composes, a `Theme`, a
`PageLayout` bound to both modes, and the captured bytes uploaded as `File` posts reachable
at **the paths their source used**.

Everything lands as **drafts**.

Add `--annotate` to keep the source's bytes *and* get editable fields on the pages that
kept all of their markup: see [Fidelity with editable
blocks](#fidelity-with-editable-blocks).

#### The site remembers where it came from

The capture, the proposal and the plan stay on the machine that ran them. What the instance
keeps is a note: after a real conversion (not `--dry-run`, `--check` or `--out`), `convert`
records in the site's memory, under the key `replication`, the source URL, the date, how many
pages and sections were written, a short digest of the plan, and the `shio clone` command that
runs the round again.

A curator opening the site in the console sees it on the site's settings page, under **Where
this site came from**. An agent reads it with `shio_memory`, or `shio memory --site mysite`. It
travels with `shio export`, and a later conversion replaces it.

#### The replica's navigation is the one the source stated

An authorable page is redrawn with Shio's vocabulary, and the source's own `<nav>` is part
of what that drops. `convert` puts one back, from what the capture recorded rather than
from the content model: `clone` reads `<nav>` and `[role=navigation]` (a footer's links
are links), keeps the entries **in source order**, and counts how many pages each appears
on. An entry that appears on every page is a menu item; one that appears on a single page
is a link, and is left out.

Those become a `Menu` post and a `MenuItem` each, in their own `/menu` folder, and the
generated layout renders them. They are ordinary content, so a curator relabels, reorders
or deletes one from the console like anything else.

The run says what it took and what it saw:

```
menu: 2 entry(ies) from the source's own navigation, in ITS order, of 4 link(s) it recorded
```

Both numbers, because the difference is what you check: the two that did not become
entries were considered. **A source that states no navigation** gets the previous
behaviour: the layout lists the site's pages by title.

#### The theme an authorable replica is presented with

An authorable page is drawn with Shio's own `shio-*` vocabulary (a container, a stack, a
grid, a card), and the `Theme` `convert` writes styles every one of those roles. Its
**values come from the source**: `convert` reads the palette `propose` extracted and fills
the theme's tokens from it, matching each one by the role the source uses it in rather than
by the name the source gave it.

The run says what came from where, and what kept a default:

```
presented by mysite (Shio), the one stylesheet the layout names
palette: 5 of 9 token(s) from the captured stylesheet, under this theme's own names
  --color-ink #10243a from ink (text#1)
  --color-accent #ff6600 from color.accent-1 (accent#1)
  --color-line #d9e2ec from color.border-1 (border#1)
  --size-radius 6px from radius (radius#1)
  --text-family "Fixture Sans", system-ui, sans-serif from font.body (font.family#1)
  --color-ink-muted #5a6373: the source states no text#2, so this theme's default
    stands. Edit the Theme post's TOKENS to set it
```

What is taken depends on what the source can be said to have *stated*, and the rule differs
by the kind of value:

- **Colour** needs a page-level statement, because the same colour is text in one rule and a
  background in another: only `body { color: … }` says which is the page's. A colour the
  stylesheet repeats more often does not outrank it; it falls to the next slot, so a card
  colour used five times becomes the muted one rather than the body copy.
- **A corner radius or a font stack** is taken from the value the stylesheet uses most: there
  is only one thing a `border-radius` can mean, so there is nothing to disambiguate.
- **A type scale is refused.** The theme has five text sizes ordered by size, and a stylesheet
  ranks its font sizes by how often it uses them: those are not the same order, so only the
  step the page states outright (`body { font-size: … }`) is taken and the rest keep their
  defaults.
- **The accent is read off your buttons.** A rule that sets a background *and* sets its text
  to the page's own background colour is an element turned inside out (a button, a badge, a
  call-to-action), and that background is your accent. It is found this way because it is
  rarely stated anywhere else: an accent is almost never a `fill` or a `caret-color`, and a
  name like `--brand` is a word rather than evidence. A band painted in the page's own text
  colour, such as a dark footer, is the page inverted rather than an accent, and is not taken.

Two things the palette will **not** do, whatever the source says:

- **A colour that paints nothing is refused.** A fully transparent value is a reset rather than
  a palette entry, and a page background taken from one shows whatever happens to sit behind
  it, which looks correct on a light layout and breaks on a dark one.
- **A pair you could not read is refused.** The text colour and the page it sits on are checked
  together, and where there is no contrast between them the source's half gives way to the
  theme's default. This catches the case where nothing about the source is wrong: a source that
  states a dark page and never states a text colour would otherwise leave near-black body copy
  on it.

A refusal is printed with the reason and, for contrast, the ratio:

```
palette: 1 of 9 token(s) from the captured stylesheet, under this theme's own names, 2 refused
  --color-surface #ffffff REFUSED: the source's background#1 is #0000, which paints nothing.
    This theme's default stands; fix it in the source, or edit the Theme post's TOKENS
  --color-ink #12151a REFUSED: #f9fafb from vg.foreground reads at 1.05:1 against
    --color-surface #ffffff, under the 1.5:1 floor. …
```

Where a value is not taken the default stands and is **named**, because one that was never
looked for, one that was refused and one that was matched are otherwise indistinguishable.
Every token is a field on the `Theme` post, so changing any of them is a console edit rather
than a stylesheet rewrite.

:::note[Palettes declared inside `@layer`]
A stylesheet built by a modern toolchain usually declares its custom properties inside
`@layer theme { :root, :host { … } }`. That is read as the source declaring its palette: a
layer only orders the cascade. A palette declared under `@media (prefers-color-scheme: dark)`
or under a `.dark` class is a *variant*, has two values and no default, and is reported rather
than written: picking one of them would choose a theme nobody asked for.
:::

### 4. Publish, then look at the public route

```bash
shio push --content            # or publish through the console / an agent
```

Then judge on the **public** route:

```
/sites/mysite/default/en-us/
```

Not the preview. Two reasons, both of which have burned a run: the preview's banner shifts
every pixel, so a comparison there can never reach zero; and the preview renders **drafts**,
so an asset you forgot to publish looks fine there and 404s in public.

---

## Judging the result

A replica that "looks right" in a browser tab is not evidence. Four checks, in the order
that actually finds things:

1. **Every reference resolves.** Fetch each same-origin `src`, `<link href>` and CSS
   `url()` and expect a 200. This is the check a screenshot cannot make, and it is the one
   that finds missing webfonts, an asset written to the wrong path, and anything left
   unpublished.
2. **No link carries the source's base.** Every same-origin link must start with the base
   the replica is served from. A verbatim `href` is a hardcoded base, and it always points
   off-site.
3. **Both spellings of a directory path answer**: `/docs` and `/docs/`, one canonical and
   one redirecting, the way the source did.
4. **`shio verify --site mysite` is clean.** The bar is: a replica whose every reference
   answers 200 verifies with no findings.

Then compare the pictures:

```bash
shio snapshot /sites/mysite/default/en-us/ --against https://example.com --width 1280
```

**A number is not a result, open the PNG.** A 100% difference has meant a screenshot of an
authorization error, which is a perfect score for the wrong reason.

For the authorable mode, one extra check, and it has to be a **count** rather than a
glance:

```
/preview/mysite/?editor=true
```

should carry a `data-shio-prop` for every field each section declares, **including the
fields of every row of a collection**. Four of the seven section types are
collection-shaped (features, questions, logos, quotes), so a page that looks fully
annotated can be five props over a grid whose cards carry none. Rendering is not
annotation.

`shio annotations post:mysite/` does that count for you, and it reports the one finding that
matters: a field whose **value is on the page** with nothing making it editable. A field the
template simply does not draw is listed apart and does not fail the check, including on a
page converted with `--annotate`, which declares the vocabulary's fields and draws the
source's own markup instead.

### One verdict, against a written bar

The checks above tell you what is wrong. `shio judge` tells you whether the round is
**done**:

```bash
shio judge --site mysite
```

`verify`, `diff` and `audit` run; `snapshot` is opt-in (`--against <origin>`). A skipped
instrument reads **NOT RUN**, never green. Every finding is graded against a bar written
down in `acceptance.properties`: at or below it, the round is **ACCEPTED** and the finding
prints as a task for later; above it, or of a kind the bar does not name, it **HOLDS** and
the command exits 1. **INCONCLUSIVE** is the third answer, and it is the one that matters:
an instrument the bar rests on did not run, so there is nothing to conclude.

The mode is read from the plan rather than assumed, so a fidelity site is graded by the
fidelity bar. What the bar deliberately leaves out is as much of the point as what it
includes: looking like the source is not a test an *authorable* replica has to pass, and a
verdict that demanded it would never be reachable.

Keep the round and compare the next one against it:

```bash
shio judge --site mysite --since --record
```

`--record` appends the round to `shio/judge-rounds.json`; `--since` reports what moved
against the last one. A count that **rose** holds the round, graded by the same bar as any
other finding; a count that fell is reported and never held: a replica that got better is
not a reason to stop.

---

## Boundaries, stated up front

- **The instance does not run a browser.** Shio itself never renders JavaScript; `--render`
  drives a browser on *your* machine at capture time, and it is an optional dependency. A
  source that builds its content client-side is capturable only through that flag, and only
  as the DOM looked at capture time.
- **Fidelity mode is not editable unless you ask for it.** `convert --annotate` is how you
  ask ([Fidelity with editable blocks](#fidelity-with-editable-blocks)), and it annotates
  the pages that kept all of their markup. For a mixed page, the authorable half is the
  editable half.
- **Authorable mode arrives styled, but not as the source.** The captured classes are dropped
  and the page is redrawn with Shio's own vocabulary, so the layout, the spacing and the type
  are the theme's rather than the source's; the colours, the corner radius and the font stack
  are the source's where its stylesheet states them plainly ([the theme an authorable replica
  is presented with](#the-theme-an-authorable-replica-is-presented-with)). This is the trade you
  accepted at `propose`.
- **A captured page carries residues.** Real-world markup is strange, and each strange case
  is found by running the chain rather than by reasoning about it. That is why the repository
  ships a conformance suite (`pnpm -C cli run conformance`) that drives capture → propose →
  convert → verify over a served fixture: it is the gate a change to any of this has to pass.

---

## Related Pages

| Page | Description |
|---|---|
| [The `shio` CLI](/shio/docs/cli/) | Every flag of `clone`, `propose`, `convert` and `snapshot` |
| [Pages, Layouts & Regions](/shio/docs/website-development/) | What `convert` writes, and the `/sites/**` route |
| [The Universal Editor](/shio/docs/universal-editor/) | Editing an authorable replica |
| [Content as Files](/shio/docs/content-as-files/) | The tree a converted site projects to |
