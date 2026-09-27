/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH1020/SH1145 — the seven things a row can do, as data.
 *
 * <p>A list rather than seven buttons in the markup, because the menu takes a list and because the
 * set is stated in one place: what a row can do was previously readable only by scrolling through
 * the JSX that drew it. SH1145 took it off the page for the same reason one step up — it sat
 * between the listbox's keys and the bulk plan with nothing marking where either began.</p>
 *
 * <p>One object of handlers rather than seven parameters: the caller is a page that has all of them
 * to hand, and a positional list of seven callbacks is the shape that gets two of them swapped.</p>
 */
import { ClipboardCopy, Copy, ExternalLink, Move, Pencil, ShieldCheck, Trash2 } from "lucide-react"

import { ROUTES } from "@/app/routes.const"

import type { ObjectChild } from "./browser-listing"

export interface RowActionHandlers {
  navigate: (to: string) => void
  t: (key: string, options?: Record<string, unknown>) => string
  onClone: (id: string) => void
  onPick: (mode: "copy" | "move", ids: string[]) => void
  onPermissions: (item: ObjectChild) => void
  onViewPage: (id: string) => void
  onDelete: (item: ObjectChild) => void
}

export function rowActionsFor(h: RowActionHandlers) {
  return (item: ObjectChild) => [

    {
      label: h.t("common.edit"),
      icon: Pencil,
      onSelect: () => h.navigate(item.objectType === "FOLDER"
        ? `${ROUTES.CONTENT_ROOT}/folder/${item.id}/edit`
        : `${ROUTES.CONTENT_ROOT}/post/${item.id}`),
    },
    { label: h.t("content.clone"), icon: ClipboardCopy, onSelect: () => h.onClone(item.id) },
    { label: h.t("content.copy"), icon: Copy, onSelect: () => h.onPick("copy", [item.id]) },
    { label: h.t("content.move"), icon: Move, onSelect: () => h.onPick("move", [item.id]) },
    {
      label: h.t("acl.permissions"),
      icon: ShieldCheck,
      onSelect: () => h.onPermissions(item),
    },
    { label: h.t("content.viewPage"), icon: ExternalLink, onSelect: () => h.onViewPage(item.id) },
    {
      label: h.t("common.delete"),
      icon: Trash2,
      tone: "destructive" as const,
      onSelect: () => h.onDelete(item),
    },
  ]

}
