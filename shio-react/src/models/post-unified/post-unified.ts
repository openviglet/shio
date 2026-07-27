export type ShPostState = "DRAFT" | "PUBLISHED"

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
  locale?: string
  translationGroupId?: string
  shPostType?: { id: string; name: string; title: string; schemaJson?: string }
  shFolder?: { id: string; name: string; shSite?: { id: string; name: string } }
  shSite?: { id: string; name: string }
  data: Record<string, unknown>
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
  children?: ShPostTypeSchemaField[]
}

export function parseSchema(schemaJson?: string | null): ShPostTypeSchemaField[] {
  if (!schemaJson) return []
  try {
    const parsed = JSON.parse(schemaJson)
    return Array.isArray(parsed) ? (parsed as ShPostTypeSchemaField[]) : []
  } catch {
    return []
  }
}

/**
 * The field shape of the post-type authoring model (SH16's `ShPostTypeAttrDef`) —
 * structurally `ShPostTypeField`, declared locally so this module stays free of a
 * post-type import it would only use for a projection.
 */
interface PostTypeFieldDef {
  name: string
  label?: string
  description?: string
  ordinal: number
  required?: boolean
  isTitle?: boolean
  isSummary?: boolean
  widget?: string
  widgetSettings?: string
  fields?: PostTypeFieldDef[]
}

/**
 * The form schema for a post-type: the post's own `schemaJson` when it carries one,
 * otherwise projected from the post-type's fields.
 *
 * The projection reads `fields`/`widget`/booleans — the one name-keyed post-type
 * shape SH117 left standing. It used to read `shPostTypeAttrs`/`shWidget.name`/
 * `0`|`1`, which was the id-keyed entity the retired `/api/v2/post/type` returned.
 */
export function buildSchemaFromPostType(postType: {
  schemaJson?: string
  fields?: PostTypeFieldDef[]
}): ShPostTypeSchemaField[] {
  const fromJson = parseSchema(postType.schemaJson)
  if (fromJson.length > 0) return fromJson
  return projectFieldDefs(postType.fields ?? [])
}

function projectFieldDefs(fields: PostTypeFieldDef[]): ShPostTypeSchemaField[] {
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
      children: f.fields ? projectFieldDefs(f.fields) : undefined,
    }))
}
