/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { useCallback, useMemo, useState } from "react"
import { useTranslation } from "react-i18next"
import {
  BentoDataTable, type BentoActionsMenuItem, type BentoDataTableAction, type BentoDataTableColumn,
  type BentoDataTableLayout,
} from "@viglet/viglet-design-system/bento"
import { formatDistanceToNow } from "date-fns"
import { FileText, Folder } from "lucide-react"
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
  onSelectionChange: (ids: string[]) => void
  onOpen: (row: BrowserRow) => void
}

/** When a row changed, as the list says it ("3 days ago"). A module function, so the columns hold. */
function when(row: BrowserRow): string {
  if (!row.date) return ""
  try {
    return formatDistanceToNow(new Date(row.date), { addSuffix: true })
  } catch {
    return ""
  }
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
  rows, scope, actionsFor, selectionActions, onSelectionChange, onOpen,
}: BrowserTableProps) {
  const { t } = useTranslation()
  const { user } = useCurrentUser()
  const [layout, setLayout] = useStoredLayout(tableLayoutKey(user?.username))

  const columns = useMemo<BentoDataTableColumn<BrowserRow>[]>(() => [
    {
      id: "title", header: t("common.title"), width: "minmax(16rem, 3fr)", hideable: false,
      sortValue: (row) => row.name.toLocaleLowerCase(),
      cell: (row) => (
        <span className="flex min-w-0 items-center gap-2">
          {row.objectType === "FOLDER"
            ? <Folder className="h-4 w-4 shrink-0 text-amber-500" />
            : <FileText className="h-4 w-4 shrink-0 text-blue-500" />}
          <span className="truncate font-medium">{row.name}</span>
        </span>
      ),
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
      cell: (row) => <span className="truncate text-xs text-muted-foreground">{row.publishStatus}</span>,
    },
    {
      id: "locale", header: t("content.filterLocale"), width: "6rem",
      sortValue: (row) => row.locale ?? "",
      cell: (row) => <span className="font-mono text-xs uppercase text-muted-foreground">{row.locale}</span>,
    },
    {
      id: "modified", header: t("content.filterModified"), width: "minmax(8rem, 1.5fr)",
      sortValue: (row) => (row.date ? new Date(row.date) : null),
      cell: (row) => <span className="truncate text-xs text-muted-foreground">{when(row)}</span>,
    },
  ], [t])

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
      getRowLabel={rowLabel}
      columns={columns}
      label={t("content.browserList")}
      rowActions={rowActions}
      selectionActions={selectionActions}
      onSelectionChange={onSelectionChange}
      selectionScope={scope}
      layout={layout}
      onLayoutChange={setLayout}
      onRowOpen={onOpen}
      height={560}
    />
  )
}
