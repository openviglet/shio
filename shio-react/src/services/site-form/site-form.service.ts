/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import axios from "@/lib/axios"
import type { ShRead } from "@/lib/read-options"

/**
 * SH1066 — where a site's form submissions land (SH632's `/api/v2/site-form`).
 *
 * One row per site: the folder by name chain within the site, the post-types it invites and the
 * browser origins it accepts, each comma-separated as the server stores them, and whether it is
 * accepting at all. Creating a row is not the same act as enabling it (P7).
 */
export interface ShSiteForm {
  id: string
  siteId: string
  folderUrl?: string
  allowedTypes?: string
  allowedOrigins?: string
  enabled: boolean
}

export type ShSiteFormInput = Omit<ShSiteForm, "id">

export class ShSiteFormService {
  async list(read: ShRead = {}): Promise<ShSiteForm[]> {
    const response = await axios.get<ShSiteForm[]>("/v2/site-form", { signal: read.signal })
    return response.data
  }

  /** The one configuration a site has, or `undefined` when it has none yet. */
  async ofSite(siteId: string, read: ShRead = {}): Promise<ShSiteForm | undefined> {
    return (await this.list(read)).find((form) => form.siteId === siteId)
  }

  async create(body: ShSiteFormInput): Promise<ShSiteForm> {
    const response = await axios.post<ShSiteForm>("/v2/site-form", body)
    return response.data
  }

  async update(id: string, body: ShSiteFormInput): Promise<ShSiteForm> {
    const response = await axios.put<ShSiteForm>(`/v2/site-form/${id}`, body)
    return response.data
  }
}
