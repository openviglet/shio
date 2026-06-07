export type ShAuthProviderVendor = {
  id: string
  name: string
  className?: string
  description?: string
  configurationPage?: string
}

export type ShAuthProviderInstance = {
  id: string
  name: string
  description?: string
  position?: number
  enabled: boolean
  vendor?: ShAuthProviderVendor
  properties?: Record<string, string>
}
