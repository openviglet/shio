/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { useQueries, useQuery } from "@tanstack/react-query"
import { ArrowDown, ArrowUp, Plus, X } from "lucide-react"
import { useState } from "react"
import { useTranslation } from "react-i18next"
import { Link } from "react-router-dom"
import { ROUTES } from "@/app/routes.const"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { addressKey } from "@/lib/query-keys"
import { ShAgentFeedService } from "@/services/agent-feed/agent-feed.service"
import { ShSearchService } from "@/services/search/search.service"
import { friendlyUrlOf, moved, siteOf } from "@/lib/section-urls"

const resolver = new ShAgentFeedService()
const search = new ShSearchService()

/**
 * SH1205 — a page's sections by name, in order.
 *
 * <p>A Page's Sections field stores the sections' friendly URLs in render order, and the multi-select
 * showed exactly that: chips reading `/sections/b59589bb5`, which says nothing about which section is
 * the hero and which the FAQ, wraps so the order does not read as an order, and reorders only by
 * removing and re-adding. This draws the same value as an ordered list: each row the section's title
 * and type, resolved from its address through `/agent/resolve`, the address in mono beside it, and
 * up, down and remove. A section is added by searching its title in the page's site. The stored value
 * is unchanged, a list of friendly URLs, so the renderer and the agent surface see no difference.</p>
 *
 * <p>SH1323 — a resolved section's title opens its editor. A page's headline lives in its hero
 * section, and the list named that section without a way into it, so the curator searched for it.</p>
 */
export function SectionList({ postId, values, onChange, fieldId, describedBy }: Readonly<{
  postId: string
  values: readonly string[]
  onChange: (next: string[]) => void
  fieldId?: string
  describedBy?: string
}>) {
  const { t } = useTranslation()
  const [term, setTerm] = useState("")
  const self = useQuery({
    queryKey: addressKey(`summary:resolve|id:${postId}`),
    queryFn: ({ signal }) => resolver.locate(`id:${postId}`, { signal }),
  })
  const site = siteOf(self.data?.address)
  const resolved = useQueries({
    queries: values.map((value) => ({
      queryKey: addressKey(`summary:resolve|post:${site ?? ""}${value}`),
      queryFn: ({ signal }: { signal: AbortSignal }) => resolver.locate(`post:${site}${value}`, { signal }),
      enabled: Boolean(site),
      retry: false,
    })),
  })
  const found = useQuery({
    queryKey: addressKey(`summary:sectionSearch|${site ?? ""}|${term.trim()}`),
    queryFn: ({ signal }) => search.search({ q: term.trim(), site: site ?? "" }, 8, { signal }),
    enabled: Boolean(site) && term.trim().length >= 2,
  })
  const suggestions = (found.data?.results ?? [])
    .map((row) => ({ row, url: site ? friendlyUrlOf(row.address, site) : null }))
    .filter((entry): entry is { row: typeof entry.row; url: string } => entry.url !== null && !values.includes(entry.url))

  return (
    <div className="space-y-2">
      {values.length > 0 && (
        <ol className="divide-y rounded-md border">
          {values.map((value, index) => {
            const hit = resolved[index]?.data
            return (
              <li key={value} className="flex items-center gap-3 px-3 py-2 text-sm">
                <span className="w-5 shrink-0 text-right font-mono text-xs text-muted-foreground">{index + 1}</span>
                <span className="min-w-0 flex-1">
                  {hit ? (
                    <Link to={`${ROUTES.CONSOLE}/content/post/${hit.id}`}
                      className="block truncate font-medium underline-offset-4 hover:underline">
                      {hit.title ?? value}
                    </Link>
                  ) : (
                    <span className="block truncate font-medium">
                      {resolved[index]?.isError ? t("content.sectionMissing") : value}
                    </span>
                  )}
                  <span className="block truncate font-mono text-xs text-muted-foreground">
                    {hit ? `${hit.type ?? ""} · ${value}` : value}
                  </span>
                </span>
                <Button type="button" size="icon-sm" variant="ghost" disabled={index === 0}
                  aria-label={t("content.sectionMoveUp", { name: hit?.title ?? value })}
                  onClick={() => onChange(moved(values, index, -1))}>
                  <ArrowUp className="size-4" />
                </Button>
                <Button type="button" size="icon-sm" variant="ghost" disabled={index === values.length - 1}
                  aria-label={t("content.sectionMoveDown", { name: hit?.title ?? value })}
                  onClick={() => onChange(moved(values, index, 1))}>
                  <ArrowDown className="size-4" />
                </Button>
                <Button type="button" size="icon-sm" variant="ghost"
                  aria-label={t("content.removeValue", { value: hit?.title ?? value })}
                  onClick={() => onChange(values.filter((each) => each !== value))}>
                  <X className="size-4" />
                </Button>
              </li>
            )
          })}
        </ol>
      )}
      <div className="relative">
        <Input
          id={fieldId}
          aria-describedby={describedBy}
          value={term}
          onChange={(event) => setTerm(event.target.value)}
          onKeyDown={(event) => {
            // A friendly URL typed whole is still accepted, as the multi-select took it.
            if (event.key === "Enter" && term.trim().startsWith("/") && !values.includes(term.trim())) {
              event.preventDefault()
              onChange([...values, term.trim()])
              setTerm("")
            }
          }}
          placeholder={t("content.sectionSearch")}
          className="text-sm"
        />
        {suggestions.length > 0 && (
          <ul className="absolute z-20 mt-1 w-full overflow-hidden rounded-md border bg-popover shadow-md">
            {suggestions.map(({ row, url }) => (
              <li key={url}>
                <button type="button" className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm hover:bg-muted"
                  onClick={() => { onChange([...values, url]); setTerm("") }}>
                  <Plus className="size-4 shrink-0 text-muted-foreground" />
                  <span className="min-w-0 flex-1 truncate">{row.title ?? url}</span>
                  <span className="shrink-0 font-mono text-xs text-muted-foreground">{row.type}</span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}
