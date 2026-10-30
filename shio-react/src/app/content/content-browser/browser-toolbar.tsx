/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH1145 — the browser's controls: what a curator can start, narrow, and act on.
 *
 * <p>Markup and no state. Every value it renders from is settled by the page — the list state owns
 * the filters and the view (SH1023), the verbs are the page's, and the facet options are computed
 * from what the listing returned. Thirteen props rather than a context, because thirteen named
 * inputs are a contract a reviewer can check and a context is a lookup they have to go and find.</p>
 *
 * <p>SH1307 — one row, beside the page's filter bar. It was two bands of its own (an action row and
 * a framed strip holding only the view toggles) under a framed counts strip, so a folder of six read
 * as a toolbar. Now the views and the filters are icons, New folder, Import, Order and Select share
 * one overflow, and New is the one filled button, at the end where the eye finishes the row.</p>
 */
import {
  ChevronDown, Copy, Download, Eye, FileText, Folder, FolderPlus, LayoutGrid, List, MoreHorizontal, Move,
  Plus, Search, Sheet, SlidersHorizontal, Trash2, Upload,
} from "lucide-react"
import { useState } from "react"
import { useLocation } from "react-router-dom"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuRadioGroup, DropdownMenuRadioItem,
  DropdownMenuSeparator, DropdownMenuSub, DropdownMenuSubContent, DropdownMenuSubTrigger, DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Checkbox } from "@/components/ui/checkbox"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { ROUTES } from "@/app/routes.const"
import type { ShPostType } from "@/models/post-type/post-type"
import type { useListState } from "@/hooks/use-list-state"

import { MORE_IGNORES, type BROWSER_LIST } from "./browser-listing"
import { lastPostTypeKey, searchBackState, type BrowserView } from "./browser-view"

export interface BrowserToolbarProps {
  objectId: string
  /** The list state: the filters, the sort and the view, all of which live in the URL (SH1023). */
  list: ReturnType<typeof useListState<keyof typeof BROWSER_LIST & string>>
  sortBy: string
  viewMode: BrowserView
  /** The type a "New …" button offers, derived from the catalogue and the last one used. */
  lastPostType: ShPostType | null
  /** SH1027 — where "search this folder" goes, or null before the breadcrumb has landed. */
  searchHereHref: string | null
  selectedItems: Set<string>
  /** The Select menu (SH1148: in every view, since the table is controlled by the page's set). */
  onSelectAction: (action: string) => void
  onBatchAction: (action: string) => void
  onPlanBulk: (op: "PUBLISH" | "UNPUBLISH") => void
  onPick: (mode: "copy" | "move", ids: string[]) => void
  navigate: (to: string, options?: { state?: unknown }) => void
  t: (key: string, options?: Record<string, unknown>) => string
}

export function BrowserToolbar({
  objectId, list, sortBy, viewMode, lastPostType, searchHereHref,
  selectedItems, onSelectAction, onBatchAction, onPlanBulk, onPick, navigate, t,
}: BrowserToolbarProps) {
  const [filtersOpen, setFiltersOpen] = useState(false)
  const location = useLocation()
  const view = (mode: BrowserView) => () => list.set("view", mode)
  return (
      <div className="flex shrink-0 flex-wrap items-center gap-0.5">
        {/*
          SH1022 — the filters, which are the server's words and not a second set computed here.
          A curator asking "drafts changed this week by Ana" is asking the same question an agent
          asks with `shio find --state draft --modified-since ... --modifier ana`, and it is the
          same query: a client-side filter over a paged listing would narrow one page.
        */}
        <Popover open={filtersOpen} onOpenChange={setFiltersOpen}>
          <PopoverTrigger asChild>
            <Button variant="ghost" size="sm" data-testid="sh-filters" aria-label={t("content.moreFilters")}>
              <SlidersHorizontal className="h-4 w-4" />
              {list.narrowed(MORE_IGNORES) > 0 && (
                <Badge variant="secondary" className="ml-1 text-[10px]">{list.narrowed(MORE_IGNORES)}</Badge>
              )}
            </Button>
          </PopoverTrigger>
          <PopoverContent align="end" className="w-80 space-y-3">
            <div className="grid grid-cols-2 gap-2">
              <div className="space-y-1">
                <Label className="text-xs" htmlFor="sh-filter-author">{t("content.filterAuthor")}</Label>
                <Input id="sh-filter-author" className="h-(--vg-control-dense) text-xs"
                  value={list.draft.author}
                  onChange={(event) => list.set("author", event.target.value)} />
              </div>
              <div className="space-y-1">
                <Label className="text-xs" htmlFor="sh-filter-modifier">{t("content.filterModifier")}</Label>
                <Input id="sh-filter-modifier" className="h-(--vg-control-dense) text-xs"
                  value={list.draft.modifier}
                  onChange={(event) => list.set("modifier", event.target.value)} />
              </div>
            </div>
            {/*
              SH1024 — the author picker's agent option: what an agent run wrote, read off the
              audit trail by the vocabulary's `agent` word, so this is the question `shio find
              --agent true` asks. A checkbox rather than a value in the author field, because an
              agent is not an owner: its writes carry the credential's user, and a name typed
              there would never match one.
            */}
            <label className="flex items-center gap-2 text-xs">
              <Checkbox checked={list.value.agent === "true"}
                onCheckedChange={(checked: boolean | "indeterminate") => list.set("agent", checked ? "true" : "")} />
              {t("content.filterAgent")}
            </label>
            <div className="flex items-center justify-between">
              {/* The subtree word, spelled as the server spells it: unsaid means this folder. */}
              <label className="flex items-center gap-2 text-xs">
                <Checkbox checked={list.value.subtree === "true"}
                  onCheckedChange={(checked: boolean | "indeterminate") => list.set("subtree", checked ? "true" : "")} />
                {t("content.filterSubtree")}
              </label>
              <Button variant="ghost" size="sm" onClick={list.clear}>{t("content.filterClear")}</Button>
            </div>
          </PopoverContent>
        </Popover>

        {/*
          SH1026 — the three views, named and pressed. They used to be three unnamed icons
          whose only effect was their own colour; now each draws the list its own way.
        */}
        <div className="flex items-center" role="group" aria-label={t("content.view")}>
          <Button variant="ghost" size="icon-sm" className={viewMode === "list" ? "bg-muted" : ""}
            aria-label={t("content.viewAsList")} aria-pressed={viewMode === "list" ? "true" : "false"}
            onClick={view("list")}>
            <List className="h-4 w-4" />
          </Button>
          <Button variant="ghost" size="icon-sm" className={viewMode === "grid" ? "bg-muted" : ""}
            aria-label={t("content.viewAsGrid")} aria-pressed={viewMode === "grid" ? "true" : "false"}
            onClick={view("grid")}>
            <LayoutGrid className="h-4 w-4" />
          </Button>
          <Button variant="ghost" size="icon-sm" className={viewMode === "spreadsheet" ? "bg-muted" : ""}
            aria-label={t("content.viewAsTable")} aria-pressed={viewMode === "spreadsheet" ? "true" : "false"}
            onClick={view("spreadsheet")}>
            <Sheet className="h-4 w-4" />
          </Button>
        </div>

        {/*
          SH1307 — the secondary verbs in one overflow. SH1148 — Select is in every view: the table is
          controlled by the page's set, so what it writes is what the table ticks.
        */}
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" size="icon-sm" aria-label={t("content.moreActions")}>
              <MoreHorizontal className="h-4 w-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem onClick={() => navigate(`${ROUTES.CONTENT_ROOT}/folder/${objectId}/post-type-select`)}>
              <LayoutGrid className="mr-2 h-4 w-4" /> {t("content.selectPostTypeTooltip")}
            </DropdownMenuItem>
            <DropdownMenuItem onClick={() => navigate(`${ROUTES.CONTENT_ROOT}/folder/${objectId}/new-folder`)}>
              <FolderPlus className="mr-2 h-4 w-4" /> {t("content.newFolder")}
            </DropdownMenuItem>
            <DropdownMenuItem onClick={() => navigate(ROUTES.SITE_IMPORT)}>
              <Upload className="mr-2 h-4 w-4" /> {t("content.import")}
            </DropdownMenuItem>
            {/* SH1027 — the search page, scoped to this folder; a place to go, so it is a menu item. */}
            {searchHereHref && (
              <DropdownMenuItem onClick={() => navigate(searchHereHref, { state: searchBackState(location) })}>
                <Search className="mr-2 h-4 w-4" /> {t("content.searchHere")}
              </DropdownMenuItem>
            )}
            <DropdownMenuSeparator />
            <DropdownMenuSub>
              <DropdownMenuSubTrigger>{t("content.sortBy")}</DropdownMenuSubTrigger>
              <DropdownMenuSubContent>
                <DropdownMenuRadioGroup value={sortBy} onValueChange={(value) => list.set("sort", value)}>
                  <DropdownMenuRadioItem value="order">{t("content.order")}</DropdownMenuRadioItem>
                  <DropdownMenuRadioItem value="name">{t("common.name")}</DropdownMenuRadioItem>
                  <DropdownMenuRadioItem value="date">{t("common.date")}</DropdownMenuRadioItem>
                </DropdownMenuRadioGroup>
              </DropdownMenuSubContent>
            </DropdownMenuSub>
            <DropdownMenuSub>
              <DropdownMenuSubTrigger>{t("content.select")}</DropdownMenuSubTrigger>
              <DropdownMenuSubContent>
                <DropdownMenuItem onClick={() => onSelectAction("content")}><FileText className="mr-2 h-4 w-4" /> {t("content.batchContent")}</DropdownMenuItem>
                <DropdownMenuItem onClick={() => onSelectAction("folder")}><Folder className="mr-2 h-4 w-4" /> {t("content.folder")}</DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem onClick={() => onSelectAction("everything")}>{t("content.selectEverything")}</DropdownMenuItem>
                <DropdownMenuItem onClick={() => onSelectAction("invert")}>{t("content.selectInvert")}</DropdownMenuItem>
                <DropdownMenuItem onClick={() => onSelectAction("nothing")}>{t("content.selectNothing")}</DropdownMenuItem>
              </DropdownMenuSubContent>
            </DropdownMenuSub>
          </DropdownMenuContent>
        </DropdownMenu>

        {/* SH1195 — the bulk verbs appear once there is a selection to act on, not disabled before it. */}
        {selectedItems.size > 0 && <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="outline" size="sm">{t("content.actionInBatch")} <ChevronDown className="ml-1 h-3.5 w-3.5" /></Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem onClick={() => onPick("copy", [...selectedItems])}><Copy className="mr-2 h-4 w-4" /> {t("content.copy")}</DropdownMenuItem>
            <DropdownMenuItem onClick={() => onPick("move", [...selectedItems])}><Move className="mr-2 h-4 w-4" /> {t("content.move")}</DropdownMenuItem>
            <DropdownMenuSeparator />
            {/*
              SH1021 — the verbs an agent has had since shio_publish shipped. Every one opens the
              plan first: a bulk verb is the one shape where a mistake is multiplied by the size
              of the selection.
            */}
            <DropdownMenuItem onClick={() => onPlanBulk("PUBLISH")}><Eye className="mr-2 h-4 w-4" /> {t("content.publish")}</DropdownMenuItem>
            <DropdownMenuItem onClick={() => onPlanBulk("UNPUBLISH")}><Download className="mr-2 h-4 w-4" /> {t("content.unpublish")}</DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem onClick={() => onBatchAction("delete")} className="text-destructive"><Trash2 className="mr-2 h-4 w-4" /> {t("common.delete")}</DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>}

        {/*
          SH1307 — New is the page's one filled button (SH1195), last in the row. The other types are
          the overflow's first item: a second button beside it pushed the filter bar onto two lines.
        */}
        <Button size="sm" className="ml-1" disabled={!lastPostType}
          onClick={() => {
            if (lastPostType) {
              localStorage.setItem("sh-last-post-type", lastPostType.name)
              localStorage.setItem(lastPostTypeKey(objectId), lastPostType.name)
              navigate(`${ROUTES.CONTENT_ROOT}/folder/${objectId}/post-type/${encodeURIComponent(lastPostType.name)}/new`)
            }
          }}>
          <Plus className="mr-2 h-4 w-4" /> {t("content.newItem", { type: lastPostType?.title || lastPostType?.name || t("content.defaultPostType") })}
        </Button>
      </div>
  )
}
