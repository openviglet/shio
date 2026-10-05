/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { IconSearch } from "@tabler/icons-react"
import { BentoFormSection } from "@viglet/viglet-design-system/bento"
import { AlertTriangle, Check } from "lucide-react"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { isCancelled } from "@/lib/read-options"
import { ShSiteService } from "@/services/site/site.service"
import type { ShIndexStatus } from "@/models/site/site"

const siteService = new ShSiteService()

/**
 * SH1063 — whether this site's published pages reach the search index.
 *
 * The card exists for one row: a site with published pages and no search site sends nothing and
 * raises nothing, so the only trace of it is somebody's search coming back empty. The rest — is
 * indexing on at all, where it points — is the context that makes that row readable.
 *
 * It says nothing at all when indexing is off. An instance that does not index has no failure to
 * report here, and a card explaining a feature nobody turned on is noise on every site page.
 */
export function SiteIndexSection({ siteName }: { siteName?: string }) {
  const { t } = useTranslation()
  const [status, setStatus] = useState<ShIndexStatus | null>(null)

  useEffect(() => {
    const abort = new AbortController()
    siteService.indexStatus({ signal: abort.signal })
      .then(setStatus)
      .catch((reason) => {
        if (!isCancelled(reason)) setStatus(null)
      })
    return () => abort.abort()
  }, [])

  if (!status?.enabled || !siteName) {
    return null
  }
  const route = status.sites.find((one) => one.site === siteName)
  const unroutable = !route?.searchSite && (route?.published ?? 0) > 0

  return (
    <BentoFormSection icon={IconSearch} title={t("content.siteIndex")}>
      <p className="text-xs text-muted-foreground">
        {t("content.siteIndexTarget", { target: status.target ?? "—" })}
      </p>
      <div className="flex items-center gap-2 text-sm">
        {unroutable
          ? <AlertTriangle className="size-4 text-amber-600 dark:text-amber-400" />
          : <Check className="size-4 text-green-600 dark:text-green-400" />}
        <span>
          {route?.searchSite
            ? t("content.siteIndexRouted", { searchSite: route.searchSite })
            : t("content.siteIndexUnroutable")}
        </span>
      </div>
      <p className="text-xs text-muted-foreground">
        {t("content.siteIndexPublished", { count: route?.published ?? 0 })}
        {status.failures > 0 && ` · ${t("content.siteIndexFailures", { count: status.failures })}`}
      </p>
    </BentoFormSection>
  )
}
