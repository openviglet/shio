export type ShPostState = "DRAFT" | "PUBLISHED"

export interface ShPostUnified {
  id: string
  state: ShPostState
  title?: string
  summary?: string
  furl?: string
  owner?: string
  published: boolean
  publishStatus?: string
  date?: string
  modifiedDate?: string
  publicationDate?: string
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
