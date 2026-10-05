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
import type { ShAuthProviderInstance } from "@/models/provider/auth-provider.model"
import { ShAuthProviderService } from "@/services/provider/auth-provider.service"
import { AdminListPage } from "./admin-list.page"

const service = new ShAuthProviderService()

/** SH905 — configuration, not a page. The list itself is `AdminListPage`. */
export default function BentoAuthProviderListPage() {
  const { t } = useTranslation()
  const query = useCallback(() => service.list(), [])

  return (
    <AdminListPage
      address="authProvider:"
      query={query}
      listRoute={ROUTES.BENTO_AUTH_PROVIDERS}
      newRoute={`${ROUTES.BENTO_AUTH_PROVIDERS}/new`}
      eyebrow={t("admin.title")}
      title={t("authProvider.title")}
      subtitle={t("authProvider.description")}
      newLabel={t("authProvider.newInstance")}
      emptyTitle={t("authProvider.blankTitle")}
      emptyDescription={t("authProvider.blankDescription")}
      itemKey={(item: ShAuthProviderInstance) => item.id}
      tileOf={(item: ShAuthProviderInstance) => ({
        to: `${ROUTES.BENTO_AUTH_PROVIDERS}/${item.id}`,
        title: item.name,
        description: item.description,
        meta: item.vendor?.name,
        // Every one of these five carries `enabled`, which groups and roles do not — so unlike
        // SH904's pages the status pill is real here and driven by the column rather than by a
        // string each list translated for itself.
        enabled: item.enabled ? 1 : 0,
        hasStatus: true,
      })}
    />
  )
}
