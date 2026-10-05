/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { useCallback } from "react"
import { useTranslation } from "react-i18next"
import { ROUTES } from "@/app/routes.const"
import type { ShApiToken } from "@/models/token/api-token.model"
import { ShApiTokenService } from "@/services/token/api-token.service"
import { AdminListPage } from "./admin-list.page"

const service = new ShApiTokenService()

/** SH905 — configuration, not a page. The list itself is `AdminListPage`. */
export default function BentoApiTokenListPage() {
  const { t } = useTranslation()
  const query = useCallback(() => service.list(), [])

  return (
    <AdminListPage
      address="token:"
      query={query}
      listRoute={ROUTES.BENTO_TOKENS}
      newRoute={`${ROUTES.BENTO_TOKENS}/new`}
      eyebrow={t("admin.title")}
      title={t("apiToken.title")}
      subtitle={t("apiToken.description")}
      newLabel={t("apiToken.newInstance")}
      emptyTitle={t("apiToken.blankTitle")}
      emptyDescription={t("apiToken.blankDescription")}
      itemKey={(item: ShApiToken) => item.id}
      tileOf={(item: ShApiToken) => ({
        to: `${ROUTES.BENTO_TOKENS}/${item.id}`,
        title: item.title,
        description: item.description,
        meta: `${item.scope ?? "READ"}${item.env === "PREVIEW" ? " · PREVIEW" : ""}${item.siteIds?.length ? ` \u00b7 ${item.siteIds.length}` : ""}`,
        // Every one of these five carries `enabled`, which groups and roles do not — so unlike
        // SH904's pages the status pill is real here and driven by the column rather than by a
        // string each list translated for itself.
        enabled: item.enabled ? 1 : 0,
        hasStatus: true,
      })}
    />
  )
}
