// Universal Editor (SH10) — types mirroring the CDA write contract
// (com.viglet.shio.api.cda.dto.*). The editor talks to /api/v2/cda with a
// WRITE-scope token, so these mirror the backend DTOs, not the console model.

export type ShCdaPost = {
  id: string
  title: string
  summary: string
  furl: string
  postType: string
  siteId: string
  folderId: string
  published: boolean
  date: string
  modifiedDate: string
  publicationDate: string
  attrs: Record<string, unknown>
}

export type ShCdaField = {
  name: string
  label: string
  description: string
  widget: string
  required: boolean
  isTitle: boolean
  isSummary: boolean
  ordinal: number
}

export type ShCdaEditablePost = {
  post: ShCdaPost
  fields: ShCdaField[]
}

// A single inline edit captured from the iframe bridge (sh:content-changed).
export type ContentChange = {
  postId: string
  prop: string
  type: string
  value: string
  label: string
  selector?: string
}

// The element the bridge reports as selected (sh:element-selected).
export type SelectedElement = {
  postId: string | null
  model: string
  /**
   * The field's *name*. Until SH303 the bridge tried `data-shio-label` first, which nothing has
   * ever emitted — so this has always been the raw name, and the console now resolves the
   * declared label from the post-type instead. A label belongs to the model, which is where the
   * console shows it and `shio pull` round-trips it; a copy in the markup is a second place for
   * it to be wrong.
   */
  label: string
  type: string
  prop: string | null
  /** SH303 — the page's post-type, so the labels can be looked up in one fetch. */
  postType?: string | null
  /**
   * SH1037 — the widget the model declares for this field, verbatim, or `""` when unknown.
   *
   * The panel renders the field the model describes instead of a bare input. `""` is a real answer
   * and not a gap: a listing row's type is not knowable at compose time, and a page rendered before
   * SH1037 or an external front end writing its own `data-shio-*` carries no kind either.
   */
  widget?: string
  /**
   * SH1037 — what the page allowed inline: `plain`, `rich`, `panel`, or `attr`.
   *
   * The browser's decision, not a second copy of `widget`: it is what `contenteditable` was actually
   * set to, so the panel can say *edit this here* about a field the caret cannot express — a `Date`,
   * a `Check Box`, a `Content Select` — rather than leaving the curator clicking at it.
   */
  affordance?: string
  /**
   * Every editable field of the same post, so the panel needs no round trip.
   *
   * `type` is `"text"` for a field the page draws as content and `"attr"` for one it draws into an
   * attribute (SH503) — a link's destination, a row's icon. An `attr` field carries the attribute it
   * lives in, and is the one kind that *has* to be edited here: there is no inline affordance for a
   * value that is not in a text node, which is why `ctaHref` was captured, stored and rendered while
   * a curator could only rename the button.
   */
  fields?: Array<{ prop: string; type: string; label: string; value: string; attr?: string }>
}

/**
 * A section a curator selected — an object on the page rather than a field of one (SH1038).
 *
 * The bridge bound two prop attributes and reported `text` or `attr`, so the editor could change a
 * hero's headline and never the hero. Everything needed was already in the DOM: the generated layout
 * writes the type, the server's region walk reads it, and SH1038's composer pass adds the position.
 *
 * `index` and `of` travel together because a position is only meaningful against a length — and the
 * position *after* the last section is a real destination with no element on it, which is what makes
 * a move expressible at all.
 */
/**
 * Where something is inside the framed page, in that page's VIEWPORT coordinates (SH1038).
 *
 * Viewport and not document, because that is the space the console can map: the iframe occupies
 * exactly one box in this layout and the framed viewport *is* that box, so `top` is an offset from
 * the iframe's own top edge and the console needs to know nothing about how far the page inside has
 * been scrolled. The price is that a scroll moves it, which `sh:section-geometry` reports.
 */
export type FrameRect = { top: number; left: number; width: number; height: number }

export type SelectedSection = {
  /** The section post's own id — what its fields belong to. */
  postId: string | null
  /** The declared section type, e.g. `SectionHero`. */
  section: string
  index: number
  of: number
  /**
   * Where it is on screen, so the console can draw the overlay itself (SH1038).
   *
   * The overlay is the console's and not a stylesheet the page injects, because a stylesheet can
   * outline a section and cannot draw a toolbar over it — and the buttons have to live where the
   * writes are. The console cannot measure the framed document, which is another origin, so the
   * page sends the box.
   */
  rect?: FrameRect
  /**
   * The Page whose ordered reference array holds this section.
   *
   * What a reorder writes: moving a hero rewrites the Page's array and the hero itself does not
   * change. One deterministic write, which is why no new write path is needed — `post.upsert`
   * already replaces that field and the file projection carries it as a diffable line.
   */
  pagePostId: string | null
}

/**
 * The three verbs a section supports, each one replacement of the owning Page's array (SH1038).
 *
 * `duplicate` places the **same** section twice rather than copying it: the array holds references,
 * so a repeated entry is a second placement of one post and editing either heading edits both. That
 * is what a single deterministic write can mean here, and the panel says it out loud — a curator who
 * finds it out by editing one of them has been surprised, which is the thing P7 forbids.
 */
export type SectionOp = "move" | "remove" | "duplicate"
