/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { IconLanguage } from "@tabler/icons-react"
import { BentoFormSection } from "@viglet/viglet-design-system/bento"
import { useTranslation } from "react-i18next"
import { Link } from "react-router-dom"
import { ROUTES } from "@/app/routes.const"
import { Checkbox } from "@/components/ui/checkbox"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import type { ShSiteLocalePolicy } from "@/models/site/site"
import type { ShLocale } from "@/services/locale/locale.service"
import { prune, setFallback, toggleLocale, type LanguagesDraft } from "@/lib/site-languages"

/** Radix refuses an empty item value, so "falls back to the default" needs a spelling of its own. */
const TO_DEFAULT = "__default__"

/**
 * SH1056 — the site's languages: which it offers, which is the default, and where a missing page
 * falls back to.
 *
 * It edits a draft and never saves on its own. The page's form is what the shell's Save submits
 * (SH899), so this section is part of that one save rather than a second button with a second
 * idea of when the site changed.
 *
 * "Declare" is a checkbox and not a pre-filled list, because an undeclared site is a state with a
 * meaning: it reports the languages its content is already in. Showing that list as if somebody had
 * chosen it would make the difference invisible, and the difference is the feature.
 */
export function SiteLanguagesSection({ catalogue, policy, draft, onChange, siteId }: {
  catalogue: ShLocale[]
  policy: ShSiteLocalePolicy | null
  draft: LanguagesDraft
  onChange: (next: LanguagesDraft) => void
  /** SH1060 — where the board lives; absent on a site that has not been created yet. */
  siteId?: string
}) {
  const { t, i18n } = useTranslation()
  const nameOf = (tag: string) => {
    const known = catalogue.find((locale) => locale.initials === tag)
    return (known && (i18n.language === "pt" ? known.pt : known.en)) || tag
  }

  return (
    <BentoFormSection icon={IconLanguage} title={t("content.siteLanguages")}>
      {/* SH1060 — which languages the site offers is decided here; what it HAS in them is a
          read, and the board is where that read is shown. */}
      {siteId && (
        <Link className="text-xs text-muted-foreground hover:underline"
          to={`${ROUTES.CONSOLE}/content/site/${siteId}/languages`}>
          {t("content.coverageOpen")} →
        </Link>
      )}
      <div className="flex items-start gap-2">
        <Checkbox
          id="site-languages-declare"
          checked={draft.declare}
          onCheckedChange={(checked) => onChange(prune({ ...draft, declare: checked === true }))}
        />
        <div>
          <Label htmlFor="site-languages-declare" className="cursor-pointer">
            {t("content.siteLanguagesDeclare")}
          </Label>
          <p className="text-muted-foreground mt-1 text-xs">
            {draft.declare
              ? t("content.siteLanguagesDeclaredHint")
              : t("content.siteLanguagesDerivedHint", {
                list: (policy?.enabled ?? []).join(", ") || "—",
              })}
          </p>
        </div>
      </div>

      {draft.declare && (
        <div className="space-y-4 pt-2">
          <div className="space-y-2">
            <Label>{t("content.siteLanguagesEnabled")}</Label>
            <div className="max-h-64 space-y-2 overflow-auto rounded-md border p-3">
              {catalogue.map((locale) => (
                <div key={locale.initials} className="flex items-center gap-2">
                  <Checkbox
                    id={`site-locale-${locale.initials}`}
                    checked={draft.enabled.includes(locale.initials)}
                    onCheckedChange={(checked) =>
                      onChange(toggleLocale(draft, locale.initials, checked === true))}
                  />
                  <Label htmlFor={`site-locale-${locale.initials}`} className="cursor-pointer font-normal">
                    {nameOf(locale.initials)}{" "}
                    <span className="text-muted-foreground font-mono text-xs">({locale.initials})</span>
                  </Label>
                </div>
              ))}
            </div>
          </div>

          {draft.enabled.length > 0 && (
            <div className="space-y-2">
              <Label htmlFor="site-languages-default">{t("content.siteLanguagesDefault")}</Label>
              <Select
                value={draft.defaultLocale}
                onValueChange={(value) => onChange(prune({ ...draft, defaultLocale: value }))}
              >
                <SelectTrigger id="site-languages-default" className="w-64">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {draft.enabled.map((tag) => (
                    <SelectItem key={tag} value={tag}>{nameOf(tag)} ({tag})</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          )}

          {draft.enabled.length > 1 && (
            <div className="space-y-2">
              <Label>{t("content.siteLanguagesFallback")}</Label>
              <p className="text-muted-foreground text-xs">{t("content.siteLanguagesFallbackHint")}</p>
              {draft.enabled.filter((tag) => tag !== draft.defaultLocale).map((from) => (
                <div key={from} className="flex items-center gap-2">
                  <Label htmlFor={`site-fallback-${from}`} className="w-40 font-normal">
                    {nameOf(from)} ({from})
                  </Label>
                  <Select
                    value={draft.fallback[from] ?? TO_DEFAULT}
                    onValueChange={(value) =>
                      onChange(setFallback(draft, from, value === TO_DEFAULT ? "" : value))}
                  >
                    <SelectTrigger id={`site-fallback-${from}`} className="w-64">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value={TO_DEFAULT}>
                        {t("content.siteLanguagesFallbackDefault", { locale: draft.defaultLocale })}
                      </SelectItem>
                      {draft.enabled.filter((to) => to !== from && to !== draft.defaultLocale)
                        .map((to) => (
                          <SelectItem key={to} value={to}>{nameOf(to)} ({to})</SelectItem>
                        ))}
                    </SelectContent>
                  </Select>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </BentoFormSection>
  )
}
