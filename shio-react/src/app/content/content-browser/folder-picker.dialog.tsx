/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH203/SH1145 — where a copy or a move is going, chosen by walking the site.
 *
 * <p>Seven pieces of state, four handlers and sixty lines of markup that belong to one dialog and
 * to nothing else. On the page they were interleaved with the listing's reads and the listbox's
 * keys; here the whole of the feature is one file, and it is mounted only while it is open — so it
 * starts at the site root every time rather than keeping the last walk in state nobody reset.</p>
 */
import axios from "axios"
import { useCallback, useEffect, useState } from "react"
import { Folder } from "lucide-react"
import { toast } from "@viglet/viglet-design-system"

import { isCancelled } from "@/lib/read-options"

import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"

import type { ObjectListResponse } from "./browser-listing"

/** What the page asks for when it opens the picker. */
export interface FolderPickerRequest {
  mode: "copy" | "move"
  /** The rows being copied or moved. */
  ids: string[]
  /**
   * SH203 — the folders in this run that cannot be their own destination.
   *
   * Only folder ids: a post has no subtree, so it can go anywhere. The dialog navigates strictly
   * *down* from the site root, so "is this row inside a folder being moved" needs nothing from the
   * server — a row is unreachable exactly when it is one of these, and disabling it means the walk
   * can never get inside one. Hence no ancestor check: there is no way to arrive below a blocked
   * row in the first place.
   */
  blockedIds: Set<string>
}

export interface FolderPickerProps {
  request: FolderPickerRequest
  /** Where the walk starts: the site root, or the folder itself when there is no site. */
  startId: string
  /** The folder being browsed, which a move may not choose as its own destination. */
  objectId: string
  t: (key: string, options?: Record<string, unknown>) => string
  onClose: () => void
  /** The copy or move went through; the page re-reads and clears its selection. */
  onDone: () => void | Promise<void>
}

export function FolderPickerDialog({
  request, startId, objectId, t, onClose, onDone,
}: FolderPickerProps) {
  const mode = request.mode
  const [folders, setFolders] = useState<Array<{ id: string; name: string }>>([])
  const [currentId, setCurrentId] = useState<string | null>(startId)
  const [path, setPath] = useState<Array<{ id: string; name: string }>>([])

  /**
   * The move/copy picker's folder list. It asks for `size=1` because it renders no posts and the
   * folders come back whole — one row of content instead of fifty, for a dialog that walks a site
   * folder by folder.
   *
   * SH1025 — it asks for no folder page, which is what keeps that true. Folder paging is opt-in
   * on the server for this caller: a dialog handed one page of a site would let the walk stop
   * above a destination that exists, and there is no "load more" in it to notice with.
   */
  const fetchFolders = useCallback((id: string, signal?: AbortSignal) =>
    axios.get<ObjectListResponse>(`/v2/object/${id}/list?size=1`, { signal }).then((res) => {
      setFolders((res.data.folders ?? []).map((f) => ({ id: f.id, name: f.name })))
    }).catch((reason: unknown) => { if (!isCancelled(reason)) throw reason }), [])

  /*
   * The walk starts at the root each time the dialog is opened, because it is mounted each time.
   *
   * SH1047 — cancelled on cleanup, and this one is not theoretical: the rows below are clickable
   * while their own read is in flight, so a curator walking down two folders quickly can have the
   * first answer land after the second and repaint the list with the folder they left.
   */
  useEffect(() => {
    const abort = new AbortController()
    void fetchFolders(startId, abort.signal)
    return () => abort.abort()
  }, [fetchFolders, startId])

  const navigateTo = (folderId: string, folderName: string) => {
    setCurrentId(folderId)
    setPath(prev => [...prev, { id: folderId, name: folderName }])
    fetchFolders(folderId)
  }

  const goBackTo = (index: number) => {
    const targetPath = path.slice(0, index)
    const targetId = index === 0 ? startId : targetPath.at(-1)!.id
    setCurrentId(targetId)
    setPath(targetPath)
    fetchFolders(targetId)
  }

  /**
   * Why the current folder cannot be the destination, or null when it can (SH203).
   *
   * The refusals stay on the server — this only stops the dialog from *offering* them.
   * A picker that lists choices the server will reject teaches the curator that the
   * operation fails at random; the same division SH108's confirm flow uses, where the CLI
   * prints the deletions and the server still demands the token.
   */
  const blockedReason = (() => {
    if (!currentId) return null
    if (request.blockedIds.has(currentId)) return t("content.moveInsideItself")
    if (mode === "move" && currentId === objectId) {
      return t("content.moveSameFolder")
    }
    return null
  })()

  const confirm = async () => {
    if (!currentId || blockedReason) return
    const endpoint = mode === "copy" ? "copyto" : "moveto"
    try {
      await axios.put(`/v2/object/${endpoint}/${currentId}`, request.ids)
    } catch (error) {
      // SH139: a move now refuses rather than re-slugging a colliding URL, so this
      // dialog has a failure to show. It used to have none, and an unhandled
      // rejection left the dialog open with no explanation.
      const problem = axios.isAxiosError(error) ? error.response?.data : null
      toast.error(problem?.detail || problem?.title || t("content.moveError"))
      return
    }
    toast.success(mode === "copy" ? t("content.copiedSuccess") : t("content.movedSuccess"))
    await onDone()
  }


  return (
    <Dialog open onOpenChange={(next) => !next && onClose()}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>{mode === "copy" ? t("content.copyTo") : t("content.moveTo")}</DialogTitle>
        </DialogHeader>
        {/* Breadcrumb inside dialog */}
        <div className="flex items-center gap-1 text-sm text-muted-foreground flex-wrap">
          <button type="button" className="hover:text-foreground hover:underline" onClick={() => goBackTo(0)}>
            {t("content.root")}
          </button>
          {path.map((item, i) => (
            <span key={item.id} className="flex items-center gap-1">
              <span>/</span>
              <button type="button" className="hover:text-foreground hover:underline" onClick={() => goBackTo(i + 1)}>
                {item.name}
              </button>
            </span>
          ))}
        </div>
        {/* Folder list */}
        <div className="max-h-60 overflow-y-auto border rounded-md divide-y">
          {folders.length === 0 && (
            <div className="px-4 py-6 text-center text-sm text-muted-foreground">{t("content.noSubfolders")}</div>
          )}
          {folders.map(folder => {
            // SH203 — a folder being moved or copied is shown and disabled rather than
            // hidden: a curator looking for "Docs" in the tree needs to see that it is
            // the thing they are dragging, not conclude the picker has lost it.
            const blocked = request.blockedIds.has(folder.id)
            return (
              <button
                key={folder.id}
                type="button"
                disabled={blocked}
                title={blocked ? t("content.moveInsideItself") : undefined}
                className="w-full flex items-center gap-2 px-3 py-2 text-sm transition-colors text-left enabled:hover:bg-muted/50 disabled:opacity-50 disabled:cursor-not-allowed"
                onClick={() => navigateTo(folder.id, folder.name)}
              >
                <Folder className="h-4 w-4 text-amber-500 shrink-0" />
                <span className="truncate">{folder.name}</span>
                {blocked && (
                  <span className="ml-auto text-xs text-muted-foreground shrink-0">
                    {mode === "copy" ? t("content.beingCopied") : t("content.beingMoved")}
                  </span>
                )}
              </button>
            )
          })}
        </div>
        {blockedReason && (
          <p className="text-xs text-muted-foreground">{blockedReason}</p>
        )}
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="outline" size="sm" onClick={() => onClose()}>{t("common.cancel")}</Button>
          <Button size="sm" onClick={confirm} disabled={!!blockedReason}>
            {mode === "copy" ? t("content.copyHere") : t("content.moveHere")}
          </Button>
        </div>
      </DialogContent>
    </Dialog>

  )
}
