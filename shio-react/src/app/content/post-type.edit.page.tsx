import { IconStack2 } from "@tabler/icons-react"
import { EmptyState } from "@/components/empty-state"
import { PageHero } from "@/components/page-hero"
import { Button } from "@/components/ui/button"
/*
 * SH892 — the form's section is `AdaptiveSectionCard`. Its header takes `{icon, title,
 * description}` and nothing else, so the details tabs live inside `Content`, not in the header.
 * Imported from the bento subpath, the only entry that exports it.
 */
import { AdaptiveSectionCard } from "@viglet/viglet-design-system/bento"
import { Input } from "@/components/ui/input"
import { Textarea } from "@/components/ui/textarea"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form"
import { Badge } from "@/components/ui/badge"
import { BadgeColorful } from "@/components/badge-colorful"
import { Separator } from "@/components/ui/separator"
import { Switch } from "@/components/ui/switch"
import { FieldConstraints } from "@/components/field-constraints"
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip"
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { ROUTES } from "@/app/routes.const"
import { IconFileText } from "@tabler/icons-react"
import {
  ChevronDown,
  ChevronRight,
  GripVertical,
  Info,
  Layers,
  Lock,
  PencilLine,
  Plus,
  Save,
  Settings2,
  Trash2,
} from "lucide-react"
import { useQuery } from "@tanstack/react-query"
import { isCancelled } from "@/lib/read-options"
import { addressKey } from "@/lib/query-keys"
import { useCallback, useEffect, useState } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router-dom"
import axios from "axios"
import { ShPostTypeService } from "@/services/post-type/post-type.service"
import type { ShPostType, ShPostTypeField } from "@/models/post-type/post-type"
import type { ShWidget } from "@/models/widget/widget"
import { GradientButton, toast } from "@viglet/viglet-design-system"
import {
  DndContext,
  closestCenter,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
  type DragEndEvent,
} from "@dnd-kit/core"
import {
  arrayMove,
  SortableContext,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable"
import { CSS } from "@dnd-kit/utilities"
import { WIDGET_BADGE_COLORS, WIDGET_ACCENT_COLORS } from "@/lib/widget-colors"
import { usePostTypes, useWidgets } from "@/hooks/use-catalogue"
import { reportProblem } from "@/lib/problem"

const postTypeService = new ShPostTypeService()

/** SH1046 — one identity each while the cached catalogues load, not one per render. */
const EMPTY_WIDGETS: ShWidget[] = []
const EMPTY_POST_TYPES: ShPostType[] = []

const SEARCH_ASSOCIATIONS = [
  "", "title", "text", "abstract", "type", "image", "publication_date", "url"
]

/**
 * A field plus an editor-local `key`.
 *
 * The name-keyed post-type model (SH117) carries no field ids — a field is
 * identified by its `name`, which is exactly what the user is editing, so it
 * cannot double as a React/dnd-kit identity. A synthetic key minted on load and on
 * add is stable across renames and reorders, and is stripped before saving.
 */
type FieldRow = Omit<ShPostTypeField, "fields"> & { key: string; fields?: FieldRow[] }

function newKey(): string {
  return crypto.randomUUID()
}

/** Attach editor keys to a field tree loaded from the server. */
function withKeys(fields: ShPostTypeField[] | undefined): FieldRow[] {
  return (fields ?? [])
    .slice()
    .sort((a, b) => a.ordinal - b.ordinal)
    .map((f) => ({ ...f, key: newKey(), fields: f.fields ? withKeys(f.fields) : undefined }))
}

/** Strip editor keys and renumber ordinals — what goes back over the wire. */
function toFieldDefs(rows: FieldRow[]): ShPostTypeField[] {
  return rows.map((row, index) => {
    const { key: _key, fields, ...field } = row
    void _key
    return {
      ...field,
      ordinal: index + 1,
      fields: fields ? toFieldDefs(fields) : undefined,
    }
  })
}

function parseSettings(raw: string | undefined | null): Record<string, unknown> {
  if (!raw) return {}
  try {
    const p = JSON.parse(raw)
    return p && typeof p === "object" ? p : {}
  } catch {
    return {}
  }
}

// --- Widget-specific settings components ---

function ChoicesSettings({ settings, onChange }: {
  settings: Record<string, unknown>
  onChange: (s: Record<string, unknown>) => void
}) {
  const { t } = useTranslation()
  return (
    <div className="space-y-4">
      <div className="space-y-2">
        <div className="flex items-center gap-2">
          <p className="font-medium text-sm">{t("content.choices")}</p>
          <Tooltip>
            <TooltipTrigger asChild>
              <Info className="h-3.5 w-3.5 text-muted-foreground cursor-help" />
            </TooltipTrigger>
            <TooltipContent>{t("content.choicesDescription")}</TooltipContent>
          </Tooltip>
        </div>
        <Textarea
          rows={5}
          className="font-mono text-sm"
          placeholder={t("content.choicesExample")}
          value={(settings.choices as string) ?? ""}
          onChange={(e) => onChange({ ...settings, choices: e.target.value })}
        />
      </div>
      <div className="flex items-center gap-3">
        <Switch
          checked={(settings.allowNull as number) === 1}
          onCheckedChange={(v) => onChange({ ...settings, allowNull: v ? 1 : 0 })}
        />
        <span className="text-sm">{t("content.allowNull")}</span>
      </div>
    </div>
  )
}

function FolderSettings({ settingsKey, settings, onChange }: {
  settingsKey: string
  settings: Record<string, unknown>
  onChange: (s: Record<string, unknown>) => void
}) {
  const { t } = useTranslation()
  const nested = (settings[settingsKey] as Record<string, string>) ?? {}
  return (
    <div className="space-y-2">
      <p className="font-medium text-sm">{t("content.folder")}</p>
      <Input
        placeholder={t("content.folderId")}
        value={nested.folderId ?? ""}
        onChange={(e) => onChange({ ...settings, [settingsKey]: { ...nested, folderId: e.target.value } })}
      />
    </div>
  )
}

function MultiSelectSettings({ settings, onChange, postTypes }: {
  settings: Record<string, unknown>
  onChange: (s: Record<string, unknown>) => void
  postTypes: ShPostType[]
}) {
  const { t } = useTranslation()
  const ms = (settings.multiselect as Record<string, string>) ?? {}
  return (
    <div className="space-y-4">
      <div className="space-y-2">
        <p className="font-medium text-sm">{t("content.postType")}</p>
        <Select
          value={ms.postType ?? ""}
          onValueChange={(v) => onChange({ ...settings, multiselect: { ...ms, postType: v } })}
        >
          <SelectTrigger className="w-full" aria-label={t("content.postType")}>
            <SelectValue placeholder={t("content.pickOne")} />
          </SelectTrigger>
          <SelectContent>
            {postTypes
              .slice().sort((a, b) => label(a).localeCompare(label(b)))
              .map((pt) => (
                <SelectItem key={pt.name} value={pt.name}>{label(pt)}</SelectItem>
              ))}
          </SelectContent>
        </Select>
      </div>
      <div className="space-y-2">
        <p className="font-medium text-sm">{t("content.folder")}</p>
        <Input
          placeholder={t("content.folderId")}
          value={ms.folderId ?? ""}
          onChange={(e) => onChange({ ...settings, multiselect: { ...ms, folderId: e.target.value } })}
        />
      </div>
    </div>
  )
}

/** A post-type's display label — `title` when it has one, else its API name. */
function label(postType: ShPostType): string {
  return postType.title ?? postType.name
}

function RelatorSettings({ field, settings, onChange, widgets, postTypes, onFieldChange }: {
  field: FieldRow
  settings: Record<string, unknown>
  onChange: (s: Record<string, unknown>) => void
  widgets: ShWidget[]
  postTypes: ShPostType[]
  onFieldChange: (field: FieldRow) => void
}) {
  const { t } = useTranslation()
  const relator = (settings.relator as Record<string, string>) ?? {}
  return (
    <div className="space-y-4">
      <div className="space-y-2">
        <p className="font-medium text-sm">{t("content.relatorTitle")}</p>
        <Input
          value={relator.title ?? ""}
          onChange={(e) => onChange({ ...settings, relator: { ...relator, title: e.target.value } })}
        />
      </div>
      <div className="space-y-2">
        <p className="font-medium text-sm">{t("content.subFields")}</p>
        <FieldList
          fields={field.fields ?? []}
          widgets={widgets}
          postTypes={postTypes}
          onChange={(subFields) => onFieldChange({ ...field, fields: subFields })}
        />
      </div>
    </div>
  )
}

function WidgetSpecificSettings({ field, widgets, postTypes, onFieldChange }: {
  field: FieldRow
  widgets: ShWidget[]
  postTypes: ShPostType[]
  onFieldChange: (field: FieldRow) => void
}) {
  const { t } = useTranslation()
  const widgetName = field.widget
  if (!widgetName) return null

  const settings = parseSettings(field.widgetSettings)

  const updateSettings = (s: Record<string, unknown>) => {
    onFieldChange({ ...field, widgetSettings: JSON.stringify(s) })
  }

  let content: React.ReactNode = null
  switch (widgetName) {
    case "Check Box":
    case "Combo Box":
      content = <ChoicesSettings settings={settings} onChange={updateSettings} />
      break
    case "File":
      content = <FolderSettings settingsKey="file" settings={settings} onChange={updateSettings} />
      break
    case "Content Select":
      content = <FolderSettings settingsKey="contentSelect" settings={settings} onChange={updateSettings} />
      break
    case "Multi Select":
      content = <MultiSelectSettings settings={settings} onChange={updateSettings} postTypes={postTypes} />
      break
    case "Relator":
      content = (
        <RelatorSettings
          field={field}
          settings={settings}
          onChange={updateSettings}
          widgets={widgets}
          postTypes={postTypes}
          onFieldChange={onFieldChange}
        />
      )
      break
    default:
      return null
  }

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-2 text-sm font-medium text-muted-foreground">
        <Settings2 className="h-4 w-4" />
        <span>{t("content.widgetSettings")}</span>
      </div>
      <div className="rounded-lg border bg-muted/30 p-4">
        {content}
      </div>
    </div>
  )
}

// --- Semantic Navigation settings ---

function SemanticNavigationSettings({ field, onFieldChange }: {
  field: FieldRow
  onFieldChange: (field: FieldRow) => void
}) {
  const { t } = useTranslation()
  const settings = parseSettings(field.widgetSettings)
  const search = (settings.search as Record<string, unknown>) ?? {}

  const updateSearch = (s: Record<string, unknown>) => {
    onFieldChange({ ...field, widgetSettings: JSON.stringify({ ...settings, search: s }) })
  }

  return (
    <div className="space-y-5 p-5">
      <div className="space-y-2">
        <p className="font-medium text-sm">{t("content.searchFieldAssociation")}</p>
        <Select
          value={(search.association as string) ?? ""}
          onValueChange={(v) => updateSearch({ ...search, association: v })}
        >
          <SelectTrigger className="w-full" aria-label={t("content.searchFieldAssociation")}>
            <SelectValue placeholder={t("content.none")} />
          </SelectTrigger>
          <SelectContent>
            {SEARCH_ASSOCIATIONS.map((a) => (
              <SelectItem key={a || "none"} value={a || "none"}>{a || t("content.none")}</SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <Separator />

      <div className="space-y-3">
        <div className="flex items-center gap-3">
          <Switch
            checked={(search.isAdditionalField as number) === 1}
            onCheckedChange={(v) => updateSearch({ ...search, isAdditionalField: v ? 1 : 0 })}
          />
          <span className="text-sm font-medium">{t("content.createAdditionalSearchField")}</span>
        </div>
        {(search.isAdditionalField as number) === 1 && (
          <div className="ml-11 space-y-3 rounded-lg border bg-muted/30 p-4">
            <p className="font-medium text-sm">{t("content.searchFieldName")}</p>
            <div className="flex items-center gap-3">
              <Switch
                checked={(search.isSamePostTypeField as number) === 1}
                onCheckedChange={(v) => updateSearch({ ...search, isSamePostTypeField: v ? 1 : 0 })}
              />
              <span className="text-sm">{t("content.samePostTypeFieldName")}</span>
            </div>
            {(search.isSamePostTypeField as number) === 1 ? (
              <Input disabled value={field.name ?? ""} className="bg-muted" />
            ) : (
              <Input
                value={(search.customFieldName as string) ?? ""}
                onChange={(e) => updateSearch({ ...search, customFieldName: e.target.value })}
              />
            )}
          </div>
        )}
      </div>
    </div>
  )
}

// --- Details tab (expanded field settings) ---

function FieldDetailsSettings({ field, widgets, postTypes, onFieldChange }: {
  field: FieldRow
  widgets: ShWidget[]
  postTypes: ShPostType[]
  onFieldChange: (field: FieldRow) => void
}) {
  const { t } = useTranslation()

  return (
    <div className="space-y-5 p-5">
      {/* Label + Identifier in 2-col layout */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div className="space-y-1.5">
          <div className="flex items-center gap-1.5">
            <label className="font-medium text-sm">{t("content.fieldLabel")}</label>
            <Tooltip>
              <TooltipTrigger asChild>
                <Info className="h-3.5 w-3.5 text-muted-foreground cursor-help" />
              </TooltipTrigger>
              <TooltipContent side="top">{t("content.fieldLabelDescription")}</TooltipContent>
            </Tooltip>
          </div>
          <Input
            value={field.label ?? ""}
            onChange={(e) => onFieldChange({ ...field, label: e.target.value })}
          />
        </div>
        <div className="space-y-1.5">
          <div className="flex items-center gap-1.5">
            <label className="font-medium text-sm">{t("content.fieldIdentifier")}</label>
            <Tooltip>
              <TooltipTrigger asChild>
                <Info className="h-3.5 w-3.5 text-muted-foreground cursor-help" />
              </TooltipTrigger>
              <TooltipContent side="top">{t("content.fieldIdentifierDescription")}</TooltipContent>
            </Tooltip>
          </div>
          <Input
            className="font-mono"
            value={field.name ?? ""}
            onChange={(e) => onFieldChange({ ...field, name: e.target.value })}
          />
        </div>
      </div>

      {/* Field Type — the widget catalogue is a closed set, keyed by name (SH117) */}
      <div className="space-y-1.5">
        <label className="font-medium text-sm">{t("content.fieldType")}</label>
        <Select
          value={field.widget ?? ""}
          onValueChange={(widgetName) => onFieldChange({ ...field, widget: widgetName })}
        >
          <SelectTrigger className="w-full md:w-80" aria-label={t("content.widget")}>
            <SelectValue placeholder={t("content.pickOne")} />
          </SelectTrigger>
          <SelectContent>
            {widgets
              .slice().sort((a, b) => a.name.localeCompare(b.name))
              .map((w) => (
                <SelectItem key={w.name} value={w.name}>
                  <div className="flex items-center gap-2">
                    <span
                      className={`inline-block h-2 w-2 rounded-full ${
                        WIDGET_BADGE_COLORS[w.name]
                          ? WIDGET_ACCENT_COLORS[w.name]?.replace("border-l-", "bg-") ?? "bg-gray-400"
                          : "bg-gray-400"
                      }`}
                    />
                    {w.name}
                  </div>
                </SelectItem>
              ))}
          </SelectContent>
        </Select>
      </div>

      <Separator />

      {/* Flags row */}
      <div className="flex flex-wrap gap-6">
        <div className="flex items-center gap-2.5">
          <Switch
            checked={Boolean(field.required)}
            onCheckedChange={(v) => onFieldChange({ ...field, required: v })}
          />
          <span className="text-sm">{t("content.isRequired")}</span>
        </div>
        <div className="flex items-center gap-2.5">
          <Switch
            checked={Boolean(field.isTitle)}
            onCheckedChange={(v) => onFieldChange({ ...field, isTitle: v })}
          />
          <span className="text-sm">{t("content.isTitle")}</span>
        </div>
        <div className="flex items-center gap-2.5">
          <Switch
            checked={Boolean(field.isSummary)}
            onCheckedChange={(v) => onFieldChange({ ...field, isSummary: v })}
          />
          <span className="text-sm">{t("content.isDescription")}</span>
        </div>
      </div>

      {/*
        SH1010 — what this field will accept, beside the flag that says it must be filled in. Here
        rather than in a dialog of its own: `required` is one rule of twelve now, and a curator
        deciding that a slug may not contain spaces is making the same kind of decision they made
        one row up.
      */}
      <FieldConstraints field={field} onFieldChange={onFieldChange} />

      {/* Widget-specific settings */}
      <WidgetSpecificSettings
        field={field}
        widgets={widgets}
        postTypes={postTypes}
        onFieldChange={onFieldChange}
      />
    </div>
  )
}

// --- Sortable field row ---

function SortableFieldRow({ field, index, widgets, postTypes, onFieldChange, onDelete }: {
  field: FieldRow
  index: number
  widgets: ShWidget[]
  postTypes: ShPostType[]
  onFieldChange: (field: FieldRow) => void
  onDelete: () => void
}) {
  const { t } = useTranslation()
  const [isOpen, setIsOpen] = useState(false)
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id: field.key })

  const widgetName = field.widget ?? ""
  const accentClass = WIDGET_ACCENT_COLORS[widgetName] ?? "border-l-gray-300 dark:border-l-gray-600"

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
  }

  return (
    <div
      ref={setNodeRef}
      style={style}
      className={`border rounded-lg border-l-4 ${accentClass} mb-3 bg-background shadow-sm hover:shadow-md transition-shadow ${
        isOpen ? "ring-1 ring-ring/20" : ""
      }`}
    >
      {/* Collapsed header */}
      {/* eslint-disable-next-line jsx-a11y/click-events-have-key-events, jsx-a11y/no-static-element-interactions -- the pointer's shortcut; the chevron button is the keyboard's (SH1054) */}
      <div
        className="flex items-center gap-3 px-3 py-2.5 cursor-pointer select-none"
        onClick={() => setIsOpen(!isOpen)}
      >
        {/* Drag handle */}
        <button
          type="button"
          className="cursor-grab touch-none text-muted-foreground/50 hover:text-muted-foreground transition-colors"
          // SH1054 — the relator's and the block editor's handles were named; this one was not.
          aria-label={t("content.relatorReorder")}
          {...attributes}
          {...listeners}
          onClick={(e) => e.stopPropagation()}
        >
          <GripVertical className="h-4 w-4" aria-hidden="true" />
        </button>

        {/* Order number */}
        <span className="flex items-center justify-center h-6 w-6 rounded-full bg-muted text-xs font-bold text-muted-foreground shrink-0">
          {index + 1}
        </span>

        {/* SH1054 — the keyboard's way to expand a field. It was a div, so only a pointer could
            open one: the header's click below stays as the pointer's shortcut to the same toggle. */}
        <button
          type="button"
          className="text-muted-foreground rounded focus-visible:outline-2 focus-visible:outline-ring"
          aria-expanded={isOpen ? "true" : "false"}
          aria-label={field.label || field.name || "Untitled"}
          onClick={(e) => {
            e.stopPropagation()
            setIsOpen(!isOpen)
          }}
        >
          {isOpen
            ? <ChevronDown className="h-4 w-4" aria-hidden="true" />
            : <ChevronRight className="h-4 w-4" aria-hidden="true" />
          }
        </button>

        {/* Field label + name */}
        <div className="flex items-center gap-2 flex-1 min-w-0">
          <span className="font-semibold text-sm truncate">{field.label || "Untitled"}</span>
          <span className="text-xs text-muted-foreground font-mono truncate hidden sm:inline">
            {field.name}
          </span>
        </div>

        {/* Badges */}
        <div className="flex items-center gap-1.5 shrink-0">
          {field.required && (
            <BadgeColorful text={t("content.required")} className="text-[10px] px-1.5 py-0 h-5" />
          )}
          {field.isTitle && (
            <BadgeColorful text="T" className="text-[10px] px-1.5 py-0 h-5" />
          )}
          {field.isSummary && (
            <BadgeColorful text="S" className="text-[10px] px-1.5 py-0 h-5" />
          )}
          <BadgeColorful text={widgetName || "—"} className="text-[10px] px-2 py-0 h-5 font-medium" />
        </div>

        {/* Delete button */}
        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              type="button"
              variant="ghost"
              size="icon"
              // SH1054 — named: the field row's delete was an icon and nothing else.
              aria-label={t("common.delete")}
              className="h-7 w-7 text-muted-foreground hover:text-destructive hover:bg-destructive/10 shrink-0"
              onClick={(e) => {
                e.stopPropagation()
                onDelete()
              }}
            >
              <Trash2 className="h-3.5 w-3.5" />
            </Button>
          </TooltipTrigger>
          <TooltipContent>{t("common.delete")}</TooltipContent>
        </Tooltip>
      </div>

      {/* Expanded content */}
      {isOpen && (
        <div className="border-t">
          <Tabs defaultValue="details">
            <div className="px-5 pt-3">
              <TabsList>
                <TabsTrigger value="details" className="gap-1.5">
                  <Settings2 className="h-3.5 w-3.5" />
                  {t("content.details")}
                </TabsTrigger>
                <TabsTrigger value="semantic" className="gap-1.5">
                  <Layers className="h-3.5 w-3.5" />
                  {t("content.semanticNavigation")}
                </TabsTrigger>
              </TabsList>
            </div>
            <TabsContent value="details">
              <FieldDetailsSettings
                field={field}
                widgets={widgets}
                postTypes={postTypes}
                onFieldChange={onFieldChange}
              />
            </TabsContent>
            <TabsContent value="semantic">
              <SemanticNavigationSettings field={field} onFieldChange={onFieldChange} />
            </TabsContent>
          </Tabs>
        </div>
      )}
    </div>
  )
}

// --- Field list with drag-and-drop ---

function FieldList({ fields, widgets, postTypes, onChange }: {
  fields: FieldRow[]
  widgets: ShWidget[]
  postTypes: ShPostType[]
  onChange: (fields: FieldRow[]) => void
}) {
  const { t } = useTranslation()
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 8 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates })
  )

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event
    if (over && active.id !== over.id) {
      const oldIndex = fields.findIndex((f) => f.key === active.id)
      const newIndex = fields.findIndex((f) => f.key === over.id)
      const reordered = arrayMove(fields, oldIndex, newIndex).map((f, i) => ({ ...f, ordinal: i + 1 }))
      onChange(reordered)
    }
  }

  const addField = () => {
    const newField: FieldRow = {
      key: newKey(),
      name: `FIELD_${fields.length + 1}`,
      label: `Field ${fields.length + 1}`,
      description: "",
      ordinal: fields.length + 1,
      required: false,
      isSummary: false,
      isTitle: false,
      widget: "Text",
      widgetSettings: "",
    }
    onChange([...fields, newField])
  }

  const updateField = (index: number, updated: FieldRow) => {
    const next = [...fields]
    next[index] = updated
    onChange(next)
  }

  const deleteField = (index: number) => {
    onChange(fields.filter((_, i) => i !== index).map((f, i) => ({ ...f, ordinal: i + 1 })))
  }

  /*
   * A post type with no fields yet — the state every newly created type starts in, so this is
   * the first thing a curator sees after naming one, not an edge case.
   *
   * SH903 converted it because SH902 had already made this page a bento surface: a hand-rolled
   * console void rendering under the frosted shell is the same silent half-migration that task
   * spent its length on, and this one carried an action, which is why the adapter takes one.
   */
  if (fields.length === 0) {
    return (
      <EmptyState
        icon={IconStack2}
        tone="amber"
        title={t("content.noFields")}
        description={t("content.noFieldsDescription")}
        action={
          <Button type="button" onClick={addField} className="gap-2">
            <Plus className="h-4 w-4" />
            {t("content.addField")}
          </Button>
        }
      />
    )
  }

  return (
    <div>
      {/* Header */}
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-2">
          <Badge variant="secondary" className="text-xs">
            {fields.length}
          </Badge>
          <span className="text-sm text-muted-foreground">{t("content.dragToReorder")}</span>
        </div>
        <Button type="button" size="sm" onClick={addField} className="gap-1.5">
          <Plus className="h-4 w-4" />
          {t("content.addField")}
        </Button>
      </div>

      {/* Sortable list */}
      <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
        <SortableContext items={fields.map((f) => f.key)} strategy={verticalListSortingStrategy}>
          {fields.map((field, index) => (
            <SortableFieldRow
              key={field.key}
              field={field}
              index={index}
              widgets={widgets}
              postTypes={postTypes}
              onFieldChange={(updated) => updateField(index, updated)}
              onDelete={() => deleteField(index)}
            />
          ))}
        </SortableContext>
      </DndContext>
    </div>
  )
}

// --- Main page ---

export default function PostTypeEditPage() {
  const { postTypeName } = useParams() as { postTypeName: string }
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [isLoading, setIsLoading] = useState(false)
  const [fields, setFields] = useState<FieldRow[]>([])
  /*
   * SH1046 — the two catalogues are cached reads and the post being edited is not.
   *
   * The widget catalogue and the post-type list are the same for every editor a curator opens;
   * `postTypeService.get(name)` is this one type, and it is deliberately left as a fetch so a save
   * followed by a reopen shows what was saved rather than what the cache remembers.
   */
  const widgets = useWidgets().data ?? EMPTY_WIDGETS
  const postTypes = usePostTypes().data ?? EMPTY_POST_TYPES
  const [renameOpen, setRenameOpen] = useState(false)
  const [renameTo, setRenameTo] = useState("")
  const [renaming, setRenaming] = useState(false)

  /*
   * SH1116 — one read per post-type, cancelled on leaving, keyed on the name it reads.
   *
   * `t` and `form` were in the dependency list and neither is a subject: a translation function and a
   * `useForm` identity say nothing about which post-type is wanted. SH117 makes this the one
   * name-keyed surface in the product, so the key is the name.
   */
  const loaded = useQuery<ShPostType>({
    queryKey: addressKey(`postType:${postTypeName}`),
    queryFn: ({ signal }) => postTypeService.get(postTypeName, { signal }),
    enabled: Boolean(postTypeName),
  })

  /*
   * SH1166 — the form is seeded through `values`, which react-hook-form resets from in its own effect
   * and only when the type differs; a `form.reset` here, during render, updated every field's
   * Controller mid-render. The page's own field rows are still set during render, once per type
   * (`react-hooks/set-state-in-effect`), because they are this component's state.
   */
  const form = useForm<ShPostType>({ values: loaded.data, resetOptions: { keepDirtyValues: true } })
  const [seeded, setSeeded] = useState<string | null>(null)
  if (loaded.data && seeded !== postTypeName) {
    setSeeded(postTypeName)
    setFields(withKeys(loaded.data.fields))
  }

  useEffect(() => {
    // The read had no failure path: a post-type that would not load left an empty form that a save
    // would have written over it.
    if (loaded.error && !isCancelled(loaded.error)) reportProblem(loaded.error, t("common.error"))
  }, [loaded.error, t])

  const save = useCallback(async (data: ShPostType, close: boolean) => {
    setIsLoading(true)
    try {
      // The path key is the name the type was loaded under — the API-name input is
      // read-only, so these cannot diverge, and a PUT to a *new* name would create a
      // second post-type rather than rename this one (SH134's lesson for posts).
      await postTypeService.update({ ...data, name: postTypeName, fields: toFieldDefs(fields) })
      toast.success(t("common.save"))
      if (close) navigate(ROUTES.POST_TYPE_ROOT)
    } catch (error) {
      // SH1139 — a create that toasts success and lets a refusal escape as an unhandled rejection
      // looks exactly like a button that did nothing. The dock reads toasts, so the curator, the
      // mascot and a screen reader all missed it.
      reportProblem(error, t("common.error"))
    } finally {
      setIsLoading(false)
    }
  }, [fields, navigate, postTypeName, t])

  /**
   * SH163 — the rename, and it routes to the new name because the route *is* the key.
   *
   * The response says what came along (the posts, and any `Multi Select` that was narrowing to
   * the old name), plus what the server will not rename: a project's content files and
   * documents name a post-type too, and nothing here can edit those. That is surfaced as a
   * toast rather than swallowed — a curator who renamed a code-adjacent type needs to know the
   * next `shio push --check` will report drift.
   */
  const rename = useCallback(async () => {
    const target = renameTo.trim()
    if (!target || target === postTypeName) {
      setRenameOpen(false)
      return
    }
    setRenaming(true)
    try {
      const moved = await postTypeService.rename(postTypeName, target)
      setRenameOpen(false)
      toast.success(t("content.renamedPostType", { from: moved.from, to: moved.to, count: moved.posts }))
      if (moved.referencesUpdated.length > 0) {
        toast.info(t("content.renameRetargeted", { fields: moved.referencesUpdated.join(", ") }))
      }
      navigate(`${ROUTES.POST_TYPE_ROOT}/${encodeURIComponent(moved.to)}`)
    } catch (error) {
      // 409 = occupied name, a system type, or a code-managed one; 400 = a bad target. Each
      // arrives as an SH80 problem whose `fix` is the sentence worth showing.
      const problem = axios.isAxiosError(error) ? error.response?.data : null
      toast.error(problem?.fix || problem?.detail || problem?.title || t("content.renameError"))
    } finally {
      setRenaming(false)
    }
  }, [navigate, postTypeName, renameTo, t])

  const title = form.watch("title")
  const managed = Boolean(form.watch("managed"))

  return (
    <>
      {/*
        SH902 — one call, the 1:2 mapping SH901 established. The hero's title is the post type's
        **display title**, not its API name: `name` is the key the whole content API, the CLI and
        the agent surface are keyed on, and renaming it is its own operation (SH117/SH163) —
        a `PUT` to a new name creates a second type and orphans the first while answering 200.
        The API name therefore stays a read-only field with its Rename button, below.
      */}
      <PageHero
        icon={IconFileText}
        eyebrow={t("content.postType")}
        title={t("content.editPostType")}
        urlBase={ROUTES.POST_TYPE_ROOT}
        backLabel={t("content.postTypes")}
        saveTitle={t("content.modelPostType", { title: title ?? "" })}
        onCancel={() => navigate(ROUTES.POST_TYPE_ROOT)}
        loading={isLoading}
        saveDisabled={managed}
        actions={
            <>
              {managed && (
                <Tooltip>
                  <TooltipTrigger asChild>
                    <Badge
                      variant="outline"
                      className="gap-1.5 border-violet-300 text-violet-600 dark:border-violet-700 dark:text-violet-400"
                    >
                      <Lock className="size-3.5" />
                      {t("content.managedByCode")}
                    </Badge>
                  </TooltipTrigger>
                  <TooltipContent>{t("content.managedByCodeHint")}</TooltipContent>
                </Tooltip>
              )}
              <GradientButton
                type="button"
                size="sm"
                loading={isLoading}
                disabled={managed}
                onClick={form.handleSubmit((data) => save(data, false))}
              >
                <Save className="size-4" />
                {t("common.save")}
              </GradientButton>
              <GradientButton
                type="button"
                size="sm"
                variant="outline"
                disabled={isLoading || managed}
                onClick={form.handleSubmit((data) => save(data, true))}
              >
                {t("content.saveAndClose")}
              </GradientButton>
              <GradientButton type="button" size="sm" variant="ghost" onClick={() => navigate(ROUTES.POST_TYPE_ROOT)}>
                {t("common.cancel")}
              </GradientButton>
            </>
          }
      />
      <div className="space-y-6">

        {managed && (
          <div className="flex items-start gap-3 rounded-lg border border-violet-200 bg-violet-50 p-4 text-sm dark:border-violet-900 dark:bg-violet-950/30">
            <Lock className="mt-0.5 size-4 shrink-0 text-violet-600 dark:text-violet-400" />
            <div className="space-y-1">
              <p className="font-medium text-violet-800 dark:text-violet-300">{t("content.managedByCode")}</p>
              <p className="text-violet-700/80 dark:text-violet-400/80">{t("content.managedByCodeDescription")}</p>
            </div>
          </div>
        )}

        {/* When managed by code, the native `disabled` fieldset cascades to every
            nested control (inputs, switches, dnd handles), making the model read-only. */}
        <fieldset disabled={managed} className="m-0 min-w-0 space-y-6 border-0 p-0 disabled:opacity-70">
        {/* Post Type details + Workflow tabs */}
        <AdaptiveSectionCard>
          <AdaptiveSectionCard.StaticHeader icon={IconFileText} title={t("content.postType")} />
          <AdaptiveSectionCard.Content>
            <Tabs defaultValue="details">
              <TabsList className="mb-4">
                <TabsTrigger value="details">{t("content.details")}</TabsTrigger>
                <TabsTrigger value="workflow">{t("content.workflow")}</TabsTrigger>
              </TabsList>
              <Form {...form}>
                <TabsContent value="details">
                  <div className="space-y-4">
                    <FormField
                      control={form.control}
                      name="title"
                      rules={{ required: t("common.required") }}
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>{t("common.name")}</FormLabel>
                          <FormControl>
                            <Input {...field} value={field.value ?? ""} />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    <FormField
                      control={form.control}
                      name="description"
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>{t("common.description")}</FormLabel>
                          <FormControl>
                            <Textarea {...field} value={field.value ?? ""} />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    {/* The API name is the post-type's identity on the name-keyed surface
                        (SH117), so the field is still not a text box: typing in it would write
                        a second post-type and leave this one's content behind. Since SH163 the
                        rename is its own operation, so the field has a button instead of a
                        dead end. */}
                    <FormItem>
                      <div className="flex items-center gap-1.5">
                        <FormLabel>{t("content.apiName")}</FormLabel>
                        <Tooltip>
                          <TooltipTrigger asChild>
                            <Info className="h-3.5 w-3.5 text-muted-foreground cursor-help" />
                          </TooltipTrigger>
                          <TooltipContent side="top">{t("content.apiNameRename")}</TooltipContent>
                        </Tooltip>
                      </div>
                      <div className="flex items-center gap-2">
                        <FormControl>
                          <Input readOnly disabled className="bg-muted font-mono" value={postTypeName} />
                        </FormControl>
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          disabled={managed || Boolean(form.watch("system"))}
                          onClick={() => { setRenameTo(postTypeName); setRenameOpen(true) }}
                        >
                          <PencilLine className="mr-2 h-4 w-4" /> {t("content.rename")}
                        </Button>
                      </div>
                    </FormItem>
                    <FormField
                      control={form.control}
                      name="namePlural"
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>{t("content.apiPluralName")}</FormLabel>
                          <FormControl>
                            <Input {...field} value={field.value ?? ""} />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                  </div>
                </TabsContent>
                <TabsContent value="workflow">
                  <div className="space-y-4">
                    <FormField
                      control={form.control}
                      name="workflowPublishEntity"
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>{t("content.responsiblePublishing")}</FormLabel>
                          <FormControl>
                            <Input {...field} value={field.value ?? ""} />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                  </div>
                </TabsContent>
              </Form>
            </Tabs>
          </AdaptiveSectionCard.Content>
        </AdaptiveSectionCard>

        {/* Fields section */}
        <AdaptiveSectionCard>
          <AdaptiveSectionCard.StaticHeader icon={Layers} title={t("content.fields")} />
          <AdaptiveSectionCard.Content>
            <FieldList
              fields={fields}
              widgets={widgets}
              postTypes={postTypes}
              onChange={setFields}
            />
          </AdaptiveSectionCard.Content>
        </AdaptiveSectionCard>
        </fieldset>
      </div>

      {/* SH163 — the rename dialog. It states what a rename does and does not carry, because
          the operation looks trivial and the thing it replaces looked like it worked. */}
      <Dialog open={renameOpen} onOpenChange={setRenameOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{t("content.renamePostType")}</DialogTitle>
            <DialogDescription>{t("content.renamePostTypeHelp")}</DialogDescription>
          </DialogHeader>
          <Input
            className="font-mono"
            value={renameTo}
            onChange={(e) => setRenameTo(e.target.value)}
            // eslint-disable-next-line jsx-a11y/no-autofocus -- a rename dialog's one field (SH1054)
            autoFocus
          />
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" size="sm" onClick={() => setRenameOpen(false)}>
              {t("common.cancel")}
            </Button>
            <Button
              size="sm"
              disabled={renaming || !renameTo.trim() || renameTo.trim() === postTypeName}
              onClick={rename}
            >
              {renaming ? t("common.loading") : t("content.rename")}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </>
  )
}
