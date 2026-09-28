/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { Folder, FolderOpen } from "lucide-react"
import type { ReactNode } from "react"
import { useTranslation } from "react-i18next"
import { Link } from "react-router-dom"
import { ROUTES } from "@/app/routes.const"
import type { BreadcrumbPathItem } from "./browser-listing"

/**
 * SH1203 — where this folder sits, beside what it holds.
 *
 * <p>The listing showed one folder's contents and nothing of the structure around it, so going up a
 * level or across to a sibling meant the trail, then the parent's listing, then scrolling to the
 * folder. This is the page's aside (BENTO-AUTHORING §1: inside the column, scrolling with it): the
 * ancestors down to this folder, and this folder's own subfolders under it. It makes no read of its
 * own. The ancestors are the `/path` answer the hero's trail already draws, and the subfolders are
 * the listing's first page, so the tree costs the page nothing and can never disagree with it.</p>
 */
export function BrowserTree({ path, folders }: Readonly<{
  path: readonly BreadcrumbPathItem[]
  folders: readonly { id: string; name: string }[]
}>) {
  const { t } = useTranslation()
  if (path.length === 0) return null
  const here = path.length - 1
  const to = (id: string) => `${ROUTES.CONTENT_ROOT}/object/${id}`
  return (
    <nav aria-label={t("content.folderTree")} className="w-56 shrink-0 max-lg:hidden">
      <ul className="space-y-0.5 text-sm">
        {path.map((item, depth) => (
          <TreeRow key={item.id} depth={depth} current={depth === here}>
            <Link to={to(item.id)} aria-current={depth === here ? "page" : "false"}
              className="flex min-w-0 items-center gap-2 rounded-md px-2 py-1 hover:bg-muted">
              {depth === here
                ? <FolderOpen aria-hidden="true" className="size-4 shrink-0 text-amber-500" />
                : <Folder aria-hidden="true" className="size-4 shrink-0 text-muted-foreground" />}
              <span className="truncate">{item.name}</span>
            </Link>
          </TreeRow>
        ))}
        {folders.map((folder) => (
          <TreeRow key={folder.id} depth={path.length} current={false}>
            <Link to={to(folder.id)} className="flex min-w-0 items-center gap-2 rounded-md px-2 py-1 text-muted-foreground hover:bg-muted hover:text-foreground">
              <Folder aria-hidden="true" className="size-4 shrink-0" />
              <span className="truncate">{folder.name}</span>
            </Link>
          </TreeRow>
        ))}
      </ul>
    </nav>
  )
}

function TreeRow({ depth, current, children }: Readonly<{ depth: number; current: boolean; children: ReactNode }>) {
  return (
    <li style={{ paddingLeft: `${Math.min(depth, 6) * 0.75}rem` }} className={current ? "font-medium text-foreground" : undefined}>
      {children}
    </li>
  )
}
