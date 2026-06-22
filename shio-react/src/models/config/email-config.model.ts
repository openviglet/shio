export type ShEmailConfig = {
  enabled: boolean
  host: string
  port: string
  username: string
  password: string
  testConnection: boolean
  smtpAuth: boolean
  tlsEnabled: boolean
  tlsRequired: boolean
  sslEnabled: boolean
}
