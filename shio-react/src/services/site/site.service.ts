import type {
  ShSite, ShSiteCoverage, ShSiteLocaleDeclaration, ShSiteLocalePolicy,
} from "@/models/site/site";
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

  /** SH1056 — the languages the site reports: declared, or read off its content. */
  async locales(id: string, read: ShRead = {}): Promise<ShSiteLocalePolicy> {
    const response = await axios.get<ShSiteLocalePolicy>(`/v2/site/${id}/locales`,
      { signal: read.signal });
    return response.data;
  }

  /**
   * SH1056 — declares the site's languages, replacing any it had. The agent's upsert only fills a
   * missing policy and sends a curator here to change one, so this is the call that replaces.
   */
  async declareLocales(id: string, policy: ShSiteLocaleDeclaration): Promise<ShSiteLocalePolicy> {
    const response = await axios.put<ShSiteLocalePolicy>(`/v2/site/${id}/locales`, policy);
    return response.data;
  }

  /** SH1056 — stops declaring, so the site reports the languages its content is in again. */
  async clearLocales(id: string): Promise<ShSiteLocalePolicy> {
    const response = await axios.delete<ShSiteLocalePolicy>(`/v2/site/${id}/locales`);
    return response.data;
  }

  /** SH1131 — what the site has in each of its languages, and what it has not. */
  async coverage(id: string, page = 0, size = 50, read: ShRead = {}): Promise<ShSiteCoverage> {
    const response = await axios.get<ShSiteCoverage>(`/v2/site/${id}/coverage`,
      { params: { page, size }, signal: read.signal });
    return response.data;
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
