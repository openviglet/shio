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

interface LegacyPostTypeAttr {
  name: string
  label: string
  description?: string
  ordinal: number
  required: number
  isTitle: number
  isSummary: number
  shWidget: { name: string }
  widgetSettings?: string
  shPostTypeAttrs?: LegacyPostTypeAttr[]
}

export function buildSchemaFromPostType(postType: {
  schemaJson?: string
  shPostTypeAttrs?: LegacyPostTypeAttr[]
}): ShPostTypeSchemaField[] {
  const fromJson = parseSchema(postType.schemaJson)
  if (fromJson.length > 0) return fromJson
  return projectLegacyAttrs(postType.shPostTypeAttrs ?? [])
}

function projectLegacyAttrs(attrs: LegacyPostTypeAttr[]): ShPostTypeSchemaField[] {
  return attrs
    .filter((a) => a.name)
    .sort((a, b) => a.ordinal - b.ordinal)
    .map((a) => ({
      name: a.name,
      label: a.label,
      description: a.description,
      ordinal: a.ordinal,
      required: a.required === 1,
      isTitle: a.isTitle === 1,
      isSummary: a.isSummary === 1,
      widget: a.shWidget?.name ?? "Text",
      widgetSettings: a.widgetSettings,
      children: a.shPostTypeAttrs ? projectLegacyAttrs(a.shPostTypeAttrs) : undefined,
    }))
}
