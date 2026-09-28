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
import type { ShExchangeProviderInstance } from "@/models/provider/exchange-provider.model"
import { ShExchangeProviderService } from "@/services/provider/exchange-provider.service"
import { AdminListPage } from "./admin-list.page"

const service = new ShExchangeProviderService()

/** SH905 — configuration, not a page. The list itself is `AdminListPage`. */
export default function BentoExchangeProviderListPage() {
  const { t } = useTranslation()
  const query = useCallback(() => service.list(), [])

  return (
    <AdminListPage
      address="exchangeProvider:"
      query={query}
      listRoute={ROUTES.BENTO_EXCHANGE_PROVIDERS}
      newRoute={`${ROUTES.BENTO_EXCHANGE_PROVIDERS}/new`}
      tone="blue"
      eyebrow={t("admin.title")}
      title={t("exchangeProvider.title")}
      subtitle={t("exchangeProvider.description")}
      newLabel={t("exchangeProvider.newInstance")}
      emptyTitle={t("exchangeProvider.blankTitle")}
      emptyDescription={t("exchangeProvider.blankDescription")}
      itemKey={(item: ShExchangeProviderInstance) => item.id}
      tileOf={(item: ShExchangeProviderInstance) => ({
        to: `${ROUTES.BENTO_EXCHANGE_PROVIDERS}/${item.id}`,
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
