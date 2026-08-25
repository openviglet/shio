/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { IconLock } from "@tabler/icons-react"
import { useTranslation } from "react-i18next"
import { ROUTES } from "@/app/routes.const"
import { ShRoleService } from "@/services/auth/role.service"
import { IdentityEntityPage } from "./identity-entity.page"

const service = new ShRoleService()

/** SH904 — configuration, not a page. The editor itself is `IdentityEntityPage`. */
export default function BentoRoleEditPage() {
  const { t } = useTranslation()
  return (
    <IdentityEntityPage
      service={service}
      param="roleId"
      listRoute={ROUTES.BENTO_ROLES}
      icon={IconLock}
      tone="rose"
      eyebrow={t("admin.roles")}
      feature={t("admin.roles")}
      headlineFallback={t("admin.newRole")}
      formId="role-edit"
    />
  )
}
