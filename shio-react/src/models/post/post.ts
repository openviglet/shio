/**
 * Widget-facing attribute shape retained as a thin adapter so the existing
 * dynamic form widgets keep their legacy {attr, onChange} contract. The
 * {@link PostFormFields} wrapper maps the unified JSON data map to this
 * shape on the way in and back on the way out.
 */
export type ShPostAttr = {
  "@type"?: string
  id: string
  strValue: string
  arrayValue: string[]
  dateValue: string
  intValue: number
  shPostTypeAttr: {
    id: string
    name: string
    label: string
    description: string
    ordinal: number
    required: number
    isSummary: number
    isTitle: number
    shWidget: { name: string }
    widgetSettings?: string
    shPostTypeAttrs?: {
      id: string
      name: string
      label: string
      description: string
      ordinal: number
      required: number
      isSummary: number
      isTitle: number
      shWidget: { name: string }
      widgetSettings?: string
    }[]
  }
  shChildrenPostAttrs?: ShPostAttr[]
  shChildrenRelatorItems?: ShRelatorItem[]
}

export type ShRelatorItem = {
  "@type"?: string
  id: string
  title?: string
  summary?: string
  ordinal: number
  shChildrenPostAttrs: ShPostAttr[]
}
