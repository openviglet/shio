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
