import { ShTenantService } from "@/services/tenant/tenant.service"
import React from "react"

const tenantService = new ShTenantService()

/**
 * Resolves the current tenant and redirects the browser to a tenant-scoped
 * URL (`/{tenant}{path}`). Uses a full-page redirect so the routes computed
 * in `routes.const.ts` are re-evaluated against the new pathname.
 */
export default function TenantGateway() {
  React.useEffect(() => {
    tenantService
      .current()
      .then(({ tenantId }) => {
        const target = `/${tenantId}/console`
        globalThis.location.replace(target)
      })
      .catch(() => {
        globalThis.location.replace(`/shio/console`)
      })
  }, [])

  return null
}
