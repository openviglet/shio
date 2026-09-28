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
import { ShRoleService } from "@/services/auth/role.service"
import { AdminListPage } from "./admin-list.page"

const service = new ShRoleService()

/** SH904/SH905 — configuration, not a page. The list itself is `AdminListPage`. */
export default function BentoRoleListPage() {
  const { t } = useTranslation()
  const query = useCallback(() => service.query(), [])
  return (
    <AdminListPage
      address="role:"
      query={query}
      listRoute={ROUTES.BENTO_ROLES}
      newRoute={ROUTES.BENTO_ROLE_NEW}
      itemKey={(role) => role.id}
      tileOf={(role) => ({ to: `${ROUTES.BENTO_ROLES}/${role.id}`, title: role.name, description: role.description })}
      tone="rose"
      eyebrow={t("admin.title")}
      title={t("admin.roles")}
      subtitle={t("admin.rolesSubtitle")}
      newLabel={t("admin.newRole")}
      emptyTitle={t("common.noResults")}
      emptyDescription={t("admin.rolesEmptyHint")}
    />
  )
}
