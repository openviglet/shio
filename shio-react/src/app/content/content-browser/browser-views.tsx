/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import type { ReactNode } from "react"
import { useTranslation } from "react-i18next"
import { BentoActionsMenu, type BentoActionsMenuItem } from "@viglet/viglet-design-system/bento"
import { FileText, Folder } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Checkbox } from "@/components/ui/checkbox"
import { rowLabel, type BrowserRow, type BrowserView } from "./browser-view"

/**
 * SH1026 — the content browser's three views, one renderer each.
 *
 * <p>The toolbar offered list, grid and table, and the choice changed the colour of the button that
 * made it: the render below was one unconditional map. Each view is now its own renderer over the
 * same row, inside the same listbox, so selection, the roving focus, Shift-extend and the row
 * actions (SH1020) are one implementation that every view gets rather than three that drift.</p>
 */

/** The columns a table row and its header share, so the two cannot disagree. */
const TABLE_COLUMNS = "grid grid-cols-[1.25rem_minmax(0,3fr)_minmax(0,1.5fr)_7rem_5rem_minmax(0,1.5fr)_2rem] items-center gap-3"

export interface BrowserItemProps {
  view: BrowserView
  item: BrowserRow
  index: number
  selected: boolean
  /** When it was changed, already worded ("3 days ago"). */
  when: string
  siteName?: string
  actions: BentoActionsMenuItem[]
  onToggle: () => void
  onOpen: () => void
}

/** The table's header row, outside the listbox's options. */
export function BrowserTableHeader() {
  const { t } = useTranslation()
  return (
    <div className={`${TABLE_COLUMNS} border-b bg-muted/40 px-4 py-2 text-xs font-medium text-muted-foreground`}
      aria-hidden="true">
      <span />
      <span>{t("common.title")}</span>
      <span>{t("content.postType")}</span>
      <span>{t("content.filterState")}</span>
      <span>{t("content.filterLocale")}</span>
      <span>{t("content.filterModified")}</span>
      <span />
    </div>
  )
}

/** One row's content, in the shape its view draws. The option around it is the page's. */
export function BrowserItem(props: BrowserItemProps) {
  if (props.view === "grid") return <GridCard {...props} />
  if (props.view === "spreadsheet") return <TableRow {...props} />
  return <ListRow {...props} />
}

function KindIcon({ item, className }: { item: BrowserRow; className: string }) {
  return item.objectType === "FOLDER"
    ? <Folder className={`${className} text-amber-500`} />
    : <FileText className={`${className} text-blue-500`} />
}

function Select({ item, selected, onToggle, className }:
  Pick<BrowserItemProps, "item" | "selected" | "onToggle"> & { className: string }) {
  return (
    <Checkbox className={className} checked={selected} onCheckedChange={onToggle}
      aria-label={`Select ${rowLabel(item)}`} />
  )
}

function Title({ item, onOpen, className }: Pick<BrowserItemProps, "item" | "onOpen"> & { className: string }) {
  return (
    <button type="button" onClick={onOpen} aria-label={rowLabel(item)}
      className={`text-left font-medium text-blue-600 dark:text-blue-400 hover:underline transition-colors truncate block ${className}`}>
      {item.name}
    </button>
  )
}

/** The actions menu, whose clicks must not reach the option's Shift-extend. */
function Actions({ item, actions }: Pick<BrowserItemProps, "item" | "actions">) {
  const { t } = useTranslation()
  return (
    <div className="shrink-0" onClick={(event) => event.stopPropagation()}>
      <BentoActionsMenu triggerLabel={t("content.rowActions", { name: rowLabel(item) })} actions={actions} />
    </div>
  )
}

function Chip({ className, title, children }: { className: string; title?: string; children: ReactNode }) {
  return <Badge variant="secondary" className={`text-[10px] px-1.5 py-0 ${className}`} title={title}>{children}</Badge>
}

function Badges({ item, siteName }: Pick<BrowserItemProps, "item" | "siteName">) {
  const { t } = useTranslation()
  return (
    <div className="flex flex-wrap gap-1 mt-1.5">
      {item.objectType === "POST" && (
        <Chip className="bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400">{t("content.postLabel")}</Chip>
      )}
      <Chip className="bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400">
        {item.objectType === "FOLDER" ? t("content.folder") : item.postTypeName || t("content.postLabel")}
      </Chip>
      {/* SH1059 — beside the type, which is the badge two translations share. */}
      {item.locale && (
        <Chip className="font-mono uppercase bg-violet-100 text-violet-700 dark:bg-violet-900/30 dark:text-violet-400"
          title={t("content.localeChip", { locale: item.locale })}>
          {item.locale}
        </Chip>
      )}
      {siteName && (
        <Chip className="bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400">{siteName}</Chip>
      )}
      {item.publishStatus && (
        <Chip className="bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400">{item.publishStatus}</Chip>
      )}
    </div>
  )
}

function ListRow({ item, index, selected, when, siteName, actions, onToggle, onOpen }: BrowserItemProps) {
  return (
    <div className="flex items-start gap-3 px-4 py-3">
      {/* Position badge */}
      <span className={`mt-0.5 inline-flex items-center justify-center h-6 w-6 rounded-full text-xs font-medium text-white shrink-0 ${item.objectType === "FOLDER" ? "bg-orange-400" : "bg-blue-400"}`}>
        {index + 1}
      </span>
      <Select item={item} selected={selected} onToggle={onToggle} className="mt-1 shrink-0" />
      <KindIcon item={item} className="h-4 w-4 shrink-0 mt-1" />
      <div className="flex-1 min-w-0">
        <Title item={item} onOpen={onOpen} className="" />
        <div className="text-xs text-muted-foreground mt-0.5">
          {when}
          {item.objectType === "POST" && item.postTypeName && ` - ${item.postTypeName}`}
        </div>
        <Badges item={item} siteName={siteName} />
      </div>
      <Actions item={item} actions={actions} />
    </div>
  )
}

/**
 * A card: the same row, read at a glance. The thumbnail from the image pipeline is SH1026's next
 * part; until then the kind's icon stands in the space it will take, tinted by kind as the list's
 * position badge is.
 */
function GridCard({ item, selected, when, siteName, actions, onToggle, onOpen }: BrowserItemProps) {
  return (
    <div className="flex h-full flex-col rounded-lg border bg-card">
      <div className={`flex h-24 items-center justify-center rounded-t-lg ${item.objectType === "FOLDER" ? "bg-[var(--vg-accent-surface)]" : "bg-blue-50 dark:bg-blue-950/20"}`}>
        <KindIcon item={item} className="h-8 w-8" />
      </div>
      <div className="flex flex-1 flex-col gap-1 p-3">
        <div className="flex items-start gap-2">
          <Select item={item} selected={selected} onToggle={onToggle} className="mt-1 shrink-0" />
          <div className="min-w-0 flex-1">
            <Title item={item} onOpen={onOpen} className="w-full" />
            <div className="text-xs text-muted-foreground mt-0.5">{when}</div>
          </div>
          <Actions item={item} actions={actions} />
        </div>
        <Badges item={item} siteName={siteName} />
      </div>
    </div>
  )
}

/** A dense row: one line per item, its fields in the header's columns. */
function TableRow({ item, selected, when, actions, onToggle, onOpen }: BrowserItemProps) {
  const { t } = useTranslation()
  return (
    <div className={`${TABLE_COLUMNS} px-4 py-1.5 text-sm`}>
      <Select item={item} selected={selected} onToggle={onToggle} className="shrink-0" />
      <span className="flex min-w-0 items-center gap-2">
        <KindIcon item={item} className="h-4 w-4 shrink-0" />
        <Title item={item} onOpen={onOpen} className="min-w-0" />
      </span>
      <span className="truncate text-muted-foreground">
        {item.objectType === "FOLDER" ? t("content.folder") : item.postTypeName}
      </span>
      <span className="truncate text-xs text-muted-foreground">{item.publishStatus}</span>
      <span className="font-mono text-xs uppercase text-muted-foreground">{item.locale}</span>
      <span className="truncate text-xs text-muted-foreground">{when}</span>
      <Actions item={item} actions={actions} />
    </div>
  )
}
