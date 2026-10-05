import type {
  ShImportOutcome, ShIndexStatus, ShSite, ShSiteCoverage, ShSiteLocaleDeclaration,
  ShSiteLocalePolicy, ShSiteMemoryNote,
} from "@/models/site/site";
import axios from "axios";
import type { ShRead } from "@/lib/read-options"
import type { ThumbnailStatus } from "@/lib/site-thumbnail"

export class ShSiteService {
  async query(read: ShRead = {}): Promise<ShSite[]> {
    const response = await axios.get<ShSite[]>("/v2/site", { signal: read.signal });
    return response.data;
  }

  async get(id: string, read: ShRead = {}): Promise<ShSite> {
    const response = await axios.get<ShSite>(`/v2/site/${id}`, { signal: read.signal });
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
  /**
   * SH1135 — what the instance remembers about one site: the same notes an agent reads through
   * `shio_memory`, which is where `shio convert` records the round a site was replicated by.
   */
  async memory(siteName: string, read: ShRead = {}): Promise<ShSiteMemoryNote[]> {
    const response = await axios.get<{ notes?: ShSiteMemoryNote[] }>("/v2/agent/memory",
      { params: { site: siteName }, signal: read.signal });
    return response.data?.notes ?? [];
  }

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

  /**
   * SH1063 — whether what this instance publishes is findable, and where it sends it.
   *
   * The agent surface's own read (`/v2/agent/index`), not a console copy of it: three readings of
   * "which search site does this site go to" would be three chances to disagree with the listener
   * that actually routes (P10).
   */
  async indexStatus(read: ShRead = {}): Promise<ShIndexStatus> {
    const response = await axios.get<ShIndexStatus>("/v2/agent/index", { signal: read.signal });
    return response.data;
  }

  /** SH1131 — what the site has in each of its languages, and what it has not. */
  async coverage(id: string, page = 0, size = 50, read: ShRead = {}): Promise<ShSiteCoverage> {
    const response = await axios.get<ShSiteCoverage>(`/v2/site/${id}/coverage`,
      { params: { page, size }, signal: read.signal });
    return response.data;
  }

  /**
   * SH1065 — the zip, and the name the server gave it.
   *
   * The download used to be saved as `${site.name}.zip`, composed here. The server slugs the
   * site's own furl to build its `Content-Disposition`, so one artifact had two names depending
   * on which surface fetched it — and `shio export` writes the server's. Read rather than
   * derived, for the reason SH1065 gives the CLI: a second slugger is how they drift.
   */
  async export(id: string): Promise<{ blob: Blob; fileName: string | null }> {
    const response = await axios.get(`/v2/site/${id}/export`, { responseType: "blob" });
    const disposition = String(response.headers?.["content-disposition"] ?? "");
    const named = /filename="?([^";]+)"?/.exec(disposition);
    return { blob: response.data, fileName: named?.[1] ?? null };
  }

  /** SH1218 — whether the stored home picture shows the home as published now. */
  async thumbnailStatus(id: string, read: ShRead = {}): Promise<ThumbnailStatus> {
    const response = await axios.get<ThumbnailStatus>(`/v2/site/${id}/thumbnail/status`,
      { signal: read.signal });
    return response.data;
  }

  /**
   * SH1218 — sends a picture of the published home, taken at appearance `digest`. A home
   * published again since is refused, and a picture already at that digest stores nothing.
   */
  async putThumbnail(id: string, digest: string, picture: Blob,
                     read: ShRead = {}): Promise<{ stored: boolean }> {
    const response = await axios.put<{ stored: boolean }>(`/v2/site/${id}/thumbnail`, picture, {
      params: { digest },
      headers: { "Content-Type": picture.type || "image/png" },
      signal: read.signal,
    });
    return response.data;
  }

  /** SH1079 — the site, what arrived in it, and what the import could not do. */
  async import(file: File): Promise<ShImportOutcome> {
    const formData = new FormData();
    formData.append("file", file);
    const response = await axios.post<ShImportOutcome>("/v2/import", formData);
    return response.data;
  }
}
