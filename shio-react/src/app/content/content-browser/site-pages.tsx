/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { useQuery } from "@tanstack/react-query"
import axios from "axios"
import { ChevronRight, FileText, Folder, Plus } from "lucide-react"
import { useState } from "react"
import { useTranslation } from "react-i18next"
import { Link } from "react-router-dom"
import { ROUTES } from "@/app/routes.const"
import { Button } from "@/components/ui/button"
import { addressKey } from "@/lib/query-keys"
import { localesOf, pageCount, pageTree, pageTypesOf, type PageNode, type PageRow, type PageSite } from "@/lib/site-pages"
import type { SiteRootMode } from "./use-site-root-mode"

/**
 * SH1316 — a site's pages, as a visitor walks them, beside the folders it is stored in.
 *
 * <p>A site opens here at its root; the folder browser stays one click away as the builder's view.
 * The rows are `/agent/find`'s, every post of the site, paged the way the scheduled agenda pages
 * them; the tree keeps the ones the site binds to a layout (`lib/site-pages`).</p>
 */
type FindPage = { results?: PageRow[]; truncated?: boolean; cursor?: string }

async function sitePosts(site: string, signal?: AbortSignal): Promise<PageRow[]> {
  const rows: PageRow[] = []
  let after: string | undefined
  // Bounded, so a server answering the same cursor forever is a short tree rather than a hang.
  for (let page = 0; page < 20; page++) {
    const response = await axios.get<FindPage>("/v2/agent/find", {
      signal, params: { site, ids: true, limit: 500, after },
    })
    rows.push(...(response.data.results ?? []))
    const next = response.data.cursor
    if (!response.data.truncated || !next || next === after) break
    after = next
  }
  return rows
}

/** The switch between the two, drawn only at a site's root. */
export function SiteRootSwitch({ mode, onChange }: Readonly<{
  mode: SiteRootMode; onChange: (mode: SiteRootMode) => void
}>) {
  const { t } = useTranslation()
  return (
    <div className="flex w-fit items-center rounded-md border p-0.5" role="group" aria-label={t("content.siteView")}>
      {(["pages", "folders"] as const).map((each) => (
        <Button key={each} variant={mode === each ? "secondary" : "ghost"} size="sm"
          aria-pressed={mode === each ? "true" : "false"} onClick={() => onChange(each)}>
          {each === "pages" ? <FileText className="mr-1.5 h-4 w-4" /> : <Folder className="mr-1.5 h-4 w-4" />}
          {t(each === "pages" ? "content.sitePagesView" : "content.siteFoldersView")}
        </Button>
      ))}
    </div>
  )
}

function PageRowLink({ node, depth }: Readonly<{ node: PageNode; depth: number }>) {
  const { t } = useTranslation()
  const page = node.page
  const label = page?.title || node.segment || t("content.homePage")
  const state = page?.published ? t("content.filterPublished") : t("content.filterDraft")
  return (
    <li>
      <div className="flex items-center gap-3 px-4 py-2.5" style={{ paddingLeft: `${1 + depth * 1.25}rem` }}>
        {page?.id ? (
          <Link to={`${ROUTES.CONTENT_ROOT}/post/${page.id}`} className="min-w-0 flex-1 truncate text-sm font-medium hover:underline">
            {label}
          </Link>
        ) : (
          <span className="text-muted-foreground min-w-0 flex-1 truncate text-sm">{label}</span>
        )}
        <span className="text-muted-foreground hidden shrink-0 font-mono text-xs sm:inline">{node.path}</span>
        {page && <span className="text-muted-foreground w-24 shrink-0 text-right text-xs">{state}</span>}
      </div>
      {node.children.length > 0 && (
        <ul className="divide-border/60 divide-y">
          {node.children.map((child) => <PageRowLink key={child.path} node={child} depth={depth + 1} />)}
        </ul>
      )}
    </li>
  )
}

export function SitePages({ site, folderId }: Readonly<{ site: PageSite; folderId: string }>) {
  const { t } = useTranslation()
  // A new page is the site's own page type, or the first type it binds to a layout (SH1283).
  const newType = site.pageType || [...pageTypesOf(site)][0]
  const { data: rows, isPending, error } = useQuery({
    queryKey: addressKey(`post:${site.name}/|pages`),
    // Read again on every visit, as the folder listing beside it is: saving a new page returns here,
    // and nothing on the write path drops `post:` keys, so the cache's 30 s would hide the page made.
    staleTime: 0,
    queryFn: ({ signal }) => sitePosts(site.name, signal),
  })
  const locales = localesOf(rows ?? [])
  const [chosen, setChosen] = useState<string | null>(null)
  const locale = chosen ?? locales[0]
  if (isPending) return <div className="text-muted-foreground py-12 text-center text-sm">{t("common.loading")}</div>
  if (error) return <div className="text-destructive py-12 text-center text-sm">{t("common.error")}</div>
  const tree = pageTree(rows ?? [], site, locale)
  const count = pageCount(tree)
  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-3">
        <span className="text-muted-foreground text-sm">{t("content.pageCount", { count })}</span>
        <span className="flex-1" />
        {locales.length > 1 && (
          <div className="flex items-center rounded-md border p-0.5" role="group" aria-label={t("content.filterLocale")}>
            {locales.map((each) => (
              <Button key={each} variant={each === locale ? "secondary" : "ghost"} size="sm"
                aria-pressed={each === locale ? "true" : "false"} onClick={() => setChosen(each)}>
                <span className="font-mono text-xs uppercase">{each}</span>
              </Button>
            ))}
          </div>
        )}
        {/* The screen's one filled button (SH1195): a page is what a curator makes here. */}
        {newType && (
          <Button asChild size="sm">
            <Link to={`${ROUTES.CONTENT_ROOT}/folder/${folderId}/post-type/${encodeURIComponent(newType)}/new`}>
              <Plus className="mr-1.5 h-4 w-4" /> {t("content.newItem", { type: newType })}
            </Link>
          </Button>
        )}
      </div>
      {count === 0 ? (
        <p className="text-muted-foreground py-12 text-center text-sm">{t("content.noPagesYet")}</p>
      ) : (
        <ul className="bg-card divide-border/60 divide-y overflow-hidden rounded-xl border">
          <PageRowLink node={tree} depth={0} />
        </ul>
      )}
      <p className="text-muted-foreground flex items-center gap-1 text-xs">
        <ChevronRight className="h-3 w-3" aria-hidden="true" /> {t("content.sitePagesHint")}
      </p>
    </div>
  )
}
