import { Building2, Check, ChevronsUpDown } from "lucide-react"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { toast } from "@viglet/viglet-design-system"

import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { ShTenantService } from "@/services/tenant/tenant.service"
import type { ShTenant } from "@/models/tenant/tenant.model"

const service = new ShTenantService()

/**
 * Console header tenant switcher (SH22/C). Lists the tenants the current user
 * belongs to ({@code GET /tenant/mine}) and switches the session's active tenant
 * ({@code POST /tenant/{name}/switch}), then reloads so the app re-resolves it.
 * Renders nothing until at least one membership is known.
 */
export function TenantSwitcher() {
  const { t } = useTranslation()
  const [tenants, setTenants] = useState<ShTenant[]>([])
  const [current, setCurrent] = useState<string>("")

  useEffect(() => {
    service.mine().then(setTenants).catch(() => setTenants([]))
    service.current().then((c) => setCurrent(c.tenantId)).catch(() => undefined)
  }, [])

  const handleSwitch = async (name: string) => {
    if (name === current) return
    try {
      await service.switchTo(name)
      globalThis.location.reload()
    } catch {
      toast.error(t("common.error"))
    }
  }

  if (tenants.length === 0) return null

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="sm" className="gap-1.5">
          <Building2 className="h-4 w-4" />
          <span className="max-w-32 truncate font-mono text-xs">{current || t("tenantAdmin.title")}</span>
          <ChevronsUpDown className="h-3.5 w-3.5 opacity-60" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="min-w-48">
        <DropdownMenuLabel>{t("tenantSwitcher.label")}</DropdownMenuLabel>
        <DropdownMenuSeparator />
        {tenants.map((tenant) => (
          <DropdownMenuItem key={tenant.id} onClick={() => handleSwitch(tenant.name)}
            className="font-mono text-xs">
            <Check className={tenant.name === current ? "h-4 w-4 opacity-100" : "h-4 w-4 opacity-0"} />
            {tenant.name}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
