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
import type { ShSiteWebhook } from "@/models/webhook/site-webhook.model"
import { ShSiteWebhookService } from "@/services/webhook/site-webhook.service"
import { AdminListPage } from "./admin-list.page"

const service = new ShSiteWebhookService()

/** SH905 — configuration, not a page. The list itself is `AdminListPage`. */
export default function BentoSiteWebhookListPage() {
  const { t } = useTranslation()
  const query = useCallback(() => service.list(), [])

  return (
    <AdminListPage
      address="webhook:"
      query={query}
      listRoute={ROUTES.BENTO_WEBHOOKS}
      newRoute={`${ROUTES.BENTO_WEBHOOKS}/new`}
      eyebrow={t("admin.title")}
      title={t("siteWebhook.title")}
      subtitle={t("siteWebhook.description")}
      newLabel={t("siteWebhook.newInstance")}
      emptyTitle={t("siteWebhook.blankTitle")}
      emptyDescription={t("siteWebhook.blankDescription")}
      itemKey={(item: ShSiteWebhook) => item.id}
      tileOf={(item: ShSiteWebhook) => ({
        to: `${ROUTES.BENTO_WEBHOOKS}/${item.id}`,
        title: item.name,
        // A webhook has no description column — its URL is what identifies it, and the
        // events are what it does. The console table spent two columns saying the same.
        description: item.url,
        meta: item.events?.join(" · "),
        // Every one of these five carries `enabled`, which groups and roles do not — so unlike
        // SH904's pages the status pill is real here and driven by the column rather than by a
        // string each list translated for itself.
        enabled: item.enabled ? 1 : 0,
        hasStatus: true,
      })}
    />
  )
}
