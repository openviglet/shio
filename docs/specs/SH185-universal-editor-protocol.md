# SH185 — The Universal Editor protocol

**Status:** shipped (2026.3) · **Depends on:** SH183 (the renderer), SH10 (the CDA write path)

The wire format between the console's visual editor and the page it frames, plus the
annotations a page must carry to be editable.

---

## Why this document exists

`visual-editor.page.tsx` has loaded a site in an iframe, posted `{type, data}` into it and
listened for replies since SH10. Its own comment said the replies came from
`shio-editor.js`. **There was no such file anywhere in this repository** — not in
`create-shio-app`, `shio-react-sdk`, `shio-sections` or `shio-client`, not one `postMessage`
among them. The console was one end of a conversation nobody was on the other side of, so the
editor could not work even against the first-party starter correctly deployed.

Writing the format down now rather than earlier is deliberate: **specifying a wire format with
nothing on the other end is how the shell got here.** SH183 gave it a first implementation to
target, and this describes what that implementation does, so a second one — a Next.js front
end that wants to be editable — has something to conform to.

---

## Part 1 — The annotations

A page is editable to the extent that it says which DOM node is which field of which post.
Four attributes, in two pairs: what a **template author** writes, and what a **browser**
reads.

| Author writes | Renderer emits | Means |
|---|---|---|
| `sh-region="NAME"` | `data-shio-region`, `data-shio-region-post` | this element is a region slot, filled from that Region post |
| `sh-post="<id>"` | `data-shio-post` | fields below here belong to that post |
| `sh-field="NAME"` | `data-shio-prop` | this element renders that field |
| — | `data-shio-post-type` | on `<body>`: the page's post-type, so the editor can fetch a field schema without a second resolve |

`sh-region` is not new. It is in `openviglet/bootstrap-site`, an export last touched in
**2018**, and SH183 keeps it through the render for exactly this reason.

### Ownership is nearest-ancestor, and the page is the default

The renderer stamps `<body>` with the page's own post, so the common case — a layout
interpolating the page's fields — needs no `sh-post` at all. A listing declares ownership per
row. Resolution walks *up* from the field, so a row's declaration wins over the page's without
either having to know about the other.

```hbs
<body>                                  <!-- data-shio-post = the page -->
  <h1 sh-field="TITLE">{{TITLE}}</h1>   <!-- belongs to the page -->

  {{#query type="Article" limit=10}}
    <li sh-post="{{id}}">               <!-- belongs to the listed post -->
      <span sh-field="title">{{title}}</span>
    </li>
  {{/query}}
</body>
```

### Why ownership is the author's job and not the engine's

This is the part worth keeping. A region is rendered against the **page's** model, so `{{TEXT}}`
inside it is the page's TEXT — but inside a `{{#query}}` block `{{title}}` is the *listed*
post's. Nothing in the rendered HTML records which interpolation came from where, and inferring
it would mean the template engine tracking provenance through every helper. **The template is
the only thing that knows, so the template says.** Every visual editor that works does this;
the ones that guess are the ones that edit the wrong thing.

### Why both forms survive the render

It would be shorter to have authors write `data-shio-prop` directly. Keeping a short authoring
form and a rendered form buys the same thing `sh-region` already buys: the `sh-*` attributes are
a **contract the content declares**, so SH193 can extract them into the reference graph and
SH192 can lint them, while the `data-*` pair is what a browser consumes. One attribute doing
both jobs could not change on either side without breaking the other.

### A field with no owner is shown, not hidden

A `sh-field` that resolves to no post is annotated and rendered **read-only with a grey
outline**, not dropped. The template is missing an `sh-post`, and a mistake the author can see
beats markup that quietly is not editable — SH169's rule.

---

## Part 2 — The messages

All messages are `{ type, data }` with a `sh:` prefix. Anything else is ignored by both sides.

### page → console

| Type | `data` | When |
|---|---|---|
| `sh:ready` | `{ postId, url }` | the bridge attached; the console shows *connected* |
| `sh:element-selected` | `SelectedElement` | a curator clicked an editable element |
| `sh:content-changed` | `ContentChange` | an editable element's content changed |

```ts
type SelectedElement = {
  postId: string | null      // null when the template forgot sh-post
  model: string              // the region name, when the field is inside one
  label: string
  type: string               // "text"
  prop: string | null
  fields?: Array<{ prop: string; type: string; label: string; value: string }>
}

type ContentChange = { postId: string; prop: string; type: string; value: string; label: string }
```

`sh:element-selected` carries **every** editable field of the same post, not only the one
clicked, so the properties panel can show the whole set without a round trip.

### console → page

| Type | `data` | When |
|---|---|---|
| `sh:mode-change` | `{ mode: "edit" \| "preview" }` | the curator toggles the switch |

Preview mode restores the page exactly: the affordances are scoped under a
`data-shio-editing` flag on `<html>`, because a curator switching to preview is asking to see
what a visitor sees.

---

## Part 3 — Origins

**Neither side uses `"*"`.** The console used to, which hands the message to whatever happens
to be in the frame — a data-leak shape — while the site's own origin was knowable the whole
time.

- **console → page**: posts to the iframe's origin, derived from its `src`.
- **page → console**: posts to the origin **passed in on the script tag**
  (`data-shio-editor-origin`), and ignores any inbound message from another origin.

The parent origin is passed in rather than guessed because `document.referrer` is empty on some
navigations and `window.parent.location` throws cross-origin. The server that rendered the page
is the only party that reliably knows who asked for it.

The script is also **inert when not framed**: no parent origin, or `window.parent === window`,
and it returns immediately. A page that starts making elements `contenteditable` outside the
editor is a page that corrupts content.

---

## Part 4 — How a page gets the bridge

`GET /preview/{site}/{url}?editor=true`.

Opt-in **per request**, so an ordinary preview ships no editor code at all. The script is
served at `/preview/_shio/editor.js` by the same controller that injects it — one place decides
both that it exists and when a page gets it — and it is `no-store` like the pages that
reference it, since caching an editor script against a deliberately uncacheable page buys a
version skew and nothing else.

The injection happens **after** the render, in `ShEditorBridge`, not inside
`ShPageRenderService`. That keeps the renderer returning exactly what a front end would serve,
which is what lets SH101's digest and SH102's snapshot compare the page rather than the page
plus an editor — the same separation `ShPreviewBanner` keeps.

A front end that has never heard of `?editor=true` ignores it and the editor degrades to a
plain iframe, which is the intended failure mode: **the console must not break on a page that
never heard of it.**

---

## Appendix — every `data-shio-*` attribute (SH300)

Part 1 lists the four the editor reads. The rendered DOM has since grown five more — SH183's slot
markers, SH281's unpublished state, SH297's theme — and they are read by other things: the SH299
digest counts three of them, and anyone debugging a preview reads all of them in source. **The
rendered DOM is a contract**, and until this table the only way to learn it was `git log`.

The rule this table follows is SH273's: it is **asserted**, not maintained by hope.
`ShRenderedDomContractTest` fails the build when the code emits an attribute with no row, or a row
names one nothing emits — because the failure direction here is silent under-documentation, which no
functional test can see.

| Attribute | Where | Emitted by | Means |
|---|---|---|---|
| `data-shio-region` | the slot element | `ShRegionComposer` | this element is a region slot; the value is the region's title, exactly as `sh-region` wrote it |
| `data-shio-region-post` | the slot element | `ShRegionComposer` | the id of the Region post that filled it |
| `data-shio-region-missing` | the slot element | `ShRegionComposer` | no Region post has that title (SH183) |
| `data-shio-region-unpublished` | the slot element | `ShRegionComposer` | a Region post has that title and is not published; only a PUBLISHED render can produce it (SH281) |
| `data-shio-theme` | `<body>` | `ShRegionComposer` | the theme title this page's layout names, when it does not resolve |
| `data-shio-theme-missing` | `<body>` | `ShRegionComposer` | that theme does not exist in this site, in either state (SH297) |
| `data-shio-post` | `<body>`, or any element | `ShRegionComposer` | fields below here belong to this post; `<body>` carries the page's own |
| `data-shio-post-type` | `<body>` | `ShRegionComposer` | the page's post-type name; the bridge sends it with each selection so the console can label fields from the model (SH303) |
| `data-shio-prop` | any element | `ShRegionComposer` | this element renders that field of the nearest `data-shio-post` |
| `data-shio-preview` | the banner element | `ShPreviewBanner` | this node is Shio's own preview banner and not the page |
| `data-shio-editor-origin` | the injected `<script>` | `ShEditorBridge` | the console origin the bridge may talk to (never `"*"`) |

**Three are defect markers** — `region-missing`, `region-unpublished`, `theme-missing` — and they
are listed once in code, as `ShRegionComposer.DEFECT_MARKERS`, which is what the SH299 digest counts.
A fourth kind of defect gets a fourth attribute rather than a value on an existing one: SH169's rule
that different mistakes must not look alike, and the reason `region-unpublished` is not
`region-missing="unpublished"`.

**Three more are set by the bridge script in the browser, not by the renderer.** They exist only
while `?editor=true` is on, and a front end styling edit mode reads them:

| Attribute | Where | Means |
|---|---|---|
| `data-shio-editing` | `<html>` | edit mode is on; the bridge's own stylesheet keys every outline off this, so turning it off restores the page exactly |
| `data-shio-editable` | a field element | whether this field resolved to a post the editor can write; `false` is drawn differently rather than hidden (SH185's "a field with no owner is shown, not hidden") |
| `data-shio-editor` | the injected `<style>` | marks the bridge's own stylesheet, so it is never mistaken for the page's |

**There is no read-and-never-written name any more.** Writing this table found one —
`data-shio-label`, which the bridge asked for and nothing had ever emitted, so every field in the
editor was labelled with its raw name. **SH303** removed the read rather than adding the write: a
field's label is declared once, by the post-type, and a copy in the markup is a second place for it
to be wrong. The bridge sends `data-shio-post-type`'s value with each selection instead, and the
console resolves the labels from the schema — which is what that attribute was put on `<body>` for
and what nothing had used. If a name ever ends up in that state again it belongs in a row here, not
left out: from the reading side it looks like a working feature.

**SH304 settled the second script.** `resources/universal-editor/shio-editor.js` was a copy of the
published `@viglet/shio-editor-cors` sitting on the server's static surface, and it is gone along with
the `/universal-editor/**` permitted path. What remains is one bridge per *producer*: this one for the
markup the renderer emits (`data-shio-*`), and the npm package for a front end that writes its own
annotations by hand (`data-sh-*`) — deliberately out of this table, because it is not what the renderer
emits. `ShEditorBridgeTest` now asserts that exactly one served script implements this protocol and
that neither half posts to `"*"`. **SH350** brought the published package under the same rule: it posts to the origin named by
`<meta name="urn:shio:system:editor-origin">`, checks `event.origin` before reading a message, and is
inert until the page declares that origin — a front end that includes the bridge has to add the tag,
which is the external counterpart of the `data-shio-editor-origin` the server injects.

---

## What this does not settle

- **Saving is still SH10's CDA path** (`PATCH /api/v2/cda/post/{id}` with a WRITE token). The
  bridge reports changes; it does not write. That split is deliberate — the page has no
  credential and should not.
- **Rich text is `innerHTML` of a `contenteditable`.** Adequate for a heading or a paragraph
  and not a rich-text editor; a field whose widget is `HTML Editor` deserves better, and that
  is a separate task.
- **Only `text` is a field type.** `SelectedElement.type` exists so images, links and
  references can join later without a protocol change.
- **An external front end has to emit the annotations itself.** Nothing generates them for a
  Next.js project yet; `@viglet/shio-react-sdk` is where that would go, and it is the reason
  the annotations are specified here rather than described as "what the renderer happens to
  do".
