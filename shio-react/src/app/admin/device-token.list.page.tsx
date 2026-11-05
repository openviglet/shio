/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { IconDeviceMobileOff } from "@tabler/icons-react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { toast } from "@viglet/viglet-design-system"
import {
  BentoChangeCell, BentoIdentityCell, BentoListPage, type BentoDataTableAction, type BentoDataTableColumn,
} from "@viglet/viglet-design-system/bento"
import { Copy, Plus } from "lucide-react"
import { useMemo, useState } from "react"
import { useTranslation } from "react-i18next"
import { ROUTES } from "@/app/routes.const"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { headlineOf, problemOf, reportProblem } from "@/lib/problem"
import { addressKey } from "@/lib/query-keys"
import { ShDeviceTokenService, type ShDevice, type ShMintedDevice } from "@/services/token/device-token.service"

const service = new ShDeviceTokenService()
const ADDRESS = "device:"

/**
 * SH1351 — the signed-in person's own devices: add one, see when each was last used, revoke it.
 *
 * <p>Adding a device here is also how a person with no password gets one: an OIDC login has a
 * console session, the session mints, and the secret is shown once to be pasted or typed into the
 * app. Revoking answers 401 to that device on its next call. Only the person's own devices are
 * listed, whoever is reading, because the token is that person and nobody else's list.</p>
 */
export default function BentoDeviceTokenListPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const { data, error } = useQuery<ShDevice[]>({ queryKey: addressKey(ADDRESS), queryFn: () => service.list() })
  const items = data ?? (error ? [] : undefined)

  const [adding, setAdding] = useState(false)
  const [name, setName] = useState("")
  const [minted, setMinted] = useState<ShMintedDevice | null>(null)
  const [busy, setBusy] = useState(false)

  const refresh = () => queryClient.invalidateQueries({ queryKey: addressKey(ADDRESS) })

  const mint = async () => {
    setBusy(true)
    try {
      setMinted(await service.mint(name.trim()))
      setName("")
      toast.success(t("device.minted"))
      await refresh()
    } catch (error) {
      reportProblem(error, t("common.error"))
    } finally {
      setBusy(false)
    }
  }

  const revoke = async (chosen: ShDevice[]) => {
    try {
      await Promise.all(chosen.filter((one) => !one.revokedAt).map((device) => service.revoke(device.id)))
      toast.success(t("device.revoked", { count: chosen.length }))
      await refresh()
    } catch (error) {
      reportProblem(error, t("common.error"))
    }
  }

  const revokeAction: BentoDataTableAction<ShDevice> = {
    id: "revoke", label: t("device.revoke"), icon: IconDeviceMobileOff, tone: "destructive",
    onSelect: (chosen) => void revoke(chosen),
  }

  const columns = useMemo<BentoDataTableColumn<ShDevice>[]>(() => [
    {
      id: "name", header: t("common.name"), width: "minmax(14rem, 2fr)", hideable: false, lines: 2,
      sortValue: (device) => device.deviceName.toLowerCase(),
      cell: (device) => (
        <BentoIdentityCell name={device.deviceName} detail={device.current ? t("device.thisDevice") : undefined} />
      ),
    },
    {
      id: "lastUsed", header: t("device.lastUsed"), width: "minmax(9rem, 1fr)",
      sortValue: (device) => (device.lastUsedAt ? new Date(device.lastUsedAt) : null),
      cell: (device) => (device.lastUsedAt ? <BentoChangeCell at={device.lastUsedAt} /> : t("device.neverUsed")),
    },
    {
      id: "created", header: t("admin.listCreated"), width: "minmax(9rem, 1fr)",
      sortValue: (device) => new Date(device.createdDate),
      cell: (device) => <BentoChangeCell at={device.createdDate} />,
    },
    {
      id: "status", header: t("common.status"), width: "minmax(8rem, 0.8fr)",
      sortValue: (device) => (device.revokedAt ? 0 : 1),
      cell: (device) => (
        <span className="inline-flex items-center gap-2 text-muted-foreground">
          <span aria-hidden="true" className={`size-2 rounded-full ${device.revokedAt ? "bg-muted-foreground/50" : "bg-emerald-500"}`} />
          {device.revokedAt ? t("device.revokedStatus") : t("common.enabled")}
        </span>
      ),
    },
  ], [t])

  const close = () => {
    setAdding(false)
    setMinted(null)
  }

  return (
    <>
      <BentoListPage
        items={items}
        error={error ? headlineOf(problemOf(error), error.message) : null}
        tryAgainUrl={ROUTES.BENTO_DEVICES}
        eyebrow={t("admin.title")}
        title={t("device.title")}
        subtitle={t("device.description")}
        hideNew
        newRoute={ROUTES.BENTO_DEVICES}
        newLabel={t("device.add")}
        toolbar={
          <Button size="sm" onClick={() => setAdding(true)}>
            <Plus className="mr-1 h-4 w-4" /> {t("device.add")}
          </Button>
        }
        itemKey={(device) => device.id}
        columns={columns}
        rowActions={[revokeAction]}
        getRowLabel={(device) => device.deviceName}
        emptyTitle={t("device.blankTitle")}
        emptyDescription={t("device.blankDescription")}
      />
      <Dialog open={adding} onOpenChange={(open) => { if (!open) close() }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{minted ? t("device.mintedTitle") : t("device.add")}</DialogTitle>
            <DialogDescription>{minted ? t("device.mintedHint") : t("device.addHint")}</DialogDescription>
          </DialogHeader>
          {minted ? (
            <div className="flex items-center gap-2">
              <Input readOnly value={minted.token} aria-label={t("device.secret")} className="font-mono text-xs" />
              <Button
                variant="outline" size="icon" aria-label={t("device.copy")}
                onClick={() => { void navigator.clipboard.writeText(minted.token).then(() => toast.success(t("device.copied"))) }}
              >
                <Copy className="h-4 w-4" />
              </Button>
            </div>
          ) : (
            <Input
              value={name} maxLength={100}
              placeholder={t("device.namePlaceholder")} aria-label={t("device.name")}
              onChange={(event) => setName(event.target.value)}
              onKeyDown={(event) => { if (event.key === "Enter" && name.trim() && !busy) void mint() }}
            />
          )}
          <DialogFooter>
            {minted ? (
              <Button onClick={close}>{t("device.done")}</Button>
            ) : (
              <Button disabled={busy || !name.trim()} onClick={() => void mint()}>{t("device.create")}</Button>
            )}
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
