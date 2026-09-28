/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH1145 — the browser's two toolbars: what a curator can start, narrow, and act on.
 *
 * <p>Markup and no state. Every value it renders from is settled by the page — the list state owns
 * the filters and the view (SH1023), the verbs are the page's, and the facet options are computed
 * from what the listing returned. Thirteen props rather than a context, because thirteen named
 * inputs are a contract a reviewer can check and a context is a lookup they have to go and find.</p>
 */
import {
  ChevronDown, Copy, Download, Eye, FileText, Folder, FolderPlus, LayoutGrid, List, Move, Plus,
  Sheet, SlidersHorizontal, Trash2, Upload,
} from "lucide-react"
import { useState } from "react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Checkbox } from "@/components/ui/checkbox"
import { Panel } from "@/components/panel"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip"
import { ROUTES } from "@/app/routes.const"
import type { ShPostType } from "@/models/post-type/post-type"
import type { useListState } from "@/hooks/use-list-state"

import { MORE_IGNORES, type BreadcrumbPathItem, type BROWSER_LIST } from "./browser-listing"
import type { BrowserView } from "./browser-view"

export interface BrowserToolbarProps {
  objectId: string
  siteId: string | null
  /** The list state: the filters, the sort and the view, all of which live in the URL (SH1023). */
  list: ReturnType<typeof useListState<keyof typeof BROWSER_LIST & string>>
  sortBy: string
  viewMode: BrowserView
  /** The type a "New …" button offers, derived from the catalogue and the last one used. */
  lastPostType: ShPostType | null
  breadcrumbPath: readonly BreadcrumbPathItem[]
  /** SH1027 — where "search this folder" goes, or null before the breadcrumb has landed. */
  searchHereHref: string | null
  selectedItems: Set<string>
  /** The Select menu (SH1148: in every view, since the table is controlled by the page's set). */
  onSelectAction: (action: string) => void
  onBatchAction: (action: string) => void
  onPlanBulk: (op: "PUBLISH" | "UNPUBLISH") => void
  onPick: (mode: "copy" | "move", ids: string[]) => void
  navigate: (to: string) => void
  t: (key: string, options?: Record<string, unknown>) => string
}

export function BrowserToolbar({
  objectId, siteId, list, sortBy, viewMode, lastPostType, breadcrumbPath, searchHereHref,
  selectedItems, onSelectAction, onBatchAction, onPlanBulk, onPick, navigate, t,
}: BrowserToolbarProps) {
  const [filtersOpen, setFiltersOpen] = useState(false)
  return (
    <>
      {/* Toolbar: New Article | Grid | New Folder | Import || Order | Select | Action in Batch */}
      <div className="flex flex-wrap items-center gap-2">
        {/* Left actions: New [PostType] + grid selector */}
        <TooltipProvider>
          <div className="flex items-center">
            <Button
              variant="outline"
              size="sm"
              className="rounded-r-none"
              disabled={!lastPostType}
              onClick={() => {
                if (lastPostType) {
                  localStorage.setItem("sh-last-post-type", lastPostType.name)
                  navigate(`${ROUTES.CONTENT_ROOT}/folder/${objectId}/post-type/${encodeURIComponent(lastPostType.name)}/new`)
                }
              }}
            >
              <Plus className="mr-2 h-4 w-4" /> {t("content.newItem", { type: lastPostType?.title || lastPostType?.name || t("content.defaultPostType") })}
            </Button>
            <Tooltip>
              <TooltipTrigger asChild>
                <Button
                  variant="outline"
                  size="icon"
                  className="h-8 w-8 rounded-l-none border-l-0"
                  // SH1145 — named, not declared. This icon carried a tooltip and no accessible
                  // name, which SH893 tolerated as a page's own debt; moving it into a shared
                  // component makes it every page's, and that rule says fix it rather than list it.
                  // A Tooltip is not a name: it needs hover or focus to exist at all.
                  aria-label={t("content.selectPostTypeTooltip")}
                  onClick={() => navigate(`${ROUTES.CONTENT_ROOT}/folder/${objectId}/post-type-select`)}
                >
                  <LayoutGrid className="h-4 w-4" />
                </Button>
              </TooltipTrigger>
              <TooltipContent>{t("content.selectPostTypeTooltip")}</TooltipContent>
            </Tooltip>
          </div>
        </TooltipProvider>

        <Button variant="outline" size="sm" onClick={() => navigate(`${ROUTES.CONTENT_ROOT}/folder/${objectId}/new-folder`)}>
          <FolderPlus className="mr-2 h-4 w-4" /> {t("content.newFolder")}
        </Button>

        <Button variant="outline" size="sm" onClick={() => navigate(ROUTES.SITE_IMPORT)}>
          <Upload className="mr-2 h-4 w-4" /> {t("content.import")}
        </Button>

        <div className="flex-1" />

        {/*
          SH1022 — the filters, which are the server's words and not a second set computed here.
          A curator asking "drafts changed this week by Ana" is asking the same question an agent
          asks with `shio find --state draft --modified-since ... --modifier ana`, and it is the
          same query: a client-side filter over a paged listing would narrow one page.
        */}
        <Popover open={filtersOpen} onOpenChange={setFiltersOpen}>
          <PopoverTrigger asChild>
            <Button variant="outline" size="sm" data-testid="sh-filters">
              <SlidersHorizontal className="mr-2 h-4 w-4" /> {t("content.moreFilters")}
              {list.narrowed(MORE_IGNORES) > 0 && (
                <Badge variant="secondary" className="ml-2 text-[10px]">
                  {list.narrowed(MORE_IGNORES)}
                </Badge>
              )}
            </Button>
          </PopoverTrigger>
          <PopoverContent align="end" className="w-80 space-y-3">
            <div className="grid grid-cols-2 gap-2">
              <div className="space-y-1">
                <Label className="text-xs" htmlFor="sh-filter-author">
                  {t("content.filterAuthor")}
                </Label>
                <Input id="sh-filter-author" className="h-8 text-xs"
                  value={list.draft.author}
                  onChange={(event) => list.set("author", event.target.value)} />
              </div>
              <div className="space-y-1">
                <Label className="text-xs" htmlFor="sh-filter-modifier">
                  {t("content.filterModifier")}
                </Label>
                <Input id="sh-filter-modifier" className="h-8 text-xs"
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
              <Button variant="ghost" size="sm" onClick={list.clear}>
                {t("content.filterClear")}
              </Button>
            </div>
          </PopoverContent>
        </Popover>

        {/* Right: Sort + Select + Batch + Download */}
        <Select value={sortBy} onValueChange={(value) => list.set("sort", value)}>
          <SelectTrigger className="w-28 h-8 text-xs" aria-label={t("content.sortBy")}><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="order">{t("content.order")}</SelectItem>
            <SelectItem value="name">{t("common.name")}</SelectItem>
            <SelectItem value="date">{t("common.date")}</SelectItem>
          </SelectContent>
        </Select>

        {/*
          SH1148 — in every view again. SH1142 took this menu out of the table view because the
          table took no selection in: every item wrote `selectedItems` and nothing on screen
          changed, so "Select all" then Delete could delete whatever the table last had ticked while
          the dialog counted another set. Since VDS174 the table is controlled by `selectedIds`, so
          what this menu writes is what the table ticks, and there is one selection in every view.
        */}
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="outline" size="sm">{t("content.select")} <ChevronDown className="ml-1 h-3.5 w-3.5" /></Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem onClick={() => onSelectAction("content")}><FileText className="mr-2 h-4 w-4 text-green-600" /> {t("content.batchContent")}</DropdownMenuItem>
            <DropdownMenuItem onClick={() => onSelectAction("folder")}><Folder className="mr-2 h-4 w-4" /> {t("content.folder")}</DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem onClick={() => onSelectAction("everything")}>{t("content.selectEverything")}</DropdownMenuItem>
            <DropdownMenuItem onClick={() => onSelectAction("invert")}>{t("content.selectInvert")}</DropdownMenuItem>
            <DropdownMenuItem onClick={() => onSelectAction("nothing")}>{t("content.selectNothing")}</DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>

        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="outline" size="sm" disabled={selectedItems.size === 0}>{t("content.actionInBatch")} <ChevronDown className="ml-1 h-3.5 w-3.5" /></Button>
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
        </DropdownMenu>

        {siteId && (
          <Button size="sm" className="bg-green-700 hover:bg-green-800 text-white" onClick={() => window.open(`/api/v2/site/${siteId}/export`, "_blank")}>
            <Download className="mr-2 h-4 w-4" /> {t("content.downloadSite")}
          </Button>
        )}
      </div>

      {/* Sub-toolbar: breadcrumb path + search/view icons */}
      <Panel contentClassName="flex items-center gap-2 py-2 px-4">
          <div className="flex-1 flex items-center gap-1 text-sm text-muted-foreground overflow-hidden">
            {breadcrumbPath.map((crumb, i) => (
              <span key={crumb.id} className="flex items-center gap-1 shrink-0">
                {i > 0 && <span>/</span>}
                <button type="button" className="hover:text-foreground hover:underline transition-colors truncate" onClick={() => navigate(`${ROUTES.CONTENT_ROOT}/object/${crumb.id}`)}>
                  {crumb.name}
                </button>
              </span>
            ))}
          </div>
          <div className="flex items-center gap-1 shrink-0">
            {searchHereHref && (
              <Tooltip>
                <TooltipTrigger asChild>
                  <Button variant="ghost" size="icon" className="h-7 w-7"
                    aria-label={t("content.searchHere")}
                    onClick={() => navigate(searchHereHref)}>
                    <SlidersHorizontal className="h-4 w-4 rotate-90" />
                  </Button>
                </TooltipTrigger>
                <TooltipContent>{t("content.searchHere")}</TooltipContent>
              </Tooltip>
            )}
            {/*
              SH1026 — the three views, named and pressed. They used to be three unnamed icons
              whose only effect was their own colour; now each draws the list its own way.
            */}
            <Button variant="ghost" size="icon" className={`h-7 w-7 ${viewMode === "list" ? "bg-muted" : ""}`}
              aria-label={t("content.viewAsList")} aria-pressed={viewMode === "list" ? "true" : "false"}
              onClick={() => list.set("view", "list")}>
              <List className="h-4 w-4" />
            </Button>
            <Button variant="ghost" size="icon" className={`h-7 w-7 ${viewMode === "grid" ? "bg-muted" : ""}`}
              aria-label={t("content.viewAsGrid")} aria-pressed={viewMode === "grid" ? "true" : "false"}
              onClick={() => list.set("view", "grid")}>
              <LayoutGrid className="h-4 w-4" />
            </Button>
            <Button variant="ghost" size="icon" className={`h-7 w-7 ${viewMode === "spreadsheet" ? "bg-muted" : ""}`}
              aria-label={t("content.viewAsTable")} aria-pressed={viewMode === "spreadsheet" ? "true" : "false"}
              onClick={() => list.set("view", "spreadsheet")}>
              <Sheet className="h-4 w-4" />
            </Button>
          </div>
      </Panel>
    </>
  )
}
