/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { IconFolder } from "@tabler/icons-react"
import { BentoEntityShell } from "@viglet/viglet-design-system/bento"
import { useQuery } from "@tanstack/react-query"
import { isCancelled } from "@/lib/read-options"
import { reportProblem } from "@/lib/problem"
import { addressKey } from "@/lib/query-keys"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router-dom"
import { ROUTES } from "@/app/routes.const"
import type { ShFolder } from "@/models/folder/folder"
import { ShFolderService } from "@/services/folder/folder.service"

const service = new ShFolderService()

/**
 * SH900 — a folder is nothing but its identity, and the shell has a mode for that.
 *
 * `ShFolder` carries `name` and `description` and nothing else a curator edits. Both are hero
 * fields under the authoring contract's first rule, so the form body is **empty** — and an empty
 * form body is exactly the shape that produced SH899's dead Save button: the shell renders
 * `<GradientButton type="submit" form={formId}>` and there is no form for it to submit.
 *
 * `autosaveOnly` is the answer the package already had. Its own line is
 * `showSaveSubmit = !(autosaveOnly && !isNew)` — on an existing entity the submit button is not
 * rendered at all, because the hero's inline edits save on blur through `onUpdate` and there is
 * nothing else to save. A save bar with nothing behind it is worse than no save bar, and this is
 * the flag that says so.
 */
export default function BentoFolderEditPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { folderId = "" } = useParams()
  const [folder, setFolder] = useState<ShFolder | null>(null)

  /*
   * SH1116 — one read per folder, cancelled when the id changes or the screen unmounts, and a
   * failure that is reported. The read had no failure path at all: a folder that would not load left
   * whatever the previous one had put on screen, under a breadcrumb naming the new one.
   */
  const loaded = useQuery<ShFolder>({
    queryKey: addressKey(`id:${folderId ?? ""}`),
    queryFn: ({ signal }) => service.get(folderId, { signal }),
    enabled: Boolean(folderId),
  })

  // Set during render rather than in an effect (`react-hooks/set-state-in-effect`), once per folder.
  const [seeded, setSeeded] = useState<string | null>(null)
  if (loaded.data && seeded !== folderId) {
    setSeeded(folderId)
    setFolder(loaded.data)
  }

  useEffect(() => {
    if (loaded.error && !isCancelled(loaded.error)) reportProblem(loaded.error, t("common.error"))
  }, [loaded.error, t])

  if (!folder) return null

  // The folder is edited from its parent's listing, so that listing is the way back: the parent
  // folder, or the site itself for a top-level folder. Only a root folder carries `shSite`.
  const parentId = folder.parentFolder?.id ?? folder.shSite?.id
  const listRoute = parentId ? `${ROUTES.BENTO_CONTENT}/object/${parentId}` : ROUTES.BENTO_CONTENT

  return (
    <BentoEntityShell
      entity={{ ...folder, title: folder.name }}
      isNew={false}
      autosaveOnly
      headlineFallback={t("content.editFolder")}
      eyebrow={t("content.folder")}
      listRoute={listRoute}
      icon={IconFolder}
      tone="amber"
      formId="folder-edit"
      feature={t("content.folder")}
      hideIcon
      onUpdate={async (next) => {
        const merged: ShFolder = { ...folder, ...next, name: next.title ?? folder.name }
        await service.update(merged)
        setFolder(merged)
      }}
      onDelete={async () => {
        await service.delete(folderId)
        navigate(listRoute)
        return true
      }}
    >
      {() => null}
    </BentoEntityShell>
  )
}
