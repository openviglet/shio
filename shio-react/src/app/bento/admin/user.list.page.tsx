/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { IconUser } from "@tabler/icons-react"
import { BentoEntityTile, BentoListPage } from "@viglet/viglet-design-system/bento"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { ROUTES } from "@/app/routes.const"
import type { ShUser } from "@/models/auth/user"
import { ShUserService } from "@/services/auth/user.service"

const service = new ShUserService()

/**
 * SH904 — the user list, which fits the mosaic even though the user *editor* does not.
 *
 * A tile wants a name and a line under it, and a user has both: `firstName lastName` reads as the
 * title and the email is what distinguishes two people with the same name. The username — the
 * actual key — is what the tile navigates by, which is the one place it belongs on this screen.
 *
 * The console table showed all four columns and made `username` the leading one. That is a table
 * being honest about the database rather than about the person, and it is the reason two of these
 * lists were sorted by a string nobody looks people up by.
 */
export default function BentoUserListPage() {
  const { t } = useTranslation()
  const [users, setUsers] = useState<ShUser[] | undefined>(undefined)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    // `list()`, not `query()` — the user service is the one of the three that names it
    // differently, because `get()` was already taken by "the user I am signed in as".
    service.list()
      .then(setUsers)
      // A failed load has to become an empty array *and* an error, or the package's gate — which
      // reads `undefined` as "still loading" — spins for ever (SH899).
      .catch((reason: unknown) => {
        setUsers([])
        setError(String(reason))
      })
  }, [])

  return (
    <BentoListPage
      items={users}
      error={error}
      tryAgainUrl={ROUTES.BENTO_USERS}
      eyebrow={t("admin.title")}
      heroIcon={IconUser}
      title={t("admin.users")}
      subtitle={t("admin.usersSubtitle")}
      tone="emerald"
      newRoute={ROUTES.BENTO_USER_NEW}
      newLabel={t("admin.newUser")}
      itemKey={(user: ShUser) => user.username}
      renderTile={(user: ShUser, emphasis) => (
        <BentoEntityTile
          to={`${ROUTES.BENTO_USERS}/${user.username}`}
          emphasis={emphasis}
          defaultIcon={IconUser}
          tone="emerald"
          title={[user.firstName, user.lastName].filter(Boolean).join(" ") || user.username}
          description={user.email}
          meta={user.username}
        />
      )}
      emptyTitle={t("common.noResults")}
      emptyDescription={t("admin.usersEmptyHint")}
    />
  )
}
