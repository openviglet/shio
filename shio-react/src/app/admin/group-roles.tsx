/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { Link } from "react-router-dom"
import { ROUTES } from "@/app/routes.const"
import { Panel } from "@/components/panel"
import { addressKey } from "@/lib/query-keys"
import { ShGroupService } from "@/services/auth/group.service"

const groups = new ShGroupService()

/**
 * SH1206 — what a group's members may do, beside who they are.
 *
 * <p>The roles are read from `GET /v2/group/{id}/roles`, the roles that name the group. They are
 * listed and linked, not edited here: a role's permissions are the escalation boundary, so changing
 * which groups a role covers stays on the role's own page, where that relation is written.</p>
 */
export function GroupRoles({ groupId }: Readonly<{ groupId: string }>) {
  const { t } = useTranslation()
  const read = useQuery({
    queryKey: addressKey(`group:${groupId}|roles`),
    queryFn: ({ signal }) => groups.roles(groupId, { signal }),
  })
  const roles = read.data ?? []
  return (
    <section aria-labelledby="group-roles-heading" className="space-y-3">
      <h2 id="group-roles-heading" className="text-base font-semibold">
        {t("admin.groupRoles", { count: roles.length })}
      </h2>
      <Panel contentClassName="p-0">
        {read.isPending ? (
          <p className="px-4 py-6 text-sm text-muted-foreground">{t("common.loading")}</p>
        ) : roles.length === 0 ? (
          <p className="px-4 py-6 text-sm text-muted-foreground">{t("admin.groupNoRoles")}</p>
        ) : (
          <ul className="divide-y">
            {roles.map((role) => (
              <li key={role.id} className="flex items-baseline gap-3 px-4 py-3 text-sm">
                <Link to={`${ROUTES.BENTO_ROLES}/${role.id}`} className="font-medium hover:underline">{role.name}</Link>
                {role.description && <span className="text-muted-foreground">{role.description}</span>}
              </li>
            ))}
          </ul>
        )}
      </Panel>
    </section>
  )
}
