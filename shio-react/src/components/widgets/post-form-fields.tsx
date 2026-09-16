import { Badge } from "@/components/ui/badge"
import { BadgeColorful } from "@/components/badge-colorful"
import { Label } from "@/components/ui/label"
import { constraintHint } from "@/components/field-constraints"
import { useTranslation } from "react-i18next"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { getWidget } from "@/components/widgets"
import { getWidgetAccentColor } from "@/lib/widget-colors"
import type { ShPostAttr, ShRelatorItem } from "@/models/post/post"
import type { ShPostTypeSchemaField } from "@/models/post-unified/post-unified"
import { useMemo } from "react"

interface Props {
  schema: ShPostTypeSchemaField[]
  data: Record<string, unknown>
  onDataChange: (data: Record<string, unknown>) => void
  postId?: string
  postTypeName?: string
  /**
   * SH955 — the field the server's last refusal was about, from the problem document's `at`.
   *
   * A conflict over a friendly URL used to float above a page with sixteen inputs, saying which
   * URL was taken and not which box to type in. Marking the field is the difference between
   * reading the message and acting on it.
   */
  problem?: { field: string; message: string }
}

interface TabGroup {
  label: string
  fields: ShPostTypeSchemaField[]
}

export function PostFormFields({ schema, data, onDataChange, postId, postTypeName, problem }: Props) {
  const { t } = useTranslation()
  /** SH1010 — one sentence per field, built from what the model declares about it. */
  const hint = (field: ShPostTypeSchemaField) => constraintHint(field, t)
  const sorted = useMemo(
    () => [...schema].sort((a, b) => a.ordinal - b.ordinal),
    [schema]
  )

  const tabs = useMemo(() => {
    const groups: TabGroup[] = []
    let current: TabGroup | null = null
    for (const field of sorted) {
      if (field.widget === "Tab") {
        current = { label: field.label || field.name, fields: [] }
        groups.push(current)
      } else {
        if (!current) {
          current = { label: postTypeName || "General", fields: [] }
          groups.push(current)
        }
        current.fields.push(field)
      }
    }
    return groups
  }, [sorted, postTypeName])

  const renderField = (field: ShPostTypeSchemaField) => {
    const isHidden = field.widget === "Hidden"
    const Widget = getWidget(field.widget)
    const attr = buildAttrFromValue(field, data[field.name])

    const handleChange = (updated: ShPostAttr) => {
      onDataChange({ ...data, [field.name]: extractValueFromAttr(field, updated) })
    }

    if (isHidden) {
      return <Widget key={field.name} attr={attr} onChange={handleChange} />
    }

    const accent = getWidgetAccentColor(field.widget)
    // SH955 — the server said this refusal was about this field, so it is marked here rather than
    // left to a toast the curator has already dismissed by the time they look for the input.
    const refused = problem?.field === field.name ? problem.message : undefined

    return (
      <div
        key={field.name}
        className={`rounded-lg border border-l-4 ${refused ? "border-destructive" : accent} bg-background p-4 space-y-3 transition-shadow hover:shadow-sm`}
      >
        <div className="flex items-center gap-2 flex-wrap">
          <Label className="text-sm font-semibold flex items-center gap-1.5">
            {field.label || field.name}
            {field.required && <span className="text-destructive">*</span>}
          </Label>
          <div className="flex items-center gap-1.5 ml-auto">
            {field.isTitle && <BadgeColorful text="Title" className="text-[10px] px-1.5 py-0 h-5" />}
            {field.isSummary && <BadgeColorful text="Summary" className="text-[10px] px-1.5 py-0 h-5" />}
            <BadgeColorful text={field.widget} className="text-[10px] px-1.5 py-0 h-5 font-medium" />
          </div>
        </div>

        <Widget attr={attr} onChange={handleChange} postId={postId} />

        {/*
          `role="alert"` rather than `aria-invalid` on the wrapper: this div is not a form control,
          so marking it invalid would be a lie to a screen reader about what kind of thing it is.
          An alert is announced when it appears, which is the behaviour wanted — the message shows
          up after a failed save, not while the curator is typing.
        */}
        {refused && <p role="alert" className="text-xs text-destructive font-medium">{refused}</p>}
        {field.description && <p className="text-xs text-muted-foreground">{field.description}</p>}
        {/*
          SH1010 — what the model will accept, said before the save rather than by it. The whole
          point of the vocabulary reaching this form is that a rule stops being a discovery: a
          curator who can read "lower-case letters, digits and hyphens only" under the box does not
          meet it as a refusal.
        */}
        {hint(field) && <p className="text-xs text-muted-foreground">{hint(field)}</p>}
      </div>
    )
  }

  if (tabs.length <= 1) {
    return <div className="space-y-4">{tabs[0]?.fields.map(renderField)}</div>
  }

  return (
    <Tabs defaultValue={tabs[0]?.label} className="w-full">
      <TabsList className="mb-2">
        {tabs.map((tab) => (
          <TabsTrigger key={tab.label} value={tab.label}>
            {tab.label}
            <Badge variant="secondary" className="ml-1.5 text-[10px] px-1.5 py-0 h-4">
              {tab.fields.length}
            </Badge>
          </TabsTrigger>
        ))}
      </TabsList>
      {tabs.map((tab) => (
        <TabsContent key={tab.label} value={tab.label} className="space-y-4 pt-1">
          {tab.fields.map(renderField)}
        </TabsContent>
      ))}
    </Tabs>
  )
}

function buildAttrFromValue(field: ShPostTypeSchemaField, value: unknown): ShPostAttr {
  const shPostTypeAttr = schemaFieldToAttrMetadata(field)
  const attr: ShPostAttr = {
    id: field.name,
    strValue: "",
    arrayValue: [],
    dateValue: "",
    intValue: 0,
    shPostTypeAttr,
  }

  if (value == null) return attr

  if (Array.isArray(value)) {
    if (value.length > 0 && typeof value[0] === "object") {
      attr.shChildrenRelatorItems = (value as Array<Record<string, unknown>>).map(valueToRelatorItem(field))
    } else {
      attr.arrayValue = (value as unknown[]).filter((v) => v != null).map(String)
    }
    return attr
  }

  if (typeof value === "string") {
    if (field.widget === "Date") {
      attr.dateValue = value
    } else {
      attr.strValue = value
    }
    return attr
  }

  if (typeof value === "number") {
    attr.intValue = value
    attr.strValue = String(value)
  }

  return attr
}

function extractValueFromAttr(field: ShPostTypeSchemaField, attr: ShPostAttr): unknown {
  if (attr.shChildrenRelatorItems && attr.shChildrenRelatorItems.length > 0) {
    return attr.shChildrenRelatorItems.map((item) => relatorItemToValue(field, item))
  }
  if (attr.arrayValue && attr.arrayValue.length > 0) {
    return [...attr.arrayValue]
  }
  if (field.widget === "Date") {
    return attr.dateValue || null
  }
  return attr.strValue ?? null
}

function valueToRelatorItem(field: ShPostTypeSchemaField) {
  return (value: Record<string, unknown>): ShRelatorItem => {
    const childAttrs: ShPostAttr[] = []
    const children = field.children ?? []
    for (const child of children) {
      childAttrs.push({
        ...buildAttrFromValue(child, value[child.name]),
        id: `${String(value._id ?? "")}-${child.name}`,
      })
    }
    return {
      id: (value._id as string) || "",
      title: (value._title as string | undefined) ?? undefined,
      summary: (value._summary as string | undefined) ?? undefined,
      ordinal: (value._ordinal as number) ?? 0,
      shChildrenPostAttrs: childAttrs,
    }
  }
}

function relatorItemToValue(field: ShPostTypeSchemaField, item: ShRelatorItem): Record<string, unknown> {
  const out: Record<string, unknown> = {
    _id: item.id,
    _ordinal: item.ordinal,
  }
  if (item.title !== undefined) out._title = item.title
  if (item.summary !== undefined) out._summary = item.summary
  for (const child of field.children ?? []) {
    const childAttr = item.shChildrenPostAttrs.find((a) => a.shPostTypeAttr?.name === child.name)
    if (childAttr) {
      out[child.name] = extractValueFromAttr(child, childAttr)
    }
  }
  return out
}

function schemaFieldToAttrMetadata(field: ShPostTypeSchemaField): ShPostAttr["shPostTypeAttr"] {
  return {
    id: field.name,
    name: field.name,
    label: field.label,
    description: field.description ?? "",
    ordinal: field.ordinal,
    required: field.required ? 1 : 0,
    isSummary: field.isSummary ? 1 : 0,
    isTitle: field.isTitle ? 1 : 0,
    shWidget: { name: field.widget },
    widgetSettings: field.widgetSettings,
    shPostTypeAttrs: field.children?.map((c) => ({
      id: c.name,
      name: c.name,
      label: c.label,
      description: c.description ?? "",
      ordinal: c.ordinal,
      required: c.required ? 1 : 0,
      isSummary: c.isSummary ? 1 : 0,
      isTitle: c.isTitle ? 1 : 0,
      shWidget: { name: c.widget },
      widgetSettings: c.widgetSettings,
    })),
  }
}
