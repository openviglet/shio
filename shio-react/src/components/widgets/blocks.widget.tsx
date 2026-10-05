import { useCallback, useMemo, useRef, useState } from "react"
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
import { ChevronDown, ChevronUp, GripVertical, Plus, Trash2, X } from "lucide-react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import {
  BLOCK_TYPES,
  blankBlock,
  blocksFromPastedHtml,
  MARK_TYPES,
  withMark,
  withoutMark,
  type ShBlock,
  type ShBlockType,
} from "@/lib/blocks"
import type { WidgetProps } from "./index"

/*
 * SH1052 — this file used to declare its own `Props` with the three fields `WidgetProps` had, and
 * that copy is why these two widgets were the only ones the new accessibility props did not reach:
 * widening the contract widened everything except the components that had restated it. The alias
 * keeps every `Props` below reading as it did, and there is now one definition.
 */
type Props = WidgetProps

/**
 * SH1017 — an editor for a block body.
 *
 * <h2>Why this is not a contentEditable</h2>
 *
 * <p>The widget it replaces called {@code document.execCommand} for bold, italic, lists, link and
 * code, asked for a URL with {@code prompt()}, and had no paste handling — so a paste from a word
 * processor arrived as spans. Every one of those is a symptom of editing <em>markup</em> in a
 * browser's own editor and then trying to get a document back out.</p>
 *
 * <p>SH1015 made the document a list of typed blocks whose inline emphasis is a range over the
 * block's own text. A range is exactly what a plain textarea already reports —
 * {@code selectionStart} and {@code selectionEnd} — so marks are applied by reading the selection
 * rather than by asking the browser to mutate a DOM and then parsing what it did. The editor is
 * therefore ordinary form controls, which is also what makes every gesture reachable by keyboard
 * without arranging for it.</p>
 *
 * <p>The cost is named: emphasis is not shown bold while typing. It is shown as a removable chip
 * under the field with the text it covers, and the page's live preview (SH1014) renders the real
 * thing. That is a worse WYSIWYG and a better document, and the document is what a front end,
 * an agent and a diff all read.</p>
 */
export function BlocksWidget({ attr, onChange, labelledBy, describedBy }: Props) {
  const { t } = useTranslation()
  const blocks = useMemo(
    () => (attr.blocksValue ?? []) as ShBlock[],
    [attr.blocksValue],
  )
  const [announcement, setAnnouncement] = useState("")

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 8 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  )

  const emit = useCallback((next: ShBlock[]) => {
    onChange({ ...attr, blocksValue: next })
  }, [attr, onChange])

  const insert = (type: ShBlockType, at: number) => {
    const block = blankBlock(type)
    emit([...blocks.slice(0, at), block, ...blocks.slice(at)])
    setAnnouncement(t("blocks.announceInserted", { type, position: at + 1 }))
  }

  const replace = (index: number, block: ShBlock) => {
    emit(blocks.map((current, i) => (i === index ? block : current)))
  }

  const remove = (index: number) => {
    emit(blocks.filter((_, i) => i !== index))
    setAnnouncement(t("blocks.announceRemoved", { position: index + 1 }))
  }

  const move = (from: number, to: number) => {
    if (to < 0 || to >= blocks.length) return
    emit(arrayMove(blocks, from, to))
    // Announced, because a move with no visual anchor is invisible to a screen reader — and the
    // keyboard path is the one a screen-reader user takes, so it is the path that must speak.
    setAnnouncement(t("blocks.announceMoved", { from: from + 1, to: to + 1 }))
  }

  /** One block becomes several, which is what a pasted document is. */
  const splitAt = (index: number, pasted: ShBlock[]) => {
    emit([...blocks.slice(0, index), ...pasted, ...blocks.slice(index + 1)])
    setAnnouncement(t("blocks.announcePasted", { count: pasted.length }))
  }

  const onDragEnd = (event: DragEndEvent) => {
    const { active, over } = event
    if (!over || active.id === over.id) return
    const from = blocks.findIndex((block) => block.key === active.id)
    const to = blocks.findIndex((block) => block.key === over.id)
    if (from >= 0 && to >= 0) move(from, to)
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
      {/* Announcements for moves, inserts and removals — polite, so typing is not interrupted. */}
      <output aria-live="polite" className="sr-only">{announcement}</output>

      {blocks.length === 0 && (
        <p className="text-xs text-muted-foreground">{t("blocks.empty")}</p>
      )}

      <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={onDragEnd}>
        <SortableContext items={blocks.map((block) => block.key)}
          strategy={verticalListSortingStrategy}>
          {blocks.map((block, index) => (
            <BlockRow
              key={block.key}
              block={block}
              index={index}
              total={blocks.length}
              onChange={(next) => replace(index, next)}
              onRemove={() => remove(index)}
              onMove={(delta) => move(index, index + delta)}
              onInsertAfter={(type) => insert(type, index + 1)}
              onSplit={(pasted) => splitAt(index, pasted)}
            />
          ))}
        </SortableContext>
      </DndContext>

      <InsertMenu label={t("blocks.add")} onPick={(type) => insert(type, blocks.length)} />
    </div>
  )
}

interface RowProps {
  block: ShBlock
  index: number
  total: number
  onChange: (block: ShBlock) => void
  onRemove: () => void
  onMove: (delta: number) => void
  onInsertAfter: (type: ShBlockType) => void
  /** Replaces this block with the several a paste produced. */
  onSplit: (blocks: ShBlock[]) => void
}

function BlockRow({ block, index, total, onChange, onRemove, onMove, onInsertAfter, onSplit }: Readonly<RowProps>) {
  const { t } = useTranslation()
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } =
    useSortable({ id: block.key })

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
  }

  return (
    <section
      ref={setNodeRef}
      style={style}
      // The region says what it is and where it is, so a screen reader reading the form hears
      // "Block 3 of 7, heading" rather than a stack of unlabelled text boxes.
      aria-label={t("blocks.regionLabel", { position: index + 1, total, type: block.type })}
      className="rounded-md border bg-muted/20"
    >
      <div className="flex items-center gap-1 px-2 py-1.5">
        <button
          type="button"
          className="cursor-grab touch-none text-muted-foreground hover:text-foreground"
          aria-label={t("blocks.reorder")}
          {...attributes}
          {...listeners}
        >
          <GripVertical className="h-4 w-4" />
        </button>
        <Badge variant="secondary" className="h-5 px-1.5 text-[10px]">{block.type}</Badge>
        <div className="ml-auto flex items-center gap-0.5">
          {/* A keyboard path for every mouse gesture: drag has these two as its equivalent. */}
          <Button type="button" variant="ghost" size="icon-sm"
            disabled={index === 0}
            aria-label={t("blocks.moveUp")} onClick={() => onMove(-1)}>
            <ChevronUp className="h-3.5 w-3.5" />
          </Button>
          <Button type="button" variant="ghost" size="icon-sm"
            disabled={index === total - 1}
            aria-label={t("blocks.moveDown")} onClick={() => onMove(1)}>
            <ChevronDown className="h-3.5 w-3.5" />
          </Button>
          <Button type="button" variant="ghost" size="icon-sm"
            aria-label={t("blocks.remove")} onClick={onRemove}>
            <Trash2 className="h-3.5 w-3.5" />
          </Button>
        </div>
      </div>

      <div className="border-t px-3 py-2 space-y-2">
        <BlockFields block={block} onChange={onChange} onSplit={onSplit} />
      </div>

      <div className="border-t px-2 py-1">
        <InsertMenu label={t("blocks.addAfter")} onPick={onInsertAfter} compact />
      </div>
    </section>
  )
}

/** The fields one block kind carries, which is the shape `ShBlocks` declares on the server. */
function BlockFields({ block, onChange, onSplit }: Readonly<{
  block: ShBlock
  onChange: (block: ShBlock) => void
  onSplit: (blocks: ShBlock[]) => void
}>) {
  const { t } = useTranslation()

  switch (block.type) {
    case "heading":
      return (
        <>
          <div className="flex items-center gap-2">
            <Label className="text-xs">{t("blocks.level")}</Label>
            <Input type="number" min={1} max={6} className="w-16 h-(--vg-control-dense)"
              value={block.level ?? 2}
              onChange={(event) => onChange({ ...block, level: Number(event.target.value) || 1 })} />
          </div>
          <MarkableText block={block} onChange={onChange} onSplit={onSplit} rows={1} />
        </>
      )
    case "paragraph":
    case "quote":
      return <MarkableText block={block} onChange={onChange} onSplit={onSplit} rows={3} />
    case "code":
      return (
        <>
          <Input className="h-(--vg-control-dense)" placeholder={t("blocks.language")}
            value={block.language ?? ""}
            onChange={(event) => onChange({ ...block, language: event.target.value })} />
          <Textarea rows={4} className="font-mono text-xs" value={block.text ?? ""}
            onChange={(event) => onChange({ ...block, text: event.target.value })} />
        </>
      )
    case "list":
      return <ListFields block={block} onChange={onChange} />
    case "image":
      return (
        <>
          <Input className="h-(--vg-control-dense)" placeholder={t("blocks.src")} value={block.src ?? ""}
            onChange={(event) => onChange({ ...block, src: event.target.value })} />
          <Input className="h-(--vg-control-dense)" placeholder={t("blocks.alt")} value={block.alt ?? ""}
            onChange={(event) => onChange({ ...block, alt: event.target.value })} />
          <p className="text-[10px] text-muted-foreground">{t("blocks.altHint")}</p>
        </>
      )
    case "embed":
      return (
        <Input className="h-(--vg-control-dense)" placeholder={t("blocks.url")} value={block.url ?? ""}
          onChange={(event) => onChange({ ...block, url: event.target.value })} />
      )
    case "reference":
      return (
        <Input className="h-(--vg-control-dense)" placeholder={t("blocks.address")} value={block.address ?? ""}
          onChange={(event) => onChange({ ...block, address: event.target.value })} />
      )
    default:
      return null
  }
}

function ListFields({ block, onChange }: Readonly<{
  block: ShBlock
  onChange: (block: ShBlock) => void
}>) {
  const { t } = useTranslation()
  const items = block.items ?? []
  return (
    <div className="space-y-1.5">
      <label className="flex items-center gap-2 text-xs">
        <input type="checkbox" checked={Boolean(block.ordered)}
          onChange={(event) => onChange({ ...block, ordered: event.target.checked })} />
        {t("blocks.ordered")}
      </label>
      {items.map((item, index) => (
        <div key={`${index}-${item}`} className="flex items-center gap-1">
          <Input className="h-(--vg-control-dense)" value={item}
            onChange={(event) => onChange({
              ...block,
              items: items.map((current, i) => (i === index ? event.target.value : current)),
            })} />
          <Button type="button" variant="ghost" size="icon-sm"
            aria-label={t("blocks.removeItem")}
            onClick={() => onChange({ ...block, items: items.filter((_, i) => i !== index) })}>
            <X className="h-3.5 w-3.5" />
          </Button>
        </div>
      ))}
      <Button type="button" variant="outline" size="sm"
        onClick={() => onChange({ ...block, items: [...items, ""] })}>
        <Plus className="h-3.5 w-3.5 mr-1" />{t("blocks.addItem")}
      </Button>
    </div>
  )
}

/**
 * A textarea whose selection becomes a mark.
 *
 * <p>`selectionStart`/`selectionEnd` are the same `{from, to}` the model stores, so applying
 * emphasis is reading two numbers — no DOM mutation, nothing to parse back, and the offsets are
 * exact by construction rather than by a round trip through markup.</p>
 */
function MarkableText({ block, onChange, onSplit, rows }: Readonly<{
  block: ShBlock
  onChange: (block: ShBlock) => void
  onSplit: (blocks: ShBlock[]) => void
  rows: number
}>) {
  const { t } = useTranslation()
  const area = useRef<HTMLTextAreaElement>(null)
  const [href, setHref] = useState("")
  const [linking, setLinking] = useState(false)

  const apply = (type: string) => {
    const element = area.current
    if (!element) return
    const from = element.selectionStart
    const to = element.selectionEnd
    if (to <= from) return
    if (type === "link") {
      setLinking(true)
      return
    }
    onChange(withMark(block, { from, to, type }))
  }

  const applyLink = () => {
    const element = area.current
    if (element && element.selectionEnd > element.selectionStart) {
      onChange(withMark(block, {
        from: element.selectionStart, to: element.selectionEnd, type: "link", href,
      }))
    }
    setLinking(false)
    setHref("")
  }

  return (
    <div className="space-y-1.5">
      <div className="flex flex-wrap items-center gap-1">
        {MARK_TYPES.map((type) => (
          <Button key={type} type="button" variant="outline" size="sm" className="px-2 text-xs"
            onClick={() => apply(type)}>
            {t(`blocks.mark.${type}`)}
          </Button>
        ))}
      </div>

      {/*
        An inline form, never `prompt()`. A prompt cannot be styled, cannot be cancelled without
        losing the selection, and is blocked outright in some browsers — the widget this replaces
        used one for every link a curator ever made.
      */}
      {linking && (
        <div className="flex items-center gap-1">
          <Input className="h-(--vg-control-dense)" placeholder={t("blocks.href")} value={href}
            onChange={(event) => setHref(event.target.value)} />
          <Button type="button" size="sm" onClick={applyLink}>{t("blocks.linkApply")}</Button>
          <Button type="button" size="sm" variant="ghost"
            onClick={() => setLinking(false)}>{t("common.cancel")}</Button>
        </div>
      )}

      <Textarea
        ref={area}
        rows={rows}
        value={block.text ?? ""}
        onChange={(event) => onChange({ ...block, text: event.target.value })}
        onPaste={(event) => {
          // Clipboard HTML becomes blocks rather than being flattened into this one: a paste from a
          // word processor is a document, and the widget this replaces turned it into spans.
          // Only when it yields more than one block — pasting a sentence into a paragraph is a
          // paste, not a document, and hijacking that would be worse than doing nothing.
          const html = event.clipboardData.getData("text/html")
          const converted = html ? blocksFromPastedHtml(html) : []
          if (converted.length > 1) {
            event.preventDefault()
            onSplit(converted)
          }
        }}
      />

      {(block.marks ?? []).length > 0 && (
        <div className="flex flex-wrap gap-1">
          {(block.marks ?? []).map((mark, index) => (
            <Badge key={`${mark.from}-${mark.to}-${mark.type}`} variant="secondary"
              className="text-[10px] gap-1">
              {t(`blocks.mark.${mark.type}`)}: {(block.text ?? "").slice(mark.from, mark.to)}
              <button type="button" aria-label={t("blocks.removeMark")}
                onClick={() => onChange(withoutMark(block, index))}>
                <X className="h-3 w-3" />
              </button>
            </Badge>
          ))}
        </div>
      )}
    </div>
  )
}

/** The slash menu, as a list of buttons: every insert is one keystroke away from the keyboard. */
function InsertMenu({ label, onPick, compact }: Readonly<{
  label: string
  onPick: (type: ShBlockType) => void
  compact?: boolean
}>) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)

  if (!open) {
    return (
      <Button type="button" variant="outline" size="sm" className={compact ? "text-xs" : ""}
        onClick={() => setOpen(true)}>
        <Plus className="h-3.5 w-3.5 mr-1" />{label}
      </Button>
    )
  }
  return (
    <div className="flex flex-wrap gap-1 rounded-md border p-1.5">
      {BLOCK_TYPES.map((type) => (
        <Button key={type} type="button" variant="ghost" size="sm" className="px-2 text-xs"
          onClick={() => {
            onPick(type)
            setOpen(false)
          }}>
          {t(`blocks.type.${type}`)}
        </Button>
      ))}
      <Button type="button" variant="ghost" size="sm" className="px-2 text-xs"
        onClick={() => setOpen(false)}>{t("common.cancel")}</Button>
    </div>
  )
}
