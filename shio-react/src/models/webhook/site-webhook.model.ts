export type ShWebhookEventType = "PUBLISH" | "UNPUBLISH" | "DELETE"

export const SH_WEBHOOK_EVENT_TYPES: ShWebhookEventType[] = ["PUBLISH", "UNPUBLISH", "DELETE"]

export type ShSiteWebhook = {
  id: string
  name: string
  /** null/blank = catch-all (every site). */
  siteId?: string | null
  url: string
  secret: string
  /** Empty array = fire on all events. */
  events: ShWebhookEventType[]
  enabled: boolean
  owner: string
  createdDate: string
}
