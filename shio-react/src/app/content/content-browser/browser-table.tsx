/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import { useTranslation } from "react-i18next"
import {
  BentoDataTable, type BentoActionsMenuItem, type BentoDataTableAction, type BentoDataTableColumn,
  type BentoDataTableLayout,
} from "@viglet/viglet-design-system/bento"
import { relativeDate } from "@/lib/relative-date"
import { TitleCell } from "./title-cell"
import { useCurrentUser } from "@/contexts/user.context"
import { readTableLayout, rowLabel, tableLayoutKey, type BrowserRow } from "./browser-view"

/**
 * SH1026 — the browser's table view, drawn by the design system's data table (VDS138).
 *
 * <p>It brings what a dense row could not: only the visible window of rows is mounted, a header
 * sorts by what it says, and a column picker hides what a curator does not need. It holds its own
 * selection and keyboard, so the page learns the selection through `onSelectionChange` and its bulk
 * verbs act on that; a selection holds within `scope`, the folder and its query, so a filter change
 * never leaves a verb pointed at rows it has hidden.</p>
 *
 * <p>The row actions are the page's own, by name. The table takes one list for every row, and the
 * browser's eight are the same for every row, so each is looked up on the row it is chosen for.</p>
 */
export interface BrowserTableProps {
  rows: readonly BrowserRow[]
  scope: string
  actionsFor: (row: BrowserRow) => BentoActionsMenuItem[]
  /**
   * The page's bulk verbs. The table makes rows selectable only when it is given some, and these
   * are the toolbar's own, which act on the selection `onSelectionChange` has just reported.
   */
  selectionActions: readonly BentoDataTableAction<BrowserRow>[]
  /**
   * SH1148 — the page's selection, handed IN (VDS174), so the table ticks exactly these rows and the
   * toolbar's Select menu and the selection bar's Clear write the one set the table shows.
   */
  selectedIds: readonly string[]
  onSelectionChange: (ids: string[]) => void
  onOpen: (row: BrowserRow) => void
  /** After an inline edit saved: the page reads its rows again, so the listing shows the new title. */
  onEdited: () => void
}

/** When a row changed, as the list says it ("há 3 dias"). A module function, so the columns hold. */
function when(row: BrowserRow, language: string): string {
  return relativeDate(row.date, language)
}

/**
 * SH1203 — a row's state as a dot and a word in the console's language. It was the server's own
 * `DRAFT`, upper-case English in a Portuguese table, beside a list view that said Rascunho.
 */
function StateMark({ status, t }: Readonly<{ status?: string; t: (key: string) => string }>) {
  if (!status) return null
  // SH1272 — the listing derives these from whether a live row exists, and CHANGED is a live page
  // whose draft has edits nobody has published yet: live, so green, with the label saying what waits.
  const published = status === "PUBLISHED" || status === "CHANGED"
  const label = status === "PUBLISHED" ? t("content.filterPublished")
    : status === "CHANGED" ? t("content.statusChanged")
    : status === "DRAFT" ? t("content.filterDraft") : status.toLowerCase()
  return (
    <span className="inline-flex items-center gap-1.5 truncate text-xs text-muted-foreground">
      <span aria-hidden="true" className={`size-1.5 rounded-full ${published ? "bg-emerald-500" : "bg-amber-500"}`} />
      {label}
    </span>
  )
}

/** The layout, per user, in this browser. Storage can be absent or refuse; the table still works. */
function useStoredLayout(key: string): [BentoDataTableLayout, (layout: BentoDataTableLayout) => void] {
  const [layout, setLayout] = useState<BentoDataTableLayout>(() => {
    try { return readTableLayout(localStorage.getItem(key)) } catch { return { hidden: [] } }
  })
  const store = useCallback((next: BentoDataTableLayout) => {
    setLayout(next)
    try { localStorage.setItem(key, JSON.stringify(next)) } catch { /* private mode: this session only */ }
  }, [key])
  return [layout, store]
}

export function BrowserTable({
  rows, scope, actionsFor, selectionActions, selectedIds, onSelectionChange, onOpen, onEdited,
}: BrowserTableProps) {
  const { t, i18n } = useTranslation()
  const { user } = useCurrentUser()
  const [layout, setLayout] = useStoredLayout(tableLayoutKey(user?.username))
  // The page's reload is a new function each render; the columns must not be, or the table would
  // rebuild them on every keystroke of an edit. So the columns call the latest one through a ref.
  const edited = useRef(onEdited)
  useEffect(() => { edited.current = onEdited })
  const afterEdit = useCallback(() => edited.current(), [])

  const columns = useMemo<BentoDataTableColumn<BrowserRow>[]>(() => [
    {
      id: "title", header: t("common.title"), width: "minmax(16rem, 3fr)", hideable: false,
      sortValue: (row) => row.name.toLocaleLowerCase(),
      // SH1026 — the title edits in place, under the version its own read answered with.
      cell: (row) => <TitleCell row={row} onSaved={afterEdit} />,
    },
    {
      id: "type", header: t("content.postType"), width: "minmax(8rem, 1.5fr)",
      sortValue: (row) => (row.objectType === "FOLDER" ? "" : row.postTypeName ?? ""),
      cell: (row) => (
        <span className="truncate text-muted-foreground">
          {row.objectType === "FOLDER" ? t("content.folder") : row.postTypeName}
        </span>
      ),
    },
    {
      id: "state", header: t("content.filterState"), width: "8rem",
      sortValue: (row) => row.publishStatus ?? "",
      cell: (row) => <StateMark status={row.publishStatus} t={t} />,
    },
    {
      id: "locale", header: t("content.filterLocale"), width: "6rem",
      sortValue: (row) => row.locale ?? "",
      cell: (row) => <span className="font-mono text-xs uppercase text-muted-foreground">{row.locale}</span>,
    },
    {
      id: "modified", header: t("content.filterModified"), width: "minmax(8rem, 1.5fr)",
      sortValue: (row) => (row.date ? new Date(row.date) : null),
      cell: (row) => <span className="truncate text-xs text-muted-foreground">{when(row, i18n.language)}</span>,
    },
  ], [t, i18n.language, afterEdit])

  /** The page's actions, one entry per name, each resolved against the row it is chosen for. */
  const rowActions = useMemo<BentoDataTableAction<BrowserRow>[]>(() => {
    const template = rows[0] ? actionsFor(rows[0]) : []
    return template.map((action, index) => ({
      id: action.id ?? `action-${index}`,
      label: action.label,
      icon: action.icon,
      tone: action.tone,
      onSelect: (chosen) => { if (chosen[0]) actionsFor(chosen[0])[index]?.onSelect() },
    }))
  }, [rows, actionsFor])

  return (
    <BentoDataTable
      rows={rows}
      getRowId={(row) => row.id}
      // SH1125 — the same marker the card and list views put on a row, so the spreadsheet view names
      // a translation the way the other two do rather than being the one place it is missing.
      getRowLabel={(row) => rowLabel(row, t("content.localeDefault"))}
      columns={columns}
      label={t("content.browserList")}
      rowActions={rowActions}
      selectionActions={selectionActions}
      selectedIds={selectedIds}
      onSelectionChange={onSelectionChange}
      selectionScope={scope}
      layout={layout}
      onLayoutChange={setLayout}
      onRowOpen={onOpen}
      // SH1223 — no `height`: the table is as tall as its rows and scrolls with the page, its header
      // row held under the shell's. A 560 px cap scrolled a folder of thousands inside a box on a
      // screen with room for twice as many rows, with a second scrollbar in the busiest screen. While
      // rows are selected the selection bar is held there too, above the header (VDS209, SH1239).
    />
  )
}
