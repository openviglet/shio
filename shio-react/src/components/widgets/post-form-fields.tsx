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
        // SH1013 — the anchor a finding scrolls to. A panel that says which field is wrong and
        // cannot take you there leaves the curator scanning a sixteen-field form for the name.
        id={fieldAnchorId(field.name)}
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

/**
 * SH1013 — the id a finding's "go to field" resolves against.
 *
 * Exported so the panel and the form cannot disagree about the spelling: an anchor scheme written
 * twice is one that drifts, and the failure is silent — the button scrolls nowhere.
 */
export function fieldAnchorId(name: string): string {
  return `sh-field-${name}`
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

  // SH1017 — a Blocks field is routed by its WIDGET, before the value's shape is consulted. Both a
  // blocks document and a relator's rows are arrays of objects, so shape alone would hand a body to
  // the relator editor — the same "the widget decides, never the post-type" rule the file
  // projection follows.
  if (field.widget === "Blocks") {
    attr.blocksValue = Array.isArray(value) ? value : []
    return attr
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
  // SH1017 — and back out the same way, answering [] for an emptied document rather than null, for
  // the reason a repeatable field does: removing the last block is an edit that has to survive.
  if (field.widget === "Blocks") {
    return attr.blocksValue ?? []
  }
  // SH1012 — a repeatable field answers with its array even when the array is empty. Falling
  // through to `strValue ?? null` would make "the curator removed the last item" indistinguishable
  // from "this field was never filled in", and the removal would not survive the save.
  if (isRepeatable(field)) {
    return (attr.shChildrenRelatorItems ?? []).map((item) => relatorItemToValue(field, item))
  }
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

/** A `Relator` whose children are declared inline — SH95's collection, the one repeatable field. */
function isRepeatable(field: ShPostTypeSchemaField): boolean {
  return field.widget === "Relator" && (field.children?.length ?? 0) > 0
}

function valueToRelatorItem(field: ShPostTypeSchemaField) {
  return (value: Record<string, unknown>): ShRelatorItem => {
    // Minted before the children, because each child's id is scoped by it — two rows of the same
    // collection otherwise give their `title` fields the same id, and React reuses the wrong input.
    const rowId = (value._id as string) || crypto.randomUUID()
    const childAttrs: ShPostAttr[] = []
    const children = field.children ?? []
    for (const child of children) {
      childAttrs.push({
        ...buildAttrFromValue(child, value[child.name]),
        id: `${rowId}-${child.name}`,
      })
    }
    return {
      // SH1012 — minted here, never read back out. A row of an SH95 collection has no id: the
      // stored value is `{title, body}` and nothing else, which `ShPostDraftMergeService` says in
      // as many words and `ShCdaWriteTest` fixes as a shape. The id exists so React and dnd-kit
      // can tell two rows apart across a reorder, which is exactly the job `FieldRow.key` does one
      // screen over — and like that one it is stripped before saving.
      id: rowId,
      title: (value._title as string | undefined) ?? undefined,
      summary: (value._summary as string | undefined) ?? undefined,
      ordinal: (value._ordinal as number) ?? 0,
      shChildrenPostAttrs: childAttrs,
    }
  }
}

/**
 * SH1012 — the row as the post actually stores it: the declared child fields, and nothing else.
 *
 * This used to write `_id`, `_ordinal`, and `_title`/`_summary` when present. Nothing server-side
 * has ever read them — a collection row is a plain map of child names, which is why
 * `ShPostDraftMergeService` can address `features.1.body` at all — so they were four undeclared
 * keys waiting to be written into content. They never were, for the one reason this task is about:
 * the widget could not save. Making it editable without this is how the fix becomes the defect.
 *
 * Position is the ordinal. A separate `_ordinal` is a second answer to a question the array already
 * answers, and two answers is how they disagree.
 */
function relatorItemToValue(field: ShPostTypeSchemaField, item: ShRelatorItem): Record<string, unknown> {
  const out: Record<string, unknown> = {}
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
