/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { IconDownload, IconExternalLink, IconWorld } from "@tabler/icons-react"
import { toast } from "@viglet/viglet-design-system"
import { BentoEntityShell, BentoFormSection } from "@viglet/viglet-design-system/bento"
import type React from "react"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router-dom"
import { ROUTES } from "@/app/routes.const"
import { Checkbox } from "@/components/ui/checkbox"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { siteDestination } from "@/lib/site-front-end"
import type { ShPostType } from "@/models/post-type/post-type"
import type { ShSite } from "@/models/site/site"
import { ShPostTypeService } from "@/services/post-type/post-type.service"
import { ShSiteService } from "@/services/site/site.service"

const service = new ShSiteService()
const postTypeService = new ShPostTypeService()

/**
 * SH899 — the site editor, and the first entity shell in this console.
 *
 * ## Identity lives in the hero, never in the form
 *
 * The authoring contract's first rule, and this page is the clearest case for it: a site's name
 * and description were two form fields among four, so the thing a curator is looking at was
 * indistinguishable from its settings. `BentoEntityShell` takes them — inline-edited in the hero,
 * beside the icon and the status — and hands the form back everything else.
 *
 * ## `name` is the site's title, and the mapping is one line each way
 *
 * `ShSite` has `name`; the shell's identity has `title`. Rather than rename a field the whole
 * backend and CDA agree on, the entity handed to the shell carries `title: site.name`, and
 * `onUpdate` maps it back. Two lines, in one place, with the reason next to them — the
 * alternative is a rename that reaches the API, the CLI and the agent surface.
 *
 * ## The save bar is the shell's, and so is delete
 *
 * No `<div className="sticky">`, no button row at the bottom of the form. The shell owns the
 * morph — controls in the hero until it scrolls away, then a fixed bar — and the delete flow with
 * its confirm dialog. Export and view-site are `extraActions`, which is where the four hover-only
 * row actions from the console list ended up: focusable, announced, and on the entity they act on.
 */
export default function BentoSiteEditPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { siteId = "" } = useParams()
  const [site, setSite] = useState<ShSite | null>(null)
  const [postTypes, setPostTypes] = useState<ShPostType[]>([])
  const [searchableTypes, setSearchableTypes] = useState<string[]>([])
  const [url, setUrl] = useState("")

  useEffect(() => {
    if (!siteId) return
    service.get(siteId).then((data) => {
      setSite(data)
      setUrl(data.url ?? "")
      try {
        const parsed: unknown = JSON.parse(data.searchablePostTypes || "[]")
        setSearchableTypes(Array.isArray(parsed) ? (parsed as string[]) : [])
      } catch {
        // The column is free text and has held a non-JSON value before. An unreadable list is
        // "none selected", never a crash on the page that would let a curator fix it.
        setSearchableTypes([])
      }
    })
    postTypeService.query().then(setPostTypes).catch(() => setPostTypes([]))
  }, [siteId])

  const toggle = (name: string, checked: boolean) =>
    setSearchableTypes((prev) => (checked ? [...prev, name] : prev.filter((n) => n !== name)))

  /*
   * The shell's Save button is `<GradientButton type="submit" form={formId}>` — it submits an
   * HTML form with that id, and nothing else. The first draft's render prop returned a
   * `BentoFormSection` with no `<form>` around it, so the page rendered perfectly and the button
   * did nothing: no request, no error, no console message. Only a run finds that.
   *
   * `onUpdate` is a different path and does not cover it — the shell calls that when the *hero*
   * identity is edited (type, blur, autosave). The body's fields are this form's.
   */
  async function onSubmit(event: React.FormEvent) {
    event.preventDefault()
    if (!site) return
    const merged: ShSite = {
      ...site,
      url,
      searchablePostTypes: searchableTypes.length ? JSON.stringify(searchableTypes) : "",
    }
    await service.update(merged)
    setSite(merged)
    toast.success(t("common.saved"))
  }

  if (!site) return null

  return (
    <BentoEntityShell
      entity={{ ...site, title: site.name }}
      isNew={false}
      headlineFallback={t("content.editSite")}
      eyebrow={t("content.siteLabel")}
      listRoute={ROUTES.BENTO_CONTENT}
      icon={IconWorld}
      tone="blue"
      formId="site-edit"
      feature={t("content.siteLabel")}
      hideIcon
      onUpdate={async (next) => {
        // Back the other way: the hero edits `title`, the API stores `name`.
        const merged: ShSite = {
          ...site,
          ...next,
          name: next.title ?? site.name,
          description: next.description ?? "",
          url,
          searchablePostTypes: searchableTypes.length ? JSON.stringify(searchableTypes) : "",
        }
        await service.update(merged)
        setSite(merged)
      }}
      onDelete={async () => {
        await service.delete(siteId)
        navigate(ROUTES.BENTO_CONTENT)
        return true
      }}
      extraActions={[
        {
          label: t("content.export"),
          icon: IconDownload,
          onSelect: () => {
            void service.export(siteId).then((blob) => {
              const href = URL.createObjectURL(blob)
              const a = document.createElement("a")
              a.href = href
              a.download = `${site.name}.zip`
              a.click()
              URL.revokeObjectURL(href)
            })
          },
        },
        {
          label: t("content.viewSite"),
          icon: IconExternalLink,
          onSelect: () => window.open(siteDestination(site).url, "_blank", "noopener"),
        },
      ]}
    >
      {({ onStateChange }) => (
        /*
         * `content.siteSettings`, and no description. The first draft titled this "Edit Site" —
         * which the hero above already says, with the site's actual name — and described it with
         * `searchableTypesHint`, the same sentence the Searchable Post Types field prints ten
         * lines further down. Two duplications on one screen, both from reaching for the nearest
         * string rather than asking what the section is. It holds the site's delivery URL and its
         * search settings, so it is called that.
         */
        <form id="site-edit" onSubmit={onSubmit}>
        <BentoFormSection icon={IconWorld} tone="blue" title={t("content.siteSettings")}>
          <div className="space-y-2">
            <Label htmlFor="site-url">{t("content.url")}</Label>
            <Input
              id="site-url"
              value={url}
              onChange={(e) => {
                setUrl(e.target.value)
                onStateChange({ isDirty: true, isSubmitting: false })
              }}
            />
          </div>

          <div className="space-y-3 pt-2">
            <div>
              <Label>{t("content.searchableTypes")}</Label>
              <p className="text-muted-foreground mt-1 text-xs">{t("content.searchableTypesHint")}</p>
            </div>
            <div className="space-y-2 rounded-md border p-3">
              {postTypes.length === 0 ? (
                <p className="text-muted-foreground text-xs">{t("content.searchableTypesNone")}</p>
              ) : (
                postTypes
                  .slice()
                  .sort((a, b) => (a.title ?? a.name).localeCompare(b.title ?? b.name))
                  .map((pt) => (
                    <div key={pt.name} className="flex items-center gap-2">
                      <Checkbox
                        id={`st-${pt.name}`}
                        checked={searchableTypes.includes(pt.name)}
                        onCheckedChange={(checked) => {
                          toggle(pt.name, checked === true)
                          onStateChange({ isDirty: true, isSubmitting: false })
                        }}
                      />
                      <Label htmlFor={`st-${pt.name}`} className="cursor-pointer font-normal">
                        {pt.title}{" "}
                        <span className="text-muted-foreground font-mono text-xs">({pt.name})</span>
                      </Label>
                    </div>
                  ))
              )}
              {searchableTypes.length === 0 && postTypes.length > 0 && (
                <p className="text-muted-foreground mt-2 border-t pt-1 text-xs">
                  {t("content.searchableTypesAll")}
                </p>
              )}
            </div>
          </div>
        </BentoFormSection>
        </form>
      )}
    </BentoEntityShell>
  )
}
