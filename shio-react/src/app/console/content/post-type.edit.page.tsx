import { PageHeader } from "@/components/page-header"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Textarea } from "@/components/ui/textarea"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form"
import { Badge } from "@/components/ui/badge"
import { Separator } from "@/components/ui/separator"
import { Switch } from "@/components/ui/switch"
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip"
import { ROUTES } from "@/app/routes.const"
import { useBreadcrumb } from "@/contexts/breadcrumb.context"
import { IconFileText } from "@tabler/icons-react"
import {
  ChevronDown,
  ChevronRight,
  GripVertical,
  Info,
  Layers,
  Loader2,
  Plus,
  Save,
  Settings2,
  Trash2,
} from "lucide-react"
import { useCallback, useEffect, useState } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router-dom"
import { ShPostTypeService } from "@/services/post-type/post-type.service"
import { ShWidgetService } from "@/services/widget/widget.service"
import type { ShPostType, ShPostTypeAttr } from "@/models/post-type/post-type"
import type { ShWidget } from "@/models/widget/widget"
import { toast } from "@viglet/viglet-design-system"
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

const postTypeService = new ShPostTypeService()
const widgetService = new ShWidgetService()

const SEARCH_ASSOCIATIONS = [
  "", "title", "text", "abstract", "type", "image", "publication_date", "url"
]

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
          placeholder={"value1: Label 1\nvalue2: Label 2"}
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
        placeholder="Folder ID"
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
          <SelectTrigger className="w-full">
            <SelectValue placeholder={t("content.pickOne")} />
          </SelectTrigger>
          <SelectContent>
            {postTypes
              .slice().sort((a, b) => a.title.localeCompare(b.title))
              .map((pt) => (
                <SelectItem key={pt.name} value={pt.name}>{pt.title}</SelectItem>
              ))}
          </SelectContent>
        </Select>
      </div>
      <div className="space-y-2">
        <p className="font-medium text-sm">{t("content.folder")}</p>
        <Input
          placeholder="Folder ID"
          value={ms.folderId ?? ""}
          onChange={(e) => onChange({ ...settings, multiselect: { ...ms, folderId: e.target.value } })}
        />
      </div>
    </div>
  )
}

function RelatorSettings({ attr, settings, onChange, widgets, postTypes, onAttrChange }: {
  attr: ShPostTypeAttr
  settings: Record<string, unknown>
  onChange: (s: Record<string, unknown>) => void
  widgets: ShWidget[]
  postTypes: ShPostType[]
  onAttrChange: (attr: ShPostTypeAttr) => void
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
        <AttrFieldList
          attrs={attr.shPostTypeAttrs ?? []}
          widgets={widgets}
          postTypes={postTypes}
          onChange={(subAttrs) => onAttrChange({ ...attr, shPostTypeAttrs: subAttrs })}
        />
      </div>
    </div>
  )
}

function WidgetSpecificSettings({ attr, widgets, postTypes, onAttrChange }: {
  attr: ShPostTypeAttr
  widgets: ShWidget[]
  postTypes: ShPostType[]
  onAttrChange: (attr: ShPostTypeAttr) => void
}) {
  const { t } = useTranslation()
  const widgetName = attr.shWidget?.name
  if (!widgetName) return null

  const settings = parseSettings(attr.widgetSettings)

  const updateSettings = (s: Record<string, unknown>) => {
    onAttrChange({ ...attr, widgetSettings: JSON.stringify(s) })
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
          attr={attr}
          settings={settings}
          onChange={updateSettings}
          widgets={widgets}
          postTypes={postTypes}
          onAttrChange={onAttrChange}
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

function SemanticNavigationSettings({ attr, onAttrChange }: {
  attr: ShPostTypeAttr
  onAttrChange: (attr: ShPostTypeAttr) => void
}) {
  const { t } = useTranslation()
  const settings = parseSettings(attr.widgetSettings)
  const search = (settings.search as Record<string, unknown>) ?? {}

  const updateSearch = (s: Record<string, unknown>) => {
    onAttrChange({ ...attr, widgetSettings: JSON.stringify({ ...settings, search: s }) })
  }

  return (
    <div className="space-y-5 p-5">
      <div className="space-y-2">
        <p className="font-medium text-sm">{t("content.searchFieldAssociation")}</p>
        <Select
          value={(search.association as string) ?? ""}
          onValueChange={(v) => updateSearch({ ...search, association: v })}
        >
          <SelectTrigger className="w-full">
            <SelectValue placeholder="NONE" />
          </SelectTrigger>
          <SelectContent>
            {SEARCH_ASSOCIATIONS.map((a) => (
              <SelectItem key={a || "none"} value={a || "none"}>{a || "NONE"}</SelectItem>
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
              <Input disabled value={attr.name ?? ""} className="bg-muted" />
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

function AttrDetailsSettings({ attr, widgets, postTypes, onAttrChange }: {
  attr: ShPostTypeAttr
  widgets: ShWidget[]
  postTypes: ShPostType[]
  onAttrChange: (attr: ShPostTypeAttr) => void
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
            value={attr.label ?? ""}
            onChange={(e) => onAttrChange({ ...attr, label: e.target.value })}
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
            value={attr.name ?? ""}
            onChange={(e) => onAttrChange({ ...attr, name: e.target.value })}
          />
        </div>
      </div>

      {/* Field Type */}
      <div className="space-y-1.5">
        <label className="font-medium text-sm">{t("content.fieldType")}</label>
        <Select
          value={attr.shWidget?.id ?? ""}
          onValueChange={(widgetId) => {
            const widget = widgets.find((w) => w.id === widgetId)
            if (widget) {
              onAttrChange({ ...attr, shWidget: { id: widget.id, name: widget.name } })
            }
          }}
        >
          <SelectTrigger className="w-full md:w-80">
            <SelectValue placeholder={t("content.pickOne")} />
          </SelectTrigger>
          <SelectContent>
            {widgets
              .slice().sort((a, b) => a.name.localeCompare(b.name))
              .map((w) => (
                <SelectItem key={w.id} value={w.id}>
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
            checked={attr.required === 1}
            onCheckedChange={(v) => onAttrChange({ ...attr, required: v ? 1 : 0 })}
          />
          <span className="text-sm">{t("content.isRequired")}</span>
        </div>
        <div className="flex items-center gap-2.5">
          <Switch
            checked={attr.isTitle === 1}
            onCheckedChange={(v) => onAttrChange({ ...attr, isTitle: v ? 1 : 0 })}
          />
          <span className="text-sm">{t("content.isTitle")}</span>
        </div>
        <div className="flex items-center gap-2.5">
          <Switch
            checked={attr.isSummary === 1}
            onCheckedChange={(v) => onAttrChange({ ...attr, isSummary: v ? 1 : 0 })}
          />
          <span className="text-sm">{t("content.isDescription")}</span>
        </div>
      </div>

      {/* Widget-specific settings */}
      <WidgetSpecificSettings
        attr={attr}
        widgets={widgets}
        postTypes={postTypes}
        onAttrChange={onAttrChange}
      />
    </div>
  )
}

// --- Sortable attr row ---

function SortableAttrRow({ attr, index, widgets, postTypes, onAttrChange, onDelete }: {
  attr: ShPostTypeAttr
  index: number
  widgets: ShWidget[]
  postTypes: ShPostType[]
  onAttrChange: (attr: ShPostTypeAttr) => void
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
  } = useSortable({ id: attr.id || `attr-${index}` })

  const widgetName = attr.shWidget?.name ?? ""
  const accentClass = WIDGET_ACCENT_COLORS[widgetName] ?? "border-l-gray-300 dark:border-l-gray-600"
  const badgeClass = WIDGET_BADGE_COLORS[widgetName] ?? "bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-300 border-gray-200 dark:border-gray-700"

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
      <div
        className="flex items-center gap-3 px-3 py-2.5 cursor-pointer select-none"
        onClick={() => setIsOpen(!isOpen)}
      >
        {/* Drag handle */}
        <button
          type="button"
          className="cursor-grab touch-none text-muted-foreground/50 hover:text-muted-foreground transition-colors"
          {...attributes}
          {...listeners}
          onClick={(e) => e.stopPropagation()}
        >
          <GripVertical className="h-4 w-4" />
        </button>

        {/* Order number */}
        <span className="flex items-center justify-center h-6 w-6 rounded-full bg-muted text-xs font-bold text-muted-foreground shrink-0">
          {index + 1}
        </span>

        {/* Expand chevron */}
        <div className="text-muted-foreground">
          {isOpen
            ? <ChevronDown className="h-4 w-4" />
            : <ChevronRight className="h-4 w-4" />
          }
        </div>

        {/* Field label + name */}
        <div className="flex items-center gap-2 flex-1 min-w-0">
          <span className="font-semibold text-sm truncate">{attr.label || "Untitled"}</span>
          <span className="text-xs text-muted-foreground font-mono truncate hidden sm:inline">
            {attr.name}
          </span>
        </div>

        {/* Badges */}
        <div className="flex items-center gap-1.5 shrink-0">
          {attr.required === 1 && (
            <Badge variant="outline" className="text-[10px] px-1.5 py-0 h-5 border-amber-300 text-amber-600 dark:border-amber-700 dark:text-amber-400">
              {t("content.required")}
            </Badge>
          )}
          {attr.isTitle === 1 && (
            <Badge variant="outline" className="text-[10px] px-1.5 py-0 h-5 border-blue-300 text-blue-600 dark:border-blue-700 dark:text-blue-400">
              T
            </Badge>
          )}
          {attr.isSummary === 1 && (
            <Badge variant="outline" className="text-[10px] px-1.5 py-0 h-5 border-green-300 text-green-600 dark:border-green-700 dark:text-green-400">
              S
            </Badge>
          )}
          <Badge
            variant="outline"
            className={`text-[10px] px-2 py-0 h-5 font-medium ${badgeClass}`}
          >
            {widgetName || "—"}
          </Badge>
        </div>

        {/* Delete button */}
        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              type="button"
              variant="ghost"
              size="icon"
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
              <AttrDetailsSettings
                attr={attr}
                widgets={widgets}
                postTypes={postTypes}
                onAttrChange={onAttrChange}
              />
            </TabsContent>
            <TabsContent value="semantic">
              <SemanticNavigationSettings attr={attr} onAttrChange={onAttrChange} />
            </TabsContent>
          </Tabs>
        </div>
      )}
    </div>
  )
}

// --- Field list with drag-and-drop ---

function AttrFieldList({ attrs, widgets, postTypes, onChange }: {
  attrs: ShPostTypeAttr[]
  widgets: ShWidget[]
  postTypes: ShPostType[]
  onChange: (attrs: ShPostTypeAttr[]) => void
}) {
  const { t } = useTranslation()
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 8 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates })
  )

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event
    if (over && active.id !== over.id) {
      const oldIndex = attrs.findIndex((a) => (a.id || `attr-${attrs.indexOf(a)}`) === active.id)
      const newIndex = attrs.findIndex((a) => (a.id || `attr-${attrs.indexOf(a)}`) === over.id)
      const reordered = arrayMove(attrs, oldIndex, newIndex).map((a, i) => ({ ...a, ordinal: i + 1 }))
      onChange(reordered)
    }
  }

  const addAttr = () => {
    const textWidget = widgets.find((w) => w.name === "Text")
    const newAttr: ShPostTypeAttr = {
      id: "",
      name: `FIELD_${attrs.length + 1}`,
      label: `Field ${attrs.length + 1}`,
      description: "",
      ordinal: attrs.length + 1,
      required: 0,
      isSummary: 0,
      isTitle: 0,
      shWidget: textWidget ? { id: textWidget.id, name: textWidget.name } : { id: "", name: "Text" },
      widgetSettings: "",
    }
    onChange([...attrs, newAttr])
  }

  const updateAttr = (index: number, updated: ShPostTypeAttr) => {
    const newAttrs = [...attrs]
    newAttrs[index] = updated
    onChange(newAttrs)
  }

  const deleteAttr = (index: number) => {
    const newAttrs = attrs.filter((_, i) => i !== index).map((a, i) => ({ ...a, ordinal: i + 1 }))
    onChange(newAttrs)
  }

  // Empty state
  if (attrs.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-16 text-center">
        <div className="rounded-full bg-muted p-4 mb-4">
          <Layers className="h-8 w-8 text-muted-foreground" />
        </div>
        <h3 className="font-semibold text-lg mb-1">{t("content.noFields")}</h3>
        <p className="text-sm text-muted-foreground max-w-sm mb-6">
          {t("content.noFieldsDescription")}
        </p>
        <Button type="button" onClick={addAttr} className="gap-2">
          <Plus className="h-4 w-4" />
          {t("content.addField")}
        </Button>
      </div>
    )
  }

  return (
    <div>
      {/* Header */}
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-2">
          <Badge variant="secondary" className="text-xs">
            {attrs.length}
          </Badge>
          <span className="text-sm text-muted-foreground">{t("content.dragToReorder")}</span>
        </div>
        <Button type="button" size="sm" onClick={addAttr} className="gap-1.5">
          <Plus className="h-4 w-4" />
          {t("content.addField")}
        </Button>
      </div>

      {/* Sortable list */}
      <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
        <SortableContext
          items={attrs.map((a, i) => a.id || `attr-${i}`)}
          strategy={verticalListSortingStrategy}
        >
          {attrs.map((attr, index) => (
            <SortableAttrRow
              key={attr.id || `attr-${index}`}
              attr={attr}
              index={index}
              widgets={widgets}
              postTypes={postTypes}
              onAttrChange={(updated) => updateAttr(index, updated)}
              onDelete={() => deleteAttr(index)}
            />
          ))}
        </SortableContext>
      </DndContext>
    </div>
  )
}

// --- Main page ---

export default function PostTypeEditPage() {
  const { postTypeId } = useParams() as { postTypeId: string }
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { resetBreadcrumb } = useBreadcrumb()
  const form = useForm<ShPostType>()
  const [isLoading, setIsLoading] = useState(false)
  const [attrs, setAttrs] = useState<ShPostTypeAttr[]>([])
  const [widgets, setWidgets] = useState<ShWidget[]>([])
  const [postTypes, setPostTypes] = useState<ShPostType[]>([])

  useEffect(() => {
    resetBreadcrumb([
      { label: t("content.sites"), href: ROUTES.CONTENT_ROOT },
      { label: t("content.postTypes"), href: ROUTES.POST_TYPE_ROOT },
      { label: t("content.editPostType") },
    ])

    Promise.all([
      postTypeService.get(postTypeId),
      widgetService.query(),
      postTypeService.query(),
    ]).then(([data, widgetList, ptList]) => {
      form.reset(data)
      const sorted = [...(data.shPostTypeAttrs ?? [])].sort((a, b) => a.ordinal - b.ordinal)
      setAttrs(sorted)
      setWidgets(widgetList)
      setPostTypes(ptList)
    })
  }, [postTypeId, resetBreadcrumb, t, form])

  const serializeWidgetSettings = useCallback((attrList: ShPostTypeAttr[]): ShPostTypeAttr[] => {
    return attrList.map((a, i) => ({
      ...a,
      ordinal: i + 1,
      id: a.id || undefined as unknown as string,
      shPostTypeAttrs: a.shPostTypeAttrs ? serializeWidgetSettings(a.shPostTypeAttrs) : undefined,
    }))
  }, [])

  async function onSubmit(data: ShPostType) {
    setIsLoading(true)
    try {
      const payload: ShPostType = {
        ...data,
        shPostTypeAttrs: serializeWidgetSettings(attrs),
      }
      await postTypeService.update(payload)
      toast.success(t("common.save"))
    } finally {
      setIsLoading(false)
    }
  }

  async function onSubmitAndClose(data: ShPostType) {
    setIsLoading(true)
    try {
      const payload: ShPostType = {
        ...data,
        shPostTypeAttrs: serializeWidgetSettings(attrs),
      }
      await postTypeService.update(payload)
      toast.success(t("common.save"))
      navigate(ROUTES.POST_TYPE_ROOT)
    } finally {
      setIsLoading(false)
    }
  }

  const title = form.watch("title")

  return (
    <>
      <PageHeader title={t("content.editPostType")} turIcon={IconFileText} urlBase={ROUTES.CONTENT_ROOT} />
      <div className="p-6 space-y-6">
        {/* Sticky save bar */}
        <div className="sticky top-0 z-10 bg-orange-50 dark:bg-orange-950/30 border rounded-md px-4 py-3 flex items-center justify-between">
          <h3 className="text-lg font-semibold">
            {t("content.modelPostType", { title: title ?? "" })}
          </h3>
          <div className="flex gap-2">
            <Button
              type="button"
              disabled={isLoading}
              onClick={form.handleSubmit(onSubmit)}
            >
              {isLoading ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Save className="mr-2 h-4 w-4" />}
              {t("common.save")}
            </Button>
            <Button
              type="button"
              variant="outline"
              disabled={isLoading}
              onClick={form.handleSubmit(onSubmitAndClose)}
            >
              {t("content.saveAndClose")}
            </Button>
            <Button type="button" variant="ghost" onClick={() => navigate(ROUTES.POST_TYPE_ROOT)}>
              {t("common.cancel")}
            </Button>
          </div>
        </div>

        {/* Post Type details + Workflow tabs */}
        <Card>
          <Tabs defaultValue="details">
            <CardHeader>
              <TabsList>
                <TabsTrigger value="details">{t("content.details")}</TabsTrigger>
                <TabsTrigger value="workflow">{t("content.workflow")}</TabsTrigger>
              </TabsList>
            </CardHeader>
            <CardContent>
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
                    <FormField
                      control={form.control}
                      name="name"
                      rules={{ required: t("common.required") }}
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>{t("content.apiName")}</FormLabel>
                          <FormControl>
                            <Input {...field} value={field.value ?? ""} />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
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
            </CardContent>
          </Tabs>
        </Card>

        {/* Fields section */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Layers className="h-5 w-5 text-muted-foreground" />
              {t("content.fields")}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <AttrFieldList
              attrs={attrs}
              widgets={widgets}
              postTypes={postTypes}
              onChange={setAttrs}
            />
          </CardContent>
        </Card>
      </div>
    </>
  )
}
