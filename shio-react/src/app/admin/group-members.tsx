/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { useQuery } from "@tanstack/react-query"
import { toast } from "@viglet/viglet-design-system"
import { UserMinus, UserPlus } from "lucide-react"
import { useState } from "react"
import { useTranslation } from "react-i18next"
import { Link } from "react-router-dom"
import { ROUTES } from "@/app/routes.const"
import { Panel } from "@/components/panel"
import { Button } from "@/components/ui/button"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import type { ShUser } from "@/models/auth/user"
import { reportProblem } from "@/lib/problem"
import { addressKey } from "@/lib/query-keys"
import { ShUserService } from "@/services/auth/user.service"

const users = new ShUserService()

/**
 * SH1200 — who is in a group, which is the reason to open one.
 *
 * <p>A group's page was the entity shell's hero over empty space, because SH904 gave groups the shell
 * a record of id, name and description fits. That is true of the row and not of the job: the
 * membership is what a curator came to read or change. The API has always carried it — `GET
 * /v2/group/{id}` answers with `shUsers`, and `PUT` replaces the membership with the list it is sent —
 * so this reads the members off the group the page already loaded and writes through the same call
 * the hero's autosave uses. Nothing here needs a new endpoint.</p>
 */
export function GroupMembers({ members, onChange }: Readonly<{
  members: readonly ShUser[]
  onChange: (next: ShUser[]) => Promise<void>
}>) {
  const { t } = useTranslation()
  const [busy, setBusy] = useState(false)
  const [adding, setAdding] = useState("")
  const everyone = useQuery({ queryKey: addressKey("user:"), queryFn: () => users.list() })
  const inGroup = new Set(members.map((member) => member.username))
  const candidates = (everyone.data ?? []).filter((user) => !inGroup.has(user.username))

  const save = async (next: ShUser[], message: string) => {
    setBusy(true)
    try {
      await onChange(next)
      toast.success(message)
    } catch (error) {
      reportProblem(error, t("common.error"))
    } finally {
      setBusy(false)
    }
  }

  const add = async () => {
    const user = candidates.find((one) => one.username === adding)
    if (!user) return
    await save([...members, user], t("admin.groupMemberAdded", { name: user.username }))
    setAdding("")
  }

  return (
    <section aria-labelledby="group-members-heading" className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 id="group-members-heading" className="text-base font-semibold">
          {t("admin.groupMembers", { count: members.length })}
        </h2>
        <div className="flex items-center gap-2">
          <Select value={adding} onValueChange={setAdding} disabled={busy || candidates.length === 0}>
            <SelectTrigger className="w-56" aria-label={t("admin.groupMemberPick")}>
              <SelectValue placeholder={candidates.length === 0 ? t("admin.groupEveryoneIn") : t("admin.groupMemberPick")} />
            </SelectTrigger>
            <SelectContent>
              {candidates.map((user) => (
                <SelectItem key={user.username} value={user.username}>
                  {[user.firstName, user.lastName].filter(Boolean).join(" ") || user.username}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button size="sm" variant="outline" disabled={busy || !adding} onClick={() => void add()}>
            <UserPlus className="mr-1 h-4 w-4" /> {t("admin.groupMemberAdd")}
          </Button>
        </div>
      </div>
      <Panel contentClassName="p-0">
        {members.length === 0 ? (
          <p className="px-4 py-6 text-sm text-muted-foreground">{t("admin.groupNoMembers")}</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t("common.name")}</TableHead>
                <TableHead>{t("admin.username")}</TableHead>
                <TableHead className="w-32"><span className="sr-only">{t("common.actions")}</span></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {members.map((member) => (
                <TableRow key={member.username}>
                  <TableCell className="font-medium">
                    <Link to={`${ROUTES.BENTO_USERS}/${member.username}`} className="hover:underline">
                      {[member.firstName, member.lastName].filter(Boolean).join(" ") || member.username}
                    </Link>
                  </TableCell>
                  <TableCell className="font-mono text-xs text-muted-foreground">{member.username}</TableCell>
                  <TableCell>
                    <Button size="sm" variant="ghost" disabled={busy}
                      aria-label={t("admin.groupMemberRemoveNamed", { name: member.username })}
                      onClick={() => void save(members.filter((one) => one.username !== member.username),
                        t("admin.groupMemberRemoved", { name: member.username }))}>
                      <UserMinus className="mr-1 h-4 w-4" /> {t("admin.groupMemberRemove")}
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Panel>
    </section>
  )
}
