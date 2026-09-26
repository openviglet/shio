import axios from "@/lib/axios"
import type { ShRead } from "@/lib/read-options"

/** The lists that keep saved views (`ShSavedViewService.LISTS`). */
export type SavedViewList = "browser" | "search" | "trash" | "review"

/** One view a curator can open: their own, or one somebody shared with the tenant. */
export interface ShSavedView {
  id: string
  name: string
  /** The console path the view opens. */
  path: string
  /** The query string, without its `?`. */
  query: string
  shared: boolean
  /** Whether the signed-in curator owns it, and so may delete it. */
  mine: boolean
}

/** SH1024 — saved views: named URLs for the lists whose filter bar offers them. */
export class ShSavedViewService {
  async list(list: SavedViewList, read: ShRead = {}): Promise<ShSavedView[]> {
    const response = await axios.get<ShSavedView[]>("/v2/user/current/views", { params: { list }, signal: read.signal })
    return response.data
  }

  async save(list: SavedViewList, name: string, path: string, query: string, shared: boolean): Promise<ShSavedView> {
    const response = await axios.post<ShSavedView>("/v2/user/current/views", { list, name, path, query, shared })
    return response.data
  }

  async delete(id: string): Promise<void> {
    await axios.delete(`/v2/user/current/views/${id}`)
  }
}
