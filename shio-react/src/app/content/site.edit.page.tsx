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
import { useQuery } from "@tanstack/react-query"
import { useLocales, usePostTypes, useSiteLocalePolicy } from "@/hooks/use-catalogue"
import { isCancelled } from "@/lib/read-options"
import { addressKey } from "@/lib/query-keys"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router-dom"
import { ROUTES } from "@/app/routes.const"
import { Checkbox } from "@/components/ui/checkbox"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { siteDestination } from "@/lib/site-front-end"
import type { ShSite, ShSiteLocalePolicy } from "@/models/site/site"
import { ShSiteService } from "@/services/site/site.service"
import { SiteIndexSection } from "@/components/site-index.section"
import { SiteProvenanceSection } from "@/components/site-provenance.section"
import { SiteLanguagesSection } from "@/components/site-languages.section"
import { declarationOf, draftOf, type LanguagesDraft } from "@/lib/site-languages"
import { reportProblem } from "@/lib/problem"

const service = new ShSiteService()

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
/**
 * The searchable post types a site declares, read from a column that is free text.
 *
 * <p>It has held a non-JSON value before, so an unreadable list is "none selected" rather than a
 * crash on the one page a curator could fix it from. A named function rather than a `try` inside the
 * seeding block, because `no-useless-assignment` is right about what that block looked like.</p>
 */
function searchableOf(raw: string | null | undefined): string[] {
  try {
    const parsed: unknown = JSON.parse(raw || "[]")
    return Array.isArray(parsed) ? (parsed as string[]) : []
  } catch {
    return []
  }
}

/**
 * SH1283 — the post types this site binds to a layout, the only ones a page type may name. The
 * column is a JSON object keyed by type (a value may be SH193's array form), and an unreadable one
 * offers nothing rather than crashing the page a curator would fix it from.
 */
function boundTypesOf(raw: string | null | undefined): string[] {
  try {
    const parsed: unknown = JSON.parse(raw || "{}")
    return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? Object.keys(parsed).sort() : []
  } catch {
    return []
  }
}

export default function BentoSiteEditPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { siteId = "" } = useParams()
  const [site, setSite] = useState<ShSite | null>(null)
  // SH1046 — the catalogue, cached under `postType:` rather than refetched per site opened.
  const postTypes = usePostTypes().data ?? []
  const [searchableTypes, setSearchableTypes] = useState<string[]>([])
  const [url, setUrl] = useState("")
  // SH1056 — the policy the site reports, the form's draft of it, and whether the draft moved.
  const [policy, setPolicy] = useState<ShSiteLocalePolicy | null>(null)
  const [languages, setLanguages] = useState<LanguagesDraft | null>(null)
  const [languagesDirty, setLanguagesDirty] = useState(false)

  /*
   * SH1116 — three reads, three queries, all cancelled when the site changes or the screen unmounts.
   *
   * They were one effect because they are wanted together, but they are not one subject: the site,
   * its locale policy and the instance's locale catalogue are three answers with three lifetimes,
   * and the catalogue is the same for every site. As one effect they also shared one failure story —
   * the site read had no `catch` at all, so a site that would not load left the previous site's
   * fields on screen under the new site's breadcrumb.
   */
  const loaded = useQuery<ShSite>({
    queryKey: addressKey(`id:${siteId ?? ""}`),
    queryFn: ({ signal }) => service.get(siteId, { signal }),
    enabled: Boolean(siteId),
  })
  // SH1152 — shared, because `post-translations.menu.tsx` reads the same two and the post editor
  // mounts it. The catalogue is the same answer for every site and never changes at runtime.
  const reportedPolicy = useSiteLocalePolicy(siteId)
  const localeCatalogue = useLocales()

  /*
   * Seeded during render rather than in an effect (`react-hooks/set-state-in-effect`), once per site.
   * `searchablePostTypes` is free text and has held a non-JSON value before, so an unreadable list is
   * "none selected" and never a crash on the page that would let a curator fix it.
   */
  const [seeded, setSeeded] = useState<string | null>(null)
  /** SH1283 — the type a new page in this site starts as; "" while the site names none. */
  const [pageType, setPageType] = useState("")
  /** SH1302 — the SH1299 switch, beside the pages it applies to. */
  const [membersLogin, setMembersLogin] = useState(false)
  if (loaded.data && seeded !== siteId) {
    setSeeded(siteId)
    setSite(loaded.data)
    setUrl(loaded.data.url ?? "")
    setSearchableTypes(searchableOf(loaded.data.searchablePostTypes))
    setPageType(loaded.data.pageType ?? "")
    setMembersLogin(loaded.data.membersLogin === true)
  }

  const [seededPolicy, setSeededPolicy] = useState<string | null>(null)
  if (reportedPolicy.data && seededPolicy !== siteId) {
    setSeededPolicy(siteId)
    setPolicy(reportedPolicy.data)
    setLanguages(draftOf(reportedPolicy.data))
  }

  const catalogue = localeCatalogue.data ?? []

  useEffect(() => {
    const failure = loaded.error ?? reportedPolicy.error
    if (failure && !isCancelled(failure)) reportProblem(failure, t("common.error"))
  }, [loaded.error, reportedPolicy.error, t])

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
      pageType: pageType || null,
      membersLogin,
    }
    await service.update(merged)
    setSite(merged)
    // SH1056 — its own call, because the site's PUT is a whole-object replace that does not carry
    // the policy, and only when it moved: an untouched card must not turn a derived policy into a
    // declared copy of itself.
    if (languages && languagesDirty) {
      try {
        const reported = languages.declare
          ? await service.declareLocales(site.id, declarationOf(languages))
          : await service.clearLocales(site.id)
        setPolicy(reported)
        setLanguages(draftOf(reported))
        setLanguagesDirty(false)
      } catch (error) {
        reportProblem(error, t("common.error"))
        return
      }
    }
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
            // SH1065 — the server's own filename, so this download and `shio export` write the
            // same file. The fallback is the site's name, for an instance older than that header.
            void service.export(siteId).then(({ blob, fileName }) => {
              const href = URL.createObjectURL(blob)
              const a = document.createElement("a")
              a.href = href
              a.download = fileName ?? `${site.name}.zip`
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
        <BentoFormSection icon={IconWorld} title={t("content.siteSettings")}>
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

          {/* SH1283 — only a type the site binds to a layout can be a page; the server refuses others. */}
          <div className="space-y-2">
            <Label htmlFor="site-page-type">{t("content.sitePageType")}</Label>
            <select id="site-page-type" className="border-input h-9 w-64 rounded-md border bg-transparent px-2 text-sm"
              value={pageType}
              onChange={(e) => {
                setPageType(e.target.value)
                onStateChange({ isDirty: true, isSubmitting: false })
              }}>
              <option value="">{t("content.sitePageTypeNone")}</option>
              {boundTypesOf(site.postTypeLayout).map((name) => (
                <option key={name} value={name}>{name}</option>
              ))}
            </select>
          </div>

          {/* SH1302 — what a visitor a members-only page turns away is sent to (SH1299). */}
          <div className="flex items-start gap-2">
            <Checkbox id="site-members-login" checked={membersLogin}
              onCheckedChange={(checked) => {
                setMembersLogin(checked === true)
                onStateChange({ isDirty: true, isSubmitting: false })
              }} />
            <div className="space-y-1">
              <Label htmlFor="site-members-login">{t("content.siteMembersLogin")}</Label>
              <p className="text-muted-foreground text-xs">{t("content.siteMembersLoginHint")}</p>
            </div>
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
        {/* SH1063 — silent unless this instance indexes; then it says whether this site reaches
            the index at all, which nothing anywhere used to answer. */}
        <SiteIndexSection siteName={site?.name} />
        <SiteProvenanceSection siteName={site?.name} />
        {languages && (
          <SiteLanguagesSection
            catalogue={catalogue}
            policy={policy}
            siteId={siteId}
            draft={languages}
            onChange={(next) => {
              setLanguages(next)
              setLanguagesDirty(true)
              onStateChange({ isDirty: true, isSubmitting: false })
            }}
          />
        )}
        </form>
      )}
    </BentoEntityShell>
  )
}
