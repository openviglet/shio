import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Separator } from "@/components/ui/separator"
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { ChevronDown, ChevronRight, GripVertical, Plus, Trash2 } from "lucide-react"
import { useMemo, useState } from "react"
import { useTranslation } from "react-i18next"
import type { WidgetProps } from "./index"
import type { ShPostAttr, ShRelatorItem } from "@/models/post/post"
import { PostFormFields } from "./post-form-fields"

/** Build empty ShPostAttr[] from the relator's post type attr definitions */
function buildEmptyAttrs(
  postTypeAttrs: NonNullable<ShPostAttr["shPostTypeAttr"]["shPostTypeAttrs"]>
): ShPostAttr[] {
  return postTypeAttrs
    .slice()
    .sort((a, b) => a.ordinal - b.ordinal)
    .map((pta) => ({
      id: "",
      strValue: "",
      arrayValue: [],
      dateValue: "",
      intValue: 0,
      shPostTypeAttr: {
        id: pta.id,
        name: pta.name,
        label: pta.label,
        description: pta.description,
        ordinal: pta.ordinal,
        required: pta.required,
        isSummary: pta.isSummary,
        isTitle: pta.isTitle,
        shWidget: pta.shWidget,
        widgetSettings: pta.widgetSettings,
      },
      shChildrenRelatorItems: [],
      shChildrenPostAttrs: [],
    }))
}

/**
 * Ensure each relator item has shChildrenPostAttrs populated.
 * If empty, fill from the post type definition.
 */
function ensureItemAttrs(
  items: ShRelatorItem[],
  postTypeAttrs: NonNullable<ShPostAttr["shPostTypeAttr"]["shPostTypeAttrs"]> | undefined
): ShRelatorItem[] {
  if (!postTypeAttrs || postTypeAttrs.length === 0) return items
  return items.map((item) => {
    if (item.shChildrenPostAttrs && item.shChildrenPostAttrs.length > 0) return item
    return { ...item, shChildrenPostAttrs: buildEmptyAttrs(postTypeAttrs) }
  })
}

function getItemTitle(item: ShRelatorItem, index: number): string {
  if (item.title) return item.title

  const attrs = item.shChildrenPostAttrs
  if (!attrs || attrs.length === 0) return `Item ${index + 1}`

  const titleAttr = attrs.find((a) => a.shPostTypeAttr?.isTitle === 1 && a.strValue)
  if (titleAttr?.strValue) return titleAttr.strValue

  const firstWithValue = attrs.find((a) => a.strValue)
  if (firstWithValue?.strValue) return firstWithValue.strValue

  const firstLabel = attrs.find((a) => a.shPostTypeAttr?.label)
  if (firstLabel?.shPostTypeAttr?.label) return firstLabel.shPostTypeAttr.label

  return `Item ${index + 1}`
}

function getItemSummary(item: ShRelatorItem): string | undefined {
  const summaryAttr = item.shChildrenPostAttrs?.find(
    (a) => a.shPostTypeAttr?.isSummary === 1 && a.strValue
  )
  return summaryAttr?.strValue || item.summary || undefined
}

export function RelatorWidget({ attr, onChange, postId }: WidgetProps) {
  const { t } = useTranslation()
  const [editingIndex, setEditingIndex] = useState<number | null>(null)
  const [expandedIndex, setExpandedIndex] = useState<number | null>(null)

  // Post type sub-field definitions for this relator
  const subPostTypeAttrs = attr.shPostTypeAttr?.shPostTypeAttrs

  // Ensure all items have their attrs populated from the post type definition
  const items = useMemo(
    () => ensureItemAttrs(attr.shChildrenRelatorItems || [], subPostTypeAttrs),
    [attr.shChildrenRelatorItems, subPostTypeAttrs]
  )

  const updateItems = (next: ShRelatorItem[]) => {
    onChange({ ...attr, shChildrenRelatorItems: next })
  }

  const handleAttrChange = (itemIndex: number, attrIndex: number, updatedAttr: ShPostAttr) => {
    const item = items[itemIndex]
    const updatedAttrs = item.shChildrenPostAttrs.map((a, i) =>
      i === attrIndex ? updatedAttr : a
    )
    const next = items.map((it, i) =>
      i === itemIndex ? { ...it, shChildrenPostAttrs: updatedAttrs } : it
    )
    updateItems(next)
  }

  const removeItem = (index: number) => {
    const next = items.filter((_, i) => i !== index).map((item, i) => ({ ...item, ordinal: i }))
    updateItems(next)
  }

  const addItem = () => {
    const emptyAttrs = subPostTypeAttrs
      ? buildEmptyAttrs(subPostTypeAttrs)
      : items[0]?.shChildrenPostAttrs?.map((a) => ({
          ...a,
          id: "",
          strValue: "",
          arrayValue: [],
          dateValue: "",
          intValue: 0,
          shChildrenRelatorItems: [],
          shChildrenPostAttrs: [],
        })) ?? []

    const newItem: ShRelatorItem = {
      id: crypto.randomUUID(),
      ordinal: items.length,
      shChildrenPostAttrs: emptyAttrs,
    }
    updateItems([...items, newItem])

    if (emptyAttrs.length > 0) {
      setExpandedIndex(items.length)
    }
  }

  const toggleExpand = (index: number) => {
    setExpandedIndex(expandedIndex === index ? null : index)
  }

  const fieldCount = (item: ShRelatorItem) =>
    item.shChildrenPostAttrs?.filter((a) => a.shPostTypeAttr?.shWidget?.name !== "Hidden").length ?? 0

  const editingItem = editingIndex !== null ? items[editingIndex] : null

  return (
    <div className="space-y-2">
      {items.length === 0 && (
        <div className="text-center py-6 text-muted-foreground text-sm">
          {t("content.noRelatorItems")}
        </div>
      )}

      {items.map((item, index) => {
        const title = getItemTitle(item, index)
        const summary = getItemSummary(item)
        const isExpanded = expandedIndex === index
        const hasAttrs = (item.shChildrenPostAttrs?.length ?? 0) > 0

        return (
          <div
            key={item.id || index}
            className={`border rounded-lg bg-background overflow-hidden transition-shadow ${
              isExpanded ? "ring-1 ring-primary/20 shadow-sm" : ""
            }`}
          >
            {/* Header row */}
            <button
              type="button"
              className="w-full flex items-center gap-2 px-3 py-2.5 hover:bg-muted/50 transition-colors text-left"
              onClick={() => toggleExpand(index)}
            >
              <GripVertical className="h-4 w-4 text-muted-foreground/50 shrink-0" />

              <Badge variant="outline" className="shrink-0 text-xs px-1.5 py-0 h-5">
                {index + 1}
              </Badge>

              <div className="text-muted-foreground shrink-0">
                {isExpanded
                  ? <ChevronDown className="h-4 w-4" />
                  : <ChevronRight className="h-4 w-4" />}
              </div>

              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium truncate">{title}</p>
                {summary && (
                  <p className="text-xs text-muted-foreground truncate">{summary}</p>
                )}
              </div>

              {hasAttrs && (
                <Badge variant="secondary" className="text-[10px] px-1.5 py-0 h-4 shrink-0">
                  {fieldCount(item)}
                </Badge>
              )}

              <div
                className="shrink-0"
                onClick={(e) => e.stopPropagation()}
              >
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  className="h-7 w-7 text-muted-foreground hover:text-destructive"
                  onClick={() => removeItem(index)}
                >
                  <Trash2 className="h-3.5 w-3.5" />
                </Button>
              </div>
            </button>

            {/* Expanded inline form */}
            {isExpanded && hasAttrs && (
              <div className="border-t px-4 py-4">
                <PostFormFields
                  attrs={item.shChildrenPostAttrs}
                  onAttrChange={(attrIndex, updatedAttr) =>
                    handleAttrChange(index, attrIndex, updatedAttr)
                  }
                  postId={postId}
                />
              </div>
            )}
          </div>
        )
      })}

      <Button type="button" variant="outline" size="sm" onClick={addItem} className="gap-1.5">
        <Plus className="h-4 w-4" />
        {t("content.addRelatorItem")}
      </Button>

      {/* Full-screen dialog editing (alternative) */}
      {editingItem && editingIndex !== null && (
        <Dialog open={true} onOpenChange={() => setEditingIndex(null)}>
          <DialogContent className="sm:max-w-2xl max-h-[80vh] flex flex-col">
            <DialogHeader>
              <DialogTitle>
                {getItemTitle(editingItem, editingIndex)}
              </DialogTitle>
            </DialogHeader>
            <Separator />
            <div className="flex-1 overflow-y-auto py-2">
              <PostFormFields
                attrs={editingItem.shChildrenPostAttrs}
                onAttrChange={(attrIndex, updatedAttr) =>
                  handleAttrChange(editingIndex, attrIndex, updatedAttr)
                }
                postId={postId}
              />
            </div>
            <DialogFooter>
              <Button type="button" onClick={() => setEditingIndex(null)}>
                {t("common.save")}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      )}
    </div>
  )
}
