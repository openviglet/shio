# SH249: Sections and regions: two mechanisms, one composition path

Status: **decided and implemented** (2026.3). Supersedes the open question in
[IMPROVEMENTS.md](../IMPROVEMENTS.md) §XV.16.

## The problem

Two things in this product answer "what is on this page", and neither knew the other existed.

**`/agent/render` composed sections.** SH169 made the digest follow a `Multi Select` carrying
`references: "post"` and fold each named section's structure into the outline, so an SH95 `Page`,
a post whose entire content is an ordered list of section URLs, stopped digesting as the hash
of nothing.

**`/preview/{site}/{url}` composed regions.** SH183 follows `sh-region="NAME"` inside a layout's
HTML and fills each slot from a `Region` post.

So an agent proving structure and a person looking at the page could be reading two different
compositions of the same post, and nothing reported the disagreement. That is the perception loop
(law P9) failing in the one place it is supposed to work: the whole value of a cheap textual proof
is that it stands in for looking.

The tempting fix (point the digest at the renderer's HTML) was correct and could not be taken,
because it silently picks a winner. This document settles the prior question first.

## The decision

**Sections and regions are two mechanisms. They are not alternatives, they compose, and the
renderer has exactly one composition path.**

|  | Region | Section |
|---|---|---|
| Named by | a **layout**, via `sh-region="NAME"` | a **page**, via a `references: "post"` field |
| Named how | by title | by friendly URL, in order |
| Owner | presentation | content |
| Varies with | the post-**type** (every page of a type gets the same regions) | the **page** (each page names its own) |
| Homogeneous? | one region, one purpose | deliberately heterogeneous, hero, FAQ, CTA |
| Changes when | a template is edited | a curator reorders a page |

They are not the same idea wearing two names, and collapsing them would lose something real in
both directions. Sections cannot be regions: a page's sections differ per page and are ordered
content, while a slot is fixed by the layout. Regions cannot be sections: a header authored once
and appearing on every page of a type is not something each page should have to list.

**The composition path is the renderer's, and there is one of it.** A layout slots regions; a
region's template may iterate a page's section list with `{{#getRelation SECTIONS}}` (SH247). So a
section-composed page is rendered by the ordinary path, the page's layout slots a region whose
template iterates the field, and the renderer needs no knowledge of the section vocabulary at
all. That is what keeps this from becoming a third path: **sections are content a template reads,
not a level of the render model.**

### What follows for the digest

`/agent/render?address=` reads the **rendered page** when the post's post-type has a layout
binding, because that is what a visitor gets and it is the only artefact that reflects both
mechanisms at once. `source` reports `rendered`.

SH169's synthetic section composition **remains**, as the fallback for a post that cannot be
rendered, no layout binding, or no friendly URL to render at. `source` reports `content`, so a
caller can always tell which question was answered. Keeping it is not sentiment: a page whose
post-type is not yet bound to a layout is the normal state of a site mid-build, and the digest
answering "the hash of nothing" for it was the SH169 defect. A fallback that is labelled is
strictly better than a fallback that is guessed at.

### What this deliberately does not do

- **It does not unify the two into one field, one attribute, or one vocabulary.** Both existed in
  shipped content before this document; either migration would rewrite somebody's site.
- **It does not make the renderer aware of `references: "post"`.** A region's template asks for
  the field by name. The engine never infers ownership (SH185's rule) and this is the same rule
  one level out: the *template* declares that a field holds a composition, by iterating it.
- **It does not make `/preview` the delivery path.** Unchanged from SH183: preview-grade, `no-store`,
  `noindex`, authenticated, bannered.

## Consequences worth knowing

**The digest's hash moves for pages that now render.** That is the point of the task and it is a
one-time step, not an instability: SH101's digest covers structure only, and rendering a page is
deterministic (SH183's stable ordering, SH247's total orders, `ShStableMap`). A stored SH102
snapshot baseline or an accepted SH142 finding taken against the old composition has to be
re-accepted once.

**A page whose layout is missing is not silently a different answer.** `ShPageRenderService`
throws a teaching exception for an unbound type or an absent layout; the digest treats those as
"cannot render" and falls back with `source: content` rather than propagating a 4xx, because a
digest that failed where it used to answer would break the SH99 loop for a site mid-build.

**SH187 becomes nearly free**, which was the stated reason to settle this. The artefact to
snapshot is the rendered HTML, produced in-process with no browser and no outbound fetch, and it
is now the same artefact the digest reads, so a structural proof and a visual one cannot disagree
about which page they are describing.

## Open follow-ups

- **SH264**: the digest renders a page per call, so a whole-site proof is N renders. Bounded and
  cacheable by the SH101 digest itself, but unmeasured.
- **SH262**: the pre-edit blast radius, which the same graph already computes.
