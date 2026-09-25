import type { ShFieldConstraints } from "@/models/post-type/post-type"

export type ShPostState = "DRAFT" | "PUBLISHED"

/**
 * The post-type as a post read carries it (SH176): the SH16 authoring def, the same shape
 * `/api/v2/post-type/{name}` serves. There is no `schemaJson` — SH177 dropped the column,
 * because a denormalized copy of `fields` had one writer and no invalidation.
 */
export interface ShPostTypeRef {
  name: string
  title?: string
  namePlural?: string
  description?: string
  system?: boolean
  managed?: boolean
  fields?: ShPostTypeFieldRef[]
}

/** A field of {@link ShPostTypeRef} — structurally the backend's `ShPostTypeAttrDef`. */
export interface ShPostTypeFieldRef {
  name: string
  label?: string
  description?: string
  widget?: string
  required?: boolean
  isTitle?: boolean
  isSummary?: boolean
  ordinal: number
  widgetSettings?: string
  /** SH1010 — the constraint block, carried through to the form so a rule is shown, not discovered. */
  constraints?: ShFieldConstraints
  fields?: ShPostTypeFieldRef[]
}

/**
 * SH996 — what publishing would do, as the server says it.
 *
 * The plan is the agent's own `ShAgentChangeSet` shape, because the console and the agent ask the
 * same question about the same act; the routes come from `include=impact` on the agent read, which is
 * where they are computed once.
 */
export interface ShPublishPlan {
  dryRun: boolean
  ops: number
  changed: number
  results: { op: string; address: string; action: string; detail?: string | null }[]
}

/** Which URLs a publish makes resolve, moves, or stops — absent when it moves none. */
export interface ShPublishRoutes {
  goesLive?: string | null
  moves?: string | null
  stops?: string | null
  inbound?: number | null
}

export interface ShPostUnified {
  id: string
  state: ShPostState
  title?: string
  summary?: string
  furl?: string
  owner?: string
  modifier?: string
  publisher?: string
  published: boolean
  publishStatus?: string
  date?: string
  modifiedDate?: string
  publicationDate?: string
  publishScheduledDate?: string
  unpublishScheduledDate?: string
  deletedDate?: string
  locale?: string
  translationGroupId?: string
  /** SH1058 — true when the page this one translates has changed since; absent otherwise. */
  translationStale?: boolean
  shPostType?: ShPostTypeRef
  shFolder?: { id: string; name: string; shSite?: { id: string; name: string } }
  shSite?: { id: string; name: string }
  data: Record<string, unknown>
  /**
   * Keys `data` holds that the post-type does not declare (SH176). The form cannot render
   * them, so it says they are there rather than letting them look like nothing.
   */
  undeclaredFields?: string[]
}

export interface ShPostTypeSchemaField {
  name: string
  label: string
  description?: string
  ordinal: number
  required: boolean
  isTitle: boolean
  isSummary: boolean
  widget: string
  widgetSettings?: string
  /** SH1010 — what the model says this field accepts, so the form can say it before a save does. */
  constraints?: ShFieldConstraints
  children?: ShPostTypeSchemaField[]
}

/**
 * **The** form schema for a post-type — one entry point, on purpose (SH178).
 *
 * There used to be two exports at equal prominence: `parseSchema(schemaJson)`, with no
 * fallback, and `buildSchemaFromPostType(postType)`, with one. The create page called the
 * second and worked; the edit page called the first and rendered nothing. That single
 * difference was the whole distance between "creating a post is fine" and "editing the post I
 * just created is impossible", and it read as a content problem rather than a missing line.
 *
 * It takes whatever a page has — the post-type off `/api/v2/post-type/{name}`, or the one a
 * post read carries — and returns the best schema available.
 */
export function postFormSchema(
  postType?: { fields?: ShPostTypeFieldRef[] } | null
): ShPostTypeSchemaField[] {
  return projectFieldDefs(postType?.fields ?? [])
}

/**
 * Projects a post-type's `fields` onto the form's schema shape: ordered by `ordinal`,
 * defaults filled, nested fields (Relator/Tab) projected recursively.
 */
function projectFieldDefs(fields: ShPostTypeFieldRef[]): ShPostTypeSchemaField[] {
  return fields
    .filter((f) => f.name)
    .slice()
    .sort((a, b) => a.ordinal - b.ordinal)
    .map((f) => ({
      name: f.name,
      label: f.label ?? f.name,
      description: f.description,
      ordinal: f.ordinal,
      required: Boolean(f.required),
      isTitle: Boolean(f.isTitle),
      isSummary: Boolean(f.isSummary),
      widget: f.widget ?? "Text",
      widgetSettings: f.widgetSettings,
      constraints: f.constraints,
      children: f.fields ? projectFieldDefs(f.fields) : undefined,
    }))
}

/** SH988 — `GET /post-unified/{id}/diff`: two sides whole, and each field with whether it moved. */
export interface ShPostDiff {
  from: string
  to: string
  /** Null when that side does not exist: `published` on a page never published is a create. */
  before: Record<string, unknown> | null
  after: Record<string, unknown> | null
  fields: { name: string; changed: boolean }[]
}

/** SH987 — one captured revision, as a listing answers it (no body). */
export interface ShRevisionRow {
  version: number
  state: string
  reason?: string
  actor?: string
  agentSession?: string
  capturedAt: string
  title?: string
  changed?: string[]
}
