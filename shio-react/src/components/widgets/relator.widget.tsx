import { useState } from "react"
import { useTranslation } from "react-i18next"
import {
  closestCenter,
  DndContext,
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
import { ChevronDown, ChevronRight, Copy, GripVertical, Plus, Trash2, Undo2 } from "lucide-react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Label } from "@/components/ui/label"
import { getWidget, type WidgetProps } from "@/components/widgets"
import type { ShPostAttr, ShRelatorItem } from "@/models/post/post"

/*
 * SH1052 — this file used to declare its own `Props` with the three fields `WidgetProps` had, and
 * that copy is why these two widgets were the only ones the new accessibility props did not reach:
 * widening the contract widened everything except the components that had restated it. The alias
 * keeps every `Props` below reading as it did, and there is now one definition.
 */
type Props = WidgetProps

/**
 * SH1012 — the one repeatable field, editable again.
 *
 * <h2>What was wrong</h2>
 *
 * <p>This widget destructured `attr` alone, so the `onChange` in its props was never called, and it
 * told the curator that editing was disabled. The documentation went on describing a nested items
 * list with add and remove. Everything underneath was complete the whole time: rows convert to and
 * from `data`, the merge verb writes them, the Markdown projection carries them — only the console
 * leg was missing, which is the rare case where the fix is one component.</p>
 *
 * <h2>Position is the ordinal</h2>
 *
 * <p>A row of an SH95 collection is a plain map of its declared child fields — `{title, body}` —
 * which is what lets `ShPostDraftMergeService` address `features.1.body`. So nothing here writes a
 * row's index into the row: order is the array's order, and `post-form-fields` strips the
 * client-side identity before saving. A second answer to "which row is this" is how the two
 * answers come to disagree.</p>
 *
 * <h2>Remove is undoable, and that is not a courtesy</h2>
 *
 * <p>A row holds as much typing as a small post and there is no per-field history to fall back on.
 * The removed row is kept until another edit replaces it, so the mistake costs a click rather than
 * a retype.</p>
 */
export function RelatorWidget({ attr, onChange, postId, labelledBy, describedBy }: Props) {
  const { t } = useTranslation()
  const items = attr.shChildrenRelatorItems ?? []
  const declared = attr.shPostTypeAttr?.shPostTypeAttrs ?? []
  const [open, setOpen] = useState<Record<string, boolean>>({})
  const [undoable, setUndoable] = useState<{ item: ShRelatorItem; at: number } | null>(null)

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 8 } }),
    // The keyboard equivalent, which is the whole reason drag is acceptable here at all: dnd-kit
    // gives a sortable list arrow-key reordering once this sensor is on, so the feature is not
    // reachable by pointer alone.
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  )

  /** Every mutation goes through here, so ordinal-is-position can never be half true. */
  const emit = (next: ShRelatorItem[]) => {
    onChange({
      ...attr,
      shChildrenRelatorItems: next.map((item, index) => ({ ...item, ordinal: index })),
    })
  }

  const blankRow = (): ShRelatorItem => {
    const id = crypto.randomUUID()
    return {
      id,
      ordinal: items.length,
      shChildrenPostAttrs: declared.map((child) => ({
        id: `${id}-${child.name}`,
        strValue: "",
        arrayValue: [],
        dateValue: "",
        intValue: 0,
        shPostTypeAttr: child,
      })),
    }
  }

  const add = () => {
    const row = blankRow()
    setOpen((was) => ({ ...was, [row.id]: true }))
    emit([...items, row])
  }

  const duplicate = (index: number) => {
    const source = items[index]
    const id = crypto.randomUUID()
    const copy: ShRelatorItem = {
      ...source,
      id,
      // Re-scoped, or the copy's inputs share the original's ids and React reuses the wrong one.
      shChildrenPostAttrs: source.shChildrenPostAttrs.map((child) => ({
        ...child,
        id: `${id}-${child.shPostTypeAttr?.name ?? ""}`,
      })),
    }
    emit([...items.slice(0, index + 1), copy, ...items.slice(index + 1)])
  }

  const remove = (index: number) => {
    setUndoable({ item: items[index], at: index })
    emit(items.filter((_, i) => i !== index))
  }

  const restore = () => {
    if (!undoable) return
    const at = Math.min(undoable.at, items.length)
    emit([...items.slice(0, at), undoable.item, ...items.slice(at)])
    setUndoable(null)
  }

  const onDragEnd = (event: DragEndEvent) => {
    const { active, over } = event
    if (!over || active.id === over.id) return
    const from = items.findIndex((item) => item.id === active.id)
    const to = items.findIndex((item) => item.id === over.id)
    if (from < 0 || to < 0) return
    emit(arrayMove(items, from, to))
  }

  const changeChild = (index: number, childIndex: number, updated: ShPostAttr) => {
    emit(items.map((item, i) => (i === index
      ? {
        ...item,
        shChildrenPostAttrs: item.shChildrenPostAttrs.map((child, c) =>
          (c === childIndex ? updated : child)),
      }
      : item)))
  }

  /** The row's own words, so a collapsed list reads as content rather than as "Item 3". */
  const headline = (item: ShRelatorItem, index: number) => {
    const titled = item.shChildrenPostAttrs.find((child) => child.shPostTypeAttr?.isTitle)
    const fallback = item.shChildrenPostAttrs.find((child) => child.strValue)
    return titled?.strValue || item.title || fallback?.strValue
      || t("content.relatorItemNumber", { number: index + 1 })
  }

  return (
    // SH1052 — a group, not a control: this widget renders several, so the field's `htmlFor` has
    // nothing single to own and the set names itself by the visible label instead.
    <div
      role="group"
      aria-labelledby={labelledBy}
      aria-describedby={describedBy}
      // SH1054 — no aria-required: a group may not carry it, and its name (the label) already says it
      className="space-y-2"
    >
      {items.length === 0 && (
        <p className="text-xs text-muted-foreground">
          {t("content.noRelatorItems", "No relator items.")}
        </p>
      )}

      <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={onDragEnd}>
        <SortableContext items={items.map((item) => item.id)} strategy={verticalListSortingStrategy}>
          {items.map((item, index) => (
            <RelatorRow
              key={item.id}
              item={item}
              index={index}
              headline={headline(item, index)}
              isOpen={open[item.id] ?? false}
              onToggle={() => setOpen((was) => ({ ...was, [item.id]: !(was[item.id] ?? false) }))}
              onDuplicate={() => duplicate(index)}
              onRemove={() => remove(index)}
              onChildChange={(childIndex, updated) => changeChild(index, childIndex, updated)}
              postId={postId}
            />
          ))}
        </SortableContext>
      </DndContext>

      <div className="flex items-center gap-2">
        <Button type="button" variant="outline" size="sm" onClick={add}>
          <Plus className="h-3.5 w-3.5 mr-1" />
          {t("content.relatorAdd")}
        </Button>
        {undoable && (
          <Button type="button" variant="ghost" size="sm" onClick={restore}>
            <Undo2 className="h-3.5 w-3.5 mr-1" />
            {t("content.relatorUndoRemove")}
          </Button>
        )}
      </div>
    </div>
  )
}

interface RowProps {
  item: ShRelatorItem
  index: number
  headline: string
  isOpen: boolean
  onToggle: () => void
  onDuplicate: () => void
  onRemove: () => void
  onChildChange: (childIndex: number, updated: ShPostAttr) => void
  postId?: string
}

function RelatorRow({
  item, index, headline, isOpen, onToggle, onDuplicate, onRemove, onChildChange, postId,
}: Readonly<RowProps>) {
  const { t } = useTranslation()
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } =
    useSortable({ id: item.id })

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
  }

  return (
    <div ref={setNodeRef} style={style} className="rounded-md border bg-muted/30">
      <div className="flex items-center gap-2 px-2 py-1.5">
        <button
          type="button"
          className="cursor-grab touch-none text-muted-foreground hover:text-foreground"
          aria-label={t("content.relatorReorder")}
          {...attributes}
          {...listeners}
        >
          <GripVertical className="h-4 w-4" />
        </button>
        <Badge variant="secondary" className="h-5 px-1.5 text-[10px] shrink-0">#{index + 1}</Badge>
        <button
          type="button"
          onClick={onToggle}
          aria-expanded={isOpen ? "true" : "false"}
          className="flex-1 min-w-0 flex items-center gap-1 text-left"
        >
          {isOpen ? <ChevronDown className="h-3.5 w-3.5 shrink-0" />
            : <ChevronRight className="h-3.5 w-3.5 shrink-0" />}
          <span className="truncate text-sm font-medium">{headline}</span>
        </button>
        <Button type="button" variant="ghost" size="icon" className="h-7 w-7"
          onClick={onDuplicate} aria-label={t("content.relatorDuplicate")}>
          <Copy className="h-3.5 w-3.5" />
        </Button>
        <Button type="button" variant="ghost" size="icon" className="h-7 w-7"
          onClick={onRemove} aria-label={t("content.relatorRemove")}>
          <Trash2 className="h-3.5 w-3.5" />
        </Button>
      </div>

      {isOpen && (
        <div className="border-t px-3 py-3 space-y-3">
          {item.shChildrenPostAttrs.map((child, childIndex) => {
            // The child's own widget, by the name the model declares — the same lookup the form
            // does one level up, so a Relator row is not a second, poorer kind of form.
            const ChildWidget = getWidget(child.shPostTypeAttr?.shWidget?.name ?? "Text")
            return (
              <div key={child.id} className="space-y-1.5">
                <Label className="text-xs font-semibold">
                  {child.shPostTypeAttr?.label || child.shPostTypeAttr?.name}
                  {child.shPostTypeAttr?.required ? <span className="text-destructive"> *</span> : null}
                </Label>
                <ChildWidget
                  attr={child}
                  onChange={(updated) => onChildChange(childIndex, updated)}
                  postId={postId}
                />
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
