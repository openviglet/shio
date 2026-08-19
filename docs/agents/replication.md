# Replication — an existing site becomes an authorable Shio site

**Read this before judging a replication, not after.** Block Q has shipped 131 tasks
and been declared closed six times. It reopened every time, and not because the work
was wrong: because *done* was never written down anywhere that survives a ship.
`docs/IMPROVEMENTS.md` holds rationale for **unshipped** work only, so SH692 — which
defined what a correct authorable page is — was deleted the moment SH692 shipped.
This file is that definition's home, and it is not governed by roadkeep.

## The chain

```
shio clone <url> --depth N --assets --markup [--render] [--scripts] --site X
shio propose [--accept all]      # accept nothing = fidelity, accept = authorable
shio convert --site X [--create-site] [--scratch]
shio publish --site X --all      # every post lands in DRAFT; publishing is explicit
shio judge --site X              # the verdict
```

Judge on the **public** route — `/sites/<site>/default/<locale>/` — never `/preview/`.
The preview draws a banner that shifts every pixel and renders drafts, so an
unpublished asset looks fine there and 404s to a visitor.

## The one rule that decides everything else

> **Design equivalence with the source is not an acceptance criterion for authorable
> mode.**

It is *fidelity's* criterion. Applying it to authorable's output is what kept Block Q
open across 131 tasks: every round found another way the replica did not look like the
site, because that class of finding has no bottom — spacing rhythm, type scale, radii,
shadows, hover states, dark mode, breakpoints. `www.viglet.org` declares 49 design
tokens; the Shio section theme reads 14. That gap is an open-ended matching problem,
not a defect list.

An authorable page is **presented by a Shio theme, on purpose** (SH510). It is held to
its *content*, its *structure* and its *routes*. Judge it as a page somebody would
keep, not as a failed copy.

## The unit is the region, not the page

Ten of twelve pages in a typical conversion are `mixed` — some blocks accepted as
sections, the rest kept as the source's own markup. A mixed page cannot be judged
whole, and it does not need to be. `shio digest <page>` prints the regions in document
order with the sheet in force over each:

```
regions (document order):
  header      1      verbatim  4 unstyled  VigR7 (captured)
  section×8   4-11   sections  0 unstyled  VigR7 (Shio)
  footer      12     sections  4 unstyled  VigR7 (captured)
```

| region kind | baseline | rule |
|---|---|---|
| **verbatim** | the source | it must look like the source did — fidelity's rule |
| **section** | its declaration × its theme | equality: what is served is exactly that composition |
| **the seam** | the declaration | declared order, one theme in force per region, no unaccounted gap |

A section region's reference needs no capture and no human approval: it is
*synthesisable*, because a section is `(its type, its field values) × (the theme in
force)`. Every appearance defect this project has found is a difference from it —
a footer above the first section is **order**, two stylesheets over one region is
**attribution**, a void between regions is a **gap**.

## Acceptance — what must be true to stop

Every line below is checkable without a person looking at the page. A round that
satisfies all of them is **done**, and a finding that is not on this list is a task for
a later block rather than a reason to hold the release.

| # | Criterion | Instrument |
|---|---|---|
| 1 | `verify` reports **0 errors** | `shio verify --site X` |
| 2 | Every captured route answers **200 anonymously**, in the slash spelling the source served (SH467) | `shio verify`, page check |
| 3 | **0 losses the mode does not claim** — the `by design` shortfalls are excluded, the rest are not | `shio diff --site X` |
| 4 | **No increase** in dangling custom properties over the recorded baseline | `page-dangling-token` (SH851) |
| 5 | **No increase** in unstyled classes over the recorded baseline | `page-unstyled` (SH686) |
| 6 | **0 post types unresolved**, and a mixed page's own title is editable | `shio annotations <page>` (SH685, SH766) |
| 7 | **No collection row whose every declared field is empty** | `shio annotations`, SH857 |
| 8 | **No overlay kept without the behaviour that dismisses it** | SH858 |
| 9 | Regions appear in declared order, **one theme in force per region** | `shio digest` regions |
| 10 | The palette report names every slot: filled, refused, or defaulted | `shio convert` (SH849) |

**Fidelity mode adds one and drops several.** It is accepted at a **0.00% pixel bar**
against the source, and it has no editable fields by definition — verbatim markup
carries no annotations, so criteria 6 and 7 do not apply to it. That is the documented
property, not a defect.

## What is deliberately out of scope

Naming these is what lets the block close.

- **Whether the design is good.** That is the curator's, and P7 says so. The
  criterion's job is to take everything that is *not* taste off their desk.
- **Colour equivalence with the source.** The palette bridge maps what the source
  declares onto the roles the theme reads and reports what it could not fill (SH849).
  A role that kept its default is a *reported* outcome, not a failure.
- **Behaviour that was not captured.** `--scripts` is how a caller asks for it, and
  `clone` names what it dropped. A replica that renders and does not respond is the
  documented cost of the flag — with one exception, criterion 8: an overlay is chrome
  whose entire purpose is behaviour, so keeping it without its script is never right.
- **Off-origin assets.** Mirroring another host's bytes is a licensing decision, not a
  fetch.

## The traps, in the order they bite

1. **Read the tool's own notes.** `clone` teaches the next flag and is usually right —
   *"almost no text with scripts present … re-run with `--render`"* turns a 1-page
   capture of a client-rendered site into a 12-page one. Following a note costs one
   flag; ignoring one costs a round.
2. **Say which mode ran.** Fidelity reaches 0.00% and looks finished, which is exactly
   why it is not evidence about the other two.
3. **Resolve references against the URL the page is served at**, not the site root.
   A relative `./../../img/x` on a page served several segments deep lands elsewhere.
4. **Count, do not eyeball** — both declaration forms, every collection row.
5. **Distrust a report that ships ahead of its behaviour.** A phase that *describes* an
   omission has not necessarily closed it. This project has met it repeatedly: a note
   promising another command's output (SH849), a writer shipping ahead of its model
   (SH763), a fix landing in the CLI and not the backend.
6. **Never run `./mvnw … clean test` in a tree a `spring-boot:run` instance is serving
   from.** `clean` deletes `target/classes` under the live JVM and the suite reports
   hundreds of failures that are not real. Use a worktree, or stop the instance.

## Cleaning up

A round leaves a site per attempt. Convert with `--scratch` and sweep with
`shio site rm --scratch`; post-types have the same flag and `shio post-type rm
--scratch`. Write what the round was for to the instance — `shio remember <key>` —
because the next session reads that and not this file's git history.
