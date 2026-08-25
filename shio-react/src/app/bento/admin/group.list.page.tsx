/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { IconShieldCog } from "@tabler/icons-react"
import { useCallback } from "react"
import { useTranslation } from "react-i18next"
import { ROUTES } from "@/app/routes.const"
import { ShGroupService } from "@/services/auth/group.service"
import { IdentityListPage } from "./identity-list.page"

const service = new ShGroupService()

/** SH904 — configuration, not a page. The list itself is `IdentityListPage`. */
export default function BentoGroupListPage() {
  const { t } = useTranslation()
  const query = useCallback(() => service.query(), [])
  return (
    <IdentityListPage
      query={query}
      listRoute={ROUTES.BENTO_GROUPS}
      newRoute={ROUTES.BENTO_GROUP_NEW}
      editRouteOf={(group) => `${ROUTES.BENTO_GROUPS}/${group.id}`}
      icon={IconShieldCog}
      tone="violet"
      eyebrow={t("admin.title")}
      title={t("admin.groups")}
      subtitle={t("admin.groupsSubtitle")}
      newLabel={t("admin.newGroup")}
      emptyTitle={t("common.noResults")}
      emptyDescription={t("admin.groupsEmptyHint")}
    />
  )
}
