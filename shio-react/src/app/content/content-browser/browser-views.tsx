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
import { rowLabel, thumbnailSrc, type BrowserRow, type BrowserView } from "./browser-view"

/**
 * SH1026 — the content browser's list and grid views, one renderer each.
 *
 * <p>The toolbar offered list, grid and table, and the choice changed the colour of the button that
 * made it: the render below was one unconditional map. The list and the grid are renderers over the
 * same row inside the page's one listbox, so selection, the roving focus, Shift-extend and the row
 * actions (SH1020) are one implementation both get. The table is the design system's data table
 * (`browser-table.tsx`), which brings its own keyboard, selection and virtualised body.</p>
 */

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

/** One row's content, in the shape its view draws. The option around it is the page's. */
export function BrowserItem(props: BrowserItemProps) {
  if (props.view === "grid") return <GridCard {...props} />
  return <ListRow {...props} />
}

function KindIcon({ item, className }: { item: BrowserRow; className: string }) {
  return item.objectType === "FOLDER"
    ? <Folder className={`${className} text-amber-500`} />
    : <FileText className={`${className} text-blue-500`} />
}

function Select({ item, selected, onToggle, className }:
  Pick<BrowserItemProps, "item" | "selected" | "onToggle"> & { className: string }) {
  // SH1125 — the word comes from i18n rather than from a prop: it is the same word on every row, so
  // threading it through three components would be passing a constant down to say one thing.
  // `rowLabel` marks only a row whose `isDefaultLocale` is true, so handing it over always is right.
  const { t } = useTranslation()
  return (
    <Checkbox className={className} checked={selected} onCheckedChange={onToggle}
      aria-label={`Select ${rowLabel(item, t("content.localeDefault"))}`} />
  )
}

function Title({ item, onOpen, className }: Pick<BrowserItemProps, "item" | "onOpen"> & { className: string }) {
  const { t } = useTranslation()
  return (
    <button type="button" onClick={onOpen} aria-label={rowLabel(item, t("content.localeDefault"))}
      className={`text-left font-medium text-blue-600 dark:text-blue-400 hover:underline transition-colors truncate block ${className}`}>
      {item.name}
    </button>
  )
}

/** The actions menu, whose clicks must not reach the option's Shift-extend. */
function Actions({ item, actions }: Pick<BrowserItemProps, "item" | "actions">) {
  const { t } = useTranslation()
  return (
    // eslint-disable-next-line jsx-a11y/click-events-have-key-events, jsx-a11y/no-static-element-interactions -- only stops the row's click; the menu trigger inside is the control (SH1054)
    <div className="shrink-0" onClick={(event) => event.stopPropagation()}>
      <BentoActionsMenu
        triggerLabel={t("content.rowActions", { name: rowLabel(item, t("content.localeDefault")) })}
        actions={actions} />
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
      {/*
        SH1059 — beside the type, which is the badge two translations share.
        SH1125 — and which of them is the original, when the site has declared one. A folder holding a
        page in English and in Portuguese said which row was which and not which one came first, which
        is the question a curator deleting one of them is actually asking.
      */}
      {item.locale && (
        <Chip className={`font-mono uppercase ${item.isDefaultLocale
          ? "bg-violet-200 text-violet-800 dark:bg-violet-900/60 dark:text-violet-200"
          : "bg-violet-100 text-violet-700 dark:bg-violet-900/30 dark:text-violet-400"}`}
          title={item.isDefaultLocale
            ? t("content.localeChipDefault", { locale: item.locale })
            : t("content.localeChip", { locale: item.locale })}>
          {item.locale}
          {item.isDefaultLocale && (
            <span className="ml-1 normal-case font-sans">{t("content.localeDefault")}</span>
          )}
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
 * A card: the same row, read at a glance. SH1026 — its image is the one the listing named (the
 * post's own, or the first it references), through the transform pipeline; with none, the kind's
 * icon holds the same space, tinted by kind as the list's position badge is. Decorative: the title
 * below names the row, so the image carries an empty alt.
 */
function GridCard({ item, selected, when, siteName, actions, onToggle, onOpen }: BrowserItemProps) {
  return (
    <div className="flex h-full flex-col rounded-lg border bg-card">
      {item.thumbnail ? (
        <img src={thumbnailSrc(item.thumbnail)} alt="" loading="lazy"
          className="h-24 w-full rounded-t-lg object-cover" />
      ) : (
        <div className={`flex h-24 items-center justify-center rounded-t-lg ${item.objectType === "FOLDER" ? "bg-[var(--vg-accent-surface)]" : "bg-blue-50 dark:bg-blue-950/20"}`}>
          <KindIcon item={item} className="h-8 w-8" />
        </div>
      )}
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
