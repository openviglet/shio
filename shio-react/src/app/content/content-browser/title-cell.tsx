/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { useState, type KeyboardEvent } from "react"
import { useTranslation } from "react-i18next"
import { toast } from "@viglet/viglet-design-system"
import { Check, FileText, Folder, Pencil, X } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { reportProblem } from "@/lib/problem"
import { ShPostUnifiedService } from "@/services/post-unified/post-unified.service"
import { editableTitleField, type BrowserRow } from "./browser-view"

const postService = new ShPostUnifiedService()

/** What an edit in progress holds: the field it writes and the value being typed. */
type Editing = { field: string; value: string; original: string }

/**
 * SH1026 — a table row's title, editable in place and guarded by the post's version.
 *
 * <p>The listing carries no field names and no versions, so an edit starts with a read of the draft.
 * That read is what makes it safe: the service keeps the ETag it answered with, the input opens on
 * the value as it is <em>now</em> rather than as the listing drew it, and the save is the merge verb
 * (SH335) with that ETag as `If-Match` (SH985). Somebody writing the page in between (a curator in
 * the editor, or an agent's run) turns the save into a 409 whose problem says to read again, never
 * into a silent overwrite.</p>
 */
export function TitleCell({ row, onSaved }: { row: BrowserRow; onSaved: () => void }) {
  const { t } = useTranslation()
  const [editing, setEditing] = useState<Editing | null>(null)
  const [busy, setBusy] = useState(false)

  /** Read the draft for its title field and its version. A type that cannot be edited here says so. */
  const start = async () => {
    setBusy(true)
    try {
      const post = await postService.get(row.id, "DRAFT")
      const field = editableTitleField(post.shPostType?.fields)
      if (!field) {
        toast.info(t("content.titleNotInline"))
        return
      }
      const current = typeof post.data?.[field] === "string" ? post.data[field] as string : ""
      setEditing({ field, value: current, original: current })
    } catch (error) {
      reportProblem(error, t("common.error"))
    } finally {
      setBusy(false)
    }
  }

  /** The merge, conditional on the version the read answered with. */
  const commit = async () => {
    if (!editing) return
    if (editing.value === editing.original) {
      setEditing(null)
      return
    }
    setBusy(true)
    try {
      await postService.patch(row.id, { [editing.field]: editing.value })
      toast.success(t("content.titleSaved"))
      setEditing(null)
      onSaved()
    } catch (error) {
      // A 409 is somebody else's write since the edit opened; its problem says to read again.
      postService.forgetVersion(row.id)
      reportProblem(error, t("common.error"))
    } finally {
      setBusy(false)
    }
  }

  const onKey = (event: KeyboardEvent<HTMLInputElement>) => {
    // The table owns Enter and Escape for its rows; inside the editor they belong to the edit.
    event.stopPropagation()
    if (event.key === "Enter") void commit()
    if (event.key === "Escape") setEditing(null)
  }

  const icon = row.objectType === "FOLDER"
    ? <Folder className="h-4 w-4 shrink-0 text-amber-500" />
    : <FileText className="h-4 w-4 shrink-0 text-blue-500" />

  if (editing) {
    return (
      // eslint-disable-next-line jsx-a11y/click-events-have-key-events, jsx-a11y/no-static-element-interactions -- only stops the row's click while renaming (SH1054)
      <span className="flex min-w-0 items-center gap-1" onClick={(event) => event.stopPropagation()}>
        {icon}
        {/* eslint-disable-next-line jsx-a11y/no-autofocus -- the curator just asked to rename; focus belongs in the field (SH1054) */}
        <Input autoFocus value={editing.value} disabled={busy} className="h-(--vg-control-dense)"
          aria-label={t("content.titleEdit", { title: row.name })}
          onChange={(event) => setEditing({ ...editing, value: event.target.value })}
          onKeyDown={onKey} />
        <Button variant="ghost" size="icon-sm" disabled={busy}
          aria-label={t("common.save")} onClick={() => void commit()}>
          <Check className="size-3.5" />
        </Button>
        <Button variant="ghost" size="icon-sm" disabled={busy}
          aria-label={t("common.cancel")} onClick={() => setEditing(null)}>
          <X className="size-3.5" />
        </Button>
      </span>
    )
  }
  return (
    <span className="group/title flex min-w-0 items-center gap-2">
      {icon}
      <span className="truncate font-medium">{row.name}</span>
      {row.objectType === "POST" && (
        <Button variant="ghost" size="icon-sm" className="shrink-0 text-muted-foreground"
          disabled={busy} aria-label={t("content.titleEdit", { title: row.name })}
          onClick={(event) => { event.stopPropagation(); void start() }}>
          <Pencil className="size-3" />
        </Button>
      )}
    </span>
  )
}
