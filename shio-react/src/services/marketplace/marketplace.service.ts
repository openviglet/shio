import axios from "@/lib/axios"

import type {
  ShMarketplaceCatalogue,
  ShMarketplaceInstalled,
  ShMarketplaceSetupRequest,
  ShMarketplaceSetupResult,
} from "@/models/marketplace/marketplace.model"

/**
 * The package marketplace (SH646, SH675) — console-authenticated.
 *
 * The shared `@/lib/axios` singleton, never a private `axios.create()`: a private instance opts
 * out of every shared interceptor including CSRF, which a session-bearing caller must send
 * (SH167). That was the Universal Editor save bug (SH233), and these two writes would have
 * repeated it.
 */
export class ShMarketplaceService {
  /** The catalogue, and where the list came from. */
  async catalogue(): Promise<ShMarketplaceCatalogue> {
    const response = await axios.get<ShMarketplaceCatalogue>("/v2/marketplace")
    return response.data
  }

  /**
   * One entry's README, fetched through the instance.
   *
   * Proxied rather than read from the browser for two reasons, and the second is the one that
   * matters: a catalogue on another host is a CORS failure, and the server only serves a URL its
   * own catalogue declares — so this cannot be pointed at an arbitrary host even by a caller that
   * edits the request.
   */
  async readme(readmeUrl: string): Promise<string> {
    const response = await axios.get<string>("/v2/marketplace/readme", {
      params: { url: readmeUrl },
      responseType: "text",
      transformResponse: [(data) => data],
    })
    return response.data
  }

  /**
   * SH1253 — where an `<img>` loads one entry's screenshot: through the instance, which serves
   * only a URL its own catalogue declares, like the README.
   */
  screenshotSrc(url: string): string {
    return `${axios.defaults.baseURL ?? "/api"}/v2/marketplace/screenshot?url=${encodeURIComponent(url)}`
  }

  /** Download and install. `dryRun` validates everything and writes nothing (P7). */
  async install(id: string, dryRun = false): Promise<ShMarketplaceInstalled> {
    const response = await axios.post<ShMarketplaceInstalled>(
      `/v2/marketplace/${encodeURIComponent(id)}/install`,
      null,
      { params: dryRun ? { dryRun: true } : {} },
    )
    return response.data
  }

  /**
   * SH1251 — install if needed, then apply to a site. With `dryRun` it plans both, including the
   * apply of a package not installed yet, and writes nothing.
   */
  async setup(id: string, request: ShMarketplaceSetupRequest): Promise<ShMarketplaceSetupResult> {
    const response = await axios.post<ShMarketplaceSetupResult>(
      `/v2/marketplace/${encodeURIComponent(id)}/setup`,
      request,
    )
    return response.data
  }

  /** Remove a package that was installed from a catalogue. */
  async uninstall(id: string): Promise<ShMarketplaceInstalled> {
    const response = await axios.delete<ShMarketplaceInstalled>(
      `/v2/marketplace/${encodeURIComponent(id)}`,
    )
    return response.data
  }
}
