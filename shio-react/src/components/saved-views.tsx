/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { useCallback, useEffect, useState } from "react"
import { useLocation, useNavigate } from "react-router-dom"
import { useTranslation } from "react-i18next"
import { Bookmark, Trash2, Users } from "lucide-react"
import { toast } from "@viglet/viglet-design-system"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Input } from "@/components/ui/input"
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Popover, PopoverAnchor, PopoverContent } from "@/components/ui/popover"
import { reportProblem } from "@/lib/problem"
import { isCancelled } from "@/lib/read-options"
import { viewHref } from "@/lib/saved-view"
import { ShSavedViewService, type SavedViewList, type ShSavedView } from "@/services/saved-view/saved-view.service"

const service = new ShSavedViewService()

/**
 * SH1024 — a list's saved views, drawn in its filter bar's `views` slot.
 *
 * A view is the list's URL under a name (SH1023 put every filter in the address), so saving takes
 * the address as it stands and opening one navigates there. Nothing here reads the query: the list
 * that opens it validates it, as it does a link somebody pasted.
 */
export function SavedViews({ list }: Readonly<{ list: SavedViewList }>) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const location = useLocation()
  const [views, setViews] = useState<ShSavedView[]>([])
  const [saving, setSaving] = useState(false)
  const [name, setName] = useState("")
  const [shared, setShared] = useState(false)

  /** The read. It raises no state before the request, which is what lets the mount effect call it. */
  const fetchViews = useCallback((signal?: AbortSignal) =>
    service.list(list, { signal }).then(setViews).catch((error: unknown) => {
      if (!isCancelled(error)) setViews([])
    }), [list])

  useEffect(() => {
    const abort = new AbortController()
    void fetchViews(abort.signal)
    return () => abort.abort()
  }, [fetchViews])

  const save = async () => {
    try {
      const saved = await service.save(list, name, location.pathname, location.search, shared)
      toast.success(t("views.saved", { name: saved.name }))
      setSaving(false)
      setName("")
      setShared(false)
      void fetchViews()
    } catch (error) {
      reportProblem(error, t("common.error"))
    }
  }

  const remove = async (view: ShSavedView) => {
    try {
      await service.delete(view.id)
      toast.success(t("views.deleted", { name: view.name }))
      void fetchViews()
    } catch (error) {
      reportProblem(error, t("common.error"))
    }
  }

  return (
    <div className="flex items-center gap-1">
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="outline" size="sm" data-testid="sh-saved-views">
            <Bookmark className="mr-2 h-4 w-4" /> {t("views.title")}
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end" className="w-64">
          {views.length === 0 && (
            <p className="px-2 py-1.5 text-xs text-muted-foreground">{t("views.none")}</p>
          )}
          {views.map((view) => (
            <DropdownMenuItem key={view.id} onSelect={() => navigate(viewHref(view))}
              className="flex items-center gap-2">
              <span className="flex-1 truncate">{view.name}</span>
              {view.shared && <Users className="h-3.5 w-3.5 text-muted-foreground" aria-label={t("views.sharedLabel")} />}
              {view.mine && (
                <button type="button" aria-label={t("views.delete", { name: view.name })}
                  className="text-muted-foreground hover:text-destructive"
                  onClick={(event) => { event.stopPropagation(); void remove(view) }}>
                  <Trash2 className="h-3.5 w-3.5" />
                </button>
              )}
            </DropdownMenuItem>
          ))}
          <DropdownMenuSeparator />
          <DropdownMenuItem onSelect={() => setSaving(true)}>{t("views.saveCurrent")}</DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
      <Popover open={saving} onOpenChange={setSaving}>
        {/* SH1054 — an anchor, not a trigger: the menu item opens this, and an empty span dressed as
            a trigger carried aria-expanded, which a span may not have. */}
        <PopoverAnchor asChild><span /></PopoverAnchor>
        <PopoverContent align="end" className="w-72 space-y-3">
          <Input aria-label={t("views.name")} placeholder={t("views.name")} value={name}
            onChange={(event) => setName(event.target.value)}
            onKeyDown={(event) => { if (event.key === "Enter" && name.trim()) void save() }} />
          <label className="flex items-center gap-2 text-xs">
            <Checkbox checked={shared} onCheckedChange={(checked) => setShared(checked === true)} />
            {t("views.shareWithTeam")}
          </label>
          <Button size="sm" className="w-full" disabled={!name.trim()} onClick={() => void save()}>
            {t("views.save")}
          </Button>
        </PopoverContent>
      </Popover>
    </div>
  )
}
