export type ShExchangeProviderVendor = {
  id: string
  name: string
  className?: string
  description?: string
  configurationPage?: string
}

export type ShExchangeProviderInstance = {
  id: string
  name: string
  description?: string
  position?: number
  enabled: boolean
  vendor?: ShExchangeProviderVendor
  properties?: Record<string, string>
}
