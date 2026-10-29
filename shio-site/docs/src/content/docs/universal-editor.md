---
title: The Universal Editor
description: "Edit the rendered page: annotate a template with sh-field, open the page in the console, click the text and save it back to the draft."
sidebar:
  label: The Universal Editor
---

The console can edit a post through the form its post type declares. That is the right
surface for a content model, and the wrong one for "this headline is too long".

The **Universal Editor** puts the editing surface on the page itself. You open the
rendered page inside the console, click the text you want to change, type, and save. What
you save goes to the **draft**, not to the live page.

It works two ways, on Shio's own rendered pages, and on **your own front end**, wherever
it is deployed, which is the case that matters once delivery is a Next.js app over the
Content Delivery API.

---

## Annotating a template

The editor cannot guess which piece of HTML is which field. A template says so, with three
attributes:

```handlebars
<article sh-post="{{id}}">
  <h1 sh-field="TITLE">{{TITLE}}</h1>
  <div sh-field="TEXT">{{{TEXT}}}</div>
</article>
```

| You write | The browser receives | Means |
|---|---|---|
| `sh-region="Header"` | `data-shio-region`, `data-shio-region-post` | this node is that Region |
| `sh-field="TITLE"` | `data-shio-prop` | this node is that field |
| `sh-post="{{id}}"` | `data-shio-post` | the field belongs to *that* post |

Two rules make this predictable:

- **The page's own post is already stamped on `<body>`**, so the common case needs no
  `sh-post` at all. You only name it when a node belongs to a *different* post: a row
  inside a `{{#query}}` listing, say.
- **Ownership is the template's job, and the engine never infers it.** Resolution is
  nearest-ancestor: the closest `sh-post` above a field wins. The rendered HTML records
  which post a value came from because the template said so, not because something guessed.

`sh-*` is the contract your content declares; `data-shio-*` is what the browser consumes.
Both survive the render, and **stripping them breaks the editor**: the rendered DOM is
the only place the mapping from node to post exists.

---

## Opening a page

In the console, go to **Universal Editor**. You get a list of sites, an entry field for an
external URL, and a token selector: the editor saves through a delivery token with write
scope, not through your console session, because the page being edited may be served from
another origin entirely.

Pick a site to edit Shio's own preview render, or paste the URL of your deployed front
end.

Behind the scenes, a preview URL with `?editor=true` gets a small bridge script injected at
`/preview/_shio/editor.js`. An **ordinary preview ships no editor code at all**.

---

## Composing a page from sections

On a page built from sections, click a section, not one of its fields, to select it. A
toolbar drawn over it moves it up or down, removes it, or duplicates it.

- **Duplicate** places an independent copy right after the original: a new section post in
  the same folder, with its own friendly URL. Editing the copy's heading leaves the original
  alone.
- **Add a section** from the panel. It offers the types the page's layout accepts in its
  `sh-accepts` declaration, each drawn as a **thumbnail**: that type rendered through this
  page's own layout and theme, with each field's label as placeholder text. The thumbnail is
  HTML in a sandboxed frame, so it runs nothing and cannot restyle the console.
- Press **Above** or **Below** to place it beside the selection, or **At end** when nothing
  is selected. Or **drag a type onto the page**: a line marks the gap between the two sections
  it will land between. Dragging needs a section selected, because the selection is what
  tells the console where each section is on screen. A drop writes exactly what the matching
  button would.

A type the layout names but this instance has not provisioned is shown, not offered. A page
that composes a type its layout does not accept still renders; `shio verify` reports it as
`section-not-accepted` (see [Proving it renders](/shio/docs/website-development/#proving-it-renders)).

### Patterns: a block that works, placed again

A **pattern** is a set of sections captured from a page into your project as
`shio/patterns/<name>.json`. To offer one in the editor, push it to the instance (an
administrator does this):

```bash
shio patterns save pricing-block --from post:Acme/pricing
shio patterns push pricing-block
shio patterns list --remote     # pushed, stale (edited since the push), or not pushed
```

The panel lists pushed patterns under **Patterns**. **Insert pattern** places all of its
sections where **Below** would place one, as drafts, and writes the same page that
`shio patterns apply` would. The file in your project stays the one you edit: the editor
holds a copy and says when it was pushed. If a pattern has a `{{placeholder}}` with no default,
the editor names it and you place it from the CLI with `--param`.

### What a save tells you

After a save, the assistant dock says what the save changed: the fields it wrote, and whether
the page's **structure** and **appearance** changed. If you only edited text and the appearance
changed anyway, the line asks you to look at the page, since that is usually a layout that
reflowed without anyone meaning it to.

---

## On your own front end

If your site is a Next.js app (or anything else) over the delivery API, add the published
bridge:

```bash
npm install @viglet/shio-editor-cors
```

Then write the annotations on the elements you want editable, and tell the page which
origin is allowed to frame it:

```html
<meta name="urn:shio:system:editor-origin" content="https://shio.example.com">
```

If you compose pages from `@viglet/shio-sections`, the components already emit the
annotations: pass the section post's id and they become editable:

```jsx
<Hero data={section.data} postId={section.id} />
```

`postId` is optional and that is deliberate: only the host knows the id, because it
fetched the section over the delivery API and React drew it. Omit it on a public page and
the markup is byte-identical, so nothing carries editing metadata where it is not wanted.
Two things are not editable in place today and are documented rather than left to be
discovered: **the rows of a collection field** (each row is its own post and the payload
carries no id for it, so the editor would have nowhere to save), and `SectionHero.align`,
which is rendered as a CSS class rather than as a value an attribute carries.

That meta tag is not optional: **the package is inert without it.** A framed page cannot
work out who framed it, `document.referrer` is empty on some navigations and reading the
parent's location throws cross-origin, so the allowed origin has to be stated by the page.

The same reasoning applies on Shio's side: the console pins the iframe's origin and ignores
messages from anywhere else, and the injected script is handed its parent's origin
explicitly. **Neither side ever posts to `"*"`**, and that is asserted by a test rather
than promised in a comment.

The script is also **inert when the page is not framed**, so shipping it to production
costs a visitor nothing.

---

## What saving does

An edit merges into the post's **`DRAFT`** row. If the post existed only as a published
page, the draft is seeded from it first, so the live page is untouched until an explicit
publish. A preview client reading with a preview-scoped token renders that draft, which is
what makes "see it before anyone else does" work with no second deployment.

Three endpoints do the work, and they are ordinary delivery-API calls:

```http
GET   /api/v2/cda/post/{id}/editable    # the post plus its field schema
PATCH /api/v2/cda/post/{id}            # merge an edit into the draft
POST  /api/v2/cda/post/{id}/publish    # publish it
```

`editable` returns the **whole field schema**, not just the fields the page happens to
annotate, so the editor's panel can offer every field the post type declares, including
the ones no template exposed.

The merge can be made conditional. `editable` answers an `ETag` for the post as it is now.
Send it back as `If-Match` on the `PATCH`, and if someone else changed the post in between, the
merge is refused with `409 Conflict` (a `stale-expectation` problem saying what changed) and nothing
is written. Every successful merge
answers the post's new `ETag`, so a second change can be pinned to the first. Without
`If-Match`, a merge is applied unconditionally, as before.

The editor has its own [assistant dock](/shio/docs/content-console/#the-assistant-dock), at the foot
of the properties panel so it never covers the page you are editing. While a save is in
flight it shows the save working. When it lands it names the fields that were saved, and a
refused save shows the server's explanation there.

Writes need a token of **write** scope, and the console uses a credential-less request for
them: the token authenticates, not your session cookie.

---

## Related Pages

| Page | Description |
|---|---|
| [Pages, Layouts & Regions](/shio/docs/website-development/) | The templates you annotate, and the preview route |
| [Letting an agent in](/shio/docs/agent-safety/) | Drafts, publishing and the review queue |
| [Content Delivery API](/shio/docs/headless/content-delivery-api/) | Token scopes, and the preview environment |
