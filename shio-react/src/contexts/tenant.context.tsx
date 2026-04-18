import React from "react"
import { useParams } from "react-router-dom"

interface TenantContextValue {
  tenantId: string
}

const TenantContext = React.createContext<TenantContextValue | null>(null)

export function TenantProvider({ children }: { children: React.ReactNode }) {
  const { tenant } = useParams<{ tenant: string }>()
  const value = React.useMemo(() => ({ tenantId: tenant ?? "shio" }), [tenant])
  return <TenantContext value={value}>{children}</TenantContext>
}

export function useTenant(): TenantContextValue {
  const ctx = React.useContext(TenantContext)
  if (!ctx) throw new Error("useTenant must be used within TenantProvider")
  return ctx
}
