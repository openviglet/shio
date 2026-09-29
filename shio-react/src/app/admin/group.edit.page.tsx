/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { IconShieldCog } from "@tabler/icons-react"
import { useTranslation } from "react-i18next"
import { ROUTES } from "@/app/routes.const"
import { ShGroupService } from "@/services/auth/group.service"
import { IdentityEntityPage } from "./identity-entity.page"
import { GroupMembers } from "./group-members"
import { GroupRoles } from "./group-roles"

const service = new ShGroupService()

/** SH904 — configuration, not a page. The editor itself is `IdentityEntityPage`. */
export default function BentoGroupEditPage() {
  const { t } = useTranslation()
  return (
    <IdentityEntityPage
      service={service}
      param="groupId"
      scheme="group:"
      listRoute={ROUTES.BENTO_GROUPS}
      icon={IconShieldCog}
      tone="violet"
      eyebrow={t("admin.groups")}
      feature={t("admin.groups")}
      headlineFallback={t("admin.newGroup")}
      formId="group-edit"
      detail={(group, save) => (
        <div className="space-y-8">
          <GroupMembers members={group.shUsers ?? []} onChange={(shUsers) => save({ ...group, shUsers })} />
          <GroupRoles groupId={group.id} />
        </div>
      )}
    />
  )
}
