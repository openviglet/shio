import axios from "@/lib/axios"
import type { ShRead } from "@/lib/read-options"
import type { ShSiteWebhook } from "@/models/webhook/site-webhook.model"

export class ShSiteWebhookService {
  async list(): Promise<ShSiteWebhook[]> {
    const response = await axios.get<ShSiteWebhook[]>("/v2/site-webhook")
    return response.data
  }

  async get(id: string, read: ShRead = {}): Promise<ShSiteWebhook> {
    const response = await axios.get<ShSiteWebhook>(`/v2/site-webhook/${id}`, { signal: read.signal })
    return response.data
  }

  async create(webhook: ShSiteWebhook): Promise<ShSiteWebhook> {
    const response = await axios.post<ShSiteWebhook>("/v2/site-webhook", webhook)
    return response.data
  }

  async update(webhook: ShSiteWebhook): Promise<ShSiteWebhook> {
    const response = await axios.put<ShSiteWebhook>(`/v2/site-webhook/${webhook.id}`, webhook)
    return response.data
  }

  async delete(id: string): Promise<void> {
    await axios.delete(`/v2/site-webhook/${id}`)
  }
}
