import type { ShSite } from "@/models/site/site";
import axios from "axios";
import type { ShRead } from "@/lib/read-options"

export class ShSiteService {
  async query(read: ShRead = {}): Promise<ShSite[]> {
    const response = await axios.get<ShSite[]>("/v2/site", { signal: read.signal });
    return response.data;
  }

  async get(id: string): Promise<ShSite> {
    const response = await axios.get<ShSite>(`/v2/site/${id}`);
    return response.data;
  }

  async create(site: ShSite): Promise<ShSite> {
    const response = await axios.post<ShSite>("/v2/site", site);
    return response.data;
  }

  async update(site: ShSite): Promise<ShSite> {
    const response = await axios.put<ShSite>(`/v2/site/${site.id}`, site);
    return response.data;
  }

  /**
   * Puts the site and everything in it in the trash (SH648) — reversible via {@link untrash}.
   *
   * <p>It used to purge, which made the console's most destructive button the only one with no
   * undo behind it while every level below had one. The button did not change; what it means
   * did, which is why the trash page had to learn about sites in the same task.</p>
   */
  async delete(id: string): Promise<void> {
    await axios.delete(`/v2/site/${id}`);
  }

  /** SH648 — sites currently in the trash, newest deletion first. */
  async trash(read: ShRead = {}): Promise<ShSite[]> {
    const response = await axios.get<ShSite[]>("/v2/site/trash", { signal: read.signal });
    return response.data;
  }

  /** SH648 — restore a trashed site, with the content that went into the trash with it. */
  async untrash(id: string): Promise<void> {
    await axios.post(`/v2/site/${id}/untrash`);
  }

  /** SH648 — remove a site for good. This is what {@link delete} used to do. */
  async purge(id: string): Promise<void> {
    await axios.delete(`/v2/site/${id}/purge`);
  }

  async export(id: string): Promise<Blob> {
    const response = await axios.get(`/v2/site/${id}/export`, { responseType: "blob" });
    return response.data;
  }

  async import(file: File): Promise<ShSite> {
    const formData = new FormData();
    formData.append("file", file);
    const response = await axios.post<ShSite>("/v2/import", formData);
    return response.data;
  }
}
