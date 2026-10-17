# SH185: The Universal Editor protocol

**Status:** shipped (2026.3) · **Depends on:** SH183 (the renderer), SH10 (the CDA write path)

The wire format between the console's visual editor and the page it frames, plus the
annotations a page must carry to be editable.

---

## Why this document exists

`visual-editor.page.tsx` has loaded a site in an iframe, posted `{type, data}` into it and
listened for replies since SH10. Its own comment said the replies came from
`shio-editor.js`. **There was no such file anywhere in this repository**, not in
`create-shio-app`, `shio-react-sdk`, `shio-sections` or `shio-client`, not one `postMessage`
among them. The console was one end of a conversation nobody was on the other side of, so the
editor could not work even against the first-party starter correctly deployed.

Writing the format down now rather than earlier is deliberate: **specifying a wire format with
nothing on the other end is how the shell got here.** SH183 gave it a first implementation to
target, and this describes what that implementation does, so a second one, a Next.js front
end that wants to be editable, has something to conform to.

---

## Part 1: The annotations

A page is editable to the extent that it says which DOM node is which field of which post.
Four attributes, in two pairs: what a **template author** writes, and what a **browser**
reads.

| Author writes | Renderer emits | Means |
|---|---|---|
| `sh-region="NAME"` | `data-shio-region`, `data-shio-region-post` | this element is a region slot, filled from that Region post |
| `sh-post="<id>"` | `data-shio-post` | fields below here belong to that post |
| `sh-field="NAME"` | `data-shio-prop` | this element renders that field |
| `sh-field="COLL.N.LEAF"` | `data-shio-prop` | this element renders the `LEAF` of row `N` of the collection field `COLL` (SH475) |
| `sh-field-attr="FIELD:ATTRIBUTE"` | `data-shio-prop-attr` | this element renders `FIELD` into its own `ATTRIBUTE`; space-separated for more than one pair (SH503) |
| n/a | `data-shio-post-type` | on `<body>`: the page's post-type, so the editor can fetch a field schema without a second resolve |

### A row of a nested collection is a path, because it is not a post (SH475)

A `Relator` field with **nested** fields holds an inline array of row maps in its own post's
`data`: there is no second post, so there is no id to put in an `sh-post`, and a flat
`sh-field="title"` on a row would rename the post that owns the collection. The address is
therefore a three-segment path: the collection's field name, the row's index, the row field.

The renderer is unchanged, it never parsed a prop and still does not. What understands the path
is the **write**: `ShPostDraftMergeService` resolves `COLL.N.LEAF` into the existing array, and
**never creates**. The collection must already be an array on the post and `N` must already name a
row of it; anything else is written as the flat key it looks like, so a mistyped prop surfaces as
an SH168 `unknown-field` instead of inventing a shape the post-type does not declare.

What the path deliberately does not express is **adding, removing or reordering** a row: it
addresses a value, not a list. Those stay with the console's form.

`sh-region` is not new. It is in `openviglet/bootstrap-site`, an export last touched in
**2018**, and SH183 keeps it through the render for exactly this reason.

### One element may declare more than one field, because a link is two (SH503)

`sh-field` says what an element's **content** is, so one element declares one field, and a link is
two of them, its text and its target:

```hbs
<a sh-field="ctaLabel" sh-field-attr="ctaHref:href" href="{{ctaHref}}">{{ctaLabel}}</a>
```

Without the second declaration `ctaHref` was captured, stored on the section and rendered into the
page, and **only the declaration was absent**, so a curator could rename the button and not repoint
it, while every other instrument said the field was fine. `image` never had the problem because its
element is the `<img>` and the field it declares *is* the attribute (`sh-field="image"` on `src`), so
the one-to-one holds there by accident rather than by design.

The rejected alternative was **wrapping** the anchor so the text and the href each own an element. A
link is one thing to a curator, and splitting it into two boxes to satisfy the markup is the tail
wagging the dog, and it would put the protocol's limit into every template that has a link in it.

Three things follow from the shape:

- **The renderer still parses nothing.** The pairs are copied verbatim, exactly as a prop is; the
  browser bridge splits them, which is the side that has to read the value off the element anyway.
- **The field half takes any address `sh-field` takes**, the SH475 indexed path included, a feature
  row's `icon` is `features.N.icon:data-shio-icon`, on an element with no text node at all.
- **An `attr` field is edited in the properties panel, never in place**, and the console posts
  `sh:field-set` back so the page shows the new value rather than the old one until a refresh.

What deliberately does **not** get a declaration is a field drawn into an attribute from a *closed
set of choices*, `align`, `variant`, `columns`. The bridge's edit surface is a text box, and one in
which three strings work and the rest silently do nothing is worse than sending the curator to the
console form. `ShConvertedLayoutRenderTest` states that as the widget predicate rather than a list of
names, so it cannot rot into a suppression.

### Ownership is nearest-ancestor, and the page is the default

The renderer stamps `<body>` with the page's own post, so the common case, a layout
interpolating the page's fields, needs no `sh-post` at all. A listing declares ownership per
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
inside it is the page's TEXT, but inside a `{{#query}}` block `{{title}}` is the *listed*
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
beats markup that quietly is not editable, SH169's rule.

---

## Part 2: The messages

All messages are `{ type, data }` with a `sh:` prefix. Anything else is ignored by both sides.

### page → console

| Type | `data` | When |
|---|---|---|
| `sh:ready` | `ReadyMessage` | the bridge attached, **on every load**, so `url` is how the console learns the frame navigated (SH1036) |
| `sh:element-selected` | `SelectedElement` | a curator clicked an editable element |
| `sh:content-changed` | `ContentChange` | an editable element's content changed |
| `sh:navigating` | `{ to, from }` | a click in the page is leaving for another page of this site (SH1036) |
| `sh:component-selected` | `SelectedSection` | a curator clicked a **section** rather than one of its fields (SH1038) |
| `sh:section-geometry` | `{ rect: FrameRect \| null, sections?: FrameRect[] }` | the selected section's box moved (a scroll, a resize, a reorder), or `null`, there is no longer one (SH1038); `sections` is every box of its container, for the drop caret (SH1105) |

`sh:ready` and `sh:navigating` are arrival and departure, and both are needed. Arrival re-keys the
session: a different `url` means the selection, the pending changes and the loaded field labels all
belong to a page that is no longer on screen. Departure closes the window in between, on a slow
page that is where a curator would press Save and write the previous page's fields.

The console also watches the iframe's own `load`, which fires for a navigation whether or not the
page carries a bridge. Because `sh:ready` is sent at `DOMContentLoaded`, it always precedes that
load, so a navigation with no new `sh:ready` is a page with **no bridge** and the badge says so. It
used to key on the iframe `src`, which in-frame navigation cannot change, and therefore read
*connected* over a page with no editor on it.

```ts
type ReadyMessage = {
  protocol: number           // 2 since SH1037; absent or 1 means a v1 bridge
  capabilities: string[]     // select | edit-inline | field-set | field-kinds | navigate
  postId: string | null
  postType: string | null    // SH1037 - so the panel need not wait for a selection to fetch a schema
  url: string
}
```

**The negotiation is one-sided, and silence means everything.** The page volunteers; the console asks
nothing. A round trip would have to survive the page navigating under it (SH1036), and there is
nothing the console could usefully refuse, hiding an affordance is the only action available to it
either way. A bridge that sends no `capabilities` array at all (protocol 1, or
`@viglet/shio-editor-cors`) is read as supporting **all** of them: two producers implement this
protocol and the published one implements less, so treating silence as "supports nothing" would take
away editing that works today. The same reasoning as a missing `data-shio-prop-type` meaning *rich*.

`navigate` is claimed only when the server shipped a URL index (`data-shio-preview-paths`), because
without one the click interception is off (SH336), claiming it would be a lie the console acts on.

```ts
type FrameRect = { top: number; left: number; width: number; height: number }

type SelectedSection = {
  postId: string | null      // the section post's own id - what its fields belong to
  section: string            // the declared type, e.g. SectionHero
  index: number              // position within its container
  of: number                 // how many that container holds
  pagePostId: string | null  // the Page whose ordered array a reorder rewrites
  rect: FrameRect            // SH1038 - where it is, so the console can draw the overlay itself
  accepts: string[]          // SH1039 - the types this container says fit; [] is "none is known"
}
```

A click on a field selects the field; a click on the space around it selects the section. The section
handler runs after the per-field one, which stops propagation, and after the link handler, so a
navigation is never turned into a selection. `sections` is claimed as a capability only when the page
actually has one, and it covers both halves: the selection and the optimistic ops below.

**The overlay is the console's, not the page's (SH1038).** Every other affordance is an injected
stylesheet scoped under `data-shio-editing`, which is right for a field, an outline and a caret are
things CSS can express. A section needs a **toolbar**, and a stylesheet cannot draw one; nor could
the page own the buttons if it could, because the writes are the console's and the page has no
session. So the page sends a box and the console draws over it. The box is in the framed
**viewport's** coordinates, which is the space the console can map: the iframe occupies exactly one
box in the console's layout and the framed viewport *is* that box, so `rect.top` is an offset from
the iframe's own top edge and neither side has to know how far the page inside has been scrolled.
The price of that choice is that a scroll moves it, which is what `sh:section-geometry` reports.

```ts
type SelectedElement = {
  postId: string | null      // null when the template forgot sh-post
  widget: string             // SH1037 - the declared widget, "" when the kind is not knowable
  affordance: string         // SH1037 - what the page allowed inline: plain | rich | panel | attr
  model: string              // the region name, when the field is inside one
  label: string
  type: string               // "text", or "attr" for a field drawn into an attribute (SH503)
  prop: string | null
  fields?: Array<{ prop: string; type: string; label: string; value: string; attr?: string }>
}

type ContentChange = {
  postId: string; prop: string; type: string; value: string; label: string
  was: string   // SH1041 - what the field held before this round of typing
}
```

**`was` is on every message and only the page can supply it (SH1041).** `sh:content-changed` fires
*after* the change, so the console sees the new value and never the old one, which is why its
pending set could be cleared all at once and never one entry at a time. The bridge takes the
baseline when a field gains focus, so the first edit of a session reports what the page was serving;
every later message for that field carries the same value, and the console keeps the **first**,
which is what makes an undo mean *before I started on this field* rather than *before the last
keystroke*. A bridge that sends none leaves the entry with nothing to revert to, and the console
offers no control rather than writing `undefined` into the page.

`sh:element-selected` carries **every** editable field of the same post, not only the one
clicked, so the properties panel can show the whole set without a round trip.

### console → page

| Type | `data` | When |
|---|---|---|
| `sh:mode-change` | `{ mode: "edit" \| "preview" }` | the curator toggles the switch |
| `sh:field-set` | `{ postId, prop, value }` | the console decided a field's value: the panel edited an `attr` one (SH503), or a text one is being reverted or replayed into a reloaded frame (SH1041) |
| `sh:section-op` | `{ op: "move" \| "remove" \| "duplicate", index, to? }` | reorder the **selected** section, optimistically, while the console writes the Page's array (SH1038) |

`sh:field-set` reaches **both kinds** of field since SH1041. It was the panel's write-back for an
`attr` field, and a text field (the kind a curator edits most) could not be written at all, so the
console could clear a pending edit and not undo it. A `plain` field is written as `textContent` and
a `rich` one as `innerHTML`, which is the same decision SH1037 made about what each can hold; the
page's baseline moves with the write, so a second edit reports the value the curator just went back
to rather than one they had already undone.

Preview mode restores the page exactly: the affordances are scoped under a
`data-shio-editing` flag on `<html>`, because a curator switching to preview is asking to see
what a visitor sees.

`sh:section-op` acts on **what the curator selected** and on nothing the sender names. With two
composed lists on one page there are two sections at index 0, so `{op, index}` identifies two
elements and a move would rewrite whichever the selector reached first; the `index` in the message is
the console's statement of which entry of the array it is writing, not a way to address an element.
It is the one inbound message that changes the *shape* of the page rather than a value in it, which
is what makes the origin check on that listener load-bearing.

Applied **optimistically**. The write is a draft merge on the console's session and the frame is
showing a render of the published row, so nothing about the write makes that document change, a
curator would press Move and watch the page sit still. The two sides can only disagree when the write
is refused, and the console reloads the frame in that case rather than leaving a page that shows an
order the content does not have. After an op the page re-derives every position (`getRelation` renders
the references in the order the field names them, so document order *is* the array's order) and
echoes `sh:component-selected` with the new one, or `sh:section-geometry: { rect: null }` for a
removal.

**Duplicate places the same section twice.** The array holds references, so a repeated entry is a
second placement of one post and not a copy of it: editing either heading edits both. That is what a
single replacement of the array can mean, and the console says it in the toolbar's own title rather
than letting a curator find out by editing one of them. A copy would be a different object and a
different write.

**Which field.** `{{#getRelation sections}}` takes the field's *value*, not its name, so the name is
gone by the time the markup exists and nothing in the rendered DOM can carry it. The console
therefore writes the one field the generated layout uses (`sections`) and **checks** rather than
assumes: it reads the Page and offers the controls only when that field holds an array as long as the
list on screen. A page composing its sections from some other field gets the header and the position
with no buttons, which is the same rule the rest of this protocol follows, a control that does
nothing is worse than none.

---

## Part 3: Origins

**Neither side uses `"*"`.** The console used to, which hands the message to whatever happens
to be in the frame (a data-leak shape) while the site's own origin was knowable the whole
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

## Part 4: How a page gets the bridge

`GET /preview/{site}/{url}?editor=true`.

Opt-in **per request**, so an ordinary preview ships no editor code at all. The script is
served at `/preview/_shio/editor.js` by the same controller that injects it, one place decides
both that it exists and when a page gets it, and it is `no-store` like the pages that
reference it, since caching an editor script against a deliberately uncacheable page buys a
version skew and nothing else.

The injection happens **after** the render, in `ShEditorBridge`, not inside
`ShPageRenderService`. That keeps the renderer returning exactly what a front end would serve,
which is what lets SH101's digest and SH102's snapshot compare the page rather than the page
plus an editor, the same separation `ShPreviewBanner` keeps.

A front end that has never heard of `?editor=true` ignores it and the editor degrades to a
plain iframe, which is the intended failure mode: **the console must not break on a page that
never heard of it.**

---

## Appendix: every `data-shio-*` attribute (SH300)

Part 1 lists the four the editor reads. The rendered DOM has since grown five more, SH183's slot
markers, SH281's unpublished state, SH297's theme, and they are read by other things: the SH299
digest counts three of them, and anyone debugging a preview reads all of them in source. **The
rendered DOM is a contract**, and until this table the only way to learn it was `git log`.

The rule this table follows is SH273's: it is **asserted**, not maintained by hope.
`ShRenderedDomContractTest` fails the build when the code emits an attribute with no row, or a row
names one nothing emits, because the failure direction here is silent under-documentation, which no
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
| `data-shio-prop-attr` | any element | `ShRegionComposer` | `FIELD:ATTRIBUTE` pairs, space-separated: fields this element renders into its **own attributes** rather than as content, so a link's destination can be edited and not only its text (SH503). Copied verbatim, the renderer parses a pair no more than it parses a prop |
| `data-shio-section` | the element that IS one section | the conversion's generated layout (SH556) | the declared section type, e.g. `SectionHero`. **Read** by `ShHtmlRegions` to tell authored content from markup a conversion kept, and by the bridge as the handle a section is selected by (SH1038); the composer never writes it, because only the template knows which element is the section |
| `data-shio-section-index` | the same element | `ShRegionComposer` | its position among the sections of its own container (SH1038), derived from document order, which *is* the order the owning Page's reference field names them in, since `{{#getRelation}}` renders that field in order. Per container, not per page: a layout may compose two ordered lists |
| `data-shio-sections` | the element composing an ordered list of sections | `ShRegionComposer` | how many it holds (SH1038). A position is only meaningful against a length, and the position *after* the last section is a real destination with no element on it. `0` where the container declares `sh-accepts` and holds nothing (SH1039), the empty container is the case insertion exists for, and the section pass alone could only find a container by the sections in it |
| `data-shio-accepts` | the same element | `ShRegionComposer` | the post-type names the **template** says fit here, space-separated, from its `sh-accepts` (SH1039). Only the template knows what a container accepts, which is why this is declared and not derived. An **absent** attribute means *nothing is known to fit* and carries the declaration to add, never *anything*, since reading it that way would need a server-side list of which post-types are sections, and `ShVocabularyPostTypes` bans a third copy of the vocabulary |
| `data-shio-prop-type` | any element with a prop | `ShRegionComposer` | the **widget the model declares** for that field, verbatim (SH1037), so the browser knows a `Text` from an `HTML Editor` and stops offering rich editing over a field that cannot hold it. The page's own type only: a listing row declares its post and not its type, so the attribute is **absent** there rather than guessed |
| `data-shio-affordance` | any annotated element | `shio-editor.js` | what the browser decided the kind allows: `plain`, `rich`, `panel`, or `attr` for an attribute-only field (SH1037). Set beside `data-shio-editable`, which says *whether*, this says *how* |
| `data-shio-command` | a button of `#shio-editor-toolbar` | `shio-editor.js` | which formatting command that button runs (SH1037): `bold`, `italic`, `createLink`, `removeFormat` and no others, because a toolbar offering more would offer writes SH956's allow-list then strips |
| `data-shio-preview` | the banner element | `ShPreviewBanner` | this node is Shio's own preview banner and not the page |
| `data-shio-editor-origin` | the injected `<script>` | `ShEditorBridge` | the console origin the bridge may talk to (never `"*"`) |
| `data-shio-preview-paths` | the injected `<script>` | `ShEditorBridge` | this site's own URLs, newline-separated, so a click on a **JS-built** link can be sent into the preview's address space while `/file_source/…` is left alone (SH336). Absent above `ShPreviewLinks.BRIDGE_URL_CAP`, which disables interception rather than shipping an index the page should not carry |

**Three are defect markers**: `region-missing`, `region-unpublished`, `theme-missing`, and they
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

**There is no read-and-never-written name any more.** Writing this table found one,
`data-shio-label`, which the bridge asked for and nothing had ever emitted, so every field in the
editor was labelled with its raw name. **SH303** removed the read rather than adding the write: a
field's label is declared once, by the post-type, and a copy in the markup is a second place for it
to be wrong. The bridge sends `data-shio-post-type`'s value with each selection instead, and the
console resolves the labels from the schema, which is what that attribute was put on `<body>` for
and what nothing had used. If a name ever ends up in that state again it belongs in a row here, not
left out: from the reading side it looks like a working feature.

**SH304 settled the second script.** `resources/universal-editor/shio-editor.js` was a copy of the
published `@viglet/shio-editor-cors` sitting on the server's static surface, and it is gone along with
the `/universal-editor/**` permitted path. What remains is one bridge per *producer*: this one for the
markup the renderer emits (`data-shio-*`), and the npm package for a front end that writes its own
annotations by hand (`data-sh-*`), deliberately out of this table, because it is not what the renderer
emits. `ShEditorBridgeTest` now asserts that exactly one served script implements this protocol and
that neither half posts to `"*"`. **SH350** brought the published package under the same rule: it posts to the origin named by
`<meta name="urn:shio:system:editor-origin">`, checks `event.origin` before reading a message, and is
inert until the page declares that origin, a front end that includes the bridge has to add the tag,
which is the external counterpart of the `data-shio-editor-origin` the server injects.

---

## What this does not settle

- **Saving is still SH10's CDA path** (`PATCH /api/v2/cda/post/{id}` with a WRITE token). The
  bridge reports changes; it does not write. That split is deliberate, the page has no
  credential and should not.
- **Rich text is `innerHTML` of a `contenteditable`.** Adequate for a heading or a paragraph
  and not a rich-text editor; a field whose widget is `HTML Editor` deserves better, and that
  is a separate task.
- **`text` and `attr` are the field types.** SH503 added the second, which is what
  `SelectedElement.type` existed for; images and references can still join without a protocol
  change. What an `attr` field gets is a text box in the properties panel, so a value from a closed
  set of choices stays the console form's job.
- **An external front end has to emit the annotations itself.** Nothing generates them for a
  Next.js project yet; `@viglet/shio-react-sdk` is where that would go, and it is the reason
  the annotations are specified here rather than described as "what the renderer happens to
  do".
