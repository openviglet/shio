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
import type { ShTenant } from "@/models/tenant/tenant.model"
import { ShTenantService } from "@/services/tenant/tenant.service"
import { AdminListPage } from "./admin-list.page"

const service = new ShTenantService()

/** SH905 — configuration, not a page. The list itself is `AdminListPage`. */
export default function BentoTenantListPage() {
  const { t } = useTranslation()
  const query = useCallback(() => service.list(), [])

  return (
    <AdminListPage
      address="tenant:"
      query={query}
      listRoute={ROUTES.BENTO_TENANTS}
      newRoute={`${ROUTES.BENTO_TENANTS}/new`}
      tone="emerald"
      eyebrow={t("admin.title")}
      title={t("tenantAdmin.title")}
      subtitle={t("tenantAdmin.description")}
      newLabel={t("tenantAdmin.newInstance")}
      emptyTitle={t("tenantAdmin.blankTitle")}
      emptyDescription={t("tenantAdmin.blankDescription")}
      itemKey={(item: ShTenant) => item.id}
      tileOf={(item: ShTenant) => ({
        to: `${ROUTES.BENTO_TENANTS}/${item.id}`,
        title: item.name,
        description: item.description,
        meta: item.owner,
        // Every one of these five carries `enabled`, which groups and roles do not — so unlike
        // SH904's pages the status pill is real here and driven by the column rather than by a
        // string each list translated for itself.
        enabled: item.enabled ? 1 : 0,
        hasStatus: true,
      })}
    />
  )
}
